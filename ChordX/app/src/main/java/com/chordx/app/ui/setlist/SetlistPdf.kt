package com.chordx.app.ui.setlist

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.chordx.app.chordpro.ChordProDocument
import com.chordx.app.chordpro.ChordRenderer
import com.chordx.app.chordpro.Line
import com.chordx.app.chordpro.Section
import java.io.OutputStream

enum class ChartLineKind { LABEL, CHORDS, LYRICS, COMMENT, PLAIN }

/** One line of a chart, already transposed, in fixed-width layout (chords sit above their lyrics). */
data class ChartTextLine(val text: String, val kind: ChartLineKind)

/** Everything the PDF shows for one slot in the setlist. */
data class PdfSong(
    val number: Int,
    val title: String,
    val artist: String?,
    val key: String?,
    val details: String?,
    val note: String?,
    val chart: List<ChartTextLine>,
)

/** Chords-over-lyrics text for [document], with chords shown through [renderer] (so transposed). */
fun chartTextLines(document: ChordProDocument, renderer: ChordRenderer): List<ChartTextLine> {
    val out = mutableListOf<ChartTextLine>()
    document.sections.forEach { section -> addSection(out, section, renderer) }
    while (out.lastOrNull()?.text?.isBlank() == true) out.removeAt(out.lastIndex)
    return out
}

private fun addSection(out: MutableList<ChartTextLine>, section: Section, renderer: ChordRenderer) {
    section.label?.let { out += ChartTextLine(it, ChartLineKind.LABEL) }
    section.lines.forEach { line ->
        when (line) {
            is Line.Blank -> out += ChartTextLine("", ChartLineKind.PLAIN)
            is Line.Comment -> out += ChartTextLine(line.text, ChartLineKind.COMMENT)
            is Line.Preformatted -> out += ChartTextLine(line.text, ChartLineKind.PLAIN)
            is Line.Grid -> out += ChartTextLine(
                line.tokens.joinToString(" ") { t -> t.chord?.let(renderer::display) ?: t.lyrics }.trim(),
                ChartLineKind.CHORDS,
            )
            is Line.Lyrics -> addLyrics(out, line, renderer)
        }
    }
    out += ChartTextLine("", ChartLineKind.PLAIN)
}

private fun addLyrics(out: MutableList<ChartTextLine>, line: Line.Lyrics, renderer: ChordRenderer) {
    val chords = StringBuilder()
    val lyrics = StringBuilder()
    line.segments.forEach { seg ->
        seg.chord?.let { ref ->
            if (chords.isNotEmpty() && chords.length >= lyrics.length) while (lyrics.length <= chords.length) lyrics.append(' ')
            while (chords.length < lyrics.length) chords.append(' ')
            chords.append(renderer.display(ref))
        }
        lyrics.append(seg.lyrics)
    }
    if (chords.isNotBlank()) out += ChartTextLine(chords.toString().trimEnd(), ChartLineKind.CHORDS)
    if (lyrics.isNotBlank() || chords.isBlank()) out += ChartTextLine(lyrics.toString().trimEnd(), ChartLineKind.LYRICS)
}

private const val PAGE_WIDTH = 595
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48f
private const val FOOTER = 28f

private class PdfLine(val text: String, val right: String?, val paint: Paint, val indent: Float, val height: Float)

/** Lines that stay on one page together (unless taller than a page), or a forced page break. */
private sealed interface Item {
    data object Break : Item
    class Group(val lines: List<PdfLine>) : Item
}

private fun paint(size: Float, face: Typeface, colour: Int = Color.BLACK) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    textSize = size; typeface = face; color = colour
}

private val title = paint(22f, Typeface.DEFAULT_BOLD)
private val sub = paint(12f, Typeface.DEFAULT, Color.DKGRAY)
private val songPaint = paint(14f, Typeface.DEFAULT_BOLD)
private val keyPaint = paint(14f, Typeface.DEFAULT_BOLD, Color.rgb(0x1F, 0x4E, 0x8C))
private val small = paint(11f, Typeface.DEFAULT, Color.DKGRAY)
private val note = paint(11f, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), Color.DKGRAY)
private val footer = paint(9f, Typeface.DEFAULT, Color.GRAY)
private val songTitle = paint(18f, Typeface.DEFAULT_BOLD)
private val label = paint(11f, Typeface.DEFAULT_BOLD, Color.rgb(0x1F, 0x4E, 0x8C))
private val mono = paint(10f, Typeface.MONOSPACE)
private val monoChord = paint(10f, Typeface.create(Typeface.MONOSPACE, Typeface.BOLD), Color.rgb(0x1F, 0x4E, 0x8C))
private val comment = paint(10f, Typeface.create(Typeface.DEFAULT, Typeface.ITALIC), Color.DKGRAY)

/** Splits [text] into lines that fit [width], breaking on spaces. */
private fun wrap(text: String, paint: Paint, width: Float): List<String> {
    val out = mutableListOf<String>()
    text.split('\n').forEach { paragraph ->
        var line = ""
        paragraph.split(' ').forEach { word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= width || line.isEmpty()) line = candidate else { out += line; line = word }
        }
        out += line
    }
    return out
}

private fun buildItems(name: String, date: String?, setlistNotes: String?, songs: List<PdfSong>): List<Item> {
    val content = PAGE_WIDTH - 2 * MARGIN
    val items = mutableListOf<Item>()
    fun one(line: PdfLine) { items += Item.Group(listOf(line)) }

    one(PdfLine(name.ifBlank { "Setlist" }, null, title, 0f, 30f))
    date?.let { one(PdfLine(it, null, sub, 0f, 18f)) }
    one(PdfLine("${songs.size} ${if (songs.size == 1) "song" else "songs"}", null, sub, 0f, 22f))
    setlistNotes?.let { n ->
        items += Item.Group(wrap(n, note, content).map { PdfLine(it, null, note, 0f, 15f) } + PdfLine("", null, note, 0f, 8f))
    }

    // Overview: one entry per song.
    songs.forEach { s ->
        val numberWidth = songPaint.measureText("${s.number}.  ")
        val lines = mutableListOf<PdfLine>()
        wrap(s.title, songPaint, content - 70f - numberWidth).forEachIndexed { i, t ->
            lines += PdfLine(if (i == 0) "${s.number}.  $t" else t, if (i == 0) s.key?.let { "Key $it" } else null, songPaint, if (i == 0) 0f else numberWidth, 19f)
        }
        s.artist?.let { lines += PdfLine(it, null, small, numberWidth, 14f) }
        s.note?.let { n -> wrap("Note: $n", note, content - numberWidth).forEach { lines += PdfLine(it, null, note, numberWidth, 14f) } }
        lines += PdfLine("", null, small, 0f, 10f)
        items += Item.Group(lines)
    }

    // Detail: each song on its own page, with its note and chart.
    songs.forEach { s ->
        items += Item.Break
        wrap("${s.number}.  ${s.title}", songTitle, content - 70f).forEachIndexed { i, t ->
            one(PdfLine(t, if (i == 0) s.key?.let { "Key $it" } else null, songTitle, 0f, 25f))
        }
        s.artist?.let { one(PdfLine(it, null, sub, 0f, 16f)) }
        s.details?.let { one(PdfLine(it, null, small, 0f, 16f)) }
        s.note?.let { n ->
            wrap("Note: $n", note, content).forEach { one(PdfLine(it, null, note, 0f, 15f)) }
        }
        one(PdfLine("", null, small, 0f, 8f))
        if (s.chart.isEmpty()) one(PdfLine("No chart for this song.", null, comment, 0f, 14f))
        val maxChars = (content / mono.measureText("M")).toInt().coerceAtLeast(10)
        s.chart.forEach { c ->
            val (p, h) = when (c.kind) {
                ChartLineKind.LABEL -> label to 17f
                ChartLineKind.CHORDS -> monoChord to 13f
                ChartLineKind.COMMENT -> comment to 13f
                else -> mono to 13f
            }
            // Fixed-width text wraps by character count, so a long line breaks cleanly.
            c.text.chunked(maxChars).ifEmpty { listOf("") }.forEach { piece -> one(PdfLine(piece, null, p, 0f, h)) }
        }
    }
    return items
}

private fun paginate(items: List<Item>): List<List<PdfLine>> {
    val usable = PAGE_HEIGHT - MARGIN - FOOTER - MARGIN
    val pages = mutableListOf<MutableList<PdfLine>>(mutableListOf())
    var used = 0f
    fun newPage() { pages += mutableListOf<PdfLine>(); used = 0f }
    items.forEach { item ->
        when (item) {
            Item.Break -> if (pages.last().isNotEmpty()) newPage()
            is Item.Group -> {
                val height = item.lines.sumOf { it.height.toDouble() }.toFloat()
                if (used + height > usable && pages.last().isNotEmpty()) newPage()
                item.lines.forEach { line ->
                    if (used + line.height > usable && pages.last().isNotEmpty()) newPage()
                    pages.last() += line
                    used += line.height
                }
            }
        }
    }
    return pages
}

private fun draw(canvas: Canvas, lines: List<PdfLine>, number: Int, total: Int) {
    var y = MARGIN
    lines.forEach { line ->
        val baseline = y + line.height - 4f
        if (line.text.isNotEmpty()) canvas.drawText(line.text, MARGIN + line.indent, baseline, line.paint)
        line.right?.let { canvas.drawText(it, PAGE_WIDTH - MARGIN - keyPaint.measureText(it), baseline, keyPaint) }
        y += line.height
    }
    val footerText = "ChordX - page $number of $total"
    canvas.drawText(footerText, PAGE_WIDTH - MARGIN - footer.measureText(footerText), PAGE_HEIGHT - MARGIN, footer)
}

/**
 * Writes the setlist summary as a PDF (A4) to [out]: an overview of the songs, then one page
 * per song with its key, note and chart. The caller closes [out].
 */
fun writeSetlistPdf(out: OutputStream, name: String, date: String?, setlistNotes: String?, songs: List<PdfSong>) {
    val pages = paginate(buildItems(name, date, setlistNotes?.takeIf { it.isNotBlank() }, songs))
    val doc = PdfDocument()
    try {
        pages.forEachIndexed { index, lines ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create())
            draw(page.canvas, lines, index + 1, pages.size)
            doc.finishPage(page)
        }
        doc.writeTo(out)
    } finally {
        doc.close()
    }
}

/** A file name safe on Android storage. */
fun pdfFileName(setlistName: String): String =
    setlistName.ifBlank { "Setlist" }.replace(Regex("""[\\/:*?"<>|\n]"""), "-").trim() + " - setlist summary.pdf"
