# Nostongue Forge Desktop (SDL2 + Dear ImGui)

Phase 1 native desktop build of Nostongue Forge. C++17, SDL2, Dear ImGui (SDL_Renderer2 backend),
and vendored miniz/nlohmann/json. No Electron, no web runtime, and no network access.

## Features (Phase 1)

- Project library: create/import, pinned ordering, project cards with stats, updated time,
  card menu (pin/export/delete), learning placeholder, edit structure.
- Project structure: chapters → units → courses tree, expand/collapse, create/rename/move/delete
  with confirmation; every change writes `structure.json`.
- Course editor: question list with 01/02 numbering, create, move up/down, delete, and a
  resource-management placeholder page.
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
`schema, name, courseLine, lessons, questions, words, updated, createdAt, pinned`.
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

## Notes

Audio recording depends on an SDL capture device; on machines without one the UI reports a
toast and does not create a file. Playback also degrades to a toast when SDL_mixer cannot open
an audio device.
