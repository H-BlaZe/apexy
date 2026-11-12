package com.apexy.alarm.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** One tracked night. [samples] holds one "activity,noiseDb" pair per minute, separated by ';'. */
@Entity(tableName = "sleep_sessions")
data class SleepSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val endedAt: Long = 0,
    val samples: String = "",
    val snoreMinutes: Int = 0,
    val goalMinutes: Int = 480,
    /** Sounds played while falling asleep, comma-separated ids. */
    val sounds: String = "",
)

@Entity(tableName = "snore_clips", indices = [Index("sessionId")])
data class SnoreClip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val startedAt: Long,
    val durationMs: Long,
    val path: String,
    val score: Float,
)

@Dao
interface SleepDao {
    @Insert suspend fun insert(session: SleepSession): Long
    @Update suspend fun update(session: SleepSession)
    @Delete suspend fun delete(session: SleepSession)

    @Query("SELECT * FROM sleep_sessions WHERE endedAt > 0 ORDER BY startedAt DESC")
    fun observeNights(): Flow<List<SleepSession>>

    @Query("SELECT * FROM sleep_sessions WHERE id = :id")
    suspend fun get(id: Long): SleepSession?

    @Insert suspend fun insertClip(clip: SnoreClip): Long

    @Query("SELECT * FROM snore_clips WHERE sessionId = :sessionId ORDER BY startedAt")
    fun observeClips(sessionId: Long): Flow<List<SnoreClip>>

    @Query("SELECT * FROM snore_clips WHERE sessionId = :sessionId")
    suspend fun clips(sessionId: Long): List<SnoreClip>

    @Query("DELETE FROM snore_clips WHERE sessionId = :sessionId")
    suspend fun deleteClips(sessionId: Long)

    @Query("DELETE FROM sleep_sessions")
    suspend fun clearNights()

    @Query("DELETE FROM snore_clips")
    suspend fun clearClips()
}
