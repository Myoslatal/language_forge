# 自定义题型（custom）— QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求（对齐 HTML）
QT.custom：自定名称与正文，作答方式可选 选项 / 输入答案 / 词块组句 / 不作答（讲解页）；题干支持词库词汇。

## 数据
- customLabel / customBody / customMode（choice|type|blocks|none），仅 custom 题型写入。
- 复用 prompt/promptItems、options/optionItems/correctIndex、acceptedAnswers/ignore 开关、blocks/answers；name = customLabel。
- 保存按模式校验并清空其它模式字段（choice 清答案/词块；type 清选项/词块；blocks 清选项/答案；none 全清）。

## 编辑器（双端）
- 名称/正文/题干/作答方式；choice 共享四项选项编辑（选词/预览/正确答案）；type 可接受答案 + 三个开关；blocks 词块行（勾选正确）+ 从词汇选择；none 无字段。
- 证据：/tmp/android-custom-editor-choice.png、/tmp/android-custom-editor-type.png、/tmp/android-custom-editor-blocks.png、/tmp/desk-custom-editor-choice.png、/tmp/desk-custom-editor-type.png、/tmp/desk-custom-editor-blocks2.png。
- JSON 往返：Android /tmp/custom-saved.json（choice 保留 label/body/mode/options/correct；type/none/blocks 原样保留）；桌面保存 blocks 修改后 blocks ok 状态生效、answers 清空，choice correctIndex 修改生效。

## 学习端（双端）
- Android：
  - choice：名称/正文卡片/题干/四选项；选「对」→ 绿色「回答正确」+ 下一题。/tmp/android-custom-choice-learn.png、android-custom-choice-correct.png。
  - type：输入 et, no zon（接受答案）→ 绿色「回答正确」+ 下一题。/tmp/android-custom-type-learn.png、android-custom-type-correct.png。
  - none：讲解页 +「继续」。/tmp/android-custom-none.png。
  - blocks：按 et , no zıon 顺序选块 → 绿色「回答正确」+ 完成。/tmp/android-custom-blocks-learn.png、android-custom-blocks-selected2.png、android-custom-blocks-correct.png。
- 桌面：
  - choice → 绿色 + 下一题：/tmp/desk-custom-choice-learn.png、desk-custom-choice-correct.png。
  - type 输入 ASCII 变体 → 正确：/tmp/desk-custom-type-learn2.png、desk-custom-type-correct.png。
  - none → 继续：/tmp/desk-custom-none.png。
  - blocks 选块 → 正确 + 完成：/tmp/desk-custom-blocks-learn2.png、desk-custom-blocks-selected.png、desk-custom-blocks-correct.png。

## 修复记录（验收时发现）
- 桌面保存缺少「把 editQuestion 写回 course」步骤，导致编辑器保存是空操作；已补（choice 改正确答案、blocks 改勾选均可落盘）。
- 桌面 custom blocks 分支起初清空了 blocks 再校验，已改为保留并校验。
- Android 自定义题型缺少初始 section 分支 / 模式联动；已补 updateCustomModeUi（choice 显示共享选项、type 显示答案区、blocks 显示词块区、none 无）。
- 题目列表 custom 行标题与类型标签重复显示名称；改为标题取题干/正文首行，标签用自定义名称。

## 构建/回归
- Android clean build、桌面 clean build；桌面 smoke 两次 SMOKE PASS；其它题型未改。

## 已知边界
- Android 自定义题型切到「输入答案/词块组句」时，复用的 type/arrange 编辑区渲染在自定义字段上方（字段顺序略跳），功能与保存不受影响。
- 词块组句模式在保存时写入 blocks 与 answers（answers 为空时学习端按正确词块顺序判定）；桌面编辑器未单独提供 answers 文本编辑，直接沿用 arrange 的答案列表（本夹具通过自动顺序验证）。
- 正文点词提示在 Android 使用固定弹层锚点（沿用阅读题型）。
- 音频听感、真实麦克风仍 N/A。
