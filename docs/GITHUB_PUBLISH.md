# CNflick GitHub 发布流程（含 Clash 代理）

以下命令以当前仓库 `main` 分支为例。

## 1. 配置 Clash 代理（可选）
如果你本机需要走 Clash 才能访问 GitHub：

```bash
export http_proxy=http://127.0.0.1:7891
export https_proxy=http://127.0.0.1:7891
export all_proxy=socks5://127.0.0.1:7891
```

如端口不同请改成你的本地端口。

## 2. 构建 APK
```bash
./gradlew :app:assembleDebug
```

生成：
- `app/build/outputs/apk/debug/app-debug.apk`

## 3. 生成发布目录中的版本包
```bash
cp app/build/outputs/apk/debug/app-debug.apk release/CNflick-v1.1-debug.apk
```

说明：
- `release/CNflick-debug.apk` 为旧版 1.0（保留）
- `release/CNflick-v1.1-debug.apk` 为新版 1.1

## 4. 提交并推送
```bash
git add -A
git commit -m "release: CNflick v1.1"
git push origin main
```

## 5. 创建 GitHub Release（可选）
安装并登录 `gh` 后：

```bash
gh release create v1.1.0 \
  release/CNflick-v1.1-debug.apk \
  --title "CNflick v1.1.0" \
  --notes-file docs/RELEASE.md
```

如果只想上传 APK 到现有 release：

```bash
gh release upload v1.1.0 release/CNflick-v1.1-debug.apk --clobber
```
