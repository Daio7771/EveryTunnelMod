package daio7771.everytunnel.client

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator

/**
 * Data generation entrypoint (run with `./gradlew runDatagen`).
 * No providers yet; they are added when the mod gains blocks, items,
 * sounds or loot tables.
 */
object EveryTunnelDataGenerator : DataGeneratorEntrypoint {

    override fun onInitializeDataGenerator(fabricDataGenerator: FabricDataGenerator) {
        @Suppress("UNUSED_VARIABLE")
        val pack = fabricDataGenerator.createPack()
    }
}
