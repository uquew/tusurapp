package com.tusur.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class CourseContentActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    companion object {
        fun start(context: Context, courseName: String, courseUrl: String, courseId: Int) {
            val intent = Intent(context, CourseContentActivity::class.java).apply {
                putExtra("course_name", courseName)
                putExtra("course_url", courseUrl)
                putExtra("course_id", courseId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_course_content)

        val courseName = intent.getStringExtra("course_name") ?: "Курс"
        val courseUrl = intent.getStringExtra("course_url") ?: ""
        val courseId = intent.getIntExtra("course_id", 0)

        val tvTitle = findViewById<TextView>(R.id.tv_course_title)
        val tvBack = findViewById<TextView>(R.id.tv_back)
        val progress = findViewById<ProgressBar>(R.id.progress_course)
        val rv = findViewById<RecyclerView>(R.id.rv_course_content)
        val tvError = findViewById<TextView>(R.id.tv_course_error)
        val webView = findViewById<WebView>(R.id.webview_module)
        val webContainer = findViewById<View>(R.id.web_container)
        val tvWebBack = findViewById<TextView>(R.id.tv_web_back)
        val tvWebTitle = findViewById<TextView>(R.id.tv_web_title)

        tvTitle.text = courseName
        rv.layoutManager = LinearLayoutManager(this)

        tvBack.setOnClickListener { finish() }

        // Извлекаем courseId из URL если не передан
        val resolvedId = if (courseId > 0) courseId else {
            val match = Regex("id=(\\d+)").find(courseUrl)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        val session = SessionManager(this)

        // Настраиваем WebView для модулей
        setupWebView(webView)
        tvWebBack.setOnClickListener {
            webContainer.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }

        progress.visibility = View.VISIBLE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchCourseContent(session.email, session.password, resolvedId, courseUrl)
            }
            progress.visibility = View.GONE

            if (result.sections.isNotEmpty()) {
                rv.adapter = CourseContentAdapter(result.sections, result.cookies) { module ->
                    // Открываем модуль
                    openModule(module, result.cookies, webView, webContainer, tvWebTitle, rv)
                }
                tvError.visibility = View.GONE
            } else {
                tvError.text = result.debug.ifEmpty { "Не удалось загрузить содержимое курса" }
                tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun setupWebView(webView: WebView) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
    }

    private fun openModule(
        module: CourseModule,
        cookies: Map<String, String>,
        webView: WebView,
        webContainer: View,
        tvWebTitle: TextView,
        rv: RecyclerView
    ) {
        val url = module.url
        if (url.isEmpty()) return

        // Для файлов — открываем в браузере (скачивание)
        if (module.modname == "resource" || module.modname == "folder") {
            // Загружаем через WebView внутри приложения с cookies
            tvWebTitle.text = module.name
            rv.visibility = View.GONE
            webContainer.visibility = View.VISIBLE

            // Устанавливаем cookies для WebView
            val cookieManager = android.webkit.CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookies.forEach { (k, v) ->
                cookieManager.setCookie("https://sdo.tusur.ru", "$k=$v")
            }
            webView.loadUrl(url)
            return
        }

        // Для страниц, заданий, тестов — открываем в WebView с cookies
        tvWebTitle.text = module.name
        rv.visibility = View.GONE
        webContainer.visibility = View.VISIBLE

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookies.forEach { (k, v) ->
            cookieManager.setCookie("https://sdo.tusur.ru", "$k=$v")
        }
        webView.loadUrl(url)
    }

    override fun onBackPressed() {
        val webContainer = findViewById<View>(R.id.web_container)
        val rv = findViewById<RecyclerView>(R.id.rv_course_content)
        val webView = findViewById<WebView>(R.id.webview_module)

        if (webContainer.visibility == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                webContainer.visibility = View.GONE
                rv.visibility = View.VISIBLE
            }
        } else {
            super.onBackPressed()
        }
    }

    // ===== DATA =====

    data class CourseSection(
        val name: String,
        val summary: String,
        val modules: List<CourseModule>
    )

    data class CourseModule(
        val name: String,
        val modname: String, // assign, quiz, resource, url, forum, page, label...
        val url: String,
        val description: String
    )

    data class ContentResult(
        val sections: List<CourseSection>,
        val cookies: Map<String, String>,
        val debug: String
    )

    // ===== FETCH =====

    private fun fetchCourseContent(
        email: String, password: String, courseId: Int, courseUrl: String
    ): ContentResult {
        val ssl = buildTrustAllSsl()
        val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        val allCookies = HashMap<String, String>()

        try {
            // 1. SSO login
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
                return ContentResult(emptyList(), emptyMap(), "Ошибка авторизации")
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

            // 2. Получаем sesskey
            val dashResp = Jsoup.connect("https://sdo.tusur.ru/my/")
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(allCookies)
                .followRedirects(true).execute()
            allCookies.putAll(dashResp.cookies())
            val dashHtml = dashResp.parse().html()

            val sesskey = extractSesskey(dashHtml)
            if (sesskey.isEmpty()) {
                return ContentResult(emptyList(), allCookies, "Не удалось получить сессию")
            }

            // 3. Пробуем Moodle AJAX API: core_course_get_contents
            if (courseId > 0) {
                val apiSections = fetchViaApi(allCookies, sesskey, courseId, ssl, ua)
                if (apiSections.isNotEmpty()) {
                    return ContentResult(apiSections, allCookies, "")
                }
            }

            // 4. Fallback: парсим HTML страницу курса
            val targetUrl = if (courseUrl.isNotEmpty()) courseUrl
                else "https://sdo.tusur.ru/course/view.php?id=$courseId"

            val htmlSections = fetchViaHtml(allCookies, targetUrl, ssl, ua)
            return ContentResult(htmlSections, allCookies,
                if (htmlSections.isEmpty()) "Содержимое курса не найдено" else "")

        } catch (e: Exception) {
            return ContentResult(emptyList(), emptyMap(), "Ошибка: ${e.message}")
        }
    }

    private fun fetchViaApi(
        cookies: Map<String, String>, sesskey: String, courseId: Int, ssl: SSLContext, ua: String
    ): List<CourseSection> {
        try {
            val url = URL("https://sdo.tusur.ru/lib/ajax/service.php?sesskey=$sesskey&info=core_course_get_contents")
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

            val body = """[{"index":0,"methodname":"core_course_get_contents","args":{"courseid":$courseId,"options":[]}}]"""
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }

            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(response)
            if (arr.length() == 0) return emptyList()

            val data = arr.getJSONObject(0)
            if (data.has("error") && data.getBoolean("error")) return emptyList()

            val sectionsArr = if (data.has("data")) {
                val d = data.get("data")
                if (d is JSONArray) d else return emptyList()
            } else return emptyList()

            val sections = mutableListOf<CourseSection>()
            for (i in 0 until sectionsArr.length()) {
                val s = sectionsArr.getJSONObject(i)
                val sectionName = s.optString("name", "").trim()
                val summary = s.optString("summary", "").trim()
                val modulesArr = s.optJSONArray("modules") ?: continue

                val modules = mutableListOf<CourseModule>()
                for (j in 0 until modulesArr.length()) {
                    val m = modulesArr.getJSONObject(j)
                    val modname = m.optString("modname", "")
                    // Пропускаем labels без ссылок
                    if (modname == "label") continue

                    modules.add(CourseModule(
                        name = m.optString("name", "").trim(),
                        modname = modname,
                        url = m.optString("url", ""),
                        description = m.optString("description", "")
                            .replace(Regex("<[^>]*>"), "").trim()
                    ))
                }

                if (sectionName.isNotEmpty() || modules.isNotEmpty()) {
                    sections.add(CourseSection(
                        name = sectionName.ifEmpty { "Раздел ${i + 1}" },
                        summary = summary.replace(Regex("<[^>]*>"), "").trim(),
                        modules = modules
                    ))
                }
            }
            return sections
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun fetchViaHtml(
        cookies: Map<String, String>, courseUrl: String, ssl: SSLContext, ua: String
    ): List<CourseSection> {
        try {
            val resp = Jsoup.connect(courseUrl)
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(cookies)
                .followRedirects(true)
                .ignoreHttpErrors(true).execute()

            val doc = resp.parse()
            val sections = mutableListOf<CourseSection>()

            // Moodle course sections
            val sectionEls = doc.select("li.section, div.section, ul.topics > li, ul.weeks > li")
            if (sectionEls.isNotEmpty()) {
                for (sectionEl in sectionEls) {
                    val header = sectionEl.selectFirst(".sectionname, .section-title, h3")
                    val sectionName = header?.text()?.trim() ?: ""

                    val modules = mutableListOf<CourseModule>()
                    val activityEls = sectionEl.select("li.activity, div.activity, .activityinstance")
                    for (actEl in activityEls) {
                        val link = actEl.selectFirst("a[href]") ?: continue
                        val name = actEl.selectFirst(".instancename, .activityname, .aalink")?.text()?.trim()
                            ?: link.text().trim()
                        if (name.isEmpty()) continue

                        val href = link.attr("abs:href")
                        val modname = when {
                            href.contains("/assign/") -> "assign"
                            href.contains("/quiz/") -> "quiz"
                            href.contains("/resource/") -> "resource"
                            href.contains("/url/") -> "url"
                            href.contains("/forum/") -> "forum"
                            href.contains("/page/") -> "page"
                            href.contains("/folder/") -> "folder"
                            href.contains("/book/") -> "book"
                            href.contains("/lesson/") -> "lesson"
                            href.contains("/choice/") -> "choice"
                            href.contains("/feedback/") -> "feedback"
                            href.contains("/glossary/") -> "glossary"
                            href.contains("/wiki/") -> "wiki"
                            else -> "unknown"
                        }

                        val desc = actEl.selectFirst(".contentafterlink, .dimmed_text")
                            ?.text()?.trim() ?: ""

                        modules.add(CourseModule(name, modname, href, desc))
                    }

                    if (sectionName.isNotEmpty() || modules.isNotEmpty()) {
                        sections.add(CourseSection(
                            name = sectionName.ifEmpty { "Материалы" },
                            summary = "",
                            modules = modules
                        ))
                    }
                }
            }

            // Если секции не найдены — ищем все ссылки на активности
            if (sections.isEmpty()) {
                val modules = mutableListOf<CourseModule>()
                doc.select("#region-main a[href*=/mod/]").forEach { link ->
                    val name = link.text().trim()
                    val href = link.attr("abs:href")
                    if (name.length > 2) {
                        modules.add(CourseModule(name, "unknown", href, ""))
                    }
                }
                if (modules.isNotEmpty()) {
                    sections.add(CourseSection("Материалы", "", modules))
                }
            }

            return sections
        } catch (e: Exception) {
            return emptyList()
        }
    }

    private fun extractSesskey(html: String): String {
        val jsMatch = Regex(""""sesskey"\s*:\s*"([a-zA-Z0-9]+)"""").find(html)
        if (jsMatch != null) return jsMatch.groupValues[1]
        val formMatch = Regex("""name="sesskey"[^>]*value="([a-zA-Z0-9]+)"""").find(html)
        if (formMatch != null) return formMatch.groupValues[1]
        val urlMatch = Regex("""sesskey=([a-zA-Z0-9]+)""").find(html)
        if (urlMatch != null) return urlMatch.groupValues[1]
        return ""
    }

    private fun buildTrustAllSsl(): SSLContext {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun checkServerTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        })
        return SSLContext.getInstance("TLS").apply { init(null, tm, java.security.SecureRandom()) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

// ===== ADAPTER =====

class CourseContentAdapter(
    private val sections: List<CourseContentActivity.CourseSection>,
    private val cookies: Map<String, String>,
    private val onModuleClick: (CourseContentActivity.CourseModule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_SECTION = 0
        const val TYPE_MODULE = 1
    }

    private data class Item(
        val type: Int,
        val section: CourseContentActivity.CourseSection? = null,
        val module: CourseContentActivity.CourseModule? = null
    )

    private val items: List<Item>

    init {
        val list = mutableListOf<Item>()
        for (section in sections) {
            list.add(Item(TYPE_SECTION, section = section))
            for (module in section.modules) {
                list.add(Item(TYPE_MODULE, module = module))
            }
        }
        items = list
    }

    class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_section_name)
        val tvSummary: TextView = view.findViewById(R.id.tv_section_summary)
    }

    class ModuleVH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tv_module_icon)
        val tvName: TextView = view.findViewById(R.id.tv_module_name)
        val tvType: TextView = view.findViewById(R.id.tv_module_type)
        val tvDesc: TextView = view.findViewById(R.id.tv_module_desc)
    }

    override fun getItemViewType(position: Int) = items[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SECTION -> SectionVH(inflater.inflate(R.layout.item_course_section, parent, false))
            else -> ModuleVH(inflater.inflate(R.layout.item_course_module, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        when (holder) {
            is SectionVH -> {
                val section = item.section!!
                holder.tvName.text = section.name
                if (section.summary.isNotEmpty()) {
                    holder.tvSummary.text = section.summary
                    holder.tvSummary.visibility = View.VISIBLE
                } else {
                    holder.tvSummary.visibility = View.GONE
                }
            }
            is ModuleVH -> {
                val module = item.module!!
                holder.tvName.text = module.name
                holder.tvType.text = getModuleTypeName(module.modname)
                holder.tvIcon.text = getModuleIcon(module.modname)

                if (module.description.isNotEmpty()) {
                    holder.tvDesc.text = module.description
                    holder.tvDesc.visibility = View.VISIBLE
                } else {
                    holder.tvDesc.visibility = View.GONE
                }

                holder.itemView.setOnClickListener { onModuleClick(module) }
            }
        }
    }

    override fun getItemCount() = items.size

    private fun getModuleIcon(modname: String): String = when (modname) {
        "assign" -> "\uD83D\uDCDD"    // memo
        "quiz" -> "\u2753"              // question
        "resource" -> "\uD83D\uDCC4"   // page
        "folder" -> "\uD83D\uDCC1"     // folder
        "url" -> "\uD83D\uDD17"        // link
        "forum" -> "\uD83D\uDCAC"      // speech
        "page" -> "\uD83D\uDCC3"       // page curl
        "book" -> "\uD83D\uDCD6"       // book
        "lesson" -> "\uD83C\uDF93"     // grad cap
        "choice" -> "\u2611"            // checkbox
        "feedback" -> "\uD83D\uDCCA"   // chart
        "glossary" -> "\uD83D\uDD24"   // abc
        "wiki" -> "\uD83C\uDF10"       // globe
        else -> "\uD83D\uDCCE"         // paperclip
    }

    private fun getModuleTypeName(modname: String): String = when (modname) {
        "assign" -> "Задание"
        "quiz" -> "Тест"
        "resource" -> "Файл"
        "folder" -> "Папка"
        "url" -> "Ссылка"
        "forum" -> "Форум"
        "page" -> "Страница"
        "book" -> "Книга"
        "lesson" -> "Лекция"
        "choice" -> "Опрос"
        "feedback" -> "Отзыв"
        "glossary" -> "Глоссарий"
        "wiki" -> "Вики"
        else -> "Материал"
    }
}
