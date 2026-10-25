package com.example.personalworkbench.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库迁移。
 *
 * 版本 1 → 2（V1.3.3）：`schedules` 新增 `is_completed` 列，用于「日程可勾选已完成」。
 * 采用 ADD COLUMN + NOT NULL DEFAULT，对既有数据无破坏，用户日程不会丢失。
 *
 * 版本 2 → 3（V1.3.5）：`notes` 新增 `pinned` 与 `order_index` 两列，
 * 支撑「笔记置顶 + 长按移动排序」。同样采用 ADD COLUMN + NOT NULL DEFAULT，既有笔记不受影响。
 *
 * 约定：**一旦发版，任何 schema 变更都必须在这里补一条 Migration**，
 * 不能再依赖 `fallbackToDestructiveMigration()`（那会清空用户数据）。
 */
object Migrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE schedules ADD COLUMN is_completed INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE notes ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0"
            )
            db.execSQL(
                "ALTER TABLE notes ADD COLUMN order_index INTEGER NOT NULL DEFAULT 0"
            )
        }
    }

    // 版本 3 → 4（V1.3.7）：新增 work_logs 工作日志表，支撑「日志填写」+ 周/月报自动汇总。
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS work_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    log_date INTEGER NOT NULL,
                    content TEXT NOT NULL DEFAULT '',
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )"""
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_logs_log_date ON work_logs (log_date)")
        }
    }

    // 版本 4 → 5（V1.3.8）：schedules 新增 task_id 列，支撑「日程⇄待办」双向状态同步。
    // 仅一次性日程会写该列；重复日程保持 NULL。
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE schedules ADD COLUMN task_id INTEGER")
        }
    }

    // 版本 5 → 6（V1.4.2）：goals 新增 image_path 列，支撑「目标自定义图片」。
    // 既有可能已存在的目标，封面为空，默认 NULL 即可，无需回填。
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE goals ADD COLUMN image_path TEXT")
        }
    }

    // 版本 6 → 7（V1.4.4）：tasks 新增 schedule_id 列 + 索引。
    // 待办侧记录来源日程，配合 due_date（=实例开始时间）定位「重复序列某一天的待办」，
    // 让重复日程也能按天同步到待办并双向同步完成状态。纯加列，既有数据不受影响。
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN schedule_id INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_tasks_schedule_id ON tasks (schedule_id)")
        }
    }

    // 版本 7 → 8（V1.5.6）：files 新增 related_type / related_id 两列，
    // 支撑「文件关联到项目/日志」的数据联动。纯加列、可空，既有文件记录不受影响。
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE files ADD COLUMN related_type TEXT")
            db.execSQL("ALTER TABLE files ADD COLUMN related_id INTEGER")
        }
    }

    // 版本 8 → 9（V1.6.0）：tasks 新增 reminder_minutes 列，
    // 支撑「待办闹钟提醒可设置提前量」。纯加列、可空；旧任务为 null，
    // 由 ReminderPlanner 按 0（截止当天 09:00）兜底，既有行为不变。
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN reminder_minutes INTEGER")
        }
    }

    // 版本 9 → 10（V1.6.5）：work_logs 补建 log_date 唯一索引，
    // 修复一处**迁移与实体长期不一致**的隐患：
    // MIGRATION_3_4 建表时创建了 `index_work_logs_log_date`（实体注释也写明「每日期望一条」），
    // 但 WorkLogEntity 一直没声明 @Index —— Room 在迁移结束后的 schema 校验里会判定
    // 「多出一个索引」并抛 IllegalStateException，即 **从 v2/v3 直接升级上来的老用户会启动崩溃**。
    // 从 v4 起升级的用户库内已有该索引，本条迁移是幂等的 no-op；
    // 同时把实体补上索引与 content 默认值声明，使「新建」与「迁移」产出的表结构完全一致。
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_work_logs_log_date ON work_logs (log_date)")
        }
    }

    /** 全部迁移按版本号升序注册，新增迁移追加到末尾 */
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
        MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10
    )
}
