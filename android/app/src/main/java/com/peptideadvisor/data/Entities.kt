package com.peptideadvisor.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import androidx.room.TypeConverter
import com.peptideadvisor.core.Reconstitution
import java.util.concurrent.TimeUnit

@Entity(tableName = "compounds", indices = [Index(value = ["name"], unique = true)])
data class CompoundEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val aliases: List<String> = emptyList(),
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "vendors", indices = [Index(value = ["name"], unique = true)])
data class VendorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val website: String = "",
    val notes: String = "",
    val addedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "vials",
    foreignKeys = [
        ForeignKey(CompoundEntity::class, ["id"], ["compoundId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(VendorEntity::class, ["id"], ["vendorId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("compoundId"), Index("vendorId")],
)
data class VialEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val compoundId: Long,
    val vendorId: Long? = null,
    /** Label claim, milligrams. */
    val peptideMg: Double,
    /** null until reconstituted. */
    val diluentMl: Double? = null,
    val lotNumber: String = "",
    val acquiredAt: Long = System.currentTimeMillis(),
    val reconstitutedAt: Long? = null,
    val discardedAt: Long? = null,
    val notes: String = "",
)

@Entity(
    tableName = "doses",
    foreignKeys = [
        ForeignKey(VialEntity::class, ["id"], ["vialId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vialId")],
)
data class DoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vialId: Long,
    val timestamp: Long = System.currentTimeMillis(),
    val doseMcg: Double,
    /**
     * Units drawn on the U-100 barrel, recorded as entered so the log preserves
     * what actually happened rather than a recomputed ideal.
     */
    val unitsDrawn: Double,
    val site: String = "",
    val notes: String = "",
)

@Entity(
    tableName = "lab_reports",
    foreignKeys = [
        ForeignKey(VendorEntity::class, ["id"], ["vendorId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("vendorId")],
)
data class LabReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vendorId: Long,
    val compoundName: String,
    val lotNumber: String = "",
    val testingLab: String = "",
    /** True when the lab was commissioned by someone other than the seller. */
    val isIndependent: Boolean = true,
    val purityPercent: Double? = null,
    val measuredContentMg: Double? = null,
    val labelClaimMg: Double,
    val testDate: Long = System.currentTimeMillis(),
    val sourceUrl: String = "",
    val notes: String = "",
)

// ---------------------------------------------------------------- Relations

data class VialWithContext(
    @Embedded val vial: VialEntity,
    @Relation(parentColumn = "compoundId", entityColumn = "id")
    val compound: CompoundEntity?,
    @Relation(parentColumn = "vendorId", entityColumn = "id")
    val vendor: VendorEntity?,
    @Relation(parentColumn = "id", entityColumn = "vialId")
    val doses: List<DoseEntity> = emptyList(),
) {
    val reconstitution: Reconstitution
        get() = Reconstitution(vial.peptideMg, vial.diluentMl ?: 0.0)

    val isReconstituted: Boolean get() = (vial.diluentMl ?: 0.0) > 0

    val mcgDrawn: Double get() = doses.sumOf { it.doseMcg }

    val mcgRemaining: Double get() = (vial.peptideMg * 1000 - mcgDrawn).coerceAtLeast(0.0)

    val fractionRemaining: Float
        get() = if (vial.peptideMg > 0) {
            (mcgRemaining / (vial.peptideMg * 1000)).toFloat()
        } else 0f

    /**
     * Reconstituted peptide in bacteriostatic water is conventionally treated as
     * good for a limited window refrigerated. A reminder, not a stability claim —
     * actual shelf life varies by peptide.
     */
    val refrigeratedUseBy: Long?
        get() = vial.reconstitutedAt?.plus(TimeUnit.DAYS.toMillis(28))

    val isPastUseBy: Boolean
        get() = refrigeratedUseBy?.let { System.currentTimeMillis() > it } ?: false

    val isActive: Boolean get() = vial.discardedAt == null && mcgRemaining > 0

    val lastDoseMcg: Double get() = doses.maxByOrNull { it.timestamp }?.doseMcg ?: 250.0
}

data class DoseWithVial(
    @Embedded val dose: DoseEntity,
    @Relation(
        parentColumn = "vialId",
        entityColumn = "id",
        entity = VialEntity::class,
    )
    val vial: VialWithCompound?,
)

data class VialWithCompound(
    @Embedded val vial: VialEntity,
    @Relation(parentColumn = "compoundId", entityColumn = "id")
    val compound: CompoundEntity?,
)

data class VendorWithReports(
    @Embedded val vendor: VendorEntity,
    @Relation(parentColumn = "id", entityColumn = "vendorId")
    val reports: List<LabReportEntity> = emptyList(),
) {
    /**
     * Only independent reports count. A certificate from the seller's own lab
     * describes a batch you didn't receive.
     */
    val independentReports: List<LabReportEntity> get() = reports.filter { it.isIndependent }

    val testedLotCount: Int get() = independentReports.size

    val meanPurity: Double?
        get() = independentReports.mapNotNull { it.purityPercent }
            .takeIf { it.isNotEmpty() }?.average()

    /** Worst deviation from label across tested lots. */
    val worstContentDeviation: Double?
        get() = independentReports.mapNotNull { it.contentDeviationPercent }
            .maxByOrNull { kotlin.math.abs(it) }
}

// ------------------------------------------------------------ Derived values

/** Percent over or under label. -18 means the vial held 18% less than claimed. */
val LabReportEntity.contentDeviationPercent: Double?
    get() = measuredContentMg?.takeIf { labelClaimMg > 0 }
        ?.let { ((it - labelClaimMg) / labelClaimMg) * 100 }

/** Effective mg once purity is applied to measured mass. */
val LabReportEntity.effectiveMg: Double?
    get() = measuredContentMg?.let { measured ->
        purityPercent?.let { measured * (it / 100) } ?: measured
    }

enum class AssayVerdict(val label: String) {
    UNTESTED("No assay data"),
    MATCHES_LABEL("Matches label"),
    MINOR_DEVIATION("Minor deviation"),
    MAJOR_DEVIATION("Off label claim"),
}

val LabReportEntity.verdict: AssayVerdict
    get() {
        val deviation = contentDeviationPercent ?: return AssayVerdict.UNTESTED
        val magnitude = kotlin.math.abs(deviation)
        return when {
            magnitude <= 5 -> AssayVerdict.MATCHES_LABEL
            magnitude <= 15 -> AssayVerdict.MINOR_DEVIATION
            else -> AssayVerdict.MAJOR_DEVIATION
        }
    }

enum class InjectionSite(val label: String) {
    ABDOMEN_L("Abdomen L"), ABDOMEN_R("Abdomen R"),
    THIGH_L("Thigh L"), THIGH_R("Thigh R"),
    DELTOID_L("Deltoid L"), DELTOID_R("Deltoid R"),
    GLUTE("Glute"), OTHER("Other"),
}

class Converters {
    @TypeConverter
    fun aliasesToString(list: List<String>): String = list.joinToString("|")

    @TypeConverter
    fun stringToAliases(value: String): List<String> =
        if (value.isBlank()) emptyList() else value.split("|")
}
