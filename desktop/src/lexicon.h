#pragma once

#include <filesystem>
#include <string>

enum class LexiconImportMode { Skip, Overwrite };

struct LexiconImportSummary {
  int added = 0;
  int updated = 0;
  int duplicates = 0;
  int invalid = 0;
  int newPos = 0;
};

/** Import Lexicon JSONL into pos.json + vocabulary.json; returns false with error. */
bool importLexiconJsonl(const std::filesystem::path& file,
                        const std::filesystem::path& projectDirectory,
                        LexiconImportMode mode,
                        LexiconImportSummary& summary, std::string& error);
