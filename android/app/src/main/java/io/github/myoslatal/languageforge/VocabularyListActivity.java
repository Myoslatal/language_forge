package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project-level vocabulary list. */
public class VocabularyListActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_ENTRY_ID = "entryId";

    private String folderName;
    private File projectDirectory;
    private VocabularyStore store;
    private Typeface vocabTypeface;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;

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
        reloadTypeface();

        setContentView(R.layout.activity_vocabulary_list);
        applyTopInset(findViewById(R.id.vocabulary_topbar));
        applyBottomInset(findViewById(R.id.vocabulary_scroll));
        bindViews();

        ((TextView) findViewById(R.id.vocabulary_project)).setText(
                VocabularyStore.readProjectName(projectDirectory));
        findViewById(R.id.btn_vocabulary_back).setOnClickListener(v -> finish());
        View.OnClickListener newEntry = v -> openEditor(null);
        findViewById(R.id.btn_new_vocabulary).setOnClickListener(newEntry);
        findViewById(R.id.btn_empty_vocabulary).setOnClickListener(newEntry);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            store.load();
            reloadTypeface();
            render();
        }
    }

    private void bindViews() {
        list = findViewById(R.id.vocabulary_list);
        emptyState = findViewById(R.id.vocabulary_empty);
        count = findViewById(R.id.vocabulary_count);
    }

    private void render() {
        list.removeAllViews();
        count.setText(String.valueOf(store.entries.size()));
        emptyState.setVisibility(store.entries.isEmpty() ? View.VISIBLE : View.GONE);
        for (VocabularyEntry entry : store.entries) {
            addRow(entry);
        }
    }

    private void addRow(VocabularyEntry entry) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_vocabulary, list, false);
        TextView name = row.findViewById(R.id.vocabulary_name);
        ForgeBadge type = row.findViewById(R.id.vocabulary_type);
        TextView codes = row.findViewById(R.id.vocabulary_codes);
        TextView meanings = row.findViewById(R.id.vocabulary_meanings);
        ForgeButton more = row.findViewById(R.id.vocabulary_more);

        name.setText(store.displayName(entry));
        applyTypeface(name);
        type.setText(VocabularyEntry.typeLabel(entry.type));
        codes.setText("编码：" + VocabularyStore.joinSummary(entry.codes));
        meanings.setText("释义：" + VocabularyStore.joinSummary(entry.meanings));
        row.setOnClickListener(v -> openEditor(entry.id));
        more.setOnClickListener(v -> showEntryMenu(more, entry));
        list.addView(row);
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
}