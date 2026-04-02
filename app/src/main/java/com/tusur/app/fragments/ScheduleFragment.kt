package com.tusur.app.fragments

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tusur.app.R
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.json.JSONArray
import org.json.JSONObject

data class Lesson(
    val time: String,
    val subject: String,
    val type: String,
    val room: String,
    val teacher: String,
    val day: String
)

class ScheduleFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_schedule)
        val progress = view.findViewById<ProgressBar>(R.id.progress_schedule)
        val tvError = view.findViewById<TextView>(R.id.tv_schedule_error)

        rv.layoutManager = LinearLayoutManager(requireContext())

        scope.launch {
            val cached = loadFromCache()
            if (cached.isNotEmpty()) {
                rv.adapter = ScheduleAdapter(cached)
                tvError.visibility = View.GONE
            }

            progress.visibility = View.VISIBLE
            try {
                val fresh = withContext(Dispatchers.IO) { parseSchedule() }
                progress.visibility = View.GONE
                if (fresh.isNotEmpty()) {
                    saveToCache(fresh)
                    rv.adapter = ScheduleAdapter(fresh)
                    tvError.visibility = View.GONE
                } else if (cached.isEmpty()) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Нет данных"
                }
            } catch (e: Exception) {
                progress.visibility = View.GONE
                if (cached.isEmpty()) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Нет интернета. Данные не загружены."
                }
            }
        }
    }

    private fun parseSchedule(): List<Lesson> {
        val lessons = mutableListOf<Lesson>()
        val doc = Jsoup.connect("https://timetable.tusur.ru/faculties/fsu/groups/444-1")
            .userAgent("Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
            .header("Accept-Language", "ru-RU,ru;q=0.9")
            .timeout(15000)
            .get()

        // Парсим расписание — ищем структуру дней и занятий
        val timeRegex = Regex("(\\d{1,2}:\\d{2})\\s+(\\d{1,2}:\\d{2})")
        val dayRegex = Regex("(пн|вт|ср|чт|пт|сб),?\\s*\\d{1,2}\\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)", RegexOption.IGNORE_CASE)
        val idRegex = Regex("^\\d{4,}\\s*")

        val typeKeywords = listOf(
            "Лабораторная работа" to "Лаб. работа",
            "Лабораторная" to "Лаб. работа",
            "Лекция" to "Лекция",
            "Практика" to "Практика",
            "Курсовое проектирование" to "Курсовой",
            "Курсовое" to "Курсовой",
            "Консультация" to "Консультация",
            "Экзамен" to "Экзамен",
            "Зачёт" to "Зачёт",
            "Зачет" to "Зачёт"
        )

        // Пробуем парсить таблицы
        val tables = doc.select("table")
        if (tables.isNotEmpty()) {
            for (table in tables) {
                var currentDay = ""
                for (row in table.select("tr")) {
                    val cells = row.select("td")
                    if (cells.isEmpty()) continue

                    // Проверяем на заголовок дня
                    val rowText = row.text().trim()
                    val dayMatch = dayRegex.find(rowText)
                    if (dayMatch != null) {
                        currentDay = formatDay(dayMatch.value)
                    }

                    for (i in cells.indices) {
                        val cell = cells[i]
                        val cellText = cell.text().trim()
                        if (cellText.length < 5) continue

                        // Определяем тип занятия
                        var lessonType = ""
                        var lessonTypeShort = ""
                        for ((keyword, short) in typeKeywords) {
                            if (cellText.contains(keyword, ignoreCase = true)) {
                                lessonType = keyword
                                lessonTypeShort = short
                                break
                            }
                        }
                        if (lessonType.isEmpty()) continue

                        // Извлекаем время
                        var timeStart = ""
                        var timeEnd = ""
                        // Ищем время в предыдущей ячейке или в текущей
                        val timeSource = if (i > 0) cells[i - 1].text() else cellText
                        val timeMatch = timeRegex.find(timeSource)
                        if (timeMatch != null) {
                            timeStart = timeMatch.groupValues[1]
                            timeEnd = timeMatch.groupValues[2]
                        } else {
                            // Пробуем найти одиночное время
                            val singleTime = Regex("(\\d{1,2}:\\d{2})").find(timeSource)
                            if (singleTime != null) timeStart = singleTime.value
                        }

                        // Извлекаем предмет — из title или из текста
                        var subject = ""
                        val titleAttr = cell.attr("title").trim()
                        if (titleAttr.isNotEmpty()) {
                            subject = titleAttr
                                .substringBefore("Вид занятия").trim()
                                .replace(idRegex, "").trim()
                        }
                        if (subject.isEmpty()) {
                            subject = cellText
                                .substringBefore(lessonType).trim()
                                .replace(idRegex, "").trim()
                        }
                        // Убираем ID из начала
                        subject = subject.replace(idRegex, "").trim()
                        if (subject.isEmpty()) continue

                        // Извлекаем аудиторию и преподавателя
                        val room = cell.select("a[href*=auditorium], a[href*=building]")
                            .joinToString(", ") { it.text().trim() }
                        val teacher = cell.select("a[href*=teacher]")
                            .joinToString(", ") { it.text().trim() }

                        if (currentDay.isEmpty()) currentDay = "Текущая неделя"

                        lessons.add(Lesson(
                            time = if (timeEnd.isNotEmpty()) "$timeStart\n$timeEnd" else timeStart,
                            subject = subject,
                            type = lessonTypeShort,
                            room = room.ifEmpty { "—" },
                            teacher = teacher.ifEmpty { "—" },
                            day = currentDay
                        ))
                    }
                }
            }
        }

        // Если таблиц нет — парсим div-структуру
        if (lessons.isEmpty()) {
            var currentDay = ""
            var currentTime = ""

            for (el in doc.allElements) {
                val text = el.ownText().trim()
                if (text.isEmpty()) continue

                val dayMatch = dayRegex.find(text)
                if (dayMatch != null) {
                    currentDay = formatDay(text)
                    continue
                }

                val timeMatch = timeRegex.find(text)
                if (timeMatch != null) {
                    currentTime = "${timeMatch.groupValues[1]}\n${timeMatch.groupValues[2]}"
                    continue
                }

                var lessonTypeShort = ""
                var lessonType = ""
                for ((keyword, short) in typeKeywords) {
                    if (text.contains(keyword, ignoreCase = true)) {
                        lessonType = keyword
                        lessonTypeShort = short
                        break
                    }
                }
                if (lessonType.isEmpty()) continue

                val subject = text
                    .substringBefore(lessonType).trim()
                    .replace(idRegex, "").trim()
                if (subject.isEmpty()) continue

                val room = el.select("a[href*=auditorium], a[href*=building]")
                    .joinToString(", ") { it.text().trim() }
                val teacher = el.select("a[href*=teacher]")
                    .joinToString(", ") { it.text().trim() }

                lessons.add(Lesson(
                    time = currentTime.ifEmpty { "—" },
                    subject = subject,
                    type = lessonTypeShort,
                    room = room.ifEmpty { "—" },
                    teacher = teacher.ifEmpty { "—" },
                    day = currentDay.ifEmpty { "Текущая неделя" }
                ))
            }
        }

        return lessons
    }

    private fun formatDay(raw: String): String {
        val cleaned = raw.trim()
        // Преобразуем "пн, 24 марта" → "Понедельник, 24 марта"
        val dayNames = mapOf(
            "пн" to "Понедельник",
            "вт" to "Вторник",
            "ср" to "Среда",
            "чт" to "Четверг",
            "пт" to "Пятница",
            "сб" to "Суббота",
            "вс" to "Воскресенье"
        )
        var result = cleaned
        for ((short, full) in dayNames) {
            if (result.lowercase().startsWith(short)) {
                result = result.replaceFirst(Regex("(?i)$short,?\\s*"), "$full, ")
                break
            }
        }
        return result.trim().trimEnd(',').trim()
    }

    // --- Кэш ---

    private fun saveToCache(lessons: List<Lesson>) {
        val prefs = requireContext().getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val array = JSONArray()
        for (lesson in lessons) {
            val obj = JSONObject()
            obj.put("time", lesson.time)
            obj.put("subject", lesson.subject)
            obj.put("type", lesson.type)
            obj.put("room", lesson.room)
            obj.put("teacher", lesson.teacher)
            obj.put("day", lesson.day)
            array.put(obj)
        }
        prefs.edit().putString("lessons", array.toString()).apply()
    }

    private fun loadFromCache(): List<Lesson> {
        val prefs = requireContext().getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("lessons", null) ?: return emptyList()
        return try {
            val array = JSONArray(json)
            val lessons = mutableListOf<Lesson>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                lessons.add(Lesson(
                    time = obj.getString("time"),
                    subject = obj.getString("subject"),
                    type = obj.getString("type"),
                    room = obj.getString("room"),
                    teacher = obj.getString("teacher"),
                    day = obj.getString("day")
                ))
            }
            lessons
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }
}

class ScheduleAdapter(private val items: List<Lesson>) :
    RecyclerView.Adapter<ScheduleAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvDay: TextView = view.findViewById(R.id.tv_lesson_day)
        val tvTimeStart: TextView = view.findViewById(R.id.tv_lesson_time_start)
        val tvTimeEnd: TextView = view.findViewById(R.id.tv_lesson_time_end)
        val tvSubject: TextView = view.findViewById(R.id.tv_lesson_subject)
        val tvType: TextView = view.findViewById(R.id.tv_lesson_type)
        val tvRoom: TextView = view.findViewById(R.id.tv_lesson_room)
        val tvTeacher: TextView = view.findViewById(R.id.tv_lesson_teacher)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_lesson, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = items[i]

        // Показываем заголовок дня только для первого элемента или при смене дня
        if (i == 0 || items[i - 1].day != item.day) {
            h.tvDay.text = item.day
            h.tvDay.visibility = View.VISIBLE
        } else {
            h.tvDay.visibility = View.GONE
        }

        // Время — разделяем start/end
        val timeParts = item.time.split("\n")
        h.tvTimeStart.text = timeParts.getOrElse(0) { "" }
        h.tvTimeEnd.text = timeParts.getOrElse(1) { "" }

        h.tvSubject.text = item.subject
        h.tvType.text = item.type
        h.tvRoom.text = item.room
        h.tvTeacher.text = item.teacher
    }

    override fun getItemCount() = items.size
}
