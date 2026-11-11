package io.github.myoslatal.languageforge;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeButton;
import io.github.myoslatal.languageforge.uikit.ForgeChip;
import io.github.myoslatal.languageforge.uikit.ForgeDialog;
import io.github.myoslatal.languageforge.uikit.ForgeSelect;
import io.github.myoslatal.languageforge.uikit.ForgeTextField;
import io.github.myoslatal.languageforge.uikit.ForgeToast;

/** Create/edit one project vocabulary entry. */
public class VocabularyEditorActivity extends ForgeActivity {

    public static final String EXTRA_FOLDER_NAME = "folderName";
    public static final String EXTRA_ENTRY_ID = "entryId";
    private static final int REQUEST_PICK_PARTS = 4301;
    private static final int REQUEST_PICK_IMAGE = 4401;
    private static final int REQUEST_CROP_IMAGE = 4402;
    private static final int REQUEST_DRAW_IMAGE = 4403;
    private static final int REQUEST_PICK_AUDIO = 4501;

    private File projectDirectory;
    private String folderName;
    private VocabularyStore store;
    private VocabularyEntry existing;

    private LinearLayout meaningsContainer;
    private LinearLayout codesContainer;
    private LinearLayout codepointsContainer;
    private LinearLayout comboSelected;
    private final List<ForgeTextField> meaningFields = new ArrayList<>();
    private final List<ForgeTextField> codeFields = new ArrayList<>();
    private final List<ForgeTextField> codepointFields = new ArrayList<>();
    private final List<String> selectedPartIds = new ArrayList<>();
    private ForgeSelect typeSelect;
    private ForgeTextField textField;
    private LinearLayout textContainer;
    private LinearLayout unicodeContainer;
    private LinearLayout comboContainer;
    private LinearLayout imageContainer;
    private TextView unicodePreview;
    private TextView comboPreview;
    private LinearLayout imageEmptyActions;
    private LinearLayout imagePreviewBox;
    private ImageView imagePreview;
    private TextView imageHint;
    private TextView imageSize;
    private String pendingImagePath;
    private int pendingImageWidth;
    private int pendingImageHeight;
    private String cropSourcePath;
    private boolean imageRemoved;
    private LinearLayout audioEmptyActions;
    private LinearLayout audioReadyBox;
    private LinearLayout audioTrimBox;
    private TextView audioHint;
    private TextView audioName;
    private TextView audioDuration;
    private TextView audioStartLabel;
    private TextView audioEndLabel;
    private TextView audioRangeText;
    private SeekBar audioStartSeek;
    private SeekBar audioEndSeek;
    private ForgeButton audioPlayButton;
    private ForgeButton audioRecordButton;
    private ForgeButton audioPreviewButton;
    private String pendingAudioPath;
    private long pendingAudioStart;
    private long pendingAudioEnd;
    private long pendingAudioDuration;
    private boolean audioRemoved;
    private boolean updatingSeek;
    private MediaPlayer mediaPlayer;
    private boolean playing;
    private boolean previewing;
    private MediaRecorder mediaRecorder;
    private boolean recording;
    private Handler recordHandler;
    private Runnable recordTicker;
    private File recordFile;
    private long recordStart;
    private Handler playbackHandler;
    private Runnable playbackStop;
    private ActivityResultLauncher<String> recordPermissionLauncher;
    private Typeface vocabTypeface;

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
        vocabTypeface = new FontStore(projectDirectory).activeTypeface();
        String entryId = getIntent().getStringExtra(EXTRA_ENTRY_ID);
        if (entryId != null && !entryId.isEmpty()) {
            existing = store.findById(entryId);
            if (existing == null) {
                toast("词汇不存在");
                finish();
                return;
            }
        }

        setContentView(R.layout.activity_vocabulary_editor);
        applyTopInset(findViewById(R.id.vocab_editor_topbar));
        applyBottomInset(findViewById(R.id.vocab_editor_scroll));
        bindViews();

        TextView title = findViewById(R.id.vocab_editor_title);
        title.setText(existing == null ? "新建词汇" : "编辑词汇");
        findViewById(R.id.btn_vocab_back).setOnClickListener(v -> cancelEditing());
        findViewById(R.id.btn_vocab_save).setOnClickListener(v -> save());
        findViewById(R.id.btn_add_meaning).setOnClickListener(v -> addMeaningRow(""));
        findViewById(R.id.btn_add_code).setOnClickListener(v -> addCodeRow(""));
        findViewById(R.id.btn_add_codepoint).setOnClickListener(v -> addCodepointRow(""));
        findViewById(R.id.btn_pick_parts).setOnClickListener(v -> pickParts());
        findViewById(R.id.btn_import_image).setOnClickListener(v -> pickImage());
        findViewById(R.id.btn_reimport_image).setOnClickListener(v -> pickImage());
        findViewById(R.id.btn_draw_image).setOnClickListener(v -> showDrawingSizeDialog());
        findViewById(R.id.btn_redraw_image).setOnClickListener(v -> showDrawingSizeDialog());
        findViewById(R.id.btn_remove_image).setOnClickListener(v -> removeImage());
        findViewById(R.id.btn_import_audio).setOnClickListener(v -> pickAudio());
        findViewById(R.id.btn_reimport_audio).setOnClickListener(v -> pickAudio());
        findViewById(R.id.btn_record_audio).setOnClickListener(v -> requestRecord());
        findViewById(R.id.btn_rerecord_audio).setOnClickListener(v -> requestRecord());
        findViewById(R.id.btn_play_audio).setOnClickListener(v -> togglePlayback());
        findViewById(R.id.btn_preview_audio).setOnClickListener(v -> previewTrim());
        findViewById(R.id.btn_remove_audio).setOnClickListener(v -> removeAudio());
        audioStartSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTrimFromSeekbars();
                }
            }
        });
        audioEndSeek.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTrimFromSeekbars();
                }
            }
        });
        recordPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (granted) {
                        startRecording();
                    } else {
                        toast("未授予录音权限");
                    }
                });

        setupType();
        populate();
        updateTypeVisibility();
        updateAudioUi();
    }

    private void bindViews() {
        meaningsContainer = findViewById(R.id.meanings_container);
        codesContainer = findViewById(R.id.codes_container);
        codepointsContainer = findViewById(R.id.codepoints_container);
        comboSelected = findViewById(R.id.combo_selected);
        typeSelect = findViewById(R.id.vocab_type);
        textField = findViewById(R.id.vocab_text);
        textContainer = findViewById(R.id.text_container);
        unicodeContainer = findViewById(R.id.unicode_container);
        comboContainer = findViewById(R.id.combo_container);
        imageContainer = findViewById(R.id.image_container);
        imageEmptyActions = findViewById(R.id.image_empty_actions);
        imagePreviewBox = findViewById(R.id.image_preview_box);
        imagePreview = findViewById(R.id.image_preview);
        imageHint = findViewById(R.id.image_hint);
        imageSize = findViewById(R.id.image_size);
        audioEmptyActions = findViewById(R.id.audio_empty_actions);
        audioReadyBox = findViewById(R.id.audio_ready_box);
        audioTrimBox = findViewById(R.id.audio_trim_box);
        audioHint = findViewById(R.id.audio_hint);
        audioName = findViewById(R.id.audio_name);
        audioDuration = findViewById(R.id.audio_duration);
        audioStartLabel = findViewById(R.id.audio_start_label);
        audioEndLabel = findViewById(R.id.audio_end_label);
        audioRangeText = findViewById(R.id.audio_range_text);
        audioStartSeek = findViewById(R.id.audio_start_seek);
        audioEndSeek = findViewById(R.id.audio_end_seek);
        audioPlayButton = findViewById(R.id.btn_play_audio);
        audioRecordButton = findViewById(R.id.btn_record_audio);
        audioPreviewButton = findViewById(R.id.btn_preview_audio);
        unicodePreview = findViewById(R.id.unicode_preview);
        comboPreview = findViewById(R.id.combo_preview);
    }

    private void setupType() {
        typeSelect.setLabel("类型");
        typeSelect.setEntries(new String[]{
                "文本词语", "Unicode 编码词语", "自定义文字组合词", "自定义文字"
        });
        typeSelect.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateTypeVisibility();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void populate() {
        if (existing == null) {
            addMeaningRow("");
            addCodeRow("");
            addCodepointRow("");
            return;
        }
        if (existing.meanings.isEmpty()) {
            addMeaningRow("");
        } else {
            for (String meaning : existing.meanings) {
                addMeaningRow(meaning);
            }
        }
        if (existing.codes.isEmpty()) {
            addCodeRow("");
        } else {
            for (String code : existing.codes) {
                addCodeRow(code);
            }
        }
        textField.setText(existing.text);
        if (existing.codepoints.isEmpty()) {
            addCodepointRow("");
        } else {
            for (String codepoint : existing.codepoints) {
                addCodepointRow(codepoint);
            }
        }
        selectedPartIds.addAll(existing.parts);
        typeSelect.setSelection(typeIndex(existing.type));
        renderParts();
    }

    private int typeIndex(String type) {
        if (VocabularyEntry.TYPE_UNICODE.equals(type)) {
            return 1;
        }
        if (VocabularyEntry.TYPE_COMBO.equals(type)) {
            return 2;
        }
        if (VocabularyEntry.TYPE_IMAGE.equals(type)) {
            return 3;
        }
        return 0;
    }

    private String selectedType() {
        int index = typeSelect.getSelection();
        if (index == 1) {
            return VocabularyEntry.TYPE_UNICODE;
        }
        if (index == 2) {
            return VocabularyEntry.TYPE_COMBO;
        }
        if (index == 3) {
            return VocabularyEntry.TYPE_IMAGE;
        }
        return VocabularyEntry.TYPE_TEXT;
    }

    private void updateTypeVisibility() {
        if (typeSelect == null || textContainer == null) {
            return;
        }
        String type = selectedType();
        textContainer.setVisibility(VocabularyEntry.TYPE_TEXT.equals(type)
                ? View.VISIBLE : View.GONE);
        unicodeContainer.setVisibility(VocabularyEntry.TYPE_UNICODE.equals(type)
                ? View.VISIBLE : View.GONE);
        comboContainer.setVisibility(VocabularyEntry.TYPE_COMBO.equals(type)
                ? View.VISIBLE : View.GONE);
        imageContainer.setVisibility(VocabularyEntry.TYPE_IMAGE.equals(type)
                ? View.VISIBLE : View.GONE);
        if (VocabularyEntry.TYPE_UNICODE.equals(type) && codepointFields.isEmpty()) {
            addCodepointRow("");
        }
        if (VocabularyEntry.TYPE_COMBO.equals(type)) {
            renderParts();
        }
        if (VocabularyEntry.TYPE_IMAGE.equals(type)) {
            updateImageUi();
        }
    }

    private void addMeaningRow(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("释义");
        field.setHint("例如：苹果");
        field.setText(value);
        row.addView(field, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ForgeButton remove = removeButton("删除释义");
        remove.setOnClickListener(v -> {
            if (meaningFields.size() <= 1) {
                toast("至少保留一条释义");
                return;
            }
            meaningsContainer.removeView(row);
            meaningFields.remove(field);
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp(32), dp(32)));
        meaningsContainer.addView(row);
        meaningFields.add(field);
    }

    private void addCodeRow(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("编码");
        field.setHint("例如：aaa");
        field.setText(value);
        row.addView(field, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ForgeButton remove = removeButton("删除编码");
        remove.setOnClickListener(v -> {
            if (codeFields.size() <= 1) {
                toast("至少保留一个编码输入框");
                return;
            }
            codesContainer.removeView(row);
            codeFields.remove(field);
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp(32), dp(32)));
        codesContainer.addView(row);
        codeFields.add(field);
    }

    private void addCodepointRow(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 0, 0, dp(6));
        ForgeTextField field = new ForgeTextField(this);
        field.setLabel("码点");
        field.setHint("如 12ab / 0x12AB");
        field.setText(value);
        field.getEditText().addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateUnicodePreview();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        row.addView(field, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        ForgeButton remove = removeButton("删除码点");
        remove.setOnClickListener(v -> {
            if (codepointFields.size() <= 1) {
                toast("至少保留一个码点输入框");
                return;
            }
            codepointsContainer.removeView(row);
            codepointFields.remove(field);
            updateUnicodePreview();
        });
        row.addView(remove, new LinearLayout.LayoutParams(dp(32), dp(32)));
        codepointsContainer.addView(row);
        codepointFields.add(field);
        updateUnicodePreview();
    }

    private ForgeButton removeButton(String description) {
        ForgeButton remove = new ForgeButton(this);
        remove.setVariant(ForgeButton.VARIANT_GHOST);
        remove.setIcon(R.drawable.forge_ic_close);
        remove.setContentDescription(description);
        return remove;
    }

    private void updateUnicodePreview() {
        if (unicodePreview == null) {
            return;
        }
        StringBuilder preview = new StringBuilder();
        for (ForgeTextField field : codepointFields) {
            String normalized = VocabularyStore.normalizeCodePoint(textOf(field));
            if (normalized != null) {
                preview.appendCodePoint(Integer.parseInt(normalized, 16));
            }
        }
        unicodePreview.setText("预览：" + (preview.length() == 0 ? "—" : preview));
        applyVocabTypeface(unicodePreview);
    }

    private void pickParts() {
        Intent intent = new Intent(this, VocabularyPickerActivity.class);
        intent.putExtra(VocabularyPickerActivity.EXTRA_FOLDER_NAME, folderName);
        intent.putExtra(VocabularyPickerActivity.EXTRA_EXCLUDE_ID,
                existing == null ? "" : existing.id);
        startActivityForResult(intent, REQUEST_PICK_PARTS);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_PARTS && resultCode == RESULT_OK && data != null) {
            ArrayList<String> ids = data.getStringArrayListExtra(
                    VocabularyPickerActivity.EXTRA_SELECTED_IDS);
            selectedPartIds.clear();
            if (ids != null) {
                selectedPartIds.addAll(ids);
            }
            renderParts();
        } else if (requestCode == REQUEST_PICK_IMAGE) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                prepareCrop(data.getData());
            }
        } else if (requestCode == REQUEST_CROP_IMAGE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String path = data.getStringExtra(ImageCropActivity.EXTRA_RESULT_PATH);
                int width = data.getIntExtra(ImageCropActivity.EXTRA_WIDTH, 0);
                int height = data.getIntExtra(ImageCropActivity.EXTRA_HEIGHT, 0);
                deleteCropSource();
                applyImageResult(path, width, height);
            } else {
                deleteCropSource();
            }
        } else if (requestCode == REQUEST_DRAW_IMAGE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                String path = data.getStringExtra(DrawingActivity.EXTRA_RESULT_PATH);
                int width = data.getIntExtra(DrawingActivity.EXTRA_RESULT_WIDTH, 0);
                int height = data.getIntExtra(DrawingActivity.EXTRA_RESULT_HEIGHT, 0);
                applyImageResult(path, width, height);
            }
        } else if (requestCode == REQUEST_PICK_AUDIO) {
            if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
                prepareAudioImport(data.getData());
            }
        }
    }

    private void renderParts() {
        if (comboSelected == null) {
            return;
        }
        comboSelected.removeAllViews();
        List<String> names = new ArrayList<>();
        for (String id : selectedPartIds) {
            VocabularyEntry part = store.findById(id);
            if (part == null) {
                continue;
            }
            String name = store.displayName(part);
            names.add(name);
            ForgeChip chip = new ForgeChip(this);
            chip.setText(name);
            applyVocabTypeface(chip);
            chip.setClosable(true);
            chip.setOnCloseClickListener(c -> {
                selectedPartIds.remove(id);
                renderParts();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(4);
            comboSelected.addView(chip, lp);
        }
        StringBuilder composed = new StringBuilder();
        for (String name : names) {
            composed.append(name);
        }
        comboPreview.setText(composed.length() == 0
                ? "已选 0 个部分（至少 2 个）"
                : "组合预览：" + composed);
        applyVocabTypeface(comboPreview);
    }

    private void applyVocabTypeface(TextView view) {
        if (view == null || vocabTypeface == null) {
            return;
        }
        Typeface base = view.getTypeface();
        view.setTypeface(Typeface.create(vocabTypeface,
                base == null ? Typeface.NORMAL : base.getStyle()));
    }

    private void updateImageUi() {
        if (imageContainer == null) {
            return;
        }
        if (!VocabularyEntry.TYPE_IMAGE.equals(selectedType())) {
            imageContainer.setVisibility(View.GONE);
            return;
        }
        imageContainer.setVisibility(View.VISIBLE);
        File preview = null;
        int width = 0;
        int height = 0;
        if (pendingImagePath != null) {
            preview = new File(pendingImagePath);
            width = pendingImageWidth;
            height = pendingImageHeight;
        } else if (!imageRemoved && existing != null
                && existing.image != null && !existing.image.isEmpty()) {
            File file = MediaFiles.resolveProjectFile(projectDirectory, existing.image);
            if (file != null && file.isFile()) {
                int[] size = MediaFiles.imageSize(file);
                if (size != null) {
                    preview = file;
                    width = size[0];
                    height = size[1];
                }
            }
        }
        Bitmap bitmap = preview == null ? null : MediaFiles.decodeFile(preview, 1024);
        if (bitmap != null) {
            imagePreview.setImageBitmap(bitmap);
            imageSize.setText(width + " × " + height + " px");
            imageHint.setText("图片已就绪；保存后写入项目资源。");
            imagePreviewBox.setVisibility(View.VISIBLE);
            imageEmptyActions.setVisibility(View.GONE);
        } else {
            imagePreview.setImageDrawable(null);
            imagePreviewBox.setVisibility(View.GONE);
            imageEmptyActions.setVisibility(View.VISIBLE);
            imageHint.setText("导入图片或手绘文字；图片会保存为项目资源。");
        }
    }

    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, REQUEST_PICK_IMAGE);
    }

    private void prepareCrop(Uri uri) {
        toast("正在准备图片…");
        new Thread(() -> {
            try {
                Bitmap bitmap = MediaFiles.decodeUri(getContentResolver(), uri,
                        MediaFiles.MAX_DECODE_DIMENSION);
                if (bitmap == null) {
                    throw new IOException("图片解码失败");
                }
                File temp = MediaFiles.cacheFile(this, "import");
                if (temp == null) {
                    throw new IOException("无法创建临时图片");
                }
                MediaFiles.writePng(bitmap, temp);
                runOnUiThread(() -> {
                    deleteCropSource();
                    cropSourcePath = temp.getAbsolutePath();
                    launchCrop();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("图片读取失败"));
            }
        }).start();
    }

    private void launchCrop() {
        Intent intent = new Intent(this, ImageCropActivity.class);
        intent.putExtra(ImageCropActivity.EXTRA_SOURCE_PATH, cropSourcePath);
        startActivityForResult(intent, REQUEST_CROP_IMAGE);
    }

    private void showDrawingSizeDialog() {
        ForgeDialog dialog = new ForgeDialog(this);
        dialog.setTitle("手绘文字");
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);

        ForgeTextField widthField = new ForgeTextField(this);
        widthField.setLabel("宽度 (px)");
        widthField.setHint("32–4096，默认 512");
        widthField.setText("512");
        widthField.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        body.addView(widthField, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        ForgeTextField heightField = new ForgeTextField(this);
        heightField.setLabel("高度 (px)");
        heightField.setHint("32–4096，默认 512");
        heightField.setText("512");
        heightField.getEditText().setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout.LayoutParams heightLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        heightLp.topMargin = dp(8);
        body.addView(heightField, heightLp);

        dialog.setBodyView(body);
        dialog.setNegativeButton("取消", null);
        dialog.setPositiveButton("开始绘制", (d, which) -> {
            int width = parseDimension(textOf(widthField));
            int height = parseDimension(textOf(heightField));
            if (width < 32 || width > 4096 || height < 32 || height > 4096) {
                toast("宽高需在 32–4096 之间");
                return;
            }
            Intent intent = new Intent(this, DrawingActivity.class);
            intent.putExtra(DrawingActivity.EXTRA_WIDTH, width);
            intent.putExtra(DrawingActivity.EXTRA_HEIGHT, height);
            startActivityForResult(intent, REQUEST_DRAW_IMAGE);
        });
        dialog.show();
    }

    private int parseDimension(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void removeImage() {
        if (pendingImagePath != null) {
            MediaFiles.deleteQuietly(new File(pendingImagePath));
            pendingImagePath = null;
        }
        imageRemoved = true;
        updateImageUi();
        toast("已移除；保存后生效");
    }

    private void applyImageResult(String path, int width, int height) {
        if (path == null || width <= 0 || height <= 0) {
            toast("图片结果无效");
            return;
        }
        if (pendingImagePath != null) {
            MediaFiles.deleteQuietly(new File(pendingImagePath));
        }
        pendingImagePath = path;
        pendingImageWidth = width;
        pendingImageHeight = height;
        imageRemoved = false;
        updateImageUi();
    }

    private void deleteCropSource() {
        if (cropSourcePath != null) {
            MediaFiles.deleteQuietly(new File(cropSourcePath));
            cropSourcePath = null;
        }
    }

    private void cancelEditing() {
        cleanupPendingMedia();
        finish();
    }

    @Override
    public void onBackPressed() {
        cleanupPendingMedia();
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        stopPlayback();
        abortRecording();
        if (isFinishing()) {
            cleanupPendingMedia();
        }
        super.onDestroy();
    }

    private void abortRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        if (recordHandler != null && recordTicker != null) {
            recordHandler.removeCallbacks(recordTicker);
        }
        try {
            mediaRecorder.stop();
        } catch (Exception ignored) {
        }
        try {
            mediaRecorder.release();
        } catch (Exception ignored) {
        }
        mediaRecorder = null;
        MediaFiles.deleteQuietly(recordFile);
        recordFile = null;
    }

    private void cleanupPendingMedia() {
        if (pendingImagePath != null) {
            MediaFiles.deleteQuietly(new File(pendingImagePath));
            pendingImagePath = null;
        }
        if (pendingAudioPath != null) {
            MediaFiles.deleteQuietly(new File(pendingAudioPath));
            pendingAudioPath = null;
        }
        deleteCropSource();
    }

    private void updateAudioUi() {
        if (audioEmptyActions == null) {
            return;
        }
        if (recording) {
            audioHint.setText("正在录音…");
            audioEmptyActions.setVisibility(View.VISIBLE);
            audioReadyBox.setVisibility(View.GONE);
            updateRecordUi();
            return;
        }
        File file = currentAudioFile();
        if (file == null) {
            audioEmptyActions.setVisibility(View.VISIBLE);
            audioReadyBox.setVisibility(View.GONE);
            if (!audioRemoved && existing != null
                    && existing.audio != null && !existing.audio.isEmpty()) {
                audioHint.setText("音频文件缺失，请重新导入或录制。");
            } else {
                audioHint.setText("可选；导入音频或现场录制，播放与裁剪会遵守区间。");
            }
            audioRecordButton.setText("录制音频");
            return;
        }
        audioEmptyActions.setVisibility(View.GONE);
        audioReadyBox.setVisibility(View.VISIBLE);
        long duration = currentAudioDuration();
        long start = currentAudioStart();
        long end = currentAudioEnd();
        if (duration <= 0) {
            duration = Math.max(end, 1);
        }
        start = Math.max(0, Math.min(start, duration));
        end = Math.max(start, Math.min(end, duration));
        audioHint.setText("播放、试听和保存都会使用下面的裁剪区间。");
        audioName.setText(file.getName());
        audioDuration.setText("时长：" + formatAudioTime(duration));
        int max = (int) Math.max(1, Math.min(Integer.MAX_VALUE, duration));
        updatingSeek = true;
        audioStartSeek.setMax(max);
        audioEndSeek.setMax(max);
        audioStartSeek.setProgress((int) start);
        audioEndSeek.setProgress((int) end);
        updatingSeek = false;
        updateTrimText(start, end, duration);
        audioPlayButton.setText(playing ? "停止" : "播放");
        audioPreviewButton.setText(previewing ? "停止试听" : "试听裁剪");
    }

    private File currentAudioFile() {
        if (pendingAudioPath != null) {
            File file = new File(pendingAudioPath);
            if (file.isFile()) {
                return file;
            }
        }
        if (!audioRemoved && existing != null
                && existing.audio != null && !existing.audio.isEmpty()) {
            File file = MediaFiles.resolveProjectFile(projectDirectory, existing.audio);
            if (file != null && file.isFile()) {
                return file;
            }
        }
        return null;
    }

    private long currentAudioDuration() {
        if (pendingAudioPath != null) {
            return pendingAudioDuration;
        }
        if (existing != null) {
            if (existing.audioDuration > 0) {
                return existing.audioDuration;
            }
            File file = currentAudioFile();
            if (file != null) {
                return MediaFiles.audioDuration(file);
            }
        }
        return 0L;
    }

    private long currentAudioStart() {
        if (pendingAudioPath != null) {
            return pendingAudioStart;
        }
        return existing == null ? 0L : existing.audioStart;
    }

    private long currentAudioEnd() {
        if (pendingAudioPath != null) {
            return pendingAudioEnd;
        }
        return existing == null ? 0L : existing.audioEnd;
    }

    private void pickAudio() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        startActivityForResult(intent, REQUEST_PICK_AUDIO);
    }

    private void prepareAudioImport(Uri uri) {
        toast("正在准备音频…");
        new Thread(() -> {
            File cache = null;
            try {
                cache = MediaFiles.copyUriToCache(this, getContentResolver(), uri);
                long duration = MediaFiles.audioDuration(cache);
                if (duration <= 0) {
                    throw new IOException("无法读取音频时长");
                }
                File finalCache = cache;
                long finalDuration = duration;
                runOnUiThread(() -> applyAudio(finalCache.getAbsolutePath(), finalDuration));
            } catch (Exception e) {
                MediaFiles.deleteQuietly(cache);
                runOnUiThread(() -> toast("音频读取失败"));
            }
        }).start();
    }

    private void applyAudio(String path, long duration) {
        if (path == null || duration <= 0) {
            toast("音频结果无效");
            return;
        }
        if (pendingAudioPath != null) {
            MediaFiles.deleteQuietly(new File(pendingAudioPath));
        }
        pendingAudioPath = path;
        pendingAudioStart = 0L;
        pendingAudioEnd = duration;
        pendingAudioDuration = duration;
        audioRemoved = false;
        updateAudioUi();
        toast("音频已就绪，可裁剪后保存");
    }

    private void requestRecord() {
        if (recording) {
            stopRecording();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            startRecording();
        } else {
            recordPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void startRecording() {
        stopPlayback();
        File output = MediaFiles.cacheAudioFile(this, ".m4a");
        if (output == null) {
            toast("无法创建录音文件");
            return;
        }
        MediaRecorder recorder = new MediaRecorder();
        try {
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(96000);
            recorder.setAudioSamplingRate(44100);
            recorder.setOutputFile(output.getAbsolutePath());
            recorder.prepare();
            recorder.start();
        } catch (Exception e) {
            try {
                recorder.release();
            } catch (Exception ignored) {
            }
            MediaFiles.deleteQuietly(output);
            toast("无法开始录音");
            return;
        }
        mediaRecorder = recorder;
        recordFile = output;
        recordStart = System.currentTimeMillis();
        recording = true;
        recordHandler = new Handler(Looper.getMainLooper());
        recordTicker = new Runnable() {
            @Override
            public void run() {
                if (recording) {
                    updateRecordUi();
                    recordHandler.postDelayed(this, 500);
                }
            }
        };
        recordHandler.post(recordTicker);
        updateAudioUi();
    }

    private void stopRecording() {
        if (!recording) {
            return;
        }
        recording = false;
        if (recordHandler != null && recordTicker != null) {
            recordHandler.removeCallbacks(recordTicker);
        }
        boolean failed = false;
        try {
            mediaRecorder.stop();
        } catch (Exception e) {
            failed = true;
        }
        try {
            mediaRecorder.release();
        } catch (Exception ignored) {
        }
        mediaRecorder = null;
        File file = recordFile;
        recordFile = null;
        if (failed || file == null || !file.isFile()) {
            MediaFiles.deleteQuietly(file);
            toast("录音失败");
            updateAudioUi();
            return;
        }
        long duration = MediaFiles.audioDuration(file);
        if (duration <= 0) {
            MediaFiles.deleteQuietly(file);
            toast("录音时长无效");
            updateAudioUi();
            return;
        }
        if (pendingAudioPath != null) {
            MediaFiles.deleteQuietly(new File(pendingAudioPath));
        }
        pendingAudioPath = file.getAbsolutePath();
        pendingAudioStart = 0L;
        pendingAudioEnd = duration;
        pendingAudioDuration = duration;
        audioRemoved = false;
        updateAudioUi();
        toast("录音完成，可裁剪后保存");
    }

    private void updateRecordUi() {
        if (audioRecordButton == null) {
            return;
        }
        if (!recording) {
            audioRecordButton.setText("录制音频");
            return;
        }
        long elapsed = System.currentTimeMillis() - recordStart;
        audioRecordButton.setText("停止录制 " + formatAudioTime(elapsed));
        audioHint.setText("正在录音…");
    }

    private void removeAudio() {
        stopPlayback();
        if (pendingAudioPath != null) {
            MediaFiles.deleteQuietly(new File(pendingAudioPath));
            pendingAudioPath = null;
        }
        audioRemoved = true;
        updateAudioUi();
        toast("已移除；保存后生效");
    }

    private void togglePlayback() {
        if (playing) {
            stopPlayback();
        } else {
            startPlayback(currentAudioStart(), currentAudioEnd(), false);
        }
    }

    private void previewTrim() {
        if (playing) {
            stopPlayback();
            return;
        }
        long start = audioStartSeek.getProgress();
        long end = audioEndSeek.getProgress();
        startPlayback(start, end, true);
    }

    private void startPlayback(long start, long end, boolean preview) {
        File file = currentAudioFile();
        if (file == null) {
            toast("没有可播放的音频");
            return;
        }
        long duration = currentAudioDuration();
        if (duration <= 0) {
            duration = MediaFiles.audioDuration(file);
        }
        if (duration <= 0) {
            toast("无法读取音频时长");
            return;
        }
        start = Math.max(0, Math.min(start, duration));
        end = Math.max(start, Math.min(end, duration));
        if (end - start < 100) {
            toast("裁剪区间至少 100ms");
            return;
        }
        stopPlayback();
        MediaPlayer player = new MediaPlayer();
        try {
            player.setDataSource(file.getAbsolutePath());
            player.prepare();
            player.seekTo((int) start);
            player.start();
        } catch (Exception e) {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            toast("播放失败");
            return;
        }
        mediaPlayer = player;
        playing = true;
        previewing = preview;
        player.setOnCompletionListener(mp -> stopPlayback());
        player.setOnErrorListener((mp, what, extra) -> {
            stopPlayback();
            return true;
        });
        if (playbackHandler == null) {
            playbackHandler = new Handler(Looper.getMainLooper());
        }
        if (playbackStop != null) {
            playbackHandler.removeCallbacks(playbackStop);
        }
        playbackStop = this::stopPlayback;
        playbackHandler.postDelayed(playbackStop, Math.max(1, end - start));
        updateAudioUi();
    }

    private void stopPlayback() {
        if (playbackHandler != null && playbackStop != null) {
            playbackHandler.removeCallbacks(playbackStop);
        }
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            try {
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
        playing = false;
        previewing = false;
        if (audioPlayButton != null) {
            audioPlayButton.setText("播放");
        }
        if (audioPreviewButton != null) {
            audioPreviewButton.setText("试听裁剪");
        }
    }

    private void updateTrimFromSeekbars() {
        if (updatingSeek) {
            return;
        }
        long duration = currentAudioDuration();
        if (duration <= 0) {
            return;
        }
        long start = audioStartSeek.getProgress();
        long end = audioEndSeek.getProgress();
        if (end < start + 100) {
            end = start + 100;
        }
        if (end > duration) {
            end = duration;
        }
        if (start > end - 100) {
            start = Math.max(0, end - 100);
        }
        updatingSeek = true;
        audioStartSeek.setProgress((int) start);
        audioEndSeek.setProgress((int) end);
        updatingSeek = false;
        if (pendingAudioPath != null) {
            pendingAudioStart = start;
            pendingAudioEnd = end;
        } else if (existing != null) {
            existing.audioStart = start;
            existing.audioEnd = end;
        }
        updateTrimText(start, end, duration);
    }

    private void updateTrimText(long start, long end, long duration) {
        audioStartLabel.setText("开始：" + formatAudioTime(start));
        audioEndLabel.setText("结束：" + formatAudioTime(end));
        audioRangeText.setText("区间：" + formatAudioTime(start) + " – "
                + formatAudioTime(end) + " / " + formatAudioTime(duration));
    }

    private String formatAudioTime(long ms) {
        return String.format(Locale.ROOT, "%.1fs", ms / 1000f);
    }

    private void save() {
        List<String> meanings = collect(meaningFields, VocabularyStore.MAX_MEANING);
        if (meanings.isEmpty()) {
            toast("请至少填写一条释义");
            return;
        }
        List<String> codes = collect(codeFields, VocabularyStore.MAX_CODE);
        String type = selectedType();

        String text = "";
        List<String> codepoints = new ArrayList<>();
        List<String> parts = new ArrayList<>();
        if (VocabularyEntry.TYPE_TEXT.equals(type)) {
            text = VocabularyStore.clean(textOf(textField), VocabularyStore.MAX_TEXT);
            if (text.isEmpty()) {
                toast("请填写文本词语");
                return;
            }
        } else if (VocabularyEntry.TYPE_UNICODE.equals(type)) {
            for (ForgeTextField field : codepointFields) {
                String raw = textOf(field);
                if (raw.trim().isEmpty()) {
                    continue;
                }
                String normalized = VocabularyStore.normalizeCodePoint(raw);
                if (normalized == null) {
                    toast("Unicode 码点格式不正确：" + raw);
                    return;
                }
                codepoints.add(normalized);
            }
            if (codepoints.isEmpty()) {
                toast("请至少填写一个有效码点");
                return;
            }
        } else if (VocabularyEntry.TYPE_COMBO.equals(type)) {
            LinkedHashSet<String> unique = new LinkedHashSet<>(selectedPartIds);
            unique.remove(existing == null ? null : existing.id);
            for (String id : unique) {
                if (store.findById(id) == null) {
                    toast("组合部分包含不存在的词汇");
                    return;
                }
            }
            if (unique.size() < 2) {
                toast("组合词至少需要选择 2 个部分");
                return;
            }
            parts.addAll(unique);
        } else if (VocabularyEntry.TYPE_IMAGE.equals(type)) {
            boolean pendingOk = pendingImagePath != null
                    && new File(pendingImagePath).isFile()
                    && pendingImageWidth > 0 && pendingImageHeight > 0;
            boolean existingOk = false;
            if (!pendingOk && !imageRemoved && existing != null
                    && existing.image != null && !existing.image.isEmpty()) {
                File file = MediaFiles.resolveProjectFile(projectDirectory, existing.image);
                int[] size = MediaFiles.imageSize(file);
                existingOk = size != null && size[0] > 0 && size[1] > 0;
            }
            if (!pendingOk && !existingOk) {
                toast("请先导入或手绘图片");
                return;
            }
        }

        String previousImage = existing == null ? "" : existing.image;
        String previousAudio = existing == null ? "" : existing.audio;
        File audioSource = null;
        long finalAudioStart = 0L;
        long finalAudioEnd = 0L;
        long finalAudioDuration = 0L;
        if (pendingAudioPath != null) {
            audioSource = new File(pendingAudioPath);
        } else if (!audioRemoved && existing != null
                && existing.audio != null && !existing.audio.isEmpty()) {
            audioSource = MediaFiles.resolveProjectFile(projectDirectory, existing.audio);
        }
        if (audioSource != null && audioSource.isFile()) {
            finalAudioDuration = pendingAudioPath != null
                    ? pendingAudioDuration : existing.audioDuration;
            if (finalAudioDuration <= 0) {
                finalAudioDuration = MediaFiles.audioDuration(audioSource);
            }
            if (finalAudioDuration <= 0) {
                toast("音频时长无效");
                return;
            }
            long start = audioStartSeek.getProgress();
            long end = audioEndSeek.getProgress();
            start = Math.max(0, Math.min(start, finalAudioDuration));
            end = Math.max(start, Math.min(end, finalAudioDuration));
            if (end <= start || end - start < 100) {
                toast("裁剪区间至少 100ms，且结束需大于开始");
                return;
            }
            finalAudioStart = start;
            finalAudioEnd = end;
        } else if (!audioRemoved && existing != null
                && existing.audio != null && !existing.audio.isEmpty()) {
            toast("音频文件不存在，请重新导入或录制");
            return;
        }

        VocabularyEntry target = existing;
        boolean created = target == null;
        if (created) {
            target = new VocabularyEntry(UUID.randomUUID().toString());
            target.createdAt = System.currentTimeMillis();
        }
        String finalImage = "";
        int finalImageWidth = 0;
        int finalImageHeight = 0;
        File newProjectImage = null;
        if (VocabularyEntry.TYPE_IMAGE.equals(type)) {
            if (pendingImagePath != null) {
                try {
                    newProjectImage = MediaFiles.copyToProject(projectDirectory,
                            new File(pendingImagePath), target.id);
                } catch (IOException e) {
                    if (created) {
                        store.entries.remove(target);
                    }
                    toast("图片写入失败，请重试");
                    return;
                }
                finalImage = MediaFiles.relativePath(projectDirectory, newProjectImage);
                if (finalImage == null) {
                    MediaFiles.deleteQuietly(newProjectImage);
                    if (created) {
                        store.entries.remove(target);
                    }
                    toast("图片路径无效");
                    return;
                }
                finalImageWidth = pendingImageWidth;
                finalImageHeight = pendingImageHeight;
            } else if (existing != null) {
                finalImage = existing.image;
                finalImageWidth = existing.imageWidth;
                finalImageHeight = existing.imageHeight;
                if (finalImageWidth <= 0 || finalImageHeight <= 0) {
                    int[] size = MediaFiles.imageSize(
                            MediaFiles.resolveProjectFile(projectDirectory, finalImage));
                    if (size != null) {
                        finalImageWidth = size[0];
                        finalImageHeight = size[1];
                    }
                }
            }
        }

        target.type = type;
        target.meanings.clear();
        target.meanings.addAll(meanings);
        target.codes.clear();
        target.codes.addAll(codes);
        target.text = text;
        target.codepoints.clear();
        target.codepoints.addAll(codepoints);
        target.parts.clear();
        target.parts.addAll(parts);
        String finalAudio = "";
        File newProjectAudio = null;
        if (audioSource != null && audioSource.isFile()) {
            if (pendingAudioPath != null) {
                try {
                    newProjectAudio = MediaFiles.copyAudioToProject(projectDirectory,
                            audioSource, target.id);
                } catch (IOException e) {
                    MediaFiles.deleteQuietly(newProjectImage);
                    if (created) {
                        store.entries.remove(target);
                    }
                    toast("音频写入失败，请重试");
                    return;
                }
                finalAudio = MediaFiles.relativePath(projectDirectory, newProjectAudio);
                if (finalAudio == null) {
                    MediaFiles.deleteQuietly(newProjectAudio);
                    MediaFiles.deleteQuietly(newProjectImage);
                    if (created) {
                        store.entries.remove(target);
                    }
                    toast("音频路径无效");
                    return;
                }
            } else if (existing != null) {
                finalAudio = existing.audio;
            }
        }

        target.image = finalImage;
        target.imageWidth = finalImageWidth;
        target.imageHeight = finalImageHeight;
        target.audio = finalAudio;
        target.audioStart = finalAudioStart;
        target.audioEnd = finalAudioEnd;
        target.audioDuration = finalAudioDuration;
        target.updatedAt = System.currentTimeMillis();
        if (created) {
            store.entries.add(target);
        }
        if (!store.save()) {
            MediaFiles.deleteQuietly(newProjectImage);
            MediaFiles.deleteQuietly(newProjectAudio);
            if (created) {
                store.entries.remove(target);
            }
            toast("保存词汇失败，请重试");
            return;
        }

        if (previousImage != null && !previousImage.isEmpty()
                && !previousImage.equals(finalImage)) {
            MediaFiles.deleteQuietly(
                    MediaFiles.resolveProjectFile(projectDirectory, previousImage));
        }
        if (previousAudio != null && !previousAudio.isEmpty()
                && !previousAudio.equals(finalAudio)) {
            MediaFiles.deleteQuietly(
                    MediaFiles.resolveProjectFile(projectDirectory, previousAudio));
        }
        if (pendingImagePath != null) {
            MediaFiles.deleteQuietly(new File(pendingImagePath));
            pendingImagePath = null;
        }
        if (pendingAudioPath != null) {
            MediaFiles.deleteQuietly(new File(pendingAudioPath));
            pendingAudioPath = null;
        }
        stopPlayback();
        deleteCropSource();
        toast("已保存");
        finish();
    }

    private List<String> collect(List<ForgeTextField> fields, int max) {
        List<String> values = new ArrayList<>();
        for (ForgeTextField field : fields) {
            String value = VocabularyStore.clean(textOf(field), max);
            if (!value.isEmpty()) {
                values.add(value);
            }
        }
        return values;
    }

    private String textOf(ForgeTextField field) {
        CharSequence value = field.getText();
        return value == null ? "" : value.toString();
    }

    private void toast(CharSequence message) {
        ForgeToast.show(this, message, 24);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
