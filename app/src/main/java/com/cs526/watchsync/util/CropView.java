package com.cs526.watchsync.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Shows a photo behind a round window. Drag to move it, pinch (or the slider) to zoom.
 * The photo always covers the whole circle, so the avatar never has empty corners.
 */
public class CropView extends View {
    public interface OnZoomChanged {
        void onZoom(float fraction);
    }

    /** Biggest zoom = 5x the smallest one. */
    private static final float MAX_ZOOM = 5f;

    private Bitmap image;
    private final Matrix matrix = new Matrix();
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hole = new Path();
    private final RectF circle = new RectF();
    private final ScaleGestureDetector scaler;

    private float scale = 1f;
    private float minScale = 1f;
    private float tx, ty;          // where the photo's top-left corner is on screen
    private float lastX, lastY;    // last finger position (middle point when 2 fingers)
    private OnZoomChanged zoomListener;

    public CropView(Context c) { this(c, null); }

    public CropView(Context c, @Nullable AttributeSet attrs) {
        super(c, attrs);
        float dp = getResources().getDisplayMetrics().density;
        shade.setColor(Color.argb(170, 0, 0, 0));
        ring.setStyle(Paint.Style.STROKE);
        ring.setColor(Color.WHITE);
        ring.setStrokeWidth(2 * dp);
        scaler = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector d) {
                zoomTo(scale * d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                if (zoomListener != null) zoomListener.onZoom(zoomFraction());
                return true;
            }
        });
    }

    public void setOnZoomChanged(OnZoomChanged l) {
        zoomListener = l;
    }

    public void setImage(Bitmap b) {
        image = b;
        fit();
        invalidate();
    }

    public boolean hasImage() {
        return image != null;
    }

    /** 0 = smallest zoom (photo just covers the circle), 1 = biggest. */
    public float zoomFraction() {
        if (minScale <= 0) return 0f;
        return (float) (Math.log(scale / minScale) / Math.log(MAX_ZOOM));
    }

    /** Zoom from the slider, around the middle of the circle. */
    public void setZoomFraction(float fraction) {
        float f = Math.max(0f, Math.min(1f, fraction));
        zoomTo((float) (minScale * Math.pow(MAX_ZOOM, f)), circle.centerX(), circle.centerY());
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float margin = 24 * getResources().getDisplayMetrics().density;
        float d = Math.min(w, h) - 2 * margin;
        float cx = w / 2f, cy = h / 2f;
        circle.set(cx - d / 2, cy - d / 2, cx + d / 2, cy + d / 2);
        hole.reset();
        hole.setFillType(Path.FillType.EVEN_ODD);
        hole.addRect(0, 0, w, h, Path.Direction.CW);
        hole.addCircle(cx, cy, d / 2, Path.Direction.CW);
        fit();
    }

    /** Smallest zoom, photo in the middle. */
    private void fit() {
        if (image == null || circle.width() <= 0) return;
        minScale = Math.max(circle.width() / image.getWidth(), circle.height() / image.getHeight());
        scale = minScale;
        tx = circle.centerX() - image.getWidth() * scale / 2;
        ty = circle.centerY() - image.getHeight() * scale / 2;
        if (zoomListener != null) zoomListener.onZoom(0f);
    }

    private void zoomTo(float newScale, float fx, float fy) {
        if (image == null) return;
        newScale = Math.max(minScale, Math.min(minScale * MAX_ZOOM, newScale));
        // keep the point under the fingers in the same place
        tx = fx - (fx - tx) * (newScale / scale);
        ty = fy - (fy - ty) * (newScale / scale);
        scale = newScale;
        keepCircleCovered();
        invalidate();
    }

    private void keepCircleCovered() {
        float w = image.getWidth() * scale;
        float h = image.getHeight() * scale;
        tx = Math.min(tx, circle.left);
        tx = Math.max(tx, circle.right - w);
        ty = Math.min(ty, circle.top);
        ty = Math.max(ty, circle.bottom - h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (image == null) return false;
        scaler.onTouchEvent(e);

        // middle point of the fingers that are still down
        int action = e.getActionMasked();
        int skip = action == MotionEvent.ACTION_POINTER_UP ? e.getActionIndex() : -1;
        float sx = 0, sy = 0;
        int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) {
            if (i == skip) continue;
            sx += e.getX(i);
            sy += e.getY(i);
            n++;
        }
        if (n == 0) return true;
        float fx = sx / n, fy = sy / n;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                lastX = fx;
                lastY = fy;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_POINTER_UP:
                lastX = fx;
                lastY = fy;
                break;
            case MotionEvent.ACTION_MOVE:
                tx += fx - lastX;
                ty += fy - lastY;
                lastX = fx;
                lastY = fy;
                keepCircleCovered();
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                performClick();
                break;
            default:
                break;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (image == null) return;
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx, ty);
        canvas.drawBitmap(image, matrix, imagePaint);
        canvas.drawPath(hole, shade);
        canvas.drawCircle(circle.centerX(), circle.centerY(), circle.width() / 2, ring);
    }

    /** The part inside the circle, as a size x size square. */
    @Nullable
    public Bitmap crop(int size) {
        if (image == null || circle.width() <= 0) return null;
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        float f = size / circle.width();
        c.scale(f, f);
        c.translate(-circle.left, -circle.top);
        matrix.setScale(scale, scale);
        matrix.postTranslate(tx, ty);
        c.drawBitmap(image, matrix, imagePaint);
        return out;
    }
}
