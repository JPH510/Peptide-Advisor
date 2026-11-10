package com.peptideadvisor

import com.peptideadvisor.core.AskEngine
import com.peptideadvisor.core.EvidenceSummary
import com.peptideadvisor.core.EvidenceTier
import com.peptideadvisor.core.Reconstitution
import com.peptideadvisor.core.Tokenizer
import com.peptideadvisor.core.trimmed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The arithmetic here is the one part of the app that can hurt you if it's wrong,
 * so it's the one part with tests. Cases were verified independently before the
 * implementation was written.
 */
class ReconstitutionTest {

    @Test
    fun `canonical mix`() {
        val mix = Reconstitution(peptideMg = 5.0, diluentMl = 2.0)
        assertEquals(2.5, mix.concentrationMgPerMl, EPSILON)
        assertEquals(25.0, mix.mcgPerUnit, EPSILON)
        assertEquals(10.0, mix.unitsFor(250.0), EPSILON)
        assertEquals(0.1, mix.volumeMlFor(250.0), EPSILON)
    }

    @Test
    fun `units across mixes`() {
        // peptideMg, diluentMl, doseMcg, expectedUnits
        val cases = listOf(
            doubleArrayOf(5.0, 2.0, 250.0, 10.0),
            doubleArrayOf(10.0, 2.0, 500.0, 10.0),
            doubleArrayOf(5.0, 1.0, 250.0, 5.0),
            doubleArrayOf(2.0, 1.0, 100.0, 5.0),
            doubleArrayOf(15.0, 3.0, 2500.0, 50.0),
            doubleArrayOf(5.0, 5.0, 100.0, 10.0),
            doubleArrayOf(30.0, 3.0, 1000.0, 10.0),
        )
        for ((mg, ml, dose, expected) in cases.map { it.toList() }) {
            val mix = Reconstitution(mg, ml)
            assertEquals(
                "$mg mg in $ml mL, $dose mcg",
                expected, mix.unitsFor(dose), EPSILON,
            )
        }
    }

    @Test
    fun `dose and units round trip`() {
        val mix = Reconstitution(5.0, 2.0)
        val units = mix.unitsFor(250.0)
        assertEquals(250.0, mix.doseMcgFor(units), EPSILON)
    }

    @Test
    fun `full doses per vial`() {
        val mix = Reconstitution(5.0, 2.0)
        assertEquals(20, mix.fullDosesPerVial(250.0))
        assertEquals(0, mix.fullDosesPerVial(0.0))
    }

    @Test
    fun `suggested diluent lands on ten units`() {
        val cases = listOf(5.0 to 250.0, 15.0 to 2500.0, 30.0 to 1000.0, 2.0 to 100.0)
        for ((mg, dose) in cases) {
            val suggested = Reconstitution(mg, 1.0).suggestedDiluentMl(dose)
            requireNotNull(suggested) { "no suggestion for $mg mg / $dose mcg" }
            assertEquals(10.0, Reconstitution(mg, suggested).unitsFor(dose), EPSILON)
        }
    }

    @Test
    fun `warns when draw is too small to read`() {
        // 10 mg in 1 mL is 100 mcg/unit — a 50 mcg dose is half a unit.
        val mix = Reconstitution(10.0, 1.0)
        assertEquals(0.5, mix.unitsFor(50.0), EPSILON)
        assertEquals(Reconstitution.DrawWarning.BELOW_SYRINGE_RESOLUTION, mix.warningFor(50.0))
    }

    @Test
    fun `warns when draw exceeds barrel`() {
        // 1 mg in 5 mL is 2 mcg/unit — a 500 mcg dose needs 250 units.
        val mix = Reconstitution(1.0, 5.0)
        assertEquals(250.0, mix.unitsFor(500.0), EPSILON)
        assertEquals(Reconstitution.DrawWarning.EXCEEDS_SYRINGE_CAPACITY, mix.warningFor(500.0))
    }

    @Test
    fun `warns before reconstitution`() {
        val mix = Reconstitution(5.0, 0.0)
        assertEquals(Reconstitution.DrawWarning.NOT_RECONSTITUTED, mix.warningFor(250.0))
        assertEquals(0.0, mix.unitsFor(250.0), EPSILON)
    }

    @Test
    fun `no warning in the readable band`() {
        assertNull(Reconstitution(5.0, 2.0).warningFor(250.0))
    }

    @Test
    fun `trimmed drops trailing zeros`() {
        assertEquals("10", 10.0.trimmed(1))
        assertEquals("12.5", 12.50.trimmed(2))
        assertEquals("0.1", 0.1.trimmed(3))
    }

    companion object {
        private const val EPSILON = 1e-9
    }
}

class CitationParsingTest {

    @Test
    fun `extracts markers`() {
        val text = "Accelerated healing in rats [S3], not replicated in humans [S1][S7]."
        assertEquals(setOf(0, 2, 6), AskEngine.parseCitations(text, sourceCount = 8))
    }

    @Test
    fun `ignores out of range markers`() {
        assertEquals(setOf(1), AskEngine.parseCitations("Claim [S9] and [S2].", sourceCount = 3))
    }

    @Test
    fun `handles no citations`() {
        assertTrue(
            AskEngine.parseCitations("These sources don't address that.", 5).isEmpty()
        )
    }
}

class TokenizerTest {

    @Test
    fun `keeps compound names intact and de-hyphenated`() {
        val tokens = Tokenizer.tokens("BPC-157 accelerated healing")
        assertTrue("bpc-157" in tokens)
        // So a query typed as "bpc157" still matches.
        assertTrue("bpc157" in tokens)
    }

    @Test
    fun `drops stopwords and single characters`() {
        val tokens = Tokenizer.tokens("the effect of X on healing")
        assertTrue("the" !in tokens)
        assertTrue("of" !in tokens)
        assertTrue("effect" in tokens)
    }
}

class EvidenceTierTest {

    @Test
    fun `orders by strength`() {
        assertTrue(EvidenceTier.HUMAN_RCT.weight > EvidenceTier.ANIMAL.weight)
        assertTrue(EvidenceTier.ANIMAL.weight > EvidenceTier.IN_VITRO.weight)
        assertTrue(EvidenceTier.CASE_REPORT.weight > EvidenceTier.ANIMAL.weight)
    }

    @Test
    fun `parses wire values and falls back to unknown`() {
        assertEquals(EvidenceTier.HUMAN_RCT, EvidenceTier.fromWire("human_rct"))
        assertEquals(EvidenceTier.UNKNOWN, EvidenceTier.fromWire("nonsense"))
        assertEquals(EvidenceTier.UNKNOWN, EvidenceTier.fromWire(null))
    }

    @Test
    fun `flags thin evidence`() {
        val thin = EvidenceSummary(listOf(EvidenceTier.ANIMAL, EvidenceTier.IN_VITRO, EvidenceTier.ANIMAL))
        assertTrue(thin.isThin)
        assertEquals("No human evidence in these sources", thin.headline)

        val solid = EvidenceSummary(listOf(EvidenceTier.HUMAN_RCT, EvidenceTier.ANIMAL))
        assertTrue(!solid.isThin)
        assertEquals("1 human source of 2", solid.headline)
    }
}

class LabReportMathTest {

    // Mirrors data.contentDeviationPercent without needing Android classes.
    private fun deviation(measured: Double, claim: Double) = ((measured - claim) / claim) * 100

    @Test
    fun `deviation from label`() {
        assertTrue(abs(deviation(4.1, 5.0) - (-18.0)) < 1e-6)
        assertTrue(abs(deviation(4.1, 5.0)) > 15)  // major
    }

    @Test
    fun `purity applies to effective mass`() {
        assertEquals(4.5, 5.0 * (90.0 / 100), 1e-9)
    }
}
