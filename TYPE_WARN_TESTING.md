# Type prompt compose + warn — task-42 last verification

## PASS
- Desktop picker height fixed: selected apple and confirmed; prompt `你好 apple`; saved/reopened JSON same; /tmp/forge-sdl-shots/type-prompt-compose.png.
- Desktop warn editor/save/reopen + helo custom yellow warning; /tmp/forge-sdl-shots/type-warn-editor.png, type-warn-learn.png.
- Android editor prompt picker opened `选择题干词汇`; selecting nız appended to prompt; save JSON retained warn eq+has and acceptedAnswers.
- Android subtitle 输入翻译; last question correct -> 完成 -> course list; /tmp/type-learn.png, type-last-complete.png.
- Android ignoreCase=false HELLO rejected; /tmp/type-case-off.png.
- Android API29 wrong bad then correct hello -> green/完成; /tmp/api29-type-wrong.png, api29-type-correct.png.
- Android/desktop clean builds; smoke twice PASS learning=true; HTML unchanged; no commit/push.

## FAIL / not verified
- Android prompt append originally produced `nız你好 apple` (no space before existing text); code fixed to insert a trailing space, but fix was not re-verified UI.
- Android ignoreSpace=false, near=false, warn eq/has hit, last reset not run.
- Desktop empty prompt/all-empty answers/duplicate warn validation, picker cancel, wrong/near/reset not run.

## N/A
- Audio audibility, physical microphone.