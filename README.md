# CNflick

兼顾效率和准度，理论最优的手机中文输入法。
 
开发者：忧郁乔班尼。
B站主页：
[https://m.bilibili.com/space/431314757?spm_id_from=333.1369.opus.module_author_avatar.click](https://m.bilibili.com/space/431314757?spm_id_from=333.1369.opus.module_author_avatar.click)

## 项目简介
CNflick 是一个 Android 自定义输入法项目，核心是 12 键 Flick 输入：
- 中文拼音 12 键 Flick（可自定义映射）
- 候选栏与动态调频（用户选词置顶）
- 英文/数字/符号/功能副键盘
- 剪贴板历史、方向键、复制粘贴等效率功能
- 自定义设置中心（词库/主题/配置/插件导入入口）

## 快速安装
APK 下载（GitHub Release）：
- https://github.com/3121455692atou-sudo/CNflick/releases/download/v0.1.0/CNflick-debug.apk

1. 在手机启用开发者选项和 USB 调试。
2. 连接手机后安装：
   ```bash
   adb install -r release/CNflick-debug.apk
   ```
3. 系统设置中启用并切换到 `CNflick` 输入法。

## 源码结构
- `app/src/main/java/com/example/flickime/FlickImeService.kt`：输入法主服务与键盘 UI
- `app/src/main/java/com/example/flickime/engine/PinyinEngine.kt`：候选查询与动态调频
- `app/src/main/java/com/example/flickime/ImeSettingsActivity.kt`：设置页
- `app/src/main/java/com/example/flickime/KeyMappingActivity.kt`：12 键映射编辑 + JSON 导入导出
- `app/src/main/java/com/example/flickime/data/KeyMapStore.kt`：映射持久化

## 映射导入/导出 JSON
设置 -> 自定义按键映射：
- `导出 JSON`：导出当前 12 键映射
- `导入 JSON`：导入并覆盖当前映射

JSON 结构（长度 12 的数组，每项 5 向）：
```json
[
  {"center":"b","left":"p","up":"m","right":"f","down":"w"}
]
```

## 主题包文档
- `docs/THEME_PACK.md`：主题包制作方式与制作规范（格式、字段、打包、导入）

## 构建
```bash
./gradlew :app:assembleDebug
```

产物：
- `app/build/outputs/apk/debug/app-debug.apk`
- `release/CNflick-debug.apk`

## 开源说明
- 本项目为实验性输入法实现，适合二次开发与研究。
- 词库与外部资源请遵守其各自许可证。
