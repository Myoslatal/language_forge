package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeChip;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project-level vocabulary list. */
public class VocabularyListActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_ENTRY_ID = "entryId";

    private String folderName;
    private File projectDirectory;
    private VocabularyStore store;
    private PosStore posStore;
    private Typeface vocabTypeface;
    private static final int PAGE_SIZE = 50;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;
    private ForgeTextField searchField;
    private TextView visibleHint;
    private TextView noMatch;
    private LinearLayout pager;
    private TextView pageLabel;
    private SeekBar pageSeek;
    private boolean updatingPageSeek;
    private int page = 1;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor();
    private ActivityResultLauncher<String[]> lexiconPicker;
    private LexiconImporter.Mode pendingMode = LexiconImporter.Mode.SKIP;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = VocabularyStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new VocabularyStore(projectDirectory);
        posStore = new PosStore(projectDirectory);
        reloadTypeface();

        setContentView(R.layout.activity_vocabulary_list);
        applyTopInset(findViewById(R.id.vocabulary_topbar));
        applyBottomInset(findViewById(R.id.vocabulary_scroll));
        bindViews();
        lexiconPicker = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(), this::onLexiconPicked);

        ((TextView) findViewById(R.id.vocabulary_project)).setText(
                VocabularyStore.readProjectName(projectDirectory));
        findViewById(R.id.btn_vocabulary_back).setOnClickListener(v -> finish());
        View.OnClickListener newEntry = v -> openEditor(null);
        findViewById(R.id.btn_new_vocabulary).setOnClickListener(newEntry);
        findViewById(R.id.btn_empty_vocabulary).setOnClickListener(newEntry);
        findViewById(R.id.btn_import_lexicon).setOnClickListener(this::showImportModeDialog);
        searchField.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                page = 1;
                render();
            }
        });
        findViewById(R.id.btn_vocabulary_prev).setOnClickListener(v -> {
            if (page > 1) {
                page--;
                render();
            }
        });
        findViewById(R.id.btn_vocabulary_next).setOnClickListener(v -> {
            page++;
            render();
        });
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            store.load();
            posStore.load();
            reloadTypeface();
            render();
        }
    }

    @Override
    protected void onDestroy() {
        ioExecutor.shutdownNow();
        super.onDestroy();
    }

    private void bindViews() {
        list = findViewById(R.id.vocabulary_list);
        emptyState = findViewById(R.id.vocabulary_empty);
        count = findViewById(R.id.vocabulary_count);
        searchField = findViewById(R.id.vocabulary_search);
        visibleHint = findViewById(R.id.vocabulary_visible);
        noMatch = findViewById(R.id.vocabulary_no_match);
        pager = findViewById(R.id.vocabulary_pager);
        pageLabel = findViewById(R.id.vocabulary_page);
        pageSeek = findViewById(R.id.vocabulary_page_seek);
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
    }

    private void render() {
        if (list == null || store == null) {
            return;
        }
        String query = searchField == null ? "" : textOf(searchField).trim().toLowerCase(Locale.ROOT);
        List<VocabularyEntry> matches = new ArrayList<>();
        for (VocabularyEntry entry : store.entries) {
            if (query.isEmpty() || matches(entry, query)) {
                matches.add(entry);
            }
        }
        int total = store.entries.size();
        int pageCount = Math.max(1, (matches.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        if (page < 1) {
            page = 1;
        }
        if (page > pageCount) {
            page = pageCount;
        }
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(matches.size(), start + PAGE_SIZE);
        list.removeAllViews();
        count.setText(String.valueOf(total));
        emptyState.setVisibility(total == 0 ? View.VISIBLE : View.GONE);
        boolean noResults = total > 0 && !query.isEmpty() && matches.isEmpty();
        noMatch.setVisibility(noResults ? View.VISIBLE : View.GONE);
        pager.setVisibility(matches.isEmpty() ? View.GONE : View.VISIBLE);
        updatingPageSeek = true;
        pageSeek.setMax(Math.max(0, pageCount - 1));
        pageSeek.setProgress(page - 1);
        pageSeek.setEnabled(pageCount > 1);
        pageSeek.setVisibility(pageCount > 1 ? View.VISIBLE : View.GONE);
        updatingPageSeek = false;
        if (!matches.isEmpty()) {
            visibleHint.setVisibility(View.VISIBLE);
            visibleHint.setText((query.isEmpty() ? "共 " : "匹配 ") + matches.size()
                    + " 条 · 第 " + page + "/" + pageCount + " 页");
            pageLabel.setText("第 " + page + " / " + pageCount + " 页");
        } else {
            visibleHint.setVisibility(View.GONE);
        }
        for (int i = start; i < end; i++) {
            addRow(matches.get(i));
        }
    }

    private boolean matches(VocabularyEntry entry, String query) {
        if (store.displayName(entry).toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        if (entry.text != null && entry.text.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String phonetic : entry.phonetics) {
            if (phonetic.toLowerCase(Locale.ROOT).contains(query)) return true;
        }
        if (entry.phonetic != null && entry.phonetic.toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (String code : entry.codes) {
            if (code.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        for (String meaning : entry.meanings) {
            if (meaning.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
        }
        for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
            PosEntry pos = posStore.findById(group.posId);
            if (pos != null && (pos.name.toLowerCase(Locale.ROOT).contains(query)
                    || pos.code.toLowerCase(Locale.ROOT).contains(query))) {
                return true;
            }
        }
        return false;
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private void addRow(VocabularyEntry entry) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_vocabulary, list, false);
        TextView name = row.findViewById(R.id.vocabulary_name);
        ForgeBadge type = row.findViewById(R.id.vocabulary_type);
        TextView codes = row.findViewById(R.id.vocabulary_codes);
        TextView meanings = row.findViewById(R.id.vocabulary_meanings);
        TextView phonetic = row.findViewById(R.id.vocabulary_phonetic);
        LinearLayout posBadges = row.findViewById(R.id.vocabulary_pos_badges);
        ForgeButton more = row.findViewById(R.id.vocabulary_more);

        name.setText(store.displayName(entry));
        applyTypeface(name);
        type.setText(VocabularyEntry.typeLabel(entry.type));
        codes.setText("编码：" + VocabularyStore.joinSummary(entry.codes));
        phonetic.setText("音标：" + phoneticText(entry));
        phonetic.setVisibility(phoneticText(entry).isEmpty()
                ? View.GONE : View.VISIBLE);
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
        row.setOnClickListener(v -> openEditor(entry.id));
        more.setOnClickListener(v -> showEntryMenu(more, entry));
        list.addView(row);
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

    private void reloadTypeface() {
        if (projectDirectory == null) {
            return;
        }
        vocabTypeface = new FontStore(projectDirectory).activeTypeface();
    }

    private void applyTypeface(TextView view) {
        if (view == null || vocabTypeface == null) {
            return;
        }
        Typeface base = view.getTypeface();
        int style = base == null ? Typeface.NORMAL : base.getStyle();
        view.setTypeface(Typeface.create(vocabTypeface, style));
    }

    private void showEntryMenu(View anchor, VocabularyEntry entry) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(store.displayName(entry));
        menu.addItem("编辑", () -> openEditor(entry.id));
        menu.addDivider();
        SpannableString delete = new SpannableString("删除");
        delete.setSpan(new ForegroundColorSpan(
                        ContextCompat.getColor(this, R.color.forge_bad)),
                0, delete.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        menu.addItem(delete, () -> confirmDelete(entry));
        menu.show(anchor);
    }

    private void confirmDelete(VocabularyEntry entry) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除词汇");
        dialog.setBodyText("确定删除「" + store.displayName(entry) + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            String oldAudio = entry.audio;
            String oldImage = entry.image;
            store.entries.remove(entry);
            if (!store.save()) {
                toast("删除失败：无法写入词汇表");
                store.load();
                render();
                return;
            }
            MediaFiles.deleteQuietly(
                    MediaFiles.resolveProjectFile(projectDirectory, oldAudio));
            MediaFiles.deleteQuietly(
                    MediaFiles.resolveProjectFile(projectDirectory, oldImage));
            render();
            toast("已删除「" + store.displayName(entry) + "」");
        });
        dialog.show();
    }

    private void showImportModeDialog(View anchor) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader("导入方式");
        menu.addItem("跳过重复", () -> openLexiconPicker(LexiconImporter.Mode.SKIP));
        menu.addItem("覆盖合并", () -> openLexiconPicker(LexiconImporter.Mode.OVERWRITE));
        menu.addItem("合并释义", () -> openLexiconPicker(LexiconImporter.Mode.MERGE));
        menu.show(anchor);
    }

    private void openLexiconPicker(LexiconImporter.Mode mode) {
        if (lexiconPicker == null) {
            return;
        }
        pendingMode = mode;
        lexiconPicker.launch(new String[]{
                "application/json", "application/x-ndjson", "text/plain", "*/*"
        });
    }

    private void onLexiconPicked(Uri uri) {
        if (uri == null) {
            return;
        }
        String name = displayName(uri);
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jsonl") && !lower.endsWith(".json") && !lower.endsWith(".txt")) {
            toast("请选择 .jsonl/.json/.txt 词表文件");
            return;
        }
        runImport(uri, pendingMode, null);
    }

    private void runImport(Uri uri, LexiconImporter.Mode mode,
                           Map<String, LexiconImporter.DuplicateChoice> duplicateChoices) {
        toast("正在导入词表…");
        ioExecutor.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                LexiconImporter.Summary summary =
                        LexiconImporter.importFrom(input, projectDirectory, mode, duplicateChoices);
                runOnUiThread(() -> {
                    if (summary.needsDuplicateChoice) {
                        showDuplicateGroupDialog(uri, mode, summary, 0,
                                new LinkedHashMap<>());
                        return;
                    }
                    store.load();
                    posStore.load();
                    page = 1;
                    render();
                    showImportSummary(summary);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("导入失败：" + (e.getMessage() == null
                        ? "无法读取词表" : e.getMessage())));
            }
        });
    }

    private void showDuplicateGroupDialog(Uri uri, LexiconImporter.Mode mode,
                                          LexiconImporter.Summary summary, int index,
                                          Map<String, LexiconImporter.DuplicateChoice> choices) {
        if (index >= summary.duplicateGroupList.size()) {
            runImport(uri, mode, choices);
            return;
        }
        LexiconImporter.DuplicateGroup group = summary.duplicateGroupList.get(index);
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("文件内重复词 " + (index + 1) + "/"
                + summary.duplicateGroupList.size() + "：" + group.text);
        dialog.setBodyText("该词共有 " + group.rows + " 条记录。\n\n"
                + "合并释义：把后续重复的释义/音标按词性合并进首条，只生成一条词汇。\n"
                + "保留第一条：丢弃后续重复。\n取消/关闭：终止整次导入，不写入任何数据。");
        dialog.setNegativeButton("保留第一条", (d, which) -> {
            choices.put(group.key, LexiconImporter.DuplicateChoice.KEEP_FIRST);
            showDuplicateGroupDialog(uri, mode, summary, index + 1, choices);
        });
        dialog.setPositiveButton("合并释义", (d, which) -> {
            choices.put(group.key, LexiconImporter.DuplicateChoice.MERGE);
            showDuplicateGroupDialog(uri, mode, summary, index + 1, choices);
        });
        dialog.show();
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
            // Fall back to the last path segment.
        }
        return uri.getLastPathSegment();
    }

    private void showImportSummary(LexiconImporter.Summary summary) {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("导入词表");
        dialog.setBodyText("新增 " + summary.added + " 条\n"
                + "更新 " + summary.updated + " 条\n"
                + "合并 " + summary.merged + " 条\n"
                + "跳过重复 " + summary.duplicates + " 条\n"
                + "跳过无效 " + summary.invalid + " 条\n"
                + "新增词性 " + summary.newPos + " 个");
        dialog.setPositiveButton("知道了", null);
        dialog.show();
    }

    private void openEditor(String entryId) {
        Intent intent = new Intent(this, VocabularyEditorActivity.class);
        intent.putExtra(VocabularyEditorActivity.EXTRA_FOLDER_NAME, folderName);
        if (entryId != null) {
            intent.putExtra(VocabularyEditorActivity.EXTRA_ENTRY_ID, entryId);
        }
        startActivity(intent);
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