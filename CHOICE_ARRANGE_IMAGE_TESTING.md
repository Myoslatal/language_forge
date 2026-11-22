# 手写（图片）词汇在组句题型中的渲染 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :133, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 范围
listen 选项之后，把同一能力扩展到所有“从词汇组成句子”的题型：
选择题（句子 + 选项）、词块组句（词块）、输入翻译（题干 promptItems）。

## 数据
- 选择题句子：segments.word 已有 vocabId，句子镜像对 image 词取 codes[0] → meanings[0] → 〔图片〕。
- 选择题选项：复用 optionItems（与 listen 相同规则：options 文本镜像、文本改到不一致时保存塌缩、无 vocab 项时丢弃）。
- 词块：block.vocabId 指向 image 词时学习端渲染图片；新增词块时 text 镜像写首个 code。
- 输入翻译题干：新增 promptItems（text / vocab），prompt 为文本镜像，规则同 optionItems。
- 编辑器附带修复：choice 的「选词 + 预览」行在 arrange/match/type/listen 下会隐藏，不再串题型残留。

## Android（API36）
- 选择题学习：句子显示手写图 + 标点 + apple；点图弹提示（顶部图片 + 释义）且无残留蓝选；选项 1 显示手写图；确认正确绿色 + 下一题。
  证据：/tmp/choice-image-learn.png、/tmp/choice-image-hint.png、/tmp/android-choice-hint2.png、/tmp/choice-image-correct.png。
- 选择题编辑：句子词表显示手写缩略图，句子镜像 “a， apple。”；四个选项各有「选词」+ 结构化预览 + ×。
  证据：/tmp/android-choice-img-editor.png。
- 选择题保存/重开：structure.json 保留 segments 与 optionItems（vocabId），options[0]=“a 苹果”。
- 词块组句学习：词块池与已选区显示手写图，正确后绿色 + 下一题。证据：/tmp/android-arrange-img-learn.png、/tmp/android-arrange-img-correct.png。
- 词块组句编辑：词块行显示缩略图；其他题型下无残留选词行。证据：/tmp/android-arrange-img-editor2.png。
- 词块保存/重开：blocks/answers 原样保留（b-hand/b-apple）。
- 输入翻译学习：题干显示手写图 + 你好。证据：/tmp/android-type-img-learn.png。
- 输入翻译编辑：题干预览图片 + 文本 + ×。证据：/tmp/android-type-img-editor2.png。

## 桌面（SDL2 / Xvfb :133）
- 选择题学习：句子中的 image 词为可点图片按钮（提示窗口含手写图，无音频时不播报错误），文本词保留下划线；选项 1 自绘行显示手写图；正确绿色 + 下一题。
  证据：/tmp/desk-choice-learn.png、/tmp/desk-choice-hint3.png、/tmp/desk-choice-correct2.png。
- 选择题编辑：词表缩略图、句子镜像 “a， apple。”、选项「选词」/预览/×；保存重开 optionItems 保留且 options[0]=“a 苹果”。
  证据：/tmp/desk-choice-editor2.png。
- 词块组句学习：池/已选显示手写图，正确绿色 + 下一题；编辑词块行缩略图。
  证据：/tmp/desk-arrange-learn.png、/tmp/desk-arrange-correct2.png、/tmp/desk-arrange-editor.png。
- 输入翻译学习/编辑：题干图片 + 文本；题干预览图 + ×。
  证据：/tmp/desk-type-img-learn.png、/tmp/desk-type-img-editor.png。

## 构建与回归
- Android clean assembleDebug、桌面 clean 构建均成功。
- 桌面 smoke 两次 SMOKE PASS（含 question/learning/lexicon 全 true）。
- 纯文本题型路径未改：无 vocab 项时仍走原按钮/文本渲染；纯文本题目保存时不写 optionItems/promptItems。

## 已知边界
- match 题（配对）仍为纯文本，本次未包含。
- 选择题句子里图片与文字混排按现有单行布局，单词较多时可能横向溢出（与改动前的句子布局一致）。
- 音频听感、真实麦克风仍 N/A（见 REAL_DEVICE_TESTS.md）。
