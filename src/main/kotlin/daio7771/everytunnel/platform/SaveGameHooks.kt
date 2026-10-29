package daio7771.everytunnel.platform

import daio7771.everytunnel.EveryTunnel
import daio7771.everytunnel.core.LevelManager
import daio7771.everytunnel.core.level.LevelDefinition
import daio7771.everytunnel.core.save.SavedGame
import daio7771.everytunnel.world.LevelTriggers
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.NbtCompound
import net.minecraft.nbt.NbtElement
import net.minecraft.nbt.NbtInt
import net.minecraft.nbt.NbtList
import net.minecraft.nbt.NbtString
import net.minecraft.registry.RegistryKey
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.world.PersistentState

/**
 * The game is saved between sessions ([SavedGame], in `data/every_tunnel_progress.dat`; the main
 * menu reads that file to offer CONTINUE, and NEW GAME deletes it).
 *
 * - **What is kept:** the furthest level reached in this game, and how far its puzzles got:
 *   Level 3's valve digits and turns, where the boxes and books lie (one in the hands counts as
 *   lying where it was picked up), the shelf's boxes and the books on the counter, the lights
 *   the keycard put out and the card readers it unlocked, the loot picked up, and the
 *   achievements earned in this game. Inventory and resources are the player's own saves
 *   (vanilla's and [ResourceHooks]').
 * - **When:** as the player leaves and the server stops, every [SNAPSHOT_TICKS] (so a crash
 *   loses little), and the level as soon as a further one is walked into. Only once this
 *   session's game has started or gone on ([live]): never over a map just rebuilt.
 *   A level whose chunks are not loaded keeps what was saved before (nothing changes there
 *   without a player near).
 * - **Going on** ([continueGame], instead of [NewGame.start] when the world opens and a game is
 *   saved): after the map's rebuild, every level's puzzles are put back as they were, the way
 *   back is shut (shutters, [LevelEntryHooks.closeNow]), the elevator stands open if the intro
 *   was had, and the player is at the start of the furthest level, as after a death, with
 *   what they carried and their thirst and battery.
 * - **The end of the demo** ([finished]) forgets the game: the menu offers NEW GAME only.
 */
object SaveGameHooks {

    /** The save's file name in the world's `data/` folder (also read by `client.screen.MainMenuScreen`). */
    const val SAVE_ID = "every_tunnel_progress"
    private const val SNAPSHOT_TICKS = 20 * 60

    private var server: MinecraftServer? = null
    /** This session's game has started or gone on: snapshots are taken. */
    private var live = false
    private var ticks = 0

    fun register() {
        ServerLifecycleEvents.SERVER_STARTED.register { server = it }
        ServerLifecycleEvents.SERVER_STOPPING.register { if (live) snapshot(it) }
        ServerLifecycleEvents.SERVER_STOPPED.register {
            server = null
            live = false
        }
        ServerPlayConnectionEvents.DISCONNECT.register { _, server -> if (live) snapshot(server) }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            if (live && ++ticks >= SNAPSHOT_TICKS) {
                ticks = 0
                snapshot(server)
            }
        }
        LevelTriggers.register { change ->
            val server = server ?: return@register
            val level = change.current.levelId?.let(LevelManager::get) ?: return@register
            if (!live || !change.levelChanged) return@register
            val state = state(server)
            if (state.game.reached(level.id, level.order)) {
                state.markDirty()
                EveryTunnel.LOGGER.info("Saved game: {} reached", level.id)
            }
        }
    }

    /** A game is saved that can go on: active, and its level still has a start. */
    fun canContinue(server: MinecraftServer): Boolean {
        val game = state(server).game
        return game.active && game.levelId?.let(LevelManager::get)?.let(::startOf) != null
    }

    /** A new game began in [level] ([NewGame.start]): the save starts over from it. */
    fun started(server: MinecraftServer, level: LevelDefinition?) {
        val state = state(server)
        if (level == null) state.game.finish() else state.game.start(level.id, level.order)
        state.markDirty()
        live = level != null
        ticks = 0
    }

    /** The end of the demo: nothing to go on with. */
    fun finished(server: MinecraftServer) {
        val state = state(server)
        state.game.finish()
        state.markDirty()
        live = false
        EveryTunnel.LOGGER.info("Saved game: the demo is over, the save forgotten")
    }

    /**
     * The world opened with a saved game: everything put back as it was, and [player] at the
     * start of the furthest level. Falls back to a new game if that level is gone.
     */
    fun continueGame(player: ServerPlayerEntity): LevelDefinition? {
        val server = player.server
        val game = state(server).game
        val level = game.levelId?.let(LevelManager::get)
        val start = level?.let(::startOf)
        val world = level?.let { world(server, it) }
        if (level == null || start == null || world == null) return NewGame.start(player)

        HidingHooks.newGame(server)
        for (lv in LevelManager.all()) {
            val w = world(server, lv) ?: continue
            game.valveCodes[lv.id]?.let { code -> ValveHooks.restore(w, lv, code, game.valveTurns[lv.id].orEmpty()) }
            BoxHooks.restore(w, lv, game.boxes[lv.id]?.map(::toPos), game.shelfSlots[lv.id].orEmpty())
            val books = game.books[lv.id]
            val returned = game.returnedBooks[lv.id]
            if (books != null || returned != null) BookHooks.restore(w, lv, books.orEmpty().map(::toPos), returned.orEmpty().map(::toPos))
        }
        KeycardHooks.restoreLightsOut(server, game.lightsOut.mapNotNull { (key, mode) ->
            LightMode.entries.firstOrNull { it.asString() == mode }?.let { key to it }
        }.toMap())
        KeycardHooks.unlockReaders(server, game.readersUnlocked)
        LootSpawner.takeAway(server, game.lootTaken)
        AchievementHooks.restoreEarned(game.achievements)
        LevelEntryHooks.closeNow(server, level.order)
        if (IntroHooks.hadIntro(player)) IntroHooks.openDoorsNow(server)

        player.stopRiding()
        player.teleport(world, start.position.x, start.position.y, start.position.z, start.yaw, start.pitch)
        player.fallDistance = 0f
        live = true
        ticks = 0
        EveryTunnel.LOGGER.info("{}: the saved game goes on in {}", player.name.string, level.id)
        return level
    }

    /** What the world is like now, into the save (see the class header for what and when). */
    private fun snapshot(server: MinecraftServer) {
        val state = state(server)
        val game = state.game
        if (!game.active) return
        val carried = CarryHooks.carriedThings()
        for (level in LevelManager.all()) {
            val world = world(server, level) ?: continue
            val dimension = level.dimension
            fun loaded(pos: BlockPos) = world.chunkManager.isChunkLoaded(pos.x shr 4, pos.z shr 4)
            fun carriedHere(block: net.minecraft.block.Block) = carried
                .filter { (dim, pos, lay) -> dim == dimension && lay.isOf(block) && LevelManager.levelAt(dim, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)?.id == level.id }
                .map { it.second }

            ValveHooks.drawnCode(level.id)?.let { game.valveCodes[level.id] = it }
            val valves = points(level, ValveHooks.VALVE_TAG)
            if (valves.isNotEmpty() && valves.all(::loaded)) {
                val turns = MutableList(ValveLetter.entries.size) { 0 }
                for (pos in valves) {
                    val block = world.getBlockState(pos)
                    if (block.isOf(ModBlocks.VALVE)) turns[block.get(ValveBlock.LETTER).ordinal] = block.get(ValveBlock.TURNS)
                }
                game.valveTurns[level.id] = turns
            }

            val boxSpots = points(level, BoxHooks.SPOT_TAG)
            val shelf = points(level, BoxHooks.SHELF_TAG).firstOrNull()
            if ((boxSpots.isNotEmpty() || shelf != null) && (boxSpots + listOfNotNull(shelf)).all(::loaded)) {
                val lying = boxSpots.filter { world.getBlockState(it).isOf(ModBlocks.PURPLE_BOX) } + carriedHere(ModBlocks.PURPLE_BOX)
                game.boxes[level.id] = lying.distinct().map(::toCell)
                val entity = shelf?.let { world.getBlockEntity(it) as? BoxShelfBlockEntity }
                game.shelfSlots[level.id] = (0 until BoxShelfBlock.SLOTS).filter { entity?.isFilled(it) == true }
            }

            val bookSpots = points(level, BookHooks.SPOT_TAG)
            val returns = points(level, BookHooks.RETURN_TAG)
            if ((bookSpots.isNotEmpty() || returns.isNotEmpty()) && (bookSpots + returns).all(::loaded)) {
                val lying = bookSpots.filter {
                    val block = world.getBlockState(it)
                    block.isOf(ModBlocks.OFFICE_BOOK) && !block.get(OfficeBookBlock.DELIVERED)
                } + carriedHere(ModBlocks.OFFICE_BOOK)
                game.books[level.id] = lying.distinct().map(::toCell)
                game.returnedBooks[level.id] = returns.filter { world.getBlockState(it).isOf(ModBlocks.OFFICE_BOOK) }.map(::toCell)
            }
        }
        game.lightsOut.clear()
        KeycardHooks.lightsOut(server).forEach { (key, mode) -> game.lightsOut[key] = mode.asString() }
        // Readers and loot only ever go one way in a game: what was saved stays.
        game.readersUnlocked += KeycardHooks.unlockedReaders(server)
        game.lootTaken += LootSpawner.taken(server)
        game.achievements.clear()
        game.achievements += AchievementHooks.earnedIds()
        state.markDirty()
    }

    private fun points(level: LevelDefinition, tag: String): List<BlockPos> =
        level.spawnPoints.filter { tag in it.tags }.map { BlockPos.ofFloored(it.position.x, it.position.y, it.position.z) }

    private fun startOf(level: LevelDefinition) = level.spawnPoints.firstOrNull { RespawnHooks.TAG in it.tags }

    private fun world(server: MinecraftServer, level: LevelDefinition): ServerWorld? =
        server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier(level.dimension)))

    private fun toCell(pos: BlockPos) = SavedGame.Cell(pos.x, pos.y, pos.z)
    private fun toPos(cell: SavedGame.Cell) = BlockPos(cell.x, cell.y, cell.z)

    private fun state(server: MinecraftServer): SaveState =
        server.overworld.persistentStateManager.getOrCreate(SaveState::read, ::SaveState, SAVE_ID)

    /** The world's save of [SavedGame]. */
    class SaveState : PersistentState() {
        val game = SavedGame()

        override fun writeNbt(nbt: NbtCompound): NbtCompound {
            nbt.putBoolean("active", game.active)
            game.levelId?.let { nbt.putString("level", it) }
            nbt.putInt("level_order", game.levelOrder)
            nbt.put("valve_codes", ints(game.valveCodes))
            nbt.put("valve_turns", ints(game.valveTurns))
            nbt.put("boxes", cells(game.boxes))
            nbt.put("shelf_slots", ints(game.shelfSlots))
            nbt.put("books", cells(game.books))
            nbt.put("returned_books", cells(game.returnedBooks))
            nbt.put("lights_out", NbtCompound().apply { game.lightsOut.forEach { (key, mode) -> putString(key, mode) } })
            nbt.put("readers_unlocked", strings(game.readersUnlocked))
            nbt.put("loot_taken", strings(game.lootTaken))
            nbt.put("achievements", NbtCompound().apply { game.achievements.forEach { (id, ids) -> put(id, strings(ids)) } })
            return nbt
        }

        companion object {
            fun read(nbt: NbtCompound): SaveState = SaveState().apply {
                game.active = nbt.getBoolean("active")
                game.restoreLevel(if (nbt.contains("level")) nbt.getString("level") else null, nbt.getInt("level_order"))
                readInts(nbt.getCompound("valve_codes"), game.valveCodes)
                readInts(nbt.getCompound("valve_turns"), game.valveTurns)
                readCells(nbt.getCompound("boxes"), game.boxes)
                readInts(nbt.getCompound("shelf_slots"), game.shelfSlots)
                readCells(nbt.getCompound("books"), game.books)
                readCells(nbt.getCompound("returned_books"), game.returnedBooks)
                val lights = nbt.getCompound("lights_out")
                for (key in lights.keys) game.lightsOut[key] = lights.getString(key)
                game.readersUnlocked += readStrings(nbt.getList("readers_unlocked", NbtElement.STRING_TYPE.toInt()))
                game.lootTaken += readStrings(nbt.getList("loot_taken", NbtElement.STRING_TYPE.toInt()))
                val achievements = nbt.getCompound("achievements")
                for (id in achievements.keys) {
                    game.achievements[id] = readStrings(achievements.getList(id, NbtElement.STRING_TYPE.toInt())).toSet()
                }
            }

            private fun strings(values: Collection<String>) = NbtList().apply { values.forEach { add(NbtString.of(it)) } }
            private fun readStrings(list: NbtList) = (0 until list.size).map { list.getString(it) }

            private fun ints(map: Map<String, List<Int>>) = NbtCompound().apply {
                map.forEach { (key, values) -> put(key, NbtList().apply { values.forEach { add(NbtInt.of(it)) } }) }
            }
            private fun readInts(nbt: NbtCompound, into: MutableMap<String, List<Int>>) {
                for (key in nbt.keys) {
                    val list = nbt.getList(key, NbtElement.INT_TYPE.toInt())
                    into[key] = (0 until list.size).map { list.getInt(it) }
                }
            }

            private fun cells(map: Map<String, List<SavedGame.Cell>>) = NbtCompound().apply {
                map.forEach { (key, cells) -> put(key, strings(cells.map { it.toString() })) }
            }
            private fun readCells(nbt: NbtCompound, into: MutableMap<String, List<SavedGame.Cell>>) {
                for (key in nbt.keys) {
                    into[key] = readStrings(nbt.getList(key, NbtElement.STRING_TYPE.toInt())).mapNotNull(SavedGame.Cell::parse)
                }
            }
        }
    }
}
