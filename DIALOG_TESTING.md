# 选择对话（dialog）题型 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :136, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求（对齐 HTML）
QT.dialog = 选择对话：先给一段对话，再选出符合语境的下一句/补全台词。
数据：q.lines=[{who,text}]（text 留空显示待补全）+ prompt + options + correctIndex；作答逻辑同 choice。

## 数据模型
- 顶层 lines 数组（仅 dialog 题型写入）：{who,text}。
- options/optionItems/correctIndex 复用选择题结构；空台词学习端显示「（待补全）」。

## Android（API36）
- 学习：左右交替气泡（A 左、B 右）、空台词显示「（待补全）」、题干、四个选项（含手写图片选项）；选中后确认→绿色+「完成/下一题」。
  证据：/tmp/android-dialog-learn3.png、/tmp/android-dialog-correct.png。
- 编辑器：题型「选择对话」；题干、台词行（角色/台词/×、＋台词）、四个选项「选词」+结构化预览+×、正确项单选。
  证据：/tmp/android-dialog-editor.png。
- 保存/重开 JSON：lines、options、optionItems（vocabId）完整保留。
  证据：/tmp/dialog-saved.json。

## 桌面（SDL2 / Xvfb）
- 学习：交替缩进气泡（A 左、B 右、空台词「……」）、题干、选项（含手写图）；确认正确→绿色+「回答正确，本课完成」。
  证据：/tmp/desk-dialog-learn.png、/tmp/desk-dialog-correct.png。
- 编辑器：题型「选择对话」；题干、台词行增删、选项选词/预览/正确项。
  证据：/tmp/desk-dialog-editor2.png、/tmp/desk-dialog-lineadded.png。
- 保存 JSON：空台词行被丢弃、lines 与 optionItems 保留。
  证据：/tmp/optimg2/桌面测试-optimg2/structure.json 中 dialog-img-1。

## 构建/回归
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。
- 选择题学习/判题逻辑被抽成共享函数，choice 与 dialog 共用（choice 回归见 smoke 与既有用例）。

## 已知边界
- 台词内的词暂不支持点词提示（对话气泡是纯文本）；选项里可点（与 choice 一致）。
- 对话气泡宽度按内容自适应，超长台词会在气泡内换行。
- 音频听感、真实麦克风仍 N/A。
