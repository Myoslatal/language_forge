# Nostongue Forge — Android UI Kit

A native **Java** component library (design tokens + widgets) that translates the visual
language of `NostongueForge_v1.3.1.html` into Android views, plus a UI-only project manager
and a **component catalog** used for visual verification and usage examples.

Scope: **UI kit + project-manager metadata demo**. The project manager persists project
metadata to its app-specific external directory; there is no editor content, player or other
business logic. The component catalog remains the development harness for the kit.

## Modules

| Module | Type | Package | Purpose |
| --- | --- | --- | --- |
| `:uikit` | `com.android.library` | `io.github.myoslatal.languageforge.uikit` | Colors, dimens, styles, drawables and custom views |
| `:app` | `com.android.application` | `io.github.myoslatal.languageforge` | Project manager, structure editor, course editor, resource management, vocabulary list/editor/picker, font/image/audio management, POS management + component catalog |

* minSdk 29 (Android 10), compileSdk/targetSdk 36, Java 17, AGP 9.0.1, Gradle 9.1.0.
* Dependencies: AndroidX **appcompat 1.7.1** and **core 1.17.0** (the 1.8.0/1.19.1 pair
  requires compileSdk 37 + AGP 9.1, so the documented fallback versions are used).
* No Kotlin, no Compose, no Material Components.

## Supported OS

* Android 10 (API 29) through Android 16 (API 36); minSdk 29, compileSdk/targetSdk 36.
* The window is edge-to-edge on every supported version. Inset helpers merge
  `systemBars() | displayCutout()`, and the window uses
  `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`, so top bars and bottom navigation stay
  outside notches, punch-holes and gesture/3-button navigation areas.
* `Theme.NostongueForge` sets `android:forceDarkAllowed=false`; the deliberately light
  palette is not auto-darkened by Android 10's system force-dark. Theme selection stays
  opt-in (light by default, manual toggle, `--ez dark` for screenshots).
* Future project import/export/audio work must use scoped-storage APIs (SAF / MediaStore)
  because the minimum is Android 10.

## Build

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
export ANDROID_HOME=/home/forin/Android/Sdk
cd android
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

## Project manager (launcher)

`ProjectListActivity` is the launcher:

* five seeded projects (two pinned) on first run, light/dark theme, status-bar and
  bottom-safe-area insets;
* project cards with pin/overflow menus, stat pills and updated time;
* new project via `ForgeDialog`, pin/unpin reorders sections immediately, delete confirmation and
  an empty state when the last project is removed;
* overflow → **组件目录（开发）** opens `CatalogActivity`; **关于** opens an about dialog;
* tapping a project card opens a 学习/编辑 dialog; **学习** is a placeholder toast and
  **编辑** opens `ProjectStructureActivity`;
* the list is loaded from the on-disk project folders, so create/delete/pin survive a cold
  start; deleting the last project keeps an empty state (no reseeding);
* the bottom note and About dialog show the actual data directory.

## Parts of speech (POS)

`ResourceManagementActivity` → **词性管理** opens `PosManagementActivity`; the card shows
the real entry count and the page has a back button, title, project name, count badge and
**＋ 新建词性**. Rows show the name, code badge (or 未设置), description summary, row tap to edit
and ⋯ menu for edit/delete; deleting asks for confirmation. Empty projects show
“还没有词性”.

`PosEditorActivity` creates/edits the three fields **名称** (required, trimmed; e.g. 名词/动词),
**描述** (multiline) and **编码**; both description/code may be empty. The editor loads existing
values, validates the name and saves via `PosStore`; `pos.json` is project-level:

```json
{"schema":1,"entries":[{"id":"...","name":"名词",
  "description":"表示人、事物、地点等","code":"n",
  "createdAt":1794440000000,"updatedAt":1794440000000}]}
```

`pos.json` is written through a temp file and atomic move; missing/corrupt JSON loads as an
empty list. The whole project directory is already archived by `.conlang` export/import, so
`pos.json` is included and preserved automatically. No new permission is needed.

## Data storage

* Root: `Context.getExternalFilesDir(null)/projects`, normally
  `/sdcard/Android/data/io.github.myoslatal.languageforge/files/projects`
  (`/storage/emulated/0/...` is the same location). This is app-specific external storage;
  API 29+ needs **no** `READ/WRITE_EXTERNAL_STORAGE` or `MANAGE_EXTERNAL_STORAGE` permission.
* One folder per project: readable sanitized name + a short random suffix, e.g.
  `Nortu 自由语 · 入门-a1b2c3`. Each folder contains `project.json` (UTF-8):
  `schema` (1), `name`, `courseLine`, `lessons`, `questions`, `words`, `updated`,
  `createdAt` (epoch millis), `pinned`.
* On the first run (no `.seeded` marker) five sample folders are written and the hidden
  `.seeded` marker is created. Deleting all projects keeps the marker, so the next launch
  stays empty; clearing app data / reinstalling seeds again.
* Loading scans `projects/*/project.json`, skips missing/bad JSON with a log, and sorts pinned
  first, then `createdAt` descending.
* Delete recursively removes the project folder only after canonical-path validation against
  the projects root. Pin updates are written back to `project.json`.
* `structure.json` stores the chapter/unit/course tree and question names/order (see
  below). Actual question content/media are **not** persisted yet.

## Project structure

Tapping a project card → **编辑** opens `ProjectStructureActivity`, a three-level
chapter → unit → course tree:

* `structure.json` (UTF-8, `schema: 1`) lives next to `project.json`:
  `{"schema":1,"chapters":[{"id":"...","name":"...","units":[{"id":"...","name":"...",
  "courses":[{"id":"...","name":"..."}]}]}]}`.
  IDs are UUIDs; array order is display order; names are trimmed and capped at 60 characters.
* Missing/corrupt structure files load as an empty tree and are logged. Writes go to
  `structure.json.tmp` and are moved into place atomically; failures show a toast and the
  UI reloads from disk.
* Each chapter row shows its unit count, each unit row its course count; rows expand/collapse
  independently and the expanded state survives re-renders. Empty states are shown for a
  project with no chapters, a chapter with no units and a unit with no courses.
* Chapter menus: new unit, move up/down, delete. Unit menus: new course, move up/down,
  delete. Course menus: edit (placeholder), move up/down, delete. Boundary moves toast
  “已经在最前/最后”; delete confirmations include cascade counts.
* Creating a unit/course expands its parent(s). Learning and the course editor are
  placeholders (toast only); renaming and cross-parent moves are not implemented.
* Because `.conlang` exports/imports the whole project directory, `structure.json` is
  included automatically and preserved on import.

## Course editor / questions

Course rows (and the course ⋯ → **编辑课程** menu) open `CourseEditorActivity` with
`folderName`, `chapterId`, `unitId` and `courseId`; the activity validates the project
directory and all three IDs before showing anything.

* Each course in `structure.json` has a `questions` array:
  `{"id":"...","name":"..."}`. IDs are UUIDs, names are trimmed and capped at 60
  characters; a missing `questions` field (older files) loads as an empty list.
  New/updated files include `"questions":[]` even when empty.
* The course editor top bar shows the course name, **章节名 · 单元名**, and an
  `N 道题目` badge. The body lists numbered questions (`01`, `02`, …), each with a
  question label and a ⋯ menu: move up/down, divider, delete (red). Boundary moves toast
  “已经在最前/最后”; row taps toast “题目编辑后续实现”.
* **＋ 新建题目** uses a `ForgeDialog` + `ForgeTextField`; an empty name becomes
  “未命名题目”. Delete asks for confirmation. Every create/move/delete writes
  `structure.json` via the same temp-file + atomic move path and reloads from disk on
  failure.
* The empty state says “这门课程还没有题目” and offers a new-question button.
* Question names/order are part of the project directory, so `.conlang` export/import
  includes and preserves them automatically.

## Resource management

The course editor top bar has a **资源管理** button next to the question count. It opens
`ResourceManagementActivity` with the chapter/unit/course names as Intent extras; missing
context names are omitted from the subtitle.

* The page uses the same Forge top bar and edge-to-edge insets. It shows a **资源** heading,
  a short explanation, a **词汇管理** card with the real count and “管理词汇表”, and a
  **字体管理** card with the real font count / active font. Tapping opens
  `VocabularyListActivity` / `FontManagementActivity`.
* A faint “更多资源类型稍后加入” note is shown below the cards.
* No storage permissions or media files are implemented. The activity is registered with
  `exported=false`; back (button or system back) returns to the course editor.

## Vocabulary

Vocabulary is **project-level**, not course-level. `ResourceManagementActivity` passes the
project folder to `VocabularyListActivity`; the card count is reloaded from disk on resume.

* `vocabulary.json` (UTF-8, `schema: 1`) lives next to `project.json`:
  `{"schema":1,"entries":[{"id":"uuid","type":"text|unicode|combo|image",
  "meanings":["..."],"posMeanings":[{"posId":"uuid","meanings":["..."]}],
  "codes":["..."],"text":"...","codepoints":["12ab","34cd"],
  "parts":["otherVocabId"],"image":"","imageWidth":0,"imageHeight":0,"audio":"",
  "audioStart":0,"audioEnd":0,"audioDuration":0,"createdAt":0,"updatedAt":0}]}`.
  IDs are UUIDs; strings are trimmed/capped; missing/bad files load empty and are logged.
  Writes use `vocabulary.json.tmp` + atomic move.
* **List**: back, title, project subtitle, count badge, new button; rows show display name
  (text/Unicode/combo preview/meaning), type badge, POS chips, code summary and grouped
  “名词：…；动词：…” meaning summary. Row tap edits; ⋯ deletes with confirmation; empty state
  has a new button.
* **Editor**: back/title/save; POS multi-select (name + code) before meanings. With no POS,
  the generic repeatable meanings remain (old vocabulary compatible); selecting a POS shows a
  group per POS in `pos.json` order with its own meanings and remove/add controls. The first
  selected POS migrates non-empty generic meanings; each selected group requires at least one
  non-empty meaning or save is blocked with a toast. Codes and the type selector follow;
  **文本词语 / Unicode 编码词语 / 自定义文字组合词 / 自定义文字**; text input, multiple
  codepoint inputs with live preview, or combo parts chosen through the picker. Unicode
  codepoints accept `12ab`/`0x12AB` and normalize to lowercase without prefix.
  Custom text supports SAF image import + crop and touch drawing; all types support an
  optional example-pronunciation audio section. See below. Saving validates type-specific
  content and writes the project file; failures keep the editor content.
* **Picker**: reusable multi-select `VocabularyPickerActivity` with search. Search matches
  all `codes` (case/space-insensitive), text `text`, Unicode `codepoints`
  (`12ab`/`0x12AB`), meanings, POS names/codes and display names. Selection order is preserved and
  returned to the combo editor; the current entry is excluded.
* **posMeanings compatibility**: `meanings` remains the aggregated/deduplicated list used by
  search and old readers; saving with selected POS writes `meanings` = all non-empty group
  meanings in POS order. Old entries without `posMeanings` load as generic meanings. Groups
  referencing a deleted POS are ignored by the editor; their text stays in `meanings` and
  saving drops the unknown group.
* `.conlang` export/import includes `vocabulary.json` and `pos.json` automatically because the
  whole project directory is archived.

## Custom-text images (Phase 2A)

`type=image` entries store their PNG under the project directory:

* `resources/images/vocab_<entryId>_<timestamp>.png`; `image` is the relative path,
  `imageWidth`/`imageHeight` are the PNG pixel size.
* **Import**: `ACTION_OPEN_DOCUMENT` (`image/*`) → decode/downsample to at most
  2048 px (EXIF orientation applied) → `ImageCropActivity` → cropped cache PNG. The crop
  screen shows the bitmap with a mask and four draggable corner handles (minimum 48 dp).
* **Draw**: a ForgeDialog asks for width/height (default 512, valid 32–4096) before
  opening `DrawingActivity`; its transparent bitmap is exactly that size. Tools: black
  brush, eraser, clear, undo and redo. Each stroke stores its bitmap-space points plus
  brush/eraser and width; undo/redo replay the ordered strokes from a transparent bitmap
  (eraser uses CLEAR), so eraser history and single-point strokes are preserved. A new
  stroke clears redo; clear resets all history; save returns the current replayed PNG +
  size.
* The editor previews the image fit-to-width, shows its size, and offers
  re-import/redraw/remove. Pending media stays in the app cache until an explicit save;
  cancel/back deletes it. A successful save copies the pending PNG into
  `resources/images`, then best-effort deletes the replaced/removed old PNG. Saving an
  image entry without a valid file and width/height > 0 is rejected.
* `.conlang` export/import archives the whole project directory, so
  `resources/images/` is included automatically. No storage permission is used.

## Example pronunciation audio (Phase 2B)

Every vocabulary type has an optional **示例发音** section; audio is project-level:

* `resources/audio/vocab_<entryId>_<timestamp>.<ext>`; `audio` is the relative path,
  `audioStart`/`audioEnd`/`audioDuration` are milliseconds.
* **Import**: `ACTION_OPEN_DOCUMENT` (`audio/*`) → copy to cache → read duration with
  `MediaMetadataRetriever` → default range 0–duration. Common m4a/mp3/wav/ogg files are
  supported by the platform decoder.
* **Record**: the manifest declares only `RECORD_AUDIO`; it is requested at runtime
  (`ActivityResultContracts.RequestPermission`). Denial only toasts and never opens the
  recorder. `MediaRecorder` writes AAC in an `.m4a` cache file; the UI shows a live timer
  and a stop button, then reads the duration and exposes the trim range.
* **Play/trim**: inline SeekBars set start/end, show time text and a **试听裁剪** preview.
  Both normal playback and preview start at `audioStart` and stop at `audioEnd`; the
  range must satisfy `0 <= start < end <= duration` and be at least 100 ms.
* Pending audio stays in the app cache until an explicit save. Save copies it to
  `resources/audio`; failure deletes the new project file and keeps the pending file.
  Cancel/back deletes pending audio. Replacing/removing audio deletes the old project file
  after a successful save, and deleting a vocabulary entry best-effort deletes its audio
  and image files.
* `.conlang` export/import archives the whole project directory, so
  `resources/audio/` is included automatically. No storage permission is used.

## Font management

The resource page has a project-level **字体管理** entry; it shows the number of imported
fonts and the active font name.

* `fonts.json` (UTF-8, `schema: 1`):
  `{"schema":1,"active":"fontId or ''","fonts":[{"id":"uuid","name":"...",
  "file":"resources/fonts/xxx.ttf","createdAt":0}]}`. Writes use
  `fonts.json.tmp` + atomic move; missing/bad files load empty.
* Import uses `ACTION_OPEN_DOCUMENT` and accepts TTF/OTF/TTC; WOFF/WOFF2 is rejected with
  a toast. The file is copied to `resources/fonts`, its header is checked
  (TrueType/OpenType/TTC magic) and `Typeface.createFromFile` must load it; invalid files
  are deleted with no `fonts.json` entry. Duplicate/failed imports leave no residue.
* The list always starts with **系统默认** (`active=""`). Only one font can be active.
  Deleting the active font falls back to the system default; delete is confirmed first.
* **Only vocabulary content** uses the active typeface: vocabulary list display names,
  picker row titles, and the editor's Unicode/combo previews (combo chips too). UI labels,
  buttons, meanings, codes and other controls stay on the default UI font. Missing or
  invalid font files return `null` and fall back safely to the system typeface.
* `.conlang` export/import includes `fonts.json` and `resources/fonts/` automatically;
  no storage permission is used.

## Image management

The resource page also exposes project-level **图片管理** with the real image count.

* `images.json` (UTF-8, `schema: 1`):
  `{"schema":1,"images":[{"id":"uuid","name":"...","file":"resources/images/xxx.png",
  "code":"img1","width":0,"height":0,"createdAt":0}]}`. Writes use
  `images.json.tmp` + atomic move; missing/bad files load empty.
* Import uses `ACTION_OPEN_DOCUMENT` (`image/*`), accepts PNG/JPG/JPEG/WebP/GIF, copies to
  a unique `resources/images/img_<uuid>_<timestamp>.<ext>`, validates decode and reads
  width/height; invalid files are deleted with no registry entry.
* The list shows a downsampled thumbnail, name/file/dimensions, code (or “未设置编码”) and
  reference info. Search matches code/name/file. Row/menu opens a dialog with a preview and
  an optional code field; saving writes `images.json`.
* References are scanned from `vocabulary.json` entries with `type=image`; files under
  `resources/images/` that are not in `images.json` are also listed as
  “未登记/来自词汇” and can be given a code (which registers them). Referenced images
  cannot be deleted (toast shows the count/names; remove the vocabulary reference first);
  unreferenced images delete both the file and registry entry.
* `.conlang` export/import includes `images.json` and `resources/images/`; no storage
  permission is used.

## Audio management

The resource page exposes project-level **音频管理** with the real audio count.

* `audios.json` (UTF-8, `schema: 1`):
  `{"schema":1,"audios":[{"id":"uuid","name":"...","file":"resources/audio/xxx.m4a",
  "code":"aud1","duration":1234,"createdAt":0}]}`. Writes use
  `audios.json.tmp` + atomic move; missing/bad files load empty; duration is milliseconds.
* Import uses `ACTION_OPEN_DOCUMENT` (`audio/*`), accepts
  M4A/MP3/WAV/OGG/AAC/FLAC, copies to a unique
  `resources/audio/aud_<uuid>_<timestamp>.<ext>`, validates with
  `MediaMetadataRetriever` and reads duration; invalid files are deleted with no registry
  entry.
* The list shows an audio icon, name/file/duration/size, code (or “未设置编码”) and
  reference info; search matches code/name/file. Row/menu opens a code dialog (filename,
  duration, optional code); saving writes `audios.json`.
* References are scanned from every `vocabulary.json` entry with a non-empty `audio`
  path; `resources/audio/` files not in `audios.json` are also listed as
  “未登记/来自词汇” and can be given a code (which registers them). Referenced audio
  cannot be deleted (toast shows count/names; remove the vocabulary reference first);
  unreferenced audio deletes both file and registry entry.
* `.conlang` export/import includes `audios.json` and `resources/audio/`; no storage
  permission is used and no new permission was added for management.

## Import / export (.conlang)

Projects can be exported and imported as a single `.conlang` file (a ZIP):

* **Export**: project card overflow → **导出项目…** → enter a filename (the suffix is added
  automatically) → pick a directory with the system `ACTION_OPEN_DOCUMENT_TREE` picker.
  The project folder is streamed into a ZIP at the chosen tree via
  `DocumentsContract.createDocument(..., "application/octet-stream", name.conlang)`.
* **Import**: the project header's **导入** button (or overflow → **导入项目…**) opens
  `ACTION_OPEN_DOCUMENT` with ZIP/octet-stream MIME types. The file must end in
  `.conlang`; the ZIP must contain `project.json` at its root, or inside exactly one
  top-level folder. Schema must be 1 and `name` non-empty.
* The archive can contain future subdirectories such as `assets/` or `audio/`; all files
  are preserved. Import extracts into a temporary folder under `projects/`, validates the
  archive, then copies into a new unique project folder. Bad ZIPs, missing JSON, Zip Slip
  paths, more than 5000 entries or more than 200 MB uncompressed are rejected and cleaned up.
* Both directions use SAF only. No `READ/WRITE_EXTERNAL_STORAGE` or
  `MANAGE_EXTERNAL_STORAGE` permission is requested. Import/export work runs off the UI
  thread; the picker cancel path has no side effects.

## Component catalog

`CatalogActivity` is non-launcher and opens from the project manager overflow. It shows every
widget in a single mobile-first scroll column:

1. Buttons (default / primary / ghost / on / mini / miniPrimary / danger / disabled / icon)
2. Tabs (panel with container divider, inline without)
3. Inputs (single-line, multiline, select, checkbox row, switch row, slider, search)
4. Badges, chips (closable), keyboard keys, tags
5. Cards, sections, normal/warn/success hints
6. Overlays (ForgeToast / ForgeDialog / ForgeMenu)
7. Lists (unit rows, question rows with warn dot, dict rows)
8. Type samples (sans / serif / mono)
9. Status bar demo + fixed bottom navigation

Theme can be switched from the top-bar moon button; the choice is stored in
`SharedPreferences` (`forge_theme` / `dark`). For deterministic screenshots the launch
intent can force a mode without persisting it:

```bash
adb shell am start -n io.github.myoslatal.languageforge/.ProjectListActivity --ez dark true
adb shell am start -n io.github.myoslatal.languageforge/.ProjectListActivity --ez dark false
```

The project manager and catalog are edge-to-edge aware: `ForgeActivity` applies status-bar
top padding and navigation-bar bottom padding, and flips system-bar icon brightness with the
theme. The catalog's fixed bottom navigation is handled the same way.

## Overlays

```java
// Toast: text-coloured pill, panel text, 20dp radius, fades/up into place.
ForgeToast.show(activity, "已保存");
ForgeToast.show(activity, "自定义距底", 64); // base dp + navigation-bar inset

// Dialog: 42% scrim, 9dp panel, panel2 header/footer, scrollable body,
// width min(screen - 20dp, 680dp), body capped so the dialog stays under 88vh.
ForgeDialog dialog = new ForgeDialog(activity);
dialog.setTitle("导出播放器")
      .setBodyView(bodyView)
      .setNegativeButton("取消", null)
      .setPositiveButton("导出", (d, which) -> ForgeToast.show(activity, "已导出"))
      .show();

// Menu: anchored PopupWindow, 206-280dp, 8dp radius, 7dp x 9dp items.
ForgeMenu menu = new ForgeMenu(activity);
menu.addHeader("文件")
    .addItem("打开项目", R.drawable.forge_ic_folder, "Ctrl+O", this::open)
    .addItem("保存项目", R.drawable.forge_ic_save, "Ctrl+S", this::save)
    .addDivider()
    .addCheckableItem("播放反馈动画", true, this::toggleAnim);
menu.show(anchorButton);
```

The gallery uses `ForgeToast` everywhere (no system `Toast`) and demonstrates all three
overlays from the "弹层 · OVERLAYS" section.

## Design tokens

Tokens live in `uikit/src/main/res/values/colors.xml` + `values-night/colors.xml`,
`dimens.xml` and `styles.xml`; they mirror the HTML `:root` / `[data-theme]` blocks:

* light: bg `#E9EBEF`, panel `#FFFFFF`, panel2 `#F6F7F9`, line `#DFE3E9`,
  text `#171B21`, accent `#2F6FEB`, accentSoft `#1C2F6FEB`, ok/warn/bad pairs
* dark: bg `#0F1115`, panel `#171A1F`, panel2 `#1D2127`, line `#2A2F37`,
  text `#E9EBEF`, accent `#4B8BFF`, accentSoft `#294B8BFF`
* radii 5dp / 9dp dialog / 8dp menu / 12dp chip / 20dp toast
* sans-serif UI, serif language/title, monospace pronunciation/code
* 12.5sp body, 12sp controls, 11.5sp tabs, 10.5sp hints/status, 10sp card title,
  9.5sp section/field/badge, 16sp mobile inputs

## Components

`ForgeButton`, `ForgeTabs`, `ForgeCard`, `ForgeSection`, `ForgeTextField`,
`ForgeSelect`, `ForgeSwitchRow`, `ForgeCheckRow`, `ForgeChip`, `ForgeBadge`,
`ForgeStatusBar`, `ForgeBottomNav`, `ForgeToast`, `ForgeDialog`, `ForgeMenu`,
`ForgeActivity`, `ForgeTheme`.

`ForgeButton` extends `AppCompatButton`, so TalkBack reports it as a button. Pure icon
buttons carry `contentDescription`; tab and bottom-navigation items expose "已选中" via
their accessibility description and selected state.

Compound controls redirect XML children into their internal body/content
(`ForgeCard` → `getBody()`, `ForgeSection` → `getContent()`) and own their child view
state, so several instances in one layout do not clash during Activity recreation.

## Known deviations / next steps

* The catalog's top bar is a two-row responsive compromise at phone width
  (brand/project/history/theme, then catalog label + open/save/export). This is demo
  chrome, not a product spec.
* The status bar is demonstrated inside the catalog scroll content.
* Dialog body scrolling is implemented with a capped ScrollView; the catalog demo body
  is short, so no scrollbar is visible until content exceeds the cap.
* Learning/player and question content editing are placeholder toasts; the project
  structure, question list, vocabulary CRUD, custom-text images, pronunciation audio,
  font management, image management and audio management are implemented. The catalog
  uses static sample content only.
* Theme preference defaults to light and does not follow the system on first run.
* Project metadata, structure, question names/order, vocabulary metadata, custom-text
  images, pronunciation audio and project fonts are disk-backed; question content/media is
  not persisted yet, and learning/question editing are placeholders.