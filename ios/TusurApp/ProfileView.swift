import SwiftUI

struct ProfileView: View {
    @State private var selectedTab = 0 // 0=КТ1, 1=КТ2, 2=ЭКЗ
    @State private var searchText = ""

    private let grades = [
        GradeItem(subjectName: "Математический анализ", kt1: "5", kt2: "5", ekz: "5"),
        GradeItem(subjectName: "Физика",                kt1: "5", kt2: "4", ekz: "5"),
        GradeItem(subjectName: "Программирование",      kt1: "5", kt2: "5", ekz: "5"),
        GradeItem(subjectName: "Дискретная математика", kt1: "4", kt2: "5", ekz: "4"),
        GradeItem(subjectName: "История",               kt1: "5", kt2: "5", ekz: "5"),
        GradeItem(subjectName: "Иностранный язык",      kt1: "4", kt2: "4", ekz: "5"),
    ]

    private var filteredGrades: [GradeItem] {
        if searchText.isEmpty { return grades }
        return grades.filter { $0.subjectName.localizedCaseInsensitiveContains(searchText) }
    }

    private var avgScore: Double {
        let allScores = grades.flatMap { [
            Double($0.kt1) ?? 0, Double($0.kt2) ?? 0, Double($0.ekz) ?? 0
        ]}
        return allScores.reduce(0, +) / Double(allScores.count)
    }

    var body: some View {
        VStack(spacing: 0) {
            // Top bar
            HStack {
                Text("ТУСУР")
                    .font(.system(size: 22, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(1)
                Spacer()
                Text("ЯП")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white)
                    .frame(width: 36, height: 36)
                    .background(Color.tusurPrimary)
                    .clipShape(Circle())
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .padding(.bottom, 12)

            ScrollView {
                VStack(spacing: 20) {
                    // Title
                    Text("Успеваемость")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.tusurTextPrimary)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    // Circle progress
                    ZStack {
                        Circle()
                            .stroke(Color.tusurDivider, lineWidth: 8)
                            .frame(width: 150, height: 150)
                        Circle()
                            .trim(from: 0, to: avgScore / 5.0)
                            .stroke(Color.tusurPrimary, style: StrokeStyle(lineWidth: 8, lineCap: .round))
                            .frame(width: 150, height: 150)
                            .rotationEffect(.degrees(-90))
                        VStack(spacing: 2) {
                            Text(String(format: "%.1f", avgScore).replacingOccurrences(of: ".", with: ","))
                                .font(.system(size: 40, weight: .bold))
                                .foregroundColor(.tusurTextPrimary)
                            Text("Средний балл")
                                .font(.system(size: 12))
                                .foregroundColor(.tusurTextSecondary)
                        }
                    }
                    .frame(height: 180)

                    // Search + tab filters
                    HStack(spacing: 8) {
                        HStack {
                            TextField("Найти предмет…", text: $searchText)
                                .font(.system(size: 13))
                        }
                        .padding(.horizontal, 12)
                        .frame(height: 40)
                        .background(Color.tusurSurface)
                        .cornerRadius(10)
                        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)

                        FilterTab(title: "КТ1", isActive: selectedTab == 0) { selectedTab = 0 }
                        FilterTab(title: "КТ2", isActive: selectedTab == 1) { selectedTab = 1 }
                        FilterTab(title: "ЭКЗ", isActive: selectedTab == 2) { selectedTab = 2 }
                    }

                    // Grades list
                    VStack(spacing: 8) {
                        ForEach(filteredGrades) { grade in
                            GradeCard(grade: grade, selectedTab: selectedTab)
                        }
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 16)
            }
        }
        .background(Color.tusurBackground.ignoresSafeArea())
    }
}

struct FilterTab: View {
    let title: String
    let isActive: Bool
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: isActive ? .bold : .regular))
                .foregroundColor(isActive ? .tusurPrimary : .tusurTextSecondary)
                .padding(.horizontal, 12)
                .frame(height: 36)
                .background(isActive ? Color.tusurPrimary.opacity(0.12) : Color.tusurSurface)
                .cornerRadius(10)
        }
    }
}

struct GradeCard: View {
    let grade: GradeItem
    let selectedTab: Int

    private var displayScore: String {
        switch selectedTab {
        case 0:  return grade.kt1
        case 1:  return grade.kt2
        default: return grade.ekz
        }
    }

    var body: some View {
        HStack {
            Text(grade.subjectName)
                .font(.system(size: 14, weight: .medium))
                .foregroundColor(.tusurTextPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Text(displayScore)
                .font(.system(size: 18, weight: .bold))
                .foregroundColor(.tusurPrimary)
                .frame(width: 40, height: 40)
                .background(Color.tusurPrimary.opacity(0.1))
                .cornerRadius(10)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.tusurSurface)
        .cornerRadius(14)
        .shadow(color: .black.opacity(0.05), radius: 2, y: 1)
    }
}
