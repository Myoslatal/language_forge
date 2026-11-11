#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include "model.h"

namespace forge {

struct VocabEntry {
  std::string id;
  std::string type = "text";
  std::vector<std::string> meanings;
  std::vector<std::string> codes;
  std::string text;
  std::vector<std::string> codepoints;
  std::vector<std::string> parts;
  std::string image;
  int imageWidth = 0;
  int imageHeight = 0;
  std::string audio;
  double audioStart = 0.0;
  double audioEnd = 0.0;
  double audioDuration = 0.0;
  std::int64_t createdAt = 0;
  std::int64_t updatedAt = 0;
};

struct VocabularyStore {
  fs::path projectDirectory;
  std::vector<VocabEntry> entries;

  explicit VocabularyStore(fs::path projectDir);
  void load();
  bool save() const;
  VocabEntry* find(const std::string& id);
  const VocabEntry* find(const std::string& id) const;
};

struct FontEntry {
  std::string id;
  std::string name;
  std::string file;
  std::int64_t createdAt = 0;
};

struct FontStore {
  fs::path projectDirectory;
  std::vector<FontEntry> fonts;
  std::string active;

  explicit FontStore(fs::path projectDir);
  void load();
  bool save() const;
  const FontEntry* find(const std::string& id) const;
  FontEntry* find(const std::string& id);
  bool importFont(const fs::path& source, const std::string& displayName,
                  FontEntry& imported, std::string& error);
  bool deleteFont(const std::string& id, std::string& error);
  bool setActive(const std::string& id);
  bool activeFileValid() const;
  fs::path activeFilePath() const;
  fs::path fontPath(const FontEntry& font) const;
};

std::string normalizeCodepoint(const std::string& raw);
std::string codepointsToUtf8(const std::vector<std::string>& codepoints);
std::string displayName(const VocabEntry& entry, const std::vector<VocabEntry>& all,
                        int depth = 0);
bool typeIsKnown(const std::string& type);

}  // namespace forge
