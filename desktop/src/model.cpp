#include "model.h"

#ifdef _WIN32
#ifndef NOMINMAX
#define NOMINMAX
#endif
#ifndef WIN32_LEAN_AND_MEAN
#define WIN32_LEAN_AND_MEAN
#endif
#include <windows.h>
#include <shlobj.h>
#endif

#include <algorithm>
#include <chrono>
#include <cctype>
#include <cstdlib>
#include <fstream>
#include <random>
#include <set>
#include <sstream>
#include <system_error>

#include "miniz.h"
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

bool writeBytesAtomic(const fs::path& file, const std::string& bytes) {
  std::error_code ec;
  fs::create_directories(file.parent_path(), ec);
  fs::path temp = file;
  temp += ".tmp-" + std::to_string(std::random_device{}());
  {
    std::ofstream output(temp, std::ios::binary | std::ios::trunc);
    if (!output) return false;
    output.write(bytes.data(), static_cast<std::streamsize>(bytes.size()));
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

bool writeJsonAtomic(const fs::path& file, const json& value) {
  return writeBytesAtomic(file, value.dump(2));
}

std::string readBytes(const fs::path& file) {
  std::ifstream input(file, std::ios::binary);
  if (!input) return {};
  std::ostringstream buffer;
  buffer << input.rdbuf();
  return buffer.str();
}

std::string trim(std::string value) {
  auto notSpace = [](unsigned char c) { return !std::isspace(c); };
  value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
  value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
  return value;
}

bool isSafeZipName(const std::string& raw) {
  if (raw.empty() || raw[0] == '/' || raw[0] == '\\') return false;
  std::string name = raw;
  std::replace(name.begin(), name.end(), '\\', '/');
  if (name.size() >= 2 && std::isalpha(static_cast<unsigned char>(name[0])) &&
      name[1] == ':') {
    return false;
  }
  std::stringstream stream(name);
  std::string part;
  while (std::getline(stream, part, '/')) {
    if (part == "..") return false;
  }
  return true;
}

std::string normalizedZipName(const std::string& raw) {
  std::string name = raw;
  std::replace(name.begin(), name.end(), '\\', '/');
  while (!name.empty() && name.front() == '/') name.erase(name.begin());
  return name;
}

std::string projectJsonString(const ProjectSummary& project) {
  json value = {
      {"schema", 1},
      {"name", project.name},
      {"courseLine", project.courseLine},
      {"lessons", project.lessons},
      {"questions", project.questions},
      {"words", project.words},
      {"updated", project.updated},
      {"createdAt", project.createdAt},
      {"pinned", project.pinned},
  };
  return value.dump(2);
}

json structureToJson(const Structure& structure) {
  json chapters = json::array();
  for (const auto& chapter : structure.chapters) {
    json units = json::array();
    for (const auto& unit : chapter.units) {
      json courses = json::array();
      for (const auto& course : unit.courses) {
        json questions = json::array();
        for (const auto& question : course.questions) {
          questions.push_back({{"id", question.id},
                               {"name", question.name},
                               {"type", question.type},
                               {"parts", question.parts},
                               {"sentence", question.sentence},
                               {"audio", question.audio},
                               {"audioDuration", question.audioDuration},
                               {"options", question.options},
                               {"correctIndex", question.correctIndex}});
        }
        courses.push_back(
            {{"id", course.id}, {"name", course.name}, {"questions", questions}});
      }
      units.push_back({{"id", unit.id}, {"name", unit.name}, {"courses", courses}});
    }
    chapters.push_back({{"id", chapter.id}, {"name", chapter.name}, {"units", units}});
  }
  return {{"schema", 1}, {"chapters", chapters}};
}

Structure structureFromJson(const json& value) {
  Structure structure;
  if (!value.is_object() || !value.contains("chapters") || !value["chapters"].is_array()) {
    return structure;
  }
  for (const auto& chapterValue : value["chapters"]) {
    if (!chapterValue.is_object()) continue;
    Chapter chapter;
    chapter.id = chapterValue.value("id", "");
    chapter.name = chapterValue.value("name", "未命名章节");
    if (chapter.id.empty()) chapter.id = Store::newUuid();
    if (chapter.name.empty()) chapter.name = "未命名章节";
    if (chapterValue.contains("units") && chapterValue["units"].is_array()) {
      for (const auto& unitValue : chapterValue["units"]) {
        if (!unitValue.is_object()) continue;
        Unit unit;
        unit.id = unitValue.value("id", "");
        unit.name = unitValue.value("name", "未命名单元");
        if (unit.id.empty()) unit.id = Store::newUuid();
        if (unit.name.empty()) unit.name = "未命名单元";
        if (unitValue.contains("courses") && unitValue["courses"].is_array()) {
          for (const auto& courseValue : unitValue["courses"]) {
            if (!courseValue.is_object()) continue;
            Course course;
            course.id = courseValue.value("id", "");
            course.name = courseValue.value("name", "未命名课程");
            if (course.id.empty()) course.id = Store::newUuid();
            if (course.name.empty()) course.name = "未命名课程";
            if (courseValue.contains("questions") &&
                courseValue["questions"].is_array()) {
              for (const auto& questionValue : courseValue["questions"]) {
                if (!questionValue.is_object()) continue;
                Question question;
                question.id = questionValue.value("id", "");
                question.name = questionValue.value("name", "未命名题目");
                question.type = questionValue.value("type", "translation_choice");
                question.sentence = questionValue.value("sentence", "");
                question.audio = questionValue.value("audio", "");
                question.audioDuration = questionValue.value("audioDuration", 0LL);
                question.correctIndex = questionValue.value("correctIndex", -1);
                if (questionValue.contains("parts") && questionValue["parts"].is_array()) {
                  for (const auto& part : questionValue["parts"]) {
                    if (part.is_string()) question.parts.push_back(part.get<std::string>());
                  }
                }
                if (questionValue.contains("options") && questionValue["options"].is_array()) {
                  for (const auto& option : questionValue["options"]) {
                    if (option.is_string()) question.options.push_back(option.get<std::string>());
                  }
                }
                if (question.id.empty()) question.id = Store::newUuid();
                if (question.name.empty()) question.name = "未命名题目";
                course.questions.push_back(std::move(question));
              }
            }
            unit.courses.push_back(std::move(course));
          }
        }
        chapter.units.push_back(std::move(unit));
      }
    }
    structure.chapters.push_back(std::move(chapter));
  }
  return structure;
}

ProjectSummary projectFromJson(const fs::path& dir, const json& value) {
  ProjectSummary project;
  project.folder = utf8(dir.filename());
  project.name = value.value("name", "未命名项目");
  project.courseLine = value.value("courseLine", "");
  project.lessons = value.value("lessons", 0);
  project.questions = value.value("questions", 0);
  project.words = value.value("words", 0);
  project.createdAt = value.value("createdAt", nowMillis());
  project.updated = value.value("updated", project.createdAt);
  project.pinned = value.value("pinned", false);
  if (project.name.empty()) project.name = "未命名项目";
  return project;
}

bool loadStructureFile(const fs::path& dir, Structure& structure) {
  json value = readJson(dir / "structure.json");
  structure = structureFromJson(value);
  return value.is_object();
}

}  // namespace

std::string utf8(const fs::path& path) {
#if defined(__cpp_char8_t)
  const std::u8string value = path.u8string();
  return std::string(reinterpret_cast<const char*>(value.data()), value.size());
#else
  return path.u8string();
#endif
}

fs::path pathFromUtf8(const std::string& value) {
#if defined(__cpp_char8_t)
  return fs::path(std::u8string(value.begin(), value.end()));
#else
  return fs::u8path(value);
#endif
}

std::string Store::sanitizeName(const std::string& value) {
  std::string result = trim(value);
  for (char& c : result) {
    unsigned char u = static_cast<unsigned char>(c);
    if (c == '/' || c == '\\' || c == ':' || c == '*' || c == '?' ||
        c == '"' || c == '<' || c == '>' || c == '|' || u < 0x20) {
      c = '_';
    }
  }
  if (result.empty()) result = "项目";
  if (result.size() > 48) {
    result.resize(48);
    while (!result.empty() && static_cast<unsigned char>(result.back()) >= 0x80) {
      result.pop_back();
    }
    if (result.empty()) result = "项目";
  }
  return result;
}

std::string Store::randomSuffix() {
  static std::mt19937_64 engine(std::random_device{}());
  std::uniform_int_distribution<int> distribution(0, 15);
  const char* digits = "0123456789abcdef";
  std::string result;
  for (int i = 0; i < 6; ++i) result.push_back(digits[distribution(engine)]);
  return result;
}

std::string Store::newUuid() {
  static std::mt19937_64 engine(std::random_device{}());
  std::uniform_int_distribution<int> distribution(0, 255);
  unsigned char bytes[16];
  for (auto& byte : bytes) byte = static_cast<unsigned char>(distribution(engine));
  bytes[6] = static_cast<unsigned char>((bytes[6] & 0x0F) | 0x40);
  bytes[8] = static_cast<unsigned char>((bytes[8] & 0x3F) | 0x80);
  const char* digits = "0123456789abcdef";
  std::string value;
  for (int i = 0; i < 16; ++i) {
    if (i == 4 || i == 6 || i == 8 || i == 10) value.push_back('-');
    value.push_back(digits[bytes[i] >> 4]);
    value.push_back(digits[bytes[i] & 0x0F]);
  }
  return value;
}

fs::path Store::defaultDataRoot() {
#ifdef _WIN32
  wchar_t documents[MAX_PATH] = {};
  if (SUCCEEDED(SHGetFolderPathW(nullptr, CSIDL_PERSONAL, nullptr, SHGFP_TYPE_CURRENT,
                                 documents))) {
    return fs::path(documents) / "Nostongue Forge" / "projects";
  }
  const wchar_t* home = _wgetenv(L"USERPROFILE");
  if (home && *home) {
    return fs::path(home) / "Documents" / "Nostongue Forge" / "projects";
  }
  return {};
#else
  const char* home = std::getenv("HOME");
  if (!home || !*home) return {};
  return pathFromUtf8(home) / "Documents" / "Nostongue Forge" / "projects";
#endif
}

Store::Store(fs::path root) : dataRoot(std::move(root)) { ensureRoot(); }

void Store::ensureRoot() const {
  std::error_code ec;
  fs::create_directories(dataRoot, ec);
}

fs::path Store::projectDirectory(const std::string& folder) const {
  if (folder.empty() || fs::path(folder).filename() != pathFromUtf8(folder) ||
      folder == "." || folder == "..") {
    return {};
  }
  fs::path dir = dataRoot / pathFromUtf8(folder);
  if (!fs::exists(dir / "project.json")) return {};
  return dir;
}

std::vector<ProjectSummary> Store::listProjects() const {
  std::vector<ProjectSummary> projects;
  std::error_code ec;
  if (!fs::exists(dataRoot, ec)) return projects;
  for (const auto& entry : fs::directory_iterator(dataRoot, ec)) {
    if (ec) break;
    if (!entry.is_directory()) continue;
    fs::path projectFile = entry.path() / "project.json";
    json value = readJson(projectFile);
    if (!value.is_object()) continue;
    ProjectSummary project = projectFromJson(entry.path(), value);
    Structure structure;
    loadStructureFile(entry.path(), structure);
    project.stats = countStructure(structure);
    projects.push_back(std::move(project));
  }
  std::sort(projects.begin(), projects.end(), [](const auto& a, const auto& b) {
    if (a.pinned != b.pinned) return a.pinned > b.pinned;
    if (a.createdAt != b.createdAt) return a.createdAt > b.createdAt;
    return a.folder < b.folder;
  });
  return projects;
}

ProjectSummary Store::createProject(const std::string& name, const std::string& courseLine) {
  ensureRoot();
  ProjectSummary project;
  project.name = trim(name).empty() ? "未命名项目" : trim(name);
  project.courseLine = trim(courseLine);
  project.createdAt = nowMillis();
  project.updated = project.createdAt;
  const std::string base = sanitizeName(project.name);
  for (int attempt = 0; attempt < 30; ++attempt) {
    std::string folder = base + "-" + randomSuffix();
    fs::path dir = dataRoot / pathFromUtf8(folder);
    if (fs::exists(dir)) continue;
    std::error_code ec;
    fs::create_directories(dir, ec);
    if (ec) break;
    project.folder = folder;
    if (writeJsonAtomic(dir / "project.json", json::parse(projectJsonString(project))) &&
        writeJsonAtomic(dir / "structure.json", structureToJson(Structure{}))) {
      return project;
    }
    fs::remove_all(dir, ec);
    break;
  }
  return {};
}

bool Store::loadProject(const std::string& folder, ProjectSummary& project,
                        Structure& structure) const {
  fs::path dir = projectDirectory(folder);
  if (dir.empty()) return false;
  json projectValue = readJson(dir / "project.json");
  if (!projectValue.is_object()) return false;
  project = projectFromJson(dir, projectValue);
  loadStructureFile(dir, structure);
  project.stats = countStructure(structure);
  return true;
}

bool Store::saveStructure(const std::string& folder, const Structure& structure,
                          ProjectSummary& project) const {
  fs::path dir = projectDirectory(folder);
  if (dir.empty()) return false;
  json projectValue = readJson(dir / "project.json");
  if (!projectValue.is_object()) return false;
  ProjectSummary current = projectFromJson(dir, projectValue);
  Stats stats = countStructure(structure);
  current.lessons = stats.courses;
  current.questions = stats.questions;
  current.updated = nowMillis();
  if (!writeJsonAtomic(dir / "structure.json", structureToJson(structure))) return false;
  if (!writeJsonAtomic(dir / "project.json", json::parse(projectJsonString(current)))) {
    return false;
  }
  project = current;
  project.stats = stats;
  return true;
}

bool Store::togglePin(const std::string& folder) const {
  fs::path dir = projectDirectory(folder);
  if (dir.empty()) return false;
  json value = readJson(dir / "project.json");
  if (!value.is_object()) return false;
  value["pinned"] = !value.value("pinned", false);
  return writeJsonAtomic(dir / "project.json", value);
}

bool Store::deleteProject(const std::string& folder) const {
  fs::path dir = projectDirectory(folder);
  if (dir.empty()) return false;
  std::error_code ec;
  fs::remove_all(dir, ec);
  return !ec;
}

bool Store::exportProject(const std::string& folder, const fs::path& output,
                          std::string& error) const {
  fs::path dir = projectDirectory(folder);
  if (dir.empty()) {
    error = "项目不存在";
    return false;
  }
  std::error_code ec;
  fs::path parent = output.parent_path();
  if (!parent.empty()) fs::create_directories(parent, ec);

  mz_zip_archive zip{};
  if (!mz_zip_writer_init_heap(&zip, 0, 0)) {
    error = "无法创建 ZIP";
    return false;
  }
  bool ok = true;
  for (const auto& entry : fs::recursive_directory_iterator(dir, ec)) {
    if (ec) { ok = false; break; }
    if (!entry.is_regular_file()) continue;
    fs::path relative = fs::relative(entry.path(), dir, ec);
    if (ec) { ok = false; break; }
    std::string name = utf8(relative.generic_string().empty()
                                ? relative
                                : fs::path(relative.generic_string()));
    std::replace(name.begin(), name.end(), '\\', '/');
    std::string bytes = readBytes(entry.path());
    if (!mz_zip_writer_add_mem(&zip, name.c_str(), bytes.data(), bytes.size(),
                               MZ_BEST_COMPRESSION)) {
      ok = false;
      break;
    }
  }
  if (ok) {
    void* archive = nullptr;
    size_t size = 0;
    if (mz_zip_writer_finalize_heap_archive(&zip, &archive, &size)) {
      std::ofstream stream(output, std::ios::binary | std::ios::trunc);
      if (stream) {
        stream.write(static_cast<const char*>(archive), static_cast<std::streamsize>(size));
        ok = static_cast<bool>(stream);
      } else {
        ok = false;
      }
      mz_free(archive);
    } else {
      ok = false;
    }
  }
  mz_zip_writer_end(&zip);
  if (!ok) {
    error = "导出 ZIP 失败";
    fs::remove(output, ec);
    return false;
  }
  return true;
}

bool Store::importConlang(const fs::path& input, ProjectSummary& project,
                          std::string& error) const {
  std::string bytes = readBytes(input);
  if (bytes.empty()) {
    error = "无法读取 ZIP";
    return false;
  }
  mz_zip_archive zip{};
  if (!mz_zip_reader_init_mem(&zip, bytes.data(), bytes.size(), 0)) {
    error = "ZIP 无效";
    return false;
  }
  struct Entry {
    std::string name;
    std::string data;
  };
  std::vector<Entry> entries;
  mz_uint fileCount = mz_zip_reader_get_num_files(&zip);
  for (mz_uint i = 0; i < fileCount; ++i) {
    mz_zip_archive_file_stat stat{};
    if (!mz_zip_reader_file_stat(&zip, i, &stat)) continue;
    if (stat.m_is_directory) continue;
    std::string raw = stat.m_filename;
    if (!isSafeZipName(raw)) {
      error = "ZIP 包含非法路径";
      mz_zip_reader_end(&zip);
      return false;
    }
    std::string name = normalizedZipName(raw);
    if (name.empty()) continue;
    size_t size = 0;
    void* data = mz_zip_reader_extract_to_heap(&zip, i, &size, 0);
    if (!data) {
      error = "读取 ZIP 条目失败";
      mz_zip_reader_end(&zip);
      return false;
    }
    entries.push_back({name, std::string(static_cast<const char*>(data), size)});
    mz_free(data);
  }
  mz_zip_reader_end(&zip);

  std::string prefix;
  bool rootProject = false;
  for (const auto& entry : entries) {
    if (entry.name == "project.json") {
      rootProject = true;
      break;
    }
  }
  if (!rootProject) {
    for (const auto& entry : entries) {
      const std::string suffix = "/project.json";
      if (entry.name.size() > suffix.size() &&
          entry.name.compare(entry.name.size() - suffix.size(), suffix.size(), suffix) == 0) {
        prefix = entry.name.substr(0, entry.name.size() - suffix.size() + 1);
        break;
      }
    }
  }
  if (!rootProject && prefix.empty()) {
    error = "ZIP 缺少 project.json";
    return false;
  }

  const Entry* projectEntry = nullptr;
  for (const auto& entry : entries) {
    if (entry.name == prefix + "project.json") {
      projectEntry = &entry;
      break;
    }
  }
  if (!projectEntry) {
    error = "ZIP 缺少 project.json";
    return false;
  }
  json projectValue;
  try {
    projectValue = json::parse(projectEntry->data);
  } catch (...) {
    error = "project.json 无效";
    return false;
  }
  if (!projectValue.is_object()) {
    error = "project.json 无效";
    return false;
  }

  std::string base = sanitizeName(projectValue.value("name", "导入项目"));
  fs::path dir;
  for (int attempt = 0; attempt < 30; ++attempt) {
    dir = dataRoot / pathFromUtf8(base + "-" + randomSuffix());
    if (!fs::exists(dir)) break;
    dir.clear();
  }
  if (dir.empty()) {
    error = "无法创建导入目录";
    return false;
  }
  std::error_code ec;
  fs::create_directories(dir, ec);
  if (ec) {
    error = "无法创建导入目录";
    return false;
  }
  bool ok = true;
  for (const auto& entry : entries) {
    if (entry.name.size() <= prefix.size() ||
        entry.name.compare(0, prefix.size(), prefix) != 0) {
      continue;
    }
    std::string relative = entry.name.substr(prefix.size());
    if (relative.empty()) continue;
    fs::path target = (dir / pathFromUtf8(relative)).lexically_normal();
    fs::path relativeCheck = target.lexically_relative(dir.lexically_normal());
    bool safe = !relativeCheck.empty() && !relativeCheck.is_absolute();
    for (const auto& part : relativeCheck) {
      if (part == "..") {
        safe = false;
        break;
      }
    }
    if (!safe) {
      ok = false;
      error = "ZIP 路径越界";
      break;
    }
    if (!writeBytesAtomic(target, entry.data)) {
      ok = false;
      error = "写入导入文件失败";
      break;
    }
  }
  if (!ok || !fs::exists(dir / "project.json")) {
    fs::remove_all(dir, ec);
    if (error.empty()) error = "导入失败";
    return false;
  }
  ProjectSummary imported = projectFromJson(dir, projectValue);
  imported.folder = utf8(dir.filename());
  imported.updated = imported.updated > 0 ? imported.updated : nowMillis();
  if (!writeJsonAtomic(dir / "project.json", json::parse(projectJsonString(imported)))) {
    fs::remove_all(dir, ec);
    error = "写入 project.json 失败";
    return false;
  }
  Structure structure;
  loadStructureFile(dir, structure);
  imported.stats = countStructure(structure);
  project = imported;
  return true;
}

bool Store::loadTheme(bool& dark) const {
  json value = readJson(dataRoot / "ui.json");
  if (!value.is_object()) return false;
  dark = value.value("theme", "light") == "dark";
  return true;
}

void Store::saveTheme(bool dark) const {
  ensureRoot();
  writeJsonAtomic(dataRoot / "ui.json", json{{"schema", 1}, {"theme", dark ? "dark" : "light"}});
}

Stats countStructure(const Structure& structure) {
  Stats stats;
  for (const auto& chapter : structure.chapters) {
    ++stats.chapters;
    for (const auto& unit : chapter.units) {
      ++stats.units;
      for (const auto& course : unit.courses) {
        ++stats.courses;
        stats.questions += static_cast<int>(course.questions.size());
      }
    }
  }
  return stats;
}

}  // namespace forge
