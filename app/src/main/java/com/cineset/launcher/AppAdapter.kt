package com.cineset.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class AppAdapter(
    private val itemWidthPx: Int? = null,
    private val onClick: (AppEntry, View) -> Unit,
    private val onLongClick: (AppEntry, View) -> Unit,
) : ListAdapter<AppEntry, AppAdapter.Holder>(Diff) {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.icon)
        val label: TextView = view.findViewById(R.id.label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        itemWidthPx?.let { view.layoutParams.width = it }
        return Holder(view)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val app = getItem(position)
        holder.icon.setImageDrawable(app.icon)
        holder.label.text = app.label
        holder.itemView.contentDescription = app.label
        holder.itemView.setOnClickListener { onClick(app, it) }
        holder.itemView.setOnLongClickListener { onLongClick(app, it); true }
    }

    private object Diff : DiffUtil.ItemCallback<AppEntry>() {
        override fun areItemsTheSame(a: AppEntry, b: AppEntry) = a.key == b.key
        override fun areContentsTheSame(a: AppEntry, b: AppEntry) = a.label == b.label
    }
}
