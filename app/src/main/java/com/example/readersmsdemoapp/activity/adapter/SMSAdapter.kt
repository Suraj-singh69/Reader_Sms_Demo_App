package com.example.readersmsdemoapp.activity.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.readersmsdemoapp.R
import com.example.readersmsdemoapp.model.SMSModel
import java.text.SimpleDateFormat
import java.util.*

class SMSAdapter(private val smsList: List<SMSModel>) :
    RecyclerView.Adapter<SMSAdapter.SMSViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SMSViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_sms, parent, false)
        return SMSViewHolder(view)
    }

    override fun onBindViewHolder(holder: SMSViewHolder, position: Int) {
        val sms = smsList[position]
        holder.senderTextView.text = sms.sender
        holder.bodyTextView.text = sms.body
        holder.dateTextView.text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(sms.timestamp))
    }

    override fun getItemCount(): Int {
        return smsList.size
    }

    class SMSViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val senderTextView: TextView = itemView.findViewById(R.id.senderTextView)
        val bodyTextView: TextView = itemView.findViewById(R.id.bodyTextView)
        val dateTextView: TextView = itemView.findViewById(R.id.dateTextView)
    }
}
