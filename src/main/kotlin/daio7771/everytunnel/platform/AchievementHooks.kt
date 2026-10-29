package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.achievement.Achievement
import daio7771.everytunnel.world.LevelTriggers
import daio7771.everytunnel.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.entity.damage.DamageSource
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import java.util.UUID

/**
 * Earning the achievements ([Achievement]): the server tells the client, which shows the
 * "Achievement Unlocked!" toast and remembers it (`client.hud.AchievementToast`,
 * `client.AchievementStore`). Each one is told **once per game**: dying to a Pale man a second
 * time shows nothing; a new game ([newGame]) lets them all come again. The vanilla
 * advancements are not touched. Only in worlds with levels.
 *
 * - First Day at Work: walking out of the start zone (tagged `start`, [IntroHooks.START_TAG])
 *   of a level with an intro: out of the elevator ([register], from [LevelTriggers]).
 * - Stay Hydrated: a water bottle drunk ([DrinkHooks]).
 * - Beware of Tall Men: walking into a level listed in [LEVEL_ENTRIES] ([LevelEntryHooks]).
 * - Ouch!: killed by a Pale man ([DeathHooks]).
 * - The puzzles, for every player in the world ([puzzleSolved]): the keycard in the reader
 *   ([CardReaderBlock]), the valves ([ValveHooks]); the boxes and the books to come.
 */
object AchievementHooks {

    /** To the client: an achievement earned. Payload: its id ([Achievement.id]). */
    val PACKET_ID = Identifier(EveryTunnel.MOD_ID, "achievement")

    /** Walking into these levels (by id, from a lower `order`) earns these achievements. */
    val LEVEL_ENTRIES = mapOf("level_2" to Achievement.TALL_MEN)

    private var server: MinecraftServer? = null

    // Server thread only. Player -> what they have earned in this game.
    private val earnedThisGame = mutableMapOf<UUID, MutableSet<Achievement>>()

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            earnedThisGame.clear()
        }
        LevelTriggers.register { change ->
            val player = server?.playerManager?.getPlayer(change.playerId) ?: return@register
            val leftElevator = change.exitedZones.any { key ->
                val level = LevelManager.get(key.levelId) ?: return@any false
                level.intro != null && level.zones.any { it.id == key.zoneId && IntroHooks.START_TAG in it.tags }
            }
            if (leftElevator && !LoadingHooks.isLoading(player) && !DeathHooks.isDying(player)) earned(player, Achievement.FIRST_DAY)
        }
    }

    /** [player] earned [achievement]: their toast, unless they already earned it in this game. */
    fun earned(player: ServerPlayerEntity, achievement: Achievement) {
        if (!WorldRules.active) return
        if (!earnedThisGame.getOrPut(player.uuid) { mutableSetOf() }.add(achievement)) return
        if (!ServerPlayNetworking.canSend(player, PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeString(achievement.id)
        ServerPlayNetworking.send(player, PACKET_ID, buf)
        EveryTunnel.LOGGER.info("{} earned the achievement {}", player.name.string, achievement.id)
    }

    /** What every player has earned in this game (UUID -> achievement ids), for the save ([SaveGameHooks]). */
    fun earnedIds(): Map<String, Set<String>> =
        earnedThisGame.entries.associate { (id, earned) -> id.toString() to earned.map { it.id }.toSet() }

    /** A saved game goes on: what was earned in it is not told again. */
    fun restoreEarned(saved: Map<String, Set<String>>) {
        earnedThisGame.clear()
        for ((id, ids) in saved) {
            val uuid = runCatching { UUID.fromString(id) }.getOrNull() ?: continue
            earnedThisGame[uuid] = ids.mapNotNull { key -> Achievement.entries.firstOrNull { it.id == key } }.toMutableSet()
        }
    }

    /** A new game for [player]: every achievement can be earned (and told) again. */
    fun newGame(player: ServerPlayerEntity) {
        earnedThisGame.remove(player.uuid)
    }

    /** A puzzle was solved in [world]: every player there earns it. */
    fun puzzleSolved(world: ServerWorld, achievement: Achievement) {
        for (player in world.players) earned(player, achievement)
    }

    /** [player] walked into the level [levelId] from an earlier one. */
    fun levelEntered(player: ServerPlayerEntity, levelId: String) {
        LEVEL_ENTRIES[levelId]?.let { earned(player, it) }
    }

    /** [player] would have died of [source]: a Pale man's doing? */
    fun died(player: ServerPlayerEntity, source: DamageSource) {
        if (source.attacker is PaleManEntity) earned(player, Achievement.OUCH)
    }
}
