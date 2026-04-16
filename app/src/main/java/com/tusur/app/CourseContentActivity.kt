package com.tusur.app

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import org.json.JSONArray
import org.jsoup.Connection
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.OutputStreamWriter
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class CourseContentActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var cookies = HashMap<String, String>()

    companion object {
        fun start(context: Context, courseName: String, courseUrl: String, courseId: Int) {
            context.startActivity(Intent(context, CourseContentActivity::class.java).apply {
                putExtra("course_name", courseName)
                putExtra("course_url", courseUrl)
                putExtra("course_id", courseId)
            })
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

        // Контейнер для нативного показа модуля
        val moduleContainer = findViewById<LinearLayout>(R.id.module_container)
        val moduleScroll = findViewById<ScrollView>(R.id.module_scroll)
        val tvModuleBack = findViewById<TextView>(R.id.tv_module_back)
        val tvModuleTitle = findViewById<TextView>(R.id.tv_module_title)
        val moduleContent = findViewById<LinearLayout>(R.id.module_content)
        val moduleProgress = findViewById<ProgressBar>(R.id.progress_module)

        tvTitle.text = courseName
        rv.layoutManager = LinearLayoutManager(this)

        tvBack.setOnClickListener { finish() }
        tvModuleBack.setOnClickListener {
            moduleContainer.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }

        val resolvedId = if (courseId > 0) courseId else {
            Regex("id=(\\d+)").find(courseUrl)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        val session = SessionManager(this)

        progress.visibility = View.VISIBLE
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                fetchCourseContent(session.email, session.password, resolvedId, courseUrl)
            }
            progress.visibility = View.GONE
            cookies.putAll(result.cookies)

            if (result.sections.isNotEmpty()) {
                rv.adapter = CourseContentAdapter(result.sections, result.cookies) { module ->
                    openModuleNative(module, tvModuleTitle, moduleContent,
                        moduleContainer, moduleScroll, moduleProgress, rv)
                }
                tvError.visibility = View.GONE
            } else {
                tvError.text = result.debug.ifEmpty { "Не удалось загрузить содержимое курса" }
                tvError.visibility = View.VISIBLE
            }
        }
    }

    // ===== Нативный показ модуля =====

    private fun openModuleNative(
        module: CourseModule,
        tvTitle: TextView,
        contentLayout: LinearLayout,
        container: LinearLayout,
        scroll: ScrollView,
        progress: ProgressBar,
        rv: RecyclerView
    ) {
        if (module.url.isEmpty()) return

        tvTitle.text = module.name
        contentLayout.removeAllViews()
        rv.visibility = View.GONE
        container.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        scroll.scrollTo(0, 0)

        scope.launch {
            val parsed = withContext(Dispatchers.IO) { parseModulePage(module.url) }
            progress.visibility = View.GONE

            if (parsed.isEmpty()) {
                addTextBlock(contentLayout, "Не удалось загрузить содержимое", isSecondary = true)
                return@launch
            }

            for (block in parsed) {
                when (block.type) {
                    BlockType.HEADING -> addHeading(contentLayout, block.text)
                    BlockType.TEXT -> addTextBlock(contentLayout, block.text)
                    BlockType.HTML -> addHtmlBlock(contentLayout, block.text)
                    BlockType.LIST_ITEM -> addListItem(contentLayout, block.text)
                    BlockType.LINK -> addLink(contentLayout, block.text, block.extra)
                    BlockType.FILE -> addFile(contentLayout, block.text, block.extra)
                    BlockType.DIVIDER -> addDivider(contentLayout)
                    BlockType.INFO -> addInfoBlock(contentLayout, block.text)
                }
            }
        }
    }

    // ===== UI-блоки для нативного отображения =====

    private fun addHeading(parent: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 18f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(16), 0, dp(8))
        }
        parent.addView(tv)
    }

    private fun addTextBlock(parent: LinearLayout, text: String, isSecondary: Boolean = false) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextColor(resources.getColor(
                if (isSecondary) R.color.text_secondary else R.color.text_primary, null))
            setLineSpacing(0f, 1.3f)
            setPadding(0, dp(4), 0, dp(4))
        }
        parent.addView(tv)
    }

    private fun addHtmlBlock(parent: LinearLayout, html: String) {
        val tv = TextView(this).apply {
            this.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            textSize = 15f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setLineSpacing(0f, 1.3f)
            movementMethod = LinkMovementMethod.getInstance()
            setPadding(0, dp(4), 0, dp(4))
        }
        parent.addView(tv)
    }

    private fun addListItem(parent: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = "  •  $text"
            textSize = 14f
            setTextColor(resources.getColor(R.color.text_primary, null))
            setLineSpacing(0f, 1.2f)
            setPadding(dp(8), dp(2), 0, dp(2))
        }
        parent.addView(tv)
    }

    private fun addLink(parent: LinearLayout, title: String, url: String) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_module_link, parent, false)
        card.findViewById<TextView>(R.id.tv_link_title).text = title
        card.findViewById<TextView>(R.id.tv_link_url).text = url
            .replace("https://", "").replace("http://", "").take(50)
        card.setOnClickListener {
            try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) } catch (_: Exception) {}
        }
        parent.addView(card)
    }

    private fun addFile(parent: LinearLayout, name: String, url: String) {
        val card = LayoutInflater.from(this).inflate(R.layout.item_module_file, parent, false)
        card.findViewById<TextView>(R.id.tv_file_name).text = name
        val ext = name.substringAfterLast(".", "").uppercase()
        card.findViewById<TextView>(R.id.tv_file_ext).text = if (ext.length <= 4) ext else "FILE"
        card.setOnClickListener {
            downloadFile(name, url)
        }
        parent.addView(card)
    }

    private fun downloadFile(fileName: String, fileUrl: String) {
        try {
            // Определяем имя файла
            val cleanName = if (fileName.contains(".")) fileName
            else "$fileName.pdf"

            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(fileUrl)).apply {
                setTitle(cleanName)
                setDescription("Загрузка из СДО ТУСУР")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "TUSUR/$cleanName")

                // Передаём cookies для авторизации
                val cookieString = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
                addRequestHeader("Cookie", cookieString)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
            }

            dm.enqueue(request)
            Toast.makeText(this, "Скачивание: $cleanName", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            // Fallback — скачиваем через корутину напрямую
            downloadFileDirect(fileName, fileUrl)
        }
    }

    private fun downloadFileDirect(fileName: String, fileUrl: String) {
        val cleanName = if (fileName.contains(".")) fileName else "$fileName.pdf"

        Toast.makeText(this, "Загрузка $cleanName...", Toast.LENGTH_SHORT).show()

        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val ssl = buildTrustAllSsl()
                    val url = URL(fileUrl)
                    val conn = (url.openConnection() as HttpsURLConnection).apply {
                        sslSocketFactory = ssl.socketFactory
                        hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                        setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                        connectTimeout = 30_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                    }

                    // Следуем редиректам вручную с cookies
                    var finalConn = conn
                    var redirectCount = 0
                    while (redirectCount < 10) {
                        finalConn.connect()
                        val code = finalConn.responseCode
                        if (code in 301..303 || code == 307) {
                            val loc = finalConn.getHeaderField("Location") ?: break
                            val nextUrl = if (loc.startsWith("http")) loc else "https://sdo.tusur.ru$loc"
                            finalConn.disconnect()
                            finalConn = (URL(nextUrl).openConnection() as HttpsURLConnection).apply {
                                sslSocketFactory = ssl.socketFactory
                                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                                connectTimeout = 30_000
                                readTimeout = 30_000
                                instanceFollowRedirects = false
                            }
                            redirectCount++
                        } else break
                    }

                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val tusurDir = java.io.File(downloadsDir, "TUSUR")
                    if (!tusurDir.exists()) tusurDir.mkdirs()

                    val outFile = java.io.File(tusurDir, cleanName)
                    finalConn.inputStream.use { input ->
                        outFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    finalConn.disconnect()
                    outFile
                }

                Toast.makeText(this@CourseContentActivity,
                    "Сохранено: Downloads/TUSUR/$cleanName", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                Toast.makeText(this@CourseContentActivity,
                    "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun addDivider(parent: LinearLayout) {
        val v = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
            ).apply { setMargins(0, dp(12), 0, dp(12)) }
            setBackgroundColor(resources.getColor(R.color.divider, null))
        }
        parent.addView(v)
    }

    private fun addInfoBlock(parent: LinearLayout, text: String) {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(resources.getColor(R.color.text_secondary, null))
            setBackgroundColor(resources.getColor(R.color.card_bg, null))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
            layoutParams = lp
        }
        parent.addView(tv)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    // ===== Парсинг страницы модуля =====

    enum class BlockType { HEADING, TEXT, HTML, LIST_ITEM, LINK, FILE, DIVIDER, INFO }
    data class ContentBlock(val type: BlockType, val text: String, val extra: String = "")

    private fun parseModulePage(url: String): List<ContentBlock> {
        val ssl = buildTrustAllSsl()
        val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        try {
            val doc = Jsoup.connect(url)
                .userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory)
                .cookies(cookies)
                .followRedirects(true)
                .ignoreHttpErrors(true).get()

            val blocks = mutableListOf<ContentBlock>()

            // Заголовок страницы
            val pageTitle = doc.selectFirst("h2, #page-header h1, .page-header-headings h1")?.text()?.trim()
            if (!pageTitle.isNullOrEmpty()) {
                blocks.add(ContentBlock(BlockType.HEADING, pageTitle))
            }

            // Основной контент — region-main
            val main = doc.selectFirst("#region-main, [role=main], .course-content") ?: doc.body()

            // Убираем навигацию, header, footer, sidebar
            main.select("nav, header, footer, .navbar, #page-header, .block, aside, " +
                    ".activity-navigation, .secondary-navigation, #nav-drawer, " +
                    ".breadcrumb, .action-menu, script, style, .hidden").remove()

            // Описание задания / модуля
            val introContent = main.select(".intro, .activity-description, .contentafterlink, " +
                    ".submissionstatustable, .assignmentcontent, .quizinfo, " +
                    "#intro, .summary, .activity-header .description")
            for (el in introContent) {
                parseElement(el, blocks)
            }

            // Основное содержимое
            val contentAreas = main.select(".generalbox, .box, .content, .que, " +
                    ".formulation, .ablock, .submission-full, [role=article], " +
                    ".page-mod-page-content, .book_content, #page-mod-page-content, " +
                    ".mod-page-content, .modified-content")
            if (contentAreas.isNotEmpty()) {
                for (area in contentAreas) {
                    parseElement(area, blocks)
                }
            }

            // Если ничего не нашли в специальных блоках — парсим всё из main
            if (blocks.size <= 1) {
                parseElement(main, blocks)
            }

            // Файлы и ссылки
            main.select("a[href]").forEach { a ->
                val href = a.attr("abs:href")
                val text = a.text().trim()
                if (text.isEmpty() || text.length < 2) return@forEach
                if (href.contains("pluginfile.php") || href.contains("/resource/")) {
                    blocks.add(ContentBlock(BlockType.FILE, text, href))
                } else if (href.contains("mod/url") || (href.startsWith("http") && !href.contains("sdo.tusur.ru"))) {
                    blocks.add(ContentBlock(BlockType.LINK, text, href))
                }
            }

            // Информация о сроках (для заданий)
            main.select("table.generaltable tr, .submissionstatustable tr").forEach { row ->
                val cells = row.select("td, th")
                if (cells.size >= 2) {
                    val label = cells[0].text().trim()
                    val value = cells[1].text().trim()
                    if (label.isNotEmpty() && value.isNotEmpty()) {
                        blocks.add(ContentBlock(BlockType.INFO, "$label: $value"))
                    }
                }
            }

            // Удаляем дубликаты
            return blocks.distinctBy { "${it.type}:${it.text.take(100)}" }
                .filter { it.text.isNotBlank() }

        } catch (e: Exception) {
            return listOf(ContentBlock(BlockType.TEXT, "Ошибка загрузки: ${e.message}"))
        }
    }

    private fun parseElement(el: Element, blocks: MutableList<ContentBlock>) {
        for (child in el.children()) {
            val tag = child.tagName().lowercase()
            val text = child.text().trim()
            if (text.isEmpty()) continue

            when {
                tag in listOf("h1", "h2", "h3", "h4", "h5") ->
                    blocks.add(ContentBlock(BlockType.HEADING, text))

                tag == "li" ->
                    blocks.add(ContentBlock(BlockType.LIST_ITEM, text))

                tag == "ul" || tag == "ol" -> {
                    child.select("li").forEach { li ->
                        val liText = li.text().trim()
                        if (liText.isNotEmpty()) blocks.add(ContentBlock(BlockType.LIST_ITEM, liText))
                    }
                }

                tag == "table" -> {
                    child.select("tr").forEach { row ->
                        val cells = row.select("td, th")
                        if (cells.isNotEmpty()) {
                            val rowText = cells.joinToString("  |  ") { it.text().trim() }
                            if (rowText.isNotBlank()) blocks.add(ContentBlock(BlockType.INFO, rowText))
                        }
                    }
                }

                tag == "hr" || tag == "br" && child.nextElementSibling()?.tagName() == "br" ->
                    blocks.add(ContentBlock(BlockType.DIVIDER, ""))

                tag == "p" || tag == "div" || tag == "span" || tag == "section" -> {
                    // Если есть значимые дочерние элементы — рекурсия
                    val innerHtml = child.html().trim()
                    if (child.children().any { it.tagName() in listOf("h1","h2","h3","h4","ul","ol","table","p","div") }) {
                        parseElement(child, blocks)
                    } else if (innerHtml.contains("<a ") || innerHtml.contains("<b>") ||
                        innerHtml.contains("<strong") || innerHtml.contains("<em") ||
                        innerHtml.contains("<i>") || innerHtml.contains("<br")) {
                        // Содержит форматирование — используем HTML
                        blocks.add(ContentBlock(BlockType.HTML, innerHtml))
                    } else if (text.length > 2) {
                        blocks.add(ContentBlock(BlockType.TEXT, text))
                    }
                }

                else -> {
                    if (text.length > 2) blocks.add(ContentBlock(BlockType.TEXT, text))
                }
            }
        }
    }

    // ===== DATA =====

    data class CourseSection(val name: String, val summary: String, val modules: List<CourseModule>)
    data class CourseModule(val name: String, val modname: String, val url: String, val description: String)
    data class ContentResult(val sections: List<CourseSection>, val cookies: Map<String, String>, val debug: String)

    // ===== FETCH COURSE STRUCTURE =====

    private fun fetchCourseContent(email: String, password: String, courseId: Int, courseUrl: String): ContentResult {
        val ssl = buildTrustAllSsl()
        val ua = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36"
        val allCookies = HashMap<String, String>()

        try {
            // SSO login
            val ssoUrl = "https://profile.tusur.ru/users/sign_in?redirect_url=https%3A%2F%2Fsdo.tusur.ru%2Fauth%2Fedu%2F%3Fid%3D1"
            val getResp = Jsoup.connect(ssoUrl).userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory).followRedirects(true).execute()
            allCookies.putAll(getResp.cookies())

            val csrf = getResp.parse().selectFirst("input[name=authenticity_token]")?.attr("value") ?: ""
            val postResp = Jsoup.connect(getResp.url().toString()).userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory).cookies(allCookies)
                .data("authenticity_token", csrf).data("user[email]", email)
                .data("user[password]", password).data("user[remember_me]", "1")
                .method(Connection.Method.POST).followRedirects(false).execute()
            allCookies.putAll(postResp.cookies())

            var location = postResp.header("Location") ?: ""
            if (postResp.statusCode() !in 301..303 || location.contains("sign_in"))
                return ContentResult(emptyList(), emptyMap(), "Ошибка авторизации")

            var redirects = 0
            while (location.isNotEmpty() && redirects < 15) {
                val nextUrl = if (location.startsWith("http")) location else "https://profile.tusur.ru$location"
                val r = Jsoup.connect(nextUrl).userAgent(ua).timeout(20_000)
                    .sslSocketFactory(ssl.socketFactory).cookies(allCookies)
                    .followRedirects(false).ignoreHttpErrors(true).execute()
                allCookies.putAll(r.cookies())
                location = r.header("Location") ?: ""
                if (r.statusCode() !in 301..303) break
                redirects++
            }

            // Получаем sesskey
            val dashResp = Jsoup.connect("https://sdo.tusur.ru/my/").userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory).cookies(allCookies).followRedirects(true).execute()
            allCookies.putAll(dashResp.cookies())
            val sesskey = extractSesskey(dashResp.parse().html())

            // API: core_course_get_contents
            if (courseId > 0 && sesskey.isNotEmpty()) {
                val api = fetchViaApi(allCookies, sesskey, courseId, ssl, ua)
                if (api.isNotEmpty()) return ContentResult(api, allCookies, "")
            }

            // Fallback: HTML
            val target = courseUrl.ifEmpty { "https://sdo.tusur.ru/course/view.php?id=$courseId" }
            val html = fetchViaHtml(allCookies, target, ssl, ua)
            return ContentResult(html, allCookies, if (html.isEmpty()) "Содержимое не найдено" else "")

        } catch (e: Exception) {
            return ContentResult(emptyList(), emptyMap(), "Ошибка: ${e.message}")
        }
    }

    private fun fetchViaApi(cookies: Map<String, String>, sesskey: String, courseId: Int, ssl: SSLContext, ua: String): List<CourseSection> {
        try {
            val url = URL("https://sdo.tusur.ru/lib/ajax/service.php?sesskey=$sesskey&info=core_course_get_contents")
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = ssl.socketFactory
                hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("User-Agent", ua)
                setRequestProperty("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                connectTimeout = 20_000; readTimeout = 20_000; doOutput = true
            }
            val body = """[{"index":0,"methodname":"core_course_get_contents","args":{"courseid":$courseId,"options":[]}}]"""
            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body); it.flush() }
            val response = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            val arr = JSONArray(response)
            if (arr.length() == 0) return emptyList()
            val data = arr.getJSONObject(0)
            if (data.has("error") && data.getBoolean("error")) return emptyList()
            val sectionsArr = if (data.has("data")) { val d = data.get("data"); if (d is JSONArray) d else return emptyList() } else return emptyList()

            val sections = mutableListOf<CourseSection>()
            for (i in 0 until sectionsArr.length()) {
                val s = sectionsArr.getJSONObject(i)
                val modulesArr = s.optJSONArray("modules") ?: continue
                val modules = mutableListOf<CourseModule>()
                for (j in 0 until modulesArr.length()) {
                    val m = modulesArr.getJSONObject(j)
                    if (m.optString("modname") == "label") continue
                    modules.add(CourseModule(
                        m.optString("name", "").trim(), m.optString("modname", ""),
                        m.optString("url", ""), m.optString("description", "").replace(Regex("<[^>]*>"), "").trim()
                    ))
                }
                val name = s.optString("name", "").trim()
                if (name.isNotEmpty() || modules.isNotEmpty())
                    sections.add(CourseSection(name.ifEmpty { "Раздел ${i + 1}" },
                        s.optString("summary", "").replace(Regex("<[^>]*>"), "").trim(), modules))
            }
            return sections
        } catch (e: Exception) { return emptyList() }
    }

    private fun fetchViaHtml(cookies: Map<String, String>, courseUrl: String, ssl: SSLContext, ua: String): List<CourseSection> {
        try {
            val doc = Jsoup.connect(courseUrl).userAgent(ua).timeout(20_000)
                .sslSocketFactory(ssl.socketFactory).cookies(cookies)
                .followRedirects(true).ignoreHttpErrors(true).get()
            val sections = mutableListOf<CourseSection>()
            val sectionEls = doc.select("li.section, div.section, ul.topics > li, ul.weeks > li")
            for (sectionEl in sectionEls) {
                val header = sectionEl.selectFirst(".sectionname, .section-title, h3")
                val modules = mutableListOf<CourseModule>()
                for (actEl in sectionEl.select("li.activity, div.activity, .activityinstance")) {
                    val link = actEl.selectFirst("a[href]") ?: continue
                    val name = actEl.selectFirst(".instancename, .activityname, .aalink")?.text()?.trim() ?: link.text().trim()
                    if (name.isEmpty()) continue
                    val href = link.attr("abs:href")
                    val modname = when {
                        href.contains("/assign/") -> "assign"; href.contains("/quiz/") -> "quiz"
                        href.contains("/resource/") -> "resource"; href.contains("/url/") -> "url"
                        href.contains("/forum/") -> "forum"; href.contains("/page/") -> "page"
                        href.contains("/folder/") -> "folder"; href.contains("/book/") -> "book"
                        else -> "unknown"
                    }
                    modules.add(CourseModule(name, modname, href,
                        actEl.selectFirst(".contentafterlink")?.text()?.trim() ?: ""))
                }
                val sName = header?.text()?.trim() ?: ""
                if (sName.isNotEmpty() || modules.isNotEmpty())
                    sections.add(CourseSection(sName.ifEmpty { "Материалы" }, "", modules))
            }
            if (sections.isEmpty()) {
                val mods = mutableListOf<CourseModule>()
                doc.select("#region-main a[href*=/mod/]").forEach { mods.add(CourseModule(it.text().trim(), "unknown", it.attr("abs:href"), "")) }
                if (mods.isNotEmpty()) sections.add(CourseSection("Материалы", "", mods))
            }
            return sections
        } catch (e: Exception) { return emptyList() }
    }

    private fun extractSesskey(html: String): String {
        return Regex(""""sesskey"\s*:\s*"([a-zA-Z0-9]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""name="sesskey"[^>]*value="([a-zA-Z0-9]+)"""").find(html)?.groupValues?.get(1)
            ?: Regex("""sesskey=([a-zA-Z0-9]+)""").find(html)?.groupValues?.get(1) ?: ""
    }

    private fun buildTrustAllSsl(): SSLContext {
        val tm = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun checkServerTrusted(c: Array<X509Certificate>, t: String) = Unit
            override fun getAcceptedIssuers() = emptyArray<X509Certificate>()
        })
        return SSLContext.getInstance("TLS").apply { init(null, tm, java.security.SecureRandom()) }
    }

    override fun onBackPressed() {
        val container = findViewById<LinearLayout>(R.id.module_container)
        val rv = findViewById<RecyclerView>(R.id.rv_course_content)
        if (container.visibility == View.VISIBLE) {
            container.visibility = View.GONE
            rv.visibility = View.VISIBLE
        } else super.onBackPressed()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}

// ===== ADAPTER =====

class CourseContentAdapter(
    sections: List<CourseContentActivity.CourseSection>,
    private val cookies: Map<String, String>,
    private val onModuleClick: (CourseContentActivity.CourseModule) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object { const val TYPE_SECTION = 0; const val TYPE_MODULE = 1 }
    private data class Item(val type: Int, val section: CourseContentActivity.CourseSection? = null, val module: CourseContentActivity.CourseModule? = null)
    private val items: List<Item> = buildList {
        for (s in sections) { add(Item(TYPE_SECTION, section = s)); for (m in s.modules) add(Item(TYPE_MODULE, module = m)) }
    }

    class SectionVH(v: View) : RecyclerView.ViewHolder(v) { val tvName: TextView = v.findViewById(R.id.tv_section_name); val tvSummary: TextView = v.findViewById(R.id.tv_section_summary) }
    class ModuleVH(v: View) : RecyclerView.ViewHolder(v) { val tvIcon: TextView = v.findViewById(R.id.tv_module_icon); val tvName: TextView = v.findViewById(R.id.tv_module_name); val tvType: TextView = v.findViewById(R.id.tv_module_type); val tvDesc: TextView = v.findViewById(R.id.tv_module_desc) }

    override fun getItemViewType(pos: Int) = items[pos].type
    override fun onCreateViewHolder(p: ViewGroup, vt: Int) = if (vt == TYPE_SECTION)
        SectionVH(LayoutInflater.from(p.context).inflate(R.layout.item_course_section, p, false))
    else ModuleVH(LayoutInflater.from(p.context).inflate(R.layout.item_course_module, p, false))

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val item = items[pos]
        when (h) {
            is SectionVH -> { h.tvName.text = item.section!!.name; h.tvSummary.apply { if (item.section.summary.isNotEmpty()) { text = item.section.summary; visibility = View.VISIBLE } else visibility = View.GONE } }
            is ModuleVH -> { val m = item.module!!; h.tvName.text = m.name; h.tvType.text = modTypeName(m.modname); h.tvIcon.text = modIcon(m.modname); h.tvDesc.apply { if (m.description.isNotEmpty()) { text = m.description; visibility = View.VISIBLE } else visibility = View.GONE }; h.itemView.setOnClickListener { onModuleClick(m) } }
        }
    }
    override fun getItemCount() = items.size
    private fun modIcon(m: String) = when (m) { "assign"->"\uD83D\uDCDD"; "quiz"->"\u2753"; "resource"->"\uD83D\uDCC4"; "folder"->"\uD83D\uDCC1"; "url"->"\uD83D\uDD17"; "forum"->"\uD83D\uDCAC"; "page"->"\uD83D\uDCC3"; "book"->"\uD83D\uDCD6"; else->"\uD83D\uDCCE" }
    private fun modTypeName(m: String) = when (m) { "assign"->"Задание"; "quiz"->"Тест"; "resource"->"Файл"; "folder"->"Папка"; "url"->"Ссылка"; "forum"->"Форум"; "page"->"Страница"; "book"->"Книга"; else->"Материал" }
}
