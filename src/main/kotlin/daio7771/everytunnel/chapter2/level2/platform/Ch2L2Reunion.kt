package daio7771.everytunnel.chapter2.level2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Life
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.platform.FactoryNpcEntity
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Reunion as ReunionRules
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.level2.core.ReunionScene
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.core.level.Position
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
 * to whoever speaks. After the last line the three look at Level 3's door and the NPCs walk towards it.
 *
 * [finish] is the one, idempotent end (the scene's end and its skip): the NPCs gone, then [Ch2L2Ending.start] (the card, the menu). A
 * start of the level or leaving the world drops it. Server thread only.
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

    /** The two, waiting in the room from the fight on (behind its shut door, so they are there, alive, when it opens). */
    private var waiting: Pair<FactoryNpcEntity, FactoryNpcEntity>? = null

    fun register() {
        Ch2L2Perf.register("reunion", ::tick)
        Ch2L2.onStart { player, _ -> drop(player.server) }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> if (running?.player == handler.player.uuid) drop(handler.player.server) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            running = null
            waiting = null
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
        val run = running
        if (run == null) {
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
        val wanted = state != null && state >= Ch2L2State.MAW && state < Ch2L2State.DONE
        if (!wanted) return
        if (running != null) return
        val npcs = waiting
        if (npcs != null && !npcs.first.isRemoved && !npcs.second.isRemoved) {
            chat(npcs, world.server.ticks)
            return
        }
        if (world.server.ticks % ENSURE_EVERY != 0) return
        val at = data.anchors.point(rules.coworkerAnchor)?.position ?: return
        if (!world.isChunkLoaded(net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y, at.z))) return
        dropNpcs(world.server)
        val a = spawn(world, rules.coworkerAnchor, Ch2L1Skins.COWORKER) ?: return
        val b = spawn(world, rules.workerBAnchor, Ch2L1Skins.WORKER_B) ?: return
        waiting = a to b
    }

    /** Two people talking: in turns of a few seconds one talks (with their hands) and looks at the other, who listens, glancing away now and then. */
    private fun chat(npcs: Pair<FactoryNpcEntity, FactoryNpcEntity>, tick: Int) {
        val (a, b) = npcs
        val turn = (tick / CHAT_TURN_TICKS + a.seed.toInt().and(1)) % 2
        for ((i, npc) in listOf(a, b).withIndex()) {
            val other = if (i == 0) b else a
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            val speaking = turn == i
            d.walkTo = data.anchors.point(if (i == 0) rules.coworkerAnchor else rules.workerBAnchor)?.position?.let { Vec3d(it.x, it.y, it.z) }
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
            pace = rules.walkSpeed / (FactoryNpcEntity.WALK * 20.0)
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
        val leaving = t >= built.leaveStart
        val leave = data.anchors.point(rules.leaveAnchor)?.position
        for ((i, npc) in listOfNotNull(run.coworker, run.workerB).withIndex()) {
            val point = data.anchors.point(if (npc === run.coworker) rules.coworkerAnchor else rules.workerBAnchor)
            val d = npc.direction ?: FactoryNpcEntity.Direction().also { npc.direction = it }
            if (leaving && leave != null) {
                // They head for Level 3's door, a step apart, looking where they go.
                d.walkTo = Vec3d(leave.x, leave.y, leave.z + if (i == 0) -0.8 else 0.8)
                d.face = null
                d.lookAt = null
                d.pose = Ch2L1Pose.NONE
                continue
            }
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

    // --- the end ----------------------------------------------------------------------------------------------

    /** The reunion over (played to the end or skipped): the NPCs gone, then the card. Idempotent. */
    fun finish(server: MinecraftServer, player: ServerPlayerEntity) {
        if (running == null) return
        drop(server)
        Ch2L2Ending.start(player)
    }

    /** Everything of the scene taken away. */
    private fun drop(server: MinecraftServer) {
        running = null
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
    private const val CHAT_TURN_TICKS = 90
    private const val GLANCE_PERIOD_TICKS = 140
    private const val GLANCE_TICKS = 22
}
