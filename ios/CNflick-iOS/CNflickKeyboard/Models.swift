import Foundation

enum KeyboardMode {
    case pinyin
    case alpha
    case number
    case symbol
    case function
}

enum KeyZone {
    case shengmu
    case yunmu
}

enum FlickDirection {
    case center
    case left
    case up
    case right
    case down
}

struct DirectionalSpec {
    let center: String
    let left: String
    let up: String
    let right: String
    let down: String

    func text(for direction: FlickDirection) -> String {
        switch direction {
        case .center: return center
        case .left: return left
        case .up: return up
        case .right: return right
        case .down: return down
        }
    }
}

struct PinyinKey {
    let zone: KeyZone
    let spec: DirectionalSpec
}

enum KeyItem {
    case mode(String, KeyboardMode)
    case pinyin(PinyinKey)
    case flick(DirectionalSpec)
    case action(String, KeyboardAction)
    case enter
    case backspace
}

enum KeyboardAction {
    case space
    case switchKeyboard
    case settings
    case dismiss
    case arrowLeft
    case arrowRight
    case arrowUp
    case arrowDown
    case copy
    case paste
    case cut
    case home
    case end
}
