import SwiftUI

struct SdoView: View {
    @EnvironmentObject var session: SessionManager

    @State private var login = ""
    @State private var password = ""
    @State private var isLoggingIn = false
    @State private var loginError: String?

    @State private var courses: [SdoCourse] = []
    @State private var isLoadingCourses = false
    @State private var coursesError: String?

    var body: some View {
        if session.sdoLoggedIn {
            coursesView
        } else {
            loginForm
        }
    }

    // MARK: - Login form

    private var loginForm: some View {
        ZStack {
            LinearGradient(
                colors: [.tusurGradientStart, .tusurGradientEnd],
                startPoint: .topLeading, endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            VStack(spacing: 0) {
                Text("ТУСУР")
                    .font(.system(size: 32, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(2)
                    .padding(.bottom, 32)

                VStack(spacing: 0) {
                    Text("Войти в СДО ТУСУР")
                        .font(.system(size: 22, weight: .bold))
                        .foregroundColor(.tusurTextPrimary)

                    Text("Доступ к курсам и материалам\nдля обучения")
                        .font(.system(size: 13))
                        .foregroundColor(.tusurTextSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.top, 8)
                        .padding(.bottom, 24)

                    TusurTextField(placeholder: "Логин", text: $login)

                    TusurSecureField(placeholder: "Пароль", text: $password)
                        .padding(.top, 12)

                    if let error = loginError {
                        Text(error)
                            .font(.system(size: 13))
                            .foregroundColor(.red)
                            .multilineTextAlignment(.center)
                            .padding(.top, 10)
                    }

                    Button(action: doLogin) {
                        if isLoggingIn {
                            ProgressView().tint(.white)
                        } else {
                            Text("Войти")
                        }
                    }
                    .buttonStyle(TusurButtonStyle(isEnabled: !isLoggingIn))
                    .disabled(isLoggingIn)
                    .padding(.top, 20)

                    Button("Забыли пароль?") {
                        if let url = URL(string: "https://sdo.tusur.ru/login/forgot_password.php") {
                            UIApplication.shared.open(url)
                        }
                    }
                    .font(.system(size: 14))
                    .foregroundColor(.tusurPrimary)
                    .padding(.top, 16)
                }
                .padding(28)
                .background(Color.tusurSurface)
                .cornerRadius(20)
                .shadow(color: .black.opacity(0.1), radius: 8, y: 4)
                .padding(.horizontal, 24)
            }
        }
    }

    // MARK: - Courses view

    private var coursesView: some View {
        VStack(spacing: 0) {
            HStack {
                Text("ТУСУР")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(1)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 4)

            Text("Курсы СДО")
                .font(.system(size: 26, weight: .bold))
                .foregroundColor(.tusurTextPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 16)

            if isLoadingCourses && courses.isEmpty {
                Spacer()
                ProgressView().tint(.tusurPrimary)
                Spacer()
            } else if let error = coursesError, courses.isEmpty {
                Spacer()
                Text(error)
                    .font(.system(size: 15))
                    .foregroundColor(.tusurTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(32)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(courses) { course in
                            CourseCard(course: course)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
        }
        .background(Color.tusurBackground.ignoresSafeArea())
        .onAppear { loadCourses() }
    }

    // MARK: - Actions

    private func doLogin() {
        guard !login.trimmingCharacters(in: .whitespaces).isEmpty else {
            loginError = "Введите логин"; return
        }
        guard !password.isEmpty else {
            loginError = "Введите пароль"; return
        }

        loginError = nil
        isLoggingIn = true

        Task {
            do {
                let success = try await session.loginSdo(login: login, password: password)
                await MainActor.run {
                    isLoggingIn = false
                    if success {
                        session.sdoLoggedIn = true
                    } else {
                        loginError = "Неверный логин или пароль"
                    }
                }
            } catch {
                await MainActor.run {
                    isLoggingIn = false
                    loginError = "Ошибка сети. Проверьте подключение"
                }
            }
        }
    }

    private func loadCourses() {
        let cached = session.cachedCourses()
        if !cached.isEmpty { courses = cached }

        isLoadingCourses = true
        Task {
            do {
                let fresh = try await session.fetchCourses()
                await MainActor.run {
                    isLoadingCourses = false
                    if !fresh.isEmpty {
                        courses = fresh
                    } else if courses.isEmpty {
                        coursesError = "Курсы не найдены"
                    }
                }
            } catch {
                await MainActor.run {
                    isLoadingCourses = false
                    if courses.isEmpty {
                        coursesError = "Нет интернета. Данные не загружены."
                    }
                }
            }
        }
    }
}

struct CourseCard: View {
    let course: SdoCourse

    var body: some View {
        HStack(spacing: 0) {
            Rectangle()
                .fill(Color.tusurPrimary)
                .frame(width: 4)
                .padding(.vertical, 2)
                .padding(.trailing, 14)

            VStack(alignment: .leading, spacing: 4) {
                Text(course.category.uppercased())
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.tusurAccent)
                    .tracking(0.5)

                Text(course.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(.tusurTextPrimary)
                    .lineSpacing(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(14)
        .background(Color.tusurSurface)
        .cornerRadius(16)
        .shadow(color: .black.opacity(0.06), radius: 3, y: 2)
    }
}
