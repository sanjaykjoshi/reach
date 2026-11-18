package app.reach.launcher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * The thumb strip on the screen edge. Slide along it and the letters near your
 * thumb bulge outwards in a wave; the app list jumps to the letter under it.
 */
final class AlphabetBar extends View {

    interface Listener {
        void onLetterSelected(String letter);
    }

    private static final int ACCENT = 0xFF8AB4F8;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private String[] letters = new String[0];
    private boolean leftHanded;
    private boolean touching;
    private float touchY;
    private int current = -1;
    private Listener listener;

    AlphabetBar(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(true);
    }

    void setLetters(String[] letters) {
        this.letters = letters;
        invalidate();
    }

    void setLeftHanded(boolean leftHanded) {
        this.leftHanded = leftHanded;
        invalidate();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    private float columnWidth() {
        return 44 * density;
    }

    private float cellHeight() {
        if (letters.length == 0) return 0;
        return (getHeight() - getPaddingTop() - getPaddingBottom()) / (float) letters.length;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (letters.length == 0) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                float grab = columnWidth() * 1.3f;
                boolean inColumn = leftHanded ? e.getX() <= grab : e.getX() >= getWidth() - grab;
                if (!inColumn) return false; // let touches outside the strip pass through
                touching = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                update(e.getY());
                return true;
            }
            case MotionEvent.ACTION_MOVE:
                if (touching) update(e.getY());
                return touching;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                touching = false;
                current = -1;
                invalidate();
                return true;
            default:
                return touching;
        }
    }

    private void update(float y) {
        touchY = y;
        float cell = cellHeight();
        if (cell <= 0) return;
        int idx = (int) ((y - getPaddingTop()) / cell);
        idx = Math.max(0, Math.min(letters.length - 1, idx));
        if (idx != current) {
            current = idx;
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (listener != null) listener.onLetterSelected(letters[idx]);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cell = cellHeight();
        if (cell <= 0) return;
        float baseSize = Math.min(cell * 0.62f, 15 * density);
        float centerX = leftHanded ? columnWidth() / 2f : getWidth() - columnWidth() / 2f;
        float waveWidth = 56 * density;

        for (int i = 0; i < letters.length; i++) {
            float cy = getPaddingTop() + cell * (i + 0.5f);
            float f = 0f;
            if (touching) {
                float distance = Math.abs(cy - touchY) / cell;
                f = Math.max(0f, 1f - distance / 4.5f);
                f = f * f * (3 - 2 * f); // smooth bulge
            }
            float x = centerX + (leftHanded ? 1 : -1) * waveWidth * f;
            paint.setTextSize(baseSize * (1f + 0.9f * f));
            if (i == current) {
                paint.setColor(ACCENT);
            } else {
                paint.setColor(Color.WHITE);
                paint.setAlpha((int) (150 + 105 * f));
            }
            canvas.drawText(letters[i], x, cy - (paint.descent() + paint.ascent()) / 2f, paint);
        }
    }
}
