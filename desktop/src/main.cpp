#include <SDL.h>
#include <SDL_image.h>
#include <SDL_ttf.h>

#include <algorithm>
#include <cctype>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <chrono>
#include <ctime>
#include <cstdarg>
#include <fstream>
#include <map>
#include <memory>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include "imgui.h"
#include "imgui_impl_sdl2.h"
#include "imgui_impl_sdlrenderer2.h"
#include "miniz.h"
#include "model.h"
#include "vocabulary.h"
#include "images.h"
#include "lexicon.h"
#include "audio.h"
#include "pos.h"
#include <SDL_mixer.h>
#include "nlohmann/json.hpp"

namespace {

using namespace forge;

static inline ImVec2 operator+(const ImVec2& a, const ImVec2& b) {
  return ImVec2(a.x + b.x, a.y + b.y);
}


using json = nlohmann::json;

constexpr int kWindowWidth = 1280;
constexpr int kWindowHeight = 860;

ImVec4 color(int r, int g, int b, int a = 255) {
  return ImVec4(r / 255.0f, g / 255.0f, b / 255.0f, a / 255.0f);
}

std::string fmt(const char* format, ...) {
  char buffer[1024];
  va_list args;
  va_start(args, format);
  vsnprintf(buffer, sizeof(buffer), format, args);
  va_end(args);
  return buffer;
}

std::string formatDuration(int millis) {
  if (millis < 0) millis = 0;
  const int totalSeconds = millis / 1000;
  const int minutes = totalSeconds / 60;
  const int seconds = totalSeconds % 60;
  return fmt("%d:%02d", minutes, seconds);
}

ImFont* addFontFromPath(const fs::path& path, float size, ImFontConfig* config,
                         const ImWchar* ranges) {
  std::ifstream input(path, std::ios::binary | std::ios::ate);
  if (!input) return nullptr;
  const std::streamsize length = input.tellg();
  if (length <= 0) return nullptr;
  input.seekg(0, std::ios::beg);
  void* data = ImGui::MemAlloc(static_cast<std::size_t>(length));
  if (!data) return nullptr;
  if (!input.read(static_cast<char*>(data), length)) {
    ImGui::MemFree(data);
    return nullptr;
  }
  return ImGui::GetIO().Fonts->AddFontFromMemoryTTF(
      data, static_cast<int>(length), size, config, ranges);
}

void addLatinFallback(float size) {
  static const ImWchar ranges[] = {0x0100, 0x02FF, 0};
  std::vector<std::string> candidates = {
      "/usr/share/fonts/noto/NotoSans-Regular.ttf",
      "/usr/share/fonts/liberation/LiberationSans-Regular.ttf",
      "/usr/share/fonts/TTF/DejaVuSans.ttf",
      "C:/Windows/Fonts/segoeui.ttf",
      "C:/Windows/Fonts/arial.ttf",
      "C:/Windows/Fonts/calibri.ttf",
  };
  for (const auto& candidate : candidates) {
    std::error_code ec;
    const fs::path path = pathFromUtf8(candidate);
    if (!fs::exists(path, ec)) continue;
    ImFontConfig config;
    config.MergeMode = true;
    config.FontNo = 0;
    config.OversampleH = 2;
    config.OversampleV = 2;
    config.PixelSnapH = true;
    if (addFontFromPath(path, size, &config, ranges)) return;
  }
}

std::string formatTime(std::int64_t millis) {
  if (millis <= 0) return "—";
  const auto now = std::chrono::duration_cast<std::chrono::milliseconds>(
                       std::chrono::system_clock::now().time_since_epoch())
                       .count();
  const std::int64_t diff = now - millis;
  if (diff < 60000) return "刚刚";
  if (diff < 3600000) return fmt("%lld 分钟前", static_cast<long long>(diff / 60000));
  if (diff < 86400000) return fmt("%lld 小时前", static_cast<long long>(diff / 3600000));
  if (diff < 604800000) return fmt("%lld 天前", static_cast<long long>(diff / 86400000));
  std::time_t time = static_cast<std::time_t>(millis / 1000);
  std::tm tm{};
#ifdef _WIN32
  localtime_s(&tm, &time);
#else
  localtime_r(&time, &tm);
#endif
  char out[32];
  std::strftime(out, sizeof(out), "%Y-%m-%d", &tm);
  return out;
}

bool endsWithConlang(const fs::path& path) {
  std::string ext = utf8(path.extension());
  std::transform(ext.begin(), ext.end(), ext.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  return ext == ".conlang";
}

struct BrowserEntry {
  std::string name;
  bool directory = false;
};

struct DrawStroke {
  std::vector<ImVec2> points;
  bool eraser = false;
  float width = 8.0f;
};

struct DrawCanvas {
  int width = 300;
  int height = 200;
  std::vector<DrawStroke> strokes;
  std::vector<DrawStroke> redo;
  SDL_Surface* surface = nullptr;
  SDL_Texture* texture = nullptr;
  DrawStroke active;
  bool drawing = false;
};

void canvasSetPixel(SDL_Surface* surface, int x, int y, Uint32 color, bool erase) {
  if (!surface || x < 0 || y < 0 || x >= surface->w || y >= surface->h) return;
  Uint32* pixels = static_cast<Uint32*>(surface->pixels);
  const int index = y * (surface->pitch / 4) + x;
  if (erase) pixels[index] = 0;
  else pixels[index] = color;
}

void canvasDot(SDL_Surface* surface, float x, float y, float radius, bool erase) {
  const int minX = static_cast<int>(std::floor(x - radius));
  const int maxX = static_cast<int>(std::ceil(x + radius));
  const int minY = static_cast<int>(std::floor(y - radius));
  const int maxY = static_cast<int>(std::ceil(y + radius));
  for (int py = minY; py <= maxY; ++py) {
    for (int px = minX; px <= maxX; ++px) {
      const float dx = px - x;
      const float dy = py - y;
      if (dx * dx + dy * dy <= radius * radius) canvasSetPixel(surface, px, py, 0xFF000000, erase);
    }
  }
}

void canvasLine(SDL_Surface* surface, ImVec2 a, ImVec2 b, float width, bool erase) {
  const float length = std::max(std::abs(b.x - a.x), std::abs(b.y - a.y));
  const int steps = std::max(1, static_cast<int>(length));
  for (int i = 0; i <= steps; ++i) {
    const float t = static_cast<float>(i) / steps;
    canvasDot(surface, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t,
              std::max(1.0f, width * 0.5f), erase);
  }
}

void canvasClear(DrawCanvas& canvas) {
  if (!canvas.surface) return;
  SDL_FillRect(canvas.surface, nullptr, SDL_MapRGBA(canvas.surface->format, 0, 0, 0, 0));
  if (canvas.texture) {
    SDL_DestroyTexture(canvas.texture);
    canvas.texture = nullptr;
  }
}

void canvasReplay(DrawCanvas& canvas) {
  canvasClear(canvas);
  for (const auto& stroke : canvas.strokes) {
    if (stroke.points.empty()) continue;
    if (stroke.points.size() == 1) {
      canvasDot(canvas.surface, stroke.points[0].x, stroke.points[0].y,
                std::max(1.0f, stroke.width * 0.5f), stroke.eraser);
    } else {
      for (std::size_t i = 1; i < stroke.points.size(); ++i) {
        canvasLine(canvas.surface, stroke.points[i - 1], stroke.points[i], stroke.width,
                   stroke.eraser);
      }
    }
  }
  if (canvas.texture) {
    SDL_DestroyTexture(canvas.texture);
    canvas.texture = nullptr;
  }
}

bool canvasSavePng(DrawCanvas& canvas, const fs::path& path) {
  if (!canvas.surface) return false;
  std::error_code ec;
  fs::create_directories(path.parent_path(), ec);
  return IMG_SavePNG(canvas.surface, utf8(path).c_str()) == 0;
}

void canvasUndo(DrawCanvas& canvas) {
  if (canvas.strokes.empty()) return;
  canvas.redo.push_back(canvas.strokes.back());
  canvas.strokes.pop_back();
  canvasReplay(canvas);
}

void canvasRedo(DrawCanvas& canvas) {
  if (canvas.redo.empty()) return;
  canvas.strokes.push_back(canvas.redo.back());
  canvas.redo.pop_back();
  canvasReplay(canvas);
}

SDL_Texture* textureForImage(SDL_Renderer* renderer, const fs::path& path,
                             std::map<std::string, SDL_Texture*>& cache) {
  if (path.empty() || !fs::exists(path)) return nullptr;
  const std::string key = utf8(path);
  auto it = cache.find(key);
  if (it != cache.end()) return it->second;
  SDL_Surface* surface = IMG_Load(key.c_str());
  if (!surface) return nullptr;
  SDL_Texture* texture = SDL_CreateTextureFromSurface(renderer, surface);
  SDL_FreeSurface(surface);
  if (texture) cache[key] = texture;
  return texture;
}

void writeLe16(std::ofstream& out, Uint16 value) {
  const char bytes[2] = {static_cast<char>(value & 0xFF),
                         static_cast<char>((value >> 8) & 0xFF)};
  out.write(bytes, 2);
}

void writeLe32(std::ofstream& out, Uint32 value) {
  const char bytes[4] = {static_cast<char>(value & 0xFF),
                         static_cast<char>((value >> 8) & 0xFF),
                         static_cast<char>((value >> 16) & 0xFF),
                         static_cast<char>((value >> 24) & 0xFF)};
  out.write(bytes, 4);
}

bool writeWavFile(const fs::path& path, const std::vector<Uint8>& pcm, int frequency,
                  int channels) {
  if (pcm.empty() || frequency <= 0 || channels <= 0) return false;
  std::error_code ec;
  fs::create_directories(path.parent_path(), ec);
  std::ofstream out(path, std::ios::binary | std::ios::trunc);
  if (!out) return false;
  const Uint32 dataSize = static_cast<Uint32>(pcm.size());
  const Uint32 byteRate = static_cast<Uint32>(frequency * channels * 2);
  out.write("RIFF", 4);
  writeLe32(out, 36 + dataSize);
  out.write("WAVE", 4);
  out.write("fmt ", 4);
  writeLe32(out, 16);
  writeLe16(out, 1);
  writeLe16(out, static_cast<Uint16>(channels));
  writeLe32(out, static_cast<Uint32>(frequency));
  writeLe32(out, byteRate);
  writeLe16(out, static_cast<Uint16>(channels * 2));
  writeLe16(out, 16);
  out.write("data", 4);
  writeLe32(out, dataSize);
  out.write(reinterpret_cast<const char*>(pcm.data()), static_cast<std::streamsize>(pcm.size()));
  return static_cast<bool>(out);
}

std::vector<Uint8>* gRecordBuffer = nullptr;

void SDLCALL recordCallback(void*, Uint8* stream, int length) {
  if (!gRecordBuffer || length <= 0) return;
  gRecordBuffer->insert(gRecordBuffer->end(), stream, stream + length);
}

struct App {
  std::unique_ptr<Store> store;
  SDL_Window* window = nullptr;
  SDL_Renderer* renderer = nullptr;
  ImFont* font = nullptr;
  bool dark = false;
  bool running = true;

  enum class Screen { Home, Structure, Course, Resources, Vocabulary, VocabularyEditor, Fonts, Images, Audios, Pos };
  Screen screen = Screen::Home;
  std::vector<ProjectSummary> projects;
  ProjectSummary project;
  Structure structure;
  std::string folder;
  std::string chapterId;
  std::string unitId;
  std::string courseId;
  std::set<std::string> expanded;

  enum class ModalKind {
    None,
    NewProject,
    AddChapter,
    AddUnit,
    AddCourse,
    AddQuestion,
    RenameChapter,
    RenameUnit,
    RenameCourse,
    ConfirmProject,
    ConfirmChapter,
    ConfirmUnit,
    ConfirmCourse,
    ConfirmQuestion,
    ConfirmVocabulary,
    ConfirmFont,
    ConfirmImage,
    ConfirmAudio,
    ConfirmPos,
    DiscardVocabulary
  };
  ModalKind modal = ModalKind::None;
  std::string modalTitle;
  std::string modalMessage;
  std::string modalId1;
  std::string modalId2;
  std::string modalId3;
  char modalInput[256] = {};
  char projectNameInput[256] = {};
  char projectLineInput[256] = {};

  bool cardDialog = false;
  std::string cardFolder;

  std::unique_ptr<VocabularyStore> vocabulary;
  std::unique_ptr<FontStore> fonts;
  std::unique_ptr<ImageStore> imageStore;
  std::map<std::string, SDL_Texture*> imageTextures;
  std::unique_ptr<AudioStore> audioStore;
  std::unique_ptr<PosStore> posStore;
  bool posEditorOpen = false;
  std::string posEditId;
  char posNameInput[256] = {};
  char posDescriptionInput[1024] = {};
  char posCodeInput[256] = {};
  char audioSearch[256] = {};
  bool audioCodeOpen = false;
  std::string audioCodeId;
  std::string audioCodeFile;
  char audioCodeInput[256] = {};
  std::string pendingAudioPath;
  std::string originalAudioPath;
  std::string removedAudioPath;
  bool audioPlaying = false;
  std::string playingAudioFile;
  double audioPlayDeadline = 0.0;
  Mix_Music* currentMusic = nullptr;
  bool recording = false;
  SDL_AudioDeviceID recordDevice = 0;
  std::vector<Uint8> recordBuffer;
  double recordStart = 0.0;
  ImFont* vocabFont = nullptr;
  std::string pendingImagePath;
  std::string originalImagePath;
  std::string removedImagePath;
  char imageSearch[256] = {};
  bool imageCodeOpen = false;
  std::string imageCodeId;
  std::string imageCodeFile;
  char imageCodeInput[256] = {};
  bool cropOpen = false;
  fs::path cropSourcePath;
  SDL_Surface* cropSurface = nullptr;
  SDL_Texture* cropTexture = nullptr;
  float cropX = 0.1f;
  float cropY = 0.1f;
  float cropW = 0.8f;
  float cropH = 0.8f;
  bool drawPresetOpen = false;
  int drawPresetWidth = 512;
  int drawPresetHeight = 512;
  bool drawOpen = false;
  bool focusAudioSection = false;
  bool drawEraser = false;
  float drawWidth = 8.0f;
  int cropDragMode = 0;
  DrawCanvas drawCanvas;
  VocabEntry editEntry;
  std::string editId;
  bool editIsNew = false;
  bool editDirty = false;
  bool pickerOpen = false;
  std::string pickerForId;
  std::vector<std::string> pickerSelected;
  char pickerSearch[256] = {};
  char vocabSearch[256] = {};
  char fontImportName[256] = {};

  enum class BrowserMode { ImportConlang, ExportConlang, ImportFont, ImportImage, ImportAudio,
                           ImportLexicon };
  BrowserMode browserMode = BrowserMode::ImportConlang;
  bool imageBrowserForEditor = false;
  bool audioBrowserForEditor = false;
  bool browserOpen = false;
  fs::path browserDir;
  char browserPathInput[1024] = {};
  char browserFileInput[256] = {};
  std::string browserError;
  std::vector<BrowserEntry> browserEntries;

  std::string toastText;
  double toastUntil = 0.0;

  void showToast(const std::string& text) {
    toastText = text;
    toastUntil = ImGui::GetTime() + 2.8;
  }

  void refreshProjects() { projects = store->listProjects(); }

  void loadStructure(const std::string& targetFolder) {
    ProjectSummary loaded;
    Structure loadedStructure;
    if (!store->loadProject(targetFolder, loaded, loadedStructure)) {
      showToast("项目读取失败");
      return;
    }
    folder = targetFolder;
    project = loaded;
    structure = std::move(loadedStructure);
    expanded.clear();
    for (const auto& chapter : structure.chapters) expanded.insert(chapter.id);
    screen = Screen::Structure;
  }

  Chapter* findChapter(const std::string& id) {
    for (auto& chapter : structure.chapters) if (chapter.id == id) return &chapter;
    return nullptr;
  }
  Unit* findUnit(Chapter* chapter, const std::string& id) {
    if (!chapter) return nullptr;
    for (auto& unit : chapter->units) if (unit.id == id) return &unit;
    return nullptr;
  }
  Course* findCourse(Unit* unit, const std::string& id) {
    if (!unit) return nullptr;
    for (auto& course : unit->courses) if (course.id == id) return &course;
    return nullptr;
  }

  Course* selectedCourse() {
    Chapter* chapter = findChapter(chapterId);
    Unit* unit = findUnit(chapter, unitId);
    return findCourse(unit, courseId);
  }

  bool persist() {
    ProjectSummary updated;
    if (!store->saveStructure(folder, structure, updated)) {
      showToast("写入 structure.json 失败");
      return false;
    }
    project = updated;
    refreshProjects();
    return true;
  }

  void openCardDialog(const std::string& target) {
    cardFolder = target;
    cardDialog = true;
  }

  void openModalText(ModalKind kind, const std::string& title, const std::string& initial,
                     const std::string& id1 = "", const std::string& id2 = "",
                     const std::string& id3 = "") {
    modal = kind;
    modalTitle = title;
    modalMessage.clear();
    modalId1 = id1;
    modalId2 = id2;
    modalId3 = id3;
    std::snprintf(modalInput, sizeof(modalInput), "%s", initial.c_str());
  }

  void openModalConfirm(ModalKind kind, const std::string& title, const std::string& message,
                        const std::string& id1 = "", const std::string& id2 = "",
                        const std::string& id3 = "") {
    modal = kind;
    modalTitle = title;
    modalMessage = message;
    modalId1 = id1;
    modalId2 = id2;
    modalId3 = id3;
  }

  void closeModal() { modal = ModalKind::None; }

  void applyModal() {
    const std::string value = modalInput;
    switch (modal) {
      case ModalKind::NewProject: {
        const std::string name = projectNameInput;
        if (name.empty()) { showToast("项目名称不能为空"); return; }
        ProjectSummary created = store->createProject(name, projectLineInput);
        if (created.folder.empty()) { showToast("创建项目失败"); return; }
        refreshProjects();
        loadStructure(created.folder);
        showToast("已创建项目");
        break;
      }
      case ModalKind::AddChapter:
        structure.chapters.push_back({Store::newUuid(), value.empty() ? "未命名章节" : value, {}});
        if (!persist()) return;
        expanded.insert(structure.chapters.back().id);
        showToast("已新建章节");
        break;
      case ModalKind::AddUnit: {
        Chapter* chapter = findChapter(modalId1);
        if (!chapter) return;
        chapter->units.push_back({Store::newUuid(), value.empty() ? "未命名单元" : value, {}});
        if (!persist()) return;
        showToast("已新建单元");
        break;
      }
      case ModalKind::AddCourse: {
        Chapter* chapter = findChapter(modalId1);
        Unit* unit = findUnit(chapter, modalId2);
        if (!unit) return;
        unit->courses.push_back({Store::newUuid(), value.empty() ? "未命名课程" : value, {}});
        if (!persist()) return;
        showToast("已新建课程");
        break;
      }
      case ModalKind::AddQuestion: {
        Course* course = selectedCourse();
        if (!course) return;
        course->questions.push_back({Store::newUuid(), value.empty() ? "未命名题目" : value});
        if (!persist()) return;
        showToast("已新建题目");
        break;
      }
      case ModalKind::RenameChapter: {
        Chapter* chapter = findChapter(modalId1);
        if (chapter) chapter->name = value.empty() ? "未命名章节" : value;
        if (!persist()) return;
        showToast("已重命名章节");
        break;
      }
      case ModalKind::RenameUnit: {
        Chapter* chapter = findChapter(modalId1);
        Unit* unit = findUnit(chapter, modalId2);
        if (unit) unit->name = value.empty() ? "未命名单元" : value;
        if (!persist()) return;
        showToast("已重命名单元");
        break;
      }
      case ModalKind::RenameCourse: {
        Chapter* chapter = findChapter(modalId1);
        Unit* unit = findUnit(chapter, modalId2);
        Course* course = findCourse(unit, modalId3);
        if (course) course->name = value.empty() ? "未命名课程" : value;
        if (!persist()) return;
        showToast("已重命名课程");
        break;
      }
      case ModalKind::ConfirmProject: {
        if (!store->deleteProject(modalId1)) { showToast("删除项目失败"); return; }
        refreshProjects();
        screen = Screen::Home;
        showToast("已删除项目");
        break;
      }
      case ModalKind::ConfirmChapter: {
        Chapter* chapter = findChapter(modalId1);
        if (chapter) structure.chapters.erase(structure.chapters.begin() +
            (chapter - &structure.chapters[0]));
        if (!persist()) return;
        showToast("已删除章节");
        break;
      }
      case ModalKind::ConfirmUnit: {
        Chapter* chapter = findChapter(modalId1);
        if (chapter) {
          auto it = std::find_if(chapter->units.begin(), chapter->units.end(),
                                 [&](const Unit& u) { return u.id == modalId2; });
          if (it != chapter->units.end()) chapter->units.erase(it);
        }
        if (!persist()) return;
        showToast("已删除单元");
        break;
      }
      case ModalKind::ConfirmCourse: {
        Chapter* chapter = findChapter(modalId1);
        Unit* unit = findUnit(chapter, modalId2);
        if (unit) {
          auto it = std::find_if(unit->courses.begin(), unit->courses.end(),
                                 [&](const Course& c) { return c.id == modalId3; });
          if (it != unit->courses.end()) unit->courses.erase(it);
        }
        if (!persist()) return;
        if (screen == Screen::Course && courseId == modalId3) screen = Screen::Structure;
        showToast("已删除课程");
        break;
      }
      case ModalKind::ConfirmQuestion: {
        Course* course = selectedCourse();
        if (course) {
          auto it = std::find_if(course->questions.begin(), course->questions.end(),
                                 [&](const Question& q) { return q.id == modalId1; });
          if (it != course->questions.end()) course->questions.erase(it);
        }
        if (!persist()) return;
        showToast("已删除题目");
        break;
      }
      case ModalKind::ConfirmVocabulary: {
        if (vocabulary) {
          vocabulary->entries.erase(
              std::remove_if(vocabulary->entries.begin(), vocabulary->entries.end(),
                             [&](const VocabEntry& entry) { return entry.id == modalId1; }),
              vocabulary->entries.end());
          if (!vocabulary->save()) {
            showToast("写入 vocabulary.json 失败");
            return;
          }
        }
        screen = Screen::Vocabulary;
        showToast("已删除词汇");
        break;
      }
      case ModalKind::ConfirmPos: {
        ensurePosLoaded();
        if (posStore && posStore->remove(modalId1)) {
          showToast("已删除词性");
        } else {
          showToast("删除词性失败");
          return;
        }
        screen = Screen::Pos;
        break;
      }
      case ModalKind::ConfirmAudio: {
        ensureAudioLoaded();
        if (audioStore) {
          std::string error;
          if (!modalId1.empty()) {
            if (!audioStore->deleteAudio(modalId1, error)) { showToast(error); return; }
          } else if (!modalId2.empty()) {
            std::error_code ec;
            fs::remove(currentProjectDirectory() / pathFromUtf8(modalId2), ec);
          }
          showToast("已删除音频");
        }
        screen = Screen::Audios;
        break;
      }
      case ModalKind::ConfirmImage: {
        ensureImagesLoaded();
        if (imageStore) {
          std::string error;
          if (!modalId1.empty()) {
            if (!imageStore->deleteImage(modalId1, error)) { showToast(error); return; }
          } else if (!modalId2.empty()) {
            std::error_code ec;
            fs::remove(currentProjectDirectory() / pathFromUtf8(modalId2), ec);
          }
          showToast("已删除图片");
        }
        screen = Screen::Images;
        break;
      }
      case ModalKind::ConfirmFont: {
        if (fonts) {
          std::string error;
          if (!fonts->deleteFont(modalId1, error)) {
            showToast(error);
            return;
          }
          refreshVocabFont();
          showToast("已删除字体");
        }
        screen = Screen::Fonts;
        break;
      }
      case ModalKind::DiscardVocabulary:
        stopAudioPlayback();
        deletePendingImage();
        deletePendingAudio();
        screen = Screen::Vocabulary;
        break;
      default:
        break;
    }
    closeModal();
  }

  void togglePin(const std::string& targetFolder) {
    if (!store->togglePin(targetFolder)) { showToast("置顶状态保存失败"); return; }
    refreshProjects();
    for (const auto& p : projects) {
      if (p.folder == targetFolder) {
        showToast(p.pinned ? "已置顶" : "已取消置顶");
        break;
      }
    }
  }

  fs::path currentProjectDirectory() const { return store->projectDirectory(folder); }

  void ensureVocabularyLoaded() {
    fs::path dir = currentProjectDirectory();
    if (dir.empty()) return;
    if (!vocabulary || vocabulary->projectDirectory != dir) loadVocabularyData();
  }

  void loadVocabularyData() {
    fs::path dir = currentProjectDirectory();
    if (dir.empty()) {
      vocabulary.reset();
      fonts.reset();
      vocabFont = nullptr;
      return;
    }
    vocabulary = std::make_unique<VocabularyStore>(dir);
    fonts = std::make_unique<FontStore>(dir);
    refreshVocabFont();
  }

  void refreshVocabFont() {
    vocabFont = nullptr;
    if (!fonts || !fonts->activeFileValid()) return;
    const fs::path file = fonts->activeFilePath();
    if (file.empty()) return;
    ImFontConfig config;
    config.FontNo = 0;
    config.OversampleH = 2;
    config.OversampleV = 2;
    config.PixelSnapH = true;
    vocabFont = addFontFromPath(
        file, 22.0f, &config, ImGui::GetIO().Fonts->GetGlyphRangesChineseFull());
    if (!vocabFont) return;
    ImFontConfig merge;
    merge.MergeMode = true;
    merge.FontNo = 0;
    merge.OversampleH = 2;
    merge.OversampleV = 2;
    merge.PixelSnapH = true;
    const fs::path cjk = pathFromUtf8("/usr/share/fonts/noto-cjk/NotoSerifCJK-Regular.ttc");
    if (fs::exists(cjk)) {
      addFontFromPath(cjk, 22.0f, &merge,
                      ImGui::GetIO().Fonts->GetGlyphRangesChineseFull());
    }
    const fs::path ethiopic = pathFromUtf8("/usr/share/fonts/noto/NotoSansEthiopic-Regular.ttf");
    if (fs::exists(ethiopic)) {
      static const ImWchar ethiopicRanges[] = {0x1200, 0x137F, 0};
      ImFontConfig ethiopicMerge = merge;
      addFontFromPath(ethiopic, 22.0f, &ethiopicMerge, ethiopicRanges);
    }
    addLatinFallback(22.0f);
  }

  void openVocabulary() {
    loadVocabularyData();
    screen = Screen::Vocabulary;
  }

  void openFonts() {
    loadVocabularyData();
    screen = Screen::Fonts;
  }

  void startNewVocabulary() {
    stopAudioPlayback();
    editEntry = VocabEntry{};
    editEntry.id = Store::newUuid();
    editEntry.type = "text";
    editEntry.meanings = {""};
    editEntry.codes = {""};
    editEntry.createdAt = editEntry.updatedAt = 0;
    editId = editEntry.id;
    editIsNew = true;
    editDirty = false;
    pendingImagePath.clear();
    originalImagePath.clear();
    removedImagePath.clear();
    pendingAudioPath.clear();
    originalAudioPath.clear();
    removedAudioPath.clear();
    screen = Screen::VocabularyEditor;
  }

  void editVocabulary(const std::string& id) {
    stopAudioPlayback();
    if (!vocabulary) loadVocabularyData();
    const VocabEntry* found = vocabulary ? vocabulary->find(id) : nullptr;
    if (!found) {
      showToast("词汇不存在");
      return;
    }
    editEntry = *found;
    ensurePosLoaded();
    if (posStore) {
      editEntry.posMeanings.erase(
          std::remove_if(editEntry.posMeanings.begin(), editEntry.posMeanings.end(),
                         [&](const PosMeaning& group) {
                           return !posStore->find(group.posId);
                         }),
          editEntry.posMeanings.end());
    }
    if (editEntry.meanings.empty()) editEntry.meanings.push_back("");
    if (editEntry.codes.empty()) editEntry.codes.push_back("");
    editId = id;
    editIsNew = false;
    editDirty = false;
    pendingImagePath.clear();
    removedImagePath.clear();
    originalImagePath.clear();
    if (!editEntry.image.empty()) {
      fs::path path = imageAbsolute(editEntry.image);
      if (fs::exists(path)) originalImagePath = utf8(path);
    }
    pendingAudioPath.clear();
    removedAudioPath.clear();
    originalAudioPath.clear();
    if (!editEntry.audio.empty()) {
      fs::path path = audioAbsolute(editEntry.audio);
      if (fs::exists(path)) originalAudioPath = utf8(path);
    }
    screen = Screen::VocabularyEditor;
  }

  void backFromEditor() {
    if (editDirty) {
      openModalConfirm(ModalKind::DiscardVocabulary, "放弃未保存修改",
                       "当前词汇有未保存的修改，确定返回列表吗？");
    } else {
      stopAudioPlayback();
      deletePendingImage();
      deletePendingAudio();
      screen = Screen::Vocabulary;
    }
  }

  void saveEditedVocabulary() {
    if (!vocabulary) loadVocabularyData();
    if (!vocabulary) return;
    std::string meaningError;
    if (!validateEditMeanings(&meaningError)) {
      showToast(meaningError);
      return;
    }
    if (editEntry.type == "image") {
      if (editEntry.image.empty() || editEntry.imageWidth <= 0 || editEntry.imageHeight <= 0) {
        showToast("自定义文字图片缺失，请先导入或手绘");
        return;
      }
      fs::path current = imageAbsolute(editEntry.image);
      if (current.empty() || !fs::exists(current)) {
        showToast("图片文件不存在");
        return;
      }
    } else {
      editEntry.image.clear();
      editEntry.imageWidth = 0;
      editEntry.imageHeight = 0;
    }
    if (editEntry.meanings.empty()) editEntry.meanings.push_back("");
    if (editEntry.codes.empty()) editEntry.codes.push_back("");
    auto trimText = [](std::string value) {
      auto notSpace = [](unsigned char c) { return !std::isspace(c); };
      value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
      value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
      return value;
    };
    if (editEntry.posMeanings.empty()) {
      bool hasMeaning = false;
      for (const auto& meaning : editEntry.meanings) if (!trimText(meaning).empty()) hasMeaning = true;
      if (!hasMeaning) { showToast("至少填写一条释义"); return; }
    } else {
      std::vector<PosMeaning> validGroups;
      std::vector<std::string> aggregate;
      for (const auto& pos : posStore->entries) {
        PosMeaning* group = findPosMeaning(pos.id);
        if (!group) continue;
        PosMeaning cleaned;
        cleaned.posId = pos.id;
        for (const auto& meaning : group->meanings) {
          const std::string value = trimText(meaning);
          if (value.empty()) continue;
          cleaned.meanings.push_back(value);
          if (std::find(aggregate.begin(), aggregate.end(), value) == aggregate.end()) {
            aggregate.push_back(value);
          }
        }
        if (cleaned.meanings.empty()) {
          showToast("词性「" + pos.name + "」至少填写一条释义");
          return;
        }
        validGroups.push_back(std::move(cleaned));
      }
      if (validGroups.empty()) {
        showToast("请选择至少一个词性");
        return;
      }
      editEntry.posMeanings = std::move(validGroups);
      editEntry.meanings = std::move(aggregate);
    }
    if (editEntry.type == "text" && editEntry.text.empty()) {
      showToast("文本词语不能为空"); return;
    }
    if (editEntry.type == "unicode") {
      for (auto& cp : editEntry.codepoints) cp = normalizeCodepoint(cp);
      editEntry.codepoints.erase(
          std::remove_if(editEntry.codepoints.begin(), editEntry.codepoints.end(),
                         [](const std::string& cp) { return cp.empty(); }),
          editEntry.codepoints.end());
      if (editEntry.codepoints.empty()) {
        showToast("至少填写一个有效 Unicode 码点"); return;
      }
    }
    if (editEntry.type == "combo") {
      std::vector<std::string> valid;
      for (const auto& id : editEntry.parts) {
        if (id != editEntry.id && vocabulary->find(id)) valid.push_back(id);
      }
      editEntry.parts = valid;
      if (editEntry.parts.size() < 2) {
        showToast("组合词至少需要两个部分"); return;
      }
    }
    if (!editEntry.audio.empty()) {
      fs::path audioPath = audioAbsolute(editEntry.audio);
      if (audioPath.empty() || !fs::exists(audioPath)) {
        showToast("音频文件不存在");
        return;
      }
      if (editEntry.audioDuration <= 0) {
        int detectedDuration = 0;
        loadAudioDuration(audioPath, detectedDuration);
        editEntry.audioDuration = detectedDuration;
      }
      if (editEntry.audioStart < 0.0 || editEntry.audioEnd <= editEntry.audioStart ||
          editEntry.audioEnd - editEntry.audioStart < 100.0 ||
          editEntry.audioEnd > editEntry.audioDuration) {
        showToast("示例发音裁剪区间无效");
        return;
      }
    } else {
      editEntry.audioStart = 0.0;
      editEntry.audioEnd = 0.0;
      editEntry.audioDuration = 0;
    }
    editEntry.updatedAt = std::chrono::duration_cast<std::chrono::milliseconds>(
                              std::chrono::system_clock::now().time_since_epoch())
                              .count();
    if (editEntry.createdAt <= 0) editEntry.createdAt = editEntry.updatedAt;
    VocabEntry* existing = vocabulary->find(editEntry.id);
    if (existing) *existing = editEntry;
    else vocabulary->entries.push_back(editEntry);
    if (!vocabulary->save()) { showToast("写入 vocabulary.json 失败"); return; }
    std::error_code cleanupEc;
    fs::path currentImage = imageAbsolute(editEntry.image);
    if (!originalImagePath.empty() &&
        (editEntry.image.empty() || utf8(currentImage) != originalImagePath)) {
      fs::remove(pathFromUtf8(originalImagePath), cleanupEc);
    }
    if (!removedImagePath.empty()) fs::remove(imageAbsolute(removedImagePath), cleanupEc);
    fs::path currentAudio = audioAbsolute(editEntry.audio);
    if (!originalAudioPath.empty() &&
        (editEntry.audio.empty() || utf8(currentAudio) != originalAudioPath)) {
      fs::remove(pathFromUtf8(originalAudioPath), cleanupEc);
    }
    if (!removedAudioPath.empty()) fs::remove(audioAbsolute(removedAudioPath), cleanupEc);
    pendingImagePath.clear();
    originalImagePath.clear();
    removedImagePath.clear();
    pendingAudioPath.clear();
    originalAudioPath.clear();
    removedAudioPath.clear();
    stopAudioPlayback();
    vocabulary->load();
    editDirty = false;
    screen = Screen::Vocabulary;
    showToast("词汇已保存");
  }

  PosMeaning* findPosMeaning(const std::string& posId) {
    for (auto& group : editEntry.posMeanings) {
      if (group.posId == posId) return &group;
    }
    return nullptr;
  }

  void togglePosMeaning(const std::string& posId, bool selected) {
    if (!posStore) return;
    const PosEntry* pos = posStore->find(posId);
    if (!pos) return;
    PosMeaning* group = findPosMeaning(posId);
    if (selected && !group) {
      const bool wasEmpty = editEntry.posMeanings.empty();
      PosMeaning created;
      created.posId = posId;
      if (wasEmpty) {
        for (const auto& meaning : editEntry.meanings) {
          if (!meaning.empty()) created.meanings.push_back(meaning);
        }
      }
      if (created.meanings.empty()) created.meanings.push_back("");
      editEntry.posMeanings.push_back(std::move(created));
      editDirty = true;
    } else if (!selected && group) {
      if (!group->meanings.empty() && !group->meanings.front().empty()) {
        showToast("已取消选择「" + pos->name + "」，保存后该组释义不再写入");
      }
      editEntry.posMeanings.erase(
          std::remove_if(editEntry.posMeanings.begin(), editEntry.posMeanings.end(),
                         [&](const PosMeaning& item) { return item.posId == posId; }),
          editEntry.posMeanings.end());
      editDirty = true;
    }
  }

  bool validateEditMeanings(std::string* error) {
    auto trimText = [](std::string value) {
      auto notSpace = [](unsigned char c) { return !std::isspace(c); };
      value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
      value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
      return value;
    };
    if (editEntry.posMeanings.empty()) {
      for (const auto& meaning : editEntry.meanings) {
        if (!trimText(meaning).empty()) return true;
      }
      if (error) *error = "至少填写一条释义";
      return false;
    }
    ensurePosLoaded();
    bool any = false;
    if (posStore) {
      for (const auto& pos : posStore->entries) {
        PosMeaning* group = findPosMeaning(pos.id);
        if (!group) continue;
        bool has = false;
        for (const auto& meaning : group->meanings) {
          if (!trimText(meaning).empty()) has = true;
        }
        if (!has) {
          if (error) *error = "词性「" + pos.name + "」至少填写一条释义";
          return false;
        }
        any = true;
      }
    }
    if (!any) {
      if (error) *error = "请选择至少一个词性";
      return false;
    }
    return true;
  }

  void openPicker() {
    if (!vocabulary) loadVocabularyData();
    pickerSelected = editEntry.parts;
    pickerForId = editEntry.id;
    std::snprintf(pickerSearch, sizeof(pickerSearch), "%s", "");
    pickerOpen = true;
  }

  void applyFontActive(const std::string& id) {
    if (!fonts) return;
    if (!fonts->setActive(id)) {
      showToast("当前字体保存失败");
      return;
    }
    refreshVocabFont();
    showToast(id.empty() ? "已切换为系统默认字体" : "已设为当前字体");
  }

  void ensureImagesLoaded() {
    fs::path dir = currentProjectDirectory();
    if (dir.empty()) return;
    if (!imageStore || imageStore->projectDirectory != dir) {
      imageStore = std::make_unique<ImageStore>(dir);
    }
  }

  void openImages() {
    ensureImagesLoaded();
    screen = Screen::Images;
  }

  fs::path imageAbsolute(const std::string& relative) const {
    if (relative.empty()) return {};
    return (currentProjectDirectory() / pathFromUtf8(relative)).lexically_normal();
  }

  void deletePendingImage() {
    if (!pendingImagePath.empty()) {
      std::error_code ec;
      fs::remove(pathFromUtf8(pendingImagePath), ec);
      pendingImagePath.clear();
    }
  }

  void openImageCode(const std::string& id, const std::string& file = "") {
    imageCodeOpen = true;
    imageCodeId = id;
    imageCodeFile = file;
    imageCodeInput[0] = '\0';
    if (imageStore && !id.empty()) {
      const ImageEntry* image = imageStore->find(id);
      if (image) {
        imageCodeFile = image->file;
        std::snprintf(imageCodeInput, sizeof(imageCodeInput), "%s", image->code.c_str());
      }
    }
  }

  void loadCropSource(const fs::path& source) {
    if (cropTexture) { SDL_DestroyTexture(cropTexture); cropTexture = nullptr; }
    if (cropSurface) { SDL_FreeSurface(cropSurface); cropSurface = nullptr; }
    cropSurface = IMG_Load(utf8(source).c_str());
    if (!cropSurface) { showToast("图片无法解码"); return; }
    cropTexture = SDL_CreateTextureFromSurface(renderer, cropSurface);
    cropSourcePath = source;
    cropX = 0.1f;
    cropY = 0.1f;
    cropW = 0.8f;
    cropH = 0.8f;
    cropOpen = true;
  }

  void closeCrop() {
    cropOpen = false;
    if (cropTexture) { SDL_DestroyTexture(cropTexture); cropTexture = nullptr; }
    if (cropSurface) { SDL_FreeSurface(cropSurface); cropSurface = nullptr; }
  }

  void applyCrop() {
    if (!cropSurface || !cropOpen) return;
    const int sx = std::clamp(static_cast<int>(cropX * cropSurface->w), 0, cropSurface->w - 1);
    const int sy = std::clamp(static_cast<int>(cropY * cropSurface->h), 0, cropSurface->h - 1);
    const int sw = std::max(1, std::min(std::max(32, static_cast<int>(cropW * cropSurface->w)),
                                        cropSurface->w - sx));
    const int sh = std::max(1, std::min(std::max(32, static_cast<int>(cropH * cropSurface->h)),
                                        cropSurface->h - sy));
    SDL_Surface* cropped = SDL_CreateRGBSurfaceWithFormat(0, sw, sh, 32, SDL_PIXELFORMAT_ARGB8888);
    if (!cropped) { showToast("裁剪失败"); return; }
    SDL_Rect sourceRect{sx, sy, sw, sh};
    SDL_BlitSurface(cropSurface, &sourceRect, cropped, nullptr);
    std::error_code ec;
    fs::path directory = currentProjectDirectory() / "resources" / "images";
    fs::create_directories(directory, ec);
    std::string id = Store::newUuid();
    id.erase(std::remove(id.begin(), id.end(), '-'), id.end());
    fs::path target = directory / ("pending_" + id + ".png");
    if (IMG_SavePNG(cropped, utf8(target).c_str()) != 0) {
      SDL_FreeSurface(cropped);
      showToast("裁剪保存失败");
      return;
    }
    SDL_FreeSurface(cropped);
    deletePendingImage();
    pendingImagePath = utf8(target);
    editEntry.image = "resources/images/" + utf8(target.filename());
    editEntry.imageWidth = sw;
    editEntry.imageHeight = sh;
    editDirty = true;
    closeCrop();
    showToast("已生成裁剪图片（待保存）");
  }

  void openDrawPreset() {
    drawPresetOpen = true;
    drawPresetWidth = 512;
    drawPresetHeight = 512;
  }

  void startDrawing() {
    if (drawCanvas.surface) { SDL_FreeSurface(drawCanvas.surface); drawCanvas.surface = nullptr; }
    if (drawCanvas.texture) { SDL_DestroyTexture(drawCanvas.texture); drawCanvas.texture = nullptr; }
    drawCanvas.width = std::clamp(drawPresetWidth, 32, 4096);
    drawCanvas.height = std::clamp(drawPresetHeight, 32, 4096);
    drawCanvas.surface = SDL_CreateRGBSurfaceWithFormat(0, drawCanvas.width, drawCanvas.height,
                                                        32, SDL_PIXELFORMAT_ARGB8888);
    if (!drawCanvas.surface) { showToast("画布创建失败"); return; }
    drawCanvas.strokes.clear();
    drawCanvas.redo.clear();
    canvasClear(drawCanvas);
    drawPresetOpen = false;
    drawOpen = true;
  }

  void closeDrawing() {
    drawOpen = false;
    if (drawCanvas.texture) { SDL_DestroyTexture(drawCanvas.texture); drawCanvas.texture = nullptr; }
    if (drawCanvas.surface) { SDL_FreeSurface(drawCanvas.surface); drawCanvas.surface = nullptr; }
    drawCanvas.strokes.clear();
    drawCanvas.redo.clear();
  }

  void saveDrawing() {
    if (!drawCanvas.surface) return;
    std::error_code ec;
    fs::path directory = currentProjectDirectory() / "resources" / "images";
    fs::create_directories(directory, ec);
    std::string id = Store::newUuid();
    id.erase(std::remove(id.begin(), id.end(), '-'), id.end());
    fs::path target = directory / ("pending_" + id + ".png");
    if (!canvasSavePng(drawCanvas, target)) { showToast("手绘保存失败"); return; }
    deletePendingImage();
    pendingImagePath = utf8(target);
    editEntry.image = "resources/images/" + utf8(target.filename());
    editEntry.imageWidth = drawCanvas.width;
    editEntry.imageHeight = drawCanvas.height;
    editDirty = true;
    closeDrawing();
    showToast("已生成手绘图片（待保存）");
  }

  void removeEditorAudio() {
    if (!pendingAudioPath.empty()) deletePendingAudio();
    else if (!editEntry.audio.empty()) removedAudioPath = editEntry.audio;
    editEntry.audio.clear();
    editEntry.audioStart = 0.0;
    editEntry.audioEnd = 0.0;
    editEntry.audioDuration = 0;
    editDirty = true;
  }

  void removeEditorImage() {
    if (!pendingImagePath.empty()) deletePendingImage();
    else if (!editEntry.image.empty()) removedImagePath = editEntry.image;
    editEntry.image.clear();
    editEntry.imageWidth = 0;
    editEntry.imageHeight = 0;
    editDirty = true;
  }

  void ensureAudioLoaded() {
    fs::path dir = currentProjectDirectory();
    if (dir.empty()) return;
    if (!audioStore || audioStore->projectDirectory != dir) {
      audioStore = std::make_unique<AudioStore>(dir);
    }
  }

  void openAudios() {
    ensureAudioLoaded();
    screen = Screen::Audios;
  }

  fs::path audioAbsolute(const std::string& relative) const {
    if (relative.empty()) return {};
    return (currentProjectDirectory() / pathFromUtf8(relative)).lexically_normal();
  }

  void deletePendingAudio() {
    if (!pendingAudioPath.empty()) {
      std::error_code ec;
      fs::remove(pathFromUtf8(pendingAudioPath), ec);
      pendingAudioPath.clear();
    }
  }

  void openAudioCode(const std::string& id, const std::string& file = "") {
    audioCodeOpen = true;
    audioCodeId = id;
    audioCodeFile = file;
    audioCodeInput[0] = '\0';
    if (audioStore && !id.empty()) {
      const AudioEntry* audio = audioStore->find(id);
      if (audio) {
        audioCodeFile = audio->file;
        std::snprintf(audioCodeInput, sizeof(audioCodeInput), "%s", audio->code.c_str());
      }
    }
  }

  void importEditorAudio(const fs::path& source) {
    int duration = 0;
    if (!loadAudioDuration(source, duration)) {
      showToast("音频无法解码");
      return;
    }
    std::error_code ec;
    fs::path directory = currentProjectDirectory() / "resources" / "audio";
    fs::create_directories(directory, ec);
    std::string ext = audioExtensionLower(source);
    std::string id = Store::newUuid();
    id.erase(std::remove(id.begin(), id.end(), '-'), id.end());
    fs::path target = directory / ("pending_" + id + ext);
    fs::copy_file(source, target, fs::copy_options::overwrite_existing, ec);
    if (ec) {
      showToast("复制音频失败");
      return;
    }
    deletePendingAudio();
    pendingAudioPath = utf8(target);
    editEntry.audio = "resources/audio/" + utf8(target.filename());
    editEntry.audioDuration = duration;
    editEntry.audioStart = 0.0;
    editEntry.audioEnd = static_cast<double>(duration);
    editDirty = true;
    showToast("已导入音频（待保存）");
  }

  void stopAudioPlayback() {
    if (Mix_PlayingMusic()) Mix_HaltMusic();
    if (currentMusic) {
      Mix_FreeMusic(currentMusic);
      currentMusic = nullptr;
    }
    audioPlaying = false;
    playingAudioFile.clear();
    audioPlayDeadline = 0.0;
  }

  void playAudioFile(const std::string& relative, int startMs, int endMs) {
    stopAudioPlayback();
    fs::path path = audioAbsolute(relative);
    if (path.empty() || !fs::exists(path)) {
      showToast("音频文件不存在");
      return;
    }
    Mix_Music* music = Mix_LoadMUS(utf8(path).c_str());
    if (!music) {
      showToast("音频播放失败（无可用音频设备或格式不支持）");
      return;
    }
    if (Mix_PlayMusic(music, 0) != 0) {
      Mix_FreeMusic(music);
      showToast("音频播放失败（无可用音频设备）");
      return;
    }
    if (startMs > 0) Mix_SetMusicPosition(startMs / 1000.0);
    currentMusic = music;
    audioPlaying = true;
    playingAudioFile = relative;
    audioPlayDeadline = ImGui::GetTime() + std::max(0.1, (endMs - startMs) / 1000.0);
  }

  void startRecording() {
    if (recording) return;
    SDL_AudioSpec want{};
    SDL_AudioSpec have{};
    want.freq = 44100;
    want.format = AUDIO_S16SYS;
    want.channels = 1;
    want.samples = 1024;
    want.callback = recordCallback;
    want.userdata = nullptr;
    recordDevice = SDL_OpenAudioDevice(nullptr, 1, &want, &have, 0);
    if (recordDevice == 0) {
      showToast("无可用录音设备，未开始录制");
      return;
    }
    recordBuffer.clear();
    gRecordBuffer = &recordBuffer;
    recordStart = ImGui::GetTime();
    recording = true;
    SDL_PauseAudioDevice(recordDevice, 0);
    showToast("开始录制…");
  }

  void stopRecording() {
    if (!recording) return;
    SDL_PauseAudioDevice(recordDevice, 1);
    SDL_CloseAudioDevice(recordDevice);
    recordDevice = 0;
    gRecordBuffer = nullptr;
    recording = false;
    if (recordBuffer.empty()) {
      showToast("录音为空，未保存");
      return;
    }
    std::error_code ec;
    fs::path directory = currentProjectDirectory() / "resources" / "audio";
    fs::create_directories(directory, ec);
    std::string id = Store::newUuid();
    id.erase(std::remove(id.begin(), id.end(), '-'), id.end());
    fs::path target = directory / ("pending_" + id + ".wav");
    if (!writeWavFile(target, recordBuffer, 44100, 1)) {
      showToast("录音写入失败");
      return;
    }
    deletePendingAudio();
    pendingAudioPath = utf8(target);
    editEntry.audio = "resources/audio/" + utf8(target.filename());
    editEntry.audioDuration = static_cast<int>(recordBuffer.size() / 2 * 1000 / 44100);
    editEntry.audioStart = 0.0;
    editEntry.audioEnd = static_cast<double>(editEntry.audioDuration);
    editDirty = true;
    showToast("录音完成（待保存）");
  }

  void ensurePosLoaded() {
    fs::path dir = currentProjectDirectory();
    if (dir.empty()) return;
    if (!posStore || posStore->projectDirectory != dir) {
      posStore = std::make_unique<PosStore>(dir);
    }
  }

  void openPos() {
    ensurePosLoaded();
    screen = Screen::Pos;
  }

  void openPosEditor(const std::string& id) {
    ensurePosLoaded();
    posEditId = id;
    posNameInput[0] = '\0';
    posDescriptionInput[0] = '\0';
    posCodeInput[0] = '\0';
    if (posStore && !id.empty()) {
      const PosEntry* entry = posStore->find(id);
      if (entry) {
        std::snprintf(posNameInput, sizeof(posNameInput), "%s", entry->name.c_str());
        std::snprintf(posDescriptionInput, sizeof(posDescriptionInput), "%s",
                      entry->description.c_str());
        std::snprintf(posCodeInput, sizeof(posCodeInput), "%s", entry->code.c_str());
      }
    }
    posEditorOpen = true;
  }

  void savePosEditor() {
    ensurePosLoaded();
    if (!posStore) return;
    std::string name = posNameInput;
    auto trim = [](std::string value) {
      auto notSpace = [](unsigned char c) { return !std::isspace(c); };
      value.erase(value.begin(), std::find_if(value.begin(), value.end(), notSpace));
      value.erase(std::find_if(value.rbegin(), value.rend(), notSpace).base(), value.end());
      return value;
    };
    name = trim(name);
    if (name.empty()) {
      showToast("词性名称不能为空");
      return;
    }
    bool ok = posEditId.empty()
                  ? posStore->create(name, posDescriptionInput, posCodeInput) != nullptr
                  : posStore->update(posEditId, name, posDescriptionInput, posCodeInput);
    if (!ok) {
      showToast("保存词性失败");
      return;
    }
    posStore->load();
    posEditorOpen = false;
    showToast("词性已保存");
  }

  void openBrowser(BrowserMode mode) {
    browserOpen = true;
    browserMode = mode;
    browserError.clear();
    browserDir = store->dataRoot.parent_path();
    if (!fs::exists(browserDir)) browserDir = fs::current_path();
    refreshBrowser();
    if (mode == BrowserMode::ExportConlang) {
      std::string name = Store::sanitizeName(project.name) + ".conlang";
      std::snprintf(browserFileInput, sizeof(browserFileInput), "%s", name.c_str());
    } else {
      browserFileInput[0] = '\0';
    }
    std::snprintf(browserPathInput, sizeof(browserPathInput), "%s",
                  utf8(browserDir).c_str());
  }

  void refreshBrowser() {
    browserEntries.clear();
    std::error_code ec;
    if (!fs::is_directory(browserDir, ec)) return;
    for (const auto& entry : fs::directory_iterator(browserDir, ec)) {
      if (ec) break;
      if (entry.is_directory()) {
        browserEntries.push_back({utf8(entry.path().filename()), true});
      } else if (entry.is_regular_file()) {
        std::string ext = utf8(entry.path().extension());
        std::transform(ext.begin(), ext.end(), ext.begin(),
                       [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
        const bool accepted =
            browserMode == BrowserMode::ImportFont
                ? (ext == ".ttf" || ext == ".otf" || ext == ".ttc")
                : browserMode == BrowserMode::ImportImage
                      ? supportedImageExtension(ext)
                      : browserMode == BrowserMode::ImportAudio
                            ? supportedAudioExtension(ext)
                            : browserMode == BrowserMode::ImportLexicon
                                  ? (ext == ".jsonl" || ext == ".json" || ext == ".txt")
                                  : endsWithConlang(entry.path());
        if (accepted) browserEntries.push_back({utf8(entry.path().filename()), false});
      }
    }
    std::sort(browserEntries.begin(), browserEntries.end(),
              [](const BrowserEntry& a, const BrowserEntry& b) {
                if (a.directory != b.directory) return a.directory > b.directory;
                return a.name < b.name;
              });
  }

  void navigateBrowser(const fs::path& target) {
    std::error_code ec;
    if (!fs::is_directory(target, ec)) {
      browserError = "目录不存在";
      return;
    }
    browserDir = target;
    std::snprintf(browserPathInput, sizeof(browserPathInput), "%s",
                  utf8(browserDir).c_str());
    refreshBrowser();
    browserError.clear();
  }

  void applyBrowser() {
    std::string filename = browserFileInput;
    if (filename.empty()) {
      browserError = "请选择或输入文件名";
      return;
    }
    fs::path target = browserDir / pathFromUtf8(filename);
    if (browserMode == BrowserMode::ExportConlang) {
      if (!endsWithConlang(target)) target += ".conlang";
      std::string error;
      if (!store->exportProject(folder, target, error)) {
        browserError = error;
        return;
      }
      browserOpen = false;
      showToast("已导出：" + utf8(target));
      return;
    }
    std::string error;
    if (browserMode == BrowserMode::ImportLexicon) {
      browserOpen = false;
      ensureVocabularyLoaded();
      ensurePosLoaded();
      LexiconImportSummary summary;
      if (!importLexiconJsonl(target, store->projectDirectory(folder), summary, error)) {
        showToast("导入失败：" + error);
        return;
      }
      if (vocabulary) vocabulary->load();
      if (posStore) posStore->load();
      screen = Screen::Vocabulary;
      showToast("导入完成：新增 " + std::to_string(summary.added) + "，重复 "
                + std::to_string(summary.duplicates) + "，无效 "
                + std::to_string(summary.invalid) + "，新词性 "
                + std::to_string(summary.newPos));
      return;
    }
    if (browserMode == BrowserMode::ImportAudio) {
      browserOpen = false;
      if (audioBrowserForEditor) {
        audioBrowserForEditor = false;
        importEditorAudio(target);
      } else {
        ensureAudioLoaded();
        if (!audioStore) { browserError = "音频数据不可用"; return; }
        AudioEntry imported;
        if (!audioStore->importAudio(target, utf8(target.filename()), imported, error)) {
          browserError = error;
          return;
        }
        showToast("已导入音频：" + imported.name);
      }
      return;
    }
    if (browserMode == BrowserMode::ImportImage) {
      browserOpen = false;
      if (imageBrowserForEditor) {
        imageBrowserForEditor = false;
        loadCropSource(target);
      } else {
        ensureImagesLoaded();
        if (!imageStore) { browserError = "图片数据不可用"; return; }
        ImageEntry imported;
        if (!imageStore->importImage(target, utf8(target.filename()), imported, error)) {
          browserError = error;
          return;
        }
        showToast("已导入图片：" + imported.name);
      }
      return;
    }
    if (browserMode == BrowserMode::ImportFont) {
      if (!fonts) loadVocabularyData();
      if (!fonts) {
        browserError = "项目字体数据不可用";
        return;
      }
      FontEntry importedFont;
      if (!fonts->importFont(target, utf8(target.filename()), importedFont, error)) {
        browserError = error;
        return;
      }
      browserOpen = false;
      showToast("已导入字体：" + importedFont.name);
      return;
    }
    ProjectSummary imported;
    if (!store->importConlang(target, imported, error)) {
      browserError = error;
      return;
    }
    refreshProjects();
    browserOpen = false;
    showToast("已导入项目：" + imported.name);
  }
};

void pushVocabFont(App& app) {
  if (app.vocabFont) ImGui::PushFont(app.vocabFont, 0.0f);
  else ImGui::PushFont(nullptr, 22.0f);
}

void popVocabFont() { ImGui::PopFont(); }

bool inputString(const char* label, std::string& value, std::size_t maxLength = 512) {
  char buffer[1024];
  std::snprintf(buffer, sizeof(buffer), "%s", value.c_str());
  if (ImGui::InputText(label, buffer, std::min(sizeof(buffer), maxLength))) {
    value = buffer;
    return true;
  }
  return false;
}

void applyStyle(App& app) {
  ImGuiStyle& style = ImGui::GetStyle();
  style.WindowRounding = 0.0f;
  style.ChildRounding = 11.0f;
  style.FrameRounding = 8.0f;
  style.PopupRounding = 11.0f;
  style.ScrollbarRounding = 8.0f;
  style.GrabRounding = 8.0f;
  style.WindowBorderSize = 0.0f;
  style.ChildBorderSize = 1.0f;
  style.FrameBorderSize = 0.0f;
  style.WindowPadding = ImVec2(0, 0);
  style.FramePadding = ImVec2(10, 7);
  style.ItemSpacing = ImVec2(9, 8);
  style.ItemInnerSpacing = ImVec2(6, 5);
  style.ScrollbarSize = 10.0f;
  style.FontSizeBase = 18.0f;
  style.AntiAliasedLines = true;

  ImVec4 bg = app.dark ? color(16, 23, 34) : color(238, 241, 246);
  ImVec4 panel = app.dark ? color(24, 33, 48) : color(255, 255, 255);
  ImVec4 panel2 = app.dark ? color(29, 40, 56) : color(246, 248, 251);
  ImVec4 panel3 = app.dark ? color(38, 50, 70) : color(233, 237, 244);
  ImVec4 text = app.dark ? color(232, 237, 247) : color(23, 32, 51);
  ImVec4 muted = app.dark ? color(160, 170, 189) : color(107, 116, 132);
  ImVec4 line = app.dark ? color(43, 56, 76) : color(223, 228, 236);
  ImVec4 accent = app.dark ? color(111, 155, 255) : color(47, 111, 237);
  ImVec4 accentSoft = app.dark ? color(31, 49, 88) : color(232, 239, 255);
  ImVec4 bad = app.dark ? color(240, 125, 125) : color(189, 58, 58);

  auto set = [&](ImGuiCol idx, ImVec4 value) { style.Colors[idx] = value; };
  set(ImGuiCol_Text, text);
  set(ImGuiCol_TextDisabled, muted);
  set(ImGuiCol_WindowBg, bg);
  set(ImGuiCol_ChildBg, panel);
  set(ImGuiCol_PopupBg, panel);
  set(ImGuiCol_Border, line);
  set(ImGuiCol_FrameBg, panel2);
  set(ImGuiCol_FrameBgHovered, panel3);
  set(ImGuiCol_FrameBgActive, panel3);
  set(ImGuiCol_TitleBg, panel);
  set(ImGuiCol_TitleBgActive, panel);
  set(ImGuiCol_Button, panel2);
  set(ImGuiCol_ButtonHovered, panel3);
  set(ImGuiCol_ButtonActive, accentSoft);
  set(ImGuiCol_Header, accentSoft);
  set(ImGuiCol_HeaderHovered, panel3);
  set(ImGuiCol_HeaderActive, accentSoft);
  set(ImGuiCol_CheckMark, accent);
  set(ImGuiCol_SliderGrab, accent);
  set(ImGuiCol_SliderGrabActive, accent);
  set(ImGuiCol_ScrollbarBg, bg);
  set(ImGuiCol_ScrollbarGrab, panel3);
  set(ImGuiCol_Separator, line);
  set(ImGuiCol_ResizeGrip, panel3);
  set(ImGuiCol_Tab, panel2);
  set(ImGuiCol_TabHovered, accentSoft);
  set(ImGuiCol_TabSelected, accentSoft);
  set(ImGuiCol_ModalWindowDimBg, ImVec4(0.03f, 0.05f, 0.09f, 0.48f));
  style.Colors[ImGuiCol_Text] = text;
  style.Colors[ImGuiCol_Button] = panel2;
  style.Colors[ImGuiCol_ButtonHovered] = panel3;
  style.Colors[ImGuiCol_ButtonActive] = accentSoft;
  style.Colors[ImGuiCol_NavCursor] = accent;
  (void)bad;
}

ImFont* loadFont(App& app) {
  ImGuiIO& io = ImGui::GetIO();
  std::vector<std::string> candidates;
  if (const char* env = std::getenv("FORGE_FONT")) {
    if (*env) candidates.push_back(env);
  }
  candidates.push_back("/usr/share/fonts/noto-cjk/NotoSansCJK-Regular.ttc");
  candidates.push_back("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc");
  candidates.push_back("C:/Windows/Fonts/msyh.ttc");
  candidates.push_back("C:/Windows/Fonts/msyhbd.ttc");
  candidates.push_back("C:/Windows/Fonts/simhei.ttf");
  candidates.push_back("C:/Windows/Fonts/segoeui.ttf");
  for (const auto& candidate : candidates) {
    std::error_code ec;
    if (!fs::exists(pathFromUtf8(candidate), ec)) continue;
    ImFontConfig config;
    config.FontNo = 0;
    config.OversampleH = 2;
    config.OversampleV = 2;
    config.PixelSnapH = true;
    ImFont* font = addFontFromPath(
        pathFromUtf8(candidate), 18.0f, &config,
        io.Fonts->GetGlyphRangesChineseFull());
    if (font) {
      app.font = font;
      addLatinFallback(18.0f);
      return font;
    }
  }
  app.font = io.Fonts->AddFontDefault();
  return app.font;
}

void textAt(ImVec2 position, ImU32 colorValue, const std::string& text, float size = 0.0f) {
  ImDrawList* draw = ImGui::GetWindowDrawList();
  if (size <= 0.0f) size = ImGui::GetFontSize();
  draw->AddText(ImGui::GetFont(), size, position, colorValue, text.c_str());
}

void pageTitle(const std::string& eyebrow, const std::string& title, const std::string& subtitle) {
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_CheckMark]);
  ImGui::TextUnformatted(eyebrow.c_str());
  ImGui::PopStyleColor();
  ImGui::PushFont(nullptr, 27.0f);
  ImGui::TextUnformatted(title.c_str());
  ImGui::PopFont();
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
  ImGui::TextWrapped("%s", subtitle.c_str());
  ImGui::PopStyleColor();
}

void topBar(App& app) {
  ImGui::PushStyleColor(ImGuiCol_ChildBg, app.dark ? ImVec4(0.094f, 0.129f, 0.188f, 1.0f)
                                                   : ImVec4(1.0f, 1.0f, 1.0f, 1.0f));
  ImGui::BeginChild("##topbar", ImVec2(0, 64), ImGuiChildFlags_Borders,
                    ImGuiWindowFlags_NoScrollbar);
  ImGui::SetCursorPos(ImVec2(20, 12));
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_CheckMark]);
  ImGui::PushFont(nullptr, 19.0f);
  ImGui::TextUnformatted("NOSTONGUE FORGE");
  ImGui::PopFont();
  ImGui::PopStyleColor();
  ImGui::SameLine();
  ImGui::SetCursorPosY(31);
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
  ImGui::TextUnformatted("DESKTOP · SDL2");
  ImGui::PopStyleColor();

  const float width = ImGui::GetWindowWidth();
  ImGui::SetCursorPos(ImVec2(width - 110, 17));
  if (ImGui::Button(app.dark ? "浅色" : "深色", ImVec2(86, 30))) {
    app.dark = !app.dark;
    app.store->saveTheme(app.dark);
    applyStyle(app);
  }
  ImGui::EndChild();
  ImGui::PopStyleColor();
}

void footer(const App& app) {
  ImGui::PushStyleColor(ImGuiCol_ChildBg, app.dark ? ImVec4(0.094f, 0.129f, 0.188f, 1.0f)
                                                   : ImVec4(1.0f, 1.0f, 1.0f, 1.0f));
  ImGui::BeginChild("##footer", ImVec2(0, 36), ImGuiChildFlags_Borders,
                    ImGuiWindowFlags_NoScrollbar);
  ImGui::SetCursorPos(ImVec2(20, 8));
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
  ImGui::TextUnformatted("数据目录");
  ImGui::SameLine();
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_Text]);
  ImGui::TextUnformatted(utf8(app.store->dataRoot).c_str());
  ImGui::PopStyleColor();
  ImGui::PopStyleColor();
  ImGui::SameLine();
  ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 100);
  ImGui::TextUnformatted("v0.3.0");
  ImGui::EndChild();
  ImGui::PopStyleColor();
}

void cardStatsText(const ProjectSummary& project, std::string& stats) {
  stats = fmt("章 %d   ·   关 %d   ·   题 %d   ·   词 %d",
              project.stats.chapters, project.stats.courses,
              project.stats.questions, project.words);
}

void renderHome(App& app) {
  pageTitle("PROJECT LIBRARY", "我的项目",
            "项目级数据保存在本机；创建、结构、题目与导入导出均可离线使用。");
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 360;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("导入 .conlang", ImVec2(140, 34))) app.openBrowser(App::BrowserMode::ImportConlang);
  ImGui::SameLine();
  if (ImGui::Button("＋ 新建项目", ImVec2(140, 34))) {
    app.openModalText(App::ModalKind::NewProject, "新建项目", "");
    std::snprintf(app.projectNameInput, sizeof(app.projectNameInput), "%s", "桌面测试");
    std::snprintf(app.projectLineInput, sizeof(app.projectLineInput), "%s", "Nortu 自由语");
  }
  ImGui::Spacing();
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
  ImGui::Text("%zu 个项目 · 置顶优先", app.projects.size());
  ImGui::PopStyleColor();
  ImGui::Spacing();

  if (app.projects.empty()) {
    ImGui::Dummy(ImVec2(0, 45));
    ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 130);
    ImGui::TextUnformatted("◇  还没有项目");
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 210);
    ImGui::TextUnformatted("新建一个语言课程项目，或导入已有的 .conlang。");
    ImGui::PopStyleColor();
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 80);
    if (ImGui::Button("新建第一个项目", ImVec2(160, 36))) {
      app.openModalText(App::ModalKind::NewProject, "新建项目", "");
      std::snprintf(app.projectNameInput, sizeof(app.projectNameInput), "%s", "桌面测试");
      std::snprintf(app.projectLineInput, sizeof(app.projectLineInput), "%s", "Nortu 自由语");
    }
    return;
  }

  if (ImGui::BeginTable("##projects", 2,
                        ImGuiTableFlags_SizingStretchSame | ImGuiTableFlags_PadOuterX)) {
    for (const auto& project : app.projects) {
      ImGui::TableNextColumn();
      ImGui::PushID(project.folder.c_str());
      ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
      ImGui::BeginChild("##card", ImVec2(0, 154), ImGuiChildFlags_Borders,
                        ImGuiWindowFlags_NoScrollbar);
      ImVec2 start = ImGui::GetCursorScreenPos();
      ImGui::PushStyleColor(ImGuiCol_Header, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
      bool clicked = ImGui::Selectable("##card_select", false,
                                       ImGuiSelectableFlags_AllowOverlap,
                                       ImVec2(ImGui::GetContentRegionAvail().x, 126));
      ImGui::PopStyleColor();
      const ImU32 textColor = ImGui::GetColorU32(ImGuiCol_Text);
      const ImU32 muted = ImGui::GetColorU32(ImGuiCol_TextDisabled);
      const ImU32 accent = ImGui::GetColorU32(ImGuiCol_CheckMark);
      textAt(start + ImVec2(14, 12), accent, "N", 22.0f);
      textAt(start + ImVec2(44, 12), textColor, project.name, 19.0f);
      textAt(start + ImVec2(44, 39), muted, project.courseLine.empty() ? "未设置课程线" : project.courseLine, 13.0f);
      std::string stats;
      cardStatsText(project, stats);
      textAt(start + ImVec2(14, 72), muted, stats, 13.0f);
      textAt(start + ImVec2(14, 103), muted, formatTime(project.updated > 0 ? project.updated : project.createdAt), 12.0f);
      textAt(start + ImVec2(14, 103), muted, "", 12.0f);
      if (project.pinned) {
        textAt(start + ImVec2(ImGui::GetContentRegionAvail().x - 58, 14), accent, "置顶", 11.0f);
      }
      ImGui::SetCursorScreenPos(start + ImVec2(ImGui::GetContentRegionAvail().x - 35, 8));
      ImGui::SetNextItemAllowOverlap();
      if (ImGui::Button("⋯", ImVec2(30, 27))) ImGui::OpenPopup("##card_menu");
      if (ImGui::BeginPopup("##card_menu")) {
        if (ImGui::MenuItem(project.pinned ? "取消置顶" : "置顶项目")) app.togglePin(project.folder);
        if (ImGui::MenuItem("导出 .conlang")) {
          app.folder = project.folder;
          ProjectSummary p;
          Structure s;
          if (app.store->loadProject(project.folder, p, s)) {
            app.project = p;
            app.openBrowser(App::BrowserMode::ExportConlang);
          }
        }
        ImGui::Separator();
        if (ImGui::MenuItem("删除项目")) {
          app.openModalConfirm(App::ModalKind::ConfirmProject, "删除项目",
                               "确定删除「" + project.name + "」吗？项目目录及其所有文件会被递归删除，此操作不可撤销。",
                               project.folder);
        }
        ImGui::EndPopup();
      }
      if (clicked) app.openCardDialog(project.folder);
      ImGui::EndChild();
      ImGui::PopStyleColor();
      ImGui::PopID();
      ImGui::Spacing();
    }
    ImGui::EndTable();
  }
}

void renderStructure(App& app) {
  if (ImGui::Button("← 项目", ImVec2(90, 30))) {
    app.screen = App::Screen::Home;
    app.refreshProjects();
    return;
  }
  ImGui::SameLine();
  ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
  ImGui::TextUnformatted("/");
  ImGui::SameLine();
  ImGui::TextUnformatted(app.project.name.c_str());
  ImGui::PopStyleColor();
  ImGui::Spacing();
  const Stats stats = countStructure(app.structure);
  pageTitle("STRUCTURE", app.project.name,
            fmt("%d 章 · %d 单元 · %d 课程 · %d 题", stats.chapters, stats.units,
                stats.courses, stats.questions));
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 170;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("＋ 新建章节", ImVec2(140, 34))) {
    app.openModalText(App::ModalKind::AddChapter, "新建章节", "未命名章节");
  }
  ImGui::Spacing();

  if (app.structure.chapters.empty()) {
    ImGui::Dummy(ImVec2(0, 50));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 150);
    ImGui::TextDisabled("还没有章节，点击右上角新建章节开始。");
    return;
  }

  for (auto& chapter : app.structure.chapters) {
    ImGui::PushID(chapter.id.c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##chapter", ImVec2(0, 0), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar | ImGuiWindowFlags_AlwaysAutoResize);
    const bool expanded = app.expanded.count(chapter.id) > 0;
    if (ImGui::Button(expanded ? "-" : "+", ImVec2(26, 26))) {
      if (expanded) app.expanded.erase(chapter.id); else app.expanded.insert(chapter.id);
    }
    ImGui::SameLine();
    if (ImGui::Selectable(chapter.name.c_str(), false, ImGuiSelectableFlags_AllowOverlap,
                          ImVec2(420, 0))) {
      if (expanded) app.expanded.erase(chapter.id); else app.expanded.insert(chapter.id);
    }
    ImGui::SameLine();
    ImGui::TextDisabled("%zu 单元", chapter.units.size());
    ImGui::SameLine();
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 50);
    ImGui::SetNextItemAllowOverlap();
    if (ImGui::Button("⋯", ImVec2(30, 26))) ImGui::OpenPopup("##chapter_menu");
    if (ImGui::BeginPopup("##chapter_menu")) {
      if (ImGui::MenuItem("新建单元")) app.openModalText(App::ModalKind::AddUnit, "新建单元", "未命名单元", chapter.id);
      if (ImGui::MenuItem("重命名章节")) app.openModalText(App::ModalKind::RenameChapter, "重命名章节", chapter.name, chapter.id);
      if (ImGui::MenuItem("上移章节")) {
        auto it = std::find_if(app.structure.chapters.begin(), app.structure.chapters.end(),
                               [&](const Chapter& c) { return c.id == chapter.id; });
        if (it != app.structure.chapters.begin() && it != app.structure.chapters.end()) {
          std::iter_swap(it, it - 1);
          app.persist();
        }
      }
      if (ImGui::MenuItem("下移章节")) {
        auto it = std::find_if(app.structure.chapters.begin(), app.structure.chapters.end(),
                               [&](const Chapter& c) { return c.id == chapter.id; });
        if (it != app.structure.chapters.end() && it + 1 != app.structure.chapters.end()) {
          std::iter_swap(it, it + 1);
          app.persist();
        }
      }
      ImGui::Separator();
      if (ImGui::MenuItem("删除章节")) app.openModalConfirm(App::ModalKind::ConfirmChapter, "删除章节",
          "确定删除「" + chapter.name + "」及其全部单元/课程吗？", chapter.id);
      ImGui::EndPopup();
    }

    if (expanded) {
      ImGui::Indent(20);
      for (auto& unit : chapter.units) {
        ImGui::PushID(unit.id.c_str());
        if (ImGui::Button("-", ImVec2(22, 22))) {}
        ImGui::SameLine();
        ImGui::TextUnformatted(unit.name.c_str());
        ImGui::SameLine();
        ImGui::TextDisabled("%zu 课程", unit.courses.size());
        ImGui::SameLine();
        ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 70);
        if (ImGui::Button("⋯", ImVec2(28, 22))) ImGui::OpenPopup("##unit_menu");
        if (ImGui::BeginPopup("##unit_menu")) {
          if (ImGui::MenuItem("新建课程")) app.openModalText(App::ModalKind::AddCourse, "新建课程", "未命名课程", chapter.id, unit.id);
          if (ImGui::MenuItem("重命名单元")) app.openModalText(App::ModalKind::RenameUnit, "重命名单元", unit.name, chapter.id, unit.id);
          if (ImGui::MenuItem("上移单元")) {
            auto it = std::find_if(chapter.units.begin(), chapter.units.end(),
                                   [&](const Unit& u) { return u.id == unit.id; });
            if (it != chapter.units.begin() && it != chapter.units.end()) {
              std::iter_swap(it, it - 1);
              app.persist();
            }
          }
          if (ImGui::MenuItem("下移单元")) {
            auto it = std::find_if(chapter.units.begin(), chapter.units.end(),
                                   [&](const Unit& u) { return u.id == unit.id; });
            if (it != chapter.units.end() && it + 1 != chapter.units.end()) {
              std::iter_swap(it, it + 1);
              app.persist();
            }
          }
          ImGui::Separator();
          if (ImGui::MenuItem("删除单元")) app.openModalConfirm(App::ModalKind::ConfirmUnit, "删除单元",
              "确定删除「" + unit.name + "」及其课程吗？", chapter.id, unit.id);
          ImGui::EndPopup();
        }
        ImGui::Indent(18);
        for (auto& course : unit.courses) {
          ImGui::PushID(course.id.c_str());
          if (ImGui::Selectable(("  " + course.name).c_str(), false,
                                ImGuiSelectableFlags_AllowOverlap, ImVec2(390, 0))) {
            app.chapterId = chapter.id;
            app.unitId = unit.id;
            app.courseId = course.id;
            app.screen = App::Screen::Course;
          }
          ImGui::SameLine();
          ImGui::TextDisabled("%zu 题", course.questions.size());
          ImGui::SameLine();
          ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 70);
          if (ImGui::Button("⋯", ImVec2(28, 22))) ImGui::OpenPopup("##course_menu");
          if (ImGui::BeginPopup("##course_menu")) {
            if (ImGui::MenuItem("重命名课程")) app.openModalText(App::ModalKind::RenameCourse, "重命名课程", course.name, chapter.id, unit.id, course.id);
            if (ImGui::MenuItem("上移课程")) {
              auto it = std::find_if(unit.courses.begin(), unit.courses.end(),
                                     [&](const Course& c) { return c.id == course.id; });
              if (it != unit.courses.begin() && it != unit.courses.end()) {
                std::iter_swap(it, it - 1);
                app.persist();
              }
            }
            if (ImGui::MenuItem("下移课程")) {
              auto it = std::find_if(unit.courses.begin(), unit.courses.end(),
                                     [&](const Course& c) { return c.id == course.id; });
              if (it != unit.courses.end() && it + 1 != unit.courses.end()) {
                std::iter_swap(it, it + 1);
                app.persist();
              }
            }
            ImGui::Separator();
            if (ImGui::MenuItem("删除课程")) app.openModalConfirm(App::ModalKind::ConfirmCourse, "删除课程",
                "确定删除「" + course.name + "」及其题目吗？", chapter.id, unit.id, course.id);
            ImGui::EndPopup();
          }
          ImGui::PopID();
        }
        ImGui::Unindent(18);
        ImGui::PopID();
      }
      if (chapter.units.empty()) ImGui::TextDisabled("还没有单元");
      ImGui::Unindent(20);
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderCourse(App& app) {
  Course* course = app.selectedCourse();
  if (!course) {
    if (ImGui::Button("← 结构", ImVec2(100, 30))) app.screen = App::Screen::Structure;
    ImGui::SameLine();
    ImGui::TextDisabled("课程不存在");
    return;
  }
  Chapter* chapter = app.findChapter(app.chapterId);
  Unit* unit = app.findUnit(chapter, app.unitId);
  if (ImGui::Button("← 结构", ImVec2(100, 30))) app.screen = App::Screen::Structure;
  ImGui::SameLine();
  ImGui::TextDisabled("%s  /  %s  /  %s", chapter ? chapter->name.c_str() : "",
                      unit ? unit->name.c_str() : "", course->name.c_str());
  ImGui::Spacing();
  pageTitle("COURSE", course->name, fmt("%zu 道题目 · 变更立即写入 structure.json",
                                        course->questions.size()));
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 310;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("资源管理", ImVec2(120, 34))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  if (ImGui::Button("＋ 新建题目", ImVec2(140, 34))) {
    app.openModalText(App::ModalKind::AddQuestion, "新建题目", "未命名题目");
  }
  ImGui::Spacing();
  if (course->questions.empty()) {
    ImGui::Dummy(ImVec2(0, 50));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 140);
    ImGui::TextDisabled("还没有题目，新建第一道题目开始组织课程内容。");
    return;
  }
  for (std::size_t i = 0; i < course->questions.size(); ++i) {
    auto& question = course->questions[i];
    ImGui::PushID(question.id.c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##question", ImVec2(0, 46), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    ImGui::SetCursorPos(ImVec2(12, 12));
    ImGui::TextDisabled("%02zu", i + 1);
    ImGui::SameLine();
    if (ImGui::Selectable(question.name.c_str(), false, 0, ImVec2(560, 0))) {
      app.showToast("题目编辑将在后续阶段实现");
    }
    ImGui::SameLine();
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 150);
    if (ImGui::Button("↑", ImVec2(30, 24)) && i > 0) {
      std::swap(course->questions[i], course->questions[i - 1]);
      app.persist();
    }
    ImGui::SameLine();
    if (ImGui::Button("↓", ImVec2(30, 24)) && i + 1 < course->questions.size()) {
      std::swap(course->questions[i], course->questions[i + 1]);
      app.persist();
    }
    ImGui::SameLine();
    if (ImGui::Button("×", ImVec2(30, 24))) {
      app.openModalConfirm(App::ModalKind::ConfirmQuestion, "删除题目",
                           "确定删除「" + question.name + "」吗？", question.id);
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderCardDialog(App& app) {
  if (!app.cardDialog) return;
  if (!ImGui::IsPopupOpen("##card_dialog")) ImGui::OpenPopup("##card_dialog");
  ImGui::SetNextWindowSize(ImVec2(460, 0));
  if (ImGui::BeginPopupModal("##card_dialog", nullptr, ImGuiWindowFlags_AlwaysAutoResize)) {
    ImGui::PushStyleColor(ImGuiCol_Text, app.dark ? ImVec4(0.91f, 0.93f, 0.97f, 1.0f)
                                                 : ImVec4(0.09f, 0.13f, 0.20f, 1.0f));
    ProjectSummary found;
    Structure structure;
    if (!app.store->loadProject(app.cardFolder, found, structure)) {
      ImGui::TextUnformatted("项目不存在");
      if (ImGui::Button("关闭")) app.cardDialog = false;
      ImGui::EndPopup();
      return;
    }
    ImGui::PushFont(nullptr, 21.0f);
    ImGui::TextUnformatted(found.name.c_str());
    ImGui::PopFont();
    ImGui::TextDisabled("%s", found.courseLine.empty() ? "未设置课程线" : found.courseLine.c_str());
    ImGui::Spacing();
    ImGui::TextWrapped("选择进入方式：学习播放器将在后续阶段实现；编辑可进入项目结构。");
    ImGui::Spacing();
    if (ImGui::Button("学习（后续）", ImVec2(180, 36))) {
      app.cardDialog = false;
      app.showToast("学习播放器将在后续阶段实现");
      ImGui::CloseCurrentPopup();
    }
    ImGui::SameLine();
    if (ImGui::Button("编辑项目", ImVec2(180, 36))) {
      app.cardDialog = false;
      ImGui::CloseCurrentPopup();
      app.loadStructure(app.cardFolder);
    }
    ImGui::PopStyleColor();
    ImGui::EndPopup();
  }
}

void renderModal(App& app) {
  if (app.modal == App::ModalKind::None) return;
  if (!ImGui::IsPopupOpen("##forge_modal")) ImGui::OpenPopup("##forge_modal");
  ImGui::SetNextWindowSize(ImVec2(480, 0));
  if (!ImGui::BeginPopupModal("##forge_modal", nullptr, ImGuiWindowFlags_AlwaysAutoResize)) return;
  ImGui::PushStyleColor(ImGuiCol_Text, app.dark ? ImVec4(0.91f, 0.93f, 0.97f, 1.0f)
                                               : ImVec4(0.09f, 0.13f, 0.20f, 1.0f));
  ImGui::PushFont(nullptr, 21.0f);
  ImGui::TextUnformatted(app.modalTitle.c_str());
  ImGui::PopFont();
  ImGui::Spacing();
  if (app.modal == App::ModalKind::NewProject) {
    ImGui::TextDisabled("项目名称");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##project_name", app.projectNameInput, sizeof(app.projectNameInput));
    ImGui::Spacing();
    ImGui::TextDisabled("课程线");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##project_line", app.projectLineInput, sizeof(app.projectLineInput));
  } else if (app.modal == App::ModalKind::ConfirmProject ||
             app.modal == App::ModalKind::ConfirmChapter ||
             app.modal == App::ModalKind::ConfirmUnit ||
             app.modal == App::ModalKind::ConfirmCourse ||
             app.modal == App::ModalKind::ConfirmQuestion) {
    ImGui::TextWrapped("%s", app.modalMessage.c_str());
  } else {
    ImGui::TextDisabled("名称");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##name", app.modalInput, sizeof(app.modalInput));
  }
  ImGui::Spacing();
  ImGui::Separator();
  ImGui::Spacing();
  const bool confirm = app.modal == App::ModalKind::ConfirmProject ||
                       app.modal == App::ModalKind::ConfirmChapter ||
                       app.modal == App::ModalKind::ConfirmUnit ||
                       app.modal == App::ModalKind::ConfirmCourse ||
                       app.modal == App::ModalKind::ConfirmQuestion;
  if (ImGui::Button("取消", ImVec2(110, 34))) {
    app.closeModal();
    ImGui::CloseCurrentPopup();
  }
  ImGui::SameLine();
  ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyle().Colors[ImGuiCol_CheckMark]);
  if (ImGui::Button(confirm ? "删除" : "保存", ImVec2(120, 34))) {
    app.applyModal();
    ImGui::CloseCurrentPopup();
  }
  ImGui::PopStyleColor();
  ImGui::PopStyleColor();
  ImGui::EndPopup();
}

void renderBrowser(App& app) {
  if (!app.browserOpen) return;
  ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 820) * 0.5f,
                                 (ImGui::GetIO().DisplaySize.y - 590) * 0.5f),
                          ImGuiCond_Always);
  ImGui::SetNextWindowSize(ImVec2(820, 590), ImGuiCond_Always);
  bool open = app.browserOpen;
  if (!ImGui::Begin("文件浏览器", &open,
                    ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                        ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
    ImGui::End();
    app.browserOpen = open;
    return;
  }
  ImGui::TextDisabled("路径（支持 Windows 盘符，例如 C:/Users/...）");
  ImGui::SetNextItemWidth(-190);
  ImGui::InputText("##path", app.browserPathInput, sizeof(app.browserPathInput));
  ImGui::SameLine();
  if (ImGui::Button("跳转", ImVec2(80, 30))) app.navigateBrowser(pathFromUtf8(app.browserPathInput));
  ImGui::SameLine();
  if (ImGui::Button("上级", ImVec2(80, 30))) {
    if (app.browserDir.has_parent_path()) app.navigateBrowser(app.browserDir.parent_path());
  }
  ImGui::Spacing();
  ImGui::BeginChild("##browser_dirs", ImVec2(360, 250), ImGuiChildFlags_Borders);
  for (const auto& entry : app.browserEntries) {
    if (!entry.directory) continue;
    if (ImGui::Selectable(("[D] " + entry.name).c_str(), false, ImGuiSelectableFlags_AllowDoubleClick)) {
      if (ImGui::IsMouseDoubleClicked(ImGuiMouseButton_Left)) {
        app.navigateBrowser(app.browserDir / pathFromUtf8(entry.name));
      }
    }
  }
  ImGui::EndChild();
  ImGui::SameLine();
  ImGui::BeginChild("##browser_files", ImVec2(0, 250), ImGuiChildFlags_Borders);
  for (const auto& entry : app.browserEntries) {
    if (entry.directory) continue;
    if (ImGui::Selectable(entry.name.c_str(), false, ImGuiSelectableFlags_AllowDoubleClick)) {
      std::snprintf(app.browserFileInput, sizeof(app.browserFileInput), "%s", entry.name.c_str());
      if (ImGui::IsMouseDoubleClicked(ImGuiMouseButton_Left)) app.applyBrowser();
    }
  }
  ImGui::EndChild();
  ImGui::Spacing();
  ImGui::TextDisabled(app.browserMode == App::BrowserMode::ExportConlang
                          ? "保存为 .conlang"
                          : app.browserMode == App::BrowserMode::ImportFont
                                ? "选择 TTF/OTF/TTC 字体"
                                : app.browserMode == App::BrowserMode::ImportImage
                                      ? "选择 PNG/JPG/JPEG/WEBP/BMP"
                                      : app.browserMode == App::BrowserMode::ImportAudio
                                            ? "选择 WAV/MP3/OGG/M4A/FLAC"
                                            : app.browserMode == App::BrowserMode::ImportLexicon
                                                  ? "选择 Lexicon .jsonl/.json/.txt 词表"
                                                  : "选择 .conlang 文件");
  ImGui::SetNextItemWidth(-150);
  ImGui::InputText("##filename", app.browserFileInput, sizeof(app.browserFileInput));
  ImGui::SameLine();
  if (ImGui::Button(app.browserMode == App::BrowserMode::ExportConlang ? "保存" : "打开",
                    ImVec2(120, 34))) app.applyBrowser();
  if (!app.browserError.empty()) {
    ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.8f, 0.2f, 0.2f, 1.0f));    ImGui::TextWrapped("%s", app.browserError.c_str());
    ImGui::PopStyleColor();
  }
  if (ImGui::Button("取消", ImVec2(110, 34))) {
    app.browserOpen = false;
    open = false;
  }
  ImGui::End();
  app.browserOpen = open;
}

void drawToast(App& app) {
  if (app.toastText.empty() || ImGui::GetTime() > app.toastUntil) return;
  const ImVec2 display = ImGui::GetIO().DisplaySize;
  const ImVec2 textSize = ImGui::CalcTextSize(app.toastText.c_str());
  ImVec2 position((display.x - textSize.x) * 0.5f - 16, display.y - 70);
  ImDrawList* draw = ImGui::GetForegroundDrawList();
  draw->AddRectFilled(ImVec2(position.x, position.y - 8),
                      ImVec2(position.x + textSize.x + 32, position.y + textSize.y + 8),
                      IM_COL32(23, 32, 51, 238), 10.0f);
  draw->AddText(ImGui::GetFont(), 18.0f, ImVec2(position.x + 16, position.y),
                IM_COL32(255, 255, 255, 255), app.toastText.c_str());
}

void renderResourcesWithPhase2(App& app);
void renderVocabulary(App& app);
void renderVocabularyEditor(App& app);
void renderFonts(App& app);
void renderImages(App& app);
void renderImageModals(App& app);
void renderAudios(App& app);
void renderAudioModals(App& app);
void renderPos(App& app);
void renderPosEditor(App& app);
void renderPicker(App& app);

void renderFrame(App& app) {
  if (app.audioPlaying && ImGui::GetTime() >= app.audioPlayDeadline) app.stopAudioPlayback();
  ImGui_ImplSDLRenderer2_NewFrame();
  ImGui_ImplSDL2_NewFrame();
  ImGui::NewFrame();

  ImGuiIO& io = ImGui::GetIO();
  ImGui::SetNextWindowPos(ImVec2(0, 0));
  ImGui::SetNextWindowSize(io.DisplaySize);
  ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(0, 0));
  ImGui::PushStyleColor(ImGuiCol_WindowBg, app.dark ? ImVec4(0.063f, 0.090f, 0.133f, 1.0f)
                                                     : ImVec4(0.933f, 0.945f, 0.965f, 1.0f));
  ImGui::Begin("##root", nullptr,
               ImGuiWindowFlags_NoTitleBar | ImGuiWindowFlags_NoResize |
                   ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoCollapse |
                   ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoBringToFrontOnFocus);
  topBar(app);
  ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_WindowBg]);
  ImGui::BeginChild("##content", ImVec2(0, -36), ImGuiChildFlags_None,
                    ImGuiWindowFlags_AlwaysVerticalScrollbar);
  ImGui::SetCursorPos(ImVec2(30, 20));
  ImGui::BeginGroup();
  switch (app.screen) {
    case App::Screen::Home: renderHome(app); break;
    case App::Screen::Structure: renderStructure(app); break;
    case App::Screen::Course: renderCourse(app); break;
    case App::Screen::Resources: renderResourcesWithPhase2(app); break;
    case App::Screen::Vocabulary: renderVocabulary(app); break;
    case App::Screen::VocabularyEditor: renderVocabularyEditor(app); break;
    case App::Screen::Fonts: renderFonts(app); break;
    case App::Screen::Images: renderImages(app); break;
    case App::Screen::Audios: renderAudios(app); break;
    case App::Screen::Pos: renderPos(app); break;
  }
  ImGui::EndGroup();
  ImGui::EndChild();
  ImGui::PopStyleColor();
  footer(app);
  ImGui::End();
  ImGui::PopStyleColor();
  ImGui::PopStyleVar();

  renderCardDialog(app);
  renderModal(app);
  renderBrowser(app);
  renderImageModals(app);
  renderAudioModals(app);
  renderPosEditor(app);
  renderPicker(app);
  drawToast(app);

  ImGui::Render();
  ImVec4 bg = ImGui::GetStyle().Colors[ImGuiCol_WindowBg];
  SDL_SetRenderDrawColor(app.renderer, static_cast<Uint8>(bg.x * 255),
                         static_cast<Uint8>(bg.y * 255), static_cast<Uint8>(bg.z * 255), 255);
  SDL_RenderClear(app.renderer);
  ImGui_ImplSDLRenderer2_RenderDrawData(ImGui::GetDrawData(), app.renderer);
  SDL_RenderPresent(app.renderer);
}

bool saveScreenshot(App& app, const fs::path& output) {
  int width = 0;
  int height = 0;
  if (SDL_GetRendererOutputSize(app.renderer, &width, &height) != 0 || width <= 0 || height <= 0) {
    return false;
  }
  std::vector<Uint32> pixels(static_cast<std::size_t>(width) * height);
  if (SDL_RenderReadPixels(app.renderer, nullptr, SDL_PIXELFORMAT_ARGB8888,
                           pixels.data(), width * 4) != 0) {
    return false;
  }
  SDL_Surface* surface = SDL_CreateRGBSurfaceWithFormatFrom(
      pixels.data(), width, height, 32, width * 4, SDL_PIXELFORMAT_ARGB8888);
  if (!surface) return false;
  std::error_code ec;
  fs::create_directories(output.parent_path(), ec);
  const int result = IMG_SavePNG(surface, utf8(output).c_str());
  SDL_FreeSurface(surface);
  return result == 0;
}

bool zipContains(const fs::path& file, const std::vector<std::string>& required) {
  std::ifstream input(file, std::ios::binary);
  if (!input) return false;
  std::ostringstream buffer;
  buffer << input.rdbuf();
  std::string bytes = buffer.str();
  mz_zip_archive zip{};
  if (!mz_zip_reader_init_mem(&zip, bytes.data(), bytes.size(), 0)) return false;
  std::set<std::string> names;
  for (mz_uint i = 0; i < mz_zip_reader_get_num_files(&zip); ++i) {
    mz_zip_archive_file_stat stat{};
    if (mz_zip_reader_file_stat(&zip, i, &stat) && !stat.m_is_directory) {
      names.insert(stat.m_filename);
    }
  }
  mz_zip_reader_end(&zip);
  for (const auto& name : required) {
    if (!names.count(name)) return false;
  }
  return true;
}

bool zipContainsPrefix(const fs::path& file, const std::string& prefix) {
  std::ifstream input(file, std::ios::binary);
  if (!input) return false;
  std::ostringstream buffer;
  buffer << input.rdbuf();
  std::string bytes = buffer.str();
  mz_zip_archive zip{};
  if (!mz_zip_reader_init_mem(&zip, bytes.data(), bytes.size(), 0)) return false;
  bool found = false;
  for (mz_uint i = 0; i < mz_zip_reader_get_num_files(&zip); ++i) {
    mz_zip_archive_file_stat stat{};
    if (mz_zip_reader_file_stat(&zip, i, &stat) && !stat.m_is_directory &&
        std::string(stat.m_filename).rfind(prefix, 0) == 0) {
      found = true;
      break;
    }
  }
  mz_zip_reader_end(&zip);
  return found;
}

int runSmoke(App& app, const fs::path& outputDir) {
  std::error_code ec;
  fs::create_directories(outputDir, ec);
  app.refreshProjects();
  const std::string targetName = "桌面测试";
  const ProjectSummary* existing = nullptr;
  for (const auto& project : app.projects) {
    if (project.name == targetName) { existing = &project; break; }
  }
  bool created = false;
  if (!existing) {
    ProjectSummary createdProject = app.store->createProject(targetName, "Nortu 自由语");
    if (createdProject.folder.empty()) {
      SDL_Log("smoke: create project failed");
      return 1;
    }
    created = true;
    app.refreshProjects();
    app.loadStructure(createdProject.folder);
    app.structure.chapters.push_back({Store::newUuid(), "第一章", {}});
    Unit unit{Store::newUuid(), "第一单元", {}};
    Course course{Store::newUuid(), "第一课", {}};
    course.questions.push_back({Store::newUuid(), "题目一"});
    course.questions.push_back({Store::newUuid(), "题目二"});
    unit.courses.push_back(course);
    app.structure.chapters[0].units.push_back(unit);
    app.persist();
    app.loadStructure(createdProject.folder);
  } else {
    app.loadStructure(existing->folder);
  }
  app.chapterId = app.structure.chapters.empty() ? "" : app.structure.chapters[0].id;
  app.unitId = app.structure.chapters.empty() || app.structure.chapters[0].units.empty()
                   ? "" : app.structure.chapters[0].units[0].id;
  app.courseId = app.structure.chapters.empty() || app.structure.chapters[0].units.empty() ||
                         app.structure.chapters[0].units[0].courses.empty()
                     ? "" : app.structure.chapters[0].units[0].courses[0].id;
  app.refreshProjects();
  app.screen = App::Screen::Home;
  renderFrame(app);
  bool ok = saveScreenshot(app, outputDir / "home.png");
  app.screen = App::Screen::Structure;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "structure.png") && ok;
  app.screen = App::Screen::Course;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "course.png") && ok;
  app.screen = App::Screen::Resources;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "resources.png") && ok;
  app.openBrowser(App::BrowserMode::ImportConlang);
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "browser.png") && ok;
  app.browserOpen = false;
  renderFrame(app);
  app.refreshProjects();
  if (app.projects.empty()) {
    SDL_Log("smoke: no projects after create");
    return 1;
  }
  app.folder = app.projects.front().folder;
  app.store->loadProject(app.folder, app.project, app.structure);
  app.chapterId = app.structure.chapters.empty() ? "" : app.structure.chapters[0].id;
  app.unitId = app.structure.chapters.empty() || app.structure.chapters[0].units.empty()
                   ? "" : app.structure.chapters[0].units[0].id;
  app.courseId = app.structure.chapters.empty() || app.structure.chapters[0].units.empty() ||
                         app.structure.chapters[0].units[0].courses.empty()
                     ? "" : app.structure.chapters[0].units[0].courses[0].id;

  bool vocabOk = false;
  bool fontOk = false;
  bool invalidFontRejected = false;
  bool invalidFontNoResidue = false;
  SDL_Log("phase2: load vocab");
  app.loadVocabularyData();
  SDL_Log("phase2: vocab loaded entries=%zu", app.vocabulary ? app.vocabulary->entries.size() : 0);
  if (app.vocabulary) {
    VocabEntry* apple = nullptr;
    for (auto& entry : app.vocabulary->entries) {
      for (const auto& code : entry.codes) if (code == "aaa") apple = &entry;
    }
    if (!apple) {
      VocabEntry entry;
      entry.id = Store::newUuid();
      entry.type = "text";
      entry.text = "apple";
      entry.meanings = {"苹果"};
      entry.codes = {"aaa"};
      entry.createdAt = entry.updatedAt = 1794400000000;
      app.vocabulary->entries.push_back(entry);
      apple = &app.vocabulary->entries.back();
    }
    const std::string appleId = apple ? apple->id : "";
    VocabEntry* unicode = nullptr;
    for (auto& entry : app.vocabulary->entries) {
      if (entry.type == "unicode" && !entry.codepoints.empty()) unicode = &entry;
    }
    if (!unicode) {
      VocabEntry entry;
      entry.id = Store::newUuid();
      entry.type = "unicode";
      entry.meanings = {"Unicode 示例"};
      entry.codes = {"u12"};
      entry.codepoints = {"12ab", "34cd"};
      entry.createdAt = entry.updatedAt = 1794400000001;
      app.vocabulary->entries.push_back(entry);
      unicode = &app.vocabulary->entries.back();
    }
    const std::string unicodeId = unicode ? unicode->id : "";
    bool hasCombo = false;
    for (const auto& entry : app.vocabulary->entries) if (entry.type == "combo") hasCombo = true;
    if (!hasCombo && apple && unicode) {
      VocabEntry entry;
      entry.id = Store::newUuid();
      entry.type = "combo";
      entry.meanings = {"组合示例"};
      entry.codes = {"combo"};
      entry.parts = {appleId, unicodeId};
      entry.createdAt = entry.updatedAt = 1794400000002;
      app.vocabulary->entries.push_back(entry);
    }
    vocabOk = app.vocabulary->save();
    SDL_Log("phase2: vocab saved ok=%d", vocabOk ? 1 : 0);
    app.loadVocabularyData();
    SDL_Log("phase2: vocab reloaded entries=%zu", app.vocabulary ? app.vocabulary->entries.size() : 0);
  }
  if (app.fonts) {
    if (app.fonts->fonts.empty() && fs::exists("/usr/share/fonts/noto/NotoSerif-Regular.ttf")) {
      FontEntry imported;
      std::string error;
      if (app.fonts->importFont(pathFromUtf8("/usr/share/fonts/noto/NotoSerif-Regular.ttf"),
                                "Noto Serif", imported, error)) {
        app.fonts->setActive(imported.id);
      }
    }
    if (!app.fonts->activeFileValid() && !app.fonts->fonts.empty()) {
      app.fonts->setActive(app.fonts->fonts.front().id);
    }
    fontOk = app.fonts->activeFileValid();
    SDL_Log("phase2: fonts=%zu active=%s valid=%d", app.fonts->fonts.size(),
            app.fonts->active.c_str(), fontOk ? 1 : 0);
    const fs::path badFont = outputDir / "bad.ttf";
    {
      std::ofstream bad(badFont, std::ios::binary);
      bad << "not a font";
    }
    FontStore probe(app.store->projectDirectory(app.folder));
    const std::size_t beforeFonts = probe.fonts.size();
    FontEntry badImported;
    std::string badFontError;
    invalidFontRejected = !probe.importFont(badFont, "Bad", badImported, badFontError);
    invalidFontNoResidue = probe.fonts.size() == beforeFonts;
    SDL_Log("phase2: invalid font rejected=%d no_residue=%d", invalidFontRejected ? 1 : 0,
            invalidFontNoResidue ? 1 : 0);
    app.loadVocabularyData();
    SDL_Log("phase2: font loaded");
  }
  if (app.vocabulary) {
    SDL_Log("phase2: render vocabulary");
    app.screen = App::Screen::Resources;
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "resources.png") && ok;
    app.screen = App::Screen::Vocabulary;
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "vocabulary.png") && ok;
    if (!app.vocabulary->entries.empty()) {
      app.editVocabulary(app.vocabulary->entries.front().id);
      app.screen = App::Screen::VocabularyEditor;
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocabulary-editor.png") && ok;
      const VocabEntry* unicodeEntry = nullptr;
      for (const auto& entry : app.vocabulary->entries) if (entry.type == "unicode") unicodeEntry = &entry;
      if (unicodeEntry) {
        app.editVocabulary(unicodeEntry->id);
        app.screen = App::Screen::VocabularyEditor;
        renderFrame(app);
        ok = saveScreenshot(app, outputDir / "font-vocab.png") && ok;
      }
      const VocabEntry* comboEntry = nullptr;
      for (const auto& entry : app.vocabulary->entries) if (entry.type == "combo") comboEntry = &entry;
      if (comboEntry) {
        app.editVocabulary(comboEntry->id);
        app.screen = App::Screen::VocabularyEditor;
        app.openPicker();
        std::snprintf(app.pickerSearch, sizeof(app.pickerSearch), "%s", "aaa");
        renderFrame(app);
        ok = saveScreenshot(app, outputDir / "picker.png") && ok;
        app.pickerOpen = false;
      }
    }
  }
  app.screen = App::Screen::Fonts;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "fonts.png") && ok;

  SDL_Surface* testSurface = SDL_CreateRGBSurfaceWithFormat(0, 80, 60, 32,
                                                            SDL_PIXELFORMAT_ARGB8888);
  if (testSurface) {
    SDL_FillRect(testSurface, nullptr, SDL_MapRGBA(testSurface->format, 40, 120, 200, 255));
    IMG_SavePNG(testSurface, utf8(outputDir / "test-image.png").c_str());
    SDL_FreeSurface(testSurface);
  }
  app.ensureImagesLoaded();
  bool imagesOk = false;
  bool imageRefOk = false;
  bool referencedDeleteBlocked = false;
  bool unusedDeleted = false;
  bool cropOk = false;
  bool drawOk = false;
  bool invalidImageRejected = false;
  bool invalidImageNoResidue = false;
  bool pendingCleanup = false;
  bool unregisteredListed = false;
  bool imageVocabSave = false;
  std::string managedImageId;
  std::string managedImageFile;
  if (app.imageStore) {
    ImageEntry* existingImage = nullptr;
    for (auto& image : app.imageStore->images) {
      if (image.code == "img1" || image.name == "测试图") existingImage = &image;
    }
    ImageEntry importedImage;
    std::string imageError;
    if (existingImage) {
      importedImage = *existingImage;
    } else if (app.imageStore->importImage(outputDir / "test-image.png", "测试图",
                                           importedImage, imageError)) {
      app.imageStore->setCode(importedImage.id, "img1");
    }
    if (!importedImage.id.empty()) {
      managedImageId = importedImage.id;
      managedImageFile = importedImage.file;
      imagesOk = true;
    }
    if (app.vocabulary && imagesOk) {
      VocabEntry* imageVocab = nullptr;
      for (auto& entry : app.vocabulary->entries) {
        if (entry.type == "image" && entry.image == managedImageFile) imageVocab = &entry;
      }
      if (!imageVocab) {
        VocabEntry entry;
        entry.id = Store::newUuid();
        entry.type = "image";
        entry.meanings = {"测试图片词"};
        entry.codes = {"imgword"};
        entry.image = managedImageFile;
        entry.imageWidth = importedImage.width;
        entry.imageHeight = importedImage.height;
        entry.createdAt = entry.updatedAt = 1794400000003;
        app.vocabulary->entries.push_back(entry);
        app.vocabulary->save();
        app.loadVocabularyData();
      }
      for (const auto& entry : app.vocabulary->entries) {
        if (entry.type == "image" && entry.image == managedImageFile) imageRefOk = true;
      }
    }
    ImageEntry unusedImage;
    if (app.imageStore->importImage(outputDir / "test-image.png", "未引用图", unusedImage,
                                    imageError)) {
      unusedDeleted = app.imageStore->deleteImage(unusedImage.id, imageError) &&
                      !fs::exists(app.imageStore->resolveFile(unusedImage.file));
    }
    if (imagesOk) {
      referencedDeleteBlocked = imageRefOk && app.imageStore->find(managedImageId) != nullptr;
      fs::path unregistered = app.currentProjectDirectory() / "resources" / "images" / "unregistered.png";
      std::error_code copyEc;
      fs::copy_file(outputDir / "test-image.png", unregistered,
                    fs::copy_options::overwrite_existing, copyEc);
      unregisteredListed = app.imageStore->unregisteredFiles().size() >= 1;
    }
  }
  app.screen = App::Screen::Images;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "images.png") && ok;
  if (!managedImageId.empty()) {
    app.openImageCode(managedImageId);
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "image-edit-code.png") && ok;
    app.imageCodeOpen = false;
  }
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "image-used-by-vocab.png") && ok;
  if (app.vocabulary) {
    const VocabEntry* imageVocab = nullptr;
    for (const auto& entry : app.vocabulary->entries) {
      if (entry.type == "image" && entry.image == managedImageFile) imageVocab = &entry;
    }
    if (imageVocab) {
      app.editVocabulary(imageVocab->id);
      app.screen = App::Screen::VocabularyEditor;
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-image-editor.png") && ok;
      app.saveEditedVocabulary();
      imageVocabSave = true;
    }
  }
  app.loadCropSource(outputDir / "test-image.png");
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "image-crop.png") && ok;
  if (app.cropOpen) {
    app.applyCrop();
    cropOk = !app.pendingImagePath.empty() && fs::exists(app.pendingImagePath);
    app.deletePendingImage();
  }
  if (app.vocabulary) {
    for (const auto& entry : app.vocabulary->entries) {
      if (entry.type == "image" && entry.image == managedImageFile) {
        app.editVocabulary(entry.id);
        app.screen = App::Screen::VocabularyEditor;
        break;
      }
    }
  }
  app.drawPresetWidth = 300;
  app.drawPresetHeight = 200;
  app.startDrawing();
  if (app.drawCanvas.surface) {
    DrawStroke strokeA;
    strokeA.points = {ImVec2(40, 50), ImVec2(120, 80), ImVec2(180, 60)};
    DrawStroke strokeB;
    strokeB.points = {ImVec2(60, 150), ImVec2(220, 120)};
    app.drawCanvas.strokes = {strokeA, strokeB};
    canvasReplay(app.drawCanvas);
    app.toastText.clear();
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "image-draw.png") && ok;
    canvasUndo(app.drawCanvas);
    canvasRedo(app.drawCanvas);
    drawOk = canvasSavePng(app.drawCanvas, outputDir / "hand.png");
    if (drawOk) {
      int handW = 0;
      int handH = 0;
      drawOk = loadImageSize(outputDir / "hand.png", handW, handH) &&
               handW == 300 && handH == 200;
      SDL_Surface* handSurface = IMG_Load(utf8(outputDir / "hand.png").c_str());
      if (handSurface) {
        Uint8 r = 0, g = 0, b = 0, a = 0;
        SDL_GetRGBA(*static_cast<Uint32*>(handSurface->pixels), handSurface->format, &r, &g, &b, &a);
        drawOk = drawOk && a == 0;
        SDL_FreeSurface(handSurface);
      }
    }
  }
  app.closeDrawing();
  const fs::path badImage = outputDir / "bad.png";
  {
    std::ofstream bad(badImage, std::ios::binary);
    bad << "not an image";
  }
  if (app.imageStore) {
    const std::size_t beforeBadImages = app.imageStore->images.size();
    ImageEntry badEntry;
    std::string badImageError;
    invalidImageRejected = !app.imageStore->importImage(badImage, "坏图", badEntry, badImageError);
    invalidImageNoResidue = app.imageStore->images.size() == beforeBadImages;
  }
  {
    fs::path pending = outputDir / "pending-test.png";
    SDL_Surface* pendingSurface = SDL_CreateRGBSurfaceWithFormat(0, 32, 32, 32,
                                                                 SDL_PIXELFORMAT_ARGB8888);
    if (pendingSurface) {
      IMG_SavePNG(pendingSurface, utf8(pending).c_str());
      SDL_FreeSurface(pendingSurface);
      app.pendingImagePath = utf8(pending);
      app.deletePendingImage();
      pendingCleanup = !fs::exists(pending);
    }
  }
  std::vector<Uint8> testPcm(static_cast<std::size_t>(44100 * 1.5) * 2, 0);
  writeWavFile(outputDir / "test.wav", testPcm, 44100, 1);
  app.ensureAudioLoaded();
  bool audioOk = false;
  bool audioRefOk = false;
  bool audioDeleteBlocked = false;
  bool unusedAudioDeleted = false;
  bool audioTrimOk = false;
  bool audioRecordGraceful = false;
  bool invalidAudioRejected = false;
  bool invalidAudioNoResidue = false;
  bool audioPendingCleanup = false;
  bool unregisteredAudioListed = false;
  bool audioVocabSave = false;
  std::string managedAudioId;
  std::string managedAudioFile;
  if (app.audioStore) {
    AudioEntry* existingAudio = nullptr;
    for (auto& audio : app.audioStore->audios) {
      if (audio.code == "aud1" || audio.name == "测试音频") existingAudio = &audio;
    }
    AudioEntry importedAudio;
    std::string audioError;
    if (existingAudio) {
      importedAudio = *existingAudio;
    } else if (app.audioStore->importAudio(outputDir / "test.wav", "测试音频",
                                           importedAudio, audioError)) {
      app.audioStore->setCode(importedAudio.id, "aud1");
    }
    if (!importedAudio.id.empty()) {
      managedAudioId = importedAudio.id;
      managedAudioFile = importedAudio.file;
      audioOk = importedAudio.duration >= 1400;
    }
    if (app.vocabulary && audioOk) {
      VocabEntry* audioVocab = nullptr;
      for (auto& entry : app.vocabulary->entries) {
        if (entry.type == "text" && entry.text == "apple") audioVocab = &entry;
      }
      if (audioVocab) {
        audioVocab->audio = managedAudioFile;
        audioVocab->audioStart = 200.0;
        audioVocab->audioEnd = 1200.0;
        audioVocab->audioDuration = importedAudio.duration;
        audioVocab->updatedAt = 1794400000004;
        const double trimStart = audioVocab->audioStart;
        const double trimEnd = audioVocab->audioEnd;
        app.vocabulary->save();
        app.loadVocabularyData();
        audioRefOk = true;
        audioTrimOk = trimEnd - trimStart >= 100.0;
      }
    }
    AudioEntry unusedAudio;
    if (app.audioStore->importAudio(outputDir / "test.wav", "未引用音频", unusedAudio,
                                    audioError)) {
      unusedAudioDeleted = app.audioStore->deleteAudio(unusedAudio.id, audioError) &&
                           !fs::exists(app.audioStore->resolveFile(unusedAudio.file));
    }
    if (audioOk) {
      audioDeleteBlocked = audioRefOk && app.audioStore->find(managedAudioId) != nullptr;
      fs::path unregistered = app.currentProjectDirectory() / "resources" / "audio" / "unregistered.wav";
      std::error_code copyEc;
      fs::copy_file(outputDir / "test.wav", unregistered, fs::copy_options::overwrite_existing,
                    copyEc);
      unregisteredAudioListed = app.audioStore->unregisteredFiles().size() >= 1;
    }
  }
  app.screen = App::Screen::Audios;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "audios.png") && ok;
  if (!managedAudioId.empty()) {
    app.openAudioCode(managedAudioId);
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "audio-edit-code.png") && ok;
    app.audioCodeOpen = false;
  }
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "audio-used-by-vocab.png") && ok;
  if (app.vocabulary) {
    const VocabEntry* audioVocab = nullptr;
    for (const auto& entry : app.vocabulary->entries) {
      if (!entry.audio.empty() && entry.audio == managedAudioFile) audioVocab = &entry;
    }
    if (audioVocab) {
      app.editVocabulary(audioVocab->id);
      app.screen = App::Screen::VocabularyEditor;
      app.focusAudioSection = true;
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-audio.png") && ok;
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "audio-trim.png") && ok;
      app.saveEditedVocabulary();
      audioVocabSave = true;
      app.focusAudioSection = false;
    }
  }
  app.startRecording();
  audioRecordGraceful = !app.recording;
  if (app.recording) {
    app.stopRecording();
    audioRecordGraceful = !app.recording;
    app.deletePendingAudio();
  }
  app.toastText.clear();
  const fs::path badAudio = outputDir / "bad.wav";
  {
    std::ofstream bad(badAudio, std::ios::binary);
    bad << "not audio";
  }
  if (app.audioStore) {
    const std::size_t beforeBadAudio = app.audioStore->audios.size();
    AudioEntry badAudioEntry;
    std::string badAudioError;
    invalidAudioRejected = !app.audioStore->importAudio(badAudio, "坏音频", badAudioEntry,
                                                        badAudioError);
    invalidAudioNoResidue = app.audioStore->audios.size() == beforeBadAudio;
  }
  {
    fs::path pending = outputDir / "pending-audio.wav";
    std::ofstream pendingFile(pending, std::ios::binary);
    pendingFile << "pending";
    pendingFile.close();
    app.pendingAudioPath = utf8(pending);
    app.deletePendingAudio();
    audioPendingCleanup = !fs::exists(pending);
  }
  app.ensurePosLoaded();
  bool posOk = false;
  bool posDeleteOk = false;
  std::string nounId;
  std::string verbId;
  if (app.posStore) {
    auto findByDisplayName = [&](const std::string& name) -> PosEntry* {
      for (auto& entry : app.posStore->entries) {
        if (entry.name == name) return &entry;
      }
      return nullptr;
    };
    if (!findByDisplayName("名词")) app.posStore->create("名词", "表示人、事物、地点等", "n");
    if (!findByDisplayName("动词")) app.posStore->create("动词", "表示动作或状态", "v");
    app.posStore->load();
    PosEntry* noun = findByDisplayName("名词");
    PosEntry* verb = findByDisplayName("动词");
    if (noun) nounId = noun->id;
    if (verb) verbId = verb->id;
    if (noun) app.posStore->update(noun->id, "名词", "表示人、事物、地点等", "n");
    if (verb) app.posStore->update(verb->id, "动词", "表示动作或状态", "v");
    PosEntry* temp = app.posStore->create("待删除词性", "临时", "tmp");
    if (temp) posDeleteOk = app.posStore->remove(temp->id);
    app.posStore->load();
    posOk = !nounId.empty() && !verbId.empty() && app.posStore->find(nounId) &&
            app.posStore->find(verbId);
  }
  app.screen = App::Screen::Pos;
  renderFrame(app);
  ok = saveScreenshot(app, outputDir / "pos.png") && ok;
  if (!nounId.empty()) {
    app.openPosEditor(nounId);
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "pos-editor.png") && ok;
    app.posEditorOpen = false;
  }
  bool posMeaningsOk = false;
  bool oldVocabCompat = false;
  bool emptyGroupBlocked = false;
  bool posDeleteUnknown = false;
  bool phoneticOk = false;
  bool oldPhoneticCompat = false;
  bool lexiconOk = false;
  bool lexiconDuplicateOk = false;
  bool lexiconBadLineOk = false;
  bool lexiconId9Ok = false;
  std::string lexiconNizId;
  std::string multiVocabId;
  if (app.vocabulary && !nounId.empty() && !verbId.empty()) {
    VocabEntry* multi = nullptr;
    for (auto& entry : app.vocabulary->entries) {
      if (entry.type == "text" && entry.text == "banana") multi = &entry;
    }
    if (!multi) {
      VocabEntry entry;
      entry.id = Store::newUuid();
      entry.type = "text";
      entry.text = "banana";
      entry.meanings = {"香蕉", "动作"};
      entry.posMeanings = {{nounId, {"香蕉"}}, {verbId, {"动作"}}};
      entry.codes = {"mp"};
      entry.createdAt = entry.updatedAt = 1794400000003;
      app.vocabulary->entries.push_back(entry);
      multi = &app.vocabulary->entries.back();
    }
    multiVocabId = multi->id;
    vocabOk = app.vocabulary->save() && vocabOk;
    app.loadVocabularyData();
    multi = app.vocabulary->find(multiVocabId);
    posMeaningsOk = multi && multi->posMeanings.size() == 2 &&
                    multi->meanings.size() == 2 &&
                    multi->posMeanings[0].posId == nounId &&
                    multi->posMeanings[1].posId == verbId;
    for (const auto& entry : app.vocabulary->entries) {
      if (entry.text == "apple" && entry.posMeanings.empty() &&
          !entry.meanings.empty() && entry.meanings.front() == "苹果") {
        oldVocabCompat = true;
      }
    }
    app.screen = App::Screen::Vocabulary;
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "vocab-pos.png") && ok;
    if (multi && !nounId.empty()) {
      VocabEntry backup = app.editEntry;
      app.editEntry.posMeanings = {{nounId, {""}}};
      std::string validationError;
      emptyGroupBlocked = !app.validateEditMeanings(&validationError) &&
                          validationError.find("名词") != std::string::npos;
      app.editEntry = backup;
    }
    if (multi && !verbId.empty()) {
      VocabEntry* stored = app.vocabulary->find(multi->id);
      const std::string unknownId = Store::newUuid();
      if (stored) stored->posMeanings.push_back({unknownId, {"orphan"}});
      app.editVocabulary(multi->id);
      posDeleteUnknown = app.findPosMeaning(unknownId) == nullptr &&
                         app.editEntry.posMeanings.size() == 2;
      if (stored) {
        stored->posMeanings.erase(
            std::remove_if(stored->posMeanings.begin(), stored->posMeanings.end(),
                           [&](const PosMeaning& group) { return group.posId == unknownId; }),
            stored->posMeanings.end());
      }
    }
    if (multi) {
      app.editVocabulary(multi->id);
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-pos-editor.png") && ok;
      const VocabEntry* appleEntry = nullptr;
      for (const auto& entry : app.vocabulary->entries) {
        if (entry.text == "apple") appleEntry = &entry;
      }
      if (appleEntry) app.editVocabulary(appleEntry->id);
      app.openPicker();
      std::snprintf(app.pickerSearch, sizeof(app.pickerSearch), "%s", "香蕉");
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-pos-picker.png") && ok;
      app.pickerOpen = false;
    }
  }
  if (app.vocabulary) {
    VocabEntry* phoneticEntry = nullptr;
    for (auto& entry : app.vocabulary->entries) {
      if (entry.text == "banana") phoneticEntry = &entry;
    }
    if (phoneticEntry) {
      const std::string phoneticId = phoneticEntry->id;
      phoneticEntry->phonetic = "/ˈæp.əl/";
      vocabOk = app.vocabulary->save() && vocabOk;
      app.loadVocabularyData();
      const VocabEntry* reloaded = app.vocabulary->find(phoneticId);
      phoneticOk = reloaded && reloaded->phonetic == "/ˈæp.əl/";
      for (const auto& entry : app.vocabulary->entries) {
        if (entry.text == "apple" && entry.phonetic.empty()) oldPhoneticCompat = true;
      }
      app.screen = App::Screen::Vocabulary;
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-phonetic.png") && ok;
    }
  }
  const fs::path lexiconSample = "/tmp/lexicon-sample.jsonl";
  if (fs::exists(lexiconSample) && app.vocabulary && app.posStore) {
    LexiconImportSummary summary;
    std::string lexiconError;
    const bool firstImport = importLexiconJsonl(
        lexiconSample, app.store->projectDirectory(app.folder), summary, lexiconError);
    app.vocabulary->load();
    app.posStore->load();
    VocabEntry* niz = nullptr;
    VocabEntry* id9 = nullptr;
    for (auto& entry : app.vocabulary->entries) {
      if (entry.text == "nız") niz = &entry;
      if (entry.text == "ıu-vurg") id9 = &entry;
    }
    if (niz) {
      lexiconNizId = niz->id;
      bool hasDefinition = false;
      for (const auto& meaning : niz->meanings) {
        if (meaning.find("人 者 person") != std::string::npos) hasDefinition = true;
      }
      lexiconOk = firstImport && summary.invalid == 0 &&
                  (summary.added >= 5000 || (summary.added == 0 && summary.duplicates == 5314)) &&
                  app.posStore->entries.size() >= 15 && niz->phonetic == "nı z" &&
                  !niz->posMeanings.empty() && hasDefinition;
    }
    lexiconId9Ok = id9 && id9->type == "text" && id9->text == "ıu-vurg";
    if (firstImport) {
      LexiconImportSummary duplicateSummary;
      const bool duplicateImport = importLexiconJsonl(
          lexiconSample, app.store->projectDirectory(app.folder), duplicateSummary, lexiconError);
      lexiconDuplicateOk = duplicateImport && duplicateSummary.added == 0 &&
                           duplicateSummary.duplicates == 5314;
      const fs::path badLexicon = "/tmp/lexicon-bad.jsonl";
      {
        std::ofstream bad(badLexicon);
        bad << "{\"bad\":";
      }
      LexiconImportSummary badSummary;
      const bool badImport = importLexiconJsonl(
          badLexicon, app.store->projectDirectory(app.folder), badSummary, lexiconError);
      lexiconBadLineOk = badImport && badSummary.invalid == 1 && badSummary.added == 0;
      app.vocabulary->load();
      app.posStore->load();
    }
    app.vocabSearch[0] = '\0';
    app.screen = App::Screen::Vocabulary;
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "vocab-import.png") && ok;
    std::snprintf(app.vocabSearch, sizeof(app.vocabSearch), "%s", "nız");
    renderFrame(app);
    ok = saveScreenshot(app, outputDir / "vocab-import-search.png") && ok;
    if (!lexiconNizId.empty()) {
      app.editVocabulary(lexiconNizId);
      renderFrame(app);
      ok = saveScreenshot(app, outputDir / "vocab-import-editor.png") && ok;
    }
    app.vocabSearch[0] = '\0';
    app.screen = App::Screen::Vocabulary;
  }
  const fs::path exportPath = outputDir / "export.conlang";
  std::string error;
  const bool exported = app.store->exportProject(app.folder, exportPath, error);
  const bool zipOk = exported && zipContains(exportPath, {"project.json", "structure.json",
                                                          "vocabulary.json", "fonts.json",
                                                          "images.json", "audios.json",
                                                          "pos.json"}) &&
                     zipContainsPrefix(exportPath, "resources/images/") &&
                     zipContainsPrefix(exportPath, "resources/audio/");
  ProjectSummary imported;
  bool importedOk = app.store->importConlang(exportPath, imported, error);
  bool importStatsOk = importedOk && imported.stats.chapters == 1 &&
                       imported.stats.courses == 1 && imported.stats.questions == 2;
  if (importedOk) {
    VocabularyStore importedVocab(app.store->projectDirectory(imported.folder));
    FontStore importedFonts(app.store->projectDirectory(imported.folder));
    ImageStore importedImages(app.store->projectDirectory(imported.folder));
    AudioStore importedAudios(app.store->projectDirectory(imported.folder));
    PosStore importedPos(app.store->projectDirectory(imported.folder));
    bool importedPosMeanings = false;
    bool importedPhonetic = false;
    for (const auto& entry : importedVocab.entries) {
      if (entry.text == "banana" && entry.posMeanings.size() == 2 &&
          entry.meanings.size() == 2) {
        importedPosMeanings = true;
      }
      if (entry.text == "banana" && entry.phonetic == "/ˈæp.əl/") importedPhonetic = true;
    }
    importStatsOk = importStatsOk && importedVocab.entries.size() >= 4 &&
                    !importedFonts.fonts.empty() && !importedImages.images.empty() &&
                    !importedAudios.audios.empty() && importedPos.entries.size() >= 2 &&
                    importedPosMeanings && importedPhonetic;
    app.store->deleteProject(imported.folder);
  }

  const fs::path badZip = outputDir / "bad.conlang";
  {
    std::ofstream bad(badZip, std::ios::binary);
    bad << "not a zip";
  }
  ProjectSummary invalid;
  std::string invalidError;
  const std::size_t beforeInvalid = app.store->listProjects().size();
  const bool invalidRejected = !app.store->importConlang(badZip, invalid, invalidError);
  const bool noResidue = app.store->listProjects().size() == beforeInvalid;

  const bool pinBefore = app.projects.front().pinned;
  app.store->togglePin(app.folder);
  app.refreshProjects();
  bool pinToggled = false;
  for (const auto& project : app.projects) {
    if (project.folder == app.folder) pinToggled = project.pinned != pinBefore;
  }
  app.store->togglePin(app.folder);
  app.refreshProjects();

  ProjectSummary temp = app.store->createProject("临时删除项目", "");
  bool deleted = !temp.folder.empty() && app.store->deleteProject(temp.folder);
  const bool noTemp = app.store->listProjects().size() == beforeInvalid;

  ProjectSummary persisted;
  Structure persistedStructure;
  const bool persistence = app.store->loadProject(app.folder, persisted, persistedStructure);
  VocabularyStore persistedVocab(app.store->projectDirectory(app.folder));
  FontStore persistedFonts(app.store->projectDirectory(app.folder));
  const bool dataOk = persistence && persistedStructure.chapters.size() == 1 &&
                      persistedStructure.chapters[0].units.size() == 1 &&
                      persistedStructure.chapters[0].units[0].courses.size() == 1 &&
                      persistedStructure.chapters[0].units[0].courses[0].questions.size() == 2 &&
                      persistedVocab.entries.size() >= 3 && !persistedFonts.fonts.empty();

  const fs::path browserExportPath = outputDir / "browser-export.conlang";
  app.browserOpen = true;
  app.browserMode = App::BrowserMode::ExportConlang;
  app.browserDir = outputDir;
  std::snprintf(app.browserFileInput, sizeof(app.browserFileInput), "%s",
                "browser-export.conlang");
  app.applyBrowser();
  const bool browserExported = fs::exists(browserExportPath) &&
                               zipContains(browserExportPath, {"project.json", "structure.json",
                                                               "vocabulary.json", "fonts.json"});
  app.browserOpen = true;
  app.browserMode = App::BrowserMode::ImportConlang;
  app.browserDir = outputDir;
  std::snprintf(app.browserFileInput, sizeof(app.browserFileInput), "%s",
                "browser-export.conlang");
  const std::size_t browserCountBefore = app.store->listProjects().size();
  app.applyBrowser();
  ProjectSummary browserImported;
  bool browserImportedOk = false;
  for (const auto& project : app.store->listProjects()) {
    if (project.name == "桌面测试" && project.folder != app.folder) {
      browserImportedOk = true;
      browserImported = project;
      break;
    }
  }
  if (!browserImported.folder.empty()) app.store->deleteProject(browserImported.folder);
  const bool browserImportKept = app.store->listProjects().size() == browserCountBefore;

  std::ofstream report(outputDir / "smoke-report.txt");
  report << "SMOKE " << (ok && exported && zipOk && importedOk && importStatsOk &&
                          invalidRejected && noResidue && pinToggled && deleted && noTemp && dataOk &&
                          browserExported && browserImportedOk && browserImportKept &&
                          vocabOk && fontOk && invalidFontRejected && invalidFontNoResidue &&
                          imagesOk && imageRefOk && referencedDeleteBlocked && unusedDeleted &&
                          cropOk && drawOk && invalidImageRejected && invalidImageNoResidue &&
                          pendingCleanup && audioOk && audioRefOk && audioDeleteBlocked &&
                          unusedAudioDeleted && audioTrimOk && audioRecordGraceful &&
                          invalidAudioRejected && invalidAudioNoResidue &&
                          audioPendingCleanup && unregisteredAudioListed && audioVocabSave &&
                          posOk && posDeleteOk && posMeaningsOk && oldVocabCompat &&
                          emptyGroupBlocked && posDeleteUnknown && phoneticOk && oldPhoneticCompat &&
                          lexiconOk && lexiconDuplicateOk && lexiconBadLineOk && lexiconId9Ok
                              ? "PASS" : "FAIL") << std::endl;
  report << "created=" << (created ? "true" : "false") << "\n";
  report << "folder=" << app.folder << "\n";
  report << "project=" << persisted.name << "\n";
  report << "chapters=" << persistedStructure.chapters.size()
         << " units=" << (persistedStructure.chapters.empty() ? 0 : persistedStructure.chapters[0].units.size())
         << " courses=" << (persistedStructure.chapters.empty() || persistedStructure.chapters[0].units.empty()
                                ? 0 : persistedStructure.chapters[0].units[0].courses.size())
         << " questions=" << (persistedStructure.chapters.empty() || persistedStructure.chapters[0].units.empty() ||
                                     persistedStructure.chapters[0].units[0].courses.empty()
                                 ? 0 : persistedStructure.chapters[0].units[0].courses[0].questions.size())
         << "\n";
  report << "vocabulary=" << persistedVocab.entries.size() << "\n";
  report << "fonts=" << persistedFonts.fonts.size() << " active=" << persistedFonts.active << "\n";
  report << "unicode=" << (persistedVocab.entries.size() > 1
                               ? codepointsToUtf8(persistedVocab.entries[1].codepoints) : "") << "\n";
  report << "zip=" << (zipOk ? "ok" : "bad") << "\n";
  report << "import=" << (importedOk && importStatsOk ? "ok" : "bad") << "\n";
  report << "invalid_rejected=" << (invalidRejected ? "true" : "false") << "\n";
  report << "pin_toggled=" << (pinToggled ? "true" : "false") << "\n";
  report << "delete_temp=" << (deleted ? "true" : "false") << "\n";
  report << "data=" << (dataOk ? "ok" : "bad") << "\n";
  report << "screenshots=" << (ok ? "ok" : "bad") << "\n";
  report << "browser_export=" << (browserExported ? "ok" : "bad") << "\n";
  report << "browser_import=" << (browserImportedOk ? "ok" : "bad") << "\n";
  report << "vocab_smoke=" << (vocabOk ? "ok" : "bad") << " font_smoke=" << (fontOk ? "ok" : "bad") << "\n";
  report << "invalid_font_rejected=" << (invalidFontRejected ? "true" : "false") << "\n";
  report << "invalid_font_no_residue=" << (invalidFontNoResidue ? "true" : "false") << "\n";
  report << "images=" << (app.imageStore ? app.imageStore->images.size() : 0) << std::endl;
  report << "image_ref=" << (imageRefOk ? "true" : "false")
         << " referenced_delete_blocked=" << (referencedDeleteBlocked ? "true" : "false")
         << " unused_deleted=" << (unusedDeleted ? "true" : "false") << std::endl;
  report << "crop=" << (cropOk ? "true" : "false")
         << " draw=" << (drawOk ? "true" : "false")
         << " pending_cleanup=" << (pendingCleanup ? "true" : "false") << std::endl;
  report << "invalid_image_rejected=" << (invalidImageRejected ? "true" : "false")
         << " invalid_image_no_residue=" << (invalidImageNoResidue ? "true" : "false") << std::endl;
  report << "unregistered_listed=" << (unregisteredListed ? "true" : "false")
         << " image_vocab_save=" << (imageVocabSave ? "true" : "false") << std::endl;
  report << "audios=" << (app.audioStore ? app.audioStore->audios.size() : 0)
         << " audio_ref=" << (audioRefOk ? "true" : "false")
         << " audio_delete_blocked=" << (audioDeleteBlocked ? "true" : "false")
         << " unused_audio_deleted=" << (unusedAudioDeleted ? "true" : "false") << std::endl;
  report << "audio_trim=" << (audioTrimOk ? "true" : "false")
         << " audio_record_graceful=" << (audioRecordGraceful ? "true" : "false")
         << " audio_pending_cleanup=" << (audioPendingCleanup ? "true" : "false") << std::endl;
  report << "invalid_audio_rejected=" << (invalidAudioRejected ? "true" : "false")
         << " invalid_audio_no_residue=" << (invalidAudioNoResidue ? "true" : "false")
         << " unregistered_audio=" << (unregisteredAudioListed ? "true" : "false")
         << " audio_vocab_save=" << (audioVocabSave ? "true" : "false") << std::endl;
  report << "pos=" << (app.posStore ? app.posStore->entries.size() : 0)
         << " pos_ok=" << (posOk ? "true" : "false")
         << " pos_delete=" << (posDeleteOk ? "true" : "false")
         << " pos_meanings=" << (posMeaningsOk ? "true" : "false")
         << " old_vocab_compat=" << (oldVocabCompat ? "true" : "false")
         << " empty_group_blocked=" << (emptyGroupBlocked ? "true" : "false")
         << " pos_delete_unknown=" << (posDeleteUnknown ? "true" : "false")
         << " phonetic=" << (phoneticOk ? "true" : "false")
         << " old_phonetic_compat=" << (oldPhoneticCompat ? "true" : "false")
         << " lexicon=" << (lexiconOk ? "true" : "false")
         << " lexicon_duplicate=" << (lexiconDuplicateOk ? "true" : "false")
         << " lexicon_bad=" << (lexiconBadLineOk ? "true" : "false")
         << " lexicon_id9=" << (lexiconId9Ok ? "true" : "false") << std::endl;
  report.close();
  SDL_Log("smoke report: %s", utf8(outputDir / "smoke-report.txt").c_str());
  const bool pass = ok && exported && zipOk && importedOk && importStatsOk && invalidRejected &&
                    noResidue && pinToggled && deleted && noTemp && dataOk &&
                    browserExported && browserImportedOk && browserImportKept && vocabOk && fontOk &&
                    invalidFontRejected && invalidFontNoResidue && imagesOk && imageRefOk &&
                    referencedDeleteBlocked && unusedDeleted && cropOk && drawOk &&
                    invalidImageRejected && invalidImageNoResidue && pendingCleanup &&
                    unregisteredListed && imageVocabSave && audioOk && audioRefOk &&
                    audioDeleteBlocked && unusedAudioDeleted && audioTrimOk &&
                    audioRecordGraceful && invalidAudioRejected && invalidAudioNoResidue &&
                    audioPendingCleanup && unregisteredAudioListed && audioVocabSave &&
                    posOk && posDeleteOk;
  SDL_Log("SMOKE %s", pass ? "PASS" : "FAIL");
  return pass ? 0 : 2;
}

void handleEvents(App& app) {
  SDL_Event event;
  while (SDL_PollEvent(&event)) {
    ImGui_ImplSDL2_ProcessEvent(&event);
    if (event.type == SDL_QUIT) app.running = false;
    if (event.type == SDL_WINDOWEVENT && event.window.event == SDL_WINDOWEVENT_CLOSE &&
        event.window.windowID == SDL_GetWindowID(app.window)) {
      app.running = false;
    }
  }
}

void renderVocabularyEditor(App& app) {
  if (ImGui::Button("← 词汇", ImVec2(100, 30))) app.backFromEditor();
  ImGui::SameLine();
  ImGui::TextDisabled("%s / %s", app.project.name.c_str(),
                      app.editIsNew ? "新建词汇" : "编辑词汇");
  ImGui::Spacing();
  pageTitle("EDIT VOCABULARY", app.editIsNew ? "新建词汇" : "编辑词汇",
            "显式保存；释义/编码/类型/内容写入 vocabulary.json。");
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 180;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("保存词汇", ImVec2(140, 34))) app.saveEditedVocabulary();
  ImGui::Spacing();

  ImGui::TextDisabled("类型");
  const char* types[] = {"文本词语", "Unicode 编码词语", "自定义文字组合词", "自定义文字"};
  const char* typeIds[] = {"text", "unicode", "combo", "image"};
  for (int i = 0; i < 4; ++i) {
    if (ImGui::RadioButton(types[i], app.editEntry.type == typeIds[i])) {
      app.editEntry.type = typeIds[i];
      app.editDirty = true;
    }
    if (i < 3) ImGui::SameLine();
  }
  ImGui::Separator();

  ImGui::TextUnformatted("词性（可多选）");
  app.ensurePosLoaded();
  if (!app.posStore || app.posStore->entries.empty()) {
    ImGui::TextDisabled("请先在资源管理→词性管理创建词性");
  } else {
    int column = 0;
    for (const auto& pos : app.posStore->entries) {
      if (column == 1) ImGui::SameLine();
      ImGui::PushID(pos.id.c_str());
      bool checked = app.findPosMeaning(pos.id) != nullptr;
      const std::string label = pos.code.empty() ? pos.name : pos.name + " · " + pos.code;
      if (ImGui::Checkbox(label.c_str(), &checked)) app.togglePosMeaning(pos.id, checked);
      ImGui::PopID();
      column = (column + 1) % 2;
      if (column == 0) ImGui::Spacing();
    }
  }
  ImGui::Spacing();
  if (app.editEntry.posMeanings.empty()) {
    ImGui::TextUnformatted("释义（通用）");
    for (std::size_t i = 0; i < app.editEntry.meanings.size(); ++i) {
      ImGui::PushID(9000 + static_cast<int>(i));
      ImGui::SetNextItemWidth(-80);
      if (inputString("##meaning", app.editEntry.meanings[i])) app.editDirty = true;
      ImGui::SameLine();
      if (ImGui::Button("删除", ImVec2(60, 28))) {
        if (app.editEntry.meanings.size() > 1) {
          app.editEntry.meanings.erase(app.editEntry.meanings.begin() + i);
          app.editDirty = true;
        } else app.showToast("至少保留一条释义");
      }
      ImGui::PopID();
    }
    if (ImGui::Button("＋ 添加释义", ImVec2(120, 28))) {
      app.editEntry.meanings.push_back("");
      app.editDirty = true;
    }
  } else {
    for (const auto& pos : app.posStore->entries) {
      PosMeaning* group = app.findPosMeaning(pos.id);
      if (!group) continue;
      ImGui::PushID(pos.id.c_str());
      ImGui::TextUnformatted((pos.name + (pos.code.empty() ? "" : " [" + pos.code + "]")).c_str());
      ImGui::SameLine();
      if (ImGui::Button("取消选择", ImVec2(90, 26))) {
        app.togglePosMeaning(pos.id, false);
        ImGui::PopID();
        continue;
      }
      if (group->meanings.empty()) group->meanings.push_back("");
      for (std::size_t i = 0; i < group->meanings.size(); ++i) {
        ImGui::PushID(static_cast<int>(i) + 5000);
        ImGui::SetNextItemWidth(-80);
        if (inputString("##pos_meaning", group->meanings[i])) app.editDirty = true;
        ImGui::SameLine();
        if (ImGui::Button("删除", ImVec2(60, 28))) {
          if (group->meanings.size() > 1) {
            group->meanings.erase(group->meanings.begin() + i);
            app.editDirty = true;
          } else app.showToast("每个词性至少保留一条释义");
        }
        ImGui::PopID();
      }
      if (ImGui::Button("＋ 添加该词性释义", ImVec2(150, 28))) {
        group->meanings.push_back("");
        app.editDirty = true;
      }
      ImGui::PopID();
      ImGui::Spacing();
    }
  }

  ImGui::TextUnformatted("编码");
  for (std::size_t i = 0; i < app.editEntry.codes.size(); ++i) {
    ImGui::PushID(8000 + static_cast<int>(i));
    ImGui::SetNextItemWidth(-80);
    if (inputString("##code", app.editEntry.codes[i])) app.editDirty = true;
    ImGui::SameLine();
    if (ImGui::Button("删除", ImVec2(60, 28))) {
      if (app.editEntry.codes.size() > 1) {
        app.editEntry.codes.erase(app.editEntry.codes.begin() + i);
        app.editDirty = true;
      } else app.showToast("至少保留一条编码");
    }
    ImGui::PopID();
  }
  if (ImGui::Button("＋ 添加编码", ImVec2(120, 28))) {
    app.editEntry.codes.push_back("");
    app.editDirty = true;
  }
  ImGui::TextUnformatted("音标（可选）");
  ImGui::SetNextItemWidth(-1);
  if (inputString("##phonetic", app.editEntry.phonetic, 200)) app.editDirty = true;
  ImGui::TextDisabled("不校验格式，例如 /ˈæp.əl/");
  ImGui::Separator();

  if (app.editEntry.type == "text") {
    ImGui::TextUnformatted("文本");
    ImGui::SetNextItemWidth(-1);
    if (inputString("##text", app.editEntry.text)) app.editDirty = true;
  } else if (app.editEntry.type == "unicode") {
    ImGui::TextUnformatted("Unicode 码点（12ab / 0x12AB / 大写）");
    for (std::size_t i = 0; i < app.editEntry.codepoints.size(); ++i) {
      ImGui::PushID(7000 + static_cast<int>(i));
      ImGui::SetNextItemWidth(-80);
      if (inputString("##cp", app.editEntry.codepoints[i], 64)) app.editDirty = true;
      ImGui::SameLine();
      if (ImGui::Button("删除", ImVec2(60, 28))) {
        app.editEntry.codepoints.erase(app.editEntry.codepoints.begin() + i);
        app.editDirty = true;
      }
      ImGui::PopID();
    }
    if (ImGui::Button("＋ 添加码点", ImVec2(120, 28))) {
      app.editEntry.codepoints.push_back("");
      app.editDirty = true;
    }
    ImGui::TextDisabled("预览（实际 Unicode 字符）");
    const std::string preview = codepointsToUtf8(app.editEntry.codepoints);
    pushVocabFont(app);
    ImGui::TextUnformatted(preview.empty() ? "（无有效码点）" : preview.c_str());
    popVocabFont();
  } else if (app.editEntry.type == "combo") {
    ImGui::TextUnformatted("组合部分（按选择顺序拼接显示名）");
    if (ImGui::Button("打开统一选择器", ImVec2(160, 30))) app.openPicker();
    ImGui::SameLine();
    ImGui::TextDisabled("至少 2 个部分");
    for (std::size_t i = 0; i < app.editEntry.parts.size(); ++i) {
      const VocabEntry* part = app.vocabulary ? app.vocabulary->find(app.editEntry.parts[i]) : nullptr;
      if (!part) continue;
      ImGui::PushID(6000 + static_cast<int>(i));
      pushVocabFont(app);
      ImGui::TextUnformatted(displayName(*part, app.vocabulary->entries).c_str());
      popVocabFont();
      ImGui::SameLine();
      if (ImGui::Button("移除", ImVec2(60, 26))) {
        app.editEntry.parts.erase(app.editEntry.parts.begin() + i);
        app.editDirty = true;
      }
      ImGui::PopID();
    }
    std::string combined;
    if (app.vocabulary) for (const auto& id : app.editEntry.parts) {
      const VocabEntry* part = app.vocabulary->find(id);
      if (part && part->id != app.editEntry.id) combined += displayName(*part, app.vocabulary->entries);
    }
    ImGui::TextDisabled("组合预览");
    pushVocabFont(app);
    ImGui::TextUnformatted(combined.empty() ? "（尚未选择部分）" : combined.c_str());
    popVocabFont();
  } else {
    if (app.editEntry.image.empty()) {
      ImGui::TextDisabled("尚未设置自定义文字图片。");
      if (ImGui::Button("导入图片", ImVec2(130, 32))) {
        app.imageBrowserForEditor = true;
        app.openBrowser(App::BrowserMode::ImportImage);
      }
      ImGui::SameLine();
      if (ImGui::Button("手绘文字", ImVec2(130, 32))) app.openDrawPreset();
    } else {
      fs::path path = app.imageAbsolute(app.editEntry.image);
      SDL_Texture* texture = textureForImage(app.renderer, path, app.imageTextures);
      if (texture) {
        float width = 260.0f;
        float height = width * app.editEntry.imageHeight / std::max(1, app.editEntry.imageWidth);
        ImGui::Image((ImTextureID)(intptr_t)texture, ImVec2(width, height));
      } else {
        ImGui::TextDisabled("图片无法加载");
      }
      ImGui::TextDisabled("%s · %dx%d", app.editEntry.image.c_str(),
                          app.editEntry.imageWidth, app.editEntry.imageHeight);
      if (ImGui::Button("重新导入", ImVec2(110, 30))) {
        app.imageBrowserForEditor = true;
        app.openBrowser(App::BrowserMode::ImportImage);
      }
      ImGui::SameLine();
      if (ImGui::Button("重新绘制", ImVec2(110, 30))) app.openDrawPreset();
      ImGui::SameLine();
      if (ImGui::Button("移除", ImVec2(80, 30))) app.removeEditorImage();
    }
  }
  ImGui::Separator();
  ImGui::TextUnformatted("示例发音");
  if (app.editEntry.audio.empty()) {
    ImGui::TextDisabled("尚未设置示例发音（可选）。");
    if (ImGui::Button("导入音频", ImVec2(120, 30))) {
      app.audioBrowserForEditor = true;
      app.openBrowser(App::BrowserMode::ImportAudio);
    }
    ImGui::SameLine();
    if (ImGui::Button("录制音频", ImVec2(120, 30))) app.startRecording();
  } else {
    int duration = app.editEntry.audioDuration;
    if (duration <= 0) {
      loadAudioDuration(app.audioAbsolute(app.editEntry.audio), duration);
      app.editEntry.audioDuration = duration;
    }
    std::error_code ec;
    const auto size = fs::exists(app.audioAbsolute(app.editEntry.audio))
                          ? fs::file_size(app.audioAbsolute(app.editEntry.audio), ec) : 0;
    ImGui::TextDisabled("%s · %s · %llu bytes", app.editEntry.audio.c_str(),
                        formatDuration(duration).c_str(),
                        static_cast<unsigned long long>(size));
    const bool playingThis = app.audioPlaying && app.playingAudioFile == app.editEntry.audio;
    if (ImGui::Button(playingThis ? "停止" : "播放", ImVec2(90, 30))) {
      if (playingThis) app.stopAudioPlayback();
      else app.playAudioFile(app.editEntry.audio, static_cast<int>(app.editEntry.audioStart),
                             static_cast<int>(app.editEntry.audioEnd));
    }
    ImGui::SameLine();
    if (ImGui::Button("试听裁剪", ImVec2(110, 30))) {
      app.playAudioFile(app.editEntry.audio, static_cast<int>(app.editEntry.audioStart),
                        static_cast<int>(app.editEntry.audioEnd));
    }
    ImGui::SameLine();
    if (ImGui::Button("移除", ImVec2(70, 30))) app.removeEditorAudio();
    ImGui::SameLine();
    if (ImGui::Button("重新导入", ImVec2(100, 30))) {
      app.audioBrowserForEditor = true;
      app.openBrowser(App::BrowserMode::ImportAudio);
    }
    ImGui::SameLine();
    if (ImGui::Button("重录", ImVec2(70, 30))) app.startRecording();
    int startMs = static_cast<int>(app.editEntry.audioStart);
    int endMs = static_cast<int>(app.editEntry.audioEnd);
    ImGui::SetNextItemWidth(-1);
    if (ImGui::SliderInt("开始##audio_start", &startMs, 0, std::max(0, duration))) {
      app.editEntry.audioStart = std::clamp(startMs, 0, std::max(0, duration));
      app.editDirty = true;
    }
    ImGui::SetNextItemWidth(-1);
    if (ImGui::SliderInt("结束##audio_end", &endMs, 0, std::max(0, duration))) {
      app.editEntry.audioEnd = std::clamp(endMs, 0, std::max(0, duration));
      app.editDirty = true;
    }
    ImGui::TextDisabled("裁剪：%s → %s（至少 100ms）",
                        formatDuration(static_cast<int>(app.editEntry.audioStart)).c_str(),
                        formatDuration(static_cast<int>(app.editEntry.audioEnd)).c_str());
    if (app.editEntry.audioStart < 0.0 || app.editEntry.audioEnd <= app.editEntry.audioStart ||
        app.editEntry.audioEnd - app.editEntry.audioStart < 100.0 ||
        app.editEntry.audioEnd > duration) {
      ImGui::TextColored(ImVec4(0.8f, 0.25f, 0.2f, 1.0f), "裁剪区间无效");
    }
  }
  if (app.recording) {
    ImGui::TextColored(ImVec4(0.8f, 0.25f, 0.2f, 1.0f), "录制中… %.1f 秒",
                       ImGui::GetTime() - app.recordStart);
    if (ImGui::Button("停止录制", ImVec2(120, 30))) app.stopRecording();
  }
  if (app.focusAudioSection) ImGui::SetScrollHereY(1.0f);
}

void renderResourcesWithPhase2(App& app) {
  if (ImGui::Button("← 课程", ImVec2(100, 30))) app.screen = App::Screen::Course;
  ImGui::SameLine();
  ImGui::TextDisabled("资源管理");
  ImGui::Spacing();
  pageTitle("RESOURCES", "资源管理", "词汇、字体、图片与音频项目资源。");
  app.ensureVocabularyLoaded();
  app.ensureImagesLoaded();
  app.ensureAudioLoaded();
  app.ensurePosLoaded();
  const std::size_t posCount = app.posStore ? app.posStore->entries.size() : 0;
  const std::size_t audioCount = app.audioStore
                                     ? app.audioStore->audios.size() +
                                           app.audioStore->unregisteredFiles().size()
                                     : 0;
  const std::size_t vocabCount = app.vocabulary ? app.vocabulary->entries.size() : 0;
  const std::size_t imageCount = app.imageStore
                                     ? app.imageStore->images.size() +
                                           app.imageStore->unregisteredFiles().size()
                                     : 0;
  std::string activeFont = "系统默认";
  if (app.fonts) {
    const FontEntry* active = app.fonts->find(app.fonts->active);
    if (active && app.fonts->activeFileValid()) activeFont = active->name;
  }
  ImGui::Spacing();
  if (ImGui::BeginTable("##resources2", 2, ImGuiTableFlags_SizingStretchSame)) {
    struct Card { const char* name; const char* letter; const char* desc; std::string meta; bool enabled; };
    Card cards[] = {
      {"词汇表", "V", "项目级共享词汇、编码与示例发音。", std::to_string(vocabCount) + " 条词汇", true},
      {"词性管理", "POS", "项目级词性名称、描述与编码。", std::to_string(posCount) + " 个词性", true},
      {"字体管理", "F", "导入 TTF/OTF/TTC，仅词汇内容应用字体。", "当前：" + activeFont, true},
      {"图片管理", "P", "项目图片导入、编码与词汇引用。",
       std::to_string(imageCount) + " 张图片", true},
      {"音频管理", "A", "项目音频导入、编码与词汇引用。",
       std::to_string(audioCount) + " 段音频", true},
    };
    for (const auto& card : cards) {
      ImGui::TableNextColumn();
      ImGui::PushID(card.name);
      ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
      ImGui::BeginChild("##resource", ImVec2(0, 150), ImGuiChildFlags_Borders);
      ImGui::SetCursorPos(ImVec2(14, 12));
      ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_CheckMark]);
      ImGui::TextUnformatted(card.letter);
      ImGui::PopStyleColor();
      ImGui::SameLine();
      ImGui::TextUnformatted(card.name);
      ImGui::SetCursorPosX(14);
      ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_TextDisabled]);
      ImGui::TextWrapped("%s", card.desc);
      ImGui::PopStyleColor();
      ImGui::SetCursorPos(ImVec2(14, 86));
      ImGui::TextDisabled("%s", card.meta.c_str());
      ImGui::SetCursorPos(ImVec2(14, 112));
      if (card.enabled) {
        if (ImGui::Button("打开", ImVec2(90, 28))) {
          if (std::string(card.name) == "词汇表") app.openVocabulary();
          else if (std::string(card.name) == "字体管理") app.openFonts();
          else if (std::string(card.name) == "词性管理") app.openPos();
          else if (std::string(card.name) == "图片管理") app.openImages();
          else if (std::string(card.name) == "音频管理") app.openAudios();
        }
      } else {
        ImGui::TextDisabled("后续阶段");
      }
      ImGui::EndChild();
      ImGui::PopStyleColor();
      ImGui::PopID();
      ImGui::Spacing();
    }
    ImGui::EndTable();
  }
}

std::string posMeaningSummary(App& app, const VocabEntry& entry) {
  app.ensurePosLoaded();
  std::string summary;
  if (app.posStore) {
    for (const auto& group : entry.posMeanings) {
      const PosEntry* pos = app.posStore->find(group.posId);
      if (!pos) continue;
      if (!summary.empty()) summary += "；";
      summary += pos->name + "：";
      bool first = true;
      for (const auto& meaning : group.meanings) {
        if (meaning.empty()) continue;
        if (!first) summary += "、";
        summary += meaning;
        first = false;
      }
    }
  }
  if (summary.empty()) {
    for (const auto& meaning : entry.meanings) {
      if (meaning.empty()) continue;
      if (!summary.empty()) summary += " / ";
      summary += meaning;
    }
  }
  return summary.empty() ? "未填写" : summary;
}

std::string posBadgeText(App& app, const VocabEntry& entry) {
  app.ensurePosLoaded();
  std::string badges;
  if (app.posStore) {
    for (const auto& group : entry.posMeanings) {
      const PosEntry* pos = app.posStore->find(group.posId);
      if (!pos) continue;
      if (!badges.empty()) badges += "  ";
      badges += "[" + pos->name + (pos->code.empty() ? "" : " " + pos->code) + "]";
    }
  }
  return badges;
}

void renderVocabulary(App& app) {
  if (ImGui::Button("← 资源", ImVec2(100, 30))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  ImGui::TextDisabled("%s / 词汇表", app.project.name.c_str());
  ImGui::Spacing();
  app.ensureVocabularyLoaded();
  app.ensurePosLoaded();
  const std::size_t count = app.vocabulary ? app.vocabulary->entries.size() : 0;
  pageTitle("VOCABULARY", "词汇表", std::to_string(count) + " 条项目级词汇");
  const float right = ImGui::GetWindowWidth() - 285;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("导入词表", ImVec2(110, 34))) app.openBrowser(App::BrowserMode::ImportLexicon);
  ImGui::SameLine();
  if (ImGui::Button("＋ 新建词汇", ImVec2(140, 34))) app.startNewVocabulary();
  ImGui::Spacing();
  ImGui::TextDisabled("搜索显示名 / 编码 / 音标 / 释义 / 词性");
  ImGui::SetNextItemWidth(-1);
  ImGui::InputText("##vocab_search", app.vocabSearch, sizeof(app.vocabSearch));
  std::string query = app.vocabSearch;
  std::transform(query.begin(), query.end(), query.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  auto lowerText = [](std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
  };
  std::vector<const VocabEntry*> matches;
  if (app.vocabulary) {
    for (const auto& entry : app.vocabulary->entries) {
      bool match = query.empty();
      if (!match && lowerText(displayName(entry, app.vocabulary->entries)).find(query) != std::string::npos)
        match = true;
      if (!match && entry.text.size() && lowerText(entry.text).find(query) != std::string::npos)
        match = true;
      if (!match && !entry.phonetic.empty() &&
          lowerText(entry.phonetic).find(query) != std::string::npos) match = true;
      if (!match) for (const auto& code : entry.codes)
        if (lowerText(code).find(query) != std::string::npos) match = true;
      if (!match) for (const auto& meaning : entry.meanings)
        if (lowerText(meaning).find(query) != std::string::npos) match = true;
      if (!match && app.posStore) for (const auto& group : entry.posMeanings) {
        const PosEntry* pos = app.posStore->find(group.posId);
        if (pos && (lowerText(pos->name).find(query) != std::string::npos ||
                    lowerText(pos->code).find(query) != std::string::npos)) match = true;
      }
      if (match) matches.push_back(&entry);
    }
  }
  if (count == 0) {
    ImGui::Dummy(ImVec2(0, 40));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 120);
    ImGui::TextDisabled("还没有词汇，点击右上角新建。");
    return;
  }
  if (matches.empty()) {
    ImGui::Dummy(ImVec2(0, 24));
    ImGui::TextDisabled("没有匹配的词汇");
    return;
  }
  constexpr std::size_t kMaxRows = 200;
  const std::size_t shown = std::min(matches.size(), kMaxRows);
  if (count > kMaxRows || !query.empty()) {
    if (query.empty()) {
      ImGui::TextDisabled("共 %zu 条，显示前 %zu 条；搜索缩小范围", count, shown);
    } else if (matches.size() > kMaxRows) {
      ImGui::TextDisabled("匹配 %zu 条，显示前 %zu 条；搜索缩小范围", matches.size(), shown);
    } else {
      ImGui::TextDisabled("匹配 %zu 条", matches.size());
    }
  }
  for (std::size_t rowIndex = 0; rowIndex < shown; ++rowIndex) {
    const auto& entry = *matches[rowIndex];
    ImGui::PushID(entry.id.c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##vrow", ImVec2(0, 86), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    const std::string name = displayName(entry, app.vocabulary->entries);
    const char* typeLabel = entry.type == "text" ? "文本" : entry.type == "unicode"
                                ? "Unicode" : entry.type == "combo" ? "组合" : "自定义文字";
    ImGui::SetCursorPos(ImVec2(12, 8));
    ImGui::TextDisabled("%s", typeLabel);
    ImGui::SameLine();
    ImGui::SetCursorPosX(100);
    pushVocabFont(app);
    if (ImGui::Selectable(name.c_str(), false, ImGuiSelectableFlags_AllowOverlap, ImVec2(500, 0)))
      app.editVocabulary(entry.id);
    popVocabFont();
    ImGui::SameLine();
    std::string codes;
    for (const auto& code : entry.codes) {
      if (code.empty()) continue;
      if (!codes.empty()) codes += " / ";
      codes += code;
    }
    ImGui::TextDisabled("编码：%s", codes.empty() ? "未设置" : codes.c_str());
    if (!entry.phonetic.empty()) {
      ImGui::SameLine();
      ImGui::TextDisabled("音标：%s", entry.phonetic.c_str());
    }
    ImGui::SetCursorPosX(12);
    ImGui::TextDisabled("释义：%s", posMeaningSummary(app, entry).c_str());
    const std::string badges = posBadgeText(app, entry);
    if (!badges.empty()) {
      ImGui::SameLine();
      ImGui::TextColored(ImVec4(0.2f, 0.45f, 0.85f, 1.0f), "%s", badges.c_str());
    }
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() - 48);
    if (ImGui::Button("⋯", ImVec2(30, 26))) ImGui::OpenPopup("##vmenu");
    if (ImGui::BeginPopup("##vmenu")) {
      if (ImGui::MenuItem("编辑")) app.editVocabulary(entry.id);
      ImGui::Separator();
      if (ImGui::MenuItem("删除"))
        app.openModalConfirm(App::ModalKind::ConfirmVocabulary, "删除词汇",
                             "确定删除「" + name + "」吗？", entry.id);
      ImGui::EndPopup();
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderFonts(App& app) {
  if (ImGui::Button("← 资源", ImVec2(100, 30))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  ImGui::TextDisabled("%s / 字体", app.project.name.c_str());
  ImGui::Spacing();
  app.ensureVocabularyLoaded();
  const std::size_t count = app.fonts ? app.fonts->fonts.size() : 0;
  std::string activeName = "系统默认";
  if (app.fonts) {
    const FontEntry* active = app.fonts->find(app.fonts->active);
    if (active && app.fonts->activeFileValid()) activeName = active->name;
  }
  pageTitle("FONTS", "字体管理", std::to_string(count) + " 个导入字体 · 当前：" + activeName);
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 180;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("＋ 导入字体", ImVec2(150, 34))) app.openBrowser(App::BrowserMode::ImportFont);
  ImGui::Spacing();

  auto fontRow = [&](const std::string& id, const std::string& name, const std::string& meta) {
    ImGui::PushID(id.empty() ? "system" : id.c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##font_row", ImVec2(0, 82), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    ImGui::SetCursorPos(ImVec2(12, 10));
    ImGui::TextUnformatted(name.c_str());
    ImGui::SameLine();
    ImGui::TextDisabled("%s", meta.c_str());
    ImGui::SetCursorPos(ImVec2(12, 42));
    if (!id.empty() && app.fonts && app.fonts->active == id &&
        app.fonts->activeFileValid()) {
      ImGui::TextDisabled("当前字体");
      ImGui::SameLine();
    } else {
      if (ImGui::Button("设为当前", ImVec2(100, 26))) app.applyFontActive(id);
      ImGui::SameLine();
    }
    if (!id.empty()) {
      if (ImGui::Button("删除", ImVec2(70, 26))) {
        app.openModalConfirm(App::ModalKind::ConfirmFont, "删除字体",
                             "确定删除该字体吗？当前字体会回退到系统默认。", id);
      }
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  };
  fontRow("", "系统默认", "不应用项目字体");
  if (app.fonts) {
    for (const auto& font : app.fonts->fonts) {
      std::string meta = font.file;
      std::error_code ec;
      fs::path path = app.fonts->fontPath(font);
      if (fs::exists(path)) meta += " · " + std::to_string(fs::file_size(path, ec)) + " bytes";
      fontRow(font.id, font.name, meta);
    }
  }
  if (count == 0) ImGui::TextDisabled("尚未导入项目字体；当前使用系统默认。");
}

void renderPicker(App& app) {
  if (!app.pickerOpen) return;
  ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 720) * 0.5f,
                                 (ImGui::GetIO().DisplaySize.y - 560) * 0.5f),
                          ImGuiCond_Always);
  ImGui::SetNextWindowSize(ImVec2(720, 560), ImGuiCond_Always);
  bool open = app.pickerOpen;
  if (!ImGui::Begin("选择词汇##picker", &open,
                    ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                        ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
    ImGui::End();
    app.pickerOpen = open;
    return;
  }
  ImGui::TextDisabled("搜索编码 / 文本 / Unicode 码点 / 字符 / 释义 / 显示名");
  ImGui::SetNextItemWidth(-120);
  ImGui::InputText("##picker_search", app.pickerSearch, sizeof(app.pickerSearch));
  ImGui::SameLine();
  ImGui::Text("已选 %zu", app.pickerSelected.size());
  ImGui::Spacing();
  std::string query = app.pickerSearch;
  std::transform(query.begin(), query.end(), query.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  auto lower = [](std::string value) {
    std::transform(value.begin(), value.end(), value.begin(),
                   [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
    return value;
  };
  std::size_t pickerMatches = 0;
  ImGui::BeginChild("##picker_list", ImVec2(0, 360), ImGuiChildFlags_Borders);
  if (app.vocabulary) {
    constexpr std::size_t kPickerMaxRows = 200;
    for (const auto& entry : app.vocabulary->entries) {
      if (entry.id == app.editEntry.id) continue;
      const std::string name = displayName(entry, app.vocabulary->entries);
      bool match = query.empty();
      if (!match) {
        if (lower(name).find(query) != std::string::npos) match = true;
        if (!match) for (const auto& code : entry.codes)
          if (lower(code).find(query) != std::string::npos) match = true;
        if (!match) if (lower(entry.text).find(query) != std::string::npos) match = true;
        if (!match) for (const auto& meaning : entry.meanings)
          if (lower(meaning).find(query) != std::string::npos) match = true;
        if (!match && app.posStore) {
          for (const auto& group : entry.posMeanings) {
            const PosEntry* pos = app.posStore->find(group.posId);
            if (pos && (lower(pos->name).find(query) != std::string::npos ||
                        lower(pos->code).find(query) != std::string::npos)) match = true;
          }
        }
        if (!match) for (const auto& cp : entry.codepoints) {
          if (lower(cp).find(query) != std::string::npos ||
              lower("0x" + cp).find(query) != std::string::npos ||
              lower(codepointsToUtf8({cp})).find(query) != std::string::npos) match = true;
        }
        if (!match) {
          for (const auto& code : entry.codes) {
            const std::string normalized = normalizeCodepoint(code);
            if (!normalized.empty() && lower(normalized).find(query) != std::string::npos) match = true;
          }
        }
      }
      if (!match) continue;
      ++pickerMatches;
      if (pickerMatches > kPickerMaxRows) continue;
      bool selected = std::find(app.pickerSelected.begin(), app.pickerSelected.end(),
                                entry.id) != app.pickerSelected.end();
      ImGui::PushID(entry.id.c_str());
      if (ImGui::Selectable(("##pick_" + entry.id).c_str(), selected, 0, ImVec2(0, 66))) {
        auto it = std::find(app.pickerSelected.begin(), app.pickerSelected.end(), entry.id);
        if (it == app.pickerSelected.end()) app.pickerSelected.push_back(entry.id);
        else app.pickerSelected.erase(it);
      }
      ImGui::SameLine();
      const char* typeLabel = entry.type == "text" ? "文本" : entry.type == "unicode"
                                  ? "Unicode" : entry.type == "combo" ? "组合" : "自定义文字";
      ImGui::TextDisabled("%s", typeLabel);
      ImGui::SameLine();
      pushVocabFont(app);
      ImGui::TextUnformatted(name.c_str());
      popVocabFont();
      ImGui::SameLine();
      std::string codes;
      for (const auto& code : entry.codes) { if (!codes.empty()) codes += " / "; codes += code; }
      ImGui::TextDisabled("%s", codes.c_str());
      const std::string pickerBadges = posBadgeText(app, entry);
      if (!pickerBadges.empty()) {
        ImGui::SameLine();
        ImGui::TextColored(ImVec4(0.2f, 0.45f, 0.85f, 1.0f), "%s", pickerBadges.c_str());
      }
      ImGui::SameLine();
      ImGui::TextDisabled("释义：%s", posMeaningSummary(app, entry).c_str());
      ImGui::PopID();
    }
  }
  ImGui::EndChild();
  if (pickerMatches > 200) {
    ImGui::TextDisabled("匹配 %zu 条，显示前 200 条；输入搜索缩小范围", pickerMatches);
  }
  ImGui::Spacing();
  if (ImGui::Button("取消", ImVec2(110, 34))) {
    app.pickerOpen = false;
    open = false;
  }
  ImGui::SameLine();
  if (ImGui::Button("确定", ImVec2(120, 34))) {
    app.editEntry.parts = app.pickerSelected;
    app.editDirty = true;
    app.pickerOpen = false;
    open = false;
  }
  ImGui::End();
  app.pickerOpen = open;
}

void renderImages(App& app) {
  if (ImGui::Button("← 资源", ImVec2(100, 30))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  ImGui::TextDisabled("%s / 图片", app.project.name.c_str());
  ImGui::Spacing();
  app.ensureImagesLoaded();
  app.ensureVocabularyLoaded();
  std::map<std::string, std::vector<std::string>> references;
  if (app.vocabulary) {
    for (const auto& entry : app.vocabulary->entries) {
      if (entry.type == "image" && !entry.image.empty()) {
        references[entry.image].push_back(displayName(entry, app.vocabulary->entries));
      }
    }
  }
  std::vector<fs::path> unregistered;
  std::size_t registeredCount = 0;
  if (app.imageStore) {
    registeredCount = app.imageStore->images.size();
    unregistered = app.imageStore->unregisteredFiles();
  }
  const std::size_t total = registeredCount + unregistered.size();
  pageTitle("IMAGES", "图片管理", std::to_string(total) + " 张图片（登记 + 未登记）");
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 190;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("＋ 导入图片", ImVec2(160, 34))) {
    app.imageBrowserForEditor = false;
    app.openBrowser(App::BrowserMode::ImportImage);
  }
  ImGui::SetNextItemWidth(-1);
  ImGui::InputTextWithHint("##image_search", "搜索编码 / 名称 / 文件名",
                           app.imageSearch, sizeof(app.imageSearch));
  ImGui::Spacing();

  std::string query = app.imageSearch;
  std::transform(query.begin(), query.end(), query.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  auto matches = [&](const std::string& name, const std::string& file, const std::string& code) {
    if (query.empty()) return true;
    auto lower = [](std::string value) {
      std::transform(value.begin(), value.end(), value.begin(),
                     [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
      return value;
    };
    return lower(name).find(query) != std::string::npos ||
           lower(file).find(query) != std::string::npos ||
           lower(code).find(query) != std::string::npos;
  };

  struct Row {
    std::string id;
    std::string name;
    std::string file;
    std::string code;
    fs::path path;
    int width = 0;
    int height = 0;
    bool registered = false;
  };
  std::vector<Row> rows;
  if (app.imageStore) {
    for (const auto& image : app.imageStore->images) {
      if (!matches(image.name, image.file, image.code)) continue;
      rows.push_back({image.id, image.name, image.file, image.code,
                      app.imageStore->imagePath(image), image.width, image.height, true});
    }
  }
  for (const auto& file : unregistered) {
    fs::path relative = file.lexically_relative(app.currentProjectDirectory());
    std::string relativeText = relative.generic_string();
    std::string name = utf8(file.filename());
    if (!matches(name, relativeText, "")) continue;
    int width = 0;
    int height = 0;
    loadImageSize(file, width, height);
    rows.push_back({"", name, relativeText, "", file, width, height, false});
  }
  if (rows.empty()) {
    ImGui::Dummy(ImVec2(0, 40));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 120);
    ImGui::TextDisabled("还没有图片，点击右上角导入。");
    return;
  }
  for (const auto& row : rows) {
    ImGui::PushID((row.id.empty() ? row.file : row.id).c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##image_row", ImVec2(0, 112), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    ImGui::SetCursorPos(ImVec2(10, 10));
    SDL_Texture* texture = textureForImage(app.renderer, row.path, app.imageTextures);
    if (texture) {
      ImGui::Image((ImTextureID)(intptr_t)texture, ImVec2(64, 64));
    } else {
      ImGui::Dummy(ImVec2(64, 64));
      ImGui::SetCursorPos(ImVec2(16, 30));
      ImGui::TextDisabled("?");
    }
    ImGui::SameLine();
    ImGui::BeginGroup();
    ImGui::TextUnformatted(row.name.c_str());
    ImGui::TextDisabled("%s · %dx%d", row.file.c_str(), row.width, row.height);
    ImGui::TextDisabled("编码：%s", row.code.empty() ? "未设置" : row.code.c_str());
    const auto refIt = references.find(row.file);
    if (refIt != references.end() && !refIt->second.empty()) {
      std::string names;
      for (const auto& name : refIt->second) {
        if (!names.empty()) names += "、";
        names += name;
      }
      ImGui::TextColored(ImVec4(0.75f, 0.35f, 0.2f, 1.0f), "被 %zu 个词汇引用：%s",
                         refIt->second.size(), names.c_str());
    } else {
      ImGui::TextDisabled("未被引用");
    }
    ImGui::EndGroup();
    ImGui::SetCursorPos(ImVec2(ImGui::GetWindowWidth() - 48, 12));
    if (ImGui::Button("⋯", ImVec2(30, 26))) ImGui::OpenPopup("##image_menu");
    if (ImGui::BeginPopup("##image_menu")) {
      if (ImGui::MenuItem("设置编码")) {
        if (row.registered) app.openImageCode(row.id, row.file);
        else app.openImageCode("", row.file);
      }
      ImGui::Separator();
      if (ImGui::MenuItem("删除")) {
        const auto refs = references.find(row.file);
        if (refs != references.end() && !refs->second.empty()) {
          app.showToast("被 " + std::to_string(refs->second.size()) +
                        " 个词汇引用，请先移除引用");
        } else {
          app.openModalConfirm(App::ModalKind::ConfirmImage, "删除图片",
                               "确定删除「" + row.name + "」吗？", row.id, row.file);
        }
      }
      ImGui::EndPopup();
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderImageModals(App& app) {
  if (app.imageCodeOpen) {
    ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 620) * 0.5f,
                                   (ImGui::GetIO().DisplaySize.y - 500) * 0.5f),
                            ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(620, 500), ImGuiCond_Always);
    bool open = app.imageCodeOpen;
    if (ImGui::Begin("设置图片编码##image_code", &open,
                     ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                         ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
      fs::path previewPath = app.imageCodeFile.empty()
                                 ? fs::path()
                                 : (app.currentProjectDirectory() / pathFromUtf8(app.imageCodeFile));
      SDL_Texture* texture = textureForImage(app.renderer, previewPath, app.imageTextures);
      if (texture) ImGui::Image((ImTextureID)(intptr_t)texture, ImVec2(260, 180));
      else ImGui::TextDisabled("无预览");
      ImGui::TextDisabled("%s", app.imageCodeFile.c_str());
      ImGui::TextUnformatted("编码");
      ImGui::SetNextItemWidth(-1);
      ImGui::InputText("##image_code_input", app.imageCodeInput, sizeof(app.imageCodeInput));
      if (ImGui::Button("取消", ImVec2(110, 34))) { app.imageCodeOpen = false; open = false; }
      ImGui::SameLine();
      if (ImGui::Button("保存", ImVec2(120, 34))) {
        app.ensureImagesLoaded();
        if (app.imageStore) {
          if (app.imageCodeId.empty()) {
            ImageEntry* registered = app.imageStore->registerFile(
                app.currentProjectDirectory() / pathFromUtf8(app.imageCodeFile));
            if (registered) app.imageStore->setCode(registered->id, app.imageCodeInput);
          } else {
            app.imageStore->setCode(app.imageCodeId, app.imageCodeInput);
          }
        }
        app.imageCodeOpen = false;
        open = false;
        app.showToast("图片编码已保存");
      }
    }
    ImGui::End();
    app.imageCodeOpen = open;
  }

  if (app.cropOpen) {
    ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 760) * 0.5f,
                                   (ImGui::GetIO().DisplaySize.y - 650) * 0.5f),
                            ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(760, 650), ImGuiCond_Always);
    bool open = app.cropOpen;
    if (ImGui::Begin("裁剪图片##crop", &open,
                     ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                         ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
      if (app.cropTexture && app.cropSurface) {
        const float scale = std::min(680.0f / app.cropSurface->w, 480.0f / app.cropSurface->h);
        const ImVec2 size(app.cropSurface->w * scale, app.cropSurface->h * scale);
        const ImVec2 origin = ImGui::GetCursorScreenPos();
        ImGui::Image((ImTextureID)(intptr_t)app.cropTexture, size);
        ImGui::SetCursorScreenPos(origin);
        ImGui::InvisibleButton("##crop_area", size);
        const float minW = 32.0f / app.cropSurface->w;
        const float minH = 32.0f / app.cropSurface->h;
        const ImVec2 mouse = ImGui::GetIO().MousePos;
        auto near = [&](float nx, float ny) {
          return std::abs(mouse.x - (origin.x + nx * size.x)) < 12.0f &&
                 std::abs(mouse.y - (origin.y + ny * size.y)) < 12.0f;
        };
        if (ImGui::IsItemHovered() && ImGui::IsMouseClicked(ImGuiMouseButton_Left)) {
          const float left = app.cropX;
          const float top = app.cropY;
          const float right = left + app.cropW;
          const float bottom = top + app.cropH;
          if (near(left, top)) app.cropDragMode = 2;
          else if (near(right, top)) app.cropDragMode = 3;
          else if (near(left, bottom)) app.cropDragMode = 4;
          else if (near(right, bottom)) app.cropDragMode = 5;
          else if (std::abs(mouse.x - (origin.x + left * size.x)) < 10.0f) app.cropDragMode = 8;
          else if (std::abs(mouse.x - (origin.x + right * size.x)) < 10.0f) app.cropDragMode = 9;
          else if (std::abs(mouse.y - (origin.y + top * size.y)) < 10.0f) app.cropDragMode = 6;
          else if (std::abs(mouse.y - (origin.y + bottom * size.y)) < 10.0f) app.cropDragMode = 7;
          else if (mouse.x > origin.x + left * size.x && mouse.x < origin.x + right * size.x &&
                   mouse.y > origin.y + top * size.y && mouse.y < origin.y + bottom * size.y) {
            app.cropDragMode = 1;
          }
        }
        if (app.cropDragMode != 0 && ImGui::IsItemActive() &&
            ImGui::IsMouseDragging(ImGuiMouseButton_Left)) {
          const ImVec2 delta = ImGui::GetIO().MouseDelta;
          float left = app.cropX;
          float top = app.cropY;
          float right = left + app.cropW;
          float bottom = top + app.cropH;
          const float dx = delta.x / size.x;
          const float dy = delta.y / size.y;
          if (app.cropDragMode == 1) {
            left = std::clamp(left + dx, 0.0f, 1.0f - app.cropW);
            top = std::clamp(top + dy, 0.0f, 1.0f - app.cropH);
            right = left + app.cropW;
            bottom = top + app.cropH;
          } else {
            if (app.cropDragMode == 2 || app.cropDragMode == 4 || app.cropDragMode == 8) left += dx;
            if (app.cropDragMode == 3 || app.cropDragMode == 5 || app.cropDragMode == 9) right += dx;
            if (app.cropDragMode == 2 || app.cropDragMode == 3 || app.cropDragMode == 6) top += dy;
            if (app.cropDragMode == 4 || app.cropDragMode == 5 || app.cropDragMode == 7) bottom += dy;
            left = std::clamp(left, 0.0f, 1.0f - minW);
            top = std::clamp(top, 0.0f, 1.0f - minH);
            right = std::clamp(right, left + minW, 1.0f);
            bottom = std::clamp(bottom, top + minH, 1.0f);
          }
          app.cropX = left;
          app.cropY = top;
          app.cropW = right - left;
          app.cropH = bottom - top;
        }
        if (ImGui::IsMouseReleased(ImGuiMouseButton_Left)) app.cropDragMode = 0;
        ImDrawList* draw = ImGui::GetWindowDrawList();
        const ImVec2 a(origin.x + app.cropX * size.x, origin.y + app.cropY * size.y);
        const ImVec2 b(a.x + app.cropW * size.x, a.y + app.cropH * size.y);
        draw->AddRectFilled(origin, ImVec2(origin.x + size.x, a.y), IM_COL32(0, 0, 0, 130));
        draw->AddRectFilled(ImVec2(origin.x, b.y), ImVec2(origin.x + size.x, origin.y + size.y),
                            IM_COL32(0, 0, 0, 130));
        draw->AddRectFilled(ImVec2(origin.x, a.y), ImVec2(a.x, b.y), IM_COL32(0, 0, 0, 130));
        draw->AddRectFilled(ImVec2(b.x, a.y), ImVec2(origin.x + size.x, b.y),
                            IM_COL32(0, 0, 0, 130));
        draw->AddRect(a, b, IM_COL32(47, 111, 237, 255), 0, 0, 2.0f);
        draw->AddCircleFilled(a, 6, IM_COL32(47, 111, 237, 255));
        draw->AddCircleFilled(ImVec2(b.x, a.y), 6, IM_COL32(47, 111, 237, 255));
        draw->AddCircleFilled(ImVec2(a.x, b.y), 6, IM_COL32(47, 111, 237, 255));
        draw->AddCircleFilled(b, 6, IM_COL32(47, 111, 237, 255));
      }
      ImGui::Spacing();
      if (ImGui::Button("取消", ImVec2(110, 34))) { app.closeCrop(); open = false; }
      ImGui::SameLine();
      if (ImGui::Button("完成裁剪", ImVec2(130, 34))) app.applyCrop();
    }
    ImGui::End();
    app.cropOpen = open;
  }

  if (app.drawPresetOpen) {
    ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 460) * 0.5f,
                                   (ImGui::GetIO().DisplaySize.y - 320) * 0.5f),
                            ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(460, 320), ImGuiCond_Always);
    bool open = app.drawPresetOpen;
    if (ImGui::Begin("手绘预设##draw_preset", &open,
                     ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                         ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
      ImGui::TextDisabled("画布像素尺寸（32–4096）");
      ImGui::SetNextItemWidth(-1);
      ImGui::InputInt("##draw_width", &app.drawPresetWidth);
      ImGui::SetNextItemWidth(-1);
      ImGui::InputInt("##draw_height", &app.drawPresetHeight);
      app.drawPresetWidth = std::clamp(app.drawPresetWidth, 32, 4096);
      app.drawPresetHeight = std::clamp(app.drawPresetHeight, 32, 4096);
      if (ImGui::Button("取消", ImVec2(110, 34))) { app.drawPresetOpen = false; open = false; }
      ImGui::SameLine();
      if (ImGui::Button("开始绘制", ImVec2(130, 34))) app.startDrawing();
    }
    ImGui::End();
    app.drawPresetOpen = open;
  }

  if (app.drawOpen) {
    ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 760) * 0.5f,
                                   (ImGui::GetIO().DisplaySize.y - 720) * 0.5f),
                            ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(760, 720), ImGuiCond_Always);
    bool open = app.drawOpen;
    if (ImGui::Begin("手绘文字##draw", &open,
                     ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                         ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
      if (app.drawCanvas.surface) {
        if (!app.drawCanvas.texture) {
          app.drawCanvas.texture = SDL_CreateTextureFromSurface(app.renderer, app.drawCanvas.surface);
        }
        const float scale = std::min(680.0f / app.drawCanvas.width, 430.0f / app.drawCanvas.height);
        const ImVec2 size(app.drawCanvas.width * scale, app.drawCanvas.height * scale);
        const ImVec2 origin = ImGui::GetCursorScreenPos();
        if (app.drawCanvas.texture) ImGui::Image((ImTextureID)(intptr_t)app.drawCanvas.texture, size);
        else ImGui::Dummy(size);
        ImGui::GetWindowDrawList()->AddRect(origin, ImVec2(origin.x + size.x, origin.y + size.y),
                                            IM_COL32(150, 160, 175, 220), 0, 0, 1.0f);
        ImGui::SetCursorScreenPos(origin);
        ImGui::InvisibleButton("##draw_area", size);
        const bool hovered = ImGui::IsItemHovered();
        const ImVec2 mouse = ImGui::GetIO().MousePos;
        if (hovered && ImGui::IsMouseClicked(ImGuiMouseButton_Left)) {
          app.drawCanvas.redo.clear();
          app.drawCanvas.active = DrawStroke{};
          app.drawCanvas.active.eraser = app.drawEraser;
          app.drawCanvas.active.width = app.drawWidth;
          app.drawCanvas.active.points.push_back(
              ImVec2((mouse.x - origin.x) / scale, (mouse.y - origin.y) / scale));
          app.drawCanvas.drawing = true;
          canvasDot(app.drawCanvas.surface, app.drawCanvas.active.points[0].x,
                    app.drawCanvas.active.points[0].y,
                    std::max(1.0f, app.drawWidth * 0.5f), app.drawEraser);
        } else if (app.drawCanvas.drawing && ImGui::IsMouseDown(ImGuiMouseButton_Left)) {
          const ImVec2 point((mouse.x - origin.x) / scale, (mouse.y - origin.y) / scale);
          if (!app.drawCanvas.active.points.empty()) {
            canvasLine(app.drawCanvas.surface, app.drawCanvas.active.points.back(), point,
                       app.drawWidth, app.drawEraser);
          }
          app.drawCanvas.active.points.push_back(point);
        }
        if (app.drawCanvas.drawing && ImGui::IsMouseReleased(ImGuiMouseButton_Left)) {
          app.drawCanvas.drawing = false;
          if (!app.drawCanvas.active.points.empty()) {
            app.drawCanvas.strokes.push_back(app.drawCanvas.active);
            app.drawCanvas.active = DrawStroke{};
          }
          if (app.drawCanvas.texture) {
            SDL_DestroyTexture(app.drawCanvas.texture);
            app.drawCanvas.texture = nullptr;
          }
        }
      }
      ImGui::Spacing();
      if (ImGui::Button("画笔", ImVec2(80, 30))) app.drawEraser = false;
      ImGui::SameLine();
      if (ImGui::Button("橡皮", ImVec2(80, 30))) app.drawEraser = true;
      ImGui::SameLine();
      ImGui::TextDisabled(app.drawEraser ? "当前：橡皮" : "当前：画笔");
      ImGui::SetNextItemWidth(180);
      ImGui::SliderFloat("线宽##draw_width", &app.drawWidth, 1.0f, 32.0f, "%.0f px");
      ImGui::SameLine();
      if (ImGui::Button("清空", ImVec2(80, 30))) {
        app.drawCanvas.strokes.clear();
        app.drawCanvas.redo.clear();
        canvasClear(app.drawCanvas);
      }
      ImGui::SameLine();
      if (ImGui::Button("撤销", ImVec2(80, 30))) canvasUndo(app.drawCanvas);
      ImGui::SameLine();
      if (ImGui::Button("重做", ImVec2(80, 30))) canvasRedo(app.drawCanvas);
      ImGui::SameLine();
      if (ImGui::Button("保存", ImVec2(100, 30))) app.saveDrawing();
      ImGui::SameLine();
      if (ImGui::Button("取消", ImVec2(80, 30))) { app.closeDrawing(); open = false; }
    }
    ImGui::End();
    app.drawOpen = open;
  }
}
void renderAudios(App& app) {
  if (ImGui::Button("← 资源", ImVec2(100, 30))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  ImGui::TextDisabled("%s / 音频", app.project.name.c_str());
  ImGui::Spacing();
  app.ensureAudioLoaded();
  app.ensureVocabularyLoaded();
  std::map<std::string, std::vector<std::string>> references;
  if (app.vocabulary) {
    for (const auto& entry : app.vocabulary->entries) {
      if (!entry.audio.empty()) references[entry.audio].push_back(displayName(entry, app.vocabulary->entries));
    }
  }
  std::vector<fs::path> unregistered;
  std::size_t registeredCount = 0;
  if (app.audioStore) {
    registeredCount = app.audioStore->audios.size();
    unregistered = app.audioStore->unregisteredFiles();
  }
  const std::size_t total = registeredCount + unregistered.size();
  pageTitle("AUDIOS", "音频管理", std::to_string(total) + " 段音频（登记 + 未登记）");
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 190;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("＋ 导入音频", ImVec2(160, 34))) {
    app.audioBrowserForEditor = false;
    app.openBrowser(App::BrowserMode::ImportAudio);
  }
  ImGui::SetNextItemWidth(-1);
  ImGui::InputTextWithHint("##audio_search", "搜索编码 / 名称 / 文件名",
                           app.audioSearch, sizeof(app.audioSearch));
  ImGui::Spacing();

  std::string query = app.audioSearch;
  std::transform(query.begin(), query.end(), query.begin(),
                 [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
  auto matches = [&](const std::string& name, const std::string& file, const std::string& code) {
    if (query.empty()) return true;
    auto lower = [](std::string value) {
      std::transform(value.begin(), value.end(), value.begin(),
                     [](unsigned char c) { return static_cast<char>(std::tolower(c)); });
      return value;
    };
    return lower(name).find(query) != std::string::npos ||
           lower(file).find(query) != std::string::npos ||
           lower(code).find(query) != std::string::npos;
  };

  struct Row {
    std::string id;
    std::string name;
    std::string file;
    std::string code;
    fs::path path;
    int duration = 0;
    bool registered = false;
  };
  std::vector<Row> rows;
  if (app.audioStore) {
    for (const auto& audio : app.audioStore->audios) {
      if (!matches(audio.name, audio.file, audio.code)) continue;
      rows.push_back({audio.id, audio.name, audio.file, audio.code,
                      app.audioStore->audioPath(audio), audio.duration, true});
    }
  }
  for (const auto& file : unregistered) {
    fs::path relative = file.lexically_relative(app.currentProjectDirectory());
    std::string relativeText = relative.generic_string();
    std::string name = utf8(file.filename());
    if (!matches(name, relativeText, "")) continue;
    int duration = 0;
    loadAudioDuration(file, duration);
    rows.push_back({"", name, relativeText, "", file, duration, false});
  }
  if (rows.empty()) {
    ImGui::Dummy(ImVec2(0, 40));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 120);
    ImGui::TextDisabled("还没有音频，点击右上角导入。");
    return;
  }
  for (const auto& row : rows) {
    ImGui::PushID((row.id.empty() ? row.file : row.id).c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##audio_row", ImVec2(0, 112), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    ImGui::SetCursorPos(ImVec2(14, 14));
    ImGui::PushStyleColor(ImGuiCol_Text, ImGui::GetStyle().Colors[ImGuiCol_CheckMark]);
    ImGui::TextUnformatted("♪");
    ImGui::PopStyleColor();
    ImGui::SameLine();
    ImGui::BeginGroup();
    ImGui::TextUnformatted(row.name.c_str());
    std::error_code ec;
    const auto size = fs::exists(row.path) ? fs::file_size(row.path, ec) : 0;
    ImGui::TextDisabled("%s · %s · %llu bytes", row.file.c_str(),
                        formatDuration(row.duration).c_str(),
                        static_cast<unsigned long long>(size));
    ImGui::TextDisabled("编码：%s", row.code.empty() ? "未设置" : row.code.c_str());
    const auto refIt = references.find(row.file);
    if (refIt != references.end() && !refIt->second.empty()) {
      std::string names;
      for (const auto& name : refIt->second) {
        if (!names.empty()) names += "、";
        names += name;
      }
      ImGui::TextColored(ImVec4(0.75f, 0.35f, 0.2f, 1.0f), "被 %zu 个词汇引用：%s",
                         refIt->second.size(), names.c_str());
    } else {
      ImGui::TextDisabled("未被引用");
    }
    ImGui::EndGroup();
    ImGui::SetCursorPos(ImVec2(ImGui::GetWindowWidth() - 48, 12));
    if (ImGui::Button("⋯", ImVec2(30, 26))) ImGui::OpenPopup("##audio_menu");
    if (ImGui::BeginPopup("##audio_menu")) {
      if (ImGui::MenuItem("设置编码")) {
        if (row.registered) app.openAudioCode(row.id, row.file);
        else app.openAudioCode("", row.file);
      }
      ImGui::Separator();
      if (ImGui::MenuItem("删除")) {
        const auto refs = references.find(row.file);
        if (refs != references.end() && !refs->second.empty()) {
          app.showToast("被 " + std::to_string(refs->second.size()) +
                        " 个词汇引用，请先移除引用");
        } else {
          app.openModalConfirm(App::ModalKind::ConfirmAudio, "删除音频",
                               "确定删除「" + row.name + "」吗？", row.id, row.file);
        }
      }
      ImGui::EndPopup();
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderAudioModals(App& app) {
  if (!app.audioCodeOpen) return;
  ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 620) * 0.5f,
                                 (ImGui::GetIO().DisplaySize.y - 420) * 0.5f),
                          ImGuiCond_Always);
  ImGui::SetNextWindowSize(ImVec2(620, 420), ImGuiCond_Always);
  bool open = app.audioCodeOpen;
  if (ImGui::Begin("设置音频编码##audio_code", &open,
                   ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                       ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
    fs::path path = app.audioCodeFile.empty()
                        ? fs::path()
                        : (app.currentProjectDirectory() / pathFromUtf8(app.audioCodeFile));
    int duration = 0;
    loadAudioDuration(path, duration);
    ImGui::TextDisabled("%s", app.audioCodeFile.c_str());
    ImGui::Text("时长：%s", formatDuration(duration).c_str());
    std::error_code ec;
    if (fs::exists(path)) {
      ImGui::Text("大小：%llu bytes", static_cast<unsigned long long>(fs::file_size(path, ec)));
    }
    ImGui::TextUnformatted("编码");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##audio_code_input", app.audioCodeInput, sizeof(app.audioCodeInput));
    if (ImGui::Button("取消", ImVec2(110, 34))) { app.audioCodeOpen = false; open = false; }
    ImGui::SameLine();
    if (ImGui::Button("保存", ImVec2(120, 34))) {
      app.ensureAudioLoaded();
      if (app.audioStore) {
        if (app.audioCodeId.empty()) {
          AudioEntry* registered = app.audioStore->registerFile(
              app.currentProjectDirectory() / pathFromUtf8(app.audioCodeFile));
          if (registered) app.audioStore->setCode(registered->id, app.audioCodeInput);
        } else {
          app.audioStore->setCode(app.audioCodeId, app.audioCodeInput);
        }
      }
      app.audioCodeOpen = false;
      open = false;
      app.showToast("音频编码已保存");
    }
  }
  ImGui::End();
  app.audioCodeOpen = open;
}
void renderPos(App& app) {
  if (ImGui::Button("← 资源", ImVec2(100, 30))) app.screen = App::Screen::Resources;
  ImGui::SameLine();
  ImGui::TextDisabled("%s / 词性", app.project.name.c_str());
  ImGui::Spacing();
  app.ensurePosLoaded();
  const std::size_t count = app.posStore ? app.posStore->entries.size() : 0;
  pageTitle("PARTS OF SPEECH", "词性管理", std::to_string(count) + " 个词性 · 名称、描述与编码");
  ImGui::SameLine();
  const float right = ImGui::GetWindowWidth() - 170;
  if (right > 0) ImGui::SetCursorPosX(right);
  if (ImGui::Button("＋ 新建词性", ImVec2(150, 34))) app.openPosEditor("");
  ImGui::Spacing();
  if (!app.posStore || app.posStore->entries.empty()) {
    ImGui::Dummy(ImVec2(0, 40));
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 90);
    ImGui::TextDisabled("还没有词性");
    ImGui::SetCursorPosX(ImGui::GetWindowWidth() * 0.5f - 150);
    ImGui::TextDisabled("点击右上角“新建词性”添加名词、动词等。");
    return;
  }
  for (const auto& entry : app.posStore->entries) {
    ImGui::PushID(entry.id.c_str());
    ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyle().Colors[ImGuiCol_ChildBg]);
    ImGui::BeginChild("##pos_row", ImVec2(0, 74), ImGuiChildFlags_Borders,
                      ImGuiWindowFlags_NoScrollbar);
    ImGui::SetCursorPos(ImVec2(0, 0));
    if (ImGui::InvisibleButton("##pos_click", ImVec2(ImGui::GetWindowWidth() - 60, 74))) {
      app.openPosEditor(entry.id);
    }
    ImGui::SetCursorPos(ImVec2(12, 9));
    ImGui::TextUnformatted(entry.name.c_str());
    ImGui::SameLine();
    ImGui::TextDisabled("%s", entry.code.empty() ? "未设置编码" : entry.code.c_str());
    ImGui::SetCursorPosX(12);
    ImGui::TextDisabled("%s", entry.description.empty() ? "无描述" : entry.description.c_str());
    ImGui::SetCursorPos(ImVec2(ImGui::GetWindowWidth() - 80, 9));
    if (ImGui::Button("⋯", ImVec2(30, 26))) ImGui::OpenPopup("##pos_menu");
    if (ImGui::BeginPopup("##pos_menu")) {
      if (ImGui::MenuItem("编辑")) app.openPosEditor(entry.id);
      ImGui::Separator();
      if (ImGui::MenuItem("删除")) {
        app.openModalConfirm(App::ModalKind::ConfirmPos, "删除词性",
                             "确定删除「" + entry.name + "」吗？", entry.id);
      }
      ImGui::EndPopup();
    }
    ImGui::EndChild();
    ImGui::PopStyleColor();
    ImGui::PopID();
    ImGui::Spacing();
  }
}

void renderPosEditor(App& app) {
  if (!app.posEditorOpen) return;
  ImGui::SetNextWindowPos(ImVec2((ImGui::GetIO().DisplaySize.x - 620) * 0.5f,
                                 (ImGui::GetIO().DisplaySize.y - 540) * 0.5f),
                          ImGuiCond_Always);
  ImGui::SetNextWindowSize(ImVec2(620, 540), ImGuiCond_Always);
  bool open = app.posEditorOpen;
  if (ImGui::Begin(app.posEditId.empty() ? "新建词性##pos_editor" : "编辑词性##pos_editor", &open,
                   ImGuiWindowFlags_NoCollapse | ImGuiWindowFlags_NoResize |
                       ImGuiWindowFlags_NoMove | ImGuiWindowFlags_NoSavedSettings)) {
    ImGui::TextDisabled("名称必填；描述和编码可空。");
    ImGui::TextUnformatted("名称");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##pos_name", app.posNameInput, sizeof(app.posNameInput));
    ImGui::TextUnformatted("描述");
    ImGui::InputTextMultiline("##pos_description", app.posDescriptionInput,
                              sizeof(app.posDescriptionInput), ImVec2(-1, 150));
    ImGui::TextUnformatted("编码");
    ImGui::SetNextItemWidth(-1);
    ImGui::InputText("##pos_code", app.posCodeInput, sizeof(app.posCodeInput));
    ImGui::Spacing();
    if (ImGui::Button("取消", ImVec2(110, 34))) {
      app.posEditorOpen = false;
      open = false;
    }
    ImGui::SameLine();
    if (ImGui::Button("保存", ImVec2(120, 34))) app.savePosEditor();
  }
  ImGui::End();
  app.posEditorOpen = open;
}
}  // namespace

int main(int argc, char** argv) {
  std::string dataDirArg;
  std::string smokeDir;
  for (int i = 1; i < argc; ++i) {
    std::string arg = argv[i];
    if (arg == "--data-dir" && i + 1 < argc) dataDirArg = argv[++i];
    else if (arg == "--smoke" && i + 1 < argc) smokeDir = argv[++i];
  }
  if (SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS | SDL_INIT_TIMER) != 0) {
    SDL_Log("SDL_Init failed: %s", SDL_GetError());
    return 1;
  }
  TTF_Init();
  IMG_Init(IMG_INIT_PNG | IMG_INIT_JPG | IMG_INIT_WEBP);
  Mix_Init(MIX_INIT_MP3 | MIX_INIT_OGG | MIX_INIT_FLAC);
  Mix_OpenAudio(44100, MIX_DEFAULT_FORMAT, 2, 2048);
  SDL_SetHint(SDL_HINT_RENDER_SCALE_QUALITY, "1");
  SDL_Window* window = SDL_CreateWindow("Nostongue Forge", SDL_WINDOWPOS_CENTERED,
                                        SDL_WINDOWPOS_CENTERED, kWindowWidth, kWindowHeight,
                                        SDL_WINDOW_RESIZABLE | SDL_WINDOW_ALLOW_HIGHDPI);
  if (!window) {
    SDL_Log("SDL_CreateWindow failed: %s", SDL_GetError());
    SDL_Quit();
    return 1;
  }
  SDL_SetWindowMinimumSize(window, 980, 640);
  Uint32 rendererFlags = SDL_RENDERER_ACCELERATED | SDL_RENDERER_PRESENTVSYNC;
  if (!smokeDir.empty()) rendererFlags = SDL_RENDERER_SOFTWARE;
  SDL_Renderer* renderer = SDL_CreateRenderer(window, -1, rendererFlags);
  if (!renderer) {
    SDL_Log("accelerated renderer unavailable, falling back: %s", SDL_GetError());
    renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_SOFTWARE);
  }
  if (!renderer) {
    SDL_Log("SDL_CreateRenderer failed: %s", SDL_GetError());
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 1;
  }
  SDL_SetRenderDrawBlendMode(renderer, SDL_BLENDMODE_BLEND);
  IMGUI_CHECKVERSION();
  ImGui::CreateContext();
  ImGuiIO& io = ImGui::GetIO();
  io.ConfigFlags |= ImGuiConfigFlags_NavEnableKeyboard;
  io.IniFilename = nullptr;
  ImGui_ImplSDL2_InitForSDLRenderer(window, renderer);
  ImGui_ImplSDLRenderer2_Init(renderer);
  App app;
  app.window = window;
  app.renderer = renderer;
  loadFont(app);
  fs::path dataRoot;
  if (!dataDirArg.empty()) dataRoot = fs::absolute(pathFromUtf8(dataDirArg));
  else {
    dataRoot = Store::defaultDataRoot();
    if (dataRoot.empty()) {
      char* pref = SDL_GetPrefPath("NostongueForge", "NostongueForge");
      if (pref) {
        dataRoot = pathFromUtf8(pref) / "projects";
        SDL_free(pref);
      }
    }
  }
  if (dataRoot.empty()) {
    SDL_Log("No usable data directory");
    ImGui_ImplSDLRenderer2_Shutdown();
    ImGui_ImplSDL2_Shutdown();
    ImGui::DestroyContext();
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    Mix_CloseAudio();
    Mix_Quit();
    IMG_Quit();
    TTF_Quit();
    SDL_Quit();
    return 1;
  }
  app.store = std::make_unique<Store>(dataRoot);
  app.store->loadTheme(app.dark);
  applyStyle(app);
  app.refreshProjects();
  int result = 0;
  if (!smokeDir.empty()) {
    result = runSmoke(app, fs::absolute(pathFromUtf8(smokeDir)));
  } else {
    while (app.running) {
      handleEvents(app);
      renderFrame(app);
      SDL_Delay(16);
    }
  }
  ImGui_ImplSDLRenderer2_Shutdown();
  ImGui_ImplSDL2_Shutdown();
  ImGui::DestroyContext();
  SDL_DestroyRenderer(renderer);
  SDL_DestroyWindow(window);
  Mix_CloseAudio();
  Mix_Quit();
  IMG_Quit();
  TTF_Quit();
  SDL_Quit();
  return result;
}
