package com.app.haven.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.app.haven.data.local.daos.ChoreDraftDao
import com.app.haven.data.local.daos.ExpenseDao
import com.app.haven.data.local.daos.ExpenseDraftDao
import com.app.haven.data.local.daos.SettlementDao
import com.app.haven.data.local.entities.ChoreDraftEntity
import com.app.haven.data.local.entities.ExpenseDraftEntity
import com.app.haven.data.local.entities.ExpenseEntity
import com.app.haven.data.local.entities.SettlementEntity

//the local database on the phone: a copy of the household's expenses and settlements
//**firestore fills it through the repositories, and the screens read from it
//plus unsaved add-expense and add-chore drafts, which exist only here
@Database(
    entities = [ExpenseEntity::class, SettlementEntity::class, ExpenseDraftEntity::class, ChoreDraftEntity::class],
    version = 6,
    exportSchema = false
)
//registering the type converter
@TypeConverters(Converters::class)
abstract class HavenDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao
    abstract fun settlementDao(): SettlementDao
    abstract fun expenseDraftDao(): ExpenseDraftDao
    abstract fun choreDraftDao(): ChoreDraftDao

    companion object {
        @Volatile private var instance: HavenDatabase? = null

        //3 -> 4: only adds the new table, nothing else is touched or wiped
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `chore_draft` (" +
                            "`householdId` TEXT NOT NULL, `title` TEXT NOT NULL, `categoryKey` TEXT, " +
                            "`notes` TEXT NOT NULL, `assigneeUid` TEXT, `dueOffset` INTEGER, " +
                            "`fixedDueDay` INTEGER NOT NULL, `repeatDays` INTEGER NOT NULL, " +
                            "`rotate` INTEGER NOT NULL, `endDay` INTEGER NOT NULL, " +
                            "PRIMARY KEY(`householdId`))"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `dateEpochDay` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `expense_draft` ADD COLUMN `dateEpochDay` INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expenses` ADD COLUMN `recurringId` TEXT NOT NULL DEFAULT ''")
            }
        }

        //one shared database for the whole app
        fun get(context: Context): HavenDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HavenDatabase::class.java,
                    "haven.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    //any other version change rebuilds instead of migrating: fine for the Firestore copies,
                    //but careful: this also wipes unsaved drafts, so add real migrations before changing tables again
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { instance = it }
            }
    }
}