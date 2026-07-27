package com.philkes.notallyx.presentation.view.note.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View

class DrawingView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null

    private val strokes = mutableListOf<Stroke>()
    private val redoStrokes = mutableListOf<Stroke>()

    private var currentPath: Path? = null
    private var lastCanvasX: Float = 0f
    private var lastCanvasY: Float = 0f
    private var pendingBitmap: Bitmap? = null
    private var loadedBitmap: Bitmap? = null
    private val currentPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

    private val previewPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = 0xFF666666.toInt()
        }

    var paintColor: Int = Color.BLACK
        set(value) {
            field = value
            currentPaint.color = value
            if (!eraserMode) previewPaint.color = value
        }

    var paintWidth: Float = 12f
        set(value) {
            field = value
            currentPaint.strokeWidth = value
            previewPaint.strokeWidth = value
        }

    var eraserMode: Boolean = false
        set(value) {
            field = value
            currentPaint.xfermode = if (value) PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
            if (!value) previewPaint.color = paintColor
        }

    var isMoveMode: Boolean = false
    var onStrokeStateChanged: (() -> Unit)? = null

    private val borderPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = 0xFF999999.toInt()
        }

    init {
        currentPaint.color = paintColor
        currentPaint.strokeWidth = paintWidth
        previewPaint.color = paintColor
        previewPaint.strokeWidth = paintWidth
    }

    private data class Stroke(val path: Path, val paint: Paint)

    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private var isTransforming = false
    private var ignoreTouchUntilUp = false
    private val tmpPoints = FloatArray(2)

    private var lastPanX: Float = 0f
    private var lastPanY: Float = 0f

    private val scaleDetector =
        ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                    isTransforming = true
                    ignoreTouchUntilUp = true
                    currentPath?.let { path ->
                        val paint = Paint(currentPaint)
                        strokes.add(Stroke(path, paint))
                        drawingCanvas?.drawPath(path, paint)
                        currentPath = null
                    }
                    return true
                }

                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    viewMatrix.postScale(
                        detector.scaleFactor,
                        detector.scaleFactor,
                        detector.focusX,
                        detector.focusY,
                    )
                    viewMatrix.invert(inverseMatrix)
                    invalidate()
                    return true
                }

                override fun onScaleEnd(detector: ScaleGestureDetector) {
                    isTransforming = false
                }
            },
        )

    private val gestureDetector =
        GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    viewMatrix.reset()
                    viewMatrix.invert(inverseMatrix)
                    invalidate()
                    return true
                }
            },
        )

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        drawingBitmap?.recycle()
        drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        drawingCanvas = Canvas(drawingBitmap!!)
        redrawAll()
        pendingBitmap?.let {
            drawingCanvas?.drawBitmap(it, 0f, 0f, null)
            pendingBitmap = null
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(viewMatrix)
        val bitmap = drawingBitmap
        if (bitmap != null) {
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), borderPaint)
        }
        if (!eraserMode) {
            currentPath?.let { path -> canvas.drawPath(path, previewPaint) }
        }
        canvas.restore()
    }

    private fun screenToCanvas(sx: Float, sy: Float): Pair<Float, Float> {
        tmpPoints[0] = sx
        tmpPoints[1] = sy
        inverseMatrix.mapPoints(tmpPoints)
        return tmpPoints[0] to tmpPoints[1]
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (isTransforming) return true

        if (ignoreTouchUntilUp) {
            if (
                event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
            ) {
                ignoreTouchUntilUp = false
            }
            return true
        }

        if (isMoveMode && event.pointerCount == 1) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastPanX = event.x
                    lastPanY = event.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - lastPanX
                    val dy = event.y - lastPanY
                    lastPanX = event.x
                    lastPanY = event.y
                    viewMatrix.postTranslate(dx, dy)
                    viewMatrix.invert(inverseMatrix)
                    invalidate()
                }
            }
            return true
        }

        val (cx, cy) = screenToCanvas(event.x, event.y)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                currentPath = Path().apply { moveTo(cx, cy) }
                lastCanvasX = cx
                lastCanvasY = cy
                redoStrokes.clear()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (eraserMode) {
                    val segment = Path()
                    segment.moveTo(lastCanvasX, lastCanvasY)
                    segment.lineTo(cx, cy)
                    drawingCanvas?.drawPath(segment, currentPaint)
                }
                currentPath?.lineTo(cx, cy)
                lastCanvasX = cx
                lastCanvasY = cy
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                val path = currentPath ?: return true
                val paint = Paint(currentPaint)
                strokes.add(Stroke(path, paint))
                drawingCanvas?.drawPath(path, paint)
                currentPath = null
                invalidate()
                onStrokeStateChanged?.invoke()
            }
        }
        return true
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            redoStrokes.add(strokes.removeLast())
            redrawAll()
            invalidate()
            onStrokeStateChanged?.invoke()
        }
    }

    fun redo() {
        if (redoStrokes.isNotEmpty()) {
            strokes.add(redoStrokes.removeLast())
            redrawAll()
            invalidate()
            onStrokeStateChanged?.invoke()
        }
    }

    fun canUndo(): Boolean = strokes.isNotEmpty()

    fun canRedo(): Boolean = redoStrokes.isNotEmpty()

    fun clear() {
        strokes.clear()
        redoStrokes.clear()
        loadedBitmap = null
        clearBitmap()
        invalidate()
        onStrokeStateChanged?.invoke()
    }

    fun saveToBitmap(): Bitmap {
        if (drawingBitmap == null) {
            return Bitmap.createBitmap(
                width.coerceAtLeast(1),
                height.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888,
            )
        }
        val src = drawingBitmap!!
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(src, 0f, 0f, null)
        return result
    }

    fun loadBitmap(bitmap: Bitmap) {
        clear()
        this.loadedBitmap = bitmap
        if (drawingCanvas != null) {
            drawingCanvas?.drawBitmap(bitmap, 0f, 0f, null)
            invalidate()
        } else {
            pendingBitmap = bitmap
        }
    }

    private fun redrawAll() {
        clearBitmap()
        loadedBitmap?.let { drawingCanvas?.drawBitmap(it, 0f, 0f, null) }
        for (stroke in strokes) {
            drawingCanvas?.drawPath(stroke.path, stroke.paint)
        }
    }

    private fun clearBitmap() {
        drawingCanvas?.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        drawingBitmap?.recycle()
        drawingBitmap = null
    }
}
