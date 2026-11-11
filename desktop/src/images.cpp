#include "images.h"

#include <SDL_image.h>

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

std::string trim(std::string value) {
  auto notSpace = [](unsigned char c) { return !std::isspace(c); };
  value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
  value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
  return value;
}

}  // namespace

bool supportedImageExtension(const std::string& extension) {
  return extension == ".png" || extension == ".jpg" || extension == ".jpeg" ||
         extension == ".webp" || extension == ".bmp";
}

std::string imageExtensionLower(const fs::path& path) {
  std::string ext = utf8(path.extension());
  std::transform(ext.begin(), ext.end(), ext.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return ext;
}

bool loadImageSize(const fs::path& path, int& width, int& height) {
  if (IMG_Init(IMG_INIT_PNG | IMG_INIT_JPG | IMG_INIT_WEBP) == 0) return false;
  SDL_Surface* surface = IMG_Load(utf8(path).c_str());
  if (!surface) return false;
  width = surface->w;
  height = surface->h;
  SDL_FreeSurface(surface);
  return width > 0 && height > 0;
}

ImageStore::ImageStore(fs::path projectDir) : projectDirectory(std::move(projectDir)) {
  load();
}

void ImageStore::load() {
  images.clear();
  json value = readJson(projectDirectory / "images.json");
  if (!value.is_object() || value.value("schema", 0) != 1) return;
  if (!value.contains("images") || !value["images"].is_array()) return;
  for (const auto& item : value["images"]) {
    if (!item.is_object()) continue;
    ImageEntry entry;
    entry.id = item.value("id", "");
    entry.name = item.value("name", "");
    entry.file = item.value("file", "");
    entry.code = item.value("code", "");
    entry.width = item.value("width", 0);
    entry.height = item.value("height", 0);
    entry.createdAt = item.value("createdAt", nowMillis());
    if (entry.id.empty() || entry.file.empty()) continue;
    if (resolveFile(entry.file).empty()) continue;
    images.push_back(std::move(entry));
  }
}

bool ImageStore::save() const {
  json root = {{"schema", 1}, {"images", json::array()}};
  for (const auto& image : images) {
    root["images"].push_back({{"id", image.id},
                              {"name", image.name},
                              {"file", image.file},
                              {"code", image.code},
                              {"width", image.width},
                              {"height", image.height},
                              {"createdAt", image.createdAt}});
  }
  return writeJsonAtomic(projectDirectory / "images.json", root);
}

ImageEntry* ImageStore::find(const std::string& id) {
  for (auto& image : images) if (image.id == id) return &image;
  return nullptr;
}

const ImageEntry* ImageStore::find(const std::string& id) const {
  for (const auto& image : images) if (image.id == id) return &image;
  return nullptr;
}

ImageEntry* ImageStore::findByFile(const std::string& relativePath) {
  for (auto& image : images) if (image.file == relativePath) return &image;
  return nullptr;
}

const ImageEntry* ImageStore::findByFile(const std::string& relativePath) const {
  for (const auto& image : images) if (image.file == relativePath) return &image;
  return nullptr;
}

fs::path ImageStore::imagePath(const ImageEntry& image) const {
  return resolveFile(image.file);
}

fs::path ImageStore::resolveFile(const std::string& relativePath) const {
  if (relativePath.empty()) return {};
  fs::path path = (projectDirectory / pathFromUtf8(relativePath)).lexically_normal();
  fs::path relative = path.lexically_relative(projectDirectory.lexically_normal());
  if (relative.empty() || relative.is_absolute()) return {};
  for (const auto& part : relative) {
    if (part == "..") return {};
  }
  return path;
}

std::vector<fs::path> ImageStore::unregisteredFiles() const {
  std::vector<fs::path> result;
  fs::path directory = projectDirectory / "resources" / "images";
  std::error_code ec;
  if (!fs::is_directory(directory, ec)) return result;
  for (const auto& entry : fs::directory_iterator(directory, ec)) {
    if (ec) break;
    if (!entry.is_regular_file()) continue;
    if (!supportedImageExtension(imageExtensionLower(entry.path()))) continue;
    fs::path relative = entry.path().lexically_relative(projectDirectory);
    if (relative.empty()) continue;
    const std::string relativeText = relative.generic_string();
    if (!findByFile(relativeText)) result.push_back(entry.path());
  }
  std::sort(result.begin(), result.end());
  return result;
}

bool ImageStore::importImage(const fs::path& source, const std::string& displayName,
                             ImageEntry& imported, std::string& error) {
  if (!fs::exists(source)) {
    error = "图片文件不存在";
    return false;
  }
  const std::string ext = imageExtensionLower(source);
  if (!supportedImageExtension(ext)) {
    error = "仅支持 PNG/JPG/JPEG/WEBP/BMP";
    return false;
  }
  int width = 0;
  int height = 0;
  if (!loadImageSize(source, width, height)) {
    error = "图片无法解码";
    return false;
  }
  std::error_code ec;
  fs::path directory = projectDirectory / "resources" / "images";
  fs::create_directories(directory, ec);
  if (ec) {
    error = "无法创建图片目录";
    return false;
  }
  std::string id = Store::newUuid();
  std::string safeId = id;
  safeId.erase(std::remove(safeId.begin(), safeId.end(), '-'), safeId.end());
  fs::path target = directory / ("img_" + safeId + "_" +
                                 std::to_string(nowMillis()) + ext);
  fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
  if (ec) {
    error = "复制图片失败";
    return false;
  }
  int checkWidth = 0;
  int checkHeight = 0;
  if (!loadImageSize(target, checkWidth, checkHeight)) {
    fs::remove(target, ec);
    error = "复制后的图片无法解码";
    return false;
  }
  imported.id = id;
  imported.name = trim(displayName).empty() ? utf8(source.filename()) : trim(displayName);
  imported.file = "resources/images/" + utf8(target.filename());
  imported.width = checkWidth;
  imported.height = checkHeight;
  imported.createdAt = nowMillis();
  images.push_back(imported);
  if (!save()) {
    images.pop_back();
    fs::remove(target, ec);
    error = "写入 images.json 失败";
    return false;
  }
  return true;
}

ImageEntry* ImageStore::registerFile(const fs::path& file) {
  if (!fs::is_regular_file(file)) return nullptr;
  fs::path relative = file.lexically_relative(projectDirectory);
  if (relative.empty()) return nullptr;
  std::string relativeText = relative.generic_string();
  if (ImageEntry* existing = findByFile(relativeText)) return existing;
  int width = 0;
  int height = 0;
  if (!loadImageSize(file, width, height)) return nullptr;
  ImageEntry entry;
  entry.id = Store::newUuid();
  entry.name = utf8(file.filename());
  entry.file = relativeText;
  entry.width = width;
  entry.height = height;
  entry.createdAt = nowMillis();
  images.push_back(entry);
  if (!save()) {
    images.pop_back();
    return nullptr;
  }
  return &images.back();
}

bool ImageStore::setCode(const std::string& id, const std::string& code) {
  ImageEntry* entry = find(id);
  if (!entry) return false;
  entry->code = trim(code);
  if (!save()) {
    load();
    return false;
  }
  return true;
}

bool ImageStore::deleteImage(const std::string& id, std::string& error) {
  auto it = std::find_if(images.begin(), images.end(),
                         [&](const ImageEntry& image) { return image.id == id; });
  if (it == images.end()) {
    error = "图片不存在";
    return false;
  }
  fs::path file = imagePath(*it);
  images.erase(it);
  if (!save()) {
    load();
    error = "写入 images.json 失败";
    return false;
  }
  std::error_code ec;
  if (!file.empty()) fs::remove(file, ec);
  return true;
}

}  // namespace forge
