# 新知卡片（card）题型 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :135, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求（对齐 HTML）
QT.card = 新知卡片，不计分：词形 / 发音 / 释义 / 例句；学习页展示后点「继续」进入下一题。
数据：q.card={word,pron,def,detail,examples:[{l,z}]}；编辑器支持「从词库生成卡片」。

## 数据模型
- card 顶层对象（仅 card 题型写入）：word/pron/def/detail/wordVocabId/examples[{l,z}]。
- wordVocabId 仅当从词库生成的词条为 image（手写）时记录；学习页词形位置显示手写图。
- 保存时若词形被改到与该 image 词条镜像不一致 → 清空 wordVocabId（避免旧图残留）；Android 编辑时改动词形也会即时清空预览。
- 纯文本卡片（无 wordVocabId）完全兼容；其它题型不写 card 键。

## Android（API36）
- 学习：手写图 + /a/ + 释义「手写字母 a」+ 两行补充说明 + 两条例句 + 不计分提示 + 「完成」；点完成后回到题单，底部不再残留按钮。
  证据：/tmp/card-img-learn2.png、/tmp/android-card-after2.png。
- 编辑器：题型「新知卡片」；词形/发音/释义/补充说明/例句行/×/＋例句；词形下方手写缩略图。
  证据：/tmp/card-img-editor2.png。
- 从词库生成卡片：选 apple（文本词）→ 词形 apple、发音空、释义「苹果 apple」、图片预览消失、例句保留；保存后 JSON card 字段完整且 wordVocabId 为空。
  证据：/tmp/card-img-generated.png、/tmp/card-saved.json。

## 桌面（SDL2 / Xvfb）
- 学习：手写图 + /a/ + 释义/说明/例句 + 底部「完成」，点击回到题单并提示「本课完成」。
  证据：/tmp/desk-card-learn2.png、/tmp/desk-card-after3.png。
- 编辑器：全部字段 + 图片预览 + 例句增删；从词库生成卡片选 image 词后词形变 "a"、图片预览出现、wordVocabId 保留。
  证据：/tmp/desk-card-editor2.png、/tmp/desk-card-generated.png。
- 手改词形为 "ax" 后图片预览立即消失，保存后 wordVocabId 清空。
  证据：/tmp/desk-card-editedword.png 与 structure.json（word=ax, wordVocabId=""）。

## 构建/回归
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。
- 顺带修复：Android 完成题目回到题单后确认按钮残留（所有题型受益）。

## 已知边界
- 卡片不计分，没有判题/提示逻辑；「继续」只推进。
- 例句仅文本（不支持在例句内嵌词汇图片）；wordVocabId 只作用于词形。
- 音频听感、真实麦克风仍 N/A。
