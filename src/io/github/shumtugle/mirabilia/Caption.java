package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Over an opened picture, the bar of the application comes back: the same
 * capsule at the foot, and in it, where the field names the room, the
 * picture's label, as a museum hangs one beside a print — its number in the
 * album on a plate of the accent, its name in the book face, and one short
 * line under it: when, with what, how large. At the end, the round button,
 * the one every screen of ours is left by, writing its mark as the bar
 * rises. The label touched opens the sheet of facts; the button puts the
 * picture back.
 *
 * It does not go by itself: a label is there to be read, and a touch on the
 * picture puts the bar away again.
 */
final class Caption extends LinearLayout {

    interface Hands {
        /** The label was touched: the sheet of facts is wanted. */
        void more();

        /** The round button was touched: the picture goes back to the grid. */
        void done();
    }

    private final TextView number;
    private final TextView name;
    private final TextView line;
    private final Blob blob;
    private boolean shown;

    Caption(Context c, final Hands hands) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));
        setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        /* The bar is a surface of its own; touches that miss its parts do
           not fall through to the picture under it. */
        setClickable(true);

        LinearLayout field = new LinearLayout(c);
        field.setOrientation(HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(Round.dp(8), 0, Round.dp(8), 0);
        field.setMinimumHeight(Round.dp(56));
        field.setBackground(Round.touch(null, Tone.of(Tone.ON_SURFACE), Round.FULL));
        field.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                hands.more();
            }
        });

        number = Letter.serif(Letter.set(new TextView(c), Letter.LABEL_L));
        number.setTextColor(Tone.of(Tone.ON_PRIMARY));
        number.setBackground(Round.box(Tone.of(Tone.PRIMARY), Round.FULL));
        number.setGravity(Gravity.CENTER);
        number.setMinWidth(Round.dp(40));
        number.setPadding(Round.dp(11), 0, Round.dp(11), 0);
        number.setSingleLine(true);
        field.addView(number, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(40)));

        LinearLayout words = new LinearLayout(c);
        words.setOrientation(VERTICAL);
        name = Letter.serif(Letter.set(new TextView(c), Letter.BODY_L));
        name.setTextColor(Tone.of(Tone.ON_SURFACE));
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        words.addView(name);
        line = Letter.set(new TextView(c), Letter.LABEL_M);
        line.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        line.setSingleLine(true);
        line.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(line);
        LayoutParams wordsAt = new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        wordsAt.leftMargin = Round.dp(14);
        field.addView(words, wordsAt);
        addView(field, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        blob = new Blob(c, Round.px(56f), Tone.of(Tone.PRIMARY_CONTAINER), Tone.of(Tone.ON_PRIMARY_CONTAINER));
        blob.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                blob.depart();
                hands.done();
            }
        });
        LayoutParams blobAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
        blobAt.leftMargin = Round.dp(4);
        addView(blob, blobAt);
        setVisibility(GONE);
    }

    /** Which picture of how many, its name, and the short line, when it is known. */
    void say(int at, int of, String title, String brief) {
        number.setText(of > 1 ? (at + 1) + "\u2009/\u2009" + of : String.valueOf(at + 1));
        name.setText(title);
        line.setText(brief);
    }

    /**
     * A copy just kept is signed: the star that began the touching up comes
     * out over the number's plate, turns an eighth, and goes; the plate
     * breathes once under it. It says done, and that it was a pleasure.
     */
    void sign() {
        final Glyph star = new Glyph(getContext(), Glyph.SPARK, Round.px(28f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.PRIMARY));
        final android.view.ViewGroup host = (android.view.ViewGroup) getParent();
        if (host == null) {
            return;
        }
        android.widget.FrameLayout.LayoutParams at = new android.widget.FrameLayout.LayoutParams(
            Round.dp(28), Round.dp(28));
        at.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
        /* The bar's margin, its own padding, the field's, then the plate: the star at the plate's shoulder. */
        at.leftMargin = Round.dp(8) + Round.dp(6) + Round.dp(8) + number.getWidth() - Round.dp(14);
        at.bottomMargin = Round.dp(16) + getHeight() - Round.dp(18);
        host.addView(star, at);
        star.setScaleX(0f);
        star.setScaleY(0f);
        star.setRotation(-45f);
        star.animate().scaleX(1.15f).scaleY(1.15f).rotation(0f).setStartDelay(Pace.STAGGER)
            .setDuration(Pace.GROW).setInterpolator(Pace.EMPHASIS).withEndAction(new Runnable() {
                public void run() {
                    star.animate().scaleX(0.6f).scaleY(0.6f).alpha(0f).setStartDelay(700L)
                        .setDuration(Pace.GROW).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                            public void run() {
                                host.removeView(star);
                            }
                        }).start();
                }
            }).start();
        number.animate().scaleX(1.12f).scaleY(1.12f).setStartDelay(Pace.STAGGER).setDuration(Pace.PRESS)
            .withEndAction(new Runnable() {
                public void run() {
                    number.animate().scaleX(1f).scaleY(1f).setStartDelay(0L).setDuration(Pace.GROW)
                        .setInterpolator(Pace.EMPHASIS).start();
                }
            }).start();
    }

    /** The short line alone, once the file has been read. */
    void brief(String brief) {
        line.setText(brief);
    }

    boolean shown() {
        return shown;
    }

    /**
     * The bar rises into place and the button writes its mark, as on any
     * screen of ours arriving; or, when the screen is only drawn again, it
     * simply stands there.
     */
    void show(boolean moving) {
        shown = true;
        animate().cancel();
        setVisibility(VISIBLE);
        blob.leaving(false);
        if (!moving) {
            setAlpha(1f);
            setTranslationY(0f);
            blob.leaving(true);
            return;
        }
        setAlpha(0f);
        setTranslationY(Round.px(24f));
        animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        blob.leaving(true);
    }

    void hide() {
        shown = false;
        animate().cancel();
        animate().alpha(0f).translationY(Round.px(24f)).setStartDelay(0L).setDuration(Pace.LEAVE)
            .setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    if (!shown) {
                        setVisibility(GONE);
                    }
                }
            }).start();
    }
}
