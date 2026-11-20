package com.app.haven.domain.chore

import java.io.Serializable

// A half-filled add-chore form, as the app works with it.
// Kept exactly as chosen or typed. Serializable so the open form can also survive rotation.
data class ChoreDraft(
    val title: String,
    val categoryKey: String?,
    val notes: String,
    val assigneeUid: String?,
    val dueOffset: Int?,      // 0 = Today, 1 = Tomorrow (follows the clock), null = use fixedDueDay
    val fixedDueDay: Long,
    val repeatDays: Int,
    val rotate: Boolean,
    val endDay: Long,         // 0 = no end date
    val scope: String = ChoreScope.HOUSEHOLD,
    val autoAssign: Boolean = true   // household chores only: Automatic (true) or Manual (false)
) : Serializable {

    // nothing typed yet, so there's nothing worth keeping
    fun isBlank() = title.isBlank() && notes.isBlank()

    companion object {
        // what an empty add form looks like
        fun newChore(myUid: String, today: Long) = ChoreDraft(
            title = "", categoryKey = null, notes = "", assigneeUid = null,
            dueOffset = 0, fixedDueDay = today, repeatDays = 0, rotate = true, endDay = 0L
        )

        // what the edit form starts from: the saved chore
        fun fromChore(chore: Chore) = ChoreDraft(
            title = chore.title, categoryKey = chore.category, notes = chore.notes,
            assigneeUid = chore.assigneeUid, dueOffset = null, fixedDueDay = chore.dueEpochDay,
            repeatDays = chore.repeatDays, rotate = chore.rotate, endDay = chore.endEpochDay,
            scope = chore.scope, autoAssign = false
        )
    }
}