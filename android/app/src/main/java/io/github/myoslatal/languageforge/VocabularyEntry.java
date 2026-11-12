package io.github.myoslatal.languageforge;

import java.util.ArrayList;
import java.util.List;

/** One project-level vocabulary entry. */
final class VocabularyEntry {
    static final String TYPE_TEXT = "text";
    static final String TYPE_UNICODE = "unicode";
    static final String TYPE_COMBO = "combo";
    static final String TYPE_IMAGE = "image";

    String id;
    String type = TYPE_TEXT;
    final List<String> meanings = new ArrayList<>();
    final List<PosMeaning> posMeanings = new ArrayList<>();
    final List<String> codes = new ArrayList<>();
    String text = "";
    String phonetic = "";
    final List<String> codepoints = new ArrayList<>();
    final List<String> parts = new ArrayList<>();
    String image = "";
    int imageWidth;
    int imageHeight;
    String audio = "";
    long audioStart;
    long audioEnd;
    long audioDuration;
    long createdAt;
    long updatedAt;

    VocabularyEntry(String id) {
        this.id = id;
    }

    /** One POS group's independent meanings; meanings[] stays the aggregated compatibility field. */
    static final class PosMeaning {
        String posId = "";
        final List<String> meanings = new ArrayList<>();
    }

    static String typeLabel(String type) {
        if (TYPE_UNICODE.equals(type)) {
            return "Unicode";
        }
        if (TYPE_COMBO.equals(type)) {
            return "组合";
        }
        if (TYPE_IMAGE.equals(type)) {
            return "自定义文字";
        }
        return "文本";
    }

    static boolean isKnownType(String type) {
        return TYPE_TEXT.equals(type) || TYPE_UNICODE.equals(type)
                || TYPE_COMBO.equals(type) || TYPE_IMAGE.equals(type);
    }
}
