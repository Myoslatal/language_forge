# Match question type — task-40 supplement

## PASS
- Data: type=match prompt/pairs load/save; name prompt + N 对.
- Android editor 4 pairs: JSON saved `配对练习 · 4 对` with 4 pairs (no/ıo/et/uzoz), reopened fields intact; /tmp/match-editor.png / match-editor-reopen.png.
- Android match editor hides choice/arrange/audio/hints and 尚未选择词汇/选择词汇/插入标点; screenshot /tmp/match-editor.png.
- Android <2 pairs validation blocked: after deleting rows save, JSON still 4 pairs; /tmp/match-validation.png.
- Android learning previously verified wrong toast/correct green/all complete; subtitle code now returns 配对.
- Desktop match learning two shuffled columns + wrong toast; /tmp/forge-sdl-shots/match-learn.png, match-wrong.png.
- Android/desktop clean builds; smoke twice PASS learning=true; HTML unchanged; no commit/push.

## FAIL / not verified
- Desktop editor match screenshot/save/reopen/≥2 validation not run.
- Android last-question 完成 return and reset not run after data changes; match learning subtitle not re-screenshot.
- API29 match wrong/correct not run.
- Desktop full-correct → 下一题 screenshot not completed.

## N/A
- Audio audibility, physical microphone.