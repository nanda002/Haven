package com.app.haven.domain.expense

enum class ExpenseRepeat(val key: String, val label: String) {
    ONCE("once", "Once"),
    WEEKLY("weekly", "Weekly"),
    EVERY_2_WEEKS("every_2_weeks", "Every 2 weeks"),
    MONTHLY("monthly", "Monthly");

    companion object {
        fun fromKey(key: String?): ExpenseRepeat = entries.firstOrNull { it.key == key } ?: ONCE
    }
}