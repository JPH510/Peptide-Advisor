package com.peptideadvisor.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import com.peptideadvisor.core.Reconstitution
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.data.InjectionSite
import com.peptideadvisor.data.VialWithContext
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.FieldLabel
import com.peptideadvisor.ui.components.NumberRow
import com.peptideadvisor.ui.components.SyringeScale
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayStamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VialDetailScreen(
    vialId: Long,
    onOpenCalculator: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogViewModel = viewModel(factory = AppViewModelFactory),
) {
    val palette = Theme.palette
    // Keyed on vialId: without remember, viewModel.vial() returns a fresh Flow on
    // every recomposition and collection restarts each frame.
    val vialFlow = remember(vialId) { viewModel.vial(vialId) }
    val vial by vialFlow.collectAsStateWithLifecycle(initialValue = null)
    var showLogDose by remember { mutableStateOf(false) }

    val current = vial ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bench)
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(Theme.gutter)),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Header
        Column {
            Text(
                current.mcgRemaining.trimmed(0),
                style = data(44, FontWeight.SemiBold),
                color = palette.ink,
            )
            Text(
                "mcg remaining of ${(current.vial.peptideMg * 1000).trimmed(0)}",
                style = ui(14),
                color = palette.steel,
            )
            current.refrigeratedUseBy?.let { useBy ->
                Spacer(Modifier.height(4.dp))
                Text(
                    if (current.isPastUseBy) {
                        "Past the ${dayStamp.format(Date(useBy))} use-by you set"
                    } else {
                        "Use by ${dayStamp.format(Date(useBy))}"
                    },
                    style = ui(12),
                    color = if (current.isPastUseBy) palette.caution else palette.steel,
                )
            }
        }

        if (current.isReconstituted) {
            BenchCard {
                FieldLabel("Current mix")
                SyringeScale(
                    units = current.reconstitution.unitsFor(current.lastDoseMcg),
                    isSuspect = false,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${current.vial.peptideMg.trimmed(1)} mg + " +
                            "${(current.vial.diluentMl ?: 0.0).trimmed(2)} mL",
                        style = data(13),
                        color = palette.ink,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${current.reconstitution.mcgPerUnit.trimmed(1)} mcg/unit",
                        style = data(13, FontWeight.SemiBold),
                        color = palette.signal,
                    )
                }
                Text(
                    "Recalculate",
                    style = ui(14, FontWeight.Medium),
                    color = palette.amber,
                    modifier = Modifier.clickable { onOpenCalculator(vialId) },
                )
            }

            PrimaryAction("Log a dose") { showLogDose = true }
        } else {
            BenchCard {
                FieldLabel("Not reconstituted")
                Text(
                    "Set a diluent volume to get a concentration and start logging draws.",
                    style = ui(14),
                    color = palette.ink,
                )
                Text(
                    "Open calculator",
                    style = ui(14, FontWeight.SemiBold),
                    color = palette.amber,
                    modifier = Modifier.clickable { onOpenCalculator(vialId) },
                )
            }
        }

        val history = current.doses.sortedByDescending { it.timestamp }
        if (history.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FieldLabel("Draws from this vial")
                BenchCard {
                    history.forEachIndexed { i, dose ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                SimpleDateFormat("d MMM, HH:mm", Locale.getDefault())
                                    .format(Date(dose.timestamp)) +
                                    if (dose.site.isBlank()) "" else " · ${dose.site}",
                                style = ui(12),
                                color = palette.steel,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${dose.doseMcg.trimmed(0)} mcg · ${dose.unitsDrawn.trimmed(1)} u",
                                style = data(13, FontWeight.SemiBold),
                                color = palette.ink,
                            )
                        }
                        if (i < history.lastIndex) {
                            HorizontalDivider(color = palette.hairline, modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showLogDose) {
        ModalBottomSheet(
            onDismissRequest = { showLogDose = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bench,
        ) {
            LogDoseSheet(
                vial = current,
                onLog = { doseMcg, units, site, notes ->
                    viewModel.logDose(vialId, doseMcg, units, site, notes)
                    showLogDose = false
                },
            )
        }
    }
}

@Composable
private fun LogDoseSheet(
    vial: VialWithContext,
    onLog: (Double, Double, String, String) -> Unit,
) {
    val palette = Theme.palette
    var doseMcg by remember { mutableDoubleStateOf(vial.lastDoseMcg) }
    var site by remember { mutableStateOf(InjectionSite.ABDOMEN_L) }
    var notes by remember { mutableStateOf("") }

    val units = vial.reconstitution.unitsFor(doseMcg)
    val warning = vial.reconstitution.warningFor(doseMcg)

    Column(
        modifier = Modifier
            .padding(Theme.gutter)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    units.trimmed(1),
                    style = data(46, FontWeight.SemiBold),
                    color = if (warning == null) palette.ink else palette.caution,
                )
                Spacer(Modifier.width(6.dp))
                Text("units", style = ui(16), color = palette.steel)
            }
            SyringeScale(units = units, isSuspect = warning != null)
        }

        NumberRow("Dose", "mcg", doseMcg, { doseMcg = it }, 25.0, 5.0..5000.0, 0)

        warning?.let {
            Text(it.message, style = ui(13), color = palette.caution)
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel("Site")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                InjectionSite.entries.forEach { option ->
                    val selected = site == option
                    Text(
                        option.label,
                        style = ui(13, if (selected) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (selected) palette.paper else palette.ink,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) palette.signal else palette.hairline)
                            .clickable { site = option }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes", style = ui(13)) },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )

        PrimaryAction("Log dose") {
            onLog(doseMcg, units, site.label, notes)
        }
        Spacer(Modifier.height(24.dp))
    }
}
