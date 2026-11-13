# Android learning Forge-style UI — task-35 final

## PASS
- Forge-style answer page: rounded option cards, primary blue bottom confirm above nav inset; /tmp/learn-forge-answer.png.
- Floating hint card: rounded/shadow, no 再听一次/关闭, text 再次点击词汇可重播; /tmp/learn-forge-hint.png.
- Same-word replay keeps card and replays; /tmp/learn-forge-same-word-replay.png; API29 /tmp/t35-api29-replay2.png.
- Blank outside closes; /tmp/t35-api29-blank.png.
- Option tap closes hint and selects option, confirm appears; /tmp/learn-forge-option-close.png.
- No-audio word shows 暂无示例发音, toast, no crash; /tmp/learn-forge-no-audio.png.
- hintsEnabled=false: no underline/hint, options usable; /tmp/learn-forge-hints-off.png.
- Wrong confirm: blue selected card + 回答错误，请重新选择 toast; /tmp/learn-forge-confirm-wrong.png.
- Correct: green option + 下一题; /tmp/learn-forge-confirm-correct.png.
- Last question: 完成 primary + 本课完成, returns list; /tmp/learn-forge-last-complete.png, learn-forge-last-back.png.
- API29 exact-bounds: wrong -> retry -> correct green -> 下一题; /tmp/learn-forge-api29-correct2.png.
- Android clean build successful; desktop unchanged smoke PASS learning=true; HTML unchanged; no commit/push.

## FAIL
- None for this feature scope.

## N/A
- Audio audibility (headless), physical microphone.