package io.github.myoslatal.languageforge;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Disk-backed project metadata store under the app-specific external files directory.
 * No storage permissions are needed on API 29+.
 */
final class ProjectStore {

    private static final String TAG = "ProjectStore";
    private static final String ROOT_NAME = "projects";
    private static final String JSON_NAME = "project.json";
    private static final String SEEDED_MARKER = ".seeded";
    private static final int SCHEMA = 1;

    private final File root;

    ProjectStore(Context context) {
        File base = context.getApplicationContext().getExternalFilesDir(null);
        root = base == null ? null : new File(base, ROOT_NAME);
    }

    boolean isAvailable() {
        return root != null;
    }

    String getRootPath() {
        return root == null ? "(外部存储不可用)" : root.getAbsolutePath();
    }

    File getRoot() {
        return root;
    }

    List<Project> loadProjects() {
        List<Project> projects = new ArrayList<>();
        if (!initialize()) {
            return projects;
        }
        File[] children = root.listFiles();
        if (children == null) {
            return projects;
        }
        for (File directory : children) {
            if (!directory.isDirectory()) {
                continue;
            }
            File json = new File(directory, JSON_NAME);
            if (!json.isFile()) {
                continue;
            }
            try {
                Project project = readProject(directory, json);
                if (project != null) {
                    projects.add(project);
                }
            } catch (Exception e) {
                Log.w(TAG, "Skipping bad project: " + directory.getName(), e);
            }
        }
        sortProjects(projects);
        return projects;
    }

    Project createProject(String name, String courseLine) {
        if (!initialize()) {
            return null;
        }
        return createProjectInternal(name, courseLine, 0, 0, 0, "刚刚",
                System.currentTimeMillis(), false);
    }

    boolean setPinned(Project project, boolean pinned) {
        if (project == null || !isProjectDirectory(project.directory)) {
            return false;
        }
        boolean old = project.pinned;
        project.pinned = pinned;
        if (!writeProject(project)) {
            project.pinned = old;
            return false;
        }
        return true;
    }

    boolean deleteProject(Project project) {
        if (project == null || !isProjectDirectory(project.directory)) {
            return false;
        }
        return deleteRecursively(project.directory);
    }

    static void sortProjects(List<Project> projects) {
        Collections.sort(projects, (a, b) -> {
            if (a.pinned != b.pinned) {
                return a.pinned ? -1 : 1;
            }
            int byCreated = Long.compare(b.createdAt, a.createdAt);
            if (byCreated != 0) {
                return byCreated;
            }
            return a.name.compareToIgnoreCase(b.name);
        });
    }

    private boolean initialize() {
        if (root == null) {
            return false;
        }
        if (!root.exists() && !root.mkdirs()) {
            return false;
        }
        if (!root.isDirectory()) {
            return false;
        }
        File marker = new File(root, SEEDED_MARKER);
        if (marker.exists()) {
            return true;
        }
        if (hasProjectFolders()) {
            markSeeded(marker);
            return true;
        }
        boolean seeded = seedSamples();
        if (seeded || hasProjectFolders()) {
            markSeeded(marker);
        }
        return true;
    }

    private boolean seedSamples() {
        long now = System.currentTimeMillis();
        Sample[] samples = {
                new Sample("Nortu 自由语 · 入门", "Nortu 自由语 · art-nortu",
                        10, 86, 5314, "2 小时前", true),
                new Sample("Nortu 自由语 · 日常会话", "Nortu 自由语 · art-nortu",
                        8, 64, 0, "2 天前", true),
                new Sample("Lektio 古典语 · 语法篇", "Lektio 古典语 · lek-classic",
                        12, 90, 0, "3 天前", false),
                new Sample("Nortu 词汇实验", "Nortu 自由语 · art-nortu",
                        4, 28, 0, "上周", false),
                new Sample("未命名项目", "—", 0, 0, 0, "8 月 30 日", false)
        };
        boolean all = true;
        for (int i = 0; i < samples.length; i++) {
            Sample sample = samples[i];
            Project project = createProjectInternal(sample.name, sample.courseLine,
                    sample.lessons, sample.questions, sample.words, sample.updated,
                    now - i * 60_000L, sample.pinned);
            if (project == null) {
                all = false;
            }
        }
        return all;
    }

    private Project createProjectInternal(String name, String courseLine, int lessons,
                                          int questions, int words, String updated,
                                          long createdAt, boolean pinned) {
        File directory = createUniqueDirectory(name);
        if (directory == null) {
            return null;
        }
        Project project = new Project(directory, directory.getName(), name, courseLine,
                lessons, questions, words, updated, createdAt, pinned);
        if (!writeProject(project)) {
            deleteRecursively(directory);
            return null;
        }
        return project;
    }

    File createUniqueDirectory(String name) {
        String base = sanitize(name);
        for (int i = 0; i < 10; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
            File directory = new File(root, base + "-" + suffix);
            if (!directory.exists() && directory.mkdirs()) {
                return directory;
            }
        }
        return null;
    }

    private String sanitize(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|]", "-");
        value = value.replaceAll("\\s+", " ");
        if (value.length() > 48) {
            value = value.substring(0, 48).trim();
        }
        return value.isEmpty() ? "project" : value;
    }

    private Project readProject(File directory, File json) throws IOException, JSONException {
        String content = new String(Files.readAllBytes(json.toPath()), StandardCharsets.UTF_8);
        JSONObject object = new JSONObject(content);
        if (object.optInt("schema", -1) != SCHEMA) {
            Log.w(TAG, "Skipping unsupported schema: " + json);
            return null;
        }
        String name = object.optString("name", "").trim();
        if (name.isEmpty()) {
            Log.w(TAG, "Skipping project without name: " + json);
            return null;
        }
        String courseLine = object.optString("courseLine", "—");
        if (courseLine.trim().isEmpty()) {
            courseLine = "—";
        }
        long createdAt = object.optLong("createdAt", directory.lastModified());
        return new Project(directory, directory.getName(), name, courseLine,
                Math.max(0, object.optInt("lessons", 0)),
                Math.max(0, object.optInt("questions", 0)),
                Math.max(0, object.optInt("words", 0)),
                object.optString("updated", ""),
                createdAt,
                object.optBoolean("pinned", false));
    }

    private boolean writeProject(Project project) {
        JSONObject object = new JSONObject();
        try {
            object.put("schema", SCHEMA);
            object.put("name", project.name);
            object.put("courseLine", project.courseLine);
            object.put("lessons", project.lessons);
            object.put("questions", project.questions);
            object.put("words", project.words);
            object.put("updated", project.updated);
            object.put("createdAt", project.createdAt);
            object.put("pinned", project.pinned);
        } catch (JSONException e) {
            Log.w(TAG, "Could not build project JSON", e);
            return false;
        }
        File json = new File(project.directory, JSON_NAME);
        try (FileOutputStream out = new FileOutputStream(json)) {
            out.write(object.toString().getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException e) {
            Log.w(TAG, "Could not write " + json, e);
            return false;
        }
    }

    private boolean hasProjectFolders() {
        File[] children = root.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.isDirectory() && new File(child, JSON_NAME).isFile()) {
                return true;
            }
        }
        return false;
    }

    private void markSeeded(File marker) {
        try (FileOutputStream ignored = new FileOutputStream(marker)) {
            // Marker content is intentionally empty.
        } catch (IOException e) {
            Log.w(TAG, "Could not write " + marker, e);
        }
    }

    private boolean isProjectDirectory(File directory) {
        if (root == null || directory == null || !directory.isDirectory()) {
            return false;
        }
        try {
            String rootCanonical = root.getCanonicalPath();
            String directoryCanonical = directory.getCanonicalPath();
            File parent = new File(directoryCanonical).getParentFile();
            return parent != null
                    && parent.getCanonicalPath().equals(rootCanonical)
                    && directoryCanonical.startsWith(rootCanonical + File.separator);
        } catch (IOException e) {
            Log.w(TAG, "Could not canonicalize project directory", e);
            return false;
        }
    }

    private boolean deleteRecursively(File file) {
        if (root == null) {
            return false;
        }
        try {
            String rootCanonical = root.getCanonicalPath();
            return deleteRecursively(file, rootCanonical);
        } catch (IOException e) {
            Log.w(TAG, "Could not canonicalize projects root", e);
            return false;
        }
    }

    private boolean deleteRecursively(File file, String rootCanonical) {
        try {
            String canonical = file.getCanonicalPath();
            if (!canonical.equals(rootCanonical)
                    && !canonical.startsWith(rootCanonical + File.separator)) {
                Log.w(TAG, "Refusing to delete outside projects root: " + canonical);
                return false;
            }
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        if (!deleteRecursively(child, rootCanonical)) {
                            return false;
                        }
                    }
                }
            }
            return file.delete();
        } catch (IOException e) {
            Log.w(TAG, "Could not delete " + file, e);
            return false;
        }
    }

    private static final class Sample {
        final String name;
        final String courseLine;
        final int lessons;
        final int questions;
        final int words;
        final String updated;
        final boolean pinned;

        Sample(String name, String courseLine, int lessons, int questions, int words,
               String updated, boolean pinned) {
            this.name = name;
            this.courseLine = courseLine;
            this.lessons = lessons;
            this.questions = questions;
            this.words = words;
            this.updated = updated;
            this.pinned = pinned;
        }
    }
}