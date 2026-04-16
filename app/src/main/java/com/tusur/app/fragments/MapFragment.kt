package com.tusur.app.fragments

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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

    private lateinit var mapView: MapView
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
        mapView = view.findViewById(R.id.map_view)
        val etSearch = view.findViewById<EditText>(R.id.et_map_search)
        val btnSearch = view.findViewById<TextView>(R.id.btn_search)
        val cardResults = view.findViewById<CardView>(R.id.card_results)
        val rvResults = view.findViewById<RecyclerView>(R.id.rv_search_results)
        val btnMyLocation = view.findViewById<CardView>(R.id.btn_my_location)

        setupMap()
        addPlaceMarkers()

        rvResults.layoutManager = LinearLayoutManager(requireContext())
        setupSearch(etSearch, btnSearch, cardResults, rvResults)

        btnMyLocation.setOnClickListener { goToMyLocation() }
        requestLocationPermission()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 10.0
        mapView.maxZoomLevel = 19.0

        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(TUSUR_CENTER)

        mapView.setUseDataConnection(true)
        mapView.isTilesScaledToDpi = true

        try {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView)
            myLocationOverlay?.enableMyLocation()
            mapView.overlays.add(myLocationOverlay)
        } catch (_: Exception) {}
    }

    private fun addPlaceMarkers() {
        for (place in places) {
            val marker = Marker(mapView)
            marker.position = GeoPoint(place.lat, place.lon)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = place.name
            marker.snippet = place.address
            if (place.description.isNotEmpty()) {
                marker.subDescription = place.description
            }
            marker.icon = createMarkerIcon(place.type)
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
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
        paint.color = when (type) {
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
        canvas.drawCircle(size / 2f, size / 2f - 1f, size / 3f, paint)

        // Белая точка
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f - 1f, size / 7f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun setupSearch(
        etSearch: EditText, btnSearch: TextView,
        cardResults: CardView, rvResults: RecyclerView
    ) {
        val searchAction = {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val results = searchPlaces(query)
                if (results.isNotEmpty()) {
                    rvResults.adapter = SearchResultAdapter(results) { place ->
                        mapView.controller.animateTo(GeoPoint(place.lat, place.lon))
                        mapView.controller.setZoom(18.0)
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

        mapView.setOnTouchListener { _, _ ->
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
        val loc = myLocationOverlay?.myLocation
        if (loc != null) {
            mapView.controller.animateTo(loc)
            mapView.controller.setZoom(17.0)
        } else {
            mapView.controller.animateTo(TUSUR_CENTER)
            mapView.controller.setZoom(16.0)
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

    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onDestroyView() { myLocationOverlay?.disableMyLocation(); mapView.onDetach(); super.onDestroyView() }
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
