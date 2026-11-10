package com.peptideadvisor.core

import java.util.UUID

data class AskAnswer(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val prose: String,
    val sources: List<CorpusRecord>,
    val citedIndices: Set<Int>,
    val askedAt: Long = System.currentTimeMillis(),
) {
    val citedSources: List<CorpusRecord>
        get() = citedIndices.sorted().mapNotNull { sources.getOrNull(it) }

    val summary: EvidenceSummary
        get() = EvidenceSummary(citedSources.map { it.evidenceTier })
}

class AskEngine(
    private val corpus: CorpusStore,
    private val client: ClaudeClient,
) {

    suspend fun ask(question: String): Result<AskAnswer> {
        val trimmed = question.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Empty question"))

        val sources = corpus.retrieve(trimmed, limit = 8)

        if (sources.isEmpty()) {
            return Result.success(
                AskAnswer(
                    question = trimmed,
                    prose = "Nothing in your corpus matches this question. Either the " +
                        "compound isn't indexed yet, or the literature pull didn't reach " +
                        "it — add the term to tools/fetch_corpus.py and re-run.",
                    sources = emptyList(),
                    citedIndices = emptySet(),
                )
            )
        }

        return runCatching {
            val prose = client.complete(SYSTEM_PROMPT, buildMessage(trimmed, sources))
            AskAnswer(
                question = trimmed,
                prose = prose,
                sources = sources,
                citedIndices = parseCitations(prose, sources.size),
            )
        }
    }

    companion object {

        /**
         * The whole reason this isn't a chat wrapper. The model gets a closed set
         * of sources and is told, several different ways, that it may not step
         * outside them.
         */
        val SYSTEM_PROMPT = """
            You answer questions about research peptides for a single technical user, using ONLY the numbered sources supplied in the message. You are a reader of the literature, not a prescriber.

            Rules, in priority order:

            1. Ground everything. Every factual claim must trace to a supplied source, marked inline as [S1], [S2], and so on. If you cannot support a claim from the sources, do not make it.

            2. Say when the sources don't answer the question. This is the most useful thing you can do. Open with it plainly — "These sources don't address X" — rather than padding with adjacent findings. Never fill a gap with recalled background knowledge.

            3. Carry the evidence tier into the prose. Each source is labelled with its tier. Do not write "improves tendon healing" when the support is a rat study; write "accelerated tendon healing in rats [S3]". Name the species, the sample size, and the year whenever you make an effect claim.

            4. Dosing. If the sources report doses used in humans, you may state them and attribute them. If the sources only report animal dosing, say so and state plainly that no validated human-equivalent dose follows from it — do not perform allometric conversion or extrapolate a human dose. Never produce a protocol, schedule, or stack the sources did not study.

            5. Report disagreement. If sources conflict, present both and say which has the stronger design.

            6. No hedged filler and no sales language. Short paragraphs. Lead with the finding. If the honest answer is "the evidence here is four small animal studies and one uncontrolled human series," write exactly that.

            Close with a one-line "Bottom line:" that states what is actually established.
        """.trimIndent()

        fun buildMessage(question: String, sources: List<CorpusRecord>): String {
            val block = sources.mapIndexed { i, source ->
                """
                [S${i + 1}] ${source.title}
                Tier: ${source.evidenceTier.label}
                ${source.citationLine}
                Abstract: ${source.abstract.take(1800)}
                """.trimIndent()
            }.joinToString("\n\n")

            return "SOURCES\n\n$block\n\nQUESTION\n$question"
        }

        private val CITATION_PATTERN = Regex("""\[S(\d+)]""")

        /**
         * Pulls [S1]-style markers back out so the UI can show which sources were
         * leaned on, and grey out the ones that were retrieved but never used.
         */
        fun parseCitations(text: String, sourceCount: Int): Set<Int> =
            CITATION_PATTERN.findAll(text)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .map { it - 1 }
                .filter { it in 0 until sourceCount }
                .toSet()
    }
}
