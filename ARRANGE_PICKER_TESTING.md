# Arrange blocks via vocabulary picker — task-38 final

## PASS
- Data: blocks[].vocabId Android/desktop load/save; old blocks without vocabId preserved.
- Android Picker: 从词汇选择 added uzoz with text=uzoz + vocabId; JSON saved; old 5 blocks kept.
- Android Picker cancel: back arrow, blocks remained 6; /tmp/arrange-picker-cancel.png.
- Android duplicate: reopen Picker with existing vocabId selected, confirm -> blocks stayed 6; /tmp/arrange-picker-duplicate.png.
- Android learning vocabId priority: changed block text to uzozX (no text match), click -> log `blockAudio uzozX vocabId=66f2... -> block_2.m4a 400..800`; text fallback not used.
- API29 Picker: selected nız, confirm/save -> JSON block `{text:nız, vocabId:1b26...}`; no crash.
- Desktop Picker: selected no's/uzoz in order, confirm/save -> JSON two blocks with vocabId; re-open confirm skipped duplicates, count unchanged.
- Android/desktop clean builds; smoke twice PASS learning=true; README updated; HTML unchanged; no commit/push.

## FAIL / not verified
- API29 save/reopen after Picker add not re-opened; screenshot only JSON.
- Desktop learning vocabId-priority audio log not captured after Picker blocks.
- Dedicated /tmp/arrange-block-vocabid screenshot not captured (JSON/log evidence).

## N/A
- Audio audibility, physical microphone.