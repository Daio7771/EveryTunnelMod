package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.chapter2.platform.Ch2Entities
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Chat
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Gaze
import daio7771.everytunnel.chapter2.level1.core.Ch2L1GroupMember
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Life
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Pose
import daio7771.everytunnel.chapter2.level1.core.Ch2L1RoutineRunner
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Routines
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1WorkerSpec
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.util.Random
import daio7771.everytunnel.platform.KeptEntities
import daio7771.everytunnel.platform.TickProfiler

/**
 * The factory's people ([FactoryNpcEntity]): putting them in and taking them out. Kept entities ([KeptEntities]): put in through its
 * `spawn`, so they come back where they were if their chunk unloads, and taken out with `forget` and `discard()`.
 *
 * - **The ordinary life** (`npcs.json`, `workers`): while a player plays the level, inside it and before its cinematic, [ensureAmbient]
 *   (every few seconds, and at once on entering) puts in each worker that is missing, with the routine the file gives it, where its
 *   spawn anchor says. The LAB's go away when the player reaches [Ch2L1State.ACT3_CUTSCENE] (phase 8), the others stay.
 *   Each walks at their own pace and looks about (`life`, [FactoryNpcEntity]).
 * - **Talks**: every few ticks, workers free to talk (not at a station, not just after a talk) who are within `chat_range` of each other are
 *   paired up, nearest first; some of the pairs ([Ch2L1Life.chatChance]) stop to talk ([Ch2L1Chat]: their routines wait, they face each
 *   other, one talks with their hands while the other listens, then they swap), the rest walk on.
 * - **A group** (`groups`): [spawnGroup] puts its people at their anchors, standing (the lockers' group of the meeting, which the phase of the
 *   meeting asks for), and [despawnGroup] takes a group out.
 * - **`/et ch2 l1 npc`**: the developer's tools ([spawnInFront] puts one person in front of them to see its skin; [clear] takes all away).
 */
object Ch2L1Npcs {

    /** How far in front of the player a person is put. */
    private const val AHEAD = 2.5

    /** How often missing workers are looked for, and how far from the level's box a player may be for it to count as being in it. */
    private const val CHECK_TICKS = 100
    private const val NEAR = 48

    private const val LAB = "lab"

    fun register() {
        TickProfiler.endServerTick.register(::tick)
        Ch2L1.onStateChange { player, _, to ->
            if (to.ordinal >= Ch2L1State.ACT3_CUTSCENE.ordinal) despawnGroup(player.server, LAB)
        }
    }

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_TICKS == 0) ensureAmbient(server)
        tickTalks(server)
    }

    private fun world(server: MinecraftServer): ServerWorld? {
        val dimension = Ch2L1DataLoader.data.anchors.dimension
        return Identifier.tryParse(dimension)?.let { server.getWorld(RegistryKey.of(RegistryKeys.WORLD, it)) }
    }

    /** The level's box in the world, grown by [NEAR] blocks. */
    private fun levelBox(): Box {
        val plan = Ch2L1Build.plan
        val (a, b) = plan.clear
        return Box(
            (plan.worldX(a.x) - NEAR).toDouble(), plan.worldY(a.y) - 8.0, (plan.worldZ(a.z) - NEAR).toDouble(),
            (plan.worldX(b.x) + NEAR + 1).toDouble(), plan.worldY(b.y) + 8.0, (plan.worldZ(b.z) + NEAR + 1).toDouble(),
        )
    }

    // --- the ordinary life ------------------------------------------------------------------------

    /**
     * Puts in every worker of `npcs.json` that is missing, if a player is playing the level (inside it, built, before its cinematic), whose
     * spawn chunk is loaded. Returns how many it put in.
     */
    fun ensureAmbient(server: MinecraftServer): Int {
        val world = world(server) ?: return 0
        val box = levelBox()
        val playing = server.playerManager.playerList.filter { it.serverWorld == world && Ch2L1.active(it) && box.contains(it.pos) }
        if (playing.isEmpty() || !Ch2L1Build.isBuilt(server)) return 0
        val lastState = playing.maxOf { Ch2L1.state(it).ordinal }
        val present = world.getEntitiesByType(Ch2Entities.FACTORY_NPC, box) { it.workerId != null }.mapNotNull { it.workerId }.toSet()
        val data = Ch2L1DataLoader.npcs
        var count = 0
        for (worker in data.workers) {
            if (worker.id in present) continue
            if (worker.group == LAB && lastState >= Ch2L1State.ACT3_CUTSCENE.ordinal) continue
            if (spawnWorker(world, worker, data.walkSpeed, data.life)) count++
        }
        return count
    }

    private fun spawnWorker(world: ServerWorld, worker: Ch2L1WorkerSpec, walkSpeed: Double, life: Ch2L1Life): Boolean {
        val problems = mutableListOf<String>()
        val resolved = Ch2L1Routines.resolve(worker, Ch2L1DataLoader.data.anchors, problems)
        if (resolved == null) {
            problems.forEach { EveryTunnel.LOGGER.warn("Chapter 2 Level 1: {}", it) }
            return false
        }
        val at = resolved.spawn.position
        if (!world.isChunkLoaded(BlockPos.ofFloored(at.x, at.y, at.z))) return false
        val seed = worker.id.hashCode().toLong()
        return KeptEntities.spawn(world) { w ->
            Ch2Entities.FACTORY_NPC.create(w)?.apply {
                refreshPositionAndAngles(at.x, at.y, at.z, resolved.spawn.yaw, 0f)
                headYaw = resolved.spawn.yaw
                bodyYaw = resolved.spawn.yaw
                skin = worker.skin
                workerId = worker.id
                group = worker.group
                pace = walkSpeed * life.pace(worker.pace, seed)
                this.life = life
                this.seed = seed
                talkCooldown = life.chatCooldownTicks.pick(Random(seed + 1)) / 2
                routine = Ch2L1RoutineRunner(resolved.steps, Random(seed), life.linger, life.lingerTicks)
            }
        } != null
    }

    // --- talks ---------------------------------------------------------------------------------------

    /** A talk going on between [a] and [b]. */
    private class Talk(val a: FactoryNpcEntity, val b: FactoryNpcEntity, val chat: Ch2L1Chat)

    private val talks = mutableListOf<Talk>()

    /** Ticks a worker who walked by someone without stopping to talk waits before the next chance. */
    private const val PASSED_BY = 200

    /** How often workers free to talk are paired up. */
    private const val PAIR_TICKS = 5

    /** The talks going on, a tick on; and every [PAIR_TICKS], new ones between workers free to talk who are close to each other. */
    private fun tickTalks(server: MinecraftServer) {
        talks.removeAll { talk ->
            val going = !talk.a.isRemoved && !talk.b.isRemoved && talk.chat.tick()
            if (going) {
                talk.a.npcPose = if (talk.chat.firstTalks) Ch2L1Pose.TALKING else Ch2L1Pose.LISTENING
                talk.b.npcPose = if (talk.chat.firstTalks) Ch2L1Pose.LISTENING else Ch2L1Pose.TALKING
            } else {
                end(talk.a)
                end(talk.b)
            }
            !going
        }
        if (server.ticks % PAIR_TICKS != 0) return
        val world = world(server) ?: return
        val life = Ch2L1DataLoader.npcs.life
        if (life.chatChance <= 0.0) return
        val free = world.getEntitiesByType(Ch2Entities.FACTORY_NPC, levelBox()) {
            it.routine != null && it.talkingTo == null && it.talkCooldown == 0 && it.mode != Ch2L1Gaze.Mode.TYPING
        }
        if (free.size < 2) return
        val byId = free.associateBy { it.id }
        val random = Random(server.ticks.toLong())
        for ((first, second) in Ch2L1Chat.pairs(free.map { Ch2L1Chat.Person(it.id, it.x, it.z) }, life.chatRange)) {
            val a = byId.getValue(first)
            val b = byId.getValue(second)
            if (random.nextDouble() >= life.chatChance) {
                a.talkCooldown = PASSED_BY
                b.talkCooldown = PASSED_BY
                continue
            }
            a.talkingTo = b
            b.talkingTo = a
            talks += Talk(a, b, Ch2L1Chat(life.chatTicks.pick(random), random))
        }
    }

    private fun end(person: FactoryNpcEntity) {
        person.talkingTo = null
        person.npcPose = Ch2L1Pose.NONE
        person.talkCooldown = person.life.chatCooldownTicks.pick(Random(person.seed + person.age))
    }

    // --- groups -------------------------------------------------------------------------------------

    /** The people of the group [name], standing at their anchors (those already there stay); how many were put in, or null if there is no such group. */
    fun spawnGroup(server: MinecraftServer, name: String): Int? {
        val members = Ch2L1DataLoader.npcs.groups[name] ?: return null
        val world = world(server) ?: return 0
        val box = levelBox()
        val present = world.getEntitiesByType(Ch2Entities.FACTORY_NPC, box) { it.group == name }.size
        if (present >= members.size) return 0
        var count = 0
        for (member in members.drop(present)) if (spawnMember(world, name, member)) count++
        return count
    }

    private fun spawnMember(world: ServerWorld, group: String, member: Ch2L1GroupMember): Boolean {
        val anchor = Ch2L1DataLoader.data.anchors.point(member.at) ?: return false
        val at = anchor.position
        return KeptEntities.spawn(world) { w ->
            Ch2Entities.FACTORY_NPC.create(w)?.apply {
                refreshPositionAndAngles(at.x, at.y, at.z, anchor.yaw, 0f)
                headYaw = anchor.yaw
                bodyYaw = anchor.yaw
                skin = member.skin
                this.group = group
            }
        } != null
    }

    /** Every person of [group] (a worker's group or a spawned one) gone; how many there were. */
    fun despawnGroup(server: MinecraftServer, group: String): Int {
        KeptEntities.forget { it is FactoryNpcEntity && it.group == group }
        var count = 0
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) {
                if (entity is FactoryNpcEntity && entity.group == group) {
                    entity.discard()
                    count++
                }
            }
        }
        return count
    }

    // --- the developer's ---------------------------------------------------------------------------

    /** A person with [skin] [AHEAD] blocks in front of [player], facing them; null if there is no room. */
    fun spawnInFront(player: ServerPlayerEntity, skin: String): FactoryNpcEntity? {
        val world = player.serverWorld
        val ahead = Vec3d.fromPolar(0f, player.yaw)
        val x = player.x + ahead.x * AHEAD
        val z = player.z + ahead.z * AHEAD
        val y = player.y
        if (!world.isSpaceEmpty(Ch2Entities.FACTORY_NPC.dimensions.getBoxAt(x, y, z))) return null
        val facing = player.yaw + 180f
        return KeptEntities.spawn(world) { w ->
            Ch2Entities.FACTORY_NPC.create(w)?.apply {
                refreshPositionAndAngles(x, y, z, facing, 0f)
                headYaw = facing
                bodyYaw = facing
                this.skin = skin
            }
        }
    }

    /** Every one of them, gone; how many there were. */
    fun clear(server: MinecraftServer): Int {
        KeptEntities.forget { it is FactoryNpcEntity }
        var count = 0
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) {
                if (entity is FactoryNpcEntity) {
                    entity.discard()
                    count++
                }
            }
        }
        return count
    }
}
