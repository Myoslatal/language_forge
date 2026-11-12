package io.github.myoslatal.languageforge;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Project-level pos.json (schema 1): id/name/description/code/createdAt/updatedAt. */
public class PosStore {

    static final String FILE_NAME = "pos.json";
    static final String TEMP_NAME = "pos.json.tmp";
    static final int SCHEMA = 1;

    final File projectDirectory;
    final List<PosEntry> entries = new ArrayList<>();

    public PosStore(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        load();
    }

    public static File resolveProjectDirectory(Context context, String folderName) {
        return VocabularyStore.resolveProjectDirectory(context, folderName);
    }

    public static String readProjectName(File projectDirectory) {
        return VocabularyStore.readProjectName(projectDirectory);
    }

    public void load() {
        entries.clear();
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
            JSONArray array = root.optJSONArray("entries");
            if (array == null) {
                return;
            }
            for (int i = 0; i < array.length(); i++) {
                JSONObject object = array.optJSONObject(i);
                if (object == null) {
                    continue;
                }
                PosEntry entry = new PosEntry();
                entry.id = object.optString("id", "").trim();
                entry.name = object.optString("name", "").trim();
                entry.description = object.optString("description", "").trim();
                entry.code = object.optString("code", "").trim();
                entry.createdAt = object.optLong("createdAt", 0L);
                entry.updatedAt = object.optLong("updatedAt", entry.createdAt);
                if (entry.id.isEmpty() || entry.name.isEmpty()) {
                    continue;
                }
                entries.add(entry);
            }
        } catch (Exception ignored) {
            entries.clear();
        }
    }

    public boolean save() {
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            JSONArray array = new JSONArray();
            for (PosEntry entry : entries) {
                JSONObject object = new JSONObject();
                object.put("id", entry.id);
                object.put("name", entry.name);
                object.put("description", entry.description);
                object.put("code", entry.code);
                object.put("createdAt", entry.createdAt);
                object.put("updatedAt", entry.updatedAt);
                array.put(object);
            }
            root.put("entries", array);
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

    public PosEntry findById(String id) {
        if (id == null) {
            return null;
        }
        for (PosEntry entry : entries) {
            if (entry.id.equals(id)) {
                return entry;
            }
        }
        return null;
    }

    public PosEntry create(String name, String description, String code) {
        String cleanName = clean(name);
        if (cleanName.isEmpty()) {
            return null;
        }
        PosEntry entry = new PosEntry();
        entry.id = UUID.randomUUID().toString();
        entry.name = cleanName;
        entry.description = clean(description);
        entry.code = clean(code);
        entry.createdAt = System.currentTimeMillis();
        entry.updatedAt = entry.createdAt;
        entries.add(entry);
        if (!save()) {
            entries.remove(entry);
            return null;
        }
        return entry;
    }

    public boolean update(String id, String name, String description, String code) {
        PosEntry entry = findById(id);
        String cleanName = clean(name);
        if (entry == null || cleanName.isEmpty()) {
            return false;
        }
        String oldName = entry.name;
        String oldDescription = entry.description;
        String oldCode = entry.code;
        long oldUpdated = entry.updatedAt;
        entry.name = cleanName;
        entry.description = clean(description);
        entry.code = clean(code);
        entry.updatedAt = System.currentTimeMillis();
        if (!save()) {
            entry.name = oldName;
            entry.description = oldDescription;
            entry.code = oldCode;
            entry.updatedAt = oldUpdated;
            return false;
        }
        return true;
    }

    public boolean delete(String id) {
        PosEntry entry = findById(id);
        if (entry == null) {
            return false;
        }
        entries.remove(entry);
        if (!save()) {
            entries.add(entry);
            return false;
        }
        return true;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() > 2000 ? trimmed.substring(0, 2000) : trimmed;
    }
}
