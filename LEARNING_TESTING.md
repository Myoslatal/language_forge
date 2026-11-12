# Learning mode gap closure — task-33

## Result
PASS 11 / FAIL 0 / N/A 2 (audio audibility, physical microphone)

## PASS
- Android learning topbar: applyTopInset/applyBottomInset added; recaptured /tmp/learn-inset.png, /tmp/learn-sentence.png, /tmp/api29-learn.png without status-bar overlap.
- Android hintsEnabled real editor UI: checkbox true -> click off -> save => JSON false; reopen -> click on -> save => JSON true.
- Desktop hintsEnabled real editor UI: xdotool click checkbox -> save => JSON true; reopen -> click off -> save => JSON false. /tmp/t33-desktop-editor-checkbox.png.
- Android single word no punctuation: injected single-word question; learning page underlined, click shows phonetics/POS/meanings, auto-play status text; /tmp/learn-single.png, /tmp/learn-single-hint.png.
- Android deleted word/missing partId: learning page renders 词汇已删除, no crash; /tmp/learn-deleted-word.png.
- API29 hintsEnabled=false learning page: 本题未启用学习提示, no underline, no crash; /tmp/api29-learn-off.png.
- API29 hintsEnabled=true níz punctuation: word underlined, comma not; /tmp/api29-learn.png.
- Android real vocabulary audio binding: nız audio resources/audio/vocab_niz_test.m4a, audioStart=200/audioEnd=800; hint says 示例发音：已自动播放, MediaPlayer no crash; /tmp/learn-word-hint.png.
- Desktop real xdotool word click: hint panel and audio entry; /tmp/forge-sdl-shots/learn-word-hint.png and learn-word-hint-audio.png.
- Desktop hints off page: plain sentence/no hint; /tmp/forge-sdl-shots/learn-hints-off.png.
- Android clean BUILD SUCCESSFUL; desktop clean build; smoke twice PASS, question=true learning=true.

## N/A
- Audio audibility: headless emulator/Xvfb has no audible output; MediaPlayer/SDL_mixer calls and no-crash verified.
- Physical microphone hardware: not available.

## Known limitation
- Desktop hints-off screenshot is from a JSON-injected state; desktop editor checkbox itself was clicked and JSON true/false verified separately.