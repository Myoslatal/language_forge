# Real-device test backlog (all N/A items)

This file records every test item that was reported as **N/A** because the current environment could not exercise it, plus the Windows runtime check that is environment-limited. These are **not known failures**; they are pending real-hardware verification.

Baseline: commit `14d46f4` (learning mode) / reports [TESTING.md](TESTING.md) and [LEARNING_TESTING.md](LEARNING_TESTING.md).

## Status

| ID | Pending check | Platform | Source N/A | Priority | Status |
|---|---|---|---|---|---|
| RD-01 | Real microphone capture, audio quality, save/reopen | Android physical device | LEARNING_TESTING N/A "physical microphone" | P0 | [ ] |
| RD-02 | No microphone / RECORD_AUDIO permission denied degradation | Android physical device | TESTING G2 N/A | P1 | [ ] |
| RD-03 | Speaker/headphone audibility, trim boundaries, learning auto-play | Android physical device | LEARNING_TESTING N/A "audio audibility" | P0 | [ ] |
| RD-04 | Real microphone capture, audio quality, save/reopen | Desktop (Linux/Windows) | LEARNING_TESTING N/A "physical microphone" | P0 | [ ] |
| RD-05 | Speaker/headphone audibility, trim boundaries, learning auto-play | Desktop (Linux/Windows) | LEARNING_TESTING N/A "audio audibility" | P0 | [ ] |
| RD-06 | Windows build + runtime (not N/A, environment-limited) | Windows | Environment limit noted in final reply | P1 | [ ] |

Traceability: the 3 N/A rows in the two reports expand to RD-01..RD-05; RD-06 is an additional environment-limited check. Xvfb itself is only a headless display, not a functional N/A.

## Common preconditions

- Build the latest Android debug APK:
  ```bash
  cd android && ./gradlew clean :app:assembleDebug
  ```
- Desktop binary: `desktop/build/nostongue-forge` (or the Windows `.exe` from RD-06).
- Create or import a project with:
  - one vocabulary with phonetic(s) and a real recorded audio;
  - one translation-choice question containing that vocabulary, with sentence audio;
  - a learning-mode path: project → 学习 → course → question.
- Use a short spoken test phrase; do not record private/sensitive content.
- Capture evidence: screen recording or phone video with audible sound, screenshots, `structure.json`/`vocabulary.json`, audio file size, and relevant `logcat` lines.

## RD-01 Android real microphone

1. Install the APK on a physical Android device (API 29–36), grant `RECORD_AUDIO`.
2. Vocabulary editor → 示例发音 → 录制; speak 2–3 seconds; stop.
3. Play, trim to ~0.2–0.8 s, play again; save.
4. Question editor → 整句录音 → record a sentence; save; reopen.
5. Force-stop/relaunch; play both audios.
6. Export `.conlang`, re-import into a new project.

Expected:
- Audio is audible and matches the recording; duration/file size > 0.
- Trim start/end is respected; saved `audioStart/audioEnd` matches the UI.
- Reopen/relaunch/export/import keep the audio.
- No crash, no orphan pending file after cancel.

Evidence to record: device model/API, permission state, screenshots/video with sound, JSON excerpt, file path/size.

## RD-02 Android no microphone / permission denied

1. Revoke microphone permission in system settings (or choose Deny on the permission prompt).
2. Try recording in vocabulary audio and question audio.
3. Check UI, files, and JSON after denial.
4. Re-grant and record again.

Expected:
- A clear toast/permission message; no crash.
- No valid audio path saved; pending/cache cleaned.
- After re-grant, recording works.

Evidence: permission dialog screenshot, toast screenshot, file listing, JSON.

## RD-03 Android real audio output

1. Use speaker and wired/Bluetooth headphones.
2. Play vocabulary audio and question sentence audio from editors and learning mode.
3. Set trims to very short ranges (e.g. 100–300 ms) and listen for boundaries.
4. Click an underlined word in learning mode; verify auto-play and "再听一次".
5. Switch `hintsEnabled` off and verify no auto-play.
6. Play while switching to another word/page; verify old audio stops.

Expected:
- Audible output on speaker/headphones; no clipping/overlap.
- Trim boundaries are audibly correct.
- Learning hint auto-play is audible once; repeat button works; off mode is silent.
- No crash when audio route changes or app backgrounds.

## RD-04 Desktop real microphone

1. Run on a real desktop session (not Xvfb / not headless), with a microphone selected by the OS.
2. Record vocabulary pronunciation and question sentence audio; stop/play/trim/save/reopen.
3. Restart the app and play; export/import `.conlang`.

Expected:
- SDL audio capture records audible speech; duration/file > 0.
- Playback and trim work; saved `audioStart/audioEnd` correct.
- No device / disconnected device shows a graceful message, no crash.

## RD-05 Desktop real audio output

1. Run on real speakers/headphones (Linux and Windows if available).
2. Play vocabulary audio, question audio, and learning hint auto-play.
3. Test trim boundaries and rapid switching between words.
4. Test `hintsEnabled=false` (no auto-play) and a question with no audio.

Expected:
- Audible output with correct stop at trim end.
- Switching words stops the previous clip.
- No audio path shows a graceful placeholder/toast.

## RD-06 Windows build/runtime

This was environment-limited (no MinGW/vcpkg toolchain here), not an N/A functional result.

1. Install vcpkg SDL2/SDL2_ttf/SDL2_image/SDL2_mixer or use MinGW packages.
2. Configure/build with CMake per [desktop/README.md](desktop/README.md); copy the SDL DLLs next to `nostongue-forge.exe`.
3. Run on Windows with a path containing Chinese characters/spaces.
4. Check CJK UI font, file browser, project create/import/export, question learning underlines, audio output and microphone capture.

Expected:
- Windows executable starts, data root is `%USERPROFILE%/Documents/Nostongue Forge/projects`.
- Chinese UI and project paths render/load; audio/record works or degrades gracefully.
- No missing-DLL or font-crash.

## Recording results

For each completed item, append:

```text
ID: RD-xx
Date/device/OS:
Build/commit:
Result: PASS / FAIL / BLOCKED
Evidence paths:
Notes:
```

Keep this file updated when real-hardware checks are run. Do not mark an item PASS without audible/real-device evidence.
