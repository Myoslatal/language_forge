# Listen options with handwritten/image vocabulary — QA

Date: 2026-11-21 (Asia/Shanghai)
Build: Android debug APK + SDL2 desktop (Xvfb :131, 1280x860)
HTML: NostongueForge_v1.3.1.html unchanged (sha256 7c1f30f8…)

## Problem
Composing listen options from the vocabulary picker flattened every entry to displayName.
For image ("手写") vocabulary displayName falls back to the first meaning, so the option
stored a Chinese gloss instead of the word and the handwriting could never be shown.

## Model
```json
"options": ["a 你好", "再见", "谢谢", "对不起"],
"optionItems": [
  [{"kind":"vocab","vocabId":"vocab-hand-a"},{"kind":"text","text":"你好"}],
  [{"kind":"text","text":"再见"}],
  [{"kind":"text","text":"谢谢"}],
  [{"kind":"text","text":"对不起"}]
]
```
- `options` stays a plain-text mirror for legacy readers/HTML/export.
- Mirror text for image entries: first code (`a`), then first meaning, then `〔图片〕`.
- Editor load recomputes the mirror from optionItems so a stored file renders consistently.
- Save collapses an option to one text item when the edited text no longer matches the mirror.
- Groups with no vocab item are dropped on save, so text-only questions keep their old shape.
- Fixed a brace bug that wrote `optionItems` only when `audioSource` was non-empty; the editor
  now also preserves `audioSource` on load.

## Android (API36, emulator)
- Editor shows preview: handwritten image thumbnail + text with per-item × delete;
  mirror field reads `a 你好`. Screenshot /tmp/android-optimg-editor.png.
- Save/reopen round-trip on the image question keeps both items
  (`[{"kind":"vocab","vocabId":"vocab-hand-a"},{"kind":"text","text":"你好"}]`) and `audioSource`.
  Verified from pulled structure.json (options[0] = `a 你好`).
- Learning renders option 1 as the handwritten image + 你好 (not the gloss);
  select → confirm → green + 下一题. Screenshots /tmp/android-optimg-learn.png,
  /tmp/android-optimg-correct.png.
- Pure-text question keeps the plain ForgeButton path; saving it drops optionItems
  (structure.json no longer contains the key).

## Desktop (SDL2, Xvfb :131)
- Editor preview shows the image thumbnail + text with × delete; screenshot
  /tmp/forge-sdl-shots/listen-option-image-editor.png.
- Learning renders the handwritten image in option 1, correct answer turns green and shows
  下一题; screenshots /tmp/forge-sdl-shots/listen-option-image-learn.png,
  /tmp/forge-sdl-shots/listen-option-image-correct.png.
- Save round-trip keeps optionItems for the image question; text-only question saved with no
  optionItems key (verified structure.json).
- Pure-text learning unchanged: /tmp/optimg-text-learn.png.

## Regression / build
- Desktop smoke twice: SMOKE PASS, all flags true (lexicon/question/learning included) after
  regenerating /tmp/lexicon-sample.jsonl with the new committed generator.
- Generator: desktop/tests/make-lexicon-fixture.py (documented in TESTING.md).
- Android clean build and desktop clean build succeed.

## Boundaries
- Composition is implemented for listen options; other question types keep plain text options.
- Punctuation is still typed manually; the picker only inserts vocabulary items.
- If a vocabulary image file is missing, both platforms show 〔图片缺失〕 (desktop equivalent text)
  instead of crashing.
- Audio audibility and physical microphone remain N/A (see REAL_DEVICE_TESTS.md).
