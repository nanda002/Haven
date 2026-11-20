package com.app.haven.notifications

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.app.haven.MainActivity
import com.app.haven.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

const val EXTRA_NOTIFICATION_TYPE = "type"
const val TYPE_EXPENSE = "expense"

//Receives push messages from FCM
//Android starts it when a push arrives or the phone's push token changes, even if the app isn't open
class HavenMessagingService : FirebaseMessagingService() {

    //saved under the signed in user so the Cloud Function knows where to send their notifications
    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
            .addOnFailureListener { Log.e(TAG, "saving the push token failed", it) }
    }

    //a push arrived while the app is open
    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        showNotification(title, body, message.data[EXTRA_NOTIFICATION_TYPE])
    }

    private fun showNotification(title: String, body: String, type: String?) {
        val manager = NotificationManagerCompat.from(this)
        //user turned notifications off
        if (!manager.areNotificationsEnabled()) return

        //tapping the notification opens the app
        //intent is a way to communicate between android objects
        val intent = Intent(this, MainActivity::class.java)
            //these flags are for checking if main activity is present in the back stack
            //to prevent making a new instance
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            //the type of notification so that it can be referred to in the main activity
            .putExtra(EXTRA_NOTIFICATION_TYPE, type)
        //Pending intent stores the action to be executed
        val openApp = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, getString(R.string.channel_expenses_id))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))   //long text isn't cut off when expanded
            .setContentIntent(openApp)
            .setAutoCancel(true)                                         //goes away once tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)               //banner on Android 7 (no channels there)
            .build()

        try {
            //different id each time, so a second expense doesn't replace the first notification
            manager.notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            //permission was taken away between the check above and now
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private companion object {
        const val TAG = "HavenMessaging"
    }
}
