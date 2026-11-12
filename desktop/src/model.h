#pragma once

#include <cstdint>
#include <filesystem>
#include <string>
#include <vector>

namespace forge {

namespace fs = std::filesystem;

struct Stats {
  int chapters = 0;
  int units = 0;
  int courses = 0;
  int questions = 0;
};

struct QuestionSegment {
  std::string kind = "word";
  std::string vocabId;
  std::string text;
};

struct Question {
  std::string id;
  std::string name;
  std::string type = "translation_choice";
  std::vector<std::string> parts;
  std::vector<QuestionSegment> segments;
  std::string sentence;
  std::string audio;
  long long audioDuration = 0;
  long long audioStart = 0;
  long long audioEnd = 0;
  std::vector<std::string> options;
  int correctIndex = -1;
};

struct Course {
  std::string id;
  std::string name;
  std::vector<Question> questions;
};

struct Unit {
  std::string id;
  std::string name;
  std::vector<Course> courses;
};

struct Chapter {
  std::string id;
  std::string name;
  std::vector<Unit> units;
};

struct Structure {
  std::vector<Chapter> chapters;
};

struct ProjectSummary {
  std::string folder;
  std::string name;
  std::string courseLine;
  int lessons = 0;
  int questions = 0;
  int words = 0;
  std::int64_t createdAt = 0;
  std::int64_t updated = 0;
  bool pinned = false;
  Stats stats;
};

struct Store {
  fs::path dataRoot;

  explicit Store(fs::path root);
  void ensureRoot() const;

  std::vector<ProjectSummary> listProjects() const;
  ProjectSummary createProject(const std::string& name, const std::string& courseLine);
  bool loadProject(const std::string& folder, ProjectSummary& project, Structure& structure) const;
  bool saveStructure(const std::string& folder, const Structure& structure, ProjectSummary& project) const;
  bool togglePin(const std::string& folder) const;
  bool deleteProject(const std::string& folder) const;

  bool exportProject(const std::string& folder, const fs::path& output, std::string& error) const;
  bool importConlang(const fs::path& input, ProjectSummary& project, std::string& error) const;

  bool loadTheme(bool& dark) const;
  void saveTheme(bool dark) const;

  fs::path projectDirectory(const std::string& folder) const;

  static fs::path defaultDataRoot();
  static std::string sanitizeName(const std::string& value);
  static std::string newUuid();
  static std::string randomSuffix();
};

Stats countStructure(const Structure& structure);
std::string utf8(const fs::path& path);
fs::path pathFromUtf8(const std::string& value);

}  // namespace forge
