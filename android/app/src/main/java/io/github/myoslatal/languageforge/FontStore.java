package io.github.myoslatal.languageforge;

import android.content.Context;
import android.graphics.Typeface;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Project-level fonts.json + resources/fonts. */
class FontStore {

    static final String FILE_NAME = "fonts.json";
    static final String TEMP_NAME = "fonts.json.tmp";
    static final int SCHEMA = 1;
    static final String FONT_DIR = "resources/fonts";

    final File projectDirectory;
    final List<ProjectFont> fonts = new ArrayList<>();
    String activeId = "";

    FontStore(File projectDirectory) {
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
        fonts.clear();
        activeId = "";
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
            activeId = root.optString("active", "");
            JSONArray array = root.optJSONArray("fonts");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    ProjectFont font = new ProjectFont();
                    font.id = object.optString("id", "").trim();
                    font.name = object.optString("name", "").trim();
                    font.file = object.optString("file", "").trim();
                    font.createdAt = object.optLong("createdAt", 0L);
                    if (font.id.isEmpty() || font.name.isEmpty() || font.file.isEmpty()) {
                        continue;
                    }
                    if (resolveFile(font.file) == null) {
                        continue;
                    }
                    fonts.add(font);
                }
            }
            if (findById(activeId) == null) {
                activeId = "";
            }
        } catch (Exception ignored) {
            fonts.clear();
            activeId = "";
        }
    }

    boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            root.put("active", activeId == null ? "" : activeId);
            JSONArray array = new JSONArray();
            for (ProjectFont font : fonts) {
                JSONObject object = new JSONObject();
                object.put("id", font.id);
                object.put("name", font.name);
                object.put("file", font.file);
                object.put("createdAt", font.createdAt);
                array.put(object);
            }
            root.put("fonts", array);
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

    ProjectFont findById(String id) {
        if (id == null) {
            return null;
        }
        for (ProjectFont font : fonts) {
            if (font.id.equals(id)) {
                return font;
            }
        }
        return null;
    }

    ProjectFont activeFont() {
        return findById(activeId);
    }

    String activeFontName() {
        ProjectFont font = activeFont();
        return font == null ? "系统默认" : font.name;
    }

    File resolveFile(String relativePath) {
        return MediaFiles.resolveProjectFile(projectDirectory, relativePath);
    }

    Typeface activeTypeface() {
        ProjectFont font = activeFont();
        if (font == null) {
            return null;
        }
        File file = resolveFile(font.file);
        if (file == null || !file.isFile() || !isValidFontFile(file)) {
            return null;
        }
        try {
            return Typeface.createFromFile(file);
        } catch (Exception e) {
            return null;
        }
    }

    ProjectFont importFont(File source, String displayName) {
        if (source == null || !source.isFile()) {
            return null;
        }
        String extension = extensionOf(source.getName());
        if (!isSupportedExtension(extension)) {
            return null;
        }
        File directory = new File(projectDirectory, FONT_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        String safeId = id.replaceAll("[^a-zA-Z0-9_-]", "");
        File target = new File(directory, "font_" + safeId + "_"
                + System.currentTimeMillis() + extension);
        try {
            copy(source, target);
            if (!isValidFontFile(target)) {
                throw new IOException("invalid font header");
            }
            Typeface.createFromFile(target);
        } catch (Exception e) {
            MediaFiles.deleteQuietly(target);
            return null;
        }
        String relative = MediaFiles.relativePath(projectDirectory, target);
        if (relative == null) {
            MediaFiles.deleteQuietly(target);
            return null;
        }
        ProjectFont font = new ProjectFont();
        font.id = id;
        font.name = cleanName(displayName, source.getName());
        font.file = relative;
        font.createdAt = System.currentTimeMillis();
        fonts.add(font);
        if (!save()) {
            fonts.remove(font);
            MediaFiles.deleteQuietly(target);
            return null;
        }
        return font;
    }

    boolean deleteFont(ProjectFont font) {
        if (font == null) {
            return false;
        }
        File file = resolveFile(font.file);
        fonts.remove(font);
        if (font.id.equals(activeId)) {
            activeId = "";
        }
        if (!save()) {
            load();
            return false;
        }
        MediaFiles.deleteQuietly(file);
        return true;
    }

    boolean setActive(String id) {
        if (!id.isEmpty() && findById(id) == null) {
            return false;
        }
        activeId = id;
        return save();
    }

    static boolean isValidFontFile(File file) {
        if (file == null || !file.isFile() || file.length() < 12) {
            return false;
        }
        byte[] header = new byte[4];
        try (InputStream in = new FileInputStream(file)) {
            int read = in.read(header);
            if (read < 4) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }
        int magic = ((header[0] & 0xff) << 24) | ((header[1] & 0xff) << 16)
                | ((header[2] & 0xff) << 8) | (header[3] & 0xff);
        if (magic == 0x00010000) {
            return true;
        }
        String tag = new String(header, java.nio.charset.StandardCharsets.ISO_8859_1);
        return "OTTO".equals(tag) || "true".equals(tag)
                || "typ1".equals(tag) || "ttcf".equals(tag);
    }

    static boolean isSupportedExtension(String extension) {
        return ".ttf".equals(extension) || ".otf".equals(extension)
                || ".ttc".equals(extension);
    }

    static boolean isUnsupportedWebFont(String extension) {
        return ".woff".equals(extension) || ".woff2".equals(extension);
    }

    private static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String displayName, String fileName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            name = fileName == null ? "字体" : fileName;
        }
        if (name.length() > 80) {
            name = name.substring(0, 80);
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