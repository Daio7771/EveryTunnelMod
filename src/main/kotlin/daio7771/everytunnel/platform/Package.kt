/**
 * Minecraft-version-dependent glue.
 *
 * Code that touches Minecraft or Fabric APIs that change between Minecraft
 * versions (identifiers, resource loading, ...) lives here, so a port to
 * another version mostly means rewriting this package. Everything else
 * (config, level model, parsers, managers) uses plain Kotlin types.
 */
package daio7771.everytunnel.platform
