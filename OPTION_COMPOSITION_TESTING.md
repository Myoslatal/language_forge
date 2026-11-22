# 选项「选词组句」+ 手写文字 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :137, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求
对话题型（以及所有带选项的题型）的选项要能像句子一样「选词组句」：按顺序组合多个词汇、
插入标点/文本片段，并支持手写（图片）词汇在选项中显示真实图片。

## 实现
- 选项结构化数据仍为 optionItems：{kind:"vocab",vocabId} 与 {kind:"text",text} 的有序数组。
- 编辑器（listen / choice / dialog 共用同一套选项编辑控件）：
  - 选词：多选词汇按选择顺序追加；
  - ＋文本：追加可原地编辑的文本片段（可写标点、空格、连接词等）；
  - 标点：弹出常用标点菜单（，。、？！；：“”（）…—/），按顺序插入；
  - ×：删除任意一项；选项文本字段始终是结构化项的镜像。
- 镜像空格规则改为标点感知：词与词之间补空格，标点前后不补空格（如 “a，苹果”）。
- 学习端渲染含 vocab 项的选项时显示手写图（与词汇条目一致）。

## Android（API36）
- 对话题型编辑器：选项 1 含手写图 + 「你好」；选项 2 多选追加「手写字母 a」+「apple」后镜像为 “再见 a apple”；选项 2 再用标点插入「。」并编辑文本片段 → “苹果。xyz”。
  证据：/tmp/android-dialog-optcomp.png、/tmp/android-optcomp-typed.png。
- 保存/重开 JSON：optionItems 保留 vocab/text 序列（例：[text 再见, vocab hand-a, vocab apple]）。
  证据：/tmp/dialog-optcomp-saved.json、/tmp/optcomp-saved.json。
- 学习：对话气泡下方选项 2 显示「再见 [手写图] apple」。
  证据：/tmp/android-dialog-optcomp-learn.png。
- 选择题同样生效：选项 1 镜像 “a 苹果，abc”，JSON 保留 [vocab, text 苹果, text ，, text abc]。
  证据：/tmp/android-optcomp-editor.png、/tmp/android-optcomp-learn.png。

## 桌面（SDL2 / Xvfb）
- 对话题型选项：选词多选追加手写词与 banana，预览显示缩略图；选项 2 插入「，」后镜像 “再见，”。
  证据：/tmp/desk-dialog-optcomp-editor4.png、/tmp/desk-dialog-optcomp-picked.png、/tmp/desk-dialog-optcomp-final.png。
- 学习：选项 1 显示「手写图 你好 banana 手写图」，选项 2「再见 ，」。
  证据：/tmp/desk-dialog-learn3.png。
- 选择题：选项 1 由手写图 + 苹果 + 「，」 + abc 组成，镜像 “a 苹果，abc”。
  证据：/tmp/desk-optcomp-typed.png、structure.json。

## 构建/回归
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。
- 纯文本选项不变：无 vocab 项时保存不写 optionItems；学习继续走原按钮路径。

## 已知边界
- 选项内的文本片段是普通文本，暂不支持把「词汇图片」嵌进用户手打的字符串中间（必须通过选词项）。
- 选项项较多时预览行会横向排列，窄屏可能溢出（可删除重排）。
- 音频听感、真实麦克风仍 N/A。
