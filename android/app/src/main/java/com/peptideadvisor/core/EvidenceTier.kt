package com.peptideadvisor.core

import androidx.compose.ui.graphics.Color
import com.peptideadvisor.ui.theme.Bench

/**
 * How much weight a single source can actually carry.
 *
 * This is the spine of the app. Every citation surfaces its tier, so "four rat
 * studies" can never be mistaken for "a human trial."
 */
enum class EvidenceTier(
    val wire: String,
    val weight: Int,
    val label: String,
    val caveat: String,
) {
    HUMAN_RCT(
        "human_rct", 7, "Human RCT",
        "Randomized in people. The strongest single source type here."
    ),
    HUMAN_CONTROLLED(
        "human_controlled", 6, "Human, controlled",
        "Human trial with a comparison group, but not randomized."
    ),
    HUMAN_OBSERVATIONAL(
        "human_observational", 5, "Human, observational",
        "Measured in people without assigning treatment. Shows association, not cause."
    ),
    REVIEW(
        "review", 4, "Review",
        "Summarizes other work. Inherits the quality of what it reviewed."
    ),
    CASE_REPORT(
        "case_report", 3, "Case report",
        "One person, or a handful. Generates hypotheses, settles nothing."
    ),
    ANIMAL(
        "animal", 2, "Animal",
        "Rodent or other animal model. Dose and effect rarely transfer cleanly to people."
    ),
    IN_VITRO(
        "in_vitro", 1, "In vitro",
        "Cells in a dish. Furthest from a human outcome."
    ),
    UNKNOWN(
        "unknown", 0, "Untagged",
        "Not yet classified by the tagging pass."
    );

    val isHuman: Boolean get() = weight >= 5

    fun tint(palette: Bench): Color = when (this) {
        HUMAN_RCT, HUMAN_CONTROLLED -> palette.signal
        HUMAN_OBSERVATIONAL, REVIEW -> palette.signalMuted
        CASE_REPORT, ANIMAL -> palette.amber
        IN_VITRO -> palette.steel
        UNKNOWN -> palette.steel.copy(alpha = 0.5f)
    }

    companion object {
        fun fromWire(value: String?): EvidenceTier =
            entries.firstOrNull { it.wire == value } ?: UNKNOWN

        /** Strongest first — the order the corpus breakdown is displayed in. */
        val byStrength: List<EvidenceTier> get() = entries.sortedByDescending { it.weight }
    }
}

/** Aggregate read on a whole answer's sourcing. */
data class EvidenceSummary(val tiers: List<EvidenceTier>) {

    val best: EvidenceTier get() = tiers.maxByOrNull { it.weight } ?: EvidenceTier.UNKNOWN

    val humanCount: Int get() = tiers.count { it.isHuman }

    val headline: String
        get() = when {
            tiers.isEmpty() -> "No sources retrieved"
            humanCount == 0 -> "No human evidence in these sources"
            else -> "$humanCount human ${if (humanCount == 1) "source" else "sources"} of ${tiers.size}"
        }

    val isThin: Boolean get() = tiers.isNotEmpty() && tiers.all { it.weight <= 4 }
}
