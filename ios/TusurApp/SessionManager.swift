import Foundation

class SessionManager: ObservableObject {
    @Published var isLoggedIn: Bool {
        didSet { UserDefaults.standard.set(isLoggedIn, forKey: "is_logged_in") }
    }
    @Published var sdoLoggedIn: Bool {
        didSet { UserDefaults.standard.set(sdoLoggedIn, forKey: "sdo_logged_in") }
    }

    init() {
        self.isLoggedIn  = UserDefaults.standard.bool(forKey: "is_logged_in")
        self.sdoLoggedIn = UserDefaults.standard.bool(forKey: "sdo_logged_in")
    }

    func logout() {
        isLoggedIn  = false
        sdoLoggedIn = false
        // Clear cookies
        if let cookies = HTTPCookieStorage.shared.cookies {
            cookies.forEach { HTTPCookieStorage.shared.deleteCookie($0) }
        }
        // Clear cached data
        UserDefaults.standard.removeObject(forKey: "cached_schedule")
        UserDefaults.standard.removeObject(forKey: "cached_courses")
    }

    // MARK: - Profile login (profile.tusur.ru)

    func loginProfile(email: String, password: String) async throws -> Bool {
        let loginUrl = "https://profile.tusur.ru/users/sign_in"

        // Step 1: GET — CSRF token
        let (getData, _) = try await urlSession.data(from: URL(string: loginUrl)!)
        let html = String(data: getData, encoding: .utf8) ?? ""
        let token = HtmlParser.extractInputValue(html: html, name: "authenticity_token")

        // Step 2: POST
        var request = URLRequest(url: URL(string: loginUrl)!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "authenticity_token": token,
            "user[email]": email,
            "user[password]": password,
            "user[remember_me]": "1"
        ].map { "\($0.key)=\(urlEncode($0.value))" }.joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        let (_, response) = try await urlSession.data(for: request)
        let finalUrl = (response as? HTTPURLResponse)?.url?.absoluteString
            ?? response.url?.absoluteString ?? loginUrl

        return !finalUrl.contains("sign_in")
    }

    // MARK: - SDO login (sdo.tusur.ru)

    func loginSdo(login: String, password: String) async throws -> Bool {
        let loginUrl = "https://sdo.tusur.ru/login/index.php"

        // Step 1: GET — logintoken
        let (getData, _) = try await urlSession.data(from: URL(string: loginUrl)!)
        let html = String(data: getData, encoding: .utf8) ?? ""
        let loginToken = HtmlParser.extractInputValue(html: html, name: "logintoken")

        // Step 2: POST
        var request = URLRequest(url: URL(string: loginUrl)!)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let body = [
            "username": login,
            "password": password,
            "logintoken": loginToken,
            "anchor": ""
        ].map { "\($0.key)=\(urlEncode($0.value))" }.joined(separator: "&")
        request.httpBody = body.data(using: .utf8)

        let (_, response) = try await urlSession.data(for: request)
        let finalUrl = (response as? HTTPURLResponse)?.url?.absoluteString
            ?? response.url?.absoluteString ?? loginUrl

        return !finalUrl.contains("login/index.php")
    }

    // MARK: - Schedule

    func fetchSchedule() async throws -> [Lesson] {
        let url = URL(string: "https://timetable.tusur.ru/faculties/fsu/groups/444-1")!
        var request = URLRequest(url: url)
        request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
        request.setValue("ru-RU,ru;q=0.9", forHTTPHeaderField: "Accept-Language")

        let (data, _) = try await urlSession.data(for: request)
        let html = String(data: data, encoding: .utf8) ?? ""
        let lessons = HtmlParser.parseSchedule(html: html)

        if !lessons.isEmpty {
            if let encoded = try? JSONEncoder().encode(lessons) {
                UserDefaults.standard.set(encoded, forKey: "cached_schedule")
            }
        }
        return lessons
    }

    func cachedSchedule() -> [Lesson] {
        guard let data = UserDefaults.standard.data(forKey: "cached_schedule"),
              let lessons = try? JSONDecoder().decode([Lesson].self, from: data) else { return [] }
        return lessons
    }

    // MARK: - SDO Courses

    func fetchCourses() async throws -> [SdoCourse] {
        let pages = ["https://sdo.tusur.ru/my/", "https://sdo.tusur.ru/my/courses.php"]
        for page in pages {
            var request = URLRequest(url: URL(string: page)!)
            request.setValue("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)", forHTTPHeaderField: "User-Agent")
            let (data, _) = try await urlSession.data(for: request)
            let html = String(data: data, encoding: .utf8) ?? ""
            let courses = HtmlParser.parseCourses(html: html)
            if !courses.isEmpty {
                if let encoded = try? JSONEncoder().encode(courses) {
                    UserDefaults.standard.set(encoded, forKey: "cached_courses")
                }
                return courses
            }
        }
        return []
    }

    func cachedCourses() -> [SdoCourse] {
        guard let data = UserDefaults.standard.data(forKey: "cached_courses"),
              let courses = try? JSONDecoder().decode([SdoCourse].self, from: data) else { return [] }
        return courses
    }

    // MARK: - Helpers

    private var urlSession: URLSession {
        let config = URLSessionConfiguration.default
        config.httpCookieStorage = .shared
        config.httpCookieAcceptPolicy = .always
        return URLSession(configuration: config)
    }

    private func urlEncode(_ string: String) -> String {
        string.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed)?
            .replacingOccurrences(of: "+", with: "%2B")
            .replacingOccurrences(of: "&", with: "%26")
            .replacingOccurrences(of: "=", with: "%3D") ?? string
    }
}
