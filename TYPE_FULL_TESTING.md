# Type full QA — task-43 final

## Android (API36)
- A1 清空答案按钮: all lines cleared; save blocked with 请至少填写一条可接受答案; JSON unchanged; /tmp/type-all-invalid-answers.png.
- A2 warn.m empty: blocked, JSON unchanged; /tmp/type-all-warn-empty.png.
- A2 warn.m duplicate: blocked, JSON unchanged; /tmp/type-all-warn-duplicate.png.
- A2 warn.m equal accepted answer: blocked, JSON unchanged; /tmp/type-all-warn-answerdup.png.
- A3 single type: picker append `nız 你好`; warn eq(helo)+has(hel); ignoreCase off; saved JSON id=type-1 prompt=`nız 你好` ignoreCase=false; reopen UI matches; /tmp/type-all-a3-editor.png, type-all-a3-reopen.png.
- T2 ignoreCase true HELLO green / false HELLO error; type-all-case-true.png, type-all-casefalse.png.
- T3 ignoreSpace true `he llo` green / false error; type-all-spacetrue.png, type-all-spacefalse.png.
- T4 near=true helo yellow 差一点就对了，应该是 hello; type-all-neartrue.png. near=false helo error/no yellow; type-all-nearfalse.png.
- T5 warn eq 差一点，用 hello / has 少了字母 yellow; type-all-warneq.png, type-all-warnhas.png.
- T6 warn priority over near; type-all-warneq.png.
- T9 two questions: Q1 correct -> Q2 input empty; Q2 完成 -> list; re-enter Q1 empty; type-all-next-reset.png, last-complete2.png, complete-list.png, reenter.png.
- Prompt append space regression: `nız 你好`; /tmp/prompt-state.png.

## Android API29
- warn eq yellow 差一点，用 hello; /tmp/api29-warn.png.
- near hallo yellow 差一点就对了，应该是 hello; /tmp/api29-near.png.
- correct hello -> 完成 -> course list; /tmp/api29-correct.png, api29-list.png.

## Desktop (root-provided real UI evidence)
- warn eq: /tmp/desk3-warn-eq-result.png; warn has: /tmp/desk3-warn-has.png.
- correct: /tmp/desk3-correct1.png; next reset: /tmp/desk3-nextreset.png.
- near=false no yellow + error toast: /tmp/desk3-nearfalse.png.
- last correct/complete list: /tmp/desk3-lastcorrect.png, /tmp/desk3-complete-list.png.
- picker cancel/empty prompt/empty answers/duplicate warn/error/near/warn priority from this session: type-all-cancel.png, type-all-empty-prompt.png, type-all-empty-answers.png, type-all-dup-warn.png, type-all-error.png, type-all-warn-learn.png, type-all-near.png.

## Regression
- choice correct: /tmp/regress-choice.png, regress-choice-done.png.
- arrange correct: /tmp/regress-arrange.png, regress-arrange-done.png.
- match complete: /tmp/regress-match.png, regress-match-done.png.
- Desktop regression root evidence: /tmp/regress-choice.png, regress-match-done2.png, regress-arrange-done.png.

## Build/Repo
- Android clean build SUCCESS; desktop clean build SUCCESS; smoke twice PASS learning=true.
- HTML sha256 7c1f30f8b37f7b4b91dd31ae1f63032f10e10b9ccdb516b6045a4228e39643dd unchanged.
- No commit/push.

FAIL=0 (N/A: audio audibility, physical microphone).