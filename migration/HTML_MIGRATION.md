# HTML demo course → app project migration

Source: `NostongueForge_v1.3.1.html` (unchanged).
Generator: `tools/migrate_html_project.py` (reads the HTML, idempotent).
Outputs:

- `/home/forin/language-forge-imports/Nortu-Intro-html.conlang` — importable ZIP (project files at ZIP root; external, not in the repository).
- `/home/forin/language-forge-imports/Nortu-Intro-html/` — generated project directory used for verification.

## What was migrated

`freshProject()` in the HTML defines exactly one unit and ten questions; the whole unit is
migrated as one chapter → one unit → one course in the app schema.

| HTML | app |
|---|---|
| project.name `Nortu 自由语 · 入门` | `project.json` name/courseLine |
| unit `第 1 关 · 问候与身份` | chapter `第一章` / unit `第一单元` / course name |
| 10 questions | same order, same types (choice→translation_choice, arrange, match, type, card, listen, dialog, reading(choice), cloze, custom(choice)) |
| `kp` + `glosses` words | `vocabulary.json` entries (dictionary headwords by id, plus synthetic entries for inflected glosses such as `no's`), pos mapped from `<BUILTIN>.meta` |
| `BUILTIN.fonts` Nozu Sans `dataUrl` | decoded `resources/fonts/nozu_sans.ttf` + `fonts.json` active font |
| listen `tts: et` | `resources/audio/listen_et.wav` (generated with `espeak-ng -v en`) + `audios.json`; the question also keeps `tts`/`ttsLang` |
| question `tts` / `ttsLang` (HTML stores them on all types; only listen uses them) | every migrated question writes the keys; empty values when HTML has none. The listen question is `tts: "et"`, `ttsLang: "en-US"`, `audioSource: "tts"` |
| `course.ttsLang: en-US` | `project.json` `ttsLang: "en-US"` (the fallback when a question's `ttsLang` is empty) |
| option `ok` flags | `correctIndex` (choice/listen/dialog/reading/custom), `clozeOrder`, arrange `answers` (correct block ids) |
| `warn` `{m,k,t}` | app `warn` `{m,k,t}` unchanged |

Counts after generation: 1 course, 10 questions (all carrying `tts`/`ttsLang`; listen has
`tts="et"`, `ttsLang="en-US"`), 7 vocabulary entries, 3 POS entries, 1 font, 1 audio, and
`project.json` `ttsLang="en-US"`.

## Not migrated (HTML-only fields)

- Per-question `glosses` text, `note`, `pron` on options, `kp` ids (used to select vocabulary),
  `sfx`, `anim`, `imgMax`, `typeNames`, `defaultMode`, `recs`.
- HTML's `course.ttsLang` is migrated to the app project-level `ttsLang`; per-question `ttsLang`
  wins over it at playback time, then it falls back to `en-US`.
- `explain` **is migrated** into the app question's `explain` field (shown after answering).
- The full 5000-word built-in Nortu dictionary is **not** bulk-imported: only words referenced by the
  course (`kp`/`glosses`) are written to `vocabulary.json`. If a full lexicon is wanted, extend the
  script to iterate `<BUILTIN>.words` instead of the referenced ids.
- HTML gloss popovers are replaced by the app's vocabulary hint popup; the migrated questions set
  `hintsEnabled` and the relevant tokens are underlined in learning. HTML `explain` prose is shown in
  the app's post-answer explanation panel.

## Regenerate

```bash
python3 tools/migrate_html_project.py
```

## Verification (this session)

- Desktop: imported `/home/forin/language-forge-imports/Nortu-Intro-html.conlang` via 项目库 → 导入 .conlang; project showed
  `1 章 · 1 关 · 10 题 · 7 词`; learning opened question 1 directly and rendered all ten types in order
  (choice, arrange, match, type, card, listen with working audio, dialog, reading with token hints,
  cloze, custom) and reached the course-complete page.
  Screenshots: `/tmp/mig-first2.png`, `/tmp/mig-q2-arrange2.png`, `/tmp/mig-q3-match.png`,
  `/tmp/mig-q4-type.png`, `/tmp/mig-q5-card.png`, `/tmp/mig-q6-listen.png`, `/tmp/mig-q7-dialog3.png`,
  `/tmp/mig-q8-reading2.png`, `/tmp/mig-q9-cloze2.png`, `/tmp/mig-q10-custom2.png`, `/tmp/mig-complete.png`,
  import screen `/tmp/imp-result.png`.
- Android: imported the same `.conlang` from Downloads; project list shows the imported project and
  learning opens question 1 with the vocabulary hint underline and options.
  Screenshot: `/tmp/android-imp-list.png`, `/tmp/android-imp-learning.png`.
- Counts cross-check: `/home/forin/language-forge-imports/Nortu-Intro-html/project.json` (`questions=10`, `words=7`,
  `ttsLang="en-US"`), `structure.json` has 1 course with 10 questions (all with `tts`/`ttsLang`;
  listen has `tts="et"`, `ttsLang="en-US"`), `audios.json`/`fonts.json` each have one entry.
- TTS follow-up (2026-11-24): desktop smoke imports this `.conlang`, verifies the migrated listen
  question falls back to TTS (`FORGE_SMOKE tts_migration data=1 tts=1 text=et lang=en-US`) when its
  audio is cleared, and returns to audio-first when restored; screenshot
  `/tmp/tts-evidence/desktop/tts-migration-learn.png`, WAV `/tmp/tts-evidence/desktop/tts-listen.wav`.
  Android reads the same generated project with project `ttsLang=en-US`, 10 questions and listen
  `et`/`en-US` (`TTS_TESTING.md` §4.5).

## Known boundaries

- The migrated `type` question accepts the original `zıon`; desktop X11 could not inject `ı` while typing,
  so the end-to-end correct-answer click was not exercised on desktop (the field/accepted answers render
  correctly, and the type engine is covered by other fixtures).
- The listen audio is a synthesized English pronunciation of `et` (the HTML used browser TTS). It is
  kept as audio-first material; deleting/emptying its `audio` field makes the app fall back to the
  migrated `tts` (et, en-US) through the system TTS engine, matching the HTML play() path.
- HTML `gloss` prose is not part of the app schema and is recorded here rather than in the project;
  HTML `explain` is migrated to question.explain.
