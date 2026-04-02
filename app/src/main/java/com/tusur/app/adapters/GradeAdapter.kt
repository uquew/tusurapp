package com.tusur.app.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.tusur.app.R

data class GradeItem(
    val subjectName: String,
    val kt1: String,
    val kt2: String,
    val ekz: String
)

class GradeAdapter(private val items: List<GradeItem>) :
    RecyclerView.Adapter<GradeAdapter.VH>() {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.tv_subject_name)
        val kt1:  TextView = view.findViewById(R.id.tv_grade_kt1)
        val kt2:  TextView = view.findViewById(R.id.tv_grade_kt2)
        val ekz:  TextView = view.findViewById(R.id.tv_grade_ekz)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_grade, parent, false))

    override fun onBindViewHolder(h: VH, i: Int) {
        val item = items[i]
        h.name.text = item.subjectName
        bindGrade(h.kt1, item.kt1)
        bindGrade(h.kt2, item.kt2)
        bindGrade(h.ekz, item.ekz)
    }

    private fun bindGrade(tv: TextView, value: String) {
        tv.text = value
        val num = value.toDoubleOrNull()
        when {
            num == null || value == "—" || value == "-" || value.isEmpty() -> {
                tv.setTextColor(Color.parseColor("#9E9EBF"))
                tv.setBackgroundResource(R.drawable.bg_grade_empty)
            }
            num >= 80 -> {
                tv.setTextColor(Color.parseColor("#2E7D32"))
                tv.setBackgroundResource(R.drawable.bg_grade_good)
            }
            num >= 50 -> {
                tv.setTextColor(Color.parseColor("#E65100"))
                tv.setBackgroundResource(R.drawable.bg_grade_ok)
            }
            else -> {
                tv.setTextColor(Color.parseColor("#C62828"))
                tv.setBackgroundResource(R.drawable.bg_grade_bad)
            }
        }
    }

    override fun getItemCount() = items.size
}
