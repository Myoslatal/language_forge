#include "tts.h"

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <random>
#include <sstream>
#include <system_error>

#ifndef _WIN32
#include <sys/wait.h>
#endif

namespace forge {
namespace {

std::string trim(std::string value) {
  auto notSpace = [](unsigned char c) { return !std::isspace(c); };
  value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
  value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
  return value;
}

std::string lower(std::string value) {
  std::transform(value.begin(), value.end(), value.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return value;
}

std::string shellQuote(const std::string& value) {
#ifdef _WIN32
  std::string out = "\"";
  for (char c : value) {
    if (c == '"') out += "\\\"";
    else out += c;
  }
  out += "\"";
  return out;
#else
  std::string out = "'";
  for (char c : value) {
    if (c == '\'') out += "'\\''";
    else out += c;
  }
  out += "'";
  return out;
#endif
}

std::string uniqueSuffix() {
  static std::mt19937_64 engine(std::random_device{}());
  return std::to_string(engine());
}

bool writeTextFile(const fs::path& file, const std::string& text) {
  std::error_code ec;
  fs::create_directories(file.parent_path(), ec);
  std::ofstream out(file, std::ios::binary | std::ios::trunc);
  if (!out) return false;
  out.write(text.data(), static_cast<std::streamsize>(text.size()));
  return static_cast<bool>(out);
}

bool commandExists(const std::string& command) {
#ifdef _WIN32
  const std::string probe = "where " + command + " >NUL 2>NUL";
#else
  const std::string probe = "command -v " + command + " >/dev/null 2>&1";
#endif
  return std::system(probe.c_str()) == 0;
}

int runCommand(const std::string& command, const fs::path& logFile) {
  const std::string full = command + " >" + shellQuote(logFile.string()) + " 2>&1";
  const int status = std::system(full.c_str());
#ifdef _WIN32
  return status;
#else
  if (status == -1) return -1;
  if (WIFEXITED(status)) return WEXITSTATUS(status);
  return 128;
#endif
}

std::string readLog(const fs::path& file) {
  std::ifstream in(file, std::ios::binary);
  if (!in) return {};
  std::ostringstream buffer;
  buffer << in.rdbuf();
  std::string text = buffer.str();
  if (text.size() > 400) text.resize(400);
  return trim(text);
}

std::string espeakVoiceFor(const std::string& lang) {
  std::string tag = lower(normalizeTtsLang(lang));
  std::replace(tag.begin(), tag.end(), '_', '-');
  const auto starts = [&](const char* prefix) {
    const std::string value(prefix);
    return tag.rfind(value, 0) == 0;
  };
  if (starts("en")) {
    if (tag == "en-gb") return "en-gb";
    return "en-us";
  }
  if (starts("zh")) return "cmn";
  if (starts("no")) return "nb";
  if (starts("el")) return "el";
  if (starts("he")) return "he";
  if (starts("id")) return "id";
  if (starts("ja")) return "ja";
  if (starts("ko")) return "ko";
  static const char* simple[] = {"et", "fi", "fr", "de", "es", "it", "pt", "ru",
                                 "ar", "hi", "tr", "sv", "nl", "pl", "ro", "hu",
                                 "cs", "da", "sk", "uk", "vi", "th", "bg", "ca"};
  for (const char* prefix : simple) {
    if (starts(prefix)) return prefix;
  }
  // Unknown tags: let espeak-ng interpret a short alphabetic tag, otherwise fall back.
  bool safe = !tag.empty() && tag.size() <= 12;
  for (char c : tag) {
    if (!(std::isalpha(static_cast<unsigned char>(c)) || c == '-')) safe = false;
  }
  return safe ? tag : "en-us";
}

int wordsPerMinute(double rate) {
  if (!(rate > 0.0) || !std::isfinite(rate)) rate = 0.85;
  return std::max(60, static_cast<int>(std::lround(175.0 * rate)));
}

}  // namespace

std::string normalizeTtsLang(const std::string& lang) {
  std::string value = trim(lang);
  if (value.empty()) return "en-US";
  std::replace(value.begin(), value.end(), '_', '-');
  return value;
}

std::string resolveTtsLang(const std::string& questionLang, const std::string& projectLang) {
  const std::string question = trim(questionLang);
  if (!question.empty()) return question;
  const std::string project = trim(projectLang);
  if (!project.empty()) return project;
  return "en-US";
}

int wavDurationMs(const fs::path& file) {
  std::ifstream in(file, std::ios::binary);
  if (!in) return 0;
  char riff[4] = {};
  in.read(riff, 4);
  if (std::string(riff, 4) != "RIFF") return 0;
  in.seekg(12, std::ios::beg);
  std::uint32_t byteRate = 0;
  std::uint32_t dataSize = 0;
  while (in) {
    char id[4] = {};
    std::uint32_t size = 0;
    in.read(id, 4);
    in.read(reinterpret_cast<char*>(&size), 4);
    if (!in) break;
    const std::string chunk(id, 4);
    if (chunk == "fmt ") {
      char fmt[16] = {};
      const std::streamsize keep = std::min<std::streamsize>(16, size);
      in.read(fmt, keep);
      if (keep >= 16) {
        std::uint32_t rate = 0;
        std::uint16_t channels = 0;
        std::uint16_t bits = 0;
        std::memcpy(&channels, fmt + 2, 2);
        std::memcpy(&rate, fmt + 4, 4);
        std::memcpy(&byteRate, fmt + 8, 4);
        std::memcpy(&bits, fmt + 14, 2);
        if (byteRate == 0 && channels > 0 && bits > 0) {
          byteRate = rate * channels * (bits / 8);
        }
      }
      if (static_cast<std::streamsize>(size) > keep) in.seekg(size - keep, std::ios::cur);
    } else if (chunk == "data") {
      dataSize = size;
      break;
    } else {
      in.seekg(size, std::ios::cur);
    }
  }
  if (byteRate == 0 || dataSize == 0) return 0;
  return static_cast<int>((static_cast<long long>(dataSize) * 1000) / byteRate);
}

TtsEngineInfo detectTtsEngine() {
  // Cache the PATH probe: renderListenQuestion asks every frame while a listen
  // question without audio is open, and spawning a shell per frame is wasteful.
  static bool cached = false;
  static TtsEngineInfo info;
  if (cached) return info;
  cached = true;
#ifdef _WIN32
  if (commandExists("powershell")) {
    info.available = true;
    info.engine = "powershell";
    return info;
  }
  info.error = "未找到 PowerShell，Windows 系统 TTS（System.Speech）不可用";
  return info;
#elif defined(__APPLE__)
  if (commandExists("say")) {
    info.available = true;
    info.engine = "say";
    return info;
  }
  info.error = "未找到 /usr/bin/say，macOS 系统 TTS 不可用";
  return info;
#else
  if (commandExists("espeak-ng")) {
    info.available = true;
    info.engine = "espeak-ng";
    return info;
  }
  if (commandExists("espeak")) {
    info.available = true;
    info.engine = "espeak";
    return info;
  }
  info.error = "未找到 espeak-ng/espeak，请安装系统 TTS（例如 sudo pacman -S espeak-ng）";
  return info;
#endif
}

TtsSynthesisResult synthesizeTts(const std::string& text, const std::string& lang,
                                 double rate, const fs::path& outputWav) {
  TtsSynthesisResult result;
  result.file = outputWav;
  const std::string trimmed = trim(text);
  if (trimmed.empty()) {
    result.error = "TTS 朗读文本为空";
    return result;
  }
  const TtsEngineInfo engine = detectTtsEngine();
  result.engine = engine.engine;
  if (!engine.available) {
    result.error = engine.error;
    std::fprintf(stderr, "FORGE_TTS unavailable %s\n", engine.error.c_str());
    return result;
  }
  std::error_code ec;
  fs::create_directories(outputWav.parent_path(), ec);
  fs::remove(outputWav, ec);
  const std::string tag = normalizeTtsLang(lang);
  const std::string suffix = uniqueSuffix();
  const fs::path textFile = outputWav.parent_path() / (outputWav.stem().string() + ".txt-" + suffix);
  const fs::path logFile = outputWav.parent_path() / (outputWav.stem().string() + ".log-" + suffix);
  if (!writeTextFile(textFile, trimmed)) {
    result.error = "无法写入 TTS 临时文本";
    return result;
  }
  result.voice = espeakVoiceFor(tag);
  const int wpm = wordsPerMinute(rate);
  std::string command;
#ifdef _WIN32
  const fs::path script = outputWav.parent_path() / (outputWav.stem().string() + ".ps1-" + suffix);
  {
    std::ofstream out(script, std::ios::binary | std::ios::trunc);
    out << "Add-Type -AssemblyName System.Speech\n"
        << "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer\n"
        << "$s.Rate = -3\n"
        << "$t = Get-Content -Raw -Encoding UTF8 -LiteralPath '" << textFile.string() << "'\n"
        << "$s.SetOutputToWaveFile('" << outputWav.string() << "')\n"
        << "$s.Speak($t)\n"
        << "$s.Dispose()\n";
  }
  command = "powershell -NoProfile -ExecutionPolicy Bypass -File \"" + script.string() + "\"";
#elif defined(__APPLE__)
  command = "say -r " + std::to_string(wpm) + " -o " + shellQuote(outputWav.string()) +
            " --data-format=LEI16@22050 -f " + shellQuote(textFile.string());
  result.voice = "system-default";
#else
  command = engine.engine + " -v " + shellQuote(result.voice) + " -s " + std::to_string(wpm) +
            " -w " + shellQuote(outputWav.string()) + " -f " + shellQuote(textFile.string());
#endif
  result.command = command;
  std::fprintf(stderr,
               "FORGE_TTS synth engine=%s lang=%s voice=%s rate=%.2f wpm=%d text=%s out=%s\n",
               result.engine.c_str(), tag.c_str(), result.voice.c_str(), rate, wpm,
               trimmed.c_str(), outputWav.string().c_str());
  int status = runCommand(command, logFile);
  // Some engines reject an unknown voice while still being able to speak. Retry
  // once with the engine default voice so a valid language tag never blocks audio.
  if (status != 0 || !fs::exists(outputWav)) {
    const std::string log = readLog(logFile);
    std::fprintf(stderr, "FORGE_TTS retry-default voice=%s status=%d log=%s\n",
                 result.voice.c_str(), status, log.c_str());
#ifdef _WIN32
    // System.Speech retry is identical; keep the failure details.
#elif defined(__APPLE__)
    command = "say -r " + std::to_string(wpm) + " -o " + shellQuote(outputWav.string()) +
              " --data-format=LEI16@22050 -f " + shellQuote(textFile.string());
    result.voice = "system-default";
#else
    command = engine.engine + " -s " + std::to_string(wpm) + " -w " +
              shellQuote(outputWav.string()) + " -f " + shellQuote(textFile.string());
    result.voice = "default";
#endif
    result.command = command;
    status = runCommand(command, logFile);
  }
  const std::string log = readLog(logFile);
  fs::remove(textFile, ec);
  fs::remove(logFile, ec);
#ifdef _WIN32
  fs::remove(script, ec);
#endif
  if (status != 0) {
    result.error = "系统 TTS 合成失败" + (log.empty() ? std::string() : "：" + log);
    std::fprintf(stderr, "FORGE_TTS fail status=%d %s\n", status, result.error.c_str());
    return result;
  }
  std::error_code sizeEc;
  const auto size = fs::exists(outputWav) ? fs::file_size(outputWav, sizeEc) : 0;
  if (size <= 64) {
    result.error = "系统 TTS 没有生成有效音频" + (log.empty() ? std::string() : "：" + log);
    std::fprintf(stderr, "FORGE_TTS fail size=%llu %s\n",
                 static_cast<unsigned long long>(size), result.error.c_str());
    fs::remove(outputWav, ec);
    return result;
  }
  result.durationMs = wavDurationMs(outputWav);
  result.ok = true;
  std::fprintf(stderr, "FORGE_TTS ok engine=%s voice=%s file=%s bytes=%llu durationMs=%d\n",
               result.engine.c_str(), result.voice.c_str(), outputWav.string().c_str(),
               static_cast<unsigned long long>(size), result.durationMs);
  return result;
}

}  // namespace forge
