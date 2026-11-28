package io.github.myoslatal.languageforge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared #<code> token syntax for editor item fields. */
final class TokenCodec {
    private TokenCodec() {}

    static final class Result {
        final List<ProjectStructure.Question.OptionItem> items = new ArrayList<>();
        String error = "";
        String rendered = "";

        boolean ok() {
            return error == null || error.isEmpty();
        }
    }

    static boolean isValidCode(String raw) {
        if (raw == null) return false;
        String code = raw.trim();
        if (code.isEmpty()) return true; // blank code rows are ignored elsewhere
        return code.indexOf('#') < 0 && code.indexOf('<') < 0 && code.indexOf('>') < 0;
    }

    static String invalidCodeMessage(String code) {
        return "编码不能包含 #、< 或 >（会破坏 #<编码> 定界）：" + (code == null ? "" : code);
    }

    static VocabularyEntry findByCode(VocabularyStore store, String raw) {
        if (store == null || raw == null) return null;
        String code = raw.trim();
        if (code.isEmpty()) return null;
        VocabularyEntry match = null;
        for (VocabularyEntry entry : store.entries) {
            for (String candidate : entry.codes) {
                if (candidate != null && candidate.trim().equals(code)) {
                    if (match != null && !match.id.equals(entry.id)) return null;
                    match = entry;
                }
            }
        }
        if (match != null) return match;
        for (VocabularyEntry entry : store.entries) {
            for (String candidate : entry.codes) {
                if (candidate != null && candidate.trim().equalsIgnoreCase(code)) {
                    if (match != null && !match.id.equals(entry.id)) return null;
                    match = entry;
                }
            }
        }
        return match;
    }

    static String serialize(List<ProjectStructure.Question.OptionItem> items, VocabularyStore store) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        for (ProjectStructure.Question.OptionItem item : items) {
            if (item == null) continue;
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = store == null ? null : store.findById(item.vocabId);
                String code = entry == null || entry.codes.isEmpty() ? "" : entry.codes.get(0);
                code = code == null ? "" : code.trim();
                if (code.isEmpty()) {
                    out.append("#<").append(item.vocabId == null ? "" : item.vocabId).append('>');
                } else {
                    out.append("#<").append(code).append('>');
                }
            } else {
                out.append(item.text == null ? "" : item.text);
            }
        }
        return out.toString();
    }

    static Result parse(String text, VocabularyStore store) {
        Result result = new Result();
        String source = text == null ? "" : text;
        StringBuilder plain = new StringBuilder();
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c != '#') {
                plain.append(c);
                i++;
                continue;
            }
            int afterHash = i + 1;
            while (afterHash < source.length()
                    && Character.isWhitespace(source.charAt(afterHash))) afterHash++;
            if (afterHash >= source.length() || source.charAt(afterHash) != '<') {
                plain.append(c);
                i++;
                continue;
            }
            int close = source.indexOf('>', afterHash + 1);
            if (close < 0) {
                result.error = "词汇 token 未闭合：缺少 >";
                result.rendered = source;
                return result;
            }
            String rawCode = source.substring(afterHash + 1, close);
            if (rawCode.indexOf('<') >= 0 || rawCode.indexOf('#') >= 0) {
                result.error = "词汇 token 内含非法字符：# 或 <";
                result.rendered = source;
                return result;
            }
            String code = rawCode.trim();
            if (code.isEmpty()) {
                result.error = "词汇 token 的编码不能为空";
                result.rendered = source;
                return result;
            }
            VocabularyEntry entry = findByCode(store, code);
            if (entry == null) {
                result.error = "未知词汇编码：" + code;
                result.rendered = source;
                return result;
            }
            appendText(result.items, plain.toString());
            plain.setLength(0);
            ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
            item.kind = "vocab";
            item.vocabId = entry.id;
            result.items.add(item);
            i = close + 1;
        }
        appendText(result.items, plain.toString());
        result.rendered = renderItems(result.items, store);
        return result;
    }

    private static void appendText(List<ProjectStructure.Question.OptionItem> items, String text) {
        if (text == null || text.isEmpty()) return;
        if (!items.isEmpty() && "text".equals(items.get(items.size() - 1).kind)) {
            ProjectStructure.Question.OptionItem last = items.get(items.size() - 1);
            last.text = (last.text == null ? "" : last.text) + text;
            return;
        }
        ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
        item.kind = "text";
        item.text = text;
        items.add(item);
    }

    static String renderItems(List<ProjectStructure.Question.OptionItem> items, VocabularyStore store) {
        StringBuilder out = new StringBuilder();
        if (items == null) return "";
        for (ProjectStructure.Question.OptionItem item : items) {
            if (item == null) continue;
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = store == null ? null : store.findById(item.vocabId);
                if (entry == null) {
                    out.append("〔词汇已删除〕");
                } else {
                    out.append(store.displayName(entry));
                }
            } else {
                out.append(item.text == null ? "" : item.text);
            }
        }
        return out.toString();
    }

    static List<int[]> tokenRanges(String text) {
        List<int[]> ranges = new ArrayList<>();
        String source = text == null ? "" : text;
        int i = 0;
        while (i < source.length()) {
            if (source.charAt(i) != '#') { i++; continue; }
            int j = i + 1;
            while (j < source.length() && Character.isWhitespace(source.charAt(j))) j++;
            if (j >= source.length() || source.charAt(j) != '<') { i++; continue; }
            int close = source.indexOf('>', j + 1);
            if (close < 0) break;
            ranges.add(new int[]{i, close + 1});
            i = close + 1;
        }
        return ranges;
    }
}
