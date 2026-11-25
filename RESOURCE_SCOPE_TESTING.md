# 项目级词汇资源 + 课程级题目资源（双端）

Date: 2026-11-25

## 设计

资源分为两个作用域，项目文件结构如下：

```text
project/
  vocabulary.json / pos.json / fonts.json
  audios.json                 # 全局词汇音频清单
  images.json                 # 全局词汇图片清单
  resources/audio/            # 词汇示例音频（项目共享）
  resources/images/           # 词汇图片（项目共享）
  resources/courses/<courseId>/
    audios.json               # 该课程题目音频清单
    audio/                    # 该课程题目音频文件
```

- **项目资源（全局）**：词汇、词性、字体、词汇音频、词汇图片；所有课程共享同一份。
- **课程资源（仅本课程题目）**：只显示该课程题目音频；课程 A 的页面/清单不会出现课程 B 的文件。
- 题目音频路径统一为 `resources/courses/<courseId>/audio/...`；词汇路径保持
  `resources/audio/...`、`resources/images/...`。
- 课程清单一律为 `schema:1` 的 `{"audios":[...]}`，与全局清单同格式；文件路径均为项目相对路径。
- 课程 ID 在磁盘上使用安全段（非 `[A-Za-z0-9._-]` 替换为 `_`）；当前 UUID 课程 ID 不受影响。

## 自动迁移

加载项目 / 导入 `.conlang` / 导出前调用同一迁移器（Android
`ResourceScopeMigration.migrate`，桌面 `migrateResourceScopes`）。

1. 扫描 `vocabulary.json`：`audio` 引用若不在全局目录则复制到 `resources/audio`，
   `image` 引用若不在全局目录则复制到 `resources/images`，并登记到全局清单。
2. 扫描 `structure.json` 每个课程的问题：
   - 已有 `resources/courses/<该课程>/audio/...` 路径：补课程清单，不重复复制。
   - 旧全局/其他路径：按 `(courseId, 原路径)` 生成稳定文件名复制到该课程目录，更新
     `question.audio`，写入课程清单。同一课程内多个问题复用同一源文件时只复制一份。
3. 全部复制并重写成功后，才清理只被题目引用、不再被词汇引用的旧全局音频清单项和文件。
   - 同一文件同时被词汇与题目引用：全局保留一份，课程目录另有一份。
   - 缺失文件保留原路径并记录，不会删除任何仍可读取的文件。
4. 所有 JSON 原子写；迁移器幂等；`.conlang` 导出前再跑一次，导入后立刻跑一次。
5. 课程删除时删除 `resources/courses/<courseId>`；项目删除仍由项目目录递归删除。

## API/编辑器兼容

- Android `AudioStore(projectDirectory, courseId)` 与桌面 `AudioStore(projectDir, courseId)`
  共用原清单/导入/编码/删除逻辑，只是清单与文件目录改为课程作用域。
- 题目编辑器的录音、TTS 生成和词汇示例选择在保存时复制到课程目录；播放仍按项目相对路径解析。
- 词汇编辑器仍写入全局 `resources/audio`/`resources/images`。
- 旧项目路径仍可直接读取；第一次加载/导入自动迁移。

## 实测

### 2 课程夹具（项目级词汇音频/图片 + A/B 各自题目音频，A 的题目音频与词汇共用）

- 夹具：`/tmp/lf-resource-fixture.conlang`（仓库外）。
- 桌面导入后：项目资源页 2 词、1 图片、1 全局音频；A 课程资源页只有
  `shared_a.wav`（1 段、被 1 题引用）；B 课程资源页只有 `b.wav`，看不到 A。
  截图：`/tmp/lf-resource-desktop/project-resources.png`、
  `/tmp/lf-resource-desktop/course-a-resources.png`、
  `/tmp/lf-resource-desktop-b/course-resources.png`。
- 迁移结果：A/B 均生成 `resources/courses/<id>/audios.json`；共享音频全局保留，B 的
  全局旧文件被课程副本替代；词汇图片保持全局。
- Android 导入后：项目资源页显示 2 词/1 图片/1 全局音频；A/B 课程资源页分别只有
  A/B 的题目音频（1 条且只引用本课程）。截图：
  `/tmp/lf-resource-android-project.png`、
  `/tmp/lf-resource-android-course-a.png`、
  `/tmp/lf-resource-android-course-b-audio.png`。

### Nortu L1-2（仓库外 `/ai/nortu/nortu-l1-2.conlang`，3 课 46 题 38 音频引用 23 WAV）

- 桌面导入后：`questions=46`、`audio=38`、全局 `audios.json=15`；
  三个课程清单分别 8/7/10 个唯一文件；38 个引用全部存在（missing=0）。
- Android 导入同一文件：迁移日志
  `moved=38 copied=25 removedGlobal=8 missing=0`；38 个题音频路径全部
  `resources/courses/...` 且文件存在；学习页自动播放
  `resources/courses/wb-course-u-4n6lk4m/audio/q_..._r_emeikbk.wav 836..1460`。
- 全局词汇音频保留 15 条，17 词仍共享；词汇图片为 0（该课程没有图片）。

### 旧项目与 .conlang 往返

- 迁移前布局的夹具项目直接放到桌面数据目录，打开即自动迁移并可播放。
- 桌面导出 `/tmp/task78-roundtrip.conlang` 后再导入：
  - 导出 ZIP 同时含 `resources/audio/shared_a.wav` 与两门课程的
    `resources/courses/<id>/audio/...` 及 `audios.json`；
  - 重新导入后仍为 2 题、2 音频引用、0 missing，全局音频 1 条、两个课程清单一对一，
    没有重复文件。
- Android 导出同一夹具得到同类 ZIP（全局共享音频 + 两个课程目录），ZIP 完整；把该 ZIP 再导入后仍有 2 题、2 音频引用、0 missing，全局音频 1 条、A/B 两个课程清单各 1 条，无重复。

## 验证命令

```bash
# 桌面
cmake --build desktop/build -j$(nproc)
desktop/build/nostongue-forge --smoke /tmp/scope-smoke-1 --data-dir /tmp/scope-smoke-data

# Android
cd android && ./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 边界

- 题目级题图与选项附图均已实现（task-80/task-79）；课程级图片池同时服务题图与选项图，`resources/courses/<id>/images` 与 `images.json` 生效，题图全题型显示。
- 删除单题不会自动回收不再引用的课程图片/音频文件；文件仍只在该课程作用域，可在课程资源页手动清理。
- 删除单个题目不会自动回收不再引用的课程音频文件；文件仍只在该课程作用域内，不影响其他课程。
  迁移器不会把课程文件误并回全局。
- 无头/模拟器只能证明文件存在、清单和播放调用；可听性仍见 RD-08。
- 迁移会保留缺失文件的引用路径，便于用户修复；不会把缺失文件制造为空文件。

## 选项附图（task-79）

### 存储与数据

- 课程级题目图片：`resources/courses/<courseId>/images/` + 同目录 `images.json`；
  与全局词汇图片（`resources/images/` + 项目根 `images.json`）完全分离。
- 选项图片字段：`optionItems[optionIndex][itemIndex].image`，值是该项目的相对路径；
  一个选项的图片取该选项组第一个带 `image` 的 item。旧 JSON 没有 `image` 时按空处理。
- 选项可只有图片：文字字段留空，判定仍按 `correctIndex`；图片只影响展示。
- 附图入口只出现在 choice / listen / dialog / reading(choice) / custom(choice)；
  cloze 没有入口，也不会把图片字段渲染为候选词。
- 题目资源图片选择器只枚举当前课程 `resources/courses/<courseId>/images`；
  可从选择器导入新图片（复制进当前课程、注册课程 `images.json` 后立即选中）。
- 课程资源页显示「课程题目图片」和真实计数；被选项引用的图片禁止删除。

### 自动迁移

- 加载/导入/导出时扫描 `optionItems` 的 `image` 引用；旧全局/外部路径按
  `(courseId, 原路径)` 生成稳定文件名复制到当前课程 `images/`，写回结构并登记课程清单。
- 同一课程多个选项/题目引用同一文件只复制一份；不同课程各自复制，绝不共享课程图片路径。
- 词汇图片和题目图片同源时：全局词汇图片保留，课程再复制一份。仅题目引用的旧全局
  图片在课程副本写入成功后从全局清单和目录移除；缺失文件保留引用并计数。
- 所有 JSON 原子写；迁移幂等；课程删除递归删除整个 `resources/courses/<courseId>`。

### 双端实测

桌面 fixture（`/tmp/lf-image-desktop/Option Image Test`）：

- 旧布局 `/tmp/lf-image-fixture/Option Image Test` 导入后迁移：
  `FORGE_RESOURCE_MIGRATION copied=0 copiedImages=2`；A/B 各自生成
  `resources/courses/<id>/images.json` + 一张课程图片；全局 `images.json` 只保留 `vocab.png`。
- 课程 A 图片页只出现 `shared.png`（被本课程 1 个选项引用）；B 的课程清单只有 `b.png`。
  桌面 B 资源页截图未单独留档，B 的隔离由清单和 Android B 页截图验证。
- 桌面编辑器点击「更换图」打开课程图片选择器；选择已有 `shared.png` 后保存，结构写回
  `optionItems[1][0].image`。点击「导入新图片」并选中新图后立即附图到选项 1；保存重开仍保留。
  截图：`/tmp/lf-image-desktop/editor-a.png`、`/tmp/lf-image-desktop/picker-a.png`、
  `/tmp/lf-image-desktop/picker-two.png`、`/tmp/lf-image-desktop/picker-existing.png`、
  `/tmp/lf-image-desktop/saved-existing2.png`。
- 学习页把选项图片显示在文字上方、居中；纯图片第一项判定正确，截图：
  `/tmp/lf-image-desktop/learn-result.png`。
- reading(choice) 附图学习和编辑器截图：`/tmp/lf-image-desktop/reading-editor.png`、
  `/tmp/lf-image-desktop/learn-reading.png`。
- cloze 编辑器没有附图入口：`/tmp/lf-image-desktop/cloze-editor.png`。
- 桌面 `.conlang` 导出再导入：4 道 A 题 + 1 道 B 题、2 张 A 图片 + 1 张 B 图片完整，
  无重复；导出 ZIP 同时含两个课程 `images.json` 和图片文件。

Android fixture（`Option Image Test-e3e7a1`）：

- 导入旧 `.conlang` 自动迁移：`movedImages=2 copiedImages=2 removedGlobalImages=2 missing=0`；
  文件路径全部为 `resources/courses/course-a|b/images/...`。
- 课程资源页显示「课程题目图片」1 张；A 图片页只有 `shared.png`，B 图片页只有 `b.png`；
  B 页面不会出现 A 图片。截图：`/tmp/lf-image-android-a-images.png`、
  `/tmp/lf-image-android-b-images.png`。
- 编辑器「更换图 / 移除图 / 附图」；打开选择器只有本课程图片；点「导入并选择」后
  新图复制进 `resources/courses/course-a/images`、登记并立即附图。保存后
  `structure.json` 的 `optionItems[0][0].image` 指向新课程图片。
- 编辑器分别选中已有 `shared.png`（选项 2）和导入的新图（选项 1），保存后结构同时保留两条
  课程图片路径；学习页图片显示在选项文字上方，纯图片选项判定通过，截图：
  `/tmp/lf-image-android-a-editor.png`、`/tmp/lf-image-android-a-editor-new.png`、
  `/tmp/lf-image-android-a-learning.png`、`/tmp/lf-image-android-a-answered.png`。
- Android reading(choice) 附图、编辑器与学习页均已验证：
  `/tmp/lf-image-android-reading-learn2.png`。
- Android 导出 ZIP 含两门课程图片目录/清单；ZIP 文件见
  `/tmp/option-image-android-export.conlang`。

### 边界

- 选择器/学习端只按当前课程 `courseId` 过滤，课程间资源不共享。
- 删除单个题目不会自动回收它不再引用的课程图片；文件仍只在该课程作用域内。
- 无头/模拟器只能证明图片路径存在、清单与渲染/判定调用；真实视觉清晰度仍由截图和
  设备显示决定。

## 题目图片（task-80）

### 数据与作用域

- `Question.image` 保存项目相对路径；旧结构没有该字段时按空处理。双端
  `structure.json` 读写、项目复制、`.conlang` 导出/导入都保留该字段。
- 题图和选项图共用课程图片池 `resources/courses/<courseId>/images/` 与
  `images.json`，不会使用全局词汇图片池。题图在所有题型中都可用。
- 同一文件既可被题图引用，也可被选项图引用；课程内只保留一份文件，删除被引用图片
  会同时检查题图与选项图。

### 编辑器与学习端

- 双端题目编辑器都在题型控件下提供「题目图片（全题型）」：缩略图、更换图、移除图；
  选择器只列出当前课程图片，并提供「导入新图片」后立即选中。
- 题图可以为空；纯图片题目允许题干/正文为空（支持 choice、card、reading、dialog
  等有文字校验的题型），保存重开保留。
- 学习端在题型标题之后、其它题目内容之前显示题图，居中、自适应宽度，最大高度约
  330；缺失文件显示「〔题目图片缺失〕」。card、cloze、custom(none) 等所有分支一致。

### 迁移与引用

- 加载/导入/导出时扫描 `question.image` 和 `optionItems[*][*].image`；非课程路径复制
  到所属课程并重写，登记课程清单；同一文件复用。
- 旧全局图片若仅被题图引用，课程副本成功后再清理全局清单/文件；仍被词汇引用时保留。
- 课程删除清理整个 `resources/courses/<courseId>`；题图/选项图引用均计入删除保护。

### 双端实测

桌面 fixture：`/tmp/lf-image-desktop/Option Image Test`。

- 卡片题 `qcard` 的旧题图 `resources/images/vocab.png` 在加载时复制为
  `resources/courses/course-a/images/q_c65d65eab242f4b5_vocab.png` 并重写；原全局文件保留。
- 编辑器截图：`q-editor.png`（choice）、`q-card-editor-before.png`（card）；
  已有课程图选择：`q-picker.png`；现场导入新图并附图：`q-card-imported.png`。
- 学习端：`q-learn-choice.png`（题图+选项图同题）、`q-learn-reading.png`、
  `q-learn-custom.png`、`q-learn-cloze.png`（cloze 题图）、`q-learn-card2.png`
  （card 题图）、`q-learn-pure.png`（纯图片题）。
- 纯图片题保存：`q-pure-saved.png`；保存后 `parts=[]`、`sentence=""`、`image` 保留。
- `.conlang` 导出 `/tmp/q80.conlang`，导入为新项目
  `Option Image Test-f92564`：6 道 A 题 + 1 道 B 题、4 张 A 图 + 1 张 B 图，题图
  路径和时间戳图片均保留，无重复。

Android fixture：`Option Image Test-e3e7a1`。

- 卡片题旧全局题图迁移日志：`movedImages=1 copiedImages=1 removedGlobalImages=0 missing=0`；
  课程 A 中生成 `q_5b8f5b82b3ea9772_vocab.png`。
- 编辑器题图控件在 choice/card/cloze 均可见；课程 A 选择器只列出 A 的图片。
  题图现场导入新图并保存为
  `resources/courses/course-a/images/img_a911473a-cbc0-44f9-b5c2-cdc0d4473ec4_1795607027468.png`。
- 学习端：`task80-android-learn-qa.png`（题图+选项图）、`task80-android-learn-cloze.png`
  （cloze 题图）、`task80-android-learn-card.png`（card 题图）、
  `task80-android-learn-pure.png`（纯图片题）。
- 纯图片保存：`task80-android-pure-save.png`；数据仍为 `parts=[]`、`sentence=""`。
- 课程 A 图片页引用显示题图标签，例如
  `被本课程 6 个选项引用：shared · 题目图、…`；题图参与删除保护。
- 删除课程 A 的一张题图文件后重进学习：页面显示 `〔题目图片缺失〕`，不崩溃；
  同文件选项显示 `〔选项图片缺失〕`。截图：`task80-android-missing.png`。
- Android 导出 ZIP `/tmp/q80-android.conlang` 含 4 张 A 图 + 1 张 B 图、A/B 清单与
  题图路径；重新导入为 `Option Image Test-55d711` 后题图路径和图片完整。

### 边界

- 修改题图不会自动删除旧题图文件；课程图片页可按引用状态手动清理。
- 无头截图证明布局/路径/判定；真实屏幕清晰度仍以设备显示为准。
