package com.peptideadvisor.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CompoundDao {
    @Query("SELECT * FROM compounds ORDER BY name")
    fun observeAll(): Flow<List<CompoundEntity>>

    @Query("SELECT * FROM compounds WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): CompoundEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(compound: CompoundEntity): Long

    /** Returns the existing row's id if the name is already taken. */
    suspend fun upsertByName(name: String): Long {
        findByName(name)?.let { return it.id }
        val id = insert(CompoundEntity(name = name))
        return if (id > 0) id else findByName(name)!!.id
    }
}

@Dao
interface VialDao {
    @Transaction
    @Query("SELECT * FROM vials ORDER BY acquiredAt DESC")
    fun observeAll(): Flow<List<VialWithContext>>

    @Transaction
    @Query("SELECT * FROM vials WHERE id = :id")
    fun observeById(id: Long): Flow<VialWithContext?>

    @Insert
    suspend fun insert(vial: VialEntity): Long

    @Update
    suspend fun update(vial: VialEntity)

    @Query("UPDATE vials SET diluentMl = :diluentMl, reconstitutedAt = :at WHERE id = :id")
    suspend fun setMix(id: Long, diluentMl: Double, at: Long)

    @Query("UPDATE vials SET discardedAt = :at WHERE id = :id")
    suspend fun discard(id: Long, at: Long = System.currentTimeMillis())
}

@Dao
interface DoseDao {
    @Transaction
    @Query("SELECT * FROM doses ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<DoseWithVial>>

    @Insert
    suspend fun insert(dose: DoseEntity): Long

    @Query("DELETE FROM doses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface VendorDao {
    @Transaction
    @Query("SELECT * FROM vendors ORDER BY name")
    fun observeAll(): Flow<List<VendorWithReports>>

    @Transaction
    @Query("SELECT * FROM vendors WHERE id = :id")
    fun observeById(id: Long): Flow<VendorWithReports?>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(vendor: VendorEntity): Long

    @Insert
    suspend fun insertReport(report: LabReportEntity): Long

    @Query("DELETE FROM lab_reports WHERE id = :id")
    suspend fun deleteReport(id: Long)
}

@Database(
    entities = [
        CompoundEntity::class,
        VendorEntity::class,
        VialEntity::class,
        DoseEntity::class,
        LabReportEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun compounds(): CompoundDao
    abstract fun vials(): VialDao
    abstract fun doses(): DoseDao
    abstract fun vendors(): VendorDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "peptide_advisor.db",
            ).build().also { instance = it }
        }
    }
}
