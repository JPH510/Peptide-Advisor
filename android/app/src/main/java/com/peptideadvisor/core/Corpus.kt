package com.peptideadvisor.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.ln

/** One retrieved source. Mirrors a line of corpus.jsonl produced by tools/. */
@Serializable
data class CorpusRecord(
    val id: String,                       // "PMID:12345678" or "NCT01234567"
    val source: String = "",              // "pubmed" | "clinicaltrials"
    val title: String = "",
    val abstract: String = "",
    val year: Int? = null,
    val journal: String? = null,
    val url: String = "",
    val compounds: List<String> = emptyList(),
    val tier: String = "unknown",
    @SerialName("tierRationale") val tierRationale: String? = null,
    val species: String? = null,
    @SerialName("sampleSize") val sampleSize: Int? = null,
) {
    val evidenceTier: EvidenceTier get() = EvidenceTier.fromWire(tier)

    val citationLine: String
        get() = buildList {
            journal?.takeIf { it.isNotBlank() }?.let { add(it) }
            year?.let { add(it.toString()) }
            sampleSize?.let { add("n=$it") }
            species?.takeIf { it.isNotBlank() && !it.equals("human", true) }?.let { add(it) }
        }.joinToString(" · ")

    /** Text the index sees. */
    val indexedText: String get() = "$title ${compounds.joinToString(" ")} $abstract"
}

object Tokenizer {

    /**
     * Keeps digits and hyphens so "BPC-157" survives, and also emits the
     * de-hyphenated form so "bpc157" in a query still hits.
     */
    fun tokens(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (ch in text.lowercase()) {
            if (ch.isLetterOrDigit() || ch == '-') {
                current.append(ch)
            } else if (current.isNotEmpty()) {
                out += expand(current.toString())
                current.clear()
            }
        }
        if (current.isNotEmpty()) out += expand(current.toString())
        return out.filter { it.length > 1 && it !in STOPWORDS }
    }

    private fun expand(token: String): List<String> {
        val trimmed = token.trim('-')
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed.contains('-')) {
            listOf(trimmed, trimmed.replace("-", ""))
        } else {
            listOf(trimmed)
        }
    }

    private val STOPWORDS = setOf(
        "the", "and", "for", "was", "were", "with", "that", "this", "from",
        "are", "but", "not", "have", "has", "had", "its", "their", "which",
        "these", "those", "than", "then", "there", "been", "can", "may",
        "our", "who", "all", "also", "into", "each", "more", "most", "such",
    )
}

/**
 * Lexical retrieval over the local corpus.
 *
 * Stage one of two. Compound names are exact-match terms ("BPC-157",
 * "tirzepatide"), which is exactly what BM25 is good at and exactly what
 * pure vector search tends to blur.
 */
class Bm25Index(records: List<CorpusRecord>) {

    private val k1 = 1.5
    private val b = 0.75

    private val termFreqs: List<Map<String, Int>>
    private val docFreq: Map<String, Int>
    private val docLengths: DoubleArray
    private val avgLength: Double
    private val docCount: Int = records.size

    init {
        val freqs = ArrayList<Map<String, Int>>(records.size)
        val df = HashMap<String, Int>()
        val lengths = DoubleArray(records.size)

        records.forEachIndexed { i, record ->
            val counts = HashMap<String, Int>()
            val tokens = Tokenizer.tokens(record.indexedText)
            tokens.forEach { counts[it] = (counts[it] ?: 0) + 1 }
            counts.keys.forEach { df[it] = (df[it] ?: 0) + 1 }
            freqs += counts
            lengths[i] = tokens.size.toDouble()
        }

        termFreqs = freqs
        docFreq = df
        docLengths = lengths
        avgLength = if (records.isEmpty()) 0.0 else lengths.average()
    }

    data class Hit(val index: Int, val score: Double)

    fun search(query: String, limit: Int): List<Hit> {
        val queryTerms = Tokenizer.tokens(query).toSet()
        if (queryTerms.isEmpty() || docCount == 0) return emptyList()

        val scores = DoubleArray(docCount)
        for (term in queryTerms) {
            val df = docFreq[term] ?: continue
            if (df == 0) continue
            val idf = ln(1 + (docCount - df + 0.5) / (df + 0.5))
            for (i in 0 until docCount) {
                val tf = termFreqs[i][term] ?: continue
                val numerator = tf * (k1 + 1)
                val denominator = tf + k1 * (1 - b + b * docLengths[i] / maxOf(avgLength, 1.0))
                scores[i] += idf * (numerator / denominator)
            }
        }

        return scores.mapIndexed { i, score -> Hit(i, score) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
            .take(limit)
    }
}
