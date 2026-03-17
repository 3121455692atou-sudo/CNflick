# CNflick

12 键 Flick 中文输入法。

开发者：忧郁乔班尼  

## v1.2 更新
- 移除系统语音输入功能（含相关开关和权限）。
- 八方向滑动改为独立开关：
  - 拼音八方向默认关闭。
  - 符号八方向默认开启。
- 按键文字显示新增独立开关：
  - 可关闭中间大字。
  - 可关闭四周小字。
- 八方向斜向映射会同步显示在按键四角小字。
- 符号默认映射重做为 12 键 * 9 向，括号映射成对且方向规律统一。
- 安装指引页补充实用技巧文案。

详细版本记录见 [docs/RELEASE.md](docs/RELEASE.md)。

## APK 文件
- `release/CNflick-debug.apk`（旧版保留）
- `release/CNflick-v1.1-debug.apk`（旧版保留）
- `release/CNflick-v1.2-debug.apk`（当前版）

## 编译（给二创作者）
```bash
./gradlew :app:assembleDebug
```

输出 APK：
- `app/build/outputs/apk/debug/app-debug.apk`
