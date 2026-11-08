package com.luis.end_security.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.luis.end_security.core.database.dao.AccountDao
import com.luis.end_security.core.database.dao.BreachCheckDao
import com.luis.end_security.core.database.dao.HealthSnapshotDao
import com.luis.end_security.core.database.dao.VaultItemDao
import com.luis.end_security.core.database.entity.AccountEntity
import com.luis.end_security.core.database.entity.BreachCheckEntity
import com.luis.end_security.core.database.entity.HealthSnapshotEntity
import com.luis.end_security.core.database.entity.SyncState
import com.luis.end_security.core.database.entity.VaultItemEntity

/**
 * `end_security.db` (modelo de datos §8). Sin SQLCipher (DEC-47): solo guarda bloques cifrados
 * y con `allowBackup = false` no sale del teléfono. Al cerrar sesión se vacía con
 * `clearAllTables()` (OP-18), fuera del hilo principal.
 */
@Database(
    entities = [AccountEntity::class, VaultItemEntity::class, BreachCheckEntity::class, HealthSnapshotEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun breachCheckDao(): BreachCheckDao
    abstract fun healthSnapshotDao(): HealthSnapshotDao

    companion object {
        const val NAME = "end_security.db"
    }
}

/** `sync_state` como `synced` / `pending` / `conflict` (DEC-47). */
class DatabaseConverters {
    @TypeConverter
    fun syncStateToDb(state: SyncState): String = state.dbValue

    @TypeConverter
    fun syncStateFromDb(value: String): SyncState = SyncState.fromDb(value)
}
