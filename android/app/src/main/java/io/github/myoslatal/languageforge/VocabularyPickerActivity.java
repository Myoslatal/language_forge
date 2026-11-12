package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeChip;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Generic multi-select vocabulary picker with search. */
public class VocabularyPickerActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_EXCLUDE_ID = "excludeId";
    public static final String EXTRA_SELECTED_IDS = "selectedIds";
    public static final String EXTRA_TITLE = "title";

    private VocabularyStore store;
    private PosStore posStore;
    private Typeface vocabTypeface;
    private String excludeId;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;
    private ForgeTextField search;
    private TextView pageLabel;
    private SeekBar pageSeek;
    private boolean updatingPageSeek;
    private int page = 1;
    private final List<String> selectedIds = new ArrayList<>();
    private String query = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        File projectDirectory = VocabularyStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new VocabularyStore(projectDirectory);
        posStore = new PosStore(projectDirectory);
        vocabTypeface = new FontStore(projectDirectory).activeTypeface();
        excludeId = getIntent().getStringExtra(EXTRA_EXCLUDE_ID);
        ArrayList<String> initialIds = getIntent().getStringArrayListExtra(EXTRA_SELECTED_IDS);
        if (initialIds != null) {
            for (String id : initialIds) {
                if (id != null && !id.isEmpty() && !selectedIds.contains(id)) {
                    selectedIds.add(id);
                }
            }
        }

        setContentView(R.layout.activity_vocabulary_picker);
        applyTopInset(findViewById(R.id.picker_topbar));
        applyBottomInset(findViewById(R.id.picker_scroll));
        list = findViewById(R.id.picker_list);
        emptyState = findViewById(R.id.picker_empty);
        count = findViewById(R.id.picker_count);
        search = findViewById(R.id.picker_search);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null && !title.isEmpty()) {
            ((TextView) findViewById(R.id.picker_title)).setText(title);
        }
        pageLabel = findViewById(R.id.picker_page);
        pageSeek = findViewById(R.id.picker_page_seek);
        pageSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingPageSeek) {
                    return;
                }
                page = progress + 1;
                render();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        findViewById(R.id.btn_picker_prev).setOnClickListener(v -> {
            if (page > 1) {
                page--;
                render();
            }
        });
        findViewById(R.id.btn_picker_next).setOnClickListener(v -> {
            page++;
            render();
        });

        findViewById(R.id.btn_picker_back).setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        findViewById(R.id.btn_picker_confirm).setOnClickListener(v -> {
            Intent result = new Intent();
            result.putStringArrayListExtra(EXTRA_SELECTED_IDS, new ArrayList<>(selectedIds));
            setResult(RESULT_OK, result);
            finish();
        });
        search.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                page = 1;
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        render();
    }

    private void render() {
        list.removeAllViews();
        List<VocabularyEntry> matches = new ArrayList<>();
        for (VocabularyEntry entry : store.entries) {
            if (!entry.id.equals(excludeId) && matches(entry, query)) {
                matches.add(entry);
            }
        }
        int pageCount = Math.max(1, (matches.size() + 49) / 50);
        if (page < 1) page = 1;
        if (page > pageCount) page = pageCount;
        int start = (page - 1) * 50;
        int end = Math.min(matches.size(), start + 50);
        for (int i = start; i < end; i++) {
            addRow(matches.get(i));
        }
        emptyState.setVisibility(matches.isEmpty() ? View.VISIBLE : View.GONE);
        count.setText("已选 " + selectedIds.size() + " 个");
        pageLabel.setText("第 " + page + " / " + pageCount + " 页 · 共 "
                + matches.size() + " 个匹配");
        updatingPageSeek = true;
        pageSeek.setMax(Math.max(0, pageCount - 1));
        pageSeek.setProgress(page - 1);
        pageSeek.setEnabled(pageCount > 1);
        pageSeek.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        updatingPageSeek = false;
    }

    private void addRow(VocabularyEntry entry) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_vocabulary_picker, list, false);
        CheckBox check = row.findViewById(R.id.picker_check);
        TextView name = row.findViewById(R.id.picker_name);
        ForgeBadge type = row.findViewById(R.id.picker_type);
        TextView codes = row.findViewById(R.id.picker_codes);
        TextView meanings = row.findViewById(R.id.picker_meanings);
        LinearLayout posBadges = row.findViewById(R.id.picker_pos_badges);

        name.setText(store.displayName(entry));
        if (vocabTypeface != null) {
            Typeface base = name.getTypeface();
            name.setTypeface(Typeface.create(vocabTypeface,
                    base == null ? Typeface.NORMAL : base.getStyle()));
        }
        type.setText(VocabularyEntry.typeLabel(entry.type));
        String codesText = entry.codes.isEmpty()
                ? "编码：—" : "编码：" + VocabularyStore.joinSummary(entry.codes);
        String phoneticText = phoneticText(entry);
        if (!phoneticText.isEmpty()) {
            codesText += " · 音标：" + phoneticText;
        }
        codes.setText(codesText);
        meanings.setText("释义：" + posMeaningSummary(entry));
        posBadges.removeAllViews();
        for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
            PosEntry pos = posStore.findById(group.posId);
            if (pos == null) {
                continue;
            }
            ForgeChip chip = new ForgeChip(this);
            chip.setText(pos.code.isEmpty() ? pos.name : pos.name + " · " + pos.code);
            chip.setActive(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(4));
            posBadges.addView(chip, lp);
        }
        check.setChecked(selectedIds.contains(entry.id));
        row.setOnClickListener(v -> {
            if (selectedIds.contains(entry.id)) {
                selectedIds.remove(entry.id);
            } else {
                selectedIds.add(entry.id);
            }
            render();
        });
        list.addView(row);
    }

    private boolean matches(VocabularyEntry entry, String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        String compact = q.replace(" ", "");
        String normalizedQuery = stripCodePointPrefix(compact);
        for (String code : entry.codes) {
            if (code.toLowerCase(Locale.ROOT).replace(" ", "").contains(compact)) {
                return true;
            }
        }
        if (entry.text.toLowerCase(Locale.ROOT).contains(q)) {
            return true;
        }
        for (String codepoint : entry.codepoints) {
            if (stripCodePointPrefix(codepoint.toLowerCase(Locale.ROOT)
                    .replace(" ", "")).contains(normalizedQuery)) {
                return true;
            }
        }
        for (String meaning : entry.meanings) {
            if (meaning.toLowerCase(Locale.ROOT).contains(q)) {
                return true;
            }
        }
        for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
            PosEntry pos = posStore.findById(group.posId);
            if (pos != null && (pos.name.toLowerCase(Locale.ROOT).contains(q)
                    || pos.code.toLowerCase(Locale.ROOT).contains(q))) {
                return true;
            }
        }
        return store.displayName(entry).toLowerCase(Locale.ROOT).contains(q);
    }

    private String posMeaningSummary(VocabularyEntry entry) {
        StringBuilder builder = new StringBuilder();
        for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
            PosEntry pos = posStore.findById(group.posId);
            if (pos == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("；");
            }
            builder.append(pos.name).append("：")
                    .append(VocabularyStore.joinSummary(group.meanings));
        }
        return builder.length() == 0
                ? VocabularyStore.joinSummary(entry.meanings) : builder.toString();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private String stripCodePointPrefix(String value) {
        if (value.startsWith("0x")) {
            return value.substring(2);
        }
        return value;
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }

    private String phoneticText(VocabularyEntry entry) {
        StringBuilder builder = new StringBuilder();
        for (String phonetic : entry.phonetics) {
            if (phonetic == null || phonetic.isEmpty()) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(phonetic);
        }
        if (builder.length() == 0 && entry.phonetic != null) builder.append(entry.phonetic);
        return builder.toString();
    }
}