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
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate
import com.tusur.app.CourseContentActivity

data class SdoCourse(val name: String, val url: String, val shortname: String)

class SdoFragment : Fragment() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sdo, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rv_courses)
        val progress = view.findViewById<ProgressBar>(R.id.progress_sdo)
        val tvError = view.findViewById<TextView>(R.id.tv_sdo_error)

        rv.layoutManager = LinearLayoutManager(requireContext())

        val session = SessionManager(requireContext())

        // Показываем кэш сразу
        val cached = loadFromCache()
        if (cached.isNotEmpty()) {
            rv.adapter = SdoCourseAdapter(cached) { openCourse(it) }
            tvError.visibility = View.GONE
        }

        if (session.email.isNotEmpty() && session.password.isNotEmpty()) {
            progress.visibility = View.VISIBLE
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    fetchCourses(session.email, session.password)
                }
                progress.visibility = View.GONE

                if (result.courses.isNotEmpty()) {
                    saveToCache(result.courses)
                    if (result.cookies.isNotEmpty()) saveCookies(result.cookies)
                    rv.adapter = SdoCourseAdapter(result.courses) { openCourse(it) }
                    tvError.visibility = View.GONE
                } else if (cached.isEmpty()) {
                    tvError.text = result.debug.ifEmpty { "Курсы не найдены" }
                    tvError.visibility = View.VISIBLE
                }
            }
        } else if (cached.isEmpty()) {
            tvError.text = "Перезайдите в приложение для загрузки курсов"
            tvError.visibility = View.VISIBLE
        }
    }

    private data class FetchResult(
        val courses: List<SdoCourse>,
        val cookies: Map<String, String>,
        val debug: String
    )

    private fun fetchCourses(email: String, password: String): FetchResult {
        val ssl = buildTrustAllSsl()
        val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        val allCookies = HashMap<String, String>()

        try {
            // ===== 1. SSO LOGIN =====
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
                return FetchResult(emptyList(), emptyMap(), "Ошибка авторизации SSO")
            }

            // Следуем по редиректам, собирая cookies
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

            // ===== 2. ПОЛУЧАЕМ sesskey и userid =====
            val dashResp = Jsoup.connect("https://sdo.tusur.ru/my/")
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(allCookies)
                .followRedirects(true)
                .ignoreHttpErrors(true).execute()
            allCookies.putAll(dashResp.cookies())
            val dashDoc = dashResp.parse()

            val finalUrl = dashResp.url().toString()
            if (finalUrl.contains("login") || dashDoc.title().contains("Вход", ignoreCase = true)) {
                return FetchResult(emptyList(), allCookies, "Не удалось войти в СДО")
            }

            // Извлекаем sesskey из JS или формы
            val sesskey = extractSesskey(dashDoc.html())
            // Извлекаем userid
            val userid = extractUserId(dashDoc.html())

            val courses = mutableListOf<SdoCourse>()

            // ===== 3. СПОСОБ 1: Moodle AJAX API =====
            if (sesskey.isNotEmpty()) {
                val ajaxCourses = fetchCoursesViaAjax(allCookies, sesskey, ssl, ua)
                if (ajaxCourses.isNotEmpty()) {
                    courses.addAll(ajaxCourses)
                    return FetchResult(courses, allCookies, "")
                }
            }

            // ===== 4. СПОСОБ 2: Web Service API (если есть token) =====
            if (userid.isNotEmpty()) {
                val wsCourses = fetchCoursesViaWS(allCookies, userid, sesskey, ssl, ua)
                if (wsCourses.isNotEmpty()) {
                    courses.addAll(wsCourses)
                    return FetchResult(courses, allCookies, "")
                }
            }

            // ===== 5. СПОСОБ 3: HTML парсинг /my/courses.php =====
            val htmlCourses = fetchCoursesViaHtml(allCookies, ssl, ua)
            if (htmlCourses.isNotEmpty()) {
                courses.addAll(htmlCourses)
                return FetchResult(courses, allCookies, "")
            }

            // ===== 6. СПОСОБ 4: Парсинг dashboard =====
            val dashCourses = parseCoursesFromDoc(dashDoc)
            if (dashCourses.isNotEmpty()) {
                courses.addAll(dashCourses)
            }

            return FetchResult(courses, allCookies, if (courses.isEmpty()) "Курсы не найдены" else "")

        } catch (e: Exception) {
            return FetchResult(emptyList(), emptyMap(), "Ошибка: ${e.message}")
        }
    }

    /**
     * Извлекает sesskey из HTML/JS страницы Moodle
     */
    private fun extractSesskey(html: String): String {
        // Ищем в JS: "sesskey":"XXXXX"
        val jsMatch = Regex(""""sesskey"\s*:\s*"([a-zA-Z0-9]+)"""").find(html)
        if (jsMatch != null) return jsMatch.groupValues[1]

        // Ищем в форме: <input name="sesskey" value="XXXXX">
        val formMatch = Regex("""name="sesskey"[^>]*value="([a-zA-Z0-9]+)"""").find(html)
        if (formMatch != null) return formMatch.groupValues[1]

        // Ищем sesskey= в URL
        val urlMatch = Regex("""sesskey=([a-zA-Z0-9]+)""").find(html)
        if (urlMatch != null) return urlMatch.groupValues[1]

        return ""
    }

    /**
     * Извлекает user ID из HTML/JS Moodle
     */
    private fun extractUserId(html: String): String {
        val match = Regex(""""userid"\s*:\s*(\d+)""").find(html)
            ?: Regex("""data-userid="(\d+)"""").find(html)
            ?: Regex(""""id"\s*:\s*(\d+).*?"userid"""").find(html)
        return match?.groupValues?.get(1) ?: ""
    }

    /**
     * Способ 1: Moodle AJAX API — core_course_get_enrolled_courses_by_timeline_classification
     */
    private fun fetchCoursesViaAjax(
        cookies: Map<String, String>, sesskey: String, ssl: SSLContext, ua: String
    ): List<SdoCourse> {
        try {
            val url = URL("https://sdo.tusur.ru/lib/ajax/service.php?sesskey=$sesskey&info=core_course_get_enrolled_courses_by_timeline_classification")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = ssl.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
            }

            val body = """[{"index":0,"methodname":"core_course_get_enrolled_courses_by_timeline_classification","args":{"offset":0,"limit":0,"classification":"all","sort":"fullname","customfieldname":"","customfieldvalue":""}}]"""
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(response)
            if (arr.length() == 0) return emptyList()

            val data = arr.getJSONObject(0)
            if (data.has("error") && data.getBoolean("error")) return emptyList()

            val coursesData = data.optJSONObject("data")?.optJSONArray("courses") ?: return emptyList()

            val courses = mutableListOf<SdoCourse>()
            for (i in 0 until coursesData.length()) {
                val c = coursesData.getJSONObject(i)
                val fullname = c.optString("fullname", "").trim()
                val viewurl = c.optString("viewurl", "")
                val shortname = c.optString("shortname", "")

                if (fullname.isNotEmpty()) {
                    courses.add(SdoCourse(fullname, viewurl, shortname))
                }
            }
            return courses
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Способ 2: Moodle Web Service — core_enrol_get_users_courses
     */
    private fun fetchCoursesViaWS(
        cookies: Map<String, String>, userid: String, sesskey: String, ssl: SSLContext, ua: String
    ): List<SdoCourse> {
        try {
            // Пробуем AJAX вызов core_enrol_get_users_courses
            val url = URL("https://sdo.tusur.ru/lib/ajax/service.php?sesskey=$sesskey&info=core_enrol_get_users_courses")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = ssl.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
            }

            val body = """[{"index":0,"methodname":"core_enrol_get_users_courses","args":{"userid":$userid,"returnusercount":0}}]"""
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(response)
            if (arr.length() == 0) return emptyList()

            val data = arr.getJSONObject(0)
            if (data.has("error") && data.getBoolean("error")) return emptyList()

            // Ответ — массив курсов в поле "data" (или напрямую)
            val coursesArr = if (data.has("data")) {
                val d = data.get("data")
                if (d is JSONArray) d else null
            } else null

            if (coursesArr == null) return emptyList()

            val courses = mutableListOf<SdoCourse>()
            for (i in 0 until coursesArr.length()) {
                val c = coursesArr.getJSONObject(i)
                val fullname = c.optString("fullname", "").trim()
                val id = c.optInt("id", 0)
                val shortname = c.optString("shortname", "")
                if (fullname.isNotEmpty()) {
                    courses.add(SdoCourse(
                        fullname,
                        "https://sdo.tusur.ru/course/view.php?id=$id",
                        shortname
                    ))
                }
            }
            return courses
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Способ 3: HTML парсинг /my/courses.php
     */
    private fun fetchCoursesViaHtml(
        cookies: Map<String, String>, ssl: SSLContext, ua: String
    ): List<SdoCourse> {
        try {
            val resp = Jsoup.connect("https://sdo.tusur.ru/my/courses.php")
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(cookies)
                .followRedirects(true)
                .ignoreHttpErrors(true).execute()

            val doc = resp.parse()
            return parseCoursesFromDoc(doc)
        } catch (e: Exception) {
            return emptyList()
        }
    }

    /**
     * Парсит курсы из HTML документа Moodle
     */
    private fun parseCoursesFromDoc(doc: org.jsoup.nodes.Document): List<SdoCourse> {
        val courses = mutableListOf<SdoCourse>()
        val seen = mutableSetOf<String>()

        // Селектор 1: Блоки курсов (coursebox)
        doc.select("div.coursebox h3.coursename a.aalink, div.coursebox .coursename a").forEach { el ->
            val name = el.text().trim()
            val href = el.attr("abs:href")
            if (name.length > 2 && href.contains("course/view.php") && seen.add(name)) {
                courses.add(SdoCourse(name, href, ""))
            }
        }
        if (courses.isNotEmpty()) return courses

        // Селектор 2: Course overview cards (Moodle 4.x)
        doc.select("div[data-region=course-content] .coursename a, .course-listitem .coursename a").forEach { el ->
            val name = el.text().trim()
            val href = el.attr("abs:href")
            if (name.length > 2 && seen.add(name)) {
                courses.add(SdoCourse(name, href, ""))
            }
        }
        if (courses.isNotEmpty()) return courses

        // Селектор 3: Карточки курсов (card)
        doc.select(".card .multiline .coursename, .card-title a[href*=course/view]").forEach { el ->
            val link = if (el.tagName() == "a") el else el.selectFirst("a") ?: return@forEach
            val name = link.text().trim()
            val href = link.attr("abs:href")
            if (name.length > 2 && seen.add(name)) {
                courses.add(SdoCourse(name, href, ""))
            }
        }
        if (courses.isNotEmpty()) return courses

        // Селектор 4: Только из основного контента (не sidebar)
        doc.select("#region-main a[href*=course/view.php], [role=main] a[href*=course/view.php], #page-content a[href*=course/view.php]").forEach { el ->
            val name = el.text().trim()
            val href = el.attr("abs:href")
            // Фильтруем мусор: короткие, дубли, навигационные ссылки
            if (name.length > 3 && !name.startsWith("Скрыть") && !name.startsWith("Свернуть")
                && !name.contains("Все курсы") && seen.add(name)) {
                courses.add(SdoCourse(name, href, ""))
            }
        }

        return courses
    }

    private fun buildTrustAllSsl(): SSLContext {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun checkServerTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        })
        return SSLContext.getInstance("TLS").apply { init(null, tm, java.security.SecureRandom()) }
    }

    private fun saveCookies(cookies: Map<String, String>) {
        val prefs = requireContext().getSharedPreferences("sdo_session", Context.MODE_PRIVATE)
        val editor = prefs.edit().clear()
        cookies.forEach { (k, v) -> editor.putString("c_$k", v) }
        editor.apply()
    }

    private fun saveToCache(courses: List<SdoCourse>) {
        val prefs = requireContext().getSharedPreferences("sdo_cache", Context.MODE_PRIVATE)
        val arr = JSONArray()
        courses.forEach {
            val o = JSONObject()
            o.put("name", it.name)
            o.put("url", it.url)
            o.put("shortname", it.shortname)
            arr.put(o)
        }
        prefs.edit().putString("courses", arr.toString()).apply()
    }

    private fun loadFromCache(): List<SdoCourse> {
        val prefs = requireContext().getSharedPreferences("sdo_cache", Context.MODE_PRIVATE)
        val json = prefs.getString("courses", null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                SdoCourse(
                    o.optString("name", ""),
                    o.optString("url", ""),
                    o.optString("shortname", "")
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun openCourse(course: SdoCourse) {
        val url = course.url.ifEmpty { "https://sdo.tusur.ru" }
        val idMatch = Regex("id=(\\d+)").find(url)
        val courseId = idMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
        CourseContentActivity.start(requireContext(), course.name, url, courseId)
    }

    override fun onDestroyView() { scope.cancel(); super.onDestroyView() }
}

class SdoCourseAdapter(
    private val items: List<SdoCourse>,
    private val onCourseClick: (SdoCourse) -> Unit
) : RecyclerView.Adapter<SdoCourseAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_course_name)
        val tvCategory: TextView = view.findViewById(R.id.tv_course_category)
        val tvArrow: TextView = view.findViewById(R.id.tv_course_arrow)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_sdo_course, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val course = items[i]
        h.tvName.text = course.name
        h.tvCategory.text = course.shortname.ifEmpty { "Курс" }
        h.itemView.setOnClickListener { onCourseClick(course) }
    }

    override fun getItemCount() = items.size
}
