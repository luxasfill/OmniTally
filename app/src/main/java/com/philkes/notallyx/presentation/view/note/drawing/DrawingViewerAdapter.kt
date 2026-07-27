package com.philkes.notallyx.presentation.view.note.drawing

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.RecyclerDrawingViewerBinding
import java.io.File

class DrawingViewerAdapter(private val drawingsRoot: File?, val items: ArrayList<FileAttachment>) :
    RecyclerView.Adapter<DrawingViewerVH>() {

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: DrawingViewerVH, position: Int) {
        val drawing = items[position]
        val file = if (drawingsRoot != null) File(drawingsRoot, drawing.localName) else null
        holder.bind(file)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawingViewerVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerDrawingViewerBinding.inflate(inflater, parent, false)
        return DrawingViewerVH(binding)
    }
}
