package io.github.shumtugle.mirabilia;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

/**
 * A few steps of one number, turned on a drum under a fixed needle, the way
 * the voice's place is turned.
 *
 * The steps stand on the drum as tall ticks with their numbers under them,
 * small ticks between, and the drum curves away at both ends: ticks narrow
 * and fade as they go round. The one under the needle is the one chosen.
 * The finger drags the drum and, let go, it settles on the nearest step,
 * carried a little further by a fling; a touch to one side of the needle
 * steps it once that way. Every step that passes the needle ticks under
 * the finger and is told at once, so what it chooses can be shown while
 * the drum still turns.
 */
final class Depth extends View {

    interface Turned {
        /** A step came under the needle; done once the drum is still. */
        void turned(int step, boolean done);
    }

    /** Length of drum between two steps, and the small ticks between them. */
    private static final float SPACING = 68f;
    private static final int BETWEEN = 4;
    /**
     * This drum's own: a few steps stand wide apart with small ticks between;
     * a long scale — degrees, a hundred either way — stands close, and only
     * its named steps are tall.
     */
    private final float spacingDp;
    private final int between;
    /** How far round the drum is seen on either side of the needle. */
    private static final double REACH = Math.toRadians(78.0);

    private final String[] labels;
    private final Turned turned;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint numbers = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final int slop;
    /** Where the drum stands, in steps; between two while it turns. */
    private float pos;
    private int told;
    private float downX;
    private float downY;
    private float downPos;
    private boolean dragging;
    private VelocityTracker tracker;
    private ValueAnimator settle;

    Depth(Context context, String[] labels, int start, Turned turned) {
        this(context, labels, start, SPACING, BETWEEN, turned);
    }

    Depth(Context context, String[] labels, int start, float spacingDp, int between, Turned turned) {
        super(context);
        this.spacingDp = spacingDp;
        this.between = between;
        this.labels = labels;
        this.turned = turned;
        pos = Math.max(0, Math.min(labels.length - 1, start));
        told = Math.round(pos);
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        numbers.setTextAlign(Paint.Align.CENTER);
        numbers.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        numbers.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
            Letter.size(Letter.LABEL_M), getResources().getDisplayMetrics()));
        setClickable(true);
        setContentDescription(labels[told]);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), Round.dp(76));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = Round.px(Round.XL);
        paint.setAlpha(255);
        paint.setColor(Tone.of(Tone.SURFACE_HIGH));
        shape.set(0f, 0f, w, h);
        canvas.drawRoundRect(shape, r, r, paint);

        float cx = w / 2f;
        float tickMid = Round.px(30f);
        float numberLine = Round.px(62f);
        float drum = (float) ((cx - Round.px(20f)) / Math.sin(REACH));
        float spacing = Round.px(spacingDp);
        int ink = Tone.of(Tone.ON_SURFACE);
        int quiet = Tone.of(Tone.ON_SURFACE_VARIANT);
        int accent = Tone.of(Tone.PRIMARY);
        int steps = labels.length;
        int nearest = Math.round(Math.max(0f, Math.min(steps - 1, pos)));

        for (int s = 0; s < steps; s++) {
            for (int k = 0; k <= between; k++) {
                if (k > 0 && s == steps - 1) {
                    break;
                }
                float along = (s + k / (float) (between + 1) - pos) * spacing;
                double th = along / drum;
                if (Math.abs(th) >= REACH) {
                    continue;
                }
                float cos = (float) ((Math.cos(th) - Math.cos(REACH)) / (1 - Math.cos(REACH)));
                float x = cx + drum * (float) Math.sin(th);
                /* On a long scale only a named step is tall; the rest are its small ticks. */
                boolean major = k == 0 && (between > 0 || labels[s].length() > 0);
                boolean here = k == 0 && s == nearest;
                float tall = Round.px(major || here ? 26f : 12f) * (0.55f + 0.45f * cos);
                float wide = Round.px(here ? 4f : (major ? 2.5f : 1.5f)) * (0.6f + 0.4f * cos);
                paint.setColor(here ? accent : (major ? ink : quiet));
                paint.setAlpha(Math.round(255f * (here ? 1f : (major ? 0.8f : 0.45f)) * cos));
                shape.set(x - wide / 2f, tickMid - tall / 2f, x + wide / 2f, tickMid + tall / 2f);
                canvas.drawRoundRect(shape, wide / 2f, wide / 2f, paint);
                if (major) {
                    numbers.setColor(here ? accent : quiet);
                    numbers.setAlpha(Math.round(255f * (here ? 1f : 0.75f) * cos));
                    numbers.setTextScaleX(0.6f + 0.4f * cos);
                    canvas.drawText(labels[s], x, numberLine, numbers);
                }
            }
        }

        /* The needle does not move; the drum turns under it. */
        paint.setColor(ink);
        paint.setAlpha(230);
        float needle = Round.px(1.5f);
        shape.set(cx - needle / 2f, Round.px(8f), cx + needle / 2f, Round.px(50f));
        canvas.drawRoundRect(shape, needle / 2f, needle / 2f, paint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float spacing = Round.px(spacingDp);
        int last = labels.length - 1;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopSettling();
                downX = e.getX();
                downY = e.getY();
                downPos = pos;
                dragging = false;
                if (tracker != null) {
                    tracker.recycle();
                }
                tracker = VelocityTracker.obtain();
                tracker.addMovement(e);
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (tracker != null) {
                    tracker.addMovement(e);
                }
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!dragging && Math.abs(dx) > slop && Math.abs(dx) > Math.abs(dy)) {
                    dragging = true;
                    /* A sideways drag is the drum's; the page must not scroll under it. */
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                }
                if (dragging) {
                    float raw = downPos - dx / spacing;
                    /* Past either end the drum gives, but grudgingly. */
                    if (raw < 0f) {
                        raw = raw * 0.3f;
                    } else if (raw > last) {
                        raw = last + (raw - last) * 0.3f;
                    }
                    pos = raw;
                    passed();
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                int target;
                if (dragging) {
                    float fling = 0f;
                    if (tracker != null) {
                        tracker.addMovement(e);
                        tracker.computeCurrentVelocity(1000);
                        fling = tracker.getXVelocity();
                    }
                    target = Math.round(Math.max(0f, Math.min(last, pos - fling / spacing * 0.12f)));
                } else {
                    float off = e.getX() - getWidth() / 2f;
                    int now = Math.round(Math.max(0f, Math.min(last, pos)));
                    if (Math.abs(off) < Round.px(24f)) {
                        target = now;
                    } else {
                        target = Math.max(0, Math.min(last, now + (off > 0 ? 1 : -1)));
                    }
                }
                release();
                settleTo(target);
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                release();
                settleTo(Math.round(Math.max(0f, Math.min(last, pos))));
                return true;
            default:
                return true;
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void release() {
        dragging = false;
        if (tracker != null) {
            tracker.recycle();
            tracker = null;
        }
    }

    /** A step has come under the needle: the finger feels it, and whoever listens is told. */
    private void passed() {
        int at = Math.round(Math.max(0f, Math.min(labels.length - 1, pos)));
        if (at != told) {
            told = at;
            setContentDescription(labels[at]);
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
            if (turned != null) {
                turned.turned(at, false);
            }
        }
    }

    private void settleTo(final int target) {
        stopSettling();
        ValueAnimator made = ValueAnimator.ofFloat(pos, target);
        long far = Math.round(Math.min(1f, Math.abs(pos - target)) * Pace.ARRIVE);
        made.setDuration(Math.max(Pace.PRESS, far));
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                pos = (Float) a.getAnimatedValue();
                passed();
                invalidate();
            }
        });
        made.addListener(new AnimatorListenerAdapter() {
            private boolean cut;

            @Override
            public void onAnimationCancel(Animator a) {
                cut = true;
            }

            @Override
            public void onAnimationEnd(Animator a) {
                if (cut) {
                    return;
                }
                pos = target;
                passed();
                invalidate();
                if (turned != null) {
                    turned.turned(target, true);
                }
            }
        });
        settle = made;
        made.start();
        performClick();
    }

    /**
     * The drum set from outside, without telling anyone: what two fingers
     * chose elsewhere, shown under the needle. A drum in the finger's hands
     * is left alone.
     */
    void show(int step) {
        if (dragging) {
            return;
        }
        stopSettling();
        pos = Math.max(0, Math.min(labels.length - 1, step));
        told = Math.round(pos);
        setContentDescription(labels[told]);
        invalidate();
    }

    private void stopSettling() {
        if (settle != null) {
            settle.cancel();
            settle = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSettling();
        release();
        super.onDetachedFromWindow();
    }
}
