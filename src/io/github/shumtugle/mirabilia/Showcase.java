package io.github.shumtugle.mirabilia;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * The widget on a wallpaper, standing still at the head of its screen while
 * the choices under it scroll.
 *
 * It is the only picture on that screen. The controls below are plain, and
 * whatever they choose is answered here: the old face gives way to the new
 * one and the whole settles a little, so the eye stays on the widget and the
 * pleasure of choosing is in its answer, not in the controls.
 */
final class Showcase extends View {

    private final Paint soft = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private final RectF at = new RectF();
    private final float radius;
    private Bitmap now;
    private Bitmap was;
    private float mix = 1f;
    private ValueAnimator fade;

    Showcase(Context context, float radius) {
        super(context);
        this.radius = radius;
    }

    /** A new face, coming in over the old one in the time given, or at once for none. */
    void show(Bitmap face, long over) {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
        if (now == null || over <= 0L) {
            was = null;
            now = face;
            mix = 1f;
            invalidate();
            return;
        }
        was = now;
        now = face;
        mix = 0f;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(over);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                mix = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        made.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                was = null;
                mix = 1f;
                invalidate();
            }
        });
        fade = made;
        made.start();
        /* The widget gives a little and comes back, as a key does under a finger. */
        animate().cancel();
        setScaleX(0.985f);
        setScaleY(0.985f);
        animate().scaleX(1f).scaleY(1f).setStartDelay(0L).setDuration(over)
            .setInterpolator(Pace.STANDARD).start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) {
            fade.cancel();
            fade = null;
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        box.set(0f, 0f, getWidth(), getHeight());
        Desk.wall(canvas, box, radius);
        if (was != null && mix < 1f) {
            face(canvas, was, 1f - mix);
        }
        if (now != null) {
            face(canvas, now, mix);
        }
    }

    private void face(Canvas canvas, Bitmap face, float alpha) {
        float u = getResources().getDisplayMetrics().density;
        float pad = 16f * u;
        float w = box.width() - 2f * pad;
        float h = w * face.getHeight() / (float) face.getWidth();
        at.set(box.left + pad, box.centerY() - h / 2f, box.left + pad + w, box.centerY() + h / 2f);
        soft.setAlpha(Math.round(255f * Math.max(0f, Math.min(1f, alpha))));
        canvas.drawBitmap(face, null, at, soft);
    }
}
