package daio7771.everytunnel.chapter1.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.LevelManager
import daio7771.everytunnel.common.core.level.LevelDefinition
import daio7771.everytunnel.common.core.progress.Checkpoint
import daio7771.everytunnel.common.core.progress.LeftBehind
import daio7771.everytunnel.chapter1.level4.core.LoungeSave
import daio7771.everytunnel.common.systems.ResourceSystem
import daio7771.everytunnel.common.world.LevelTriggers
import daio7771.everytunnel.common.world.WorldRules
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.WorldSavePath
import net.minecraft.util.math.BlockPos
import net.minecraft.world.GameMode
import java.nio.file.Files
import java.nio.file.Path
import daio7771.everytunnel.chapter1.level4.platform.BreakerHooks
import daio7771.everytunnel.chapter1.level4.platform.DartHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungeClosingHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungeHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungePuzzleHooks
import daio7771.everytunnel.chapter1.level4.platform.SafeHooks
import daio7771.everytunnel.chapter1.level5.platform.LabHooks
import daio7771.everytunnel.common.platform.ContinueGame
import daio7771.everytunnel.common.platform.LootSpawner
import daio7771.everytunnel.common.platform.ProgressService

/**
 * A game continued in Chapter 1's Level 4 or 5 (the levels built in play): their checkpoint builds them again behind the loading
 * screen ([ContinueGame.BuiltInPlay]; the shared CONTINUE is `ContinueGame`'s), Level 4 gets its puzzles' state back ([LoungeSave], written
 * when the player leaves the world) and Level 5 its start or the Lab hall.
 */
object Ch1Continue : ContinueGame.BuiltInPlay {

    private const val LEVEL_4 = "level_4"
    private const val LEVEL_5 = "level_5"

    fun register() {
        ContinueGame.builtInPlay += this
    }

    override fun resume(player: ServerPlayerEntity, level: LevelDefinition): Boolean {
        val server = player.server
        if (level.id == LEVEL_4) {
            LoungeHooks.toCorridor(player)
            ContinueGame.wait(player, LEVEL_4)
            if (!LoungeHooks.beginAgain(server)) loungeBuilt(server)
            return true
        }
        if (level.id == LEVEL_5) {
            LoungeHooks.toCorridor(player)
            ContinueGame.wait(player, LEVEL_5)
            if (!LabHooks.beginAgain(server)) labBuilt(server)
            return true
        }
        return false
    }

    /** Level 4 stands again ([LoungeHooks]). */
    fun loungeBuilt(server: MinecraftServer) {
        val player = ContinueGame.finishWaiting(LEVEL_4) ?: return
        val level = LevelManager.get(LEVEL_4) ?: return
        val world = ContinueGame.worldOf(server, level) ?: return
        val save = LoungeSave.fromText(ProgressService.get().lounge(player))
        if (save != null) {
            LoungePuzzleHooks.restore(save, server, player)
            DartHooks.restore(save, world, level, player)
            BreakerHooks.restore(save, world, level)
            SafeHooks.restore(save, world)
            LootSpawner.restoreTaken(save.lootTaken, world, level)
        }
        LoungeClosingHooks.undoKey(player)
        ContinueGame.toStart(player, level)
        EveryTunnel.LOGGER.info("{}: Level 4 is back as it was", player.name.string)
    }

    /** Level 5 stands again ([LabHooks]): the player goes to its start, or the Lab hall, with what they carried then. */
    fun labBuilt(server: MinecraftServer) {
        val player = ContinueGame.finishWaiting(LEVEL_5) ?: return
        val level = LevelManager.get(LEVEL_5) ?: return
        val progress = ProgressService.get()
        progress.restoreInventory(player)
        ContinueGame.leaveBehind(player, level)
        ContinueGame.toStart(player, level, Checkpoint.parse(progress.checkpoint(player))?.pointId)
        EveryTunnel.LOGGER.info("{}: Level 5 stands again", player.name.string)
    }

    override fun disconnected(player: ServerPlayerEntity) = writeLounge(player)

    private fun writeLounge(player: ServerPlayerEntity) {
        val progress = ProgressService.get()
        if (Checkpoint.parse(progress.checkpoint(player))?.levelId != LEVEL_4 || ContinueGame.waitingForBuild) return
        val level = LevelManager.get(LEVEL_4) ?: return
        if (LoungeHooks.notBuiltYet(level)) return
        val world = ContinueGame.worldOf(player.server, level) ?: return
        val save = LoungeSave()
        LoungePuzzleHooks.save(save)
        DartHooks.save(save)
        BreakerHooks.save(save, world, level)
        SafeHooks.save(save, world)
        save.lootTaken = LootSpawner.taken.toList()
        progress.setLounge(player, save.toText())
    }
}
