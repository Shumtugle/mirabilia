package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

/**
 * Two choices in one capsule — or a few, side by side, when one control
 * chooses among a handful of things of one kind.
 *
 * Each half shows what it chooses: a small drawing of the thing, where it has
 * one, and its name. The chosen half is filled, and when the other is chosen
 * the fill slides across to it — one sign of the choice rather than a ring, a
 * tick and a frame, and a movement the hand can feel. Everything that is a
 * button is a capsule; this is one button with two ends.
 */
final class Pair extends View {

    /** A drawing of one choice, centred on a point and no wider than a span. */
    interface Icon {
        void paint(Canvas canvas, float cx, float cy, float span);
    }

    interface Picked {
        void picked(int which);
    }

    private static final float ICON = 30f;
    private static final float GAP = 10f;
    private static final float INSET = 4f;

    /**
     * The ink of the part being drawn this moment, for drawings that are
     * marks rather than pictures and should follow the choice as the words do.
     */
    static int ink;

    private final String[] names;
    private final Icon[] icons;
    private Picked picked;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint words = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private int chosen;
    /** Where the fill stands, from the first half (0) to the second (1). */
    private float at;
    private ValueAnimator slide;
    private int down = -1;
    private float press;
    private ValueAnimator pressing;

    Pair(Context context, String[] names, Icon[] icons, int chosen, Picked picked) {
        super(context);
        this.names = names;
        this.icons = icons;
        this.picked = picked;
        this.chosen = Math.max(0, Math.min(names.length - 1, chosen));
        this.at = this.chosen;
        words.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        words.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            Letter.size(Letter.LABEL_L), getResources().getDisplayMetrics()));
        words.setLetterSpacing(0.1f / Letter.size(Letter.LABEL_L));
        setClickable(true);
        setContentDescription(names[this.chosen]);
    }

    int chosen() {
        return chosen;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int tall = Round.dp(icons == null ? 48 : 60);
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), tall);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        paint.setColor(Tone.of(Tone.SURFACE_HIGH));
        shape.set(0f, 0f, w, h);
        canvas.drawRoundRect(shape, r, r, paint);

        float inset = Round.px(INSET);
        int parts = names.length;
        float half = (w - 2f * inset) / parts;
        float fillLeft = inset + at * half;
        paint.setColor(Tone.of(Tone.SECONDARY_CONTAINER));
        shape.set(fillLeft, inset, fillLeft + half, h - inset);
        canvas.drawRoundRect(shape, r - inset, r - inset, paint);

        if (down >= 0 && press > 0f) {
            int ink = Tone.of(Tone.ON_SURFACE);
            paint.setColor((Math.round(255f * 0.08f * press) << 24) | (ink & 0x00FFFFFF));
            float left = inset + down * half;
            shape.set(left, inset, left + half, h - inset);
            canvas.drawRoundRect(shape, r - inset, r - inset, paint);
        }

        for (int i = 0; i < parts; i++) {
            /* The ink follows the fill: full where it stands, quiet where it has left. */
            float lit = Math.max(0f, 1f - Math.abs(at - i));
            int ink = blend(Tone.of(Tone.ON_SURFACE_VARIANT),
                Tone.of(Tone.ON_SECONDARY_CONTAINER), lit);
            half(canvas, i, inset + i * half, half, h, ink);
        }
    }

    private void half(Canvas canvas, int i, float left, float width, float h, int ink) {
        float icon = icons != null && icons[i] != null ? Round.px(ICON) : 0f;
        float gap = icon > 0f && names[i].length() > 0 ? Round.px(GAP) : 0f;
        /* With three or more parts the edges of each give up some of their air. */
        float air = Round.px(names.length > 2 ? 16f : 28f);
        float room = width - air - icon - gap;
        /* A word that does not fit is drawn a little smaller before it is ever cut:
           a name cut short is not the name. */
        float size = words.getTextSize();
        float full = words.measureText(names[i]);
        if (full > room && full > 0f) {
            words.setTextSize(size * Math.max(0.82f, room / full));
        }
        CharSequence name = TextUtils.ellipsize(names[i], words, Math.max(0f, room),
            TextUtils.TruncateAt.END);
        float wordsWide = words.measureText(name, 0, name.length());
        float start = left + (width - icon - gap - wordsWide) / 2f;
        float mid = h / 2f;
        if (icon > 0f) {
            Pair.ink = ink;
            icons[i].paint(canvas, start + icon / 2f, mid, icon);
        }
        words.setColor(ink);
        Paint.FontMetrics m = words.getFontMetrics();
        canvas.drawText(name, 0, name.length(), start + icon + gap,
            mid - (m.ascent + m.descent) / 2f, words);
        words.setTextSize(size);
    }

    private static int blend(int from, int to, float t) {
        int a = Math.round(((from >>> 24) & 255) + (((to >>> 24) & 255) - ((from >>> 24) & 255)) * t);
        int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                down = Math.max(0, Math.min(names.length - 1,
                    (int) (e.getX() / Math.max(1f, getWidth()) * names.length)));
                pressTo(1f);
                return true;
            case MotionEvent.ACTION_UP: {
                int which = down;
                pressTo(0f);
                boolean inside = e.getX() >= 0 && e.getX() <= getWidth()
                    && e.getY() >= 0 && e.getY() <= getHeight();
                if (inside && which >= 0 && which != chosen) {
                    choose(which);
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                    performClick();
                    if (picked != null) {
                        picked.picked(which);
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                pressTo(0f);
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    /** The fill slides to a half; the drawings in both halves are drawn again on the way. */
    /** Who is told of a choice, set once there is someone to tell. */
    void setPicked(Picked p) {
        picked = p;
    }

    void choose(int which) {
        chosen = Math.max(0, Math.min(names.length - 1, which));
        setContentDescription(names[chosen]);
        if (slide != null) {
            slide.cancel();
        }
        ValueAnimator made = ValueAnimator.ofFloat(at, chosen);
        made.setDuration(Pace.ARRIVE);
        made.setInterpolator(Pace.EMPHASIS);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                at = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        slide = made;
        made.start();
    }

    private void pressTo(float to) {
        if (pressing != null) {
            pressing.cancel();
        }
        ValueAnimator made = ValueAnimator.ofFloat(press, to);
        made.setDuration(Pace.PRESS);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                press = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        pressing = made;
        made.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (slide != null) {
            slide.cancel();
        }
        if (pressing != null) {
            pressing.cancel();
        }
        super.onDetachedFromWindow();
    }
}
