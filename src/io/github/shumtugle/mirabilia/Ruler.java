package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.graphics.Path;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ScrollView;

import java.util.ArrayList;

/**
 * A way through a long list: a string of pearls down its right edge, one
 * pearl for every row, the whole list from the top of the screen to the
 * bottom.
 *
 * While the list moves, one pearl rides on a faint thread at the edge where
 * the eye is in the list, and fades when the list is still. Held there, the
 * pearl turns gold — the dot of the application's i — and the string runs out
 * of it to both ends of the screen: the first row at the top clasp, the last
 * at the bottom one, every row a pearl between them. The gold pearl follows
 * the finger along the string and points at the row whose pearl it is on; a
 * card by it names the row and where it stands, and the list moves so that
 * row stands beside it.
 *
 * The string is laid out once, where it was taken hold of, and there it is
 * magnified, as under a loupe: around that place the pearls are large and
 * apart, a finger's small movement is a row; toward the clasps they grow
 * small and crowd, so the same movement passes tens and then hundreds. The
 * spacing is logarithmic, as the scales of a slide rule crowd toward their
 * end, and it is fitted to the screen, so the ends are always at the
 * clasps; a list short enough to stand at full spacing is simply spread
 * evenly. To go finely somewhere far off, the finger lets go and takes hold
 * again there. Where the list changes its section — a letter of the shelf,
 * a chapter of a folder — the thread has a knot, as a jeweller ties one
 * between pearls, and the sections before and after the one in hand are
 * named at theirs; a section is only a section if it holds more than one
 * row. There is no panel behind the string, only a shade rising from the
 * edge. Let go, the list stands at the row, and the row answers as if touched.
 *
 * It is meant to be played with, not only used. The gold pearl has weight:
 * it follows the finger a moment behind, and the pearls it passes swell as
 * it goes by. The card is large — the row's cover and its name, over the row
 * itself — and each new row comes into it from the side the finger moves to
 * while the last one leaves, as the leaves of a board at a station turn;
 * every pearl passed ticks under the finger, and every knot knocks.
 */
final class Ruler extends View {

    /** One row of the list: the view itself, what the card calls it, and the section it opens or continues. */
    static final class Entry {
        final View row;
        final String title;
        final String section;
        /** Whether the row's picture is round, as a record's label, or a page's corner. */
        final boolean round;
        /** What stands in for a picture the row has not got: its initial, or its number. */
        final String badge;
        private ImageView face;
        private boolean looked;

        Entry(View row, String title, String section, boolean round, String badge) {
            this.row = row;
            this.title = title == null ? "" : title;
            this.section = section == null ? "" : section;
            this.round = round;
            this.badge = badge == null ? "" : badge;
        }

        /** The picture the row shows, once its preview has come; asked of the row itself. */
        Drawable picture() {
            if (!looked) {
                looked = true;
                face = find(row);
            }
            return face == null ? null : face.getDrawable();
        }

        private static ImageView find(View v) {
            if (v instanceof ImageView) {
                return (ImageView) v;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    ImageView got = find(g.getChildAt(i));
                    if (got != null) {
                        return got;
                    }
                }
            }
            return null;
        }
    }

    /**
     * What the string is strung along: a list of some kind, told as rows —
     * how many, what each is called and which section it stands in, the
     * picture it shows, which row is beside a height of the screen, and how
     * to bring a row to a height. A column of rows and a grid of pictures
     * are both told this way.
     */
    interface Track {
        int count();

        String title(int i);

        String section(int i);

        boolean round(int i);

        String badge(int i);

        /** The row's picture, if it has one yet; asking may start it coming. */
        android.graphics.Bitmap picture(int i);

        /** The row beside a height of the screen, or the nearest. */
        int beside(float y);

        /** The list moved so a row stands at a height of the screen, as far as it can. */
        void bring(int i, float y);

        /** Where the eye is in the list, from its top (0) to its end (1). */
        float along();

        /** Any movement the list is still making is stopped. */
        void still();

        /** The row answers as if touched, without being opened. */
        void answer(int i);
    }

    /** A column of rows in a scrolled view, each an entry. */
    static final class Rows implements Track {
        private final ScrollView list;
        private final ArrayList<Entry> entries;

        Rows(ScrollView list, ArrayList<Entry> entries) {
            this.list = list;
            this.entries = entries;
        }

        public int count() {
            return entries.size();
        }

        public String title(int i) {
            return entries.get(i).title;
        }

        public String section(int i) {
            return entries.get(i).section;
        }

        public boolean round(int i) {
            return entries.get(i).round;
        }

        public String badge(int i) {
            return entries.get(i).badge;
        }

        public android.graphics.Bitmap picture(int i) {
            Drawable face = entries.get(i).picture();
            return face instanceof android.graphics.drawable.BitmapDrawable
                ? ((android.graphics.drawable.BitmapDrawable) face).getBitmap() : null;
        }

        private float middleOf(int i) {
            View row = entries.get(i).row;
            return row.getTop() + row.getHeight() / 2f;
        }

        public int beside(float y) {
            float at = list.getScrollY() + y;
            int best = 0;
            float bestD = Float.MAX_VALUE;
            for (int i = 0; i < entries.size(); i++) {
                View row = entries.get(i).row;
                if (at >= row.getTop() && at <= row.getBottom()) {
                    return i;
                }
                float d = Math.abs(middleOf(i) - at);
                if (d < bestD) {
                    bestD = d;
                    best = i;
                }
            }
            return best;
        }

        public void bring(int i, float y) {
            list.scrollTo(0, Math.max(0, Math.round(middleOf(i) - y)));
        }

        public float along() {
            int range = list.getChildCount() == 0 ? 0
                : Math.max(0, list.getChildAt(0).getHeight() - list.getHeight());
            return range == 0 ? 0f : list.getScrollY() / (float) range;
        }

        public void still() {
            list.fling(0);
        }

        public void answer(int i) {
            final View row = entries.get(i).row;
            row.drawableHotspotChanged(row.getWidth() / 2f, row.getHeight() / 2f);
            row.setPressed(true);
            row.postDelayed(new Runnable() {
                public void run() {
                    row.setPressed(false);
                }
            }, 320L);
        }
    }

    /** The spacing of the pearls where the string was taken hold of, and the largest a pearl grows. */
    private static final float NEAR = 26f;
    private static final float PEARL = 11f;
    private static final float GOLD = 18f;

    private static final float ZONE = 44f;
    /** Where the thread runs from the right edge, how far the shade reaches in, and the string's ends. */
    private static final float THREAD = 26f;
    private static final float SHADE = 220f;
    /** Clear of the mark in the corner above, and of the edge below. */
    private static final float TOP = 84f;
    private static final float BOTTOM = 18f;
    private static final long FADE_AFTER = 1300L;

    private final Track track;
    private final boolean[] opens;
    private final boolean sectioned;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final Path clip = new Path();
    private final android.graphics.Rect cropFrom = new android.graphics.Rect();
    private final Paint pictured = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint letter = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint big = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    /** Where the gold pearl is drawn: behind the finger by a little, as a weight follows a hand. */
    private float ballShown;
    private boolean pulling;
    private final Runnable pull = new Runnable() {
        public void run() {
            float gap = ballY - ballShown;
            ballShown += gap * 0.32f;
            if (Math.abs(gap) < 0.5f && !held) {
                ballShown = ballY;
                pulling = false;
                invalidate();
                return;
            }
            invalidate();
            postOnAnimation(this);
        }
    };
    /** The card's leaves: the row leaving, the side it leaves to, and how far the turn has gone. */
    private int leaving = -1;
    private float turnSide = 1f;
    private float turned = 1f;
    private ValueAnimator turning;

    /** How much the riding pearl shows, and how much the string. */
    private float shown;
    private float risen;
    private ValueAnimator showing;
    private ValueAnimator rising;

    private boolean held;
    /** Where the string was taken hold of: the row, and the height it stands at. */
    private int grab;
    private float grabY;
    /** The gold pearl's height, and the row it points at, between two rows while it moves. */
    private float ballY;
    private float pos;
    private int told = -1;
    private long toldAt;
    /** Each half of the string, fitted to its part of the screen: evenly, or on a logarithm. */
    private final Half up = new Half();
    private final Half down = new Half();

    private final Runnable fade = new Runnable() {
        public void run() {
            if (!held) {
                showTo(0f, Pace.LEAVE);
            }
        }
    };

    /**
     * One half of the string: rows away from where it was taken hold of,
     * set out over a distance of the screen. Near spacing is the finger's
     * row; if every row can have it and more, the rows are spread evenly
     * across the distance, otherwise the spacing falls away on a logarithm
     * whose one free number is found so the last row lands on the clasp.
     */
    private static final class Half {
        boolean even;
        float step;
        float span;
        float fine;

        void fit(int rows, float distance, float near) {
            if (rows <= 0 || distance <= 0f) {
                even = true;
                step = near;
                return;
            }
            if (near * rows <= distance) {
                even = true;
                step = distance / rows;
                return;
            }
            even = false;
            /* fine * ln(1 + rows / fine) grows with fine toward rows; find it. */
            float want = distance / near;
            double lo = 1e-4;
            double hi = 1e7;
            for (int k = 0; k < 60; k++) {
                double mid = (lo + hi) / 2.0;
                double got = mid * Math.log(1.0 + rows / mid);
                if (got < want) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }
            fine = (float) ((lo + hi) / 2.0);
            span = near * fine;
        }

        /** How far from the hold a row stands, for its distance in rows. */
        float at(float rows) {
            return even ? rows * step : span * (float) Math.log(1.0 + rows / fine);
        }

        /** The rows for a distance from the hold: the inverse of the above. */
        float rows(float distance) {
            return even ? distance / step : fine * (float) (Math.exp(distance / span) - 1.0);
        }
    }

    Ruler(Context context, ScrollView list, ArrayList<Entry> entries) {
        this(context, new Rows(list, entries));
    }

    Ruler(Context context, Track track) {
        super(context);
        this.track = track;
        int n = track.count();
        opens = new boolean[n];
        int count = 0;
        String before = null;
        for (int i = 0; i < n; i++) {
            String s = track.section(i);
            opens[i] = s.length() > 0 && (i == 0 || !s.equals(before));
            before = s;
            if (opens[i]) {
                count++;
            }
        }
        /* Sections that hold one row each are no sections at all: then the
           string has no knots, rather than a knot at every pearl. */
        if (count * 2 > n) {
            for (int i = 0; i < opens.length; i++) {
                opens[i] = false;
            }
            sectioned = false;
        } else {
            sectioned = count > 0;
        }
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setTextSize(sp(Letter.size(Letter.LABEL_M)));
        small.setTextAlign(Paint.Align.RIGHT);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setTextSize(sp(Letter.size(Letter.TITLE_M)));
        letter.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        letter.setTextSize(sp(Letter.size(Letter.TITLE_L)));
        letter.setTextAlign(Paint.Align.CENTER);
        big.setTypeface(Typeface.create("serif", Typeface.NORMAL));
        big.setTextSize(sp(Letter.size(Letter.HEADLINE_M)));
        big.setTextAlign(Paint.Align.CENTER);
    }

    private float sp(float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
            getResources().getDisplayMetrics());
    }

    /** The list moved: the riding pearl shows where it is, and fades once it stops. */
    void moved() {
        if (track.count() == 0) {
            return;
        }
        if (shown < 1f && (showing == null || !showing.isRunning())) {
            showTo(1f, Pace.PRESS);
        }
        removeCallbacks(fade);
        postDelayed(fade, FADE_AFTER);
        invalidate();
    }

    private void showTo(float to, long over) {
        if (showing != null) {
            showing.cancel();
        }
        showing = ValueAnimator.ofFloat(shown, to);
        showing.setDuration(over);
        showing.setInterpolator(Pace.STANDARD);
        showing.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                shown = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        showing.start();
    }

    private void riseTo(float to, long over) {
        if (rising != null) {
            rising.cancel();
        }
        rising = ValueAnimator.ofFloat(risen, to);
        rising.setDuration(over);
        rising.setInterpolator(Pace.EMPHASIS);
        rising.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                risen = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        rising.start();
    }

    // ------------------------------------------------------------ the string

    private int last() {
        return track.count() - 1;
    }

    private float top() {
        return Round.px(TOP);
    }

    private float bottom() {
        return getHeight() - Round.px(BOTTOM);
    }

    /** Where a row's pearl stands on the string, as it was laid out when taken hold of. */
    private float yOf(float i) {
        return i < grab ? grabY - up.at(grab - i) : grabY + down.at(i - grab);
    }

    /** The row, between two while it moves, whose pearl stands at a height. */
    private float rowAt(float y) {
        float r = y < grabY ? grab - up.rows(grabY - y) : grab + down.rows(y - grabY);
        return Math.max(0f, Math.min(last(), r));
    }

    /** The list stands so the row the gold pearl points at is beside it, as far as the list allows. */
    private void follow() {
        int i = Math.round(pos);
        track.bring(i, ballY);
        if (i != told) {
            long now = System.currentTimeMillis();
            if (now - toldAt > 24L) {
                performHapticFeedback(opens[i] ? HapticFeedbackConstants.KEYBOARD_TAP
                    : HapticFeedbackConstants.CLOCK_TICK);
                toldAt = now;
            }
            if (told >= 0 && held) {
                turn(told, i > told ? 1f : -1f);
            }
            told = i;
        }
    }

    /** The card turns a leaf: the row that was leaves one way, the new one comes from the other. */
    private void turn(int from, float side) {
        leaving = from;
        turnSide = side;
        if (turning != null) {
            turning.cancel();
        }
        turned = 0f;
        turning = ValueAnimator.ofFloat(0f, 1f);
        turning.setDuration(170L);
        turning.setInterpolator(Pace.STANDARD);
        turning.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                turned = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        turning.start();
    }

    private void pullBall() {
        if (!pulling) {
            pulling = true;
            postOnAnimation(pull);
        }
    }

    // ------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (track.count() == 0) {
            return false;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                boolean inZone = e.getX() >= getWidth() - Round.px(ZONE);
                if (!inZone || shown < 0.3f) {
                    return false;
                }
                held = true;
                removeCallbacks(fade);
                track.still();
                grabY = Math.max(top(), Math.min(bottom(), e.getY()));
                grab = track.beside(grabY);
                /* The string is laid out from here: the rows above over the
                   screen above, the rows below over the screen below. */
                up.fit(grab, grabY - top(), Round.px(NEAR));
                down.fit(last() - grab, bottom() - grabY, Round.px(NEAR));
                ballY = grabY;
                ballShown = grabY;
                pos = grab;
                told = grab;
                leaving = -1;
                turned = 1f;
                pullBall();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                riseTo(1f, Pace.ARRIVE);
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                follow();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!held) {
                    return false;
                }
                ballY = Math.max(top(), Math.min(bottom(), e.getY()));
                pos = rowAt(ballY);
                follow();
                pullBall();
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (!held) {
                    return false;
                }
                held = false;
                pos = Math.round(pos);
                follow();
                answer(Math.round(pos));
                riseTo(0f, Pace.LEAVE);
                postDelayed(fade, FADE_AFTER);
                return true;
            }
            default:
                return true;
        }
    }

    private void answer(int i) {
        track.answer(i);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(fade);
        removeCallbacks(pull);
        pulling = false;
        if (turning != null) {
            turning.cancel();
        }
        if (showing != null) {
            showing.cancel();
        }
        if (rising != null) {
            rising.cancel();
        }
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (track.count() == 0) {
            return;
        }
        if (risen > 0.01f) {
            string(canvas);
        }
        if (shown > 0.01f && risen < 0.99f) {
            rider(canvas, shown * (1f - risen));
        }
    }

    private float threadX() {
        return getWidth() - Round.px(THREAD);
    }

    /** The colour of a pearl: the ink of the page, softened toward the ground. */
    private static int pearl() {
        int ink = Tone.of(Tone.ON_SURFACE);
        int ground = Tone.of(Tone.SURFACE_LOW);
        return mix(ink, ground, 0.12f);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    /** One pearl: a round of the pearl's colour, a shade under it and the light on it. */
    private void bead(Canvas canvas, float x, float y, float r, float alpha) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        if (r > Round.px(3f)) {
            paint.setColor(0xFF000000);
            paint.setAlpha(Math.round(70f * alpha));
            canvas.drawCircle(x + r * 0.12f, y + r * 0.22f, r, paint);
        }
        paint.setColor(pearl());
        paint.setAlpha(Math.round(255f * alpha));
        canvas.drawCircle(x, y, r, paint);
        if (r > Round.px(2.2f)) {
            paint.setColor(0xFFFFFFFF);
            paint.setAlpha(Math.round(180f * alpha));
            canvas.drawCircle(x - r * 0.36f, y - r * 0.38f, r * 0.32f, paint);
        }
    }

    /** While the list moves: one pearl on a faint thread, where the eye is in the list. */
    private void rider(Canvas canvas, float alpha) {
        float along = track.along();
        float x = threadX();
        paint.setShader(null);
        paint.setColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        paint.setAlpha(Math.round(70f * alpha));
        paint.setStrokeWidth(Math.max(1f, Round.px(1f)));
        canvas.drawLine(x, top(), x, bottom(), paint);
        float y = top() + (bottom() - top()) * along;
        bead(canvas, x, y, Round.px(9f), alpha);
    }

    private void string(Canvas canvas) {
        float a = risen;
        float w = getWidth();
        float h = getHeight();
        float x = threadX();

        /* The shade from the edge, in place of a panel: the list stays seen. */
        int ground = Tone.of(Tone.SURFACE_LOW);
        paint.setShader(new LinearGradient(w, 0f, w - Round.px(SHADE), 0f,
            (Math.round(235f * a) << 24) | (ground & 0x00FFFFFF), ground & 0x00FFFFFF,
            Shader.TileMode.CLAMP));
        paint.setAlpha(255);
        canvas.drawRect(w - Round.px(SHADE), 0f, w, h, paint);
        paint.setShader(null);

        /* The string runs out of the gold pearl to both clasps as it rises. */
        float spread = 0.3f + 0.7f * a;
        float first = grabY + (yOf(0) - grabY) * spread;
        float end = grabY + (yOf(last()) - grabY) * spread;
        paint.setColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        paint.setAlpha(Math.round(120f * a));
        paint.setStrokeWidth(Math.max(1f, Round.px(1.3f)));
        canvas.drawLine(x, first, x, end, paint);
        clasp(canvas, x, first - Round.px(9f), a);
        clasp(canvas, x, end + Round.px(9f), a);

        /* Knots first, so the pearls lie over the thread and the knots between them. */
        int here = Math.round(pos);
        int start = -1;
        int next = -1;
        if (sectioned) {
            float lastKnot = Float.NaN;
            for (int j = 1; j <= last(); j++) {
                if (!opens[j]) {
                    continue;
                }
                if (j <= here) {
                    start = j;
                } else if (next < 0) {
                    next = j;
                }
                float gap = Math.abs(yOf(j) - yOf(j - 1)) * spread;
                float ky = grabY + (yOf(j - 0.5f) - grabY) * spread;
                if (gap >= Round.px(6f)
                    && (Float.isNaN(lastKnot) || Math.abs(ky - lastKnot) >= Round.px(7f))) {
                    knot(canvas, x, ky, a);
                    lastKnot = ky;
                }
            }
        }

        float lastY = Float.NaN;
        float lastR = 0f;
        for (int i = 0; i <= last(); i++) {
            float y = grabY + (yOf(i) - grabY) * spread;
            /* A pearl is as large as the room it has on the string, up to its size. */
            float room = Math.min(i > 0 ? Math.abs(yOf(i) - yOf(i - 1)) : Float.MAX_VALUE,
                i < last() ? Math.abs(yOf(i + 1) - yOf(i)) : Float.MAX_VALUE) * spread;
            if (room == Float.MAX_VALUE) {
                room = Round.px(NEAR);
            }
            float r = Math.max(Round.px(1.3f), Math.min(Round.px(PEARL), room * 0.42f));
            /* As the gold pearl goes by, the pearls near it swell, and settle after. */
            float near = (y - ballShown) / Round.px(70f);
            r = Math.min(room * 0.5f, r * (1f + 0.45f * (float) Math.exp(-near * near)));
            r = Math.max(Round.px(1.3f), r);
            if (!Float.isNaN(lastY) && y - lastY < lastR + r + Round.px(0.6f)) {
                continue;
            }
            if (Math.abs(y - ballShown) < Round.px(GOLD) + r * 0.5f) {
                lastY = y;
                lastR = r;
                continue;
            }
            bead(canvas, x, y, r, a);
            lastY = y;
            lastR = r;
        }

        if (sectioned) {
            int previous = -1;
            for (int j = (start < 0 ? -1 : start - 1); j >= 1; j--) {
                if (opens[j]) {
                    previous = j;
                    break;
                }
            }
            /* Named are the section before this one and the one after; this one is on the card. */
            if (previous >= 0) {
                label(canvas, previous, grabY + (yOf(previous - 0.5f) - grabY) * spread, x, a);
            } else if (start >= 1 && opens[0]) {
                label(canvas, 0, grabY + (yOf(0) - grabY) * spread, x, a);
            }
            if (next >= 0) {
                label(canvas, next, grabY + (yOf(next - 0.5f) - grabY) * spread, x, a);
            }
        }

        /* The gold pearl, at the finger, with the card beside it. */
        float gold = Round.px(GOLD) * (0.6f + 0.4f * a);
        int layer = canvas.saveLayerAlpha(x - gold * 2f, ballShown - gold * 2f, x + gold * 2.5f,
            ballShown + gold * 2.5f, Math.round(255f * a));
        Desk.ball(canvas, x, ballShown, gold, getResources().getDisplayMetrics().density * 0.6f);
        canvas.restoreToCount(layer);
        card(canvas, here, x - gold - Round.px(12f), a);
    }

    /** A clasp at an end of the string: a small ring, where the list begins or ends. */
    private void clasp(Canvas canvas, float x, float y, float a) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, Round.px(1.5f)));
        paint.setColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        paint.setAlpha(Math.round(210f * a));
        canvas.drawCircle(x, y, Round.px(4f), paint);
        paint.setStyle(Paint.Style.FILL);
    }

    /** A knot on the thread, as a jeweller ties one between pearls. */
    private void knot(Canvas canvas, float x, float y, float a) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Tone.of(Tone.PRIMARY));
        paint.setAlpha(Math.round(235f * a));
        shape.set(x - Round.px(4.5f), y - Round.px(2f), x + Round.px(4.5f), y + Round.px(2f));
        canvas.drawOval(shape, paint);
    }

    /**
     * The name of the section a knot opens, in the serif of the rooms' names,
     * on a round of the accent's container beside the thread — large enough
     * to be read in passing, as the letters on the edge of a card index are.
     */
    private void label(Canvas canvas, int i, float y, float x, float a) {
        if (Math.abs(y - ballShown) < Round.px(58f) || y < top() - Round.px(4f)
            || y > bottom() + Round.px(4f)) {
            return;
        }
        String name = TextUtils.ellipsize(track.section(i), letter, Round.px(140f),
            TextUtils.TruncateAt.END).toString();
        float tall = Round.px(40f);
        float wide = Math.max(tall, letter.measureText(name) + Round.px(24f));
        float right = x - Round.px(16f);
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Tone.of(Tone.PRIMARY_CONTAINER));
        paint.setAlpha(Math.round(250f * a));
        shape.set(right - wide, y - tall / 2f, right, y + tall / 2f);
        canvas.drawRoundRect(shape, tall / 2f, tall / 2f, paint);
        letter.setColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
        letter.setAlpha(Math.round(255f * a));
        Paint.FontMetrics m = letter.getFontMetrics();
        canvas.drawText(name, right - wide / 2f, y - (m.ascent + m.descent) / 2f, letter);
    }

    /**
     * By the gold pearl, the row it points at, large, over the row itself:
     * its picture, or its initial or number where it has none, its name, and
     * where it stands. A new row comes in from the side the finger moves to
     * while the one before leaves by the other.
     */
    private void card(Canvas canvas, int i, float right, float a) {
        float tall = Round.px(96f);
        float left = Round.px(14f);
        float top = ballShown - tall / 2f;
        top = Math.max(top() - Round.px(40f), Math.min(getHeight() - Round.px(8f) - tall, top));
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF000000);
        paint.setAlpha(Math.round(60f * a));
        shape.set(left + Round.px(1f), top + Round.px(4f), right + Round.px(1f), top + tall + Round.px(4f));
        canvas.drawRoundRect(shape, Round.px(28f), Round.px(28f), paint);
        paint.setColor(Tone.of(Tone.SECONDARY_CONTAINER));
        paint.setAlpha(Math.round(255f * a));
        shape.set(left, top, right, top + tall);
        canvas.drawRoundRect(shape, Round.px(28f), Round.px(28f), paint);

        canvas.save();
        clip.reset();
        clip.addRoundRect(shape, Round.px(28f), Round.px(28f), Path.Direction.CW);
        canvas.clipPath(clip);
        float travel = tall * 0.55f;
        if (leaving >= 0 && turned < 1f) {
            leaf(canvas, leaving, left, top - turnSide * turned * travel, right, tall,
                a * (1f - turned));
            leaf(canvas, i, left, top + turnSide * (1f - turned) * travel, right, tall, a * turned);
        } else {
            leaf(canvas, i, left, top, right, tall, a);
        }
        canvas.restore();
    }

    /** One leaf of the card: the picture at the left, the name and the place beside it. */
    private void leaf(Canvas canvas, int i, float left, float top, float right, float tall, float a) {
        String named = track.title(i);
        String section = track.section(i);
        float pad = Round.px(12f);
        float side = tall - 2f * pad;
        float px0 = left + pad;
        float py0 = top + pad;
        android.graphics.Bitmap bits = track.picture(i);
        shape.set(px0, py0, px0 + side, py0 + side);
        float corner = track.round(i) ? side / 2f : Round.px(14f);
        if (bits != null && !bits.isRecycled() && bits.getWidth() > 0 && bits.getHeight() > 0) {
            /* The row's own picture, cut to the middle square; the row's
               drawable itself is left as it is, since the row draws it too. */
            canvas.save();
            clip.reset();
            clip.addRoundRect(shape, corner, corner, Path.Direction.CW);
            canvas.clipPath(clip);
            int bw = bits.getWidth();
            int bh = bits.getHeight();
            int cut = Math.min(bw, bh);
            cropFrom.set((bw - cut) / 2, (bh - cut) / 2, (bw + cut) / 2, (bh + cut) / 2);
            pictured.setAlpha(Math.round(255f * a));
            canvas.drawBitmap(bits, cropFrom, shape, pictured);
            canvas.restore();
        } else {
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Tone.of(Tone.SURFACE_HIGHEST));
            paint.setAlpha(Math.round(255f * a));
            canvas.drawRoundRect(shape, corner, corner, paint);
            big.setColor(Tone.of(Tone.ON_SURFACE));
            big.setAlpha(Math.round(255f * a));
            String badge = TextUtils.ellipsize(track.badge(i), big, side - Round.px(8f),
                TextUtils.TruncateAt.END).toString();
            Paint.FontMetrics m = big.getFontMetrics();
            canvas.drawText(badge, shape.centerX(), shape.centerY() - (m.ascent + m.descent) / 2f, big);
        }

        float tx = px0 + side + Round.px(14f);
        int maxW = Math.round(Math.max(Round.px(40f), right - Round.px(18f) - tx));
        String count = Words.s("row_of").replace("{n}", String.valueOf(i + 1))
            .replace("{m}", String.valueOf(track.count()));
        String where = (sectioned && section.length() > 0 ? section + "  \u00B7  " : "") + count;
        TextPaint sub = new TextPaint(small);
        sub.setTextAlign(Paint.Align.LEFT);
        CharSequence line = TextUtils.ellipsize(where, sub, maxW, TextUtils.TruncateAt.END);
        /* The name on up to two lines, the place under it, the whole set in the card's middle. */
        title.setColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
        title.setAlpha(Math.round(255f * a));
        android.text.StaticLayout set = android.text.StaticLayout.Builder
            .obtain(named, 0, named.length(), title, maxW)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build();
        Paint.FontMetrics sm = sub.getFontMetrics();
        float subTall = sm.descent - sm.ascent;
        float block = set.getHeight() + Round.px(4f) + subTall;
        float y0 = top + (tall - block) / 2f;
        canvas.save();
        canvas.translate(tx, y0);
        set.draw(canvas);
        canvas.restore();
        sub.setColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
        sub.setAlpha(Math.round(200f * a));
        canvas.drawText(line, 0, line.length(), tx, y0 + set.getHeight() + Round.px(4f) - sm.ascent, sub);
    }
}
