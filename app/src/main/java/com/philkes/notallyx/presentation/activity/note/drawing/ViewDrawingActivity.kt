package com.philkes.notallyx.presentation.activity.note.drawing

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.Converters
import com.philkes.notallyx.data.model.FileAttachment
import com.philkes.notallyx.databinding.ActivityViewDrawingBinding
import com.philkes.notallyx.presentation.activity.LockedActivity
import com.philkes.notallyx.presentation.activity.note.EditActivity.Companion.EXTRA_SELECTED_BASE_NOTE
import com.philkes.notallyx.presentation.add
import com.philkes.notallyx.presentation.setCancelButton
import com.philkes.notallyx.presentation.view.note.drawing.DrawingViewerAdapter
import com.philkes.notallyx.utils.SUBFOLDER_DRAWINGS
import com.philkes.notallyx.utils.getCurrentDrawingsDirectory
import com.philkes.notallyx.utils.getUriForFile
import com.philkes.notallyx.utils.resolveAttachmentFile
import com.philkes.notallyx.utils.wrapWithChooser
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ViewDrawingActivity : LockedActivity<ActivityViewDrawingBinding>() {

    companion object {
        const val EXTRA_DRAWING = "notallyx.intent.extra.VIEW_DRAWING"
        const val EXTRA_POSITION = "notallyx.intent.extra.POSITION"
        const val CURRENT_DRAWING = "CURRENT_DRAWING"
        const val EXTRA_DELETED_DRAWINGS = "notallyx.intent.extra.DELETED_DRAWINGS"
    }

    private var currentDrawing: FileAttachment? = null
    private lateinit var deletedDrawings: ArrayList<FileAttachment>
    private lateinit var exportFileActivityResultLauncher: ActivityResultLauncher<Intent>
    private lateinit var editLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityViewDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        configureEdgeToEdgeInsets()

        val savedList =
            savedInstanceState?.let {
                BundleCompat.getParcelableArrayList(
                    it,
                    EXTRA_DELETED_DRAWINGS,
                    FileAttachment::class.java,
                )
            }
        deletedDrawings = savedList ?: ArrayList()

        val resultIntent = Intent()
        resultIntent.putExtra(EXTRA_DELETED_DRAWINGS, deletedDrawings)
        setResult(RESULT_OK, resultIntent)

        val savedDrawing =
            savedInstanceState?.let {
                BundleCompat.getParcelable(it, CURRENT_DRAWING, FileAttachment::class.java)
            }
        if (savedDrawing != null) {
            currentDrawing = savedDrawing
        }

        binding.MainListView.apply {
            setHasFixedSize(true)
            layoutManager =
                LinearLayoutManager(this@ViewDrawingActivity, RecyclerView.HORIZONTAL, false)
            PagerSnapHelper().attachToRecyclerView(binding.MainListView)
        }

        val initial = intent.getIntExtra(EXTRA_POSITION, 0)
        binding.MainListView.scrollToPosition(initial)

        val database = NotallyDatabase.getDatabase(application)
        val id = intent.getLongExtra(EXTRA_SELECTED_BASE_NOTE, 0)

        database.observe(this@ViewDrawingActivity) {
            lifecycleScope.launch {
                val json = withContext(Dispatchers.IO) { it.getBaseNoteDao().getDrawings(id) }
                val original = Converters.jsonToFiles(json)
                val drawings = ArrayList<FileAttachment>(original.size)
                original.filterNotTo(drawings) { drawing -> deletedDrawings.contains(drawing) }

                val drawingsRoot = application.getCurrentDrawingsDirectory()
                val adapter = DrawingViewerAdapter(drawingsRoot, drawings)
                binding.MainListView.adapter = adapter
                setupToolbar(binding, adapter)
            }
        }

        exportFileActivityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    result.data?.data?.let { uri -> writeDrawingToUri(uri) }
                }
            }

        editLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val editedDrawing =
                        result.data?.let {
                            IntentCompat.getParcelableExtra(
                                it,
                                EditDrawingActivity.EXTRA_DRAWING,
                                FileAttachment::class.java,
                            )
                        }
                    if (editedDrawing != null) {
                        val layoutManager =
                            binding.MainListView.layoutManager as? LinearLayoutManager
                        val position =
                            layoutManager?.findFirstCompletelyVisibleItemPosition()
                                ?: return@registerForActivityResult
                        val adapter =
                            binding.MainListView.adapter as? DrawingViewerAdapter
                                ?: return@registerForActivityResult
                        if (position >= 0 && position < adapter.items.size) {
                            adapter.items[position] = editedDrawing
                            adapter.notifyItemChanged(position)
                        }
                    }
                }
            }
    }

    private fun configureEdgeToEdgeInsets() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBarsInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

            binding.Toolbar.apply {
                (layoutParams as ViewGroup.MarginLayoutParams).topMargin = systemBarsInsets.top
                requestLayout()
            }
            binding.MainListView.apply {
                setPadding(
                    paddingLeft,
                    paddingTop,
                    paddingRight,
                    systemBarsInsets.bottom + imeInsets.bottom,
                )
            }
            insets
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.apply {
            putParcelable(CURRENT_DRAWING, currentDrawing)
            putParcelableArrayList(EXTRA_DELETED_DRAWINGS, deletedDrawings)
        }
    }

    private fun setupToolbar(binding: ActivityViewDrawingBinding, adapter: DrawingViewerAdapter) {
        binding.Toolbar.setNavigationOnClickListener { finish() }

        val layoutManager = binding.MainListView.layoutManager as LinearLayoutManager
        adapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {

                override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                    val position = layoutManager.findFirstVisibleItemPosition()
                    binding.Toolbar.title = "${position + 1} / ${adapter.itemCount}"
                }
            }
        )

        binding.MainListView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    val position = layoutManager.findFirstVisibleItemPosition()
                    binding.Toolbar.title = "${position + 1} / ${adapter.itemCount}"
                }
            }
        )
        binding.Toolbar.menu.apply {
            add(R.string.edit, R.drawable.edit) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    val drawing = adapter.items[position]
                    editDrawing(drawing)
                }
            }
            add(R.string.share, R.drawable.share) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    val drawing = adapter.items[position]
                    share(drawing)
                }
            }
            add(R.string.save_to_device, R.drawable.save) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    val drawing = adapter.items[position]
                    saveToDevice(drawing)
                }
            }
            add(R.string.delete, R.drawable.delete) {
                val position = layoutManager.findFirstCompletelyVisibleItemPosition()
                if (position != -1) {
                    delete(position, adapter)
                }
            }
        }
    }

    private fun share(drawing: FileAttachment) {
        val file = application.resolveAttachmentFile(SUBFOLDER_DRAWINGS, drawing.localName)
        if (file != null && file.exists()) {
            val uri = getUriForFile(file)
            val intent =
                Intent(Intent.ACTION_SEND)
                    .apply {
                        type = drawing.mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri(null, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    .wrapWithChooser(this@ViewDrawingActivity)
            startActivity(intent)
        }
    }

    private fun editDrawing(drawing: FileAttachment) {
        val intent = Intent(this, EditDrawingActivity::class.java)
        intent.putExtra(EditDrawingActivity.EXTRA_EXISTING_DRAWING, drawing)
        editLauncher.launch(intent)
    }

    private fun saveToDevice(drawing: FileAttachment) {
        val file = application.resolveAttachmentFile(SUBFOLDER_DRAWINGS, drawing.localName)
        if (file != null && file.exists()) {
            val intent =
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .apply {
                        type = drawing.mimeType
                        addCategory(Intent.CATEGORY_OPENABLE)
                        putExtra(Intent.EXTRA_TITLE, "OmniTally Drawing")
                    }
                    .wrapWithChooser(this)
            currentDrawing = drawing
            exportFileActivityResultLauncher.launch(intent)
        }
    }

    private fun writeDrawingToUri(uri: Uri) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                val cd = requireNotNull(currentDrawing) { "currentDrawing is null" }
                val file = application.resolveAttachmentFile(SUBFOLDER_DRAWINGS, cd.localName)
                if (file != null && file.exists()) {
                    val output = contentResolver.openOutputStream(uri) as FileOutputStream
                    output.channel.truncate(0)
                    val input = FileInputStream(file)
                    input.copyTo(output)
                    input.close()
                    output.close()
                }
            }
            Toast.makeText(this@ViewDrawingActivity, R.string.saved_to_device, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun delete(position: Int, adapter: DrawingViewerAdapter) {
        MaterialAlertDialogBuilder(this)
            .setMessage(R.string.delete_image_forever)
            .setCancelButton()
            .setPositiveButton(R.string.delete) { _, _ ->
                val drawing = adapter.items.removeAt(position)
                deletedDrawings.add(drawing)
                adapter.notifyItemRemoved(position)
                if (adapter.items.isEmpty()) {
                    finish()
                }
            }
            .show()
    }

    override fun onBackPressed() {
        finish()
    }
}
