package io.github.myoslatal.languageforge;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Project-level vocabulary store backed by vocabulary.json. */
final class VocabularyStore {

    static final String FILE_NAME = "vocabulary.json";
    static final String TEMP_NAME = "vocabulary.json.tmp";
    static final int SCHEMA = 1;
    static final int MAX_TEXT = 200;
    static final int MAX_MEANING = 200;
    static final int MAX_CODE = 80;
    static final int MAX_CODEPOINT = 16;
    private static final String TAG = "VocabularyStore";

    final File projectDirectory;
    final List<VocabularyEntry> entries = new ArrayList<>();

    VocabularyStore(File projectDirectory) {
        this.projectDirectory = projectDirectory;
        load();
    }

    static File resolveProjectDirectory(Context context, String folderName) {
        if (context == null || folderName == null || folderName.trim().isEmpty()) {
            return null;
        }
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            return null;
        }
        File root = new File(base, "projects");
        File directory = new File(root, folderName);
        try {
            String rootCanonical = root.getCanonicalPath();
            String directoryCanonical = directory.getCanonicalPath();
            File parent = new File(directoryCanonical).getParentFile();
            if (parent == null || !parent.getCanonicalPath().equals(rootCanonical)
                    || !directoryCanonical.startsWith(rootCanonical + File.separator)
                    || !directory.isDirectory()) {
                return null;
            }
            return directory;
        } catch (IOException e) {
            return null;
        }
    }

    static String readProjectName(File projectDirectory) {
        if (projectDirectory == null) {
            return "";
        }
        File projectJson = new File(projectDirectory, "project.json");
        try {
            String content = new String(Files.readAllBytes(projectJson.toPath()),
                    StandardCharsets.UTF_8);
            JSONObject object = new JSONObject(content);
            String name = object.optString("name", "").trim();
            return name.isEmpty() ? projectDirectory.getName() : name;
        } catch (Exception e) {
            return projectDirectory.getName();
        }
    }

    void load() {
        entries.clear();
        if (projectDirectory == null) {
            return;
        }
        File file = new File(projectDirectory, FILE_NAME);
        if (!file.isFile()) {
            Log.i(TAG, "No vocabulary.json; using empty list: " + file);
            return;
        }
        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            JSONObject root = new JSONObject(content);
            if (root.optInt("schema", -1) != SCHEMA) {
                Log.w(TAG, "Unsupported vocabulary schema; using empty list");
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
                VocabularyEntry entry = readEntry(object);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not read vocabulary.json; using empty list", e);
            entries.clear();
        }
    }

    boolean save() {
        if (projectDirectory == null || !projectDirectory.isDirectory()) {
            return false;
        }
        File temp = new File(projectDirectory, TEMP_NAME);
        File target = new File(projectDirectory, FILE_NAME);
        try {
            JSONObject root = new JSONObject();
            root.put("schema", SCHEMA);
            JSONArray array = new JSONArray();
            for (VocabularyEntry entry : entries) {
                array.put(writeEntry(entry));
            }
            root.put("entries", array);
            try (FileOutputStream out = new FileOutputStream(temp)) {
                out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                out.getFD().sync();
            }
            try {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp.toPath(), target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | JSONException e) {
            Log.w(TAG, "Could not write vocabulary.json", e);
            //noinspection ResultOfMethodCallIgnored
            temp.delete();
            return false;
        }
    }

    VocabularyEntry findById(String id) {
        if (id == null) {
            return null;
        }
        for (VocabularyEntry entry : entries) {
            if (id.equals(entry.id)) {
                return entry;
            }
        }
        return null;
    }

    String displayName(VocabularyEntry entry) {
        return displayName(entry, new HashSet<>());
    }

    /** Renders stored hex code points as the corresponding Unicode characters. */
    static String unicodeText(List<String> codepoints) {
        StringBuilder text = new StringBuilder();
        for (String raw : codepoints) {
            if (raw == null) {
                continue;
            }
            String normalized = normalizeCodePoint(raw);
            if (normalized == null) {
                continue;
            }
            try {
                text.appendCodePoint(Integer.parseInt(normalized, 16));
            } catch (IllegalArgumentException ignored) {
                // normalizeCodePoint already validates the range; keep this defensive.
            }
        }
        return text.toString();
    }

    private String displayName(VocabularyEntry entry, Set<String> visited) {
        if (entry == null || !visited.add(entry.id)) {
            return "";
        }
        if (VocabularyEntry.TYPE_TEXT.equals(entry.type) && !entry.text.isEmpty()) {
            return entry.text;
        }
        if (VocabularyEntry.TYPE_UNICODE.equals(entry.type)) {
            String unicodeText = unicodeText(entry.codepoints);
            if (!unicodeText.isEmpty()) {
                return unicodeText;
            }
        }
        if (VocabularyEntry.TYPE_COMBO.equals(entry.type) && !entry.parts.isEmpty()) {
            StringBuilder composed = new StringBuilder();
            for (String partId : entry.parts) {
                composed.append(displayName(findById(partId), visited));
            }
            if (composed.length() > 0) {
                return composed.toString();
            }
        }
        if (!entry.meanings.isEmpty()) {
            return entry.meanings.get(0);
        }
        if (!entry.codes.isEmpty()) {
            return entry.codes.get(0);
        }
        return "未命名词汇";
    }

    static String joinSummary(List<String> values) {
        if (values.isEmpty()) {
            return "—";
        }
        return String.join(" · ", values);
    }

    static String clean(String value, int max) {
        String text = value == null ? "" : value.trim();
        if (text.length() > max) {
            text = text.substring(0, max).trim();
        }
        return text;
    }

    static String normalizeCodePoint(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        if (text.startsWith("0x")) {
            text = text.substring(2);
        }
        if (!text.matches("[0-9a-f]{1,6}")) {
            return null;
        }
        int codePoint;
        try {
            codePoint = Integer.parseInt(text, 16);
        } catch (NumberFormatException e) {
            return null;
        }
        if (!Character.isValidCodePoint(codePoint)
                || (codePoint >= Character.MIN_SURROGATE
                && codePoint <= Character.MAX_SURROGATE)) {
            return null;
        }
        return Integer.toHexString(codePoint);
    }

    private VocabularyEntry readEntry(JSONObject object) {
        VocabularyEntry entry = new VocabularyEntry(cleanId(object.optString("id")));
        String type = object.optString("type", VocabularyEntry.TYPE_TEXT);
        entry.type = VocabularyEntry.isKnownType(type) ? type : VocabularyEntry.TYPE_TEXT;
        readStrings(object.optJSONArray("meanings"), entry.meanings, MAX_MEANING);
        JSONArray posArray = object.optJSONArray("posMeanings");
        if (posArray != null) {
            for (int i = 0; i < posArray.length(); i++) {
                JSONObject group = posArray.optJSONObject(i);
                if (group == null) {
                    continue;
                }
                VocabularyEntry.PosMeaning posMeaning = new VocabularyEntry.PosMeaning();
                posMeaning.posId = cleanId(group.optString("posId"));
                readStrings(group.optJSONArray("meanings"), posMeaning.meanings, MAX_MEANING);
                if (!posMeaning.posId.isEmpty()) {
                    entry.posMeanings.add(posMeaning);
                }
            }
        }
        readStrings(object.optJSONArray("codes"), entry.codes, MAX_CODE);
        entry.text = clean(object.optString("text", ""), MAX_TEXT);
        readStrings(object.optJSONArray("codepoints"), entry.codepoints, MAX_CODEPOINT);
        readStrings(object.optJSONArray("parts"), entry.parts, 64);
        entry.image = clean(object.optString("image", ""), 500);
        entry.imageWidth = Math.max(0, object.optInt("imageWidth", 0));
        entry.imageHeight = Math.max(0, object.optInt("imageHeight", 0));
        entry.audio = clean(object.optString("audio", ""), 500);
        entry.audioStart = Math.max(0L, object.optLong("audioStart", 0L));
        entry.audioEnd = Math.max(0L, object.optLong("audioEnd", 0L));
        entry.audioDuration = Math.max(0L, object.optLong("audioDuration", 0L));
        entry.createdAt = object.optLong("createdAt", 0L);
        entry.updatedAt = object.optLong("updatedAt", entry.createdAt);
        return entry;
    }

    private JSONObject writeEntry(VocabularyEntry entry) throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", entry.id);
        object.put("type", entry.type);
        object.put("meanings", toArray(entry.meanings));
        JSONArray posArray = new JSONArray();
        for (VocabularyEntry.PosMeaning posMeaning : entry.posMeanings) {
            if (posMeaning.posId.isEmpty()) {
                continue;
            }
            JSONObject group = new JSONObject();
            group.put("posId", posMeaning.posId);
            group.put("meanings", toArray(posMeaning.meanings));
            posArray.put(group);
        }
        object.put("posMeanings", posArray);
        object.put("codes", toArray(entry.codes));
        object.put("text", entry.text);
        object.put("codepoints", toArray(entry.codepoints));
        object.put("parts", toArray(entry.parts));
        object.put("image", entry.image);
        object.put("imageWidth", entry.imageWidth);
        object.put("imageHeight", entry.imageHeight);
        object.put("audio", entry.audio);
        object.put("audioStart", entry.audioStart);
        object.put("audioEnd", entry.audioEnd);
        object.put("audioDuration", entry.audioDuration);
        object.put("createdAt", entry.createdAt);
        object.put("updatedAt", entry.updatedAt);
        return object;
    }

    private static void readStrings(JSONArray array, List<String> target, int max) {
        if (array == null) {
            return;
        }
        for (int i = 0; i < array.length(); i++) {
            String value = clean(array.optString(i, ""), max);
            if (!value.isEmpty()) {
                target.add(value);
            }
        }
    }

    private static JSONArray toArray(List<String> values) {
        JSONArray array = new JSONArray();
        for (String value : values) {
            array.put(value);
        }
        return array;
    }

    private static String cleanId(String value) {
        return value == null || value.trim().isEmpty()
                ? UUID.randomUUID().toString() : value.trim();
    }
}
