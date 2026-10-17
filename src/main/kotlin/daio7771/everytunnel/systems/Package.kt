/**
 * Gameplay systems that run every tick.
 *
 * Planned contents (later phases):
 * - `ResourceSystem`: stamina, thirst and flashlight battery drain/regen.
 * - `NoiseSystem`: noise emitted by the player (walking, running, items),
 *   consumed by hearing-based enemies such as the Pale man.
 * - `LookDetection`: whether the player is looking at a given entity/point,
 *   for enemies that react to being watched.
 *
 * All tuning values come from JSON config, not from code. Empty in Phase 0.
 */
package daio7771.everytunnel.systems
