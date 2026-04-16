package com.tusur.app.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.tusur.app.R
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MapFragment : Fragment() {

    private var mapView: MapView? = null
    private var myLocationOverlay: MyLocationNewOverlay? = null

    // Центр — главный корпус ТУСУР (пр. Ленина, 40, Томск)
    private val TUSUR_CENTER = GeoPoint(56.4703, 84.9505)

    data class MapPlace(
        val name: String,
        val shortName: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val type: PlaceType,
        val description: String = ""
    )

    enum class PlaceType { BUILDING, DORMITORY, SPORTS, LIBRARY, FOOD, TRANSPORT, SHOP, MEDICINE, CULTURE, OTHER }

    private val places = listOf(
        // ===== КОРПУСА ТУСУР =====
        MapPlace("Главный корпус ТУСУР", "ГК", "пр. Ленина, 40",
            56.4703, 84.9505, PlaceType.BUILDING, "Главный учебный корпус университета"),
        MapPlace("Учебный корпус ФЭТ", "ФЭТ", "ул. Вершинина, 74",
            56.4678, 84.9486, PlaceType.BUILDING, "Факультет электронной техники"),
        MapPlace("Учебно-лабораторный корпус", "УЛК", "ул. Красноармейская, 146",
            56.4714, 84.9533, PlaceType.BUILDING, "Лаборатории и учебные аудитории"),
        MapPlace("Радиотехнический корпус", "РК", "ул. Вершинина, 47",
            56.4688, 84.9462, PlaceType.BUILDING, "Радиотехнический факультет"),
        MapPlace("Корпус ФСУ", "ФСУ", "ул. Красноармейская, 146",
            56.4710, 84.9540, PlaceType.BUILDING, "Факультет систем управления"),
        MapPlace("Корпус ФВС", "ФВС", "ул. Красноармейская, 146б",
            56.4718, 84.9528, PlaceType.BUILDING, "Факультет вычислительных систем"),
        MapPlace("Бизнес-инкубатор ТУСУР", "БИ", "ул. Вершинина, 72",
            56.4681, 84.9479, PlaceType.BUILDING, "Студенческий бизнес-инкубатор"),
        MapPlace("Научная библиотека ТУСУР", "Библиотека", "ул. Вершинина, 47",
            56.4685, 84.9458, PlaceType.LIBRARY, "Научная библиотека университета"),

        // ===== ОБЩЕЖИТИЯ ТУСУР =====
        MapPlace("Общежитие ТУСУР №1", "Общ.1", "ул. Вершинина, 48",
            56.4672, 84.9454, PlaceType.DORMITORY),
        MapPlace("Общежитие ТУСУР №2", "Общ.2", "ул. Вершинина, 46",
            56.4669, 84.9448, PlaceType.DORMITORY),
        MapPlace("Общежитие ТУСУР №3", "Общ.3", "ул. Лыткина, 8",
            56.4651, 84.9430, PlaceType.DORMITORY),
        MapPlace("Общежитие ТУСУР №4", "Общ.4", "ул. Вершинина, 44",
            56.4665, 84.9442, PlaceType.DORMITORY),
        MapPlace("Общежитие ТУСУР №5", "Общ.5", "пер. Спортивный, 9",
            56.4660, 84.9420, PlaceType.DORMITORY),
        MapPlace("Общежитие ТУСУР №6", "Общ.6", "ул. 19 Гвардейской дивизии, 11а",
            56.4620, 84.9350, PlaceType.DORMITORY),

        // ===== СПОРТ ТУСУР =====
        MapPlace("Спортивный зал ТУСУР", "Спортзал", "ул. Вершинина, 48а",
            56.4675, 84.9460, PlaceType.SPORTS, "Спортивный комплекс"),

        // ===== СТОЛОВЫЕ =====
        MapPlace("Столовая ТУСУР (ГК)", "Столовая", "пр. Ленина, 40",
            56.4701, 84.9502, PlaceType.FOOD, "Столовая в главном корпусе"),

        // ===== ДРУГИЕ ВУЗЫ ТОМСКА =====
        MapPlace("Томский политехнический университет", "ТПУ", "пр. Ленина, 30",
            56.4634, 84.9502, PlaceType.BUILDING, "Национальный исследовательский ТПУ"),
        MapPlace("Томский государственный университет", "ТГУ", "пр. Ленина, 36",
            56.4673, 84.9504, PlaceType.BUILDING, "Национальный исследовательский ТГУ"),
        MapPlace("ТГАСУ", "ТГАСУ", "пл. Соляная, 2",
            56.4740, 84.9543, PlaceType.BUILDING, "Томский архитектурно-строительный"),
        MapPlace("СибГМУ", "СибГМУ", "Московский тракт, 2",
            56.4760, 84.9492, PlaceType.BUILDING, "Сибирский медицинский университет"),

        // ===== ТРАНСПОРТ =====
        MapPlace("Ж/д вокзал Томск-1", "Вокзал", "пр. Кирова, 68",
            56.4840, 84.9478, PlaceType.TRANSPORT, "Железнодорожный вокзал"),
        MapPlace("Автовокзал Томска", "Автовокзал", "пр. Кирова, 68а",
            56.4843, 84.9470, PlaceType.TRANSPORT, "Междугородный автобусный вокзал"),
        MapPlace("Остановка «Кинотеатр Киномир»", "Киномир", "пр. Ленина, 41",
            56.4707, 84.9520, PlaceType.TRANSPORT, "Трамвай, автобус"),
        MapPlace("Остановка «Площадь Ленина»", "Пл. Ленина", "пл. Ленина",
            56.4718, 84.9560, PlaceType.TRANSPORT, "Трамвай, автобус, маршрутка"),
        MapPlace("Остановка «ТПУ»", "ТПУ ост.", "пр. Ленина, 30",
            56.4636, 84.9490, PlaceType.TRANSPORT, "Трамвай, автобус"),

        // ===== МАГАЗИНЫ =====
        MapPlace("ТЦ «Изумрудный город»", "Изумрудный", "пр. Ленина, 195",
            56.4917, 84.9682, PlaceType.SHOP, "Торговый центр"),
        MapPlace("Лента", "Лента", "ул. Елизаровых, 13",
            56.4910, 84.9470, PlaceType.SHOP, "Гипермаркет"),
        MapPlace("Пятёрочка (Вершинина)", "Пятёрочка", "ул. Вершинина, 40",
            56.4660, 84.9435, PlaceType.SHOP, "Продуктовый магазин"),
        MapPlace("Мария-Ра (Ленина)", "Мария-Ра", "пр. Ленина, 54",
            56.4725, 84.9520, PlaceType.SHOP, "Продуктовый магазин"),

        // ===== ЕДА =====
        MapPlace("KFC (Ленина)", "KFC", "пр. Ленина, 38",
            56.4697, 84.9510, PlaceType.FOOD, "Быстрое питание"),
        MapPlace("Додо Пицца (Ленина)", "Додо Пицца", "пр. Ленина, 83",
            56.4778, 84.9555, PlaceType.FOOD, "Пиццерия"),
        MapPlace("Subway (Ленина)", "Subway", "пр. Ленина, 54",
            56.4726, 84.9518, PlaceType.FOOD, "Быстрое питание"),
        MapPlace("Шоколадница", "Шоколадница", "пр. Ленина, 56",
            56.4730, 84.9525, PlaceType.FOOD, "Кофейня"),

        // ===== МЕДИЦИНА =====
        MapPlace("Поликлиника №1", "Поликл.1", "ул. Кузнецова, 26",
            56.4755, 84.9498, PlaceType.MEDICINE, "Городская поликлиника"),
        MapPlace("Аптека 36,6 (Ленина)", "Аптека", "пр. Ленина, 48",
            56.4715, 84.9515, PlaceType.MEDICINE, "Аптека"),
        MapPlace("ОКБ", "ОКБ", "ул. Ивана Черных, 96",
            56.4530, 84.9530, PlaceType.MEDICINE, "Областная клиническая больница"),

        // ===== КУЛЬТУРА =====
        MapPlace("Площадь Ленина", "Пл. Ленина", "пл. Ленина",
            56.4718, 84.9563, PlaceType.CULTURE, "Центральная площадь Томска"),
        MapPlace("Томский областной драмтеатр", "Драмтеатр", "пл. Ленина, 4",
            56.4725, 84.9575, PlaceType.CULTURE, "Театр драмы"),
        MapPlace("Набережная Томи", "Набережная", "Набережная реки Томи",
            56.4685, 84.9555, PlaceType.CULTURE, "Прогулочная зона"),
        MapPlace("Лагерный сад", "Лагерный сад", "пр. Ленина",
            56.4590, 84.9480, PlaceType.CULTURE, "Парк и мемориал"),
        MapPlace("Городской сад (Горсад)", "Горсад", "пр. Ленина, 60",
            56.4740, 84.9530, PlaceType.CULTURE, "Парк аттракционов"),
        MapPlace("Киномир IMAX", "Киномир", "пр. Ленина, 41",
            56.4705, 84.9515, PlaceType.CULTURE, "Кинотеатр"),
        MapPlace("Новособорная площадь", "Новособорная", "пр. Ленина / ул. Советская",
            56.4685, 84.9520, PlaceType.CULTURE, "Фонтаны и прогулочная зона"),

        // ===== ПРОЧЕЕ =====
        MapPlace("Почта России (Ленина)", "Почта", "пр. Ленина, 93",
            56.4790, 84.9570, PlaceType.OTHER, "Почтовое отделение"),
        MapPlace("МФЦ Томска", "МФЦ", "пр. Фрунзе, 103д",
            56.4700, 84.9370, PlaceType.OTHER, "Многофункциональный центр"),
        MapPlace("Администрация Томска", "Администрация", "пр. Ленина, 73",
            56.4757, 84.9558, PlaceType.OTHER, "Администрация города")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName
        Configuration.getInstance().tileFileSystemCacheMaxBytes = 256L * 1024 * 1024
        Configuration.getInstance().tileFileSystemCacheTrimBytes = 200L * 1024 * 1024
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val map = view.findViewById<MapView>(R.id.map_view)
        mapView = map

        setupMap(map)
        addPlaceMarkers(map)

        val etSearch = view.findViewById<EditText>(R.id.et_map_search)
        val btnSearch = view.findViewById<TextView>(R.id.btn_search)
        val cardResults = view.findViewById<CardView>(R.id.card_results)
        val rvResults = view.findViewById<RecyclerView>(R.id.rv_search_results)
        val btnMyLocation = view.findViewById<CardView>(R.id.btn_my_location)

        rvResults.layoutManager = LinearLayoutManager(requireContext())
        setupSearch(map, etSearch, btnSearch, cardResults, rvResults)

        btnMyLocation.setOnClickListener { goToMyLocation() }
        requestLocationPermission()
    }

    private fun setupMap(map: MapView) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 10.0
        map.maxZoomLevel = 19.0

        map.controller.setZoom(16.0)
        map.controller.setCenter(TUSUR_CENTER)

        map.setUseDataConnection(true)
        map.isTilesScaledToDpi = true

        try {
            val overlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), map)
            overlay.enableMyLocation()
            myLocationOverlay = overlay
            map.overlays.add(overlay)
        } catch (_: Exception) {}
    }

    private fun addPlaceMarkers(map: MapView) {
        for (place in places) {
            val isTusur = place.name.contains("ТУСУР", true)
            val needsLabel = isTusur && (place.type == PlaceType.BUILDING || place.type == PlaceType.DORMITORY)

            val marker = Marker(map)
            marker.position = GeoPoint(place.lat, place.lon)
            marker.title = place.name
            marker.snippet = place.address
            if (place.description.isNotEmpty()) {
                marker.subDescription = place.description
            }

            if (needsLabel) {
                marker.icon = createLabeledMarkerIcon(place.shortName, place.type)
                marker.setAnchor(Marker.ANCHOR_CENTER, 1.0f)
            } else {
                marker.icon = createMarkerIcon(place.type)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }

            map.overlays.add(marker)
        }
        map.invalidate()
    }

    private fun getTypeColor(type: PlaceType): Int = when (type) {
        PlaceType.BUILDING -> Color.parseColor("#3D3DA8")
        PlaceType.DORMITORY -> Color.parseColor("#E65100")
        PlaceType.SPORTS -> Color.parseColor("#2E7D32")
        PlaceType.LIBRARY -> Color.parseColor("#1565C0")
        PlaceType.FOOD -> Color.parseColor("#C62828")
        PlaceType.TRANSPORT -> Color.parseColor("#00838F")
        PlaceType.SHOP -> Color.parseColor("#6A1B9A")
        PlaceType.MEDICINE -> Color.parseColor("#D32F2F")
        PlaceType.CULTURE -> Color.parseColor("#F57F17")
        PlaceType.OTHER -> Color.parseColor("#546E7A")
    }

    private fun createLabeledMarkerIcon(label: String, type: PlaceType): BitmapDrawable {
        val density = resources.displayMetrics.density
        val pinRadius = (14 * density)
        val pinSize = (pinRadius * 2).toInt()
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12 * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textBounds = android.graphics.Rect()
        textPaint.getTextBounds(label, 0, label.length, textBounds)

        val labelPadH = (8 * density).toInt()
        val labelPadV = (4 * density).toInt()
        val labelW = textBounds.width() + labelPadH * 2
        val labelH = textBounds.height() + labelPadV * 2
        val labelRadius = 6 * density
        val gap = (4 * density).toInt()

        val totalW = maxOf(labelW, pinSize) + 4
        val totalH = labelH + gap + pinSize + 4

        val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val typeColor = getTypeColor(type)

        // — Плашка с надписью —
        val labelLeft = (totalW - labelW) / 2f
        val labelTop = 0f
        val labelRect = RectF(labelLeft, labelTop, labelLeft + labelW, labelTop + labelH)

        // Тень плашки
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        shadowPaint.color = Color.parseColor("#30000000")
        canvas.drawRoundRect(
            RectF(labelRect.left + 1, labelRect.top + 2, labelRect.right + 1, labelRect.bottom + 2),
            labelRadius, labelRadius, shadowPaint
        )

        // Фон плашки
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        bgPaint.color = typeColor
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, bgPaint)

        // Обводка
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        strokePaint.color = Color.WHITE
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.5f * density
        canvas.drawRoundRect(labelRect, labelRadius, labelRadius, strokePaint)

        // Текст
        val textX = totalW / 2f
        val textY = labelTop + labelPadV + textBounds.height()
        canvas.drawText(label, textX, textY, textPaint)

        // — Маркер-точка —
        val pinCx = totalW / 2f
        val pinCy = labelH + gap + pinRadius

        // Тень
        shadowPaint.color = Color.parseColor("#40000000")
        canvas.drawCircle(pinCx, pinCy + 2f, pinRadius * 0.85f, shadowPaint)

        // Круг
        bgPaint.color = typeColor
        canvas.drawCircle(pinCx, pinCy, pinRadius * 0.85f, bgPaint)

        // Белая точка внутри
        bgPaint.color = Color.WHITE
        canvas.drawCircle(pinCx, pinCy, pinRadius * 0.35f, bgPaint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun createMarkerIcon(type: PlaceType): BitmapDrawable {
        val size = (36 * resources.displayMetrics.density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Тень
        paint.color = Color.parseColor("#40000000")
        canvas.drawCircle(size / 2f, size / 2f + 2f, size / 3f, paint)

        // Цвет по типу
        paint.color = getTypeColor(type)
        canvas.drawCircle(size / 2f, size / 2f - 1f, size / 3f, paint)

        // Белая точка
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f - 1f, size / 7f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun setupSearch(
        map: MapView, etSearch: EditText, btnSearch: TextView,
        cardResults: CardView, rvResults: RecyclerView
    ) {
        val searchAction = {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val results = searchPlaces(query)
                if (results.isNotEmpty()) {
                    rvResults.adapter = SearchResultAdapter(results) { place ->
                        map.controller.animateTo(GeoPoint(place.lat, place.lon))
                        map.controller.setZoom(18.0)
                        cardResults.visibility = View.GONE
                        hideKeyboard(etSearch)
                    }
                    cardResults.visibility = View.VISIBLE
                } else {
                    cardResults.visibility = View.GONE
                }
            } else {
                cardResults.visibility = View.GONE
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { searchAction() }
        })

        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { searchAction(); hideKeyboard(etSearch); true } else false
        }
        btnSearch.setOnClickListener { searchAction(); hideKeyboard(etSearch) }

        map.setOnTouchListener { _, _ ->
            if (cardResults.visibility == View.VISIBLE) cardResults.visibility = View.GONE
            false
        }
    }

    private fun searchPlaces(query: String): List<MapPlace> {
        val q = query.lowercase()
        return places.filter {
            it.name.lowercase().contains(q) ||
            it.shortName.lowercase().contains(q) ||
            it.address.lowercase().contains(q) ||
            it.description.lowercase().contains(q) ||
            it.type.name.lowercase().contains(q)
        }.sortedBy {
            // ТУСУР в приоритете
            if (it.name.contains("ТУСУР", true)) 0 else 1
        }
    }

    private fun goToMyLocation() {
        val map = mapView ?: return
        val loc = myLocationOverlay?.myLocation
        if (loc != null) {
            map.controller.animateTo(loc)
            map.controller.setZoom(17.0)
        } else {
            map.controller.animateTo(TUSUR_CENTER)
            map.controller.setZoom(16.0)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        mapView?.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        try {
            myLocationOverlay?.disableMyLocation()
            myLocationOverlay = null
            mapView?.overlays?.clear()
            mapView?.onDetach()
        } catch (_: Exception) {}
        mapView = null
        super.onDestroyView()
    }
}

class SearchResultAdapter(
    private val items: List<MapFragment.MapPlace>,
    private val onClick: (MapFragment.MapPlace) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvIcon: TextView = view.findViewById(R.id.tv_result_icon)
        val tvName: TextView = view.findViewById(R.id.tv_result_name)
        val tvAddress: TextView = view.findViewById(R.id.tv_result_address)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = items[i]
        h.tvName.text = item.name
        h.tvAddress.text = item.address
        h.tvIcon.text = when (item.type) {
            MapFragment.PlaceType.BUILDING -> "\uD83C\uDFEB"
            MapFragment.PlaceType.DORMITORY -> "\uD83C\uDFE0"
            MapFragment.PlaceType.SPORTS -> "\u26BD"
            MapFragment.PlaceType.LIBRARY -> "\uD83D\uDCDA"
            MapFragment.PlaceType.FOOD -> "\uD83C\uDF7D"
            MapFragment.PlaceType.TRANSPORT -> "\uD83D\uDE8C"
            MapFragment.PlaceType.SHOP -> "\uD83D\uDED2"
            MapFragment.PlaceType.MEDICINE -> "\u2695"
            MapFragment.PlaceType.CULTURE -> "\uD83C\uDFAD"
            MapFragment.PlaceType.OTHER -> "\uD83D\uDCCD"
        }
        h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
