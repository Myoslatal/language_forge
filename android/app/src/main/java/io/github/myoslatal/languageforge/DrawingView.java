package io.github.myoslatal.languageforge;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** Transparent drawing canvas with stroke history and replay-based undo/redo. */
public class DrawingView extends View {

    private final Bitmap bitmap;
    private final Canvas bitmapCanvas;
    private final Paint brushPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG
            | Paint.FILTER_BITMAP_FLAG);
    private final RectF imageRect = new RectF();
    private final List<Stroke> strokes = new ArrayList<>();
    private final List<Stroke> redo = new ArrayList<>();
    private final float strokeWidth;
    private Stroke active;
    private boolean eraser;
    private float lastX;
    private float lastY;
    private OnHistoryChangedListener listener;

    public interface OnHistoryChangedListener {
        void onHistoryChanged();
    }

    public DrawingView(Context context, int width, int height) {
        super(context);
        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmapCanvas = new Canvas(bitmap);
        strokeWidth = Math.max(6f, Math.min(width, height) / 28f);
        brushPaint.setColor(Color.BLACK);
        brushPaint.setStyle(Paint.Style.STROKE);
        brushPaint.setStrokeWidth(strokeWidth);
        brushPaint.setStrokeCap(Paint.Cap.ROUND);
        brushPaint.setStrokeJoin(Paint.Join.ROUND);
        setBackgroundColor(0xFFF6F7F9);
    }

    public void setHistoryChangedListener(OnHistoryChangedListener listener) {
        this.listener = listener;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public boolean canUndo() {
        return !strokes.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public void setEraser(boolean eraser) {
        this.eraser = eraser;
    }

    public void undo() {
        if (strokes.isEmpty()) {
            return;
        }
        redo.add(strokes.remove(strokes.size() - 1));
        replay();
        notifyHistory();
    }

    public void redo() {
        if (redo.isEmpty()) {
            return;
        }
        strokes.add(redo.remove(redo.size() - 1));
        replay();
        notifyHistory();
    }

    public void clear() {
        strokes.clear();
        redo.clear();
        active = null;
        bitmap.eraseColor(Color.TRANSPARENT);
        invalidate();
        notifyHistory();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float scale = Math.min((float) w / bitmap.getWidth(),
                (float) h / bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = (w - width) / 2f;
        float top = (h - height) / 2f;
        imageRect.set(left, top, left + width, top + height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawBitmap(bitmap, null, imageRect, bitmapPaint);
        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(1f);
        border.setColor(0xFFDFE3E9);
        canvas.drawRect(imageRect, border);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = mapX(event.getX());
        float y = mapY(event.getY());
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                redo.clear();
                active = new Stroke(eraser, strokeWidth, Color.BLACK);
                active.points.add(new float[]{x, y});
                strokes.add(active);
                lastX = x;
                lastY = y;
                drawDot(active, x, y);
                notifyHistory();
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (active != null) {
                    active.points.add(new float[]{x, y});
                    drawSegment(active, lastX, lastY, x, y);
                    lastX = x;
                    lastY = y;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (active != null) {
                    active = null;
                    notifyHistory();
                }
                return true;
            default:
                return false;
        }
    }

    private void replay() {
        bitmap.eraseColor(Color.TRANSPARENT);
        for (Stroke stroke : strokes) {
            if (stroke.points.isEmpty()) {
                continue;
            }
            if (stroke.points.size() == 1) {
                float[] point = stroke.points.get(0);
                drawDot(stroke, point[0], point[1]);
            } else {
                for (int i = 1; i < stroke.points.size(); i++) {
                    float[] previous = stroke.points.get(i - 1);
                    float[] current = stroke.points.get(i);
                    drawSegment(stroke, previous[0], previous[1], current[0], current[1]);
                }
            }
        }
        invalidate();
    }

    private void drawDot(Stroke stroke, float x, float y) {
        Paint paint = paintFor(stroke);
        paint.setStyle(Paint.Style.FILL);
        bitmapCanvas.drawCircle(x, y, stroke.width / 2f, paint);
        paint.setStyle(Paint.Style.STROKE);
    }

    private void drawSegment(Stroke stroke, float x1, float y1, float x2, float y2) {
        Paint paint = paintFor(stroke);
        bitmapCanvas.drawLine(x1, y1, x2, y2, paint);
    }

    private Paint paintFor(Stroke stroke) {
        brushPaint.setColor(stroke.color);
        brushPaint.setStrokeWidth(stroke.width);
        brushPaint.setXfermode(stroke.eraser
                ? new PorterDuffXfermode(PorterDuff.Mode.CLEAR) : null);
        return brushPaint;
    }

    private void notifyHistory() {
        if (listener != null) {
            listener.onHistoryChanged();
        }
    }

    private float mapX(float x) {
        return (x - imageRect.left) / imageRect.width() * bitmap.getWidth();
    }

    private float mapY(float y) {
        return (y - imageRect.top) / imageRect.height() * bitmap.getHeight();
    }

    private static final class Stroke {
        final List<float[]> points = new ArrayList<>();
        final boolean eraser;
        final float width;
        final int color;

        Stroke(boolean eraser, float width, int color) {
            this.eraser = eraser;
            this.width = width;
            this.color = color;
        }
    }
}
