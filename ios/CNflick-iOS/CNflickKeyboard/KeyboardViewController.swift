import UIKit

final class KeyboardViewController: UIInputViewController {
    private let root = UIView()
    private let candidateBar = UIView()
    private let composingLabel = UILabel()
    private let candidateScroll = UIScrollView()
    private let candidateStack = UIStackView()
    private let expandButton = UIButton(type: .system)
    private let keyboardContainer = UIStackView()
    private let overlay = UIView()

    private let hintCenter = UILabel()
    private let hintLeft = UILabel()
    private let hintUp = UILabel()
    private let hintRight = UILabel()
    private let hintDown = UILabel()

    private var mode: KeyboardMode = .pinyin { didSet { renderKeyboard() } }
    private var shengmuPart: String?
    private var composedSyllables: [String] = []
    private var allCandidates: [String] = []

    private var heavyImpact = UIImpactFeedbackGenerator(style: .heavy)
    private var rigidImpact = UIImpactFeedbackGenerator(style: .rigid)

    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
        renderKeyboard()
        refreshCandidates()
        heavyImpact.prepare()
        rigidImpact.prepare()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        overlay.frame = view.bounds
    }

    private func setupUI() {
        view.backgroundColor = UIColor(red: 0.68, green: 0.72, blue: 0.78, alpha: 1)

        root.translatesAutoresizingMaskIntoConstraints = false
        root.backgroundColor = .clear
        view.addSubview(root)

        candidateBar.translatesAutoresizingMaskIntoConstraints = false
        candidateBar.backgroundColor = UIColor(red: 0.84, green: 0.86, blue: 0.90, alpha: 1)
        root.addSubview(candidateBar)

        composingLabel.translatesAutoresizingMaskIntoConstraints = false
        composingLabel.font = .systemFont(ofSize: 12)
        composingLabel.textColor = UIColor(red: 0.42, green: 0.46, blue: 0.50, alpha: 1)
        composingLabel.text = "拼音"
        candidateBar.addSubview(composingLabel)

        candidateScroll.translatesAutoresizingMaskIntoConstraints = false
        candidateScroll.showsHorizontalScrollIndicator = false
        candidateBar.addSubview(candidateScroll)

        candidateStack.axis = .horizontal
        candidateStack.spacing = 8
        candidateStack.translatesAutoresizingMaskIntoConstraints = false
        candidateScroll.addSubview(candidateStack)

        expandButton.translatesAutoresizingMaskIntoConstraints = false
        expandButton.setTitle("˅", for: .normal)
        expandButton.titleLabel?.font = .systemFont(ofSize: 22, weight: .semibold)
        expandButton.setTitleColor(UIColor(red: 0.29, green: 0.33, blue: 0.39, alpha: 1), for: .normal)
        candidateBar.addSubview(expandButton)

        keyboardContainer.translatesAutoresizingMaskIntoConstraints = false
        keyboardContainer.axis = .vertical
        keyboardContainer.spacing = 4
        root.addSubview(keyboardContainer)

        overlay.backgroundColor = .clear
        overlay.isUserInteractionEnabled = false
        view.addSubview(overlay)

        [hintCenter, hintLeft, hintUp, hintRight, hintDown].forEach {
            $0.textAlignment = .center
            $0.font = .systemFont(ofSize: 16, weight: .bold)
            $0.textColor = .white
            $0.backgroundColor = UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 0.95)
            $0.layer.cornerRadius = 12
            $0.clipsToBounds = true
            $0.isHidden = true
            overlay.addSubview($0)
        }
        hintCenter.backgroundColor = UIColor(red: 0.15, green: 0.39, blue: 0.92, alpha: 0.95)

        let barHeight: CGFloat = isLandscape ? 40 : 50
        NSLayoutConstraint.activate([
            root.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 4),
            root.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -4),
            root.topAnchor.constraint(equalTo: view.topAnchor, constant: 4),
            root.bottomAnchor.constraint(equalTo: view.bottomAnchor, constant: -6),

            candidateBar.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            candidateBar.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            candidateBar.topAnchor.constraint(equalTo: root.topAnchor),
            candidateBar.heightAnchor.constraint(equalToConstant: barHeight),

            composingLabel.leadingAnchor.constraint(equalTo: candidateBar.leadingAnchor, constant: 8),
            composingLabel.trailingAnchor.constraint(equalTo: candidateBar.trailingAnchor, constant: -8),
            composingLabel.topAnchor.constraint(equalTo: candidateBar.topAnchor, constant: 4),

            expandButton.trailingAnchor.constraint(equalTo: candidateBar.trailingAnchor, constant: -2),
            expandButton.bottomAnchor.constraint(equalTo: candidateBar.bottomAnchor, constant: -2),
            expandButton.widthAnchor.constraint(equalToConstant: 36),
            expandButton.heightAnchor.constraint(equalToConstant: 28),

            candidateScroll.leadingAnchor.constraint(equalTo: candidateBar.leadingAnchor, constant: 8),
            candidateScroll.trailingAnchor.constraint(equalTo: expandButton.leadingAnchor, constant: -4),
            candidateScroll.topAnchor.constraint(equalTo: composingLabel.bottomAnchor, constant: 2),
            candidateScroll.bottomAnchor.constraint(equalTo: candidateBar.bottomAnchor, constant: -4),

            candidateStack.leadingAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.leadingAnchor),
            candidateStack.trailingAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.trailingAnchor),
            candidateStack.topAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.topAnchor),
            candidateStack.bottomAnchor.constraint(equalTo: candidateScroll.contentLayoutGuide.bottomAnchor),
            candidateStack.heightAnchor.constraint(equalTo: candidateScroll.frameLayoutGuide.heightAnchor),

            keyboardContainer.leadingAnchor.constraint(equalTo: root.leadingAnchor),
            keyboardContainer.trailingAnchor.constraint(equalTo: root.trailingAnchor),
            keyboardContainer.topAnchor.constraint(equalTo: candidateBar.bottomAnchor, constant: 4),
            keyboardContainer.bottomAnchor.constraint(equalTo: root.bottomAnchor)
        ])
    }

    private var isLandscape: Bool {
        view.bounds.width > view.bounds.height
    }

    private func renderKeyboard() {
        keyboardContainer.arrangedSubviews.forEach { $0.removeFromSuperview() }
        for row in rows(for: mode) {
            keyboardContainer.addArrangedSubview(makeRow(row))
        }
    }

    private func makeRow(_ items: [KeyItem]) -> UIView {
        let row = UIStackView()
        row.axis = .horizontal
        row.spacing = 4
        row.distribution = .fillEqually

        for item in items {
            row.addArrangedSubview(makeCell(for: item))
        }
        return row
    }

    private func makeCell(for item: KeyItem) -> UIView {
        switch item {
        case .mode(let title, let target):
            return modeButton(title: title, target: target)
        case .pinyin(let key):
            let v = FlickKeyView()
            v.configure(spec: key.spec, allowVertical: true)
            v.onCommit = { [weak self] text in self?.onPinyinInput(zone: key.zone, text: text) }
            v.onTouchStateChanged = { [weak self] spec, dir, keyView in self?.showHint(spec: spec, direction: dir, anchor: keyView) }
            return v
        case .flick(let spec):
            let v = FlickKeyView()
            v.configure(spec: spec, allowVertical: mode != .alpha)
            v.onCommit = { [weak self] text in self?.commit(text) }
            v.onTouchStateChanged = { [weak self] spec, dir, keyView in self?.showHint(spec: spec, direction: dir, anchor: keyView) }
            return v
        case .action(let title, let action):
            return actionButton(title: title, action: action)
        case .enter:
            return primaryButton(title: "回车") { [weak self] in self?.onEnter() }
        case .backspace:
            return actionButton(title: "⌫", action: .dismiss, handler: { [weak self] in self?.onBackspace() })
        }
    }

    private func modeButton(title: String, target: KeyboardMode) -> UIButton {
        let selected = mode == target
        return styledButton(
            title: title,
            bg: selected ? UIColor(red: 0.96, green: 0.62, blue: 0.04, alpha: 1) : UIColor(red: 0.78, green: 0.82, blue: 0.86, alpha: 1),
            fg: selected ? .white : UIColor(red: 0.12, green: 0.16, blue: 0.22, alpha: 1),
            bold: false
        ) { [weak self] in
            self?.impactStrong()
            self?.mode = target
        }
    }

    private func actionButton(title: String, action: KeyboardAction, handler: (() -> Void)? = nil) -> UIButton {
        styledButton(
            title: title,
            bg: UIColor(red: 0.78, green: 0.82, blue: 0.86, alpha: 1),
            fg: UIColor(red: 0.12, green: 0.16, blue: 0.22, alpha: 1),
            bold: false
        ) { [weak self] in
            self?.impactLight()
            if let handler {
                handler()
            } else {
                self?.handle(action: action, title: title)
            }
        }
    }

    private func primaryButton(title: String, handler: @escaping () -> Void) -> UIButton {
        styledButton(
            title: title,
            bg: UIColor(red: 0.09, green: 0.47, blue: 1.0, alpha: 1),
            fg: .white,
            bold: true,
            handler: { [weak self] in self?.impactStrong(); handler() }
        )
    }

    private func styledButton(
        title: String,
        bg: UIColor,
        fg: UIColor,
        bold: Bool,
        handler: @escaping () -> Void
    ) -> UIButton {
        let b = UIButton(type: .system)
        b.setTitle(title.uppercasedIfAscii(), for: .normal)
        b.setTitleColor(fg, for: .normal)
        b.titleLabel?.font = .systemFont(ofSize: 15, weight: bold ? .bold : .regular)
        b.backgroundColor = bg
        b.layer.cornerRadius = 11
        b.layer.borderColor = UIColor(red: 0.62, green: 0.66, blue: 0.72, alpha: 1).cgColor
        b.layer.borderWidth = 1
        b.addAction(UIAction { _ in handler() }, for: .touchUpInside)
        return b
    }

    private func rows(for mode: KeyboardMode) -> [[KeyItem]] {
        switch mode {
        case .pinyin: return pinyinRows()
        case .alpha: return alphaRows()
        case .number: return numberRows()
        case .symbol: return symbolRows()
        case .function: return functionRows()
        }
    }

    private func pinyinRows() -> [[KeyItem]] {
        let keys: [PinyinKey] = [
            .init(zone: .shengmu, spec: .init(center: "b", left: "p", up: "m", right: "f", down: "w")),
            .init(zone: .shengmu, spec: .init(center: "d", left: "t", up: "n", right: "l", down: "y")),
            .init(zone: .shengmu, spec: .init(center: "g", left: "k", up: "h", right: "j", down: "q")),
            .init(zone: .shengmu, spec: .init(center: "x", left: "zh", up: "ch", right: "sh", down: "r")),
            .init(zone: .shengmu, spec: .init(center: "z", left: "c", up: "s", right: "ü", down: "v")),
            .init(zone: .yunmu, spec: .init(center: "a", left: "ai", up: "an", right: "ang", down: "ao")),
            .init(zone: .yunmu, spec: .init(center: "e", left: "ei", up: "en", right: "eng", down: "ou")),
            .init(zone: .yunmu, spec: .init(center: "i", left: "ie", up: "in", right: "ing", down: "iu")),
            .init(zone: .yunmu, spec: .init(center: "ia", left: "iong", up: "ian", right: "iang", down: "iao")),
            .init(zone: .yunmu, spec: .init(center: "u", left: "ui", up: "un", right: "ong", down: "uo")),
            .init(zone: .yunmu, spec: .init(center: "ua", left: "uai", up: "uan", right: "uang", down: "o")),
            .init(zone: .yunmu, spec: .init(center: "。", left: "，", up: "！", right: "？", down: "er"))
        ]

        return [
            [.mode("☆123", .number), .pinyin(keys[0]), .pinyin(keys[1]), .pinyin(keys[2]), .backspace],
            [.mode("ABC", .alpha), .pinyin(keys[3]), .pinyin(keys[4]), .pinyin(keys[5]), .action("空格", .space)],
            [.mode("拼音", .pinyin), .pinyin(keys[6]), .pinyin(keys[7]), .pinyin(keys[8]), .enter],
            [.mode("符号", .symbol), .pinyin(keys[9]), .pinyin(keys[10]), .pinyin(keys[11]), .mode("功能", .function)]
        ]
    }

    private func alphaRows() -> [[KeyItem]] {
        let specs: [DirectionalSpec] = [
            .init(center: "b", left: "a", up: "", right: "c", down: ""),
            .init(center: "e", left: "d", up: "", right: "f", down: ""),
            .init(center: "h", left: "g", up: "", right: "i", down: ""),
            .init(center: "k", left: "j", up: "", right: "l", down: ""),
            .init(center: "n", left: "m", up: "", right: "o", down: ""),
            .init(center: "q", left: "p", up: "", right: "r", down: ""),
            .init(center: "t", left: "s", up: "", right: "u", down: ""),
            .init(center: "w", left: "v", up: "", right: "x", down: ""),
            .init(center: "z", left: "y", up: "", right: "'", down: "")
        ]
        return [
            [.mode("☆123", .number), .flick(specs[0]), .flick(specs[1]), .flick(specs[2]), .backspace],
            [.mode("ABC", .alpha), .flick(specs[3]), .flick(specs[4]), .flick(specs[5]), .action("空格", .space)],
            [.mode("拼音", .pinyin), .flick(specs[6]), .flick(specs[7]), .flick(specs[8]), .enter],
            [.mode("符号", .symbol), .action(",", .dismiss), .action("大写锁定", .dismiss), .action(".", .dismiss), .mode("功能", .function)]
        ]
    }

    private func numberRows() -> [[KeyItem]] {
        [
            [.mode("☆123", .number), .action("1", .dismiss), .action("2", .dismiss), .action("3", .dismiss), .backspace],
            [.mode("ABC", .alpha), .action("4", .dismiss), .action("5", .dismiss), .action("6", .dismiss), .action("空格", .space)],
            [.mode("拼音", .pinyin), .action("7", .dismiss), .action("8", .dismiss), .action("9", .dismiss), .enter],
            [.mode("符号", .symbol), .action("(", .dismiss), .action("0", .dismiss), .action(")", .dismiss), .mode("功能", .function)]
        ]
    }

    private func symbolRows() -> [[KeyItem]] {
        let s: [DirectionalSpec] = [
            .init(center: "，", left: ",", up: "。", right: ".", down: "、"),
            .init(center: "？", left: "?", up: "！", right: "!", down: "…"),
            .init(center: "：", left: ":", up: "；", right: ";", down: "·"),
            .init(center: "（", left: "(", up: "）", right: ")", down: "—"),
            .init(center: "“", left: "\"", up: "”", right: "'", down: "‘"),
            .init(center: "’", left: "@", up: "#", right: "&", down: "%"),
            .init(center: "￥", left: "$", up: "€", right: "£", down: "¥"),
            .init(center: "【", left: "[", up: "】", right: "]", down: "{"),
            .init(center: "}", left: "<", up: "《", right: ">", down: "》"),
            .init(center: "、", left: "\\", up: "/", right: "|", down: "_"),
            .init(center: "+", left: "-", up: "=", right: "*", down: "^"),
            .init(center: "~", left: "`", up: "§", right: "©", down: "™")
        ]
        return [
            [.mode("☆123", .number), .flick(s[0]), .flick(s[1]), .flick(s[2]), .backspace],
            [.mode("ABC", .alpha), .flick(s[3]), .flick(s[4]), .flick(s[5]), .action("空格", .space)],
            [.mode("拼音", .pinyin), .flick(s[6]), .flick(s[7]), .flick(s[8]), .enter],
            [.mode("符号", .symbol), .flick(s[9]), .flick(s[10]), .flick(s[11]), .mode("功能", .function)]
        ]
    }

    private func functionRows() -> [[KeyItem]] {
        [
            [.mode("☆123", .number), .action("复制", .copy), .action("↑", .arrowUp), .action("粘贴", .paste), .backspace],
            [.mode("ABC", .alpha), .action("←", .arrowLeft), .action("●", .dismiss), .action("→", .arrowRight), .action("空格", .space)],
            [.mode("拼音", .pinyin), .action("剪切", .cut), .action("↓", .arrowDown), .action("HOME", .home), .enter],
            [.mode("符号", .symbol), .action("🌐", .switchKeyboard), .action("END", .end), .action("设置", .settings), .mode("功能", .function)]
        ]
    }

    private func onPinyinInput(zone: KeyZone, text: String) {
        if ["，", "。", "？", "！", ",", ".", "?", "!"].contains(text) {
            commit(text)
            resetComposing()
            return
        }

        let normalized = text.replacingOccurrences(of: "ü", with: "v").lowercased()
        let actualZone: KeyZone
        if zone == .shengmu && (normalized == "v" || normalized == "er" || ["a", "e", "i", "o", "u"].contains(String(normalized.prefix(1)))) {
            actualZone = .yunmu
        } else {
            actualZone = zone
        }

        switch actualZone {
        case .shengmu:
            shengmuPart = normalized
        case .yunmu:
            let full = (shengmuPart ?? "") + normalized
            shengmuPart = nil
            composedSyllables.append(full)
        }

        refreshCandidates()
    }

    private func refreshCandidates() {
        let composing = displayComposing()
        composingLabel.text = composing.isEmpty ? "拼音" : composing
        composingLabel.textColor = composing.isEmpty ? UIColor(red: 0.42, green: 0.46, blue: 0.50, alpha: 1) : UIColor(red: 0.11, green: 0.31, blue: 0.84, alpha: 1)

        candidateStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        let query = queryPinyin()
        allCandidates = candidateList(for: query)

        for cand in allCandidates.prefix(12) {
            let b = UIButton(type: .system)
            b.setTitle(cand, for: .normal)
            b.setTitleColor(UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 1), for: .normal)
            b.titleLabel?.font = .systemFont(ofSize: 18, weight: .regular)
            b.addAction(UIAction { [weak self] _ in self?.commitCandidate(cand) }, for: .touchUpInside)
            candidateStack.addArrangedSubview(b)
        }
    }

    private func queryPinyin() -> String {
        (composedSyllables + (shengmuPart.map { [$0] } ?? [])).joined(separator: "")
    }

    private func displayComposing() -> String {
        let left = composedSyllables.joined(separator: "'")
        if let s = shengmuPart, !s.isEmpty {
            return left.isEmpty ? s : left + "'" + s
        }
        return left
    }

    private func candidateList(for query: String) -> [String] {
        guard !query.isEmpty else { return [] }
        let map: [String: [String]] = [
            "zhong": ["中", "种", "重", "钟", "终"],
            "guo": ["国", "过", "果", "锅", "郭"],
            "ren": ["人", "认", "任", "仁", "忍"],
            "ni": ["你", "呢", "泥", "拟", "逆"],
            "wo": ["我", "握", "窝", "卧", "沃"],
            "ta": ["他", "她", "它", "塔"],
            "de": ["的", "得", "德"],
            "shi": ["是", "时", "事", "市", "使"]
        ]
        return map[query] ?? [query]
    }

    private func commitCandidate(_ text: String) {
        commit(text)
        resetComposing()
    }

    private func onEnter() {
        let raw = queryPinyin()
        if !raw.isEmpty {
            commit(raw)
            resetComposing()
            return
        }
        textDocumentProxy.insertText("\n")
    }

    private func onBackspace() {
        if shengmuPart != nil {
            shengmuPart = nil
            refreshCandidates()
            return
        }
        if !composedSyllables.isEmpty {
            composedSyllables.removeLast()
            refreshCandidates()
            return
        }
        textDocumentProxy.deleteBackward()
    }

    private func handle(action: KeyboardAction, title: String? = nil) {
        switch action {
        case .space:
            if let first = allCandidates.first {
                commitCandidate(first)
            } else if !queryPinyin().isEmpty {
                commit(queryPinyin())
                resetComposing()
            } else {
                commit(" ")
            }
        case .switchKeyboard:
            advanceToNextInputMode()
        case .settings:
            break
        case .dismiss:
            if let t = title, !t.isEmpty, t != "大写锁定", t != "●" {
                commit(t)
            }
        case .arrowLeft:
            textDocumentProxy.adjustTextPosition(byCharacterOffset: -1)
        case .arrowRight:
            textDocumentProxy.adjustTextPosition(byCharacterOffset: 1)
        case .arrowUp, .arrowDown:
            break
        case .copy, .paste, .cut:
            break
        case .home, .end:
            break
        }
    }

    private func commit(_ text: String) {
        if text == "⌫" {
            onBackspace()
            return
        }
        if ["1","2","3","4","5","6","7","8","9","0",",",".","(",")"].contains(text) {
            textDocumentProxy.insertText(text)
            impactLight()
            return
        }
        textDocumentProxy.insertText(text)
        impactStrong()
    }

    private func resetComposing() {
        shengmuPart = nil
        composedSyllables.removeAll()
        allCandidates.removeAll()
        refreshCandidates()
    }

    private func showHint(spec: DirectionalSpec, direction: FlickDirection, anchor: UIView) {
        if direction == .center {
            [hintCenter, hintLeft, hintUp, hintRight, hintDown].forEach { $0.isHidden = true }
            return
        }

        hintCenter.text = spec.center.uppercasedIfAscii()
        hintLeft.text = spec.left.uppercasedIfAscii()
        hintUp.text = spec.up.uppercasedIfAscii()
        hintRight.text = spec.right.uppercasedIfAscii()
        hintDown.text = spec.down.uppercasedIfAscii()

        let keyFrame = anchor.convert(anchor.bounds, to: overlay)
        let cx = keyFrame.midX
        let cy = keyFrame.midY
        let d: CGFloat = 58

        place(hintCenter, x: cx, y: cy)
        place(hintLeft, x: cx - d, y: cy)
        place(hintUp, x: cx, y: cy - d)
        place(hintRight, x: cx + d, y: cy)
        place(hintDown, x: cx, y: cy + d)

        [hintCenter, hintLeft, hintUp, hintRight, hintDown].forEach { $0.isHidden = false }

        let selected = colorForSelection()
        hintLeft.backgroundColor = direction == .left ? selected : UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 0.95)
        hintUp.backgroundColor = direction == .up ? selected : UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 0.95)
        hintRight.backgroundColor = direction == .right ? selected : UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 0.95)
        hintDown.backgroundColor = direction == .down ? selected : UIColor(red: 0.07, green: 0.09, blue: 0.12, alpha: 0.95)
    }

    private func colorForSelection() -> UIColor {
        UIColor(red: 0.42, green: 0.45, blue: 0.50, alpha: 1)
    }

    private func place(_ label: UILabel, x: CGFloat, y: CGFloat) {
        label.frame = CGRect(x: x - 30, y: y - 22, width: 60, height: 44)
    }

    private func impactLight() {
        rigidImpact.impactOccurred(intensity: 0.9)
        rigidImpact.prepare()
    }

    private func impactStrong() {
        heavyImpact.impactOccurred(intensity: 1.0)
        heavyImpact.prepare()
    }
}

private extension String {
    func uppercasedIfAscii() -> String {
        guard contains(where: { $0.isASCII && $0.isLetter }) else { return self }
        return uppercased()
    }
}
