package io.github.myoslatal.languageforge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeMenu;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** First question type: build a sentence from vocabulary and choose the correct translation. */
public class QuestionEditorActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_CHAPTER_ID = "chapterId";
    public static final String EXTRA_UNIT_ID = "unitId";
    public static final String EXTRA_COURSE_ID = "courseId";
    public static final String EXTRA_QUESTION_ID = "questionId";

    private File projectDirectory;
    private PosStore posStore;
    private ProjectStructure structure;
    private ProjectStructure.Course course;
    private ProjectStructure.Question question;
    private VocabularyStore vocabulary;
    private final List<String> parts = new ArrayList<>();
    private final List<ProjectStructure.Question.Segment> segments = new ArrayList<>();
    private final ForgeTextField[] optionFields = new ForgeTextField[4];
    private RadioButton[] correctButtons = new RadioButton[4];
    private LinearLayout partsContainer;
    private TextView sentenceView;
    private TextView audioHint;
    private TextView audioTrimText;
    private SeekBar startSeek;
    private SeekBar endSeek;
    private boolean updatingTrim;
    private CheckBox hintsEnabledView;
    private Handler playHandler;
    private Runnable playStopTask;
    private ForgeButton recordButton;
    private boolean recording;
    private boolean dirty;
    private boolean saved;
    private MediaRecorder recorder;
    private MediaPlayer player;
    private File recordFile;
    private long recordStart;
    private Handler ticker;
    private Runnable tickerTask;
    private String pendingAudio;
    private long pendingDuration;
    private long audioStart;
    private long audioEnd;
    private String existingAudio = "";
    private String originalAudio = "";
    private long existingDuration;
    private boolean audioRemoved;
    private Spinner typeSpinner;
    private LinearLayout arrangeSection;
    private LinearLayout arrangeBlockRows;
    private EditText arrangePrompt;
    private final List<ProjectStructure.Question.Block> editBlocks = new ArrayList<>();
    private final List<List<String>> editAnswers = new ArrayList<>();
    private LinearLayout arrangeAnswers;
    private LinearLayout matchSection;
    private LinearLayout typeSection;
    private LinearLayout listenSection;
    private LinearLayout cardSection;
    private LinearLayout dialogSection;
    private EditText dialogPrompt;
    private LinearLayout dialogLines;
    private final List<ProjectStructure.Question.DialogLine> editLines = new ArrayList<>();
    private EditText cardWord, cardPron, cardDef, cardDetail;
    private LinearLayout cardWordPreview;
    private LinearLayout cardExamples;
    private final List<ProjectStructure.Question.CardExample> editExamples = new ArrayList<>();
    private String audioRelative = "";
    private String audioSource = "";
    private EditText listenPrompt;
    private EditText[] listenOptions = new EditText[4];
    private android.widget.RadioGroup listenCorrect;
    private TextView listenAudioLabel;
    private final List<List<ProjectStructure.Question.OptionItem>> listenOptionItems = new ArrayList<>();
    private int listenOptionIndex = -1;
    private int listenOptionPickIndex = -1;
    private final List<List<ProjectStructure.Question.OptionItem>> choiceOptionItems = new ArrayList<>();
    private final LinearLayout[] choiceOptionPreviews = new LinearLayout[4];
    private final View[] choiceOptionExtras = new View[4];
    private int choiceOptionPickIndex = -1;
    private EditText typePrompt;
    private EditText typeAnswers;
    private CheckBox typeIgnoreCase;
    private CheckBox typeIgnoreSpace;
    private CheckBox typeNear;
    private LinearLayout typeWarnRows;
    private final List<ProjectStructure.Question.WarnRule> editWarn = new ArrayList<>();
    private final List<ProjectStructure.Question.OptionItem> editPromptItems = new ArrayList<>();
    private LinearLayout typePromptPreview;
    private LinearLayout matchRows;
    private EditText matchPrompt;
    private final List<ProjectStructure.Question.MatchPair> editPairs = new ArrayList<>();
    private final List<EditText[]> matchPairFields = new ArrayList<>();
    private int matchPickPair = -1;
    private String matchPickSide = "l";
    private LinearLayout editorScrollContent;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String folder = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        String chapterId = getIntent().getStringExtra(EXTRA_CHAPTER_ID);
        String unitId = getIntent().getStringExtra(EXTRA_UNIT_ID);
        String courseId = getIntent().getStringExtra(EXTRA_COURSE_ID);
        String questionId = getIntent().getStringExtra(EXTRA_QUESTION_ID);
        File base = getExternalFilesDir(null);
        if (folder == null || base == null || chapterId == null || unitId == null
                || courseId == null) {
            toast("课程不存在");
            finish();
            return;
        }
        projectDirectory = new File(new File(base, "projects"), folder);
        posStore = new PosStore(projectDirectory);
        structure = ProjectStructure.load(projectDirectory);
        course = findCourse(chapterId, unitId, courseId);
        if (course == null) {
            toast("课程不存在");
            finish();
            return;
        }
        vocabulary = new VocabularyStore(projectDirectory);
        vocabulary.load();
        question = questionId == null ? null : structure.findQuestion(course, questionId);
        if (questionId != null && question == null) {
            toast("题目不存在");
            finish();
            return;
        }
        boolean isNew = question == null;
        if (isNew) {
            question = new ProjectStructure.Question(UUID.randomUUID().toString(), "未命名题目");
            question.options.add("");
            question.options.add("");
            question.options.add("");
            question.options.add("");
        } else {
            parts.addAll(question.parts);
            segments.addAll(question.segments);
            if (segments.isEmpty()) {
                for (String part : question.parts) {
                    segments.add(ProjectStructure.Question.Segment.word(part));
                }
            }
            existingAudio = question.audio;
            originalAudio = question.audio;
            existingDuration = question.audioDuration;
            audioStart = question.audioStart;
            audioEnd = question.audioEnd;
            if (audioEnd <= audioStart && existingDuration > 0) {
                audioStart = 0;
                audioEnd = existingDuration;
            }
        }

        setContentView(R.layout.activity_question_editor);
        applyTopInset(findViewById(R.id.question_editor_topbar));
        applyBottomInset(findViewById(R.id.question_editor_scroll));
        ((TextView) findViewById(R.id.question_editor_title)).setText(
                isNew ? "新建题目" : "编辑题目");
        ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                "选择正确翻译 · " + course.name);
        LinearLayout scrollContent = (LinearLayout) ((ScrollView)
                findViewById(R.id.question_editor_scroll)).getChildAt(0);
        editorScrollContent = scrollContent;
        typeSpinner = new Spinner(this);
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"选择正确翻译", "词块组句", "配对", "输入翻译", "听音选择", "新知卡片", "选择对话"});
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(typeAdapter);
        typeSpinner.setSelection(ProjectStructure.Question.TYPE_ARRANGE.equals(question.type) ? 1
                : ProjectStructure.Question.TYPE_MATCH.equals(question.type) ? 2
                : ProjectStructure.Question.TYPE_TYPE.equals(question.type) ? 3
                : ProjectStructure.Question.TYPE_LISTEN.equals(question.type) ? 4
                : ProjectStructure.Question.TYPE_CARD.equals(question.type) ? 5
                : ProjectStructure.Question.TYPE_DIALOG.equals(question.type) ? 6 : 0);
        scrollContent.addView(typeSpinner, 0);
        arrangeSection = new LinearLayout(this);
        arrangeSection.setOrientation(LinearLayout.VERTICAL);
        arrangeSection.setVisibility(View.GONE);
        arrangePrompt = new EditText(this);
        arrangePrompt.setHint("题干/释义（必填）");
        arrangePrompt.setText(question.prompt == null ? "" : question.prompt);
        arrangeSection.addView(arrangePrompt);
        arrangeBlockRows = new LinearLayout(this);
        arrangeBlockRows.setOrientation(LinearLayout.VERTICAL);
        arrangeSection.addView(arrangeBlockRows);
        ForgeButton addBlock = new ForgeButton(this);
        addBlock.setAllCaps(false);
        addBlock.setText("＋ 从词汇选择");
        addBlock.setOnClickListener(v -> openArrangePicker());
        arrangeSection.addView(addBlock);
        arrangeAnswers = new LinearLayout(this);
        arrangeAnswers.setOrientation(LinearLayout.VERTICAL);
        arrangeSection.addView(arrangeAnswers);
        ForgeButton addAnswer = new ForgeButton(this);
        addAnswer.setAllCaps(false);
        addAnswer.setText("＋ 添加答案");
        addAnswer.setOnClickListener(v -> openAnswerDialog(-1));
        arrangeSection.addView(addAnswer);
        scrollContent.addView(arrangeSection, 1);
        matchSection = new LinearLayout(this);
        matchSection.setOrientation(LinearLayout.VERTICAL);
        matchPrompt = new EditText(this);
        matchPrompt.setHint("配对说明/题干（可选）");
        matchPrompt.setText(question.prompt == null ? "" : question.prompt);
        matchSection.addView(matchPrompt);
        matchRows = new LinearLayout(this);
        matchRows.setOrientation(LinearLayout.VERTICAL);
        matchSection.addView(matchRows);
        ForgeButton addPair = new ForgeButton(this);
        addPair.setAllCaps(false);
        addPair.setText("＋ 添加配对");
        addPair.setOnClickListener(v -> {
            editPairs.add(new ProjectStructure.Question.MatchPair("", ""));
            renderMatchRows();
            dirty = true;
        });
        matchSection.addView(addPair);
        matchSection.setVisibility(View.GONE);
        scrollContent.addView(matchSection, 2);
        typeSection = new LinearLayout(this);
        typeSection.setOrientation(LinearLayout.VERTICAL);
        typePrompt = new EditText(this);
        typePrompt.setHint("题干/要翻译的内容（必填）");
        typePrompt.setText(question.prompt == null ? "" : question.prompt);
        typeSection.addView(typePrompt);
        typePromptPreview = new LinearLayout(this);
        typePromptPreview.setOrientation(LinearLayout.HORIZONTAL);
        typeSection.addView(typePromptPreview);
        editPromptItems.addAll(question.promptItems);
        if (editPromptItems.isEmpty() && question.prompt != null && !question.prompt.isEmpty()) {
            ProjectStructure.Question.OptionItem text = new ProjectStructure.Question.OptionItem();
            text.text = question.prompt;
            editPromptItems.add(text);
        }
        renderTypePromptPreview();
        typeAnswers = new EditText(this);
        typeAnswers.setHint("可接受答案，一行一个");
        typeAnswers.setMinLines(3);
        typeAnswers.setText(String.join("\n", question.acceptedAnswers));
        typeSection.addView(typeAnswers);
        ForgeButton clearAnswers = new ForgeButton(this);
        clearAnswers.setAllCaps(false);
        clearAnswers.setText("清空答案");
        clearAnswers.setOnClickListener(v -> {
            typeAnswers.setText("");
            dirty = true;
        });
        typeSection.addView(clearAnswers);
        typeIgnoreCase = new CheckBox(this);
        typeIgnoreCase.setText("忽略大小写");
        typeIgnoreCase.setChecked(question.ignoreCase);
        typeSection.addView(typeIgnoreCase);
        typeIgnoreSpace = new CheckBox(this);
        typeIgnoreSpace.setText("忽略空格与标点");
        typeIgnoreSpace.setChecked(question.ignoreSpace);
        typeSection.addView(typeIgnoreSpace);
        typeNear = new CheckBox(this);
        typeNear.setText("自动识别拼写相近（差 1 字母）");
        typeNear.setChecked(question.near);
        typeSection.addView(typeNear);
        LinearLayout promptTools = new LinearLayout(this);
        ForgeButton pickPrompt = new ForgeButton(this);
        pickPrompt.setAllCaps(false);
        pickPrompt.setText("从词库选词");
        pickPrompt.setOnClickListener(v -> openTypePromptPicker());
        promptTools.addView(pickPrompt);
        ForgeButton punctuation = new ForgeButton(this);
        punctuation.setAllCaps(false);
        punctuation.setText("插入标点");
        punctuation.setOnClickListener(v -> openPunctuationDialog());
        promptTools.addView(punctuation);
        typeSection.addView(promptTools);
        typeSection.addView(new TextView(this) {{ setText("差一点提示"); }});
        typeWarnRows = new LinearLayout(this);
        typeWarnRows.setOrientation(LinearLayout.VERTICAL);
        typeSection.addView(typeWarnRows);
        ForgeButton addWarn = new ForgeButton(this);
        addWarn.setAllCaps(false);
        addWarn.setText("＋ 添加差一点提示");
        addWarn.setOnClickListener(v -> {
            editWarn.add(new ProjectStructure.Question.WarnRule("", "eq", ""));
            renderWarnRows();
        });
        typeSection.addView(addWarn);
        editWarn.addAll(question.warn);
        renderWarnRows();
        typeSection.setVisibility(View.GONE);
        scrollContent.addView(typeSection, 3);
        listenSection = new LinearLayout(this);
        listenSection.setOrientation(LinearLayout.VERTICAL);
        listenPrompt = new EditText(this);
        listenPrompt.setHint("题干/说明（可选）");
        listenSection.addView(listenPrompt);
        listenAudioLabel = new TextView(this);
        listenSection.addView(listenAudioLabel);
        ForgeButton pickAudio = new ForgeButton(this);
        pickAudio.setAllCaps(false);
        pickAudio.setText("从词汇示例音频选择");
        pickAudio.setOnClickListener(v -> openListenAudioPicker());
        listenSection.addView(pickAudio);
        listenCorrect = new android.widget.RadioGroup(this);
        for (int i = 0; i < 4; i++) {
            final int index = i;
            LinearLayout optionRow = new LinearLayout(this);
            optionRow.setOrientation(LinearLayout.HORIZONTAL);
            optionRow.setGravity(Gravity.CENTER_VERTICAL);
            listenOptions[i] = new EditText(this);
            listenOptions[i].setHint("选项 " + (i + 1));
            optionRow.addView(listenOptions[i], new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton pickWords = new ForgeButton(this);
            pickWords.setAllCaps(false);
            pickWords.setText("选词");
            pickWords.setOnClickListener(v -> {
                listenOptionPickIndex = index;
                openListenOptionPicker();
            });
            optionRow.addView(pickWords, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            listenSection.addView(optionRow);
            LinearLayout optionPreview = new LinearLayout(this);
            optionPreview.setId(3000 + i);
            optionPreview.setOrientation(LinearLayout.HORIZONTAL);
            listenSection.addView(optionPreview);
            android.widget.RadioButton radio = new android.widget.RadioButton(this);
            radio.setText("正确 " + (i + 1));
            radio.setId(1000 + i);
            listenCorrect.addView(radio);
        }
        listenSection.addView(listenCorrect);
        listenSection.setVisibility(View.GONE);
        scrollContent.addView(listenSection, 4);
        cardSection = new LinearLayout(this);
        cardSection.setOrientation(LinearLayout.VERTICAL);
        cardWord = new EditText(this); cardWord.setHint("词形"); cardSection.addView(cardWord);
        cardWordPreview = new LinearLayout(this); cardWordPreview.setOrientation(LinearLayout.HORIZONTAL);
        cardSection.addView(cardWordPreview);
        cardPron = new EditText(this); cardPron.setHint("发音"); cardSection.addView(cardPron);
        cardDef = new EditText(this); cardDef.setHint("释义（多行）"); cardSection.addView(cardDef);
        cardDetail = new EditText(this); cardDetail.setHint("补充说明（多行）"); cardSection.addView(cardDetail);
        ForgeButton cardPick = new ForgeButton(this); cardPick.setAllCaps(false); cardPick.setText("从词库生成卡片");
        cardPick.setOnClickListener(v -> openCardPicker()); cardSection.addView(cardPick);
        cardExamples = new LinearLayout(this); cardExamples.setOrientation(LinearLayout.VERTICAL); cardSection.addView(cardExamples);
        ForgeButton addExample = new ForgeButton(this); addExample.setAllCaps(false); addExample.setText("＋ 例句");
        addExample.setOnClickListener(v -> { editExamples.add(new ProjectStructure.Question.CardExample()); renderCardExamples(); });
        cardSection.addView(addExample);
        cardSection.setVisibility(View.GONE);
        scrollContent.addView(cardSection, 5);
        dialogSection = new LinearLayout(this);
        dialogSection.setOrientation(LinearLayout.VERTICAL);
        dialogPrompt = new EditText(this);
        dialogPrompt.setHint("题干/提示（默认：选出最合适的回答）");
        dialogPrompt.setText(question.prompt == null ? "" : question.prompt);
        dialogPrompt.addTextChangedListener(new SimpleWatcher());
        dialogSection.addView(dialogPrompt);
        TextView dialogLinesTitle = new TextView(this);
        dialogLinesTitle.setText("对话台词（留空 = 待补全）");
        dialogLinesTitle.setTextSize(13f);
        dialogLinesTitle.setTextColor(getColor(R.color.forge_muted));
        dialogSection.addView(dialogLinesTitle);
        dialogLines = new LinearLayout(this);
        dialogLines.setOrientation(LinearLayout.VERTICAL);
        dialogSection.addView(dialogLines);
        ForgeButton addLine = new ForgeButton(this);
        addLine.setAllCaps(false);
        addLine.setText("＋ 台词");
        addLine.setOnClickListener(v -> {
            editLines.add(new ProjectStructure.Question.DialogLine("", ""));
            renderDialogLines();
            dirty = true;
        });
        dialogSection.addView(addLine);
        editLines.addAll(question.lines);
        renderDialogLines();
        dialogSection.setVisibility(View.GONE);
        scrollContent.addView(dialogSection, 6);
        cardWord.setText(question.card.word);
        cardWord.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            public void onTextChanged(CharSequence s,int a,int b,int c){
                dirty = true;
                if (question.card.wordVocabId.isEmpty()) return;
                VocabularyEntry entry = findEntry(question.card.wordVocabId);
                if (entry == null || !segmentMirror(entry).equals(s.toString())) {
                    question.card.wordVocabId = "";
                    renderCardWordPreview();
                }
            }
            public void afterTextChanged(android.text.Editable s){}
        });
        cardPron.setText(question.card.pron);
        cardDef.setText(question.card.def); cardDetail.setText(question.card.detail);
        editExamples.addAll(question.card.examples); renderCardExamples(); renderCardWordPreview();
        listenPrompt.setText(question.prompt == null ? "" : question.prompt);
        for (int i = 0; i < 4; i++) {
            List<ProjectStructure.Question.OptionItem> items = new ArrayList<>();
            if (i < question.optionItems.size()) items.addAll(question.optionItems.get(i));
            if (items.isEmpty()) {
                String text = i < question.options.size() ? question.options.get(i) : "";
                if (!text.isEmpty()) {
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.text = text;
                    items.add(item);
                }
            }
            listenOptionItems.add(items);
            listenOptions[i].setText(items.isEmpty()
                    ? (i < question.options.size() ? question.options.get(i) : "")
                    : mirrorListenOption(items));
        }
        renderListenOptionPreviews();
        if (question.correctIndex >= 0 && question.correctIndex < 4) {
            listenCorrect.check(1000 + question.correctIndex);
        }
        audioSource = question.audioSource == null ? "" : question.audioSource;
        audioRelative = question.audio == null ? "" : question.audio;
        audioStart = question.audioStart;
        audioEnd = question.audioEnd;
        listenAudioLabel.setText(audioRelative.isEmpty() ? "尚未选择音频" : audioRelative);
        editPairs.addAll(question.pairs);
        renderMatchRows();
        editBlocks.addAll(question.blocks);
        for (List<String> answer : question.answers) editAnswers.add(new ArrayList<>(answer));
        if (editAnswers.isEmpty()) {
            List<String> migrated = new ArrayList<>();
            for (ProjectStructure.Question.Block block : editBlocks) {
                if (block.ok) migrated.add(block.id);
            }
            if (migrated.size() >= 2) editAnswers.add(migrated);
        }
        renderArrangeBlocks();
        renderArrangeAnswers();
        typeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                                   int position, long id) {
                if (matchSection != null) matchSection.setVisibility(View.GONE);
                if (typeSection != null) typeSection.setVisibility(View.GONE);
                if (listenSection != null) listenSection.setVisibility(View.GONE);
                if (cardSection != null) cardSection.setVisibility(View.GONE);
                if (dialogSection != null) dialogSection.setVisibility(View.GONE);
                if (position == 6) {
                    if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
                    if (dialogSection != null) dialogSection.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                            "选择对话 · " + course.name);
                    showDialogEditor();
                } else if (position == 5) {
                    if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
                    if (cardSection != null) cardSection.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                            "新知卡片 · " + course.name);
                    hideForMatch(true);
                } else if (position == 4) {
                    if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
                    if (listenSection != null) listenSection.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                            "听音选择 · " + course.name);
                    showListenAudio();
                } else if (position == 3) {
                    if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
                    if (typeSection != null) typeSection.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                            "输入翻译 · " + course.name);
                    hideForMatch(true);
                } else if (position == 2) {
                    if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
                    if (matchSection != null) matchSection.setVisibility(View.VISIBLE);
                    ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                            "配对 · " + course.name);
                    hideForMatch(true);
                } else {
                    applyType(position == 1);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        if (ProjectStructure.Question.TYPE_MATCH.equals(question.type)) {
            if (matchSection != null) matchSection.setVisibility(View.VISIBLE);
            if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
            hideForMatch(true);
        } else if (ProjectStructure.Question.TYPE_DIALOG.equals(question.type)) {
            if (dialogSection != null) dialogSection.setVisibility(View.VISIBLE);
            if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
            showDialogEditor();
        } else if (ProjectStructure.Question.TYPE_CARD.equals(question.type)) {
            if (cardSection != null) cardSection.setVisibility(View.VISIBLE);
            if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
            hideForMatch(true);
        } else if (ProjectStructure.Question.TYPE_LISTEN.equals(question.type)) {
            if (listenSection != null) listenSection.setVisibility(View.VISIBLE);
            if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
            showListenAudio();
        } else if (ProjectStructure.Question.TYPE_TYPE.equals(question.type)) {
            if (typeSection != null) typeSection.setVisibility(View.VISIBLE);
            if (arrangeSection != null) arrangeSection.setVisibility(View.GONE);
            hideForMatch(true);
        } else {
            applyType(ProjectStructure.Question.TYPE_ARRANGE.equals(question.type));
        }
        partsContainer = findViewById(R.id.question_parts);
        sentenceView = findViewById(R.id.question_sentence);
        audioHint = findViewById(R.id.question_audio_hint);
        audioTrimText = findViewById(R.id.question_audio_trim);
        startSeek = findViewById(R.id.question_audio_start);
        endSeek = findViewById(R.id.question_audio_end);
        recordButton = findViewById(R.id.btn_question_record);
        hintsEnabledView = findViewById(R.id.question_hints_enabled);
        hintsEnabledView.setChecked(question.hintsEnabled);
        hintsEnabledView.setOnCheckedChangeListener((buttonView, checked) -> dirty = true);
        startSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingTrim) return;
                audioStart = Math.min(progress, Math.max(0, audioEnd - 100));
                dirty = true;
                updateTrimUi();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        endSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser || updatingTrim) return;
                audioEnd = Math.max(progress, audioStart + 100);
                dirty = true;
                updateTrimUi();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        findViewById(R.id.btn_question_trim_play).setOnClickListener(v -> playAudio());
        for (int i = 0; i < 4; i++) {
            int id = getResources().getIdentifier("question_option_" + i, "id", getPackageName());
            optionFields[i] = findViewById(id);
            int radioId = getResources().getIdentifier("question_correct_" + i, "id",
                    getPackageName());
            correctButtons[i] = findViewById(radioId);
        }
        String[] options = new String[4];
        for (int i = 0; i < 4; i++) {
            List<ProjectStructure.Question.OptionItem> items = new ArrayList<>();
            if (i < question.optionItems.size()) items.addAll(question.optionItems.get(i));
            if (items.isEmpty()) {
                String text = i < question.options.size() ? question.options.get(i) : "";
                if (!text.isEmpty()) {
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.text = text;
                    items.add(item);
                }
            }
            choiceOptionItems.add(items);
            options[i] = containsVocabItem(items) || items.isEmpty()
                    ? (i < question.options.size() ? question.options.get(i) : "")
                    : mirrorListenOption(items);
            optionFields[i].setText(options[i]);
            optionFields[i].getEditText().addTextChangedListener(new SimpleWatcher());
            ViewGroup parent = (ViewGroup) optionFields[i].getParent();
            int fieldIndex = parent.indexOfChild(optionFields[i]);
            LinearLayout extra = new LinearLayout(this);
            extra.setOrientation(LinearLayout.HORIZONTAL);
            extra.setGravity(Gravity.CENTER_VERTICAL);
            final int optionIndex = i;
            ForgeButton pick = new ForgeButton(this);
            pick.setAllCaps(false);
            pick.setText("选词");
            pick.setOnClickListener(v -> {
                choiceOptionPickIndex = optionIndex;
                openChoiceOptionPicker();
            });
            extra.addView(pick);
            LinearLayout preview = new LinearLayout(this);
            preview.setOrientation(LinearLayout.HORIZONTAL);
            preview.setGravity(Gravity.CENTER_VERTICAL);
            choiceOptionPreviews[i] = preview;
            extra.addView(preview, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            parent.addView(extra, fieldIndex + 1);
            choiceOptionExtras[i] = extra;
        }
        renderChoiceOptionPreviews();
        if (question.correctIndex >= 0 && question.correctIndex < 4) {
            correctButtons[question.correctIndex].setChecked(true);
        }
        for (RadioButton button : correctButtons) {
            button.setOnCheckedChangeListener((v, checked) -> {
                if (checked) dirty = true;
            });
        }
        findViewById(R.id.btn_question_back).setOnClickListener(v -> confirmBack());
        applyType(ProjectStructure.Question.TYPE_ARRANGE.equals(question.type));
        findViewById(R.id.btn_save_question).setOnClickListener(v -> save());
        findViewById(R.id.btn_choose_question_parts).setOnClickListener(v -> openPicker());
        findViewById(R.id.btn_insert_punct).setOnClickListener(v -> showPunctuationMenu(v));
        findViewById(R.id.btn_question_record).setOnClickListener(v -> toggleRecord());
        findViewById(R.id.btn_question_play).setOnClickListener(v -> playAudio());
        findViewById(R.id.btn_question_remove_audio).setOnClickListener(v -> removeAudio());
        renderParts();
        updateAudioUi();
    }

    private ProjectStructure.Course findCourse(String chapterId, String unitId, String courseId) {
        for (ProjectStructure.Chapter chapter : structure.chapters) {
            if (!chapter.id.equals(chapterId)) continue;
            for (ProjectStructure.Unit unit : chapter.units) {
                if (!unit.id.equals(unitId)) continue;
                for (ProjectStructure.Course candidate : unit.courses) {
                    if (candidate.id.equals(courseId)) return candidate;
                }
            }
        }
        return null;
    }

    private void showPunctuationMenu(View anchor) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader("插入标点");
        String[] marks = {"，", "。", "、", "？", "！", "；", "：", "“", "”", "（", "）", "…", "—", "/"};
        for (String mark : marks) {
            menu.addItem(mark, () -> {
                segments.add(ProjectStructure.Question.Segment.punct(mark));
                dirty = true;
                renderParts();
            });
        }
        menu.show(anchor);
    }

    private void openPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择句子词汇");
        intent.putStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS,
                new ArrayList<>(parts));
        pickerLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> pickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> selected = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (selected == null) return;
                parts.clear();
                for (String id : selected) {
                    if (id != null && !id.isEmpty() && !parts.contains(id)) parts.add(id);
                }
                List<ProjectStructure.Question.Segment> punctuation = new ArrayList<>();
                for (ProjectStructure.Question.Segment segment : segments) {
                    if (!segment.word) punctuation.add(segment);
                }
                segments.clear();
                for (String id : parts) {
                    segments.add(ProjectStructure.Question.Segment.word(id));
                }
                segments.addAll(punctuation);
                if (!punctuation.isEmpty()) {
                    toast("已保留原有标点，并移到新词序列末尾");
                }
                dirty = true;
                renderParts();
            });

    private final class SimpleWatcher implements android.text.TextWatcher {
        @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
        @Override public void onTextChanged(CharSequence s, int st, int b, int c) { dirty = true; }
        @Override public void afterTextChanged(android.text.Editable s) {}
    }

    private VocabularyEntry findEntry(String vocabId) {
        for (VocabularyEntry candidate : vocabulary.entries) {
            if (candidate.id.equals(vocabId)) return candidate;
        }
        return null;
    }

    private String displayName(String vocabId) {
        VocabularyEntry entry = findEntry(vocabId);
        return entry == null ? "" : vocabulary.displayName(entry);
    }

    private String segmentMirror(VocabularyEntry entry) {
        if (entry == null) return "";
        if (VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
            if (!entry.codes.isEmpty()) return entry.codes.get(0);
            if (!entry.meanings.isEmpty()) return entry.meanings.get(0);
            return "〔图片〕";
        }
        return vocabulary.displayName(entry);
    }

    private String sentenceText() {
        StringBuilder builder = new StringBuilder();
        for (ProjectStructure.Question.Segment segment : segments) {
            if (segment.word) {
                String name = segmentMirror(findEntry(segment.vocabId));
                if (name.isEmpty()) continue;
                if (builder.length() > 0) builder.append(' ');
                builder.append(name);
            } else {
                builder.append(segment.text);
            }
        }
        if (builder.length() == 0) builder.append(question.sentence);
        return builder.toString();
    }

    private void renderParts() {
        partsContainer.removeAllViews();
        parts.clear();
        for (ProjectStructure.Question.Segment segment : segments) {
            if (segment.word) parts.add(segment.vocabId);
        }
        sentenceView.setText(segments.isEmpty() ? "尚未选择词汇" : sentenceText());
        for (int i = 0; i < segments.size(); i++) {
            final int index = i;
            ProjectStructure.Question.Segment segment = segments.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            VocabularyEntry segmentEntry = segment.word ? findEntry(segment.vocabId) : null;
            if (segmentEntry != null && VocabularyEntry.TYPE_IMAGE.equals(segmentEntry.type)) {
                TextView number = new TextView(this);
                number.setText((i + 1) + ".");
                number.setTextSize(14f);
                number.setTextColor(getColor(R.color.forge_text));
                row.addView(number, new LinearLayout.LayoutParams(dp(28), dp(40)));
                android.widget.ImageView thumb = new android.widget.ImageView(this);
                android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory,
                        segmentEntry.image);
                if (bitmap != null) {
                    thumb.setImageBitmap(bitmap);
                    thumb.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    row.addView(thumb, new LinearLayout.LayoutParams(dp(28), dp(28)));
                } else {
                    row.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                }
                android.widget.Space spacer = new android.widget.Space(this);
                row.addView(spacer, new LinearLayout.LayoutParams(0, dp(40), 1f));
            } else {
                TextView name = new TextView(this);
                name.setText(segment.word
                        ? (i + 1) + ". " + displayName(segment.vocabId)
                        : (i + 1) + ". 标点 " + segment.text);
                name.setTextSize(14f);
                name.setTextColor(getColor(segment.word ? R.color.forge_text : R.color.forge_accent));
                row.addView(name, new LinearLayout.LayoutParams(0, dp(40), 1f));
            }
            ForgeButton up = small("↑");
            up.setOnClickListener(v -> movePart(index, -1));
            row.addView(up, new LinearLayout.LayoutParams(dp(32), dp(32)));
            ForgeButton down = small("↓");
            down.setOnClickListener(v -> movePart(index, 1));
            row.addView(down, new LinearLayout.LayoutParams(dp(32), dp(32)));
            ForgeButton remove = small("×");
            remove.setOnClickListener(v -> {
                segments.remove(index);
                dirty = true;
                renderParts();
            });
            row.addView(remove, new LinearLayout.LayoutParams(dp(32), dp(32)));
            partsContainer.addView(row);
        }
    }

    private void movePart(int index, int delta) {
        int target = index + delta;
        if (target < 0 || target >= segments.size()) return;
        ProjectStructure.Question.Segment value = segments.remove(index);
        segments.add(target, value);
        dirty = true;
        renderParts();
    }

    private ForgeButton small(String text) {
        ForgeButton button = new ForgeButton(this);
        button.setText(text);
        button.setTextSize(12f);
        button.setVariant(ForgeButton.VARIANT_GHOST);
        return button;
    }

    private void toggleRecord() {
        if (recording) {
            stopRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 41);
            return;
        }
        startRecording();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 41) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startRecording();
            } else {
                toast("没有麦克风权限，无法录音");
            }
        }
    }

    private void startRecording() {
        stopPlayback();
        File output = MediaFiles.cacheAudioFile(this, ".m4a");
        if (output == null) {
            toast("无法创建录音文件");
            return;
        }
        MediaRecorder mediaRecorder = new MediaRecorder();
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioEncodingBitRate(96000);
            mediaRecorder.setAudioSamplingRate(44100);
            mediaRecorder.setOutputFile(output.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
        } catch (Exception e) {
            try { mediaRecorder.release(); } catch (Exception ignored) {}
            MediaFiles.deleteQuietly(output);
            toast("无法开始录音（设备可能无麦克风）");
            return;
        }
        recorder = mediaRecorder;
        recordFile = output;
        recordStart = System.currentTimeMillis();
        recording = true;
        dirty = true;
        ticker = new Handler(Looper.getMainLooper());
        tickerTask = new Runnable() {
            @Override public void run() {
                if (recording) {
                    recordButton.setText("停止 " + ((System.currentTimeMillis() - recordStart) / 1000) + "s");
                    ticker.postDelayed(this, 500);
                }
            }
        };
        ticker.post(tickerTask);
        updateAudioUi();
    }

    private void stopRecording() {
        if (!recording) return;
        recording = false;
        if (ticker != null && tickerTask != null) ticker.removeCallbacks(tickerTask);
        boolean failed = false;
        try { recorder.stop(); } catch (Exception e) { failed = true; }
        try { recorder.release(); } catch (Exception ignored) {}
        recorder = null;
        File file = recordFile;
        recordFile = null;
        if (failed || file == null || !file.isFile()) {
            MediaFiles.deleteQuietly(file);
            toast("录音失败");
            updateAudioUi();
            return;
        }
        long duration = MediaFiles.audioDuration(file);
        if (duration <= 0) duration = Math.max(1, System.currentTimeMillis() - recordStart);
        if (pendingAudio != null) MediaFiles.deleteQuietly(new File(pendingAudio));
        pendingAudio = file.getAbsolutePath();
        pendingDuration = duration;
        audioStart = 0;
        audioEnd = duration;
        audioRemoved = false;
        toast("录音已就绪，保存后写入项目");
        updateAudioUi();
    }

    private void updateAudioUi() {
        if (recording) {
            audioHint.setText("正在录音…");
            recordButton.setText("停止录制");
            return;
        }
        recordButton.setText("录制");
        String path = pendingAudio != null ? pendingAudio : existingAudio;
        if (audioRemoved) path = "";
        if (path == null || path.isEmpty()) {
            audioHint.setText("可选；录制整句示例发音。无麦克风时会提示但不影响保存。");
            return;
        }
        long duration = pendingAudio != null ? pendingDuration : existingDuration;
        audioHint.setText("音频：" + new File(path).getName() + " · "
                + String.format(Locale.ROOT, "%.1fs", Math.max(0, duration) / 1000.0));
        updateTrimUi(duration);
    }

    private void updateTrimUi() {
        updateTrimUi(pendingAudio != null ? pendingDuration : existingDuration);
    }

    private void updateTrimUi(long duration) {
        if (audioTrimText == null) return;
        boolean hasAudio = duration > 0 && currentAudioFile() != null;
        if (!hasAudio) {
            audioTrimText.setText("裁剪：未录制");
            updatingTrim = true;
            startSeek.setMax(1);
            startSeek.setProgress(0);
            endSeek.setMax(1);
            endSeek.setProgress(1);
            startSeek.setEnabled(false);
            endSeek.setEnabled(false);
            updatingTrim = false;
            return;
        }
        audioStart = Math.max(0, Math.min(audioStart, duration));
        audioEnd = Math.max(audioStart, Math.min(audioEnd, duration));
        if (audioEnd - audioStart < 100) audioEnd = Math.min(duration, audioStart + 100);
        updatingTrim = true;
        startSeek.setMax((int) duration);
        endSeek.setMax((int) duration);
        startSeek.setProgress((int) audioStart);
        endSeek.setProgress((int) audioEnd);
        startSeek.setEnabled(true);
        endSeek.setEnabled(true);
        updatingTrim = false;
        audioTrimText.setText("裁剪：" + formatMs(audioStart) + " → " + formatMs(audioEnd)
                + "（至少 100ms）");
    }

    private String formatMs(long ms) {
        return String.format(Locale.ROOT, "%.1fs", ms / 1000.0);
    }

    private File currentAudioFile() {
        if (audioRemoved) return null;
        String path = pendingAudio != null ? pendingAudio : existingAudio;
        if (path == null || path.isEmpty()) return null;
        if (pendingAudio != null) return new File(path);
        return MediaFiles.resolveProjectFile(projectDirectory, path);
    }

    private void playAudio() {
        if (recording) {
            toast("请先停止录音");
            return;
        }
        File file = currentAudioFile();
        if (file == null || !file.isFile()) {
            toast("没有可播放的音频");
            return;
        }
        stopPlayback();
        try {
            player = new MediaPlayer();
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.seekTo((int) Math.max(0, audioStart));
            player.start();
            if (playHandler == null) playHandler = new Handler(Looper.getMainLooper());
            if (playStopTask != null) playHandler.removeCallbacks(playStopTask);
            long length = Math.max(100, audioEnd - audioStart);
            playStopTask = () -> stopPlayback();
            playHandler.postDelayed(playStopTask, length);
        } catch (Exception e) {
            stopPlayback();
            toast("播放失败（设备可能没有音频输出）");
        }
    }

    private void stopPlayback() {
        if (playHandler != null && playStopTask != null) {
            playHandler.removeCallbacks(playStopTask);
            playStopTask = null;
        }
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
            player = null;
        }
    }

    private void removeAudio() {
        stopPlayback();
        if (pendingAudio != null) {
            MediaFiles.deleteQuietly(new File(pendingAudio));
            pendingAudio = null;
        }
        pendingDuration = 0;
        existingAudio = "";
        existingDuration = 0;
        audioStart = 0;
        audioEnd = 0;
        audioRemoved = true;
        dirty = true;
        updateAudioUi();
    }

    private void openTypePromptPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择题干词汇");
        typePickerLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> typePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> selected = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (selected == null) return;
                for (String id : selected) {
                    if (id == null) continue;
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.kind = "vocab";
                    item.vocabId = id;
                    editPromptItems.add(item);
                }
                String mirrored = mirrorListenOption(editPromptItems);
                typePrompt.setText(mirrored);
                typePrompt.setSelection(mirrored.length());
                renderTypePromptPreview();
                dirty = true;
            });

    private void renderTypePromptPreview() {
        if (typePromptPreview == null) return;
        typePromptPreview.removeAllViews();
        for (int i = 0; i < editPromptItems.size(); i++) {
            ProjectStructure.Question.OptionItem item = editPromptItems.get(i);
            final int index = i;
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = null;
                for (VocabularyEntry candidate : vocabulary.entries) {
                    if (item.vocabId.equals(candidate.id)) { entry = candidate; break; }
                }
                if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                    android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
                    if (bitmap != null) {
                        android.widget.ImageView image = new android.widget.ImageView(this);
                        image.setImageBitmap(bitmap);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        typePromptPreview.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
                    } else {
                        typePromptPreview.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                    }
                } else if (entry != null) {
                    typePromptPreview.addView(text(vocabulary.displayName(entry), 14, R.color.forge_text));
                }
            } else {
                typePromptPreview.addView(text(item.text, 14, R.color.forge_text));
            }
            ForgeButton remove = new ForgeButton(this);
            remove.setAllCaps(false);
            remove.setText("×");
            remove.setOnClickListener(v -> {
                editPromptItems.remove(index);
                String mirrored = mirrorListenOption(editPromptItems);
                typePrompt.setText(mirrored);
                renderTypePromptPreview();
                dirty = true;
            });
            typePromptPreview.addView(remove);
        }
    }

    private void openPunctuationDialog() {
        String[] marks = {"。", "，", "、", "！", "？", "；", "：", "“”", "（）"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("插入标点")
                .setItems(marks, (dialog, which) -> {
                    String text = typePrompt.getText().toString();
                    int cursor = Math.max(0, typePrompt.getSelectionStart());
                    StringBuilder builder = new StringBuilder(text);
                    String mark = marks[which];
                    builder.insert(cursor, mark);
                    typePrompt.setText(builder.toString());
                    typePrompt.setSelection(cursor + mark.length());
                    dirty = true;
                }).show();
    }

    private void renderWarnRows() {
        if (typeWarnRows == null) return;
        typeWarnRows.removeAllViews();
        for (int i = 0; i < editWarn.size(); i++) {
            final int index = i;
            ProjectStructure.Question.WarnRule rule = editWarn.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            EditText match = new EditText(this);
            match.setHint("触发写法");
            match.setText(rule.match);
            match.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) { rule.match = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s) {}
            });
            row.addView(match);
            LinearLayout modeRow = new LinearLayout(this);
            android.widget.Spinner kind = new android.widget.Spinner(this);
            ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_spinner_item, new String[]{"eq 完全相同", "has 包含"});
            kind.setAdapter(adapter);
            kind.setSelection("has".equals(rule.kind) ? 1 : 0);
            kind.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                    rule.kind = pos == 1 ? "has" : "eq";
                    dirty = true;
                }
                public void onNothingSelected(android.widget.AdapterView<?> p) {}
            });
            modeRow.addView(kind);
            EditText hint = new EditText(this);
            hint.setHint("提示语（可空）");
            hint.setText(rule.text);
            hint.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) { rule.text = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s) {}
            });
            modeRow.addView(hint, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton remove = new ForgeButton(this);
            remove.setAllCaps(false);
            remove.setText("×");
            remove.setOnClickListener(v -> {
                editWarn.remove(index);
                renderWarnRows();
                dirty = true;
            });
            modeRow.addView(remove);
            row.addView(modeRow);
            typeWarnRows.addView(row);
        }
    }

    private void openArrangePicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择词块词汇");
        ArrayList<String> selected = new ArrayList<>();
        for (ProjectStructure.Question.Block block : editBlocks) {
            if (!block.vocabId.isEmpty()) selected.add(block.vocabId);
        }
        intent.putStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS, selected);
        arrangePickerLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> arrangePickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> selected = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (selected == null) return;
                int skipped = 0;
                for (String id : selected) {
                    if (id == null || id.isEmpty()) continue;
                    boolean exists = false;
                    for (ProjectStructure.Question.Block block : editBlocks) {
                        if (id.equals(block.vocabId)) { exists = true; break; }
                    }
                    if (exists) { skipped++; continue; }
                    VocabularyEntry entry = null;
                    for (VocabularyEntry candidate : vocabulary.entries) {
                        if (id.equals(candidate.id)) { entry = candidate; break; }
                    }
                    if (entry == null) continue;
                            ProjectStructure.Question.Block block = new ProjectStructure.Question.Block(
                            segmentMirror(entry), false, id);
                    block.id = java.util.UUID.randomUUID().toString();
                    editBlocks.add(block);
                }
                if (skipped > 0) toast("已跳过重复词汇块");
                renderArrangeBlocks();
                renderArrangeAnswers();
                dirty = true;
            });

    private void renderArrangeBlocks() {
        arrangeBlockRows.removeAllViews();
        for (int i = 0; i < editBlocks.size(); i++) {
            final int index = i;
            ProjectStructure.Question.Block block = editBlocks.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            VocabularyEntry blockEntry = block.vocabId == null ? null : findEntry(block.vocabId);
            if (blockEntry != null && VocabularyEntry.TYPE_IMAGE.equals(blockEntry.type)) {
                android.widget.ImageView thumb = new android.widget.ImageView(this);
                android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory,
                        blockEntry.image);
                if (bitmap != null) {
                    thumb.setImageBitmap(bitmap);
                    thumb.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                    row.addView(thumb, new LinearLayout.LayoutParams(dp(30), dp(30)));
                } else {
                    row.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                }
            }
            EditText text = new EditText(this);
            text.setHint("词块文本");
            text.setText(block.text);
            text.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                    block.text = s.toString();
                    dirty = true;
                }
                @Override public void afterTextChanged(android.text.Editable s) {}
            });
            row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton up = new ForgeButton(this);
            up.setAllCaps(false);
            up.setText("↑");
            up.setOnClickListener(v -> {
                if (index > 0) java.util.Collections.swap(editBlocks, index, index - 1);
                renderArrangeBlocks();
            });
            row.addView(up);
            ForgeButton remove = new ForgeButton(this);
            remove.setAllCaps(false);
            remove.setText("×");
            remove.setOnClickListener(v -> confirmRemoveBlock(index));
            row.addView(remove);
            arrangeBlockRows.addView(row);
        }
    }

    private void applyType(boolean arrange) {
        if (arrangeSection == null) return;
        arrangeSection.setVisibility(arrange ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.question_editor_subtitle)).setText(
                (arrange ? "词块组句" : "选择正确翻译") + " · " + course.name);
        if (editorScrollContent != null) {
            hideChoiceSections(editorScrollContent, arrange);
        }
        View hints = findViewById(R.id.question_hints_enabled);
        if (hints != null) hints.setVisibility(arrange ? View.GONE : View.VISIBLE);
        int[] hide = {R.id.question_sentence, R.id.question_parts,
                R.id.btn_choose_question_parts, R.id.btn_insert_punct};
        for (int id : hide) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(arrange ? View.GONE : View.VISIBLE);
        }
        int[] audioIds = {R.id.question_audio_hint, R.id.question_audio_trim,
                R.id.question_audio_start, R.id.question_audio_end, R.id.btn_question_record,
                R.id.btn_question_play, R.id.btn_question_remove_audio, R.id.btn_question_trim_play};
        for (int id : audioIds) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(arrange ? View.GONE : View.VISIBLE);
        }
        for (int i = 0; i < 4; i++) {
            int optionId = getResources().getIdentifier("question_option_" + i, "id", getPackageName());
            int correctId = getResources().getIdentifier("question_correct_" + i, "id", getPackageName());
            View option = findViewById(optionId);
            View correct = findViewById(correctId);
            if (option != null) option.setVisibility(arrange ? View.GONE : View.VISIBLE);
            if (correct != null) correct.setVisibility(arrange ? View.GONE : View.VISIBLE);
            if (choiceOptionExtras[i] != null) {
                choiceOptionExtras[i].setVisibility(arrange ? View.GONE : View.VISIBLE);
            }
        }
    }

    private void hideChoiceSections(View view, boolean arrange) {
        if (view instanceof TextView) {
            String text = ((TextView) view).getText().toString();
            if (text.startsWith("句子词汇") || text.startsWith("四个选项")
                    || text.contains("选择一个正确答案") || text.startsWith("整句录音")
                    || text.startsWith("裁剪：") || text.startsWith("试听裁剪")
                    || text.startsWith("可选；录制") || text.startsWith("尚未选择词汇")) {
                view.setVisibility(arrange ? View.GONE : View.VISIBLE);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                hideChoiceSections(group.getChildAt(i), arrange);
            }
        }
    }

    private void renderMatchRows() {
        if (matchRows == null) return;
        matchRows.removeAllViews();
        matchPairFields.clear();
        for (int i = 0; i < editPairs.size(); i++) {
            final int index = i;
            ProjectStructure.Question.MatchPair pair = editPairs.get(i);
            if (pair.leftItems.isEmpty() && pair.left != null && !pair.left.isEmpty()) {
                ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                item.text = pair.left;
                pair.leftItems.add(item);
            }
            if (pair.rightItems.isEmpty() && pair.right != null && !pair.right.isEmpty()) {
                ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                item.text = pair.right;
                pair.rightItems.add(item);
            }
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            EditText left = new EditText(this);
            left.setHint("左（词形）");
            left.setText(pair.left);
            left.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) { pair.left = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s) {}
            });
            row.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton pickLeft = new ForgeButton(this);
            pickLeft.setAllCaps(false);
            pickLeft.setText("选词左");
            pickLeft.setOnClickListener(v -> {
                matchPickPair = index;
                matchPickSide = "l";
                openMatchOptionPicker();
            });
            row.addView(pickLeft);
            EditText right = new EditText(this);
            right.setHint("右（释义）");
            right.setText(pair.right);
            right.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                public void onTextChanged(CharSequence s, int a, int b, int c) { pair.right = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s) {}
            });
            row.addView(right, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton pickRight = new ForgeButton(this);
            pickRight.setAllCaps(false);
            pickRight.setText("选词右");
            pickRight.setOnClickListener(v -> {
                matchPickPair = index;
                matchPickSide = "r";
                openMatchOptionPicker();
            });
            row.addView(pickRight);
            ForgeButton remove = new ForgeButton(this);
            remove.setAllCaps(false);
            remove.setText("×");
            remove.setOnClickListener(v -> {
                editPairs.remove(index);
                renderMatchRows();
                dirty = true;
            });
            row.addView(remove);
            matchRows.addView(row);
            LinearLayout previews = new LinearLayout(this);
            previews.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout leftPreview = new LinearLayout(this);
            leftPreview.setOrientation(LinearLayout.HORIZONTAL);
            leftPreview.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout rightPreview = new LinearLayout(this);
            rightPreview.setOrientation(LinearLayout.HORIZONTAL);
            rightPreview.setGravity(android.view.Gravity.CENTER_VERTICAL);
            previews.addView(leftPreview, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            previews.addView(rightPreview, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            matchRows.addView(previews);
            matchPairFields.add(new EditText[]{left, right});
            renderMatchSidePreview(index, leftPreview, true);
            renderMatchSidePreview(index, rightPreview, false);
        }
    }

    private void renderMatchSidePreview(int pairIndex, LinearLayout preview, boolean leftSide) {
        preview.removeAllViews();
        ProjectStructure.Question.MatchPair pair = editPairs.get(pairIndex);
        List<ProjectStructure.Question.OptionItem> items =
                leftSide ? pair.leftItems : pair.rightItems;
        for (int j = 0; j < items.size(); j++) {
            ProjectStructure.Question.OptionItem item = items.get(j);
            final int itemIndex = j;
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = findEntry(item.vocabId);
                if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                    android.widget.ImageView image = new android.widget.ImageView(this);
                    android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory,
                            entry.image);
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        preview.addView(image, new LinearLayout.LayoutParams(dp(26), dp(26)));
                    } else {
                        preview.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                    }
                } else if (entry != null) {
                    preview.addView(text(vocabulary.displayName(entry), 13, R.color.forge_text));
                } else {
                    preview.addView(text("〔词汇已删除〕", 12, R.color.forge_warn));
                }
            } else {
                preview.addView(text(item.text, 13, R.color.forge_text));
            }
            ForgeButton remove = new ForgeButton(this);
            remove.setAllCaps(false);
            remove.setText("×");
            remove.setOnClickListener(v -> {
                if (leftSide) pair.leftItems.remove(itemIndex);
                else pair.rightItems.remove(itemIndex);
                renderMatchRows();
                if (pairIndex < matchPairFields.size()) {
                    EditText[] fields = matchPairFields.get(pairIndex);
                    fields[leftSide ? 0 : 1].setText(
                            mirrorListenOption(leftSide ? pair.leftItems : pair.rightItems));
                }
                dirty = true;
            });
            preview.addView(remove);
        }
    }

    private void openMatchOptionPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "为配对选择词汇");
        matchOptionLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> matchOptionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> ids = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (ids == null || ids.isEmpty()) return;
                int target = matchPickPair;
                if (target < 0 || target >= editPairs.size()) return;
                ProjectStructure.Question.MatchPair pair = editPairs.get(target);
                List<ProjectStructure.Question.OptionItem> items =
                        "l".equals(matchPickSide) ? pair.leftItems : pair.rightItems;
                for (String id : ids) {
                    VocabularyEntry entry = findEntry(id);
                    if (entry == null) continue;
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.kind = "vocab";
                    item.vocabId = entry.id;
                    items.add(item);
                }
                String mirrored = mirrorListenOption(items);
                if ("l".equals(matchPickSide)) pair.left = mirrored;
                else pair.right = mirrored;
                renderMatchRows();
                matchPickPair = -1;
                matchPickSide = "l";
                dirty = true;
            });

    private void showListenAudio() {
        hideForMatch(true);
        int[] audioIds = {R.id.question_audio_hint, R.id.question_audio_trim,
            R.id.question_audio_start, R.id.question_audio_end, R.id.btn_question_record,
            R.id.btn_question_play, R.id.btn_question_remove_audio, R.id.btn_question_trim_play};
        for (int id : audioIds) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(View.VISIBLE);
        }
        int[] hideIds = {R.id.btn_choose_question_parts, R.id.btn_insert_punct};
        for (int id : hideIds) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }
        for (int i = 0; i < 4; i++) {
            int optionId = getResources().getIdentifier("question_option_" + i, "id", getPackageName());
            View option = findViewById(optionId);
            if (option != null) option.setVisibility(View.GONE);
        }
        if (hintsEnabledView != null) hintsEnabledView.setVisibility(View.GONE);
    }

    private void hideForMatch(boolean match) {
        if (editorScrollContent != null) hideChoiceSections(editorScrollContent, match);
        if (hintsEnabledView != null) hintsEnabledView.setVisibility(match ? View.GONE : View.VISIBLE);
        int[] audioIds = {R.id.question_audio_hint, R.id.question_audio_trim,
            R.id.question_audio_start, R.id.question_audio_end, R.id.btn_question_record,
            R.id.btn_question_play, R.id.btn_question_remove_audio, R.id.btn_question_trim_play};
        for (int id : audioIds) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(match ? View.GONE : View.VISIBLE);
        }
        int[] choiceIds = {R.id.btn_choose_question_parts, R.id.btn_insert_punct};
        for (int id : choiceIds) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(match ? View.GONE : View.VISIBLE);
        }
        for (int i = 0; i < 4; i++) {
            int optionId = getResources().getIdentifier("question_option_" + i, "id", getPackageName());
            int correctId = getResources().getIdentifier("question_correct_" + i, "id", getPackageName());
            View option = findViewById(optionId);
            View correct = findViewById(correctId);
            if (option != null) option.setVisibility(match ? View.GONE : View.VISIBLE);
            if (correct != null) correct.setVisibility(match ? View.GONE : View.VISIBLE);
            if (choiceOptionExtras[i] != null) {
                choiceOptionExtras[i].setVisibility(match ? View.GONE : View.VISIBLE);
            }
        }
    }

    private String mirrorListenOption(List<ProjectStructure.Question.OptionItem> items) {
        StringBuilder builder = new StringBuilder();
        for (ProjectStructure.Question.OptionItem item : items) {
            String value;
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = null;
                for (VocabularyEntry candidate : vocabulary.entries) {
                    if (item.vocabId.equals(candidate.id)) { entry = candidate; break; }
                }
                if (entry == null) continue;
                if (VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                    if (!entry.codes.isEmpty()) value = entry.codes.get(0);
                    else if (!entry.meanings.isEmpty()) value = entry.meanings.get(0);
                    else value = "〔图片〕";
                } else {
                    value = vocabulary.displayName(entry);
                }
            } else {
                value = item.text;
            }
            if (builder.length() > 0 && !value.isEmpty()
                    && !Character.isWhitespace(builder.charAt(builder.length() - 1))
                    && !Character.isWhitespace(value.charAt(0))
                    && !isPunctuationChar(builder.charAt(builder.length() - 1))
                    && !isPunctuationChar(value.charAt(0))) {
                builder.append(' ');
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private boolean isPunctuationChar(char value) {
        int type = Character.getType(value);
        return type == Character.OTHER_PUNCTUATION || type == Character.CONNECTOR_PUNCTUATION
                || type == Character.DASH_PUNCTUATION || type == Character.START_PUNCTUATION
                || type == Character.END_PUNCTUATION
                || type == Character.INITIAL_QUOTE_PUNCTUATION
                || type == Character.FINAL_QUOTE_PUNCTUATION;
    }

    private void renderListenOptionPreviews() {
        for (int i = 0; i < 4; i++) {
            LinearLayout preview = findViewById(3000 + i);
            if (preview == null) continue;
            renderOptionPreviewItems(listenOptionItems.get(i), listenOptions[i], preview,
                    this::renderListenOptionPreviews);
        }
    }

    private void renderOptionPreviewItems(List<ProjectStructure.Question.OptionItem> items,
                                          EditText mirrorField, LinearLayout preview,
                                          Runnable rerender) {
        preview.removeAllViews();
        for (int j = 0; j < items.size(); j++) {
            final int index = j;
            ProjectStructure.Question.OptionItem item = items.get(j);
            if ("vocab".equals(item.kind)) {
                VocabularyEntry entry = findEntry(item.vocabId);
                if (entry != null && VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                    android.widget.ImageView image = new android.widget.ImageView(this);
                    android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory,
                            entry.image);
                    if (bitmap != null) {
                        image.setImageBitmap(bitmap);
                        image.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                        preview.addView(image, new LinearLayout.LayoutParams(dp(28), dp(28)));
                    } else {
                        preview.addView(text("〔图片缺失〕", 12, R.color.forge_warn));
                    }
                } else if (entry != null) {
                    preview.addView(text(vocabulary.displayName(entry), 14, R.color.forge_text));
                } else {
                    preview.addView(text("〔词汇已删除〕", 12, R.color.forge_warn));
                }
            } else {
                EditText fragment = new EditText(this);
                fragment.setText(item.text);
                fragment.setTextSize(13f);
                fragment.setMinWidth(dp(48));
                fragment.setPadding(dp(4), 0, dp(4), 0);
                fragment.addTextChangedListener(new android.text.TextWatcher() {
                    public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
                    public void onTextChanged(CharSequence s, int a, int b, int c) {
                        item.text = s.toString();
                        mirrorField.setText(mirrorListenOption(items));
                        dirty = true;
                    }
                    public void afterTextChanged(android.text.Editable s) {}
                });
                preview.addView(fragment);
            }
            ForgeButton remove = small("×");
            remove.setOnClickListener(v -> {
                items.remove(index);
                mirrorField.setText(mirrorListenOption(items));
                rerender.run();
                dirty = true;
            });
            preview.addView(remove);
        }
        ForgeButton addText = small("＋文本");
        addText.setOnClickListener(v -> {
            ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
            item.text = "";
            items.add(item);
            mirrorField.setText(mirrorListenOption(items));
            rerender.run();
            dirty = true;
        });
        preview.addView(addText);
        ForgeButton punct = small("标点");
        punct.setOnClickListener(v -> showOptionPunctuationMenu(v, items, mirrorField, rerender));
        preview.addView(punct);
    }

    private void showOptionPunctuationMenu(View anchor,
                                           List<ProjectStructure.Question.OptionItem> items,
                                           EditText mirrorField, Runnable rerender) {
        ForgeMenu menu = new ForgeMenu(this);
        menu.addHeader("插入标点");
        String[] marks = {"，", "。", "、", "？", "！", "；", "：", "“", "”", "（", "）", "…", "—", "/"};
        for (String mark : marks) {
            menu.addItem(mark, () -> {
                ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                item.text = mark;
                items.add(item);
                mirrorField.setText(mirrorListenOption(items));
                rerender.run();
                dirty = true;
            });
        }
        menu.show(anchor);
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(getColor(color));
        return view;
    }

    private void openListenOptionPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "为选项选择词汇");
        listenOptionLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> listenOptionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> ids = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (ids == null || ids.isEmpty()) return;
                int target = listenOptionPickIndex;
                if (target < 0 || target >= listenOptions.length) return;
                List<ProjectStructure.Question.OptionItem> items = listenOptionItems.get(target);
                for (String id : ids) {
                    VocabularyEntry entry = null;
                    for (VocabularyEntry candidate : vocabulary.entries) {
                        if (id != null && id.equals(candidate.id)) { entry = candidate; break; }
                    }
                    if (entry == null) continue;
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.kind = "vocab";
                    item.vocabId = id;
                    items.add(item);
                }
                listenOptions[target].setText(mirrorListenOption(items));
                renderListenOptionPreviews();
                dirty = true;
                listenOptionPickIndex = -1;
            });

    private boolean containsVocabItem(List<ProjectStructure.Question.OptionItem> items) {
        for (ProjectStructure.Question.OptionItem item : items) {
            if ("vocab".equals(item.kind)) return true;
        }
        return false;
    }

    private void renderChoiceOptionPreviews() {
        for (int i = 0; i < 4; i++) {
            LinearLayout preview = choiceOptionPreviews[i];
            if (preview == null) continue;
            renderOptionPreviewItems(choiceOptionItems.get(i), optionFields[i].getEditText(),
                    preview, this::renderChoiceOptionPreviews);
        }
    }

    private void openChoiceOptionPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "为选项选择词汇");
        choiceOptionLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> choiceOptionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> ids = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (ids == null || ids.isEmpty()) return;
                int target = choiceOptionPickIndex;
                if (target < 0 || target >= 4) return;
                List<ProjectStructure.Question.OptionItem> items = choiceOptionItems.get(target);
                for (String id : ids) {
                    VocabularyEntry entry = findEntry(id);
                    if (entry == null) continue;
                    ProjectStructure.Question.OptionItem item = new ProjectStructure.Question.OptionItem();
                    item.kind = "vocab";
                    item.vocabId = entry.id;
                    items.add(item);
                }
                optionFields[target].setText(mirrorListenOption(items));
                renderChoiceOptionPreviews();
                dirty = true;
                choiceOptionPickIndex = -1;
            });

    private void openListenAudioPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择示例音频词汇");
        listenAudioLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> listenAudioLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> ids = result.getData()
                        .getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (ids == null || ids.isEmpty()) return;
                for (VocabularyEntry entry : vocabulary.entries) {
                    if (!ids.get(0).equals(entry.id)) continue;
                    if (entry.audio == null || entry.audio.isEmpty()) {
                        toast("该词汇没有音频，已保持原音频");
                        return;
                    }
                    if (pendingAudio != null) {
                        MediaFiles.deleteQuietly(new java.io.File(pendingAudio));
                        pendingAudio = null;
                    }
                    existingAudio = entry.audio;
                    existingDuration = entry.audioDuration;
                    audioRelative = entry.audio;
                    audioStart = entry.audioStart;
                    audioEnd = entry.audioEnd > 0 ? entry.audioEnd : entry.audioDuration;
                    audioRemoved = false;
                    audioSource = "vocab";
                    updateAudioUi();
                    listenAudioLabel.setText(audioRelative + "  " + audioStart + ".." + audioEnd);
                    dirty = true;
                    return;
                }
            });

    private void saveListen() {
        String prompt = listenPrompt.getText() == null ? "" : listenPrompt.getText().toString().trim();
        String path = audioRemoved ? "" : existingAudio;
        if (pendingAudio != null) {
            try {
                java.io.File copied = MediaFiles.copyAudioToProject(projectDirectory,
                        new java.io.File(pendingAudio), question.id);
                path = MediaFiles.relativePath(projectDirectory, copied);
                audioSource = "recording";
            } catch (Exception ex) {
                toast("保存录音失败");
                return;
            }
        }
        if (path.isEmpty()) { toast("请先选择或录制音频"); return; }
        java.io.File audioFile = MediaFiles.resolveProjectFile(projectDirectory, path);
        if (!audioFile.exists()) { toast("音频文件不存在，请重新选择"); return; }
        List<String> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String value = listenOptions[i].getText().toString().trim();
            if (value.isEmpty()) { toast("四个选项都不能为空"); return; }
            options.add(value);
        }
        int correct = -1;
        int checked = listenCorrect.getCheckedRadioButtonId();
        if (checked >= 1000 && checked < 1004) correct = checked - 1000;
        if (correct < 0) { toast("请选择一个正确答案"); return; }
        question.type = ProjectStructure.Question.TYPE_LISTEN;
        question.prompt = prompt;
        question.audio = path;
        question.audioDuration = pendingAudio != null ? pendingDuration : existingDuration;
        question.audioStart = Math.max(0, audioStart);
        question.audioEnd = Math.max(question.audioStart, audioEnd);
        question.audioSource = audioSource;
        question.options.clear();
        question.options.addAll(options);
        question.optionItems.clear();
        for (int i = 0; i < 4; i++) {
            List<ProjectStructure.Question.OptionItem> items = listenOptionItems.get(i);
            String mirror = mirrorListenOption(items);
            if (!mirror.equals(options.get(i))) {
                ProjectStructure.Question.OptionItem textItem = new ProjectStructure.Question.OptionItem();
                textItem.text = options.get(i);
                items = new ArrayList<>();
                items.add(textItem);
            }
            question.optionItems.add(items);
        }
        boolean hasVocabItem = false;
        for (List<ProjectStructure.Question.OptionItem> group : question.optionItems) {
            for (ProjectStructure.Question.OptionItem item : group) {
                if ("vocab".equals(item.kind)) { hasVocabItem = true; break; }
            }
            if (hasVocabItem) break;
        }
        if (!hasVocabItem) question.optionItems.clear();
        question.correctIndex = correct;
        question.hintsEnabled = false;
        question.name = (prompt.isEmpty() ? "听音选择" : prompt) + " · " + options.get(correct);
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true;
        toast("已保存听音题");
        finish();
    }

    private void renderCardWordPreview() {
        if (cardWordPreview == null) return;
        cardWordPreview.removeAllViews();
        VocabularyEntry entry = question.card.wordVocabId.isEmpty() ? null : findEntry(question.card.wordVocabId);
        if (entry == null || !VocabularyEntry.TYPE_IMAGE.equals(entry.type)) return;
        android.graphics.Bitmap bitmap = MediaFiles.loadProjectImage(projectDirectory, entry.image);
        if (bitmap == null) { cardWordPreview.addView(text("〔图片缺失〕", 12, R.color.forge_warn)); return; }
        android.widget.ImageView image = new android.widget.ImageView(this);
        image.setImageBitmap(bitmap); image.setAdjustViewBounds(true);
        int h = dp(32);
        int w = Math.max(dp(32), bitmap.getWidth() * h / Math.max(1, bitmap.getHeight()));
        cardWordPreview.addView(image, new LinearLayout.LayoutParams(w, h));
    }

    private void renderCardExamples() {
        if (cardExamples == null) return;
        cardExamples.removeAllViews();
        for (int i = 0; i < editExamples.size(); i++) {
            final int index = i;
            ProjectStructure.Question.CardExample example = editExamples.get(i);
            LinearLayout row = new LinearLayout(this);
            EditText l = new EditText(this); l.setHint("例句原文"); l.setText(example.l);
            l.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){ example.l=s.toString(); }
                public void afterTextChanged(android.text.Editable s){}
            });
            row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            EditText z = new EditText(this); z.setHint("例句翻译"); z.setText(example.z);
            z.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){ example.z=s.toString(); }
                public void afterTextChanged(android.text.Editable s){}
            });
            row.addView(z, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton remove = new ForgeButton(this); remove.setAllCaps(false); remove.setText("×");
            remove.setOnClickListener(v -> { editExamples.remove(index); renderCardExamples(); });
            row.addView(remove);
            cardExamples.addView(row);
        }
    }

    private void openCardPicker() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, projectDirectory.getName());
        intent.putExtra(VocabularyPickerActivity.EXTRA_TITLE, "选择卡片词汇");
        cardPickerLauncher.launch(intent);
    }

    private final androidx.activity.result.ActivityResultLauncher<Intent> cardPickerLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                ArrayList<String> ids = result.getData().getStringArrayListExtra(VocabularyPickerActivity.EXTRA_SELECTED_IDS);
                if (ids == null || ids.isEmpty()) return;
                for (VocabularyEntry entry : vocabulary.entries) {
                    if (!ids.get(0).equals(entry.id)) continue;
                    String word;
                    if (VocabularyEntry.TYPE_IMAGE.equals(entry.type)) {
                        word = !entry.codes.isEmpty() ? entry.codes.get(0)
                                : !entry.meanings.isEmpty() ? entry.meanings.get(0) : "〔图片〕";
                        question.card.wordVocabId = entry.id;
                    } else {
                        word = vocabulary.displayName(entry);
                        question.card.wordVocabId = "";
                    }
                    cardWord.setText(word);
                    cardPron.setText(String.join(" / ", entry.phonetics.isEmpty()
                            ? java.util.Collections.singletonList(entry.phonetic) : entry.phonetics));
                    StringBuilder def = new StringBuilder();
                    if (entry.posMeanings.isEmpty()) {
                        def.append(String.join("；", entry.meanings));
                    } else {
                        for (VocabularyEntry.PosMeaning group : entry.posMeanings) {
                            if (def.length() > 0) def.append("；");
                            PosEntry pos = posStore.findById(group.posId);
                            if (pos != null && !pos.name.isEmpty()) {
                                def.append(pos.name).append("：");
                            }
                            def.append(String.join("、", group.meanings));
                        }
                    }
                    cardDef.setText(def.toString());
                    renderCardWordPreview();
                    return;
                }
            });

    private void renderDialogLines() {
        if (dialogLines == null) return;
        dialogLines.removeAllViews();
        for (int i = 0; i < editLines.size(); i++) {
            final int index = i;
            ProjectStructure.Question.DialogLine line = editLines.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            EditText who = new EditText(this);
            who.setHint("角色");
            who.setText(line.who);
            who.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){ line.who = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s){}
            });
            row.addView(who, new LinearLayout.LayoutParams(dp(84),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            EditText text = new EditText(this);
            text.setHint("台词（留空 = 待补全）");
            text.setText(line.text);
            text.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence s,int a,int b,int c){}
                public void onTextChanged(CharSequence s,int a,int b,int c){ line.text = s.toString(); dirty = true; }
                public void afterTextChanged(android.text.Editable s){}
            });
            row.addView(text, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            ForgeButton remove = small("×");
            remove.setOnClickListener(v -> {
                editLines.remove(index);
                renderDialogLines();
                dirty = true;
            });
            row.addView(remove);
            dialogLines.addView(row);
        }
    }

    private void showDialogEditor() {
        hideChoiceSections(editorScrollContent, true);
        int[] hidden = {R.id.question_sentence, R.id.question_parts,
                R.id.btn_choose_question_parts, R.id.btn_insert_punct,
                R.id.question_hints_enabled, R.id.question_audio_hint,
                R.id.question_audio_trim, R.id.question_audio_start,
                R.id.question_audio_end, R.id.btn_question_record, R.id.btn_question_play,
                R.id.btn_question_remove_audio, R.id.btn_question_trim_play};
        for (int id : hidden) {
            View view = findViewById(id);
            if (view != null) view.setVisibility(View.GONE);
        }
        for (int i = 0; i < 4; i++) {
            int optionId = getResources().getIdentifier("question_option_" + i, "id", getPackageName());
            int correctId = getResources().getIdentifier("question_correct_" + i, "id", getPackageName());
            View option = findViewById(optionId);
            View correct = findViewById(correctId);
            if (option != null) option.setVisibility(View.VISIBLE);
            if (correct != null) correct.setVisibility(View.VISIBLE);
            if (choiceOptionExtras[i] != null) choiceOptionExtras[i].setVisibility(View.VISIBLE);
        }
        showTextView(editorScrollContent, "四个选项");
    }

    private void showTextView(View view, String prefix) {
        if (view instanceof TextView) {
            if (((TextView) view).getText().toString().startsWith(prefix)) {
                view.setVisibility(View.VISIBLE);
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                showTextView(group.getChildAt(i), prefix);
            }
        }
    }

    private void saveDialog() {
        String prompt = dialogPrompt.getText() == null ? "" : dialogPrompt.getText().toString().trim();
        List<ProjectStructure.Question.DialogLine> lines = new ArrayList<>();
        boolean hasLineText = false;
        for (ProjectStructure.Question.DialogLine line : editLines) {
            String who = line.who == null ? "" : line.who.trim();
            String text = line.text == null ? "" : line.text.trim();
            if (who.isEmpty() && text.isEmpty()) continue;
            ProjectStructure.Question.DialogLine value = new ProjectStructure.Question.DialogLine(who, text);
            lines.add(value);
            if (!text.isEmpty()) hasLineText = true;
        }
        if (!hasLineText) { toast("对话里至少需要一句台词"); return; }
        List<String> options = new ArrayList<>();
        for (ForgeTextField field : optionFields) {
            String value = field.getText() == null ? "" : field.getText().toString().trim();
            if (value.isEmpty()) { toast("四个选项都必须填写"); return; }
            options.add(value);
        }
        int correct = -1;
        for (int i = 0; i < correctButtons.length; i++) {
            if (correctButtons[i].isChecked()) { correct = i; break; }
        }
        if (correct < 0) { toast("请选择一个正确答案"); return; }
        question.type = ProjectStructure.Question.TYPE_DIALOG;
        question.prompt = prompt;
        question.lines.clear();
        question.lines.addAll(lines);
        question.options.clear();
        question.options.addAll(options);
        question.optionItems.clear();
        for (int i = 0; i < 4; i++) {
            List<ProjectStructure.Question.OptionItem> items =
                    new ArrayList<>(choiceOptionItems.get(i));
            if (!mirrorListenOption(items).equals(options.get(i))) {
                ProjectStructure.Question.OptionItem textItem =
                        new ProjectStructure.Question.OptionItem();
                textItem.text = options.get(i);
                items.clear();
                items.add(textItem);
            }
            question.optionItems.add(items);
        }
        boolean hasVocabOption = false;
        for (List<ProjectStructure.Question.OptionItem> group : question.optionItems) {
            for (ProjectStructure.Question.OptionItem item : group) {
                if ("vocab".equals(item.kind)) { hasVocabOption = true; break; }
            }
            if (hasVocabOption) break;
        }
        if (!hasVocabOption) question.optionItems.clear();
        question.correctIndex = correct;
        question.hintsEnabled = false;
        String fallback = lines.isEmpty() ? "" : lines.get(0).text;
        question.name = prompt.isEmpty() ? (fallback.isEmpty() ? "选择对话" : fallback) : prompt;
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true;
        toast("已保存对话题");
        finish();
    }

    private void saveCard() {
        String word = cardWord.getText().toString().trim();
        String def = cardDef.getText().toString().trim();
        if (word.isEmpty() && def.isEmpty()) { toast("词形或释义至少填写一个"); return; }
        question.type = ProjectStructure.Question.TYPE_CARD;
        VocabularyEntry wordEntry = question.card.wordVocabId.isEmpty()
                ? null : findEntry(question.card.wordVocabId);
        if (wordEntry == null || !VocabularyEntry.TYPE_IMAGE.equals(wordEntry.type)
                || !segmentMirror(wordEntry).equals(word)) {
            question.card.wordVocabId = "";
            renderCardWordPreview();
        }
        question.card.word = word; question.card.pron = cardPron.getText().toString().trim();
        question.card.def = def; question.card.detail = cardDetail.getText().toString();
        question.card.examples.clear();
        for (ProjectStructure.Question.CardExample ex : editExamples) question.card.examples.add(ex);
        question.name = word + " · " + def;
        question.hintsEnabled = false;
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true; toast("已保存新知卡片"); finish();
    }

    private void saveTypeQuestion() {
        String prompt = typePrompt.getText() == null ? "" : typePrompt.getText().toString().trim();
        if (prompt.isEmpty()) { toast("请填写题干"); return; }
        List<String> accepted = new ArrayList<>();
        for (String line : typeAnswers.getText().toString().split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) accepted.add(value);
        }
        if (accepted.isEmpty()) { toast("请至少填写一条可接受答案"); return; }
        List<ProjectStructure.Question.WarnRule> warn = new ArrayList<>();
        for (ProjectStructure.Question.WarnRule rule : editWarn) {
            String match = rule.match == null ? "" : rule.match.trim();
            if (match.isEmpty()) { toast("差一点提示的触发写法不能为空"); return; }
            if (accepted.contains(match)) { toast("触发写法不能与可接受答案重复"); return; }
            for (ProjectStructure.Question.WarnRule existing : warn) {
                if (existing.match.equals(match)) { toast("触发写法不能重复"); return; }
            }
            warn.add(new ProjectStructure.Question.WarnRule(match,
                    "has".equals(rule.kind) ? "has" : "eq", rule.text == null ? "" : rule.text.trim()));
        }
        question.type = ProjectStructure.Question.TYPE_TYPE;
        question.prompt = prompt;
        question.promptItems.clear();
        String mirrored = mirrorListenOption(editPromptItems);
        List<ProjectStructure.Question.OptionItem> items = new ArrayList<>();
        boolean hasVocab = false;
        for (ProjectStructure.Question.OptionItem item : editPromptItems) {
            if ("vocab".equals(item.kind)) hasVocab = true;
        }
        if (hasVocab && mirrored.equals(prompt)) {
            items.addAll(editPromptItems);
        }
        question.promptItems.addAll(items);
        question.acceptedAnswers.clear();
        question.acceptedAnswers.addAll(accepted);
        question.warn.clear();
        question.warn.addAll(warn);
        question.ignoreCase = typeIgnoreCase.isChecked();
        question.ignoreSpace = typeIgnoreSpace.isChecked();
        question.near = typeNear.isChecked();
        question.name = prompt + " → " + accepted.get(0);
        question.hintsEnabled = false;
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true;
        toast("已保存输入题");
        finish();
    }

    private void saveMatch() {
        String prompt = matchPrompt.getText() == null ? "" : matchPrompt.getText().toString().trim();
        if (editPairs.size() < 2) { toast("至少需要 2 对配对"); return; }
        List<ProjectStructure.Question.MatchPair> valid = new ArrayList<>();
        for (ProjectStructure.Question.MatchPair pair : editPairs) {
            String left = pair.left == null ? "" : pair.left.trim();
            String right = pair.right == null ? "" : pair.right.trim();
            if (left.isEmpty() || right.isEmpty()) { toast("每对左右都不能为空"); return; }
            ProjectStructure.Question.MatchPair value =
                    new ProjectStructure.Question.MatchPair(left, right);
            value.leftItems.addAll(normalizeMatchSide(pair.leftItems, left));
            value.rightItems.addAll(normalizeMatchSide(pair.rightItems, right));
            valid.add(value);
        }
        question.type = ProjectStructure.Question.TYPE_MATCH;
        question.prompt = prompt;
        question.pairs.clear();
        question.pairs.addAll(valid);
        String base = prompt.isEmpty() ? "配对" : prompt;
        question.name = base + " · " + valid.size() + " 对";
        question.hintsEnabled = false;
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true;
        toast("已保存配对题");
        finish();
    }

    private List<ProjectStructure.Question.OptionItem> normalizeMatchSide(
            List<ProjectStructure.Question.OptionItem> items, String text) {
        List<ProjectStructure.Question.OptionItem> result = new ArrayList<>();
        if (items.isEmpty()) return result;
        boolean hasVocab = containsVocabItem(items);
        if (!hasVocab) return result;
        if (!mirrorListenOption(items).equals(text)) return result;
        result.addAll(items);
        return result;
    }

    private void renderArrangeAnswers() {
        if (arrangeAnswers == null) return;
        arrangeAnswers.removeAllViews();
        for (int a = 0; a < editAnswers.size(); a++) {
            final int answerIndex = a;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(10), dp(8), dp(10), dp(8));
            StringBuilder text = new StringBuilder("答案 ").append(a + 1).append("：");
            for (String id : editAnswers.get(a)) {
                ProjectStructure.Question.Block block = findBlock(id);
                if (block != null) text.append(" ").append(block.text);
            }
            TextView label = new TextView(this);
            label.setText(text.toString());
            card.addView(label);
            LinearLayout actions = new LinearLayout(this);
            ForgeButton edit = new ForgeButton(this);
            edit.setAllCaps(false);
            edit.setText("编辑答案");
            edit.setOnClickListener(v -> openAnswerDialog(answerIndex));
            actions.addView(edit);
            ForgeButton delete = new ForgeButton(this);
            delete.setAllCaps(false);
            delete.setText("删除答案");
            delete.setOnClickListener(v -> {
                editAnswers.remove(answerIndex);
                renderArrangeAnswers();
                dirty = true;
            });
            actions.addView(delete);
            card.addView(actions);
            arrangeAnswers.addView(card);
        }
    }

    private ProjectStructure.Question.Block findBlock(String id) {
        for (ProjectStructure.Question.Block block : editBlocks) {
            if (id.equals(block.id)) return block;
        }
        return null;
    }

    private void openAnswerDialog(int editIndex) {
        List<String> selected = new ArrayList<>();
        if (editIndex >= 0 && editIndex < editAnswers.size()) {
            selected.addAll(editAnswers.get(editIndex));
        }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        TextView poolLabel = new TextView(this);
        poolLabel.setText("词块池（点击加入末尾）");
        root.addView(poolLabel);
        LinearLayout pool = new LinearLayout(this);
        pool.setOrientation(LinearLayout.VERTICAL);
        root.addView(pool);
        TextView orderLabel = new TextView(this);
        orderLabel.setText("已选顺序（↑/↓/×）");
        root.addView(orderLabel);
        LinearLayout order = new LinearLayout(this);
        order.setOrientation(LinearLayout.VERTICAL);
        root.addView(order);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            pool.removeAllViews();
            for (ProjectStructure.Question.Block block : editBlocks) {
                if (selected.contains(block.id)) continue;
                ForgeButton add = new ForgeButton(this);
                add.setAllCaps(false);
                add.setText("＋ " + block.text);
                add.setOnClickListener(v -> {
                    selected.add(block.id);
                    refresh[0].run();
                });
                pool.addView(add);
            }
            order.removeAllViews();
            for (int i = 0; i < selected.size(); i++) {
                final int index = i;
                ProjectStructure.Question.Block block = findBlock(selected.get(i));
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView text = new TextView(this);
                text.setText((i + 1) + ". " + (block == null ? "?" : block.text));
                row.addView(text, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                ForgeButton up = new ForgeButton(this);
                up.setAllCaps(false);
                up.setText("↑");
                up.setOnClickListener(v -> {
                    if (index > 0) java.util.Collections.swap(selected, index, index - 1);
                    refresh[0].run();
                });
                row.addView(up);
                ForgeButton down = new ForgeButton(this);
                down.setAllCaps(false);
                down.setText("↓");
                down.setOnClickListener(v -> {
                    if (index + 1 < selected.size()) {
                        java.util.Collections.swap(selected, index, index + 1);
                    }
                    refresh[0].run();
                });
                row.addView(down);
                ForgeButton remove = new ForgeButton(this);
                remove.setAllCaps(false);
                remove.setText("×");
                remove.setOnClickListener(v -> {
                    selected.remove(index);
                    refresh[0].run();
                });
                row.addView(remove);
                order.addView(row);
            }
        };
        refresh[0].run();
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle(editIndex >= 0 ? "编辑答案" : "添加答案");
        builder.setView(scroll);
        builder.setNegativeButton("取消", null);
        builder.setPositiveButton("保存", null);
        android.app.AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    if (selected.size() < 2) { toast("每个答案至少 2 个词块"); return; }
                    if (editIndex >= 0) editAnswers.set(editIndex, new ArrayList<>(selected));
                    else editAnswers.add(new ArrayList<>(selected));
                    renderArrangeAnswers();
                    dirty = true;
                    dialog.dismiss();
                }));
        dialog.show();
    }

    private void confirmRemoveBlock(int index) {
        ProjectStructure.Question.Block block = editBlocks.get(index);
        boolean referenced = false;
        for (List<String> answer : editAnswers) if (answer.contains(block.id)) referenced = true;
        if (!referenced) {
            editBlocks.remove(index);
            renderArrangeBlocks();
            dirty = true;
            return;
        }
        new android.app.AlertDialog.Builder(this)
                .setMessage("该词块被答案引用，删除后会从所有答案移除；不足 2 块的答案将删除。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    editBlocks.remove(index);
                    for (int a = editAnswers.size() - 1; a >= 0; a--) {
                        editAnswers.get(a).remove(block.id);
                        if (editAnswers.get(a).size() < 2) editAnswers.remove(a);
                    }
                    renderArrangeBlocks();
                    renderArrangeAnswers();
                    dirty = true;
                }).show();
    }

    private void saveArrange() {
        String prompt = arrangePrompt.getText() == null ? "" : arrangePrompt.getText().toString().trim();
        if (prompt.isEmpty()) { toast("请填写题干/释义"); return; }
        List<ProjectStructure.Question.Block> valid = new ArrayList<>();
        for (ProjectStructure.Question.Block block : editBlocks) {
            String value = block.text == null ? "" : block.text.trim();
            if (value.isEmpty()) { toast("词块文本不能为空"); return; }
            ProjectStructure.Question.Block copy =
                    new ProjectStructure.Question.Block(value, block.ok, block.vocabId);
            copy.id = block.id.isEmpty() ? java.util.UUID.randomUUID().toString() : block.id;
            valid.add(copy);
        }
        if (editAnswers.isEmpty()) { toast("请至少添加一个答案"); return; }
        List<List<String>> answers = new ArrayList<>();
        for (List<String> answer : editAnswers) {
            if (answer.size() < 2) { toast("每个答案至少 2 个词块"); return; }
            List<String> ids = new ArrayList<>();
            for (String id : answer) {
                boolean found = false;
                for (ProjectStructure.Question.Block block : valid) {
                    if (id.equals(block.id)) { found = true; break; }
                }
                if (!found) { toast("答案引用了已删除的词块"); return; }
                if (ids.contains(id)) { toast("同一答案不能重复词块"); return; }
                ids.add(id);
            }
            answers.add(ids);
        }
        question.type = ProjectStructure.Question.TYPE_ARRANGE;
        question.prompt = prompt;
        question.blocks.clear();
        question.blocks.addAll(valid);
        question.answers.clear();
        question.answers.addAll(answers);
        StringBuilder name = new StringBuilder(prompt).append(" · ");
        int firstAnswer = editAnswers.isEmpty() ? -1 : 0;
        if (firstAnswer >= 0) {
            boolean first = true;
            for (String id : editAnswers.get(firstAnswer)) {
                ProjectStructure.Question.Block block = findBlock(id);
                if (block == null) continue;
                if (!first) name.append(' ');
                first = false;
                name.append(block.text);
            }
        }
        question.name = name.toString();
        question.hintsEnabled = hintsEnabledView.isChecked();
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) { toast("保存题目失败"); return; }
        saved = true;
        toast("已保存词块组句");
        finish();
    }

    private void save() {
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 6) {
            saveDialog();
            return;
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 5) {
            saveCard();
            return;
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 4) {
            saveListen();
            return;
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 3) {
            saveTypeQuestion();
            return;
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 2) {
            saveMatch();
            return;
        }
        if (typeSpinner != null && typeSpinner.getSelectedItemPosition() == 1) {
            saveArrange();
            return;
        }
        if (recording) {
            toast("请先停止录音");
            return;
        }
        List<String> options = new ArrayList<>();
        for (ForgeTextField field : optionFields) {
            String value = field.getText() == null ? "" : field.getText().toString().trim();
            if (value.isEmpty()) {
                toast("四个选项都必须填写");
                return;
            }
            options.add(value);
        }
        if (parts.isEmpty()) {
            toast("请至少选择一个词汇组成句子");
            return;
        }
        int correct = -1;
        for (int i = 0; i < correctButtons.length; i++) {
            if (correctButtons[i].isChecked()) { correct = i; break; }
        }
        if (correct < 0) {
            toast("请选择一个正确答案");
            return;
        }
        File sourceAudio = null;
        long duration = pendingDuration;
        if (pendingAudio != null) {
            sourceAudio = new File(pendingAudio);
            if (!sourceAudio.isFile()) {
                toast("录音文件不存在");
                return;
            }
            if (duration <= 0) duration = MediaFiles.audioDuration(sourceAudio);
        } else if (!audioRemoved && !existingAudio.isEmpty()) {
            File file = MediaFiles.resolveProjectFile(projectDirectory, existingAudio);
            if (file == null || !file.isFile()) {
                toast("现有音频文件缺失，请重录或移除");
                return;
            }
            duration = existingDuration;
        }
        if (duration > 0) {
            if (audioStart < 0 || audioEnd > duration || audioEnd - audioStart < 100) {
                toast("音频裁剪区间无效（至少 100ms）");
                return;
            }
        }
        String oldAudio = originalAudio;
        String audioRelative = audioRemoved ? "" : existingAudio;
        if (sourceAudio != null) {
            try {
                File copied = MediaFiles.copyAudioToProject(projectDirectory, sourceAudio,
                        question.id);
                File desired = new File(copied.getParent(), "question_" + question.id + "_"
                        + System.currentTimeMillis() + extensionOf(copied));
                if (copied.renameTo(desired)) copied = desired;
                audioRelative = MediaFiles.relativePath(projectDirectory, copied);
                if (duration <= 0) duration = MediaFiles.audioDuration(copied);
            } catch (Exception e) {
                toast("保存录音失败");
                return;
            }
        }
        question.parts.clear();
        question.parts.addAll(parts);
        question.segments.clear();
        question.segments.addAll(segments);
        question.type = ProjectStructure.Question.TYPE_TRANSLATION_CHOICE;
        question.sentence = sentenceText();
        question.audioStart = audioStart;
        question.audioEnd = audioEnd;
        question.hintsEnabled = hintsEnabledView.isChecked();
        question.options.clear();
        question.options.addAll(options);
        question.optionItems.clear();
        for (int i = 0; i < 4; i++) {
            List<ProjectStructure.Question.OptionItem> items =
                    new ArrayList<>(choiceOptionItems.get(i));
            if (!mirrorListenOption(items).equals(options.get(i))) {
                ProjectStructure.Question.OptionItem textItem =
                        new ProjectStructure.Question.OptionItem();
                textItem.text = options.get(i);
                items.clear();
                items.add(textItem);
            }
            question.optionItems.add(items);
        }
        boolean hasVocabOption = false;
        for (List<ProjectStructure.Question.OptionItem> group : question.optionItems) {
            for (ProjectStructure.Question.OptionItem item : group) {
                if ("vocab".equals(item.kind)) { hasVocabOption = true; break; }
            }
            if (hasVocabOption) break;
        }
        if (!hasVocabOption) question.optionItems.clear();
        question.correctIndex = correct;
        question.audio = audioRelative;
        question.audioDuration = Math.max(0, duration);
        String display = question.sentence;
        if (display.isEmpty()) display = options.get(0);
        if (display.isEmpty()) display = "未命名题目";
        question.name = display;
        if (!course.questions.contains(question)) course.questions.add(question);
        if (!structure.save(projectDirectory)) {
            toast("保存题目失败");
            return;
        }
        if (!oldAudio.isEmpty() && !oldAudio.equals(audioRelative)) {
            MediaFiles.deleteQuietly(MediaFiles.resolveProjectFile(projectDirectory, oldAudio));
        }
        originalAudio = audioRelative;
        if (pendingAudio != null) MediaFiles.deleteQuietly(new File(pendingAudio));
        pendingAudio = null;
        saved = true;
        toast("题目已保存");
        finish();
    }

    private String extensionOf(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot) : ".m4a";
    }

    private void confirmBack() {
        if (saved || !dirty) {
            finish();
            return;
        }
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("放弃编辑？");
        dialog.setBodyText("未保存的修改会丢失。");
        dialog.setNegativeButton("继续编辑", null);
        dialog.setPositiveButton("放弃", (d, w) -> {
            cleanupPending();
            saved = true;
            finish();
        });
        dialog.show();
    }

    @Override
    public void onBackPressed() {
        confirmBack();
    }

    private void cleanupPending() {
        stopPlayback();
        if (recording) {
            recording = false;
            if (ticker != null && tickerTask != null) ticker.removeCallbacks(tickerTask);
            try { recorder.stop(); } catch (Exception ignored) {}
            try { recorder.release(); } catch (Exception ignored) {}
            recorder = null;
        }
        if (pendingAudio != null) {
            MediaFiles.deleteQuietly(new File(pendingAudio));
            pendingAudio = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        if (!saved) cleanupPending();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }
}
