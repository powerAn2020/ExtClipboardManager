package com.hhvvg.ecm.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.hhvvg.ecm.databinding.ItemClipboardReadLogBinding
import com.hhvvg.ecm.model.ClipboardReadInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClipboardReadLogAdapter : RecyclerView.Adapter<ClipboardReadLogAdapter.ViewHolder>() {
    private val items = mutableListOf<ClipboardReadInfo>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun updateData(newItems: List<ClipboardReadInfo>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemClipboardReadLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvPackageName.text = item.packageName
        holder.binding.tvTimestamp.text = dateFormat.format(Date(item.timestamp))
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemClipboardReadLogBinding) : RecyclerView.ViewHolder(binding.root)
}
