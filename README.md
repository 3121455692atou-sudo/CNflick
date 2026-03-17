# CNflick

兼顾效率和准度的 12 键 Flick 中文输入法。

开发者：忧郁乔班尼  


## 当前版本
- `1.0` 安装包（保留）：`release/CNflick-debug.apk`
- `1.1` 安装包（新增）：`release/CNflick-v1.1-debug.apk`
- GitHub Release（1.1）：https://github.com/3121455692atou-sudo/CNflick/releases/tag/v1.1.0
- GitHub Release（1.0）：https://github.com/3121455692atou-sudo/CNflick/releases/tag/v0.1.0

详细改动请看：`docs/RELEASE.md`

## 项目简介
CNflick 是 Android 自定义输入法项目，核心能力：
- 中文拼音 12 键 Flick（支持映射自定义）
- 英文/数字/符号/功能副键盘
- 候选栏 + 候选展开页 + 动态调频
- 长拼音分段候选与整句学习
- 剪贴板历史、方向键、复制粘贴等效率功能
- 主题 / 字体 / 背景图 / 按键图 / 词库 / 快捷词库自定义

## 快速安装
1. 手机启用开发者选项与 USB 调试。
2. 安装 1.1：
```bash
adb install -r release/CNflick-v1.1-debug.apk
```
3. 系统设置中启用并切换到 `CNflick` 输入法。

## 使用与教程
- 使用总教程：`docs/USER_GUIDE.md`
- 主题包制作教程：`docs/THEME_PACK.md`
- 词库来源与重建：`docs/LEXICON_SOURCES.md`
- GitHub 发布与 Clash 代理流程：`docs/GITHUB_PUBLISH.md`

## 目录结构
- `app/src/main/java/com/example/flickime/FlickImeService.kt`：输入法主服务与键盘 UI
- `app/src/main/java/com/example/flickime/ImeSettingsActivity.kt`：设置页
- `app/src/main/java/com/example/flickime/LexiconSettingsActivity.kt`：词库/快捷词库页
- `app/src/main/java/com/example/flickime/engine/PinyinEngine.kt`：候选与学习引擎
- `app/src/main/java/com/example/flickime/engine/LexiconManager.kt`：多词库管理
- `app/src/main/java/com/example/flickime/theme/`：主题/字体/背景资源管理
- `tools/`：词库构建脚本
- `release/theme-packs/`：示例主题包

## 构建
```bash
./gradlew :app:assembleDebug
```

产物：
- `app/build/outputs/apk/debug/app-debug.apk`

## 开源说明
- 本项目为实验性输入法实现，适合二次开发与研究。
- 词库与外部资源请遵守其各自许可证。
