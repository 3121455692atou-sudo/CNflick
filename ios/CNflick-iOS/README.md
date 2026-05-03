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


## iPhone 启用键盘
设置 -> 通用 -> 键盘 -> 键盘 -> 添加新键盘 -> 选择 `CNflick Keyboard`
然后回到 `CNflick Keyboard` 项，开启“允许完全访问”。

## 目录
- `CNflickApp/`：宿主 App（安装与引导页）
- `CNflickKeyboard/`：键盘扩展核心逻辑
- `project.yml`：XcodeGen 工程定义
