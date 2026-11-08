package com.luis.end_security.feature.settings.ui.legal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LegalMarkdownTest {

    private fun parse(vararg lines: String) = LegalMarkdown.parse(lines.joinToString("\n"))

    private fun plain(block: Block): String = when (block) {
        is Block.Paragraph -> LegalMarkdown.plain(block.spans)
        is Block.Bullet -> LegalMarkdown.plain(block.spans)
        is Block.Numbered -> LegalMarkdown.plain(block.spans)
        is Block.Subtitle -> block.text
        is Block.Card -> LegalMarkdown.plain(block.title)
    }

    // --- Cada elemento ---

    @Test
    fun `el titulo y la linea de fecha`() {
        val doc = parse("# Aviso de privacidad integral", "", "**End Security** · Última actualización: 4 de noviembre de 2026", "", "## Uno", "Texto")
        assertEquals("Aviso de privacidad integral", doc.title)
        assertEquals("End Security · Última actualización: 4 de noviembre de 2026", doc.meta)
    }

    @Test
    fun `lo que va entre la fecha y la primera seccion es la introduccion`() {
        val doc = parse("# T", "**E** · fecha", "", "Al crear tu cuenta aceptas estas condiciones.", "", "## Uno", "x")
        assertEquals(1, doc.intro.size)
        assertEquals("Al crear tu cuenta aceptas estas condiciones.", plain(doc.intro.single()))
    }

    @Test
    fun `las secciones con doble numeral van en orden y con sus bloques`() {
        val doc = parse("# T", "m", "## 1. Primera", "uno", "## 2. Segunda", "dos", "tres")
        assertEquals(listOf("1. Primera", "2. Segunda"), doc.sections.map { it.title })
        assertEquals(listOf("uno"), doc.sections[0].blocks.map(::plain))
        assertEquals(listOf("dos", "tres"), doc.sections[1].blocks.map(::plain))
    }

    @Test
    fun `una linea completa en negritas es un subtitulo`() {
        val doc = parse("# T", "m", "## S", "**Datos de cuenta**", "texto")
        assertEquals(Block.Subtitle("Datos de cuenta"), doc.sections.single().blocks[0])
        assertTrue(doc.sections.single().blocks[1] is Block.Paragraph)
    }

    @Test
    fun `negritas en linea parten el parrafo en tramos`() {
        val spans = LegalMarkdown.spans("Hola **mundo** y **más** fin")
        assertEquals(
            listOf(Span("Hola ", false), Span("mundo", true), Span(" y ", false), Span("más", true), Span(" fin", false)),
            spans,
        )
    }

    @Test
    fun `un parrafo que empieza con negritas pero sigue con texto no es subtitulo`() {
        val doc = parse("# T", "m", "## S", "**Sin relación abogado-cliente.** End Security es un proyecto.")
        val block = doc.sections.single().blocks.single() as Block.Paragraph
        assertEquals(Span("Sin relación abogado-cliente.", true), block.spans[0])
        assertEquals(Span(" End Security es un proyecto.", false), block.spans[1])
    }

    @Test
    fun `un doble asterisco sin pareja queda como texto literal`() {
        assertEquals(listOf(Span("a ** b")), LegalMarkdown.spans("a ** b"))
    }

    @Test
    fun `las vinetas`() {
        val doc = parse("# T", "m", "## S", "- uno **fuerte**", "- dos")
        val blocks = doc.sections.single().blocks
        assertTrue(blocks.all { it is Block.Bullet })
        assertEquals(listOf("uno fuerte", "dos"), blocks.map(::plain))
    }

    @Test
    fun `las listas numeradas conservan su numero`() {
        val doc = parse("# T", "m", "## S", "1. primero", "2. segundo", "10. décimo")
        val blocks = doc.sections.single().blocks.map { it as Block.Numbered }
        assertEquals(listOf(1, 2, 10), blocks.map { it.number })
        assertEquals(listOf("primero", "segundo", "décimo"), blocks.map { LegalMarkdown.plain(it.spans) })
    }

    @Test
    fun `una tabla se convierte en una tarjeta por fila`() {
        val doc = parse(
            "# T", "m", "## S",
            "| Encargado | Qué hace | Datos que recibe |",
            "|---|---|---|",
            "| **Correo:** Resend | Envía los correos | Tu correo |",
            "| **Analítica web** | No usamos nada | Ninguno |",
            "",
            "Después de la tabla.",
        )
        val blocks = doc.sections.single().blocks
        assertEquals(3, blocks.size)
        val first = blocks[0] as Block.Card
        assertEquals(listOf(Span("Correo:", true), Span(" Resend", false)), first.title)
        assertEquals(listOf("Qué hace", "Datos que recibe"), first.fields.map { it.label })
        assertEquals("Envía los correos", LegalMarkdown.plain(first.fields[0].spans))
        assertEquals("Tu correo", LegalMarkdown.plain(first.fields[1].spans))
        assertEquals("Analítica web", LegalMarkdown.plain((blocks[1] as Block.Card).title))
        assertEquals("Después de la tabla.", plain(blocks[2]))
    }

    @Test
    fun `una linea con barras pero sin separador de tabla no se pierde`() {
        val doc = parse("# T", "m", "## S", "| solo una linea |")
        assertEquals(listOf("| solo una linea |"), doc.sections.single().blocks.map(::plain))
    }

    @Test
    fun `la seccion Resumen va aparte y no entre las secciones`() {
        val doc = parse("# T", "m", "## Resumen", "- punto 1", "- punto 2", "## 1. Uno", "texto")
        assertEquals(listOf("punto 1", "punto 2"), doc.summary.map(::plain))
        assertEquals(listOf("1. Uno"), doc.sections.map { it.title })
    }

    @Test
    fun `sin Resumen el resumen esta vacio`() {
        val doc = parse("# T", "m", "## 1. Uno", "texto")
        assertTrue(doc.summary.isEmpty())
    }

    @Test
    fun `admite saltos de linea de Windows`() {
        val doc = LegalMarkdown.parse("# T\r\n\r\nm\r\n\r\n## Resumen\r\n- a\r\n\r\n## S\r\nb\r\n")
        assertEquals("T", doc.title)
        assertEquals("m", doc.meta)
        assertEquals(listOf("a"), doc.summary.map(::plain))
        assertEquals(listOf("b"), doc.sections.single().blocks.map(::plain))
    }

    @Test
    fun `la linea de fecha conserva la negrita de End Security`() {
        val doc = parse("# T", "**End Security** · Última actualización: 4 de noviembre de 2026", "## S", "x")
        assertEquals("End Security · Última actualización: 4 de noviembre de 2026", doc.meta)
        assertEquals(Span("End Security", bold = true), doc.metaSpans.first())
        assertEquals(Span(" · Última actualización: 4 de noviembre de 2026"), doc.metaSpans.last())
    }

    @Test
    fun `un documento vacio no falla`() {
        val doc = LegalMarkdown.parse("")
        assertEquals("", doc.title)
        assertTrue(doc.sections.isEmpty() && doc.summary.isEmpty() && doc.intro.isEmpty())
    }

    @Test
    fun `los corchetes de datos pendientes se conservan tal cual`() {
        val doc = parse("# T", "m", "## S", "Contacto: [privacidad@correo.com] y [Nombre completo del integrante 1].")
        assertEquals("Contacto: [privacidad@correo.com] y [Nombre completo del integrante 1].", plain(doc.sections.single().blocks.single()))
    }

    // --- Los documentos reales ---

    private fun legal(name: String): String {
        val candidates = listOf(File("../docs/legal/$name"), File("docs/legal/$name"))
        return candidates.first { it.exists() }.readText(Charsets.UTF_8)
    }

    @Test
    fun `el aviso de privacidad real se interpreta completo`() {
        val doc = LegalMarkdown.parse(legal("aviso-de-privacidad.md"))
        assertEquals("Aviso de privacidad integral", doc.title)
        assertTrue(doc.meta.contains("Última actualización"))
        assertEquals(5, doc.summary.size)
        assertEquals(9, doc.sections.size)
        assertTrue(doc.sections.first().title.startsWith("1. "))
        // La tabla de encargados: tres tarjetas, con «Qué hace» y «Datos que recibe».
        val cards = doc.sections.flatMap { it.blocks }.filterIsInstance<Block.Card>()
        assertEquals(3, cards.size)
        assertTrue(cards.all { card -> card.fields.map { it.label } == listOf("Qué hace", "Datos que recibe") })
        // Nada de Markdown crudo queda en pantalla.
        val everything = (doc.intro + doc.summary + doc.sections.flatMap { it.blocks })
        assertTrue(everything.none { it is Block.Paragraph && LegalMarkdown.plain(it.spans).contains("**") })
        assertTrue(everything.none { it is Block.Paragraph && LegalMarkdown.plain(it.spans).startsWith("|") })
    }

    @Test
    fun `los terminos reales se interpretan completos`() {
        val doc = LegalMarkdown.parse(legal("terminos-y-condiciones.md"))
        assertEquals("Términos y condiciones de uso", doc.title)
        assertEquals(5, doc.summary.size)
        assertEquals(9, doc.sections.size)
        assertEquals("Contacto", doc.sections.last().title)
        assertEquals(1, doc.intro.size)
        val everything = (doc.intro + doc.summary + doc.sections.flatMap { it.blocks })
        assertTrue(everything.none { it is Block.Paragraph && LegalMarkdown.plain(it.spans).contains("**") })
    }
}
