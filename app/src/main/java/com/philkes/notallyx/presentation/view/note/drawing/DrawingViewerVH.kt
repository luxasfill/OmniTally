package com.philkes.notallyx.presentation.view.note.drawing

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.signature.ObjectKey
import com.philkes.notallyx.databinding.RecyclerDrawingViewerBinding
import java.io.File

class DrawingViewerVH(private val binding: RecyclerDrawingViewerBinding) :
    RecyclerView.ViewHolder(binding.root) {

    fun bind(file: File?) {
        binding.Message.visibility = View.GONE
        Glide.with(binding.ImageView).clear(binding.ImageView)
        if (file?.exists() != true) {
            binding.Message.visibility = View.VISIBLE
            return
        }

        Glide.with(binding.ImageView)
            .load(file)
            .signature(ObjectKey(file.lastModified()))
            .fitCenter()
            .transition(DrawableTransitionOptions.withCrossFade())
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .listener(
                object : RequestListener<android.graphics.drawable.Drawable> {
                    override fun onLoadFailed(
                        e: GlideException?,
                        model: Any?,
                        target: Target<android.graphics.drawable.Drawable>?,
                        isFirstResource: Boolean,
                    ): Boolean {
                        binding.Message.visibility = View.VISIBLE
                        return false
                    }

                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable?,
                        model: Any?,
                        target: Target<android.graphics.drawable.Drawable>?,
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
