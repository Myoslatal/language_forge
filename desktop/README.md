# Nostongue Forge Desktop (SDL2 + Dear ImGui)

Phase 1 native desktop build of Nostongue Forge. C++17, SDL2, Dear ImGui (SDL_Renderer2 backend),
and vendored miniz/nlohmann/json. No Electron, no web runtime, and no network access.

## Features (Phase 1)

- Project library: create/import, pinned ordering, project cards with stats, updated time,
  card menu (pin/export/delete), learning placeholder, edit structure.
- Project structure: chapters → units → courses tree, expand/collapse, create/rename/move/delete
  with confirmation; every change writes `structure.json`.
- Course editor: question list with 01/02 numbering, create, move up/down, delete, and a
  resource-management placeholder page. Question rows open the translation-choice editor:
  ordered vocabulary parts (picker), whole-sentence WAV recording/playback/removal, four
  options with a unique correct answer, validation, and structure.json persistence; old
  name-only questions load compatibly.
- Project-level `.conlang` ZIP import/export using the built-in ImGui file browser (no native
  file dialogs). Supports root or single top-level-folder ZIPs, Zip Slip protection, unique
  import directories, and preserves every file in the project directory.
- Light/dark theme persisted to `ui.json` in the data root (never inside a project).
- Chinese font loading (Noto CJK on Linux, Microsoft YaHei/SimHei/Segoe UI candidates on Windows).

## Build (Linux)

Dependencies: CMake ≥ 3.20, a C++17 compiler, SDL2, SDL2_ttf, SDL2_image, SDL2_mixer development
packages. On Arch: `sudo pacman -S cmake sdl2 sdl2_ttf sdl2_image sdl2_mixer`.

```bash
cd desktop
cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j"$(nproc)"
```

Run:

```bash
./build/nostongue-forge
# or with an isolated data root:
./build/nostongue-forge --data-dir /tmp/forge-data
```

### Xvfb / headless smoke

All automated checks are run under Xvfb; do not launch test windows on the user's :0 desktop.

```bash
xvfb-run -a -s "-screen 0 1400x900x24" \
  ./build/nostongue-forge --data-dir /tmp/forge-sdl-smoke --smoke /tmp/forge-sdl-shots
```

The smoke mode creates/loads “桌面测试” with one chapter/unit/course and two questions, writes
`home.png`, `structure.png`, `course.png`, `resources.png`, tests ZIP export/import, invalid ZIP
rejection, pin/unpin, delete, and persistence. Run it twice against the same data root: the second
run reports `created=false` and verifies the persisted data.

## Windows build

Install dependencies with vcpkg, for example:

```powershell
vcpkg install sdl2 sdl2-ttf sdl2-image sdl2-mixer
cmake -S desktop -B desktop/build -DCMAKE_TOOLCHAIN_FILE=C:/vcpkg/scripts/buildsystems/vcpkg.cmake
cmake --build desktop/build --config Release
```

CMake uses `find_package(SDL2 CONFIG)` and the SDL2_ttf/image/mixer config packages on Windows.
If a vcpkg triplet uses different target names, adjust the `SDL2_*_TARGET` variables in
`desktop/CMakeLists.txt`. Copy the required SDL2, SDL2_ttf, SDL2_image and SDL2_mixer DLLs next
to `nostongue-forge.exe` (or ensure they are on `PATH`). The app uses UTF-8 paths and
`std::filesystem`; fonts are searched in `C:/Windows/Fonts/msyh.ttc`, `msyhbd.ttc`,
`simhei.ttf`, and `segoeui.ttf`.

## Data layout

Default data root:

- Linux: `$HOME/Documents/Nostongue Forge/projects`
- Windows: `%USERPROFILE%/Documents/Nostongue Forge/projects`
- Fallback: `SDL_GetPrefPath("NostongueForge", "NostongueForge")/projects`

`--data-dir <dir>` overrides the root. Each project is a directory named
`sanitize(project name)-<6 hex>` containing:

```text
project.json
structure.json
```

`project.json` fields are compatible with Android:
`schema, name, courseLine, lessons, questions, words, updated, createdAt, pinned, ttsLang`
(`ttsLang` defaults to `en-US`; old files without it read as `en-US`).
`structure.json` is `{"schema":1,"chapters":[{"id":"uuid","name":"...","units":[{"id":"uuid","name":"...","courses":[{"id":"uuid","name":"...","questions":[{"id":"uuid","name":"..."}]}]}]}]}`
with UUIDs. Writes use a temp file + rename; malformed/missing JSON is treated as empty.

## Phase 2: vocabulary, picker and fonts

- Project-level `vocabulary.json` (`schema:1`, `entries`) uses the Android-compatible fields
  `id, type, meanings, codes, text, codepoints, parts, image, imageWidth, imageHeight, audio,
  audioStart, audioEnd, audioDuration, createdAt, updatedAt`. Text/Unicode/combo types are
  editable; image/custom-text editing and audio fields remain Phase 3 placeholders and image
  entries cannot be saved yet.
- The vocabulary list shows a type badge, display name, code/meaning summaries and delete
  confirmation. Display names are: text for `text`, actual UTF-8 characters for `unicode`,
  concatenated child display names for `combo` (with cycle protection), and first meaning for
  image/fallbacks. Unicode codepoint inputs accept `12ab`, `0x12AB` and uppercase forms and
  normalize to lowercase hex without prefix.
- The editor has dynamic meaning/code rows, type selection, text/Unicode inputs, a combo preview
  and a reusable multi-select vocabulary picker. The picker searches codes, text, codepoint
  strings, actual characters, meanings and display names; `aaa` finds `apple`. Selected parts
  keep click order and exclude the current entry.
- Project-level `fonts.json` (`schema:1`, `active`, `fonts`) stores `id, name, file,
  createdAt`; files live under `resources/fonts/`. Import uses the built-in file browser
  (TTF/OTF/TTC), validates with SDL_ttf `TTF_OpenFontIndex` (TTC first face), copies to a unique
  name and rejects invalid/WOFF files without residue. The Fonts page lists System Default,
  sets/removes the active font and falls back to System Default if the file is missing/broken.
- The active font is loaded as a separate 22px ImGui font and applied only to vocabulary list
  display names, picker row titles, Unicode previews, combo chips and combo previews. UI labels,
  buttons, meanings and codes keep the normal UI font. CJK/Ethiopic fallback glyphs are merged so
  Unicode/CJK vocabulary stays visible even with a Latin serif font.
- `.conlang` export/import includes `vocabulary.json`, `fonts.json` and
  `resources/fonts/` automatically (whole project directory).
- Smoke mode now creates `apple` (code `aaa`), a Unicode entry (`12ab/34cd`), a combo, imports
  the system Noto Serif TTF, sets it active and captures `vocabulary.png`,
  `vocabulary-editor.png`, `picker.png`, `fonts.png` and `font-vocab.png`.

## Phase 3A: images and custom-text media

- Project-level `images.json` (`schema:1`, `images`) stores
  `id, name, file, code, width, height, createdAt`; files live under
  `resources/images/`. Writes use tmp + atomic rename; bad/missing JSON loads empty and
  relative paths are checked against the project root. Import uses the built-in ImGui file
  browser with PNG/JPG/JPEG/WEBP/BMP filtering, SDL_image decode validation, a unique
  `img_<uuid>_<timestamp>.<ext>` copy, width/height probing and cleanup on failure.
- The Images page lists registered images plus unregistered files in
  `resources/images/` (marked “未设置”/“未被引用”), with thumbnails, name, file/size, code,
  reference summary, search over code/name/file and a ⋯ menu for setting the code or deleting.
  Vocabulary entries of type `image` are scanned from `vocabulary.json`; referenced images
  show “被 N 个词汇引用：…” and cannot be deleted from the UI. Unreferenced registered images
  delete their entry and file; unregistered files delete only the file.
- Custom-text vocabulary (`type: image`) is enabled. The editor offers “导入图片” and
  “手绘文字”; importing opens a crop modal (scaled preview, draggable rectangle with corner/edge
  handles, minimum 32px, outside mask), and drawing opens a width/height preset modal
  (32–4096, default 512) followed by a transparent canvas with black brush, eraser, clear,
  undo and redo. Strokes are recorded as point sequences with brush/eraser and width; undo/redo
  replay the transparent canvas from scratch, including single-point strokes and eraser order.
- Crop and draw results are written to `resources/images/pending_<id>.png` and update
  `image/imageWidth/imageHeight` as pending media. Saving the vocabulary keeps the pending file;
  cancelling/back deletes it; replacing/removing an existing image deletes the old file
  best-effort after a successful save. Image-type saves require an existing image with
  width/height > 0.
- Smoke mode generates a test PNG, imports it, registers code `img1`, creates an image
  vocabulary referencing it, adds an unregistered file, verifies reference protection and
  unreferenced deletion, crops a pending PNG, draws two strokes (undo/redo) and verifies a
  300×200 transparent PNG, then captures `images.png`, `image-edit-code.png`,
  `image-used-by-vocab.png`, `vocab-image-editor.png`, `image-crop.png` and
  `image-draw.png`.
- `.conlang` export/import includes `images.json` and `resources/images/`.

## Resource scopes (project vs course)

- **项目资源（全局）** is opened from the Structure page. It contains the shared vocabulary,
  POS, fonts, vocabulary audio (`resources/audio`) and vocabulary images (`resources/images`).
- **课程资源** is opened from a course editor. It lists only that course's question audio,
  `resources/courses/<courseId>/audios.json` and its `audio/` directory; global items are not
  shown. `AudioStore(projectDir, courseId)` scopes import/code/play/delete.
- Question recording and selected vocabulary audio are copied into the course scope on save.
  Legacy projects and `.conlang` import/export run `migrateResourceScopes`: vocabulary stays
  global, each question audio/image moves to its course (with a copy when a vocabulary shares
  the file), updates the manifests and is idempotent. See
  [RESOURCE_SCOPE_TESTING.md](../RESOURCE_SCOPE_TESTING.md).
- Choice-like questions (choice, listen, dialog, reading-choice, custom-choice) can attach a
  course question image to an option. The editor's "附图" opens a course-only picker with
  "导入新图片"; image-only options are valid and the image renders above the option text.
  Cloze has no image entry/rendering. Vocabulary images remain global; course question images
  live in `resources/courses/<courseId>/images/` with a course `images.json`.

## Phase 3B: audio and vocabulary pronunciation

- Project-level `audios.json` (`schema:1`, `audios`) stores
  `id, name, file, code, duration, createdAt`; duration is milliseconds and files live
  under `resources/audio/`. Writes use tmp + atomic rename, bad/missing JSON loads empty and
  relative paths are checked against the project root. Import uses the built-in ImGui file
  browser with WAV/MP3/OGG/M4A/FLAC filtering, SDL_mixer `Mix_LoadMUS` validation,
  `Mix_MusicDuration` timing, a unique `aud_<uuid>_<timestamp>.<ext>` copy and cleanup on
  failure.
- The Audios page lists registered audio plus unregistered files in `resources/audio/`
  (marked “未设置”/“未被引用”), with note icon, name, file/duration/size, code, reference
  summary, search over code/name/file and a ⋯ menu for setting the code or deleting. Every
  vocabulary entry's `audio` path is scanned; referenced audio shows “被 N 个词汇引用：…”
  and cannot be deleted from the UI. Unreferenced registered audio deletes its entry and file;
  unregistered files delete only the file.
- All vocabulary types have a “示例发音” section: import audio, record with
  `SDL_OpenAudioDevice` (mono 44.1kHz S16 WAV, graceful toast when no capture device is
  available), play/stop, re-import, re-record, remove, and start/end trim sliders in
  milliseconds. Playback uses `Mix_LoadMUS`/`Mix_PlayMusic` with
  `Mix_SetMusicPosition(start)` and a timed stop at the trim end; starting another playback
  stops the previous one. The saved interval must satisfy
  `0 <= start < end <= duration` and be at least 100ms; audio is optional.
- Imported/recorded audio is written to `resources/audio/pending_<id>.<ext>` and kept only
  when the vocabulary is saved; cancel/back deletes it and replacing/removing an old file is
  best-effort after a successful save. Audio is optional, but if present the file must exist
  and the trim must be valid.
- Smoke mode writes a 1.5s WAV, imports it as `aud1`, binds it to `apple` with
  `start=200/end=1200`, verifies reference protection and unreferenced deletion, records the
  no-device path gracefully, rejects invalid audio without residue, captures `audios.png`,
  `audio-edit-code.png`, `audio-used-by-vocab.png`, `vocab-audio.png` and
  `audio-trim.png`, and checks `.conlang` includes `audios.json` and
  `resources/audio/`.
- The top bar now reads `DESKTOP · SDL2`; the footer version is `v0.3.0`.
- `.conlang` export/import includes `audios.json` and `resources/audio/`.

## Parts of speech (POS)

- Project-level `pos.json` (`schema:1`, `entries`) stores
  `id, name, description, code, createdAt, updatedAt`. Writes use tmp + atomic rename;
  missing/bad JSON loads as an empty list; names are trimmed and required, description/code are
  optional and trimmed.
- The Resources page has a **词性管理** card with the real count. The POS page lists name, code
  (or 未设置) and description; row click edits, ⋯ menu edits/deletes with confirmation, and the
  empty state reads “还没有词性”.
- The editor modal has **名称** (required; e.g. 名词/动词), **描述** (multiline) and **编码**
  inputs, save/cancel, loads existing values and refreshes the list. The page works in light and
  dark themes with Chinese labels.
- Smoke mode creates 名词 (n) and 动词 (v), edits their descriptions/codes, deletes a temporary
  entry and captures `pos.png` / `pos-editor.png`; `pos.json` is included in `.conlang`
  whole-directory export/import. Vocabulary binding is intentionally not added in this round.

## Multi-POS vocabulary meanings

- `vocabulary.json` entries may carry `posMeanings:[{posId,meanings}]`; `meanings` remains
  the aggregated/deduplicated compatibility and search field. Saving with POS selected writes
  the aggregate in POS order; saving without POS keeps generic meanings.
- The vocabulary editor has a **词性（可多选）** checkbox row. With no POS selected it shows
  generic meanings; with POS selected it hides generic meanings and renders one group per POS
  in `pos.json` order, each with add/remove rows. The first selected POS migrates non-empty
  generic meanings; every selected group needs at least one non-empty meaning or save is
  blocked with a toast. Unknown groups from deleted POS are ignored on open/save.
- Vocabulary rows show colored POS badges and grouped summaries; the picker shows the same
  badges and searches POS names/codes in addition to meanings/codes/text/codepoints.
- Smoke creates `banana` with 名词/动词 groups, checks aggregated meanings, old `apple`
  compatibility, empty-group validation, reload and `.conlang`, and captures
  `vocab-pos.png`, `vocab-pos-editor.png`, `vocab-pos-picker.png`.

## Phonetic text

- Vocabulary entries have an optional `phonetic` string (trimmed, capped at 200 chars,
  default empty) stored alongside `text` in `vocabulary.json`. Old files without it load
  as empty; no format or uniqueness validation is applied.
- The editor has a single-line **音标（可选）** input near the codes, with helper text
  `不校验格式，例如 /ˈæp.əl/`. It loads existing values, saves edits, and an empty field
  writes an empty string; the list shows “音标：…” only when non-empty.
- Smoke sets `/ˈæp.əl/` on `banana`, verifies reload/persistence, old `apple` compatibility,
  `.conlang` retention and captures `vocab-phonetic.png`.

## Lexicon JSONL import

- The Vocabulary page has **导入词表** (skip), **覆盖合并**, and **合并释义**; the file browser
  accepts `.jsonl/.json/.txt` and imports through `lexicon.cpp` into `pos.json` +
  `vocabulary.json`. The first non-empty line is the Lexicon `_meta` (15 types in the sample);
  `rendered`/string `entry` becomes text, `pronunciation` becomes phonetic, and
  `definition`/`detail` become the POS meanings and aggregate. POS are matched by code then
  name and created in meta order when absent. Matching uses text (trimmed/case-insensitive) and
  Unicode display text (codepoints rendered to characters); combo/image entries are not matched.
  Overwrite/merge update a matched Unicode entry in place and preserve type, codepoints, id,
  codes and media; non-empty pronunciation replaces/appends per mode. Before writing, the
  importer preflights file-internal duplicate groups and opens one modal listing every group
  with per-row **合并释义** / **保留第一条** toggles, plus 全部合并/全部保留, 开始导入 and 取消;
  cancel writes nothing, and choices are independent of the external mode.
- Duplicate text is skipped (case-insensitive/trimmed), as are Unicode display matches and
  file-internal duplicates under the chosen policy; bad JSON and empty-text/empty-definition
  rows are invalid. A toast reports added / updated / merged / duplicate / invalid / new POS.
- The vocabulary list and picker both paginate at 50 rows with prev/next and an ImGui SliderInt
  jump-to-page slider; search resets to page 1 and picker selections persist across pages.
  Lists/picker show all phonetics joined by “ / ” and search every value. Smoke imports the full
  sample, checks merge/duplicate/overwrite, multi-phonetic data, page/slider and picker state,
  and captures vocab-merge.png, vocab-multi-phonetic.png, vocab-page-slider.png and
  vocab-picker-page.png.

## TTS (system speech synthesis)

- `question.tts` and `question.ttsLang` mirror the HTML Web Speech data and are read/written for
  every question type; `project.json` now has a project-level `ttsLang` (default `en-US`, legacy
  projects read as `en-US`). At playback the order is `question.ttsLang` → project `ttsLang` →
  `en-US`; the engine rate is `0.85`, matching the HTML `speak()` helper.
- Desktop speech uses a system command to synthesize a WAV and then reuses the existing
  SDL_mixer playback path: Linux `espeak-ng` (falls back to `espeak`), Windows PowerShell
  `System.Speech`, macOS `say`. The spoken text is written to a temp UTF-8 file, so no shell
  escaping of the text is needed; output goes to a local TTS cache. If no engine is found the UI
  shows a clear toast instead of failing silently.
- Learning → listen keeps audio-first behavior: a valid `audio` file plays (with trim) exactly as
  before; when it is empty/missing the same auto-play/button path synthesizes `tts` (or `prompt`)
  and logs `FORGE_TTS synth` / `FORGE_LEARN listenTts`; the page shows “本题缺少音频，将使用 TTS
  朗读：…”. When audio is restored/valid, `FORGE_LEARN listenPlay` and the audio file win again.
- The listen editor adds **朗读文本 (TTS)** and **朗读语言** inputs plus a **TTS 试听** button;
  saving a listen question now accepts TTS-only content (no audio) as long as one of audio/tts is
  present, and removes the old audio file when switching to TTS-only.
- Vocabulary → 示例发音 adds **TTS 朗读音标** (preview, no mutation) and **生成示例音频**: the
  text is `phonetics[0]` → `phonetic` → `text` → display name, language is the project `ttsLang`.
  Generation writes `resources/audio/pending_tts_<id>.wav`, sets `audio/audioStart/audioEnd/
  audioDuration (rate 0.85)`, plays through the normal path, and saving registers a `TTS：…` row in
  `audios.json`; reopening the editor shows the trimmed duration and existing playback/trim controls
  still work.
- Learning also has a generic question-audio control: any non-listen question with a non-empty `audio` gets a **播放题目音频** button and one auto-play per question (trim respected, logs `FORGE_LEARN questionAudio`). Listen keeps its existing audio-first/TTS-fallback behavior.
- Smoke covers TTS engine detection/synthesis, listen editor save/reopen JSON, TTS fallback in
  learning, audio priority, vocabulary preview/generation/save/reopen, and import of the migrated
  HTML project with `tts="et"` / `ttsLang="en-US"`; screenshots are written as `tts-*.png` and
  `vocab-tts-*.png` plus `tts-evidence.json`.

## Notes

Audio recording depends on an SDL capture device; on machines without one the UI reports a
toast and does not create a file. Playback also degrades to a toast when SDL_mixer cannot open
an audio device.

Question editor additionally supports ordered punctuation segments, audio audioStart /
audioEnd trim, and audio-management question reference counts with delete protection.

Learning mode: Home -> 学习 opens read-only course/question lists and a learning page with
underlined clickable word tokens, POS/meaning/phonetic hints and vocabulary audio.
hintsEnabled disables hints; old questions default to enabled.

Learning hint cards are floating ImGui windows positioned by the word rect (no modal/dim); clicking another word moves them and outside clicks close. Options require 确认答案; correct answers lock green and offer 下一题/完成.

Arrange questions use type="arrange" with prompt, blocks (id/text/vocabId) and an answers array of ordered block-id sequences. The desktop editor adds blocks through the picker and manages answer sequences from the block pool; learning accepts any configured answer order. Legacy ok/alt data remains readable, but alt is no longer edited.

Arrange learning matches tapped block text to vocabulary entries and plays their audio with trim; arrange editor hides the whole-sentence recording section.

Arrange blocks are added from the shared vocabulary picker, storing vocabId + display-name text; duplicates are skipped by vocabId and learning audio prefers vocabId.

Custom questions use type="custom" with customLabel, customBody and customMode ("choice"|"type"|"blocks"|"none"); the label is the display name, the body is a hint-enabled text card whose vocabulary words open the floating hint, and prompt/optionItems/acceptedAnswers/blocks/answers are reused. The editor adds a 自定义题型 type with label/body/prompt/mode fields and mode-specific editors (shared option editor, accepted answers, block rows with 正确 checkboxes and vocabulary picker, or nothing). Saving validates per mode, writes only custom keys and clears the other modes; learning renders label/body/prompt, then the shared choice flow, a text input matched against accepted answers, a block bank/selection checked against answers or correct-block order, or a 继续 button.

Vocabulary picked from the lexicon (options, prompts, sentence parts, blocks, match sides, card words, cloze candidates) renders image-type entries as handwritten images in editor previews and learning; a filled cloze blank draws the handwritten image before the mirror text, empty blanks stay ＿＿.

Cloze questions use type="cloze" with clozeText containing {} blanks, clozeOrder (candidate indices in blank order) and up to four candidate options. Candidates and the prompt can be composed from vocabulary: clozeItems stores per-candidate item groups and promptItems stores the prompt composition; the editor adds 题干选词/选词 buttons with the shared structured-item editor, and saving keeps structured items only when mirrors match. Learning renders prompt and candidate rows with handwritten-vocabulary images. The editor adds a 选词填空 type with body/prompt, live blank count and candidate rows with 正确词 checkboxes; saving validates 1–4 blanks, at least two candidates and checked count == blanks (candidates compacted before storing indices). Learning splits the body around blanks, fills the next blank when a candidate is clicked (click a filled blank to clear), and confirms against clozeOrder when full.

Reading questions use type="reading" with passage (multiline), answerMode ("choice"|"type"), prompt and either four options/correctIndex or acceptedAnswers/ignore flags. The editor has a 阅读短文 type with passage/mode combo and mode-dependent editors (shared option editor for choice, accepted answers plus ignore-case/space/near for type); saving validates the active mode and clears stale fields. Learning renders the passage with tappable vocabulary words (existing floating hint), then prompt and either the shared choice flow or a text input checked against accepted answers.

Dialog questions use type="dialog": prompt plus lines[{who,text}] and four options with correctIndex; optionItems works like choice. The editor adds a 选择对话 type with prompt, line rows (who/text, ＋台词, ×) and the shared option editor (选词/preview/correct radio); saving requires at least one line with text and four options. Learning renders alternating speaker bubbles, then prompt and the standard option/confirm/next flow.

Card questions use type="card" (new-knowledge intro card, not graded): card={word,pron,def,detail,wordVocabId,examples:[{l,z}]}. The editor has a 新知卡片 type with word/pron/def/detail/example fields, a 从词库生成卡片 button (prefills from a vocabulary entry, keeps wordVocabId for image entries and previews the handwriting) and per-example add/remove. Learning shows the image or text word, /pron/, def, multi-line detail, examples and a note; 继续/完成 advances without grading.

Match questions use type="match" with prompt and pairs[{l,r,lItems,rItems}]; the editor gives each pair side a 选词 button (preview with × removal, mirror text refreshed from items, image thumbnails), and saving keeps side items only when they contain vocab and the text matches the mirror. Desktop learning shows shuffled two columns with immediate pairing; vocab sides render as image-capable rows while text pairs keep the plain button, and 下一题/完成 appears after all pairs.

Type questions use type="type" with prompt/acceptedAnswers and ignoreCase/ignoreSpace/near matching options.

Type prompt supports vocabulary composition; warn rules (m/k/t) are checked before near-miss.

All option editors (listen, choice, dialog) support phrase composition: structured options can append multiple vocabulary items in order, insert punctuation from a 标点 menu, add inline-editable ＋文本 fragments and remove items with ×; the option text stays a mirror with punctuation-aware spacing (no space around punctuation). Handwritten vocabulary renders as a thumbnail in the editor and as the real image in learning.

Listen questions use type="listen" with prompt/audio/audioStart/audioEnd and four text options plus correctIndex. The editor requires an existing audio file and supports recording/trimming or a vocabulary example clip. Learning auto-plays once, labels 播放中…/重新播放, disables playback/options for missing audio, and logs listenPlay/stopAudio before using the standard option/confirm/next flow. Each option has a 选词 button that appends selected vocabulary entries as structured optionItems (per-option arrays of {kind:"text"} or {kind:"vocab",vocabId}), so handwritten/image vocabulary renders with its real image instead of a gloss. options[i] stays a plain-text mirror (image entries use their first code, then meaning); editing an option's text so it no longer matches the mirror collapses that option to a text item on save, and groups without vocab items are dropped so legacy text-only questions keep their original shape. The editor draws previews (image thumbnail, text, 〔图片缺失〕) with per-item removal, and learning renders vocab options as self-drawn rows with Forge selected/correct colours while pure-text options keep the original button.

Listen options support optionItems (text / vocab) with image previews and mirrored option text; pure-text options keep the original button path.

Choice questions support handwritten vocabulary: image segments render as inline image buttons in learning with the same clickable hint/audio behaviour as words (underline only for text), the editor shows thumbnails in the word list and mirrors image words with their first code, and options reuse optionItems with 选词 pickers, previews/×, mirror collapse and drop-if-text-only rules; pure-text options keep the plain button path.

Arrange blocks with image vocabulary render as image chips in the learning pool/selected row and show a thumbnail in the editor, with image blocks defaulting their text mirror to the first code. Type prompts support promptItems rendered inline as text/images with the same mirror rules; pure-text prompts are unchanged.

Questions store an optional explain field edited by the shared 「解析（答完后显示）」 multiline input in the question editor (all types). After a check — correct or wrong — learning shows a 解析 window with the explanation; it hides when moving to the next question. Old projects without explain show no panel.

Learning starts at the first question of a single course, advances in order, and shows a course-complete page at the end. Courses unlock linearly by stable course id (first always unlocked, completing one unlocks the next); progress.json in the project directory stores version/unlockedCourseIds/completedCourseIds with atomic temp+rename, learning resumes the first unlocked unfinished course from question 1, the course list marks locked/completed entries, and leaving mid-question shows a discard-progress confirmation.

Course progress is persisted per project in progress.json; courses unlock linearly and the learning entry resumes at the first unlocked, incomplete course.

Learning course list is grouped by chapter → unit → course; exiting mid-question returns to this grouped list after confirmation.
