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

- 当前 schema 没有题目图片字段；课程级图片目录/清单**未启用**，仅保留路径设计语义。
  题目图片将来可复用 `resources/courses/<id>/images` 与同构清单。
- 删除单个题目不会自动回收不再引用的课程音频文件；文件仍只在该课程作用域内，不影响其他课程。
  迁移器不会把课程文件误并回全局。
- 无头/模拟器只能证明文件存在、清单和播放调用；可听性仍见 RD-08。
- 迁移会保留缺失文件的引用路径，便于用户修复；不会把缺失文件制造为空文件。
