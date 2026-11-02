package com.chordx.app.music

/** How black-key notes are spelled when chords are shown transposed. */
enum class Accidentals { AUTO, SHARPS, FLATS }

private const val LETTERS = "CDEFGAB"
private val LETTER_PITCH = intArrayOf(0, 2, 4, 5, 7, 9, 11)

/** A note name: a letter plus -1 (flat), 0 or +1 (sharp). */
data class Note(val letter: Char, val accidental: Int) {
    init {
        require(letter in LETTERS) { "Not a note letter: $letter" }
        require(accidental in -1..1) { "Accidental out of range: $accidental" }
    }

    /** Pitch class, 0 = C … 11 = B. */
    val pitch: Int get() = (LETTER_PITCH[LETTERS.indexOf(letter)] + accidental).mod(12)

    override fun toString(): String = letter + when (accidental) {
        -1 -> "b"
        1 -> "#"
        else -> ""
    }

    companion object {
        private val SHARP_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val FLAT_NAMES = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")

        fun named(pitch: Int, flats: Boolean): Note = parse((if (flats) FLAT_NAMES else SHARP_NAMES)[pitch.mod(12)])!!

        /** Reads an upper-case note at the start of [text]; returns it with the number of characters used. */
        fun parsePrefix(text: String): Pair<Note, Int>? {
            val letter = text.firstOrNull()?.takeIf { it in LETTERS } ?: return null
            return when (text.getOrNull(1)) {
                '#', '♯' -> Note(letter, 1) to 2
                'b', '♭' -> Note(letter, -1) to 2
                else -> Note(letter, 0) to 1
            }
        }

        fun parse(text: String): Note? = parsePrefix(text)?.takeIf { it.second == text.length }?.first
    }
}

/** Chooses a name for each of the 12 pitch classes. */
fun interface Spelling {
    fun name(pitch: Int): Note
}

val SharpSpelling = Spelling { Note.named(it, flats = false) }
val FlatSpelling = Spelling { Note.named(it, flats = true) }

/**
 * Spells notes the way they'd be written in [key]: scale notes use the key's letters
 * and chromatic notes follow the common b2, b3, #4, b6, b7 convention (so in C:
 * Db, Eb, F#, Ab, Bb). Awkward results like E# or Cb fall back to plain sharps or flats.
 */
class KeySpelling(key: Key) : Spelling {
    private val names: List<Note>

    init {
        val major = if (key.minor) Key.conventionalTonic(key.tonic.pitch + 3, minor = false) else key.tonic
        val flatKey = major.accidental < 0 || major == Note('F', 0)
        val tonicIndex = LETTERS.indexOf(major.letter)
        names = (0..11).map { pitch ->
            val offset = (pitch - major.pitch).mod(12)
            val letterIndex = (tonicIndex + DEGREE_FOR_OFFSET[offset]) % 7
            var accidental = (pitch - LETTER_PITCH[letterIndex]).mod(12)
            if (accidental > 6) accidental -= 12
            val letter = LETTERS[letterIndex]
            val awkward = accidental !in -1..1 ||
                (accidental == 1 && (letter == 'E' || letter == 'B')) ||
                (accidental == -1 && (letter == 'F' || letter == 'C'))
            if (awkward) Note.named(pitch, flatKey) else Note(letter, accidental)
        }
    }

    override fun name(pitch: Int): Note = names[pitch.mod(12)]

    private companion object {
        /** Semitones above the tonic → scale degree (0-based) used to spell it. */
        val DEGREE_FOR_OFFSET = intArrayOf(0, 1, 1, 2, 2, 3, 3, 4, 5, 5, 6, 6)
    }
}

data class Key(val tonic: Note, val minor: Boolean) {
    override fun toString(): String = tonic.toString() + if (minor) "m" else ""

    /** The key [semitones] away, using the conventional name (Bb not A#, F# not Gb, …). */
    fun transpose(semitones: Int): Key = Key(conventionalTonic(tonic.pitch + semitones, minor), minor)

    /** Smallest shift from this key to [other], in -5..+6. */
    fun semitonesTo(other: Key): Int = normalizeSemitones(other.tonic.pitch - tonic.pitch)

    companion object {
        private val MAJOR = listOf("C", "Db", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")
        private val MINOR = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "G#", "A", "Bb", "B")
        private val PATTERN = Regex("^([A-G][#b♯♭]?)(m)?$")

        fun conventionalTonic(pitch: Int, minor: Boolean): Note = Note.parse((if (minor) MINOR else MAJOR)[pitch.mod(12)])!!

        fun parse(text: String): Key? {
            val match = PATTERN.matchEntire(text.trim()) ?: return null
            return Key(Note.parse(match.groupValues[1])!!, minor = match.groupValues[2].isNotEmpty())
        }

        /** All 12 keys of one mode, from C upwards. */
        fun all(minor: Boolean): List<Key> = (0..11).map { Key(conventionalTonic(it, minor), minor) }
    }
}

/** Maps any shift to the equivalent one in -5..+6. */
fun normalizeSemitones(semitones: Int): Int {
    val m = semitones.mod(12)
    return if (m > 6) m - 12 else m
}

/** A chord symbol: root, quality/extension text, and optional slash bass. */
data class Chord(val root: Note, val suffix: String, val bass: Note?) {
    override fun toString(): String = "$root$suffix" + (bass?.let { "/$it" } ?: "")

    fun transpose(semitones: Int, spelling: Spelling): Chord =
        Chord(spelling.name(root.pitch + semitones), suffix, bass?.let { spelling.name(it.pitch + semitones) })

    companion object {
        /**
         * Suffix made only of recognised chord vocabulary, so words like "Chorus"
         * (C + "horus") are rejected rather than read as chords.
         */
        private val SUFFIX = Regex("""^(?:maj|min|dim|aug|sus|add|alt|no|6/9|m|M|[0-9]|[#b+\-^()°øΔ♯♭])*$""")

        /**
         * An addition written without a root, such as "+M7" in "Bm--+M7": it changes the
         * chord before it (Bm → Bm with a major 7th), so it has no pitch of its own and
         * reads the same in every key.
         */
        fun isAddition(text: String): Boolean = text.length > 1 && text.startsWith("+") && SUFFIX.matches(text)

        fun parse(text: String): Chord? {
            val (root, used) = Note.parsePrefix(text) ?: return null
            var rest = text.substring(used)
            var bass: Note? = null
            val slash = rest.lastIndexOf('/')
            if (slash >= 0) {
                val candidate = Note.parse(rest.substring(slash + 1))
                if (candidate != null) {
                    bass = candidate
                    rest = rest.substring(0, slash)
                }
            }
            if (!SUFFIX.matches(rest)) return null
            // "-" can mean minor ("C-7"), but trailing dashes are beat timing ("Bm--"), never part of a chord.
            if (rest.length > 1 && rest.endsWith("-")) return null
            return Chord(root, rest, bass)
        }
    }
}
