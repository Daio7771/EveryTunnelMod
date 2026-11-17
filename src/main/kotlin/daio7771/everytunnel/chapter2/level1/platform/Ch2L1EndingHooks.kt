package daio7771.everytunnel.chapter2.level1.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.chapter2.level1.core.Ch2L1Overtime
import daio7771.everytunnel.chapter2.level1.core.Ch2L1State
import daio7771.everytunnel.chapter2.platform.Ch2Blocks
import daio7771.everytunnel.chapter2.platform.Ch2Progress
import daio7771.everytunnel.chapter2.platform.Ch2Sounds
import daio7771.everytunnel.platform.DeathHooks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.block.Block
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.sound.SoundCategory
import net.minecraft.text.Text
import net.minecraft.util.ActionResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The end of Chapter 2's Level 1, from the basement's last dirt swept ([Ch2L1TaskHooks]) to the way out.
 *
 * - **Locked in** ([Ch2L1State.ACT3_LOCKED_IN]): the basement's lights go out with a thud, and a moment later the sealed door up the
 *   stairs is bolted from outside (a dry clack, heard from below). Nobody came back for the new hire. The objective is to go home; trying
 *   the door ([Ch2L1Act3Hooks]: "Locked from the outside.", [Ch2L1Overtime.TRIED_DOOR]) turns it into finding another way out, and the
 *   guide arrow shows the emergency manhole in the basement's floor ([ManholeBlock]).
 * - **The manhole**: a click held on it for [Ch2L1Overtime.liftTicks] prises its lid up (a bar fills, iron grinds on concrete) and lets it
 *   fall aside with a clang ([Ch2L1Overtime.MANHOLE_OPEN]). A click on the open hole, or stepping into it, takes the player down.
 * - **Down it** ([Ch2L1State.DONE]): the level is marked finished in the player's progress ([Ch2L1Overtime.COMPLETED]), they climb down
 *   the rungs while the screen fades to black ([Ch2L1Overtime.fadeTicks], the base game's fade: `DeathHooks.sendFade`), and after
 *   [Ch2L1Overtime.darkTicks] more in the dark the level is over: [onFinished]'s listeners are told (`Chapter2` takes the player on to
 *   Level 2, the catacombs, at the foot of the ladder, where the screen fades back). No closing text and no menu: the way goes on.
 *
 * A player who left the world before the end and comes back finds it as they left it: locked in in the dark, or going down, which plays
 * again. Going back before [Ch2L1State.ACT3_LOCKED_IN] (a command) lights the basement again and shuts the manhole. Server thread only.
 */
object Ch2L1EndingHooks {

    private const val KEY = "every_tunnel.ch2.l1."

    /** A player going down the manhole: ticks before the screen starts to fade (-1: it has), then before the level is over. */
    private class Descent(var toFade: Int, var toEnd: Int)

    /** Players going down the manhole. */
    private val pending = HashMap<UUID, Descent>()

    /** Told when a player has gone down the manhole, in the dark: the level is over. */
    private val finished = CopyOnWriteArrayList<(ServerPlayerEntity) -> Unit>()

    /** Players just locked in, and the ticks left before the sealed door's bolt is heard. */
    private val bolts = HashMap<UUID, Int>()

    private val overtime: Ch2L1Overtime get() = Ch2L1DataLoader.act3.overtime

    /** On joining at the end, this much more before the fade starts (the client's world loading). */
    private const val REJOIN_TICKS = 40

    /** From the lights going out to the bolt. */
    private const val BOLT_TICKS = 30

    /** How often the grinding plays while the lid comes up (a held click is repeated every 4 ticks). */
    private const val SCRAPE_EVERY = 14

    fun register() {
        Ch2L1.onStateChange(::onState)
        ServerTickEvents.END_SERVER_TICK.register(::tick)
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            pending.remove(handler.player.uuid)
            bolts.remove(handler.player.uuid)
        }
        // Back in the world after the overtime (it was left in the dark): the dark again, the manhole as it was, and at the end the end again.
        ServerPlayConnectionEvents.JOIN.register { handler, _, _ ->
            val player = handler.player
            if (!Ch2L1.active(player)) return@register
            val state = Ch2L1.state(player)
            if (state < Ch2L1State.ACT3_LOCKED_IN) return@register
            if (Ch2L1Build.isBuilt(player.server)) Ch2L1Build.zoneLights(player.serverWorld, overtime.basementZone, true)
            setManhole(player, Ch2Progress.ch2L1Counter(player, Ch2L1Overtime.MANHOLE_OPEN) > 0)
            if (state == Ch2L1State.DONE) pending[player.uuid] = Descent(REJOIN_TICKS, overtime.fadeTicks + overtime.darkTicks)
        }
    }

    /** [listener] is told when a player has gone down the manhole and the screen is black: the level is over. */
    fun onFinished(listener: (ServerPlayerEntity) -> Unit) {
        finished += listener
    }

    // --- where things are -------------------------------------------------------------------------

    /** The manhole block: the one under its anchor (which stands on the lid). */
    private fun manholeCell(): BlockPos? =
        Ch2L1DataLoader.data.anchors.point(overtime.manhole)?.position?.let { BlockPos.ofFloored(it.x, it.y, it.z).down() }

    private fun counter(player: ServerPlayerEntity, key: String) = Ch2Progress.ch2L1Counter(player, key)

    private fun set(player: ServerPlayerEntity, key: String, value: Int) = Ch2Progress.setCh2L1Counter(player, key, value)

    /** The manhole's lid up ([open]) or down in the world; nothing if the level is not built. */
    private fun setManhole(player: ServerPlayerEntity, open: Boolean) {
        if (!Ch2L1Build.isBuilt(player.server)) return
        val pos = manholeCell() ?: return
        val world = player.serverWorld
        val state = world.getBlockState(pos)
        if (state.isOf(Ch2Blocks.MANHOLE) && state.get(ManholeBlock.OPEN) != open) world.setBlockState(pos, state.with(ManholeBlock.OPEN, open), Block.NOTIFY_ALL)
    }

    // --- the manhole --------------------------------------------------------------------------------

    /** The manhole clicked: locked in, a held click lifts its lid; open, the player goes down it. */
    fun useManhole(player: ServerPlayerEntity, pos: BlockPos): ActionResult {
        if (pos != manholeCell()) return ActionResult.PASS
        if (Ch2L1.state(player) != Ch2L1State.ACT3_LOCKED_IN) {
            player.sendMessage(Text.translatable(KEY + "task.not_now"), true)
            return ActionResult.CONSUME
        }
        if (counter(player, Ch2L1Overtime.MANHOLE_OPEN) > 0) {
            goDown(player)
            return ActionResult.CONSUME
        }
        val world = player.serverWorld
        val fraction = Ch2L1TaskHooks.fractionHeld(player, "manhole", overtime.liftTicks, KEY + "hold.lifting")
        if (fraction < 1f) {
            if ((fraction * overtime.liftTicks).toInt() % SCRAPE_EVERY < 4) {
                world.playSound(null, pos, Ch2Sounds.CH2L1_MANHOLE_SCRAPE, SoundCategory.BLOCKS, 0.8f, 0.9f + world.random.nextFloat() * 0.2f)
            }
            return ActionResult.CONSUME
        }
        set(player, Ch2L1Overtime.MANHOLE_OPEN, 1)
        setManhole(player, true)
        world.playSound(null, pos, Ch2Sounds.CH2L1_MANHOLE_OPEN, SoundCategory.BLOCKS, 1.2f, 1f)
        EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has lifted the emergency manhole's lid", player.gameProfile.name)
        return ActionResult.CONSUME
    }

    /** Into the open manhole: the player stands in the hole, and the level is over. */
    private fun goDown(player: ServerPlayerEntity) {
        if (Ch2L1.state(player) != Ch2L1State.ACT3_LOCKED_IN) return
        val pos = manholeCell() ?: return
        player.velocity = Vec3d.ZERO
        player.networkHandler.requestTeleport(pos.x + 0.5, pos.y + 1.0 / 16, pos.z + 0.5, player.yaw, 60f)
        Ch2L1.setState(player, Ch2L1State.DONE, "down the manhole")
    }

    /** A player locked in, with the lid up, who has stepped into the hole goes down it as if they had clicked it. */
    private fun steppedIn(player: ServerPlayerEntity): Boolean {
        if (counter(player, Ch2L1Overtime.MANHOLE_OPEN) == 0) return false
        val pos = manholeCell() ?: return false
        return player.x >= pos.x + 0.1 && player.x <= pos.x + 0.9 && player.z >= pos.z + 0.1 && player.z <= pos.z + 0.9 &&
            player.y < pos.y + 0.9 && player.y > pos.y - 1.0
    }

    // --- the states ------------------------------------------------------------------------------

    private fun onState(player: ServerPlayerEntity, from: Ch2L1State, to: Ch2L1State) {
        val world = player.serverWorld
        val built = Ch2L1Build.isBuilt(player.server)
        if (to == Ch2L1State.ACT3_LOCKED_IN && from < to) {
            set(player, Ch2L1Overtime.TRIED_DOOR, 0)
            set(player, Ch2L1Overtime.MANHOLE_OPEN, 0)
            setManhole(player, false)
            if (built) Ch2L1Build.zoneLights(world, overtime.basementZone, true)
            world.playSound(null, player.blockPos, ModSounds.LOUNGE_BLACKOUT, SoundCategory.BLOCKS, 1f, 1f)
            bolts[player.uuid] = BOLT_TICKS
            EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has swept the basement: the lights go out, the door is bolted", player.gameProfile.name)
        }
        if (to == Ch2L1State.DONE) {
            Ch2Progress.setCh2L1Counter(player, Ch2L1Overtime.COMPLETED, 1)
            // Reached by a command: the dark and the open hole, as if played.
            if (built && from < Ch2L1State.ACT3_LOCKED_IN) Ch2L1Build.zoneLights(world, overtime.basementZone, true)
            set(player, Ch2L1Overtime.MANHOLE_OPEN, 1)
            setManhole(player, true)
            world.playSound(null, player.blockPos, Ch2Sounds.CH2L1_LADDER, SoundCategory.BLOCKS, 1f, 1f)
            pending[player.uuid] = Descent(0, overtime.fadeTicks + overtime.darkTicks)
            EveryTunnel.LOGGER.info("Chapter 2 Level 1: {} has gone down the manhole: the level is finished", player.gameProfile.name)
        } else {
            pending.remove(player.uuid)
        }
        if (to < Ch2L1State.ACT3_LOCKED_IN && from >= Ch2L1State.ACT3_LOCKED_IN) {
            bolts.remove(player.uuid)
            set(player, Ch2L1Overtime.TRIED_DOOR, 0)
            set(player, Ch2L1Overtime.MANHOLE_OPEN, 0)
            setManhole(player, false)
            if (built) Ch2L1Build.zoneLights(world, overtime.basementZone, false)
        }
    }

    // --- the clock ---------------------------------------------------------------------------------

    private fun tick(server: MinecraftServer) {
        if (bolts.isNotEmpty()) {
            val iterator = bolts.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value > 0) {
                    entry.setValue(entry.value - 1)
                    continue
                }
                iterator.remove()
                val player = server.playerManager.getPlayer(entry.key) ?: continue
                val door = Ch2L1DataLoader.data.anchors.point(Ch2L1DataLoader.act3.sealedDoor)?.position ?: continue
                player.serverWorld.playSound(null, BlockPos.ofFloored(door.x, door.y + 1, door.z), Ch2Sounds.CH2L1_LOCK_SHUT, SoundCategory.BLOCKS, 3f, 0.9f)
            }
        }
        if (server.ticks % 5 == 0) {
            for (player in server.playerManager.playerList) {
                if (Ch2L1.active(player) && Ch2L1.state(player) == Ch2L1State.ACT3_LOCKED_IN && steppedIn(player)) goDown(player)
            }
        }
        if (pending.isEmpty()) return
        val ended = mutableListOf<UUID>()
        for ((id, descent) in pending) {
            if (descent.toFade > 0) {
                descent.toFade--
                continue
            }
            if (descent.toFade == 0) {
                descent.toFade = -1
                server.playerManager.getPlayer(id)?.let { DeathHooks.sendFade(it, 1f, overtime.fadeTicks / 20f) }
            }
            if (descent.toEnd > 0) descent.toEnd-- else ended += id
        }
        for (id in ended) {
            pending.remove(id)
            val player = server.playerManager.getPlayer(id) ?: continue
            for (listener in finished) {
                try {
                    listener(player)
                } catch (e: Exception) {
                    EveryTunnel.LOGGER.error("Chapter 2 Level 1: a listener of the level's end failed", e)
                }
            }
        }
    }
}
