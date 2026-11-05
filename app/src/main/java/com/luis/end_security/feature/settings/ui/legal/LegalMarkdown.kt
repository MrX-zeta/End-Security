package com.luis.end_security.feature.settings.ui.legal

/** Un tramo de texto; [bold] si venía entre `**dobles asteriscos**`. */
data class Span(val text: String, val bold: Boolean = false)

/** Un campo de una tarjeta de tabla: «Qué hace: …». */
data class CardField(val label: String, val spans: List<Span>)

sealed interface Block {
    /** Un párrafo (con negritas en línea). */
    data class Paragraph(val spans: List<Span>) : Block

    /** Una línea completa en `**negritas**`: subtítulo dentro de una sección. */
    data class Subtitle(val text: String) : Block

    /** `- elemento`. */
    data class Bullet(val spans: List<Span>) : Block

    /** `1. elemento`; [number] es el de la fuente. */
    data class Numbered(val number: Int, val spans: List<Span>) : Block

    /** Una fila de tabla como tarjeta: la primera celda en negrita y debajo «Encabezado: celda» por cada una de las demás. */
    data class Card(val title: List<Span>, val fields: List<CardField>) : Block
}

/** Una sección `## …` (se muestra como acordeón). */
data class LegalSection(val title: String, val blocks: List<Block>)

/**
 * Un documento legal ya interpretado.
 * - [title]: la línea `# …`.
 * - [meta]: la primera línea tras el título (p. ej. «End Security · Última actualización: 4 de noviembre de 2026»), sin negritas.
 * - [intro]: lo que va entre [meta] y la primera sección.
 * - [summary]: la sección `## Resumen`, aparte (tarjeta siempre abierta).
 * - [sections]: el resto de las secciones `##`, en orden.
 */
data class LegalDocumentModel(
    val title: String,
    val meta: String,
    val intro: List<Block>,
    val summary: List<Block>,
    val sections: List<LegalSection>,
)

/**
 * Renderizador mínimo de Markdown (tarea 22): solo lo que usan los dos documentos de `docs/legal`. **No** interpreta enlaces, HTML, cursivas ni
 * código; lo que no reconoce queda como párrafo con su texto tal cual (nada se pierde en silencio).
 *
 * Reconoce: `#` (título), `##` (sección), una línea completa en `**negritas**` (subtítulo), `**negritas**` en línea, viñetas `- `,
 * listas `1. ` y tablas (una tarjeta por fila). Admite saltos de línea `\n` y `\r\n`.
 */
object LegalMarkdown {

    const val SUMMARY_TITLE = "Resumen"

    private val numbered = Regex("""^(\d+)\.\s+(.*)$""")
    private val subtitle = Regex("""^\*\*([^*]+)\*\*$""")
    private val separatorCell = Regex("""^:?-{3,}:?$""")

    fun parse(markdown: String): LegalDocumentModel {
        val lines = markdown.lines().map { it.trimEnd() }
        var title = ""
        var meta = ""
        val intro = mutableListOf<Block>()
        var summary = emptyList<Block>()
        val sections = mutableListOf<LegalSection>()
        var currentTitle: String? = null
        var current = mutableListOf<Block>()
        var sawMeta = false

        fun closeSection() {
            val name = currentTitle ?: return
            if (name.equals(SUMMARY_TITLE, ignoreCase = true)) summary = current.toList() else sections += LegalSection(name, current.toList())
            currentTitle = null
            current = mutableListOf()
        }

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            when {
                line.isBlank() -> Unit
                line.startsWith("## ") -> {
                    closeSection()
                    currentTitle = line.removePrefix("## ").trim()
                }
                line.startsWith("# ") && title.isEmpty() -> title = line.removePrefix("# ").trim()
                currentTitle == null && title.isNotEmpty() && !sawMeta -> {
                    meta = stripBold(line)
                    sawMeta = true
                }
                line.startsWith("|") -> {
                    // Una tabla: encabezado, separador y filas hasta que se acaban las líneas con `|`.
                    val table = mutableListOf<String>()
                    while (index < lines.size && lines[index].startsWith("|")) table += lines[index++]
                    (if (currentTitle == null) intro else current) += cards(table)
                    continue
                }
                else -> (if (currentTitle == null) intro else current) += block(line)
            }
            index++
        }
        closeSection()
        return LegalDocumentModel(title, meta, intro, summary, sections)
    }

    private fun block(line: String): Block {
        subtitle.matchEntire(line)?.let { return Block.Subtitle(it.groupValues[1].trim()) }
        if (line.startsWith("- ")) return Block.Bullet(spans(line.removePrefix("- ").trim()))
        numbered.matchEntire(line)?.let { return Block.Numbered(it.groupValues[1].toInt(), spans(it.groupValues[2].trim())) }
        return Block.Paragraph(spans(line.trim()))
    }

    private fun cells(row: String): List<String> = row.trim().removePrefix("|").removeSuffix("|").split("|").map { it.trim() }

    /** Encabezado + separador + filas → una [Block.Card] por fila. Una tabla sin encabezado y separador se deja como párrafos. */
    private fun cards(table: List<String>): List<Block> {
        if (table.size < 2) return table.map { Block.Paragraph(spans(it)) }
        val header = cells(table[0])
        val separator = cells(table[1])
        if (separator.isEmpty() || !separator.all { separatorCell.matches(it) }) return table.map { Block.Paragraph(spans(it)) }
        return table.drop(2).map { row ->
            val values = cells(row)
            Block.Card(
                title = spans(values.firstOrNull().orEmpty()),
                fields = values.drop(1).mapIndexed { i, value -> CardField(header.getOrElse(i + 1) { "" }, spans(value)) },
            )
        }
    }

    /** Separa por `**`: lo de dentro va en negrita. Un `**` sin pareja queda como texto literal. */
    fun spans(text: String): List<Span> {
        val parts = text.split("**")
        // Con un número par de partes hay un `**` sin cerrar: se devuelve literal (nada se pierde).
        if (parts.size % 2 == 0) return listOf(Span(text))
        return parts.mapIndexedNotNull { i, part -> if (part.isEmpty()) null else Span(part, bold = i % 2 == 1) }
    }

    private fun stripBold(text: String): String = text.replace("**", "").trim()

    /** El texto plano de una lista de tramos (para TalkBack y las pruebas). */
    fun plain(spans: List<Span>): String = spans.joinToString("") { it.text }
}
