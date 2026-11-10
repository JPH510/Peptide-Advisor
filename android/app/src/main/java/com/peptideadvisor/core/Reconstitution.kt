package com.peptideadvisor.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure arithmetic for turning a lyophilized vial + diluent into syringe units.
 *
 * A U-100 insulin syringe is graduated so 100 units == 1.0 mL. Every conversion
 * here routes through that identity — nothing is estimated.
 *
 *     5 mg vial + 2 mL bacteriostatic water
 *       -> 2.5 mg/mL
 *       -> 25 mcg per unit
 *       -> a 250 mcg dose is 10 units
 */
data class Reconstitution(
    /** Milligrams of peptide in the vial, per the label. */
    val peptideMg: Double,
    /** Milliliters of bacteriostatic water added. */
    val diluentMl: Double,
) {
    val concentrationMgPerMl: Double
        get() = if (diluentMl > 0) peptideMg / diluentMl else 0.0

    val concentrationMcgPerMl: Double
        get() = concentrationMgPerMl * 1000

    /** Micrograms delivered per single unit mark on a U-100 barrel. */
    val mcgPerUnit: Double
        get() = concentrationMcgPerMl * ML_PER_UNIT

    fun unitsFor(doseMcg: Double): Double =
        if (mcgPerUnit > 0) doseMcg / mcgPerUnit else 0.0

    fun volumeMlFor(doseMcg: Double): Double = unitsFor(doseMcg) * ML_PER_UNIT

    fun doseMcgFor(units: Double): Double = units * mcgPerUnit

    /** Whole doses left if the vial is drawn down at this dose. */
    fun fullDosesPerVial(doseMcg: Double): Int =
        if (doseMcg > 0) ((peptideMg * 1000) / doseMcg).toInt() else 0

    enum class DrawWarning(val message: String) {
        BELOW_SYRINGE_RESOLUTION(
            "Under 2 units. Measurement error at this mark is large — add more " +
                "diluent so the same dose lands higher on the barrel."
        ),
        EXCEEDS_SYRINGE_CAPACITY(
            "Over 100 units. This dose won't fit in one U-100 syringe — mix more " +
                "concentrated or split the draw."
        ),
        NOT_RECONSTITUTED("Add a diluent volume to calculate a draw."),
    }

    fun warningFor(doseMcg: Double): DrawWarning? {
        if (diluentMl <= 0 || peptideMg <= 0) return DrawWarning.NOT_RECONSTITUTED
        val u = unitsFor(doseMcg)
        return when {
            u > UNITS_PER_ML -> DrawWarning.EXCEEDS_SYRINGE_CAPACITY
            u > 0 && u < 2 -> DrawWarning.BELOW_SYRINGE_RESOLUTION
            else -> null
        }
    }

    /** The diluent volume that puts [doseMcg] on a clean, readable mark. */
    fun suggestedDiluentMl(doseMcg: Double, targetUnits: Double = 10.0): Double? {
        if (doseMcg <= 0 || peptideMg <= 0 || targetUnits <= 0) return null
        val dosesPerVial = (peptideMg * 1000) / doseMcg
        return (targetUnits * ML_PER_UNIT) * dosesPerVial
    }

    companion object {
        const val UNITS_PER_ML = 100.0
        const val ML_PER_UNIT = 0.01
    }
}

/** Trims trailing zeros: 10.0 -> "10", 12.50 -> "12.5" */
fun Double.trimmed(places: Int = 2): String {
    if (isNaN() || isInfinite()) return "—"
    val s = String.format("%.${places}f", this)
    if (!s.contains('.')) return s
    return s.trimEnd('0').trimEnd('.')
}

/** Rounds to the nearest quarter mL — the resolution of a real syringe. */
fun Double.toQuarterMl(): Double = (this * 4).roundToInt() / 4.0

fun Double.nearlyEquals(other: Double, tolerance: Double = 1e-6): Boolean =
    abs(this - other) < tolerance
