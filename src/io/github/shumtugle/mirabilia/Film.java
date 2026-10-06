package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;

/**
 * A film, over the whole screen, playing as soon as it is opened and from
 * where it was left.
 *
 * Its keys are the recordings' own: the same strip, in the same capsule, at
 * the foot of the film — its name, how far it has gone and at what speed,
 * fifteen seconds back, stop or go on, thirty on; the words touched step
 * the speed; the words held raise the dial of its time, a tick every five
 * seconds and a taller one every minute, the edges turning it on by
 * themselves. The strip comes with a touch on the film and goes when the
 * film has been left alone a few seconds; a touch while it stands puts it
 * away. Drawn down, the film follows the finger, grows smaller and the dark
 * behind it thins; let go far enough, or with a flick, and it closes, the
 * same way back closes it. Held, it offers
 * what can be done with it: its speed, on a drum under a needle, for a
 * lecture heard quicker; its frame, kept as a picture. Two fingers spread
 * fill the screen with it, cropping what does not fit; two fingers drawn
 * together show it whole again. Where it stopped is kept under the film's
 * place, so it goes on from there the next time; heard to the end, it starts
 * again from the beginning.
 */
final class Film extends FrameLayout implements TextureView.SurfaceTextureListener {

    interface Watcher {
        void done();

        void held();

        /** The words of the strip were held: the dial of the film's time rises, and is turned, and goes. */
        void dialOpen();

        void dialTurn(float dx);

        void dialEdge(float across);

        void dialClose(boolean take);

        /** The strip came, or went: the mark comes and goes with it. */
        void chrome(boolean shown);

        /** The player would not take another speed for this video. */
        void refused();
    }

    private final Gallery.Photo item;
    private final Watcher watcher;
    private final String key;
    private final TextureView screen;
    private final Matrix fit = new Matrix();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF shape = new RectF();
    private final GestureDetector taps;
    private final ScaleGestureDetector pinch;
    private float spread = 1f;
    /** Whole (0) or filling the screen (1), and on the way between. */
    private float filled;
    private ValueAnimator filling;
    private android.widget.LinearLayout panel;
    /** The strip at the foot, in its capsule, and whether the dial is up over it. */
    private final android.widget.LinearLayout foot;
    private final Voice strip;
    private boolean dialing;
    /** Opened to stand still at its place rather than to play: its sound was paused when taken back. */
    private boolean still;
    private boolean footShown;
    private final Runnable footAway = new Runnable() {
        public void run() {
            if (!dialing && panel == null) {
                hideStrip();
            }
        }
    };
    private final Runnable footBeat = new Runnable() {
        public void run() {
            if (footShown) {
                stripLine();
                postDelayed(this, 500L);
            }
        }
    };
    private final Runnable hidePanel = new Runnable() {
        public void run() {
            closePanel();
        }
    };
    private final int slop;
    private final int dark;
    private MediaPlayer player;
    private Surface surface;
    private boolean prepared;
    private int videoW;
    private int videoH;
    private long length;

    private float downX;
    private float downY;
    /**
     * A touch that began at the top edge, where the phone's own shade is
     * pulled from: it is the phone's, and never pulls the film down.
     */
    private boolean fromEdge;
    /** A frame being chosen: the film holds still and answers no gesture of its own. */
    private boolean picking;
    private boolean pulling;
    private boolean closing;
    private float drop;
    private VelocityTracker tracker;

    /** When the foot line and the middle mark were last shown, and which mark. */
    private long footAt;
    private long markAt;
    private int mark = Glyph.PLAY;
    private final Runnable tick = new Runnable() {
        public void run() {
            long now = System.currentTimeMillis();
            if (now - markAt < 700L) {
                invalidate();
                postOnAnimation(this);
            } else {
                ticking = false;
                invalidate();
            }
        }
    };
    private boolean ticking;
    private final Runnable keep = new Runnable() {
        public void run() {
            save();
            /* A video plays on its own screen, away from the sound service:
               it watches for the same sleep, and goes quiet at the same hour. */
            long at = Keep.sleepAt(getContext());
            if (at > 0L && System.currentTimeMillis() >= at && going()) {
                Keep.setSleep(getContext(), 0L, Keep.sleepStep(getContext()), false);
                hold();
                Trace.note("sleep: the video was held");
            }
            postDelayed(this, 5000L);
        }
    };

    private final AudioManager audio;
    private AudioFocusRequest focus;

    Film(Context context, Gallery.Photo item, Watcher watcher) {
        super(context);
        this.item = item;
        this.watcher = watcher;
        this.key = item.item().key();
        this.length = item.duration;
        dark = Tone.of(Tone.SURFACE_LOWEST);
        setWillNotDraw(false);
        screen = new TextureView(context);
        screen.setSurfaceTextureListener(this);
        addView(screen, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        words.setColor(0xFFFFFFFF);
        words.setTextAlign(Paint.Align.CENTER);
        words.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        filled = Keep.filmFill(context) ? 1f : 0f;
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector d) {
                spread = 1f;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector d) {
                spread *= d.getScaleFactor();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector d) {
                if (spread > 1.12f) {
                    fillTo(true);
                } else if (spread < 0.9f) {
                    fillTo(false);
                }
            }
        });
        foot = new android.widget.LinearLayout(context);
        foot.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        foot.setGravity(android.view.Gravity.CENTER_VERTICAL);
        foot.setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));
        foot.setBackground(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL));
        strip = new Voice(context, new Voice.Hands() {
            public void back() {
                nudge(-Sound.BACK_MS);
            }

            public void hold() {
                toggle();
            }

            public void on() {
                nudge(Sound.ON_MS);
            }

            public void faster() {
                /* The words bring the drum of speed, as the recordings' do. */
                if (panel != null) {
                    closePanel();
                } else {
                    showSpeed();
                }
                showStrip();
            }

            public void dialOpen() {
                dialing = true;
                removeCallbacks(footAway);
                if (Film.this.watcher != null) {
                    Film.this.watcher.dialOpen();
                }
            }

            public void dialTurn(float dx) {
                if (Film.this.watcher != null) {
                    Film.this.watcher.dialTurn(dx);
                }
            }

            public void dialEdge(float across) {
                if (Film.this.watcher != null) {
                    Film.this.watcher.dialEdge(across);
                }
            }

            public void dialClose(boolean take) {
                dialing = false;
                if (Film.this.watcher != null) {
                    Film.this.watcher.dialClose(take);
                }
                showStrip();
            }
        });
        strip.tint();
        foot.addView(strip, new android.widget.LinearLayout.LayoutParams(0,
            LayoutParams.WRAP_CONTENT, 1f));
        LayoutParams footPlace = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM);
        footPlace.setMargins(Round.dp(8), 0, Round.dp(8), Round.dp(16));
        addView(foot, footPlace);
        foot.setVisibility(GONE);
        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                if (panel != null) {
                    closePanel();
                    return true;
                }
                if (pulling || closing || pinch.isInProgress()) {
                    return true;
                }
                /* A touch on the film brings its keys, or puts them away. */
                if (footShown) {
                    hideStrip();
                } else {
                    showStrip();
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!pulling && !closing && Film.this.watcher != null) {
                    Film.this.watcher.held();
                }
            }
        });
    }

    // ------------------------------------------------------------ the player

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        surface = new Surface(texture);
        open();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        fitIn();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
    }

    private void open() {
        try {
            final MediaPlayer p = new MediaPlayer();
            player = p;
            p.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
            p.setDataSource(getContext(), item.uri);
            p.setSurface(surface);
            p.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                public void onVideoSizeChanged(MediaPlayer mp, int w, int h) {
                    videoW = w;
                    videoH = h;
                    fitIn();
                }
            });
            p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    length = Math.max(length, mp.getDuration());
                    long at = Shelf.soundAt(getContext(), key);
                    if (at > 0L && at < length - 3000L) {
                        mp.seekTo((int) at);
                    }
                    if (still) {
                        still = false;
                        stripLine();
                    } else {
                        play();
                    }
                    showStrip();
                }
            });
            p.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer mp) {
                    Shelf.setSoundAt(getContext(), key, 0L);
                    Shelf.setDone(getContext(), key, true);
                    setKeepScreenOn(false);
                    mark = Glyph.PLAY;
                    markAt = System.currentTimeMillis();
                    showFoot();
                }
            });
            p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    Trace.note("film: cannot play: " + what + "/" + extra);
                    return true;
                }
            });
            p.prepareAsync();
        } catch (Exception broken) {
            Trace.note("film: not opened: " + broken.getClass().getSimpleName());
        }
    }

    /** The film fitted into the screen whole, with dark bands where its shape and the screen's differ. */
    private void fitIn() {
        int vw = screen.getWidth();
        int vh = screen.getHeight();
        if (vw == 0 || vh == 0 || videoW == 0 || videoH == 0) {
            return;
        }
        float viewRatio = vw / (float) vh;
        float filmRatio = videoW / (float) videoH;
        float sx = 1f;
        float sy = 1f;
        if (filmRatio > viewRatio) {
            sy = viewRatio / filmRatio;
        } else {
            sx = filmRatio / viewRatio;
        }
        /* Filling, it grows until its shorter side meets the screen's edge. */
        float grow = 1f + (1f / Math.min(sx, sy) - 1f) * filled;
        fit.setScale(sx * grow, sy * grow, vw / 2f, vh / 2f);
        screen.setTransform(fit);
    }

    /** Whole, or filling the screen, reached softly; the choice is kept for the next film. */
    private void fillTo(final boolean fill) {
        if ((fill ? 1f : 0f) == filled) {
            return;
        }
        Keep.setFilmFill(getContext(), fill);
        if (filling != null) {
            filling.cancel();
        }
        filling = ValueAnimator.ofFloat(filled, fill ? 1f : 0f);
        filling.setDuration(Pace.ARRIVE);
        filling.setInterpolator(Pace.EMPHASIS);
        filling.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                filled = (Float) a.getAnimatedValue();
                fitIn();
            }
        });
        filling.start();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    // ------------------------------------------------------------ speed

    static final float[] SPEEDS = {0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f};

    /** The speeds as the drum writes them: a comma, and the sign of times. */
    static String[] speedLabels() {
        String[] labels = new String[SPEEDS.length];
        for (int i = 0; i < SPEEDS.length; i++) {
            float v = SPEEDS[i];
            labels[i] = (v == Math.round(v) ? String.valueOf(Math.round(v))
                : String.valueOf(v).replace('.', ',')) + "\u00D7";
        }
        return labels;
    }

    /**
     * The speed set on the player. Some players refuse the settings they
     * themselves report — a pitch of nought, an unknown way of stretching —
     * so a refusal is tried again with every setting but the speed left to
     * its default; and a refusal of that too is written down with its reason.
     */
    private void applySpeed() {
        if (player == null || !prepared) {
            return;
        }
        float v = SPEEDS[Keep.filmSpeed(getContext(), item.albumId)];
        try {
            player.setPlaybackParams(player.getPlaybackParams().setSpeed(v));
            return;
        } catch (Exception first) {
            Trace.note("film: speed refused once: " + first.getMessage());
        }
        try {
            android.media.PlaybackParams fresh = new android.media.PlaybackParams();
            fresh.allowDefaults();
            fresh.setPitch(1f);
            fresh.setSpeed(v);
            player.setPlaybackParams(fresh);
        } catch (Exception again) {
            Trace.note("film: speed refused: " + again.getClass().getSimpleName() + " " + again.getMessage());
            if (watcher != null) {
                watcher.refused();
            }
        }
    }

    /** The speed it really plays at: what the player says, or one where it would not say. */
    private float speedNow() {
        try {
            if (player != null && prepared) {
                float v = player.getPlaybackParams().getSpeed();
                if (v > 0f) {
                    return v;
                }
            }
        } catch (Exception unknown) {
            // the player keeps it to itself
        }
        return SPEEDS[Keep.filmSpeed(getContext(), item.albumId)];
    }

    /**
     * The speed, on a drum under a needle at the foot of the film, as the
     * depth of glass is set: a step to each speed, felt under the finger,
     * heard at once while it plays. It goes by itself a moment after the drum
     * stops, or at a touch anywhere else.
     */
    void showSpeed() {
        if (panel != null) {
            return;
        }
        Context c = getContext();
        final android.widget.LinearLayout made = new android.widget.LinearLayout(c);
        made.setOrientation(android.widget.LinearLayout.VERTICAL);
        made.setPadding(Round.dp(16), Round.dp(12), Round.dp(16), Round.dp(16));
        made.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.XL));
        android.widget.TextView named = Letter.set(new android.widget.TextView(c), Letter.LABEL_L);
        named.setText(Words.s("speed"));
        named.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        named.setPadding(Round.dp(4), 0, 0, Round.dp(8));
        made.addView(named);
        made.addView(new Depth(c, speedLabels(), Keep.filmSpeed(c, item.albumId), new Depth.Turned() {
            public void turned(int step, boolean done) {
                Keep.setFilmSpeed(getContext(), item.albumId, step);
                if (playing()) {
                    applySpeed();
                }
                stripLine();
                removeCallbacks(hidePanel);
                if (done) {
                    postDelayed(hidePanel, 1600L);
                }
            }
        }));
        LayoutParams at = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
            android.view.Gravity.BOTTOM);
        at.setMargins(Round.dp(16), 0, Round.dp(16), Round.dp(64));
        addView(made, at);
        made.setAlpha(0f);
        made.setTranslationY(Round.px(24f));
        made.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        panel = made;
        postDelayed(hidePanel, 4000L);
    }

    private void closePanel() {
        removeCallbacks(hidePanel);
        final android.view.View gone = panel;
        panel = null;
        if (gone == null) {
            return;
        }
        gone.animate().alpha(0f).translationY(Round.px(24f)).setDuration(Pace.LEAVE)
            .setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    removeView(gone);
                }
            }).start();
    }

    /** The strip comes, and goes by itself a few seconds after the film is left alone. */
    void showStrip() {
        removeCallbacks(footAway);
        postDelayed(footAway, 3500L);
        if (footShown) {
            return;
        }
        footShown = true;
        if (watcher != null) {
            watcher.chrome(true);
        }
        stripLine();
        foot.setVisibility(VISIBLE);
        foot.setAlpha(0f);
        foot.setTranslationY(Round.px(16f));
        foot.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        strip.start();
        removeCallbacks(footBeat);
        postDelayed(footBeat, 500L);
    }

    private void hideStrip() {
        removeCallbacks(footAway);
        if (!footShown) {
            return;
        }
        footShown = false;
        if (watcher != null) {
            watcher.chrome(false);
        }
        removeCallbacks(footBeat);
        foot.animate().alpha(0f).translationY(Round.px(16f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    if (!footShown) {
                        foot.setVisibility(GONE);
                        strip.end();
                    }
                }
            }).start();
    }

    /** The strip is told the film's name, the time, and the speed. */
    private void stripLine() {
        long at = position();
        float speed = playing() ? speedNow() : SPEEDS[Keep.filmSpeed(getContext(), item.albumId)];
        String place = clock(at) + (length > 0 ? " / " + clock(length) : "") + "  \u00B7  "
            + String.format(java.util.Locale.ROOT, "%.2f", speed).replaceAll("0+$", "")
                .replaceAll("\\.$", "") + "\u00D7";
        strip.line(Shelf.title(item.name), place, length > 0 ? at / (float) length : 0f);
        strip.playing(playing());
    }

    /** A step back or on, from where it is. */
    private void nudge(long by) {
        if (player == null || !prepared) {
            return;
        }
        long to = Math.max(0L, Math.min(length, position() + by));
        seekTo(to);
    }

    /** To a moment, and the strip says so. */
    void seekTo(long to) {
        if (player == null || !prepared) {
            return;
        }
        try {
            player.seekTo(to, MediaPlayer.SEEK_PREVIOUS_SYNC);
        } catch (Exception ignored) {
            // it stays where it was
        }
        save();
        stripLine();
        showStrip();
    }

    long length() {
        return length;
    }

    /** Where the film is now, for a frame to be taken from it. */
    long now() {
        return position();
    }

    Gallery.Photo item() {
        return item;
    }

    private boolean playing() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (IllegalStateException gone) {
            return false;
        }
    }

    private long position() {
        try {
            return player != null && prepared ? player.getCurrentPosition() : 0L;
        } catch (IllegalStateException gone) {
            return 0L;
        }
    }

    private void play() {
        if (player == null || !prepared) {
            return;
        }
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                    public void onAudioFocusChange(int change) {
                        if (change == AudioManager.AUDIOFOCUS_LOSS
                            || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                            pause();
                        }
                    }
                }).build();
        }
        audio.requestAudioFocus(focus);
        player.start();
        /* A video sounds too: the home screen should show it, not whatever was heard before it. */
        Keep.heardFilm(getContext());
        Desk.push(getContext());
        if (Keep.filmSpeed(getContext(), item.albumId) != 1) {
            applySpeed();
        }
        setKeepScreenOn(true);
        removeCallbacks(keep);
        postDelayed(keep, 5000L);
    }

    private void pause() {
        if (player == null || !prepared) {
            return;
        }
        try {
            player.pause();
        } catch (IllegalStateException gone) {
            return;
        }
        setKeepScreenOn(false);
        save();
    }

    /** The window went away: the film stops where it is, and is kept there. */
    void hold() {
        pause();
    }

    /** Whether it was playing when the window went: its sound may go on without it. */
    boolean going() {
        return playing();
    }

    /** It will open still at its place, not playing. */
    void openStill() {
        still = true;
    }

    /**
     * Back from behind the home screen: the picture takes over from where
     * its sound got to, and plays on if the sound was playing.
     */
    void takeBack(long at, boolean play) {
        Shelf.setSoundAt(getContext(), key, at);
        if (player == null || !prepared) {
            still = !play;
            return;
        }
        try {
            player.seekTo(at, MediaPlayer.SEEK_PREVIOUS_SYNC);
        } catch (Exception ignored) {
            // it goes on from where the picture was
        }
        if (play) {
            play();
        }
        showFoot();
    }

    /** A touch: stopped, or going on, and the mark in the middle says which. */
    private void toggle() {
        if (playing()) {
            pause();
            mark = Glyph.PAUSE;
        } else {
            play();
            mark = Glyph.PLAY;
        }
        markAt = System.currentTimeMillis();
        showFoot();
        stripLine();
        showStrip();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    private void save() {
        if (player != null && prepared) {
            long at = position();
            if (at > 0L && at < length - 3000L) {
                Shelf.setSoundAt(getContext(), key, at);
            }
        }
    }

    private void release() {
        removeCallbacks(keep);
        save();
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // the player lets go either way
            }
            player = null;
        }
        prepared = false;
        if (focus != null) {
            audio.abandonAudioFocusRequest(focus);
        }
        if (surface != null) {
            surface.release();
            surface = null;
        }
        setKeepScreenOn(false);
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(hidePanel);
        removeCallbacks(footAway);
        removeCallbacks(footBeat);
        strip.end();
        release();
        super.onDetachedFromWindow();
    }

    // ------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (closing || picking) {
            return true;
        }
        pinch.onTouchEvent(e);
        taps.onTouchEvent(e);
        if (e.getPointerCount() > 1 || pinch.isInProgress()) {
            /* Two fingers are for the size, not for closing. */
            pulling = false;
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = e.getX();
                downY = e.getY();
                fromEdge = downY < Film.edge(this);
                if (!pulling && drop > 0f) {
                    /* A pull the phone took away midway, never let go: the film comes back up. */
                    rise();
                }
                pulling = false;
                if (tracker != null) {
                    tracker.recycle();
                }
                tracker = VelocityTracker.obtain();
                tracker.addMovement(e);
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return true;
            case MotionEvent.ACTION_MOVE: {
                if (tracker != null) {
                    tracker.addMovement(e);
                }
                float dx = e.getX() - downX;
                float dy = e.getY() - downY;
                if (!pulling && !fromEdge) {
                    /* Sideways is left to the strip and its dial; downward closes. */
                    if (dy > slop && dy > Math.abs(dx)) {
                        pulling = true;
                        hideStrip();
                    }
                }
                if (pulling) {
                    drop = Math.max(0f, dy);
                    sink();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                float fall = 0f;
                if (tracker != null) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    fall = tracker.getYVelocity();
                    tracker.recycle();
                    tracker = null;
                }
                if (pulling) {
                    pulling = false;
                    if (drop > getHeight() / 5f || fall > 1400f) {
                        close();
                    } else {
                        rise();
                    }
                }
                return true;
            }
            default:
                return true;
        }
    }

    /**
     * How far down from the top a touch still belongs to the phone: its own
     * gesture edge where it says, and never less than a finger's width.
     */
    static float edge(View v) {
        float least = 48f * v.getResources().getDisplayMetrics().density;
        if (android.os.Build.VERSION.SDK_INT >= 29 && v.getRootWindowInsets() != null) {
            return Math.max(least, v.getRootWindowInsets().getSystemGestureInsets().top);
        }
        return least;
    }

    /** Whether it has closed: a closed film is never shown again, a new one is made. */
    boolean closed() {
        return closing;
    }

    /** A frame is being chosen over it: it stops, its keys go, it answers nothing itself. */
    void picking(boolean on) {
        picking = on;
        pulling = false;
        if (on) {
            pause();
            hideStrip();
        }
    }

    /** To this very frame, not the nearest one the film can start from. */
    void exact(long ms) {
        if (player == null || !prepared) {
            return;
        }
        try {
            player.seekTo(Math.max(0L, Math.min(length, ms)), MediaPlayer.SEEK_CLOSEST);
        } catch (Exception ignored) {
            // it stays where it was
        }
    }

    /** The film, pulled down, follows the finger and grows smaller; the dark behind it thins. */
    private void sink() {
        float h = Math.max(1f, getHeight());
        float thin = Math.min(1f, drop / (h * 0.6f));
        float k = 1f - 0.35f * thin;
        screen.setTranslationY(drop);
        screen.setScaleX(k);
        screen.setScaleY(k);
        invalidate();
    }

    private void rise() {
        final float from = drop;
        ValueAnimator made = ValueAnimator.ofFloat(from, 0f);
        made.setDuration(Pace.LEAVE);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                drop = (Float) a.getAnimatedValue();
                sink();
            }
        });
        made.start();
    }

    /** The film closes as a picture does: it sinks, grows smaller, the dark thins. */
    void close() {
        if (closing) {
            return;
        }
        closing = true;
        pause();
        final float from = drop;
        final float to = getHeight() * 0.6f;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Math.round(Pace.GROW * 0.75f));
        made.setInterpolator(Pace.AWAY);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                drop = from + (to - from) * (Float) a.getAnimatedValue();
                sink();
            }
        });
        made.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (watcher != null) {
                    watcher.done();
                }
            }
        });
        made.start();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    // ------------------------------------------------------------ drawing

    private void showFoot() {
        footAt = System.currentTimeMillis();
        if (!ticking) {
            ticking = true;
            postOnAnimation(tick);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float thin = h <= 0f ? 0f : Math.min(1f, drop / (h * 0.6f));
        canvas.drawColor((Math.round(255f * (1f - thin)) << 24) | (dark & 0x00FFFFFF));
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (drop > 0f) {
            return;
        }
        long now = System.currentTimeMillis();
        float w = getWidth();
        float h = getHeight();
        float u = getResources().getDisplayMetrics().density;

        /* The mark in the middle, for a moment after a touch. */
        long sinceMark = now - markAt;
        if (sinceMark < 700L) {
            float a = 1f - sinceMark / 700f;
            paint.setColor(0xFF000000);
            paint.setAlpha(Math.round(120f * a));
            canvas.drawCircle(w / 2f, h / 2f, 40f * u, paint);
            Desk.mark(canvas, mark, w / 2f, h / 2f, 40f * u, (Math.round(255f * a) << 24) | 0x00FFFFFF);
        }

    }

    /** Minutes and seconds, and hours when there are any. */
    static String clock(long ms) {
        long s = Math.max(0L, ms / 1000L);
        long hh = s / 3600L;
        long mm = (s / 60L) % 60L;
        long ss = s % 60L;
        return hh > 0L
            ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hh, mm, ss)
            : String.format(java.util.Locale.ROOT, "%d:%02d", mm, ss);
    }
}
