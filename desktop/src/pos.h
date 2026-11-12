#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

#include "model.h"

namespace forge {

struct PosEntry {
  std::string id;
  std::string name;
  std::string description;
  std::string code;
  std::int64_t createdAt = 0;
  std::int64_t updatedAt = 0;
};

struct PosStore {
  fs::path projectDirectory;
  std::vector<PosEntry> entries;

  explicit PosStore(fs::path projectDir);
  void load();
  bool save() const;
  PosEntry* find(const std::string& id);
  const PosEntry* find(const std::string& id) const;
  PosEntry* create(const std::string& name, const std::string& description,
                   const std::string& code);
  bool update(const std::string& id, const std::string& name,
              const std::string& description, const std::string& code);
  bool remove(const std::string& id);
};

}  // namespace forge
