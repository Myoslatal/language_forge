#pragma once

#include <filesystem>
#include <string>

namespace forge {

namespace fs = std::filesystem;

/** True if a project-relative path belongs to the course question-audio scope. */
bool isCourseAudioPath(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& relative);

/** Project-relative directory for a course's question images. */
std::string courseImageDir(const fs::path& projectDirectory, const std::string& courseId);

/** Project-relative file holding per-course vocabulary hint-body overrides. */
std::string courseVocabHintsFile(const fs::path& projectDirectory, const std::string& courseId);

/** True if a project-relative path belongs to the course question-image scope. */
bool isCourseImagePath(const fs::path& projectDirectory, const std::string& courseId,
                       const std::string& relative);

/** Copy an option image into its course image directory. */
bool copyImageToCourse(const fs::path& projectDirectory, const std::string& courseId,
                       const fs::path& source, const std::string& optionId,
                       fs::path& copied, std::string& error);

/** Project-relative directory for a course's question audio. */
std::string courseAudioDir(const fs::path& projectDirectory, const std::string& courseId);

/**
 * Migrate legacy project-global question audio into resources/courses/<id>/audio.
 * Vocabulary audio/images remain global. Idempotent; missing files are left in
 * the structure for later repair. Returns false only when a rewrite fails.
 */
bool migrateResourceScopes(const fs::path& projectDirectory, std::string& error);

/** Delete a course's scoped resources after its structure entry is removed. */
void deleteCourseResources(const fs::path& projectDirectory, const std::string& courseId);

/** Copy a question recording into its course audio directory. */
bool copyAudioToCourse(const fs::path& projectDirectory, const std::string& courseId,
                       const fs::path& source, const std::string& questionId,
                       fs::path& copied, std::string& error);

/** Stable file name component used by migration (exposed for tests). */
std::string resourceScopeTag(const std::string& value);

}  // namespace forge
