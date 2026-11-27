package io.github.myoslatal.languageforge;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Per-course overrides for the vocabulary hint body; never touches vocabulary.json. */
final class CourseVocabHints {
    static final String FILE_NAME = CourseResourcePaths.VOCAB_HINTS_MANIFEST;
    static final int VERSION = 1;

    final File projectDirectory;
    final String courseId;
    final LinkedHashMap<String, String> hints = new LinkedHashMap<>();

    CourseVocabHints(File projectDirectory, String courseId) {
        this.projectDirectory = projectDirectory;
        this.courseId = courseId == null ? "" : courseId.trim();
        load();
    }

    static CourseVocabHints load(File projectDirectory, String courseId) {
        return new CourseVocabHints(projectDirectory, courseId);
    }

    File file() {
        if (projectDirectory == null || courseId.isEmpty()) return null;
        return CourseResourcePaths.vocabHintsManifest(projectDirectory, courseId);
    }

    void load() {
        hints.clear();
        File source = file();
        if (source == null || !source.isFile()) return;
        try {
            String content = new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            JSONObject values = root.optJSONObject("hints");
            if (values == null) return;
            for (java.util.Iterator<String> it = values.keys(); it.hasNext(); ) {
                String id = it.next();
                String text = values.optString(id, "");
                if (!id.isEmpty() && !text.trim().isEmpty()) hints.put(id, text);
            }
        } catch (IOException | JSONException ignored) {
            hints.clear();
        }
    }

    String get(String vocabId) {
        return vocabId == null ? null : hints.get(vocabId);
    }

    int size() {
        return hints.size();
    }

    void put(String vocabId, String text) {
        if (vocabId == null || vocabId.isEmpty()) return;
        String clean = text == null ? "" : text.trim();
        if (clean.isEmpty()) hints.remove(vocabId);
        else hints.put(vocabId, clean);
    }

    void remove(String vocabId) {
        if (vocabId != null) hints.remove(vocabId);
    }

    boolean save() {
        File target = file();
        if (target == null || projectDirectory == null || !projectDirectory.isDirectory()) return false;
        try {
            if (hints.isEmpty()) {
                Files.deleteIfExists(target.toPath());
                return true;
            }
            File parent = target.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            JSONObject values = new JSONObject();
            for (Map.Entry<String, String> entry : hints.entrySet()) {
                values.put(entry.getKey(), entry.getValue());
            }
            JSONObject root = new JSONObject();
            root.put("version", VERSION);
            root.put("hints", values);
            File temp = new File(target.getPath() + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | JSONException e) {
            new File(target.getPath() + ".tmp").delete();
            return false;
        }
    }
}
