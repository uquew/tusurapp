import SwiftUI

struct LoginView: View {
    @EnvironmentObject var session: SessionManager

    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                Spacer().frame(height: 72)

                Text("ТУСУР")
                    .font(.system(size: 48, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(2)

                Text("Войти в личный кабинет")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.tusurTextPrimary)
                    .padding(.top, 28)

                Text("Доступ к курсам и материалам\nдля обучения")
                    .font(.system(size: 14))
                    .foregroundColor(.tusurTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(.top, 8)

                VStack(spacing: 16) {
                    TusurTextField(
                        placeholder: "Электронная почта",
                        text: $email,
                        keyboardType: .emailAddress
                    )

                    TusurSecureField(
                        placeholder: "Пароль",
                        text: $password
                    )
                }
                .padding(.top, 44)

                if let error = errorMessage {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundColor(.red)
                        .multilineTextAlignment(.center)
                        .padding(.top, 10)
                }

                Button(action: login) {
                    if isLoading {
                        ProgressView()
                            .tint(.white)
                    } else {
                        Text("Войти")
                    }
                }
                .buttonStyle(TusurButtonStyle(isEnabled: !isLoading))
                .disabled(isLoading)
                .padding(.top, 28)

                Button("Забыли пароль?") {
                    if let url = URL(string: "https://profile.tusur.ru/users/password/new") {
                        UIApplication.shared.open(url)
                    }
                }
                .font(.system(size: 14))
                .foregroundColor(.tusurPrimary)
                .padding(.top, 18)

                Spacer()
            }
            .padding(.horizontal, 32)
        }
        .background(
            LinearGradient(
                colors: [.tusurGradientStart, .tusurGradientEnd],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()
        )
    }

    private func login() {
        guard !email.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "Введите электронную почту"; return
        }
        guard !password.isEmpty else {
            errorMessage = "Введите пароль"; return
        }

        errorMessage = nil
        isLoading = true

        Task {
            do {
                let success = try await session.loginProfile(email: email, password: password)
                await MainActor.run {
                    isLoading = false
                    if success {
                        session.isLoggedIn = true
                    } else {
                        errorMessage = "Неверная почта или пароль"
                    }
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    errorMessage = "Ошибка сети. Проверьте подключение"
                }
            }
        }
    }
}

// MARK: - Custom text fields

struct TusurTextField: View {
    let placeholder: String
    @Binding var text: String
    var keyboardType: UIKeyboardType = .default

    var body: some View {
        TextField(placeholder, text: $text)
            .keyboardType(keyboardType)
            .autocapitalization(.none)
            .disableAutocorrection(true)
            .padding(14)
            .background(Color.tusurSurface)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color.tusurDivider, lineWidth: 1)
            )
    }
}

struct TusurSecureField: View {
    let placeholder: String
    @Binding var text: String
    @State private var isVisible = false

    var body: some View {
        HStack {
            if isVisible {
                TextField(placeholder, text: $text)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
            } else {
                SecureField(placeholder, text: $text)
            }
            Button(action: { isVisible.toggle() }) {
                Image(systemName: isVisible ? "eye.slash.fill" : "eye.fill")
                    .foregroundColor(.tusurTextSecondary)
                    .font(.system(size: 16))
            }
        }
        .padding(14)
        .background(Color.tusurSurface)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color.tusurDivider, lineWidth: 1)
        )
    }
}
