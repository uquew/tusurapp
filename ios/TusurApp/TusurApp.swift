import SwiftUI

@main
struct TusurApp: App {
    @StateObject private var session = SessionManager()

    var body: some Scene {
        WindowGroup {
            Group {
                if session.isLoggedIn {
                    ContentView()
                } else {
                    LoginView()
                }
            }
            .environmentObject(session)
        }
    }
}
