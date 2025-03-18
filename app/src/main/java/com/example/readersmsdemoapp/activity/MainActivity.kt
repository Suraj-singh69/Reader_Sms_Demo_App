package com.example.readersmsdemoapp.activity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.readersmsdemoapp.R
import com.example.readersmsdemoapp.activity.adapter.SMSAdapter
import com.example.readersmsdemoapp.model.SMSModel

class MainActivity : AppCompatActivity() {

    private lateinit var smsAdapter: SMSAdapter
    private lateinit var recyclerView: RecyclerView
    private val smsList = mutableListOf<SMSModel>()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var senderIdInput: EditText
    private lateinit var filterButton: Button
    private var senderFilter: String? = null
    private val smsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("MainActivity", "smsUpdateReceiver received: ${intent?.action}")
            if (intent?.action == "com.example.readersmsdemoapp.NEW_SMS_RECEIVED") {
                loadSMS()
            }
        }
    }

    private val testReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("TestReceiver", "Received broadcast: ${intent?.action}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerView)
        swipeRefreshLayout = findViewById(R.id.swiperefresh)
        senderIdInput = findViewById(R.id.senderIdInput)
        filterButton = findViewById(R.id.filterButton)

        recyclerView.layoutManager = LinearLayoutManager(this)
        smsAdapter = SMSAdapter(smsList)
        recyclerView.adapter = smsAdapter

        checkPermissions()

        val intentFilter = IntentFilter(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
        registerReceiver(smsReceiver, intentFilter)

        val updateIntentFilter = IntentFilter("com.example.readersmsdemoapp.NEW_SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsUpdateReceiver, updateIntentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsUpdateReceiver, updateIntentFilter)
        }

        val testIntentFilter = IntentFilter()
        testIntentFilter.addAction(Intent.ACTION_BOOT_COMPLETED)
        testIntentFilter.addAction(Intent.ACTION_POWER_CONNECTED)
        registerReceiver(testReceiver, testIntentFilter)
        filterButton.setOnClickListener {
            senderFilter = senderIdInput.text.toString().trim()
            loadSMS()
        }
    }

    override fun onResume() {
        super.onResume()
        swipeRefreshLayout.setOnRefreshListener {
            loadSMS()
        }
//        loadSMS()
    }

    private fun checkPermissions() {
        Log.d("MainActivity", "Checking permissions...")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "READ_SMS permission not granted.")
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
            Log.d("MainActivity", "RECEIVE_SMS permission not granted.")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d("MainActivity", "Requesting permissions.")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS),
                1
            )
        } else {
            Log.d("MainActivity", "Permissions already granted.")
            loadSMS()
        }
    }

    private fun loadSMS() {
        Log.d("MainActivity", "loadSMS called")
        swipeRefreshLayout.isRefreshing = true
        smsList.clear()
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, Telephony.Sms.DEFAULT_SORT_ORDER)

        cursor?.use {
            val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
            val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val indexDate = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext()) {
                val body = it.getString(indexBody)
                val sender = it.getString(indexAddress)
                val timestamp = it.getLong(indexDate)

                if (senderFilter.isNullOrEmpty() || isSenderMatchingFilter(sender, senderFilter!!)) {
                    smsList.add(SMSModel(body, sender, timestamp))
                    Log.d("MainActivity", "SMS: Sender=$sender, Body=$body, Time=$timestamp")
                }
            }
        }
        smsAdapter.notifyDataSetChanged()
        swipeRefreshLayout.isRefreshing = false
    }

    private fun isSenderMatchingFilter(sender: String, filter: String): Boolean {
        val normalizedSender = PhoneNumberUtils.normalizeNumber(sender)
        val normalizedFilter = PhoneNumberUtils.normalizeNumber(filter)

        if (normalizedSender != null && normalizedFilter != null && normalizedSender.contains(normalizedFilter, ignoreCase = true)) {
            Log.d("SenderFilter", "Number Match")
            return true
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            val contactUri = Uri.withAppendedPath(
                android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(filter)
            )
            val cursor = contentResolver.query(
                contactUri,
                arrayOf(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val contactName = it.getString(it.getColumnIndex(android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME))
                    if (sender.equals(contactName, ignoreCase = true)) {
                        Log.d("SenderFilter", "Contact Match")
                        return true
                    }
                }
            }
        }
        Log.d("SenderFilter", "No Match")
        return false
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("MainActivity", "onRequestPermissionsResult called.")
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("MainActivity", "Permissions granted by user.")
                loadSMS()
            } else {
                Log.d("MainActivity", "Permissions denied by user.")
                Toast.makeText(this, "SMS permissions are required.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val smsReceiver = object : BroadcastReceiver() {
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

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsReceiver)
        unregisterReceiver(smsUpdateReceiver)
        unregisterReceiver(testReceiver)
    }
}