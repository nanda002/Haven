package com.app.haven.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.haven.data.local.daos.ExpenseDao
import com.app.haven.data.local.daos.ExpenseDraftDao
import com.app.haven.data.local.daos.SettlementDao
import com.app.haven.data.local.entities.ExpenseDraftEntity
import com.app.haven.data.local.entities.ExpenseEntity
import com.app.haven.data.local.entities.SettlementEntity

//the local database on the phone: a copy of the household's expenses and settlements
//**firestore fills it through the repositories, and the screens read from it
//plus unsaved add-expense drafts, which exist only here
@Database(
    entities = [ExpenseEntity::class, SettlementEntity::class, ExpenseDraftEntity::class],
    version = 2,
    exportSchema = false
)
//registering the type converter
@TypeConverters(Converters::class)
abstract class HavenDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao
    abstract fun expenseDraftDao(): ExpenseDraftDao

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
                    //if the tables change, rebuild instead of migrating: fine for the Firestore copies,
                    //but careful: this also wipes unsaved drafts, so add real migrations before changing tables again
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}