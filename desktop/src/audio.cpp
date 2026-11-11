#include "audio.h"

#include <SDL_mixer.h>

#include <algorithm>
#include <cctype>
#include <chrono>
#include <fstream>
#include <random>

#include "nlohmann/json.hpp"

namespace forge {
namespace {

using json = nlohmann::json;

std::int64_t nowMillis() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

json readJson(const fs::path& file) {
  std::ifstream input(file, std::ios::binary);
  if (!input) return json();
  try {
    json value;
    input >> value;
    return value;
  } catch (...) {
    return json();
  }
}

bool writeJsonAtomic(const fs::path& file, const json& value) {
  std::error_code ec;
  fs::create_directories(file.parent_path(), ec);
  fs::path temp = file;
  temp += ".tmp-" + std::to_string(std::random_device{}());
  {
    std::ofstream output(temp, std::ios::binary | std::ios::trunc);
    if (!output) return false;
    const std::string text = value.dump(2);
    output.write(text.data(), static_cast<std::streamsize>(text.size()));
    if (!output) {
      output.close();
      fs::remove(temp, ec);
      return false;
    }
  }
  fs::rename(temp, file, ec);
  if (ec) {
    std::error_code ignored;
    fs::remove(file, ignored);
    fs::rename(temp, file, ec);
  }
  if (ec) {
    fs::remove(temp, ec);
    return false;
  }
  return true;
}

std::string trim(std::string value) {
  auto notSpace = [](unsigned char c) { return !std::isspace(c); };
  value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
  value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
  return value;
}

}  // namespace

bool supportedAudioExtension(const std::string& extension) {
  return extension == ".wav" || extension == ".mp3" || extension == ".ogg" ||
         extension == ".m4a" || extension == ".flac";
}

std::string audioExtensionLower(const fs::path& path) {
  std::string ext = utf8(path.extension());
  std::transform(ext.begin(), ext.end(), ext.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return ext;
}

bool loadAudioDuration(const fs::path& path, int& durationMs) {
  durationMs = 0;
  if (!fs::exists(path)) return false;
  Mix_Init(MIX_INIT_MP3 | MIX_INIT_OGG | MIX_INIT_FLAC);
  Mix_Music* music = Mix_LoadMUS(utf8(path).c_str());
  if (!music) return false;
  const double seconds = Mix_MusicDuration(music);
  Mix_FreeMusic(music);
  if (seconds > 0.0) durationMs = static_cast<int>(seconds * 1000.0 + 0.5);
  return true;
}

AudioStore::AudioStore(fs::path projectDir) : projectDirectory(std::move(projectDir)) {
  load();
}

void AudioStore::load() {
  audios.clear();
  json value = readJson(projectDirectory / "audios.json");
  if (!value.is_object() || value.value("schema", 0) != 1) return;
  if (!value.contains("audios") || !value["audios"].is_array()) return;
  for (const auto& item : value["audios"]) {
    if (!item.is_object()) continue;
    AudioEntry entry;
    entry.id = item.value("id", "");
    entry.name = item.value("name", "");
    entry.file = item.value("file", "");
    entry.code = item.value("code", "");
    entry.duration = item.value("duration", 0);
    entry.createdAt = item.value("createdAt", nowMillis());
    if (entry.id.empty() || entry.file.empty()) continue;
    if (resolveFile(entry.file).empty()) continue;
    audios.push_back(std::move(entry));
  }
}

bool AudioStore::save() const {
  json root = {{"schema", 1}, {"audios", json::array()}};
  for (const auto& audio : audios) {
    root["audios"].push_back({{"id", audio.id},
                              {"name", audio.name},
                              {"file", audio.file},
                              {"code", audio.code},
                              {"duration", audio.duration},
                              {"createdAt", audio.createdAt}});
  }
  return writeJsonAtomic(projectDirectory / "audios.json", root);
}

AudioEntry* AudioStore::find(const std::string& id) {
  for (auto& audio : audios) if (audio.id == id) return &audio;
  return nullptr;
}

const AudioEntry* AudioStore::find(const std::string& id) const {
  for (const auto& audio : audios) if (audio.id == id) return &audio;
  return nullptr;
}

AudioEntry* AudioStore::findByFile(const std::string& relativePath) {
  for (auto& audio : audios) if (audio.file == relativePath) return &audio;
  return nullptr;
}

const AudioEntry* AudioStore::findByFile(const std::string& relativePath) const {
  for (const auto& audio : audios) if (audio.file == relativePath) return &audio;
  return nullptr;
}

fs::path AudioStore::resolveFile(const std::string& relativePath) const {
  if (relativePath.empty()) return {};
  fs::path path = (projectDirectory / pathFromUtf8(relativePath)).lexically_normal();
  fs::path relative = path.lexically_relative(projectDirectory.lexically_normal());
  if (relative.empty() || relative.is_absolute()) return {};
  for (const auto& part : relative) {
    if (part == "..") return {};
  }
  return path;
}

fs::path AudioStore::audioPath(const AudioEntry& audio) const {
  return resolveFile(audio.file);
}

std::vector<fs::path> AudioStore::unregisteredFiles() const {
  std::vector<fs::path> result;
  fs::path directory = projectDirectory / "resources" / "audio";
  std::error_code ec;
  if (!fs::is_directory(directory, ec)) return result;
  for (const auto& entry : fs::directory_iterator(directory, ec)) {
    if (ec) break;
    if (!entry.is_regular_file()) continue;
    if (!supportedAudioExtension(audioExtensionLower(entry.path()))) continue;
    fs::path relative = entry.path().lexically_relative(projectDirectory);
    if (relative.empty()) continue;
    if (!findByFile(relative.generic_string())) result.push_back(entry.path());
  }
  std::sort(result.begin(), result.end());
  return result;
}

bool AudioStore::importAudio(const fs::path& source, const std::string& displayName,
                             AudioEntry& imported, std::string& error) {
  if (!fs::exists(source)) {
    error = "音频文件不存在";
    return false;
  }
  const std::string ext = audioExtensionLower(source);
  if (!supportedAudioExtension(ext)) {
    error = "仅支持 WAV/MP3/OGG/M4A/FLAC";
    return false;
  }
  int duration = 0;
  if (!loadAudioDuration(source, duration)) {
    error = "音频无法解码";
    return false;
  }
  std::error_code ec;
  fs::path directory = projectDirectory / "resources" / "audio";
  fs::create_directories(directory, ec);
  if (ec) {
    error = "无法创建音频目录";
    return false;
  }
  std::string id = Store::newUuid();
  std::string safeId = id;
  safeId.erase(std::remove(safeId.begin(), safeId.end(), '-'), safeId.end());
  fs::path target = directory / ("aud_" + safeId + "_" +
                                 std::to_string(nowMillis()) + ext);
  fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    error = "复制音频失败";
    return false;
  }
  int checkDuration = 0;
  if (!loadAudioDuration(target, checkDuration)) {
    fs::remove(target, ec);
    error = "复制后的音频无法解码";
    return false;
  }
  imported.id = id;
  imported.name = trim(displayName).empty() ? utf8(source.filename()) : trim(displayName);
  imported.file = "resources/audio/" + utf8(target.filename());
  imported.duration = checkDuration;
  imported.createdAt = nowMillis();
  audios.push_back(imported);
  if (!save()) {
    audios.pop_back();
    fs::remove(target, ec);
    error = "写入 audios.json 失败";
    return false;
  }
  return true;
}

AudioEntry* AudioStore::registerFile(const fs::path& file) {
  if (!fs::is_regular_file(file)) return nullptr;
  fs::path relative = file.lexically_relative(projectDirectory);
  if (relative.empty()) return nullptr;
  std::string relativeText = relative.generic_string();
  if (AudioEntry* existing = findByFile(relativeText)) return existing;
  int duration = 0;
  if (!loadAudioDuration(file, duration)) return nullptr;
  AudioEntry entry;
  entry.id = Store::newUuid();
  entry.name = utf8(file.filename());
  entry.file = relativeText;
  entry.duration = duration;
  entry.createdAt = nowMillis();
  audios.push_back(entry);
  if (!save()) {
    audios.pop_back();
    return nullptr;
  }
  return &audios.back();
}

bool AudioStore::setCode(const std::string& id, const std::string& code) {
  AudioEntry* entry = find(id);
  if (!entry) return false;
  entry->code = trim(code);
  if (!save()) {
    load();
    return false;
  }
  return true;
}

bool AudioStore::deleteAudio(const std::string& id, std::string& error) {
  auto it = std::find_if(audios.begin(), audios.end(),
                         [&](const AudioEntry& audio) { return audio.id == id; });
  if (it == audios.end()) {
    error = "音频不存在";
    return false;
  }
  fs::path file = audioPath(*it);
  audios.erase(it);
  if (!save()) {
    load();
    error = "写入 audios.json 失败";
    return false;
  }
  std::error_code ec;
  if (!file.empty()) fs::remove(file, ec);
  return true;
}

}  // namespace forge
