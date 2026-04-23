package com.tusur.app.fragments

import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tusur.app.R
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

data class Lesson(
    val time: String,       // "HH:MM\nHH:MM"
    val subject: String,
    val type: String,
    val room: String,
    val teacher: String,
    val day: String         // "Понедельник", "Вторник", etc.
)

class ScheduleFragment : Fragment() {

    private enum class Screen { WEEK, CALENDAR, DAY }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var allLessons: List<Lesson> = emptyList()

    private var currentWeekMonday: Calendar = run {
        val cal = Calendar.getInstance()
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            cal.add(Calendar.DAY_OF_MONTH, -1)
        cal
    }
    private var calendarMonth: Calendar = Calendar.getInstance()
    private var currentDay: Calendar = Calendar.getInstance()

    // Screens
    private lateinit var screenWeek: View
    private lateinit var screenCalendar: View
    private lateinit var screenDay: View

    // Week screen
    private lateinit var tvWeekTitle: TextView
    private lateinit var rvWeekDays: RecyclerView

    // Calendar screen
    private lateinit var tvCalendarTitle: TextView
    private lateinit var calendarContainer: LinearLayout

    // Day screen
    private lateinit var tvDayNameDetail: TextView
    private lateinit var tvDayDateDetail: TextView
    private lateinit var rvDayLessons: RecyclerView
    private lateinit var tvNoLessons: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Bind screens
        screenWeek     = view.findViewById(R.id.screen_week)
        screenCalendar = view.findViewById(R.id.screen_calendar)
        screenDay      = view.findViewById(R.id.screen_day)

        // Week screen
        tvWeekTitle = view.findViewById(R.id.tv_week_title)
        rvWeekDays  = view.findViewById(R.id.rv_week_days)
        rvWeekDays.layoutManager = LinearLayoutManager(requireContext())

        // Calendar screen
        tvCalendarTitle   = view.findViewById(R.id.tv_calendar_title)
        calendarContainer = view.findViewById(R.id.calendar_container)

        // Day screen
        tvDayNameDetail = view.findViewById(R.id.tv_day_name)
        tvDayDateDetail = view.findViewById(R.id.tv_day_date_detail)
        rvDayLessons    = view.findViewById(R.id.rv_day_lessons)
        tvNoLessons     = view.findViewById(R.id.tv_no_lessons)
        rvDayLessons.layoutManager = LinearLayoutManager(requireContext())

        // --- Week screen buttons ---
        view.findViewById<TextView>(R.id.btn_calendar_open).setOnClickListener {
            calendarMonth = currentWeekMonday.clone() as Calendar
            showScreen(Screen.CALENDAR)
            buildCalendarView()
        }
        view.findViewById<TextView>(R.id.btn_week_prev).setOnClickListener {
            currentWeekMonday.add(Calendar.DAY_OF_MONTH, -7)
            renderWeekScreen()
        }
        view.findViewById<TextView>(R.id.btn_week_next).setOnClickListener {
            currentWeekMonday.add(Calendar.DAY_OF_MONTH, 7)
            renderWeekScreen()
        }

        // --- Calendar screen buttons ---
        view.findViewById<TextView>(R.id.btn_calendar_back).setOnClickListener {
            showScreen(Screen.WEEK)
        }
        view.findViewById<TextView>(R.id.btn_cal_prev).setOnClickListener {
            calendarMonth.add(Calendar.MONTH, -1)
            buildCalendarView()
        }
        view.findViewById<TextView>(R.id.btn_cal_next).setOnClickListener {
            calendarMonth.add(Calendar.MONTH, 1)
            buildCalendarView()
        }

        // --- Day screen buttons ---
        view.findViewById<TextView>(R.id.btn_day_back).setOnClickListener {
            showScreen(Screen.WEEK)
        }
        view.findViewById<TextView>(R.id.btn_day_prev).setOnClickListener {
            currentDay.add(Calendar.DAY_OF_MONTH, -1)
            renderDayScreen()
        }
        view.findViewById<TextView>(R.id.btn_day_next).setOnClickListener {
            currentDay.add(Calendar.DAY_OF_MONTH, 1)
            renderDayScreen()
        }

        // Initial render
        showScreen(Screen.WEEK)

        scope.launch {
            val cached = loadFromCache()
            allLessons = cached
            renderWeekScreen()

            try {
                val fresh = withContext(Dispatchers.IO) { parseSchedule() }
                if (fresh.isNotEmpty()) {
                    allLessons = fresh
                    saveToCache(fresh)
                    renderWeekScreen()
                }
            } catch (_: Exception) {}
        }
    }

    // ==================== NAVIGATION ====================

    private fun showScreen(screen: Screen) {
        screenWeek.visibility     = if (screen == Screen.WEEK)     View.VISIBLE else View.GONE
        screenCalendar.visibility = if (screen == Screen.CALENDAR) View.VISIBLE else View.GONE
        screenDay.visibility      = if (screen == Screen.DAY)      View.VISIBLE else View.GONE
    }

    fun onBackPressed(): Boolean {
        return when {
            screenCalendar.visibility == View.VISIBLE -> { showScreen(Screen.WEEK); true }
            screenDay.visibility == View.VISIBLE      -> { showScreen(Screen.WEEK); true }
            else -> false
        }
    }

    // ==================== WEEK SCREEN ====================

    private fun renderWeekScreen() {
        val weekNum = getAcademicWeekNumber(currentWeekMonday)
        val parity  = if (weekNum % 2 == 1) "нечетная" else "четная"
        tvWeekTitle.text = "$weekNum неделя — $parity"

        val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота")
        val days = (0..5).map { i ->
            val day = currentWeekMonday.clone() as Calendar
            day.add(Calendar.DAY_OF_MONTH, i)
            Pair(dayNames[i], day)
        }

        rvWeekDays.adapter = WeekDaysAdapter(days) { dayName, day ->
            currentDay = day.clone() as Calendar
            renderDayScreen()
            showScreen(Screen.DAY)
        }
    }

    // ==================== DAY SCREEN ====================

    private fun renderDayScreen() {
        val dayName = calDayName(currentDay)
        tvDayNameDetail.text = dayName
        tvDayDateDetail.text = formatDateFull(currentDay)

        val lessons = allLessons.filter { it.day.contains(dayName, ignoreCase = true) }

        if (lessons.isEmpty()) {
            rvDayLessons.visibility = View.GONE
            tvNoLessons.visibility  = View.VISIBLE
            tvNoLessons.text = if (allLessons.isEmpty()) "Загрузка расписания…" else "Нет занятий"
        } else {
            rvDayLessons.visibility = View.VISIBLE
            tvNoLessons.visibility  = View.GONE
            rvDayLessons.adapter = DayLessonsAdapter(lessons)
        }
    }

    private fun calDayName(cal: Calendar): String = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY    -> "Понедельник"
        Calendar.TUESDAY   -> "Вторник"
        Calendar.WEDNESDAY -> "Среда"
        Calendar.THURSDAY  -> "Четверг"
        Calendar.FRIDAY    -> "Пятница"
        Calendar.SATURDAY  -> "Суббота"
        Calendar.SUNDAY    -> "Воскресенье"
        else               -> "День"
    }

    // ==================== CALENDAR SCREEN ====================

    private fun buildCalendarView() {
        val year  = calendarMonth.get(Calendar.YEAR)
        val month = calendarMonth.get(Calendar.MONTH)

        val monthNames = arrayOf("Январь","Февраль","Март","Апрель","Май","Июнь",
            "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
        tvCalendarTitle.text = "${monthNames[month]} $year"

        calendarContainer.removeAllViews()

        // Header row: empty | пн вт ср чт пт сб вс
        val headerRow = makeRow()
        headerRow.addView(makeLabelCell(""))
        listOf("пн","вт","ср","чт","пт","сб","вс").forEach {
            headerRow.addView(makeDayCell(it, isHeader = true))
        }
        calendarContainer.addView(headerRow)

        // Find Monday of the first week of this month
        val firstOfMonth = Calendar.getInstance().apply { set(year, month, 1) }
        val cur = firstOfMonth.clone() as Calendar
        while (cur.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cur.add(Calendar.DAY_OF_MONTH, -1)
        }

        val today = Calendar.getInstance()

        while (true) {
            val weekRow = makeRow()

            // Week parity label
            val weekNum = getAcademicWeekNumber(cur)
            weekRow.addView(makeLabelCell(if (weekNum % 2 == 1) "Н" else "Ч"))

            for (d in 0..6) {
                val cellDay = cur.clone() as Calendar
                val isThisMonth = cellDay.get(Calendar.MONTH) == month
                val isToday     = isSameDay(cellDay, today)
                val isInWeek    = isInWeek(cellDay, currentWeekMonday)

                weekRow.addView(makeDayCell(
                    text         = cellDay.get(Calendar.DAY_OF_MONTH).toString(),
                    isOtherMonth = !isThisMonth,
                    isToday      = isToday,
                    isSelected   = isInWeek && isThisMonth,
                    onClick      = {
                        currentWeekMonday = getMondayOfWeek(cellDay)
                        renderWeekScreen()
                        showScreen(Screen.WEEK)
                    }
                ))
                cur.add(Calendar.DAY_OF_MONTH, 1)
            }

            calendarContainer.addView(weekRow)

            // Stop after we've gone past this month
            if (cur.get(Calendar.MONTH) != month && cur.get(Calendar.DAY_OF_MONTH) > 7) break
            if (cur.get(Calendar.YEAR) > year && cur.get(Calendar.MONTH) > month) break
        }
    }

    private fun makeRow(): LinearLayout {
        val row = LinearLayout(requireContext())
        row.orientation = LinearLayout.HORIZONTAL
        row.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        return row
    }

    private fun makeLabelCell(text: String): TextView {
        val density = resources.displayMetrics.density
        val tv = TextView(requireContext())
        tv.layoutParams = LinearLayout.LayoutParams(
            (28 * density).toInt(), (40 * density).toInt()
        )
        tv.gravity = Gravity.CENTER
        tv.text = text
        tv.textSize = 11f
        tv.setTextColor(Color.parseColor("#1565C0"))
        tv.typeface = Typeface.DEFAULT_BOLD
        return tv
    }

    private fun makeDayCell(
        text: String,
        isHeader: Boolean = false,
        isOtherMonth: Boolean = false,
        isToday: Boolean = false,
        isSelected: Boolean = false,
        onClick: (() -> Unit)? = null
    ): TextView {
        val density = resources.displayMetrics.density
        val cellH = (40 * density).toInt()
        val tv = TextView(requireContext())
        tv.layoutParams = LinearLayout.LayoutParams(0, cellH, 1f)
        tv.gravity = Gravity.CENTER
        tv.text = text

        when {
            isHeader -> {
                tv.textSize = 11f
                tv.setTextColor(Color.parseColor("#AAAAAA"))
                tv.typeface = Typeface.DEFAULT_BOLD
            }
            isToday -> {
                tv.textSize = 13f
                tv.setTextColor(Color.WHITE)
                tv.typeface = Typeface.DEFAULT_BOLD
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#1565C0"))
                    val r = (17 * density).toInt()
                    setSize(r * 2, r * 2)
                }
                tv.background = bg
            }
            isSelected -> {
                tv.textSize = 13f
                tv.setTextColor(Color.parseColor("#1565C0"))
                tv.typeface = Typeface.DEFAULT_BOLD
            }
            isOtherMonth -> {
                tv.textSize = 12f
                tv.setTextColor(Color.parseColor("#CCCCCC"))
            }
            else -> {
                tv.textSize = 13f
                tv.setTextColor(Color.parseColor("#333333"))
            }
        }

        if (onClick != null) {
            tv.setOnClickListener { onClick() }
            if (!isHeader) {
                tv.background = tv.background ?: run {
                    val ripple = android.util.TypedValue()
                    requireContext().theme.resolveAttribute(
                        android.R.attr.selectableItemBackgroundBorderless, ripple, true
                    )
                    ContextCompat.getDrawable(requireContext(), ripple.resourceId)
                }
            }
        }
        return tv
    }

    // ==================== HELPERS ====================

    private fun getMondayOfWeek(cal: Calendar): Calendar {
        val result = cal.clone() as Calendar
        while (result.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            result.add(Calendar.DAY_OF_MONTH, -1)
        return result
    }

    private fun getAcademicWeekNumber(monday: Calendar): Int {
        val m = monday.get(Calendar.MONTH)
        val y = monday.get(Calendar.YEAR)
        val semYear = if (m >= Calendar.SEPTEMBER) y else y - 1

        val semStart = Calendar.getInstance().apply {
            set(semYear, Calendar.SEPTEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
                add(Calendar.DAY_OF_MONTH, 1)
        }

        val weeks = ((monday.timeInMillis - semStart.timeInMillis) /
                (7L * 24 * 60 * 60 * 1000)).toInt()
        return maxOf(1, weeks + 1)
    }

    private fun isSameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isInWeek(day: Calendar, monday: Calendar): Boolean {
        val d = day.timeInMillis
        val m = monday.timeInMillis
        return d >= m && d < m + 7L * 24 * 60 * 60 * 1000
    }

    private fun formatDateFull(cal: Calendar): String {
        val months = arrayOf("января","февраля","марта","апреля","мая","июня",
            "июля","августа","сентября","октября","ноября","декабря")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} года"
    }

    // ==================== PARSE ====================

    private fun parseSchedule(): List<Lesson> {
        val lessons = mutableListOf<Lesson>()
        val doc = Jsoup.connect("https://timetable.tusur.ru/faculties/fsu/groups/444-1")
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(15000)
            .get()

        val timeRegex = Regex("(\\d{1,2}:\\d{2})\\s+(\\d{1,2}:\\d{2})")
        val dayRegex  = Regex("(пн|вт|ср|чт|пт|сб),?\\s*\\d{1,2}\\s+" +
                "(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)",
            RegexOption.IGNORE_CASE)
        val idRegex = Regex("^\\d{4,}\\s*")

        val typeKeywords = listOf(
            "Лабораторная работа" to "Лаб. работа",
            "Лабораторная"        to "Лаб. работа",
            "Лекция"              to "Лекция",
            "Практика"            to "Практика",
            "Курсовое проектирование" to "Курсовой",
            "Курсовое"            to "Курсовой",
            "Консультация"        to "Консультация",
            "Экзамен"             to "Экзамен",
            "Зачёт"               to "Зачёт",
            "Зачет"               to "Зачёт"
        )

        val tables = doc.select("table")
        if (tables.isNotEmpty()) {
            for (table in tables) {
                var currentDay = ""
                for (row in table.select("tr")) {
                    val cells = row.select("td")
                    if (cells.isEmpty()) continue

                    val rowText  = row.text().trim()
                    val dayMatch = dayRegex.find(rowText)
                    if (dayMatch != null) currentDay = formatDay(dayMatch.value)

                    for (i in cells.indices) {
                        val cell     = cells[i]
                        val cellText = cell.text().trim()
                        if (cellText.length < 5) continue

                        var lessonTypeShort = ""
                        var lessonType      = ""
                        for ((kw, short) in typeKeywords) {
                            if (cellText.contains(kw, ignoreCase = true)) {
                                lessonType      = kw
                                lessonTypeShort = short
                                break
                            }
                        }
                        if (lessonType.isEmpty()) continue

                        val timeSource = if (i > 0) cells[i - 1].text() else cellText
                        val timeMatch  = timeRegex.find(timeSource)
                        val timeStr = if (timeMatch != null)
                            "${timeMatch.groupValues[1]}\n${timeMatch.groupValues[2]}"
                        else Regex("(\\d{1,2}:\\d{2})").find(timeSource)?.value ?: "—"

                        val titleAttr = cell.attr("title").trim()
                        var subject = if (titleAttr.isNotEmpty())
                            titleAttr.substringBefore("Вид занятия").trim()
                                .replace(idRegex, "").trim()
                        else
                            cellText.substringBefore(lessonType).trim()
                                .replace(idRegex, "").trim()
                        if (subject.isEmpty()) continue

                        val room    = cell.select("a[href*=auditorium], a[href*=building]")
                            .joinToString(", ") { it.text().trim() }
                        val teacher = cell.select("a[href*=teacher]")
                            .joinToString(", ") { it.text().trim() }

                        lessons.add(Lesson(
                            time    = timeStr,
                            subject = subject,
                            type    = lessonTypeShort,
                            room    = room.ifEmpty { "—" },
                            teacher = teacher.ifEmpty { "—" },
                            day     = currentDay.ifEmpty { "Понедельник" }
                        ))
                    }
                }
            }
        }

        if (lessons.isEmpty()) {
            var curDay  = ""
            var curTime = ""
            for (el in doc.allElements) {
                val text = el.ownText().trim()
                if (text.isEmpty()) continue

                val dm = dayRegex.find(text)
                if (dm != null) { curDay = formatDay(text); continue }

                val tm = timeRegex.find(text)
                if (tm != null) { curTime = "${tm.groupValues[1]}\n${tm.groupValues[2]}"; continue }

                var typeShort = ""
                var typeKw    = ""
                for ((kw, short) in typeKeywords) {
                    if (text.contains(kw, ignoreCase = true)) { typeKw = kw; typeShort = short; break }
                }
                if (typeKw.isEmpty()) continue

                val subj = text.substringBefore(typeKw).trim().replace(idRegex, "").trim()
                if (subj.isEmpty()) continue

                val room    = el.select("a[href*=auditorium]").joinToString(", ") { it.text() }
                val teacher = el.select("a[href*=teacher]").joinToString(", ") { it.text() }

                lessons.add(Lesson(
                    time    = curTime.ifEmpty { "—" },
                    subject = subj,
                    type    = typeShort,
                    room    = room.ifEmpty { "—" },
                    teacher = teacher.ifEmpty { "—" },
                    day     = curDay.ifEmpty { "Понедельник" }
                ))
            }
        }

        return lessons
    }

    private fun formatDay(raw: String): String {
        val map = mapOf("пн" to "Понедельник","вт" to "Вторник","ср" to "Среда",
            "чт" to "Четверг","пт" to "Пятница","сб" to "Суббота","вс" to "Воскресенье")
        var result = raw.trim()
        for ((short, full) in map) {
            if (result.lowercase().startsWith(short)) {
                result = result.replaceFirst(Regex("(?i)$short,?\\s*"), "$full, ")
                break
            }
        }
        return result.trim().trimEnd(',').trim()
    }

    // ==================== CACHE ====================

    private fun saveToCache(lessons: List<Lesson>) {
        val prefs = requireContext().getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (l in lessons) arr.put(JSONObject().apply {
            put("time", l.time); put("subject", l.subject); put("type", l.type)
            put("room", l.room); put("teacher", l.teacher); put("day", l.day)
        })
        prefs.edit().putString("lessons", arr.toString()).apply()
    }

    private fun loadFromCache(): List<Lesson> {
        val json = requireContext()
            .getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
            .getString("lessons", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getJSONObject(it).run {
                Lesson(getString("time"), getString("subject"), getString("type"),
                       getString("room"), getString("teacher"), getString("day"))
            }}
        } catch (_: Exception) { emptyList() }
    }

    override fun onDestroyView() { scope.cancel(); super.onDestroyView() }
}

// ==================== ADAPTERS ====================

class WeekDaysAdapter(
    private val days: List<Pair<String, Calendar>>,
    private val onClick: (String, Calendar) -> Unit
) : RecyclerView.Adapter<WeekDaysAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_day_item_name)
        val tvDate: TextView = view.findViewById(R.id.tv_day_item_date)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_week_day, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val (name, cal) = days[i]
        h.tvName.text = name
        h.tvDate.text = formatDate(cal)
        h.itemView.setOnClickListener { onClick(name, cal) }
    }

    private fun formatDate(cal: Calendar): String {
        val months = arrayOf("января","февраля","марта","апреля","мая","июня",
            "июля","августа","сентября","октября","ноября","декабря")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} года"
    }

    override fun getItemCount() = days.size
}

class DayLessonsAdapter(private val lessons: List<Lesson>) :
    RecyclerView.Adapter<DayLessonsAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject:   TextView = view.findViewById(R.id.tv_dl_subject)
        val tvRoom:      TextView = view.findViewById(R.id.tv_dl_room)
        val tvTimeStart: TextView = view.findViewById(R.id.tv_dl_time_start)
        val tvTimeEnd:   TextView = view.findViewById(R.id.tv_dl_time_end)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_day_lesson, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val l = lessons[i]
        h.tvSubject.text = l.subject
        h.tvRoom.text    = l.room
        val times = l.time.split("\n")
        h.tvTimeStart.text = times.getOrElse(0) { "" }
        h.tvTimeEnd.text   = times.getOrElse(1) { "" }
    }

    override fun getItemCount() = lessons.size
}
