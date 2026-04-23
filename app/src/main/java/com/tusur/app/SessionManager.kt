package com.tusur.app

import android.content.Context

class SessionManager(context: Context) {

    private val prefs = context.getSharedPreferences("tusur_session", Context.MODE_PRIVATE)

    var isLoggedIn: Boolean
        get() = prefs.getBoolean("is_logged_in", false)
        set(value) { prefs.edit().putBoolean("is_logged_in", value).apply() }

    var email: String
        get() = prefs.getString("user_email", "") ?: ""
        set(value) { prefs.edit().putString("user_email", value).apply() }

    var password: String
        get() = prefs.getString("user_password", "") ?: ""
        set(value) { prefs.edit().putString("user_password", value).apply() }

    // Номер группы для расписания, например "444-1"
    var group: String
        get() = prefs.getString("user_group", "") ?: ""
        set(value) { prefs.edit().putString("user_group", value).apply() }

    fun saveCookies(cookies: Map<String, String>) {
        val editor = prefs.edit()
        cookies.forEach { (k, v) -> editor.putString("cookie_$k", v) }
        editor.apply()
    }

    fun getCookies(): Map<String, String> =
        prefs.all
            .filter { it.key.startsWith("cookie_") }
            .mapKeys { it.key.removePrefix("cookie_") }
            .mapValues { it.value as String }

    fun clearSession() {
        prefs.edit().clear().apply()
    }
}
