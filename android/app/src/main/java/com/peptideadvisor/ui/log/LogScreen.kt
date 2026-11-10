package com.peptideadvisor.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peptideadvisor.AppViewModelFactory
import com.peptideadvisor.LogViewModel
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.data.DoseWithVial
import com.peptideadvisor.data.VialWithContext
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.FieldLabel
import com.peptideadvisor.ui.components.NumberRow
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.display
import com.peptideadvisor.ui.theme.ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val stamp = SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    onOpenVial: (Long) -> Unit,
    onOpenCalculator: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogViewModel = viewModel(factory = AppViewModelFactory),
) {
    val palette = Theme.palette
    val vials by viewModel.vials.collectAsStateWithLifecycle()
    val doses by viewModel.recentDoses.collectAsStateWithLifecycle()
    val compounds by viewModel.compounds.collectAsStateWithLifecycle()
    var showAddVial by remember { mutableStateOf(false) }

    val active = vials.filter { it.isActive }

    Box(modifier.fillMaxSize().background(palette.bench)) {
        LazyColumn(
            contentPadding = PaddingValues(Theme.gutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (active.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.height(24.dp))
                        Text("Nothing open.", style = display(26), color = palette.ink)
                        Text(
                            "Add a vial to start tracking concentration, draws, and what's left in it.",
                            style = ui(15),
                            color = palette.steel,
                        )
                        PrimaryAction("Add vial") { showAddVial = true }
                    }
                }
            } else {
                item { FieldLabel("Open vials") }
                items(active, key = { it.vial.id }) { vial ->
                    VialRow(vial, onClick = { onOpenVial(vial.vial.id) })
                }
            }

            if (doses.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    FieldLabel("Recent doses")
                }
                item {
                    BenchCard {
                        doses.forEachIndexed { i, entry ->
                            DoseRow(entry)
                            if (i < doses.lastIndex) {
                                HorizontalDivider(color = palette.hairline, modifier = Modifier.padding(vertical = 8.dp))
                            }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (active.isNotEmpty()) PrimaryAction("Add vial") { showAddVial = true }
                    SecondaryAction("Calculator", onOpenCalculator)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showAddVial) {
        ModalBottomSheet(
            onDismissRequest = { showAddVial = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bench,
        ) {
            AddVialSheet(
                knownCompounds = compounds.map { it.name },
                onAdd = { name, mg, lot ->
                    viewModel.addVial(name, mg, lot, null)
                    showAddVial = false
                },
            )
        }
    }
}

@Composable
fun VialRow(vial: VialWithContext, onClick: () -> Unit) {
    val palette = Theme.palette
    Box(Modifier.clickable(onClick = onClick)) {
        BenchCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    vial.compound?.name ?: "Unassigned",
                    style = ui(16, FontWeight.SemiBold),
                    color = palette.ink,
                )
                Spacer(Modifier.weight(1f))
                if (vial.isPastUseBy) {
                    Text("Past use-by", style = ui(11, FontWeight.SemiBold), color = palette.caution)
                }
            }

            Text(subtitleFor(vial), style = data(12), color = palette.steel)

            // Remaining, drawn as a level rather than a bar chart.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(palette.hairline)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(vial.fractionRemaining)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(if (vial.isPastUseBy) palette.caution else palette.signal)
                )
            }

            Text(
                "${vial.mcgRemaining.trimmed(0)} mcg left of ${(vial.vial.peptideMg * 1000).trimmed(0)}",
                style = data(11),
                color = palette.steel,
            )
        }
    }
}

private fun subtitleFor(vial: VialWithContext): String = buildList {
    add("${vial.vial.peptideMg.trimmed(1)} mg")
    if (vial.isReconstituted) {
        add("${vial.reconstitution.concentrationMgPerMl.trimmed(2)} mg/mL")
        add("${vial.reconstitution.mcgPerUnit.trimmed(1)} mcg/u")
    } else {
        add("not reconstituted")
    }
    vial.vendor?.let { add(it.name) }
}.joinToString(" · ")

@Composable
fun DoseRow(entry: DoseWithVial) {
    val palette = Theme.palette
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column {
            Text(
                entry.vial?.compound?.name ?: "—",
                style = ui(14, FontWeight.Medium),
                color = palette.ink,
            )
            Text(
                stamp.format(Date(entry.dose.timestamp)) +
                    if (entry.dose.site.isBlank()) "" else " · ${entry.dose.site}",
                style = ui(11),
                color = palette.steel,
            )
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${entry.dose.doseMcg.trimmed(0)} mcg",
                style = data(14, FontWeight.SemiBold),
                color = palette.ink,
            )
            Text("${entry.dose.unitsDrawn.trimmed(1)} u", style = data(11), color = palette.steel)
        }
    }
}

@Composable
private fun AddVialSheet(
    knownCompounds: List<String>,
    onAdd: (String, Double, String) -> Unit,
) {
    val palette = Theme.palette
    var name by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var peptideMg by remember { mutableDoubleStateOf(5.0) }

    Column(
        modifier = Modifier.padding(Theme.gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add vial", style = display(22), color = palette.ink)

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Compound", style = ui(13)) },
            placeholder = { Text("e.g. BPC-157", style = ui(14), color = palette.steel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (knownCompounds.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                knownCompounds.forEach { known ->
                    Text(
                        known,
                        style = ui(13),
                        color = palette.ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(palette.hairline)
                            .clickable { name = known }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }

        NumberRow("Label claim", "mg", peptideMg, { peptideMg = it }, 0.5, 0.5..100.0, 1)

        OutlinedTextField(
            value = lot,
            onValueChange = { lot = it },
            label = { Text("Lot number", style = ui(13)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (name.isNotBlank()) {
            PrimaryAction("Add vial") { onAdd(name, peptideMg, lot) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit) {
    val palette = Theme.palette
    Text(
        label,
        style = ui(15, FontWeight.SemiBold),
        color = palette.paper,
        modifier = Modifier
            .clip(RoundedCornerShape(Theme.radius))
            .background(palette.amber)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit) {
    val palette = Theme.palette
    Text(
        label,
        style = ui(15, FontWeight.Medium),
        color = palette.ink,
        modifier = Modifier
            .clip(RoundedCornerShape(Theme.radius))
            .background(palette.hairline)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
    )
}
