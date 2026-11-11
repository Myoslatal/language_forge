package io.github.myoslatal.languageforge;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import io.github.myoslatal.languageforge.uikit.ForgeActivity;
import io.github.myoslatal.languageforge.uikit.ForgeButton;

/** Transparent drawing surface for custom-text vocabulary. */
public class DrawingActivity extends ForgeActivity {

    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_RESULT_PATH = "resultPath";
    public static final String EXTRA_RESULT_WIDTH = "resultWidth";
    public static final String EXTRA_RESULT_HEIGHT = "resultHeight";

    private DrawingView drawingView;
    private ForgeButton brushButton;
    private ForgeButton eraserButton;
    private ForgeButton undoButton;
    private ForgeButton redoButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        int width = getIntent().getIntExtra(EXTRA_WIDTH, 0);
        int height = getIntent().getIntExtra(EXTRA_HEIGHT, 0);
        if (width < 32 || width > 4096 || height < 32 || height > 4096) {
            Toast.makeText(this, "画布尺寸无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.activity_drawing);
        applyTopInset(findViewById(R.id.drawing_topbar));
        applyBottomInset(findViewById(R.id.drawing_root));

        FrameLayout host = findViewById(R.id.drawing_canvas_host);
        drawingView = new DrawingView(this, width, height);
        host.addView(drawingView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        brushButton = findViewById(R.id.btn_brush);
        eraserButton = findViewById(R.id.btn_eraser);
        undoButton = findViewById(R.id.btn_undo);
        redoButton = findViewById(R.id.btn_redo);
        drawingView.setHistoryChangedListener(this::updateHistoryButtons);
        findViewById(R.id.btn_drawing_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_drawing_save).setOnClickListener(v -> save());
        brushButton.setOnClickListener(v -> setEraser(false));
        eraserButton.setOnClickListener(v -> setEraser(true));
        undoButton.setOnClickListener(v -> drawingView.undo());
        redoButton.setOnClickListener(v -> drawingView.redo());
        findViewById(R.id.btn_clear).setOnClickListener(v -> drawingView.clear());
        updateHistoryButtons();
    }

    private void updateHistoryButtons() {
        boolean canUndo = drawingView.canUndo();
        boolean canRedo = drawingView.canRedo();
        undoButton.setEnabled(canUndo);
        redoButton.setEnabled(canRedo);
        undoButton.setAlpha(canUndo ? 1f : 0.35f);
        redoButton.setAlpha(canRedo ? 1f : 0.35f);
    }

    private void setEraser(boolean eraser) {
        drawingView.setEraser(eraser);
        brushButton.setVariant(eraser ? ForgeButton.VARIANT_GHOST : ForgeButton.VARIANT_ON);
        eraserButton.setVariant(eraser ? ForgeButton.VARIANT_ON : ForgeButton.VARIANT_GHOST);
    }

    private void save() {
        Bitmap bitmap = drawingView.getBitmap();
        File output = MediaFiles.cacheFile(this, "drawing");
        if (output == null) {
            Toast.makeText(this, "无法创建绘制文件", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            MediaFiles.writePng(bitmap, output);
        } catch (IOException e) {
            MediaFiles.deleteQuietly(output);
            Toast.makeText(this, "绘制保存失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_RESULT_PATH, output.getAbsolutePath());
        result.putExtra(EXTRA_RESULT_WIDTH, bitmap.getWidth());
        result.putExtra(EXTRA_RESULT_HEIGHT, bitmap.getHeight());
        setResult(RESULT_OK, result);
        finish();
    }
}