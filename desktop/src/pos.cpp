#include "pos.h"

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

std::string clean(std::string value) {
  auto notSpace = [](unsigned char c) { return !std::isspace(c); };
  value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
  value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
  if (value.size() > 2000) value.resize(2000);
  return value;
}

}  // namespace

PosStore::PosStore(fs::path projectDir) : projectDirectory(std::move(projectDir)) {
  load();
}

void PosStore::load() {
  entries.clear();
  json value = readJson(projectDirectory / "pos.json");
  if (!value.is_object() || value.value("schema", 0) != 1) return;
  if (!value.contains("entries") || !value["entries"].is_array()) return;
  for (const auto& item : value["entries"]) {
    if (!item.is_object()) continue;
    PosEntry entry;
    entry.id = item.value("id", "");
    entry.name = clean(item.value("name", ""));
    entry.description = clean(item.value("description", ""));
    entry.code = clean(item.value("code", ""));
    entry.createdAt = item.value("createdAt", 0LL);
    entry.updatedAt = item.value("updatedAt", entry.createdAt);
    if (entry.id.empty() || entry.name.empty()) continue;
    entries.push_back(std::move(entry));
  }
}

bool PosStore::save() const {
  json root = {{"schema", 1}, {"entries", json::array()}};
  for (const auto& entry : entries) {
    root["entries"].push_back({{"id", entry.id},
                               {"name", entry.name},
                               {"description", entry.description},
                               {"code", entry.code},
                               {"createdAt", entry.createdAt},
                               {"updatedAt", entry.updatedAt}});
  }
  return writeJsonAtomic(projectDirectory / "pos.json", root);
}

PosEntry* PosStore::find(const std::string& id) {
  for (auto& entry : entries) if (entry.id == id) return &entry;
  return nullptr;
}

const PosEntry* PosStore::find(const std::string& id) const {
  for (const auto& entry : entries) if (entry.id == id) return &entry;
  return nullptr;
}

PosEntry* PosStore::create(const std::string& name, const std::string& description,
                           const std::string& code) {
  const std::string cleanName = clean(name);
  if (cleanName.empty()) return nullptr;
  PosEntry entry;
  entry.id = Store::newUuid();
  entry.name = cleanName;
  entry.description = clean(description);
  entry.code = clean(code);
  entry.createdAt = entry.updatedAt = nowMillis();
  entries.push_back(entry);
  if (!save()) {
    entries.pop_back();
    return nullptr;
  }
  return &entries.back();
}

bool PosStore::update(const std::string& id, const std::string& name,
                      const std::string& description, const std::string& code) {
  PosEntry* entry = find(id);
  const std::string cleanName = clean(name);
  if (!entry || cleanName.empty()) return false;
  PosEntry backup = *entry;
  entry->name = cleanName;
  entry->description = clean(description);
  entry->code = clean(code);
  entry->updatedAt = nowMillis();
  if (!save()) {
    *entry = backup;
    return false;
  }
  return true;
}

bool PosStore::remove(const std::string& id) {
  auto it = std::find_if(entries.begin(), entries.end(),
                         [&](const PosEntry& entry) { return entry.id == id; });
  if (it == entries.end()) return false;
  PosEntry backup = *it;
  entries.erase(it);
  if (!save()) {
    entries.push_back(backup);
    return false;
  }
  return true;
}

}  // namespace forge
