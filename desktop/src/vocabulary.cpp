#include "vocabulary.h"

#include <SDL_ttf.h>

#include <algorithm>
#include <cctype>
#include <chrono>
#include <fstream>
#include <random>
#include <sstream>

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

std::string extensionLower(const fs::path& path) {
  std::string ext = utf8(path.extension());
  std::transform(ext.begin(), ext.end(), ext.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return ext;
}

bool supportedFontExtension(const std::string& ext) {
  return ext == ".ttf" || ext == ".otf" || ext == ".ttc";
}

std::vector<std::string> stringArray(const json& value) {
  std::vector<std::string> result;
  if (value.is_array()) {
    for (const auto& item : value) {
      if (item.is_string()) result.push_back(item.get<std::string>());
    }
  }
  return result;
}

VocabEntry entryFromJson(const json& value) {
  VocabEntry entry;
  entry.id = value.value("id", Store::newUuid());
  entry.type = value.value("type", "text");
  if (!typeIsKnown(entry.type)) entry.type = "text";
  entry.meanings = stringArray(value.value("meanings", json::array()));
  if (value.contains("posMeanings") && value["posMeanings"].is_array()) {
    for (const auto& group : value["posMeanings"]) {
      if (!group.is_object()) continue;
      PosMeaning posMeaning;
      posMeaning.posId = group.value("posId", "");
      posMeaning.meanings = stringArray(group.value("meanings", json::array()));
      if (!posMeaning.posId.empty()) entry.posMeanings.push_back(posMeaning);
    }
  }
  entry.codes = stringArray(value.value("codes", json::array()));
  entry.text = value.value("text", "");
  entry.codepoints = stringArray(value.value("codepoints", json::array()));
  entry.parts = stringArray(value.value("parts", json::array()));
  entry.image = value.value("image", "");
  entry.imageWidth = value.value("imageWidth", 0);
  entry.imageHeight = value.value("imageHeight", 0);
  entry.audio = value.value("audio", "");
  entry.audioStart = value.value("audioStart", 0.0);
  entry.audioEnd = value.value("audioEnd", 0.0);
  entry.audioDuration = value.value("audioDuration", 0.0);
  entry.createdAt = value.value("createdAt", nowMillis());
  entry.updatedAt = value.value("updatedAt", entry.createdAt);
  for (auto& cp : entry.codepoints) cp = normalizeCodepoint(cp);
  entry.codepoints.erase(
      std::remove_if(entry.codepoints.begin(), entry.codepoints.end(),
                     [](const std::string& cp) { return cp.empty(); }),
      entry.codepoints.end());
  if (entry.meanings.empty()) entry.meanings.push_back("");
  if (entry.codes.empty()) entry.codes.push_back("");
  return entry;
}

json entryToJson(const VocabEntry& entry) {
  json posArray = json::array();
  for (const auto& group : entry.posMeanings) {
    if (group.posId.empty()) continue;
    posArray.push_back({{"posId", group.posId}, {"meanings", group.meanings}});
  }
  return json{
      {"id", entry.id},
      {"type", entry.type},
      {"meanings", entry.meanings},
      {"posMeanings", posArray},
      {"codes", entry.codes},
      {"text", entry.text},
      {"codepoints", entry.codepoints},
      {"parts", entry.parts},
      {"image", entry.image},
      {"imageWidth", entry.imageWidth},
      {"imageHeight", entry.imageHeight},
      {"audio", entry.audio},
      {"audioStart", entry.audioStart},
      {"audioEnd", entry.audioEnd},
      {"audioDuration", entry.audioDuration},
      {"createdAt", entry.createdAt},
      {"updatedAt", entry.updatedAt},
  };
}

}  // namespace

bool typeIsKnown(const std::string& type) {
  return type == "text" || type == "unicode" || type == "combo" || type == "image";
}

std::string normalizeCodepoint(const std::string& raw) {
  std::string value = trim(raw);
  if (value.empty()) return {};
  if (value.size() > 2 && value[0] == '0' && (value[1] == 'x' || value[1] == 'X')) {
    value = value.substr(2);
  } else if (value.size() > 2 && (value[0] == 'U' || value[0] == 'u') && value[1] == '+') {
    value = value.substr(2);
  }
  if (value.empty()) return {};
  for (char c : value) {
    if (!std::isxdigit(static_cast<unsigned char>(c))) return {};
  }
  if (value.size() > 6) return {};
  unsigned long number = 0;
  try {
    number = std::stoul(value, nullptr, 16);
  } catch (...) {
    return {};
  }
  if (number == 0 || number > 0x10FFFF || (number >= 0xD800 && number <= 0xDFFF)) {
    return {};
  }
  std::ostringstream out;
  out << std::hex << std::nouppercase << number;
  return out.str();
}

std::string codepointsToUtf8(const std::vector<std::string>& codepoints) {
  std::string result;
  for (const auto& raw : codepoints) {
    const std::string cp = normalizeCodepoint(raw);
    if (cp.empty()) continue;
    unsigned long code = 0;
    try {
      code = std::stoul(cp, nullptr, 16);
    } catch (...) {
      continue;
    }
    if (code <= 0x7F) {
      result.push_back(static_cast<char>(code));
    } else if (code <= 0x7FF) {
      result.push_back(static_cast<char>(0xC0 | (code >> 6)));
      result.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else if (code <= 0xFFFF) {
      result.push_back(static_cast<char>(0xE0 | (code >> 12)));
      result.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
      result.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    } else {
      result.push_back(static_cast<char>(0xF0 | (code >> 18)));
      result.push_back(static_cast<char>(0x80 | ((code >> 12) & 0x3F)));
      result.push_back(static_cast<char>(0x80 | ((code >> 6) & 0x3F)));
      result.push_back(static_cast<char>(0x80 | (code & 0x3F)));
    }
  }
  return result;
}

std::string displayName(const VocabEntry& entry, const std::vector<VocabEntry>& all,
                        int depth) {
  if (depth > 8) return "组合循环";
  if (entry.type == "text") {
    if (!entry.text.empty()) return entry.text;
  } else if (entry.type == "unicode") {
    const std::string characters = codepointsToUtf8(entry.codepoints);
    if (!characters.empty()) return characters;
  } else if (entry.type == "combo") {
    std::string combined;
    for (const auto& partId : entry.parts) {
      const VocabEntry* part = nullptr;
      for (const auto& candidate : all) {
        if (candidate.id == partId) {
          part = &candidate;
          break;
        }
      }
      if (part && part->id != entry.id) {
        combined += displayName(*part, all, depth + 1);
      }
    }
    if (!combined.empty()) return combined;
  } else if (entry.type == "image") {
    if (!entry.meanings.empty() && !entry.meanings.front().empty()) {
      return entry.meanings.front();
    }
  }
  if (!entry.meanings.empty() && !entry.meanings.front().empty()) {
    return entry.meanings.front();
  }
  if (!entry.codes.empty() && !entry.codes.front().empty()) {
    return entry.codes.front();
  }
  return "未命名词汇";
}

VocabularyStore::VocabularyStore(fs::path projectDir)
    : projectDirectory(std::move(projectDir)) {
  load();
}

void VocabularyStore::load() {
  entries.clear();
  json value = readJson(projectDirectory / "vocabulary.json");
  if (!value.is_object() || value.value("schema", 0) != 1) return;
  if (!value.contains("entries") || !value["entries"].is_array()) return;
  for (const auto& item : value["entries"]) {
    if (!item.is_object()) continue;
    VocabEntry entry = entryFromJson(item);
    if (entry.id.empty()) continue;
    entries.push_back(std::move(entry));
  }
}

bool VocabularyStore::save() const {
  json root = {{"schema", 1}, {"entries", json::array()}};
  for (const auto& entry : entries) root["entries"].push_back(entryToJson(entry));
  return writeJsonAtomic(projectDirectory / "vocabulary.json", root);
}

VocabEntry* VocabularyStore::find(const std::string& id) {
  for (auto& entry : entries) if (entry.id == id) return &entry;
  return nullptr;
}

const VocabEntry* VocabularyStore::find(const std::string& id) const {
  for (const auto& entry : entries) if (entry.id == id) return &entry;
  return nullptr;
}

FontStore::FontStore(fs::path projectDir) : projectDirectory(std::move(projectDir)) {
  load();
}

void FontStore::load() {
  fonts.clear();
  active.clear();
  json value = readJson(projectDirectory / "fonts.json");
  if (!value.is_object() || value.value("schema", 0) != 1) return;
  active = value.value("active", "");
  if (!value.contains("fonts") || !value["fonts"].is_array()) return;
  for (const auto& item : value["fonts"]) {
    if (!item.is_object()) continue;
    FontEntry font;
    font.id = item.value("id", "");
    font.name = item.value("name", "");
    font.file = item.value("file", "");
    font.createdAt = item.value("createdAt", nowMillis());
    if (font.id.empty() || font.file.empty()) continue;
    fonts.push_back(std::move(font));
  }
}

bool FontStore::save() const {
  json root = {{"schema", 1}, {"active", active}, {"fonts", json::array()}};
  for (const auto& font : fonts) {
    root["fonts"].push_back({{"id", font.id},
                             {"name", font.name},
                             {"file", font.file},
                             {"createdAt", font.createdAt}});
  }
  return writeJsonAtomic(projectDirectory / "fonts.json", root);
}

const FontEntry* FontStore::find(const std::string& id) const {
  for (const auto& font : fonts) if (font.id == id) return &font;
  return nullptr;
}

FontEntry* FontStore::find(const std::string& id) {
  for (auto& font : fonts) if (font.id == id) return &font;
  return nullptr;
}

fs::path FontStore::fontPath(const FontEntry& font) const {
  if (font.file.empty()) return {};
  fs::path path = (projectDirectory / pathFromUtf8(font.file)).lexically_normal();
  fs::path relative = path.lexically_relative(projectDirectory.lexically_normal());
  if (relative.empty() || relative.is_absolute()) return {};
  for (const auto& part : relative) {
    if (part == "..") return {};
  }
  return path;
}

fs::path FontStore::activeFilePath() const {
  const FontEntry* font = find(active);
  if (!font) return {};
  return fontPath(*font);
}

bool FontStore::activeFileValid() const {
  const fs::path file = activeFilePath();
  if (file.empty() || !fs::exists(file)) return false;
  if (!TTF_WasInit() && TTF_Init() != 0) return false;
  TTF_Font* font = TTF_OpenFontIndex(utf8(file).c_str(), 24, 0);
  if (!font) return false;
  TTF_CloseFont(font);
  return true;
}

bool FontStore::importFont(const fs::path& source, const std::string& displayNameValue,
                           FontEntry& imported, std::string& error) {
  if (!fs::exists(source)) {
    error = "字体文件不存在";
    return false;
  }
  const std::string ext = extensionLower(source);
  if (!supportedFontExtension(ext)) {
    error = "仅支持 TTF/OTF/TTC";
    return false;
  }
  if (!TTF_WasInit() && TTF_Init() != 0) {
    error = "SDL_ttf 初始化失败";
    return false;
  }
  TTF_Font* probe = TTF_OpenFontIndex(utf8(source).c_str(), 24, 0);
  if (!probe) {
    error = "字体无法解析";
    return false;
  }
  TTF_CloseFont(probe);

  std::error_code ec;
  fs::path directory = projectDirectory / "resources" / "fonts";
  fs::create_directories(directory, ec);
  if (ec) {
    error = "无法创建字体目录";
    return false;
  }
  std::string id = Store::newUuid();
  std::string safeId = id;
  safeId.erase(std::remove(safeId.begin(), safeId.end(), '-'), safeId.end());
  fs::path target = directory / ("font_" + safeId + "_" +
                                 std::to_string(nowMillis()) + ext);
  fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    error = "复制字体失败";
    return false;
  }
  TTF_Font* check = TTF_OpenFontIndex(utf8(target).c_str(), 24, 0);
  if (!check) {
    fs::remove(target, ec);
    error = "字体无法解析";
    return false;
  }
  TTF_CloseFont(check);

  imported.id = id;
  imported.name = trim(displayNameValue).empty() ? utf8(source.filename())
                                                 : trim(displayNameValue);
  imported.file = "resources/fonts/" + utf8(target.filename());
  imported.createdAt = nowMillis();
  fonts.push_back(imported);
  if (!save()) {
    fonts.pop_back();
    fs::remove(target, ec);
    error = "写入 fonts.json 失败";
    return false;
  }
  return true;
}

bool FontStore::deleteFont(const std::string& id, std::string& error) {
  auto it = std::find_if(fonts.begin(), fonts.end(),
                         [&](const FontEntry& font) { return font.id == id; });
  if (it == fonts.end()) {
    error = "字体不存在";
    return false;
  }
  fs::path file = fontPath(*it);
  if (active == id) active.clear();
  fonts.erase(it);
  if (!save()) {
    error = "写入 fonts.json 失败";
    load();
    return false;
  }
  std::error_code ec;
  if (!file.empty()) fs::remove(file, ec);
  return true;
}

bool FontStore::setActive(const std::string& id) {
  if (!id.empty() && !find(id)) return false;
  active = id;
  return save();
}

}  // namespace forge
