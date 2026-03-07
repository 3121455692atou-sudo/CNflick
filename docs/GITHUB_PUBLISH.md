# GitHub 发布步骤（自动/手动）

## 自动（推荐）
```bash
# 1) 登录（可走 Clash）
export http_proxy=http://127.0.0.1:7890 https_proxy=http://127.0.0.1:7890 all_proxy=socks5://127.0.0.1:7890

gh auth login

# 2) 创建仓库并推送
git branch -M main
gh repo create CNflick --public --source=. --remote=origin --push --description "兼顾效率和准度，理论最优的手机中文输入法"

# 3) 创建 Release 并上传 APK
gh release create v0.1.0 release/CNflick-debug.apk -t "CNflick v0.1.0" -n "初版发布"
```

## 手动
1. 在 GitHub 网页新建仓库 `CNflick`。
2. 执行：
```bash
git remote add origin https://github.com/<你的用户名>/CNflick.git
git branch -M main
git push -u origin main
```
3. 在仓库 Releases 上传 `release/CNflick-debug.apk`。
