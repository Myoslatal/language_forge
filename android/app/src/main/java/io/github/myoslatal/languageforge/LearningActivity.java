package io.github.myoslatal.languageforge;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeButton;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
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
    private PopupWindow hintPopup;
    private LinearLayout bottomBar;
    private ForgeButton confirmButton;
    private EditText readingInput;
    private int selectedOption = -1;
    private boolean answered;
    private int lastTouchX;
    private int lastTouchY;
    private String openVocabId;
    private TextView sentenceView;
    private final List<ProjectStructure.Question.Block> arrangePool = new ArrayList<>();
    private final List<ProjectStructure.Question.Block> arrangeSelected = new ArrayList<>();
    private final List<Integer> matchLeftOrder = new ArrayList<>();
    private final List<Integer> matchRightOrder = new ArrayList<>();
    private final List<Integer> matchMatched = new ArrayList<>();
    private int matchLeft = -1;
    private int matchRight = -1;
    private EditText typeInput;
    private int listenSelected = -1;
    private String listenAutoId = "";
    private ForgeButton listenPlayButton;
    private boolean listenPlaying;
    private TextView typeNearView;

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
        applyBottomInset(bottomBar);
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
        ForgeButton back = new ForgeButton(this);
        back.setVariant(ForgeButton.VARIANT_GHOST);
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
        bottomBar = new LinearLayout(this);
        bottomBar.setPadding(dp(16), dp(8), dp(16), dp(8));
        bottomBar.setOrientation(LinearLayout.VERTICAL);
        confirmButton = new ForgeButton(this);
        confirmButton.setVariant(ForgeButton.VARIANT_PRIMARY);
        confirmButton.setText("确认答案");
        confirmButton.setTextSize(15f);
        confirmButton.setVisibility(View.GONE);
        confirmButton.setOnClickListener(v -> {
            if (answered) nextQuestion(); else confirmAnswer();
        });
        bottomBar.addView(confirmButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        root.addView(bottomBar);
        return root;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (hintPopup != null && sentenceView != null && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            int[] location = new int[2];
            sentenceView.getLocationOnScreen(location);
            float y = event.getY();
            if (y < location[1] - dp(8) || y > location[1] + sentenceView.getHeight() + dp(8)) {
                dismissHint();
            }
        }
        return super.dispatchTouchEvent(event);
    }

    private void showCourses() {
        dismissHint();
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
        dismissHint();
        course = selected;
        question = null;
        confirmButton.setVisibility(View.GONE);
        confirmButton.setText("确认答案");
        titleView.setText(selected.name);
        subtitleView.setText("选择题目");
        content.removeAllViews();
        for (ProjectStructure.Question item : selected.questions) {
            Button row = new Button(this);
            row.setAllCaps(false);
            String sentence = item.sentence == null || item.sentence.isEmpty() ? item.name : item.sentence;
            boolean arrange = ProjectStructure.Question.TYPE_ARRANGE.equals(item.type);
            boolean match = ProjectStructure.Question.TYPE_MATCH.equals(item.type);
            boolean typing = ProjectStructure.Question.TYPE_TYPE.equals(item.type);
            boolean listening = ProjectStructure.Question.TYPE_LISTEN.equals(item.type);
            String typeLabel = arrange ? "词块组句" : match ? "配对"
                    : typing ? "输入翻译" : listening ? "听音选择"
                    : ProjectStructure.Question.TYPE_CARD.equals(item.type) ? "新知卡片"
                    : ProjectStructure.Question.TYPE_DIALOG.equals(item.type) ? "选择对话"
                    : ProjectStructure.Question.TYPE_READING.equals(item.type) ? "阅读短文" : "选择正确翻译";
            boolean choice = ProjectStructure.Question.TYPE_TRANSLATION_CHOICE.equals(item.type);
            row.setText(sentence + "\n" + typeLabel +
                    (choice ? (item.hintsEnabled ? " · 学习提示" : " · 无提示") : ""));
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
        dismissHint();
        selectedOption = -1;
        answered = false;
        arrangePool.clear();
        arrangeSelected.clear();
        matchLeftOrder.clear();
        matchRightOrder.clear();
        matchMatched.clear();
        matchLeft = -1;
        matchRight = -1;
        typeInput = null;
        typeNearView = null;
        listenSelected = -1;
        listenAutoId = "";
        listenPlaying = false;
        listenPlayButton = null;
        confirmButton.setVisibility(View.GONE);
        confirmButton.setText("确认答案");
        course = selected;
        question = item;
        titleView.setText(item.name == null || item.name.isEmpty() ? "题目" : item.name);
        String mode = ProjectStructure.Question.TYPE_ARRANGE.equals(item.type) ? "词块组句"
                : ProjectStructure.Question.TYPE_MATCH.equals(item.type) ? "配对"
                : ProjectStructure.Question.TYPE_TYPE.equals(item.type) ? "输入翻译"
                : ProjectStructure.Question.TYPE_LISTEN.equals(item.type) ? "听音选择"
                : ProjectStructure.Question.TYPE_CARD.equals(item.type) ? "新知卡片"
                : ProjectStructure.Question.TYPE_DIALOG.equals(item.type) ? "选择对话"
                : ProjectStructure.Question.TYPE_READING.equals(item.type) ? "阅读短文" : "选择正确翻译";
        subtitleView.setText(selected.name + " · " + mode);
        content.removeAllViews();
        if (ProjectStructure.Question.TYPE_READING.equals(item.type)) {
            renderReadingQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_CARD.equals(item.type)) {
            renderCardQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_LISTEN.equals(item.type)) {
            renderListenQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_TYPE.equals(item.type)) {
            renderTypeQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_MATCH.equals(item.type)) {
            renderMatchQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_ARRANGE.equals(item.type)) {
            renderArrangeQuestion(item);
            return;
        }
        if (ProjectStructure.Question.TYPE_DIALOG.equals(item.type)) {
            renderDialogQuestion(item);
            return;
        }

        TextView sentence = new TextView(this);
        sentence.setTextSize(22f);
        sentence.setTextColor(getColor(R.color.forge_text));
        sentence.setLineSpacing(dp(6), 1f);
        CharSequence rendered = renderSentence(item, sentence);
        sentence.setText(rendered);
        sentenceView = sentence;
        sentence.setTextIsSelectable(false);
        sentence.setFocusable(false);
        sentence.setMovementMethod(LinkMovementMethod.getInstance());
        sentence.setOnTouchListener((v, event) -> {
            lastTouchX = (int) event.getRawX();
            lastTouchY = (int) event.getRawY();
            return false;
        });
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

        renderChoiceOptions(item);
    }

    private void renderChoiceOptions(ProjectStructure.Question item) {
        content.addView(text("选项（选择后确认）", 14, R.color.forge_muted));
        for (int i = 0; i < item.options.size(); i++) {
            final int index = i;
            boolean hasVocab = i < item.optionItems.size() && !item.optionItems.get(i).isEmpty();
            if (hasVocab) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(4), dp(16), dp(4));
                TextView number = new TextView(this);
                number.setText((i + 1) + ". ");
                row.addView(number);
                for (ProjectStructure.Question.OptionItem value : item.optionItems.get(i)) {
                    if ("vocab".equals(value.kind)) {
                        VocabularyEntry entry = findEntry(value.vocabId);
                        if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                            if (bitmap != null) {
                                android.widget.ImageView image = new android.widget.ImageView(this);
                                image.setImageBitmap(bitmap);
                                image.setAdjustViewBounds(true);
                                int h = dp(22);
                                int w = Math.max(dp(22), bitmap.getWidth() * h / Math.max(1, bitmap.getHeight()));
                                row.addView(image, new LinearLayout.LayoutParams(w, h));
                            } else {
                                row.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                            }
                        } else if (entry != null) {
                            row.addView(text(vocabulary.displayName(entry), 16, R.color.forge_text));
                        }
                    } else {
                        row.addView(text(value.text, 16, R.color.forge_text));
                    }
                }
                styleMatchRow(row, answered && index == item.correctIndex, selectedOption == index);
                row.setTag("option");
                row.setOnClickListener(v -> {
                    if (answered) return;
                    dismissHint();
                    selectedOption = index;
                    updateOptionStyles();
                    confirmButton.setVisibility(View.VISIBLE);
                });
                content.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            } else {
                ForgeButton option = new ForgeButton(this);
                option.setAllCaps(false);
                option.setText((i + 1) + ". " + item.options.get(i));
                option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                option.setTextSize(16f);
                option.setPadding(dp(16), 0, dp(16), 0);
                styleOption(option, false, false);
                option.setTag("option");
                option.setOnClickListener(v -> {
                    if (answered) return;
                    dismissHint();
                    selectedOption = index;
                    updateOptionStyles();
                    confirmButton.setVisibility(View.VISIBLE);
                });
                content.addView(option, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            }
        }
    }

    private void renderDialogQuestion(ProjectStructure.Question item) {
        content.removeAllViews();
        for (int i = 0; i < item.lines.size(); i++) {
            ProjectStructure.Question.DialogLine line = item.lines.get(i);
            String who = line.who == null ? "" : line.who.trim();
            String body = line.text == null ? "" : line.text.trim();
            if (who.isEmpty() && body.isEmpty()) continue;
            LinearLayout bubble = new LinearLayout(this);
            bubble.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable shape = new GradientDrawable();
            shape.setCornerRadius(dp(12));
            shape.setColor(getColor(i % 2 == 0 ? R.color.forge_panel : R.color.forge_accent_soft));
            bubble.setBackground(shape);
            bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
            TextView speaker = text(who.isEmpty() ? String.valueOf(i + 1) : who, 12,
                    R.color.forge_muted);
            bubble.addView(speaker);
            TextView lineText = text(body.isEmpty() ? "（待补全）" : body, 16, R.color.forge_text);
            bubble.addView(lineText);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = i % 2 == 0 ? Gravity.START : Gravity.END;
            lp.bottomMargin = dp(6);
            content.addView(bubble, lp);
        }
        if (item.prompt != null && !item.prompt.isEmpty()) {
            content.addView(text(item.prompt, 18, R.color.forge_text));
        }
        renderChoiceOptions(item);
    }

    private void renderReadingQuestion(ProjectStructure.Question item) {
        content.removeAllViews();
        SpannableString passage = new SpannableString(item.passage);
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[\\p{L}\\p{N}]+").matcher(item.passage);
        while (matcher.find()) {
            String token = matcher.group();
            VocabularyEntry matched = null;
            for (VocabularyEntry entry : vocabulary.entries) {
                String name = vocabulary.displayName(entry);
                if (token.equalsIgnoreCase(name) || entry.text.equalsIgnoreCase(token)
                        || entry.codes.contains(token)) { matched = entry; break; }
            }
            if (matched == null) continue;
            if (!item.hintsEnabled) continue;
            final String vocabId = matched.id;
            int start = matcher.start(), end = matcher.end();
            passage.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            passage.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    lastTouchX = widget.getWidth() / 2;
                    lastTouchY = 420;
                    showHint(vocabId);
                }
                @Override public void updateDrawState(TextPaint ds) {
                    ds.setColor(getColor(R.color.forge_accent)); ds.setUnderlineText(true);
                }
            }, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView passageView = text(passage, 16, R.color.forge_text);
        passageView.setLineSpacing(dp(4), 1f);
        passageView.setMovementMethod(LinkMovementMethod.getInstance());
        passageView.setTextIsSelectable(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(getColor(R.color.forge_panel)); bg.setCornerRadius(dp(12));
        passageView.setBackground(bg); passageView.setPadding(dp(12), dp(10), dp(12), dp(10));
        content.addView(passageView);
        if (!item.prompt.isEmpty()) content.addView(text(item.prompt, 18, R.color.forge_text));
        if ("type".equals(item.answerMode)) {
            readingInput = new EditText(this);
            readingInput.setHint("输入答案后确认");
            readingInput.setTextSize(16f);
            readingInput.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) {
                    if (answered) return;
                    confirmButton.setVisibility(s.toString().trim().isEmpty()
                            ? View.GONE : View.VISIBLE);
                }
                public void afterTextChanged(android.text.Editable s) {}
            });
            content.addView(readingInput);
            confirmButton.setText(answered
                    ? (isLastQuestion() ? "完成" : "下一题") : "确认答案");
            confirmButton.setVisibility(answered ? View.VISIBLE
                    : (readingInput.getText().toString().trim().isEmpty()
                            ? View.GONE : View.VISIBLE));
            return;
        }
        for (int i = 0; i < item.options.size(); i++) {
            final int index = i;
            boolean hasVocab = i < item.optionItems.size() && !item.optionItems.get(i).isEmpty();
            if (hasVocab) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(4), dp(16), dp(4));
                row.addView(text((i + 1) + ". ", 16, R.color.forge_text));
                for (ProjectStructure.Question.OptionItem value : item.optionItems.get(i)) {
                    if ("vocab".equals(value.kind)) {
                        VocabularyEntry entry = findEntry(value.vocabId);
                        if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                            if (bitmap != null) {
                                android.widget.ImageView image = new android.widget.ImageView(this);
                                image.setImageBitmap(bitmap); image.setAdjustViewBounds(true);
                                int h = dp(22);
                                int w = Math.max(dp(22), bitmap.getWidth() * h / Math.max(1, bitmap.getHeight()));
                                row.addView(image, new LinearLayout.LayoutParams(w, h));
                            }
                        } else if (entry != null) row.addView(text(vocabulary.displayName(entry), 16, R.color.forge_text));
                    } else row.addView(text(value.text, 16, R.color.forge_text));
                }
                styleMatchRow(row, answered && index == item.correctIndex, selectedOption == index);
                row.setTag("option");
                row.setOnClickListener(v -> {
                    if (answered) return;
                    dismissHint(); selectedOption = index;
                    updateOptionStyles(); confirmButton.setVisibility(View.VISIBLE);
                });
                content.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            } else {
                ForgeButton option = new ForgeButton(this);
                option.setAllCaps(false); option.setText((i + 1) + ". " + item.options.get(i));
                option.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                styleOption(option, false, false); option.setTag("option");
                option.setOnClickListener(v -> {
                    if (answered) return;
                    dismissHint(); selectedOption = index;
                    updateOptionStyles(); confirmButton.setVisibility(View.VISIBLE);
                });
                content.addView(option, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            }
        }
    }

    private void renderCardQuestion(ProjectStructure.Question item) {
        content.removeAllViews();
        content.addView(text("新知卡片", 14, R.color.forge_muted));
        VocabularyEntry entry = item.card.wordVocabId.isEmpty() ? null : findEntry(item.card.wordVocabId);
        if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
            if (bitmap != null) {
                android.widget.ImageView image = new android.widget.ImageView(this);
                image.setImageBitmap(bitmap);
                image.setAdjustViewBounds(true);
                int h = dp(48);
                int w = Math.max(dp(48), bitmap.getWidth() * h / Math.max(1, bitmap.getHeight()));
                content.addView(image, new LinearLayout.LayoutParams(w, h));
            }
        } else {
            content.addView(text(item.card.word, 28, R.color.forge_text));
        }
        if (!item.card.pron.isEmpty()) content.addView(text("/" + item.card.pron + "/", 15, R.color.forge_muted));
        content.addView(text(item.card.def, 18, R.color.forge_text));
        if (!item.card.detail.isEmpty()) content.addView(text(item.card.detail, 14, R.color.forge_text));
        for (ProjectStructure.Question.CardExample ex : item.card.examples) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.addView(text(ex.l, 15, R.color.forge_text));
            if (!ex.z.isEmpty()) row.addView(text(ex.z, 13, R.color.forge_muted));
            content.addView(row);
        }
        content.addView(text("这是介绍卡，不计分；点「继续」进入下一题。", 12, R.color.forge_muted));
        confirmButton.setText(isLastQuestion() ? "完成" : "继续");
        confirmButton.setVisibility(View.VISIBLE);
    }

    private void renderListenQuestion(ProjectStructure.Question item) {
        content.removeAllViews();
        if (!item.prompt.isEmpty()) content.addView(text(item.prompt, 20, R.color.forge_text));
        java.io.File audioFile = item.audio == null || item.audio.isEmpty()
                ? null : MediaFiles.resolveProjectFile(projectDirectory, item.audio);
        boolean missingAudio = audioFile == null || !audioFile.exists();
        if (missingAudio) content.addView(text("本题缺少音频", 16, R.color.forge_warn));
        listenPlayButton = new ForgeButton(this);
        listenPlayButton.setAllCaps(false);
        listenPlayButton.setText(listenPlaying ? "播放中…"
                : (listenAutoId.equals(item.id) ? "重新播放" : "播放音频"));
        listenPlayButton.setEnabled(!missingAudio);
        listenPlayButton.setOnClickListener(v -> {
            if (missingAudio) return;
            android.util.Log.d("FORGE_LEARN", "listenPlay " + item.audio + " " + item.audioStart + ".." + item.audioEnd);
            play(item.audio, item.audioStart, item.audioEnd);
            listenPlaying = true;
            renderListenQuestion(item);
        });
        content.addView(listenPlayButton);
        for (int i = 0; i < item.options.size(); i++) {
            final int index = i;
            boolean hasVocab = i < item.optionItems.size() && !item.optionItems.get(i).isEmpty();
            if (hasVocab) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(android.view.Gravity.CENTER_VERTICAL);
                row.setPadding(dp(12), dp(4), dp(12), dp(4));
                TextView number = new TextView(this);
                number.setText((i + 1) + ". ");
                row.addView(number);
                for (ProjectStructure.Question.OptionItem value : item.optionItems.get(i)) {
                    if ("vocab".equals(value.kind)) {
                        VocabularyEntry entry = null;
                        for (VocabularyEntry candidate : vocabulary.entries) {
                            if (value.vocabId.equals(candidate.id)) { entry = candidate; break; }
                        }
                        if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                            if (bitmap != null) {
                                android.widget.ImageView image = new android.widget.ImageView(this);
                                image.setImageBitmap(bitmap);
                                image.setAdjustViewBounds(true);
                                image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                                int width = Math.max(dp(22), bitmap.getWidth() * dp(22) / Math.max(1, bitmap.getHeight()));
                                row.addView(image, new LinearLayout.LayoutParams(width, dp(22)));
                            } else {
                                row.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                            }
                        } else if (entry != null) {
                            row.addView(text(vocabulary.displayName(entry), 16, R.color.forge_text));
                        }
                    } else {
                        row.addView(text(value.text, 16, R.color.forge_text));
                    }
                }
                styleMatchRow(row, answered && index == item.correctIndex, listenSelected == index);
                row.setOnClickListener(v -> {
                    if (answered || missingAudio) return;
                    listenSelected = index;
                    confirmButton.setVisibility(View.VISIBLE);
                    renderListenQuestion(item);
                });
                content.addView(row, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            } else {
                ForgeButton option = new ForgeButton(this);
                option.setAllCaps(false);
                option.setText((i + 1) + ". " + item.options.get(i));
                option.setOnClickListener(v -> {
                    if (answered || missingAudio) return;
                    listenSelected = index;
                    confirmButton.setVisibility(View.VISIBLE);
                    renderListenQuestion(item);
                });
                option.setEnabled(!answered && !missingAudio);
                option.setAlpha(answered || !missingAudio ? 1f : 0.55f);
                styleMatch(option, answered && index == item.correctIndex, listenSelected == index);
                content.addView(option, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
            }
        }
        if (missingAudio) confirmButton.setVisibility(View.GONE);
        if (!answered && listenSelected < 0) confirmButton.setVisibility(View.GONE);
        if (!listenAutoId.equals(item.id) && !missingAudio) {
            listenAutoId = item.id;
            android.util.Log.d("FORGE_LEARN", "listenPlay " + item.audio + " " + item.audioStart + ".." + item.audioEnd);
            play(item.audio, item.audioStart, item.audioEnd);
            listenPlaying = true;
            listenPlayButton.setText("播放中…");
        }
    }

    private void renderTypeQuestion(ProjectStructure.Question item) {
        boolean structured = false;
        for (ProjectStructure.Question.OptionItem value : item.promptItems) {
            if ("vocab".equals(value.kind)) structured = true;
        }
        if (structured) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            for (ProjectStructure.Question.OptionItem value : item.promptItems) {
                if ("vocab".equals(value.kind)) {
                    VocabularyEntry entry = null;
                    for (VocabularyEntry candidate : vocabulary.entries) {
                        if (value.vocabId.equals(candidate.id)) { entry = candidate; break; }
                    }
                    if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                        android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                        if (bitmap != null) {
                            android.widget.ImageView image = new android.widget.ImageView(this);
                            image.setImageBitmap(bitmap);
                            image.setAdjustViewBounds(true);
                            image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                            int width = Math.max(dp(28), bitmap.getWidth() * dp(28) / Math.max(1, bitmap.getHeight()));
                            row.addView(image, new LinearLayout.LayoutParams(width, dp(28)));
                        } else {
                            row.addView(text("〔图片缺失〕", 14, R.color.forge_warn));
                        }
                    } else if (entry != null) {
                        row.addView(text(vocabulary.displayName(entry), 20, R.color.forge_text));
                    }
                } else {
                    row.addView(text(value.text, 20, R.color.forge_text));
                }
            }
            content.addView(row);
        } else {
            content.addView(text(item.prompt, 20, R.color.forge_text));
        }
        typeInput = new EditText(this);
        typeInput.setHint("输入翻译");
        typeInput.setTextSize(16f);
        typeInput.setEnabled(!answered);
        typeInput.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {
                if (answered) return;
                confirmButton.setVisibility(s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
            }
            public void afterTextChanged(android.text.Editable s) {}
        });
        content.addView(typeInput);
        typeNearView = new TextView(this);
        typeNearView.setTextColor(getColor(R.color.forge_warn));
        typeNearView.setVisibility(View.GONE);
        content.addView(typeNearView);
        confirmButton.setVisibility(View.GONE);
    }

    private String normalizeType(String value, boolean ignoreCase, boolean ignoreSpace) {
        String result = value == null ? "" : value.trim().replaceAll("\s+", " ");
        if (ignoreCase) result = result.toLowerCase(Locale.ROOT);
        if (ignoreSpace) result = result.replaceAll("[\s\\p{Punct}，。！？、；：‘’“”（）《》…—]", "");
        return result;
    }

    private int editDistance(String a, String b) {
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] cur = new int[b.length() + 1];
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            prev = cur;
        }
        return prev[b.length()];
    }

    private void renderMatchQuestion(ProjectStructure.Question item) {
        if (matchLeftOrder.isEmpty()) {
            for (int i = 0; i < item.pairs.size(); i++) {
                matchLeftOrder.add(i);
                matchRightOrder.add(i);
            }
            Collections.shuffle(matchLeftOrder, new Random());
            Collections.shuffle(matchRightOrder, new Random());
        }
        content.removeAllViews();
        if (!item.prompt.isEmpty()) content.addView(text(item.prompt, 20, R.color.forge_text));
        content.addView(text("已配对 " + matchMatched.size() + " / " + item.pairs.size(),
                12, R.color.forge_muted));
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout leftColumn = new LinearLayout(this);
        leftColumn.setOrientation(LinearLayout.VERTICAL);
        LinearLayout rightColumn = new LinearLayout(this);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        for (int index : matchLeftOrder) {
            ProjectStructure.Question.MatchPair pair = item.pairs.get(index);
            View view;
            if (hasVocabItems(pair.leftItems)) {
                LinearLayout chip = matchSideChip(pair.leftItems, pair.left);
                styleMatchRow(chip, matchMatched.contains(index), matchLeft == index);
                chip.setOnClickListener(v -> {
                    if (matchMatched.contains(index)) return;
                    matchLeft = index;
                    tryMatch(item);
                });
                view = chip;
            } else {
                ForgeButton button = new ForgeButton(this);
                button.setAllCaps(false);
                button.setText(pair.left);
                button.setTextSize(15f);
                styleMatch(button, matchMatched.contains(index), matchLeft == index);
                button.setOnClickListener(v -> {
                    if (matchMatched.contains(index)) return;
                    matchLeft = index;
                    tryMatch(item);
                });
                view = button;
            }
            leftColumn.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        for (int index : matchRightOrder) {
            ProjectStructure.Question.MatchPair pair = item.pairs.get(index);
            View view;
            if (hasVocabItems(pair.rightItems)) {
                LinearLayout chip = matchSideChip(pair.rightItems, pair.right);
                styleMatchRow(chip, matchMatched.contains(index), matchRight == index);
                chip.setOnClickListener(v -> {
                    if (matchMatched.contains(index)) return;
                    matchRight = index;
                    tryMatch(item);
                });
                view = chip;
            } else {
                ForgeButton button = new ForgeButton(this);
                button.setAllCaps(false);
                button.setText(pair.right);
                button.setTextSize(15f);
                styleMatch(button, matchMatched.contains(index), matchRight == index);
                button.setOnClickListener(v -> {
                    if (matchMatched.contains(index)) return;
                    matchRight = index;
                    tryMatch(item);
                });
                view = button;
            }
            rightColumn.addView(view, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        }
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        columnParams.setMargins(0, 0, dp(6), 0);
        columns.addView(leftColumn, columnParams);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rightParams.setMargins(dp(6), 0, 0, 0);
        columns.addView(rightColumn, rightParams);
        content.addView(columns);
    }

    private boolean hasVocabItems(List<ProjectStructure.Question.OptionItem> items) {
        for (ProjectStructure.Question.OptionItem item : items) {
            if ("vocab".equals(item.kind)) return true;
        }
        return false;
    }

    private LinearLayout matchSideChip(List<ProjectStructure.Question.OptionItem> items,
                                       String fallback) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(10), 0, dp(10), 0);
        if (items.isEmpty()) {
            chip.addView(text(fallback, 15, R.color.forge_text));
            return chip;
        }
        for (ProjectStructure.Question.OptionItem item : items) {
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = findEntry(item.vocabId);
                if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                    android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory,
                            entry.image);
                    if (bitmap != null) {
                        android.widget.ImageView image = new android.widget.ImageView(this);
                        image.setImageBitmap(bitmap);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(22), dp(22));
                        lp.setMargins(0, 0, dp(6), 0);
                        chip.addView(image, lp);
                    } else {
                        chip.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                    }
                } else if (entry != null) {
                    TextView name = text(vocabulary.displayName(entry), 15, R.color.forge_text);
                    name.setPadding(0, 0, dp(6), 0);
                    chip.addView(name);
                }
            } else if (item.text != null && !item.text.isEmpty()) {
                TextView value = text(item.text, 15, R.color.forge_text);
                value.setPadding(0, 0, dp(6), 0);
                chip.addView(value);
            }
        }
        return chip;
    }

    private void tryMatch(ProjectStructure.Question item) {
        if (matchLeft < 0 || matchRight < 0) {
            renderMatchQuestion(item);
            return;
        }
        if (matchLeft == matchRight) {
            matchMatched.add(matchLeft);
            matchLeft = -1;
            matchRight = -1;
            if (matchMatched.size() == item.pairs.size()) {
                answered = true;
                confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
                confirmButton.setVisibility(View.VISIBLE);
                toast("全部配对完成");
            }
            renderMatchQuestion(item);
        } else {
            matchLeft = -1;
            matchRight = -1;
            toast("配对错误，请重试");
            renderMatchQuestion(item);
        }
    }

    private void styleMatchRow(LinearLayout row, boolean correct, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        if (correct) shape.setColor(getColor(R.color.forge_ok_bg));
        else if (selected) shape.setColor(getColor(R.color.forge_accent_soft));
        else shape.setColor(getColor(R.color.forge_panel));
        shape.setStroke(dp(1), getColor(R.color.forge_panel3));
        row.setBackground(shape);
    }

    private void styleMatch(Button button, boolean correct, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(12));
        if (correct) {
            shape.setColor(getColor(R.color.forge_ok_bg));
            button.setTextColor(getColor(R.color.forge_ok));
        } else if (selected) {
            shape.setColor(getColor(R.color.forge_accent_soft));
            button.setTextColor(getColor(R.color.forge_accent));
        } else {
            shape.setColor(getColor(R.color.forge_panel));
            button.setTextColor(getColor(R.color.forge_text));
        }
        shape.setStroke(dp(1), getColor(R.color.forge_panel3));
        button.setBackground(shape);
    }

    private void renderArrangeQuestion(ProjectStructure.Question item) {
        if (arrangePool.isEmpty() && arrangeSelected.isEmpty()) {
            arrangePool.addAll(item.blocks);
            Collections.shuffle(arrangePool);
        }
        content.removeAllViews();
        content.addView(text(item.prompt == null || item.prompt.isEmpty()
                ? "词块组句" : item.prompt, 20, R.color.forge_text));
        content.addView(text("按正确顺序点击词块", 12, R.color.forge_muted));

        content.addView(text("已选", 13, R.color.forge_muted));
        LinearLayout selectedRow = new LinearLayout(this);
        selectedRow.setOrientation(LinearLayout.HORIZONTAL);
        selectedRow.setPadding(0, dp(6), 0, dp(6));
        if (arrangeSelected.isEmpty()) {
            selectedRow.addView(text("点击下方词块加入", 13, R.color.forge_muted));
        }
        for (ProjectStructure.Question.Block block : arrangeSelected) {
            View chip = blockChipView(block);
            styleChip(chip, answered, false);
            chip.setOnClickListener(v -> {
                if (answered) return;
                playVocabularyForBlock(block);
                arrangeSelected.remove(block);
                arrangePool.add(block);
                renderArrangeQuestion(question);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(42));
            lp.setMargins(0, 0, dp(6), 0);
            selectedRow.addView(chip, lp);
        }
        content.addView(selectedRow);

        content.addView(text("词块池", 13, R.color.forge_muted));
        LinearLayout pool = new LinearLayout(this);
        pool.setOrientation(LinearLayout.VERTICAL);
        for (ProjectStructure.Question.Block block : arrangePool) {
            View chip = blockChipView(block);
            styleChip(chip, false, false);
            chip.setOnClickListener(v -> {
                if (answered) return;
                dismissHint();
                playVocabularyForBlock(block);
                arrangePool.remove(block);
                arrangeSelected.add(block);
                renderArrangeQuestion(question);
            });
            pool.addView(chip, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));
        }
        content.addView(pool);
        confirmButton.setVisibility(arrangeSelected.isEmpty() ? View.GONE : View.VISIBLE);
        if (!answered) confirmButton.setText("确认答案");
    }

    private void playVocabularyForBlock(ProjectStructure.Question.Block block) {
        if (block == null) return;
        String needle = block.text == null ? "" : block.text.trim();
        VocabularyEntry match = null;
        if (block.vocabId != null && !block.vocabId.isEmpty()) {
            match = findEntry(block.vocabId);
        }
        if (match == null) {
            for (VocabularyEntry entry : vocabulary.entries) {
                String name = vocabulary.displayName(entry);
                if (name.equalsIgnoreCase(needle)
                        || (entry.text != null && entry.text.trim().equalsIgnoreCase(needle))) {
                    match = entry;
                    break;
                }
            }
        }
        if (match != null && !match.audio.isEmpty()) {
            android.util.Log.d("FORGE_LEARN", "blockAudio " + needle + " vocabId="
                    + block.vocabId + " -> " + match.audio + " " + match.audioStart
                    + ".." + match.audioEnd);
            play(match.audio, match.audioStart, match.audioEnd);
        }
    }

    private String arrangeAnswer(List<ProjectStructure.Question.Block> blocks) {
        StringBuilder builder = new StringBuilder();
        for (ProjectStructure.Question.Block block : blocks) {
            if (builder.length() > 0) builder.append(' ');
            builder.append(block.text.trim());
        }
        String value = builder.toString().trim().replaceAll("\\s+", " ");
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean arrangeCorrect(ProjectStructure.Question item) {
        String answer = arrangeAnswer(arrangeSelected);
        if (!item.answers.isEmpty()) {
            for (List<String> ids : item.answers) {
                List<ProjectStructure.Question.Block> blocks = new ArrayList<>();
                for (String id : ids) {
                    for (ProjectStructure.Question.Block block : item.blocks) {
                        if (id.equals(block.id)) { blocks.add(block); break; }
                    }
                }
                if (blocks.size() == ids.size() && answer.equals(arrangeAnswer(blocks))) return true;
            }
            return false;
        }
        // Legacy fallback for questions saved before the answer-list format.
        List<ProjectStructure.Question.Block> correct = new ArrayList<>();
        for (ProjectStructure.Question.Block block : item.blocks) {
            if (block.ok) correct.add(block);
        }
        if (!correct.isEmpty() && answer.equals(arrangeAnswer(correct))) return true;
        for (String alt : item.alt) {
            if (answer.equals(alt.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void updateOptionStyles() {
        int optionIndex = 0;
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (!"option".equals(child.getTag())) continue;
            boolean correct = answered && question.correctIndex == optionIndex;
            boolean selected = selectedOption == optionIndex;
            if (child instanceof Button) {
                styleOption((Button) child, correct, selected);
            } else if (child instanceof LinearLayout) {
                styleMatchRow((LinearLayout) child, correct, selected);
            }
            child.setEnabled(true);
            child.setAlpha(1f);
            optionIndex++;
        }
    }

    private void styleOption(Button button, boolean correct, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        if (correct) {
            shape.setColor(getColor(R.color.forge_ok_bg));
            button.setTextColor(getColor(R.color.forge_ok));
        } else if (selected) {
            shape.setColor(getColor(R.color.forge_accent_soft));
            button.setTextColor(getColor(R.color.forge_accent));
        } else {
            shape.setColor(getColor(R.color.forge_panel));
            button.setTextColor(getColor(R.color.forge_text));
        }
        shape.setStroke(dp(1), getColor(R.color.forge_panel3));
        button.setBackground(shape);
    }

    private View blockChipView(ProjectStructure.Question.Block block) {
        VocabularyEntry entry = block.vocabId == null || block.vocabId.isEmpty()
                ? null : findEntry(block.vocabId);
        if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(12), 0, dp(12), 0);
            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
            if (bitmap != null) {
                android.widget.ImageView image = new android.widget.ImageView(this);
                image.setImageBitmap(bitmap);
                image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                chip.addView(image, new LinearLayout.LayoutParams(dp(22), dp(22)));
            } else {
                chip.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
            }
            if (block.text != null && !block.text.trim().isEmpty()) {
                TextView mirror = text(block.text, 13, R.color.forge_text);
                mirror.setPadding(dp(8), 0, 0, 0);
                chip.addView(mirror);
            }
            return chip;
        }
        ForgeButton chip = new ForgeButton(this);
        chip.setAllCaps(false);
        chip.setText(block.text);
        chip.setTextSize(15f);
        chip.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        chip.setPadding(dp(16), 0, dp(16), 0);
        return chip;
    }

    private void styleChip(View view, boolean correct, boolean selected) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(14));
        int textColor;
        if (correct) {
            shape.setColor(getColor(R.color.forge_ok_bg));
            textColor = getColor(R.color.forge_ok);
        } else if (selected) {
            shape.setColor(getColor(R.color.forge_accent_soft));
            textColor = getColor(R.color.forge_accent);
        } else {
            shape.setColor(getColor(R.color.forge_panel));
            textColor = getColor(R.color.forge_text);
        }
        shape.setStroke(dp(1), getColor(R.color.forge_panel3));
        view.setBackground(shape);
        if (view instanceof TextView) ((TextView) view).setTextColor(textColor);
    }

    private void confirmAnswer() {
        if (question == null) return;
        if (ProjectStructure.Question.TYPE_CARD.equals(question.type)) {
            nextQuestion();
            return;
        }
        if (answered) return;
        if (ProjectStructure.Question.TYPE_LISTEN.equals(question.type)) {
            stopAudio();
            File questionAudio = question.audio == null || question.audio.isEmpty()
                    ? null : MediaFiles.resolveProjectFile(projectDirectory, question.audio);
            if (questionAudio == null || !questionAudio.isFile()) {
                toast("本题缺少音频");
                return;
            }
            if (listenSelected < 0) return;
            if (listenSelected == question.correctIndex) {
                answered = true;
                confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
                confirmButton.setVisibility(View.VISIBLE);
                toast("回答正确");
                renderListenQuestion(question);
            } else {
                toast("回答错误，请重新选择");
                listenSelected = -1;
                renderListenQuestion(question);
            }
            return;
        }
        if (ProjectStructure.Question.TYPE_READING.equals(question.type)
                && "type".equals(question.answerMode)) {
            if (readingInput == null) return;
            String value = readingInput.getText().toString();
            if (value.trim().isEmpty()) return;
            String normalized = normalizeType(value, question.ignoreCase, question.ignoreSpace);
            boolean ok = false;
            for (String answer : question.acceptedAnswers) {
                if (normalized.equals(normalizeType(answer, question.ignoreCase,
                        question.ignoreSpace))) { ok = true; break; }
            }
            if (ok) {
                answered = true;
                readingInput.setEnabled(false);
                readingInput.setTextColor(getColor(R.color.forge_ok));
                readingInput.setBackgroundColor(getColor(R.color.forge_ok_bg));
                confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
                confirmButton.setVisibility(View.VISIBLE);
                toast("回答正确");
            } else {
                toast("回答错误，请再试一次");
            }
            return;
        }
        if (ProjectStructure.Question.TYPE_TYPE.equals(question.type)) {
            if (typeInput == null) return;
            String value = typeInput.getText().toString();
            if (value.trim().isEmpty()) return;
            String normalized = normalizeType(value, question.ignoreCase, question.ignoreSpace);
            boolean ok = false;
            for (String answer : question.acceptedAnswers) {
                if (normalized.equals(normalizeType(answer, question.ignoreCase,
                        question.ignoreSpace))) { ok = true; break; }
            }
            if (ok) {
                answered = true;
                typeInput.setEnabled(false);
                typeInput.setTextColor(getColor(R.color.forge_ok));
                typeInput.setBackgroundColor(getColor(R.color.forge_ok_bg));
                typeNearView.setVisibility(View.GONE);
                confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
                confirmButton.setVisibility(View.VISIBLE);
            } else {
                boolean warned = false;
                for (ProjectStructure.Question.WarnRule rule : question.warn) {
                    String match = normalizeType(rule.match, question.ignoreCase, question.ignoreSpace);
                    boolean hit = "has".equals(rule.kind) ? normalized.contains(match)
                            : normalized.equals(match);
                    if (hit) {
                        typeNearView.setText(rule.text.isEmpty()
                                ? "差一点就对了，应该是 " + rule.match : rule.text);
                        typeNearView.setVisibility(View.VISIBLE);
                        warned = true;
                        break;
                    }
                }
                if (!warned && question.near) {
                    for (String answer : question.acceptedAnswers) {
                        if (editDistance(normalized, normalizeType(answer, question.ignoreCase,
                                question.ignoreSpace)) <= 1) {
                            typeNearView.setText("差一点就对了，应该是 " + answer);
                            typeNearView.setVisibility(View.VISIBLE);
                            warned = true;
                            break;
                        }
                    }
                }
                if (!warned) toast("回答错误，请再试一次");
            }
            return;
        }
        if (ProjectStructure.Question.TYPE_ARRANGE.equals(question.type)) {
            if (arrangeSelected.isEmpty()) return;
            dismissHint();
            if (arrangeCorrect(question)) {
                answered = true;
                renderArrangeQuestion(question);
                confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
                confirmButton.setVisibility(View.VISIBLE);
            } else {
                toast("回答错误，请重新选择");
            }
            return;
        }
        if (selectedOption < 0) return;
        dismissHint();
        if (selectedOption == question.correctIndex) {
            answered = true;
            updateOptionStyles();
            confirmButton.setText(isLastQuestion() ? "完成" : "下一题");
            confirmButton.setVisibility(View.VISIBLE);
            if (isLastQuestion()) toast("本课完成");
        } else {
            toast("回答错误，请重新选择");
        }
    }

    private boolean isLastQuestion() {
        if (course == null || question == null) return true;
        int index = course.questions.indexOf(question);
        return index < 0 || index >= course.questions.size() - 1;
    }

    private void nextQuestion() {
        if (course == null || question == null) return;
        int index = course.questions.indexOf(question);
        if (index >= 0 && index + 1 < course.questions.size()) {
            showQuestion(course, course.questions.get(index + 1));
        } else {
            showQuestions(course);
        }
    }

    private CharSequence renderSentence(ProjectStructure.Question item, TextView view) {
        SpannableString span = new SpannableString("");
        StringBuilder builder = new StringBuilder();
        List<int[]> ranges = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<VocabularyEntry> entries = new ArrayList<>();
        for (ProjectStructure.Question.Segment segment : item.segments) {
            if (segment.word) {
                VocabularyEntry entry = findEntry(segment.vocabId);
                boolean image = entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type);
                String name = entry == null ? "" : (image ? "\uFFFC" : vocabulary.displayName(entry));
                if (name.isEmpty()) name = "〔词汇已删除〕";
                if (builder.length() > 0) builder.append(' ');
                int start = builder.length();
                builder.append(name);
                ranges.add(new int[]{start, builder.length()});
                ids.add(segment.vocabId);
                entries.add(entry);
            } else {
                builder.append(segment.text);
            }
        }
        String rendered = builder.toString();
        SpannableString result = new SpannableString(rendered);
        for (int i = 0; i < ranges.size(); i++) {
            int start = ranges.get(i)[0];
            int end = ranges.get(i)[1];
            final String vocabId = ids.get(i);
            VocabularyEntry entry = entries.get(i);
            if (entry == null) continue;
            if (VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                if (bitmap != null) {
                    int height = Math.max(dp(20), (int) (view.getTextSize() * 1.4f));
                    int width = Math.max(dp(20), bitmap.getWidth() * height / Math.max(1, bitmap.getHeight()));
                    android.graphics.drawable.BitmapDrawable drawable =
                            new android.graphics.drawable.BitmapDrawable(getResources(), bitmap);
                    drawable.setBounds(0, 0, width, height);
                    result.setSpan(new android.text.style.ImageSpan(drawable,
                            android.text.style.ImageSpan.ALIGN_BASELINE), start, end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else {
                result.setSpan(new UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            if (!item.hintsEnabled) continue;
            result.setSpan(new ClickableSpan() {
                @Override public void onClick(View widget) {
                    if (widget instanceof TextView) {
                        TextView textView = (TextView) widget;
                        textView.clearFocus();
                        CharSequence text = textView.getText();
                        if (text instanceof android.text.Spannable) {
                            android.text.Selection.removeSelection((android.text.Spannable) text);
                        }
                    }
                    showHint(vocabId);
                }
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
        if (hintPopup != null && vocabId.equals(openVocabId)) {
            play(entry.audio, entry.audioStart, entry.audioEnd);
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
        openVocabId = vocabId;
        showHintPopup(entry, text.toString().trim());
        play(entry.audio, entry.audioStart, entry.audioEnd);
    }

    private void showHintPopup(VocabularyEntry entry, String body) {
        dismissHint();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(getColor(R.color.forge_panel));
        cardBg.setCornerRadius(dp(16));
        cardBg.setStroke(dp(1), getColor(R.color.forge_panel3));
        card.setBackground(cardBg);
        card.setElevation(dp(8));
        TextView title = new TextView(this);
        if (VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
            android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
            if (bitmap != null) {
                android.widget.ImageView preview = new android.widget.ImageView(this);
                preview.setImageBitmap(bitmap);
                preview.setAdjustViewBounds(true);
                preview.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                int height = dp(48);
                int width = Math.max(dp(48), bitmap.getWidth() * height / Math.max(1, bitmap.getHeight()));
                card.addView(preview, new LinearLayout.LayoutParams(width, height));
            }
        }
        title.setText(vocabulary.displayName(entry));
        title.setTextSize(16f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(getColor(R.color.forge_text));
        card.addView(title);
        ScrollView scroll = new ScrollView(this);
        TextView contentText = new TextView(this);
        contentText.setText(body);
        contentText.setTextSize(13f);
        contentText.setTextColor(getColor(R.color.forge_text));
        scroll.addView(contentText);
        card.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(180)));
        TextView replayHint = new TextView(this);
        replayHint.setText("再次点击词汇可重播");
        replayHint.setTextSize(11f);
        replayHint.setTextColor(getColor(R.color.forge_muted));
        replayHint.setPadding(0, dp(6), 0, 0);
        card.addView(replayHint);

        hintPopup = new PopupWindow(card, dp(320), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        hintPopup.setOutsideTouchable(false);
        hintPopup.setFocusable(false);
        hintPopup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        hintPopup.setOnDismissListener(() -> {
            openVocabId = null;
            stopAudio();
        });
        int[] location = new int[2];
        content.getLocationOnScreen(location);
        int x = Math.max(dp(8), Math.min(lastTouchX - location[0], content.getWidth() - dp(320)));
        int y = Math.max(dp(8), lastTouchY - location[1] + dp(12));
        hintPopup.showAtLocation(getWindow().getDecorView(), Gravity.TOP | Gravity.START,
                x + location[0], y + location[1]);
    }

    private void dismissHint() {
        android.util.Log.d("FORGE_LEARN", "dismissHint -> stopAudio");
        if (hintPopup != null) {
            hintPopup.dismiss();
            hintPopup = null;
        }
        stopAudio();
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
        android.util.Log.d("FORGE_LEARN", "stopAudio");
        listenPlaying = false;
        if (listenPlayButton != null) listenPlayButton.setText("重新播放");
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) { }
            player.release();
            player = null;
        }
    }

    private void goBack() {
        dismissHint();
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

    private TextView text(CharSequence value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        return view;
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