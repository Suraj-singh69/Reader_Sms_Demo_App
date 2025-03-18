package com.example.readersmsdemoapp.activity

import android.Manifest
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
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.readersmsdemoapp.R
import com.example.readersmsdemoapp.activity.adapter.SMSAdapter
import com.example.readersmsdemoapp.model.SMSModel
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var smsAdapter: SMSAdapter
    private lateinit var recyclerView: RecyclerView
    private val smsList = mutableListOf<SMSModel>()
    private lateinit var swipeRefreshLayout: SwipeRefreshLayout
    private lateinit var senderIdInput: EditText
    private lateinit var filterButton: Button
    private var senderFilter: String? = null
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val smsUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "NEW_SMS_RECEIVED") {
                loadSMS()
            }
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

        val updateIntentFilter = IntentFilter("NEW_SMS_RECEIVED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(smsUpdateReceiver, updateIntentFilter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(smsUpdateReceiver, updateIntentFilter)
        }
        filterButton.setOnClickListener {
            senderFilter = senderIdInput.text.toString().trim()
            loadSMS()
        }

        swipeRefreshLayout.setOnRefreshListener {
            loadSMS()
        }
    }

    override fun onResume() {
        super.onResume()
        senderIdInput.text.clear()
        senderFilter = null
        loadSMS()
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_CONTACTS),
                1
            )
        } else {
            checkNotificationPermission()
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    2
                )
            } else {
                loadSMS()
            }
        } else {
            loadSMS()
        }
    }

    private fun loadSMS() {
        Log.d("MainActivity", "Fetching SMS...")

        swipeRefreshLayout.isRefreshing = true
        senderIdInput.text.clear() // Clear EditText here

        coroutineScope.launch(Dispatchers.IO) {
            val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
            val cursor: Cursor? = contentResolver.query(
                uri,
                arrayOf(Telephony.Sms.BODY, Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
                null,
                null,
                "${Telephony.Sms.DEFAULT_SORT_ORDER}"
            )

            val newSmsList = mutableListOf<SMSModel>()

            cursor?.use {
                val indexBody = it.getColumnIndex(Telephony.Sms.BODY)
                val indexAddress = it.getColumnIndex(Telephony.Sms.ADDRESS)
                val indexDate = it.getColumnIndex(Telephony.Sms.DATE)

                while (it.moveToNext()) {
                    val body = it.getString(indexBody)
                    val sender = it.getString(indexAddress)
                    val timestamp = it.getLong(indexDate)

                    if (senderFilter.isNullOrEmpty() || isSenderMatchingFilter(sender, senderFilter!!)) {
                        newSmsList.add(SMSModel(body, sender, timestamp))
                    }
                }
            }

            withContext(Dispatchers.Main) {
                smsList.clear()
                smsList.addAll(newSmsList)
                smsAdapter.notifyDataSetChanged()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }

    private fun isSenderMatchingFilter(sender: String, filter: String): Boolean {
        val normalizedSender = PhoneNumberUtils.normalizeNumber(sender)
        val normalizedFilter = PhoneNumberUtils.normalizeNumber(filter)
        return normalizedSender.contains(normalizedFilter, ignoreCase = true)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            checkNotificationPermission()
        } else if (requestCode == 2 && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            loadSMS()
        } else {
            Toast.makeText(this, "Permissions are required.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(smsUpdateReceiver)
        coroutineScope.cancel()
    }
}