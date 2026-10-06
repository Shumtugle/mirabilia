package io.github.shumtugle.mirabilia;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ImageDecoder;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.OutputStream;

/**
 * A picture touched up where it lies, without going anywhere to do it.
 *
 * The picture stays on the screen it was looked at on; it settles a little
 * higher, onto a stage, and the bar rises under it with three ways of
 * touching it — light, frame and geometry — and the round button that keeps
 * the result. Each way is a row of small drawings, one for each thing it
 * can turn, and one drum that turns the one chosen; the panel is the same
 * height for all three, so the picture never moves when the way changes.
 * Nothing is applied and nothing confirmed: every turn of a drum shows at
 * once on a copy the size of the screen, and the picture in full is worked
 * only once, when the round button is touched, away from the screen. What is
 * kept is always a copy beside the original, carrying the camera's marks;
 * the original is never touched.
 *
 * Light: first the automatic drum, from the picture as it is to a sheet of
 * paper made white. Along it the black and white points are read from the
 * picture itself, with more of the extremes let go the further it turns; a
 * middle that has wandered from grey is brought back by a curve; and from
 * the middle of the drum on, the brightest reds and blues are balanced so
 * that white is white. Should the picture be too light for its black point
 * to come down, the strength steps back by itself rather than break the
 * picture. What it finds it writes into brightness, contrast and warmth,
 * which have drums of their own beside it, with saturation; a turn of the
 * automatic drum writes them afresh. Colour is mixed first, then everything
 * else folds into one table for each channel, so a pixel is touched once.
 * While a finger rests on the picture, the light is shown as it was.
 *
 * Frame: the frame on the screen stands still and the picture moves under
 * it — closer on its drum or with two fingers, up to four times, and led by
 * one finger. What is in the frame is what is kept. The shape's drum gives
 * the frame a shape, and the frame flows into it.
 *
 * Geometry: the angle, the lean of either axis and the bend of the lens,
 * the picture drawn through a mesh that the display draws in one pass. Lines
 * in thirds stand in the frame to straighten against. As the picture
 * straightens it stops filling its rectangle, so the frame shrinks — once,
 * for all of it together — until it lies wholly on the picture again, and
 * a finger can lead it only as far as the picture reaches.
 */
final class Mend extends FrameLayout {

    interface Hands {
        /** The copy was written: its address, or null when it could not be. */
        void kept(Uri made);

        /** The work is to be handed to another application, by this address; null when it could not be made. */
        void handed(Uri made);

        /** The touch-up was put away, nothing kept. */
        void gone();
    }

    /** The stops of the automatic light, from nothing done to a sheet of paper made white. */
    static final int[] STOPS = {0, 6, 30, 51, 65, 76, 88, 100};
    private static final float[] ZOOMS = {1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f};
    /**
     * Shapes of the frame, as width over height: the picture's own, free,
     * and each proportion either way round — standing or lying is chosen,
     * not guessed from the picture.
     */
    private static final float[] RATIOS = {0f, -1f, 1f, 4f / 5f, 5f / 4f, 2f / 3f, 3f / 2f, 9f / 16f, 16f / 9f};
    private static final String[] RATIO_WORDS = {null, null, "1:1", "4:5", "5:4", "2:3", "3:2", "9:16", "16:9"};
    private static final int FREE = 1;
    private static final int LIGHT = 0;
    private static final int FRAME = 1;
    private static final int GEOMETRY = 2;
    /** The things each way turns, as their drawings. */
    private static final int[][] PARTS = {
        {Marks.AUTO, Marks.BRIGHT, Marks.CONTRAST, Marks.WARMTH, Marks.SATURATION, Marks.SHARP},
        {Marks.SHAPE, Marks.CLOSER, Marks.SIZE},
        {Marks.ANGLE, Marks.TILT_V, Marks.TILT_H, Marks.LENS},
    };
    private static final String[] PART_WORDS = {
        "p_auto", "p_bright", "p_contrast", "p_warm", "p_saturation",
        "p_shape", "p_closer", "p_angle", "p_tilt_v", "p_tilt_h", "p_lens",
        "p_sharp", "p_size",
    };
    /** What the kept copy is made, against the frame: smaller for sending, larger for printing. */
    private static final float[] SIZES = {0.25f, 0.5f, 1f, 2f, 3f, 4f};
    private static final String[] SIZE_WORDS = {"\u00BC", "\u00BD", null, "2\u00D7", "3\u00D7", "4\u00D7"};
    /** The largest copy made: a larger picture than this is only a heavier one. */
    private static final long MOST_PIXELS = 24000000L;
    private static final int MOST_SIDE = 8192;
    /** The copy worked on the screen is about this long on its longer side. */
    private static final int PREVIEW = 2048;
    /** Squares a side of the mesh a picture is drawn through when it is straightened. */
    private static final int MESH = 48;

    private final Gallery.Photo photo;
    private final Hands hands;
    private final Stage stage;
    private final LinearLayout panel;
    private final FrameLayout partRow;
    private final FrameLayout drumRow;
    private final TextView said;
    private final Blob blob;
    private Depth drum;

    /** What the viewer was showing: drawn until the copy for work is read. */
    private final Bitmap quick;
    private volatile Bitmap base;
    private volatile Bitmap shown;
    private final Bitmap[] buffers = new Bitmap[2];
    private int buffer;

    private int tab = LIGHT;
    private final int[] part = new int[3];

    /* The light: the automatic stop, and the numbers it writes and the hand may change. */
    private int stop;
    private float br;
    private float co;
    private float ga;
    private float te;
    private float sa;
    /** Sharpness, from none to a hundred. */
    private float sharp;
    /** The step of the size drum, and the picture's own size in full, as it stands. */
    private int size = 2;
    private int origW;
    private int origH;

    /* The frame. */
    private int ratio;
    /** The shape of a free frame, as its edges were last left. */
    private float freeAspect = 1f;
    private float zoom = 1f;
    /** The middle of the frame, as fractions of the picture's width and height as it lands. */
    private float cx = 0.5f;
    private float cy = 0.5f;

    /* The geometry, and what it costs the frame. */
    private float angle;
    private float tiltV;
    private float tiltH;
    private float lens;
    private float[] edge;
    private float fit = 1f;
    private Warp meshOf;
    private float meshW;
    private float meshH;
    private float[] mesh;

    private boolean comparing;
    private boolean keeping;
    private boolean leaving;
    /** Whether the hand has changed anything: only then does leaving ask to be sure. */
    private boolean changed;
    /** The way out, a quiet cross in the corner, and whether it has already asked once. */
    private final LinearLayout away;
    private final TextView awayWords;
    /** The way out that keeps nothing here: the work handed straight to another application. */
    private final FrameLayout hand;
    private boolean asking;

    /* A free frame's edge in the finger's hold: which edges, and the picture held still under it. */
    private boolean holding;
    private int held;
    private float holdX;
    private float holdY;
    private float perX;
    private float perY;
    private final RectF heldCrop = new RectF();

    /** How far the picture has come from where the viewer had it to its place on the stage. */
    private float enter;
    /** How dark the room around the frame is, how bright its corners, how clear its thirds. */
    private float dim = 0.92f;
    private float corners;
    private float thirds;
    private final RectF fromWin = new RectF();
    private final RectF fromCrop = new RectF();
    private float morph = 1f;
    private ValueAnimator morphing;
    /** The lowest the frame may reach: a little above the panel, wherever the panel now stands. */
    private float floor;

    private final Object lock = new Object();
    private boolean dirty;
    private boolean working;
    private int[][] wantTables;
    private float wantSa;
    private float wantSharp;

    Mend(Context c, Gallery.Photo photo, Bitmap quick, int startStop, Hands hands) {
        super(c);
        this.photo = photo;
        this.quick = quick;
        this.hands = hands;
        this.stop = Math.max(0, Math.min(STOPS.length - 1, startStop));
        setClickable(true);
        setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST));

        stage = new Stage(c);
        addView(stage, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        said = Letter.set(new TextView(c), Letter.LABEL_L);
        said.setTextColor(Tone.of(Tone.ON_SURFACE));
        said.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        said.setPadding(Round.dp(16), Round.dp(8), Round.dp(16), Round.dp(8));
        said.setAlpha(0f);
        LayoutParams saidAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        saidAt.topMargin = Round.dp(20);
        addView(said, saidAt);

        away = new LinearLayout(c);
        away.setOrientation(LinearLayout.HORIZONTAL);
        away.setGravity(Gravity.CENTER_VERTICAL);
        away.setPadding(Round.dp(8), Round.dp(8), Round.dp(8), Round.dp(8));
        away.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        away.setLayoutTransition(new android.animation.LayoutTransition());
        Glyph cross = new Glyph(c, Glyph.CROSS, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT));
        away.addView(cross, new LinearLayout.LayoutParams(Round.dp(24), Round.dp(24)));
        awayWords = Letter.set(new TextView(c), Letter.LABEL_L);
        awayWords.setText(Words.s("leave_unsaved"));
        awayWords.setTextColor(Tone.of(Tone.ON_SURFACE));
        awayWords.setVisibility(GONE);
        LinearLayout.LayoutParams wordsAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordsAt.leftMargin = Round.dp(10);
        wordsAt.rightMargin = Round.dp(8);
        away.addView(awayWords, wordsAt);
        away.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                away();
            }
        });
        away.setAlpha(0f);

        hand = new FrameLayout(c);
        hand.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        hand.addView(new Glyph(c, Glyph.SHARE, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT)), new FrameLayout.LayoutParams(Round.dp(24), Round.dp(24),
            Gravity.CENTER));
        hand.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                handOn();
            }
        });
        hand.setAlpha(0f);
        LayoutParams handAt = new LayoutParams(Round.dp(40), Round.dp(40), Gravity.TOP | Gravity.END);
        handAt.rightMargin = Round.dp(16);
        handAt.topMargin = Round.dp(20);
        addView(hand, handAt);
        LayoutParams awayAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        awayAt.leftMargin = Round.dp(16);
        awayAt.topMargin = Round.dp(20);
        addView(away, awayAt);

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        partRow = new FrameLayout(c);
        panel.addView(partRow, wide());
        drumRow = new FrameLayout(c);
        LinearLayout.LayoutParams drumAt = wide();
        drumAt.topMargin = Round.dp(8);
        panel.addView(drumRow, drumAt);

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        bar.setClickable(true);
        Pair tabs = new Pair(c, new String[] {Words.s("mend_light"), Words.s("mend_frame"),
            Words.s("mend_geometry")}, null, LIGHT, new Pair.Picked() {
                public void picked(int which) {
                    pick(which);
                }
            });
        bar.addView(tabs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
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
        LinearLayout.LayoutParams barAt = wide();
        barAt.topMargin = Round.dp(8);
        panel.addView(bar, barAt);

        LayoutParams panelAt = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        panelAt.setMargins(Round.dp(8), 0, Round.dp(8), Round.dp(16));
        addView(panel, panelAt);
        panel.setAlpha(0f);
        panel.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                float next = t - Round.px(20f);
                if (next == floor) {
                    return;
                }
                boolean first = floor == 0f;
                if (!first) {
                    seen(fromWin, fromCrop);
                }
                floor = next;
                if (first) {
                    stage.invalidate();
                    return;
                }
                change(null);
            }
        });
        parts(false);

        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final int[] whole = new int[2];
                final Bitmap read = load(app, Mend.this.photo, whole);
                post(new Runnable() {
                    public void run() {
                        if (read == null) {
                            Trace.note("touch-up: picture unread");
                            return;
                        }
                        base = read;
                        /* The decoder may give the size before the turn; the copy says which way round. */
                        boolean across = read.getWidth() >= read.getHeight();
                        boolean given = whole[0] >= whole[1];
                        origW = across == given ? whole[0] : whole[1];
                        origH = across == given ? whole[1] : whole[0];
                        refit();
                        if (stop > 0) {
                            auto();
                        }
                        relight();
                        stage.invalidate();
                        if (waiting) {
                            /* Nothing was on the screen to rise from: it rises now, with its picture. */
                            waiting = false;
                            open();
                        }
                    }
                });
            }
        }, "touch-up").start();
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------ coming and going

    /** Opened with nothing yet to show: the rise waits for the picture. */
    private boolean waiting;

    /** The picture settles onto the stage and the bar rises, its button writing its mark. */
    void open() {
        if (quick == null && base == null) {
            waiting = true;
            return;
        }
        if (stop > 0) {
            tell();
        }
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Pace.GROW);
        made.setInterpolator(Pace.EMPHASIS);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                enter = (Float) a.getAnimatedValue();
                stage.invalidate();
            }
        });
        made.start();
        panel.setTranslationY(Round.px(48f));
        panel.animate().alpha(1f).translationY(0f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        away.animate().alpha(1f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        hand.animate().alpha(1f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        blob.leaving(true);
    }

    private final Runnable unask = new Runnable() {
        public void run() {
            asking = false;
            awayWords.setVisibility(GONE);
        }
    };

    /**
     * The way out without keeping. Nothing changed: it simply goes. Something
     * changed: the cross first opens into words — leave without keeping — and
     * only a second touch, or a second back, leaves; left alone, the words
     * fold away again. Nothing is lost by leaving: the original was never
     * touched; only what was done here is let go.
     */
    void away() {
        if (leaving || keeping) {
            return;
        }
        if (!changed || asking) {
            removeCallbacks(unask);
            leave();
            return;
        }
        asking = true;
        removeCallbacks(hush);
        said.animate().alpha(0f).setDuration(Pace.PRESS).start();
        awayWords.setVisibility(VISIBLE);
        postDelayed(unask, 3000L);
    }

    /** Put away with nothing kept: the picture goes back to where the viewer had it. */
    void leave() {
        if (leaving || keeping) {
            return;
        }
        leaving = true;
        panel.animate().alpha(0f).translationY(Round.px(48f)).setStartDelay(0L).setDuration(Pace.LEAVE)
            .setInterpolator(Pace.AWAY).start();
        said.animate().alpha(0f).setDuration(Pace.LEAVE).start();
        away.animate().alpha(0f).setStartDelay(0L).setDuration(Pace.LEAVE).start();
        hand.animate().alpha(0f).setStartDelay(0L).setDuration(Pace.LEAVE).start();
        ValueAnimator made = ValueAnimator.ofFloat(enter, 0f);
        made.setDuration(Pace.GROW);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                enter = (Float) a.getAnimatedValue();
                stage.invalidate();
            }
        });
        made.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator a) {
                animate().alpha(0f).setDuration(Pace.PRESS).withEndAction(new Runnable() {
                    public void run() {
                        hands.gone();
                    }
                }).start();
            }
        });
        made.start();
    }

    /** The copy could not be written: the bar comes back, as it was. */
    void back() {
        keeping = false;
        panel.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
    }

    // ------------------------------------------------------------ the three ways

    private void pick(int which) {
        if (which == tab) {
            return;
        }
        tab = which;
        parts(true);
        final float d0 = dim;
        final float c0 = corners;
        final float t0 = thirds;
        final float d1 = tab == LIGHT ? 0.92f : 0.62f;
        final float c1 = tab == LIGHT ? 0f : 1f;
        final float t1 = tab == GEOMETRY ? 1f : 0f;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Pace.ARRIVE);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                dim = d0 + (d1 - d0) * t;
                corners = c0 + (c1 - c0) * t;
                thirds = t0 + (t1 - t0) * t;
                stage.invalidate();
            }
        });
        made.start();
    }

    /** The row of drawings of the way chosen, and the drum of the part chosen in it. */
    private void parts(boolean moving) {
        partRow.removeAllViews();
        int[] kinds = PARTS[tab];
        Pair.Icon[] icons = new Pair.Icon[kinds.length];
        String[] names = new String[kinds.length];
        for (int i = 0; i < kinds.length; i++) {
            icons[i] = new Marks(kinds[i]);
            names[i] = "";
        }
        Pair row = new Pair(getContext(), names, icons, part[tab], new Pair.Picked() {
            public void picked(int which) {
                part[tab] = which;
                drum(true);
                tell();
            }
        });
        partRow.addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        drum(moving);
        if (moving) {
            partRow.setAlpha(0f);
            partRow.animate().alpha(1f).setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
            tell();
        }
    }

    private int kind() {
        return PARTS[tab][part[tab]];
    }

    /** The drum of the part chosen, set where that part now stands. */
    private void drum(boolean moving) {
        drumRow.removeAllViews();
        final int kind = kind();
        Context c = getContext();
        Depth.Turned turned = new Depth.Turned() {
            public void turned(int step, boolean done) {
                set(kind, step);
                tell();
            }
        };
        if (kind == Marks.AUTO) {
            String[] words = new String[STOPS.length];
            for (int i = 0; i < words.length; i++) {
                words[i] = "";
            }
            words[0] = Words.s("as_is");
            words[1] = Words.s("for_photo");
            words[words.length - 1] = Words.s("for_paper");
            drum = new Depth(c, words, stop, turned);
        } else if (kind == Marks.SHAPE) {
            String[] shapes = RATIO_WORDS.clone();
            shapes[0] = Words.s("as_is");
            shapes[FREE] = Words.s("free");
            drum = new Depth(c, shapes, ratio, 72f, 2, turned);
        } else if (kind == Marks.CLOSER) {
            String[] steps = new String[ZOOMS.length];
            for (int i = 0; i < steps.length; i++) {
                steps[i] = times(ZOOMS[i]);
            }
            drum = new Depth(c, steps, nearestZoom(), turned);
        } else if (kind == Marks.SIZE) {
            String[] words = SIZE_WORDS.clone();
            words[2] = Words.s("as_is");
            drum = new Depth(c, words, size, turned);
        } else if (kind == Marks.SHARP) {
            String[] steps = new String[21];
            for (int i = 0; i < steps.length; i++) {
                steps[i] = i % 4 == 0 ? String.valueOf(i * 5) : "";
            }
            drum = new Depth(c, steps, Math.round(sharp / 5f), 14f, 0, turned);
        } else if (kind == Marks.ANGLE) {
            drum = new Depth(c, degrees(), Math.round(angle + 45f), 12f, 0, turned);
        } else {
            drum = new Depth(c, hundred(), Math.round((valueOf(kind) + 100f) / 5f), 14f, 0, turned);
        }
        drumRow.addView(drum, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
        if (moving) {
            drum.setAlpha(0f);
            drum.setTranslationY(Round.px(10f));
            drum.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
                .setInterpolator(Pace.STANDARD).start();
        }
    }

    /** A hundred either way in fives, named every twenty. */
    private static String[] hundred() {
        String[] out = new String[41];
        for (int i = 0; i < out.length; i++) {
            int v = -100 + 5 * i;
            out[i] = i % 4 == 0 ? signed(v) : "";
        }
        return out;
    }

    /** Forty five degrees either way, by the degree, named every five. */
    private static String[] degrees() {
        String[] out = new String[91];
        for (int i = 0; i < out.length; i++) {
            int v = i - 45;
            out[i] = v % 5 == 0 ? signed(v) + "\u00B0" : "";
        }
        return out;
    }

    private static String signed(int v) {
        return v < 0 ? "\u2212" + (-v) : (v > 0 ? "+" + v : "0");
    }

    private float valueOf(int kind) {
        switch (kind) {
            case Marks.BRIGHT:
                return br;
            case Marks.CONTRAST:
                return co;
            case Marks.WARMTH:
                return te;
            case Marks.SATURATION:
                return sa;
            case Marks.TILT_V:
                return tiltV;
            case Marks.TILT_H:
                return tiltH;
            case Marks.LENS:
                return lens;
            case Marks.ANGLE:
                return angle;
            default:
                return 0f;
        }
    }

    /** A step of a drum came under its needle: the part it turns takes it. */
    private void set(int kind, final int step) {
        changed = true;
        switch (kind) {
            case Marks.AUTO:
                if (step != stop) {
                    stop = step;
                    auto();
                    relight();
                }
                return;
            case Marks.SHAPE:
                if (step != ratio) {
                    changed = true;
                    change(new Runnable() {
                        public void run() {
                            if (step == FREE) {
                                /* A free frame starts as the frame it was, and the edges take it from there. */
                                freeAspect = frameAspect();
                            }
                            ratio = step;
                            refit();
                        }
                    });
                }
                return;
            case Marks.CLOSER: {
                final float to = ZOOMS[step];
                if (Math.abs(to - zoom) > 0.01f) {
                    change(new Runnable() {
                        public void run() {
                            zoom = to;
                            clampCentre();
                        }
                    });
                }
                return;
            }
            case Marks.ANGLE:
                angle = step - 45f;
                refit();
                stage.invalidate();
                return;
            case Marks.SIZE:
                size = step;
                return;
            case Marks.SHARP:
                if (sharp != step * 5f) {
                    sharp = step * 5f;
                    relight();
                }
                return;
            default:
                break;
        }
        float v = -100f + 5f * step;
        switch (kind) {
            case Marks.BRIGHT:
                br = v;
                break;
            case Marks.CONTRAST:
                co = v;
                break;
            case Marks.WARMTH:
                te = v;
                break;
            case Marks.SATURATION:
                sa = v;
                break;
            case Marks.TILT_V:
                tiltV = v;
                break;
            case Marks.TILT_H:
                tiltH = v;
                break;
            case Marks.LENS:
                lens = v;
                break;
            default:
                break;
        }
        if (tab == LIGHT) {
            relight();
        } else {
            refit();
            stage.invalidate();
        }
    }

    private final Runnable hush = new Runnable() {
        public void run() {
            said.animate().alpha(0f).setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).start();
        }
    };

    /** The name of the part in hand and where it stands, over the stage a moment. */
    private void tell() {
        int kind = kind();
        String value;
        if (kind == Marks.AUTO) {
            value = stop == 0 ? Words.s("as_is") : stop == 1 ? Words.s("for_photo")
                : stop == STOPS.length - 1 ? Words.s("for_paper") : String.valueOf(STOPS[stop]);
        } else if (kind == Marks.SHAPE) {
            value = ratio == 0 ? Words.s("as_is") : ratio == FREE ? Words.s("free") : RATIO_WORDS[ratio];
        } else if (kind == Marks.CLOSER) {
            value = times(zoom);
        } else if (kind == Marks.ANGLE) {
            value = signed(Math.round(angle)) + "\u00B0";
        } else if (kind == Marks.SIZE) {
            int[] out = outSize();
            value = out[0] > 0 ? out[0] + " \u00D7 " + out[1] : (size == 2 ? Words.s("as_is") : SIZE_WORDS[size]);
        } else if (kind == Marks.SHARP) {
            value = String.valueOf(Math.round(sharp));
        } else {
            value = signed(Math.round(valueOf(kind)));
        }
        said.setText(Words.s(PART_WORDS[kind]) + "  \u00B7  " + value);
        said.animate().cancel();
        said.animate().alpha(1f).setDuration(Pace.PRESS).setInterpolator(Pace.STANDARD).start();
        removeCallbacks(hush);
        postDelayed(hush, 1200L);
    }

    private static String times(float z) {
        float r = Math.round(z * 10f) / 10f;
        if (r == Math.floor(r)) {
            return (int) r + "\u00D7";
        }
        return String.format(java.util.Locale.getDefault(), "%.1f", r) + "\u00D7";
    }

    /** The copy's size in pixels, as the frame and the size drum make it; zeros while unknown. */
    private int[] outSize() {
        if (origW <= 0 || origH <= 0) {
            return new int[] {0, 0};
        }
        RectF crop = new RectF();
        look(new RectF(), crop);
        return scaled(Math.round(crop.width() * origW), Math.round(crop.height() * origH), SIZES[size]);
    }

    /** A size times a factor, held within the largest copy made. */
    static int[] scaled(int w, int h, float f) {
        double tw = Math.max(1, w) * (double) f;
        double th = Math.max(1, h) * (double) f;
        double k = 1;
        if (f > 1f) {
            /* Only a larger copy is held within the largest made, and never made
               smaller than the frame itself; the picture's own size is its own. */
            if (tw * th > MOST_PIXELS) {
                k = Math.sqrt(MOST_PIXELS / (tw * th));
            }
            k = Math.min(k, MOST_SIDE / Math.max(tw, th));
            k = Math.max(k, 1.0 / f);
        }
        return new int[] {Math.max(1, (int) Math.round(tw * Math.min(1, k))),
            Math.max(1, (int) Math.round(th * Math.min(1, k)))};
    }

    private int nearestZoom() {
        int best = 0;
        for (int i = 1; i < ZOOMS.length; i++) {
            if (Math.abs(ZOOMS[i] - zoom) < Math.abs(ZOOMS[best] - zoom)) {
                best = i;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ the frame and the geometry

    private Warp warp() {
        return new Warp(lens, tiltV, tiltH, angle);
    }

    /** The picture's own shape, width over height, from whatever of it is at hand. */
    private float aspect() {
        Bitmap b = base != null ? base : quick;
        if (b == null || b.getHeight() == 0) {
            return 4f / 3f;
        }
        return b.getWidth() / (float) b.getHeight();
    }

    private float frameAspect() {
        if (ratio == FREE) {
            return freeAspect;
        }
        return RATIOS[ratio] > 0f ? RATIOS[ratio] : aspect();
    }

    /** The frame at its largest, before the geometry and the zoom take their share. */
    private float fullW() {
        float a = frameAspect();
        float img = aspect();
        return a > img ? 1f : a / img;
    }

    private float fullH() {
        float a = frameAspect();
        float img = aspect();
        return a > img ? img / a : 1f;
    }

    private float cropW() {
        return fullW() * fit / zoom;
    }

    private float cropH() {
        return fullH() * fit / zoom;
    }

    /** The picture's edge as the geometry leaves it, and how far that shrinks the frame. */
    private void refit() {
        Warp w = warp();
        if (w.plain()) {
            edge = null;
            fit = 1f;
        } else {
            edge = w.edge(aspect(), 1f, 32);
            fit = Warp.fit(edge, fullW(), fullH());
        }
        clampCentre();
    }

    /** The frame kept on the picture: within its rectangle, or within its straightened edge. */
    private void clampCentre() {
        float w = cropW();
        float h = cropH();
        if (edge == null) {
            cx = Math.max(w / 2f, Math.min(1f - w / 2f, cx));
            cy = Math.max(h / 2f, Math.min(1f - h / 2f, cy));
            return;
        }
        if (Warp.covers(edge, cx, cy, w, h)) {
            return;
        }
        /* Drawn back towards the middle, where it is sure to lie on the picture, no further than needed. */
        float lo = 0f;
        float hi = 1f;
        float x0 = cx;
        float y0 = cy;
        for (int i = 0; i < 14; i++) {
            float mid = (lo + hi) / 2f;
            if (Warp.covers(edge, x0 + (0.5f - x0) * mid, y0 + (0.5f - y0) * mid, w, h)) {
                hi = mid;
            } else {
                lo = mid;
            }
        }
        cx = x0 + (0.5f - x0) * hi;
        cy = y0 + (0.5f - y0) * hi;
    }

    /**
     * A free frame set from a rectangle of the picture: its shape becomes the
     * frame's, and its size the zoom. Answers false, changing nothing, when the
     * rectangle would leave the picture or grow past it.
     */
    private boolean frameTo(RectF r) {
        if (r.width() <= 0f || r.height() <= 0f) {
            return false;
        }
        if (edge == null && (r.left < -0.0005f || r.top < -0.0005f || r.right > 1.0005f || r.bottom > 1.0005f)) {
            return false;
        }
        if (edge != null && !Warp.covers(edge, r.centerX(), r.centerY(), r.width(), r.height())) {
            return false;
        }
        float was = freeAspect;
        float wasFit = fit;
        freeAspect = r.width() / r.height() * aspect();
        if (edge != null) {
            fit = Warp.fit(edge, fullW(), fullH());
        }
        float z = fullW() * fit / r.width();
        if (z < 0.999f || z > 8f) {
            freeAspect = was;
            fit = wasFit;
            return false;
        }
        zoom = Math.max(1f, z);
        cx = r.centerX();
        cy = r.centerY();
        return true;
    }

    /** The frame led by a finger, as far as the picture reaches, sliding along its edge. */
    private void lead(float dx, float dy) {
        float w = cropW();
        float h = cropH();
        if (edge == null) {
            cx += dx;
            cy += dy;
            clampCentre();
            return;
        }
        if (Warp.covers(edge, cx + dx, cy + dy, w, h)) {
            cx += dx;
            cy += dy;
        } else if (Warp.covers(edge, cx + dx, cy, w, h)) {
            cx += dx;
        } else if (Warp.covers(edge, cx, cy + dy, w, h)) {
            cy += dy;
        }
    }

    /** Where the frame may stand: over the panel, with a margin all round. */
    private void room(RectF r) {
        float w = stage.getWidth();
        float h = stage.getHeight();
        float bottom = floor > 0f ? floor : h * 0.55f;
        r.set(Round.px(16f), Round.px(72f), w - Round.px(16f), Math.max(Round.px(160f), bottom));
    }

    /** The frame on the screen and the part of the picture in it, as they are to be. */
    private void look(RectF win, RectF crop) {
        if (holding) {
            /* While an edge is in the hand, the picture does not move: the frame's
               edge follows the finger over it, and fits the room again on release. */
            float w = cropW();
            float h = cropH();
            crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
            win.set(holdX + crop.left * perX, holdY + crop.top * perY,
                holdX + crop.right * perX, holdY + crop.bottom * perY);
            return;
        }
        room(win);
        float a = frameAspect();
        float rw = win.width();
        float rh = win.height();
        float fw = rw;
        float fh = rw / a;
        if (fh > rh) {
            fh = rh;
            fw = rh * a;
        }
        float mx = win.centerX();
        float my = win.centerY();
        win.set(mx - fw / 2f, my - fh / 2f, mx + fw / 2f, my + fh / 2f);
        float w = cropW();
        float h = cropH();
        crop.set(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f);
    }

    /** As they are drawn this moment: on their way from where they were, if they are moving. */
    private void seen(RectF win, RectF crop) {
        look(win, crop);
        if (morph < 1f) {
            lerp(win, fromWin, win, morph);
            lerp(crop, fromCrop, crop, morph);
        }
    }

    private static void lerp(RectF out, RectF a, RectF b, float t) {
        out.set(a.left + (b.left - a.left) * t, a.top + (b.top - a.top) * t,
            a.right + (b.right - a.right) * t, a.bottom + (b.bottom - a.bottom) * t);
    }

    /**
     * A change of the frame, flowed into rather than jumped to. With nothing
     * to change, the frame flows from where it was last seen — the room it
     * stands in has moved.
     */
    private void change(Runnable what) {
        if (what != null) {
            seen(fromWin, fromCrop);
            what.run();
        }
        if (morphing != null) {
            morphing.cancel();
        }
        morph = 0f;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Pace.ARRIVE);
        made.setInterpolator(Pace.EMPHASIS);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                morph = (Float) a.getAnimatedValue();
                stage.invalidate();
            }
        });
        morphing = made;
        made.start();
    }

    private void settle() {
        if (morphing != null) {
            morphing.cancel();
            morphing = null;
        }
        morph = 1f;
    }

    /** The mesh for a picture of this size under the geometry of the moment. */
    private float[] meshFor(float w, float h) {
        Warp now = warp();
        if (mesh == null || meshW != w || meshH != h || !now.same(meshOf)) {
            mesh = now.mesh(w, h, MESH);
            meshOf = now;
            meshW = w;
            meshH = h;
        }
        return mesh;
    }

    // ------------------------------------------------------------ the light

    /** The automatic stop read from the picture in the frame, written into the numbers of the light. */
    private void auto() {
        if (stop == 0) {
            br = 0f;
            co = 0f;
            ga = 0f;
            te = 0f;
            return;
        }
        Bitmap b = base;
        if (b == null) {
            return;
        }
        RectF crop = new RectF();
        look(new RectF(), crop);
        float[] v = autoOf(b, crop, STOPS[stop]);
        br = v[0];
        co = v[1];
        ga = v[2];
        te = v[3];
    }

    /** The light worked again on the copy for the screen, away from it; the last wish wins. */
    private void relight() {
        final int[][] tables = plainLight() && sharp == 0f ? null : tables(br, co, ga, te);
        final float saturation = sa;
        final float sharpness = sharp;
        synchronized (lock) {
            wantTables = tables;
            wantSa = saturation;
            wantSharp = sharpness;
            dirty = true;
            if (working) {
                return;
            }
            working = true;
        }
        new Thread(new Runnable() {
            public void run() {
                while (true) {
                    int[][] l;
                    float s;
                    float edge;
                    synchronized (lock) {
                        l = wantTables;
                        s = wantSa;
                        edge = wantSharp;
                        dirty = false;
                    }
                    Bitmap src = base;
                    if (src == null) {
                        synchronized (lock) {
                            working = false;
                        }
                        return;
                    }
                    Bitmap out = null;
                    if (l != null) {
                        int k = buffer;
                        buffer ^= 1;
                        if (buffers[k] == null) {
                            buffers[k] = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
                        }
                        paint(src, buffers[k], l, s);
                        if (edge > 0f) {
                            sharpen(buffers[k], edge, 2);
                        }
                        out = buffers[k];
                    }
                    final Bitmap now = out;
                    post(new Runnable() {
                        public void run() {
                            shown = now;
                            stage.invalidate();
                        }
                    });
                    synchronized (lock) {
                        if (!dirty) {
                            working = false;
                            return;
                        }
                    }
                }
            }
        }, "light").start();
    }

    private boolean plainLight() {
        return br == 0f && co == 0f && ga == 0f && te == 0f && sa == 0f;
    }

    /**
     * What a stop of the automatic light makes of this picture, read from the
     * part of it in the frame: brightness, contrast, curve and warmth.
     */
    static float[] autoOf(Bitmap b, RectF crop, int stopValue) {
        float[] none = {0f, 0f, 0f, 0f};
        float t = Math.max(0, Math.min(100, stopValue)) / 100f;
        if (t <= 0f) {
            return none;
        }
        float k = 0.15f + 0.85f * t;
        float paper = Math.max(0f, (t - 0.5f) * 2f);
        int w = b.getWidth();
        int h = b.getHeight();
        int x0 = Math.max(0, (int) Math.floor(crop.left * w));
        int y0 = Math.max(0, (int) Math.floor(crop.top * h));
        int x1 = Math.min(w, (int) Math.ceil(crop.right * w));
        int y1 = Math.min(h, (int) Math.ceil(crop.bottom * h));
        if (x1 <= x0 || y1 <= y0) {
            return none;
        }
        /* Every pixel need not be asked: a quarter of a million tell the same story. */
        long all = (long) (x1 - x0) * (y1 - y0);
        int stride = Math.max(1, (int) Math.round(Math.sqrt(all / 250000.0)));
        int[] lum = new int[256];
        int[] red = new int[256];
        int[] blue = new int[256];
        int n = 0;
        int[] row = new int[x1 - x0];
        for (int y = y0; y < y1; y += stride) {
            b.getPixels(row, 0, x1 - x0, x0, y, x1 - x0, 1);
            for (int x = 0; x < row.length; x += stride) {
                int p = row[x];
                int r = (p >> 16) & 255;
                int g = (p >> 8) & 255;
                int bl = p & 255;
                int l = (int) (r * 0.2126f + g * 0.7152f + bl * 0.0722f);
                lum[l > 255 ? 255 : l]++;
                red[r]++;
                blue[bl]++;
                n++;
            }
        }
        if (n == 0) {
            return none;
        }
        float[] strengths = {k, 0.65f, 0.50f, 0.35f, 0.20f, 0.05f};
        float br = 0f;
        float co = 0f;
        float ga = 0f;
        for (int i = 0; i < strengths.length; i++) {
            if (strengths[i] > k) {
                continue;
            }
            float ks = strengths[i];
            double trim = 0.005 * Math.pow(10, (ks - 0.5) * 1.2);
            double hw = 0.10 * Math.pow(2, (0.5 - ks) * 2);
            double gaCap = 30 * Math.pow(2, (ks - 0.5) * 2);
            double cut = n * trim;
            int lo = 0;
            int hi = 255;
            int med = 128;
            long acc = 0;
            for (int j = 0; j < 256; j++) {
                acc += lum[j];
                if (acc > cut) {
                    lo = j;
                    break;
                }
            }
            acc = 0;
            for (int j = 255; j >= 0; j--) {
                acc += lum[j];
                if (acc > cut) {
                    hi = j;
                    break;
                }
            }
            acc = 0;
            for (int j = 0; j < 256; j++) {
                acc += lum[j];
                if (acc >= n / 2.0) {
                    med = j;
                    break;
                }
            }
            double l = lo / 255.0;
            double u = hi / 255.0;
            double m = med / 255.0;
            if (!(u > l)) {
                continue;
            }
            double s = 1.0 / (u - l);
            double off = -l / (u - l);
            double bb = s + 2 * off;
            if (!(bb > 0)) {
                continue;
            }
            double cc = s / bb;
            double gam = 0;
            if (m < 0.5 - hw || m > 0.5 + hw) {
                double mm = Math.max(0.02, Math.min(0.98, s * m + off));
                double g = Math.log(0.5) / Math.log(mm);
                if (g > 0) {
                    gam = Math.max(-gaCap, Math.min(gaCap, -100 * Math.log(g) / Math.log(2)));
                }
            }
            br = Math.round(Math.max(-100, Math.min(100, (bb - 1) * 100)));
            co = Math.round(Math.max(-100, Math.min(100, (cc - 1) * 100)));
            ga = Math.round(Math.max(-100, Math.min(100, gam)));
            break;
        }
        float te = 0f;
        if (paper > 0f) {
            /* White is white: the brightest reds and blues, a hundredth of the picture let go. */
            double cut = n * 0.01;
            int wr = 255;
            int wb = 255;
            long acc = 0;
            for (int j = 255; j >= 0; j--) {
                acc += red[j];
                if (acc > cut) {
                    wr = j;
                    break;
                }
            }
            acc = 0;
            for (int j = 255; j >= 0; j--) {
                acc += blue[j];
                if (acc > cut) {
                    wb = j;
                    break;
                }
            }
            if (wr > 0 && wb > 0) {
                float whole = Math.max(-100f, Math.min(100f, Math.round(220f * (wb - wr) / (float) (wb + wr))));
                te = Math.round(Math.max(-100f, Math.min(100f, whole * paper)));
            }
        }
        return new float[] {br, co, ga, te};
    }

    /**
     * The four numbers of the light as a table for each channel: brightness
     * and contrast as one straight line, the warmth leaning red against blue,
     * and the curve on top.
     */
    static int[][] tables(float br, float co, float ga, float te) {
        float b = 1f + br / 100f;
        float c = 1f + co / 100f;
        float t = te / 220f;
        float inter = b * 0.5f * (1f - c);
        float[] lean = {1f + t, 1f, 1f - t};
        double g = Math.pow(2, -ga / 100.0);
        int[][] out = new int[3][256];
        for (int ch = 0; ch < 3; ch++) {
            float slope = Math.max(0f, b * c * lean[ch]);
            for (int v = 0; v < 256; v++) {
                float x = Math.max(0f, Math.min(1f, slope * (v / 255f) + inter));
                out[ch][v] = Math.max(0, Math.min(255, (int) Math.round(Math.pow(x, g) * 255.0)));
            }
        }
        return out;
    }

    /**
     * Every pixel through the colour mix and then the tables, a strip at a
     * time; the picture may be its own target.
     */
    static void paint(Bitmap src, Bitmap dst, int[][] l, float saturation) {
        int w = src.getWidth();
        int h = src.getHeight();
        int rows = Math.max(1, 262144 / Math.max(1, w));
        int[] px = new int[w * rows];
        int[] r = l[0];
        int[] g = l[1];
        int[] b = l[2];
        boolean mix = saturation != 0f;
        float s = Math.max(0f, 1f + saturation / 100f);
        /* The mixing of a saturation, as the display's own filters mix it, in thousandths. */
        int rr = Math.round((0.213f + 0.787f * s) * 1024f);
        int rg = Math.round((0.715f - 0.715f * s) * 1024f);
        int rb = Math.round((0.072f - 0.072f * s) * 1024f);
        int gr = Math.round((0.213f - 0.213f * s) * 1024f);
        int gg = Math.round((0.715f + 0.285f * s) * 1024f);
        int gb = Math.round((0.072f - 0.072f * s) * 1024f);
        int br = Math.round((0.213f - 0.213f * s) * 1024f);
        int bg = Math.round((0.715f - 0.715f * s) * 1024f);
        int bb = Math.round((0.072f + 0.928f * s) * 1024f);
        for (int y = 0; y < h; y += rows) {
            int count = Math.min(rows, h - y);
            src.getPixels(px, 0, w, 0, y, w, count);
            int end = w * count;
            for (int i = 0; i < end; i++) {
                int p = px[i];
                int pr = (p >> 16) & 255;
                int pg = (p >> 8) & 255;
                int pb = p & 255;
                if (mix) {
                    int nr = (rr * pr + rg * pg + rb * pb) >> 10;
                    int ng = (gr * pr + gg * pg + gb * pb) >> 10;
                    int nb = (br * pr + bg * pg + bb * pb) >> 10;
                    pr = nr < 0 ? 0 : (nr > 255 ? 255 : nr);
                    pg = ng < 0 ? 0 : (ng > 255 ? 255 : ng);
                    pb = nb < 0 ? 0 : (nb > 255 ? 255 : nb);
                }
                px[i] = (p & 0xFF000000) | (r[pr] << 16) | (g[pg] << 8) | b[pb];
            }
            dst.setPixels(px, 0, w, 0, y, w, count);
        }
    }

    // ------------------------------------------------------------ sharpness and size

    /**
     * Sharper: the picture set against a softened copy of itself and the
     * difference added back, as a darkroom's unsharp mask does; the least
     * differences are let alone, so grain is not sharpened into noise. The
     * softened copy is the picture made smaller by a step and drawn back up,
     * which the display's own filtering does quickly and gently.
     */
    static void sharpen(Bitmap bm, float amount, int step) {
        int w = bm.getWidth();
        int h = bm.getHeight();
        if (w < 4 || h < 4) {
            return;
        }
        Bitmap small = Bitmap.createScaledBitmap(bm, Math.max(1, w / step), Math.max(1, h / step), true);
        Bitmap soft = Bitmap.createScaledBitmap(small, w, h, true);
        if (small != bm) {
            small.recycle();
        }
        float k = amount / 100f * 1.4f;
        int kk = Math.round(k * 256f);
        int rows = Math.max(1, 131072 / w);
        int[] a = new int[w * rows];
        int[] b = new int[w * rows];
        for (int y = 0; y < h; y += rows) {
            int count = Math.min(rows, h - y);
            bm.getPixels(a, 0, w, 0, y, w, count);
            soft.getPixels(b, 0, w, 0, y, w, count);
            int end = w * count;
            for (int i = 0; i < end; i++) {
                int p = a[i];
                int q = b[i];
                a[i] = (p & 0xFF000000) | (lift((p >> 16) & 255, (q >> 16) & 255, kk) << 16)
                    | (lift((p >> 8) & 255, (q >> 8) & 255, kk) << 8) | lift(p & 255, q & 255, kk);
            }
            bm.setPixels(a, 0, w, 0, y, w, count);
        }
        soft.recycle();
    }

    private static int lift(int v, int soft, int kk) {
        int d = v - soft;
        if (d > -3 && d < 3) {
            return v;
        }
        int out = v + ((d * kk) >> 8);
        return out < 0 ? 0 : (out > 255 ? 255 : out);
    }

    /** Smaller by halves, each half filtered, and the last step exact: no pixel is skipped over. */
    static Bitmap smaller(Bitmap src, int tw, int th) {
        Bitmap now = src;
        while (now.getWidth() / 2 >= tw && now.getHeight() / 2 >= th) {
            Bitmap half = Bitmap.createScaledBitmap(now, now.getWidth() / 2, now.getHeight() / 2, true);
            if (now != src) {
                now.recycle();
            }
            now = half;
        }
        if (now.getWidth() == tw && now.getHeight() == th) {
            return now;
        }
        Bitmap out = Bitmap.createScaledBitmap(now, tw, th, true);
        if (now != src && now != out) {
            now.recycle();
        }
        return out;
    }

    /**
     * Larger, by the three-lobed windowed sine: each new pixel weighed from
     * six old ones each way, which keeps an edge an edge longer than straight
     * blending does. It invents nothing: what was not in the picture is not
     * in the larger one either, only drawn smoothly.
     */
    static Bitmap lanczos(Bitmap src, int tw, int th) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int[] xi = new int[tw * 6];
        float[] xw = new float[tw * 6];
        int[] yi = new int[th * 6];
        float[] yw = new float[th * 6];
        weights(sw, tw, xi, xw);
        weights(sh, th, yi, yw);
        Bitmap out = Bitmap.createBitmap(tw, th, Bitmap.Config.ARGB_8888);
        int[] line = new int[sw];
        float[][] rows = new float[8][tw * 3];
        int[] tag = new int[8];
        java.util.Arrays.fill(tag, -1);
        int[] made = new int[tw];
        for (int y = 0; y < th; y++) {
            float[][] use = new float[6][];
            for (int k = 0; k < 6; k++) {
                int sy = yi[y * 6 + k];
                int slot = sy & 7;
                if (tag[slot] != sy) {
                    src.getPixels(line, 0, sw, 0, sy, sw, 1);
                    float[] r = rows[slot];
                    for (int x = 0; x < tw; x++) {
                        float cr = 0f;
                        float cg = 0f;
                        float cb = 0f;
                        for (int j = 0; j < 6; j++) {
                            int p = line[xi[x * 6 + j]];
                            float wgt = xw[x * 6 + j];
                            cr += ((p >> 16) & 255) * wgt;
                            cg += ((p >> 8) & 255) * wgt;
                            cb += (p & 255) * wgt;
                        }
                        r[x * 3] = cr;
                        r[x * 3 + 1] = cg;
                        r[x * 3 + 2] = cb;
                    }
                    tag[slot] = sy;
                }
                use[k] = rows[slot];
            }
            for (int x = 0; x < tw; x++) {
                float cr = 0f;
                float cg = 0f;
                float cb = 0f;
                for (int k = 0; k < 6; k++) {
                    float wgt = yw[y * 6 + k];
                    float[] r = use[k];
                    cr += r[x * 3] * wgt;
                    cg += r[x * 3 + 1] * wgt;
                    cb += r[x * 3 + 2] * wgt;
                }
                made[x] = 0xFF000000 | (clamp8(cr) << 16) | (clamp8(cg) << 8) | clamp8(cb);
            }
            out.setPixels(made, 0, tw, 0, y, tw, 1);
        }
        return out;
    }

    /** For each new pixel, the six old ones it is weighed from, and their weights, summing to one. */
    private static void weights(int from, int to, int[] at, float[] wt) {
        float scale = from / (float) to;
        for (int i = 0; i < to; i++) {
            float centre = (i + 0.5f) * scale - 0.5f;
            int left = (int) Math.floor(centre) - 2;
            float sum = 0f;
            for (int k = 0; k < 6; k++) {
                int s = left + k;
                float w = lanczos3(centre - s);
                at[i * 6 + k] = s < 0 ? 0 : (s >= from ? from - 1 : s);
                wt[i * 6 + k] = w;
                sum += w;
            }
            if (sum != 0f) {
                for (int k = 0; k < 6; k++) {
                    wt[i * 6 + k] /= sum;
                }
            }
        }
    }

    private static float lanczos3(float x) {
        if (x == 0f) {
            return 1f;
        }
        if (x <= -3f || x >= 3f) {
            return 0f;
        }
        double px = Math.PI * x;
        return (float) (3.0 * Math.sin(px) * Math.sin(px / 3.0) / (px * px));
    }

    private static int clamp8(float v) {
        int i = Math.round(v);
        return i < 0 ? 0 : (i > 255 ? 255 : i);
    }

    // ------------------------------------------------------------ keeping

    /**
     * The work handed on where it stands: made in full, written to the
     * scratch, and given to whoever will take it. Nothing is kept in the
     * collection, and the touch-up stays open — what was handed on can still
     * be kept, or changed further, or let go.
     */
    private void handOn() {
        if (keeping || leaving || base == null) {
            return;
        }
        keeping = true;
        said.setText(Words.s("share"));
        said.animate().cancel();
        said.animate().alpha(1f).setDuration(Pace.PRESS).start();
        removeCallbacks(hush);
        postDelayed(hush, 1400L);
        final RectF crop = new RectF();
        settle();
        look(new RectF(), crop);
        final int[][] l = plainLight() ? null : tables(br, co, ga, te);
        final float s = sa;
        final Warp w = warp();
        final float shape = base.getWidth() / (float) base.getHeight();
        final float edge = sharp;
        final float factor = SIZES[size];
        final int seenLong = Math.max(base.getWidth(), base.getHeight());
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                Bitmap full = made(app, photo, crop, w, l, s, shape, edge, factor, seenLong);
                final Uri handed = full == null ? null : pass(app, photo, full);
                post(new Runnable() {
                    public void run() {
                        keeping = false;
                        hands.handed(handed);
                    }
                });
            }
        }, "hand").start();
    }

    private void keep() {
        if (keeping || leaving || base == null) {
            return;
        }
        keeping = true;
        blob.depart();
        panel.animate().alpha(0f).translationY(Round.px(48f)).setStartDelay(0L).setDuration(Pace.LEAVE)
            .setInterpolator(Pace.AWAY).start();
        said.animate().alpha(0f).setDuration(Pace.LEAVE).start();
        away.animate().alpha(0f).setStartDelay(0L).setDuration(Pace.LEAVE).start();
        hand.animate().alpha(0f).setStartDelay(0L).setDuration(Pace.LEAVE).start();
        settle();
        final RectF crop = new RectF();
        look(new RectF(), crop);
        final int[][] l = plainLight() ? null : tables(br, co, ga, te);
        final float s = sa;
        final Warp w = warp();
        final float shape = base.getWidth() / (float) base.getHeight();
        final float edge = sharp;
        final float factor = SIZES[size];
        final int seenLong = Math.max(base.getWidth(), base.getHeight());
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Uri made = write(app, photo, crop, w, l, s, shape, edge, factor, seenLong);
                post(new Runnable() {
                    public void run() {
                        hands.kept(made);
                    }
                });
            }
        }, "keep").start();
    }

    /**
     * The picture in full, as the geometry, the frame and the light have it,
     * written as a copy beside the original, with the camera's marks carried
     * over and the picture standing as it is seen. Answers the copy's
     * address, or null.
     */
    static Uri write(Context c, Gallery.Photo p, final RectF crop, Warp warp, int[][] l, float s,
                     final float shape, float edge, float factor, int seenLong) {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            return null;
        }
        Bitmap full = made(c, p, crop, warp, l, s, shape, edge, factor, seenLong);
        if (full == null) {
            return null;
        }
        return keep(c, p, full);
    }

    /**
     * The picture in full as the frame, the geometry, the light, the size and
     * the sharpness have it — the whole of the work, before anything is done
     * with it. Answers null when it cannot be read.
     */
    static Bitmap made(Context c, Gallery.Photo p, final RectF crop, Warp warp, int[][] l, float s,
                       final float shape, float edge, float factor, int seenLong) {
        final boolean straight = warp == null || warp.plain();
        /* The size the picture was read at, before the frame cut it: the measure for sharpening. */
        final int[] read = new int[2];
        Bitmap full;
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), p.uri);
            full = ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                    int w = info.getSize().getWidth();
                    int h = info.getSize().getHeight();
                    /* The copy on the screen was read by the same decoder; its shape says
                       which way round the size is given. */
                    float given = w / (float) h;
                    if (Math.abs(Math.log(given) - Math.log(shape)) > Math.abs(Math.log(1f / given) - Math.log(shape))) {
                        int k = w;
                        w = h;
                        h = k;
                    }
                    /* Straightened, the whole picture is needed to draw from; otherwise only the frame. */
                    float share = straight ? crop.width() * crop.height() : 1f;
                    int sample = 1;
                    while ((w / (float) sample) * (h / (float) sample) * share > 36000000f) {
                        sample *= 2;
                    }
                    int sw = w / sample;
                    int sh = h / sample;
                    read[0] = sw;
                    read[1] = sh;
                    if (sample > 1) {
                        d.setTargetSampleSize(sample);
                    }
                    if (straight) {
                        Rect r = new Rect(Math.round(crop.left * sw), Math.round(crop.top * sh),
                            Math.round(crop.right * sw), Math.round(crop.bottom * sh));
                        r.intersect(0, 0, sw, sh);
                        if (r.width() > 0 && r.height() > 0 && (r.width() < sw || r.height() < sh)) {
                            d.setCrop(r);
                        }
                    }
                    d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    d.setMutableRequired(true);
                }
            });
        } catch (Throwable broken) {
            Trace.note("touch-up: picture not read in full: " + broken.getClass().getSimpleName());
            return null;
        }
        if (!straight) {
            /* The picture drawn through the mesh onto a sheet the size of the frame. */
            int fw = full.getWidth();
            int fh = full.getHeight();
            Bitmap out;
            try {
                out = Bitmap.createBitmap(Math.max(1, Math.round(crop.width() * fw)),
                    Math.max(1, Math.round(crop.height() * fh)), Bitmap.Config.ARGB_8888);
            } catch (Throwable big) {
                Trace.note("touch-up: no room for the straightened copy");
                full.recycle();
                return null;
            }
            Canvas cv = new Canvas(out);
            cv.drawColor(0xFF000000);
            cv.translate(-crop.left * fw, -crop.top * fh);
            Paint smooth = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            cv.drawBitmapMesh(full, MESH, MESH, warp.mesh(fw, fh, MESH), 0, null, 0, smooth);
            full.recycle();
            full = out;
        }
        if (l != null) {
            paint(full, full, l, s);
        } else if (s != 0f) {
            paint(full, full, tables(0f, 0f, 0f, 0f), s);
        }
        /* The size last but one, and the sharpness last, on the pixels that are kept. */
        int before = full.getWidth();
        int[] to = scaled(full.getWidth(), full.getHeight(), factor);
        if (to[0] != full.getWidth() || to[1] != full.getHeight()) {
            Bitmap resized;
            try {
                resized = to[0] > full.getWidth() ? lanczos(full, to[0], to[1]) : smaller(full, to[0], to[1]);
            } catch (Throwable big) {
                Trace.note("touch-up: no room for the new size");
                full.recycle();
                return null;
            }
            if (resized != full) {
                full.recycle();
                full = resized;
            }
        }
        if (edge > 0f) {
            /* The screen's copy was sharpened against a blur a pixel or so of its own
               wide; the kept one gets the same width measured on the picture, so what
               was seen is what is kept, however large the copy is made. */
            float perSeen = Math.max(read[0], read[1]) / (float) Math.max(1, seenLong);
            float grown = full.getWidth() / (float) Math.max(1, before);
            int reach = Math.round(2f * perSeen * grown);
            sharpen(full, edge, Math.max(2, Math.min(8, reach)));
        }
        return full;
    }

    /**
     * The work written into the application's own scratch and handed on by
     * an address of ours: a message, a note, a workshop take it as they take
     * any file, and nothing is added to the collection. What is not kept is
     * swept away by itself within half a day.
     */
    static Uri pass(Context c, Gallery.Photo p, Bitmap full) {
        try {
            File room = Pass.room(c);
            if (room == null) {
                return null;
            }
            File made = new File(room, copyName(p.name));
            OutputStream out = new java.io.FileOutputStream(made);
            try {
                full.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } finally {
                out.close();
            }
            Trace.note("touch-up: handed on, " + made.length() + " bytes");
            return Pass.uriOf(made);
        } catch (Exception unwritten) {
            Trace.note("touch-up: not handed: " + unwritten.getClass().getSimpleName());
            return null;
        } finally {
            full.recycle();
        }
    }

    /** The made picture kept as a copy beside the original. */
    private static Uri keep(Context c, Gallery.Photo p, Bitmap full) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, copyName(p.name));
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, folderOf(p.place));
        if (p.taken > 0L) {
            v.put(MediaStore.Images.Media.DATE_TAKEN, p.taken);
        }
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri made = null;
        try {
            made = c.getContentResolver().insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (made == null) {
                return null;
            }
            OutputStream out = c.getContentResolver().openOutputStream(made);
            try {
                full.compress(Bitmap.CompressFormat.JPEG, 95, out);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            marks(c, p, made, full.getWidth(), full.getHeight());
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            c.getContentResolver().update(made, done, null, null);
            Trace.note("touch-up: copy kept, " + full.getWidth() + "x" + full.getHeight());
            return made;
        } catch (Exception unwritten) {
            Trace.note("touch-up: copy not written: " + unwritten.getClass().getSimpleName());
            if (made != null) {
                try {
                    c.getContentResolver().delete(made, null, null);
                } catch (Exception ignored) {
                    // a half-made copy the index will drop by itself
                }
            }
            return null;
        } finally {
            full.recycle();
        }
    }

    /** The copy's name: the original's, marked as touched up once, however often it was. */
    private static String copyName(String name) {
        String stem = Change.stem(name);
        if (stem.endsWith("_edit")) {
            stem = stem.substring(0, stem.length() - 5);
        }
        return stem + "_edit.jpg";
    }

    /**
     * The folder the copy goes into: the original's own, if pictures may be
     * put there; the application's among the pictures otherwise.
     */
    private static String folderOf(String place) {
        String fallback = "Pictures/Mirabilia/";
        if (place == null || !place.startsWith("primary:")) {
            return fallback;
        }
        String path = place.substring("primary:".length());
        int slash = path.lastIndexOf('/');
        if (slash <= 0) {
            return fallback;
        }
        String dir = path.substring(0, slash + 1);
        if (dir.startsWith("DCIM/") || dir.startsWith("Pictures/")) {
            return dir;
        }
        return fallback;
    }

    /** The camera's marks, from the original onto the copy; the copy stands as it is seen. */
    private static final String[] MARKS = {
        ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
        "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized",
        ExifInterface.TAG_SUBSEC_TIME, ExifInterface.TAG_SUBSEC_TIME_ORIG, ExifInterface.TAG_SUBSEC_TIME_DIG,
        ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL, "LensMake", "LensModel",
        ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE, ExifInterface.TAG_FLASH, ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
    };

    private static void marks(Context c, Gallery.Photo p, Uri made, int w, int h) {
        ExifInterface from = null;
        ParcelFileDescriptor in = null;
        try {
            File file = Shelf.allFiles() ? Change.fileOf(p.place) : null;
            in = file != null && file.isFile()
                ? ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                : c.getContentResolver().openFileDescriptor(p.uri, "r");
            if (in != null) {
                from = new ExifInterface(in.getFileDescriptor());
            }
        } catch (Exception unread) {
            Trace.note("touch-up: marks unread: " + unread.getClass().getSimpleName());
        } finally {
            close(in);
        }
        ParcelFileDescriptor out = null;
        try {
            out = c.getContentResolver().openFileDescriptor(made, "rw");
            if (out == null) {
                return;
            }
            ExifInterface to = new ExifInterface(out.getFileDescriptor());
            if (from != null) {
                for (int i = 0; i < MARKS.length; i++) {
                    String value = from.getAttribute(MARKS[i]);
                    if (value != null) {
                        to.setAttribute(MARKS[i], value);
                    }
                }
            }
            to.setAttribute(ExifInterface.TAG_ORIENTATION, String.valueOf(ExifInterface.ORIENTATION_NORMAL));
            to.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, String.valueOf(w));
            to.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, String.valueOf(h));
            to.saveAttributes();
        } catch (Exception unwritten) {
            Trace.note("touch-up: marks not written: " + unwritten.getClass().getSimpleName());
        } finally {
            close(out);
        }
    }

    private static void close(ParcelFileDescriptor f) {
        if (f != null) {
            try {
                f.close();
            } catch (Exception ignored) {
                // closing a descriptor loses nothing
            }
        }
    }

    /** The copy for work: the picture turned as it lies, a software copy about PREVIEW long. */
    private static Bitmap load(Context c, Gallery.Photo p, final int[] whole) {
        if (android.os.Build.VERSION.SDK_INT < 28) {
            return null;
        }
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), p.uri);
            return ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                    whole[0] = info.getSize().getWidth();
                    whole[1] = info.getSize().getHeight();
                    int longer = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    int sample = 1;
                    while (longer / sample > PREVIEW * 5 / 4) {
                        sample *= 2;
                    }
                    if (sample > 1) {
                        d.setTargetSampleSize(sample);
                    }
                    d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
            });
        } catch (Throwable broken) {
            Trace.note("touch-up: not decoded: " + broken.getClass().getSimpleName());
            return null;
        }
    }

    // ------------------------------------------------------------ the stage

    private static final int LEFT = 1;
    private static final int TOP = 2;
    private static final int RIGHT = 4;
    private static final int BOTTOM = 8;

    /**
     * Whether a touch falls on a free frame's edge or corner, within a
     * fingertip of it; if so, which, and the picture is held still from here.
     */
    private boolean grab(float x, float y) {
        RectF win = new RectF();
        RectF crop = new RectF();
        settle();
        look(win, crop);
        float reach = Round.px(28f);
        if (x < win.left - reach || x > win.right + reach || y < win.top - reach || y > win.bottom + reach) {
            return false;
        }
        int which = 0;
        if (Math.abs(x - win.left) < reach) {
            which |= LEFT;
        } else if (Math.abs(x - win.right) < reach) {
            which |= RIGHT;
        }
        if (Math.abs(y - win.top) < reach) {
            which |= TOP;
        } else if (Math.abs(y - win.bottom) < reach) {
            which |= BOTTOM;
        }
        if (which == 0) {
            return false;
        }
        held = which;
        perX = win.width() / crop.width();
        perY = win.height() / crop.height();
        holdX = win.left - crop.left * perX;
        holdY = win.top - crop.top * perY;
        heldCrop.set(crop);
        return true;
    }

    private float grabX;
    private float grabY;

    /** A free frame's edge led by the finger; on release the frame fits its room again, flowing. */
    private boolean drag(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                holding = true;
                changed = true;
                grabX = e.getX();
                grabY = e.getY();
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                stage.invalidate();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dx = (e.getX() - grabX) / perX;
                float dy = (e.getY() - grabY) / perY;
                RectF r = new RectF(heldCrop);
                if ((held & LEFT) != 0) {
                    r.left = Math.min(heldCrop.left + dx, heldCrop.right - 0.02f);
                }
                if ((held & RIGHT) != 0) {
                    r.right = Math.max(heldCrop.right + dx, heldCrop.left + 0.02f);
                }
                if ((held & TOP) != 0) {
                    r.top = Math.min(heldCrop.top + dy, heldCrop.bottom - 0.02f);
                }
                if ((held & BOTTOM) != 0) {
                    r.bottom = Math.max(heldCrop.bottom + dy, heldCrop.top + 0.02f);
                }
                /* Held to the picture: an edge pushed past it stops at it. */
                if (edge == null) {
                    r.left = Math.max(0f, r.left);
                    r.top = Math.max(0f, r.top);
                    r.right = Math.min(1f, r.right);
                    r.bottom = Math.min(1f, r.bottom);
                }
                if (frameTo(r)) {
                    stage.invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (holding) {
                    seen(fromWin, fromCrop);
                    holding = false;
                    change(null);
                    tell();
                }
                return true;
            default:
                return true;
        }
    }

    /**
     * The picture on its stage: under the frame as it is, the rest of it in
     * a quieter light, the frame's four corners while it is being set, and
     * its thirds while the picture is being straightened.
     */
    private final class Stage extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint hair = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF win = new RectF();
        private final RectF crop = new RectF();
        private final RectF start = new RectF();
        private final RectF end = new RectF();
        private final RectF drawn = new RectF();
        private final RectF hole = new RectF();
        private final ScaleGestureDetector pinch;
        private float lastX;
        private float lastY;

        Stage(Context c) {
            super(c);
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeCap(Paint.Cap.ROUND);
            ink.setStrokeWidth(Round.px(3f));
            hair.setStyle(Paint.Style.STROKE);
            hair.setStrokeWidth(Math.max(1f, Round.px(0.8f)));
            pinch = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                @Override
                public boolean onScale(ScaleGestureDetector d) {
                    seen(win, crop);
                    float next = Math.max(1f, Math.min(4f, zoom * d.getScaleFactor()));
                    if (next == zoom) {
                        return true;
                    }
                    changed = true;
                    /* The point between the fingers stays between them. */
                    float fx = crop.left + (d.getFocusX() - win.left) / win.width() * crop.width();
                    float fy = crop.top + (d.getFocusY() - win.top) / win.height() * crop.height();
                    zoom = next;
                    float w = cropW();
                    float h = cropH();
                    cx = fx - (d.getFocusX() - win.left) / win.width() * w + w / 2f;
                    cy = fy - (d.getFocusY() - win.top) / win.height() * h + h / 2f;
                    clampCentre();
                    if (drum != null && kind() == Marks.CLOSER) {
                        drum.show(nearestZoom());
                    }
                    invalidate();
                    return true;
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            Bitmap bm = comparing || shown == null ? (base != null ? base : quick) : shown;
            if (bm == null || getWidth() == 0) {
                return;
            }
            seen(win, crop);
            float bw = bm.getWidth();
            float bh = bm.getHeight();
            float s = win.width() / (crop.width() * bw);
            end.set(win.left - crop.left * bw * s, win.top - crop.top * bh * s, 0f, 0f);
            end.right = end.left + bw * s;
            end.bottom = end.top + bh * s;
            /* Where the viewer had it: the whole picture fitted to the screen. */
            float k = Math.min(getWidth() / bw, getHeight() / bh);
            start.set((getWidth() - bw * k) / 2f, (getHeight() - bh * k) / 2f, 0f, 0f);
            start.right = start.left + bw * k;
            start.bottom = start.top + bh * k;
            lerp(drawn, start, end, enter);
            if (edge == null) {
                canvas.drawBitmap(bm, null, drawn, paint);
            } else {
                canvas.save();
                canvas.translate(drawn.left, drawn.top);
                canvas.scale(drawn.width() / bw, drawn.height() / bh);
                canvas.drawBitmapMesh(bm, MESH, MESH, meshFor(bw, bh), 0, null, 0, paint);
                canvas.restore();
            }

            hole.set(drawn.left + crop.left * drawn.width(), drawn.top + crop.top * drawn.height(),
                drawn.left + crop.right * drawn.width(), drawn.top + crop.bottom * drawn.height());
            float quiet = dim * enter;
            if (quiet > 0f) {
                canvas.save();
                canvas.clipOutRect(hole);
                canvas.drawColor(Tone.of(Tone.SURFACE_LOWEST, quiet));
                canvas.restore();
            }
            float grid = thirds * enter;
            if (grid > 0f) {
                hair.setColor(Tone.of(Tone.ON_SURFACE, 0.45f * grid));
                for (int i = 1; i < 3; i++) {
                    float x = hole.left + hole.width() * i / 3f;
                    float y = hole.top + hole.height() * i / 3f;
                    canvas.drawLine(x, hole.top, x, hole.bottom, hair);
                    canvas.drawLine(hole.left, y, hole.right, y, hair);
                }
                hair.setColor(Tone.of(Tone.ON_SURFACE, 0.16f * grid));
                for (int i = 1; i < 9; i++) {
                    if (i % 3 == 0) {
                        continue;
                    }
                    float x = hole.left + hole.width() * i / 9f;
                    float y = hole.top + hole.height() * i / 9f;
                    canvas.drawLine(x, hole.top, x, hole.bottom, hair);
                    canvas.drawLine(hole.left, y, hole.right, y, hair);
                }
            }
            float lit = corners * enter;
            if (lit > 0f && ratio == FREE && tab == FRAME) {
                /* A free frame shows it can be taken by its edges: a short bar at each. */
                ink.setColor(Tone.of(Tone.ON_SURFACE, 0.95f * lit));
                float bar = Math.min(Round.px(14f), Math.min(hole.width(), hole.height()) / 6f);
                float o = Round.px(4f);
                canvas.drawLine(hole.centerX() - bar, hole.top - o, hole.centerX() + bar, hole.top - o, ink);
                canvas.drawLine(hole.centerX() - bar, hole.bottom + o, hole.centerX() + bar, hole.bottom + o, ink);
                canvas.drawLine(hole.left - o, hole.centerY() - bar, hole.left - o, hole.centerY() + bar, ink);
                canvas.drawLine(hole.right + o, hole.centerY() - bar, hole.right + o, hole.centerY() + bar, ink);
            }
            if (lit > 0f) {
                ink.setColor(Tone.of(Tone.ON_SURFACE, 0.95f * lit));
                float arm = Math.min(Round.px(22f), Math.min(hole.width(), hole.height()) / 3f);
                float o = Round.px(4f);
                float l = hole.left - o;
                float t = hole.top - o;
                float r = hole.right + o;
                float b = hole.bottom + o;
                canvas.drawLine(l, t, l + arm, t, ink);
                canvas.drawLine(l, t, l, t + arm, ink);
                canvas.drawLine(r, t, r - arm, t, ink);
                canvas.drawLine(r, t, r, t + arm, ink);
                canvas.drawLine(l, b, l + arm, b, ink);
                canvas.drawLine(l, b, l, b - arm, ink);
                canvas.drawLine(r, b, r - arm, b, ink);
                canvas.drawLine(r, b, r, b - arm, ink);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (keeping || leaving || enter < 1f) {
                return true;
            }
            if (tab == LIGHT) {
                /* A finger resting on the picture shows the light as it was. */
                int act = e.getActionMasked();
                boolean was = comparing;
                comparing = act == MotionEvent.ACTION_DOWN || act == MotionEvent.ACTION_MOVE;
                if (comparing != was) {
                    invalidate();
                }
                return true;
            }
            if (holding || (e.getActionMasked() == MotionEvent.ACTION_DOWN && tab == FRAME && ratio == FREE
                && grab(e.getX(), e.getY()))) {
                return drag(e);
            }
            pinch.onTouchEvent(e);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    settle();
                    lastX = e.getX();
                    lastY = e.getY();
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (pinch.isInProgress() || e.getPointerCount() > 1) {
                        lastX = e.getX();
                        lastY = e.getY();
                        return true;
                    }
                    seen(win, crop);
                    changed = true;
                    lead(-(e.getX() - lastX) / win.width() * crop.width(),
                        -(e.getY() - lastY) / win.height() * crop.height());
                    lastX = e.getX();
                    lastY = e.getY();
                    invalidate();
                    return true;
                }
                case MotionEvent.ACTION_POINTER_UP: {
                    /* The finger left on the glass carries on from where it is. */
                    int gone = e.getActionIndex();
                    int stays = gone == 0 ? 1 : 0;
                    lastX = e.getX(stays);
                    lastY = e.getY(stays);
                    return true;
                }
                default:
                    return true;
            }
        }
    }
}
