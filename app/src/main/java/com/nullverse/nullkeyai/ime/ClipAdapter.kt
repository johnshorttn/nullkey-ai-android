package com.nullverse.nullkeyai.ime

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.nullverse.nullkeyai.R
import com.nullverse.nullkeyai.db.Clip

/** Renders captured clips inside the IME's clip vault and reports taps. */
class ClipAdapter(
    private val onClick: (Clip) -> Unit
) : RecyclerView.Adapter<ClipAdapter.ClipViewHolder>() {

    private val items = mutableListOf<Clip>()

    fun clipAt(position: Int): Clip? = items.getOrNull(position)

    fun submit(newItems: List<Clip>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(o: Int, n: Int) = items[o].id == newItems[n].id
            override fun areContentsTheSame(o: Int, n: Int) = items[o] == newItems[n]
        })
        items.clear()
        items.addAll(newItems)
        diff.dispatchUpdatesTo(this)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ClipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_clip, parent, false)
        return ClipViewHolder(view)
    }

    override fun onBindViewHolder(holder: ClipViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ClipViewHolder(view: android.view.View) : RecyclerView.ViewHolder(view) {
        private val preview: TextView = view.findViewById(R.id.clip_preview)
        private val meta: TextView = view.findViewById(R.id.clip_meta)

        fun bind(clip: Clip) {
            preview.text = when {
                clip.protected -> "Protected clip"
                clip.contentType == "IMAGE" -> clip.notes.ifBlank { "Image" }
                clip.contentType == "FILE" -> clip.notes.ifBlank { clip.mimeType ?: "File" }
                else -> clip.content
            }
            val kind = clip.contentType
            val pin = if (clip.pinned) " • PIN" else ""
            val lock = if (clip.protected) " • PROTECTED" else ""
            val source = clip.sourceAppLabel?.let { " • $it" }
                ?: clip.sourcePackage?.let { " • $it" }
                ?: ""
            val note = if (clip.notes.isNotBlank() && clip.contentType == "TEXT") " • NOTE" else ""
            meta.text = "$kind$pin$lock$note$source"
            itemView.contentDescription = itemView.context.getString(
                R.string.paste_clip,
                preview.text,
            )
            itemView.setOnClickListener { onClick(clip) }
        }
    }
}
