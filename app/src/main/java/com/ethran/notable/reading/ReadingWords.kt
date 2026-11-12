package com.ethran.notable.reading

import androidx.annotation.StringRes
import com.ethran.notable.R
import java.time.LocalDate

/** The word stays English (it is a list of English words to learn); [meaning] follows the app language. */
data class WordOfDay(val word: String, @StringRes val meaningRes: Int) {
    val meaning: String get() = RS.get(meaningRes)
}

/** A short list of words worth knowing, one a day. Chosen by date, so the word stays the same all day and on every launch. */
object ReadingWords {
    val WORDS: List<WordOfDay> = listOf(
        WordOfDay("ephemeral", R.string.reading_word_ephemeral),
        WordOfDay("sonder", R.string.reading_word_sonder),
        WordOfDay("petrichor", R.string.reading_word_petrichor),
        WordOfDay("liminal", R.string.reading_word_liminal),
        WordOfDay("sanguine", R.string.reading_word_sanguine),
        WordOfDay("laconic", R.string.reading_word_laconic),
        WordOfDay("ineffable", R.string.reading_word_ineffable),
        WordOfDay("mellifluous", R.string.reading_word_mellifluous),
        WordOfDay("equanimity", R.string.reading_word_equanimity),
        WordOfDay("serendipity", R.string.reading_word_serendipity),
        WordOfDay("obfuscate", R.string.reading_word_obfuscate),
        WordOfDay("perspicacious", R.string.reading_word_perspicacious),
        WordOfDay("halcyon", R.string.reading_word_halcyon),
        WordOfDay("recondite", R.string.reading_word_recondite),
        WordOfDay("tenacious", R.string.reading_word_tenacious),
        WordOfDay("verdant", R.string.reading_word_verdant),
        WordOfDay("quixotic", R.string.reading_word_quixotic),
        WordOfDay("sedulous", R.string.reading_word_sedulous),
        WordOfDay("lucid", R.string.reading_word_lucid),
        WordOfDay("crepuscular", R.string.reading_word_crepuscular),
        WordOfDay("palimpsest", R.string.reading_word_palimpsest),
        WordOfDay("eloquent", R.string.reading_word_eloquent),
        WordOfDay("fastidious", R.string.reading_word_fastidious),
        WordOfDay("gregarious", R.string.reading_word_gregarious),
        WordOfDay("juxtapose", R.string.reading_word_juxtapose),
        WordOfDay("limpid", R.string.reading_word_limpid),
        WordOfDay("numinous", R.string.reading_word_numinous),
        WordOfDay("reverie", R.string.reading_word_reverie),
        WordOfDay("solace", R.string.reading_word_solace),
        WordOfDay("zenith", R.string.reading_word_zenith)
    )

    /** The word for [date]: the same word all day, a new one the next day. */
    fun of(date: LocalDate): WordOfDay = WORDS[Math.floorMod(date.toEpochDay(), WORDS.size.toLong()).toInt()]
}
