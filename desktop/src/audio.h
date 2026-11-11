#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include "model.h"

namespace forge {

struct AudioEntry {
  std::string id;
  std::string name;
  std::string file;
  std::string code;
  int duration = 0;
  std::int64_t createdAt = 0;
};

struct AudioStore {
  fs::path projectDirectory;
  std::vector<AudioEntry> audios;

  explicit AudioStore(fs::path projectDir);
  void load();
  bool save() const;
  AudioEntry* find(const std::string& id);
  const AudioEntry* find(const std::string& id) const;
  AudioEntry* findByFile(const std::string& relativePath);
  const AudioEntry* findByFile(const std::string& relativePath) const;
  fs::path resolveFile(const std::string& relativePath) const;
  fs::path audioPath(const AudioEntry& audio) const;
  std::vector<fs::path> unregisteredFiles() const;
  bool importAudio(const fs::path& source, const std::string& displayName,
                   AudioEntry& imported, std::string& error);
  AudioEntry* registerFile(const fs::path& file);
  bool setCode(const std::string& id, const std::string& code);
  bool deleteAudio(const std::string& id, std::string& error);
};

bool supportedAudioExtension(const std::string& extension);
std::string audioExtensionLower(const fs::path& path);
bool loadAudioDuration(const fs::path& path, int& durationMs);

}  // namespace forge
