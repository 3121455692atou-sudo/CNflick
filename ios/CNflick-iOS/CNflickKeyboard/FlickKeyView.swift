import UIKit

final class FlickKeyView: UIView {
    private let bg = UIView()
    private let centerLabel = UILabel()
    private let leftLabel = UILabel()
    private let upLabel = UILabel()
    private let rightLabel = UILabel()
    private let downLabel = UILabel()

    private var startPoint: CGPoint = .zero
    private var currentDirection: FlickDirection = .center
    private var spec: DirectionalSpec?
    private var allowVertical = true

    var onCommit: ((String) -> Void)?
    var onTouchStateChanged: ((DirectionalSpec, FlickDirection, UIView) -> Void)?

    override init(frame: CGRect) {
        super.init(frame: frame)
        commonInit()
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        commonInit()
    }

    private func commonInit() {
        backgroundColor = .clear
        clipsToBounds = false

        bg.translatesAutoresizingMaskIntoConstraints = false
        bg.backgroundColor = UIColor(red: 0.93, green: 0.95, blue: 0.97, alpha: 1)
        bg.layer.cornerRadius = 11
        bg.layer.borderWidth = 1
        bg.layer.borderColor = UIColor(red: 0.65, green: 0.69, blue: 0.74, alpha: 1).cgColor
        addSubview(bg)

        [centerLabel, leftLabel, upLabel, rightLabel, downLabel].forEach {
            $0.translatesAutoresizingMaskIntoConstraints = false
            $0.textColor = UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 1)
            $0.textAlignment = .center
            bg.addSubview($0)
        }

        centerLabel.font = .systemFont(ofSize: 18, weight: .bold)
        [leftLabel, upLabel, rightLabel, downLabel].forEach {
            $0.font = .systemFont(ofSize: 10, weight: .regular)
            $0.textColor = UIColor(red: 0.29, green: 0.33, blue: 0.39, alpha: 1)
        }

        NSLayoutConstraint.activate([
            bg.leadingAnchor.constraint(equalTo: leadingAnchor),
            bg.trailingAnchor.constraint(equalTo: trailingAnchor),
            bg.topAnchor.constraint(equalTo: topAnchor),
            bg.bottomAnchor.constraint(equalTo: bottomAnchor),

            centerLabel.centerXAnchor.constraint(equalTo: bg.centerXAnchor),
            centerLabel.centerYAnchor.constraint(equalTo: bg.centerYAnchor),

            leftLabel.leadingAnchor.constraint(equalTo: bg.leadingAnchor, constant: 4),
            leftLabel.centerYAnchor.constraint(equalTo: bg.centerYAnchor),

            upLabel.centerXAnchor.constraint(equalTo: bg.centerXAnchor),
            upLabel.topAnchor.constraint(equalTo: bg.topAnchor, constant: 2),

            rightLabel.trailingAnchor.constraint(equalTo: bg.trailingAnchor, constant: -4),
            rightLabel.centerYAnchor.constraint(equalTo: bg.centerYAnchor),

            downLabel.centerXAnchor.constraint(equalTo: bg.centerXAnchor),
            downLabel.bottomAnchor.constraint(equalTo: bg.bottomAnchor, constant: -2)
        ])

        let pan = UIPanGestureRecognizer(target: self, action: #selector(onPan(_:)))
        pan.maximumNumberOfTouches = 1
        addGestureRecognizer(pan)
    }

    func configure(spec: DirectionalSpec, allowVertical: Bool = true) {
        self.spec = spec
        self.allowVertical = allowVertical
        centerLabel.text = spec.center.uppercasedIfAscii()
        leftLabel.text = spec.left.uppercasedIfAscii()
        upLabel.text = spec.up.uppercasedIfAscii()
        rightLabel.text = spec.right.uppercasedIfAscii()
        downLabel.text = spec.down.uppercasedIfAscii()

        let landscape = bounds.width > bounds.height
        centerLabel.font = .systemFont(ofSize: landscape ? 15 : 18, weight: .bold)
    }

    @objc
    private func onPan(_ pan: UIPanGestureRecognizer) {
        guard let spec else { return }
        let point = pan.location(in: self)

        switch pan.state {
        case .began:
            startPoint = point
            currentDirection = .center
            onTouchStateChanged?(spec, currentDirection, self)
        case .changed:
            currentDirection = detectDirection(dx: point.x - startPoint.x, dy: point.y - startPoint.y)
            onTouchStateChanged?(spec, currentDirection, self)
        case .ended:
            onCommit?(spec.text(for: currentDirection))
            onTouchStateChanged?(spec, .center, self)
        case .cancelled, .failed:
            onTouchStateChanged?(spec, .center, self)
        default:
            break
        }
    }

    private func detectDirection(dx: CGFloat, dy: CGFloat) -> FlickDirection {
        let threshold: CGFloat = 14
        if abs(dx) < threshold && abs(dy) < threshold { return .center }
        if !allowVertical {
            if dx > threshold { return .right }
            if dx < -threshold { return .left }
            return .center
        }
        if abs(dx) >= abs(dy) {
            return dx >= 0 ? .right : .left
        }
        return dy >= 0 ? .down : .up
    }
}

private extension String {
    func uppercasedIfAscii() -> String {
        guard contains(where: { $0.isASCII && $0.isLetter }) else { return self }
        return uppercased()
    }
}
