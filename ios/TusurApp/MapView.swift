import SwiftUI
import MapKit

struct MapView: View {
    @State private var searchText = ""
    @State private var region = MKCoordinateRegion(
        center: CLLocationCoordinate2D(latitude: 56.4884, longitude: 84.9480), // ТУСУР
        span: MKCoordinateSpan(latitudeDelta: 0.005, longitudeDelta: 0.005)
    )

    private let buildings = [
        TusurBuilding(name: "Главный корпус", coordinate: .init(latitude: 56.4884, longitude: 84.9480)),
        TusurBuilding(name: "УК ФЭТ", coordinate: .init(latitude: 56.4870, longitude: 84.9500)),
        TusurBuilding(name: "Библиотека", coordinate: .init(latitude: 56.4878, longitude: 84.9465)),
    ]

    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack {
                Text("ТУСУР")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(1)
                Spacer()
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 8)

            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.tusurTextSecondary)
                TextField("Найти аудиторию, корпус…", text: $searchText)
                    .font(.system(size: 14))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 10)
            .background(Color.tusurSurface)
            .cornerRadius(12)
            .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
            .padding(.horizontal, 20)
            .padding(.bottom, 12)

            // Map
            Map(coordinateRegion: $region, annotationItems: buildings) { building in
                MapAnnotation(coordinate: building.coordinate) {
                    VStack(spacing: 2) {
                        Image(systemName: "building.2.fill")
                            .font(.system(size: 18))
                            .foregroundColor(.tusurPrimary)
                        Text(building.name)
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(.tusurTextPrimary)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color.tusurSurface.opacity(0.9))
                            .cornerRadius(4)
                    }
                }
            }
            .cornerRadius(16)
            .padding(.horizontal, 16)
            .padding(.bottom, 8)
        }
        .background(Color.tusurBackground.ignoresSafeArea())
    }
}

struct TusurBuilding: Identifiable {
    let id = UUID()
    let name: String
    let coordinate: CLLocationCoordinate2D
}
