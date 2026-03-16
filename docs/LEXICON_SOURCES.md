# CNflick 词库来源与重建

## 当前方案
CNflick 使用：
- 全量雾凇拼音词库（`gaboolic/rime-frost` 下 `cn_dicts` 目录全部 `*.dict.yaml`，不做删减）
- 开源动漫词库（`drganghe/Rime-Settings` 的 `luna_pinyin.anime.dict.yaml` 转换为拼音键值）

当前内置词库文件：
- 主词库 SQLite：`app/src/main/assets/pinyin_dict_v2.db`
- 动漫词库（默认启用）：`app/src/main/assets/lexicons/anime_character_cn.tsv`

## 重建命令
1. 拉取词库仓库（必要时可走 Clash 代理）：

```bash
git clone --depth 1 https://github.com/gaboolic/rime-frost.git /tmp/cnflick_lexicons/rime-frost
```

2. 生成 SQLite 主词库（全量扫描 `cn_dicts/**/*.dict.yaml`）：

```bash
python3 tools/build_pinyin_dict_from_rime.py \
  --rime-root /tmp/cnflick_lexicons/rime-frost \
  --output-db app/src/main/assets/pinyin_dict_v2.db
```

3. 生成动漫词库（拼音 -> 中文词条）：

```bash
git clone --depth 1 https://github.com/drganghe/Rime-Settings.git /tmp/cnflick_lexicons/Rime-Settings
python3 -m pip install pypinyin opencc-python-reimplemented
python3 tools/build_anime_lexicon_from_rime_settings.py \
  --source /tmp/cnflick_lexicons/Rime-Settings/luna_pinyin.anime.dict.yaml \
  --output app/src/main/assets/lexicons/anime_character_cn.tsv
```

4. 可选检查：

```bash
sqlite3 app/src/main/assets/pinyin_dict_v2.db 'select count(*) from dict;'
```

## 许可证
词库版权与许可证遵循上游仓库说明：
- https://github.com/gaboolic/rime-frost
- https://github.com/drganghe/Rime-Settings
