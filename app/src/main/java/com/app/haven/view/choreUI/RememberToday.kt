package com.app.haven.view.choreUI

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.app.haven.domain.chore.millisUntilNextLocalMidnight
import com.app.haven.domain.chore.todayEpochDay
import kotlinx.coroutines.delay

// Today as a local epoch day that stays correct while the screen is open:
// refreshes when the app returns to the foreground (also covers time zone changes)
// and ticks over at local midnight.
@Composable
fun rememberTodayEpochDay(): Long {
    var today by remember { mutableLongStateOf(todayEpochDay()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) today = todayEpochDay()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // loop (not a single delay): if the timer fires early, e.g. on a DST day, it re-checks
    LaunchedEffect(today) {
        while (true) {
            delay(millisUntilNextLocalMidnight() + 500)
            today = todayEpochDay()
        }
    }
    return today
}