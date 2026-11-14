# Type question type — task-41 supplement

## PASS
- Android editor/learning core: type=type fields and switches; wrong/near/correct UI; saves acceptedAnswers/name/switches.
- Desktop type editor screenshot: prompt/multiline accepted answers/3 switches; /tmp/forge-sdl-shots/type-editor.png.
- Desktop type learning real input hello -> 确认答案 -> 回答正确 green + 下一题; /tmp/forge-sdl-shots/type-learn.png, type-correct.png.
- Desktop typeIndex combo mapping fixed.
- Android/desktop clean builds; smoke twice PASS learning=true; HTML unchanged; no commit/push.

## FAIL / not verified
- Desktop type editor save/reopen JSON and empty-prompt/all-empty validation not run; combo screenshot predates typeIndex fix.
- Desktop wrong/near input and next/reset not captured.
- Android subtitle 输入翻译 screenshot not retaken; ignoreCase/ignoreSpace off cases, last-question 完成, API29 correct+wrong not run.

## N/A
- Audio audibility, physical microphone.