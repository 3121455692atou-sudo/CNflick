import SwiftUI

struct ContentView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("CNflick 安装指引")
                .font(.system(size: 28, weight: .bold))
            Text("1. 打开 设置 -> 通用 -> 键盘 -> 键盘 -> 添加新键盘")
            Text("2. 选择 CNflick Keyboard")
            Text("3. 回到键盘列表，点 CNflick Keyboard，开启“允许完全访问”")
            Text("4. 在任意输入框长按地球键切换到 CNflick")
                .padding(.bottom, 8)

            Text("说明")
                .font(.headline)
            Text("- iOS 版已实现 12 键 Flick、候选栏、模式切换、功能区与增强震动。")
            Text("- 若需完全等同 Android 的词库规模，可后续接入 SQLite 或 Rime 数据。")

            Spacer()
        }
        .padding(20)
    }
}
