package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.OvershootInterpolator;

/**
 * An empty room is not an error, and it should not look like one.
 *
 * Each room that holds nothing yet shows a shape of its own instead: a soft
 * form with a number of rounded corners, drawn as one closed line around a
 * circle that swells and dips as it goes, with the room's mark standing
 * upright in the middle. Touched, the shape gives a little under the finger
 * and, let go, turns by exactly one of its corners and settles with a small
 * overshoot, so it always comes to rest looking as it did — a thing to turn
 * over in the hand while there is nothing else to do. Quick touches add up:
 * each one turns it one corner further than the last was going.
 */
final class Wonder extends View {

    private static final int STEPS = 240;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path outline = new Path();
    private final float size;
    private final int corners;
    private final float depth;
    private final int kind;

    private float turn;
    private float aim;
    private ValueAnimator turning;

    Wonder(Context context, float sizePx, int corners, float depth, int kind) {
        super(context);
        this.size = sizePx;
        this.corners = corners;
        this.depth = depth;
        this.kind = kind;
        fill.setStyle(Paint.Style.FILL);
        Glyph.pens(pen, solid, sizePx * 0.34f / 24f);
        cut.setStyle(Paint.Style.FILL);
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                spin();
            }
        });
        shape();
    }

    void tint(int body, int ink) {
        fill.setColor(body);
        cut.setColor(body);
        pen.setColor(ink);
        solid.setColor(ink);
        invalidate();
    }

    /** One closed line: a circle whose radius rises at each corner and dips between. */
    private void shape() {
        outline.reset();
        float centre = size / 2f;
        float reach = size / 2f;
        for (int i = 0; i <= STEPS; i++) {
            double angle = 2.0 * Math.PI * i / STEPS;
            double r = reach * (1.0 + depth * Math.cos(corners * angle)) / (1.0 + depth);
            float x = centre + (float) (r * Math.sin(angle));
            float y = centre - (float) (r * Math.cos(angle));
            if (i == 0) {
                outline.moveTo(x, y);
            } else {
                outline.lineTo(x, y);
            }
        }
        outline.close();
    }

    private void spin() {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
        aim += 360f / corners;
        final float from = turn;
        final float to = aim;
        if (turning != null) {
            turning.cancel();
        }
        turning = ValueAnimator.ofFloat(0f, 1f);
        turning.setDuration(Pace.ARRIVE);
        turning.setInterpolator(new OvershootInterpolator(2.2f));
        turning.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator animation) {
                float t = (Float) animation.getAnimatedValue();
                turn = from + (to - from) * t;
                invalidate();
            }
        });
        turning.start();
    }

    /**
     * A little give under the finger, and back when it lifts. The delay is
     * cleared on purpose: a view keeps the delay of the last movement asked
     * of it, and this one arrived a few beats behind the blocks above it.
     */
    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was == pressed) {
            return;
        }
        float to = pressed ? 0.93f : 1f;
        animate().scaleX(to).scaleY(to).setStartDelay(0L).setDuration(Pace.PRESS)
            .setInterpolator(Pace.STANDARD).start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(Math.round(size), Math.round(size));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float centre = size / 2f;
        canvas.save();
        canvas.rotate(turn % 360f, centre, centre);
        canvas.drawPath(outline, fill);
        canvas.restore();
        Glyph.mark(canvas, kind, centre, centre, size * 0.34f / 24f, pen, solid, cut);
    }
}
