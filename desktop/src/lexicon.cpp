#include "lexicon.h"

#include "nlohmann/json.hpp"
#include "pos.h"
#include "vocabulary.h"

#include <algorithm>
#include <cctype>
#include <fstream>
#include <map>
#include <set>
#include <string>
#include <utility>
#include <vector>

namespace fs = std::filesystem;
using json = nlohmann::json;
using namespace forge;

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

std::string jsonText(const json& value, const char* key) {
  if (!value.contains(key) || !value[key].is_string()) return "";
  return trim(value[key].get<std::string>());
}

void addMeaning(std::vector<std::string>& values, const std::string& value) {
  std::string clean = trim(value);
  if (clean.size() > 200) {
    std::size_t cut = 200;
    while (cut > 0 && (static_cast<unsigned char>(clean[cut]) & 0xC0) == 0x80) --cut;
    clean.resize(cut);
  }
  if (!clean.empty() && std::find(values.begin(), values.end(), clean) == values.end()) {
    values.push_back(clean);
  }
}

}  // namespace

bool importLexiconJsonl(const fs::path& file, const fs::path& projectDirectory,
                        LexiconImportMode mode, LexiconImportSummary& summary,
                        std::string& error) {
  summary = LexiconImportSummary{};
  std::ifstream input(file, std::ios::binary);
  if (!input) {
    error = "无法打开词表文件";
    return false;
  }

  std::vector<std::string> lines;
  std::string line;
  bool first = true;
  while (std::getline(input, line)) {
    if (first) {
      first = false;
      if (line.size() >= 3 && static_cast<unsigned char>(line[0]) == 0xEF &&
          static_cast<unsigned char>(line[1]) == 0xBB &&
          static_cast<unsigned char>(line[2]) == 0xBF) {
        line.erase(0, 3);
      }
    }
    lines.push_back(line);
  }

  PosStore pos(projectDirectory);
  VocabularyStore vocabulary(projectDirectory);
  std::vector<std::pair<std::string, std::string>> metaTypes;
  std::vector<std::string> entryLines;
  for (const auto& raw : lines) {
    const std::string text = trim(raw);
    if (text.empty()) continue;
    if (text.find("\"_meta\"") != std::string::npos) {
      try {
        json value = json::parse(text);
        if (value.contains("_meta") && value["_meta"].is_object()) {
          if (metaTypes.empty() && value["_meta"].contains("types") &&
              value["_meta"]["types"].is_array()) {
            for (const auto& type : value["_meta"]["types"]) {
              if (!type.is_object()) continue;
              const std::string code = jsonText(type, "k");
              const std::string label = jsonText(type, "label");
              if (code.empty() && label.empty()) continue;
              metaTypes.emplace_back(code.empty() ? label : code,
                                     label.empty() ? code : label);
            }
          }
          continue;
        }
      } catch (...) {
        // Count as invalid entry below.
      }
    }
    entryLines.push_back(text);
  }

  auto findCode = [&](const std::string& code) -> PosEntry* {
    if (code.empty()) return nullptr;
    for (auto& item : pos.entries) {
      if (lower(item.code) == lower(code)) return &item;
    }
    return nullptr;
  };
  auto findName = [&](const std::string& name) -> PosEntry* {
    for (auto& item : pos.entries) {
      if (lower(item.name) == lower(name)) return &item;
    }
    return nullptr;
  };

  std::vector<std::pair<std::string, std::string>> posIds;
  for (const auto& meta : metaTypes) {
    PosEntry* existing = findCode(meta.first);
    if (!existing) existing = findName(meta.second);
    if (!existing) {
      PosEntry* created = pos.create(meta.second, "Lexicon 词表导入", meta.first);
      if (created) {
        summary.newPos++;
        posIds.emplace_back(meta.first, created->id);
      }
    } else {
      posIds.emplace_back(meta.first, existing->id);
    }
    pos.load();
  }

  std::map<std::string, VocabEntry*> existingByText;
  for (auto& entry : vocabulary.entries) {
    existingByText.emplace(lower(trim(entry.text)), &entry);
  }
  std::set<std::string> seen;
  vocabulary.entries.reserve(vocabulary.entries.size() + entryLines.size());

  for (const auto& text : entryLines) {
    json value;
    try {
      value = json::parse(text);
    } catch (...) {
      summary.invalid++;
      continue;
    }
    if (value.contains("_meta")) continue;
    std::string word = jsonText(value, "rendered");
    if (word.empty() && value.contains("entry") && value["entry"].is_string()) {
      word = trim(value["entry"].get<std::string>());
    }
    if (word.empty()) {
      summary.invalid++;
      continue;
    }
    const std::string normalized = lower(word);
    if (!seen.insert(normalized).second) {
      summary.duplicates++;
      continue;
    }
    std::vector<std::string> meanings;
    addMeaning(meanings, jsonText(value, "definition"));
    addMeaning(meanings, jsonText(value, "detail"));
    if (meanings.empty()) {
      summary.invalid++;
      continue;
    }

    const std::string type = trim(jsonText(value, "type"));
    std::string posId;
    for (const auto& item : posIds) {
      if (lower(item.first) == lower(type)) {
        posId = item.second;
        break;
      }
    }
    auto existingIt = existingByText.find(normalized);
    if (existingIt != existingByText.end()) {
      if (mode == LexiconImportMode::Skip) {
        summary.duplicates++;
        continue;
      }
      VocabEntry* existing = existingIt->second;
      const std::string pronunciation = trim(jsonText(value, "pronunciation"));
      if (!pronunciation.empty()) existing->phonetic = pronunciation;
      if (!posId.empty()) {
        PosMeaning* target = nullptr;
        for (auto& group : existing->posMeanings) {
          if (group.posId == posId) {
            target = &group;
            break;
          }
        }
        if (!target) {
          PosMeaning created;
          created.posId = posId;
          existing->posMeanings.push_back(std::move(created));
          target = &existing->posMeanings.back();
        }
        target->meanings = meanings;
      }
      if (!existing->posMeanings.empty()) {
        std::vector<std::string> aggregate;
        for (const auto& group : existing->posMeanings) {
          for (const auto& meaning : group.meanings) {
            if (std::find(aggregate.begin(), aggregate.end(), meaning) == aggregate.end()) {
              aggregate.push_back(meaning);
            }
          }
        }
        existing->meanings = std::move(aggregate);
      } else {
        existing->meanings = meanings;
      }
      existing->updatedAt = 1794401000000 + summary.updated;
      summary.updated++;
      continue;
    }

    VocabEntry entry;
    entry.id = Store::newUuid();
    entry.type = "text";
    entry.text = word;
    entry.phonetic = trim(jsonText(value, "pronunciation"));
    entry.meanings = meanings;
    entry.createdAt = entry.updatedAt = 1794401000000 + summary.added;
    if (!posId.empty()) {
      PosMeaning group;
      group.posId = posId;
      group.meanings = meanings;
      entry.posMeanings.push_back(std::move(group));
    }
    vocabulary.entries.push_back(std::move(entry));
    summary.added++;

  }

  if (summary.newPos > 0 && !pos.save()) {
    error = "无法写入 pos.json";
    return false;
  }
  if (!vocabulary.save()) {
    error = "无法写入 vocabulary.json";
    return false;
  }
  return true;
}
