# CNflick 主题包制作方式与规范

本文档定义 CNflick 主题包的推荐结构与字段规范，便于社区统一制作与分享。

## 1. 主题包格式

推荐使用 `zip` 文件，后缀建议为 `.cnflick-theme.zip`。

目录结构：

```text
my-theme.cnflick-theme.zip
├── theme.json
├── preview.png
└── assets/
    └── optional-background.png
```

- `theme.json`：必需，主题元数据与样式参数。
- `preview.png`：建议，主题预览图（建议 1080x2400）。
- `assets/`：可选，放扩展资源。

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
