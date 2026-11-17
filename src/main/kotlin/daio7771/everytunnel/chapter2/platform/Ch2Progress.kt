package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.core.Ch2PlayerProgress
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.level2.core.Ch2L2Snapshot
import daio7771.everytunnel.chapter2.level2.core.Ch2L2State
import daio7771.everytunnel.chapter2.prologue.platform.PrologueSceneHooks
import daio7771.everytunnel.platform.ChapterRequest
import daio7771.everytunnel.platform.ProgressService
import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.util.Identifier

/**
 * Chapter 2's progress of each player ([Ch2PlayerProgress]) on Cardinal Components, a component of its own (`every_tunnel:ch2_progress`),
 * apart from Chapter 1's (`platform.CardinalProgress`): saved with the player, copied onto them when they respawn, and sent to their
 * client on every change. A Chapter 1 new game ([ProgressService.reset]) clears it too ([register]).
 *
 * Declared in `fabric.mod.json` (`custom`, "cardinal-components"); this object is a `cardinal-components-entity` entrypoint.
 */
object Ch2Progress : EntityComponentInitializer {

    private val KEY: ComponentKey<Component> =
        ComponentRegistry.getOrCreate(Identifier(EveryTunnel.MOD_ID, "ch2_progress"), Component::class.java)

    override fun registerEntityComponentFactories(registry: EntityComponentFactoryRegistry) {
        registry.registerForPlayers(KEY, { player: PlayerEntity -> Component(player) }, RespawnCopyStrategy.ALWAYS_COPY)
    }

    /** A Chapter 1 new game forgets Chapter 2's progress too, its saved game and the way back to Chapter 1 ([ch2Return]). */
    fun register() {
        ProgressService.onReset { player ->
            KEY.get(player).returnTo = null
            KEY.get(player).l2Inventory = null
            change(player) { it.clear() }
            (player as? ServerPlayerEntity)?.let { ChapterRequest.setSaved(it.server, PrologueSceneHooks.MENU_CHAPTER, false) }
        }
    }

    /** Where the player is in Chapter 2's Level 1 ([Ch2L1State.START] before they begin it). */
    fun ch2L1State(player: PlayerEntity): Ch2L1State = of(player).l1State

    fun setCh2L1State(player: PlayerEntity, state: Ch2L1State) = change(player) { it.l1State = state }

    /** The player is playing Chapter 2's Level 1, whose rules apply to them ([Ch2PlayerProgress.l1Active]). */
    fun ch2L1Active(player: PlayerEntity): Boolean = of(player).l1Active

    fun setCh2L1Active(player: PlayerEntity, active: Boolean) = change(player) { it.l1Active = active }

    /** A named whole number of the player's Chapter 2 Level 1 ([Ch2PlayerProgress.l1Counter]); 0 if it has none. */
    fun ch2L1Counter(player: PlayerEntity, key: String): Int = of(player).l1Counter(key)

    fun setCh2L1Counter(player: PlayerEntity, key: String, value: Int) = change(player) { it.setL1Counter(key, value) }

    /** Forgets the counters whose names start with [prefix] (every one if it is empty). */
    fun clearCh2L1Counters(player: PlayerEntity, prefix: String = "") = change(player) { it.clearL1Counters(prefix) }

    /** Where the player is in Chapter 2's Level 2 ([Ch2L2State.START] before they begin it). */
    fun ch2L2State(player: PlayerEntity): Ch2L2State = of(player).l2State

    fun setCh2L2State(player: PlayerEntity, state: Ch2L2State) = change(player) { it.l2State = state }

    /** The player is playing Chapter 2's Level 2, whose rules apply to them ([Ch2PlayerProgress.l2Active]). */
    fun ch2L2Active(player: PlayerEntity): Boolean = of(player).l2Active

    fun setCh2L2Active(player: PlayerEntity, active: Boolean) = change(player) { it.l2Active = active }

    /** A named whole number of the player's Chapter 2 Level 2; 0 if it has none. */
    fun ch2L2Counter(player: PlayerEntity, key: String): Int = of(player).l2Counter(key)

    fun setCh2L2Counter(player: PlayerEntity, key: String, value: Int) = change(player) { it.setL2Counter(key, value) }

    fun ch2L2Counters(player: PlayerEntity): Map<String, Int> = of(player).l2CounterMap

    fun replaceCh2L2Counters(player: PlayerEntity, counters: Map<String, Int>) = change(player) { it.replaceL2Counters(counters) }

    /** Level 2 at its last checkpoint ([Ch2L2Snapshot]), or null. */
    fun ch2L2Snapshot(player: PlayerEntity): Ch2L2Snapshot? = of(player).l2Snapshot

    /** What the player carried at Level 2's last checkpoint, as `PlayerInventory.writeNbt` wrote it; null with no snapshot. */
    fun ch2L2Inventory(player: PlayerEntity): NbtList? = KEY.get(player).l2Inventory

    /** Takes Level 2's snapshot, with what the player carries ([inventory]); null [snapshot] forgets both. */
    fun setCh2L2Snapshot(player: PlayerEntity, snapshot: Ch2L2Snapshot?, inventory: NbtList?) {
        KEY.get(player).l2Inventory = if (snapshot == null) null else inventory
        change(player) { it.l2Snapshot = snapshot }
    }

    /** Level 2 forgotten: state, counters, snapshot ([Ch2PlayerProgress.clearL2]). */
    fun clearCh2L2(player: PlayerEntity) {
        KEY.get(player).l2Inventory = null
        change(player) { it.clearL2() }
    }

    /** One line for the debug command. */
    fun describe(player: PlayerEntity): String = of(player).describe()

    /**
     * Where the player goes back to when they leave Chapter 2 after the menu's Chapter 2 (`Ch2Return`): their place and their
     * things in Chapter 1, as that object writes them; null when there is none.
     */
    fun ch2Return(player: PlayerEntity): NbtCompound? = KEY.get(player).returnTo

    fun setCh2Return(player: PlayerEntity, tag: NbtCompound?) {
        KEY.get(player).returnTo = tag
        if (!player.world.isClient) KEY.sync(player)
    }

    private fun of(player: PlayerEntity): Ch2PlayerProgress = KEY.get(player).progress

    /** Applies a change on the server and sends the result to the player's client. */
    private fun <T> change(player: PlayerEntity, action: (Ch2PlayerProgress) -> T): T {
        val result = action(of(player))
        if (!player.world.isClient) KEY.sync(player)
        return result
    }

    /** The component itself. Saved and synced as the same NBT. */
    class Component(private val owner: PlayerEntity) : AutoSyncedComponent {

        val progress = Ch2PlayerProgress()

        /** [Ch2Progress.ch2Return]: kept apart from [progress], which has no Minecraft types. */
        var returnTo: NbtCompound? = null

        /** [Ch2Progress.ch2L2Inventory], the same way. */
        var l2Inventory: NbtList? = null

        override fun shouldSyncWith(player: ServerPlayerEntity): Boolean = player === owner

        override fun readFromNbt(tag: NbtCompound) {
            progress.clear()
            progress.l1State = Ch2L1State.parse(tag.getString(L1_STATE)) ?: Ch2L1State.START
            progress.l1Active = tag.getBoolean(L1_ACTIVE)
            val counters = tag.getCompound(L1_COUNTERS)
            for (key in counters.keys) progress.setL1Counter(key, counters.getInt(key))
            returnTo = if (tag.contains(RETURN_TO)) tag.getCompound(RETURN_TO) else null
            progress.l2State = Ch2L2State.parse(tag.getString(L2_STATE)) ?: Ch2L2State.START
            progress.l2Active = tag.getBoolean(L2_ACTIVE)
            progress.replaceL2Counters(counterMap(tag.getCompound(L2_COUNTERS)))
            val saved = tag.getCompound(L2_SAVED)
            val savedState = Ch2L2State.parse(saved.getString(STATE))
            progress.l2Snapshot = if (!tag.contains(L2_SAVED) || savedState == null) null
            else Ch2L2Snapshot(saved.getString(CHECKPOINT), savedState, counterMap(saved.getCompound(COUNTERS)))
            l2Inventory = if (progress.l2Snapshot != null && saved.contains(INVENTORY)) saved.getList(INVENTORY, NbtElement.COMPOUND_TYPE.toInt()) else null
        }

        override fun writeToNbt(tag: NbtCompound) {
            tag.putString(L1_STATE, progress.l1State.name)
            tag.putBoolean(L1_ACTIVE, progress.l1Active)
            tag.put(L1_COUNTERS, counterTag(progress.l1CounterMap))
            returnTo?.let { tag.put(RETURN_TO, it) }
            tag.putString(L2_STATE, progress.l2State.name)
            tag.putBoolean(L2_ACTIVE, progress.l2Active)
            tag.put(L2_COUNTERS, counterTag(progress.l2CounterMap))
            progress.l2Snapshot?.let { snapshot ->
                val saved = NbtCompound()
                saved.putString(CHECKPOINT, snapshot.checkpoint)
                saved.putString(STATE, snapshot.state.name)
                saved.put(COUNTERS, counterTag(snapshot.counters))
                l2Inventory?.let { saved.put(INVENTORY, it) }
                tag.put(L2_SAVED, saved)
            }
        }

        private fun counterTag(counters: Map<String, Int>): NbtCompound = NbtCompound().also { tag -> counters.forEach { (key, value) -> tag.putInt(key, value) } }

        private fun counterMap(tag: NbtCompound): Map<String, Int> = tag.keys.associateWith { tag.getInt(it) }

        private companion object {
            const val L1_STATE = "l1_state"
            const val L1_ACTIVE = "l1_active"
            const val L1_COUNTERS = "l1_counters"
            const val RETURN_TO = "return_to"
            const val L2_STATE = "l2_state"
            const val L2_ACTIVE = "l2_active"
            const val L2_COUNTERS = "l2_counters"
            const val L2_SAVED = "l2_saved"
            const val CHECKPOINT = "checkpoint"
            const val STATE = "state"
            const val COUNTERS = "counters"
            const val INVENTORY = "inventory"
        }
    }
}
