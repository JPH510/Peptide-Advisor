package com.peptideadvisor.core

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

data class CorpusState(
    val records: List<CorpusRecord> = emptyList(),
    val isLoaded: Boolean = false,
    val error: String? = null,
    val semanticEnabled: Boolean = false,
    val vectorsPrecomputed: Boolean = false,
) {
    val recordCount: Int get() = records.size
    val tierBreakdown: Map<EvidenceTier, Int>
        get() = records.groupingBy { it.evidenceTier }.eachCount()
}

/**
 * Loads the corpus and answers retrieval queries.
 *
 * Retrieval is two-stage:
 *   1. BM25 shortlist — exact compound-name matching, which is most of the work.
 *   2. Semantic rerank of that shortlist via MediaPipe's TextEmbedder.
 *
 * Because the same .tflite model runs in Python (tools/embed_corpus.py) and here,
 * corpus vectors can be precomputed and shipped. If corpus_vectors.bin is present
 * the rerank is a dot product against preloaded floats; if not, candidate titles
 * are embedded on the fly. If the model asset is missing entirely, the app falls
 * back to BM25 alone rather than failing.
 */
class CorpusStore(private val context: Context) {

    private val _state = MutableStateFlow(CorpusState())
    val state: StateFlow<CorpusState> = _state.asStateFlow()

    private var index: Bm25Index? = null
    private var embedder: TextEmbedder? = null
    private var vectors: Array<FloatArray>? = null

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val loadMutex = Mutex()

    /**
     * Safe to call repeatedly. AskViewModel is recreated on configuration change,
     * and rebuilding the index each time would stall the first query for no reason.
     */
    suspend fun load() = withContext(Dispatchers.IO) {
        loadMutex.withLock {
            if (_state.value.isLoaded) return@withContext
            loadLocked()
        }
    }

    private fun loadLocked() {
        val records = runCatching { readCorpus() }.getOrElse { error ->
            _state.value = CorpusState(
                error = "Couldn't read corpus.jsonl from assets: ${error.message}. " +
                    "Run tools/fetch_corpus.py, then tools/tag_evidence.py, and drop the " +
                    "result in app/src/main/assets/."
            )
            return
        }

        if (records.isEmpty()) {
            _state.value = CorpusState(error = "corpus.jsonl parsed to zero records.")
            return
        }

        index = Bm25Index(records)
        embedder = createEmbedder()
        vectors = if (embedder != null) readVectors(records.size) else null

        _state.value = CorpusState(
            records = records,
            isLoaded = true,
            semanticEnabled = embedder != null,
            vectorsPrecomputed = vectors != null,
        )
    }

    private fun readCorpus(): List<CorpusRecord> =
        context.assets.open(CORPUS_ASSET).bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                if (line.isBlank()) null
                else runCatching { json.decodeFromString<CorpusRecord>(line) }.getOrNull()
            }.toList()
        }

    private fun createEmbedder(): TextEmbedder? = runCatching {
        val options = TextEmbedder.TextEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            // Normalized vectors make cosine similarity a plain dot product.
            .setL2Normalize(true)
            .build()
        TextEmbedder.createFromOptions(context, options)
    }.onFailure {
        Log.i(TAG, "No text embedder (${it.message}); retrieval will be lexical only.")
    }.getOrNull()

    /**
     * corpus_vectors.bin layout, little-endian:
     *   int32 count, int32 dim, then count * dim float32, in corpus.jsonl line order.
     */
    private fun readVectors(expectedCount: Int): Array<FloatArray>? = runCatching {
        context.assets.open(VECTORS_ASSET).use { stream ->
            val input = DataInputStream(stream.buffered())
            val header = ByteArray(8).also { input.readFully(it) }
            val buffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val count = buffer.int
            val dim = buffer.int

            if (count != expectedCount) {
                Log.w(TAG, "corpus_vectors.bin has $count rows, corpus has $expectedCount. " +
                    "Re-run tools/embed_corpus.py. Falling back to on-the-fly embedding.")
                return@use null
            }

            val bytes = ByteArray(count * dim * 4).also { input.readFully(it) }
            val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
            Array(count) { row ->
                FloatArray(dim).also { floats.get(it, 0, dim) }
            }
        }
    }.onFailure {
        Log.i(TAG, "No precomputed vectors; will embed candidate titles per query.")
    }.getOrNull()

    suspend fun retrieve(
        query: String,
        limit: Int = 8,
        shortlist: Int = 50,
    ): List<CorpusRecord> = withContext(Dispatchers.Default) {
        val bm25 = index ?: return@withContext emptyList()
        val records = _state.value.records
        val hits = bm25.search(query, shortlist)
        if (hits.isEmpty()) return@withContext emptyList()

        val queryVector = embedQuery(query)
            ?: return@withContext hits.take(limit).map { records[it.index] }

        val topLexical = hits.first().score.takeIf { it > 0 } ?: 1.0

        hits.map { hit ->
            val record = records[hit.index]
            val candidate = vectors?.getOrNull(hit.index)
                // Sentence embeddings degrade on long text, so rerank on the title.
                ?: embedText(record.title)
            val semantic = candidate?.let { cosine(queryVector, it) } ?: 0.0
            val lexical = hit.score / topLexical
            // Lexical carries most of the weight: the exact compound name matters
            // more here than topical similarity.
            record to (0.65 * lexical + 0.35 * semantic)
        }
            .sortedWith(
                // Break near-ties toward stronger evidence.
                compareByDescending<Pair<CorpusRecord, Double>> { (it.second * 50).toInt() }
                    .thenByDescending { it.first.evidenceTier.weight }
                    .thenByDescending { it.second }
            )
            .take(limit)
            .map { it.first }
    }

    private fun embedQuery(query: String): FloatArray? = embedText(query)

    private fun embedText(text: String): FloatArray? = runCatching {
        embedder?.embed(text)?.embeddingResult()?.embeddings()?.firstOrNull()?.floatEmbedding()
    }.getOrNull()

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA <= 0 || normB <= 0) return 0.0
        return dot / (sqrt(normA) * sqrt(normB))
    }

    fun close() {
        embedder?.close()
        embedder = null
    }

    companion object {
        private const val TAG = "CorpusStore"
        const val CORPUS_ASSET = "corpus.jsonl"
        const val VECTORS_ASSET = "corpus_vectors.bin"
        const val MODEL_ASSET = "embedder.tflite"
    }
}
