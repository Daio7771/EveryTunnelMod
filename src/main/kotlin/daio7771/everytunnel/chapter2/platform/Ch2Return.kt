package daio7771.everytunnel.chapter2.platform

import daio7771.everytunnel.common.EveryTunnel
import daio7771.everytunnel.chapter2.level1.platform.Ch2L1
import daio7771.everytunnel.chapter2.level2.platform.Ch2L2
import daio7771.everytunnel.common.config.ConfigManager
import daio7771.everytunnel.common.systems.ResourceSystem
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtList
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Identifier
import net.minecraft.util.math.Vec3d

/**
 * The menu's Chapter 2 opens the same world as Chapter 1's saved game. So, before Chapter 2 takes the player anywhere (the prologue's
 * end into the factory, or CONTINUE into the catacombs), where they stand, what they carry, their health and their stamina, thirst and
 * battery are kept ([keep], in their Chapter 2 progress: [Ch2Progress.ch2Return]); when they leave the world (halfway, or at an end that
 * goes back to the menu), they are out of the chapter's levels and back there with their own things ([restore]), and the world is saved
 * as Chapter 1 left it, but for Chapter 2's own progress and saved game. A player who comes into the world with the way back still kept
 * (the game stopped without leaving) is put back the same way on joining. Without a way back (`/et ch2 l1 enter`, `/et ch2 l2 enter`)
 * nothing of this happens. Server thread only.
 */
object Ch2Return {

    /** Joining: before the levels' own joining hooks, which would carry on with them. */
    private val FIRST = Identifier(EveryTunnel.MOD_ID, "ch2_return_first")

    /** Leaving: after the levels' own leaving hooks, which may still move the player. */
    private val LAST = Identifier(EveryTunnel.MOD_ID, "ch2_return_last")

    fun register() {
        ServerPlayConnectionEvents.JOIN.addPhaseOrdering(FIRST, Event.DEFAULT_PHASE)
        ServerPlayConnectionEvents.JOIN.register(FIRST) { handler, _, _ -> restore(handler.player, joining = true) }
        ServerPlayConnectionEvents.DISCONNECT.addPhaseOrdering(Event.DEFAULT_PHASE, LAST)
        ServerPlayConnectionEvents.DISCONNECT.register(LAST) { handler, _ -> restore(handler.player, joining = false) }
    }

    /** Keeps where [player] stands, what they carry, their health and resources, to give back when they leave the chapter. */
    fun keep(player: ServerPlayerEntity) {
        val tag = NbtCompound()
        tag.putString(WORLD, player.serverWorld.registryKey.value.toString())
        tag.putDouble(X, player.x)
        tag.putDouble(Y, player.y)
        tag.putDouble(Z, player.z)
        tag.putFloat(YAW, player.yaw)
        tag.putFloat(PITCH, player.pitch)
        tag.put(INVENTORY, player.inventory.writeNbt(NbtList()))
        tag.putFloat(HEALTH, player.health)
        val resources = ResourceSystem.stateOf(player.uuid, ConfigManager.gameplay)
        tag.putDouble(STAMINA, resources.stamina)
        tag.putDouble(THIRST, resources.thirst)
        tag.putDouble(BATTERY, resources.battery)
        Ch2Progress.setCh2Return(player, tag)
    }

    /**
     * The prologue the menu asked for is over, [player] back where they were: that place and their things are kept ([keep]) and they go
     * into the factory ([Ch2L1.enter]). If the level cannot begin, the way back is forgotten and they stay there, told why.
     */
    fun enterFromPrologue(player: ServerPlayerEntity) {
        keep(player)
        val why = Ch2L1.enter(player) ?: return
        Ch2Progress.setCh2Return(player, null)
        EveryTunnel.LOGGER.warn("Chapter 2 Level 1 after the prologue: {}", why)
        player.sendMessage(Text.literal(why))
    }

    /**
     * [player] is out of the chapter's levels ([Ch2L1.leave], [Ch2L2.leave]), their things back in their pockets, their health and
     * resources as they were, and themselves where they were kept; the way back is forgotten. Nothing if none is kept. On leaving the
     * world they are only placed there (the save takes them as they are).
     */
    private fun restore(player: ServerPlayerEntity, joining: Boolean) {
        val tag = Ch2Progress.ch2Return(player) ?: return
        if (Ch2L1.active(player)) Ch2L1.leave(player)
        if (Ch2L2.active(player)) Ch2L2.leave(player)
        player.closeHandledScreen()
        player.inventory.clear()
        player.inventory.readNbt(tag.getList(INVENTORY, NbtElement.COMPOUND_TYPE.toInt()))
        if (tag.contains(HEALTH)) player.health = tag.getFloat(HEALTH).coerceIn(1f, player.maxHealth)
        if (tag.contains(THIRST)) {
            val resources = ResourceSystem.stateOf(player.uuid, ConfigManager.gameplay)
            ResourceSystem.set(
                player.uuid,
                resources.copy(
                    stamina = tag.getDouble(STAMINA), thirst = tag.getDouble(THIRST), battery = tag.getDouble(BATTERY),
                    flashlightOn = false, exhausted = false,
                ),
            )
        }
        val key = Identifier.tryParse(tag.getString(WORLD))?.let { RegistryKey.of(RegistryKeys.WORLD, it) }
        val world = key?.let { player.server.getWorld(it) }
        val (x, y, z) = Triple(tag.getDouble(X), tag.getDouble(Y), tag.getDouble(Z))
        val (yaw, pitch) = tag.getFloat(YAW) to tag.getFloat(PITCH)
        player.stopRiding()
        when {
            world == null -> EveryTunnel.LOGGER.warn("Chapter 2: {}'s way back is in a world that is not there ({})", player.gameProfile.name, key)
            joining || world != player.serverWorld -> player.teleport(world, x, y, z, yaw, pitch)
            else -> player.refreshPositionAndAngles(x, y, z, yaw, pitch)
        }
        player.velocity = Vec3d.ZERO
        Ch2Progress.setCh2Return(player, null)
        EveryTunnel.LOGGER.info("Chapter 2: {} is back where Chapter 1 left them", player.gameProfile.name)
    }

    private const val WORLD = "world"
    private const val X = "x"
    private const val Y = "y"
    private const val Z = "z"
    private const val YAW = "yaw"
    private const val PITCH = "pitch"
    private const val INVENTORY = "inventory"
    private const val HEALTH = "health"
    private const val STAMINA = "stamina"
    private const val THIRST = "thirst"
    private const val BATTERY = "battery"
}
