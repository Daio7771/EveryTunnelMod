package daio7771.everytunnel.client.effects

import daio7771.everytunnel.platform.FluorescentLightBlock
import daio7771.everytunnel.platform.LightMode
import daio7771.everytunnel.platform.ModBlocks
import daio7771.everytunnel.platform.ModSounds
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.minecraft.client.MinecraftClient
import net.minecraft.client.sound.MovingSoundInstance
import net.minecraft.client.sound.SoundInstance
import net.minecraft.client.world.ClientWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.util.math.BlockPos

/**
 * The hum of fluorescent lights near the player.
 *
 * Twice a second the blocks within [RADIUS] of the player are scanned for lights that
 * are not off; the [MAX_SOUNDS] nearest get a looping hum at their position (more would
 * only add noise and use up sound channels). A flickering light's hum drops out while
 * it is dark. Each light hums at a slightly different pitch, like real ballasts, so
 * nearby lights do not sound like one.
 */
object FluorescentHum {

    private const val RADIUS = 16
    private const val MAX_SOUNDS = 8
    private const val SCAN_TICKS = 10
    private const val VOLUME = 0.35f

    private val playing = HashMap<BlockPos, HumSound>()
    private var ticks = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, client -> client.execute { stopAll() } }
    }

    private fun tick(client: MinecraftClient) {
        val world = client.world
        val player = client.player
        if (world == null || player == null) {
            stopAll()
            return
        }
        playing.values.removeIf { it.isDone }
        if (++ticks % SCAN_TICKS != 0) return

        val center = player.blockPos
        val found = ArrayList<Pair<BlockPos, Int>>()
        val pos = BlockPos.Mutable()
        for (dx in -RADIUS..RADIUS) for (dy in -RADIUS..RADIUS) for (dz in -RADIUS..RADIUS) {
            val distanceSq = dx * dx + dy * dy + dz * dz
            if (distanceSq > RADIUS * RADIUS) continue
            pos.set(center.x + dx, center.y + dy, center.z + dz)
            if (humming(world, pos)) found += pos.toImmutable() to distanceSq
        }
        val nearest = found.sortedBy { it.second }.take(MAX_SOUNDS).mapTo(HashSet()) { it.first }

        playing.entries.removeIf { (lightPos, sound) ->
            (lightPos !in nearest).also { if (it) sound.stop() }
        }
        for (lightPos in nearest) {
            if (lightPos !in playing) {
                val sound = HumSound(world, lightPos)
                client.soundManager.play(sound)
                playing[lightPos] = sound
            }
        }
    }

    private fun stopAll() {
        playing.values.forEach { it.stop() }
        playing.clear()
    }

    private fun humming(world: ClientWorld, pos: BlockPos): Boolean {
        val state = world.getBlockState(pos)
        return state.isOf(ModBlocks.FLUORESCENT_LIGHT) && state.get(FluorescentLightBlock.MODE) != LightMode.OFF
    }

    private class HumSound(private val world: ClientWorld, private val pos: BlockPos) :
        MovingSoundInstance(ModSounds.FLUORESCENT_HUM, SoundCategory.BLOCKS, SoundInstance.createRandom()) {

        init {
            x = pos.x + 0.5
            y = pos.y + 0.5
            z = pos.z + 0.5
            repeat = true
            repeatDelay = 0
            volume = VOLUME
            // 0.97-1.03, fixed per position.
            pitch = 0.97f + (Math.floorMod(pos.hashCode(), 61)) / 1000.0f
        }

        /** Keeps playing silently while a flickering light is dark. */
        override fun shouldAlwaysPlay(): Boolean = true

        override fun tick() {
            val state = world.getBlockState(pos)
            if (!state.isOf(ModBlocks.FLUORESCENT_LIGHT) || state.get(FluorescentLightBlock.MODE) == LightMode.OFF) {
                setDone()
                return
            }
            volume = if (state.get(FluorescentLightBlock.LIT)) VOLUME else 0.0f
        }

        fun stop() {
            setDone()
        }
    }
}
