package io.github.myoslatal.languageforge;

import android.graphics.Color;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.UnderlineSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only learning flow: course list -> question list -> sentence hints. */
public class LearningActivity extends ForgeActivity {
    public static final String EXTRA_FOLDER_NAME = "folderName";

    private File projectDirectory;
    private ProjectStructure structure;
    private VocabularyStore vocabulary;
    private PosStore posStore;
    private LinearLayout content;
    private LinearLayout topbarView;
    private ScrollView scrollView;
    private TextView titleView;
    private TextView subtitleView;
    private ProjectStructure.Course course;
    private ProjectStructure.Question question;
    private MediaPlayer player;
    private Handler handler;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folder = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        projectDirectory = VocabularyStore.resolveProjectDirectory(this, folder);
        if (projectDirectory == null) {
            toast("项目目录不存在");
            finish();
            return;
        }
        structure = ProjectStructure.load(projectDirectory);
        vocabulary = new VocabularyStore(projectDirectory);
        vocabulary.load();
        posStore = new PosStore(projectDirectory);
        handler = new Handler(Looper.getMainLooper());
        setContentView(buildRoot());
        applyTopInset(topbarView);
        applyBottomInset(scrollView);
        showCourses();
    }

    private View buildRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getColor(R.color.forge_bg));
        LinearLayout topbar = new LinearLayout(this);
        topbarView = topbar;
        topbar.setOrientation(LinearLayout.HORIZONTAL);
        topbar.setGravity(Gravity.CENTER_VERTICAL);
        topbar.setPadding(dp(12), dp(12), dp(12), dp(8));
        Button back = new Button(this);
        back.setText("←");
        back.setOnClickListener(v -> goBack());
        topbar.addView(back, new LinearLayout.LayoutParams(dp(48), dp(44)));
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titleView = new TextView(this);
        titleView.setTextSize(20f);
        titleView.setTextColor(getColor(R.color.forge_text));
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        subtitleView = new TextView(this);
        subtitleView.setTextSize(12f);
        subtitleView.setTextColor(getColor(R.color.forge_muted));
        titles.addView(titleView);
        titles.addView(subtitleView);
        topbar.addView(titles, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(topbar);
        ScrollView scroll = new ScrollView(this);
        scrollView = scroll;
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return root;
    }

    private void showCourses() {
        stopAudio();
        course = null;
        question = null;
        titleView.setText("学习");
        subtitleView.setText("选择课程");
        content.removeAllViews();
        for (ProjectStructure.Chapter chapter : structure.chapters) {
            for (ProjectStructure.Unit unit : chapter.units) {
                for (ProjectStructure.Course item : unit.courses) {
                    Button row = new Button(this);
                    row.setAllCaps(false);
                    row.setText(item.name + "\n" + chapter.name + " · " + unit.name
                            + "  ·  " + item.questions.size() + " 道题目");
                    row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                    row.setOnClickListener(v -> showQuestions(item));
                    content.addView(row, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
                }
            }
        }
        if (content.getChildCount() == 0) {
            content.addView(text("还没有课程", 16, R.color.forge_muted));
        }
    }

    private void showQuestions(ProjectStructure.Course selected) {
        stopAudio();
        course = selected;
        question = null;
        titleView.setText(selected.name);
        subtitleView.setText("选择题目");
        content.removeAllViews();
        for (ProjectStructure.Question item : selected.questions) {
            Button row = new Button(this);
            row.setAllCaps(false);
            String sentence = item.sentence == null || item.sentence.isEmpty() ? item.name : item.sentence;
            row.setText(sentence + "\n选择正确翻译" + (item.hintsEnabled ? " · 学习提示" : " · 无提示"));
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setOnClickListener(v -> showQuestion(selected, item));
            content.addView(row, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(72)));
        }
        if (selected.questions.isEmpty()) {
            content.addView(text("该课程还没有题目", 16, R.color.forge_muted));
        }
    }

    private void showQuestion(ProjectStructure.Course selected, ProjectStructure.Question item) {
        stopAudio();
        course = selected;
        question = item;
        titleView.setText(item.name == null || item.name.isEmpty() ? "题目" : item.name);
        subtitleView.setText(selected.name + " · 选择正确翻译");
        content.removeAllViews();

        TextView sentence = new TextView(this);
        sentence.setTextSize(22f);
        sentence.setTextColor(getColor(R.color.forge_text));
        sentence.setLineSpacing(dp(6), 1f);
        CharSequence rendered = renderSentence(item, sentence);
        sentence.setText(rendered);
        sentence.setMovementMethod(LinkMovementMethod.getInstance());
        content.addView(sentence);

        content.addView(text(item.hintsEnabled ? "点击带下划线的词查看提示" : "本题未启用学习提示",
                12, R.color.forge_muted));

        if (!item.audio.isEmpty()) {
            Button playQuestion = new Button(this);
            playQuestion.setAllCaps(false);
            playQuestion.setText("播放整句音频");
            playQuestion.setOnClickListener(v -> play(item.audio, item.audioStart, item.audioEnd));
            content.addView(playQuestion);
        }

        content.addView(text("选项（只读）", 14, R.color.forge_muted));
        for (int i = 0; i < item.options.size(); i++) {
            Button option = new Button(this);
            option.setAllCaps(false);
            option.setText((i + 1) + ". " + item.options.get(i));
            option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            final boolean[] chosen = {false};
            option.setOnClickListener(v -> {
                chosen[0] = !chosen[0];
                option.setBackgroundColor(chosen[0]
                        ? getColor(R.color.forge_accent_soft) : Color.TRANSPARENT);
            });
            content.addView(option, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
    }

    private CharSequence renderSentence(ProjectStructure.Question item, TextView view) {
        SpannableString span = new SpannableString("");
        StringBuilder builder = new StringBuilder();
        List<int[]> ranges = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (ProjectStructure.Question.Segment segment : item.segments) {
            if (segment.word) {
                VocabularyEntry entry = findEntry(segment.vocabId);
                String name = entry == null ? "" : vocabulary.displayName(entry);
                if (name.isEmpty()) name = "〔词汇已删除〕";
                if (builder.length() > 0) builder.append(' ');
                int start = builder.length();
                builder.append(name);
                ranges.add(new int[]{start, builder.length()});
                ids.add(segment.vocabId);
            } else {
                builder.append(segment.text);
            }
        }
        String rendered = builder.toString();
        if (!item.hintsEnabled) return rendered;
        SpannableString result = new SpannableString(rendered);
        for (int i = 0; i < ranges.size(); i++) {
            int start = ranges.get(i)[0];
            int end = ranges.get(i)[1];
            final String vocabId = ids.get(i);
            if (findEntry(vocabId) == null) continue;
            result.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            result.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) { showHint(vocabId); }
                @Override public void updateDrawState(TextPaint ds) {
                    ds.setColor(getColor(R.color.forge_accent));
                    ds.setUnderlineText(true);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return result;
    }

    private void showHint(String vocabId) {
        VocabularyEntry entry = findEntry(vocabId);
        if (entry == null) {
            toast("词汇已删除");
            return;
        }
        StringBuilder text = new StringBuilder();
        if (!entry.phonetics.isEmpty()) {
            text.append("音标：").append(String.join(" / ", entry.phonetics)).append("\n\n");
        } else if (!entry.phonetic.isEmpty()) {
            text.append("音标：").append(entry.phonetic).append("\n\n");
        }
        if (entry.posMeanings.isEmpty()) {
            text.append("释义：").append(String.join("、", entry.meanings.isEmpty()
                    ? List.of("暂无释义") : entry.meanings));
        } else {
            for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
                PosEntry pos = posStore.findById(group.posId);
                String name = pos == null ? "未标注词性" : pos.name;
                String code = pos == null || pos.code.isEmpty() ? "" : "（" + pos.code + "）";
                text.append(name).append(code).append("：")
                        .append(String.join("、", group.meanings)).append("\n");
            }
        }
        text.append("\n\n").append(entry.audio.isEmpty()
                ? "示例发音：暂无" : "示例发音：已自动播放");
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle(vocabulary.displayName(entry));
        dialog.setBodyText(text.toString().trim());
        dialog.setNegativeButton("关闭", null);
        dialog.setPositiveButton("再听一次", (d, w) -> play(entry.audio, entry.audioStart, entry.audioEnd));
        dialog.show();
        play(entry.audio, entry.audioStart, entry.audioEnd);
    }

    private VocabularyEntry findEntry(String id) {
        for (VocabularyEntry entry : vocabulary.entries) {
            if (entry.id.equals(id)) return entry;
        }
        return null;
    }

    private void play(String relative, long start, long end) {
        stopAudio();
        if (relative == null || relative.isEmpty()) {
            toast("暂无示例发音");
            return;
        }
        File file = MediaFiles.resolveProjectFile(projectDirectory, relative);
        if (file == null || !file.isFile()) {
            toast("音频文件缺失");
            return;
        }
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            long safeStart = Math.max(0, start);
            long length = Math.max(100, end - start);
            if (safeStart > 0) player.seekTo((int) safeStart);
            player.start();
            handler.postDelayed(this::stopAudio, length);
        } catch (Exception error) {
            stopAudio();
            toast("音频播放失败");
        }
    }

    private void stopAudio() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void goBack() {
        if (question != null) {
            showQuestions(course);
        } else if (course != null) {
            showCourses();
        } else {
            finish();
        }
    }

    @Override public void onBackPressed() {
        if (question != null || course != null) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override protected void onPause() {
        super.onPause();
        stopAudio();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopAudio();
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        view.setPadding(0, dp(10), 0, dp(6));
        return view;
    }

    private void toast(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}