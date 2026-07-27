package com.philkes.notallyx.presentation.view.note.preview

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.RecyclerPreviewDrawingBinding
import java.io.File

class PreviewDrawingAdapter(
    private val drawingsRoot: File?,
    private val onClick: (position: Int) -> Unit,
) :
    ListAdapter<FileAttachment, PreviewDrawingAdapter.DrawingVH>(
        object : DiffUtil.ItemCallback<FileAttachment>() {
            override fun areItemsTheSame(
                oldItem: FileAttachment,
                newItem: FileAttachment,
            ): Boolean {
                return oldItem.localName == newItem.localName
            }

            override fun areContentsTheSame(
                oldItem: FileAttachment,
                newItem: FileAttachment,
            ): Boolean {
                return false
            }
        }
    ) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawingVH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = RecyclerPreviewDrawingBinding.inflate(inflater, parent, false)
        return DrawingVH(binding, onClick)
    }

    override fun onBindViewHolder(holder: DrawingVH, position: Int) {
        val drawing = getItem(position)
        val file = if (drawingsRoot != null) File(drawingsRoot, drawing.localName) else null
        holder.bind(file)
    }

    class DrawingVH(
        private val binding: RecyclerPreviewDrawingBinding,
        onClick: ((position: Int) -> Unit),
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener { onClick(absoluteAdapterPosition) }
        }

        fun bind(file: File?) {
            if (file?.exists() == false) {
                binding.Message.visibility = View.VISIBLE
                return
            }
            binding.Message.visibility = View.GONE

            Glide.with(binding.ImageView)
                .load(file)
                .signature(ObjectKey(file?.lastModified() ?: System.currentTimeMillis()))
                .fitCenter()
                .transition(DrawableTransitionOptions.withCrossFade())
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .listener(
                    object : RequestListener<Drawable> {
                        override fun onLoadFailed(
                            e: GlideException?,
                            model: Any?,
                            target: Target<Drawable>?,
                            isFirstResource: Boolean,
                        ): Boolean {
                            binding.Message.visibility = View.VISIBLE
                            return false
                        }

                        override fun onResourceReady(
                            resource: Drawable?,
                            model: Any?,
                            target: Target<Drawable>?,
                            dataSource: DataSource?,
                            isFirstResource: Boolean,
                        ): Boolean {
                            return false
                        }
                    }
                )
                .into(binding.ImageView)
        }
    }
}
