package com.peptideadvisor.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.peptideadvisor.core.Reconstitution
import com.peptideadvisor.core.toQuarterMl
import com.peptideadvisor.core.trimmed
import com.peptideadvisor.ui.components.BenchCard
import com.peptideadvisor.ui.components.FactRow
import com.peptideadvisor.ui.components.FieldLabel
import com.peptideadvisor.ui.components.NumberRow
import com.peptideadvisor.ui.components.SyringeScale
import com.peptideadvisor.ui.theme.Theme
import com.peptideadvisor.ui.theme.data
import com.peptideadvisor.ui.theme.ui

@Composable
fun CalculatorScreen(
    initialPeptideMg: Double = 5.0,
    initialDiluentMl: Double = 2.0,
    initialDoseMcg: Double = 250.0,
    onCommit: ((Double) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val palette = Theme.palette

    var peptideMg by remember { mutableDoubleStateOf(initialPeptideMg) }
    var diluentMl by remember { mutableDoubleStateOf(initialDiluentMl) }
    var doseMcg by remember { mutableDoubleStateOf(initialDoseMcg) }

    val mix = Reconstitution(peptideMg, diluentMl)
    val units = mix.unitsFor(doseMcg)
    val warning = mix.warningFor(doseMcg)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bench)
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(Theme.gutter)),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        // Readout
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    units.trimmed(1),
                    style = data(52, FontWeight.SemiBold),
                    color = if (warning == null) palette.ink else palette.caution,
                )
                Spacer(Modifier.width(6.dp))
                Text("units", style = ui(17), color = palette.steel)
            }

            SyringeScale(units = units, isSuspect = warning != null)

            Text(
                "${doseMcg.trimmed(0)} mcg  ·  ${mix.volumeMlFor(doseMcg).trimmed(3)} mL  ·  " +
                    "${mix.concentrationMgPerMl.trimmed(2)} mg/mL",
                style = data(12),
                color = palette.steel,
            )
        }

        // Inputs
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            NumberRow("Vial contents", "mg", peptideMg, { peptideMg = it }, 0.5, 0.5..100.0, 1)
            NumberRow("Bacteriostatic water", "mL", diluentMl, { diluentMl = it }, 0.25, 0.25..10.0, 2)
            NumberRow("Target dose", "mcg", doseMcg, { doseMcg = it }, 25.0, 5.0..5000.0, 0)
        }

        // Warning
        warning?.let { w ->
            BenchCard {
                FieldLabel("Check this draw")
                Text(w.message, style = ui(14), color = palette.ink)

                if (w != Reconstitution.DrawWarning.NOT_RECONSTITUTED) {
                    mix.suggestedDiluentMl(doseMcg)?.let { suggested ->
                        Text(
                            "Use ${suggested.trimmed(2)} mL instead — puts this dose at 10 units",
                            style = ui(13, FontWeight.Medium),
                            color = palette.amber,
                            modifier = Modifier.clickable {
                                diluentMl = suggested.toQuarterMl().coerceIn(0.25, 10.0)
                            },
                        )
                    }
                }
            }
        }

        // Derived
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FieldLabel("At this mix")
            BenchCard {
                FactRow("Per unit", "${mix.mcgPerUnit.trimmed(1)} mcg")
                HorizontalDivider(color = palette.hairline)
                FactRow("Full doses in vial", "${mix.fullDosesPerVial(doseMcg)}")
                HorizontalDivider(color = palette.hairline)
                FactRow("Concentration", "${mix.concentrationMcgPerMl.trimmed(0)} mcg/mL")
            }
            Text(
                "A U-100 syringe reads 100 units to 1 mL. Every number here comes from " +
                    "that identity — nothing is estimated.",
                style = ui(12),
                color = palette.steel,
            )
        }

        onCommit?.let { commit ->
            Text(
                "Save this mix",
                style = ui(16, FontWeight.SemiBold),
                color = palette.paper,
                modifier = Modifier
                    .background(palette.amber, androidx.compose.foundation.shape.RoundedCornerShape(Theme.radius))
                    .clickable { commit(diluentMl) }
                    .padding(horizontal = 20.dp, vertical = 13.dp),
            )
        }
    }
}
