package daio7771.everytunnel.client.menu

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.blaze3d.systems.RenderSystem
import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.client.screen.MainMenuScreen
import daio7771.everytunnel.core.menu.BlockLight
import daio7771.everytunnel.core.menu.LampFlicker
import daio7771.everytunnel.platform.FluorescentLightBlock
import daio7771.everytunnel.platform.LightMode
import daio7771.everytunnel.platform.ModBlocks
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.model.loading.v1.FabricBakedModelManager
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.block.BlockRenderType
import net.minecraft.block.BlockState
import net.minecraft.client.MinecraftClient
import net.minecraft.client.gl.VertexBuffer
import net.minecraft.client.render.BufferBuilder
import net.minecraft.client.render.FogShape
import net.minecraft.client.render.OverlayTexture
import net.minecraft.client.render.RenderLayer
import net.minecraft.client.render.RenderLayers
import net.minecraft.client.render.model.BakedModel
import net.minecraft.client.texture.NativeImageBackedTexture
import net.minecraft.client.util.math.MatrixStack
import net.minecraft.command.argument.BlockArgumentParser
import net.minecraft.registry.Registries
import net.minecraft.resource.ResourceManager
import net.minecraft.resource.ResourceType
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import org.joml.Matrix4f
import org.lwjgl.opengl.GL11
import java.util.concurrent.CompletableFuture

/**
 * The main menu's scene: a parking hall drawn behind the menu ([MainMenuScreen]) from a fixed
 * camera, its lamps flickering now and then. The menu draws its black over the left part.
 *
 * - **The hall** comes from `tools/map/map_generator.py` (`garage/menu_scene.py`):
 *   `menu/scene.json` holds the camera, a palette of block states and the blocks. Blocks the
 *   world's resource pack re-skins (the garage floor, walls, signs) name their own model
 *   under `block/menu/`, loaded by a model loading plugin; turned ones are drawn through
 *   [RotatedModel]. Without the file the menu stays black.
 * - **No world:** Minecraft's block renderer draws the blocks from [MenuSceneView], into one
 *   vertex buffer per render layer, rebuilt whenever a lamp switches (a few thousand blocks).
 * - **Light:** the scene's own block light ([BlockLight]) and its own lightmap: the levels'
 *   Moody brightness curve with vanilla's warm block light. A lit lamp is drawn at full
 *   brightness, as in the game. Fog fades the far end of the hall to black.
 * - **Lamps:** every working lamp flickers now and then ([LampFlicker]), one step per client
 *   tick while the menu is open. Dead lamps stay dark. No sound.
 * - **Camera:** `vanish_x` shifts the picture sideways, so the far end of the aisle sits
 *   right of the menu's black.
 */
object MenuScene {

    private val SCENE = Identifier(EveryTunnel.MOD_ID, "menu/scene.json")

    /** Fog: none up to [FOG_START] blocks from the camera, black at [FOG_END]. */
    private const val FOG_START = 24f
    private const val FOG_END = 56f

    private data class ModelRef(val id: Identifier, val quarterTurns: Int)

    private class Camera(val x: Float, val y: Float, val z: Float, val yaw: Float, val pitch: Float, val fov: Float, val vanishX: Float)

    private class Scene(
        val camera: Camera,
        val blocks: HashMap<BlockPos, BlockState>,
        val models: Map<BlockPos, ModelRef>,
        val lamps: List<BlockPos>,
        val origin: BlockPos,
        val light: BlockLight,
        val flicker: LampFlicker,
    )

    private var scene: Scene? = null
    private var dirty = true
    private val buffers = HashMap<RenderLayer, VertexBuffer>()
    private val builders = HashMap<RenderLayer, BufferBuilder>()
    private val turnedModels = HashMap<ModelRef, BakedModel>()
    private var lightmap: Identifier? = null

    fun register() {
        PreparableModelLoadingPlugin.register(
            { manager, executor -> CompletableFuture.supplyAsync({ modelIds(manager) }, executor) },
            { ids: List<Identifier>, context -> context.addModels(ids) },
        )
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            object : SimpleSynchronousResourceReloadListener {
                override fun getFabricId(): Identifier = Identifier(EveryTunnel.MOD_ID, "menu_scene")
                override fun reload(manager: ResourceManager) = load(manager)
            },
        )
        ClientTickEvents.END_CLIENT_TICK.register { client -> if (client.currentScreen is MainMenuScreen) tick() }
    }

    /**
     * Draws the scene over the whole screen, from its camera; false if there is none.
     * Call before anything else of the screen is drawn: it clears the depth buffer.
     */
    fun render(): Boolean {
        val scene = scene ?: return false
        val client = MinecraftClient.getInstance()
        if (dirty) {
            build(scene, client)
            dirty = false
        }
        if (buffers.isEmpty()) return false
        val camera = scene.camera
        val window = client.window
        val aspect = window.framebufferWidth.toFloat() / window.framebufferHeight.coerceAtLeast(1)
        val projection = Matrix4f().translation(camera.vanishX * 2f - 1f, 0f, 0f)
            .mul(Matrix4f().perspective(Math.toRadians(camera.fov.toDouble()).toFloat(), aspect, 0.05f, 256f))
        val view = Matrix4f()
            .rotateX(Math.toRadians(camera.pitch.toDouble()).toFloat())
            .rotateY(Math.toRadians(camera.yaw + 180.0).toFloat())
            .translate(-camera.x, -camera.y, -camera.z)

        val fogStart = RenderSystem.getShaderFogStart()
        val fogEnd = RenderSystem.getShaderFogEnd()
        val fogColor = RenderSystem.getShaderFogColor().copyOf()
        val fogShape = RenderSystem.getShaderFogShape()
        RenderSystem.setShaderFogStart(FOG_START)
        RenderSystem.setShaderFogEnd(FOG_END)
        RenderSystem.setShaderFogColor(0f, 0f, 0f, 1f)
        RenderSystem.setShaderFogShape(FogShape.SPHERE)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f)
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC)
        RenderSystem.enableDepthTest()
        RenderSystem.depthMask(true)
        RenderSystem.enableCull()
        val lightmap = lightmap(client)
        for (layer in RenderLayer.getBlockLayers()) {
            val buffer = buffers[layer] ?: continue
            layer.startDrawing()
            // After startDrawing, which binds the world's lightmap.
            RenderSystem.setShaderTexture(2, lightmap)
            RenderSystem.getShader()?.let { shader ->
                // Left over from the last world drawn otherwise.
                shader.chunkOffset?.set(0f, 0f, 0f)
                buffer.bind()
                buffer.draw(view, projection, shader)
                VertexBuffer.unbind()
            }
            layer.endDrawing()
        }
        RenderSystem.setShaderFogStart(fogStart)
        RenderSystem.setShaderFogEnd(fogEnd)
        RenderSystem.setShaderFogColor(fogColor[0], fogColor[1], fogColor[2], fogColor[3])
        RenderSystem.setShaderFogShape(fogShape)
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC)
        return true
    }

    private fun tick() {
        val scene = scene ?: return
        if (!scene.flicker.tick()) return
        scene.lamps.forEachIndexed { i, pos ->
            val state = scene.blocks[pos] ?: return@forEachIndexed
            val lit = scene.flicker.isLit(i)
            if (state.get(FluorescentLightBlock.LIT) == lit) return@forEachIndexed
            val switched = state.with(FluorescentLightBlock.LIT, lit)
            scene.blocks[pos] = switched
            scene.light.setEmission(pos.x - scene.origin.x, pos.y - scene.origin.y, pos.z - scene.origin.z, switched.luminance)
        }
        scene.light.compute()
        dirty = true
    }

    // --- Loading ---------------------------------------------------------------------

    /** The model ids the scene names, for the model loader (on a worker thread). */
    private fun modelIds(manager: ResourceManager): List<Identifier> = try {
        read(manager)?.getAsJsonArray("palette")
            ?.mapNotNull { it.asJsonObject.get("model")?.asString?.let(::Identifier) }
            ?.distinct() ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun read(manager: ResourceManager): JsonObject? {
        val resource = manager.getResource(SCENE).orElse(null) ?: return null
        return resource.reader.use { JsonParser.parseReader(it).asJsonObject }
    }

    private fun load(manager: ResourceManager) {
        buffers.values.forEach { it.close() }
        buffers.clear()
        turnedModels.clear()
        scene = null
        dirty = true
        val json = try {
            read(manager)
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("The menu scene {} could not be read: {}", SCENE, e.message)
            null
        } ?: return
        scene = try {
            parse(json)
        } catch (e: Exception) {
            EveryTunnel.LOGGER.warn("The menu scene {} is broken: {}", SCENE, e.message)
            null
        }
    }

    private fun parse(json: JsonObject): Scene? {
        val c = json.getAsJsonObject("camera")
        val pos = c.getAsJsonArray("pos")
        val camera = Camera(
            pos[0].asFloat, pos[1].asFloat, pos[2].asFloat,
            c.get("yaw").asFloat, c.get("pitch").asFloat, c.get("fov").asFloat, c.get("vanish_x").asFloat,
        )
        val unknown = mutableSetOf<String>()
        val palette = json.getAsJsonArray("palette").map { entry ->
            val o = entry.asJsonObject
            val text = o.get("state").asString
            val state = try {
                BlockArgumentParser.block(Registries.BLOCK.readOnlyWrapper, text, false).blockState()
            } catch (e: Exception) {
                unknown += text
                null
            }
            val model = o.get("model")?.asString?.let { ModelRef(Identifier(it), (o.get("y")?.asInt ?: 0) / 90) }
            state to model
        }
        if (unknown.isNotEmpty()) EveryTunnel.LOGGER.warn("Menu scene: unknown block states left out: {}", unknown)

        val blocks = HashMap<BlockPos, BlockState>()
        val models = HashMap<BlockPos, ModelRef>()
        val lamps = mutableListOf<BlockPos>()
        for (element in json.getAsJsonArray("blocks")) {
            val b = element.asJsonArray
            val (state, model) = palette[b[3].asInt]
            if (state == null) continue
            val at = BlockPos(b[0].asInt, b[1].asInt, b[2].asInt)
            if (state.isOf(ModBlocks.FLUORESCENT_LIGHT) && state.get(FluorescentLightBlock.MODE) != LightMode.OFF) {
                // A working lamp: lit, flickering only when LampFlicker says so.
                blocks[at] = state.with(FluorescentLightBlock.LIT, true).with(FluorescentLightBlock.BURST, false)
                lamps += at
            } else {
                blocks[at] = state
            }
            if (model != null) models[at] = model
        }
        if (blocks.isEmpty()) return null

        // The light box: the blocks plus one block of air all round.
        val minX = blocks.keys.minOf { it.x } - 1
        val minY = blocks.keys.minOf { it.y } - 1
        val minZ = blocks.keys.minOf { it.z } - 1
        val origin = BlockPos(minX, minY, minZ)
        val light = BlockLight(
            blocks.keys.maxOf { it.x } - minX + 2, blocks.keys.maxOf { it.y } - minY + 2, blocks.keys.maxOf { it.z } - minZ + 2,
        )
        val view = MenuSceneView(blocks, light, origin)
        for ((at, state) in blocks) {
            light.setOpacity(at.x - minX, at.y - minY, at.z - minZ, state.getOpacity(view, at))
            light.setEmission(at.x - minX, at.y - minY, at.z - minZ, state.luminance)
        }
        light.compute()
        return Scene(camera, blocks, models, lamps, origin, light, LampFlicker(lamps.size, kotlin.random.Random.Default))
    }

    // --- Drawing ---------------------------------------------------------------------

    private fun build(scene: Scene, client: MinecraftClient) {
        val renderer = client.blockRenderManager.modelRenderer
        val view = MenuSceneView(scene.blocks, scene.light, scene.origin)
        val matrices = MatrixStack()
        val random = Random.create()
        val used = HashSet<RenderLayer>()
        for ((pos, state) in scene.blocks) {
            if (state.renderType != BlockRenderType.MODEL) continue
            val layer = RenderLayers.getBlockLayer(state)
            val builder = builders.getOrPut(layer) { BufferBuilder(layer.expectedBufferSize) }
            if (used.add(layer)) builder.begin(layer.drawMode, layer.vertexFormat)
            matrices.push()
            matrices.translate(pos.x.toFloat(), pos.y.toFloat(), pos.z.toFloat())
            renderer.render(view, model(scene, pos, state, client), state, pos, matrices, builder, true, random, state.getRenderingSeed(pos), OverlayTexture.DEFAULT_UV)
            matrices.pop()
        }
        for (layer in buffers.keys.filter { it !in used }) buffers.remove(layer)?.close()
        for (layer in used) {
            val built = builders.getValue(layer).end()
            if (built.isEmpty) {
                built.release()
                buffers.remove(layer)?.close()
                continue
            }
            val buffer = buffers.getOrPut(layer) { VertexBuffer(VertexBuffer.Usage.STATIC) }
            buffer.bind()
            buffer.upload(built)
            VertexBuffer.unbind()
        }
    }

    private fun model(scene: Scene, pos: BlockPos, state: BlockState, client: MinecraftClient): BakedModel {
        val ref = scene.models[pos] ?: return client.blockRenderManager.getModel(state)
        return turnedModels.getOrPut(ref) {
            val manager = client.bakedModelManager
            val base = (manager as FabricBakedModelManager).getModel(ref.id) ?: manager.missingModel
            if (ref.quarterTurns == 0) base else RotatedModel(base, ref.quarterTurns)
        }
    }

    /**
     * The scene's lightmap (block light across, sky light down; the scene has none, but a
     * full-bright lamp asks for both at 15): the levels' Moody curve, vanilla's warm tint.
     */
    private fun lightmap(client: MinecraftClient): Identifier {
        lightmap?.let { return it }
        val texture = NativeImageBackedTexture(16, 16, false)
        val image = texture.image!!
        for (sky in 0 until 16) {
            for (block in 0 until 16) {
                val level = maxOf(block, sky) / 15f
                val n = level / (4f - 3f * level) * 1.5f
                val r = tone(n)
                val g = tone(n * ((n * 0.6f + 0.4f) * 0.6f + 0.4f))
                val b = tone(n * (n * n * 0.6f + 0.4f))
                image.setColor(block, sky, (0xFF shl 24) or (b shl 16) or (g shl 8) or r)
            }
        }
        texture.upload()
        texture.setFilter(true, false)
        return client.textureManager.registerDynamicTexture("every_tunnel_menu_lightmap", texture).also { lightmap = it }
    }

    /** One channel: clamped, lifted a little off black like vanilla's lightmap, as a byte. */
    private fun tone(value: Float): Int {
        val v = value.coerceIn(0f, 1f) * 0.96f + 0.75f * 0.04f
        return (v.coerceIn(0f, 1f) * 255f).toInt()
    }
}
