package com.app.haven.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.haven.data.local.daos.ExpenseDao
import com.app.haven.data.local.daos.SettlementDao
import com.app.haven.data.local.entities.ExpenseEntity
import com.app.haven.data.local.entities.SettlementEntity

//the local database on the phone: a copy of the household's expenses and settlements
//**firestore fills it through the repositories, and the screens read from it
@Database(
    entities = [ExpenseEntity::class, SettlementEntity::class],
    version = 1,
    exportSchema = false
)
//registering the type converter
@TypeConverters(Converters::class)
abstract class HavenDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao

    companion object {
        @Volatile private var instance: HavenDatabase? = null

        //one shared database for the whole app
        fun get(context: Context): HavenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HavenDatabase::class.java,
                    "haven.db"
                )
                    //it's only a copy of Firestore, so if the tables change, rebuild instead of migrating
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}