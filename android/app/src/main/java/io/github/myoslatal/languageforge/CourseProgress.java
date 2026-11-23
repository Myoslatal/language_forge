package io.github.myoslatal.languageforge;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Course-level progress stored next to structure.json. */
final class CourseProgress {
    static final String FILE_NAME = "progress.json";
    final Set<String> unlocked = new LinkedHashSet<>();
    final Set<String> completed = new LinkedHashSet<>();

    static CourseProgress load(File projectDirectory) {
        CourseProgress progress = new CourseProgress();
        File file = new File(projectDirectory, FILE_NAME);
        if (!file.isFile()) return progress;
        try {
            org.json.JSONObject json = new org.json.JSONObject(
                    new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
            org.json.JSONArray unlocked = json.optJSONArray("unlockedCourseIds");
            if (unlocked != null) for (int i = 0; i < unlocked.length(); i++) {
                String id = unlocked.optString(i, "");
                if (!id.isEmpty()) progress.unlocked.add(id);
            }
            org.json.JSONArray completed = json.optJSONArray("completedCourseIds");
            if (completed != null) for (int i = 0; i < completed.length(); i++) {
                String id = completed.optString(i, "");
                if (!id.isEmpty()) progress.completed.add(id);
            }
        } catch (Exception ignored) {
            progress.unlocked.clear();
            progress.completed.clear();
        }
        return progress;
    }

    void normalize(List<ProjectStructure.Course> courses) {
        if (!courses.isEmpty()) unlocked.add(courses.get(0).id);
        for (int i = 0; i < courses.size(); i++) {
            ProjectStructure.Course course = courses.get(i);
            if (completed.contains(course.id)) unlocked.add(course.id);
            if (i > 0 && completed.contains(courses.get(i - 1).id)) unlocked.add(course.id);
        }
    }

    boolean allCompleted(List<ProjectStructure.Course> courses) {
        if (courses.isEmpty()) return false;
        for (ProjectStructure.Course course : courses) {
            if (!completed.contains(course.id)) return false;
        }
        return true;
    }

    boolean isUnlocked(ProjectStructure.Course course) { return unlocked.contains(course.id); }
    boolean isCompleted(ProjectStructure.Course course) { return completed.contains(course.id); }

    ProjectStructure.Course current(List<ProjectStructure.Course> courses) {
        for (ProjectStructure.Course course : courses) {
            if (unlocked.contains(course.id) && !completed.contains(course.id)) return course;
        }
        return null;
    }

    ProjectStructure.Course next(List<ProjectStructure.Course> courses, ProjectStructure.Course course) {
        int index = courses.indexOf(course);
        return index >= 0 && index + 1 < courses.size() ? courses.get(index + 1) : null;
    }

    void complete(List<ProjectStructure.Course> courses, ProjectStructure.Course course) {
        completed.add(course.id);
        unlocked.add(course.id);
        ProjectStructure.Course next = next(courses, course);
        if (next != null) unlocked.add(next.id);
    }

    boolean save(File projectDirectory) {
        try {
            org.json.JSONObject json = new org.json.JSONObject();
            json.put("version", 1);
            json.put("unlockedCourseIds", new org.json.JSONArray(new ArrayList<>(unlocked)));
            json.put("completedCourseIds", new org.json.JSONArray(new ArrayList<>(completed)));
            File target = new File(projectDirectory, FILE_NAME);
            File temp = new File(projectDirectory, FILE_NAME + ".tmp");
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(json.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            return temp.renameTo(target) || target.delete() && temp.renameTo(target);
        } catch (Exception ignored) {
            return false;
        }
    }
}
