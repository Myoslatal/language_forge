# Listen question type — implementation/QA

## Implemented
- type="listen" with prompt (optional), audio/audioStart/audioEnd, four text options, correctIndex.
- Editors: type selector, prompt/options/correct radio, audio card with record/play/remove/trim, and “从词汇示例音频选择” (vocabulary audio + trim).
- Validation: audio required and file must exist; four non-empty options; exactly one correct.
- Learning: auto-play once per question, 播放中…/重新播放 state, replay, missing-audio warning with playback/options/confirm disabled, Forge options + confirm + next/finish, stop on confirm/next/back/onPause.
- Android and SDL2 desktop.

## Verified
- Android API36 editor: audio controls visible without choice sections; /tmp/android-listen-editor2.png.
- Android vocabulary audio picker: selected nız → label resources/audio/vocab_niz_test.m4a 200..800; saved JSON kept path/start/end; /tmp/android-listen-picker.png.
- Android learning: auto-play log FORGE_LEARN listenPlay resources/audio/vocab_niz_test.m4a 200..800, then stopAudio; button 重新播放; /tmp/android-listen-learn.png.
- Android correct option green + 完成; /tmp/android-listen-correct-new2.png.
- Android missing audio: 本题缺少音频, play disabled, options disabled, no confirm; /tmp/android-listen-missing2.png.
- Android API29: auto-play, 重新播放, correct green, completion; /tmp/api29-listen.png, /tmp/api29-listen-correct.png.
- Desktop: auto-play stderr FORGE_LEARN listenPlay and stopAudio; 播放中… → 重新播放; correct green; /tmp/desk-listen-playing.png, /tmp/desk-listen-correct2.png.
- Android/desktop clean builds; Xvfb smoke twice PASS learning=true; HTML unchanged sha256 7c1f30f8….
- Existing choice/arrange/match/type learning flows unchanged by smoke and prior tests.

## Not covered / boundaries
- Desktop editor save/reopen and vocabulary-audio binding not exercised through UI (code path exists and builds).
- Desktop missing-audio UI and API29 missing-audio case not separately captured.
- TTS/朗读文本 is not implemented; listen needs a recorded/project/vocabulary audio file.
- Audio audibility and physical microphone are N/A in headless tests.
