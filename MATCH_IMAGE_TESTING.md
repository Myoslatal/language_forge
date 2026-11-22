# 配对题（match）选词与手写词汇渲染 — QA

Date: 2026-11-22 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :134, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged

## 需求
配对题每对的左右两侧支持“从词汇选择器选词”，并对手写（图片）词汇显示真实图片，而不是释义文本。

## 数据模型
pairs 每项新增可选 lItems / rItems（与 optionItems 同构：{kind:"text",text} 或 {kind:"vocab",vocabId}），l/r 仍是文本镜像：

    {"l": "a", "r": "手写字母 a",
     "lItems": [{"kind":"vocab","vocabId":"vocab-hand-a"}]}

- 镜像规则与其它题型一致：image 词汇取 codes[0] → meanings[0] → 〔图片〕。
- 保存时：某侧含 vocab 项且侧文本仍等于镜像 → 保留 items；否则该侧 items 丢弃（旧纯文本配对不受影响）。
- 旧文件无 lItems/rItems 时按 l/r 造文本项仅供显示，不写回。

## Android（API36）
- 配对学习：左侧 image 词显示手写图块，点选变蓝，与右侧释义配对成功后两侧绿色、已配对 1/2。
  证据：/tmp/android-match-img-learn.png、/tmp/android-match-leftsel2.png、/tmp/android-match-correct2.png。
- 配对编辑：每对左右各有「选词左/选词右」，预览行显示图片/文本 + × 删除；从选择器选入手写词后镜像变为 "a" 并出现图片缩略图。
  证据：/tmp/android-match-img-editor.png、/tmp/android-match-picked.png、/tmp/android-match-removed2.png。
- 保存/重开 JSON：lItems/rItems 按规则保留（vocabId），纯文本侧不写入。
  证据：/tmp/match-saved2.json（lItems 保留；纯文本侧无 rItems）。

## 桌面（SDL2 / Xvfb）
- 配对学习：手写图块行 + 释义列，配对成功后绿色、配对正确提示。
  证据：/tmp/desk-match-learn2.png、/tmp/desk-match-correct.png。
- 配对编辑：「选词左/选词右」打开词汇选择器，选中后侧文本变镜像、预览出现缩略图；× 删除后文本同步回退。
  证据：/tmp/desk-match-editor5.png、/tmp/desk-match-rightpicked.png、/tmp/desk-match-removed2.png。
- 保存 JSON：两侧 vocab 项均保留（lItems/rItems）；纯文本侧仅保留 l/r 文本。
  证据：/tmp/optimg2/桌面测试-optimg2/structure.json 中 match-img-1。

## 回归
- 纯文本配对：不写 lItems/rItems，学习端继续走 ForgeButton/Button 路径（夹具第二对 apple/苹果、smoke 的 match 流程均未见异常）。
- Android clean build、桌面 clean build 成功；桌面 smoke 两次 SMOKE PASS。

## 已知边界
- 配对判题仍是“左序号 == 右序号”，与是否选词无关。
- 一对方若两侧都选同一张手写图（测试中曾出现），属内容问题，不属于渲染缺陷。
- 音频听感、真实麦克风仍 N/A。
