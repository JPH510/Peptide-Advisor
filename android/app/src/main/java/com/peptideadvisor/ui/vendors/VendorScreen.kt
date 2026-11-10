package com.peptideadvisor.ui.vendors

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.peptideadvisor.AppViewModelFactory
import com.peptideadvisor.VendorViewModel
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.data.AssayVerdict
import com.peptideadvisor.data.LabReportEntity
import com.peptideadvisor.data.VendorWithReports
import com.peptideadvisor.data.contentDeviationPercent
import com.peptideadvisor.data.verdict
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.NumberRow
import com.peptideadvisor.ui.log.PrimaryAction
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.display
import com.peptideadvisor.ui.theme.ui
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val dayStamp = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VendorScreen(
    modifier: Modifier = Modifier,
    viewModel: VendorViewModel = viewModel(factory = AppViewModelFactory),
) {
    val palette = Theme.palette
    val vendors by viewModel.vendors.collectAsStateWithLifecycle()
    var showAddVendor by remember { mutableStateOf(false) }
    var assayFor by remember { mutableStateOf<VendorWithReports?>(null) }

    Box(modifier.fillMaxSize().background(palette.bench)) {
        LazyColumn(
            contentPadding = PaddingValues(Theme.gutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (vendors.isEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Spacer(Modifier.height(24.dp))
                        Text("No vendors yet.", style = display(26), color = palette.ink)
                        Text(
                            "Add a vendor, then log independent assay results by lot number. " +
                                "What accumulates is a record of whether their vials match their " +
                                "labels over time.",
                            style = ui(15),
                            color = palette.steel,
                        )
                        PrimaryAction("Add vendor") { showAddVendor = true }
                    }
                }
            } else {
                item {
                    Text(
                        "Records are kept per lot. A certificate from the seller's own lab " +
                            "tells you nothing about the vial in your fridge.",
                        style = ui(13),
                        color = palette.steel,
                    )
                }
                items(vendors, key = { it.vendor.id }) { vendor ->
                    VendorCard(vendor, onAddAssay = { assayFor = vendor })
                }
                item {
                    PrimaryAction("Add vendor") { showAddVendor = true }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (showAddVendor) {
        ModalBottomSheet(
            onDismissRequest = { showAddVendor = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bench,
        ) {
            AddVendorSheet { name, site, notes ->
                viewModel.addVendor(name, site, notes)
                showAddVendor = false
            }
        }
    }

    assayFor?.let { vendor ->
        ModalBottomSheet(
            onDismissRequest = { assayFor = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = palette.bench,
        ) {
            AddAssaySheet(vendorId = vendor.vendor.id) { report ->
                viewModel.addReport(report)
                assayFor = null
            }
        }
    }
}

@Composable
private fun VendorCard(vendor: VendorWithReports, onAddAssay: () -> Unit) {
    val palette = Theme.palette
    var expanded by remember { mutableStateOf(false) }

    BenchCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(vendor.vendor.name, style = ui(16, FontWeight.SemiBold), color = palette.ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (expanded) "Hide lots" else "Show lots",
                style = ui(13, FontWeight.Medium),
                color = palette.amber,
                modifier = Modifier.clickable { expanded = !expanded },
            )
        }

        if (vendor.testedLotCount == 0) {
            Text("No independent assays logged", style = ui(12), color = palette.steel)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Stat("Lots tested", "${vendor.testedLotCount}")
                vendor.meanPurity?.let { Stat("Mean purity", "${it.trimmed(1)}%") }
                vendor.worstContentDeviation?.let {
                    Stat(
                        "Worst vs label",
                        "${if (it > 0) "+" else ""}${it.trimmed(1)}%",
                        if (abs(it) > 15) palette.caution else palette.signal,
                    )
                }
            }
        }

        if (expanded) {
            vendor.reports.sortedByDescending { it.testDate }.forEach { report ->
                Spacer(Modifier.height(8.dp))
                AssayRow(report)
            }
        }

        Text(
            "Add assay",
            style = ui(13, FontWeight.Medium),
            color = palette.amber,
            modifier = Modifier.clickable(onClick = onAddAssay),
        )
    }
}

@Composable
private fun AssayRow(report: LabReportEntity) {
    val palette = Theme.palette
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(report.compoundName, style = ui(14, FontWeight.SemiBold), color = palette.ink)
            Spacer(Modifier.weight(1f))
            Text(
                if (report.isIndependent) "Independent" else "Seller's lab",
                style = ui(11, FontWeight.SemiBold),
                color = if (report.isIndependent) palette.signal else palette.caution,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        (if (report.isIndependent) palette.signal else palette.caution)
                            .copy(alpha = 0.13f)
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        Text(
            "Lot ${report.lotNumber.ifBlank { "—" }} · ${dayStamp.format(Date(report.testDate))}" +
                if (report.testingLab.isBlank()) "" else " · ${report.testingLab}",
            style = data(11),
            color = palette.steel,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            report.purityPercent?.let { Stat("Purity", "${it.trimmed(1)}%") }
            report.measuredContentMg?.let { Stat("Measured", "${it.trimmed(2)} mg") }
            report.contentDeviationPercent?.let {
                Stat(
                    "vs label",
                    "${if (it > 0) "+" else ""}${it.trimmed(1)}%",
                    if (abs(it) > 15) palette.caution else palette.signal,
                )
            }
        }

        Text(
            report.verdict.label,
            style = ui(12, FontWeight.Medium),
            color = if (report.verdict == AssayVerdict.MAJOR_DEVIATION) palette.caution else palette.steel,
        )
    }
}

@Composable
private fun Stat(label: String, value: String, tint: Color? = null) {
    val palette = Theme.palette
    Column {
        Text(
            label.uppercase(),
            style = ui(10, FontWeight.SemiBold),
            color = palette.steel,
        )
        Text(value, style = data(14, FontWeight.SemiBold), color = tint ?: palette.ink)
    }
}

@Composable
private fun AddVendorSheet(onAdd: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.padding(Theme.gutter),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add vendor", style = display(22), color = Theme.palette.ink)
        OutlinedTextField(name, { name = it }, label = { Text("Name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(website, { website = it }, label = { Text("Website") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Notes") },
            maxLines = 3, modifier = Modifier.fillMaxWidth())
        if (name.isNotBlank()) PrimaryAction("Add vendor") { onAdd(name, website, notes) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AddAssaySheet(vendorId: Long, onSave: (LabReportEntity) -> Unit) {
    val palette = Theme.palette
    var compound by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var lab by remember { mutableStateOf("") }
    var independent by remember { mutableStateOf(true) }
    var labelClaim by remember { mutableDoubleStateOf(5.0) }
    var purity by remember { mutableStateOf("") }
    var measured by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .padding(Theme.gutter)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Add assay", style = display(22), color = palette.ink)

        OutlinedTextField(compound, { compound = it }, label = { Text("Compound") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lot, { lot = it }, label = { Text("Lot number") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lab, { lab = it }, label = { Text("Testing lab") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = independent, onCheckedChange = { independent = it })
            Column {
                Text("Commissioned independently", style = ui(14), color = palette.ink)
                Text(
                    if (independent) {
                        "Counts toward this vendor's record."
                    } else {
                        "Stored, but excluded from purity and deviation stats."
                    },
                    style = ui(12),
                    color = palette.steel,
                )
            }
        }

        NumberRow("Label claim", "mg", labelClaim, { labelClaim = it }, 0.5, 0.5..100.0, 1)

        OutlinedTextField(purity, { purity = it }, label = { Text("Purity %") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(measured, { measured = it }, label = { Text("Measured content, mg") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(url, { url = it }, label = { Text("Report URL") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        if (compound.isNotBlank()) {
            PrimaryAction("Save assay") {
                onSave(
                    LabReportEntity(
                        vendorId = vendorId,
                        compoundName = compound.trim(),
                        lotNumber = lot,
                        testingLab = lab,
                        isIndependent = independent,
                        purityPercent = purity.toDoubleOrNull(),
                        measuredContentMg = measured.toDoubleOrNull(),
                        labelClaimMg = labelClaim,
                        sourceUrl = url,
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
