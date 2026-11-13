# Arrange block vocabulary audio — task-37 final

## PASS
- Android arrange editor real screenshot: only 词块组句 fields (prompt/blocks/正/↑/×/add/alt); no 整句录音, hints, choice labels/options; /tmp/arrange-editor-no-audio.png.
- Android pool block audio: et/no's/uzoz matched by text/display name, logs `blockAudio et -> block_0.m4a 100..500`, `no's -> block_1.m4a 250..650`, `uzoz -> block_2.m4a 400..800`; /tmp/arrange-block-audio.png.
- Android selected block audio: click selected chip logs blockAudio + stopAudio; /tmp/arrange-block-selected.png.
- Fast switching logs stopAudio before each new block; unmatched orst silent/no crash.
- API29: pool et logs `blockAudio et ... 100..500`; selected et/no's logs; orst no match no crash; /tmp/arrange-api29-audio.png.
- Desktop xdotool: pool/selected block logs `FORGE_LEARN blockAudio et -> question_smoke_audio.wav 100..500`; /tmp/forge-sdl-shots/arrange-block-audio.png, arrange-block-selected.png.
- Arrange learning has no whole-sentence audio button; desktop arrange editor has no recording section.
- Android clean build; desktop clean build; smoke twice PASS learning=true; README updated; HTML unchanged; no commit/push.

## FAIL
- None for requested scope.

## N/A
- Audio audibility (headless), physical microphone.