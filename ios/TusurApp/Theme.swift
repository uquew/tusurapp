import SwiftUI

extension Color {
    static let tusurPrimary     = Color(hex: "3D3DA8")
    static let tusurPrimaryLight = Color(hex: "5B5BD6")
    static let tusurPrimaryDark = Color(hex: "2A2A7A")
    static let tusurAccent      = Color(hex: "7B7BE8")
    static let tusurBackground  = Color(hex: "F0F0FA")
    static let tusurSurface     = Color.white
    static let tusurTextPrimary = Color(hex: "1A1A2E")
    static let tusurTextSecondary = Color(hex: "6B6B8A")
    static let tusurDivider     = Color(hex: "E0E0F0")
    static let tusurGradientStart = Color(hex: "E8E8F8")
    static let tusurGradientEnd   = Color(hex: "E5E5FA")

    init(hex: String) {
        let scanner = Scanner(string: hex)
        var rgb: UInt64 = 0
        scanner.scanHexInt64(&rgb)
        self.init(
            red:   Double((rgb >> 16) & 0xFF) / 255,
            green: Double((rgb >>  8) & 0xFF) / 255,
            blue:  Double( rgb        & 0xFF) / 255
        )
    }
}

struct TusurButtonStyle: ButtonStyle {
    var isEnabled: Bool = true

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 16, weight: .bold))
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 16)
            .background(isEnabled ? Color.tusurPrimary : Color.tusurPrimary.opacity(0.5))
            .cornerRadius(12)
            .scaleEffect(configuration.isPressed ? 0.97 : 1.0)
    }
}

struct TusurCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        content
            .background(Color.tusurSurface)
            .cornerRadius(16)
            .shadow(color: .black.opacity(0.08), radius: 4, y: 2)
    }
}
