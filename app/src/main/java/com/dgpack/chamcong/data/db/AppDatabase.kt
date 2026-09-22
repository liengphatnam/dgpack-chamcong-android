package com.dgpack.chamcong.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        AttendanceEventEntity::class,
        ErpEmployeeEntity::class,
        LuckyDrawWinEntity::class,
        CardAssignmentEntity::class,
        ForgotCardLogEntity::class,
        EmployeeMonthSummaryEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun attendanceEventDao(): AttendanceEventDao
    abstract fun erpEmployeeDao(): ErpEmployeeDao
    abstract fun luckyDrawWinDao(): LuckyDrawWinDao
    abstract fun cardAssignmentDao(): CardAssignmentDao
    abstract fun forgotCardLogDao(): ForgotCardLogDao
    abstract fun employeeMonthSummaryDao(): EmployeeMonthSummaryDao

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

        /**
         * v4: chấm công bằng THẺ TỪ —
         *  - erp_employee thêm cardId (DROP + CREATE vì chỉ là cache),
         *  - attendance_event_local thêm method ('Face'|'Card'|'ForgotCard') + cardId (dòng cũ = Face),
         *  - bảng card_assignment (thẻ gán trên máy), forgot_card_log (quên thẻ + ảnh bằng chứng),
         *    employee_month_summary (cache chi tiết công tháng từ ERP).
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
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
                        "`cardId` TEXT, " +
                        "PRIMARY KEY(`employeeCode`))"
                )
                db.execSQL("ALTER TABLE `attendance_event_local` ADD COLUMN `method` TEXT NOT NULL DEFAULT 'Face'")
                db.execSQL("ALTER TABLE `attendance_event_local` ADD COLUMN `cardId` TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `card_assignment` (" +
                        "`cardId` TEXT NOT NULL, " +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`assignedAt` TEXT NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, " +
                        "`syncedAt` TEXT, " +
                        "PRIMARY KEY(`cardId`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `forgot_card_log` (" +
                        "`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`fullName` TEXT NOT NULL, " +
                        "`eventTimeUtc` TEXT NOT NULL, " +
                        "`dateVn` TEXT NOT NULL, " +
                        "`monthVn` TEXT NOT NULL, " +
                        "`photoJpeg` BLOB, " +
                        "`deviceCode` TEXT NOT NULL, " +
                        "`syncStatus` TEXT NOT NULL, " +
                        "`syncedAt` TEXT)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `employee_month_summary` (" +
                        "`employeeCode` TEXT NOT NULL, " +
                        "`month` TEXT NOT NULL, " +
                        "`workDays` REAL NOT NULL, " +
                        "`otRegularHours` REAL NOT NULL, " +
                        "`otSundayHours` REAL NOT NULL, " +
                        "`otHolidayHours` REAL NOT NULL, " +
                        "`leaveDays` REAL NOT NULL, " +
                        "`disciplinaryCount` INTEGER NOT NULL, " +
                        "`commendationCount` INTEGER NOT NULL, " +
                        "`forgotCardCount` INTEGER NOT NULL, " +
                        "`penaltyAmount` INTEGER NOT NULL, " +
                        "`syncedAt` TEXT NOT NULL, " +
                        "PRIMARY KEY(`employeeCode`, `month`))"
                )
            }
        }

        /** v5: bỏ nhận diện khuôn mặt — xoá bảng enrolled_employee (embedding). */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `enrolled_employee`")
            }
        }

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "chamcong.db"
                )
                    // KHÔNG dùng fallbackToDestructiveMigration — sẽ xoá sạch sự kiện chưa đồng bộ,
                    // thẻ đã gán và nhật ký quên thẻ trên tablet đang chạy bản cũ.
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build().also { instance = it }
            }
    }
}
