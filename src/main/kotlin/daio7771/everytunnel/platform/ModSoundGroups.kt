package daio7771.everytunnel.platform

import net.minecraft.sound.BlockSoundGroup

/**
 * Block sound groups for the props, one per material (sounds made by
 * `tools/sounds/catalog.py`, group "Materials"). Breaking, placing, hitting and landing
 * share the material's impact sound; walking on it plays its step sound.
 */
object ModSoundGroups {

    val CARDBOARD = group(ModSounds.CARDBOARD)
    val PLASTIC = group(ModSounds.PLASTIC)
    val HOLLOW_METAL = group(ModSounds.HOLLOW_METAL)
    val METAL_PIPE = group(ModSounds.METAL_PIPE)
    val SHEET_METAL = group(ModSounds.SHEET_METAL)
    val WIRE_MESH = group(ModSounds.WIRE_MESH)
    val WOOD = group(ModSounds.WOOD)
    val PUDDLE = group(ModSounds.PUDDLE)

    private fun group(sounds: ModSounds.MaterialSounds) =
        BlockSoundGroup(1.0f, 1.0f, sounds.impact, sounds.step, sounds.impact, sounds.impact, sounds.impact)
}
