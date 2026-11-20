# Listen option vocabulary composition — QA

## Implemented
- Each listen option row has a 选词 button.
- Picker is the shared multi-select vocabulary picker; selected display text is appended to that option, space-separated.
- Direct text editing remains; correct-answer radio is unchanged.
- Android and SDL2 desktop.

## Verified
- Android API36 editor: four 选词 buttons visible next to options; audio controls remain.
- Android option 1: tapped 选词, selected nız, option became “你好 nız”; saved structure.json kept ["你好 nız","再见","谢谢","对不起"] with correctIndex 0.
- Android clean build successful.
- Existing listen audio/record/trim and learning flow unchanged by this change.

## Not covered / boundaries
- Desktop option picker was compiled but not separately exercised with a screenshot.
- The picker appends vocabulary display text only; punctuation is typed manually.
- Composition is currently for listen options; choice/arrange/match/type option fields are unchanged.
- Audio audibility/microphone remain N/A.

