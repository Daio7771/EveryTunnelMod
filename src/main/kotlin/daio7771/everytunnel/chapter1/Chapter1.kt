package daio7771.everytunnel.chapter1

import daio7771.everytunnel.chapter1.level2.platform.KeycardHooks
import daio7771.everytunnel.chapter1.level2.platform.PaleManSpawner
import daio7771.everytunnel.chapter1.level3.platform.BookHooks
import daio7771.everytunnel.chapter1.level3.platform.BookPhaseHooks
import daio7771.everytunnel.chapter1.level3.platform.ObserverHooks
import daio7771.everytunnel.chapter1.level3.platform.ValveHooks
import daio7771.everytunnel.chapter1.level3.platform.WaterDispenserHooks
import daio7771.everytunnel.chapter1.level4.platform.DartHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungeClosingHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungeHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungeMusicHooks
import daio7771.everytunnel.chapter1.level4.platform.LoungePuzzleHooks
import daio7771.everytunnel.chapter1.level4.platform.SafeHooks
import daio7771.everytunnel.chapter1.level4.platform.SuiteBedHooks
import daio7771.everytunnel.chapter1.level5.platform.ChemicalHooks
import daio7771.everytunnel.chapter1.level5.platform.CloudGameHooks
import daio7771.everytunnel.chapter1.level5.platform.DeepWaterHooks
import daio7771.everytunnel.chapter1.level5.platform.DirectorLoopHooks
import daio7771.everytunnel.chapter1.level5.platform.DirectorSceneHooks
import daio7771.everytunnel.chapter1.level5.platform.DrainHooks
import daio7771.everytunnel.chapter1.level5.platform.EveryFinalHooks
import daio7771.everytunnel.chapter1.level5.platform.FalseExitHooks
import daio7771.everytunnel.chapter1.level5.platform.LabDoorHooks
import daio7771.everytunnel.chapter1.level5.platform.LabFigureHooks
import daio7771.everytunnel.chapter1.level5.platform.LabHallHooks
import daio7771.everytunnel.chapter1.level5.platform.LabHooks
import daio7771.everytunnel.chapter1.level5.platform.SlidingDoors
import daio7771.everytunnel.chapter1.platform.CarryHooks
import daio7771.everytunnel.chapter1.platform.Ch1Blocks
import daio7771.everytunnel.chapter1.platform.Ch1Entities
import daio7771.everytunnel.chapter1.platform.Ch1Items
import daio7771.everytunnel.chapter1.platform.EndingHooks
import daio7771.everytunnel.common.platform.HidingHooks
import daio7771.everytunnel.chapter1.platform.PathingRules
import daio7771.everytunnel.chapter1.platform.RunStatsHooks
import daio7771.everytunnel.chapter1.platform.WhisperHooks
import daio7771.everytunnel.chapter1.level2.platform.PaleManEntity
import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.common.core.achievement.Achievement
import daio7771.everytunnel.common.platform.AchievementHooks
import daio7771.everytunnel.common.platform.PlaytestLog
import net.fabricmc.api.ModInitializer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import daio7771.everytunnel.common.core.level.LevelDefinition
import daio7771.everytunnel.common.platform.DeathHooks
import daio7771.everytunnel.chapter1.level3.platform.BoxHooks
import daio7771.everytunnel.common.platform.NewGame
import daio7771.everytunnel.chapter1.platform.Ch1Continue
import daio7771.everytunnel.chapter1.platform.Ch1Commands

/**
 * Chapter 1's common (server + client) entrypoint: its items, blocks and entities, and the hooks of its levels (the parking garage,
 * the offices, the lounge and the laboratory). It runs after [EveryTunnel] (`fabric.mod.json`), which holds what every chapter
 * shares and never names a chapter (`ChapterBoundaryTest`), and before Chapter 2's. Chapter 1 reaches the shared code through its
 * extension points, like any later chapter.
 */
object Chapter1 : ModInitializer {

    override fun onInitialize() {
        Ch1Items.register()
        Ch1Blocks.register()
        Ch1Entities.register()
        RunStatsHooks.register()
        PathingRules.register()
        PaleManSpawner.register()
        ObserverHooks.register()
        KeycardHooks.register()
        ValveHooks.register()
        WhisperHooks.register()
        HidingHooks.register()
        CarryHooks.register()
        BookHooks.register()
        BookPhaseHooks.register()
        WaterDispenserHooks.register()
        LoungeHooks.register()
        LabHooks.register()
        LabDoorHooks.register()
        SlidingDoors.register()
        CloudGameHooks.register()
        LabFigureHooks.register()
        ChemicalHooks.register()
        DrainHooks.register()
        DeepWaterHooks.register()
        DirectorSceneHooks.register()
        DirectorLoopHooks.register()
        EveryFinalHooks.register()
        FalseExitHooks.register()
        LabHallHooks.register()
        LoungePuzzleHooks.register()
        DartHooks.register()
        LoungeClosingHooks.register()
        SafeHooks.register()
        LoungeMusicHooks.register()
        SuiteBedHooks.register()
        EndingHooks.register()
        AchievementHooks.addDeathRule { if (it.attacker is PaleManEntity) Achievement.OUCH else null }
        PlaytestLog.describers += { if (it is PaleManEntity) "Pale man ${it.id} (${it.homeKey ?: "no home"})" else null }
        PlaytestLog.causeRules += { if (it.attacker is PaleManEntity) "Pale man" else null }
        PlaytestLog.levelNotes += { level: LevelDefinition -> "${level.spawnPoints.count { PaleManSpawner.TAG in it.tags }} Pale men" }
        DeathHooks.listeners += object : DeathHooks.Listener {
            override fun died(player: ServerPlayerEntity) = CarryHooks.died(player)

            override fun backTo() = LoungeClosingHooks.keyTable()

            override fun tookBack(player: ServerPlayerEntity) {
                LoungeClosingHooks.takeBack(player)
                CloudGameHooks.died(player)
            }

            override fun restarted(player: ServerPlayerEntity, world: ServerWorld, level: LevelDefinition, levelId: String) {
                for (paleMan in world.getEntitiesByType(Ch1Entities.PALE_MAN) { it.isAlive && it.homeKey?.startsWith("$levelId/") == true }) {
                    val point = level.spawnPoint(paleMan.homeKey!!.substringAfter('/')) ?: continue
                    paleMan.returnHome(point.position.x, point.position.y, point.position.z, point.yaw)
                }
                ObserverHooks.sendHome(world, levelId)
                DrainHooks.restart(player, levelId)
            }
        }
        // Putting the world back for a new game (the phases keep the order of the steps among the shared ones: see `NewGame`).
        NewGame.addResetStep(20) { CarryHooks.forgetAll() }
        NewGame.addResetStep(30, BoxHooks::newGame)
        NewGame.addResetStep(40, BookHooks::newGame)
        NewGame.addResetStep(60, LoungeHooks::newGame)
        NewGame.addResetStep(70) { LabHooks.newGame() }
        NewGame.addResetStep(100, KeycardHooks::restoreLights)
        NewGame.addResetStep(101, KeycardHooks::resetReaders)
        NewGame.addResetStep(110, ValveHooks::newGame)
        NewGame.addResetStep(120, PaleManSpawner::startNewGame)
        // ... and after the levels were rebuilt.
        NewGame.addRebuiltStep(20, KeycardHooks::forgetLights)
        NewGame.addRebuiltStep(30) { ValveHooks.levelsRebuilt() }
        NewGame.addRebuiltStep(40) { LoungeHooks.levelsRebuilt() }
        NewGame.addRebuiltStep(50) { LabHooks.levelsRebuilt() }
        NewGame.addRebuiltStep(60) { CarryHooks.forgetAll() }
        NewGame.addRebuiltStep(90, PaleManSpawner::startNewGame)
        NewGame.addRebuiltStep(100, ObserverHooks::levelsRebuilt)
        Ch1Continue.register()
        Ch1Commands.register()
        EveryTunnel.LOGGER.info("Chapter 1 loaded")
    }
}
