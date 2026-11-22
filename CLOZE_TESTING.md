# 选词填空（cloze）题型 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求（对齐 HTML）
QT.cloze：正文里用 {} 标记空格，从候选词里按顺序填入；候选词中勾选的是正确词，勾选顺序=空格顺序。

## 数据模型
- 顶层 clozeText（含 {}）与 clozeOrder（正确候选词下标，按空格顺序；候选词列表已压缩掉空行，下标对应压缩后的 options）。
- options 复用 4 个候选词槽位；correctIndex 固定 -1；仅 cloze 题型写入 clozeText/clozeOrder。

## Android（API36）
- 学习：正文按 {} 切段，空格按钮显示 ＿＿/已填词；候选词可点填入第一个空，点已填空格可移除；全部填满出现确认；正确→绿色「回答正确」+「完成」（最后一题），错误顺序→「回答错误，请重新选择」。
  证据：/tmp/cloze-learn.png、/tmp/cloze-filled.png、/tmp/cloze-wrong.png、/tmp/cloze-correct.png。
- 编辑器：「选词填空」；正文/题干/当前空格数/四个候选词+正确词勾选；保存重开 JSON 保留 clozeText/clozeOrder/options（correctIndex=-1、无 optionItems）。
  证据：/tmp/cloze-editor2.png、/tmp/cloze-saved.json。

## 桌面（SDL2 / Xvfb）
- 学习：正文切段+空格按钮+候选词按钮；填 ıo/apple → 回答正确 + 完成。
  证据：/tmp/desk-cloze-learn.png、/tmp/desk-cloze-filled.png、/tmp/desk-cloze-correct2.png。
- 编辑器：正文/题干/空格数/候选词正确词勾选；保存重开 JSON 保留 clozeText/clozeOrder/options。
  证据：/tmp/desk-cloze-editor2.png、structure.json。

## 修复记录（验收时发现）
- 双端保存时「候选词空行压缩」与「勾选下标」未同步，勾选靠后的候选词会存错/越界；改为先建压缩映射再写 clozeOrder。
- Android 打开 cloze 题时缺少初始 section 分支（会落到选择题界面）；已补。
- Android 候选词已用状态按文本匹配（重复词会误判），改为按下标标签判断。
- 桌面学习候选词空串会渲染空按钮、最后一题没有「完成」；已修。

## 构建/回归
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。
- 其它题型未改。

## 已知边界
- 双端均支持 {} 与 { }（大括号内空白）两种空格写法。
- 桌面学习布局为逐段+空格按钮纵向排列，未做 HTML 那样的完全内联换行。
- 空格数限制 1..4（受四候选槽位与 UI 约束）。
- 音频听感、真实麦克风仍 N/A。
