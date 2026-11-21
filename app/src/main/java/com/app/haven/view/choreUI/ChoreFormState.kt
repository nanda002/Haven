package com.app.haven.view.choreUI

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import com.app.haven.domain.chore.ChoreDraft

// Editable, observable copy of a draft that the form reads and writes.
// Saved with rememberSaveable so an open form also survives rotation.
class ChoreFormState(draft: ChoreDraft) {
    var title by mutableStateOf(draft.title)
    var categoryKey by mutableStateOf(draft.categoryKey)
    var notes by mutableStateOf(draft.notes)
    var assigneeUid by mutableStateOf(draft.assigneeUid)
    var dueOffset by mutableStateOf(draft.dueOffset)
    var fixedDueDay by mutableStateOf(draft.fixedDueDay)
    var repeatDays by mutableStateOf(draft.repeatDays)
    var rotate by mutableStateOf(draft.rotate)
    var endDay by mutableStateOf(draft.endDay)
    var scope by mutableStateOf(draft.scope)
    var autoAssign by mutableStateOf(draft.autoAssign)

    fun toDraft() = ChoreDraft(
        title, categoryKey, notes, assigneeUid, dueOffset, fixedDueDay, repeatDays, rotate, endDay,
        scope, autoAssign
    )

    // replaces every field at once (used by Clear)
    fun load(d: ChoreDraft) {
        title = d.title
        categoryKey = d.categoryKey
        notes = d.notes
        assigneeUid = d.assigneeUid
        dueOffset = d.dueOffset
        fixedDueDay = d.fixedDueDay
        repeatDays = d.repeatDays
        rotate = d.rotate
        endDay = d.endDay
        scope = d.scope
        autoAssign = d.autoAssign
    }

    companion object {
        val Saver = Saver<ChoreFormState, ChoreDraft>(
            save = { it.toDraft() },
            restore = { ChoreFormState(it) }
        )
    }
}