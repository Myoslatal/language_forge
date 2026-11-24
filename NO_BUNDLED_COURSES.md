# 安装后无默认课程 / 课程外置 — 验证

Date: 2026-11-25 (Asia/Shanghai)
APK: `dist/LanguageForge-1.0-release-signed.apk`（CN=kjoremorf，debug/unsigned 不用于本条）

## 要求
- 软件安装后项目列表必须为空；不内置、不 seed 任何课程。
- 迁移课程以仓库外文件提供，仓库和 APK 内不包含课程数据。
- 用户手动导入外部 .conlang 或新建项目后才出现内容。

## 代码改动
- Android `ProjectStore.initialize()` 不再调用 `seedSamples()`；删除 sample 列表、`.seeded` marker 和未使用的
  seed 辅助方法。首次启动只创建/确认项目根目录，列表为空。
- 桌面本来没有 seed；外部 .conlang 由用户通过「导入 .conlang」手动选择。
- 迁移脚本默认输出改为仓库外 `/ai/nortu`（可用 `FORGE_MIGRATION_OUT` 覆盖）。
- 仓库内 `migration/` 只保留脚本说明和迁移文档；已删除 `migration/generated/*` 与 `migration/*.conlang`。

## 实测
- **Android 全新安装**（先 `adb uninstall`，再安装 signed release APK）：
  - 项目列表标题「我的项目 0」；
  - 空状态「还没有项目 / 新建一个项目开始制作关卡」；
  - 无任何默认课程卡片。
  - 截图：`/tmp/fresh-signed-empty.png`（debug 构建的空列表同证据：`/tmp/fresh-android-empty.png`）。
- **桌面全新数据目录** `/tmp/fresh-empty`：显示「0 个项目 · 置顶优先」「还没有项目」。
  - 截图：`/tmp/fresh-check.png`。
- 双端 clean build 成功；桌面 smoke 两次 PASS（smoke 从仓库外
  `/ai/nortu/Nortu-Intro-html.conlang` 读取 TTS 迁移夹具，
  `tts_migration=true`）。

## 仓库外课程文件（供手动导入）
- `/ai/nortu/nortu-l1-2.conlang`（3 课 46 题，23 段 WAV，Nozu Sans）
- `/ai/nortu/Nortu-Intro-html.conlang`（HTML demo 10 题）
- 对应生成项目目录：同目录下 `Nortu-L1-2/`、`Nortu-Intro-html/`
- 这些文件不在 Git 工作区、不会打进 APK；安装后必须手动导入。

## 说明
- 已有安装升级不会被强制清空：升级只会停止 seed；用户已有项目保留。若要完全空列表，需清除 App 数据/重装。
- 历史 Git 提交中仍可能包含旧迁移产物；当前 HEAD 工作区已无课程数据。
