package io.github.myoslatal.languageforge;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
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
        partsContainer = findViewById(R.id.question_parts);
        sentenceView = findViewById(R.id.question_sentence);
        audioHint = findViewById(R.id.question_audio_hint);
        audioTrimText = findViewById(R.id.question_audio_trim);
        startSeek = findViewById(R.id.question_audio_start);
        endSeek = findViewById(R.id.question_audio_end);
        recordButton = findViewById(R.id.btn_question_record);
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
            options[i] = i < question.options.size() ? question.options.get(i) : "";
            optionFields[i].setText(options[i]);
            optionFields[i].getEditText().addTextChangedListener(new SimpleWatcher());
        }
        if (question.correctIndex >= 0 && question.correctIndex < 4) {
            correctButtons[question.correctIndex].setChecked(true);
        }
        for (RadioButton button : correctButtons) {
            button.setOnCheckedChangeListener((v, checked) -> {
                if (checked) dirty = true;
            });
        }
        findViewById(R.id.btn_question_back).setOnClickListener(v -> confirmBack());
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

    private String displayName(String vocabId) {
        VocabularyEntry entry = null;
        for (VocabularyEntry candidate : vocabulary.entries) {
            if (candidate.id.equals(vocabId)) {
                entry = candidate;
                break;
            }
        }
        return entry == null ? "" : vocabulary.displayName(entry);
    }

    private String sentenceText() {
        StringBuilder builder = new StringBuilder();
        for (ProjectStructure.Question.Segment segment : segments) {
            if (segment.word) {
                String name = displayName(segment.vocabId);
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
            TextView name = new TextView(this);
            name.setText(segment.word
                    ? (i + 1) + ". " + displayName(segment.vocabId)
                    : (i + 1) + ". 标点 " + segment.text);
            name.setTextSize(14f);
            name.setTextColor(getColor(segment.word ? R.color.forge_text : R.color.forge_accent));
            row.addView(name, new LinearLayout.LayoutParams(0, dp(40), 1f));
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

    private void save() {
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
        question.options.clear();
        question.options.addAll(options);
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
