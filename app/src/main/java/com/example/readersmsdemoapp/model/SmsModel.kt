package com.example.readersmsdemoapp.model

data class SMSModel(
    val body: String,
    val sender: String,
    val timestamp: Long
)