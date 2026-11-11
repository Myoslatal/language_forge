#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include "model.h"

namespace forge {

struct ImageEntry {
  std::string id;
  std::string name;
  std::string file;
  std::string code;
  int width = 0;
  int height = 0;
  std::int64_t createdAt = 0;
};

struct ImageStore {
  fs::path projectDirectory;
  std::vector<ImageEntry> images;

  explicit ImageStore(fs::path projectDir);
  void load();
  bool save() const;
  ImageEntry* find(const std::string& id);
  const ImageEntry* find(const std::string& id) const;
  ImageEntry* findByFile(const std::string& relativePath);
  const ImageEntry* findByFile(const std::string& relativePath) const;
  fs::path resolveFile(const std::string& relativePath) const;
  std::vector<fs::path> unregisteredFiles() const;
  bool importImage(const fs::path& source, const std::string& displayName,
                   ImageEntry& imported, std::string& error);
  ImageEntry* registerFile(const fs::path& file);
  bool setCode(const std::string& id, const std::string& code);
  bool deleteImage(const std::string& id, std::string& error);
  fs::path imagePath(const ImageEntry& image) const;
};

bool supportedImageExtension(const std::string& extension);
std::string imageExtensionLower(const fs::path& path);
bool loadImageSize(const fs::path& path, int& width, int& height);

}  // namespace forge
