package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Life
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Reunion as ReunionRules
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.ReunionScene
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.common.core.cutscene.SceneFrame
import daio7771.everytunnel.common.core.cutscene.Timeline
import daio7771.everytunnel.common.core.level.Position
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvent
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d
import java.util.Random
import java.util.UUID

/**
 * The reunion with the coworker and worker B (Chapter 2 Level 2, phase 6.1), in the room beyond Stone Maw's arena (`reunion` in
 * `level.json`). A player in [Ch2L2State.MAW_DEAD] who gets to the middle of the room ([TRIGGER_RADIUS] of `reunion.anchors.player`) starts it
 * ([begin]; CONTINUE to the `reunion` checkpoint is that spot, so it plays again): the state goes to [Ch2L2State.REUNION], the two
 * stand at their anchors facing the player, and [Ch2L2Scenes.REUNION] holds the player and plays the cinematic ([ReunionScene]: first person, black bands, the
 * camera turning to whoever speaks). Each line's sound (the user's voices) plays when its beat begins, from its speaker; the NPCs are
 * alive: the speaker talks with their hands and looks at the player, the other listens and glances about now and then, both turn
 * to whoever speaks. After the last line (+ its gap) the scene is over and the player has control again while the NPCs set off ([lead]).
 *
 * **The way to the lift** (phase 9, `reunion.lead`): the coworker opens Level 3's door (`l3_door`) and the two lead the player along the
 * corridor, at `lead.speed`, one waypoint at each turn ([Lead.route]); when the player is more than `wait_distance` behind they stop and
 * turn to look back, and walk on when they are near again. They go into the cab ([Ch2L2Lead.coworkerAnchor], [Ch2L2Lead.workerBAnchor]) and
 * face its gate, alive (they glance about). When the player is in `lead.lift.zone` the gate shuts, a pause, the motor and the shaking, and
 * [Ch2L2Ending.start] (the fade, the card, the menu). With the state [Ch2L2State.DONE] (CONTINUE after the card) the two wait in the cab.
 *
 * [finish] is the one, idempotent end of the scene (its end and its skip): the lead begins if it has not. A start of the level or leaving the
 * world drops everything. Server thread only.
 */
object Ch2L2Reunion {

    private const val GROUP = "ch2l2_reunion"
    private const val TRIGGER_RADIUS = 2.0
    private const val NPC_EYE = 1.62

    private val data get() = Ch2L2DataLoader.data
    private val rules get() = data.level.reunion

    private class Running(val player: UUID, val startTick: Long, val built: ReunionScene.Built) {
        var coworker: FactoryNpcEntity? = null
        var workerB: FactoryNpcEntity? = null
        var lastBeat = -1
    }

    private var running: Running? = null

    /** The walk to the lift, once the scene is over (see the class). */
    private class Lead(val player: UUID, val coworker: FactoryNpcEntity, val workerB: FactoryNpcEntity) {
        var leg = 0
        var waiting = false
        var doorOpened = false
        var gateAt: Long? = null
        var endAt: Long? = null
        var motorStarted = false

        /** Where the coworker has been, a position a tick: the second worker walks the same line a few steps behind (never side by side, so never into each other). */
        val trail = ArrayDeque<Vec3d>()
    }

    private var lead: Lead? = null

    /** The two, waiting in the room from the fight on (behind its shut door, so they are there, alive, when it opens). */
    private var waiting: Pair<FactoryNpcEntity, FactoryNpcEntity>? = null
    private var waitingInLift = false

    fun register() {
        Ch2L2Perf.register("reunion", ::tick)
        Ch2L2.onStart { player, state ->
            drop(player.server)
            // The doors as the state wants them: past the end, Level 3's door is open and the lift's gate shut behind the two.
            levelWorld(player.server)?.let {
                Ch2L2Build.setGate(it, rules.lead.doorGate, if (state == Ch2L2State.DONE) "open" else "shut")
                Ch2L2Build.setGate(it, rules.lead.gate, if (state == Ch2L2State.DONE) "shut" else "open")
            }
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (running?.player == handler.player.uuid) drop(handler.player.server) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            running = null
            waiting = null
            lead = null
        }
    }

    val isRunning: Boolean get() = running != null

    /** The scene's timeline, from the data alone (the client builds the same one). */
    fun timeline(): Timeline = build().timeline

    /** The scene as the data lays it out (the client's subtitles read who speaks when from it). */
    fun built(): ReunionScene.Built = build()

    private fun build(): ReunionScene.Built {
        val r = rules
        val player = data.anchors.point(r.playerAnchor)
        val frame = player?.let { SceneFrame(it.position.x, it.position.y, it.position.z, it.yaw.toDouble()) } ?: SceneFrame(0.0, 0.0, 0.0, 0.0)
        val places = linkedMapOf<String, Position>()
        data.anchors.point(r.coworkerAnchor)?.let { places[Ch2L1Skins.COWORKER] = it.position }
        data.anchors.point(r.workerBAnchor)?.let { places[Ch2L1Skins.WORKER_B] = it.position }
        val leave = data.anchors.point(r.leaveAnchor)?.position ?: Position(frame.x, frame.y, frame.z)
        return ReunionScene.build(r, frame, places, leave)
    }

    // --- the clock -------------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        val world = levelWorld(server) ?: return
        wait(world)
        tickLead(world, server.ticks.toLong())
        val run = running
        if (run == null) {
            if (lead != null) return
            val centre = data.anchors.point(rules.playerAnchor)?.position ?: return
            for (player in world.players) {
                if (!Ch2L2.active(player) || !player.isAlive || player.isSpectator) continue
                val state = Ch2L2.state(player)
                if (state != Ch2L2State.MAW_DEAD && state != Ch2L2State.REUNION) continue
                if (Math.hypot(player.x - centre.x, player.z - centre.z) > TRIGGER_RADIUS || Math.abs(player.y - centre.y) > 2.0) continue
                begin(world, player)
                return
            }
            return
        }
        val player = server.playerManager.getPlayer(run.player)
        if (player == null) {
            drop(server)
            return
        }
        val t = (server.ticks - run.startTick).toInt()
        direct(world, player, run, t)
        if (t >= run.built.total) finish(server, player)
    }

    /**
     * From [Ch2L2State.MAW] until the level is done the coworker and worker B stand in the reunion room, so that they are already there,
     * and alive (they chat: each in turn talks with their hands, the other listens, they look at each other), when the far door opens and the
     * player sees them. Put there when a player is playing the level at that state, and put back if lost.
     */
    private fun wait(world: ServerWorld) {
        val player = world.players.firstOrNull { Ch2L2.active(it) && it.isAlive }
        val state = player?.let { Ch2L2.state(it) }
        val wanted = state != null && state >= Ch2L2State.MAW
        if (!wanted) return
        if (running != null || lead != null) return
        val inLift = state == Ch2L2State.DONE
        val coworkerAnchor = if (inLift) rules.lead.coworkerAnchor else rules.coworkerAnchor
        val workerBAnchor = if (inLift) rules.lead.workerBAnchor else rules.workerBAnchor
        val npcs = waiting
        if (npcs != null && !npcs.first.isRemoved && !npcs.second.isRemoved) {
            chat(npcs, world.server.ticks)
            return
        }
        if (world.server.ticks % ENSURE_EVERY != 0) return
        val at = data.anchors.point(coworkerAnchor)?.position ?: return
        if (!world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y, at.z))) return
        dropNpcs(world.server)
        val a = spawn(world, coworkerAnchor, Ch2L1Skins.COWORKER) ?: return
        val b = spawn(world, workerBAnchor, Ch2L1Skins.WORKER_B) ?: return
        waiting = a to b
        waitingInLift = inLift
    }

    /** Two people talking: in turns of a few seconds one talks (with their hands) and looks at the other, who listens, glancing away now and then. */
    private fun chat(npcs: Pair<FactoryNpcEntity, FactoryNpcEntity>, tick: Int) {
        val (a, b) = npcs
        val turn = (tick / CHAT_TURN_TICKS + a.seed.toInt().and(1)) % 2
        for ((i, npc) in listOf(a, b).withIndex()) {
            val other = if (i == 0) b else a
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            val speaking = turn == i
            d.walkTo = data.anchors.point(if (i == 0) (if (waitingInLift) rules.lead.coworkerAnchor else rules.coworkerAnchor) else (if (waitingInLift) rules.lead.workerBAnchor else rules.workerBAnchor))?.position?.let { Vec3d(it.x, it.y, it.z) }
            d.face = null
            d.lookAt = if (!speaking && glance(i, tick)) Vec3d(other.x + (if (i == 0) 2.0 else -2.0), other.eyeY, other.z) else Vec3d(other.x, other.eyeY, other.z)
            d.pose = if (speaking) Ch2L1Pose.TALKING else Ch2L1Pose.LISTENING
        }
    }

    private fun begin(world: ServerWorld, player: ServerPlayerEntity) {
        val built = build()
        val run = Running(player.uuid, world.server.ticks.toLong(), built)
        running = run
        if (Ch2L2.state(player) < Ch2L2State.REUNION) Ch2L2.setState(player, Ch2L2State.REUNION, "the reunion")
        val npcs = waiting?.takeIf { !it.first.isRemoved && !it.second.isRemoved }
        run.coworker = npcs?.first ?: spawn(world, rules.coworkerAnchor, Ch2L1Skins.COWORKER)
        run.workerB = npcs?.second ?: spawn(world, rules.workerBAnchor, Ch2L1Skins.WORKER_B)
        waiting = null
        val frame = data.anchors.point(rules.playerAnchor)?.let { SceneFrame(it.position.x, it.position.y, it.position.z, it.yaw.toDouble()) }
        if (frame != null) Ch2L2Scenes.REUNION.start(player, frame)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the reunion begins for {} ({} lines, {} ticks)", player.gameProfile.name, built.beats.size, built.total)
    }

    private fun spawn(world: ServerWorld, anchor: String, skin: String): FactoryNpcEntity? {
        val point = data.anchors.point(anchor) ?: return null
        val p = point.position
        return Ch2Entities.FACTORY_NPC.create(world)?.apply {
            refreshPositionAndAngles(p.x, p.y, p.z, point.yaw, 0f)
            headYaw = point.yaw
            bodyYaw = point.yaw
            this.skin = skin
            this.group = GROUP
            seed = skin.hashCode().toLong()
            life = Ch2L1Life.DEFAULT
            pace = rules.lead.speed / (FactoryNpcEntity.WALK * 20.0)
            direction = FactoryNpcEntity.Direction().apply { face = point.yaw }
        }?.also { world.spawnEntity(it) }
    }

    /** One tick of the NPCs: whoever speaks talks to the player, the other listens; both turn to the speaker; they glance about; at the end they leave. */
    private fun direct(world: ServerWorld, player: ServerPlayerEntity, run: Running, t: Int) {
        val built = run.built
        val beat = built.beatAt(t.toDouble())
        val index = if (beat == null) -1 else built.beats.indexOf(beat)
        if (index != run.lastBeat) {
            run.lastBeat = index
            if (beat != null) speak(world, run, beat)
        }
        val speaker = when (beat?.line?.who) {
            Ch2L1Skins.COWORKER -> run.coworker
            Ch2L1Skins.WORKER_B -> run.workerB
            else -> null
        }
        val playerEyes = Vec3d(player.x, player.eyeY, player.z)
        // After the last line (+ its gap) the two set off: the camera is flying back to the player's head (the scene's own end follows).
        if (t >= built.leaveStart) {
            beginLead(world, player, run)
            return
        }
        val leave = data.anchors.point(rules.leaveAnchor)?.position
        for ((i, npc) in listOfNotNull(run.coworker, run.workerB).withIndex()) {
            val point = data.anchors.point(if (npc === run.coworker) rules.coworkerAnchor else rules.workerBAnchor)
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            d.walkTo = point?.position?.let { Vec3d(it.x, it.y, it.z) }
            d.face = null
            val door = leave?.let { Vec3d(it.x, it.y + NPC_EYE, it.z) }
            val nervous = door != null && glance(i, t)
            d.lookAt = when {
                npc === speaker -> playerEyes
                nervous -> door
                speaker != null -> Vec3d(speaker.x, speaker.eyeY, speaker.z)
                else -> playerEyes
            }
            d.pose = when {
                npc === speaker -> Ch2L1Pose.TALKING
                speaker != null -> Ch2L1Pose.LISTENING
                else -> Ch2L1Pose.NONE
            }
        }
    }

    /** The listener's nerves: now and then, for a second, each looks away at the door, each on their own times (the same in every game). */
    private fun glance(index: Int, t: Int): Boolean {
        val period = GLANCE_PERIOD_TICKS + index * 37
        val phase = (t + index * 61) % period
        return phase < GLANCE_TICKS && Random((t + index * 61) / period * 31L + index).nextInt(3) != 0
    }

    private fun speak(world: ServerWorld, run: Running, beat: ReunionScene.Beat) {
        val sound = beat.line.sound ?: return
        val speaker = if (beat.line.who == Ch2L1Skins.COWORKER) run.coworker else run.workerB
        val at = speaker?.let { Vec3d(it.x, it.eyeY, it.z) } ?: return
        world.playSound(null, at.x, at.y, at.z, SoundEvent.of(Identifier(sound)), SoundCategory.VOICE, 1.6f, 1.0f)
    }

    // --- the end: the way to the lift ---------------------------------------------------------------------

    /** The reunion over (played to the end or skipped): the two set off for the lift if they have not already. Idempotent. */
    fun finish(server: MinecraftServer, player: ServerPlayerEntity) {
        val run = running ?: return
        val world = levelWorld(server) ?: return
        beginLead(world, player, run)
        running = null
    }

    /** The lead begins (once): Level 3's door is theirs to open and the NPCs are given to [lead]. */
    private fun beginLead(world: ServerWorld, player: ServerPlayerEntity, run: Running) {
        if (lead != null) return
        val a = run.coworker ?: return
        val b = run.workerB ?: return
        lead = Lead(player.uuid, a, b)
        EveryTunnel.LOGGER.info("Chapter 2 Level 2: the two lead {} to the lift", player.gameProfile.name)
    }

    /** The route: where the two go through, after the door (waypoints, the last the top of the flight). */
    private fun route(): List<Position> = (listOf(rules.lead.doorAnchor) + rules.lead.waypoints).mapNotNull { data.anchors.point(it)?.position }

    /** One tick of the walk to the lift (see the class). */
    private fun tickLead(world: ServerWorld, now: Long) {
        val l = lead ?: return
        val player = world.server.playerManager.getPlayer(l.player)
        if (player == null || !player.isAlive || l.coworker.isRemoved || l.workerB.isRemoved) {
            if (player == null) drop(world.server)
            return
        }
        val cfg = rules.lead
        val points = route()
        val npcs = listOf(l.coworker, l.workerB)
        // The door: the coworker opens it as they come to it.
        if (!l.doorOpened) {
            val door = Ch2L2Build.gateCentre(cfg.doorGate)
            if (door == null || Math.hypot(l.coworker.x - door.x, l.coworker.z - door.z) <= DOOR_OPEN_DISTANCE) {
                l.doorOpened = true
                Ch2L2Build.setGate(world, cfg.doorGate, "open")
                data.anchors.point(cfg.doorAnchor)?.position?.let { world.playSound(null, it.x - 2.0, it.y + 1.0, it.z, Ch2Sounds.CH2L2_EXIT_DOOR, SoundCategory.BLOCKS, 1.5f, 1.0f) }
            }
        }
        val inLift = l.leg >= points.size
        l.trail.addLast(Vec3d(l.coworker.x, l.coworker.y, l.coworker.z))
        while (l.trail.size > FOLLOW_TICKS) l.trail.removeFirst()
        // Waiting: the player too far behind the leader (with a margin so that they do not flicker).
        val behind = Math.hypot(player.x - l.coworker.x, player.z - l.coworker.z) + Math.abs(player.y - l.coworker.y) * 0.5
        if (!l.waiting && behind > cfg.waitDistance) l.waiting = true
        if (l.waiting && behind < cfg.waitDistance * WAIT_RESUME_SHARE) l.waiting = false
        val eyes = Vec3d(player.x, player.eyeY, player.z)
        for ((i, npc) in npcs.withIndex()) {
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            d.pose = Ch2L1Pose.NONE
            when {
                l.waiting && !inLift -> {
                    // They stop and turn to look back for the player.
                    d.walkTo = null
                    d.face = null
                    d.lookAt = eyes
                }
                !inLift && i == 0 -> {
                    val to = points[l.leg]
                    d.walkTo = Vec3d(to.x, to.y, to.z)
                    d.stopThere = false
                    d.face = null
                    d.lookAt = null
                }
                !inLift -> {
                    // The second worker follows the coworker's own line, FOLLOW_TICKS behind.
                    val at = l.trail.firstOrNull()
                    d.walkTo = at ?: Vec3d(l.coworker.x, l.coworker.y, l.coworker.z)
                    d.stopThere = false
                    d.face = null
                    d.lookAt = null
                }
                else -> {
                    val target = data.anchors.point(if (i == 0) cfg.coworkerAnchor else cfg.workerBAnchor) ?: continue
                    d.walkTo = Vec3d(target.position.x, target.position.y, target.position.z)
                    d.stopThere = true
                    d.face = target.yaw
                    d.lookAt = null
                }
            }
        }
        if (!inLift && !l.waiting) {
            val to = points[l.leg]
            if (Math.hypot(l.coworker.x - to.x, l.coworker.z - to.z) <= WAYPOINT_REACHED) l.leg++
        }
        // In the cab: the gate shuts when the two are in and so is the player; then the motor; then the end.
        if (inLift) {
            val cab = npcs.withIndex().all { (i, npc) ->
                val target = data.anchors.point(if (i == 0) cfg.coworkerAnchor else cfg.workerBAnchor)?.position
                target == null || Math.hypot(npc.x - target.x, npc.z - target.z) <= AT_PLACE
            }
            val gateAt = l.gateAt
            if (gateAt == null) {
                if (cab && inZone(player, cfg.zone)) {
                    l.gateAt = now
                    Ch2L2Build.setGate(world, cfg.gate, "shut")
                    data.anchors.point(cfg.gateAnchor)?.position?.let { world.playSound(null, it.x, it.y, it.z, Ch2Sounds.CH2L2_LIFT_GATE, SoundCategory.BLOCKS, 1.8f, 1.0f) }
                    Ch2L2Maw.shake(world, Vec3d(player.x, player.y, player.z), 0.25, 8)
                    // From here the player stays in the cab.
                    npcs.forEach { n -> n.direction?.let { it.lookAt = null } }
                }
            } else if (!l.motorStarted && now - gateAt >= Math.round(cfg.gatePauseSeconds * 20)) {
                l.motorStarted = true
                world.playSound(null, player.x, player.y, player.z, Ch2Sounds.CH2L2_LIFT_MOTOR, SoundCategory.BLOCKS, 2.0f, 1.0f)
                Ch2L2Maw.shake(world, Vec3d(player.x, player.y, player.z), 0.5, Math.round(cfg.motorSeconds * 20).toInt())
                l.endAt = now + Math.round(cfg.motorSeconds * 20) - MOTOR_END_OVERLAP_TICKS
            } else if (l.motorStarted && l.endAt?.let { now >= it } == true) {
                l.endAt = null
                Ch2L2Ending.start(player)
            }
        }
    }

    private fun inZone(player: ServerPlayerEntity, zone: String) = data.anchors.zone(zone)?.box?.contains(player.x, player.y, player.z) == true

    /** Everything of the scene taken away. */
    private fun drop(server: MinecraftServer) {
        running = null
        lead = null
        dropNpcs(server)
    }

    private fun dropNpcs(server: MinecraftServer) {
        waiting = null
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) if (entity is FactoryNpcEntity && entity.group == GROUP) entity.discard()
        }
    }

    /** The scene played at once, wherever the player is (the debug command): they are put at the player's spot first. False if it cannot. */
    fun debugPlay(player: ServerPlayerEntity): String? {
        val world = levelWorld(player.server) ?: return "The anchors' dimension ${data.anchors.dimension} is not there."
        val point = data.anchors.point(rules.playerAnchor) ?: return "anchors.json has no anchor ${rules.playerAnchor}."
        if (running != null) return "The reunion is already playing."
        player.teleport(world, point.position.x, point.position.y, point.position.z, point.yaw, 0f)
        begin(world, player)
        return null
    }

    private fun levelWorld(server: MinecraftServer): ServerWorld? =
        Identifier.tryParse(data.anchors.dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }

    private const val ENSURE_EVERY = 20

    /** Blocks from the door's middle at which the coworker opens it. */
    private const val DOOR_OPEN_DISTANCE = 3.6
    private const val WAYPOINT_REACHED = 1.5

    /** The second worker walks where the coworker was this many ticks ago (a few steps behind). */
    private const val FOLLOW_TICKS = 14
    private const val AT_PLACE = 0.8

    /** They walk on again when the player is within this share of the wait distance. */
    private const val WAIT_RESUME_SHARE = 0.7

    /** The end begins this long before the motor's sound does. */
    private const val MOTOR_END_OVERLAP_TICKS = 20
    private const val CHAT_TURN_TICKS = 90
    private const val GLANCE_PERIOD_TICKS = 140
    private const val GLANCE_TICKS = 22
}
