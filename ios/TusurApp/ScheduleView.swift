import SwiftUI

struct ScheduleView: View {
    @EnvironmentObject var session: SessionManager

    @State private var lessons: [Lesson] = []
    @State private var isLoading = false
    @State private var errorMessage: String?

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
            .padding(.bottom, 4)

            Text("Расписание")
                .font(.system(size: 26, weight: .bold))
                .foregroundColor(.tusurTextPrimary)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 20)
                .padding(.top, 4)
                .padding(.bottom, 16)

            if isLoading && lessons.isEmpty {
                Spacer()
                ProgressView()
                    .tint(.tusurPrimary)
                Spacer()
            } else if let error = errorMessage, lessons.isEmpty {
                Spacer()
                Text(error)
                    .font(.system(size: 15))
                    .foregroundColor(.tusurTextSecondary)
                    .multilineTextAlignment(.center)
                    .padding(32)
                Spacer()
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(Array(lessons.enumerated()), id: \.element.id) { index, lesson in
                            let showDay = index == 0 || lessons[index - 1].day != lesson.day
                            LessonCard(lesson: lesson, showDay: showDay)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 16)
                }
            }
        }
        .background(Color.tusurBackground.ignoresSafeArea())
        .onAppear { loadSchedule() }
    }

    private func loadSchedule() {
        let cached = session.cachedSchedule()
        if !cached.isEmpty {
            lessons = cached
        }

        isLoading = true
        Task {
            do {
                let fresh = try await session.fetchSchedule()
                await MainActor.run {
                    isLoading = false
                    if !fresh.isEmpty {
                        lessons = fresh
                    } else if lessons.isEmpty {
                        errorMessage = "Нет данных"
                    }
                }
            } catch {
                await MainActor.run {
                    isLoading = false
                    if lessons.isEmpty {
                        errorMessage = "Нет интернета. Данные не загружены."
                    }
                }
            }
        }
    }
}

struct LessonCard: View {
    let lesson: Lesson
    let showDay: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            if showDay {
                Text(lesson.day.uppercased())
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.tusurPrimary)
                    .tracking(0.5)
                    .padding(.top, 16)
                    .padding(.bottom, 8)
            }

            HStack(alignment: .top, spacing: 0) {
                // Time column
                let times = lesson.time.split(separator: " ")
                VStack(spacing: 4) {
                    Text(times.first.map(String.init) ?? "")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.tusurPrimary)
                    Rectangle()
                        .fill(Color.tusurDivider)
                        .frame(width: 2)
                        .frame(maxHeight: .infinity)
                    Text(times.count > 1 ? String(times[1]) : "")
                        .font(.system(size: 11))
                        .foregroundColor(.tusurTextSecondary)
                }
                .frame(width: 44)
                .padding(.trailing, 14)

                // Divider
                Rectangle()
                    .fill(Color.tusurPrimary)
                    .frame(width: 3)
                    .padding(.trailing, 14)
                    .padding(.vertical, 2)

                // Content
                VStack(alignment: .leading, spacing: 4) {
                    Text(lesson.type.uppercased())
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.tusurAccent)
                        .tracking(0.5)

                    Text(lesson.subject)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.tusurTextPrimary)
                        .lineSpacing(2)

                    HStack(spacing: 4) {
                        Text("📍")
                            .font(.system(size: 11))
                        Text(lesson.room.isEmpty ? "—" : lesson.room)
                            .font(.system(size: 12))
                            .foregroundColor(.tusurTextSecondary)
                    }
                    .padding(.top, 2)

                    HStack(spacing: 4) {
                        Text("👤")
                            .font(.system(size: 11))
                        Text(lesson.teacher.isEmpty ? "—" : lesson.teacher)
                            .font(.system(size: 12))
                            .foregroundColor(.tusurTextSecondary)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(14)
            .background(Color.tusurSurface)
            .cornerRadius(16)
            .shadow(color: .black.opacity(0.06), radius: 3, y: 2)
            .padding(.bottom, 8)
        }
    }
}
