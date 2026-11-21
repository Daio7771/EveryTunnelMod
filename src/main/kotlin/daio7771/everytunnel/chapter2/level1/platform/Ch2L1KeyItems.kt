package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Act3
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Bits
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Tasks
import daio7771.everytunnel.chapter2.platform.Ch2Items
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.common.platform.TickProfiler
import net.minecraft.entity.EntityType
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity

/**
 * The level's key items can never be lost: the broom (task 3, the basement), the reagent bottles, the badge's copy and Rowan's pass. A
 * player can throw one away (Q) or have it fall out of a full inventory, and the step it is for could not be done any more. So, now and
 * then ([CHECK_TICKS]), any of them lying on the ground in the level is taken away, and a player who should hold one by their state and
 * counters and does not (in their inventory or held on the cursor) gets it back, as many as they should have. Server thread only.
 */
object Ch2L1KeyItems {

    private const val CHECK_TICKS = 20

    private val keyItems: Set<Item> get() = setOf(Ch2Items.BROOM, Ch2Items.LAB_BADGE_COPY, Ch2Items.OVERTIME_PASS) + Ch2Items.REAGENTS.values

    fun register() {
        TickProfiler.endServerTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        if (server.ticks % CHECK_TICKS != 0) return
        val players = server.playerManager.playerList.filter { Ch2L1.active(it) }
        if (players.isEmpty()) return
        val world = Ch2L1Npcs.world(server) ?: return
        val items = keyItems
        world.getEntitiesByType(EntityType.ITEM, Ch2L1Npcs.levelBox()) { it.stack.item in items }.forEach { it.discard() }
        for (player in players) restore(player)
    }

    /** What [player] should be holding now: item and how many. */
    private fun owed(player: ServerPlayerEntity): Map<Item, Int> {
        val state = Ch2L1.state(player)
        fun counter(key: String) = Ch2Progress.ch2L1Counter(player, key)
        val out = HashMap<Item, Int>()
        if (state == Ch2L1State.ACT1_TASK3 && counter(Ch2L1Tasks.BROOM) != 0) out[Ch2Items.BROOM] = 1
        if (state == Ch2L1State.ACT3_BASEMENT && counter(Ch2L1Tasks.BASEMENT_BROOM) != 0) out[Ch2Items.BROOM] = 1
        if (state == Ch2L1State.ACT1_REAGENTS) {
            val taken = counter(Ch2L1Tasks.REAGENTS_TAKEN)
            val poured = counter(Ch2L1Tasks.REAGENTS_POURED)
            Ch2L1DataLoader.tasks.lab.bottles.forEachIndexed { i, bottle ->
                if (Ch2L1Bits.has(taken, i) && !Ch2L1Bits.has(poured, i)) Ch2Items.REAGENTS[bottle.colour]?.let { out[it] = (out[it] ?: 0) + 1 }
            }
        }
        if (state == Ch2L1State.ACT3_EXPLORE && counter(Ch2L1Act3.LAB_OPEN) == 0) out[Ch2Items.LAB_BADGE_COPY] = 1
        if (state == Ch2L1State.ACT3_OVERTIME && counter(Ch2L1Act3.SEALED_OPEN) == 0) out[Ch2Items.OVERTIME_PASS] = 1
        return out
    }

    private fun restore(player: ServerPlayerEntity) {
        if (player.isSpectator) return
        val cursor = player.currentScreenHandler.cursorStack
        for ((item, count) in owed(player)) {
            val held = player.inventory.count(item) + (if (cursor.isOf(item)) cursor.count else 0)
            if (held >= count) continue
            repeat(count - held) { player.inventory.insertStack(ItemStack(item)) }
            EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} gets back {} lost {}", player.gameProfile.name, count - held, item)
        }
    }
}
