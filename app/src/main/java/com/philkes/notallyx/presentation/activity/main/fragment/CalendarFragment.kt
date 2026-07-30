package com.philkes.notallyx.presentation.activity.main.fragment

import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.philkes.notallyx.R
import com.philkes.notallyx.data.NotallyDatabase
import com.philkes.notallyx.data.model.BaseNote
import com.philkes.notallyx.data.model.Folder
import com.philkes.notallyx.data.model.Item
import com.philkes.notallyx.data.model.NoteViewMode
import com.philkes.notallyx.data.model.Reminder
import com.philkes.notallyx.data.model.RepetitionTimeUnit
import com.philkes.notallyx.data.model.Type
import com.philkes.notallyx.databinding.FragmentCalendarBinding
import com.philkes.notallyx.presentation.activity.note.EditListActivity
import com.philkes.notallyx.presentation.activity.note.EditNoteActivity
import com.philkes.notallyx.presentation.dp
import com.philkes.notallyx.presentation.getColorFromAttr
import com.philkes.notallyx.presentation.view.main.BaseNoteAdapter
import com.philkes.notallyx.presentation.view.main.BaseNoteVHPreferences
import com.philkes.notallyx.presentation.view.main.sorting.BaseNoteCreationDateSort
import com.philkes.notallyx.presentation.view.misc.ItemListener
import com.philkes.notallyx.presentation.viewmodel.BaseNoteModel
import com.philkes.notallyx.presentation.viewmodel.preference.NotesSortBy
import com.philkes.notallyx.presentation.viewmodel.preference.SortDirection
import com.philkes.notallyx.utils.scheduleReminder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CalendarFragment : Fragment(), ItemListener {

    private var _binding: FragmentCalendarBinding? = null
    private val binding
        get() = _binding!!

    private val model: BaseNoteModel by activityViewModels()

    private val calendar = Calendar.getInstance()
    private val selectedCal =
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    private var allNotes: List<BaseNote> = emptyList()
    private var notesAdapter: BaseNoteAdapter? = null
    private var notesByDateMap: Map<Int, Int> = emptyMap()
    private var reminderDates: Set<Int> = emptySet()

    private val openNoteLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == android.app.Activity.RESULT_OK) {
                model.baseNotes?.value?.let { items ->
                    allNotes = items.filterIsInstance<BaseNote>()
                    rebuildNotesByDateMap()
                    updateCalendarGrid()
                    updateNotesList()
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupMonthNavigation()
        buildDayOfWeekRow()
        setupNotesRecyclerView()
        setupAddNoteButton()
        observeNotes()
        updateCalendarGrid()
        updateMonthYearText()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        notesAdapter = null
    }

    private fun setupMonthNavigation() {
        binding.PrevMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateMonthYearText()
            rebuildNotesByDateMap()
            updateCalendarGrid()
            updateNotesList()
        }
        binding.NextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateMonthYearText()
            rebuildNotesByDateMap()
            updateCalendarGrid()
            updateNotesList()
        }
    }

    private fun updateMonthYearText() {
        val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.MonthYearText.text = format.format(calendar.time)
    }

    private fun buildDayOfWeekRow() {
        val row = binding.DayOfWeekRow
        row.removeAllViews()
        val color =
            requireContext()
                .getColorFromAttr(com.google.android.material.R.attr.colorOnSurfaceVariant)
        for (day in listOf("S", "M", "T", "W", "T", "F", "S")) {
            row.addView(
                TextView(requireContext()).apply {
                    text = day
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(0, 32.dp, 1f)
                    setTextColor(color)
                    textSize = 12f
                }
            )
        }
    }

    private fun observeNotes() {
        model.baseNotes?.observe(viewLifecycleOwner) { items ->
            allNotes = items.filterIsInstance<BaseNote>()
            rebuildNotesByDateMap()
            updateCalendarGrid()
            updateNotesList()
        }
    }

    private fun reminderOccursOnDate(
        reminder: Reminder,
        targetYear: Int,
        targetMonth: Int,
        targetDay: Int,
    ): Boolean {
        val remCal =
            Calendar.getInstance().apply {
                time = reminder.dateTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        val rep =
            reminder.repetition
                ?: return (remCal.get(Calendar.YEAR) == targetYear &&
                    remCal.get(Calendar.MONTH) == targetMonth &&
                    remCal.get(Calendar.DAY_OF_MONTH) == targetDay)

        val targetCal =
            Calendar.getInstance().apply {
                set(Calendar.YEAR, targetYear)
                set(Calendar.MONTH, targetMonth)
                set(Calendar.DAY_OF_MONTH, targetDay)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

        if (targetCal.before(remCal)) return false

        val endCal =
            reminder.endDate?.let {
                Calendar.getInstance().apply {
                    time = it
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }
            }
        if (endCal != null && targetCal.after(endCal)) return false

        return when (rep.unit) {
            RepetitionTimeUnit.DAYS -> {
                val diffMs = targetCal.timeInMillis - remCal.timeInMillis
                val diffDays = (diffMs / (1000L * 60 * 60 * 24)).toInt()
                diffDays % rep.value == 0
            }
            RepetitionTimeUnit.WEEKS -> {
                if (targetCal.get(Calendar.DAY_OF_WEEK) != remCal.get(Calendar.DAY_OF_WEEK))
                    return false
                val diffMs = targetCal.timeInMillis - remCal.timeInMillis
                val diffWeeks = (diffMs / (1000L * 60 * 60 * 24 * 7)).toInt()
                diffWeeks % rep.value == 0
            }
            RepetitionTimeUnit.MONTHS -> {
                val occurrence = rep.occurrence
                val dayOfWeek = rep.dayOfWeek
                if (occurrence != null && dayOfWeek != null) {
                    val tempCal =
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, targetYear)
                            set(Calendar.MONTH, targetMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                    val occurrences = mutableListOf<Int>()
                    val maxDay = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    for (d in 1..maxDay) {
                        tempCal.set(Calendar.DAY_OF_MONTH, d)
                        if (tempCal.get(Calendar.DAY_OF_WEEK) == dayOfWeek) {
                            occurrences.add(d)
                        }
                    }
                    if (occurrences.size < occurrence) return false
                    if (targetDay != occurrences[occurrence - 1]) return false
                    val diffMonths =
                        (targetYear - remCal.get(Calendar.YEAR)) * 12 + targetMonth -
                            remCal.get(Calendar.MONTH)
                    diffMonths % rep.value == 0
                } else {
                    if (targetDay != remCal.get(Calendar.DAY_OF_MONTH)) return false
                    val diffMonths =
                        (targetYear - remCal.get(Calendar.YEAR)) * 12 + targetMonth -
                            remCal.get(Calendar.MONTH)
                    diffMonths % rep.value == 0
                }
            }
            RepetitionTimeUnit.YEARS -> {
                if (targetMonth != remCal.get(Calendar.MONTH)) return false
                if (targetDay != remCal.get(Calendar.DAY_OF_MONTH)) return false
                val diffYears = targetYear - remCal.get(Calendar.YEAR)
                diffYears % rep.value == 0
            }
            else -> false
        }
    }

    private fun rebuildNotesByDateMap() {
        val noteMap = mutableMapOf<Int, Int>()
        val remSet = mutableSetOf<Int>()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        for (note in allNotes) {
            for (reminder in note.reminders) {
                val tempCal =
                    Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, month)
                        set(Calendar.DAY_OF_MONTH, 1)
                    }
                val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                for (day in 1..daysInMonth) {
                    if (reminderOccursOnDate(reminder, year, month, day)) {
                        noteMap[day] = (noteMap[day] ?: 0) + 1
                        remSet.add(day)
                    }
                }
            }
        }
        notesByDateMap = noteMap
        reminderDates = remSet
    }

    private fun setupNotesRecyclerView() {
        val prefs = model.preferences
        notesAdapter =
            BaseNoteAdapter(
                selectedIds = model.actionMode.selectedIds,
                dateFormat = prefs.dateFormatOverview.value,
                timeFormat = prefs.timeFormatOverview.value,
                notesSortCallback = { adapter ->
                    BaseNoteCreationDateSort(adapter, SortDirection.DESC)
                },
                preferences =
                    BaseNoteVHPreferences(
                        prefs.textSizeOverview.value,
                        prefs.maxItems.value,
                        prefs.maxLines.value,
                        prefs.maxTitle.value,
                        prefs.labelTagsHiddenInOverview.value,
                        prefs.imagesHiddenInOverview.value,
                        NotesSortBy.CREATION_DATE,
                    ),
                imageRoot = model.imageRoot,
                drawingsRoot = model.drawingsRoot,
                listener = this,
            )
        binding.CalendarNotesList.apply {
            adapter = notesAdapter
            layoutManager = LinearLayoutManager(requireContext())
            setHasFixedSize(false)
        }
    }

    private fun setupAddNoteButton() {
        binding.AddNoteBtn.setOnClickListener {
            val year = selectedCal.get(Calendar.YEAR)
            val month = selectedCal.get(Calendar.MONTH)
            val day = selectedCal.get(Calendar.DAY_OF_MONTH)

            val reminderCal =
                Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                    set(Calendar.HOUR_OF_DAY, 9)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }

            val ctx = requireActivity()
            lifecycleScope.launch {
                val id =
                    withContext(Dispatchers.IO) {
                        val db = NotallyDatabase.getDatabase(ctx).value ?: return@withContext -1L
                        val dao = db.getBaseNoteDao()
                        val note =
                            BaseNote(
                                id = 0L,
                                type = Type.NOTE,
                                folder = Folder.NOTES,
                                color = BaseNote.COLOR_DEFAULT,
                                title = "",
                                pinned = false,
                                timestamp = System.currentTimeMillis(),
                                modifiedTimestamp = System.currentTimeMillis(),
                                labels = emptyList(),
                                body = "",
                                spans = emptyList(),
                                items = emptyList(),
                                images = emptyList(),
                                files = emptyList(),
                                audios = emptyList(),
                                reminders =
                                    listOf(
                                        Reminder(
                                            id = 0L,
                                            dateTime = reminderCal.time,
                                            repetition = null,
                                            isNotificationVisible = false,
                                        )
                                    ),
                                viewMode = NoteViewMode.EDIT,
                                isPinnedToStatus = false,
                            )
                        dao.insertSafe(ctx, note)
                    }
                if (id != -1L) {
                    val savedNote =
                        withContext(Dispatchers.IO) {
                            NotallyDatabase.getDatabase(ctx).value?.getBaseNoteDao()?.get(id)
                        }
                    savedNote?.reminders?.forEach { reminder -> ctx.scheduleReminder(id, reminder) }
                    val intent = Intent(ctx, EditNoteActivity::class.java)
                    intent.putExtra("notallyx.intent.extra.SELECTED_BASE_NOTE", id)
                    openNoteLauncher.launch(intent)
                }
            }
        }
    }

    private fun updateCalendarGrid() {
        val grid = binding.CalendarGrid
        grid.removeAllViews()

        val tempCal = calendar.clone() as Calendar
        tempCal.set(Calendar.DAY_OF_MONTH, 1)
        val offset = tempCal.get(Calendar.DAY_OF_WEEK) - 1
        val daysInMonth = tempCal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val todayCal = Calendar.getInstance()
        val isCurrentMonth =
            todayCal.get(Calendar.YEAR) == calendar.get(Calendar.YEAR) &&
                todayCal.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)
        val todayDay = todayCal.get(Calendar.DAY_OF_MONTH)

        val selectedYear = selectedCal.get(Calendar.YEAR)
        val selectedMonth = selectedCal.get(Calendar.MONTH)
        val isDateSelected =
            selectedYear == calendar.get(Calendar.YEAR) &&
                selectedMonth == calendar.get(Calendar.MONTH)

        val cellSize = 48.dp
        val ctx = requireContext()
        val primaryColor = ctx.getColorFromAttr(com.google.android.material.R.attr.colorPrimary)
        val onPrimaryColor = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnPrimary)
        val onSurfaceColor = ctx.getColorFromAttr(com.google.android.material.R.attr.colorOnSurface)
        val tertiaryColor = ctx.getColorFromAttr(com.google.android.material.R.attr.colorTertiary)
        val dotSize = 5.dp

        for (i in 0 until offset) {
            grid.addView(createEmptyCell(cellSize))
        }

        for (day in 1..daysInMonth) {
            val hasNotes = notesByDateMap.containsKey(day)
            val hasReminder = reminderDates.contains(day)
            val isToday = isCurrentMonth && day == todayDay
            val isSelected = isDateSelected && day == selectedCal.get(Calendar.DAY_OF_MONTH)

            val cell = FrameLayout(ctx)
            cell.layoutParams =
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = cellSize
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }

            val dayText =
                TextView(ctx).apply {
                    text = day.toString()
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(cellSize, cellSize, Gravity.CENTER)
                    textSize = 14f

                    when {
                        isSelected -> {
                            val bg =
                                GradientDrawable().apply {
                                    shape = GradientDrawable.OVAL
                                    setColor(primaryColor)
                                }
                            background = bg
                            setTextColor(onPrimaryColor)
                        }
                        isToday -> {
                            setTextColor(primaryColor)
                            setTypeface(typeface, Typeface.BOLD)
                        }
                        else -> setTextColor(onSurfaceColor)
                    }

                    if (hasNotes && !isSelected) {
                        setTypeface(typeface, Typeface.BOLD)
                    }
                }
            cell.addView(dayText)

            if (isSelected) {
                // no dot on selected day
            } else if (hasReminder) {
                val dot =
                    View(ctx).apply {
                        val dotBg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(tertiaryColor)
                            }
                        background = dotBg
                        layoutParams =
                            FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER).apply {
                                topMargin = 14.dp
                            }
                    }
                cell.addView(dot)
            } else if (hasNotes) {
                val dot =
                    View(ctx).apply {
                        val dotBg =
                            GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(primaryColor)
                            }
                        background = dotBg
                        layoutParams =
                            FrameLayout.LayoutParams(dotSize, dotSize, Gravity.CENTER).apply {
                                topMargin = 14.dp
                            }
                    }
                cell.addView(dot)
            }

            cell.setOnClickListener {
                if (
                    selectedCal.get(Calendar.DAY_OF_MONTH) != day ||
                        selectedCal.get(Calendar.YEAR) != calendar.get(Calendar.YEAR) ||
                        selectedCal.get(Calendar.MONTH) != calendar.get(Calendar.MONTH)
                ) {
                    selectedCal.timeInMillis = calendar.timeInMillis
                    selectedCal.set(Calendar.DAY_OF_MONTH, day)
                    updateCalendarGrid()
                    updateNotesList()
                }
            }

            grid.addView(cell)
        }

        grid.requestLayout()
    }

    private fun createEmptyCell(size: Int): View {
        return View(requireContext()).apply {
            layoutParams =
                GridLayout.LayoutParams().apply {
                    width = 0
                    height = size
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                }
        }
    }

    private fun updateNotesList() {
        val year = selectedCal.get(Calendar.YEAR)
        val month = selectedCal.get(Calendar.MONTH)
        val day = selectedCal.get(Calendar.DAY_OF_MONTH)

        val notesForDay =
            allNotes.filter { note ->
                note.reminders.any { reminder -> reminderOccursOnDate(reminder, year, month, day) }
            }

        val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        binding.SelectedDateText.text = dateFormat.format(selectedCal.time)
        binding.SelectedDateText.visibility = View.VISIBLE
        binding.AddNoteBtn.visibility = View.VISIBLE

        if (notesForDay.isEmpty()) {
            notesAdapter?.submitList(emptyList())
            binding.ImageView.apply {
                setImageResource(R.drawable.notebook)
                visibility = View.VISIBLE
            }
            binding.EmptyMessage.apply {
                text = getString(R.string.all_clear)
                visibility = View.VISIBLE
            }
        } else {
            binding.ImageView.visibility = View.GONE
            binding.EmptyMessage.visibility = View.GONE
            notesAdapter?.submitList(notesForDay.map { it as Item })
            binding.CalendarNotesList.scrollToPosition(0)
        }
    }

    override fun onClick(position: Int) {
        val item = notesAdapter?.getItem(position) ?: return
        if (item is BaseNote) {
            val intent =
                Intent(
                    requireContext(),
                    if (item.type == Type.LIST) EditListActivity::class.java
                    else EditNoteActivity::class.java,
                )
            intent.putExtra("notallyx.intent.extra.SELECTED_BASE_NOTE", item.id)
            openNoteLauncher.launch(intent)
        }
    }

    override fun onLongClick(position: Int) {
        notesAdapter?.getItem(position)?.let { item ->
            if (item is BaseNote) {
                val id = item.id
                if (model.actionMode.selectedNotes.contains(id)) {
                    model.actionMode.remove(id)
                } else {
                    model.actionMode.add(id, item)
                }
            }
        }
    }

    override fun onReminderClick(position: Int) {
        onClick(position)
    }
}
