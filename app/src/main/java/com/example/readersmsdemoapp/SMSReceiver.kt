package com.example.readersmsdemoapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.readersmsdemoapp.activity.MainActivity

class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("SMSReceiver", "SMSReceiver triggered. Intent action: ${intent?.action}")
        if (intent?.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Log.d("SMSReceiver", "SMS_RECEIVED_ACTION received.")
            try {
                for (smsMessage in Telephony.Sms.Intents.getMessagesFromIntent(intent)) {
                    val sender = smsMessage.displayOriginatingAddress
                    val messageBody = smsMessage.messageBody
                    Log.d("SMSReceiver", "Sender: $sender, Message: $messageBody")
                    showNotification(context!!, sender, messageBody)

                    val refreshIntent = Intent("com.example.readersmsdemoapp.NEW_SMS_RECEIVED")
                    context?.sendBroadcast(refreshIntent)
                    Log.d("SMSReceiver", "Refresh intent sent.")
                }
            } catch (e: Exception) {
                Log.e("SMSReceiver", "Error processing SMS: ${e.message}", e)
            }
        }
    }

    private fun showNotification(context: Context, sender: String, message: String) {
        val channelId = "sms_notifications"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "SMS Notifications", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_background)
            .setContentTitle("New SMS from $sender")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}