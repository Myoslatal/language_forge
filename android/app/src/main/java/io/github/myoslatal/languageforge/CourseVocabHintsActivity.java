package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Manage per-course vocabulary hint-body overrides. */
public class CourseVocabHintsActivity extends ForgeActivity {
    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_COURSE_ID = "courseId";
    public static final String EXTRA_COURSE_NAME = "courseName";
    private static final int REQUEST_PICK_VOCAB = 4901;

    private File projectDirectory;
    private String folderName;
    private String courseId;
    private String courseName;
    private VocabularyStore vocabulary;
    private CourseVocabHints hints;
    private LinearLayout list;
    private LinearLayout empty;
    private TextView count;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        courseId = getIntent().getStringExtra(EXTRA_COURSE_ID);
        courseName = getIntent().getStringExtra(EXTRA_COURSE_NAME);
        projectDirectory = VocabularyStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null || courseId == null || courseId.trim().isEmpty()) {
            toast("课程目录不存在");
            finish();
            return;
        }
        vocabulary = new VocabularyStore(projectDirectory);
        hints = new CourseVocabHints(projectDirectory, courseId);
        setContentView(R.layout.activity_course_vocab_hints);
        applyTopInset(findViewById(R.id.vocab_hints_topbar));
        applyBottomInset(findViewById(R.id.vocab_hints_scroll));
        list = findViewById(R.id.vocab_hints_list);
        empty = findViewById(R.id.vocab_hints_empty);
        count = findViewById(R.id.vocab_hints_count);
        ((TextView) findViewById(R.id.vocab_hints_context)).setText(
                courseName == null || courseName.trim().isEmpty()
                        ? "课程 · 仅本课程" : courseName.trim() + " · 仅本课程");
        findViewById(R.id.btn_vocab_hints_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_vocab_hint).setOnClickListener(v -> openPicker());
    }

    @Override
    protected void onResume() {
        super.onResume();
        hints = new CourseVocabHints(projectDirectory, courseId);
        render();
    }

    private void openPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, folderName);
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择词汇");
        intent.putExtra(VocabularyPickerActivity.EXTRA_SINGLE_SELECT, true);
        startActivityForResult(intent, REQUEST_PICK_VOCAB);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_PICK_VOCAB || resultCode != RESULT_OK || data == null) return;
        ArrayList<String> ids = data.getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
        if (ids == null || ids.isEmpty()) return;
        openEditor(ids.get(0));
    }

    private void render() {
        if (list == null) return;
        list.removeAllViews();
        List<String> ids = new ArrayList<>(hints.hints.keySet());
        count.setText(ids.size() + " 个词 · 仅本课程复习点词生效");
        empty.setVisibility(ids.isEmpty() ? View.VISIBLE : View.GONE);
        for (String id : ids) {
            VocabularyEntry entry = vocabulary.findById(id);
            String word = entry == null ? "〔词汇已删除〕" : vocabulary.displayName(entry);
            String body = hints.get(id);
            list.addView(buildRow(id, word, body));
        }
    }

    private View buildRow(String id, String word, String body) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundResource(R.drawable.forge_bg_project_card);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(8);
        row.setLayoutParams(params);

        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setOrientation(LinearLayout.HORIZONTAL);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this);
        name.setText(word);
        name.setTextSize(16f);
        name.setTextColor(getColor(R.color.forge_text));
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleLine.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView badge = new TextView(this);
        badge.setText("本课程自定义");
        badge.setTextSize(10f);
        badge.setTextColor(getColor(R.color.forge_accent));
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(8), dp(3), dp(8), dp(3));
        badge.setBackgroundResource(R.drawable.forge_bg_project_chip);
        titleLine.addView(badge);
        row.addView(titleLine);

        TextView summary = new TextView(this);
        summary.setText(body == null ? "" : body.replace('\n', ' '));
        summary.setTextSize(12f);
        summary.setTextColor(getColor(R.color.forge_muted));
        summary.setMaxLines(2);
        summary.setEllipsize(android.text.TextUtils.TruncateAt.END);
        summary.setPadding(0, dp(5), 0, 0);
        row.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);
        actions.setPadding(0, dp(6), 0, 0);
        ForgeButton edit = new ForgeButton(this);
        edit.setVariant(ForgeButton.VARIANT_GHOST);
        edit.setText("编辑");
        edit.setTextSize(12f);
        edit.setOnClickListener(v -> openEditor(id));
        actions.addView(edit, new LinearLayout.LayoutParams(dp(76), dp(32)));
        ForgeButton clear = new ForgeButton(this);
        clear.setVariant(ForgeButton.VARIANT_GHOST);
        clear.setText("清除");
        clear.setTextSize(12f);
        clear.setOnClickListener(v -> confirmDelete(id, word));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(dp(76), dp(32));
        clearParams.setMarginStart(dp(6));
        actions.addView(clear, clearParams);
        row.addView(actions);
        return row;
    }

    private void confirmDelete(String id, String word) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("清除提示覆盖")
                .setMessage("清除「" + word + "」在本课程的自定义提示？清除后恢复全局提示。")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (dialog, which) -> {
                    hints.remove(id);
                    if (!hints.save()) toast("保存失败");
                    else { toast("已清除，恢复全局提示"); render(); }
                })
                .show();
    }

    private void openEditor(String vocabId) {
        VocabularyEntry entry = vocabulary.findById(vocabId);
        if (entry == null) {
            toast("词汇不存在");
            return;
        }
        String word = vocabulary.displayName(entry);
        String existing = hints.get(vocabId);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(4));
        TextView title = new TextView(this);
        title.setText(word);
        title.setTextSize(18f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.forge_text));
        content.addView(title);
        TextView note = new TextView(this);
        note.setText("标题和图片/音频保持全局词汇；正文只在本课程复习点词时替换全局音标/词性/释义说明。");
        note.setTextSize(12f);
        note.setTextColor(getColor(R.color.forge_muted));
        note.setPadding(0, dp(4), 0, dp(8));
        content.addView(note);
        ForgeTextField field = new ForgeTextField(this);
        field.setMultiline(true);
        field.setLabel("自定义提示正文");
        field.setHelper("支持多行；清除后恢复现有全局提示。");
        field.setText(existing == null ? "" : existing);
        content.addView(field);

        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this)
                .setTitle("课程词汇提示")
                .setView(content)
                .setPositiveButton("保存", null)
                .setNegativeButton("取消", null);
        if (existing != null && !existing.trim().isEmpty()) {
            builder.setNeutralButton("清除覆盖", null);
        }
        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = field.getText() == null ? "" : field.getText().toString().trim();
                if (text.isEmpty()) {
                    toast("请输入提示正文");
                    return;
                }
                hints.put(vocabId, text);
                if (!hints.save()) {
                    toast("保存失败");
                    return;
                }
                toast("已保存本课程提示");
                dialog.dismiss();
                render();
            });
            Button clear = dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL);
            if (clear != null) clear.setOnClickListener(v -> {
                hints.remove(vocabId);
                if (!hints.save()) toast("保存失败");
                else { toast("已清除，恢复全局提示"); render(); }
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void toast(String message) {
        ForgeToast.show(this, message, 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
