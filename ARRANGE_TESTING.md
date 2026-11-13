# Arrange question type — task-36 final (all requested scenarios)

## PASS
- Android arrange editor UI: 词块组句 subtitle; choice labels + hints hidden; /tmp/arrange-editor.png.
- Android save/reopen: type=arrange, prompt, blocks+ok, alt preserved.
- Android empty block blocked: blank block added, save toast 词块文本不能为空, structure.json still 5 blocks/no empty; /tmp/arrange-empty-block.png.
- Android old choice edit/save: type translation_choice, name/options unchanged; /tmp/arrange-old-choice.png.
- Android alt equivalence: alt=['no's et uzoz']; selected no's/et/uzoz -> green + 下一题; /tmp/arrange-alt.png.
- Desktop editor save/reopen: type/prompt/5 blocks+ok/alt reloaded identically; /tmp/forge-sdl-shots/arrange-editor-reopen.png.
- Desktop validation: empty block toast 词块文本不能为空, JSON unchanged; <2 ok blocked; empty prompt blocked; /tmp/forge-sdl-shots/arrange-validation.png, arrange-validation-ok.png, arrange-validation-prompt.png.
- Desktop reorder: selected uzoz/et/no's, removed uzoz, re-added -> et/no's/uzoz green + 下一题; /tmp/forge-sdl-shots/arrange-reorder-next.png, arrange-next.png.
- API29 arrange: wrong confirm toast, exact-text taps correct order -> 完成 -> list; /tmp/arrange-api29-wrong.png, arrange-api29-correct.png, arrange-api29-complete.png.
- Android clean build success; desktop clean build; smoke twice PASS learning=true; README updated; HTML unchanged; no commit/push.

## FAIL
- None for the requested arrange scenarios.

## N/A
- Audio audibility, physical microphone.