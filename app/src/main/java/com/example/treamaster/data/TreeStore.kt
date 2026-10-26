package com.example.treamaster.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** One measured tree. Location fields are null when the user did not grant/allow GPS. */
@Entity(tableName = "tree_measurements", indices = [Index(value = ["uuid"], unique = true)])
data class TreeMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Global id used as the cloud primary key, so a re-sent row is recognised as a duplicate. */
    val uuid: String = UUID.randomUUID().toString(),
    /** Seedling number such as "A-1"; null when not labelled yet. See [TreeId]. */
    val treeId: String? = null,
    val species: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val heightM: Double?,
    val diameterCm: Double?,
    val distanceToTreeM: Double?,
    val latitude: Double?,
    val longitude: Double?,
    val altitudeM: Double?,
    val gpsAccuracyM: Float?,
    /** True-north bearing from the observer to the tree, degrees. */
    val bearingDeg: Double?,
    val note: String = "",
    /** Which phone measured it (see [DeviceId]); sent to the cloud so rows can be told apart. */
    val deviceId: String = "",
    /** Null until the row has been accepted by the cloud. */
    val syncedAtMs: Long? = null,
    /** Built-in test tree (debug builds only, see [samples]); it can never be deleted. */
    val isSample: Boolean = false,
    /** True while the row belongs to the measuring run still in progress; "測量完畢" clears it. */
    val inSession: Boolean = false,
    /** The scan (one measuring run) this tree belongs to; set by "測量完畢". See [Batch]. */
    val batchId: String? = null,
) {
    companion object {
        /** One full region's worth of test trees; slot i+1 is the i-th tree (see [TreeDao.getSamples]). */
        const val SAMPLE_COUNT = 20
        /** The test trees form one scan of their own. */
        const val SAMPLE_BATCH = "sample"
        /** Rows finished before scans were grouped. */
        const val LEGACY_BATCH = "legacy"
        private val sampleSpecies = listOf("樟樹", "台灣欒樹", "楓香", "光臘樹", "茄苳")

        /** Seedling-sized values that vary per tree, so the website's charts show a spread. */
        fun samples(deviceId: String, nowMs: Long = System.currentTimeMillis()) = List(SAMPLE_COUNT) { i ->
            TreeMeasurement(
                treeId = null,
                species = sampleSpecies[i % sampleSpecies.size],
                // createdAtMs fixes the order, which is what maps a sample to its slot.
                createdAtMs = nowMs + i,
                heightM = 1.5 + (i * 7 % 20) / 10.0,
                diameterCm = 3.0 + (i * 3 % 10) / 2.5,
                distanceToTreeM = 6.0,
                latitude = null,
                longitude = null,
                altitudeM = null,
                gpsAccuracyM = null,
                bearingDeg = null,
                note = "測試資料：用來確認上傳是否正常",
                deviceId = deviceId,
                isSample = true,
                batchId = SAMPLE_BATCH,
            )
        }
    }
}

/** One scan as the upload screen lists it: its trees counted, newest scans first. */
data class Batch(
    val batchId: String,
    val treeCount: Int,
    val syncedCount: Int,
    val startedAtMs: Long,
    val isSample: Boolean,
)

@Dao
interface TreeDao {
    @Insert suspend fun insert(m: TreeMeasurement): Long
    @Query("SELECT * FROM tree_measurements ORDER BY createdAtMs DESC") fun observeAll(): Flow<List<TreeMeasurement>>
    @Query("SELECT * FROM tree_measurements WHERE id = :id") suspend fun get(id: Long): TreeMeasurement?
    @Query("SELECT * FROM tree_measurements WHERE id = :id") fun observe(id: Long): Flow<TreeMeasurement?>

    /** The upload screen: the test scan first, then real scans newest first. */
    @Query(
        "SELECT batchId, COUNT(*) AS treeCount, SUM(syncedAtMs IS NOT NULL) AS syncedCount, " +
            "MIN(createdAtMs) AS startedAtMs, MAX(isSample) AS isSample " +
            "FROM tree_measurements WHERE inSession = 0 AND batchId IS NOT NULL " +
            "GROUP BY batchId ORDER BY isSample DESC, startedAtMs DESC",
    )
    fun observeBatches(): Flow<List<Batch>>
    /** Trees of one scan in the order they were measured (for samples, slot order). */
    @Query("SELECT * FROM tree_measurements WHERE batchId = :batchId AND inSession = 0 ORDER BY createdAtMs")
    fun observeBatch(batchId: String): Flow<List<TreeMeasurement>>
    @Query("SELECT * FROM tree_measurements WHERE inSession = 1 ORDER BY createdAtMs") fun observeSession(): Flow<List<TreeMeasurement>>
    /** Turns the run in progress into the scan [batchId]. */
    @Query("UPDATE tree_measurements SET inSession = 0, batchId = :batchId WHERE inSession = 1")
    suspend fun finishSession(batchId: String)
    @Query("SELECT COUNT(*) FROM tree_measurements WHERE isSample = 1") suspend fun sampleCount(): Int

    /** Rows a scan's "全部上傳" sends. Samples are always included so the test set can be sent again and again. */
    @Query(
        "SELECT * FROM tree_measurements WHERE batchId = :batchId AND inSession = 0 " +
            "AND (syncedAtMs IS NULL OR isSample = 1) ORDER BY createdAtMs",
    )
    suspend fun getUnsynced(batchId: String): List<TreeMeasurement>
    @Query("SELECT * FROM tree_measurements WHERE isSample = 1 ORDER BY createdAtMs") suspend fun getSamples(): List<TreeMeasurement>
    @Query("DELETE FROM tree_measurements WHERE isSample = 1") suspend fun deleteSamples()
    @Query("DELETE FROM tree_measurements WHERE batchId = :batchId AND isSample = 0") suspend fun deleteBatch(batchId: String)
    @Insert suspend fun insertAll(rows: List<TreeMeasurement>)
    @Query("UPDATE tree_measurements SET syncedAtMs = :atMs WHERE id = :id") suspend fun markSynced(id: Long, atMs: Long)
    /** A new number means the website has not got the row under it yet, so it becomes unsynced again. */
    @Query("UPDATE tree_measurements SET treeId = :treeId, syncedAtMs = NULL WHERE id = :id")
    suspend fun updateTreeId(id: Long, treeId: String?)
    /** The sample row is protected here as well as in the UI. */
    @Query("DELETE FROM tree_measurements WHERE id = :id AND isSample = 0") suspend fun delete(id: Long)
    @Query("SELECT * FROM tree_measurements WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun observeWithLocation(): Flow<List<TreeMeasurement>>
}

@Database(entities = [TreeMeasurement::class], version = 4, exportSchema = false)
abstract class TreeDatabase : RoomDatabase() {
    abstract fun dao(): TreeDao

    companion object {
        @Volatile private var instance: TreeDatabase? = null

        /** Adds scans; trees finished before then are kept together in one "先前的資料" scan. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tree_measurements ADD COLUMN batchId TEXT")
                db.execSQL("UPDATE tree_measurements SET batchId = '${TreeMeasurement.SAMPLE_BATCH}' WHERE isSample = 1")
                db.execSQL(
                    "UPDATE tree_measurements SET batchId = '${TreeMeasurement.LEGACY_BATCH}' WHERE isSample = 0 AND inSession = 0",
                )
            }
        }

        fun get(context: Context): TreeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TreeDatabase::class.java, "trees.db")
                // Versions 1-2 never held real data (no UI saved to them), so rebuilding is safe.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .addMigrations(MIGRATION_3_4)
                .build().also { instance = it }
        }
    }
}
