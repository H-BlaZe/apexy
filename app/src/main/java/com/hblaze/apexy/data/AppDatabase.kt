package com.hblaze.apexy.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute")
    fun observeAll(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms")
    suspend fun getAll(): List<Alarm>

    @Query("SELECT * FROM alarms")
    fun observeAllUnordered(): Flow<List<Alarm>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun get(id: Long): Alarm?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alarm: Alarm): Long

    @Update
    suspend fun update(alarm: Alarm)

    @Delete
    suspend fun delete(alarm: Alarm)

    @Query("DELETE FROM alarms")
    suspend fun clear()
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: AlarmEvent): Long

    @Update
    suspend fun update(event: AlarmEvent)

    @Query("SELECT * FROM alarm_events WHERE id = :id")
    suspend fun get(id: Long): AlarmEvent?

    @Query("SELECT * FROM alarm_events ORDER BY ringAt ASC")
    fun observeAll(): Flow<List<AlarmEvent>>

    @Query("DELETE FROM alarm_events")
    suspend fun clear()
}

@Database(entities = [Alarm::class, AlarmEvent::class, SleepSession::class, SnoreClip::class], version = 6, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarms(): AlarmDao
    abstract fun events(): EventDao
    abstract fun sleep(): SleepDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "apexy.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        /** v0.4: wake-up check response time and pending check time. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN wakeUpCheckResponse INTEGER NOT NULL DEFAULT 60")
                db.execSQL("ALTER TABLE alarms ADD COLUMN wakeCheckAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v0.5: richer history — event kind and mission results. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarm_events ADD COLUMN kind TEXT NOT NULL DEFAULT 'ring'")
                db.execSQL("ALTER TABLE alarm_events ADD COLUMN missionCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_events ADD COLUMN missionMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE alarm_events ADD COLUMN escapes INTEGER NOT NULL DEFAULT 0")
                // Wake-up checks from v0.4 were stored as rings labelled "Wake-up check".
                db.execSQL("UPDATE alarm_events SET kind = 'check' WHERE outcome IN ('awake', 'asleep')")
            }
        }

        /** v1.0: per-alarm accent colour. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE alarms ADD COLUMN accentColor INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v1.3: ramp curve, escalation, speech, flash, mission order and snooze rules. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf(
                    "rampStart INTEGER NOT NULL DEFAULT 5",
                    "rampCurve TEXT NOT NULL DEFAULT 'linear'",
                    "escalate INTEGER NOT NULL DEFAULT 0",
                    "escalateAfter INTEGER NOT NULL DEFAULT 2",
                    "volumeCap INTEGER NOT NULL DEFAULT 85",
                    "speakMessage TEXT NOT NULL DEFAULT ''",
                    "speakEvery INTEGER NOT NULL DEFAULT 45",
                    "flash TEXT NOT NULL DEFAULT 'off'",
                    "missionOrder TEXT NOT NULL DEFAULT 'all'",
                    "randomCount INTEGER NOT NULL DEFAULT 1",
                    "duringMission TEXT NOT NULL DEFAULT 'quiet'",
                    "idleSeconds INTEGER NOT NULL DEFAULT 30",
                    "snoozeShrink INTEGER NOT NULL DEFAULT 0",
                    "snoozeMission INTEGER NOT NULL DEFAULT 0",
                    "morningCard INTEGER NOT NULL DEFAULT 1",
                ).forEach { db.execSQL("ALTER TABLE alarms ADD COLUMN $it") }
            }
        }

        /** v0.6: sleep tracking. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_sessions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startedAt` INTEGER NOT NULL, `endedAt` INTEGER NOT NULL, `samples` TEXT NOT NULL, " +
                        "`snoreMinutes` INTEGER NOT NULL, `goalMinutes` INTEGER NOT NULL, `sounds` TEXT NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `snore_clips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`sessionId` INTEGER NOT NULL, `startedAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, " +
                        "`path` TEXT NOT NULL, `score` REAL NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_snore_clips_sessionId` ON `snore_clips` (`sessionId`)")
            }
        }
    }
}
