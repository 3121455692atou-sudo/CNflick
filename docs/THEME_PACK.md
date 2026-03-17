# CNflick 主题包制作方式与规范

本文档定义 CNflick 主题包的推荐结构与字段规范，便于社区统一制作与分享。

## 1. 主题包格式

推荐使用 `zip` 文件，后缀建议为 `.cnflick-theme.zip`。

一键主题包（推荐）目录结构：

```text
my-pack.cnflick-theme.zip
├── theme-pack.json          # 一键包清单（必需）
├── theme.json               # 主题颜色（必需）
├── preview.png              # 预览图（可选）
└── assets/
    ├── font.ttf             # 可选
    ├── ime_background.png   # 可选
    ├── key_background.png   # 可选
    └── key_sound.wav        # 可选
```

`theme-pack.json` 字段（固定格式）：

```json
{
  "packId": "demo.pink.template",
  "packName": "粉色主题包模板",
  "version": "1.0.0",
  "themeId": "cnflick.theme.pink_template",
  "assets": {
    "font": "assets/font.ttf",
    "imeBackground": "assets/ime_background.png",
    "keyBackground": "assets/key_background.png",
    "keySound": "assets/key_sound.wav"
  }
}
```

说明：
- `packId`/`packName`/`version` 为必填。
- `themeId` 推荐与 `theme.json.id` 一致。
- `assets.*` 均为可选，缺失时自动回退默认（背景/按键图）或关闭自定义音效。
- 资源路径必须是 zip 内相对路径，不能包含 `..`。

兼容旧格式：
- 仅包含 `theme.json` 的 zip 仍可通过“仅导入主题色（兼容旧格式）”导入。

## 2. theme.json 字段规范

示例：

```json
{
  "id": "cnflick.theme.default_light",
  "name": "Default Light",
  "version": "1.0.0",
  "author": "your_name",
  "license": "MIT",
  "minAppVersion": "0.1.0",
  "colors": {
    "keyboardBackground": "#E5EAF1",
    "panelBackground": "#D5DCE6",
    "keyBackground": "#EEF1F5",
    "keyBorder": "#A6AFBC",
    "keyText": "#111827",
    "subKeyText": "#4B5563",
    "accentKeyBackground": "#1677FF",
    "accentKeyText": "#FFFFFF",
    "selectedItemBackground": "#6B7280",
    "selectedItemText": "#FFFFFF",
    "hintText": "#6B7280"
  },
  "shape": {
    "keyCornerRadiusDp": 10,
    "panelCornerRadiusDp": 12,
    "strokeWidthDp": 1
  },
  "typography": {
    "mainKeySp": 18,
    "subKeySp": 12,
    "candidateSp": 20
  }
}
```

必填：
- `id`：主题唯一 ID，建议反域名风格。
- `name`：主题名称。
- `version`：主题版本，语义化版本。
- `colors`：主题颜色配置。

建议填写：
- `author`、`license`、`minAppVersion`、`shape`、`typography`。

## 3. 设计规范

- 对比度：文字与背景建议对比度不低于 4.5:1。
- 可读性：主按键文字建议 >= `16sp`，副文字建议 >= `11sp`。
- 一致性：普通键、功能键、高亮键要有稳定视觉层级。

### 尺寸与像素建议

- 预览图建议：`1080x2400`（PNG/JPG）。
- 输入法背景图建议比例：`1:1`（导入后会自动中心裁切）。
- 按键图片建议比例：`2.45:1`（导入后会自动中心裁切）。
- 图片长边建议至少 `1200px`，避免放大模糊。
- 按键圆角建议：`10dp~14dp`；描边建议 `1dp`。
- 主按键文字建议：`16sp~24sp`；四周副文字建议：`9sp~16sp`。

### 自定义字段建议（扩展）

- `assets.backgroundImage`：输入法背景图路径。
- `assets.keyImage`：按键图片路径。
- `opacity.keyTextAlpha`：按键文字透明度（`0.2~1.0`）。
- `opacity.keyImageAlpha`：按键图片透明度（`0.1~1.0`）。

## 4. 打包步骤

1. 按规范准备 `theme.json` 和资源文件。
2. 在主题目录执行压缩：

```bash
zip -r my-theme.cnflick-theme.zip theme.json preview.png assets
```

3. 将生成的主题包导入 CNflick 设置页中的“导入主题包”。

## 5. 导入与兼容说明

- 当前版本会记录导入文件 URI，并作为主题资源入口。
- 不认识的字段会被忽略，已识别字段才会生效。
- 建议保留向后兼容：旧字段不要直接删除，优先新增字段。
- v1.1 起支持背景图/按键图导入后的自动裁切与可选列表持久化。

## 6. 预设主题包

应用内置了两个可一键切换的预设主题包：

- `纯默认主题包`：默认浅色 + 系统字体 + 默认背景/按键图。
- `初音主题包`：初音主题色 + 初音背景 + JetBrains Mono 字体。

这两个预设会和你导入的主题包一起出现在“可切换主题包（预设 + 已导入）”列表中。

