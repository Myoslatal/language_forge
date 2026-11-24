#pragma once

#include <filesystem>
#include <string>

namespace forge {

namespace fs = std::filesystem;

struct TtsEngineInfo {
  bool available = false;
  std::string engine;  // espeak-ng, espeak, say, powershell
  std::string error;   // user-facing reason when unavailable
};

struct TtsSynthesisResult {
  bool ok = false;
  std::string error;
  std::string engine;
  std::string voice;
  std::string command;
  fs::path file;
  int durationMs = 0;
};

// Detects a usable system speech synthesizer without speaking anything.
TtsEngineInfo detectTtsEngine();

// Normalizes a BCP-47-ish language tag to a non-empty value; empty -> en-US.
std::string normalizeTtsLang(const std::string& lang);

// Resolves question-level -> project-level -> en-US fallback.
std::string resolveTtsLang(const std::string& questionLang, const std::string& projectLang);

// Synthesizes UTF-8 text with a system TTS command (espeak-ng/espeak on Linux,
// System.Speech on Windows, say on macOS) into a PCM WAV file. rate mirrors the
// HTML Web Speech API rate (0.85 default); text is passed through a temp file so
// no shell escaping of the spoken content is needed.
TtsSynthesisResult synthesizeTts(const std::string& text, const std::string& lang,
                                 double rate, const fs::path& outputWav);

// Returns the duration of a PCM WAV file in milliseconds, or 0 when unknown.
int wavDurationMs(const fs::path& file);

}  // namespace forge
