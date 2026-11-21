package daio7771.everytunnel.common.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.achievement.Achievement
import daio7771.everytunnel.common.core.achievement.AchievementDef
import daio7771.everytunnel.common.world.LevelTriggers
import daio7771.everytunnel.common.world.WorldRules
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
 * "Achievement Unlocked!" toast the first time and remembers it for good (`common.client.hud.AchievementToast`,
 * `common.client.AchievementStore`, which Ctrl+Shift+D in the ACHIEVEMENTS screen resets). Each one is told
 * once per game at most. The vanilla advancements are not touched. Only in worlds with levels. A later chapter's achievements
 * ([daio7771.everytunnel.common.common.core.achievement.AchievementRegistry]) are earned through the same two calls, [earned] and [puzzleSolved].
 *
 * - First Day at Work: walking out of the start zone (tagged `start`, [IntroHooks.START_TAG])
 *   of a level with an intro: out of the elevator ([register], from [LevelTriggers]).
 * - Stay Hydrated: a water bottle drunk ([DrinkHooks]).
 * - Beware of Tall Men: walking into a level listed in [LEVEL_ENTRIES] ([LevelEntryHooks]).
 * - Ouch!: killed by a Pale man ([DeathHooks]).
 * - Big Brain: the first piece of Level 4's master card ([LoungePuzzleHooks.pieceTaken]).
 * - Privacy Policy: a safe opened in the Suites ([SafeHooks]).
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
    private val earnedThisGame = mutableMapOf<UUID, MutableSet<String>>()

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

    /** [player] earned [achievement] (Chapter 1's or any chapter's registered one): their toast, unless they already earned it in this game. */
    fun earned(player: ServerPlayerEntity, achievement: AchievementDef) {
        if (!WorldRules.active) return
        if (!earnedThisGame.getOrPut(player.uuid) { mutableSetOf() }.add(achievement.id)) return
        if (!ServerPlayNetworking.canSend(player, PACKET_ID)) return
        val buf = PacketByteBufs.create()
        buf.writeString(achievement.id)
        ServerPlayNetworking.send(player, PACKET_ID, buf)
        EveryTunnel.LOGGER.info("{} earned the achievement {}", player.name.string, achievement.id)
    }

    /** A new game for [player]: every achievement can be earned (and told) again. */
    fun newGame(player: ServerPlayerEntity) {
        earnedThisGame.remove(player.uuid)
    }

    /** A puzzle was solved in [world]: every player there earns it. */
    fun puzzleSolved(world: ServerWorld, achievement: AchievementDef) {
        for (player in world.players) earned(player, achievement)
    }

    /** [player] walked into the level [levelId] from an earlier one. */
    fun levelEntered(player: ServerPlayerEntity, levelId: String) {
        LEVEL_ENTRIES[levelId]?.let { earned(player, it) }
    }

    /** What a death of [source] earns (a chapter's creatures: Chapter 1's Pale man gives [Achievement.OUCH]); null: nothing. */
    private val deathRules = java.util.concurrent.CopyOnWriteArrayList<(DamageSource) -> Achievement?>()

    fun addDeathRule(rule: (DamageSource) -> Achievement?) {
        deathRules += rule
    }

    /** [player] would have died of [source]: a creature's doing? */
    fun died(player: ServerPlayerEntity, source: DamageSource) {
        deathRules.firstNotNullOfOrNull { it(source) }?.let { earned(player, it) }
    }
}
