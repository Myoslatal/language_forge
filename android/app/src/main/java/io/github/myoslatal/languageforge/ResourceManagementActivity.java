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

    private String folderName;
    private TextView vocabularySummary;
    private TextView fontSummary;
    private TextView imageSummary;
    private TextView audioSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resource_management);
        applyTopInset(findViewById(R.id.resource_topbar));
        applyBottomInset(findViewById(R.id.resource_scroll));

        findViewById(R.id.btn_resource_back).setOnClickListener(v -> finish());
        vocabularySummary = findViewById(R.id.vocabulary_summary);
        fontSummary = findViewById(R.id.font_summary);
        imageSummary = findViewById(R.id.image_summary);
        audioSummary = findViewById(R.id.audio_summary);
        folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        findViewById(R.id.vocabulary_entry).setOnClickListener(v -> openVocabulary());
        findViewById(R.id.font_entry).setOnClickListener(v -> openFonts());
        findViewById(R.id.image_entry).setOnClickListener(v -> openImages());
        findViewById(R.id.audio_entry).setOnClickListener(v -> openAudios());

        String chapterName = getIntent().getStringExtra(EXTRA_CHAPTER_NAME);
        String unitName = getIntent().getStringExtra(EXTRA_UNIT_NAME);
        String courseName = getIntent().getStringExtra(EXTRA_COURSE_NAME);
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, chapterName);
        addIfPresent(parts, unitName);
        addIfPresent(parts, courseName);

        TextView context = findViewById(R.id.resource_context);
        if (parts.isEmpty()) {
            context.setVisibility(View.GONE);
        } else {
            context.setText(String.join(" · ", parts));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateVocabularyCount();
        updateFontSummary();
        updateImageSummary();
        updateAudioSummary();
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

    private void updateAudioSummary() {
        if (audioSummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = AudioStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            AudioStore store = new AudioStore(directory);
            count = store.audios.size() + store.unregisteredFiles().size();
        }
        audioSummary.setText(count + " 条音频 · 编码与引用");
    }

    private void openAudios() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开音频管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, AudioManagementActivity.class);
        intent.putExtra(AudioManagementActivity.EXTRA_FOLDER_NAME, folderName);
        startActivity(intent);
    }

    private void updateImageSummary() {
        if (imageSummary == null) {
            return;
        }
        int count = 0;
        java.io.File directory = ImageStore.resolveProjectDirectory(this, folderName);
        if (directory != null) {
            ImageStore store = new ImageStore(directory);
            count = store.images.size() + store.unregisteredFiles().size();
        }
        imageSummary.setText(count + " 张图片 · 编码与引用");
    }

    private void openImages() {
        if (folderName == null || folderName.isEmpty()) {
            toast("无法打开图片管理：项目目录不存在");
            return;
        }
        Intent intent = new Intent(this, ImageManagementActivity.class);
        intent.putExtra(ImageManagementActivity.EXTRA_FOLDER_NAME, folderName);
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