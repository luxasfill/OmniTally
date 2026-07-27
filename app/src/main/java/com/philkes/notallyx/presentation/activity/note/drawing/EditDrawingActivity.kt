package com.philkes.notallyx.presentation.activity.note.drawing

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.ActivityEditDrawingBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.utils.getCurrentDrawingsDirectory
import com.philkes.notallyx.utils.showColorSelectDialog
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditDrawingActivity : LockedActivity<ActivityEditDrawingBinding>() {

    companion object {
        const val EXTRA_DRAWING = "notallyx.intent.extra.DRAWING"
        const val EXTRA_EXISTING_DRAWING = "notallyx.intent.extra.EXISTING_DRAWING"
    }

    private var existingDrawing: FileAttachment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        existingDrawing =
            IntentCompat.getParcelableExtra(
                intent,
                EXTRA_EXISTING_DRAWING,
                FileAttachment::class.java,
            )

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    maybeFinish()
                }
            },
        )

        setupToolbar()
        setupBottomBar()
        configureEdgeToEdgeInsets()

        val drawing = existingDrawing
        if (drawing != null) {
            loadDrawing(drawing)
        }
    }

    private fun setupToolbar() {
        binding.Toolbar.setNavigationOnClickListener { maybeFinish() }
        binding.save.setOnClickListener { saveDrawing() }
    }

    private fun setupBottomBar() {
        val drawingView = binding.drawingView

        setIconTint(binding.undo, Color.WHITE)
        setIconTint(binding.redo, Color.WHITE)
        setIconTint(binding.pen, Color.WHITE)
        setIconTint(binding.eraser, Color.WHITE)
        setIconTint(binding.move, Color.WHITE)

        binding.undo.setOnClickListener { drawingView.undo() }
        binding.redo.setOnClickListener { drawingView.redo() }

        drawingView.onStrokeStateChanged = { updateUndoRedo() }

        binding.pen.setOnClickListener {
            if (!drawingView.eraserMode) {
                toggleStrokeWidthBar()
            } else {
                drawingView.eraserMode = false
                updatePenEraser()
            }
        }
        binding.eraser.setOnClickListener {
            if (drawingView.eraserMode) {
                toggleStrokeWidthBar()
            } else {
                drawingView.eraserMode = true
                updatePenEraser()
            }
        }

        updateUndoRedo()
        updatePenEraser()
        updateColorButton(drawingView.paintColor)
        updateMoveButton()

        binding.strokeWidth.setOnSeekBarChangeListener(
            object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(
                    seekBar: android.widget.SeekBar?,
                    progress: Int,
                    fromUser: Boolean,
                ) {
                    drawingView.paintWidth = (progress + 1).toFloat()
                    binding.strokeWidthLabel.text = (progress + 1).toString()
                }

                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
            }
        )

        binding.drawingView.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                hideStrokeWidthBar()
            }
            false
        }

        binding.colorPicker.setOnClickListener {
            hideStrokeWidthBar()
            val colors =
                mutableSetOf(
                    "#FF000000",
                    "#FFFFFFFF",
                    "#FFD32F2F",
                    "#FFE91E63",
                    "#FF9C27B0",
                    "#FF673AB7",
                    "#FF3F51B5",
                    "#FF2196F3",
                    "#FF03A9F4",
                    "#FF00BCD4",
                    "#FF009688",
                    "#FF4CAF50",
                    "#FF8BC34A",
                    "#FFCDDC39",
                    "#FFFFEB3B",
                    "#FFFFC107",
                    "#FFFF9800",
                    "#FFFF5722",
                    "#FF795548",
                    "#FF9E9E9E",
                    "#FF607D8B",
                )
            showColorSelectDialog(
                colors,
                String.format("#%08X", drawingView.paintColor),
                false,
                { selectedColor, _ ->
                    val colorInt = Color.parseColor(selectedColor)
                    drawingView.paintColor = colorInt
                    updateColorButton(colorInt)
                },
                { _, _ -> },
            )
        }

        binding.move.setOnClickListener {
            hideStrokeWidthBar()
            drawingView.isMoveMode = !drawingView.isMoveMode
            updateMoveButton()
        }
    }

    private fun toggleStrokeWidthBar() {
        if (binding.strokeWidthBar.visibility == android.view.View.VISIBLE) {
            hideStrokeWidthBar()
        } else {
            showStrokeWidthBar()
        }
    }

    private fun showStrokeWidthBar() {
        binding.strokeWidth.progress = (binding.drawingView.paintWidth - 1f).toInt().coerceIn(0, 50)
        binding.strokeWidthLabel.text = binding.drawingView.paintWidth.toInt().toString()
        binding.strokeWidthBar.visibility = android.view.View.VISIBLE
    }

    private fun hideStrokeWidthBar() {
        binding.strokeWidthBar.visibility = android.view.View.GONE
    }

    private fun setIconTint(btn: android.widget.ImageButton, color: Int) {
        btn.drawable?.mutate()?.setTint(color)
    }

    private fun updateUndoRedo() {
        binding.undo.alpha = if (binding.drawingView.canUndo()) 1f else 0.3f
        binding.redo.alpha = if (binding.drawingView.canRedo()) 1f else 0.3f
    }

    private fun updatePenEraser() {
        if (binding.drawingView.eraserMode) {
            setIconTint(binding.pen, Color.GRAY)
            setIconTint(binding.eraser, Color.WHITE)
        } else {
            setIconTint(binding.pen, Color.WHITE)
            setIconTint(binding.eraser, Color.GRAY)
        }
    }

    private fun configureEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin =
                    systemBarsInsets.top + 28.dp
                requestLayout()
            }
            binding.drawingView.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).bottomMargin =
                    systemBarsInsets.bottom + imeInsets.bottom
                requestLayout()
            }
            insets
        }
    }

    private fun updateMoveButton() {
        setIconTint(binding.move, if (binding.drawingView.isMoveMode) Color.WHITE else Color.GRAY)
    }

    private fun updateColorButton(color: Int) {
        binding.colorPicker.setColorFilter(color)
    }

    private fun loadDrawing(drawing: FileAttachment) {
        val dir = getCurrentDrawingsDirectory()
        val file = File(dir, drawing.localName)
        if (file.exists()) {
            lifecycleScope.launch {
                val bitmap =
                    withContext(Dispatchers.IO) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.absolutePath, options)
                        val sampleSize = calculateInSampleSize(options, 1920, 1080)
                        options.inJustDecodeBounds = false
                        options.inSampleSize = sampleSize
                        BitmapFactory.decodeFile(file.absolutePath, options)
                    }
                if (bitmap != null) {
                    binding.drawingView.loadBitmap(bitmap)
                }
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
    ): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    private fun maybeFinish() {
        if (binding.drawingView.canUndo()) {
            showSaveChangesDialog()
        } else {
            finish()
        }
    }

    private fun showSaveChangesDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.save_changes_title)
            .setMessage(R.string.save_changes_message)
            .setPositiveButton(R.string.save) { _, _ -> saveDrawing() }
            .setNeutralButton(R.string.discard) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun saveDrawing() {
        val bitmap = binding.drawingView.saveToBitmap()
        val dir = getCurrentDrawingsDirectory()
        dir.mkdirs()
        val existing = existingDrawing
        val fileName: String
        val drawing: FileAttachment
        if (existing != null) {
            fileName = existing.localName
            drawing = existing
        } else {
            fileName = "${UUID.randomUUID()}.png"
            drawing = FileAttachment(fileName, fileName, "image/png")
        }
        val file = File(dir, fileName)
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, out) }
        file.setLastModified(System.currentTimeMillis())
        val intent = Intent()
        intent.putExtra(EXTRA_DRAWING, drawing)
        existing?.let { intent.putExtra(EXTRA_EXISTING_DRAWING, it) }
        setResult(RESULT_OK, intent)
        finish()
    }
}
