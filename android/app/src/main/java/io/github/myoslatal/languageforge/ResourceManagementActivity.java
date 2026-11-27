package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Placeholder resource-management page for one course. */
public class ResourceManagementActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_CHAPTER_NAME = "chapterName";
    public static final String EXTRA_UNIT_NAME = "unitName";
    public static final String EXTRA_COURSE_NAME = "courseName";
    public static final String EXTRA_COURSE_ID = "courseId";

    private String folderName;
    private String courseId;
    private String courseName;
    private boolean courseMode;
    private TextView vocabularySummary;
    private TextView posSummary;
    private TextView fontSummary;
    private TextView imageSummary;
    private TextView audioSummary;
    private TextView vocabHintSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_management);
        applyTopInset(findViewById(R.id.resource_topbar));
        applyBottomInset(findViewById(R.id.resource_scroll));

        findViewById(R.id.btn_resource_back).setOnClickListener(v -> finish());
        vocabularySummary = findViewById(R.id.vocabulary_summary);
        posSummary = findViewById(R.id.pos_summary);
        fontSummary = findViewById(R.id.font_summary);
        imageSummary = findViewById(R.id.image_summary);
        audioSummary = findViewById(R.id.audio_summary);
        vocabHintSummary = findViewById(R.id.vocab_hint_summary);
        folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        courseId = getIntent().getStringExtra(EXTRA_COURSE_ID);
        courseName = getIntent().getStringExtra(EXTRA_COURSE_NAME);
        courseMode = courseId != null && !courseId.trim().isEmpty();
        findViewById(R.id.vocabulary_entry).setOnClickListener(v -> openVocabulary());
        findViewById(R.id.pos_entry).setOnClickListener(v -> openPos());
        findViewById(R.id.font_entry).setOnClickListener(v -> openFonts());
        findViewById(R.id.image_entry).setOnClickListener(v -> openImages());
        findViewById(R.id.audio_entry).setOnClickListener(v -> openAudios());
        findViewById(R.id.vocab_hint_entry).setOnClickListener(v -> openVocabHints());

        String chapterName = getIntent().getStringExtra(EXTRA_CHAPTER_NAME);
        String unitName = getIntent().getStringExtra(EXTRA_UNIT_NAME);
        String courseName = getIntent().getStringExtra(EXTRA_COURSE_NAME);
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, chapterName);
        addIfPresent(parts, unitName);
        addIfPresent(parts, courseName);

        TextView context = findViewById(R.id.resource_context);
        TextView hint = findViewById(R.id.resource_hint);
        if (courseMode) {
            context.setText("课程资源（仅本课程题目） · " + String.join(" · ", parts));
            hint.setText("只显示本课程题目引用的图片与音频；词汇、词性、字体和词汇音频/图片是项目资源，请从项目结构进入「项目资源」。");
            findViewById(R.id.vocabulary_entry).setVisibility(View.GONE);
            findViewById(R.id.pos_entry).setVisibility(View.GONE);
            findViewById(R.id.font_entry).setVisibility(View.GONE);
            findViewById(R.id.vocab_hint_entry).setVisibility(View.VISIBLE);
            ((TextView) findViewById(R.id.audio_title)).setText("课程题目音频");
            ((TextView) findViewById(R.id.image_title)).setText("课程题目图片");
        } else {
            context.setText("项目资源（全局） · 所有课程共享");
            hint.setText("项目级共享词汇、词性、字体与词汇音频/图片；题目图片/音频按课程隔离。");
            ((TextView) findViewById(R.id.audio_title)).setText("词汇音频（全局）");
            ((TextView) findViewById(R.id.image_title)).setText("词汇图片（全局）");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVocabularyCount();
        updatePosCount();
        updateFontSummary();
        updateImageSummary();
        updateAudioSummary();
        updateVocabHintSummary();
    }

    private void updateVocabularyCount() {
        if (vocabularySummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = VocabularyStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            count = new VocabularyStore(directory).entries.size();
        }
        vocabularySummary.setText(count + " 个词 · 管理词汇表");
    }

    private void updatePosCount() {
        if (posSummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = PosStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            count = new PosStore(directory).entries.size();
        }
        posSummary.setText(count + " 个词性 · 名称、描述与编码");
    }

    private void updateVocabHintSummary() {
        if (vocabHintSummary == null || !courseMode) return;
        int count = 0;
        java.io.File directory = VocabularyStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            count = CourseVocabHints.load(directory, courseId).size();
        }
        vocabHintSummary.setText(count + " 个词 · 仅本课程复习提示");
    }

    private void openVocabHints() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开词汇提示：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, CourseVocabHintsActivity.class);
        intent.putExtra(CourseVocabHintsActivity.EXTRA_FOLDER_NAME, folderName);
        intent.putExtra(CourseVocabHintsActivity.EXTRA_COURSE_ID, courseId);
        intent.putExtra(CourseVocabHintsActivity.EXTRA_COURSE_NAME, courseName);
        startActivity(intent);
    }

    private void openPos() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开词性管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, PosManagementActivity.class);
        intent.putExtra(PosManagementActivity.EXTRA_FOLDER_NAME, folderName);
        startActivity(intent);
    }

    private void updateAudioSummary() {
        if (audioSummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = AudioStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            AudioStore store = courseMode ? new AudioStore(directory, courseId)
                    : new AudioStore(directory);
            count = store.audios.size() + store.unregisteredFiles().size();
        }
        audioSummary.setText(courseMode
                ? count + " 条题目音频 · 仅本课程"
                : count + " 条词汇音频 · 项目全局");
    }

    private void openAudios() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开音频管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, AudioManagementActivity.class);
        intent.putExtra(AudioManagementActivity.EXTRA_FOLDER_NAME, folderName);
        if (courseMode) {
            intent.putExtra(AudioManagementActivity.EXTRA_COURSE_ID, courseId);
            intent.putExtra(AudioManagementActivity.EXTRA_COURSE_NAME, courseName);
        }
        startActivity(intent);
    }

    private void updateImageSummary() {
        if (imageSummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = ImageStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            ImageStore store = courseMode ? new ImageStore(directory, courseId)
                    : new ImageStore(directory);
            count = store.images.size() + store.unregisteredFiles().size();
        }
        imageSummary.setText(courseMode
                ? count + " 张题目图片 · 仅本课程"
                : count + " 张词汇图片 · 项目全局");
    }

    private void openImages() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开图片管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, ImageManagementActivity.class);
        intent.putExtra(ImageManagementActivity.EXTRA_FOLDER_NAME, folderName);
        if (courseMode) {
            intent.putExtra(ImageManagementActivity.EXTRA_COURSE_ID, courseId);
            intent.putExtra(ImageManagementActivity.EXTRA_COURSE_NAME, courseName);
        }
        startActivity(intent);
    }

    private void updateFontSummary() {
        if (fontSummary == null) {
            return;
        }
        int count = 0;
        String active = "系统默认";
        java.io.File directory = FontStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            FontStore store = new FontStore(directory);
            count = store.fonts.size();
            active = store.activeFontName();
        }
        fontSummary.setText(count + " 个字体 · 当前：" + active);
    }

    private void openFonts() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开字体管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, FontManagementActivity.class);
        intent.putExtra(FontManagementActivity.EXTRA_FOLDER_NAME, folderName);
        startActivity(intent);
    }

    private void openVocabulary() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开词汇表：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, VocabularyListActivity.class);
        intent.putExtra(VocabularyListActivity.EXTRA_FOLDER_NAME, folderName);
        startActivity(intent);
    }

    private void addIfPresent(List<String> parts, String value) {
        if (value != null && !value.trim().isEmpty()) {
            parts.add(value.trim());
        }
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }
}