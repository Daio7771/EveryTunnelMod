package daio7771.everytunnel.config

import daio7771.everytunnel.EveryTunnel
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Owns the loaded configuration and its file on disk.
 *
 * File: `<config dir>/every_tunnel/gameplay.json`
 * - Missing file: created with the defaults.
 * - Invalid values: logged as warnings, defaults used for those values only.
 * - Not valid JSON: logged as an error, all defaults used, file left untouched.
 * - Keys missing (e.g. added in a newer mod version): defaults used and the keys
 *   are written into the file, but only when it had no other problems, so a file
 *   with typos is never overwritten.
 */
object ConfigManager {

    const val GAMEPLAY_FILE_NAME: String = "gameplay.json"

    /** The current gameplay values. Never null; defaults until [load] runs. */
    @Volatile
    var gameplay: GameplayConfig = GameplayConfig()
        private set

    fun load(configDir: Path) {
        gameplay = loadGameplay(configDir.resolve(EveryTunnel.MOD_ID).resolve(GAMEPLAY_FILE_NAME))
    }

    private fun loadGameplay(file: Path): GameplayConfig {
        val log = EveryTunnel.LOGGER
        try {
            if (Files.notExists(file)) {
                val defaults = GameplayConfig()
                write(file, defaults)
                log.info("Created default gameplay config at {}", file)
                return defaults
            }

            val result = GameplayConfigParser.parse(Files.readString(file))
            result.problems.forEach { log.warn("{}: {}", GAMEPLAY_FILE_NAME, it) }

            if (result.syntaxError) {
                log.error("{} could not be read; using built-in defaults for every value. The file was left untouched.", file)
            } else if (result.problems.isEmpty() && result.missingKeys.isNotEmpty()) {
                write(file, result.config)
                log.info("Added missing key(s) with default values to {}: {}", file, result.missingKeys.joinToString())
            }
            log.info("Loaded gameplay config from {}", file)
            return result.config
        } catch (e: IOException) {
            log.error("Could not read or write {}; using built-in defaults", file, e)
            return GameplayConfig()
        }
    }

    /** Writes through a temporary file so a crash mid-write cannot leave a half written config. */
    private fun write(file: Path, config: GameplayConfig) {
        Files.createDirectories(file.parent)
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(temp, GameplayConfigParser.toJson(config))
        Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}
