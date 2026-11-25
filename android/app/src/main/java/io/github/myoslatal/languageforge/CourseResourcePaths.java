package io.github.myoslatal.languageforge;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Locale;

/** Paths for the project-global resources and course-scoped question resources. */
final class CourseResourcePaths {

    static final String COURSE_ROOT = "resources/courses";
    static final String AUDIO_DIR_NAME = "audio";
    static final String AUDIO_MANIFEST = "audios.json";
    static final String IMAGE_DIR_NAME = "images";
    static final String IMAGE_MANIFEST = "images.json";

    private CourseResourcePaths() {
    }

    static String safeSegment(String value) {
        String raw = value == null ? "" : value.trim();
        String safe = raw.replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safe.isEmpty() || safe.equals(".") || safe.equals("..")) {
            safe = "course";
        }
        if (safe.length() > 80) {
            safe = safe.substring(0, 80);
        }
        return safe;
    }

    static File courseDirectory(File projectDirectory, String courseId) {
        return new File(new File(projectDirectory, COURSE_ROOT), safeSegment(courseId));
    }

    static File audioDirectory(File projectDirectory, String courseId) {
        return new File(courseDirectory(projectDirectory, courseId), AUDIO_DIR_NAME);
    }

    static File audioManifest(File projectDirectory, String courseId) {
        return new File(courseDirectory(projectDirectory, courseId), AUDIO_MANIFEST);
    }

    static File imageDirectory(File projectDirectory, String courseId) {
        return new File(courseDirectory(projectDirectory, courseId), IMAGE_DIR_NAME);
    }

    static File imageManifestDirectory(File projectDirectory, String courseId) {
        return courseDirectory(projectDirectory, courseId);
    }

    static File imageManifest(File projectDirectory, String courseId) {
        return new File(courseDirectory(projectDirectory, courseId), IMAGE_MANIFEST);
    }

    static String relativeCourseImage(File projectDirectory, String courseId, String fileName) {
        File dir = imageDirectory(projectDirectory, courseId);
        return MediaFiles.relativePath(projectDirectory, new File(dir, fileName));
    }

    static boolean isCourseImagePath(File projectDirectory, String courseId, String relative) {
        if (relative == null || relative.isEmpty()) {
            return false;
        }
        String expected = MediaFiles.relativePath(projectDirectory,
                imageDirectory(projectDirectory, courseId));
        String normalized = relative.replace('\\', '/');
        return expected != null && (normalized.equals(expected)
                || normalized.startsWith(expected + "/"));
    }

    static String relativeCourseAudio(File projectDirectory, String courseId, String fileName) {
        File dir = audioDirectory(projectDirectory, courseId);
        return MediaFiles.relativePath(projectDirectory, new File(dir, fileName));
    }

    static boolean isCourseAudioPath(File projectDirectory, String courseId, String relative) {
        if (relative == null || relative.isEmpty()) {
            return false;
        }
        String expected = MediaFiles.relativePath(projectDirectory,
                audioDirectory(projectDirectory, courseId));
        String normalized = relative.replace('\\', '/');
        return expected != null && (normalized.equals(expected)
                || normalized.startsWith(expected + "/"));
    }

    static void deleteCourseResources(File projectDirectory, String courseId) {
        if (projectDirectory == null) return;
        File directory = courseDirectory(projectDirectory, courseId);
        try {
            String root = new File(projectDirectory, COURSE_ROOT).getCanonicalPath();
            String target = directory.getCanonicalPath();
            if (!target.startsWith(root + File.separator)) return;
            deleteRecursively(directory);
        } catch (IOException ignored) {
            // Best effort: stale course resources are invisible to other courses.
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    static File copyAudioToCourse(File projectDirectory, String courseId, File source,
                                  String questionId) throws IOException {
        if (projectDirectory == null || source == null || !source.isFile()) {
            throw new IOException("音频不存在");
        }
        File directory = audioDirectory(projectDirectory, courseId);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建课程音频目录");
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : ".m4a";
        if (!extension.matches("\\.[a-z0-9]{1,8}")) {
            extension = ".m4a";
        }
        String safeQuestion = safeSegment(questionId == null ? "question" : questionId);
        File target = new File(directory, "question_" + safeQuestion + "_"
                + System.currentTimeMillis() + extension);
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
        return target;
    }

    static File copyImageToCourse(File projectDirectory, String courseId, File source,
                                  String optionId) throws IOException {
        if (projectDirectory == null || source == null || !source.isFile()) {
            throw new IOException("图片不存在");
        }
        File directory = imageDirectory(projectDirectory, courseId);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("无法创建课程图片目录");
        }
        String name = source.getName();
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot).toLowerCase(Locale.ROOT) : ".png";
        if (!extension.matches("\\.[a-z0-9]{1,8}")) {
            extension = ".png";
        }
        String safeOption = safeSegment(optionId == null ? "option" : optionId);
        File target = new File(directory, "option_" + safeOption + "_"
                + System.currentTimeMillis() + extension);
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
        return target;
    }
}
