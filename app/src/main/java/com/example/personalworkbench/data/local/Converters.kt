package com.example.personalworkbench.data.local

import androidx.room.TypeConverter
import com.example.personalworkbench.data.local.entities.FileCategory
import com.example.personalworkbench.data.local.entities.GoalMilestoneStatus
import com.example.personalworkbench.data.local.entities.GoalPeriod
import com.example.personalworkbench.data.local.entities.GoalStatus
import com.example.personalworkbench.data.local.entities.GoalType
import com.example.personalworkbench.data.local.entities.IssueSeverity
import com.example.personalworkbench.data.local.entities.IssueStatus
import com.example.personalworkbench.data.local.entities.ModuleType
import com.example.personalworkbench.data.local.entities.Priority
import com.example.personalworkbench.data.local.entities.ProjectMilestoneStatus
import com.example.personalworkbench.data.local.entities.ProjectStatus
import com.example.personalworkbench.data.local.entities.ResourceCategory
import com.example.personalworkbench.data.local.entities.TaskStatus
import org.json.JSONArray

/**
 * Room 类型转换器。
 * - 日期以 Long (Epoch Millis) 存储 (原生支持，无需转换)
 * - 所有枚举以 name 字符串存储
 * - 列表以 JSON 字符串存储 (org.json 为 Android 内置，无需额外依赖)
 */
object Converters {

    // ---------- 枚举 (逐一显式转换，Room 不内置支持泛型枚举转换) ----------
    @TypeConverter fun fromPriority(v: Priority): String = v.name
    @TypeConverter fun toPriority(v: String): Priority = Priority.valueOf(v)

    @TypeConverter fun fromTaskStatus(v: TaskStatus): String = v.name
    @TypeConverter fun toTaskStatus(v: String): TaskStatus = TaskStatus.valueOf(v)

    @TypeConverter fun fromFileCategory(v: FileCategory): String = v.name
    @TypeConverter fun toFileCategory(v: String): FileCategory = FileCategory.valueOf(v)

    @TypeConverter fun fromModuleType(v: ModuleType): String = v.name
    @TypeConverter fun toModuleType(v: String): ModuleType = ModuleType.valueOf(v)

    @TypeConverter fun fromGoalType(v: GoalType): String = v.name
    @TypeConverter fun toGoalType(v: String): GoalType = GoalType.valueOf(v)

    @TypeConverter fun fromGoalPeriod(v: GoalPeriod): String = v.name
    @TypeConverter fun toGoalPeriod(v: String): GoalPeriod = GoalPeriod.valueOf(v)

    @TypeConverter fun fromGoalStatus(v: GoalStatus): String = v.name
    @TypeConverter fun toGoalStatus(v: String): GoalStatus = GoalStatus.valueOf(v)

    @TypeConverter fun fromGoalMilestoneStatus(v: GoalMilestoneStatus): String = v.name
    @TypeConverter fun toGoalMilestoneStatus(v: String): GoalMilestoneStatus = GoalMilestoneStatus.valueOf(v)

    @TypeConverter fun fromProjectStatus(v: ProjectStatus): String = v.name
    @TypeConverter fun toProjectStatus(v: String): ProjectStatus = ProjectStatus.valueOf(v)

    @TypeConverter fun fromProjectMilestoneStatus(v: ProjectMilestoneStatus): String = v.name
    @TypeConverter fun toProjectMilestoneStatus(v: String): ProjectMilestoneStatus = ProjectMilestoneStatus.valueOf(v)

    @TypeConverter fun fromResourceCategory(v: ResourceCategory): String = v.name
    @TypeConverter fun toResourceCategory(v: String): ResourceCategory = ResourceCategory.valueOf(v)

    @TypeConverter fun fromIssueSeverity(v: IssueSeverity): String = v.name
    @TypeConverter fun toIssueSeverity(v: String): IssueSeverity = IssueSeverity.valueOf(v)

    @TypeConverter fun fromIssueStatus(v: IssueStatus): String = v.name
    @TypeConverter fun toIssueStatus(v: String): IssueStatus = IssueStatus.valueOf(v)

    // ---------- String 列表 <-> JSON ----------
    @TypeConverter
    fun fromStringList(list: List<String>?): String? = list?.let { JSONArray(it).toString() }

    @TypeConverter
    fun toStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { arr.getString(it) }
    }
}
