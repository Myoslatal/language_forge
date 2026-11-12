package io.github.myoslatal.languageforge;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeBadge;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Project audio registry: import, code, references, delete. */
public class AudioManagementActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    private static final int REQUEST_IMPORT_AUDIO = 4801;

    private File projectDirectory;
    private AudioStore store;
    private LinearLayout list;
    private LinearLayout emptyState;
    private ForgeBadge count;
    private ForgeTextField search;
    private String query = "";
    private Map<String, List<String>> references = new HashMap<>();
    private Map<String, List<String>> questionReferences = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folderName = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = AudioStore.resolveProjectDirectory(this, folderName);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        store = new AudioStore(projectDirectory);

        setContentView(R.layout.activity_audio_management);
        applyTopInset(findViewById(R.id.audio_topbar));
        applyBottomInset(findViewById(R.id.audio_scroll));
        list = findViewById(R.id.audio_list);
        emptyState = findViewById(R.id.audio_empty);
        count = findViewById(R.id.audio_count);
        search = findViewById(R.id.audio_search);
        ((TextView) findViewById(R.id.audio_project))
                .setText(AudioStore.readProjectName(projectDirectory));

        findViewById(R.id.btn_audio_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_import_audio_asset).setOnClickListener(v -> pickAudio());
        search.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString();
                render();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store != null) {
            store.load();
            render();
        }
    }

    private void reloadReferences() {
        references = new HashMap<>();
        questionReferences = new HashMap<>();
        VocabularyStore vocabulary = new VocabularyStore(projectDirectory);
        for (VocabularyEntry entry : vocabulary.entries) {
            if (entry.audio == null || entry.audio.isEmpty()) {
                continue;
            }
            references.computeIfAbsent(entry.audio, key -> new ArrayList<>())
                    .add(vocabulary.displayName(entry));
        }
        ProjectStructure structure = ProjectStructure.load(projectDirectory);
        for (ProjectStructure.Chapter chapter : structure.chapters) {
            for (ProjectStructure.Unit unit : chapter.units) {
                for (ProjectStructure.Course course : unit.courses) {
                    for (ProjectStructure.Question question : course.questions) {
                        if (question.audio == null || question.audio.isEmpty()) continue;
                        String title = question.sentence == null || question.sentence.isEmpty()
                                ? question.name : question.sentence;
                        questionReferences.computeIfAbsent(question.audio,
                                key -> new ArrayList<>()).add(title);
                    }
                }
            }
        }
    }

    private void render() {
        reloadReferences();
        list.removeAllViews();
        List<Item> items = new ArrayList<>();
        for (ProjectAudio audio : store.audios) {
            items.add(new Item(audio, store.resolveFile(audio.file), true));
        }
        for (File file : store.unregisteredFiles()) {
            items.add(new Item(null, file, false));
        }
        int shown = 0;
        for (Item item : items) {
            if (!matches(item, query)) {
                continue;
            }
            addRow(item);
            shown++;
        }
        count.setText(items.size() + " 条");
        emptyState.setVisibility(shown == 0 ? View.VISIBLE : View.GONE);
    }

    private boolean matches(Item item, String rawQuery) {
        String q = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        return item.name().toLowerCase(Locale.ROOT).contains(q)
                || item.code().toLowerCase(Locale.ROOT).contains(q)
                || item.fileName().toLowerCase(Locale.ROOT).contains(q);
    }

    private void addRow(Item item) {
        View row = LayoutInflater.from(this)
                .inflate(R.layout.item_audio_asset, list, false);
        TextView name = row.findViewById(R.id.audio_name);
        TextView meta = row.findViewById(R.id.audio_meta);
        TextView code = row.findViewById(R.id.audio_code);
        TextView refs = row.findViewById(R.id.audio_refs);
        ForgeButton more = row.findViewById(R.id.audio_more);

        name.setText(item.name());
        String prefix = item.registered ? "" : "未登记/来自词汇 · ";
        meta.setText(prefix + item.fileName() + " · " + formatDuration(item.duration())
                + " · " + formatSize(item.size()));
        code.setText(item.code().isEmpty() ? "未设置编码" : "编码：" + item.code());

        List<String> names = references.get(item.relativePath());
        List<String> titles = questionReferences.get(item.relativePath());
        int vocabCount = names == null ? 0 : names.size();
        int questionCount = titles == null ? 0 : titles.size();
        if (vocabCount == 0 && questionCount == 0) {
            refs.setText("未被引用");
        } else {
            List<String> all = new ArrayList<>();
            if (names != null) all.addAll(names);
            if (titles != null) all.addAll(titles);
            refs.setText("被 " + vocabCount + " 个词汇、" + questionCount
                    + " 道题目引用：" + String.join("、", all));
        }
        row.setOnClickListener(v -> showEditCode(item));
        more.setOnClickListener(v -> showMenu(more, item));
        list.addView(row);
    }

    private void showMenu(View anchor, Item item) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader(item.name());
        menu.addItem("设置编码", () -> showEditCode(item));
        menu.addDivider();
        menu.addItem("删除", () -> confirmDelete(item));
        menu.show(anchor);
    }

    private void showEditCode(Item item) {
        if (item.file == null || !item.file.isFile()) {
            toast("音频文件不存在");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("设置编码");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        TextView info = new TextView(this);
        info.setText(item.fileName() + "\n时长：" + formatDuration(item.duration())
                + " · 大小：" + formatSize(item.size()));
        info.setTextSize(12);
        info.setTextColor(0xFF6B7280);
        body.addView(info);

        ForgeTextField codeField = new ForgeTextField(this);
        codeField.setLabel("音频编码");
        codeField.setHint("可留空");
        codeField.setText(item.code());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        body.addView(codeField, lp);

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("保存", (d, which) -> {
            ProjectAudio target = item.audio;
            if (target == null) {
                target = store.registerFile(item.file);
            }
            if (target == null) {
                toast("音频登记失败");
                return;
            }
            if (!store.setCode(target, textOf(codeField))) {
                toast("保存编码失败");
                return;
            }
            render();
            toast("编码已保存");
        });
        dialog.show();
    }

    private void confirmDelete(Item item) {
        List<String> names = references.get(item.relativePath());
        List<String> titles = questionReferences.get(item.relativePath());
        int vocabCount = names == null ? 0 : names.size();
        int questionCount = titles == null ? 0 : titles.size();
        if (vocabCount > 0 || questionCount > 0) {
            toast("被 " + vocabCount + " 个词汇、" + questionCount
                    + " 道题目引用，请先移除引用");
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("删除音频");
        dialog.setBodyText("确定删除「" + item.name() + "」吗？");
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("删除", (d, which) -> {
            if (item.audio != null) {
                if (!store.deleteAudio(item.audio)) {
                    toast("删除失败");
                    return;
                }
            } else if (item.file != null) {
                MediaFiles.deleteQuietly(item.file);
            }
            render();
            toast("已删除音频");
        });
        dialog.show();
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_IMPORT_AUDIO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT_AUDIO || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        String displayName = readDisplayName(uri);
        String extension = AudioStore.extensionOf(displayName);
        if (!AudioStore.isSupportedExtension(extension)) {
            toast("仅支持 M4A/MP3/WAV/OGG/AAC/FLAC");
            return;
        }
        File cache = null;
        try {
            cache = MediaFiles.copyUriToCache(this, getContentResolver(), uri);
            ProjectAudio audio = store.importAudio(cache, displayName);
            if (audio == null) {
                toast("音频无法读取，已拒绝");
            } else {
                render();
                toast("已导入音频：" + audio.name);
            }
        } catch (Exception e) {
            toast("音频导入失败");
        } finally {
            MediaFiles.deleteQuietly(cache);
        }
    }

    private String readDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) {
                    return name.trim();
                }
            }
        } catch (Exception ignored) {
        }
        String path = uri.getLastPathSegment();
        return path == null ? "audio.m4a" : path;
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private String formatDuration(long ms) {
        if (ms <= 0) {
            return "0.0s";
        }
        long seconds = ms / 1000;
        long minutes = seconds / 60;
        if (minutes > 0) {
            return String.format(Locale.ROOT, "%d:%02d", minutes, seconds % 60);
        }
        return String.format(Locale.ROOT, "%.1fs", ms / 1000f);
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f KB", bytes / 1024f);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024f * 1024f));
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static final class Item {
        final ProjectAudio audio;
        final File file;
        final boolean registered;

        Item(ProjectAudio audio, File file, boolean registered) {
            this.audio = audio;
            this.file = file;
            this.registered = registered;
        }

        String name() {
            if (audio != null) {
                return audio.name;
            }
            return file == null ? "音频" : file.getName();
        }

        String code() {
            return audio == null ? "" : audio.code;
        }

        String relativePath() {
            if (audio != null) {
                return audio.file;
            }
            return file == null ? "" : "resources/audio/" + file.getName();
        }

        String fileName() {
            return file == null ? "" : file.getName();
        }

        long duration() {
            if (audio != null && audio.duration > 0) {
                return audio.duration;
            }
            return file == null ? 0L : MediaFiles.audioDuration(file);
        }

        long size() {
            return file == null ? 0L : file.length();
        }
    }
}
