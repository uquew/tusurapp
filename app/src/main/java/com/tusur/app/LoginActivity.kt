package com.tusur.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup

class LoginActivity : AppCompatActivity() {

    private lateinit var session: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        session = SessionManager(this)

        val tilEmail    = findViewById<TextInputLayout>(R.id.til_email)
        val tilPassword = findViewById<TextInputLayout>(R.id.til_password)
        val etEmail     = findViewById<TextInputEditText>(R.id.et_email)
        val etPassword  = findViewById<TextInputEditText>(R.id.et_password)
        val tvError     = findViewById<TextView>(R.id.tv_error)
        val btnLogin    = findViewById<MaterialButton>(R.id.btn_login)
        val tvForgot    = findViewById<TextView>(R.id.tv_forgot)

        btnLogin.setOnClickListener {
            val email    = etEmail.text?.toString()?.trim() ?: ""
            val password = etPassword.text?.toString() ?: ""

            tilEmail.error    = null
            tilPassword.error = null
            tvError.visibility = View.GONE

            if (email.isEmpty()) {
                tilEmail.error = "Введите электронную почту"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                tilPassword.error = "Введите пароль"
                return@setOnClickListener
            }

            btnLogin.isEnabled = false
            btnLogin.text = "Вход…"

            lifecycleScope.launch {
                when (performLogin(email, password)) {
                    LoginResult.SUCCESS -> {
                        session.isLoggedIn = true
                        session.email = email
                        session.password = password
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    }
                    LoginResult.WRONG_CREDENTIALS -> {
                        tvError.text = "Неверная почта или пароль"
                        tvError.visibility = View.VISIBLE
                        resetButton(btnLogin)
                    }
                    LoginResult.NETWORK_ERROR -> {
                        tvError.text = "Ошибка сети. Проверьте подключение к интернету"
                        tvError.visibility = View.VISIBLE
                        resetButton(btnLogin)
                    }
                }
            }
        }

        tvForgot.setOnClickListener {
            startActivity(
                Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://profile.tusur.ru/users/password/new"))
            )
        }
    }

    private fun resetButton(btn: MaterialButton) {
        btn.isEnabled = true
        btn.text = getString(R.string.btn_login)
    }

    private suspend fun performLogin(email: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            try {
                // Шаг 1: GET — получаем CSRF-токен и cookies сессии
                val getResponse = Jsoup.connect("https://profile.tusur.ru/users/sign_in")
                    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .timeout(15_000)
                    .execute()

                val csrfToken = getResponse.parse()
                    .selectFirst("input[name=authenticity_token]")
                    ?.attr("value") ?: ""
                val sessionCookies = getResponse.cookies()

                // Шаг 2: POST — отправляем учётные данные
                val postResponse = Jsoup.connect("https://profile.tusur.ru/users/sign_in")
                    .userAgent("Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36")
                    .timeout(15_000)
                    .cookies(sessionCookies)
                    .data("authenticity_token", csrfToken)
                    .data("user[email]", email)
                    .data("user[password]", password)
                    .data("user[remember_me]", "1")
                    .method(Connection.Method.POST)
                    .followRedirects(true)
                    .execute()

                // Если URL больше не содержит sign_in — вход успешен
                if (!postResponse.url().toString().contains("sign_in")) {
                    session.saveCookies(sessionCookies + postResponse.cookies())
                    LoginResult.SUCCESS
                } else {
                    LoginResult.WRONG_CREDENTIALS
                }
            } catch (e: Exception) {
                LoginResult.NETWORK_ERROR
            }
        }

    private enum class LoginResult { SUCCESS, WRONG_CREDENTIALS, NETWORK_ERROR }
}
