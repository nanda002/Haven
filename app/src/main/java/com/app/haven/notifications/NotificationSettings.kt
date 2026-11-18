//*AI assisstance was used in coding the notificaition settings


package com.app.haven.notifications

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit

//true when Haven is allowed to show notifications
fun notificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled()

//opens Haven's notification page in the phone's Settings (used once the system dialog can't be shown anymore)
fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

//small flags kept on this phone (not per user): whether Home already asked, and whether the system
//dialog was ever shown (needed to tell "never asked" from "blocked" on Android 13+)
object NotificationPrefs {
    private const val FILE = "notification_prefs"
    private const val ASKED_ON_HOME = "asked_on_home"
    private const val REQUESTED_BEFORE = "requested_before"

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun askedOnHome(context: Context) = prefs(context).getBoolean(ASKED_ON_HOME, false)
    fun markAskedOnHome(context: Context) = prefs(context).edit { putBoolean(ASKED_ON_HOME, true) }

    fun requestedBefore(context: Context) = prefs(context).getBoolean(REQUESTED_BEFORE, false)
    fun markRequested(context: Context) = prefs(context).edit { putBoolean(REQUESTED_BEFORE, true) }
}
