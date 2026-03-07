# CNflick iOS

这是 CNflick 的 iOS 版本骨架（Swift + 自定义键盘扩展），目标是与 Android 版保持相同的 12 键 Flick 交互风格。

## 已实现
- 自定义 Keyboard Extension（`CNflickKeyboard`）
- 12 键 Flick 主键盘（拼音）
- 候选栏 + composing 显示
- 英文/数字/符号/功能模式切换
- 十字 Flick 提示层
- 模式键高亮（橙底白字）
- iPhone 增强触觉反馈（`UIImpactFeedbackGenerator` heavy + rigid）

## 本机当前限制
当前机器未安装完整 Xcode（仅 Command Line Tools），无法直接真机编译和安装。

## 你在本机需要做的事
1. 从 App Store 安装 Xcode（完整版本）
2. 执行：
   ```bash
   xcode-select -s /Applications/Xcode.app/Contents/Developer
   ```
3. 安装 XcodeGen（可选，但推荐）：
   ```bash
   brew install xcodegen
   ```
4. 在本目录生成工程：
   ```bash
   cd ios/CNflick-iOS
   xcodegen generate
   ```
5. 用 Xcode 打开 `CNflickiOS.xcodeproj`
6. 在 `Signing & Capabilities` 里给 `CNflickApp` 和 `CNflickKeyboard` 配置同一个 Team
7. 连接 iPhone，选择真机，Run `CNflickApp`

## iPhone 启用键盘
设置 -> 通用 -> 键盘 -> 键盘 -> 添加新键盘 -> 选择 `CNflick Keyboard`
然后回到 `CNflick Keyboard` 项，开启“允许完全访问”。

## 目录
- `CNflickApp/`：宿主 App（安装与引导页）
- `CNflickKeyboard/`：键盘扩展核心逻辑
- `project.yml`：XcodeGen 工程定义
