package com.peptideadvisor.ui.ask

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peptideadvisor.AppViewModelFactory
import com.peptideadvisor.AskViewModel
import com.peptideadvisor.core.AskAnswer
import com.peptideadvisor.core.CorpusRecord
import com.peptideadvisor.core.EvidenceSummary
import com.peptideadvisor.core.EvidenceTier
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.FieldLabel
import com.peptideadvisor.ui.components.TierBadge
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.display
import com.peptideadvisor.ui.theme.ui

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(
    modifier: Modifier = Modifier,
    viewModel: AskViewModel = viewModel(factory = AppViewModelFactory),
) {
    val palette = Theme.palette
    val corpus by viewModel.corpusState.collectAsStateWithLifecycle()
    val answers by viewModel.answers.collectAsStateWithLifecycle()
    val isThinking by viewModel.isThinking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().background(palette.bench)) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(Theme.gutter),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            corpus.error?.let { message ->
                item { CorpusProblem(message) }
            }

            if (answers.isEmpty() && corpus.error == null) {
                item { EmptyState(corpus.recordCount, corpus.tierBreakdown, corpus.semanticEnabled) }
            }

            items(answers, key = { it.id }) { answer ->
                AnswerBlock(answer)
            }
        }

        AnimatedVisibility(visible = error != null) {
            Text(
                text = error.orEmpty(),
                style = ui(13),
                color = palette.caution,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.clearError() }
                    .padding(horizontal = Theme.gutter, vertical = 8.dp),
            )
        }

        InputBar(
            value = question,
            onValueChange = { question = it },
            isThinking = isThinking,
            onSend = {
                viewModel.ask(question)
                question = ""
            },
        )
    }
}

@Composable
private fun EmptyState(
    recordCount: Int,
    breakdown: Map<EvidenceTier, Int>,
    semanticEnabled: Boolean,
) {
    val palette = Theme.palette
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(24.dp))
        Text("Ask the corpus.", style = display(26), color = palette.ink)
        Text(
            "Answers are built only from the $recordCount abstracts on this device. " +
                "When the literature doesn't cover something, this will say so rather than guess.",
            style = ui(15),
            color = palette.steel,
        )
        if (!semanticEnabled && recordCount > 0) {
            Text(
                "Retrieval is lexical only — add embedder.tflite to assets for semantic rerank.",
                style = ui(12),
                color = palette.steel,
            )
        }
        if (breakdown.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FieldLabel("What's indexed")
            EvidenceTier.byStrength.forEach { tier ->
                val count = breakdown[tier] ?: 0
                if (count > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TierBadge(tier)
                        Text("$count", style = data(13), color = palette.steel)
                    }
                }
            }
        }
    }
}

@Composable
private fun CorpusProblem(message: String) {
    BenchCard {
        FieldLabel("Corpus not loaded")
        Text(message, style = ui(14), color = Theme.palette.ink)
    }
}

@Composable
private fun AnswerBlock(answer: AskAnswer) {
    val palette = Theme.palette
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(answer.question, style = display(19), color = palette.ink)
        EvidenceBanner(answer.summary)
        Text(answer.prose, style = ui(15).copy(lineHeight = 22.sp), color = palette.ink)

        if (answer.sources.isNotEmpty()) {
            FieldLabel("Sources")
            answer.sources.forEachIndexed { index, record ->
                CitationCard(
                    number = index + 1,
                    record = record,
                    wasCited = index in answer.citedIndices,
                )
            }
        }
    }
}

@Composable
private fun EvidenceBanner(summary: EvidenceSummary) {
    val palette = Theme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .background(if (summary.isThin) palette.caution else palette.signal)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(summary.headline, style = ui(13, FontWeight.SemiBold), color = palette.ink)
            if (summary.isThin) {
                Text(
                    "Treat everything below as preliminary.",
                    style = ui(12),
                    color = palette.steel,
                )
            }
        }
    }
}

@Composable
private fun CitationCard(number: Int, record: CorpusRecord, wasCited: Boolean) {
    val palette = Theme.palette
    val uriHandler = LocalUriHandler.current
    var expanded by remember { mutableStateOf(false) }

    Box(Modifier.alpha(if (wasCited) 1f else 0.62f)) {
        BenchCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "S$number",
                    style = data(12, FontWeight.SemiBold),
                    color = if (wasCited) palette.ink else palette.steel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(palette.hairline.copy(alpha = if (wasCited) 1f else 0.4f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
                Spacer(Modifier.width(8.dp))
                TierBadge(record.evidenceTier)
                Spacer(Modifier.weight(1f))
                if (!wasCited) {
                    Text("Retrieved, not used", style = ui(11), color = palette.steel)
                }
            }

            Text(record.title, style = ui(14, FontWeight.Medium), color = palette.ink)

            if (record.citationLine.isNotBlank()) {
                Text(record.citationLine, style = data(11), color = palette.steel)
            }

            if (expanded) {
                Text(record.abstract, style = ui(13), color = palette.ink.copy(alpha = 0.85f))
                record.tierRationale?.takeIf { it.isNotBlank() }?.let { rationale ->
                    FieldLabel("Why this tier")
                    Text(rationale, style = ui(12), color = palette.steel)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    if (expanded) "Hide abstract" else "Read abstract",
                    style = ui(13, FontWeight.Medium),
                    color = palette.amber,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                if (record.url.isNotBlank()) {
                    Text(
                        "Open source",
                        style = ui(13, FontWeight.Medium),
                        color = palette.amber,
                        modifier = Modifier.clickable { uriHandler.openUri(record.url) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    isThinking: Boolean,
    onSend: () -> Unit,
) {
    val palette = Theme.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.paper)
            .padding(Theme.gutter),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text("What does the literature say about…", style = ui(15), color = palette.steel)
            },
            textStyle = ui(15).copy(color = palette.ink),
            maxLines = 4,
            shape = RoundedCornerShape(Theme.radius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.amber,
                unfocusedBorderColor = palette.hairline,
                focusedContainerColor = palette.bench,
                unfocusedContainerColor = palette.bench,
            ),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(Theme.radius))
                .background(palette.amber.copy(alpha = if (value.isBlank()) 0.4f else 1f))
                .clickable(enabled = value.isNotBlank() && !isThinking, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            if (isThinking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = palette.paper,
                    strokeWidth = 2.dp,
                )
            } else {
                Text("↑", style = ui(20, FontWeight.Bold), color = palette.paper)
            }
        }
    }
}
