package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.core.Ch2PlayerProgress
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.platform.ProgressService
import dev.onyxstudios.cca.api.v3.component.ComponentKey
import dev.onyxstudios.cca.api.v3.component.ComponentRegistry
import dev.onyxstudios.cca.api.v3.component.sync.AutoSyncedComponent
import dev.onyxstudios.cca.api.v3.entity.EntityComponentFactoryRegistry
import dev.onyxstudios.cca.api.v3.entity.EntityComponentInitializer
import dev.onyxstudios.cca.api.v3.entity.RespawnCopyStrategy
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.nbt.NbtCompound
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

    /** A Chapter 1 new game forgets Chapter 2's progress too. */
    fun register() {
        ProgressService.onReset { player -> change(player) { it.clear() } }
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

    /** One line for the debug command. */
    fun describe(player: PlayerEntity): String = of(player).describe()

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

        override fun shouldSyncWith(player: ServerPlayerEntity): Boolean = player === owner

        override fun readFromNbt(tag: NbtCompound) {
            progress.clear()
            progress.l1State = Ch2L1State.parse(tag.getString(L1_STATE)) ?: Ch2L1State.START
            progress.l1Active = tag.getBoolean(L1_ACTIVE)
            val counters = tag.getCompound(L1_COUNTERS)
            for (key in counters.keys) progress.setL1Counter(key, counters.getInt(key))
        }

        override fun writeToNbt(tag: NbtCompound) {
            tag.putString(L1_STATE, progress.l1State.name)
            tag.putBoolean(L1_ACTIVE, progress.l1Active)
            val counters = NbtCompound()
            progress.l1CounterMap.forEach { (key, value) -> counters.putInt(key, value) }
            tag.put(L1_COUNTERS, counters)
        }

        private companion object {
            const val L1_STATE = "l1_state"
            const val L1_ACTIVE = "l1_active"
            const val L1_COUNTERS = "l1_counters"
        }
    }
}
