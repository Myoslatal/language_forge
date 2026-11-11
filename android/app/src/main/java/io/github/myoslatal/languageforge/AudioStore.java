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

/** Project-level audios.json + resources/audio. */
class AudioStore {

    static final String FILE_NAME = "audios.json";
    static final String TEMP_NAME = "audios.json.tmp";
    static final int SCHEMA = 1;
    static final String AUDIO_DIR = "resources/audio";

    final File projectDirectory;
    final List<ProjectAudio> audios = new ArrayList<>();

    AudioStore(File projectDirectory) {
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
        audios.clear();
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
            JSONArray array = root.optJSONArray("audios");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject object = array.optJSONObject(i);
                    if (object == null) {
                        continue;
                    }
                    ProjectAudio audio = new ProjectAudio();
                    audio.id = object.optString("id", "").trim();
                    audio.name = object.optString("name", "").trim();
                    audio.file = object.optString("file", "").trim();
                    audio.code = object.optString("code", "").trim();
                    audio.duration = Math.max(0L, object.optLong("duration", 0L));
                    audio.createdAt = object.optLong("createdAt", 0L);
                    if (audio.id.isEmpty() || audio.file.isEmpty()) {
                        continue;
                    }
                    if (resolveFile(audio.file) == null) {
                        continue;
                    }
                    if (audio.name.isEmpty()) {
                        File resolved = resolveFile(audio.file);
                        audio.name = resolved == null ? audio.file : resolved.getName();
                    }
                    audios.add(audio);
                }
            }
        } catch (Exception ignored) {
            audios.clear();
        }
    }

    boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            JSONArray array = new JSONArray();
            for (ProjectAudio audio : audios) {
                JSONObject object = new JSONObject();
                object.put("id", audio.id);
                object.put("name", audio.name);
                object.put("file", audio.file);
                object.put("code", audio.code);
                object.put("duration", audio.duration);
                object.put("createdAt", audio.createdAt);
                array.put(object);
            }
            root.put("audios", array);
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

    ProjectAudio findById(String id) {
        if (id == null) {
            return null;
        }
        for (ProjectAudio audio : audios) {
            if (audio.id.equals(id)) {
                return audio;
            }
        }
        return null;
    }

    ProjectAudio findByFile(String relativePath) {
        if (relativePath == null) {
            return null;
        }
        for (ProjectAudio audio : audios) {
            if (audio.file.equals(relativePath)) {
                return audio;
            }
        }
        return null;
    }

    File resolveFile(String relativePath) {
        return MediaFiles.resolveProjectFile(projectDirectory, relativePath);
    }

    List<File> unregisteredFiles() {
        List<File> result = new ArrayList<>();
        File directory = new File(projectDirectory, AUDIO_DIR);
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

    ProjectAudio importAudio(File source, String displayName) {
        if (source == null || !source.isFile()) {
            return null;
        }
        String extension = extensionOf(source.getName());
        if (!isSupportedExtension(extension)) {
            return null;
        }
        File directory = new File(projectDirectory, AUDIO_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            return null;
        }
        String id = UUID.randomUUID().toString();
        String safeId = id.replaceAll("[^a-zA-Z0-9_-]", "");
        File target = new File(directory, "aud_" + safeId + "_"
                + System.currentTimeMillis() + extension);
        try {
            copy(source, target);
            long duration = MediaFiles.audioDuration(target);
            if (duration <= 0) {
                throw new IOException("invalid audio");
            }
            ProjectAudio audio = new ProjectAudio();
            audio.id = id;
            audio.name = cleanName(displayName, target.getName());
            audio.file = MediaFiles.relativePath(projectDirectory, target);
            audio.duration = duration;
            audio.createdAt = System.currentTimeMillis();
            if (audio.file == null) {
                throw new IOException("path outside project");
            }
            audios.add(audio);
            if (!save()) {
                audios.remove(audio);
                throw new IOException("save failed");
            }
            return audio;
        } catch (Exception e) {
            MediaFiles.deleteQuietly(target);
            return null;
        }
    }

    ProjectAudio registerFile(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String relative = MediaFiles.relativePath(projectDirectory, file);
        if (relative == null) {
            return null;
        }
        ProjectAudio existing = findByFile(relative);
        if (existing != null) {
            return existing;
        }
        long duration = MediaFiles.audioDuration(file);
        if (duration <= 0) {
            return null;
        }
        ProjectAudio audio = new ProjectAudio();
        audio.id = UUID.randomUUID().toString();
        audio.name = file.getName();
        audio.file = relative;
        audio.duration = duration;
        audio.createdAt = System.currentTimeMillis();
        audios.add(audio);
        if (!save()) {
            audios.remove(audio);
            return null;
        }
        return audio;
    }

    boolean setCode(ProjectAudio audio, String code) {
        if (audio == null) {
            return false;
        }
        audio.code = code == null ? "" : code.trim();
        if (!save()) {
            load();
            return false;
        }
        return true;
    }

    boolean deleteAudio(ProjectAudio audio) {
        if (audio == null) {
            return false;
        }
        File file = resolveFile(audio.file);
        audios.remove(audio);
        if (!save()) {
            load();
            return false;
        }
        MediaFiles.deleteQuietly(file);
        return true;
    }

    static boolean isSupportedExtension(String extension) {
        return ".m4a".equals(extension) || ".mp3".equals(extension)
                || ".wav".equals(extension) || ".ogg".equals(extension)
                || ".aac".equals(extension) || ".flac".equals(extension);
    }

    static String extensionOf(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String displayName, String fileName) {
        String name = displayName == null ? "" : displayName.trim();
        if (name.isEmpty()) {
            name = fileName == null ? "音频" : fileName;
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
