# CNflick 词库来源与重建

## 当前方案
CNflick 使用：
- 全量雾凇拼音词库（`gaboolic/rime-frost` 下 `cn_dicts` 目录全部 `*.dict.yaml`，不做删减）
- 开源动漫词库（`drganghe/Rime-Settings` 的 `luna_pinyin.anime.dict.yaml` 转换为拼音键值）

当前内置词库文件：
- 主词库 SQLite：`app/src/main/assets/pinyin_dict_v2.db`
- 动漫词库（默认启用）：`app/src/main/assets/lexicons/anime_character_cn.tsv`

## 许可证
词库版权与许可证遵循上游仓库说明：
- https://github.com/gaboolic/rime-frost
- https://github.com/drganghe/Rime-Settings
