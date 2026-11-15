

package com.quackmove.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalTripEntity::class,
        SelectedVehicleEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class QuackMoveDatabase : RoomDatabase() {

    abstract fun localTripDao(): LocalTripDao

    abstract fun selectedVehicleDao(): SelectedVehicleDao

    companion object {

        // Versión 2: tabla del carro elegido en el catálogo.
        // La migración conserva los viajes ya guardados.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `selected_vehicle` (" +
                            "`id` INTEGER NOT NULL, " +
                            "`vehicleId` TEXT NOT NULL, " +
                            "`batteryPercent` REAL NOT NULL, " +
                            "`updatedAtMillis` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`id`))"
                )
            }
        }

        @Volatile
        private var INSTANCE: QuackMoveDatabase? = null

        fun getDatabase(
            context: Context
        ): QuackMoveDatabase {

            return INSTANCE ?: synchronized(this) {

                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    QuackMoveDatabase::class.java,
                    "quackmove_local.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build().also { database ->
                    INSTANCE = database
                }
            }
        }
    }
}
