package io.github.myoslatal.languageforge;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Migrates legacy project-global question audio into course-scoped storage.
 *
 * <p>Global vocabulary audio/images stay in resources/audio and resources/images.
 * Question audio moves to resources/courses/&lt;courseId&gt;/audio and is listed in
 * that course's audios.json. The migration is reference-driven, copies rather than
 * moves until the rewritten structure is safely stored, is idempotent, and leaves
 * missing files untouched for later repair.</p>
 */
final class ResourceScopeMigration {

    static final String TAG = "ResourceScopeMigration";
    static final String GLOBAL_AUDIO_DIR = "resources/audio";
    static final String GLOBAL_IMAGE_DIR = "resources/images";
    static final String GLOBAL_AUDIO_MANIFEST = "audios.json";
    static final String GLOBAL_IMAGE_MANIFEST = "images.json";
    static final String STRUCTURE_FILE = "structure.json";
    static final String VOCABULARY_FILE = "vocabulary.json";
    static final int SCHEMA = 1;

    private ResourceScopeMigration() {
    }

    static final class Result {
        int movedQuestions;
        int copiedFiles;
        int removedGlobalEntries;
        int missingFiles;
        boolean changed;
    }

    static Result migrate(File projectDirectory) {
        Result result = new Result();
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            return result;
        }
        JSONObject structure = readObject(new File(projectDirectory, STRUCTURE_FILE));
        if (structure == null) {
            return result;
        }
        JSONObject vocabulary = readObject(new File(projectDirectory, VOCABULARY_FILE));
        JSONObject globalAudios = readObject(
                new File(projectDirectory, GLOBAL_AUDIO_MANIFEST));
        JSONObject globalImages = readObject(
                new File(projectDirectory, GLOBAL_IMAGE_MANIFEST));
        if (globalAudios == null) {
            globalAudios = new JSONObject();
        }
        if (globalImages == null) {
            globalImages = new JSONObject();
        }
        JSONArray globalAudioArray = array(globalAudios, "audios");
        JSONArray globalImageArray = array(globalImages, "images");
        Map<String, JSONObject> globalAudioByFile = index(globalAudioArray, "file");
        Map<String, JSONObject> globalImageByFile = index(globalImageArray, "file");

        Set<String> globalAudioRefs = new LinkedHashSet<>();
        Set<String> globalImageRefs = new LinkedHashSet<>();
        boolean vocabularyChanged = false;
        try {
            vocabularyChanged = migrateVocabulary(projectDirectory, vocabulary,
                    globalAudioArray, globalAudioByFile, globalImageArray, globalImageByFile,
                    globalAudioRefs, globalImageRefs);
        } catch (org.json.JSONException e) {
            Log.w(TAG, "could not migrate vocabulary resources", e);
            return result;
        }
        result.changed = vocabularyChanged;

        List<CourseData> courses = new ArrayList<>();
        Set<String> questionSources = new LinkedHashSet<>();
        Set<String> targetPaths = new LinkedHashSet<>();
        collectCourses(structure, courses);
        boolean structureChanged = false;
        for (CourseData course : courses) {
            if (course.questions.length() == 0) {
                continue;
            }
            File manifestFile = CourseResourcePaths.audioManifest(projectDirectory, course.id);
            JSONObject manifest = readObject(manifestFile);
            if (manifest == null) {
                manifest = new JSONObject();
            }
            JSONArray manifestArray = array(manifest, "audios");
            Map<String, JSONObject> manifestByFile = index(manifestArray, "file");
            boolean courseChanged = false;
            Map<String, String> sourceTargets = new HashMap<>();
            for (int qi = 0; qi < course.questions.length(); qi++) {
                JSONObject question = course.questions.optJSONObject(qi);
                if (question == null) {
                    continue;
                }
                String oldPath = clean(question.optString("audio", ""));
                if (oldPath.isEmpty()) {
                    continue;
                }
                questionSources.add(oldPath);
                File source = MediaFiles.resolveProjectFile(projectDirectory, oldPath);
                if (source == null || !source.isFile()) {
                    result.missingFiles++;
                    Log.w(TAG, "missing question audio: " + oldPath);
                    continue;
                }
                String targetPath = sourceTargets.get(oldPath);
                if (targetPath == null) {
                    if (CourseResourcePaths.isCourseAudioPath(projectDirectory, course.id,
                            oldPath)) {
                        targetPath = oldPath;
                    } else {
                        targetPath = copyQuestionAudio(projectDirectory, course.id, oldPath,
                                source, result);
                    }
                    if (targetPath == null) {
                        result.missingFiles++;
                        continue;
                    }
                    sourceTargets.put(oldPath, targetPath);
                }
                targetPaths.add(targetPath);
                if (!oldPath.equals(targetPath)) {
                    try {
                        question.put("audio", targetPath);
                    } catch (org.json.JSONException e) {
                        Log.w(TAG, "could not rewrite question audio", e);
                        continue;
                    }
                    structureChanged = true;
                    result.movedQuestions++;
                }
                JSONObject globalEntry = globalAudioByFile.get(oldPath);
                if (manifestByFile.get(targetPath) == null) {
                    JSONObject entry = makeCourseEntry(course.id, targetPath, globalEntry);
                    manifestArray.put(entry);
                    manifestByFile.put(targetPath, entry);
                    courseChanged = true;
                }
            }
            if (courseChanged || manifestArray.length() > 0) {
                JSONObject root = new JSONObject();
                try {
                    root.put("schema", SCHEMA);
                    root.put("audios", manifestArray);
                } catch (org.json.JSONException e) {
                    Log.w(TAG, "could not build course manifest", e);
                    continue;
                }
                if (!writeObjectAtomic(manifestFile, root)) {
                    Log.w(TAG, "could not write course manifest: " + manifestFile);
                }
            }
        }

        // Remove question-only files from the project-global audio registry once
        // every course has a copy. Never delete a file still referenced by the
        // vocabulary or still used as a quote/answer target.
        JSONArray keptGlobal = new JSONArray();
        Set<String> remainingQuestionPaths = currentQuestionSources(structure);
        Set<String> deleteAfterWrite = new LinkedHashSet<>();
        for (int i = 0; i < globalAudioArray.length(); i++) {
            JSONObject entry = globalAudioArray.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String file = clean(entry.optString("file", ""));
            boolean keep = !file.isEmpty()
                    && (globalAudioRefs.contains(file) || !questionSources.contains(file)
                    || targetPaths.contains(file) || remainingQuestionPaths.contains(file)
                    || !isUnder(projectDirectory, file, GLOBAL_AUDIO_DIR));
            if (keep) {
                keptGlobal.put(entry);
            } else {
                deleteAfterWrite.add(file);
                result.removedGlobalEntries++;
            }
        }
        for (String source : questionSources) {
            if (!globalAudioRefs.contains(source) && !targetPaths.contains(source)
                    && !remainingQuestionPaths.contains(source)
                    && isUnder(projectDirectory, source, GLOBAL_AUDIO_DIR)) {
                deleteAfterWrite.add(source);
            }
        }
        boolean globalAudioChanged = result.removedGlobalEntries > 0;
        if (globalAudioChanged || vocabularyChanged) {
            try {
                JSONObject root = new JSONObject();
                root.put("schema", SCHEMA);
                root.put("audios", globalAudioChanged ? keptGlobal : globalAudioArray);
                if (!writeObjectAtomic(new File(projectDirectory, GLOBAL_AUDIO_MANIFEST), root)) {
                    Log.w(TAG, "could not write global audios.json");
                }
            } catch (org.json.JSONException e) {
                Log.w(TAG, "could not build global audios.json", e);
            }
        }
        if (vocabularyChanged) {
            try {
                JSONObject root = new JSONObject();
                root.put("schema", SCHEMA);
                root.put("images", globalImageArray);
                if (!writeObjectAtomic(new File(projectDirectory, GLOBAL_IMAGE_MANIFEST), root)) {
                    Log.w(TAG, "could not write global images.json");
                }
            } catch (org.json.JSONException e) {
                Log.w(TAG, "could not build global images.json", e);
            }
        }

        if (vocabulary != null && vocabularyChanged) {
            if (!writeObjectAtomic(new File(projectDirectory, VOCABULARY_FILE), vocabulary)) {
                Log.w(TAG, "could not write vocabulary.json");
                return result;
            }
        }
        if (structureChanged) {
            if (!writeObjectAtomic(new File(projectDirectory, STRUCTURE_FILE), structure)) {
                Log.w(TAG, "could not write structure.json");
                return result;
            }
        }
        for (String file : deleteAfterWrite) {
            File target = MediaFiles.resolveProjectFile(projectDirectory, file);
            if (target != null && target.isFile()) {
                if (!target.delete()) {
                    Log.w(TAG, "could not delete migrated global audio: " + file);
                }
            }
        }
        result.changed = result.changed || structureChanged || globalAudioChanged;
        if (result.changed) {
            Log.i(TAG, "migration: moved=" + result.movedQuestions
                    + " copied=" + result.copiedFiles
                    + " removedGlobal=" + result.removedGlobalEntries
                    + " missing=" + result.missingFiles);
        }
        return result;
    }

    private static boolean migrateVocabulary(File projectDirectory, JSONObject vocabulary,
            JSONArray globalAudioArray, Map<String, JSONObject> globalAudioByFile,
            JSONArray globalImageArray, Map<String, JSONObject> globalImageByFile,
            Set<String> globalAudioRefs, Set<String> globalImageRefs)
            throws org.json.JSONException {
        if (vocabulary == null) {
            return false;
        }
        JSONArray entries = vocabulary.optJSONArray("entries");
        if (entries == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < entries.length(); i++) {
            JSONObject entry = entries.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String id = clean(entry.optString("id", ""));
            String audio = clean(entry.optString("audio", ""));
            if (!audio.isEmpty()) {
                String migrated = migrateToGlobal(projectDirectory, audio, id, true);
                if (!migrated.equals(audio)) {
                    entry.put("audio", migrated);
                    changed = true;
                }
                globalAudioRefs.add(migrated);
                if (!globalAudioByFile.containsKey(migrated)) {
                    JSONObject manifestEntry = makeGlobalEntry(projectDirectory, migrated, true,
                            id);
                    if (manifestEntry != null) {
                        globalAudioArray.put(manifestEntry);
                        globalAudioByFile.put(migrated, manifestEntry);
                        changed = true;
                    }
                }
            }
            String image = clean(entry.optString("image", ""));
            if (!image.isEmpty()) {
                String migrated = migrateToGlobal(projectDirectory, image, id, false);
                if (!migrated.equals(image)) {
                    entry.put("image", migrated);
                    changed = true;
                }
                globalImageRefs.add(migrated);
                if (!globalImageByFile.containsKey(migrated)) {
                    JSONObject manifestEntry = makeGlobalEntry(projectDirectory, migrated, false,
                            id);
                    if (manifestEntry != null) {
                        globalImageArray.put(manifestEntry);
                        globalImageByFile.put(migrated, manifestEntry);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    private static String migrateToGlobal(File projectDirectory, String relative, String entryId,
                                          boolean audio) {
        File source = MediaFiles.resolveProjectFile(projectDirectory, relative);
        if (source == null || !source.isFile()) {
            return relative;
        }
        String globalDir = audio ? GLOBAL_AUDIO_DIR : GLOBAL_IMAGE_DIR;
        if (isUnder(projectDirectory, relative, globalDir)) {
            return relative;
        }
        File directory = new File(projectDirectory, globalDir);
        if (!directory.exists() && !directory.mkdirs()) {
            return relative;
        }
        String extension = extensionOf(source.getName(), audio ? ".m4a" : ".png");
        String fileName = (audio ? "vocab_" : "vocab_")
                + CourseResourcePaths.safeSegment(entryId) + "_" + tag(relative) + extension;
        File target = new File(directory, fileName);
        if (!target.isFile()) {
            try {
                Files.copy(source.toPath(), target.toPath(),
                        StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                // COPY_ATTRIBUTES is not supported by every provider; retry plainly.
                try {
                    Files.copy(source.toPath(), target.toPath());
                } catch (IOException ignored) {
                    Log.w(TAG, "could not migrate global asset: " + relative, ignored);
                    return relative;
                }
            }
        }
        String result = MediaFiles.relativePath(projectDirectory, target);
        return result == null ? relative : result;
    }

    private static JSONObject makeGlobalEntry(File projectDirectory, String relative,
                                              boolean audio, String entryId) {
        File file = MediaFiles.resolveProjectFile(projectDirectory, relative);
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            JSONObject entry = new JSONObject();
            entry.put("id", (audio ? "ga_" : "gi_") + tag(relative));
            entry.put("name", file.getName());
            entry.put("file", relative);
            entry.put("code", "");
            if (audio) {
                entry.put("duration", Math.max(0L, MediaFiles.audioDuration(file)));
            } else {
                int[] size = MediaFiles.imageSize(file);
                entry.put("width", size == null ? 0 : size[0]);
                entry.put("height", size == null ? 0 : size[1]);
            }
            entry.put("createdAt", System.currentTimeMillis());
            return entry;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private static String copyQuestionAudio(File projectDirectory, String courseId,
                                             String oldPath, File source, Result result) {
        File directory = CourseResourcePaths.audioDirectory(projectDirectory, courseId);
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        String extension = extensionOf(source.getName(), ".m4a");
        String base = sanitizeName(source.getName());
        String targetName = "q_" + tag(courseId + "|" + oldPath) + "_" + base;
        File target = new File(directory, targetName);
        if (!target.isFile()) {
            try {
                Files.copy(source.toPath(), target.toPath(),
                        StandardCopyOption.COPY_ATTRIBUTES);
            } catch (IOException e) {
                try {
                    Files.copy(source.toPath(), target.toPath());
                } catch (IOException ignored) {
                    Log.w(TAG, "could not copy question audio: " + oldPath, ignored);
                    return null;
                }
            }
            result.copiedFiles++;
        }
        String relative = MediaFiles.relativePath(projectDirectory, target);
        return relative == null ? null : relative;
    }

    private static JSONObject makeCourseEntry(String courseId, String targetPath,
                                               JSONObject globalEntry) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("id", "qa_" + tag(courseId + "|" + targetPath));
            entry.put("name", globalEntry == null ? new File(targetPath).getName()
                    : globalEntry.optString("name", new File(targetPath).getName()));
            entry.put("file", targetPath);
            entry.put("code", globalEntry == null ? "" : globalEntry.optString("code", ""));
            entry.put("duration", globalEntry == null ? 0L
                    : globalEntry.optLong("duration", 0L));
            entry.put("createdAt", globalEntry == null ? System.currentTimeMillis()
                    : globalEntry.optLong("createdAt", System.currentTimeMillis()));
            return entry;
        } catch (org.json.JSONException e) {
            return null;
        }
    }

    private static void collectCourses(JSONObject structure, List<CourseData> courses) {
        JSONArray chapters = structure.optJSONArray("chapters");
        if (chapters == null) {
            return;
        }
        for (int ci = 0; ci < chapters.length(); ci++) {
            JSONObject chapter = chapters.optJSONObject(ci);
            if (chapter == null) continue;
            JSONArray units = chapter.optJSONArray("units");
            if (units == null) continue;
            for (int ui = 0; ui < units.length(); ui++) {
                JSONObject unit = units.optJSONObject(ui);
                if (unit == null) continue;
                JSONArray courseArray = unit.optJSONArray("courses");
                if (courseArray == null) continue;
                for (int co = 0; co < courseArray.length(); co++) {
                    JSONObject course = courseArray.optJSONObject(co);
                    if (course == null) continue;
                    JSONArray questions = course.optJSONArray("questions");
                    courses.add(new CourseData(clean(course.optString("id", "")),
                            questions == null ? new JSONArray() : questions));
                }
            }
        }
    }

    private static Set<String> currentQuestionSources(JSONObject structure) {
        Set<String> paths = new LinkedHashSet<>();
        List<CourseData> courses = new ArrayList<>();
        collectCourses(structure, courses);
        for (CourseData course : courses) {
            for (int i = 0; i < course.questions.length(); i++) {
                JSONObject question = course.questions.optJSONObject(i);
                if (question == null) continue;
                String path = clean(question.optString("audio", ""));
                if (!path.isEmpty()) paths.add(path);
            }
        }
        return paths;
    }

    private static JSONObject readObject(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.w(TAG, "could not read " + file, e);
            return null;
        }
    }

    private static JSONArray array(JSONObject object, String key) {
        JSONArray value = object == null ? null : object.optJSONArray(key);
        return value == null ? new JSONArray() : value;
    }

    private static Map<String, JSONObject> index(JSONArray array, String key) {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object == null) continue;
            String value = clean(object.optString(key, ""));
            if (!value.isEmpty()) result.put(value, object);
        }
        return result;
    }

    private static boolean writeObjectAtomic(File file, JSONObject object) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return false;
        }
        File temp = new File(file.getPath() + ".tmp-" + UUID.randomUUID().toString());
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(object.toString().getBytes(StandardCharsets.UTF_8));
            out.getFD().sync();
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
        try {
            Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ignored) {
                //noinspection ResultOfMethodCallIgnored
                temp.delete();
                return false;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    private static boolean isUnder(File projectDirectory, String relative, String directory) {
        String normalized = relative == null ? "" : relative.replace('\\', '/');
        return normalized.equals(directory) || normalized.startsWith(directory + "/");
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String extensionOf(String name, String fallback) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return fallback;
        String extension = name.substring(dot).toLowerCase(Locale.ROOT);
        return extension.matches("\\.[a-z0-9]{1,8}") ? extension : fallback;
    }

    private static String sanitizeName(String name) {
        String value = name == null ? "audio" : name;
        value = value.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (value.isEmpty()) value = "audio";
        if (value.length() > 100) value = value.substring(0, 100);
        return value;
    }

    private static String tag(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] hash = digest.digest((value == null ? "" : value)
                    .getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format(Locale.ROOT, "%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception e) {
            return Integer.toHexString(value == null ? 0 : value.hashCode());
        }
    }

    private static final class CourseData {
        final String id;
        final JSONArray questions;

        CourseData(String id, JSONArray questions) {
            this.id = id;
            this.questions = questions;
        }
    }
}
