# WorkBuddy 分享站课程迁移：Nortu 自由语 L1-2

## 来源与产物

- 来源页面：<https://nortu-free-language-l1-2.app.workbuddy.host/?sharecode=TNWRVMOI4N&shareId=b9a912e4a4844f22ac2017fd7985e543>
- 抓取页面：`/tmp/nortu-site.html`（3,159,596 bytes，sha256 `18339ed927378bb4cee4c1676090405eb66ecf2377550206910c20235f6611e4`）
- 播放器数据：`/tmp/nortu-pack.json`（2,907,501 bytes，schema `clf-pack/2`，sha256 `c85fea85dadd163171f6453be16dca049c77995fb3c2c4750315f1908aa3fba5`）
- 可复现脚本：`tools/migrate_workbuddy_course.py`
- 项目目录（仓库外）：`/home/forin/language-forge-imports/Nortu-L1-2/`（34 个 ZIP 条目：7 个 JSON、23 个 WAV、1 个 TTF）
- 导入包（仓库外）：`/home/forin/language-forge-imports/nortu-l1-2.conlang`（1,129,898 bytes，sha256 `a06a17e7be67ad1f727452f1705e50eb8a68ad7b1a9f837f32c344e6bbc6b7f8`）
- 仓库内不保存本次课程数据；`migration/generated/Nortu-L1-2/` 与 `migration/nortu-l1-2.conlang` 均不存在。

生成结果：1 个 chapter「Nortu 自由语」、3 个 unit（第1关/第2关/第3关）、每个 unit 1 个 course，共 **3 课 46 题**。

```
chapters=1 units=3 courses=3 questions=46
choice=30 arrange=9 match=3 type=2 listen=2
```

课程按 unit 顺序排列，课程 id 稳定：

| 顺序 | 课程 id | 课程名 | 题数 |
| --- | --- | --- | --- |
| 1 | `wb-course-u-206r4i5` | 第1关 · 你好！自由语 | 15 |
| 2 | `wb-course-u-4n6lk4m` | 第2关 · 早上好！ | 15 |
| 3 | `wb-course-u-zymkonp` | 第3关 · 晚安，我的小猫 | 16 |

## 题目映射

- source `q.id` → `wb-<q.id>`，例如 `q_el4nd18` → `wb-q_el4nd18`；重复运行稳定。
- source `prompt + " " + sub` 合并为 app 的 `prompt` / `name` / `sentence`（app 没有独立 `sub` 字段）。`explain` 原样保留。
- `choice` → `translation_choice`：选项文本进入 `options` 和 `optionItems`，`ok:true` 的索引写 `correctIndex`；选项图 9 个未迁移，文本全部保留。
- `arrange` → `arrange`：每个词块生成稳定 id `wb-<q.id>-b<N>`，`ok` 保留；`answers` 写入正确词块 id 顺序，source `alt` 保留；词块文本命中词表时写 `vocabId`。
- `match` → `match`：`pairs` 的 `l/r` 以及 `leftItems/rightItems` 保留；左侧 Nortu 词命中词表时使用 vocab item，以便提示/发音，右侧中文保持 text。
- `type` → `type`：source `answers` → app `acceptedAnswers`，`warn`、`near`、`ignoreCase`、`ignoreSpace` 保留；source 的 `alt` 也保留。
- `listen` → `listen`：`options` + `correctIndex` + 音频、`tts/ttsLang` 保留；音频缺失时仍走软件原有 TTS 兜底（本课程 listen 音频齐全）。
- 项目级 `ttsLang` = `en-US`（来自 `course.ttsLang`）；每题 `tts`/`ttsLang` 保留（本 pack 均为空字符串）。

## 音频

- 23 个 `recs` 均为 `data:audio/webm;codecs=opus;base64,...`；脚本用 `ffmpeg` 转为单声道 22.05 kHz PCM WAV，写入 `resources/audio/<rec>.wav`。
- 38 道题有 `question.audio`（choice 29、arrange 7、listen 2），引用 23 个唯一 WAV；`audios.json` 恰好 23 条，文件名/`code` 与 rec id 对应。
- `trim.s/e`（秒）转为毫秒：`audioStart=round(s*1000)`、`audioEnd=round(e*1000)`，`audioDuration` 为整段 WAV 时长；非法/过短区间回退整段。示例：

```json
{"id":"wb-q_bysxfhc","type":"listen","audio":"resources/audio/r_kndm5zs.wav","audioStart":688,"audioEnd":1555,"audioDuration":1740}
```

- 38 道题的 `audio` 路径全部存在（脚本输出 `missing_audio=0`；WAV 总数 23）。
- 15 个 Nortu 词条复用其整词题目的录音做词条发音（同一 WAV，按题目 trim 设置 `audioStart/End`），便于点击下划线词试听。

## 字体

- 从页面第一个 `@font-face` 的 `data:font/ttf;base64,...` 解码 **Nozu Sans TTF**（138,304 bytes），写入 `resources/fonts/nozu_sans.ttf`。
- `fonts.json` 注册 id `wb-font-nozu-sans`，`active` 指向它；桌面和 Android 导入后均能加载字体。
- 页面另有 `Nozu Sans Nortu` WOFF2；现有字体管理只接受 TTF/OTF/TTC，未迁移（列在跳过项）。

## 词汇

- `glossary` 15 条全部导入；题目 `glosses`/`kp` 合并后得到 **17** 条词条（额外 2 条为中文释义词「晚上好」「小狗」，用于下划线提示）。
- 4 个词性：形容词 `adj`、名词 `n`、代词 `pron`、叹词/语气词 `interj`；写入 `pos.json`。
- 词条保留文本、释义、音标、词性；题目 `glosses` 的中文→Nortu 映射会合并到对应 Nortu 词条（例如 `et` 增加「你好」）。
- `kp` 只用于补词表，不作为独立 question 字段保存；所有 kp 都能在词表中找到。

## 跳过项与原因

| 字段/资源 | 数量 | 处理 |
| --- | --- | --- |
| 选项图片 `option.image` | 9 | 跳过图片，选项文本保留 |
| `question.image` | 0 | 无 |
| `label` | 19 非空 | app 无字段，跳过 |
| `alias` | 0 | 跳过 |
| `hint` | 27 非空 | app 无 question hint 字段；词汇提示由词表提供 |
| `tag` / `note` | 0 | 跳过 |
| `level` | 46 全有 | app 无字段，跳过 |
| `ff` | 6 非空 | 字体样式字段，app 无字段，跳过 |
| `shuffle` | 46 个字段（45 true/1 false） | app 不支持 per-question shuffle，跳过 |
| `multi` | 46 全 false | app 单选模型；本课程无多选 |
| `lines/passage/card` | 全空 | 无内容可迁 |
| `Nozu Sans Nortu` WOFF2 | 1 | app 字体存储不接受 WOFF2 |
| `sfx/anim/imgMax` | pack 顶层 | 页面音效/动画配置，不属于课程数据 |

## 复现

```bash
# 默认从 /tmp/nortu-pack.json + /tmp/nortu-site.html；缺失时会尝试从 --url 下载页面
python3 tools/migrate_workbuddy_course.py

# 可指定输入/输出
python3 tools/migrate_workbuddy_course.py \
  --pack /tmp/nortu-pack.json --html /tmp/nortu-site.html \
  --output-dir /home/forin/language-forge-imports/Nortu-L1-2 \
  --conlang /home/forin/language-forge-imports/nortu-l1-2.conlang
```

脚本会先删除并重建输出目录与 `.conlang`；相同输入重复运行，7 个 JSON 与 23 个 WAV 的 sha256 完全一致（已实际 diff 验证），因此幂等。若 `/tmp/nortu-pack.json` 不存在，会从 HTML 的 `const PACK={...}` 提取；HTML 也不存在时才访问 `--url`。字体需从 HTML 或 `--font` 提供。

资源核对命令：

```bash
python3 - <<'PY'
import json, glob
base = '/home/forin/language-forge-imports/Nortu-L1-2'
st = json.load(open(base + '/structure.json'))
qs = [q for ch in st['chapters'] for u in ch['units'] for c in u['courses'] for q in c['questions']]
aud = json.load(open(base + '/audios.json'))['audios']
print('questions', len(qs), 'audio questions', sum(bool(q['audio']) for q in qs))
print('wav files', len(glob.glob(base + '/resources/audio/*.wav')), 'audios.json', len(aud))
print('missing', [q['id'] for q in qs if q['audio'] and not __import__('os').path.isfile(base + '/' + q['audio'])])
print('vocabulary', len(json.load(open(base + '/vocabulary.json'))['entries']))
print('fonts', json.load(open(base + '/fonts.json')))
PY
```

## 实测记录

### 桌面（SDL2）

- 用「导入 .conlang」UI 导入仓库外的 `/home/forin/language-forge-imports/nortu-l1-2.conlang`；首页显示 **章 1 · 关 3 · 题 46 · 词 17**。
- 课程列表显示 1 章 3 课；第 1 课完成后第 2 课解锁，`progress.json` 写入 `completedCourseIds/unlockedCourseIds`；为验证第 3 课，将第 2 课完成状态写入同一进度结构后，列表显示 3 课全部可进入，且第 3 课首题正常打开。
- 逐题实测：choice、arrange（含逐字母拼写）、type、match、listen；非 listen 题的「播放题目音频」按钮和自动播放均走 `FORGE_LEARN questionAudio`，listen 走原有 `listenPlay`/TTS 分支。类型截图与日志来自同一迁移数据；listen 截图来自把该课 listen 题排到首位的派生听力项目（音频/题目内容相同）。
- 音频证据日志示例：

```
FORGE_LEARN questionAudio resources/audio/r_gh3nshv.wav 660..1994
FORGE_AUDIO play /tmp/.../resources/audio/r_gh3nshv.wav label=resources/audio/r_gh3nshv.wav 660..1994
FORGE_LEARN listenPlay resources/audio/r_kndm5zs.wav 688..1555
```

- 截图：导入首页、结构/课程列表、choice 音频播放、arrange、type、match、listen（`/tmp/wb-shots/`）。

### Android

- 通过项目列表「导入」文件选择器导入同一 `.conlang`；列表显示 3 关 46 题 17 词。
- 学习首页第一题 choice：显示通用「播放题目音频」按钮；自动播放日志 `questionAudio resources/audio/r_emeikbk.wav 836..1460`，点击按钮再次播放。
- 完成第 1 课全部 15 题（choice/arrange/type/match），出现「本课完成 / 下一课：第2关」。
- 第 2 课实测至 listen：日志 `listenPlay resources/audio/r_kndm5zs.wav 688..1555`，点「重新播放」再次触发；choice/arrange/type/match 已在第 1/2 课实际完成。为补齐 Android 类型截图，另用同一数据派生「题型验证」项目（type→match→arrange 顺序）截图；截图后已删除该临时项目，正式迁移项目仍在设备上。
- 项目 JSON 核对：`structure.json` 1 章 3 unit 46 题；`audios.json` 23；`resources/audio` 23 个 WAV；`fonts.json` active Nozu Sans；`vocabulary.json` 17；`project.ttsLang=en-US`。

### 构建

- 桌面 clean build（`cmake --build` 从空 build 目录）成功。
- Android clean `./gradlew clean :app:assembleDebug :app:assembleRelease --no-build-cache` 成功。
- 桌面 smoke 连续两次 `SMOKE PASS`。
- 未修改 `NostongueForge_v1.3.1.html`；不 commit/push。

## 边界

- 无头环境只能证明 WAV/MediaPlayer/引擎调用、UI 和 JSON；不能声称「听到」声音。真实耳机可听性需在真机复核。
- 第 3 课解锁链路通过写入 app 的 `progress.json` 完成状态验证；第 2 课未完整手打 15 题，报告中如实说明。
- 选项图片按任务约定跳过；文本和正确答案完整。
- 迁移不改变 app 的 TTS 行为；listen 仍优先音频，缺音频时沿用 TTS。
