package io.github.myoslatal.languageforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Project-level images.json + resources/images. */
class ImageStore {

    static final String FILE_NAME = "images.json";
    static final String TEMP_NAME = "images.json.tmp";
    static final int SCHEMA = 1;
    static final String IMAGE_DIR = "resources/images";

    final File projectDirectory;
    final List<ProjectImage> images = new ArrayList<>();

    ImageStore(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        load();
    }

    static File resolveProjectDirectory(Context context, String folderName) {
        return VocabularyStore.resolveProjectDirectory(context, folderName);
    }

    static String readProjectName(File projectDirectory) {
        return VocabularyStore.readProjectName(projectDirectory);
    }

    void load() {
        images.clear();
        File file = new File(projectDirectory, FILE_NAME);
        if (!file.isFile()) {
            return;
        }
        try {
            String raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(raw);
            if (root.optInt("schema", 0) != SCHEMA) {
                return;
            }
            JSONArray array = root.optJSONArray("images");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    ProjectImage image = new ProjectImage();
                    image.id = object.optString("id", "").trim();
                    image.name = object.optString("name", "").trim();
                    image.file = object.optString("file", "").trim();
                    image.code = object.optString("code", "").trim();
                    image.width = Math.max(0, object.optInt("width", 0));
                    image.height = Math.max(0, object.optInt("height", 0));
                    image.createdAt = object.optLong("createdAt", 0L);
                    if (image.id.isEmpty() || image.file.isEmpty()) {
                        continue;
                    }
                    if (resolveFile(image.file) == null) {
                        continue;
                    }
                    if (image.name.isEmpty()) {
                        File resolved = resolveFile(image.file);
                        image.name = resolved == null ? image.file : resolved.getName();
                    }
                    images.add(image);
                }
            }
        } catch (Exception ignored) {
            images.clear();
        }
    }

    boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            JSONArray array = new JSONArray();
            for (ProjectImage image : images) {
                JSONObject object = new JSONObject();
                object.put("id", image.id);
                object.put("name", image.name);
                object.put("file", image.file);
                object.put("code", image.code);
                object.put("width", image.width);
                object.put("height", image.height);
                object.put("createdAt", image.createdAt);
                array.put(object);
            }
            root.put("images", array);
            File temp = new File(projectDirectory, TEMP_NAME);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
            }
            Files.move(temp.toPath(), new File(projectDirectory, FILE_NAME).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (Exception e) {
            new File(projectDirectory, TEMP_NAME).delete();
            return false;
        }
    }

    ProjectImage findById(String id) {
        if (id == null) {
            return null;
        }
        for (ProjectImage image : images) {
            if (image.id.equals(id)) {
                return image;
            }
        }
        return null;
    }

    ProjectImage findByFile(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        for (ProjectImage image : images) {
            if (image.file.equals(relativePath)) {
                return image;
            }
        }
        return null;
    }

    File resolveFile(String relativePath) {
        return MediaFiles.resolveProjectFile(projectDirectory, relativePath);
    }

    List<File> unregisteredFiles() {
        List<File> result = new ArrayList<>();
        File directory = new File(projectDirectory, IMAGE_DIR);
        File[] files = directory.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (!file.isFile() || !isSupportedExtension(extensionOf(file.getName()))) {
                continue;
            }
            String relative = MediaFiles.relativePath(projectDirectory, file);
            if (relative != null && findByFile(relative) == null) {
                result.add(file);
            }
        }
        return result;
    }

    ProjectImage importImage(File source, String displayName) {
        if (source == null || !source.isFile()) {
            return null;
        }
        String extension = extensionOf(source.getName());
        if (!isSupportedExtension(extension)) {
            return null;
        }
        File directory = new File(projectDirectory, IMAGE_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        String safeId = id.replaceAll("[^a-zA-Z0-9_-]", "");
        File target = new File(directory, "img_" + safeId + "_"
                + System.currentTimeMillis() + extension);
        try {
            copy(source, target);
            int[] size = imageSize(target);
            if (size == null || MediaFiles.decodeFile(target, 256) == null) {
                throw new IOException("invalid image");
            }
            ProjectImage image = new ProjectImage();
            image.id = id;
            image.name = cleanName(displayName, target.getName());
            image.file = MediaFiles.relativePath(projectDirectory, target);
            image.width = size[0];
            image.height = size[1];
            image.createdAt = System.currentTimeMillis();
            if (image.file == null) {
                throw new IOException("path outside project");
            }
            images.add(image);
            if (!save()) {
                images.remove(image);
                throw new IOException("save failed");
            }
            return image;
        } catch (Exception e) {
            MediaFiles.deleteQuietly(target);
            return null;
        }
    }

    ProjectImage registerFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String relative = MediaFiles.relativePath(projectDirectory, file);
        if (relative == null) {
            return null;
        }
        ProjectImage existing = findByFile(relative);
        if (existing != null) {
            return existing;
        }
        int[] size = imageSize(file);
        if (size == null) {
            return null;
        }
        ProjectImage image = new ProjectImage();
        image.id = UUID.randomUUID().toString();
        image.name = file.getName();
        image.file = relative;
        image.width = size[0];
        image.height = size[1];
        image.createdAt = System.currentTimeMillis();
        images.add(image);
        if (!save()) {
            images.remove(image);
            return null;
        }
        return image;
    }

    boolean setCode(ProjectImage image, String code) {
        if (image == null) {
            return false;
        }
        image.code = code == null ? "" : code.trim();
        if (!save()) {
            load();
            return false;
        }
        return true;
    }

    boolean deleteImage(ProjectImage image) {
        if (image == null) {
            return false;
        }
        File file = resolveFile(image.file);
        images.remove(image);
        if (!save()) {
            load();
            return false;
        }
        MediaFiles.deleteQuietly(file);
        return true;
    }

    static boolean isSupportedExtension(String extension) {
        return ".png".equals(extension) || ".jpg".equals(extension)
                || ".jpeg".equals(extension) || ".webp".equals(extension)
                || ".gif".equals(extension);
    }

    static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static int[] imageSize(File file) {
        return MediaFiles.imageSize(file);
    }

    private static String cleanName(String displayName, String fileName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            name = fileName == null ? "图片" : fileName;
        }
        if (name.length() > 120) {
            name = name.substring(0, 120);
        }
        return name;
    }

    private static void copy(File source, File target) throws IOException {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
        }
    }
}
