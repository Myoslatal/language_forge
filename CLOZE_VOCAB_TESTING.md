# 选词填空候选词/题干词汇支持 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求
选词填空的候选词（选项）与题干要支持词库词汇（手写文字/手写图），不能只存平铺文本。

## 数据
- options 仍是候选词的可见文本镜像；新增 clozeItems（每个候选词一组 OptionItem：{kind:"text",text} / {kind:"vocab",vocabId}），与 options 下标对齐（空候选已压缩）。
- 题干复用 promptItems；prompt 为可见文本镜像。
- 保存规则与其它题型一致：结构化项存在且镜像文本 == 输入框文本才保留；否则退化为文本项；纯文本时不写 clozeItems/promptItems。

## 编辑器（双端）
- 题干行增加「题干选词」+ 预览（手写图/文本/×/＋文本/标点），候选词每行增加「选词」+ 预览。
- 选词面板多选后按顺序追加；镜像文本实时更新；正确词勾选、空格数提示、校验不变。
- 证据（桌面）：/tmp/desk-clozev-editor3.png；Android：/tmp/android-clozev-editor.png。

## 学习端（双端）
- 题干按 promptItems 渲染：手写图/显示名/文本混排。
- 候选词按 clozeItems 渲染：含词汇的候选显示手写图/显示名行，点击仍填入第一个空格；空格内显示候选镜像文本（手写词显示其编码/显示名文本）。
- 证据（桌面）：/tmp/desk-clozev-learn.png、/tmp/desk-clozev-filled.png、/tmp/desk-clozev-correct.png。
- 证据（Android）：/tmp/android-clozev-learn.png、/tmp/android-clozev-filled2.png、/tmp/android-clozev-correct2.png。

## JSON 往返
- 桌面保存后：promptItems [vocab hand-a, text " 请填空"]、clozeItems [[vocab hand-a],[vocab apple],[text 你好],[text 谢谢]]、options/clozeOrder 保留。
- Android 保存后同上（/tmp/clozev-saved.json）。

## 构建/回归
- Android clean build、桌面 clean build；桌面 smoke 两次 SMOKE PASS。
- 其它题型未改。

## 已知边界
- 空格槽位显示候选镜像文本（手写词显示编码/显示名），未在空格内绘制手写图。
- 选项模式下「手写词 + 文本」混排会按既有规则插入空格，题干镜像同理。
- 其它题型的题干仍为纯文本（选项/词块/配对侧已支持词汇）；如需全部题型题干支持词汇可继续扩展。
