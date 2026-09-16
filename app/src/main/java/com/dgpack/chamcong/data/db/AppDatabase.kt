package com.dgpack.chamcong.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        EnrolledEmployeeEntity::class,
        AttendanceEventEntity::class,
        ErpEmployeeEntity::class,
        LuckyDrawWinEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun enrolledEmployeeDao(): EnrolledEmployeeDao
    abstract fun attendanceEventDao(): AttendanceEventDao
    abstract fun erpEmployeeDao(): ErpEmployeeDao
    abstract fun luckyDrawWinDao(): LuckyDrawWinDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /**
         * v2: đồng bộ nhân viên + embedding với ERP (API_FACE_SYNC.md) —
         *  - enrolled_employee thêm cột faceSyncedAt (mốc server đã nhận embedding),
         *  - bảng erp_employee mới (bản sao dm.Employee để biết ai chưa có khuôn mặt).
         * Câu CREATE phải khớp đúng schema Room sinh ra từ ErpEmployeeEntity (Room kiểm tra lúc mở DB).
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `enrolled_employee` ADD COLUMN `faceSyncedAt` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `erp_employee` (" +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`fullName` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`hasFaceOnServer` INTEGER NOT NULL, " +
                        "`faceUpdatedAt` TEXT, " +
                        "`syncedAt` TEXT NOT NULL, " +
                        "PRIMARY KEY(`employeeCode`))"
                )
            }
        }

        /**
         * v3: chương trình trúng thưởng lon nước ngọt (tháng 8–9/2026) —
         *  - erp_employee thêm birthDate / lateEarlyCount30d / commendationCount. Bảng này chỉ là
         *    cache kéo lại toàn bộ mỗi lần đồng bộ nên DROP + CREATE cho khớp schema Room, không
         *    mất dữ liệu gì quan trọng (lần đồng bộ tới sẽ đầy lại).
         *  - bảng lucky_draw_win mới (sổ người trúng thưởng).
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `erp_employee`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `erp_employee` (" +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`fullName` TEXT NOT NULL, " +
                        "`isActive` INTEGER NOT NULL, " +
                        "`hasFaceOnServer` INTEGER NOT NULL, " +
                        "`faceUpdatedAt` TEXT, " +
                        "`syncedAt` TEXT NOT NULL, " +
                        "`birthDate` TEXT, " +
                        "`lateEarlyCount30d` INTEGER NOT NULL, " +
                        "`commendationCount` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`employeeCode`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lucky_draw_win` (" +
                        "`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`fullName` TEXT NOT NULL, " +
                        "`drawDate` TEXT NOT NULL, " +
                        "`wonAtUtc` TEXT NOT NULL, " +
                        "`cans` INTEGER NOT NULL, " +
                        "`reason` TEXT NOT NULL, " +
                        "`weight` REAL NOT NULL, " +
                        "`chance` REAL NOT NULL, " +
                        "`deviceCode` TEXT NOT NULL, " +
                        "`claimed` INTEGER NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, " +
                        "`syncedAt` TEXT)"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chamcong.db"
                )
                    // KHÔNG dùng fallbackToDestructiveMigration — sẽ xoá sạch khuôn mặt đã enroll
                    // và sự kiện chưa đồng bộ trên tablet đang chạy bản cũ.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { instance = it }
            }
    }
}
