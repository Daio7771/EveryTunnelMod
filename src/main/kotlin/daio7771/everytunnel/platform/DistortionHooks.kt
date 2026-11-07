package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.config.ConfigManager
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.level.Position
import daio7771.everytunnel.entities.CloudSchedule
import daio7771.everytunnel.entities.Gaze
import daio7771.everytunnel.entities.HunterBrain
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.entity.Entity
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.Identifier
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.RaycastContext
import java.util.UUID
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Level 5's Hunters ([HunterEntity]) and Distortions ([DistortionCloudEntity], [DistortionJuniorEntity]):
 *
 * - **Posts:** each spawn point tagged [HUNTERS_TAG] holds a group of three Hunters (harmless ones if it is
 *   tagged [HARMLESS_TAG] too), each tagged
 *   [JUNIOR_TAG] a Junior. When the level is built ([levelBuilt], from [LabHooks]) they are put there;
 *   a player dying in it puts every one back, the dead too, and sends any Cloud away ([restart], from
 *   [DeathHooks]); a new game, or the levels rebuilt, clears them all ([clearAll]). None is saved. What
 *   the debug command `/everytunnel spawn` puts in ([spawnForDebug]) holds no post: a death in its level
 *   clears it, and it does not come back. Each one put in is logged.
 * - **Telling:** a Hunter that sees a player tells its group ([sighted]); a Junior marks the players it
 *   troubles ([mark]). Hunters ask both ([toldOf]): for a while after, they know where that player is,
 *   however far.
 * - **The Cloud:** a player in a zone tagged [CLOUD_TAG], with no Cloud about, gets one after a random
 *   wait ([CloudSchedule], `cloud.min_seconds` to `cloud.max_seconds` of time spent in such zones): it
 *   appears in front of them, between `cloud.nearest` and `cloud.farthest` blocks ahead, on the floor,
 *   in a Cloud zone, where they can see it ([appear], which the level's scripted ones call too).
 */
object DistortionHooks {

    /** Spawn points holding a group of three Hunters. */
    const val HUNTERS_TAG = "hunters"
    /** With [HUNTERS_TAG]: Hunters that do no harm (layer 1's, the player's first with the wrench). */
    const val HARMLESS_TAG = "harmless"
    /** Spawn points holding a Distortion Junior. */
    const val JUNIOR_TAG = "distortion_junior"
    /** Zones where Clouds come. */
    const val CLOUD_TAG = "cloud"

    private enum class Kind { HUNTERS, JUNIOR }

    private data class Post(
        val kind: Kind,
        val levelId: String?,
        val world: RegistryKey<net.minecraft.world.World>,
        val position: Position,
        val yaw: Float,
        val group: String,
        val harmless: Boolean = false,
    )

    private data class Sighting(val player: UUID, val position: Position, val tick: Long)

    // Server thread only.
    private var server: MinecraftServer? = null
    private val posts = mutableListOf<Post>()
    private val sightings = HashMap<String, Sighting>()
    private val marks = HashMap<UUID, Long>()
    private val schedules = HashMap<UUID, CloudSchedule>()
    private val clouds = mutableSetOf<DistortionCloudEntity>()
    private var debugSpawns = 0

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            posts.clear()
            forget()
        }
        ServerTickEvents.END_SERVER_TICK.register(::tick)
    }

    // --- posts ---------------------------------------------------------------------------

    /** [level] has just been built: its Hunters and Juniors take their posts, anew. */
    fun levelBuilt(server: MinecraftServer, level: LevelDefinition) {
        val world = worldOf(server, level) ?: return
        discard(world, level.id)
        posts.removeAll { it.levelId == level.id }
        for (point in level.spawnPoints) {
            val kind = when {
                HUNTERS_TAG in point.tags -> Kind.HUNTERS
                JUNIOR_TAG in point.tags -> Kind.JUNIOR
                else -> continue
            }
            posts += Post(kind, level.id, world.registryKey, point.position, point.yaw, "${level.id}/${point.id}", HARMLESS_TAG in point.tags)
        }
        spawnPosts(world, level.id)
    }

    /** A player died in [levelId]: every Hunter and Junior back at its post, the dead too; no Cloud about, nor any put in by hand. */
    fun restart(world: ServerWorld, levelId: String) {
        discard(world, levelId)
        for (cloud in clouds.toList()) if (cloud.world == world) cloud.discard()
        sightings.clear()
        marks.clear()
        for (schedule in schedules.values) schedule.appeared()
        spawnPosts(world, levelId)
    }

    /** A new game, or the levels rebuilt: none of them anywhere until the level is built again. */
    fun clearAll() {
        val server = server ?: return
        for (world in server.worlds) {
            for (entity in world.iterateEntities().toList()) {
                if (entity is HunterEntity || entity is DistortionEntity) entity.discard()
            }
        }
        posts.clear()
        forget()
    }

    private fun forget() {
        sightings.clear()
        marks.clear()
        schedules.clear()
        clouds.clear()
    }

    private fun spawnPosts(world: ServerWorld, levelId: String?) {
        for (post in posts) {
            if (post.levelId != levelId || post.world != world.registryKey) continue
            spawn(world, post)
        }
    }

    private fun spawn(world: ServerWorld, post: Post) {
        EveryTunnel.LOGGER.info("{} put in at ({}, {}, {}), {}", if (post.kind == Kind.HUNTERS) "Three Hunters" else "A Distortion Junior",
            floor(post.position.x).toInt(), floor(post.position.y).toInt(), floor(post.position.z).toInt(), post.group)
        when (post.kind) {
            Kind.HUNTERS -> {
                val yaw = Math.toRadians(post.yaw.toDouble())
                for ((side, back) in GROUP_PLACES) {
                    // Side by side across the way it faces, two a step behind the first.
                    val px = post.position.x + cos(yaw) * side + sin(yaw) * back
                    val pz = post.position.z + sin(yaw) * side - cos(yaw) * back
                    val feet = floorNear(world, px, post.position.y, pz) ?: post.position.y
                    val hunter = ModEntities.HUNTER.create(world) ?: continue
                    hunter.refreshPositionAndAngles(px, feet, pz, post.yaw, 0f)
                    hunter.headYaw = post.yaw
                    hunter.home = Position(px, feet, pz)
                    hunter.group = post.group
                    hunter.levelId = post.levelId
                    hunter.harmless = post.harmless
                    world.spawnEntity(hunter)
                }
            }
            Kind.JUNIOR -> {
                val junior = ModEntities.DISTORTION_JUNIOR.create(world) ?: return
                junior.refreshPositionAndAngles(post.position.x, post.position.y, post.position.z, post.yaw, 0f)
                junior.levelId = post.levelId
                world.spawnEntity(junior)
            }
        }
    }

    /** The Hunters and Distortions of [levelId] (null: the debug ones of no level) in [world], gone. */
    private fun discard(world: ServerWorld, levelId: String?) {
        for (entity in world.iterateEntities().toList()) {
            val of = when (entity) {
                is HunterEntity -> entity.levelId
                is DistortionEntity -> entity.levelId
                else -> continue
            }
            if (of == levelId) entity.discard()
        }
    }

    // --- telling ---------------------------------------------------------------------------

    /** A Hunter of [group] sees [seen]: the group knows, for a while. */
    fun sighted(group: String, seen: HunterBrain.Contact, now: Long) {
        sightings[group] = Sighting(seen.id, seen.position, now)
    }

    /** A Junior troubles [player]: the Hunters know where they are, for a while. */
    fun mark(player: ServerPlayerEntity, now: Long) {
        marks[player.uuid] = now
    }

    /** The player [hunter] is told of now: one its group saw lately, or one a Junior marks; or null. */
    fun toldOf(hunter: HunterEntity, now: Long): HunterBrain.Contact? {
        val world = hunter.world as? ServerWorld ?: return null
        var best: HunterBrain.Contact? = null
        fun consider(player: ServerPlayerEntity?) {
            if (player == null || player.world != world || DeathHooks.isDying(player) || !ResourceHooks.appliesTo(player)) return
            val distance = sqrt(hunter.squaredDistanceTo(player))
            if (distance > TOLD_RANGE) return
            if (best == null || distance < best!!.distance) best = HunterBrain.Contact(player.uuid, Position(player.x, player.y, player.z), distance)
        }
        sightings[hunter.group]?.takeIf { now - it.tick <= SIGHTING_TICKS }?.let { consider(world.getPlayerByUuid(it.player) as? ServerPlayerEntity) }
        for ((id, tick) in marks) if (now - tick <= MARK_TICKS) consider(world.getPlayerByUuid(id) as? ServerPlayerEntity)
        return best
    }

    // --- the Cloud ---------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        if (server.ticks % SCHEDULE_TICKS != 0) return
        val config = ConfigManager.gameplay.cloud
        for (player in server.playerManager.playerList) {
            if (!ResourceHooks.appliesTo(player) || DeathHooks.isDying(player)) continue
            val world = player.serverWorld
            val inZone = LevelManager.zonesAt(world.registryKey.value.toString(), player.x, player.y + 0.5, player.z)
                .any { CLOUD_TAG in it.zone.tags }
            val about = clouds.any { !it.isRemoved && it.world == world && it.squaredDistanceTo(player) < ABOUT_RANGE * ABOUT_RANGE }
            val schedule = schedules.getOrPut(player.uuid) { CloudSchedule.of(config.minSeconds, config.maxSeconds) { Math.random() } }
            if (schedule.tick(inZone, about, SCHEDULE_TICKS) && appear(player)) schedule.appeared()
        }
    }

    /**
     * A Cloud in front of [player], now: [CloudConfig.nearest][daio7771.everytunnel.config.CloudConfig.nearest]
     * to `farthest` blocks ahead (straight ahead first, then a little to either side), standing on the
     * floor, where they can see it, in a zone tagged [CLOUD_TAG] unless [anywhere]. False if there is no
     * such place.
     */
    fun appear(player: ServerPlayerEntity, anywhere: Boolean = false): Boolean {
        val world = player.serverWorld
        val config = ConfigManager.gameplay.cloud
        val dimension = world.registryKey.value.toString()
        val near = minOf(config.nearest, config.farthest)
        val far = maxOf(config.nearest, config.farthest)
        val middle = (near + far) / 2
        val distances = generateSequence(near) { it + 1 }.takeWhile { it <= far }.sortedBy { kotlin.math.abs(it - middle) }.toList()
        for (offset in SIDE_OFFSETS) {
            val yaw = Math.toRadians(player.yaw.toDouble() + offset)
            for (distance in distances) {
                val tx = player.x - sin(yaw) * distance
                val tz = player.z + cos(yaw) * distance
                val feet = floorNear(world, tx, player.y, tz) ?: continue
                if (!anywhere && LevelManager.zonesAt(dimension, tx, feet + 0.5, tz).none { CLOUD_TAG in it.zone.tags }) continue
                if (!clear(world, player.eyePos, Vec3d(tx, feet + 1.6, tz), player)) continue
                val cloud = ModEntities.DISTORTION_CLOUD.create(world) ?: return false
                val facing = Gaze.yawTowards(Position(tx, feet, tz), Position(player.x, player.y, player.z)).toFloat()
                cloud.refreshPositionAndAngles(tx, feet, tz, facing, 0f)
                cloud.levelId = LevelManager.levelAt(dimension, tx, feet + 0.5, tz)?.id
                if (!world.spawnEntity(cloud)) return false
                clouds += cloud
                world.playSound(null, tx, feet + 1.2, tz, ModSounds.CLOUD_APPEAR, SoundCategory.HOSTILE, 1.5f, 1f)
                EveryTunnel.LOGGER.info("A Distortion Cloud appears {} blocks in front of {} at ({}, {}, {})",
                    "%.0f".format(distance), player.name.string, floor(tx).toInt(), feet.toInt(), floor(tz).toInt())
                return true
            }
        }
        return false
    }

    /** [cloud] is gone (it left, or was discarded). */
    fun cloudGone(cloud: DistortionCloudEntity) {
        clouds -= cloud
    }

    // --- debug ---------------------------------------------------------------------------

    /**
     * `/everytunnel spawn hunters|junior|cloud`: a group of Hunters or a Junior a few blocks in front of
     * [player], once (no post: a death in the level they stand in clears them, for good); or a Cloud in
     * front of them, wherever they are. False if there was no room.
     */
    fun spawnForDebug(player: ServerPlayerEntity, what: String): Boolean {
        val world = player.serverWorld
        if (what == "cloud") return appear(player, anywhere = true)
        val kind = if (what == "hunters") Kind.HUNTERS else Kind.JUNIOR
        val yaw = Math.toRadians(player.yaw.toDouble())
        val distance = if (kind == Kind.HUNTERS) 4.0 else 5.0
        val tx = player.x - sin(yaw) * distance
        val tz = player.z + cos(yaw) * distance
        val feet = floorNear(world, tx, player.y, tz) ?: return false
        val levelId = LevelManager.levelAt(world.registryKey.value.toString(), tx, feet + 0.5, tz)?.id
        val facing = Gaze.yawTowards(Position(tx, feet, tz), Position(player.x, player.y, player.z)).toFloat()
        spawn(world, Post(kind, levelId, world.registryKey, Position(tx, feet, tz), facing, "$DEBUG_GROUP${++debugSpawns}"))
        return true
    }

    // --- helpers -----------------------------------------------------------------------

    /** The height of a floor near ([x], [y], [z]) with two free blocks over it, or null. */
    private fun floorNear(world: ServerWorld, x: Double, y: Double, z: Double): Double? {
        val base = BlockPos.ofFloored(x, y, z)
        for (dy in FLOOR_SEARCH) {
            val feet = base.up(dy)
            if (free(world, feet) && free(world, feet.up()) && !free(world, feet.down())) return feet.y.toDouble()
        }
        return null
    }

    private fun free(world: ServerWorld, pos: BlockPos): Boolean = world.getBlockState(pos).getCollisionShape(world, pos).isEmpty

    private fun clear(world: ServerWorld, from: Vec3d, to: Vec3d, entity: Entity): Boolean =
        world.raycast(RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, entity)).type == HitResult.Type.MISS

    private fun worldOf(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    private const val DEBUG_GROUP = "debug/"
    /** A group round its spawn point: (blocks to the side, blocks behind). */
    private val GROUP_PLACES = listOf(0.0 to 0.0, -1.2 to 1.0, 1.2 to 1.0)
    /** Heights from a point where a floor is looked for, the nearest first. */
    private val FLOOR_SEARCH = listOf(0, -1, 1, -2, 2, -3)
    /** Degrees off straight ahead tried for a Cloud's place, in this order. */
    private val SIDE_OFFSETS = listOf(0.0, 12.0, -12.0, 25.0, -25.0)
    /** How long, in ticks, a group's sighting and a Junior's mark are worth. */
    private const val SIGHTING_TICKS = 10L
    private const val MARK_TICKS = 10L
    /** Hunters farther than this from a player are never told of them. */
    private const val TOLD_RANGE = 96.0
    /** A Cloud nearer than this to a player is about: no other comes. */
    private const val ABOUT_RANGE = 64.0
    /** The Cloud schedule is looked at once a second. */
    private const val SCHEDULE_TICKS = 20
}
