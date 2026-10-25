# Cómo contribuir

*[English version](../en/contributing.md) · [Todas las páginas](README.md)*

Cómo se trabaja en Every Tunnel. Estas reglas valen igual para personas que para
asistentes de IA; la [última sección](#notas-para-asistentes-de-ia) añade lo que un
asistente necesita saber.

## Idioma

- **Código, comentarios, mensajes de commit, textos del juego: inglés.**
- **Documentación:** inglés y español, las mismas páginas en `docs/en/` y `docs/es/`.
  Cambia las dos en el mismo commit.
- El juego en sí está solo en inglés por ahora (`en_us.json`); hay prevista una traducción
  al español para más adelante. La guía del juego ya está en los dos.

## Páginas públicas

`README.md` (GitHub), `INSTALL.md` y `CURSEFORGE.md` (la descripción de CurseForge) son lo que
ven los jugadores. Están solo en inglés, con tono directo, y sin nada técnico: eso va en
`docs/`. La descripción de CurseForge repite lo que un jugador necesita y enlaza a GitHub con
URLs completas (allí no funcionan los enlaces relativos). Cuando cambie algo que ve el jugador
(requisitos, características, mods recomendados, controles), actualiza las tres.

## Código

- **Kotlin** para todo; Java solo para las mixins.
- **La lógica en Kotlin puro, el pegamento en `platform`.** Todo lo que se pueda expresar
  sin tipos de Minecraft (reglas, decisiones, parseo, matemáticas) va en `config`, `core`,
  `systems` o `entities`, con tests unitarios. `platform` y el cliente lo alimentan y
  aplican su resultado.
- **Robusto y claro antes que corto.** Los archivos largos están bien. Mejor nombres
  explícitos y un flujo legible que líneas ingeniosas.
- **Cada archivo empieza con un KDoc** que dice qué hace, cómo encaja y de dónde vienen sus
  datos. Las constantes públicas y las decisiones no evidentes llevan un comentario que
  dice *por qué*.
- **Nunca colgarse por la entrada.** Los archivos de configuración y de nivel informan de
  los problemas con su ruta JSON y usan los valores por defecto (`util.JsonFields`).
- **Vanilla sigue siendo vanilla.** Condiciona cada función a `WorldRules.active` /
  `WorldRules.appliesTo(...)`.
- **Mixins solo donde Fabric API no tiene evento.** Cada una documenta su objetivo y su
  motivo.
- **Hilo del servidor** para el estado del juego; pasa los paquetes al hilo del cliente con
  `client.execute`.
- **El contenido generado sigue siendo generado.** Cambia la herramienta Python y vuelve a
  ejecutarla; nunca edites a mano los JSON, PNG u OGG generados.

## Recursos y licencias

- Código y recursos propios: MIT.
- Grabaciones: **solo CC0**, con su fuente en `tools/sounds/cc0/SOURCES.md`.
- Lo que necesite crédito (hoy solo la canción del ascensor) va en
  `tools/sounds/licensed/`, `CREDITS.md`, `fabric.mod.json` y los créditos finales.
- **Nada de música ni recursos generados con IA en el juego.** El concept art hecho con IA
  solo se puede usar como referencia (`tools/entity/reference/`), nunca se distribuye.

## Pruebas

- `./gradlew build` debe pasar (ejecuta los tests unitarios).
- Los cambios que tocan Minecraft se comprueban **en el juego**. Escribe pasos de prueba
  claros: qué hacer y qué debería pasar.
- Para ajustar, usa el [registro `[playtest]`](debugging.md#el-registro-de-playtest).

## Commits

- Un commit por paso terminado, en inglés, con el prefijo de la fase:
  `Phase 7: main menu theme`.
- Commit y push solo cuando se pida.

## Páginas de la guía del jugador

Las páginas de `docs/<idioma>/guide/` las dibuja el juego (menú principal, GUIDE; la
compilación las copia al jar y las dibuja `client.screen.GuideScreen`), así que
usan un subconjunto pequeño de Markdown que se lee bien tanto en GitHub como en el juego:

- `# Título`: uno por página, la primera línea.
- `## Sección` y `### Subsección`.
- Párrafos, separados por una línea en blanco. Las líneas de un párrafo se unen.
- Listas con `- ` (un nivel), listas numeradas con `1. `.
- `**negrita**` y `*cursiva*`.
- `---` para una línea separadora.

Nada más: sin enlaces, tablas, imágenes, código ni HTML (`GuideMarkdownTest` hace fallar la
compilación si una página los usa, o si los dos idiomas no tienen los mismos archivos). Nombra las teclas con su valor por
defecto y el ajuste ("la tecla Linterna, F por defecto"), porque los jugadores pueden
cambiarlas.

Reglas para el contenido:

- Escrito para jugadores: corto, cercano, un poco de humor seco está bien.
- **Nada de lore.** La guía explica cómo jugar, no la historia.
- Pistas, no números, salvo en la página *Datos técnicos*.
- Nunca destripar lo que hace el Pale man más allá de lo que un jugador necesita para
  sobrevivir.

## Notas para asistentes de IA

- **Empieza aquí:** lee [Arquitectura](architecture.md) y la cabecera KDoc de cada archivo
  que toques. Las cabeceras se mantienen exactas; fíate más de ellas que de la memoria.
- **Pregunta antes de decidir.** Antes de cada paso, hazle al usuario una ronda agrupada de
  preguntas de diseño. Nunca decidas solo lo que el brief no fija (diseño, textos, ajustes,
  alcance). Al usuario le gustan las rondas completas.
- **Responde al usuario en español**; el código y los textos del juego siguen en inglés.
- **Nunca abras el cliente de Minecraft.** Compila y ejecuta los tests, y luego dale al
  usuario pasos de prueba; el usuario prueba en el juego.
- **Commit y push solo cuando el usuario lo diga.** El remoto es SSH, así que el push
  funciona desde una sesión.
- **Envía los artefactos** (borradores de audio, builds, vistas previas) al usuario para que
  los revise.
- **Uso razonable de tokens:** sin exploración innecesaria, sin subagentes salvo que se
  pidan.
- `PROJECT_STATE.md` en la raíz del repositorio (no está en git) es el relevo entre
  sesiones: progreso, preguntas abiertas, decisiones. Actualízalo cuando se pida.
- Cuando un cambio deje mal una página de la documentación, arréglala (en los dos idiomas)
  en el mismo paso.
