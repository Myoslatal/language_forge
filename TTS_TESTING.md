# TTS 语音合成测试报告（Android + SDL2 桌面）

日期：2026-11-24（Asia/Shanghai）
仓库：/ai/language_forge
任务：task-76「TTS 语音合成（学习 listen + 词汇示例语音 TTS 朗读音标）双端」
HTML 参考：NostongueForge_v1.3.1.html（未修改，sha256
7c1f30f8b37f7b4b91dd31ae1f63032f10e10b9ccdb516b6045a4228e39643dd）

## 1. 实现摘要

### 数据模型

- `Question` 增加 `tts` / `ttsLang`，JSON 键为 `"tts"` / `"ttsLang"`，所有题型读写；
  读取时兼容早期的 `ttsText`。`project.json` 增加项目级 `ttsLang`，缺省/空值按 `en-US`。
- 学习 listen 的解析顺序：`question.ttsLang` → 项目 `ttsLang` → `en-US`；
  朗读文本：`question.tts`（空则 `prompt`）。rate = 0.85（桌面 espeak-ng 换算 149 wpm）。
- 迁移脚本把 HTML 的 `q.tts`、`q.ttsLang` 和 `course.ttsLang` 写入迁移项目；重新生成的
  `migration/Nortu-Intro-html.conlang` 中 10 道题均带 `tts`/`ttsLang` 键，listen 题为
  `tts="et"`、`ttsLang="en-US"`，项目 `ttsLang="en-US"`；原有 `listen_et.wav` 保留。

### 桌面（SDL2 + ImGui + espeak-ng）

- 新增 `desktop/src/tts.cpp/.h`：`detectTtsEngine`（缓存探测）、`synthesizeTts`、
  `wavDurationMs`、语言归一化/回退。Linux 使用 `espeak-ng -v <voice> -s 149 -w out.wav -f text.txt`，
  文本经临时 UTF-8 文件传递，避免 shell 注入；失败自动用默认 voice 重试一次。
  Windows 为 PowerShell `System.Speech`，macOS 为 `say --data-format=LEI16@22050`。
- 学习 listen：音频文件存在则走原 `playAudioFile`（裁剪区间、自动播放、日志
  `FORGE_LEARN listenPlay` 不变）；文件为空/缺失时 `playListenAudio` 合成缓存 WAV 后复用
  `playAudioPath`，日志 `FORGE_TTS synth/ok` 与 `FORGE_LEARN listenTts`。页面显示
  “本题缺少音频，将使用 TTS 朗读：<text> · <lang>”；TTS 不可用显示明确错误。
- listen 编辑器新增「朗读文本（TTS）」「朗读语言」「TTS 试听」；允许无音频、仅 TTS 的听音题，
  切换为 TTS-only 时删除旧音频文件。
- 词汇编辑器「示例发音」新增「TTS 朗读音标」「生成示例音频」：文本
  `phonetics[0]` → `phonetic` → `text` → 显示名；语言为项目 `ttsLang`。生成写
  `resources/audio/pending_tts_<id>.wav`，设置 `audio/audioStart/audioEnd/audioDuration`；
  保存时注册 `audios.json`（名称 `TTS：…`）并替换旧 TTS 注册项。

### Android（Java + android.speech.tts.TextToSpeech）

- 新增 `TtsSupport.java`：异步初始化（8s 超时）、BCP-47 `setLanguage`、不支持语言回退并
  toast、`QUEUE_FLUSH`、rate 0.85、`synthesizeToFile`、缺失引擎/语言错误提示、
  `UtteranceProgressListener` 回调；Manifest 增加 TTS_SERVICE 的 `<queries>`（Android 11+）。
- 学习 listen 与桌面同规则：audio 优先，空/缺失时自动播放/按钮走 TTS；页面显示
  “本题缺少音频，将使用 TTS 朗读：…”，引擎不可用时显示“系统 TTS 不可用：…”且不崩溃。
- listen 编辑器新增「朗读文本」「朗读语言」；允许 TTS-only 保存，重开 JSON 正确；
  TTS-only 保存时删除旧音频文件。
- 词汇编辑器示例发音区新增「TTS 朗读音标」「生成示例音频」：生成成功显示时长，
  保存复制到 `resources/audio/vocab_<entryId>_<timestamp>.wav`，写
  `audio/audioStart/audioEnd/audioDuration`，并向 `audios.json` 注册 `TTS：…` 记录。

## 2. 构建与 smoke

### 桌面 clean build

命令：

```bash
rm -rf desktop/build
cmake -S desktop -B desktop/build -DCMAKE_BUILD_TYPE=Release
cmake --build desktop/build -j"$(nproc)"
```

结果：`BUILD_EXIT=0`，产物 `desktop/build/nostongue-forge`（2,944,352 bytes）。

### 桌面 smoke ×2（同一数据根）

```bash
rm -rf /tmp/forge-final2-smoke /tmp/forge-final2-shots /tmp/forge-final2-shots2
xvfb-run -a -s "-screen 0 1400x900x24" ./desktop/build/nostongue-forge \
  --data-dir /tmp/forge-final2-smoke --smoke /tmp/forge-final2-shots
xvfb-run -a -s "-screen 0 1400x900x24" ./desktop/build/nostongue-forge \
  --data-dir /tmp/forge-final2-smoke --smoke /tmp/forge-final2-shots2
```

结果：第一次 `SMOKE PASS`（created=true），第二次 `SMOKE PASS`（created=false）。
两次报告均包含：

```text
tts_engine=true tts_synth=true tts_editor=true tts_listen=true
tts_audio_priority=true tts_vocab=true tts_migration=true
```

报告路径：`/tmp/forge-final2-shots/smoke-report.txt`、`/tmp/forge-final2-shots2/smoke-report.txt`；
TTS 结构化证据：`/tmp/forge-final2-shots/tts-evidence.json`：

```json
{
  "engine": "espeak-ng",
  "engine_available": true,
  "probe_duration_ms": 712,
  "listen_editor": true,
  "listen_fallback": true,
  "listen_text": "et",
  "listen_lang": "en-US",
  "listen_audio_duration_ms": 712,
  "audio_priority": true,
  "vocab_tts": true,
  "vocab_audio": "resources/audio/pending_tts_f9e14edd65824230a10cec17e7c9c08b.wav",
  "vocab_audio_duration_ms": 2736,
  "migration_tts": true,
  "migration_text": "et",
  "migration_lang": "en-US"
}
```

### Android clean build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/home/forin/Android/Sdk
cd android
./gradlew clean :app:assembleDebug :app:assembleRelease --no-build-cache
```

结果：`BUILD SUCCESSFUL`；`app-debug.apk` 9,443,096 bytes，
`app-release-unsigned.apk` 7,701,105 bytes。安装/启动 EnvCheck_API36 无崩溃。

## 3. 桌面证据

### 3.1 espeak-ng 合成

日志：

```text
FORGE_TTS synth engine=espeak-ng lang=en-US voice=en-us rate=0.85 wpm=149 text=et
  out=/tmp/forge-final2-shots/tts-probe.wav
FORGE_TTS ok engine=espeak-ng voice=en-us file=/tmp/forge-final2-shots/tts-probe.wav
  bytes=31452 durationMs=712
```

`/tmp/forge-final2-shots/tts-probe.wav`：RIFF PCM 16-bit mono 22050 Hz，31452 bytes，712 ms。
另有学习 TTS WAV `/tmp/forge-final2-shots/tts-listen.wav`（同内容，712 ms）。

### 3.2 学习 listen：TTS 兜底与音频优先

- 迁移项目导入（smoke 内调用 `Store::importConlang`）后：10 题、项目 `ttsLang=en-US`、
  listen `tts=et`、`ttsLang=en-US`；证据日志：
  `FORGE_SMOKE tts_migration data=1 tts=1 text=et lang=en-US err=`。
- 迁移 listen 临时清空 audio 后自动播放 TTS：
  `FORGE_LEARN listenTts question=html-q7 text=et lang=en-US file=/tmp/nostongue-forge-tts/tts_187ce2ba7cd9189a.wav playing=1`。
  截图：`/tmp/forge-final2-shots/tts-migration-learn.png`、`tts-listen-learn.png`。
- 恢复 audio 后音频优先：`FORGE_LEARN listenPlay resources/audio/listen_et.wav 0..520`，
  截图 `/tmp/forge-final2-shots/tts-audio-priority.png`。
- 真实 UI 点击（最终构建，Xvfb :99 + xdotool）：项目卡片 → 学习 → 自动进入无音频 listen 题，
  自动播放 TTS；再点击「重新播放」按钮，日志新增一次
  `FORGE_AUDIO play /tmp/nostongue-forge-tts/tts_187ce2ba7cd9189a.wav label=tts://... 0..712` 与
  `FORGE_LEARN listenTts ... playing=1`；截图
  `/tmp/tts-evidence/desktop/desktop-click-listen-playing.png`，日志
  `/tmp/tts-evidence/desktop/desktop-listen-click-tts.txt`。
- 新建/保存的 TTS-only listen 题 JSON（`structure.json`）：
  `{"type":"listen","tts":"et","ttsLang":"en-US","audio":"","audioDuration":0}`；
  重开编辑器截图 `/tmp/forge-final2-shots/tts-listen-editor.png`，输入值均为 et、en-US。

### 3.3 词汇编辑器：TTS 朗读音标 / 生成示例音频

真实 UI 点击（xdotool，Xvfb :99，1280x860）：

1. 打开 桌面测试 → 编辑项目 → 第一课 → 资源管理 → 词汇表 → 搜索 banana → 双击进入编辑；
2. 点击「TTS 朗读音标」→ 日志 `FORGE_AUDIO play /tmp/nostongue-forge-tts/tts_f463881c7abf8cdc.wav label=tts://... 0..2736`；
   截图 `/tmp/desktop-ui-tts-preview.png`；
3. 点击「生成示例音频」→ 日志
   `FORGE_TTS synth ... text=/ˈæp.əl/ ... pending_tts_6b43cd1ff6d149c6aa67081882be135c.wav` +
   `FORGE_TTS ok ... bytes=120712 durationMs=2736`；截图 `/tmp/desktop-ui-tts-generated.png`；
4. 点击「保存词汇」→ `FORGE_TTS registered audio id=514677e1-...`；落盘 JSON：

```json
{
  "id": "0f285710-5947-43e6-987e-fe9418f09f13",
  "text": "banana",
  "phonetic": "/ˈæp.əl/",
  "audio": "resources/audio/pending_tts_6b43cd1ff6d149c6aa67081882be135c.wav",
  "audioStart": 0.0,
  "audioEnd": 2736.0,
  "audioDuration": 2736.0
}
```

`audios.json` 对应：
`{"name":"TTS：/ˈæp.əl/","file":"resources/audio/pending_tts_6b43cd1ff6d149c6aa67081882be135c.wav","duration":2736,...}`。
smoke 的 `vocab-tts-editor.png` / `vocab-tts-generated.png` / `vocab-tts-reopen.png` 显示按钮、
生成后的时长与再次打开后的播放/裁剪控件。词汇音频文件在
`/tmp/forge-final2-smoke/桌面测试-*/resources/audio/pending_tts_*.wav`。

## 4. Android 证据

环境：EnvCheck_API36（1080x2400），Android 16，Google TTS 引擎 `com.google.android.tts`。
注意：本环境为 headless 模拟器，**无法用耳朵确认实际出声**；以下只声称“引擎初始化成功 /
synthesizeToFile 成功 / speak 的 onStart 被回调 / UI 与 JSON 正确”，不声称听到声音。

### 4.1 学习 listen：音频优先

- 题目 1（有 `listen_et.wav`）：日志
  `FORGE_LEARN listenPlay resources/audio/listen_et.wav 0..520`；UI 无 TTS 提示。
  截图：`/tmp/android-tts-audio-priority.png`。
- 题目 2（audio 为空、`tts=et`、`ttsLang` 留空 → 项目 en-US）：
  - UI：`本题缺少音频，将使用 TTS 朗读：et · en-US`；
  - 日志：`FORGE_LEARN listenTts et en-US` →
    `FORGE_TTS speak text=et lang=en-US rate=0.85 engine=com.google.android.tts` →
    `FORGE_TTS onStart forge-speak-...`；
  - 最终 APK 截图：`/tmp/android-final-tts-listen.png`（早期构建同界面：
    `/tmp/android-tts-listen.png`）。

### 4.2 listen 编辑器 tts/ttsLang 保存重开

- 编辑题 2：朗读文本字段显示 `et`，朗读语言输入 `en-US` 后保存；
  `structure.json` 片段：
  `{"id":"tts-listen-tts","type":"listen","prompt":"听到的词是什么意思？（无音频，TTS）","tts":"et","ttsLang":"en-US","audio":"","audioDuration":0,"correctIndex":0}`；
- 重开编辑器：朗读文本 `et`、朗读语言 `en-US`、尚未选择音频；
  截图：`/tmp/android-tts-listen-editor.png`、`/tmp/android-tts-listen-editor-reopen.png`。

### 4.3 词汇编辑器：TTS 朗读音标 / 生成示例音频

- UI：示例发音区出现「TTS 朗读音标」「生成示例音频」和“TTS 文本：e de · en-US”。
  截图：`/tmp/android-tts-vocab-editor.png`。
- 点击「生成示例音频」：日志
  `FORGE_TTS synthesize text=e de lang=en-US rate=0.85 out=/data/user/0/.../cache/audio_...wav`、
  `FORGE_TTS onStart forge-synth-...`、`FORGE_TTS synthesize ok ... bytes=31964`；
  UI 显示 `audio_...wav · 时长：0.7s`；截图 `/tmp/android-tts-vocab-generated.png`。
- 最终 APK 复测（2026-11-24 23:39）：同样从 UI 点「生成示例音频」→ 保存，日志
  `FORGE_TTS synthesize ... audio_fa79e421...wav` / `synthesize ok ... bytes=31964`，保存后
  `resources/audio/vocab_nortu-1861_1795534774991.wav`，`audios.json` 只保留一条 `TTS：e de`
  注册项（旧 TTS 项已清理）；截图 `/tmp/android-final-vocab-editor.png`、
  `/tmp/android-final-vocab-generated.png`。
- 点击保存后 `vocabulary.json`：

```json
{
  "id": "nortu-1861",
  "text": "et",
  "phonetic": "e de",
  "phonetics": ["e de"],
  "audio": "resources/audio/vocab_nortu-1861_1795534774991.wav",
  "audioStart": 0,
  "audioEnd": 665,
  "audioDuration": 665
}
```

`audios.json` 新增：

```json
{"name":"TTS：e de","file":"resources/audio/vocab_nortu-1861_1795534774991.wav","duration":665,...}
```

文件 `resources/audio/vocab_nortu-1861_1795534774991.wav`，31,964 bytes。
重开编辑器截图 `/tmp/android-tts-vocab-reopen.png`（早期构建同 UI）与最终 APK 的
`/tmp/android-final-vocab-editor.png`：显示“时长：0.7s”、播放/试听裁剪/区间控件。

### 4.4 TTS 引擎不可用降级

临时执行 `adb shell pm disable-user --user 0 com.google.android.tts` 后重进 listen 题 2：

- 页面显示“本题缺少音频，将使用 TTS 朗读：et · en-US”以及一行
  “系统 TTS 不可用：未安装语音合成引擎”；
- Logcat：`FORGE_TTS: unavailable: 未安装语音合成引擎`；
- 无崩溃，选项/重新播放仍可见；最终 APK 截图 `/tmp/android-final-tts-unavailable.png`。
- 测试后已 `pm enable com.google.android.tts` 恢复。

### 4.5 迁移数据（Android）

把重新生成的迁移项目 push 到模拟器后检查：

- `project.json`：`"ttsLang": "en-US"`；
- `structure.json`：10 题均含 `tts` 键；listen 题
  `tts="et"`、`ttsLang="en-US"`、`audio="resources/audio/listen_et.wav"`。
- 对应文件：`/sdcard/Android/data/io.github.myoslatal.languageforge/files/projects/Nortu-Intro-TTS/`。

## 5. 已知边界

1. **无法确认实际可听声音**：桌面为 Xvfb、Android 为 headless 模拟器；只能证明
   espeak-ng / Google TTS 被调用、WAV 生成、SDL Audio/MediaPlayer 播放调用与 UI 正确。
   真实扬声器/耳机可听性列入 [REAL_DEVICE_TESTS.md](REAL_DEVICE_TESTS.md) RD-07。
2. Windows PowerShell System.Speech、macOS `say` 分支已实现但未在本 Linux 环境运行验证。
3. 项目级 `ttsLang` 目前来自迁移/默认值，桌面与 Android 尚无独立的“项目设置”编辑 UI；
   listen 题仍可逐题覆盖语言。
4. TTS 直接朗读音标字符串（例如 `/ˈæp.əl/`），espeak-ng 会把 IPA 符号按字符读；
   这符合“由 TTS 朗读音标”的数据来源要求，但不是严格音素发音。需要更自然发音可改用
   真实录音或后续接入音素映射。
5. 生成文件沿用现有导入/录制流程的 `pending_tts_<id>.wav` 命名（与 `pending_<id>` 一致）；
   `audios.json` 中的 TTS 条目名带前缀，替换时会清理旧 TTS 注册项，避免累积死链。
6. 模拟器 API36 自带 Google TTS；不同设备/语言的引擎可用性不同，代码在缺失/不支持时给出
   toast + 页内提示，不崩溃。

## 6. 关键代码位置

- 数据：`desktop/src/model.h`（Question.tts/ttsLang、ProjectSummary.ttsLang）、
  `desktop/src/model.cpp`（读写/ttsText 兼容）、
  `android/app/src/main/java/io/github/myoslatal/languageforge/ProjectStructure.java`、
  `Project.java`、`ProjectStore.java`。
- 桌面 TTS：`desktop/src/tts.h`、`desktop/src/tts.cpp`、`desktop/CMakeLists.txt`。
- 桌面 UI/播放：`desktop/src/main.cpp` 的 `projectTtsLang/playTtsText/playListenAudio`、
  学习 listen、listen 编辑器、词汇示例发音、smoke TTS 段。
- Android TTS：`android/.../TtsSupport.java`、`LearningActivity.java`、
  `QuestionEditorActivity.java`、`VocabularyEditorActivity.java`、
  `res/layout/activity_vocabulary_editor.xml`、`AndroidManifest.xml`。
- 迁移：`tools/migrate_html_project.py`、`migration/generated/Nortu-Intro-html/`、
  `migration/Nortu-Intro-html.conlang`、`migration/HTML_MIGRATION.md`。
