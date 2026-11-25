#include "resource_scope.h"

#include <algorithm>
#include <cctype>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <map>
#include <set>
#include <sstream>
#include <vector>

#include "audio.h"
#include "images.h"
#include "model.h"
#include "nlohmann/json.hpp"

namespace forge {
namespace {

using json = nlohmann::json;

constexpr const char* kCourseRoot = "resources/courses";
constexpr const char* kGlobalAudio = "resources/audio";
constexpr const char* kGlobalImages = "resources/images";

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
  temp += ".tmp-" + resourceScopeTag(utf8(file) + std::to_string(
      std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch()).count()));
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

std::string lower(std::string value) {
  std::transform(value.begin(), value.end(), value.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return value;
}

std::string extensionOf(const std::string& name, const std::string& fallback) {
  const std::size_t dot = name.find_last_of('.');
  if (dot == std::string::npos || dot + 1 >= name.size()) return fallback;
  const std::string ext = lower(name.substr(dot));
  if (ext.size() > 9) return fallback;
  for (std::size_t i = 1; i < ext.size(); ++i) {
    if (!std::isalnum(static_cast<unsigned char>(ext[i]))) return fallback;
  }
  return ext;
}

std::string safeSegment(const std::string& value) {
  std::string result;
  for (unsigned char c : value) {
    if (std::isalnum(c) || c == '.' || c == '_' || c == '-') {
      result.push_back(static_cast<char>(c));
    } else {
      result.push_back('_');
    }
  }
  if (result.empty() || result == "." || result == "..") result = "course";
  if (result.size() > 80) result.resize(80);
  return result;
}

std::string sanitizeName(const std::string& value) {
  std::string result;
  for (unsigned char c : value) {
    if (std::isalnum(c) || c == '.' || c == '_' || c == '-') {
      result.push_back(static_cast<char>(c));
    } else {
      result.push_back('_');
    }
  }
  if (result.empty()) result = "audio";
  if (result.size() > 100) result.resize(100);
  return result;
}

std::string relativePath(const fs::path& projectDirectory, const fs::path& file) {
  const fs::path relative = file.lexically_relative(projectDirectory);
  if (relative.empty() || relative.is_absolute()) return {};
  for (const auto& part : relative) {
    if (part == "..") return {};
  }
  return relative.generic_string();
}

fs::path resolvePath(const fs::path& projectDirectory, const std::string& relative) {
  if (relative.empty()) return {};
  fs::path path = (projectDirectory / pathFromUtf8(relative)).lexically_normal();
  const fs::path rel = path.lexically_relative(projectDirectory.lexically_normal());
  if (rel.empty() || rel.is_absolute()) return {};
  for (const auto& part : rel) {
    if (part == "..") return {};
  }
  return path;
}

bool underPath(const std::string& relative, const std::string& directory) {
  std::string normalized = relative;
  std::replace(normalized.begin(), normalized.end(), '\\', '/');
  return normalized == directory || normalized.rfind(directory + "/", 0) == 0;
}

int findFile(const json& array, const std::string& file) {
  if (!array.is_array()) return -1;
  for (std::size_t i = 0; i < array.size(); ++i) {
    if (array[i].is_object() && array[i].value("file", "") == file) {
      return static_cast<int>(i);
    }
  }
  return -1;
}

json makeGlobalEntry(const fs::path& projectDirectory, const std::string& relative,
                     bool audio) {
  const fs::path file = resolvePath(projectDirectory, relative);
  json entry = json::object();
  if (!fs::is_regular_file(file)) return entry;
  entry["id"] = std::string(audio ? "ga_" : "gi_") + resourceScopeTag(relative);
  entry["name"] = utf8(file.filename());
  entry["file"] = relative;
  entry["code"] = "";
  entry["createdAt"] = std::chrono::duration_cast<std::chrono::milliseconds>(
      std::chrono::system_clock::now().time_since_epoch()).count();
  if (audio) {
    int duration = 0;
    loadAudioDuration(file, duration);
    entry["duration"] = duration;
  } else {
    int width = 0;
    int height = 0;
    loadImageSize(file, width, height);
    entry["width"] = width;
    entry["height"] = height;
  }
  return entry;
}

bool migrateToGlobal(const fs::path& projectDirectory, const std::string& relative,
                     const std::string& entryId, bool audio, std::string& migrated) {
  const fs::path source = resolvePath(projectDirectory, relative);
  if (!fs::is_regular_file(source)) {
    migrated = relative;
    return false;
  }
  const std::string directory = audio ? kGlobalAudio : kGlobalImages;
  if (underPath(relative, directory)) {
    migrated = relative;
    return true;
  }
  const fs::path targetDirectory = projectDirectory / pathFromUtf8(directory);
  std::error_code ec;
  fs::create_directories(targetDirectory, ec);
  if (ec) {
    migrated = relative;
    return false;
  }
  const std::string extension = extensionOf(source.filename().string(), audio ? ".m4a" : ".png");
  const std::string filename = "vocab_" + safeSegment(entryId) + "_" +
      resourceScopeTag(relative) + extension;
  const fs::path target = targetDirectory / pathFromUtf8(filename);
  if (!fs::is_regular_file(target)) {
    fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
    if (ec) {
      migrated = relative;
      return false;
    }
  }
  const std::string result = relativePath(projectDirectory, target);
  migrated = result.empty() ? relative : result;
  return !result.empty();
}

bool migrateVocabulary(const fs::path& projectDirectory, json& vocabulary,
                       json& globalAudio, json& globalImages,
                       std::set<std::string>& audioRefs,
                       std::set<std::string>& imageRefs) {
  if (!vocabulary.is_object() || !vocabulary.contains("entries") ||
      !vocabulary["entries"].is_array()) {
    return false;
  }
  bool changed = false;
  for (auto& entry : vocabulary["entries"]) {
    if (!entry.is_object()) continue;
    const std::string id = entry.value("id", "");
    const std::string audio = entry.value("audio", "");
    if (!audio.empty()) {
      std::string migrated;
      if (migrateToGlobal(projectDirectory, audio, id, true, migrated)) {
        if (migrated != audio) {
          entry["audio"] = migrated;
          changed = true;
        }
        audioRefs.insert(migrated);
        if (findFile(globalAudio["audios"], migrated) < 0) {
          json added = makeGlobalEntry(projectDirectory, migrated, true);
          if (!added.empty()) {
            globalAudio["audios"].push_back(added);
            changed = true;
          }
        }
      }
    }
    const std::string image = entry.value("image", "");
    if (!image.empty()) {
      std::string migrated;
      if (migrateToGlobal(projectDirectory, image, id, false, migrated)) {
        if (migrated != image) {
          entry["image"] = migrated;
          changed = true;
        }
        imageRefs.insert(migrated);
        if (findFile(globalImages["images"], migrated) < 0) {
          json added = makeGlobalEntry(projectDirectory, migrated, false);
          if (!added.empty()) {
            globalImages["images"].push_back(added);
            changed = true;
          }
        }
      }
    }
  }
  return changed;
}

bool copyQuestionAudio(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& oldPath, const fs::path& source,
                       std::string& targetRelative, int& copied) {
  const fs::path directory = projectDirectory / pathFromUtf8(kCourseRoot) /
      pathFromUtf8(safeSegment(courseId)) / "audio";
  std::error_code ec;
  fs::create_directories(directory, ec);
  if (ec) return false;
  const std::string extension = extensionOf(source.filename().string(), ".m4a");
  const std::string base = sanitizeName(source.filename().string());
  const fs::path target = directory / pathFromUtf8(
      "q_" + resourceScopeTag(courseId + "|" + oldPath) + "_" + base);
  if (!fs::is_regular_file(target)) {
    fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
    if (ec) return false;
    ++copied;
  }
  targetRelative = relativePath(projectDirectory, target);
  return !targetRelative.empty();
}

json makeCourseEntry(const fs::path& projectDirectory, const std::string& courseId,
                     const std::string& path, const json* globalEntry) {
  json entry = json::object();
  const fs::path file = resolvePath(projectDirectory, path);
  entry["id"] = "qa_" + resourceScopeTag(courseId + "|" + path);
  entry["name"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("name", utf8(file.filename())) : utf8(file.filename());
  entry["file"] = path;
  entry["code"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("code", "") : "";
  int duration = globalEntry && globalEntry->is_object()
      ? globalEntry->value("duration", 0) : 0;
  if (duration <= 0) loadAudioDuration(file, duration);
  entry["duration"] = duration;
  entry["createdAt"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("createdAt", 0) : 0;
  return entry;
}

std::string questionPath(const json& question) {
  return question.is_object() ? question.value("audio", "") : "";
}

void collectQuestionImages(const json& question, std::set<std::string>& paths) {
  if (!question.is_object()) return;
  const std::string questionImage = question.value("image", "");
  if (!questionImage.empty()) paths.insert(questionImage);
  if (!question.contains("optionItems") || !question["optionItems"].is_array()) {
    return;
  }
  for (const auto& group : question["optionItems"]) {
    if (!group.is_array()) continue;
    for (const auto& item : group) {
      if (!item.is_object()) continue;
      const std::string image = item.value("image", "");
      if (!image.empty()) paths.insert(image);
    }
  }
}

bool copyQuestionImage(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& oldPath, const fs::path& source,
                       std::string& target, int& copied) {
  const fs::path directory = projectDirectory / pathFromUtf8(kCourseRoot) /
      pathFromUtf8(safeSegment(courseId)) / "images";
  std::error_code ec;
  fs::create_directories(directory, ec);
  if (ec) return false;
  const std::string filename = "q_" + resourceScopeTag(courseId + "|" + oldPath) +
      "_" + sanitizeName(utf8(source.filename()));
  const fs::path destination = directory / pathFromUtf8(filename);
  if (!fs::is_regular_file(destination)) {
    fs::copy_file(source, destination, fs::copy_options::overwrite_existing, ec);
    if (ec) {
      std::fprintf(stderr, "FORGE_IMAGE_COPY fail copy %s -> %s: %s\n",
                   utf8(source).c_str(), utf8(destination).c_str(), ec.message().c_str());
      return false;
    }
    int width = 0;
    int height = 0;
    if (!loadImageSize(destination, width, height)) {
      std::fprintf(stderr, "FORGE_IMAGE_COPY fail decode %s\n", utf8(destination).c_str());
      std::error_code ignored;
      fs::remove(destination, ignored);
      return false;
    }
    ++copied;
  }
  target = relativePath(projectDirectory, destination);
  return !target.empty();
}

json makeCourseImageEntry(const fs::path& projectDirectory, const std::string& courseId,
                          const std::string& relative, const json* globalEntry) {
  const fs::path file = resolvePath(projectDirectory, relative);
  if (!fs::is_regular_file(file)) return json::object();
  int width = 0;
  int height = 0;
  loadImageSize(file, width, height);
  json entry = json::object();
  entry["id"] = std::string("qi_") + resourceScopeTag(courseId + "|" + relative);
  entry["name"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("name", utf8(file.filename())) : utf8(file.filename());
  entry["file"] = relative;
  entry["code"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("code", "") : "";
  entry["width"] = width;
  entry["height"] = height;
  entry["createdAt"] = globalEntry && globalEntry->is_object()
      ? globalEntry->value("createdAt", 0LL) : 0LL;
  return entry;
}

}  // namespace

std::string resourceScopeTag(const std::string& value) {
  // FNV-1a keeps deterministic names without pulling a crypto dependency.
  std::uint64_t hash = 1469598103934665603ULL;
  for (unsigned char c : value) {
    hash ^= c;
    hash *= 1099511628211ULL;
  }
  char buffer[17];
  std::snprintf(buffer, sizeof(buffer), "%016llx",
                static_cast<unsigned long long>(hash));
  return std::string(buffer);
}

std::string courseAudioDir(const fs::path& projectDirectory, const std::string& courseId) {
  return (fs::path(kCourseRoot) / safeSegment(courseId) / "audio").generic_string();
}

bool isCourseAudioPath(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& relative) {
  const std::string dir = courseAudioDir(projectDirectory, courseId);
  return underPath(relative, dir);
}

std::string courseImageDir(const fs::path& projectDirectory, const std::string& courseId) {
  return (fs::path(kCourseRoot) / safeSegment(courseId) / "images").generic_string();
}

bool isCourseImagePath(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& relative) {
  return underPath(relative, courseImageDir(projectDirectory, courseId));
}

void deleteCourseResources(const fs::path& projectDirectory, const std::string& courseId) {
  if (projectDirectory.empty()) return;
  const fs::path directory = projectDirectory / pathFromUtf8(kCourseRoot) /
      pathFromUtf8(safeSegment(courseId));
  std::error_code ec;
  const fs::path root = projectDirectory / pathFromUtf8(kCourseRoot);
  const fs::path rel = directory.lexically_relative(root);
  if (rel.empty() || rel.is_absolute()) return;
  for (const auto& part : rel) if (part == "..") return;
  fs::remove_all(directory, ec);
}

bool copyAudioToCourse(const fs::path& projectDirectory, const std::string& courseId,
                       const fs::path& source, const std::string& questionId,
                       fs::path& copied, std::string& error) {
  if (!fs::is_regular_file(source)) {
    error = "音频不存在";
    return false;
  }
  const fs::path directory = projectDirectory / pathFromUtf8(kCourseRoot) /
      pathFromUtf8(safeSegment(courseId)) / "audio";
  std::error_code ec;
  fs::create_directories(directory, ec);
  if (ec) {
    error = "无法创建课程音频目录";
    return false;
  }
  const std::string extension = extensionOf(source.filename().string(), ".m4a");
  const fs::path target = directory / pathFromUtf8(
      "question_" + safeSegment(questionId) + "_" +
      std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch()).count()) +
      extension);
  fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    error = "复制音频失败";
    return false;
  }
  copied = target;
  return true;
}

bool copyImageToCourse(const fs::path& projectDirectory, const std::string& courseId,
                       const fs::path& source, const std::string& optionId,
                       fs::path& copied, std::string& error) {
  if (!fs::is_regular_file(source)) {
    error = "图片不存在";
    return false;
  }
  const fs::path directory = projectDirectory / pathFromUtf8(kCourseRoot) /
      pathFromUtf8(safeSegment(courseId)) / "images";
  std::error_code ec;
  fs::create_directories(directory, ec);
  if (ec) {
    error = "无法创建课程图片目录";
    return false;
  }
  const std::string extension = extensionOf(source.filename().string(), ".png");
  const std::string filename = "option_" + safeSegment(optionId) + "_" +
      std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(
          std::chrono::system_clock::now().time_since_epoch()).count()) + extension;
  const fs::path target = directory / pathFromUtf8(filename);
  fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    error = "复制图片失败";
    return false;
  }
  int width = 0;
  int height = 0;
  if (!loadImageSize(target, width, height)) {
    std::error_code ignored;
    fs::remove(target, ignored);
    error = "复制后的图片无法解码";
    return false;
  }
  copied = target;
  return true;
}

bool migrateResourceScopes(const fs::path& projectDirectory, std::string& error) {
  error.clear();
  if (projectDirectory.empty() || !fs::is_directory(projectDirectory)) return true;
  json structure = readJson(projectDirectory / "structure.json");
  if (!structure.is_object()) return true;
  json vocabulary = readJson(projectDirectory / "vocabulary.json");
  json globalAudio = readJson(projectDirectory / "audios.json");
  json globalImages = readJson(projectDirectory / "images.json");
  if (!globalAudio.is_object()) globalAudio = json{{"schema", 1}, {"audios", json::array()}};
  if (!globalImages.is_object()) globalImages = json{{"schema", 1}, {"images", json::array()}};
  if (!globalAudio.contains("audios") || !globalAudio["audios"].is_array()) {
    globalAudio["audios"] = json::array();
  }
  if (!globalImages.contains("images") || !globalImages["images"].is_array()) {
    globalImages["images"] = json::array();
  }

  std::set<std::string> globalAudioRefs;
  std::set<std::string> globalImageRefs;
  const bool vocabularyChanged = migrateVocabulary(projectDirectory, vocabulary,
      globalAudio, globalImages, globalAudioRefs, globalImageRefs);

  std::set<std::string> questionSources;
  std::set<std::string> targets;
  std::set<std::string> remainingPaths;
  std::set<std::string> removableSources;
  std::set<std::string> questionImageSources;
  std::set<std::string> imageTargets;
  std::set<std::string> removableImageSources;
  bool structureChanged = false;
  int copied = 0;
  int copiedImages = 0;
  for (auto& chapter : structure["chapters"]) {
    if (!chapter.is_object()) continue;
    for (auto& unit : chapter["units"]) {
      if (!unit.is_object()) continue;
      for (auto& course : unit["courses"]) {
        if (!course.is_object()) continue;
        const std::string courseId = course.value("id", "");
        if (!course.contains("questions") || !course["questions"].is_array()) continue;
        const fs::path manifestPath = projectDirectory / pathFromUtf8(kCourseRoot) /
            pathFromUtf8(safeSegment(courseId)) / "audios.json";
        json manifest = readJson(manifestPath);
        if (!manifest.is_object()) manifest = json{{"schema", 1}, {"audios", json::array()}};
        if (!manifest.contains("audios") || !manifest["audios"].is_array()) {
          manifest["audios"] = json::array();
        }
        bool courseChanged = false;
        for (auto& question : course["questions"]) {
          if (!question.is_object()) continue;
          const std::string oldPath = questionPath(question);
          if (oldPath.empty()) continue;
          questionSources.insert(oldPath);
          const fs::path source = resolvePath(projectDirectory, oldPath);
          if (!fs::is_regular_file(source)) continue;
          std::string target = oldPath;
          if (!isCourseAudioPath(projectDirectory, courseId, oldPath)) {
            if (!copyQuestionAudio(projectDirectory, courseId, oldPath, source, target, copied)) {
              continue;
            }
            question["audio"] = target;
            structureChanged = true;
          }
          targets.insert(target);
          const int index = findFile(manifest["audios"], target);
          if (index < 0) {
            const int globalIndex = findFile(globalAudio["audios"], oldPath);
            const json* globalEntry = globalIndex >= 0
                ? &globalAudio["audios"][globalIndex] : nullptr;
            manifest["audios"].push_back(makeCourseEntry(projectDirectory, courseId,
                target, globalEntry));
            courseChanged = true;
          }
        }
        if (courseChanged) {
          if (!writeJsonAtomic(manifestPath, manifest)) {
            error = "写入课程音频清单失败";
            return false;
          }
        }

        const fs::path imageManifestPath = projectDirectory / pathFromUtf8(kCourseRoot) /
            pathFromUtf8(safeSegment(courseId)) / "images.json";
        json imageManifest = readJson(imageManifestPath);
        if (!imageManifest.is_object()) {
          imageManifest = json{{"schema", 1}, {"images", json::array()}};
        }
        if (!imageManifest.contains("images") || !imageManifest["images"].is_array()) {
          imageManifest["images"] = json::array();
        }
        bool imageManifestChanged = false;
        auto migrateImage = [&](json& owner) {
          if (!owner.is_object() || !owner.contains("image") ||
              !owner["image"].is_string()) {
            return;
          }
          const std::string oldImage = owner.value("image", "");
          if (oldImage.empty()) return;
          questionImageSources.insert(oldImage);
          const fs::path source = resolvePath(projectDirectory, oldImage);
          if (!fs::is_regular_file(source)) return;
          std::string target = oldImage;
          if (!isCourseImagePath(projectDirectory, courseId, oldImage)) {
            if (!copyQuestionImage(projectDirectory, courseId, oldImage, source, target,
                                   copiedImages)) {
              return;
            }
            owner["image"] = target;
            structureChanged = true;
          }
          imageTargets.insert(target);
          if (findFile(imageManifest["images"], target) < 0) {
            const int globalIndex = findFile(globalImages["images"], oldImage);
            const json* globalEntry = globalIndex >= 0
                ? &globalImages["images"][globalIndex] : nullptr;
            json entry = makeCourseImageEntry(projectDirectory, courseId, target,
                                              globalEntry);
            if (entry.is_object()) {
              imageManifest["images"].push_back(entry);
              imageManifestChanged = true;
            }
          }
        };
        for (auto& question : course["questions"]) {
          if (!question.is_object()) continue;
          migrateImage(question);
          if (!question.contains("optionItems") || !question["optionItems"].is_array()) {
            continue;
          }
          for (auto& group : question["optionItems"]) {
            if (!group.is_array()) continue;
            for (auto& item : group) {
              if (item.is_object()) migrateImage(item);
            }
          }
        }
        if (imageManifestChanged) {
          if (!writeJsonAtomic(imageManifestPath, imageManifest)) {
            error = "写入课程图片清单失败";
            return false;
          }
        }
      }
    }
  }

  std::set<std::string> remainingImagePaths;
  for (const auto& chapter : structure["chapters"]) {
    if (!chapter.is_object()) continue;
    for (const auto& unit : chapter["units"]) {
      if (!unit.is_object()) continue;
      for (const auto& course : unit["courses"]) {
        if (!course.is_object() || !course.contains("questions")) continue;
        for (const auto& question : course["questions"]) {
          const std::string path = questionPath(question);
          if (!path.empty()) remainingPaths.insert(path);
          collectQuestionImages(question, remainingImagePaths);
        }
      }
    }
  }

  // Remove entries that only existed for legacy question audio, but keep any
  // file still referenced by vocabulary or by a missing/current question path.
  json kept = json::array();
  bool globalChanged = false;
  for (const auto& entry : globalAudio["audios"]) {
    if (!entry.is_object()) continue;
    const std::string file = entry.value("file", "");
    const bool keep = !file.empty() &&
        (globalAudioRefs.count(file) > 0 || questionSources.count(file) == 0 ||
         targets.count(file) > 0 || remainingPaths.count(file) > 0 ||
         !underPath(file, kGlobalAudio));
    if (keep) {
      kept.push_back(entry);
    } else {
      globalChanged = true;
      if (!file.empty()) removableSources.insert(file);
    }
  }
  for (const auto& source : questionSources) {
    if (globalAudioRefs.count(source) == 0 && targets.count(source) == 0 &&
        remainingPaths.count(source) == 0 && underPath(source, kGlobalAudio)) {
      removableSources.insert(source);
    }
  }
  if (globalChanged) globalAudio["audios"] = kept;

  json keptImages = json::array();
  bool globalImagesChanged = false;
  for (const auto& entry : globalImages["images"]) {
    if (!entry.is_object()) continue;
    const std::string file = entry.value("file", "");
    const bool keep = !file.empty() &&
        (globalImageRefs.count(file) > 0 || questionImageSources.count(file) == 0 ||
         imageTargets.count(file) > 0 || remainingImagePaths.count(file) > 0 ||
         !underPath(file, kGlobalImages));
    if (keep) {
      keptImages.push_back(entry);
    } else {
      globalImagesChanged = true;
      if (!file.empty()) removableImageSources.insert(file);
    }
  }
  for (const auto& source : questionImageSources) {
    if (globalImageRefs.count(source) == 0 && imageTargets.count(source) == 0 &&
        remainingImagePaths.count(source) == 0 && underPath(source, kGlobalImages)) {
      removableImageSources.insert(source);
    }
  }
  if (globalImagesChanged) globalImages["images"] = keptImages;

  if (vocabularyChanged) {
    if (!writeJsonAtomic(projectDirectory / "vocabulary.json", vocabulary)) {
      error = "写入 vocabulary.json 失败";
      return false;
    }
  }
  if (vocabularyChanged || globalImagesChanged) {
    if (!writeJsonAtomic(projectDirectory / "images.json", globalImages)) {
      error = "写入 images.json 失败";
      return false;
    }
  }
  if (globalChanged || vocabularyChanged) {
    if (!writeJsonAtomic(projectDirectory / "audios.json", globalAudio)) {
      error = "写入 audios.json 失败";
      return false;
    }
  }
  if (structureChanged) {
    if (!writeJsonAtomic(projectDirectory / "structure.json", structure)) {
      error = "写入 structure.json 失败";
      return false;
    }
  }
  // Files are removed only after every rewrite succeeded. The copies already
  // exist in the course directories, so a crash before this point is safe.
  for (const auto& file : removableSources) {
    if (globalAudioRefs.count(file) > 0 || targets.count(file) > 0 ||
        remainingPaths.count(file) > 0 || !underPath(file, kGlobalAudio)) {
      continue;
    }
    const fs::path path = resolvePath(projectDirectory, file);
    if (fs::is_regular_file(path)) {
      std::error_code ec;
      fs::remove(path, ec);
    }
  }
  for (const auto& file : removableImageSources) {
    if (globalImageRefs.count(file) > 0 || imageTargets.count(file) > 0 ||
        remainingImagePaths.count(file) > 0 || !underPath(file, kGlobalImages)) {
      continue;
    }
    const fs::path path = resolvePath(projectDirectory, file);
    if (fs::is_regular_file(path)) {
      std::error_code ec;
      fs::remove(path, ec);
    }
  }
  if (copied > 0 || copiedImages > 0) {
    std::fprintf(stderr, "FORGE_RESOURCE_MIGRATION copied=%d copiedImages=%d\n",
                 copied, copiedImages);
  }
  return true;
}

}  // namespace forge
