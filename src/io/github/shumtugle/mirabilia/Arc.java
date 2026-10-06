package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;

/**
 * The dial of the voice: the book as a drum turned under a fixed needle.
 *
 * Every paragraph is a tick — or, for a recording, every five seconds. The ticks stand on an arc that falls away at
 * both ends and grow fainter and thinner as they go, so the strip reads as
 * the front of a turning drum rather than a flat rule; every tenth tick is
 * taller, so a distance can be judged at a glance. The paragraph under the
 * needle is thick. Above the drum stands where the voice would land — the
 * page and how far that is from where it is now, in minutes and seconds of
 * listening — and under it the first words of that paragraph.
 *
 * The dial does not read touches itself: it is turned by the finger that
 * is still on the voice's strip, so holding and turning is one gesture.
 *
 * The finger's travel is scaled as the string of pearls is, as a slide
 * rule's: near where it took hold, a tick to each small movement — a
 * paragraph, five seconds — and further off, faster and faster, so that
 * somewhat less than half the screen's width reaches the beginning or the
 * end of the whole book or recording. A little is exact; far is quick; and
 * the finger brought back to where it began brings the needle back too.
 */
final class Arc extends View {

    interface Say {
        String page(int paragraph);

        String words(int paragraph);

        String distance(int paragraph);
    }

    /**
     * How many paragraphs stand on each side of the needle, how far round the
     * drum the eye sees, and the angle each paragraph takes. The drum is not
     * seen to its rim: past about sixty degrees the ticks would crowd into a
     * wall, so they fade out before they get there.
     */
    private static final int SIDE = 18;
    private static final float REACH = (float) Math.toRadians(62.0);
    private static final float STEP = REACH / SIDE;
    private static final float SPAN = 0.47f;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint card = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint place = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint words = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final Say say;
    private int count = 1;
    private float pos;
    private int now;
    /** Every how many ticks one stands taller: ten paragraphs, or a minute of five-second ticks. */
    private int major = 10;
    /** Where the needle stood when the drum rose, how far the finger has gone since, and what the edges added. */
    private float origin;
    private float drag;
    private float rolled;
    /** Each way from where it rose, the travel fitted to the ticks that way: evenly, or on a logarithm. */
    private boolean laid;
    private boolean evenAhead;
    private boolean evenBack;
    private float stepAhead;
    private float stepBack;
    private float fineAhead;
    private float fineBack;
    private float spanAhead;
    private float spanBack;

    Arc(Context c, Say say) {
        super(c);
        this.say = say;
        tick.setStyle(Paint.Style.FILL);
        needle.setStyle(Paint.Style.FILL);
        card.setStyle(Paint.Style.FILL);
        place.setTextAlign(Paint.Align.CENTER);
        place.setTextSize(sp(c, Letter.size(Letter.LABEL_L)));
        place.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        place.setLetterSpacing(0.04f);
        words.setTextAlign(Paint.Align.CENTER);
        words.setTextSize(sp(c, Letter.size(Letter.BODY_M)));
        words.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        tint();
    }

    private static float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
            c.getResources().getDisplayMetrics());
    }

    void tint() {
        card.setColor(Tone.of(Tone.SURFACE_HIGH));
        tick.setColor(Tone.of(Tone.ON_SURFACE));
        needle.setColor(Tone.of(Tone.PRIMARY));
        place.setColor(Tone.of(Tone.PRIMARY));
        words.setColor(Tone.of(Tone.ON_SURFACE));
        invalidate();
    }

    /** How many paragraphs the book has, where the needle starts, and where the voice is now. */
    void set(int paragraphs, int at, int voice) {
        count = Math.max(1, paragraphs);
        pos = clamp(at);
        now = voice;
        origin = pos;
        drag = 0f;
        rolled = 0f;
        laid = false;
        invalidate();
    }

    /**
     * The travel, laid out once the drum knows its width: near the start a
     * tick to each spacing of the drum's ticks, and so much faster further
     * off that a little under half the width reaches the last tick that way.
     */
    private void lay() {
        float near = Math.max(1f, getWidth() * SPAN * STEP / (float) Math.sin(REACH));
        float room = Math.max(near * 4f, getWidth() * 0.42f);
        float[] ahead = fit(count - 1 - origin, room, near);
        float[] back = fit(origin, room, near);
        evenAhead = ahead[0] > 0f;
        stepAhead = ahead[1];
        fineAhead = ahead[2];
        spanAhead = ahead[3];
        evenBack = back[0] > 0f;
        stepBack = back[1];
        fineBack = back[2];
        spanBack = back[3];
        laid = true;
    }

    /** Evenly if every tick can have its spacing within the room; otherwise the logarithm whose end lands on the last tick. */
    private static float[] fit(float ticks, float room, float near) {
        if (ticks <= 0f || near * ticks <= room) {
            return new float[] {1f, near, 0f, 0f};
        }
        double want = room / near;
        double lo = 1e-4;
        double hi = 1e7;
        for (int k = 0; k < 60; k++) {
            double mid = (lo + hi) / 2.0;
            if (mid * Math.log(1.0 + ticks / mid) < want) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        float fine = (float) ((lo + hi) / 2.0);
        return new float[] {0f, near, fine, near * fine};
    }

    /** The ticks passed for the finger's travel from where the drum rose: later to the right, earlier to the left. */
    private float travelled() {
        if (drag >= 0f) {
            return evenAhead ? drag / stepAhead : fineAhead * (float) (Math.exp(drag / spanAhead) - 1.0);
        }
        float d = -drag;
        return -(evenBack ? d / stepBack : fineBack * (float) (Math.exp(d / spanBack) - 1.0));
    }

    void major(int every) {
        major = Math.max(1, every);
        invalidate();
    }

    /** Turns the drum by a distance in pixels; answers the paragraph now under the needle. */
    int turn(float dx) {
        if (!laid && getWidth() > 0) {
            lay();
        }
        if (!laid) {
            return Math.round(pos);
        }
        /* The finger carries the needle's place along the book, as it would
           carry the head of a slider: to the left is earlier, to the right
           later — the same way the edges keep turning. */
        drag += dx;
        pos = clamp(origin + rolled + travelled());
        invalidate();
        return Math.round(pos);
    }

    /** Turns the drum by a number of paragraphs, for the edges that keep turning while held. */
    int roll(float paragraphs) {
        rolled += paragraphs;
        pos = clamp(origin + rolled + (laid ? travelled() : 0f));
        invalidate();
        return Math.round(pos);
    }

    int at() {
        return Math.round(pos);
    }

    private float clamp(float v) {
        return Math.max(0f, Math.min(count - 1, v));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float radius = Round.px(Round.XL);
        shape.set(0f, 0f, w, h);
        canvas.drawRoundRect(shape, radius, radius, card);

        int here = Math.round(pos);
        float textTop = Round.px(16f);
        float line = place.getTextSize() * 1.3f;
        canvas.drawText(say.page(here) + "   " + say.distance(here), w / 2f,
            textTop + place.getTextSize(), place);
        String said = TextUtils.ellipsize(say.words(here), words, w - Round.px(48f),
            TextUtils.TruncateAt.END).toString();
        canvas.drawText(said, w / 2f, textTop + line + words.getTextSize(), words);

        float cx = w / 2f;
        float drumR = w * SPAN / (float) Math.sin(REACH);
        float rimCos = (float) Math.cos(REACH);
        float mid = h * 0.66f;
        float tall = Round.px(22f);
        float sag = Round.px(20f);
        int first = (int) Math.floor(pos) - SIDE;
        for (int i = first; i <= first + SIDE * 2 + 1; i++) {
            if (i < 0 || i >= count) {
                continue;
            }
            float theta = (i - pos) * STEP;
            if (Math.abs(theta) >= REACH) {
                continue;
            }
            /* How far from the rim the tick is: one under the needle, nought at the edge of sight. */
            float cos = ((float) Math.cos(theta) - rimCos) / (1f - rimCos);
            float x = cx + drumR * (float) Math.sin(theta);
            float y = mid + sag * (1f - cos);
            boolean tenth = i % major == 0;
            boolean held = i == here;
            boolean voice = i == now;
            float len = tall * (tenth ? 1.45f : 1f) * (0.55f + 0.45f * cos);
            float thick = Round.px(held ? 5f : (tenth ? 2.2f : 1.6f)) * (0.6f + 0.4f * cos);
            int alpha = (int) (255 * Math.pow(cos, 1.6) * (held ? 0.95f : (tenth ? 0.7f : 0.45f)));
            tick.setColor(voice && !held ? Tone.of(Tone.PRIMARY) : Tone.of(Tone.ON_SURFACE));
            tick.setAlpha(Math.max(0, Math.min(255, voice && !held ? Math.max(alpha, 200) : alpha)));
            shape.set(x - thick / 2f, y - len / 2f, x + thick / 2f, y + len / 2f);
            canvas.drawRoundRect(shape, thick / 2f, thick / 2f, tick);
        }

        float nw = Round.px(2f);
        shape.set(cx - nw / 2f, mid - tall * 1.3f, cx + nw / 2f, h - Round.px(12f));
        canvas.drawRoundRect(shape, nw / 2f, nw / 2f, needle);
    }
}
