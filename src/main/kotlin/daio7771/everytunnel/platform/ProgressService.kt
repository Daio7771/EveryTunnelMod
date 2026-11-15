package daio7771.everytunnel.platform

import daio7771.everytunnel.core.ch2l1.Ch2L1State
import daio7771.everytunnel.core.ending.RunSummary
import net.minecraft.entity.player.PlayerEntity

/**
 * The player's progress, as the rest of the mod sees it: pieces, notes read, scenes seen,
 * Level 3's books and the checkpoint (the data in `core.progress.PlayerProgress`). It survives
 * death, is saved with the world and reaches the player's client, so both sides can read it;
 * only the server changes it. Everything goes through this interface, so that a single class
 * ([CardinalProgress]) knows how it is stored.
 */
interface ProgressService {

    fun hasPiece(player: PlayerEntity, levelId: String, pieceId: String): Boolean

    fun pieceCount(player: PlayerEntity, levelId: String): Int

    /** True if the piece is new, false if the player already had it. */
    fun addPiece(player: PlayerEntity, levelId: String, pieceId: String): Boolean

    fun hasReadNote(player: PlayerEntity, noteId: String): Boolean

    /** True if it is the first time the player reads the note. */
    fun markNoteRead(player: PlayerEntity, noteId: String): Boolean

    fun hasSeenScene(player: PlayerEntity, sceneId: String): Boolean

    /** True if it is the first time the player sees the scene. */
    fun markSceneSeen(player: PlayerEntity, sceneId: String): Boolean

    fun level3Books(player: PlayerEntity): Int

    fun setLevel3Books(player: PlayerEntity, books: Int)

    fun checkpoint(player: PlayerEntity): String?

    fun setCheckpoint(player: PlayerEntity, checkpointId: String?)

    /** Level 4 as it was left, as text (`core.save.LoungeSave`), or null. */
    fun lounge(player: PlayerEntity): String?

    fun setLounge(player: PlayerEntity, text: String?)

    /** Where the player is in Chapter 2's Level 1 ([Ch2L1State.START] before they begin it). */
    fun ch2L1State(player: PlayerEntity): Ch2L1State

    fun setCh2L1State(player: PlayerEntity, state: Ch2L1State)

    /** The player is playing Chapter 2's Level 1, whose rules apply to them (`PlayerProgress.ch2l1Active`). */
    fun ch2L1Active(player: PlayerEntity): Boolean

    fun setCh2L1Active(player: PlayerEntity, active: Boolean)

    /** A named whole number of the player's Chapter 2 Level 1 (`PlayerProgress.ch2l1Counter`); 0 if it has none. */
    fun ch2L1Counter(player: PlayerEntity, key: String): Int

    fun setCh2L1Counter(player: PlayerEntity, key: String, value: Int)

    /** Forgets the counters whose names start with [prefix] (every one if it is empty). */
    fun clearCh2L1Counters(player: PlayerEntity, prefix: String = "")

    /** Adds [ticks] played to this game's total. Not synced to the client: it is counted every tick. */
    fun addPlayTicks(player: PlayerEntity, ticks: Long)

    /** A death more in this game's total. */
    fun addDeath(player: PlayerEntity)

    /** The time played and the deaths of this game so far, summed over its sessions. */
    fun runSummary(player: PlayerEntity): RunSummary

    /** Notes what the player carries, for going back to a checkpoint. */
    fun saveInventory(player: PlayerEntity)

    /** What the player carried at the checkpoint. */
    fun restoreInventory(player: PlayerEntity)

    /** A new game: the player's progress is forgotten. */
    fun reset(player: PlayerEntity)

    /** One line for the debug command. */
    fun describe(player: PlayerEntity): String

    companion object {
        fun get(): ProgressService = CardinalProgress
    }
}
