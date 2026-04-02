import Foundation

struct Lesson: Codable, Identifiable {
    var id: String { "\(day)-\(time)-\(subject)" }
    let time: String
    let subject: String
    let type: String
    let room: String
    let teacher: String
    let day: String
}

struct SdoCourse: Codable, Identifiable {
    var id: String { name }
    let name: String
    let category: String
}

struct GradeItem: Identifiable {
    var id: String { subjectName }
    let subjectName: String
    let kt1: String
    let kt2: String
    let ekz: String
}
