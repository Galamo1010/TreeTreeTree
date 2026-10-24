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
    /** Built-in row for trying the upload; it can never be deleted. */
    val isSample: Boolean = false,
    /** True while the row belongs to the measuring run still in progress; "測量完畢" clears it. */
    val inSession: Boolean = false,
) {
    companion object {
        fun sample(deviceId: String) = TreeMeasurement(
            treeId = null,
            species = "測試樹種",
            heightM = 5.0,
            diameterCm = 20.0,
            distanceToTreeM = 6.0,
            latitude = null,
            longitude = null,
            altitudeM = null,
            gpsAccuracyM = null,
            bearingDeg = null,
            note = "測試資料：用來確認上傳是否正常",
            deviceId = deviceId,
            isSample = true,
        )
    }
}

@Dao
interface TreeDao {
    @Insert suspend fun insert(m: TreeMeasurement): Long
    @Query("SELECT * FROM tree_measurements ORDER BY createdAtMs DESC") fun observeAll(): Flow<List<TreeMeasurement>>
    @Query("SELECT * FROM tree_measurements WHERE id = :id") suspend fun get(id: Long): TreeMeasurement?
    @Query("SELECT * FROM tree_measurements WHERE id = :id") fun observe(id: Long): Flow<TreeMeasurement?>

    /** Everything shown on the upload screen: the sample first, then finished measurements, newest first. */
    @Query("SELECT * FROM tree_measurements WHERE inSession = 0 ORDER BY isSample DESC, createdAtMs DESC")
    fun observeUploadList(): Flow<List<TreeMeasurement>>
    @Query("SELECT * FROM tree_measurements WHERE inSession = 1 ORDER BY createdAtMs") fun observeSession(): Flow<List<TreeMeasurement>>
    @Query("UPDATE tree_measurements SET inSession = 0 WHERE inSession = 1") suspend fun finishSession()
    @Query("SELECT COUNT(*) FROM tree_measurements WHERE isSample = 1") suspend fun sampleCount(): Int

    @Query("SELECT * FROM tree_measurements WHERE syncedAtMs IS NULL AND inSession = 0 ORDER BY createdAtMs")
    suspend fun getUnsynced(): List<TreeMeasurement>
    @Query("UPDATE tree_measurements SET syncedAtMs = :atMs WHERE id = :id") suspend fun markSynced(id: Long, atMs: Long)
    /** The sample row is protected here as well as in the UI. */
    @Query("DELETE FROM tree_measurements WHERE id = :id AND isSample = 0") suspend fun delete(id: Long)
    @Query("SELECT * FROM tree_measurements WHERE latitude IS NOT NULL AND longitude IS NOT NULL")
    fun observeWithLocation(): Flow<List<TreeMeasurement>>
}

@Database(entities = [TreeMeasurement::class], version = 3, exportSchema = false)
abstract class TreeDatabase : RoomDatabase() {
    abstract fun dao(): TreeDao

    companion object {
        @Volatile private var instance: TreeDatabase? = null
        fun get(context: Context): TreeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TreeDatabase::class.java, "trees.db")
                // Versions 1-2 never held real data (no UI saved to them), so rebuilding is safe.
                .fallbackToDestructiveMigrationFrom(1, 2)
                .build().also { instance = it }
        }
    }
}
