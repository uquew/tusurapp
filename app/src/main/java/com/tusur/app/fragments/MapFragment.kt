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
import androidx.core.app.ActivityCompat
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

    // Центр Томска / ТУСУР
    private val TUSUR_CENTER = GeoPoint(56.4884, 68.0178)

    // Корпуса и объекты ТУСУР
    data class MapPlace(
        val name: String,
        val shortName: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val type: PlaceType,
        val description: String = ""
    )

    enum class PlaceType { BUILDING, DORMITORY, SPORTS, LIBRARY, FOOD, OTHER }

    private val places = listOf(
        // Учебные корпуса
        MapPlace("Главный корпус ТУСУР", "ГК", "пр. Ленина, 40",
            56.4884, 68.0178, PlaceType.BUILDING, "Главный учебный корпус"),
        MapPlace("Учебный корпус ТУСУР (ФЭТ)", "УК ФЭТ", "ул. Вершинина, 74",
            56.4870, 68.0120, PlaceType.BUILDING, "Факультет электронной техники"),
        MapPlace("Учебный корпус ТУСУР (КИБЭВС)", "УК КИБЭВС", "ул. Красноармейская, 146",
            56.4868, 68.0218, PlaceType.BUILDING, "Кафедра КИБЭВС"),
        MapPlace("Радиотехнический корпус", "РТК", "ул. Вершинина, 47",
            56.4862, 68.0148, PlaceType.BUILDING, "Радиотехнический факультет"),
        MapPlace("Корпус ФСУ", "ФСУ", "ул. Красноармейская, 146",
            56.4872, 68.0225, PlaceType.BUILDING, "Факультет систем управления"),
        MapPlace("Учебно-лабораторный корпус", "УЛК", "ул. Вершинина, 64",
            56.4878, 68.0105, PlaceType.BUILDING, "Лаборатории и практикумы"),
        MapPlace("Корпус ФВС", "ФВС", "ул. Ленина, 40, стр. 2",
            56.4880, 68.0190, PlaceType.BUILDING, "Факультет вычислительных систем"),
        MapPlace("Научная библиотека ТУСУР", "НБ", "ул. Вершинина, 47",
            56.4858, 68.0145, PlaceType.LIBRARY, "Научная библиотека"),
        MapPlace("Бизнес-инкубатор ТУСУР", "БИ", "ул. Вершинина, 72",
            56.4875, 68.0110, PlaceType.BUILDING, "Студенческий бизнес-инкубатор"),

        // Общежития
        MapPlace("Общежитие №1", "Общ. 1", "ул. Вершинина, 48",
            56.4856, 68.0132, PlaceType.DORMITORY),
        MapPlace("Общежитие №2", "Общ. 2", "ул. Вершинина, 46",
            56.4852, 68.0125, PlaceType.DORMITORY),
        MapPlace("Общежитие №3", "Общ. 3", "ул. Лыткина, 8",
            56.4830, 68.0155, PlaceType.DORMITORY),
        MapPlace("Общежитие №4", "Общ. 4", "ул. Вершинина, 44",
            56.4848, 68.0118, PlaceType.DORMITORY),
        MapPlace("Общежитие №5", "Общ. 5", "пер. Спортивный, 9",
            56.4840, 68.0100, PlaceType.DORMITORY),
        MapPlace("Общежитие №6", "Общ. 6", "ул. 19 Гвардейской дивизии, 11а",
            56.4800, 68.0040, PlaceType.DORMITORY),

        // Спорт
        MapPlace("Спортивный зал ТУСУР", "Спортзал", "ул. Вершинина, 48а",
            56.4860, 68.0138, PlaceType.SPORTS, "Спортивный комплекс"),
        MapPlace("Стадион ТУСУР", "Стадион", "ул. Вершинина, 48",
            56.4854, 68.0140, PlaceType.SPORTS),

        // Еда
        MapPlace("Столовая ТУСУР (ГК)", "Столовая ГК", "пр. Ленина, 40",
            56.4882, 68.0175, PlaceType.FOOD, "Столовая в главном корпусе"),
        MapPlace("Буфет УЛК", "Буфет УЛК", "ул. Вершинина, 64",
            56.4876, 68.0108, PlaceType.FOOD),

        // Важные места Томска рядом
        MapPlace("Площадь Ленина", "Пл. Ленина", "пл. Ленина",
            56.4885, 68.0230, PlaceType.OTHER),
        MapPlace("Томский политехнический университет", "ТПУ", "пр. Ленина, 30",
            56.4690, 68.0310, PlaceType.OTHER, "Соседний вуз"),
        MapPlace("Томский государственный университет", "ТГУ", "пр. Ленина, 36",
            56.4715, 68.0415, PlaceType.OTHER, "Классический университет")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        // Конфигурация OSMDroid
        val ctx = requireContext()
        Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = ctx.packageName
        // Увеличиваем размер кэша для оффлайн работы (256 MB)
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

        // Настройка карты
        setupMap()

        // Добавляем маркеры корпусов
        addPlaceMarkers()

        // Поиск
        rvResults.layoutManager = LinearLayoutManager(requireContext())
        setupSearch(etSearch, btnSearch, cardResults, rvResults)

        // Моё местоположение
        btnMyLocation.setOnClickListener { goToMyLocation() }

        // Запрашиваем разрешение на геолокацию
        requestLocationPermission()
    }

    private fun setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setMultiTouchControls(true)
        mapView.minZoomLevel = 10.0
        mapView.maxZoomLevel = 19.0

        // Начальная позиция — ТУСУР
        mapView.controller.setZoom(16.0)
        mapView.controller.setCenter(TUSUR_CENTER)

        // Включаем кэширование тайлов (оффлайн)
        mapView.setUseDataConnection(true) // сначала загрузим, потом работает оффлайн
        mapView.isTilesScaledToDpi = true

        // Оверлей «моё местоположение»
        try {
            myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(requireContext()), mapView)
            myLocationOverlay?.enableMyLocation()
            mapView.overlays.add(myLocationOverlay)
        } catch (e: Exception) {
            // Если нет разрешения — пропускаем
        }
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

            // Цветной маркер по типу
            marker.icon = createMarkerIcon(place.type)

            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    private fun createMarkerIcon(type: PlaceType): BitmapDrawable {
        val size = 40
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Цвет по типу
        paint.color = when (type) {
            PlaceType.BUILDING -> Color.parseColor("#3D3DA8")   // фиолетовый — корпуса
            PlaceType.DORMITORY -> Color.parseColor("#E65100")  // оранжевый — общежития
            PlaceType.SPORTS -> Color.parseColor("#2E7D32")     // зелёный — спорт
            PlaceType.LIBRARY -> Color.parseColor("#1565C0")    // синий — библиотека
            PlaceType.FOOD -> Color.parseColor("#C62828")       // красный — еда
            PlaceType.OTHER -> Color.parseColor("#6B6B8A")      // серый — прочее
        }

        // Рисуем pin
        canvas.drawCircle(size / 2f, size / 2f - 4f, size / 3f, paint)

        // Белая точка внутри
        paint.color = Color.WHITE
        canvas.drawCircle(size / 2f, size / 2f - 4f, size / 6f, paint)

        return BitmapDrawable(resources, bitmap)
    }

    private fun setupSearch(
        etSearch: EditText,
        btnSearch: TextView,
        cardResults: CardView,
        rvResults: RecyclerView
    ) {
        val searchAction = {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val results = searchPlaces(query)
                if (results.isNotEmpty()) {
                    rvResults.adapter = SearchResultAdapter(results) { place ->
                        // Переходим к месту
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
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchAction()
                hideKeyboard(etSearch)
                true
            } else false
        }

        btnSearch.setOnClickListener {
            searchAction()
            hideKeyboard(etSearch)
        }

        // Закрытие результатов при нажатии на карту
        mapView.setOnTouchListener { _, _ ->
            if (cardResults.visibility == View.VISIBLE) {
                cardResults.visibility = View.GONE
            }
            false
        }
    }

    private fun searchPlaces(query: String): List<MapPlace> {
        val q = query.lowercase()
        return places.filter {
            it.name.lowercase().contains(q) ||
            it.shortName.lowercase().contains(q) ||
            it.address.lowercase().contains(q) ||
            it.description.lowercase().contains(q)
        }
    }

    private fun goToMyLocation() {
        val loc = myLocationOverlay?.myLocation
        if (loc != null) {
            mapView.controller.animateTo(loc)
            mapView.controller.setZoom(17.0)
        } else {
            // Если нет геолокации — центрируем на ТУСУР
            mapView.controller.animateTo(TUSUR_CENTER)
            mapView.controller.setZoom(16.0)
        }
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1001
            )
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onDestroyView() {
        myLocationOverlay?.disableMyLocation()
        mapView.onDetach()
        super.onDestroyView()
    }
}

// Адаптер для результатов поиска
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
            MapFragment.PlaceType.BUILDING -> "\uD83C\uDFEB"   // school
            MapFragment.PlaceType.DORMITORY -> "\uD83C\uDFE0"  // house
            MapFragment.PlaceType.SPORTS -> "\u26BD"            // football
            MapFragment.PlaceType.LIBRARY -> "\uD83D\uDCDA"    // books
            MapFragment.PlaceType.FOOD -> "\uD83C\uDF7D"       // fork & knife
            MapFragment.PlaceType.OTHER -> "\uD83D\uDCCD"      // pin
        }
        h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
