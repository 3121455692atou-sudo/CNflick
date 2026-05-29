# CNflick 词库来源与说明

## 中文词库
CNflick 中文输入使用：
- 雾凇拼音词库（`gaboolic/rime-frost` 下 `cn_dicts`）
- 动漫词库（`drganghe/Rime-Settings` 的 `luna_pinyin.anime.dict.yaml` 转换）

内置文件：
- 主词库 SQLite：`app/src/main/assets/pinyin_dict_v2.db`
- 动漫词库：`app/src/main/assets/lexicons/anime_character_cn.tsv`

## 日语词库
CNflick 日语输入内置词库文件：
- `app/src/main/assets/lexicons/japanese_keyboard_core.tsv`

该文件基于以下开源词典转换得到：
- `skk-dev/dict` 仓库中的 `SKK-JISYO.L`
- 上游地址：https://github.com/skk-dev/dict

说明：
- 原始词典为读音到候选词格式（SKK 字典格式）。
- CNflick 进行了编码转换与格式转换，生成 `reading<TAB>cand1,cand2,...` 的轻量 TSV。

## 形码码表
CNflick 形码输入内置五笔 86 码表：
- `app/src/main/assets/shape/wubi86.dict.yaml`

该文件来自 `rime/rime-wubi` 的 `wubi86.dict.yaml`，授权为 LGPL-3.0。

## 离线语音模型
CNflick 语音输入内置 Sherpa-ONNX SenseVoice int8 模型：
- `app/src/main/assets/sense-voice/`

该模型来自 Sherpa-ONNX 发布的 `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17`：
- https://github.com/k2-fsa/sherpa/blob/master/docs/source/onnx/sense-voice/pretrained.rst
- https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-int8-2024-07-17.tar.bz2

## 许可证说明
词库版权和许可遵循各上游仓库原始说明：
- 中文：
  - https://github.com/gaboolic/rime-frost
  - https://github.com/drganghe/Rime-Settings
- 日语：
  - https://github.com/skk-dev/dict
- 形码：
  - https://github.com/rime/rime-wubi
- 离线语音：
  - https://github.com/k2-fsa/sherpa-onnx
  - https://www.modelscope.cn/models/iic/SenseVoiceSmall
