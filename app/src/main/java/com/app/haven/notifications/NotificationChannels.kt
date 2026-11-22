package com.app.haven.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.app.haven.R

fun createNotificationChannels(context: Context) {
    //android 8 introduced notification channels
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    //creating a notificiation channel for expenses
    val expenses = NotificationChannel(
        context.getString(R.string.channel_expenses_id),
        context.getString(R.string.channel_expenses_name),
        //high priority to display the notification banner
        NotificationManager.IMPORTANCE_HIGH
    ).apply { description = context.getString(R.string.channel_expenses_description) }
    //registers the channel with notif manager
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(expenses)
}
