#pragma once

#include <filesystem>
#include <string>
#include <vector>

enum class LexiconImportMode { Skip, Overwrite, Merge };
enum class LexiconDuplicateChoice { Ask, Merge, KeepFirst };

struct LexiconImportSummary {
  int added = 0;
  int updated = 0;
  int merged = 0;
  int duplicates = 0;
  bool needsDuplicateChoice = false;
  int duplicateGroups = 0;
  std::vector<std::string> duplicateExamples;
  int invalid = 0;
  int newPos = 0;
};

/** Import Lexicon JSONL into pos.json + vocabulary.json; returns false with error. */
bool importLexiconJsonl(const std::filesystem::path& file,
                        const std::filesystem::path& projectDirectory,
                        LexiconImportMode mode,
                        LexiconDuplicateChoice duplicateChoice,
                        LexiconImportSummary& summary, std::string& error);
