package io.github.myoslatal.languageforge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Import a Lexicon JSONL vocabulary file into pos.json + vocabulary.json. */
final class LexiconImporter {

    static final class Summary {
        int added;
        int duplicates;
        int invalid;
        int newPos;
    }

    private LexiconImporter() {
    }

    static Summary importFrom(InputStream input, File projectDirectory) throws IOException {
        if (input == null || projectDirectory == null) {
            throw new IOException("词表文件不可用");
        }
        PosStore posStore = new PosStore(projectDirectory);
        VocabularyStore vocabulary = new VocabularyStore(projectDirectory);
        Summary summary = new Summary();

        List<String> lines = readLines(input);
        JSONObject meta = null;
        List<String> entryLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.contains("\"_meta\"")) {
                try {
                    JSONObject object = new JSONObject(trimmed);
                    JSONObject candidate = object.optJSONObject("_meta");
                    if (candidate != null) {
                        if (meta == null) {
                            meta = candidate;
                        }
                        continue;
                    }
                } catch (Exception ignored) {
                    // Counted as an invalid entry below.
                }
            }
            entryLines.add(trimmed);
        }

        Map<String, String> metaLabels = new LinkedHashMap<>();
        Map<String, PosEntry> posByCode = new HashMap<>();
        Map<String, PosEntry> posByName = new HashMap<>();
        for (PosEntry pos : posStore.entries) {
            if (pos.code != null && !pos.code.isEmpty()) {
                posByCode.put(pos.code.toLowerCase(Locale.ROOT), pos);
            }
            posByName.put(pos.name.toLowerCase(Locale.ROOT), pos);
        }
        if (meta != null) {
            JSONArray types = meta.optJSONArray("types");
            if (types != null) {
                for (int i = 0; i < types.length(); i++) {
                    JSONObject type = types.optJSONObject(i);
                    if (type == null) {
                        continue;
                    }
                    String code = type.optString("k", "").trim();
                    String label = type.optString("label", "").trim();
                    if (code.isEmpty() && label.isEmpty()) {
                        continue;
                    }
                    String key = code.isEmpty() ? label : code;
                    String name = label.isEmpty() ? code : label;
                    String lowerKey = key.toLowerCase(Locale.ROOT);
                    if (metaLabels.containsKey(lowerKey)) {
                        continue;
                    }
                    metaLabels.put(lowerKey, name);
                    PosEntry pos = code.isEmpty() ? null : posByCode.get(code.toLowerCase(Locale.ROOT));
                    if (pos == null) {
                        pos = posByName.get(name.toLowerCase(Locale.ROOT));
                    }
                    if (pos == null) {
                        pos = posStore.create(name, "Lexicon 词表导入", code);
                        if (pos != null) {
                            summary.newPos++;
                        }
                    }
                    if (pos != null) {
                        if (!code.isEmpty()) {
                            posByCode.put(code.toLowerCase(Locale.ROOT), pos);
                        }
                        posByName.put(name.toLowerCase(Locale.ROOT), pos);
                    }
                }
            }
        }

        Set<String> existingTexts = new HashSet<>();
        for (VocabularyEntry entry : vocabulary.entries) {
            existingTexts.add(normalizeText(entry.text));
        }
        Set<String> seen = new HashSet<>();

        for (String line : entryLines) {
            JSONObject object;
            try {
                object = new JSONObject(line);
            } catch (Exception e) {
                summary.invalid++;
                continue;
            }
            if (object.optJSONObject("_meta") != null) {
                continue;
            }

            String text = object.optString("rendered", "").trim();
            if (text.isEmpty()) {
                Object rawEntry = object.opt("entry");
                if (rawEntry instanceof String) {
                    text = ((String) rawEntry).trim();
                }
            }
            if (text.isEmpty()) {
                summary.invalid++;
                continue;
            }
            String normalized = normalizeText(text);
            if (existingTexts.contains(normalized) || !seen.add(normalized)) {
                summary.duplicates++;
                continue;
            }

            List<String> meanings = new ArrayList<>();
            addMeaning(meanings, object.optString("definition", ""));
            addMeaning(meanings, object.optString("detail", ""));
            if (meanings.isEmpty()) {
                summary.invalid++;
                continue;
            }

            VocabularyEntry entry = new VocabularyEntry(UUID.randomUUID().toString());
            entry.type = VocabularyEntry.TYPE_TEXT;
            entry.text = text;
            entry.phonetic = VocabularyStore.clean(
                    object.optString("pronunciation", ""), VocabularyStore.MAX_PHONETIC);
            entry.meanings.addAll(meanings);
            entry.createdAt = entry.updatedAt = System.currentTimeMillis();

            String typeCode = object.optString("type", "").trim();
            PosEntry pos = typeCode.isEmpty()
                    ? null : posByCode.get(typeCode.toLowerCase(Locale.ROOT));
            if (pos != null) {
                VocabularyEntry.PosMeaning group = new VocabularyEntry.PosMeaning();
                group.posId = pos.id;
                group.meanings.addAll(meanings);
                entry.posMeanings.add(group);
            }
            vocabulary.entries.add(entry);
            summary.added++;
        }

        if (summary.newPos > 0 && !posStore.save()) {
            throw new IOException("无法写入 pos.json");
        }
        if (!vocabulary.save()) {
            throw new IOException("无法写入 vocabulary.json");
        }
        return summary;
    }

    private static List<String> readLines(InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (first) {
                    first = false;
                    if (!line.isEmpty() && line.charAt(0) == '\ufeff') {
                        line = line.substring(1);
                    }
                }
                lines.add(line);
            }
        }
        return lines;
    }

    private static void addMeaning(List<String> meanings, String value) {
        String clean = VocabularyStore.clean(value, VocabularyStore.MAX_MEANING);
        if (!clean.isEmpty() && !meanings.contains(clean)) {
            meanings.add(clean);
        }
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }
}
