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

    // Центр между главным корпусом и спорткомплексом
    private val TUSUR_CENTER = GeoPoint(56.4613, 84.9530)

    data class MapPlace(
        val name: String,
        val shortName: String,
        val address: String,
        val lat: Double,
        val lon: Double,
        val type: PlaceType,
        val description: String = ""
    )

    enum class PlaceType { BUILDING, LIBRARY }

    // Учебные корпуса и спорткомплекс ТУСУР
    private val places = listOf(
        MapPlace("Главный корпус ТУСУР", "ГК", "пр. Ленина, 40",
            56.4703, 84.9505, PlaceType.BUILDING, "Главный учебный корпус"),
        MapPlace("Корпус ФЭТ ТУСУР", "ФЭТ", "ул. Вершинина, 74",
            56.4676, 84.9483, PlaceType.BUILDING, "Факультет электронной техники"),
        MapPlace("Радиотехнический корпус ТУСУР", "РК", "ул. Вершинина, 47",
            56.4687, 84.9455, PlaceType.BUILDING, "Радиотехнический факультет"),
        MapPlace("Учебно-лабораторный корпус ТУСУР", "УЛК", "ул. Красноармейская, 146",
            56.4712, 84.9532, PlaceType.BUILDING, "Учебно-лабораторный корпус"),
        MapPlace("Спорткомплекс ТУСУР", "Спорт", "ул. Красноармейская",
            56.45236, 84.959653, PlaceType.BUILDING, "Спортивный комплекс ТУСУР")
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

        val etSearch    = view.findViewById<EditText>(R.id.et_map_search)
        val btnSearch   = view.findViewById<TextView>(R.id.btn_search)
        val cardResults = view.findViewById<CardView>(R.id.card_results)
        val rvResults   = view.findViewById<RecyclerView>(R.id.rv_search_results)
        val btnMyLocation = view.findViewById<CardView>(R.id.btn_my_location)

        setupMap(map, cardResults)
        addPlaceMarkers(map)

        rvResults.layoutManager = LinearLayoutManager(requireContext())
        setupSearch(map, etSearch, btnSearch, cardResults, rvResults)

        btnMyLocation.setOnClickListener { goToMyLocation() }
        requestLocationPermission()
    }

    private fun setupMap(map: MapView, cardResults: CardView) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 10.0
        map.maxZoomLevel = 19.0

        map.controller.setZoom(15.0)
        map.controller.setCenter(TUSUR_CENTER)

        map.setUseDataConnection(true)
        map.isTilesScaledToDpi = true

        // Единственный touch listener: запрещаем родителю перехватывать жесты
        // и закрываем результаты поиска при касании карты
        map.setOnTouchListener { v, _ ->
            v.parent?.requestDisallowInterceptTouchEvent(true)
            if (cardResults.visibility == View.VISIBLE) cardResults.visibility = View.GONE
            false
        }

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
            val needsLabel = isTusur && place.type == PlaceType.BUILDING

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
        PlaceType.LIBRARY  -> Color.parseColor("#1565C0")
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
        // touch listener для карты устанавливается в setupMap с учётом cardResults
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
            MapFragment.PlaceType.LIBRARY  -> "\uD83D\uDCDA"
        }
        h.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount() = items.size
}
