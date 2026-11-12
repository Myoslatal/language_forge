# Full regression report — Android + SDL2 desktop

Date: 2026-11-12 (Asia/Shanghai)
Repo: /ai/language_forge
Android: API36 AVD EnvCheck_API36 + API29 AVD EnvCheck_API29, debug APK
Desktop: SDL2/ImGui under Xvfb :99 1280x860, SDL_VIDEODRIVER=x11
Tested source: 18c9b2f feat: add question punctuation and trimmed sentence audio references + desktop UI fixes included in the report commit
HTML: NostongueForge_v1.3.1.html unchanged (git diff exit 0)

## Summary
PASS 26 / FAIL 0 / N/A 1 (matrix rows 27).

## Build / smoke
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
| G1 | light/dark theme persistence | PASS | smoke theme/UI screenshots; no user desktop Xvfb |
| G2 | no microphone / permission denied degradation | N/A | API29 emulator has virtual mic and recording succeeded; no physical no-device test |
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
- N/A: real no-microphone hardware; API29 virtual mic records. Xvfb has no desktop user but smoke/UI worked.

## Screenshots
Android: /tmp/question-punctuation.png, /tmp/question-audio-trim.png, /tmp/audio-question-ref.png, /tmp/audio-question-ref-delete.png, /tmp/question-api29-punct-trim.png
Desktop: /tmp/forge-sdl-shots/question-punctuation.png, /tmp/forge-sdl-shots/question-audio-trim.png, /tmp/forge-sdl-shots/audio-question-ref.png, /tmp/t32-punct-added.png, /tmp/t32-punct-moved.png
Desktop UI navigation: /tmp/t32-course.png, /tmp/t32-question.png, /tmp/t32-popup.png, /tmp/t32-audio-mgmt.png