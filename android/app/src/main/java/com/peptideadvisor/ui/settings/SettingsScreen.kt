package com.peptideadvisor.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peptideadvisor.AppViewModelFactory
import com.peptideadvisor.SettingsViewModel
import com.peptideadvisor.core.EvidenceTier
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.FactRow
import com.peptideadvisor.ui.components.FieldLabel
import com.peptideadvisor.ui.log.PrimaryAction
import com.peptideadvisor.ui.log.SecondaryAction
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.ui

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val palette = Theme.palette
    val corpus by viewModel.corpusState.collectAsStateWithLifecycle()
    val hasKey by viewModel.hasKey.collectAsStateWithLifecycle()
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bench)
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(Theme.gutter)),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        BenchCard {
            FieldLabel("Anthropic API key")
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                placeholder = { Text(if (hasKey) "Key saved" else "sk-ant-…", style = ui(14)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Encrypted with AES-GCM under a non-exportable Keystore key. Questions and " +
                    "retrieved abstracts go to the API; your vials, doses, and vendor records " +
                    "never leave this phone.",
                style = ui(12),
                color = palette.steel,
            )
            if (apiKey.isNotBlank()) {
                PrimaryAction("Save key") {
                    viewModel.saveKey(apiKey)
                    apiKey = ""
                }
            } else if (hasKey) {
                SecondaryAction("Remove key") { viewModel.removeKey() }
            }
        }

        BenchCard {
            FieldLabel("Corpus")
            FactRow("Records", "${corpus.recordCount}")
            HorizontalDivider(color = palette.hairline)
            FactRow(
                "Retrieval",
                when {
                    !corpus.semanticEnabled -> "Lexical only"
                    corpus.vectorsPrecomputed -> "Hybrid, precomputed"
                    else -> "Hybrid, on-the-fly"
                },
            )
            HorizontalDivider(color = palette.hairline)
            EvidenceTier.byStrength.forEach { tier ->
                val count = corpus.tierBreakdown[tier] ?: 0
                if (count > 0) FactRow(tier.label, "$count")
            }
            corpus.error?.let {
                Text(it, style = ui(13), color = palette.caution)
            }
            Text(
                "Rebuild with tools/fetch_corpus.py and tools/tag_evidence.py, then replace " +
                    "corpus.jsonl in app/src/main/assets/.",
                style = ui(12),
                color = palette.steel,
            )
        }

        Text(
            "This app reads literature and does arithmetic. It doesn't know your medical " +
                "history and it isn't a substitute for a clinician who does.",
            style = ui(13),
            color = palette.steel,
        )
        Spacer(Modifier.height(24.dp))
    }
}
