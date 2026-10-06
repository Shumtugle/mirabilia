package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.HapticFeedbackConstants;
import android.view.View;

/**
 * A switch, as the platform's design language draws one, in the seed's
 * colours: a track, and a thumb that is small and outlined when off, large
 * and filled with a tick in it when on. It says one thing only — on or off —
 * and it is never also a label for the state, the way a chip that names
 * what it will do is, so there is nothing to read twice.
 *
 * It does not turn itself: whoever holds it decides, since turning some
 * switches on first asks the system for leave, and the switch should only
 * show on once the leave is given.
 */
final class Toggle extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final Path tick = new Path();
    private boolean on;
    /** From off (0) to on (1), while it moves. */
    private float at;
    private ValueAnimator moving;

    Toggle(Context context, boolean on) {
        super(context);
        this.on = on;
        this.at = on ? 1f : 0f;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    boolean on() {
        return on;
    }

    /** Set, moving to it if asked; the hand feels it. */
    void set(boolean to, boolean animate) {
        if (to == on) {
            return;
        }
        on = to;
        if (moving != null) {
            moving.cancel();
        }
        if (!animate) {
            at = on ? 1f : 0f;
            invalidate();
            return;
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        moving = ValueAnimator.ofFloat(at, on ? 1f : 0f);
        moving.setDuration(Pace.ARRIVE);
        moving.setInterpolator(Pace.EMPHASIS);
        moving.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                at = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        moving.start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Round.dp(52), Round.dp(32));
    }

    @Override
    protected void onDetachedFromWindow() {
        if (moving != null) {
            moving.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        float edge = Round.px(2f);

        /* The track: the accent when on; when off, the highest surface with an outline. */
        int offTrack = Tone.of(Tone.SURFACE_HIGHEST);
        int onTrack = Tone.of(Tone.PRIMARY);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mix(offTrack, onTrack, at));
        shape.set(0f, 0f, w, h);
        canvas.drawRoundRect(shape, r, r, paint);
        if (at < 1f) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(edge);
            paint.setColor(Tone.of(Tone.OUTLINE));
            paint.setAlpha(Math.round(255f * (1f - at)));
            shape.set(edge / 2f, edge / 2f, w - edge / 2f, h - edge / 2f);
            canvas.drawRoundRect(shape, r - edge / 2f, r - edge / 2f, paint);
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
        }

        /* The thumb: sixteen across and the outline's colour when off, twenty-four and the ink on the accent when on. */
        float small = Round.px(8f);
        float large = Round.px(12f);
        float thumb = small + (large - small) * at;
        float left = r;
        float right = w - r;
        float cx = left + (right - left) * at;
        float cy = h / 2f;
        paint.setColor(mix(Tone.of(Tone.OUTLINE), Tone.of(Tone.ON_PRIMARY), at));
        canvas.drawCircle(cx, cy, thumb, paint);

        /* On, the thumb carries a tick in the accent's container ink. */
        if (at > 0.5f) {
            float k = (at - 0.5f) * 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeWidth(Round.px(2f));
            paint.setColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
            paint.setAlpha(Math.round(255f * k));
            float u = thumb / 12f;
            tick.reset();
            tick.moveTo(cx - 5f * u, cy + 0.2f * u);
            tick.lineTo(cx - 1.5f * u, cy + 3.6f * u);
            tick.lineTo(cx + 5.2f * u, cy - 3.6f * u);
            canvas.drawPath(tick, paint);
            paint.setAlpha(255);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int r = Math.round(ar + (((b >> 16) & 255) - ar) * t);
        int g = Math.round(ag + (((b >> 8) & 255) - ag) * t);
        int bl = Math.round(ab + ((b & 255) - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
