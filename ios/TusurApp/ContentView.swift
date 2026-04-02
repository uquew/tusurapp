import SwiftUI

struct ContentView: View {
    @EnvironmentObject var session: SessionManager
    @State private var selectedTab = 1 // СДО по умолчанию

    var body: some View {
        TabView(selection: $selectedTab) {
            MapView()
                .tabItem {
                    Image(systemName: "map")
                    Text("Карта")
                }
                .tag(0)

            SdoView()
                .tabItem {
                    Image(systemName: "book")
                    Text("СДО")
                }
                .tag(1)

            ScheduleView()
                .tabItem {
                    Image(systemName: "calendar")
                    Text("Расписание")
                }
                .tag(2)

            ProfileView()
                .tabItem {
                    Image(systemName: "person")
                    Text("Профиль")
                }
                .tag(3)
        }
        .accentColor(.tusurPrimary)
    }
}
