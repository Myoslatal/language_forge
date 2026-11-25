package io.github.myoslatal.languageforge;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versioned per-course best learning accuracy, stored locally next to structure.json. */
final class LearningStats {
    static final String FILE_NAME = "learning-stats.json";
    private static final int VERSION = 1;

    static final class Entry {
        int bestCorrect;
        int bestTotal;
        int bestAccuracy;
        long updatedAt;
    }

    private final Map<String, Entry> courses = new LinkedHashMap<>();

    static LearningStats load(File projectDirectory) {
        LearningStats stats = new LearningStats();
        if (projectDirectory == null) return stats;
        File file = new File(projectDirectory, FILE_NAME);
        if (!file.isFile()) return stats;
        try {
            org.json.JSONObject root = new org.json.JSONObject(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            if (root.optInt("version", 0) != VERSION) return stats;
            org.json.JSONObject courses = root.optJSONObject("courses");
            if (courses == null) return stats;
            java.util.Iterator<String> keys = courses.keys();
            while (keys.hasNext()) {
                String id = keys.next();
                org.json.JSONObject value = courses.optJSONObject(id);
                if (id == null || id.isEmpty() || value == null) continue;
                int total = Math.max(0, value.optInt("bestTotal", 0));
                int correct = Math.max(0, value.optInt("bestCorrect", 0));
                if (total <= 0 || correct > total) continue;
                Entry entry = new Entry();
                entry.bestCorrect = correct;
                entry.bestTotal = total;
                entry.bestAccuracy = value.optInt("bestAccuracy",
                        Math.round(correct * 100f / total));
                entry.updatedAt = Math.max(0, value.optLong("updatedAt", 0));
                stats.courses.put(id, entry);
            }
        } catch (Exception ignored) {
            stats.courses.clear();
        }
        return stats;
    }

    Entry best(String courseId) {
        return courseId == null ? null : courses.get(courseId);
    }

    /** Records only a strictly better completed run; returns true when updated. */
    boolean record(String courseId, int correct, int total) {
        if (courseId == null || courseId.isEmpty() || total <= 0) return false;
        correct = Math.max(0, Math.min(correct, total));
        int accuracy = Math.round(correct * 100f / total);
        Entry old = courses.get(courseId);
        if (old != null && accuracy <= old.bestAccuracy) return false;
        Entry entry = new Entry();
        entry.bestCorrect = correct;
        entry.bestTotal = total;
        entry.bestAccuracy = accuracy;
        entry.updatedAt = System.currentTimeMillis();
        courses.put(courseId, entry);
        return true;
    }

    boolean save(File projectDirectory) {
        if (projectDirectory == null) return false;
        try {
            org.json.JSONObject root = new org.json.JSONObject();
            root.put("version", VERSION);
            org.json.JSONObject jsonCourses = new org.json.JSONObject();
            for (Map.Entry<String, Entry> item : courses.entrySet()) {
                org.json.JSONObject value = new org.json.JSONObject();
                value.put("bestCorrect", item.getValue().bestCorrect);
                value.put("bestTotal", item.getValue().bestTotal);
                value.put("bestAccuracy", item.getValue().bestAccuracy);
                value.put("updatedAt", item.getValue().updatedAt);
                jsonCourses.put(item.getKey(), value);
            }
            root.put("courses", jsonCourses);
            File target = new File(projectDirectory, FILE_NAME);
            File temp = new File(projectDirectory, FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            return temp.renameTo(target) || target.delete() && temp.renameTo(target);
        } catch (Exception ignored) {
            return false;
        }
    }
}
