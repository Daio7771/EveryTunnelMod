package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Angles
import daio7771.everytunnel.chapter2.level1.core.Ch2L1OfficeScene
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Skins
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Voices
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.core.cutscene.Cutscene
import daio7771.everytunnel.core.cutscene.Local
import daio7771.everytunnel.core.cutscene.Look
import daio7771.everytunnel.core.cutscene.SceneFrame
import daio7771.everytunnel.core.cutscene.Shot
import daio7771.everytunnel.core.cutscene.Timeline
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.platform.KeptEntities
import daio7771.everytunnel.systems.ResourceSystem
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.TickProfiler
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.EntityType
import net.minecraft.entity.decoration.DisplayEntity
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtDouble
import net.minecraft.nbt.NbtFloat
import net.minecraft.nbt.NbtList
import net.minecraft.network.PacketByteBuf
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.UUID

/**
 * The office's cinematic of Chapter 2's Level 1 (phase 8, `cutscenes/office.json`, [Ch2L1OfficeScene]): Rowan catches the player at his
 * desk. It begins at [Ch2L1State.ACT3_CUTSCENE], which closing the second of Rowan's files reaches ([Ch2L1Act3Hooks.onOfficeTrigger]).
 *
 * - **Before the bands**: a player elsewhere in the office glides to the mark behind the desk (`glide_seconds`), facing the door; a
 *   player farther away (a command) is put there.
 * - **The scene** ([START_PACKET_ID]: the frame) plays on the client (`Ch2L1OfficeView`: the player's own eyes fixed on Rowan). Here, on
 *   the same ticks, Rowan ([FactoryNpcEntity], skin `rowan`, put in for the scene) is moved along the scene's track, facing where he walks,
 *   or the door, or the player, in its poses; the door opens for him and shuts behind him; the pass slides across the desk (an item display)
 *   and is the player's, and so is the flashlight (the level's rules give it from the overtime on: `Chapter2`).
 * - **The end**, played or skipped ([SKIP_PACKET_ID]), is [Ch2L1State.ACT3_OVERTIME]: Rowan beside the door, which he has opened, waits
 *   for the player to leave the office, watching them; once they are out he shuts it behind them and is gone ([Waiting]). Reaching the
 *   overtime any way ([onState]) gives the pass and fills the flashlight's battery, leaves the LAB almost dark (its lamps off, a faint red
 *   emergency light here and there; its workers and the patrols are already gone: [Ch2L1Npcs], [Ch2L1PatrolHooks]) and, 3 to 5 seconds
 *   after the player has control back in a loudspeaker's zone, plays `rowan_loyal_a6` once. Going back before it takes the pass back and
 *   the lights up.
 *
 * Server thread only.
 */
object Ch2L1OfficeHooks {

    val START_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_office_start")
    val END_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_office_end")
    val SKIP_PACKET_ID = Identifier(EveryTunnel.MOD_ID, "ch2l1_office_skip")

    private const val A6 = "rowan_loyal_a6"
    private const val DOOR_RADIUS = 2.6

    /** Farther than this from the mark, the player is put there rather than glide. */
    private const val GLIDE_REACH = 10.0

    private class Glide(val player: UUID, val from: Vec3d, val fromYaw: Float, val startTick: Long)

    private class Running(val player: UUID, val startTick: Long, val at: Vec3d, val yaw: Float, val built: Ch2L1OfficeScene.Built, val rowan: FactoryNpcEntity) {
        var opened = false
        var closed = false
        var reopened = false
        var card: DisplayEntity.ItemDisplayEntity? = null
        var cardGiven = false
    }

    private class Pending(var countdown: Int)

    /** Rowan by the office's door after the scene, waiting for [player] to go. */
    private class Waiting(val player: UUID, val rowan: FactoryNpcEntity)

    /** Out of the office and this far from its door, the player has gone: Rowan shuts it. */
    private const val GONE_FROM_DOOR = 2.5

    /** One lamp in this many of the LAB stays as a faint red emergency light. */
    private const val EMERGENCY_EVERY = 4

    private var glide: Glide? = null
    private var running: Running? = null
    private var waiting: Waiting? = null
    private val a6 = HashMap<UUID, Pending>()

    @Volatile
    private var clientScene: Ch2L1OfficeScene.Built? = null

    private val voices: Ch2L1Voices by lazy { Ch2L1Voices.load() }
    private val spec get() = Ch2L1DataLoader.office

    fun register() {
        ServerPlayNetworking.registerGlobalReceiver(SKIP_PACKET_ID) { server, player, _, _, _ ->
            server.execute { if (running?.player == player.uuid) finish(server, skipped = true) }
        }
        TickProfiler.endServerTick.register(::tick)
        Ch2L1.onStateChange(::onState)
        Ch2L1Act3Hooks.onOfficeTrigger { player -> Ch2L1.setState(player, Ch2L1State.ACT3_CUTSCENE, "Rowan's files read") }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            if (glide?.player == handler.player.uuid) glide = null
            if (running?.player == handler.player.uuid) handler.player.server.let { finish(it, skipped = true, setState = false) }
        }
        ServerLifecycleEvents.SERVER_STOPPED.register { forget() }
    }

    fun forget() {
        glide = null
        running?.let { cleanUp(it) }
        running = null
        waiting = null
        a6.clear()
    }

    fun isRunning(player: ServerPlayerEntity): Boolean = running?.player == player.uuid || glide?.player == player.uuid

    // --- the states ------------------------------------------------------------------------------

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        val server = player.server
        if (to == Ch2L1State.ACT3_CUTSCENE) {
            begin(player)
            return
        }
        if (from == Ch2L1State.ACT3_CUTSCENE && (running?.player == player.uuid || glide?.player == player.uuid)) {
            glide = null
            finish(server, skipped = true, setState = false)
        }
        if (to >= Ch2L1State.ACT3_OVERTIME) {
            after(player)
        } else if (from >= Ch2L1State.ACT3_OVERTIME || to == Ch2L1State.START) {
            undo(player)
        }
    }

    /** What the office leaves, however it was reached: the pass, the flashlight's battery full, the LAB almost dark, a6 to come. */
    private fun after(player: ServerPlayerEntity) {
        if (!player.inventory.containsAny { it.isOf(Ch2Items.OVERTIME_PASS) }) player.giveItemStack(ItemStack(Ch2Items.OVERTIME_PASS))
        val config = ConfigManager.gameplay
        ResourceSystem.set(player.uuid, ResourceSystem.stateOf(player.uuid, config).copy(battery = config.flashlight.batteryMaxSeconds))
        labLights(player.serverWorld, dim = true)
        if (!Ch2L1SpeakerHooks.heard(player, A6)) a6[player.uuid] = Pending(60 + player.random.nextInt(41))
    }

    private fun undo(player: ServerPlayerEntity) {
        val inventory = player.inventory
        for (slot in 0 until inventory.size()) if (inventory.getStack(slot).isOf(Ch2Items.OVERTIME_PASS)) inventory.setStack(slot, ItemStack.EMPTY)
        labLights(player.serverWorld, dim = false)
        a6.remove(player.uuid)
        if (waiting?.player == player.uuid) waiting?.let { gone(it.rowan) }
        waiting = null
        val resources = ResourceSystem.stateOf(player.uuid, ConfigManager.gameplay)
        if (resources.flashlightOn) ResourceSystem.set(player.uuid, ResourceSystem.switchOff(resources))
    }

    /**
     * The LAB's ceiling lights (the plan's lamps in the zone `lab`) off ([Ch2Blocks.FACTORY_LIGHT_OFF]), one in [EMERGENCY_EVERY] a faint red
     * emergency light ([Ch2Blocks.FACTORY_LIGHT_EMERGENCY]); or all back on.
     */
    private fun labLights(world: ServerWorld, dim: Boolean) {
        val box = Ch2L1DataLoader.data.anchors.zone("lab")?.box ?: return
        val plan = Ch2L1Build.plan
        val lamps = plan.lights.map { BlockPos(plan.worldX(it.x), plan.worldY(it.y), plan.worldZ(it.z)) }
            .filter { box.contains(it.x + 0.5, it.y - 0.5, it.z + 0.5) || box.contains(it.x + 0.5, it.y + 0.5, it.z + 0.5) }
        for ((i, pos) in lamps.withIndex()) {
            val state = world.getBlockState(pos)
            val lit = state.isOf(ModBlocks.LAB_CEILING_LIGHT)
            val dark = state.isOf(Ch2Blocks.FACTORY_LIGHT_OFF) || state.isOf(Ch2Blocks.FACTORY_LIGHT_EMERGENCY)
            when {
                dim && lit -> world.setBlockState(pos, (if (i % EMERGENCY_EVERY == 0) Ch2Blocks.FACTORY_LIGHT_EMERGENCY else Ch2Blocks.FACTORY_LIGHT_OFF).defaultState)
                !dim && dark -> world.setBlockState(pos, ModBlocks.LAB_CEILING_LIGHT.defaultState)
            }
        }
    }

    // --- the scene -------------------------------------------------------------------------------

    /** The scene seen from [frame], or null (logged) if the data lacks something. */
    fun build(frame: SceneFrame): Ch2L1OfficeScene.Built? {
        val result = Ch2L1OfficeScene.build(spec, Ch2L1DataLoader.data.anchors, voices, frame)
        result.problem?.let { EveryTunnel.LOGGER.warn("Chapter 2 Level 1: the office's scene cannot be made: {}", it) }
        return result.built
    }

    fun clientBegin(frame: SceneFrame): Boolean {
        clientScene = build(frame)
        return clientScene != null
    }

    fun clientScene(): Ch2L1OfficeScene.Built? = clientScene

    fun clientTimeline(): Timeline = clientScene?.timeline ?: PLACEHOLDER

    private val PLACEHOLDER = Timeline(Cutscene(listOf(Shot(1, Local(0.0, 0.0, 0.0), Local(0.0, 0.0, 0.0), Look.Player)), 1, 1), emptyList(), emptyList())

    fun readStart(buf: PacketByteBuf): SceneFrame = SceneFrame(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat().toDouble())

    /** At [Ch2L1State.ACT3_CUTSCENE]: the player to the mark (gliding if they are in the office), then the scene. */
    private fun begin(player: ServerPlayerEntity) {
        val mark = Ch2L1DataLoader.data.anchors.point(spec.playerMark)
        if (mark == null) {
            EveryTunnel.LOGGER.warn("Chapter 2 Level 1: no {}: the office's scene is skipped", spec.playerMark)
            Ch2L1.setState(player, Ch2L1State.ACT3_OVERTIME, "no office scene")
            return
        }
        val p = mark.position
        val far = player.squaredDistanceTo(p.x, p.y, p.z) > GLIDE_REACH * GLIDE_REACH || player.serverWorld != Ch2L1Npcs.world(player.server)
        if (far) {
            Ch2L1Npcs.world(player.server)?.let { player.teleport(it, p.x, p.y, p.z, mark.yaw, 0f) }
            start(player)
        } else {
            glide = Glide(player.uuid, player.pos, player.yaw, player.server.ticks.toLong())
        }
    }

    private fun start(player: ServerPlayerEntity) {
        val server = player.server
        val world = Ch2L1Npcs.world(server) ?: return
        val anchors = Ch2L1DataLoader.data.anchors
        val mark = anchors.point(spec.playerMark) ?: return
        val spawn = anchors.point(spec.spawn)
        val p = mark.position
        player.stopRiding()
        player.teleport(world, p.x, p.y, p.z, mark.yaw, 0f)
        val frame = SceneFrame(p.x, p.y, p.z, mark.yaw.toDouble())
        val built = build(frame)
        val rowan = spawn?.let { s ->
            KeptEntities.spawn(world) { w ->
                Ch2Entities.FACTORY_NPC.create(w)?.apply {
                    refreshPositionAndAngles(s.position.x, s.position.y, s.position.z, s.yaw, 0f)
                    skin = Ch2L1Skins.ROWAN
                    group = GROUP
                    life = Ch2L1DataLoader.npcs.life
                    direction = FactoryNpcEntity.Direction()
                }
            }
        }
        if (built == null || rowan == null || !ServerPlayNetworking.canSend(player, START_PACKET_ID)) {
            rowan?.let(::gone)
            Ch2L1.setState(player, Ch2L1State.ACT3_OVERTIME, "no office scene")
            return
        }
        running = Running(player.uuid, server.ticks.toLong(), Vec3d(p.x, p.y, p.z), mark.yaw, built, rowan)
        val buf = PacketByteBufs.create()
        buf.writeDouble(frame.x)
        buf.writeDouble(frame.y)
        buf.writeDouble(frame.z)
        buf.writeFloat(mark.yaw)
        ServerPlayNetworking.send(player, START_PACKET_ID, buf)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the office's scene begins for {} ({} s)", player.gameProfile.name, built.totalTicks / 20)
    }

    private fun tick(server: MinecraftServer) {
        tickGlide(server)
        tickScene(server)
        tickWaiting(server)
        tickA6(server)
    }

    private fun tickGlide(server: MinecraftServer) {
        val g = glide ?: return
        val player = server.playerManager.getPlayer(g.player)
        val mark = Ch2L1DataLoader.data.anchors.point(spec.playerMark)
        if (player == null || mark == null) {
            glide = null
            return
        }
        val t = ((server.ticks - g.startTick).toDouble() / maxOf(1, spec.glideTicks)).coerceIn(0.0, 1.0)
        val e = Cutscene.ease(t)
        val p = mark.position
        val yaw = g.fromYaw + Ch2L1Angles.wrap(mark.yaw - g.fromYaw) * e.toFloat()
        player.networkHandler.requestTeleport(g.from.x + (p.x - g.from.x) * e, g.from.y + (p.y - g.from.y) * e, g.from.z + (p.z - g.from.z) * e, yaw, 0f)
        player.velocity = Vec3d.ZERO
        if (t >= 1.0) {
            glide = null
            start(player)
        }
    }

    private fun tickScene(server: MinecraftServer) {
        val r = running ?: return
        val player = server.playerManager.getPlayer(r.player)
        if (player == null) {
            cleanUp(r)
            running = null
            return
        }
        val built = r.built
        val t = (server.ticks - r.startTick).toInt()
        if (t >= built.totalTicks) {
            finish(server, skipped = false)
            return
        }
        if (player.pos.squaredDistanceTo(r.at) > 0.0025) player.networkHandler.requestTeleport(r.at.x, r.at.y, r.at.z, r.yaw, 0f)
        player.velocity = Vec3d.ZERO
        val world = player.serverWorld
        val anchors = Ch2L1DataLoader.data.anchors
        val door = anchors.point(spec.door)?.position?.let { Vec3d(it.x, it.y, it.z) }
        // Rowan, on the scene's track.
        val rowan = r.rowan
        val segment = built.segmentAt(t.toDouble())
        val at = built.rowanAt(t.toDouble())
        rowan.setPosition(at.x, at.y, at.z)
        rowan.velocity = Vec3d.ZERO
        val d = rowan.direction ?: FactoryNpcEntity.Direction().also { rowan.direction = it }
        val eyes = Vec3d(player.x, player.eyeY, player.z)
        d.walkTo = null
        d.lookAt = if (segment.lookDoor && door != null) door.add(0.0, 1.4, 0.0) else eyes
        d.face = when {
            segment.moving -> Ch2L1Angles.yawOf(segment.to.x - segment.from.x, segment.to.z - segment.from.z)
            segment.lookDoor && door != null -> Ch2L1Angles.yawOf(door.x - at.x, door.z - at.z)
            built.poseAt(t.toDouble()) == Ch2L1Pose.POINTING && door != null -> Ch2L1Angles.yawOf(door.x - at.x, door.z - at.z)
            else -> Ch2L1Angles.yawOf(player.x - at.x, player.z - at.z)
        }
        d.pose = if (segment.moving && built.poseAt(t.toDouble()) == Ch2L1Pose.NONE) Ch2L1Pose.NONE else built.poseAt(t.toDouble())
        // The door, and the pass.
        if (!r.opened && t >= built.doorOpens) {
            r.opened = true
            door?.let { Ch2L1Doors.set(world, it, DOOR_RADIUS, true, rowan, locked = true) }
        }
        if (!r.closed && t >= built.doorCloses) {
            r.closed = true
            door?.let { Ch2L1Doors.set(world, it, DOOR_RADIUS, false, rowan, locked = true) }
        }
        if (!r.reopened && t >= built.doorReopens) {
            r.reopened = true
            door?.let { Ch2L1Doors.set(world, it, DOOR_RADIUS, true, rowan, locked = true) }
        }
        if (t >= built.slideStart && !r.cardGiven) slideCard(r, player, t)
    }

    /** The pass across the desk: an item display gliding from `card_from` to `card_to`, then in the player's pockets. */
    private fun slideCard(r: Running, player: ServerPlayerEntity, t: Int) {
        val anchors = Ch2L1DataLoader.data.anchors
        val from = anchors.point(spec.cardFrom)?.position
        val to = anchors.point(spec.cardTo)?.position
        val world = player.serverWorld
        if (from == null || to == null || t >= r.built.slideEnd) {
            r.card?.discard()
            r.card = null
            r.cardGiven = true
            givePass(player)
            return
        }
        val card = r.card ?: EntityType.ITEM_DISPLAY.create(world)?.also { display ->
            display.readNbt(cardNbt(from.x, from.y, from.z))
            world.spawnEntity(display)
            r.card = display
            world.playSound(null, BlockPos.ofFloored(from.x, from.y, from.z), SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6f, 1.4f)
        } ?: return
        val k = ((t - r.built.slideStart).toDouble() / maxOf(1, r.built.slideEnd - r.built.slideStart)).coerceIn(0.0, 1.0)
        val e = Cutscene.ease(k)
        card.setPosition(from.x + (to.x - from.x) * e, from.y + (to.y - from.y) * e, from.z + (to.z - from.z) * e)
    }

    /** An item display of the pass lying flat on the desk at ([x], [y], [z]). */
    private fun cardNbt(x: Double, y: Double, z: Double): NbtCompound {
        fun floats(vararg v: Float) = NbtList().also { list -> v.forEach { list.add(NbtFloat.of(it)) } }
        val nbt = NbtCompound()
        nbt.put("Pos", NbtList().also { it.add(NbtDouble.of(x)); it.add(NbtDouble.of(y)); it.add(NbtDouble.of(z)) })
        nbt.put("item", ItemStack(Ch2Items.OVERTIME_PASS).writeNbt(NbtCompound()))
        val transformation = NbtCompound()
        transformation.put("left_rotation", floats(0.7071f, 0f, 0f, 0.7071f))
        transformation.put("right_rotation", floats(0f, 0f, 0f, 1f))
        transformation.put("translation", floats(0f, 0.01f, 0f))
        transformation.put("scale", floats(0.45f, 0.45f, 0.45f))
        nbt.put("transformation", transformation)
        return nbt
    }

    private fun givePass(player: ServerPlayerEntity) {
        if (player.inventory.containsAny { it.isOf(Ch2Items.OVERTIME_PASS) }) return
        player.giveItemStack(ItemStack(Ch2Items.OVERTIME_PASS))
        player.world.playSound(null, player.blockPos, SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.5f, 1.2f)
    }

    /** The scene's end, played through or skipped; [setState] false when a state change is what ends it. */
    private fun finish(server: MinecraftServer, skipped: Boolean, setState: Boolean = true) {
        val r = running ?: return
        running = null
        cleanUp(r)
        val player = server.playerManager.getPlayer(r.player)
        if (player == null) {
            gone(r.rowan)
            return
        }
        if (!r.cardGiven) givePass(player)
        // Beside the door, which he has opened, he waits for the player to go.
        val anchors = Ch2L1DataLoader.data.anchors
        anchors.point(spec.door)?.position?.let { Ch2L1Doors.set(player.serverWorld, Vec3d(it.x, it.y, it.z), DOOR_RADIUS, true, locked = true) }
        anchors.point(spec.threshold)?.let { r.rowan.refreshPositionAndAngles(it.position.x, it.position.y, it.position.z, it.yaw, 0f) }
        r.rowan.npcPose = Ch2L1Pose.NONE
        waiting = Waiting(r.player, r.rowan)
        if (ServerPlayNetworking.canSend(player, END_PACKET_ID)) {
            val buf = PacketByteBufs.create()
            buf.writeBoolean(skipped)
            ServerPlayNetworking.send(player, END_PACKET_ID, buf)
        }
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: the office's scene {}", if (skipped) "skipped" else "ends")
        if (setState) Ch2L1.setState(player, Ch2L1State.ACT3_OVERTIME, if (skipped) "office scene skipped" else "office scene over")
    }

    private fun cleanUp(r: Running) {
        r.card?.discard()
        r.card = null
    }

    /** Rowan by the door, watching the player until they are out; then he shuts the door behind them and is gone. */
    private fun tickWaiting(server: MinecraftServer) {
        val w = waiting ?: return
        val player = server.playerManager.getPlayer(w.player)
        if (player == null || w.rowan.isRemoved || !Ch2L1.active(player) || Ch2L1.state(player) < Ch2L1State.ACT3_OVERTIME) {
            gone(w.rowan)
            waiting = null
            return
        }
        val anchors = Ch2L1DataLoader.data.anchors
        val spot = anchors.point(spec.threshold)
        val door = anchors.point(spec.door)?.position
        val d = w.rowan.direction ?: FactoryNpcEntity.Direction().also { w.rowan.direction = it }
        d.walkTo = spot?.position?.let { Vec3d(it.x, it.y, it.z) }
        d.lookAt = Vec3d(player.x, player.eyeY, player.z)
        d.face = Ch2L1Angles.yawOf(player.x - w.rowan.x, player.z - w.rowan.z)
        d.pose = Ch2L1Pose.NONE
        val inside = anchors.zone("office")?.box?.contains(player.x, player.y, player.z) == true
        val far = door == null || player.squaredDistanceTo(door.x, door.y, door.z) > GONE_FROM_DOOR * GONE_FROM_DOOR
        if (!inside && far) {
            door?.let { Ch2L1Doors.set(player.serverWorld, Vec3d(it.x, it.y, it.z), DOOR_RADIUS, false, w.rowan, locked = true) }
            gone(w.rowan)
            waiting = null
            EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has left the office; Rowan shuts the door", player.gameProfile.name)
        }
    }

    private fun gone(npc: FactoryNpcEntity) {
        KeptEntities.forget { it === npc }
        npc.discard()
    }

    // --- a6, once, after it ----------------------------------------------------------------------

    private fun tickA6(server: MinecraftServer) {
        if (a6.isEmpty()) return
        for (id in a6.keys.toList()) {
            val player = server.playerManager.getPlayer(id) ?: continue
            if (!Ch2L1.active(player) || Ch2L1.state(player) != Ch2L1State.ACT3_OVERTIME) {
                a6.remove(id)
                continue
            }
            if (isRunning(player) || Ch2L1SpeakerHooks.zoneOf(player) == null) continue
            val pending = a6.getValue(id)
            if (--pending.countdown > 0) continue
            a6.remove(id)
            Ch2L1SpeakerHooks.announceOnce(player, A6)
        }
    }

    /** The group Rowan is put in for the scene. */
    const val GROUP = "office"
}
