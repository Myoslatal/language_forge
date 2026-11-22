# 全入口「选词 → 手写文字」渲染审计 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :138, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求
凡可「从词库选词」的入口，手写词汇（VocabularyEntry.type=="image"）必须以手写图呈现，不能退化成编码/释义文字；编辑器预览与学习页一致。

## 入口清单与结论
| 入口 | 编辑器预览 | 学习页 | 证据 |
|---|---|---|---|
| 句子词汇（choice/listen/arrange segments） | 图片 (renderParts，Android；桌面分段预览) | 图片 Text/ImageSpan | 既有 choice/arrange 测试 + 代码审查 |
| 题干词汇（type/cloze promptItems） | 图片预览行 | 图片+文本混排 | TYPE 既有测试；CLOZE 本次 /tmp/android-hw-cloze-filled.png、/tmp/desk-hw-cloze-filled.png |
| 选项词汇（choice/listen/dialog/reading/custom） | 每项预览图片 | 选项行图片 | 既有 CHOICE_ARRANGE/LISTEN/DIALOG/READING 测试；custom 本次 /tmp/android-hw-custom-choice.png、/tmp/desk-hw-custom-choice5.png、/tmp/android-hw-custom-editor.png |
| 词块（arrange/custom blocks） | 词块行图片 | 词块池/已选图片 | /tmp/android-hw-arrange.png；桌面 drawBlockChip 代码审查 + ARRANGE 既有测试 |
| 配对两侧（match） | 左右预览图片 | 左右列图片 | /tmp/android-hw-match.png；MATCH_IMAGE_TESTING.md |
| 克漏候选词/题干 | 图片 | 候选行/题干图片 | /tmp/android-hw-cloze-filled.png、/tmp/desk-hw-cloze-filled.png |
| 克漏空格填入 | — | 手写图+镜像文本（本次修复） | 同上，空格内显示 ✎ a |
| 卡片词 | 预览图片 | 卡片大图 | CARD_TESTING.md |
| 自定义题型 | 选项预览图片 | 选项图片 | 上述 custom 截图 |

## 本次修复
- Android LearningActivity：克漏空格由纯文本按钮改为 LinearLayout 槽位，填入图片词汇时绘制 24dp 等比手写图 + 镜像文本；空位仍「＿＿」，点击槽位清除。
- 桌面 main.cpp：克漏空格在按钮前绘制 24x24 手写图（textureForImage），候选词沿用 clozeItems 的图片渲染。

## 回归
- Android clean build SUCCESS；桌面 clean build SUCCESS；桌面 smoke 两次 PASS。
- 其它题型未改数据格式；图片缺失统一显示〔图片缺失〕。

## 已知边界
- 克漏空格匹配图片依据镜像文本（displayName/codes）；若有多个图片词汇镜像文本相同，可能取第一个匹配项。
- 桌面克漏空格中图片区域本身不可点击（点击文字按钮清除）。
- 图片词汇的额外文本镜像在部分入口（克漏空格、词块）同时展示，属于辅助信息。
- 自定义题型的题干当前为纯文本输入（没有选词按钮），不在「选词入口」范围内。
- 音频听感、真实麦克风仍 N/A。
