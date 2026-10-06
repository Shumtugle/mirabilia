package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;

/**
 * One frame of a video chosen to be kept as a picture — the very frame, not
 * the moment near it. Even a second holds some thirty; a clip of a moment
 * holds sixty, and a dial of seconds cannot tell them apart.
 *
 * The video stops and its keys go. Under it a drum turns frame by frame, a
 * tick for each, the seconds named; the video follows the drum to the exact
 * frame under the needle. Over it the bar says where: the time, and which
 * frame of the second. The round button keeps the frame — the screen blinks
 * once, as a shutter would, and the chooser stays, for the next frame; a
 * cross in the corner, or back, goes. For a clip of up to twenty seconds the
 * drum holds all of it; for a longer one, ten seconds either way of where the
 * video stood.
 */
final class Still extends FrameLayout {

    interface Hands {
        /** This frame, by its middle in milliseconds, is to be kept. */
        void keep(long ms);

        /** The chooser is put away. */
        void gone();
    }

    private final Film film;
    private final Hands hands;
    private final float fps;
    /** The first frame on the drum, and how many there are. */
    private final int first;
    private final int count;
    private int at;
    private final TextView time;
    private final TextView which;
    private final View shutter;
    private final LinearLayout panel;
    private final LinearLayout away;
    private final Blob blob;
    private boolean leaving;

    Still(Context c, Film film, float fps, Hands hands) {
        super(c);
        this.film = film;
        this.hands = hands;
        this.fps = fps > 1f && fps < 1000f ? fps : 30f;
        setClickable(true);

        long length = Math.max(1L, film.length());
        long now = film.now();
        int all = Math.max(1, (int) Math.floor(length * this.fps / 1000.0));
        int here = Math.max(0, Math.min(all - 1, (int) Math.floor(now * this.fps / 1000.0)));
        if (length <= 20000L) {
            first = 0;
            count = all;
        } else {
            int reach = Math.round(10f * this.fps);
            first = Math.max(0, here - reach);
            count = Math.min(all, here + reach + 1) - first;
        }
        at = here - first;

        shutter = new View(c);
        shutter.setBackgroundColor(0xFFFFFFFF);
        shutter.setAlpha(0f);
        addView(shutter, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        away = new LinearLayout(c);
        away.setPadding(Round.dp(8), Round.dp(8), Round.dp(8), Round.dp(8));
        away.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        away.addView(new Glyph(c, Glyph.CROSS, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT)), new LinearLayout.LayoutParams(Round.dp(24), Round.dp(24)));
        away.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                leave();
            }
        });
        LayoutParams awayAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        awayAt.leftMargin = Round.dp(16);
        awayAt.topMargin = Round.dp(20);
        addView(away, awayAt);

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        String[] ticks = new String[count];
        for (int i = 0; i < count; i++) {
            int frame = first + i;
            int step = Math.max(1, Math.round(this.fps));
            ticks[i] = frame % step == 0 ? clock(frame / this.fps) : "";
        }
        Depth drum = new Depth(c, ticks, at, 10f, 0, new Depth.Turned() {
            public void turned(int step, boolean done) {
                at = step;
                show();
            }
        });
        panel.addView(drum, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Round.dp(24), Round.dp(8), Round.dp(8), Round.dp(8));
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        bar.setClickable(true);
        LinearLayout words = new LinearLayout(c);
        words.setOrientation(LinearLayout.VERTICAL);
        time = Letter.set(new TextView(c), Letter.TITLE_M);
        time.setTextColor(Tone.of(Tone.ON_SURFACE));
        time.setFontFeatureSettings("tnum");
        words.addView(time);
        which = Letter.set(new TextView(c), Letter.LABEL_M);
        which.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        which.setFontFeatureSettings("tnum");
        words.addView(which);
        bar.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        blob = new Blob(c, Round.px(56f), Tone.of(Tone.PRIMARY_CONTAINER), Tone.of(Tone.ON_PRIMARY_CONTAINER));
        blob.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                keep();
            }
        });
        LinearLayout.LayoutParams blobAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blobAt.leftMargin = Round.dp(4);
        bar.addView(blob, blobAt);
        LinearLayout.LayoutParams barAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barAt.topMargin = Round.dp(8);
        panel.addView(bar, barAt);

        LayoutParams panelAt = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        panelAt.setMargins(Round.dp(8), 0, Round.dp(8), Round.dp(16));
        addView(panel, panelAt);
        show();
    }

    /** Seconds as the clock on the drum names them. */
    private static String clock(float seconds) {
        int s = (int) Math.floor(seconds + 0.0001f);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }

    /** The middle of the frame under the needle, in milliseconds: never on a frame's edge. */
    private long middle() {
        return Math.round((first + at + 0.5) * 1000.0 / fps);
    }

    /** The video to the frame under the needle, and the bar says which. */
    private void show() {
        int frame = first + at;
        int second = (int) Math.floor(frame / fps);
        int within = frame - (int) Math.floor(second * fps);
        time.setText(clock(frame / fps) + String.format(Locale.ROOT, ".%02d", within));
        which.setText(Words.s("frame_n").replace("{n}", String.valueOf(within + 1))
            .replace("{m}", String.valueOf(Math.round(fps))));
        film.exact(middle());
    }

    void open() {
        film.picking(true);
        panel.setAlpha(0f);
        panel.setTranslationY(Round.px(48f));
        panel.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
        away.setAlpha(0f);
        away.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        blob.leaving(true);
    }

    /** The frame kept: the screen blinks once, as a shutter, and the chooser stays for the next. */
    private void keep() {
        if (leaving) {
            return;
        }
        hands.keep(middle());
        blob.depart();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        ValueAnimator blink = ValueAnimator.ofFloat(0.55f, 0f);
        blink.setDuration(Pace.GROW);
        blink.setInterpolator(Pace.AWAY);
        blink.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                shutter.setAlpha((Float) a.getAnimatedValue());
            }
        });
        blink.start();
    }

    void leave() {
        if (leaving) {
            return;
        }
        leaving = true;
        panel.animate().alpha(0f).translationY(Round.px(48f)).setDuration(Pace.LEAVE)
            .setInterpolator(Pace.AWAY).start();
        away.animate().alpha(0f).setDuration(Pace.LEAVE).withEndAction(new Runnable() {
            public void run() {
                film.picking(false);
                hands.gone();
            }
        }).start();
    }
}
