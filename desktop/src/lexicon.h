#pragma once

#include <filesystem>
#include <map>
#include <string>
#include <vector>

enum class LexiconImportMode { Skip, Overwrite, Merge };
enum class LexiconDuplicateChoice { Merge, KeepFirst };

struct LexiconDuplicateGroup {
  std::string key;
  std::string text;
  int rows = 0;
};

struct LexiconImportSummary {
  int added = 0;
  int updated = 0;
  int merged = 0;
  int duplicates = 0;
  bool needsDuplicateChoice = false;
  int duplicateGroups = 0;
  std::vector<std::string> duplicateExamples;
  std::vector<LexiconDuplicateGroup> duplicateGroupList;
  int invalid = 0;
  int newPos = 0;
};

/** Import Lexicon JSONL into pos.json + vocabulary.json; returns false with error. */
bool importLexiconJsonl(const std::filesystem::path& file,
                        const std::filesystem::path& projectDirectory,
                        LexiconImportMode mode,
                        const std::map<std::string, LexiconDuplicateChoice>* duplicateChoices,
                        LexiconImportSummary& summary, std::string& error);
