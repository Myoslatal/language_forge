# 阅读短文（reading）题型 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求（对齐 HTML）
QT.reading：先读一段短文，再回答选择题或输入答案；短文里的词可点开释义。
数据：passage（多行）+ answerMode（choice/type）+ prompt；choice 复用 options/optionItems/correctIndex，type 复用 acceptedAnswers/ignoreCase/ignoreSpace/near。

## 数据模型
- 顶层 passage / answerMode（仅 reading 题型写入；旧文件缺省 choice）。
- 切换作答方式保存时会清空另一模式的字段（choice 保存清 acceptedAnswers；type 保存清 options/optionItems/correctIndex），避免脏数据。

## Android（API36）
- 学习（选项模式）：短文卡片、可点词（a/apple 蓝色下划线）、题干、四选项（含手写图项）；点词弹出悬浮提示（音标/释义/发音）；选对绿色+下一题。
  证据：/tmp/reading-choice-learn.png、/tmp/reading-type-hint2.png、/tmp/reading-choice-correct.png。
- 学习（输入模式）：短文+输入框，输入 apple 正确→绿色+完成；错误提示可用；键盘收起后可点确认。
  证据：/tmp/reading-type-learn.png、/tmp/reading-type-correct2.png。
- 编辑器：题型「阅读短文」；短文/作答方式/题干；选项模式显示共享选项编辑（选词/＋文本/标点/×），输入模式显示可接受答案+忽略大小写/空格/相近；保存重开 JSON 保留 passage/answerMode/options/optionItems。
  证据：/tmp/reading-choice-editor.png、/tmp/reading-type-editor.png、/tmp/reading-saved.json。

## 桌面（SDL2 / Xvfb）
- 学习（选项模式）：短文按词渲染可点按钮（a/apple 蓝色下划线）、悬浮提示、题干、选项（含手写图）；选对绿色+下一题。
  证据：/tmp/desk-reading-choice-learn.png、/tmp/desk-reading-choice-hint.png、/tmp/desk-reading-choice-correct.png。
- 学习（输入模式）：短文可点词、输入 apple→「回答正确」+完成。
  证据：/tmp/desk-reading-type-learn.png、/tmp/desk-reading-type-correct.png。
- 编辑器：选项模式共享选项编辑；输入模式可接受答案+三个开关；保存重开 JSON：type 题保留 acceptedAnswers、清空 options/optionItems，choice 题保留选项与 optionItems。
  证据：/tmp/desk-reading-choice-editor.png、/tmp/desk-reading-type-editor2.png、structure.json。

## 构建/回归
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。
- 其它题型未改；纯文本阅读题（无 optionItems）保存不写该键。

## 已知边界
- 正文点词释义按项目词汇（displayName/codes/text，忽略大小写）匹配；未收录词不可点，未实现 HTML 的 per-question 自定义 glosses。
- 输入模式的「差 1 字母」仅保存 near 开关，学习端暂未给近似提示（输入题既有实现可后续复用）。
- 点词后再次点击同一词可重播（Android 弹层规则）；弹层打开时会拦截其覆盖区域的点击，需先点外部关闭。
- 音频听感、真实麦克风仍 N/A。
