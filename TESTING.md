# Full regression report — Android + SDL2 desktop

Date: 2026-11-12 (Asia/Shanghai)
Repo: /ai/language_forge
Android: API36 AVD EnvCheck_API36 + API29 AVD EnvCheck_API29, debug APK
Desktop: SDL2/ImGui under Xvfb :99 1280x860, SDL_VIDEODRIVER=x11
Tested source: 18c9b2f feat: add question punctuation and trimmed sentence audio references + desktop UI fixes included in the report commit
HTML: NostongueForge_v1.3.1.html unchanged (git diff exit 0)

## Summary

### Token text editor + vocabulary code validation (2026-11-28)

All editor fields that used to compose text from multiple vocabulary chips now use one text field
with #<code> tokens and a live preview: choice sentence segments, choice/dialog/reading/custom
options, listen options, type prompt, cloze prompt/candidates and match left/right items. Plain
text stays plain; tokens are blue in the real input (Android ForegroundColorSpan; desktop InputText
overlay) and a picker inserts a token at the current cursor. #<code>, # <code> and #< code > are
accepted; unknown or unclosed tokens show an error and block save. The stored schema is unchanged:
parsed text becomes text items and parsed tokens become vocab items in order. Whitespace-only text
items are preserved (fixed 2026-11-28), so consecutive spaces in token fields and choice sentences
survive save/reload; only an entirely empty field is treated as empty.

Vocabulary codes now reject #, < and > in the Android/desktop vocabulary editors and in both
lexicon import paths. Android and desktop project .conlang imports warn when the imported
vocabulary contains such codes; existing data still opens and can be fixed in the editor. Unicode
and combination vocabulary codes remain valid.

Evidence (true UI, Android API36 + desktop Xvfb):
- Desktop sentence token input/preview: /tmp/t85-d-sentence.png; token + preview in option:
  /tmp/t85-token-option.png; token picker insertion: /tmp/t85-d-picked.png.
- Desktop unknown-token preview/save block: /tmp/t85-d-unknown.png, /tmp/t85-d-save-block.png.
- Android sentence token input/preview: /tmp/t85-a-sentence2.png; picker insertion at cursor:
  /tmp/t85-a-picked2.png; unknown-token error/save block: /tmp/t85-a-unknown2.png,
  /tmp/t85-a-saveblock.png.
- Android choice learning regression (token-backed option selected/correct):
  /tmp/t85-a-learn-select.png, /tmp/t85-a-learn-correct2.png.
- Final gates: desktop clean Release build from a fresh build directory:
  /tmp/task85-dclean-final; smoke run 1: /tmp/task85-final-smoke1b.log (SMOKE PASS,
  question=true/learning=true); smoke run 2: /tmp/task85-final-smoke2b.log (SMOKE PASS).
  Android clean debug build: /tmp/task85-aclean2.log; install/launch no crash:
  /tmp/t85-final-launch.png. HTML SHA-256 remains
  7c1f30f8b37f7b4b91dd31ae1f63032f10e10b9ccdb516b6045a4228e39643dd.
- Desktop invalid vocabulary code # blocked with an explicit toast and JSON unchanged:
  /tmp/t85-d-badcode3.png. Android has the equivalent check in VocabularyEditorActivity
  (compiled and clean-built); both lexicon importers skip invalid codes and both project
  import flows report invalid-code counts.

Known boundaries:
- In the choice-sentence field, whitespace-only text segments are treated as separators and
  dropped when the sentence is rebuilt, so JSON stores "shared other" rather than a
  duplicate-space punctuation segment. Option/type/cloze text preserves user spaces exactly.
- A token always uses the first code of the chosen vocabulary entry. Duplicate codes are
  ambiguous and the preview reports an error rather than guessing.
- Desktop token input is backed by a 4 KB buffer per field; extremely long prose can exceed it.
  Android has no such small fixed limit.
- HTML is unchanged; all course fixtures remain under /tmp or app external storage.

### Card sentence persistence + image token previews (2026-11-28, task-86)

- Card persistence: Android saveCard now writes the common sentence token state back to
  segments/parts/sentence; the desktop card editor exposes the same sentence token field and
  the card save branch synchronizes it before persistence. Card learning remains unchanged
  (the card page still intentionally does not display the sentence).
- Image vocabulary token previews are image-aware on both platforms: Android creates an
  ImageSpan bitmap thumbnail from MediaFiles.loadProjectImage; desktop drawTokenField draws
  the texture inline. Missing files show "〔手写文字图片缺失〕". Text/Unicode/combination
  vocabulary still render their display text; meanings are never substituted for an image.

Verified (Android API36, desktop Xvfb):
- Android card edit with "hello #<imgword>" previews text + image, saves, reopens and survives
  force-stop/start:
  /tmp/t86-a-card-edited.png, /tmp/t86-a-card-reopen.png, /tmp/t86-a-card-restart.png.
- Card JSON after save:
  segments=[{kind:punct,text:"hello "},{kind:word,vocabId:<image entry>}],
  parts=[<image entry>], sentence="hello imgword".
- Android choice sentence + option previews display the image thumbnail:
  /tmp/t86-a-choice-preview.png. Android learning displays the same image in the sentence and
  option, not the meaning: /tmp/t86-a-choice-learn.png.
- Android missing image placeholder: /tmp/t86-a-missing.png.
- Desktop seeded card sentence/field and image preview: /tmp/t86-d-card-seeded.png; card save
  persisted segments/parts/sentence in structure.json.
- Desktop choice sentence + option image previews: /tmp/t86-d-choice-image.png; missing image
  placeholder: /tmp/t86-d-missing.png.
- Desktop clean Release build: /tmp/task86-dclean; smoke x2 PASS:
  /tmp/task86-smoke1.log and /tmp/task86-smoke2.log (question=true, learning=true).
- Android clean debug build: /tmp/task86-aclean.log; install/launch /tmp/t86-a-final-launch.png
  (no FATAL). HTML unchanged; no course data added; no commit/push.

Known boundary: desktop card sentence typing was seeded/persisted in this test (the field itself
was rendered and picker-capable); Android covered the full typed edit/save/restart path. The
underlying save path is shared for both platforms and smoke still passes.

PASS 26 / FAIL 0 / N/A 1 (matrix rows 27).

### TTS follow-up (2026-11-24)

The system-TTS work (HTML-compatible `tts`/`ttsLang`, listen audio-first/TTS-fallback, vocabulary
phonetic preview and generated example audio) is reported separately in
[TTS_TESTING.md](TTS_TESTING.md). Desktop clean build + smoke ×2 PASS with TTS flags; Android
clean debug/release build and API36 engine/synthesis/fallback checks PASS. Real audible output
remains a physical-device item (RD-07).

### No bundled courses / external course files (2026-11-25)

A fresh install starts with an empty project list (Android seed samples removed; desktop never
seeded). Migrated courses are intentionally external: `nortu-l1-2.conlang` and
`Nortu-Intro-html.conlang` live under `/ai/nortu/` and are never committed
or bundled into the APK. Details and screenshots: [NO_BUNDLED_COURSES.md](NO_BUNDLED_COURSES.md).

### Resource scopes: project vocabulary vs course question audio (2026-11-25)

Project resources (vocabulary, POS, fonts, vocabulary audio/images) live at the project
root and are shared by all courses. Question audio lives under
`resources/courses/<courseId>/audio` with a per-course `audios.json`; the course resource
page only shows that course. Every question can carry a course question image (`Question.image`), and choice-like options
can carry course option images (`optionItems[i][0].image`); both use the course picker/import
and delete protection. Question images render for all types, including cloze and card. Legacy projects and `.conlang` imports are migrated by reference. See
[RESOURCE_SCOPE_TESTING.md](RESOURCE_SCOPE_TESTING.md).

### Learning accuracy stats (2026-11-25)

Each entry from question 1 is one run. Only the first answer check per answerable question can be
first-correct; a wrong-then-retry question counts as answered but not first-correct. Card questions and
custom questions with `customMode=none` are excluded from the denominator; all other types/modes count.
Accuracy is rounded first-correct/answerable total, and a course with no answerable questions writes
nothing. Only completing the whole course records a best; exiting mid-run does not write. The best is
strictly higher-only and is stored per stable course id in a separate versioned `learning-stats.json`
(atomic temp+rename), not in `progress.json`. Both course lists show 历史最佳 x% · c/t or 暂无成绩;
completion shows current run, best and 刷新最佳, and question pages show 首次答对/已答. The file is
local learning history: `.conlang` export excludes it and import never installs it. Android fixture used choice/type/card/custom-none, the main desktop fixture used
choice/reading/custom/cloze/card and a second desktop fixture used choice/type/card. First wrong then
retry gave 4/5=80% with card/custom-none excluded; the type fixture gave 2/2=100% with its card
excluded; a 3/5=60% rerun left best 80%; a 5/5=100% rerun refreshed best; restart kept it; B stayed
independent; an abandoned run left the file unchanged. Android screenshots: `t81-acomplete.png`,
`t81-alower-complete.png`, `t81-abetter100.png`, `t81-afinal-list2.png`, `t81-arestart-list.png`;
desktop: `t81-complete.png`, `t81-r3-complete2.png`, `t81-best100b.png`, `t81-restart-best.png`, `t81-dtype-fixed-complete.png`; Android archive `Stats Fixture.conlang` had no `learning-stats.json` entry; importing an archive with a forged local stats file produced a project without `learning-stats.json`.
A zero-answerable card-only course completed with 本课没有可作答题目，不计入正确率 and did not create
the stats file (`t81-empty-complete.png`). Smoke also asserts archive exclusion
(`learning_stats_export=excluded import=excluded`).

### Learning review / self-test mode chooser (2026-11-26)

Clicking a completed course now opens a 复习还是自测？ dialog on both clients. 复习 runs the existing
learning flow unchanged (underlines, tappable vocabulary hints, explanations, card/custom-none pages,
review best updates). 自测 suppresses underlines, vocabulary hints and the explanation panel; card and
`custom` `none` questions are skipped, progress uses only answerable questions, and the completion
page shows 自测完成 + current accuracy while leaving `learning-stats.json` and progress.json untouched.
If a completed course contains only skipped pages, 自测 shows 本课没有可自测的题目 and stays on the list.
Unfinished courses still start normal learning directly. Android uses an AlertDialog; desktop uses an
ImGui modal; both use the same buttons and explain the restrictions.

Fixture: 3 courses / 6 questions. Completed A has a choice with sentence hints/image/audio/option
images, a card, a custom-none page and another choice; completed B has only a card; unfinished C has one
choice. A's initial best was 50% (1/2); after self-test the JSON still held bestAccuracy 50/updatedAt 1 and progress.json still held completed A+B, unlocked A+B+C. Screenshots: Android `/tmp/t83-a-modal-final.png`,
`/tmp/t83-a-selftest-q1.png`, `/tmp/t83-a-selftest-q2.png`, `/tmp/t83-a-selftest-complete.png`,
`/tmp/t83-a-review-q1.png`, `/tmp/t83-a-review-hint.png`, `/tmp/t83-a-review-explain.png`,
`/tmp/t83-a-review-card.png`, `/tmp/t83-a-uncompleted-direct.png`, `/tmp/t83-a-card-only-warning.png`;
desktop `/tmp/t83-d-modal-final.png`, `/tmp/t83-d-selftest-q1.png`, `/tmp/t83-d-selftest-q2.png`,
`/tmp/t83-d-selftest-complete.png`, `/tmp/t83-d-review-q1.png`, `/tmp/t83-d-review-hint.png`,
`/tmp/t83-d-review-correct.png`, `/tmp/t83-d-review-card.png`, `/tmp/t83-d-uncompleted-direct.png`,
`/tmp/t83-d-card-only-warning.png`. Android clean debug build and desktop clean Release build succeeded;
desktop clean smoke ×2 reported SMOKE PASS (learning_stats_export=excluded).

### Per-course vocabulary hint overrides (2026-11-27)

Each course can override the body text shown after clicking an underlined word in 复习; the title
remains the global word form, while phonetics/POS/meanings/audio and image ownership stay global.
A missing or cleared override falls back to the existing global hint. 自测 still shows no hint at all.
The store is per course at `resources/courses/<safeCourseId>/vocab-hints.json`
(`{"version":1,"hints":{"<vocabId>":"multiline body"}}`), loaded lazily and saved atomically
(temp + rename). Course resources expose an entry with a live count; the manager supports search/add
via the vocabulary picker, multiline editing, save/clear, and delete confirmation. Android mirrors
the same store and UI natively; desktop uses `CourseVocabHints` in `model` plus a course-resources
card. Deleting a course removes the whole course resource directory, including this file; `.conlang`
export/import includes it because it is an ordinary project file (only `learning-stats.json` is excluded).

Fixture: completed A and unfinished C share `shared`; A has overrides for `shared`/`other`, C has
none. Android screenshots: `/tmp/t84-a-resources.png` (entry/count), `/tmp/t84-a-hints2.png` (list),
`/tmp/t84-a-a-hint.png` (A override), `/tmp/t84-a-c-hint3.png` (C global fallback),
`/tmp/t84-a-picker-search.png` (search), `/tmp/t84-a-editor-typed.png` (multiline edit),
`/tmp/t84-a-cleared.png` (clear). Desktop: `/tmp/t84-d-resources.png`, `/tmp/t84-d-hints-list.png`,
`/tmp/t84-d-editor-typed5.png`, `/tmp/t84-d-saved3.png` (saved body), `/tmp/t84-d-a-hint2.png`,
`/tmp/t84-d-c-hint.png`, `/tmp/t84-d-selftest-no-hint.png`. Global `vocabulary.json` SHA-256 stayed
`1d7994fe14db94a677f112ffdb13cd100ae8ef9c918f47628945eea410075245` on both fixtures; course A and C
stayed independent. Archive round-trip via the store: `unzip -l` showed
`resources/courses/course-a/vocab-hints.json`, no `learning-stats.json`; importing into a clean data
dir preserved A's override, left B without a file, and kept the same vocabulary hash. Deleting course A
through the Android course menu removed `resources/courses/course-a/` and the hint file. Android clean
debug build and desktop clean Release build succeeded; smoke ×2 reported SMOKE PASS
(lexicon fixture regenerated from the 5314-word Nortu project).

### Learning course-list visual refresh (2026-11-25)

The learning course selector now follows the share-site start page (`/tmp/nortu-site.html`,
`.pl-start/.pl-unit/.pl-bestline/.pl-best`: light surface, 760px content, 16px rounded cards,
blue primary actions, green best-score pills). Android uses native rounded cards with elevation,
state chips, best pills and full-width `开始这一关/再练一次` buttons; desktop uses ImGui-drawn
rounded cards, borders/shadows, pills and buttons instead of the old selectable rows. The full
chapter → unit → course hierarchy is retained, including chapter headers, unit titles/subtitles
and locked rows that remain visible with a disabled button. Unlock order, completion progress,
learning-stats, start/retry behavior and question pages are unchanged. A 2-chapter/3-unit/4-course
fixture covered completed courses with 80%/100% bests, an unlocked course with no stats, and a
locked course with an old 50% record; both themes were checked. Screenshots: Android
`/tmp/t82-a-list-light-final.png`, `/tmp/t82-a-list-light-final-lower.png`,
`/tmp/t82-a-list-dark.png`, `/tmp/t82-a-list-small-dark2.png`; desktop
`/tmp/t82-d-list-light-final.png`, `/tmp/t82-d-list-light-final-lower.png`,
`/tmp/t82-d-list-dark2.png`, `/tmp/t82-d-small-window-top.png`. Starting from a scrolled list
resets the question view to the top. Android clean debug build and desktop clean Release build succeeded;
desktop clean smoke ×2 reported SMOKE PASS (including learning_stats_export=excluded).

### WorkBuddy share-site course migration (2026-11-25)

The 3-course/46-question Nortu L1-2 pack, 23 embedded recordings, Nozu Sans TTF and 17-entry
vocabulary are migrated by `tools/migrate_workbuddy_course.py` into
the external `/ai/nortu/nortu-l1-2.conlang`. Both platforms import it and learn choice/arrange/type/match/listen;
the new generic non-listen question audio button auto-plays question audio. Details and skipped
fields: [migration/WORKBUDDY_MIGRATION.md](migration/WORKBUDDY_MIGRATION.md). Headless audio
verification uses WAV/MediaPlayer/SDL logs; physical audibility remains RD-07.

## Build / smoke

- The desktop smoke needs `/tmp/lexicon-sample.jsonl` (5312 unique words + duplicated `e`/`u` + `_meta` type table). If it is missing, regenerate it from any project's stores:
  `python3 desktop/tests/make-lexicon-fixture.py <vocabulary.json> <pos.json> /tmp/lexicon-sample.jsonl`
  (the script also accepts the Nortu project copy used above; without the fixture the smoke reports lexicon/question/learning false).

- Android: `./gradlew clean :app:assembleDebug --no-build-cache` => BUILD SUCCESSFUL (APK android/app/build/outputs/apk/debug/app-debug.apk, 9,206,123 bytes).
- Desktop clean: `cmake -S desktop -B desktop/build -DCMAKE_BUILD_TYPE=Release` + `cmake --build` => success.
- Desktop smoke run twice from clean data: both `SMOKE PASS`; `question=true`, `lexicon_unicode=true`, `picker_cross_page=true`, all audio/image/font/POS flags true.
- Smoke report: /tmp/forge-sdl-shots-final/smoke-report.txt

## Closed gaps
1) Desktop real UI punctuation (xdotool, not smoke construction) PASS
   - Opened course question `nız， e。`; clicked 「插入标点」 popup, clicked 「、」; token appended.
   - Clicked row up: order became `nız， e、。` (screenshot /tmp/t32-punct-added.png, /tmp/t32-punct-moved.png).
   - Clicked delete on `。`, clicked 保存题目; structure.json now has segments word/，/word/、 and sentence `nız， e、`; toast 题目已保存.
   - Root cause of prior failed click: wrong row Y after the token count changed; buttons work, no UI bug found.
   - Required screenshot: /tmp/forge-sdl-shots/question-punctuation.png
2) Android audio reference delete protection PASS
   - Audio manager rows: referenced question audio shows 「被 0 个词汇、1 道题目引用」.
   - Clicked delete: visible toast 「被 0 个词汇、1 道题目引用，请先移除引用」; row/file remained.
   - Screenshot: /tmp/audio-question-ref-delete.png; reference view /tmp/audio-question-ref.png.
3) Android audio trim / reopen PASS (actual values recorded)
   - 3 words + 2 punctuation question, 1.9 s recording; sliders saved audioStart=138, audioEnd=786.
   - API29: 2.0 s recording, saved audioStart=140, audioEnd=818; reopen no crash.
   - JSON: /tmp Android structure evidence below; screenshot /tmp/question-audio-trim.png, API29 /tmp/question-api29-punct-trim.png.
   - Slider target was 200..800 but Android SeekBar drag landed 138..786 / 140..818; desktop smoke has exact 200..800.
4) Android old parts-only / name-only PASS
   - Injected old parts-only question (no segments): opened as nız / 4, completed 4 options + correct, saved.
   - Migrated JSON: segments [{kind:word},{kind:word}], parts same 2 IDs, sentence `nız 4`, options [on,two,three,four], correctIndex 0.
   - name-only old question opened blank without crash in prior task-30 run; desktop smoke also covers old name-only/parts-only.
5) API29 punctuation + trim PASS
   - nız + 「，」, recorded 2.0 s, saved/reopened; segments word/punct, sentence `nız，`, audioStart 140/audioEnd 818.
   - Screenshot /tmp/question-api29-punct-trim.png; no crash.
6) Picker punctuation behavior (documented design)
   - When vocabulary is re-picked, all punctuation tokens are preserved and moved after the new word sequence; toast 「已保留原有标点，并移到新词序列末尾」.
   - Android verified: 1 word + 2 punctuation -> picker adds 2 words -> sentence `nız 4 uogzr。，` (punctuation at end).

## Matrix
| ID | Area | Result | Evidence |
|---|---|---|---|
| A1 | project list/create/pin/delete/stats | PASS | desktop smoke created/deleted/pin; Android prior UI screenshots |
| A2 | chapter/unit/course CRUD/move | PASS | desktop smoke + Android structure UI |
| A3 | course questions new/click/move/delete/empty | PASS | smoke question flags; Android course list |
| B1 | Android 3 words + 2 punctuation | PASS | JSON segments 3 words + 。/，; sentence nız 4 uogzr。， |
| B2 | punctuation move/delete Android | PASS | JSON after delete: 3 words + 。 only |
| B3 | desktop real UI insert punctuation | PASS | xdotool popup + 「、」; /tmp/t32-punct-added.png |
| B4 | desktop real UI move/delete/save | PASS | JSON nız， e、; /tmp/forge-sdl-shots/question-punctuation.png |
| B5 | single word + punctuation | PASS | API29 JSON nız，; Android screenshot |
| B6 | four options / unique correct validation | PASS | prior Android blocked missing option/no words; code checks |
| B7 | correctIndex=-1 injected block | PASS | desktop injected parts/options complete, correctIndex=-1; save toast 请选择一个正确答案, options/correctIndex unchanged; /tmp/t32-correct-minus-one-desktop.png |
| B8 | old parts-only/name-only compatibility | PASS | Android migrated JSON + desktop smoke old flags |
| B9 | cancel new question adds nothing | PASS | prior Android task-30/31 UI; code adds only on save |
| C1 | record/play question audio | PASS | Android 1.9 s/2.0 s recordings; desktop smoke WAV |
| C2 | Android/desktop trim save/reopen | PASS | Android 138..786, API29 140..818, desktop 200..800 |
| C3 | remove/re-record audio cleanup | PASS | remove audio => structure audio empty; new file cleanup observed |
| C4 | audio management question ref count/delete protection | PASS | Android toast + file/row remained |
| C5 | desktop real UI delete protection | PASS | real UI: 资源管理→音频管理→题目音频行 ⋯→删除; toast 被 0 个词汇、1 道题目引用，请先移除引用, row remained; /tmp/forge-sdl-shots/audio-question-ref.png |
| C6 | reference decrease after audio removal | PASS | Android re-enter audio manager => 未被引用 |
| D1 | vocabulary multi-POS/multi-phonetic/Unicode/search/code | PASS | smoke flags pos/phonetic/unicode |
| D2 | picker pagination/slider/cross-page/punctuation retention | PASS | smoke picker_cross_page; Android punctuation retention |
| E1 | lexicon 5312/2 duplicates/three modes/Unicode merge | PASS | smoke lexicon/duplicate/id9/overwrite/merge/unicode |
| E2 | per-word duplicate mixed/cancel, default code, phonetic append | PASS | smoke lexicon_prompt/perword/unicode |
| F1 | fonts/images/audio import/ref/delete guards | PASS | smoke font/image/audio flags |
| F2 | .conlang export/import including question audio | PASS | smoke zip/import/browser export/import |
| J1 | completed course 复习/自测 chooser | PASS | completed A/B + unfinished C fixture; cancel/review/self-test; self-test skipped card+custom-none, used 2/2 progress, no hints/explain, completion 2/2=100% with review best still 50% (1/2); card-only self-test showed no-test warning; unfinished C started directly; Android+desktop screenshots above |
| I1 | learning course selector card refresh | PASS | Android/desktop 4-course hierarchy fixture: completed 80%/100%, unlocked no-stats, locked 50% disabled; light/dark; small Android 720x1280 and desktop 800x600 no overflow; start/retry works; screenshots above |
| H1 | learning first-attempt accuracy + course best | PASS | desktop/Android mixed fixtures: 80% first run, 60% rerun kept 80, 100% rerun refreshed; card + custom-none excluded; restart/best and mid-run no-write verified; JSON learning-stats.json; screenshots above |
| G1 | light/dark theme persistence | PASS | smoke theme/UI screenshots; no user desktop Xvfb |
| G2 | no microphone / permission denied degradation | N/A | API29 emulator has virtual mic and recording succeeded; no physical no-device test — tracked as RD-02 in [REAL_DEVICE_TESTS.md](REAL_DEVICE_TESTS.md) |
| G3 | API29 audio refs/delete protection + old name-only | PASS | refs 被 0 个词汇、1 道题目引用：nız，; delete toast + file remained; old-name opened/blank no crash, completed one word+options+correct saved; /tmp/api29-audio-delete.png, /tmp/api29-old-name.png |

## Key JSON
Android main question:
`{segments:[word,word,word,{punct:。}],parts:[3 IDs],sentence:'nız 4 uogzr。',audio:'resources/audio/question_...m4a',audioDuration:1907,audioStart:138,audioEnd:786}`
Android migrated old parts-only:
`{segments:[word,word],parts:[2 IDs],sentence:'nız 4',options:['on','two','three','four'],correctIndex:0}`
API29:
`{segments:[word,{punct:，}],sentence:'nız，',audioDuration:2043,audioStart:140,audioEnd:818}`
Desktop after real UI punctuation save:
`{segments:[word,{punct:，},word,{punct:、}],sentence:'nız， e、'}`

## Closure update
- correctIndex=-1: PASS (desktop injected question; save blocked, JSON unchanged).
- Desktop real UI audio delete protection: PASS (real menu click; toast + row/file remain).
- API29 audio reference delete protection + old name-only: PASS (refs text, delete toast/file remain; old name-only opened and completed/saved).
- UI fix: audio card 打开 button was not hit-testing under Xvfb at its position; added a real header entry 音频管理 on Resources page; both use app.openAudios().
- UI fix: duplicate ImGui IDs from two 保存题目 buttons produced a Dear ImGui conflict window; labels now use distinct ## IDs.
- N/A: real no-microphone hardware; API29 virtual mic records. Xvfb has no desktop user but smoke/UI worked. Real-device follow-ups: [REAL_DEVICE_TESTS.md](REAL_DEVICE_TESTS.md).

## Screenshots
Android: /tmp/question-punctuation.png, /tmp/question-audio-trim.png, /tmp/audio-question-ref.png, /tmp/audio-question-ref-delete.png, /tmp/question-api29-punct-trim.png
Desktop: /tmp/forge-sdl-shots/question-punctuation.png, /tmp/forge-sdl-shots/question-audio-trim.png, /tmp/forge-sdl-shots/audio-question-ref.png, /tmp/t32-punct-added.png, /tmp/t32-punct-moved.png
Desktop UI navigation: /tmp/t32-course.png, /tmp/t32-question.png, /tmp/t32-popup.png, /tmp/t32-audio-mgmt.png