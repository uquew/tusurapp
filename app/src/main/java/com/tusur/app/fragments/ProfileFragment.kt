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
import com.tusur.app.SessionManager
import com.tusur.app.adapters.GradeAdapter
import com.tusur.app.adapters.GradeItem
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup

class ProfileFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_profile, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv         = view.findViewById<RecyclerView>(R.id.rv_grades)
        val progress   = view.findViewById<ProgressBar>(R.id.progress_grades)
        val tvStatus   = view.findViewById<TextView>(R.id.tv_grades_status)
        val tvAvg      = view.findViewById<TextView>(R.id.tv_avg_score)
        val tvInitials = view.findViewById<TextView>(R.id.tv_initials)

        rv.layoutManager = LinearLayoutManager(requireContext())

        val session = SessionManager(requireContext())

        // Показываем кэш сразу
        val cached = loadFromCache()
        if (cached.isNotEmpty()) {
            rv.adapter = GradeAdapter(cached)
            updateAvgScore(tvAvg, cached)
        }

        // Загружаем оценки через SSO → SDO Moodle
        if (session.email.isNotEmpty() && session.password.isNotEmpty()) {
            progress.visibility = View.VISIBLE
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    fetchGrades(session.email, session.password)
                }
                progress.visibility = View.GONE

                if (result.grades.isNotEmpty()) {
                    saveToCache(result.grades)
                    rv.adapter = GradeAdapter(result.grades)
                    updateAvgScore(tvAvg, result.grades)
                    tvStatus.visibility = View.GONE

                    if (result.userName.isNotEmpty()) {
                        val parts = result.userName.split(" ")
                        val initials = parts.take(2).mapNotNull { s ->
                            s.firstOrNull()?.uppercase()
                        }.joinToString("")
                        if (initials.isNotEmpty()) tvInitials.text = initials
                    }
                } else if (cached.isEmpty()) {
                    tvStatus.text = result.debug
                    tvStatus.visibility = View.VISIBLE
                }
            }
        } else if (cached.isEmpty()) {
            tvStatus.text = "Перезайдите в приложение для загрузки оценок"
            tvStatus.visibility = View.VISIBLE
        }
    }

    private fun updateAvgScore(tvAvg: TextView, grades: List<GradeItem>) {
        val scores = grades.flatMap { listOf(it.kt1, it.kt2, it.ekz) }
            .mapNotNull { it.toDoubleOrNull() }
        if (scores.isNotEmpty()) {
            val avg = scores.sum() / scores.size
            tvAvg.text = String.format("%.1f", avg).replace(".", ",")
        }
    }

    data class FetchResult(val grades: List<GradeItem>, val userName: String, val debug: String)

    private fun fetchGrades(email: String, password: String): FetchResult {
        val ssl = buildTrustAllSsl()
        val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        val allCookies = HashMap<String, String>()
        val debug = StringBuilder()

        try {
            // 1. SSO login → sdo.tusur.ru (тот же SSO что и для курсов)
            val ssoUrl = "https://profile.tusur.ru/users/sign_in?redirect_url=https%3A%2F%2Fsdo.tusur.ru%2Fauth%2Fedu%2F%3Fid%3D1"

            val getResp = Jsoup.connect(ssoUrl)
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .followRedirects(true).execute()
            allCookies.putAll(getResp.cookies())

            val csrf = getResp.parse()
                .selectFirst("input[name=authenticity_token]")?.attr("value") ?: ""

            val postResp = Jsoup.connect(getResp.url().toString())
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(allCookies)
                .data("authenticity_token", csrf)
                .data("user[email]", email)
                .data("user[password]", password)
                .data("user[remember_me]", "1")
                .method(Connection.Method.POST)
                .followRedirects(false).execute()
            allCookies.putAll(postResp.cookies())

            var location = postResp.header("Location") ?: ""
            if (postResp.statusCode() !in 301..303 || location.contains("sign_in")) {
                debug.append("SSO не удался")
                return FetchResult(emptyList(), "", debug.toString())
            }

            var redirects = 0
            while (location.isNotEmpty() && redirects < 15) {
                val nextUrl = if (location.startsWith("http")) location
                              else "https://profile.tusur.ru$location"
                val r = Jsoup.connect(nextUrl)
                    .userAgent(ua).timeout(20_000)
                    .sslSocketFactory(ssl.socketFactory)
                    .cookies(allCookies)
                    .followRedirects(false)
                    .ignoreHttpErrors(true).execute()
                allCookies.putAll(r.cookies())
                location = r.header("Location") ?: ""
                if (r.statusCode() !in 301..303) break
                redirects++
            }

            debug.append("SSO ok\n")

            // 2. Получаем имя пользователя со страницы SDO
            var userName = ""
            try {
                val myResp = Jsoup.connect("https://sdo.tusur.ru/my/")
                    .userAgent(ua).timeout(15_000)
                    .sslSocketFactory(ssl.socketFactory)
                    .cookies(allCookies)
                    .followRedirects(true)
                    .ignoreHttpErrors(true).execute()
                allCookies.putAll(myResp.cookies())
                val myDoc = myResp.parse()

                // Ищем имя в разных местах Moodle
                userName = myDoc.select("span.usertext, .userbutton .usertext, a[title*=profile]")
                    .toList()
                    .map { it.text().trim() }
                    .firstOrNull { it.length in 3..60 } ?: ""

                if (userName.isEmpty()) {
                    userName = myDoc.select("a[href*=user/profile.php]")
                        .toList()
                        .map { it.text().trim() }
                        .firstOrNull { it.length in 3..60 && !it.contains("http") } ?: ""
                }
            } catch (e: Exception) {
                debug.append("user name: ${e.javaClass.simpleName}\n")
            }

            // 3. Загружаем страницу оценок Moodle
            val grades = mutableListOf<GradeItem>()

            // Пробуем разные страницы оценок Moodle
            val gradePages = listOf(
                "https://sdo.tusur.ru/grade/report/overview/index.php",
                "https://sdo.tusur.ru/grade/report/user/index.php"
            )

            for (gradePage in gradePages) {
                if (grades.isNotEmpty()) break
                try {
                    val gradeResp = Jsoup.connect(gradePage)
                        .userAgent(ua).timeout(20_000)
                        .sslSocketFactory(ssl.socketFactory)
                        .cookies(allCookies)
                        .followRedirects(true)
                        .ignoreHttpErrors(true).execute()
                    allCookies.putAll(gradeResp.cookies())
                    val gradeDoc = gradeResp.parse()
                    val gradeUrl = gradeResp.url().toString()
                    debug.append("grades: $gradeUrl\n")

                    if (gradeUrl.contains("login")) {
                        debug.append("не авторизован\n")
                        continue
                    }

                    // Парсим таблицу оценок Moodle
                    val tables = gradeDoc.select("table")
                    debug.append("tables: ${tables.size}\n")

                    for (table in tables) {
                        val rows = table.select("tr")
                        for (row in rows) {
                            val cells = row.select("td, th")
                            if (cells.size < 2) continue

                            val firstCell = cells[0].text().trim()
                            // Пропускаем заголовки
                            if (firstCell.isEmpty() || firstCell == "Курс" || firstCell == "Course"
                                || firstCell == "Оценка" || firstCell == "Grade") continue

                            // overview/index.php: Курс | Оценка
                            if (cells.size >= 2) {
                                val courseName = firstCell
                                if (courseName.length < 3) continue
                                val gradeText = cells[1].text().trim()

                                // Проверяем что это не заголовок таблицы
                                if (gradeText == "Итого" || gradeText == "Total") continue

                                if (gradeText.isNotEmpty() && gradeText != "-") {
                                    grades.add(GradeItem(
                                        courseName,
                                        gradeText,
                                        "—",
                                        "—"
                                    ))
                                }
                            }
                        }
                    }

                    // Альтернативный парсинг: ищем generaltable
                    if (grades.isEmpty()) {
                        val genTable = gradeDoc.selectFirst("table.generaltable, table.boxaligncenter")
                        if (genTable != null) {
                            for (row in genTable.select("tr")) {
                                val cells = row.select("td")
                                if (cells.size < 2) continue
                                val name = cells[0].text().trim()
                                if (name.length < 3) continue
                                val grade = cells.last()?.text()?.trim() ?: "—"
                                grades.add(GradeItem(name, grade, "—", "—"))
                            }
                        }
                    }

                    debug.append("grades found: ${grades.size}\n")
                } catch (e: Exception) {
                    debug.append("$gradePage → ${e.javaClass.simpleName}: ${e.message}\n")
                }
            }

            // 4. Также пробуем ocenka.tusur.ru (SSO)
            if (grades.isEmpty()) {
                try {
                    val ocenkaLogin = "https://profile.tusur.ru/users/sign_in?redirect_url=http%3A%2F%2Focenka.tusur.ru%2F"

                    val ocenkaGet = Jsoup.connect(ocenkaLogin)
                        .userAgent(ua).timeout(20_000)
                        .sslSocketFactory(ssl.socketFactory)
                        .cookies(allCookies)
                        .followRedirects(true).execute()
                    allCookies.putAll(ocenkaGet.cookies())

                    // Если уже авторизованы — нас перенаправит на ocenka.tusur.ru
                    val ocenkaUrl = ocenkaGet.url().toString()
                    debug.append("ocenka: $ocenkaUrl\n")

                    if (ocenkaUrl.contains("ocenka")) {
                        // Уже на ocenka — парсим HTML таблицы
                        val htmlPages = listOf(
                            "https://ocenka.tusur.ru/student",
                            "https://ocenka.tusur.ru/student/marks",
                            "https://ocenka.tusur.ru/student/progress"
                        )
                        for (page in htmlPages) {
                            if (grades.isNotEmpty()) break
                            try {
                                val doc = Jsoup.connect(page)
                                    .userAgent(ua).timeout(15_000)
                                    .sslSocketFactory(ssl.socketFactory)
                                    .cookies(allCookies)
                                    .followRedirects(true)
                                    .ignoreHttpErrors(true).get()
                                for (table in doc.select("table")) {
                                    for (row in table.select("tr")) {
                                        val cells = row.select("td")
                                        if (cells.size < 2) continue
                                        val subject = cells[0].text().trim()
                                        if (subject.length < 3) continue
                                        val values = cells.toList().drop(1).map { it.text().trim() }
                                        if (values.any { it.matches(Regex("\\d+")) }) {
                                            grades.add(GradeItem(
                                                subject,
                                                values.getOrElse(0) { "—" },
                                                values.getOrElse(1) { "—" },
                                                values.getOrElse(2) { "—" }
                                            ))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                debug.append("$page → ${e.message}\n")
                            }
                        }
                    }
                } catch (e: Exception) {
                    debug.append("ocenka fallback → ${e.javaClass.simpleName}\n")
                }
            }

            if (grades.isEmpty()) {
                debug.append("Оценки не найдены")
            }

            return FetchResult(grades, userName, debug.toString())
        } catch (e: Exception) {
            return FetchResult(emptyList(), "", "Ошибка: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun buildTrustAllSsl(): javax.net.ssl.SSLContext {
        val tm = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
            override fun checkClientTrusted(c: Array<java.security.cert.X509Certificate>, t: String) = Unit
            override fun checkServerTrusted(c: Array<java.security.cert.X509Certificate>, t: String) = Unit
            override fun getAcceptedIssuers() = emptyArray<java.security.cert.X509Certificate>()
        })
        return javax.net.ssl.SSLContext.getInstance("TLS").apply { init(null, tm, java.security.SecureRandom()) }
    }

    // --- Кэш ---
    private fun saveToCache(grades: List<GradeItem>) {
        val prefs = requireContext().getSharedPreferences("grades_cache", Context.MODE_PRIVATE)
        val arr = JSONArray()
        grades.forEach { g ->
            val o = JSONObject()
            o.put("name", g.subjectName); o.put("kt1", g.kt1)
            o.put("kt2", g.kt2); o.put("ekz", g.ekz)
            arr.put(o)
        }
        prefs.edit().putString("grades", arr.toString()).apply()
    }

    private fun loadFromCache(): List<GradeItem> {
        val prefs = requireContext().getSharedPreferences("grades_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("grades", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GradeItem(o.getString("name"), o.getString("kt1"), o.getString("kt2"), o.getString("ekz"))
            }
        } catch (e: Exception) { emptyList() }
    }

    override fun onDestroyView() { scope.cancel(); super.onDestroyView() }
}
