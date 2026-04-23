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
import com.tusur.app.SessionManager
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.abs

data class Lesson(
    val time: String,
    val subject: String,
    val type: String,
    val room: String,
    val teacher: String,
    val day: String
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

    private lateinit var screenWeek: View
    private lateinit var screenCalendar: View
    private lateinit var screenDay: View

    private lateinit var tvWeekTitle: TextView
    private lateinit var rvWeekDays: RecyclerView
    private lateinit var tvCalendarTitle: TextView
    private lateinit var calendarContainer: LinearLayout
    private lateinit var tvDayNameDetail: TextView
    private lateinit var tvDayDateDetail: TextView
    private lateinit var rvDayLessons: RecyclerView
    private lateinit var tvNoLessons: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        screenWeek     = view.findViewById(R.id.screen_week)
        screenCalendar = view.findViewById(R.id.screen_calendar)
        screenDay      = view.findViewById(R.id.screen_day)

        tvWeekTitle = view.findViewById(R.id.tv_week_title)
        rvWeekDays  = view.findViewById(R.id.rv_week_days)
        rvWeekDays.layoutManager = LinearLayoutManager(requireContext())

        tvCalendarTitle   = view.findViewById(R.id.tv_calendar_title)
        calendarContainer = view.findViewById(R.id.calendar_container)

        tvDayNameDetail = view.findViewById(R.id.tv_day_name)
        tvDayDateDetail = view.findViewById(R.id.tv_day_date_detail)
        rvDayLessons    = view.findViewById(R.id.rv_day_lessons)
        tvNoLessons     = view.findViewById(R.id.tv_no_lessons)
        rvDayLessons.layoutManager = LinearLayoutManager(requireContext())

        // --- Кнопки ---
        view.findViewById<TextView>(R.id.btn_calendar_open).setOnClickListener {
            calendarMonth = currentWeekMonday.clone() as Calendar
            showScreen(Screen.CALENDAR)
            buildCalendarView()
        }
        view.findViewById<TextView>(R.id.btn_week_prev).setOnClickListener { prevWeek() }
        view.findViewById<TextView>(R.id.btn_week_next).setOnClickListener { nextWeek() }
        view.findViewById<TextView>(R.id.btn_calendar_back).setOnClickListener { showScreen(Screen.WEEK) }
        view.findViewById<TextView>(R.id.btn_cal_prev).setOnClickListener { prevMonth() }
        view.findViewById<TextView>(R.id.btn_cal_next).setOnClickListener { nextMonth() }
        view.findViewById<TextView>(R.id.btn_day_back).setOnClickListener { showScreen(Screen.WEEK) }
        view.findViewById<TextView>(R.id.btn_day_prev).setOnClickListener {
            currentDay.add(Calendar.DAY_OF_MONTH, -1); renderDayScreen()
        }
        view.findViewById<TextView>(R.id.btn_day_next).setOnClickListener {
            currentDay.add(Calendar.DAY_OF_MONTH, 1); renderDayScreen()
        }

        // --- Свайп на экране недели ---
        setupSwipe(screenWeek,
            onLeft  = { nextWeek() },
            onRight = { prevWeek() }
        )

        // --- Свайп на экране календаря ---
        setupSwipe(screenCalendar,
            onLeft  = { nextMonth() },
            onRight = { prevMonth() }
        )

        showScreen(Screen.WEEK)

        scope.launch {
            val cached = loadFromCache()
            allLessons = cached
            renderWeekScreen()

            try {
                val group = SessionManager(requireContext()).group
                val fresh = withContext(Dispatchers.IO) { parseSchedule(group) }
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

    fun onBackPressed(): Boolean = when {
        screenCalendar.visibility == View.VISIBLE -> { showScreen(Screen.WEEK); true }
        screenDay.visibility      == View.VISIBLE -> { showScreen(Screen.WEEK); true }
        else -> false
    }

    private fun prevWeek() { currentWeekMonday.add(Calendar.DAY_OF_MONTH, -7); renderWeekScreen() }
    private fun nextWeek() { currentWeekMonday.add(Calendar.DAY_OF_MONTH,  7); renderWeekScreen() }
    private fun prevMonth() { calendarMonth.add(Calendar.MONTH, -1); buildCalendarView() }
    private fun nextMonth() { calendarMonth.add(Calendar.MONTH,  1); buildCalendarView() }

    // ==================== SWIPE ====================

    private fun setupSwipe(target: View, onLeft: () -> Unit, onRight: () -> Unit) {
        val detector = GestureDetector(requireContext(),
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float
                ): Boolean {
                    val dx = e2.x - (e1?.x ?: return false)
                    val dy = e2.y - (e1?.y ?: return false)
                    if (abs(dx) > abs(dy) * 1.2f && abs(dx) > 80 && abs(vX) > 80) {
                        if (dx < 0) onLeft() else onRight()
                        return true
                    }
                    return false
                }
            })
        target.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false   // не потребляем — дочерние view тоже получают событие
        }
    }

    // ==================== WEEK SCREEN ====================

    private fun renderWeekScreen() {
        val weekNum = getAcademicWeekNumber(currentWeekMonday)
        val parity  = if (weekNum % 2 == 1) "нечётная" else "чётная"
        tvWeekTitle.text = "$weekNum неделя — $parity"

        val names = listOf("Понедельник","Вторник","Среда","Четверг","Пятница","Суббота")
        val days  = (0..5).map { i ->
            val d = currentWeekMonday.clone() as Calendar
            d.add(Calendar.DAY_OF_MONTH, i)
            Pair(names[i], d)
        }

        rvWeekDays.adapter = WeekDaysAdapter(days) { _, day ->
            currentDay = day.clone() as Calendar
            renderDayScreen()
            showScreen(Screen.DAY)
        }
    }

    // ==================== DAY SCREEN ====================

    private fun renderDayScreen() {
        val name = calDayName(currentDay)
        tvDayNameDetail.text = name
        tvDayDateDetail.text = formatDateFull(currentDay)

        val lessons = allLessons.filter { it.day.contains(name, ignoreCase = true) }

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

    private fun calDayName(c: Calendar) = when (c.get(Calendar.DAY_OF_WEEK)) {
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

        val names = arrayOf("Январь","Февраль","Март","Апрель","Май","Июнь",
            "Июль","Август","Сентябрь","Октябрь","Ноябрь","Декабрь")
        tvCalendarTitle.text = "${names[month]} $year"

        calendarContainer.removeAllViews()

        // Заголовок: пн вт ср чт пт сб вс
        val header = makeRow()
        header.addView(makeLabelCell(""))
        listOf("пн","вт","ср","чт","пт","сб","вс").forEach { header.addView(makeDayCell(it, isHeader = true)) }
        calendarContainer.addView(header)

        // Первый понедельник недели, содержащей 1-е число
        val cur = Calendar.getInstance().apply { set(year, month, 1) }
        while (cur.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY)
            cur.add(Calendar.DAY_OF_MONTH, -1)

        val today = Calendar.getInstance()

        while (true) {
            val row = makeRow()
            val wn  = getAcademicWeekNumber(cur)
            row.addView(makeLabelCell(if (wn % 2 == 1) "Н" else "Ч"))

            for (d in 0..6) {
                val cell = cur.clone() as Calendar
                row.addView(makeDayCell(
                    text         = cell.get(Calendar.DAY_OF_MONTH).toString(),
                    isOtherMonth = cell.get(Calendar.MONTH) != month,
                    isToday      = isSameDay(cell, today),
                    isSelected   = isInWeek(cell, currentWeekMonday) && cell.get(Calendar.MONTH) == month,
                    onClick      = {
                        currentWeekMonday = getMondayOfWeek(cell)
                        renderWeekScreen()
                        showScreen(Screen.WEEK)
                    }
                ))
                cur.add(Calendar.DAY_OF_MONTH, 1)
            }
            calendarContainer.addView(row)

            if (cur.get(Calendar.MONTH) != month && cur.get(Calendar.DAY_OF_MONTH) > 7) break
        }
    }

    private fun makeRow() = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    private fun makeLabelCell(text: String) = TextView(requireContext()).apply {
        val dp = resources.displayMetrics.density
        layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (40 * dp).toInt())
        gravity = Gravity.CENTER
        this.text = text
        textSize = 11f
        setTextColor(Color.parseColor("#3D3DA8"))
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun makeDayCell(
        text: String,
        isHeader: Boolean = false,
        isOtherMonth: Boolean = false,
        isToday: Boolean = false,
        isSelected: Boolean = false,
        onClick: (() -> Unit)? = null
    ) = TextView(requireContext()).apply {
        val dp = resources.displayMetrics.density
        layoutParams = LinearLayout.LayoutParams(0, (40 * dp).toInt(), 1f)
        gravity = Gravity.CENTER
        this.text = text
        when {
            isHeader      -> { textSize = 11f; setTextColor(Color.parseColor("#AAAAAA")); typeface = Typeface.DEFAULT_BOLD }
            isToday       -> {
                textSize = 13f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#3D3DA8"))
                    val r = (17 * dp).toInt(); setSize(r * 2, r * 2)
                }
            }
            isSelected    -> { textSize = 13f; setTextColor(Color.parseColor("#3D3DA8")); typeface = Typeface.DEFAULT_BOLD }
            isOtherMonth  -> { textSize = 12f; setTextColor(Color.parseColor("#CCCCCC")) }
            else          -> { textSize = 13f; setTextColor(Color.parseColor("#333333")) }
        }
        onClick?.let { cb -> setOnClickListener { cb() } }
    }

    // ==================== HELPERS ====================

    private fun getMondayOfWeek(cal: Calendar): Calendar {
        val r = cal.clone() as Calendar
        while (r.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) r.add(Calendar.DAY_OF_MONTH, -1)
        return r
    }

    private fun getAcademicWeekNumber(monday: Calendar): Int {
        val m = monday.get(Calendar.MONTH)
        val y = monday.get(Calendar.YEAR)
        val sy = if (m >= Calendar.SEPTEMBER) y else y - 1
        val start = Calendar.getInstance().apply {
            set(sy, Calendar.SEPTEMBER, 1)
            while (get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) add(Calendar.DAY_OF_MONTH, 1)
        }
        val weeks = ((monday.timeInMillis - start.timeInMillis) / (7L * 24 * 60 * 60 * 1000)).toInt()
        return maxOf(1, weeks + 1)
    }

    private fun isSameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
        a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

    private fun isInWeek(day: Calendar, monday: Calendar): Boolean {
        val d = day.timeInMillis; val m = monday.timeInMillis
        return d >= m && d < m + 7L * 24 * 60 * 60 * 1000
    }

    private fun formatDateFull(cal: Calendar): String {
        val months = arrayOf("января","февраля","марта","апреля","мая","июня",
            "июля","августа","сентября","октября","ноября","декабря")
        return "${cal.get(Calendar.DAY_OF_MONTH)} ${months[cal.get(Calendar.MONTH)]} ${cal.get(Calendar.YEAR)} года"
    }

    // ==================== PARSE ====================

    private fun parseSchedule(group: String): List<Lesson> {
        // Ищем URL группы на timetable.tusur.ru
        val url = findGroupUrl(group)
            ?: "https://timetable.tusur.ru/faculties/fsu/groups/444-1"

        val lessons = mutableListOf<Lesson>()
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(15000)
            .get()

        val timeRegex = Regex("(\\d{1,2}:\\d{2})\\s+(\\d{1,2}:\\d{2})")
        val dayRegex  = Regex(
            "(пн|вт|ср|чт|пт|сб),?\\s*\\d{1,2}\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)",
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
                var curDay = ""
                for (row in table.select("tr")) {
                    val cells = row.select("td")
                    if (cells.isEmpty()) continue
                    val dm = dayRegex.find(row.text().trim())
                    if (dm != null) curDay = formatDay(dm.value)
                    for (i in cells.indices) {
                        val cell = cells[i]; val ct = cell.text().trim()
                        if (ct.length < 5) continue
                        var typeShort = ""; var typeKw = ""
                        for ((kw, sh) in typeKeywords) {
                            if (ct.contains(kw, true)) { typeKw = kw; typeShort = sh; break }
                        }
                        if (typeKw.isEmpty()) continue
                        val tSrc = if (i > 0) cells[i - 1].text() else ct
                        val tm   = timeRegex.find(tSrc)
                        val time = if (tm != null) "${tm.groupValues[1]}\n${tm.groupValues[2]}"
                                   else Regex("(\\d{1,2}:\\d{2})").find(tSrc)?.value ?: "—"
                        val ta = cell.attr("title").trim()
                        var subj = if (ta.isNotEmpty())
                            ta.substringBefore("Вид занятия").trim().replace(idRegex, "").trim()
                        else
                            ct.substringBefore(typeKw).trim().replace(idRegex, "").trim()
                        if (subj.isEmpty()) continue
                        val room    = cell.select("a[href*=auditorium],a[href*=building]").joinToString(", ") { it.text() }
                        val teacher = cell.select("a[href*=teacher]").joinToString(", ") { it.text() }
                        lessons.add(Lesson(time, subj, typeShort, room.ifEmpty { "—" }, teacher.ifEmpty { "—" }, curDay.ifEmpty { "Понедельник" }))
                    }
                }
            }
        }

        if (lessons.isEmpty()) {
            var curDay = ""; var curTime = ""
            for (el in doc.allElements) {
                val t = el.ownText().trim(); if (t.isEmpty()) continue
                val dm = dayRegex.find(t); if (dm != null) { curDay = formatDay(t); continue }
                val tm = timeRegex.find(t); if (tm != null) { curTime = "${tm.groupValues[1]}\n${tm.groupValues[2]}"; continue }
                var ts = ""; var tk = ""
                for ((kw, sh) in typeKeywords) { if (t.contains(kw, true)) { tk = kw; ts = sh; break } }
                if (tk.isEmpty()) continue
                val subj = t.substringBefore(tk).trim().replace(idRegex, "").trim()
                if (subj.isEmpty()) continue
                val room    = el.select("a[href*=auditorium]").joinToString(", ") { it.text() }
                val teacher = el.select("a[href*=teacher]").joinToString(", ") { it.text() }
                lessons.add(Lesson(curTime.ifEmpty { "—" }, subj, ts, room.ifEmpty { "—" }, teacher.ifEmpty { "—" }, curDay.ifEmpty { "Понедельник" }))
            }
        }
        return lessons
    }

    /** Ищет прямую ссылку на группу на timetable.tusur.ru */
    private fun findGroupUrl(group: String): String? {
        if (group.isBlank()) return null
        return try {
            // Пробуем страницу поиска / главную — там есть все группы
            val doc = Jsoup.connect("https://timetable.tusur.ru/")
                .userAgent("Mozilla/5.0 (Linux; Android 10)").timeout(10000).get()
            doc.select("a[href*=/groups/$group]").firstOrNull()?.absUrl("href")
                ?: run {
                    // Если не нашли на главной — пробуем поиск
                    val search = Jsoup.connect("https://timetable.tusur.ru/search?q=$group")
                        .userAgent("Mozilla/5.0 (Linux; Android 10)").timeout(10000).get()
                    search.select("a[href*=/groups/$group]").firstOrNull()?.absUrl("href")
                }
        } catch (_: Exception) { null }
    }

    private fun formatDay(raw: String): String {
        val map = mapOf("пн" to "Понедельник","вт" to "Вторник","ср" to "Среда",
            "чт" to "Четверг","пт" to "Пятница","сб" to "Суббота","вс" to "Воскресенье")
        var r = raw.trim()
        for ((s, f) in map) {
            if (r.lowercase().startsWith(s)) { r = r.replaceFirst(Regex("(?i)$s,?\\s*"), "$f, "); break }
        }
        return r.trim().trimEnd(',').trim()
    }

    // ==================== CACHE ====================

    private fun saveToCache(lessons: List<Lesson>) {
        val arr = JSONArray()
        for (l in lessons) arr.put(JSONObject().apply {
            put("time", l.time); put("subject", l.subject); put("type", l.type)
            put("room", l.room); put("teacher", l.teacher); put("day", l.day)
        })
        requireContext().getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
            .edit().putString("lessons", arr.toString()).apply()
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
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvName: TextView = v.findViewById(R.id.tv_day_item_name)
        val tvDate: TextView = v.findViewById(R.id.tv_day_item_date)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_week_day, p, false))
    override fun onBindViewHolder(h: VH, i: Int) {
        val (name, cal) = days[i]
        h.tvName.text = name
        h.tvDate.text = formatDate(cal)
        h.itemView.setOnClickListener { onClick(name, cal) }
    }
    private fun formatDate(c: Calendar): String {
        val m = arrayOf("января","февраля","марта","апреля","мая","июня",
            "июля","августа","сентября","октября","ноября","декабря")
        return "${c.get(Calendar.DAY_OF_MONTH)} ${m[c.get(Calendar.MONTH)]} ${c.get(Calendar.YEAR)} года"
    }
    override fun getItemCount() = days.size
}

class DayLessonsAdapter(private val lessons: List<Lesson>) :
    RecyclerView.Adapter<DayLessonsAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvSubject:   TextView = v.findViewById(R.id.tv_dl_subject)
        val tvRoom:      TextView = v.findViewById(R.id.tv_dl_room)
        val tvTimeStart: TextView = v.findViewById(R.id.tv_dl_time_start)
        val tvTimeEnd:   TextView = v.findViewById(R.id.tv_dl_time_end)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(R.layout.item_day_lesson, p, false))
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
