package com.tusur.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.tusur.app.fragments.*

class MainActivity : AppCompatActivity() {

    private val fragments = listOf(
        MapFragment(),
        SdoFragment(),
        ScheduleFragment(),
        ProfileFragment()
    )

    private val navIds = listOf(
        R.id.nav_map,
        R.id.nav_sdo,
        R.id.nav_schedule,
        R.id.nav_profile
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SessionManager(this).isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.view_pager)
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottom_nav)

        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount() = fragments.size
            override fun createFragment(position: Int): Fragment = fragments[position]
        }

        // Свайп → меняет таб
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                bottomNav.selectedItemId = navIds[position]
            }
        })

        // Таб → меняет страницу
        bottomNav.setOnItemSelectedListener { item ->
            val index = navIds.indexOf(item.itemId)
            if (index != -1) viewPager.setCurrentItem(index, true)
            true
        }

        // Стартовая страница — СДО
        viewPager.setCurrentItem(1, false)
    }
}