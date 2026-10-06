package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * What the bar becomes while a book is read aloud.
 *
 * The field steps aside and the voice takes its place, at the same height,
 * so the page above does not move by a line. On the left a mark breathes:
 * five soft bars that rise while the voice speaks and settle when it is
 * held, and give a small start each time a new paragraph begins. Beside it,
 * the words being said, in the book's own face, and under them a thread as
 * long as the book with the voice's place on it. On the right, back a
 * fifteen seconds, hold or go on, and on thirty. The round button beside
 * the strip puts the voice away, and the place it stopped at is kept.
 * Touching the words changes how fast the voice reads; holding them raises
 * the dial, and the same finger, still down, turns it.
 */
final class Voice extends LinearLayout {

    interface Hands {
        void back();

        void hold();

        void on();

        void faster();

        /** The words were held: the dial rises. */
        void dialOpen();

        /** The finger moved along while holding, by so many pixels. */
        void dialTurn(float dx);

        /** Where the finger is across the screen, nought to one, for the edges that keep turning. */
        void dialEdge(float across);

        /** The finger lifted, or the gesture was taken away. */
        void dialClose(boolean take);
    }

    /** How long the words must be held before the dial rises. */
    private static final long HOLD = 380L;

    private final Breath breath;
    private final TextView saying;
    private final TextView where;
    private final Yarn thread;
    private final Glyph holdMark;
    private final Glyph[] marks;

    Voice(Context c, final Hands hands) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(Round.dp(56));
        setPadding(Round.dp(10), 0, Round.dp(6), 0);

        breath = new Breath(c);
        LayoutParams breathParams = new LayoutParams(Round.dp(24), Round.dp(26));
        breathParams.rightMargin = Round.dp(8);
        addView(breath, breathParams);

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setClickable(true);
        words.setOnTouchListener(new Grip(hands));
        saying = Letter.serif(Letter.set(new TextView(c), Letter.BODY_M));
        saying.setSingleLine(true);
        saying.setEllipsize(TextUtils.TruncateAt.END);
        saying.setTypeface(android.graphics.Typeface.create(android.graphics.Typeface.SERIF,
            android.graphics.Typeface.ITALIC));
        words.addView(saying, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        thread = new Yarn(c);
        LayoutParams threadParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Round.dp(4));
        threadParams.topMargin = Round.dp(5);
        threadParams.bottomMargin = Round.dp(3);
        words.addView(thread, threadParams);
        where = Letter.set(new TextView(c), Letter.LABEL_S);
        where.setSingleLine(true);
        words.addView(where);
        addView(words, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Glyph back = key(c, Glyph.REWIND, 40f, new OnClickListener() {
            public void onClick(View v) {
                hands.back();
            }
        });
        holdMark = key(c, Glyph.PAUSE, 44f, new OnClickListener() {
            public void onClick(View v) {
                hands.hold();
            }
        });
        Glyph on = key(c, Glyph.SKIP, 40f, new OnClickListener() {
            public void onClick(View v) {
                hands.on();
            }
        });
        marks = new Glyph[] {back, holdMark, on};
        addView(back);
        addView(holdMark);
        addView(on);
        back.setContentDescription(Words.s("voice_back"));
        on.setContentDescription(Words.s("voice_on"));
        tint();
    }

    private Glyph key(Context c, int kind, float size, final OnClickListener click) {
        Glyph g = new Glyph(c, kind, Round.px(size), kind == Glyph.REWIND || kind == Glyph.SKIP
            ? 0.56f : 0.44f, 0x00000000, 0x00000000, 0);
        g.setClickable(true);
        g.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                click.onClick(v);
            }
        });
        return g;
    }

    /** The voice's own colours: the strip sits on the quiet container, its hold on the accent. */
    void tint() {
        saying.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
        where.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER, 0.72f));
        for (int i = 0; i < marks.length; i++) {
            boolean hold = marks[i] == holdMark;
            marks[i].tint(hold ? Tone.of(Tone.PRIMARY) : 0x00000000, 0x00000000,
                Tone.of(hold ? Tone.ON_PRIMARY : Tone.ON_SECONDARY_CONTAINER));
            marks[i].setBackground(Round.disc(Tone.of(Tone.ON_SECONDARY_CONTAINER)));
        }
        breath.ink.setColor(Tone.of(Tone.PRIMARY));
        thread.track.setColor(Tone.of(Tone.ON_SECONDARY_CONTAINER, 0.18f));
        thread.done.setColor(Tone.of(Tone.PRIMARY));
        invalidate();
    }

    /** Speaking or held: the middle key and the breath follow. */
    void playing(boolean on) {
        holdMark.kind(on ? Glyph.PAUSE : Glyph.PLAY);
        holdMark.setContentDescription(Words.s(on ? "voice_hold" : "voice_go"));
        breath.ease(on ? 1f : 0.18f);
    }

    /** What is being said, where, and how far into the book. */
    void line(String said, String place, float along) {
        saying.setText(said);
        where.setText(place);
        thread.along(along);
    }

    /** A new paragraph begins: the breath gives a small start. */
    void pulse() {
        breath.kick();
    }

    void start() {
        breath.run();
    }

    void end() {
        breath.halt();
    }

    // ------------------------------------------------------------ the grip

    /**
     * The words answer two gestures. A touch and a lift steps the pace. A
     * hold raises the dial, and from then on the same finger turns it: the
     * words keep the gesture, so the finger can wander over the page and the
     * dial still follows. Lifting chooses; the gesture taken away by the
     * system chooses nothing.
     */
    private static final class Grip implements OnTouchListener {
        private final Hands hands;
        private final android.os.Handler clock = new android.os.Handler();
        private float downX;
        private float downY;
        private float lastX;
        private boolean dialing;
        private boolean moved;
        private View held;

        private final Runnable rise = new Runnable() {
            public void run() {
                if (moved || held == null) {
                    return;
                }
                dialing = true;
                held.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                held.getParent().requestDisallowInterceptTouchEvent(true);
                hands.dialOpen();
            }
        };

        Grip(Hands hands) {
            this.hands = hands;
        }

        public boolean onTouch(View v, android.view.MotionEvent e) {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    held = v;
                    downX = e.getRawX();
                    downY = e.getRawY();
                    lastX = downX;
                    moved = false;
                    dialing = false;
                    v.setPressed(true);
                    clock.postDelayed(rise, HOLD);
                    return true;
                case android.view.MotionEvent.ACTION_MOVE:
                    if (dialing) {
                        hands.dialTurn(e.getRawX() - lastX);
                        hands.dialEdge(e.getRawX() / Math.max(1,
                            v.getResources().getDisplayMetrics().widthPixels));
                        lastX = e.getRawX();
                    } else if (Math.abs(e.getRawX() - downX) > Round.px(10f)
                        || Math.abs(e.getRawY() - downY) > Round.px(10f)) {
                        moved = true;
                        clock.removeCallbacks(rise);
                        v.setPressed(false);
                    }
                    return true;
                case android.view.MotionEvent.ACTION_UP:
                    clock.removeCallbacks(rise);
                    v.setPressed(false);
                    if (dialing) {
                        dialing = false;
                        hands.dialClose(true);
                    } else if (!moved) {
                        v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
                        hands.faster();
                    }
                    return true;
                case android.view.MotionEvent.ACTION_CANCEL:
                    clock.removeCallbacks(rise);
                    v.setPressed(false);
                    if (dialing) {
                        dialing = false;
                        hands.dialClose(false);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    // ------------------------------------------------------------ the breath

    /**
     * Five soft bars that rise and fall out of step with one another, like a
     * voice seen rather than heard. The platform does not say how loud the
     * voice is at each moment, so the bars do not pretend to measure it:
     * they breathe, and start a little at each paragraph.
     */
    private static final class Breath extends View {
        final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bar = new RectF();
        private final ValueAnimator clock = ValueAnimator.ofFloat(0f, 1f);
        private float time;
        private float level = 0.18f;
        private float aim = 0.18f;
        private float start;

        Breath(Context c) {
            super(c);
            clock.setDuration(1000L);
            clock.setRepeatCount(ValueAnimator.INFINITE);
            clock.setInterpolator(new LinearInterpolator());
            clock.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator animation) {
                    time += 1f / 60f;
                    level += (aim - level) * 0.08f;
                    start *= 0.9f;
                    invalidate();
                }
            });
        }

        void run() {
            if (!clock.isStarted()) {
                clock.start();
            }
        }

        void halt() {
            clock.cancel();
        }

        void ease(float to) {
            aim = to;
        }

        void kick() {
            start = 1f;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth();
            float h = getHeight();
            int n = 5;
            float wide = w / (n * 2f - 1f);
            float[] pace = {1.7f, 2.3f, 1.3f, 2.9f, 1.9f};
            float[] shift = {0f, 1.1f, 2.3f, 0.6f, 1.7f};
            for (int i = 0; i < n; i++) {
                float swing = 0.5f + 0.5f * (float) Math.sin(time * pace[i] * 2.0 + shift[i]);
                float middle = 1f - Math.abs(i - (n - 1) / 2f) / n;
                float tall = h * (0.18f + level * (0.35f + 0.45f * swing) * middle
                    + start * 0.25f * middle);
                tall = Math.min(h, tall);
                float x = i * wide * 2f;
                bar.set(x, (h - tall) / 2f, x + wide, (h + tall) / 2f);
                canvas.drawRoundRect(bar, wide / 2f, wide / 2f, ink);
            }
        }
    }

    // ------------------------------------------------------------ the thread

    /** The whole book as a thread, and the voice's place on it. */
    private static final class Yarn extends View {
        final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF line = new RectF();
        private float along;

        Yarn(Context c) {
            super(c);
        }

        void along(float to) {
            along = Math.max(0f, Math.min(1f, to));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float h = getHeight();
            float r = h / 2f;
            line.set(0f, 0f, getWidth(), h);
            canvas.drawRoundRect(line, r, r, track);
            float x = Math.max(h, getWidth() * along);
            line.set(0f, 0f, x, h);
            canvas.drawRoundRect(line, r, r, done);
        }
    }
}
