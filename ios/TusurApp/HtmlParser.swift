import Foundation

enum HtmlParser {

    // MARK: - Extract <input> value by name

    static func extractInputValue(html: String, name: String) -> String {
        // Matches: <input ... name="NAME" ... value="VALUE" ...>
        let patterns = [
            "name=\"\(name)\"[^>]*value=\"([^\"]*)\"",
            "value=\"([^\"]*)\"[^>]*name=\"\(name)\""
        ]
        for pattern in patterns {
            if let match = html.range(of: pattern, options: .regularExpression) {
                let sub = String(html[match])
                if let valRange = sub.range(of: "value=\"([^\"]*)\"", options: .regularExpression) {
                    let valStr = String(sub[valRange])
                    return valStr
                        .replacingOccurrences(of: "value=\"", with: "")
                        .replacingOccurrences(of: "\"", with: "")
                }
            }
        }
        return ""
    }

    // MARK: - Parse schedule from timetable.tusur.ru

    static func parseSchedule(html: String) -> [Lesson] {
        var lessons: [Lesson] = []
        var currentDay = ""

        let rows = extractTags(html: html, tag: "tr")
        for row in rows {
            let cells = extractTags(html: row, tag: "td")
            if cells.isEmpty { continue }

            let firstText = stripHtml(cells[0]).trimmingCharacters(in: .whitespacesAndNewlines)

            let dayPatterns = ["пн", "вт", "ср", "чт", "пт", "сб", "вс",
                               "января", "февраля", "марта", "апреля", "мая", "июня",
                               "июля", "августа", "сентября", "октября", "ноября", "декабря"]
            if dayPatterns.contains(where: { firstText.lowercased().contains($0) }) {
                currentDay = firstText
            }

            for i in cells.indices {
                let cellHtml = cells[i]
                let text = stripHtml(cellHtml)
                if text.count < 5 { continue }

                let type: String
                if text.contains("Лекция")       { type = "Лекция" }
                else if text.contains("Практика") { type = "Практика" }
                else if text.contains("Лабораторная") { type = "Лаб. работа" }
                else if text.contains("Курсовое") { type = "Курсовой" }
                else { continue }

                let time = i > 0 ? stripHtml(cells[i - 1]).trimmingCharacters(in: .whitespacesAndNewlines)
                                 : stripHtml(cells[0]).trimmingCharacters(in: .whitespacesAndNewlines)

                // Extract subject: text before the type keyword
                var subject = text.components(separatedBy: type).first?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if subject.count > 80 { subject = String(subject.suffix(80)) }

                // Extract room from links containing "auditorium"
                let room = extractLinkTexts(html: cellHtml, hrefContains: "auditorium").joined(separator: ", ")

                // Extract teacher from links containing "teacher"
                let teacher = extractLinkTexts(html: cellHtml, hrefContains: "teacher").joined(separator: ", ")

                if !subject.isEmpty {
                    lessons.append(Lesson(
                        time: time, subject: subject, type: type,
                        room: room, teacher: teacher,
                        day: currentDay.isEmpty ? "Текущая неделя" : currentDay
                    ))
                }
            }
        }
        return lessons
    }

    // MARK: - Parse courses from sdo.tusur.ru

    static func parseCourses(html: String) -> [SdoCourse] {
        var courses: [SdoCourse] = []
        var seen = Set<String>()

        // Universal fallback: all links to /course/view.php
        let links = extractAllLinks(html: html)
        for (href, text) in links {
            if href.contains("course/view.php") {
                let name = text.trimmingCharacters(in: .whitespacesAndNewlines)
                if name.count > 2 && !seen.contains(name) {
                    seen.insert(name)
                    courses.append(SdoCourse(name: name, category: "Курс"))
                }
            }
        }
        return courses
    }

    // MARK: - HTML helpers

    static func stripHtml(_ html: String) -> String {
        html.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&nbsp;", with: " ")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
            .replacingOccurrences(of: "&quot;", with: "\"")
    }

    static func extractTags(html: String, tag: String) -> [String] {
        var results: [String] = []
        let pattern = "<\(tag)[^>]*>([\\s\\S]*?)</\(tag)>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return [] }
        let matches = regex.matches(in: html, range: NSRange(html.startIndex..., in: html))
        for match in matches {
            if let range = Range(match.range, in: html) {
                results.append(String(html[range]))
            }
        }
        return results
    }

    static func extractLinkTexts(html: String, hrefContains keyword: String) -> [String] {
        var results: [String] = []
        let pattern = "<a[^>]*href=\"[^\"]*\(keyword)[^\"]*\"[^>]*>([^<]*)</a>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return [] }
        let matches = regex.matches(in: html, range: NSRange(html.startIndex..., in: html))
        for match in matches {
            if let range = Range(match.range(at: 1), in: html) {
                let text = String(html[range]).trimmingCharacters(in: .whitespacesAndNewlines)
                if !text.isEmpty { results.append(text) }
            }
        }
        return results
    }

    static func extractAllLinks(html: String) -> [(href: String, text: String)] {
        var results: [(String, String)] = []
        let pattern = "<a[^>]*href=\"([^\"]*)\"[^>]*>([^<]*)</a>"
        guard let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive) else { return [] }
        let matches = regex.matches(in: html, range: NSRange(html.startIndex..., in: html))
        for match in matches {
            if let hrefRange = Range(match.range(at: 1), in: html),
               let textRange = Range(match.range(at: 2), in: html) {
                results.append((String(html[hrefRange]), String(html[textRange])))
            }
        }
        return results
    }
}
