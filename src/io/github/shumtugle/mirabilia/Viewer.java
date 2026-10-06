package io.github.shumtugle.mirabilia;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import java.util.ArrayList;

/**
 * One picture at a time, over the whole screen, and the pictures beside it
 * a swipe away. A touch brings its label, or puts it away — the same touch
 * that brings a video's strip. Every way out closes it the same way, a pull
 * downward or back: the picture sinks, grows smaller, and the dark behind
 * it thins, as a print put back on the pile. Upward, the picture gives a
 * little and springs back, and the sheet of what it says about itself
 * rises: down to close, up to be told.
 *
 * Two touches in quick succession bring the picture close where they
 * fell, and two more put it back whole; the second touch held and drawn
 * up or down brings it closer or further by as much, one-handed. Because a
 * touch might be the first of two, the label waits the moment it takes to
 * tell them apart.
 *
 * Sideways, a finger drags the picture and the next one comes in after it;
 * let go past a quarter of the width, or with a flick, and it goes; short of
 * that, it comes back. Downward, the picture follows the finger, grows
 * smaller and the dark behind it thins; let go far enough, or with a flick,
 * and it closes, as a print put back on the pile; short of that, it comes
 * back. Two fingers bring it closer, as close as they like, and one finger
 * then moves over it; opened fingers below whole put it back whole. The
 * small picture the grid already has is shown at once, and the picture in
 * full takes its place when it has been read. Held, it offers what can be
 * done with it.
 */
final class Viewer extends View {

    interface Watcher {
        /** Another picture came to the front. */
        void showing(int i);

        /** The picture was held. */
        void held(int i);

        /** Done: pulled down and let go, or closed by back. */
        void done(int i);

        /** Touched once: the label comes, or goes. */
        void touched(int i);

        /** Pushed upward and let go: the facts are wanted. */
        void lifted(int i);

        /** The picture has begun to close, whichever way it was asked to. */
        void closing(int i);
    }

    private final ArrayList<Gallery.Photo> photos;
    private final Watcher watcher;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF box = new RectF();
    private int at;
    /** The picture in full, for the one in front, once it is read. */
    private Bitmap full;
    private int fullOf = -1;
    private int asking = -1;

    /** How close, and where, while looking close. */
    private float zoom = 1f;
    private float offX;
    private float offY;
    /** How far the pictures are dragged aside, while turning. */
    private float slide;
    private ValueAnimator settling;
    private final ScaleGestureDetector pinch;
    private final GestureDetector taps;
    private final int slop;
    private float downX;
    private float downY;
    /** A touch from the top edge is the phone's shade, not a pull on the picture. */
    private boolean fromEdge;
    private float lastX;
    private float lastY;
    private boolean turning;
    private boolean panning;
    /** How far the picture is pulled down, while it is. */
    private boolean pulling;
    private float drop;
    /** How far it is pushed up, while it is; it gives only a third of that. */
    private boolean lifting;
    private float lift;
    /**
     * The second of two touches is down: it is either a touch again, which
     * brings the picture close or whole, or it is drawn along, which zooms,
     * one-handed. Either way it is not a pull, a push or a turn.
     */
    private boolean second;
    private boolean secondMoved;
    private float secondX;
    private float secondY;
    private ValueAnimator zooming;
    /** How close two touches bring a whole picture. */
    private static final float CLOSE = 2.5f;
    private VelocityTracker tracker;

    Viewer(Context context, ArrayList<Gallery.Photo> photos, int start, Watcher watcher) {
        super(context);
        this.photos = photos;
        this.watcher = watcher;
        this.at = Math.max(0, Math.min(photos.size() - 1, start));
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        pinch = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float before = zoom;
                zoom = Math.max(1f, Math.min(6f, zoom * d.getScaleFactor()));
                float k = zoom / before;
                float cx = d.getFocusX() - getWidth() / 2f;
                float cy = d.getFocusY() - getHeight() / 2f;
                offX = cx - (cx - offX) * k;
                offY = cy - (cy - offY) * k;
                clamp();
                invalidate();
                return true;
            }
        });
        taps = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (turning || panning || pulling || lifting || pinch.isInProgress()) {
                    return true;
                }
                if (watcher != null && !closing) {
                    watcher.touched(at);
                }
                return true;
            }

            @Override
            public boolean onDoubleTapEvent(MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        second = true;
                        secondMoved = false;
                        secondX = e.getX();
                        secondY = e.getY();
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getX() - secondX) + Math.abs(e.getY() - secondY) > slop) {
                            secondMoved = true;
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                        if (!secondMoved && !pinch.isInProgress() && !closing) {
                            if (zoom > 1.001f) {
                                zoomTo(1f, e.getX(), e.getY());
                            } else {
                                zoomTo(CLOSE, e.getX(), e.getY());
                            }
                        }
                        break;
                    default:
                        break;
                }
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!turning && !panning && !pinch.isInProgress() && watcher != null) {
                    watcher.held(at);
                }
            }
        });
        dark = Tone.of(Tone.SURFACE_LOWEST);
    }

    private final int dark;

    int index() {
        return at;
    }

    /** The picture in front read again, as it now lies. */
    void reread() {
        Gallery.forget(photos.get(at));
        full = null;
        fullOf = -1;
        asking = -1;
        read();
        invalidate();
    }

    /** The picture in front as it is drawn now, for whatever takes the screen over from here. */
    Bitmap picture() {
        return photos.isEmpty() ? null : shown(at);
    }

    Gallery.Photo current() {
        return photos.isEmpty() ? null : photos.get(at);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        read();
    }

    /** Reads the picture in front in full, away from the screen's thread; the small ones of its neighbours are asked for too. */
    private void read() {
        if (photos.isEmpty() || getWidth() == 0) {
            return;
        }
        final int i = at;
        for (int d = -1; d <= 1; d++) {
            int n = i + d;
            if (n >= 0 && n < photos.size()) {
                Gallery.want(getContext(), photos.get(n), new Runnable() {
                    public void run() {
                        invalidate();
                    }
                });
            }
        }
        if (fullOf == i || asking == i) {
            return;
        }
        asking = i;
        final Gallery.Photo p = photos.get(i);
        final int w = getWidth();
        final int h = getHeight();
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Bitmap b = Gallery.decode(app, p, w, h);
                post(new Runnable() {
                    public void run() {
                        if (at != i) {
                            return;
                        }
                        asking = -1;
                        if (b != null) {
                            full = b;
                            fullOf = i;
                            invalidate();
                        }
                    }
                });
            }
        }, "picture").start();
    }

    private Bitmap shown(int i) {
        if (i == fullOf && full != null) {
            return full;
        }
        return Gallery.cached(photos.get(i));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float h = getHeight();
        float thin = h <= 0f ? 0f : Math.min(1f, drop / (h * 0.6f));
        canvas.drawColor((Math.round(255f * (1f - thin)) << 24) | (dark & 0x00FFFFFF));
        if (photos.isEmpty()) {
            return;
        }
        float w = getWidth();
        if (drop > 0f) {
            /* Pulled down, or closing, the picture follows and grows smaller. */
            float k = 1f - 0.35f * thin;
            canvas.save();
            canvas.translate(0f, drop);
            canvas.scale(k, k, w / 2f, h / 2f);
            draw(canvas, at, 0f, zoom, offX, offY);
            canvas.restore();
            return;
        }
        if (lift > 0f) {
            canvas.save();
            canvas.translate(0f, -lift / 3f);
            draw(canvas, at, 0f, zoom, offX, offY);
            canvas.restore();
            return;
        }
        draw(canvas, at, slide, zoom, offX, offY);
        if (slide < 0f && at + 1 < photos.size()) {
            draw(canvas, at + 1, slide + w + gap(), 1f, 0f, 0f);
        } else if (slide > 0f && at > 0) {
            draw(canvas, at - 1, slide - w - gap(), 1f, 0f, 0f);
        }
    }

    private float gap() {
        return Round.px(16f);
    }

    private void draw(Canvas canvas, int i, float dx, float z, float ox, float oy) {
        Bitmap b = shown(i);
        if (b == null || b.isRecycled()) {
            return;
        }
        float w = getWidth();
        float h = getHeight();
        float k = Math.min(w / b.getWidth(), h / b.getHeight());
        float bw = b.getWidth() * k * z;
        float bh = b.getHeight() * k * z;
        box.set((w - bw) / 2f + ox + dx, (h - bh) / 2f + oy, (w + bw) / 2f + ox + dx, (h + bh) / 2f + oy);
        canvas.drawBitmap(b, null, box, paint);
    }

    private float fitW() {
        Bitmap b = shown(at);
        if (b == null) {
            return getWidth();
        }
        return b.getWidth() * Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight());
    }

    private float fitH() {
        Bitmap b = shown(at);
        if (b == null) {
            return getHeight();
        }
        return b.getHeight() * Math.min(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight());
    }

    private void clamp() {
        if (zoom <= 1.001f) {
            zoom = 1f;
            offX = 0f;
            offY = 0f;
            return;
        }
        float sx = Math.max(0f, (fitW() * zoom - getWidth()) / 2f);
        float sy = Math.max(0f, (fitH() * zoom - getHeight()) / 2f);
        offX = Math.max(-sx, Math.min(sx, offX));
        offY = Math.max(-sy, Math.min(sy, offY));
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (photos.isEmpty()) {
            return false;
        }
        pinch.onTouchEvent(e);
        taps.onTouchEvent(e);
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (closing) {
                    return true;
                }
                fromEdge = e.getY() < Film.edge(this);
                if (settling != null) {
                    settling.cancel();
                    settling = null;
                }
                if (zooming != null) {
                    zooming.cancel();
                    zooming = null;
                }
                downX = e.getX();
                downY = e.getY();
                lastX = downX;
                lastY = downY;
                turning = false;
                panning = false;
                pulling = false;
                lifting = false;
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
                if (second || pinch.isInProgress() || e.getPointerCount() > 1) {
                    lastX = e.getX();
                    lastY = e.getY();
                    return true;
                }
                float dx = e.getX() - lastX;
                float dy = e.getY() - lastY;
                if (zoom > 1.001f) {
                    if (panning || Math.abs(e.getX() - downX) + Math.abs(e.getY() - downY) > slop) {
                        panning = true;
                        offX += dx;
                        offY += dy;
                        clamp();
                        invalidate();
                    }
                } else if (pulling || (!turning && !lifting && !fromEdge && e.getY() - downY > slop
                    && Math.abs(e.getY() - downY) > Math.abs(e.getX() - downX))) {
                    pulling = true;
                    drop = Math.max(0f, e.getY() - downY);
                    invalidate();
                } else if (lifting || (!turning && downY - e.getY() > slop
                    && Math.abs(e.getY() - downY) > Math.abs(e.getX() - downX))) {
                    lifting = true;
                    lift = Math.max(0f, downY - e.getY());
                    invalidate();
                } else if (turning || Math.abs(e.getX() - downX) > slop) {
                    turning = true;
                    slide += dx;
                    /* At either end the picture gives, grudgingly. */
                    if ((slide > 0f && at == 0) || (slide < 0f && at == photos.size() - 1)) {
                        slide -= dx * 0.7f;
                    }
                    invalidate();
                }
                lastX = e.getX();
                lastY = e.getY();
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (second) {
                    /* The second touch is over, whatever it was. */
                    second = false;
                    if (tracker != null) {
                        tracker.recycle();
                        tracker = null;
                    }
                    if (zoom < 1.001f && zoom != 1f) {
                        zoom = 1f;
                        clamp();
                        invalidate();
                    }
                    return true;
                }
                float fling = 0f;
                float fall = 0f;
                if (tracker != null) {
                    tracker.addMovement(e);
                    tracker.computeCurrentVelocity(1000);
                    fling = tracker.getXVelocity();
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
                    return true;
                }
                if (lifting) {
                    lifting = false;
                    boolean asked = lift > getHeight() / 10f || fall < -1200f;
                    sink();
                    if (asked && watcher != null) {
                        watcher.lifted(at);
                    }
                    return true;
                }
                if (zoom < 1.001f && zoom != 1f) {
                    zoom = 1f;
                    clamp();
                }
                if (turning) {
                    float w = getWidth();
                    int dir = 0;
                    if ((slide < -w / 4f || fling < -1200f) && at + 1 < photos.size()) {
                        dir = 1;
                    } else if ((slide > w / 4f || fling > 1200f) && at > 0) {
                        dir = -1;
                    }
                    settle(dir);
                }
                turning = false;
                panning = false;
                return true;
            }
            default:
                return true;
        }
    }

    private boolean closing;

    /**
     * The picture closes: it sinks from wherever it is, grows smaller and the
     * dark thins, and when it has gone whoever watches is told. The same for
     * a touch, a pull let go, or back.
     */
    void close() {
        if (closing) {
            return;
        }
        closing = true;
        if (settling != null) {
            settling.cancel();
        }
        if (watcher != null) {
            watcher.closing(at);
        }
        final float from = drop;
        final float to = getHeight() * 0.6f;
        final float z = zoom;
        final float ox = offX;
        final float oy = offY;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Math.round(Pace.GROW * 0.75f));
        made.setInterpolator(Pace.AWAY);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                drop = from + (to - from) * t;
                zoom = z + (1f - z) * t;
                offX = ox * (1f - t);
                offY = oy * (1f - t);
                invalidate();
            }
        });
        made.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (watcher != null) {
                    watcher.done(at);
                }
            }
        });
        made.start();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    /**
     * Closer, or back to whole, softly: the point touched stays under the
     * finger as the picture grows around it, as far as the edges allow.
     */
    private void zoomTo(float target, float x, float y) {
        if (zooming != null) {
            zooming.cancel();
        }
        final float z0 = zoom;
        final float ox0 = offX;
        final float oy0 = offY;
        float k = target / zoom;
        float cx = x - getWidth() / 2f;
        float cy = y - getHeight() / 2f;
        zoom = target;
        offX = target <= 1f ? 0f : cx - (cx - offX) * k;
        offY = target <= 1f ? 0f : cy - (cy - offY) * k;
        clamp();
        final float z1 = zoom;
        final float ox1 = offX;
        final float oy1 = offY;
        zoom = z0;
        offX = ox0;
        offY = oy0;
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(Pace.ARRIVE);
        made.setInterpolator(Pace.EMPHASIS);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                zoom = z0 + (z1 - z0) * t;
                offX = ox0 + (ox1 - ox0) * t;
                offY = oy0 + (oy1 - oy0) * t;
                invalidate();
            }
        });
        zooming = made;
        made.start();
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    /** A picture pushed up comes back down to its place. */
    private void sink() {
        final float from = lift;
        ValueAnimator made = ValueAnimator.ofFloat(from, 0f);
        made.setDuration(Pace.GROW);
        made.setInterpolator(Pace.EMPHASIS);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                lift = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        made.start();
    }

    /** A pull short of closing: the picture goes back up, whole. */
    private void rise() {
        final float from = drop;
        ValueAnimator made = ValueAnimator.ofFloat(from, 0f);
        made.setDuration(Pace.LEAVE);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                drop = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        made.start();
    }

    /** The pictures slide to where they go: the next, the one before, or back. */
    private void settle(final int dir) {
        final float from = slide;
        final float to = dir == 0 ? 0f : -dir * (getWidth() + gap());
        ValueAnimator made = ValueAnimator.ofFloat(0f, 1f);
        made.setDuration(dir == 0 ? Pace.LEAVE : Pace.GROW);
        made.setInterpolator(Pace.STANDARD);
        made.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator a) {
                slide = from + (to - from) * (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        made.addListener(new android.animation.AnimatorListenerAdapter() {
            private boolean cut;

            @Override
            public void onAnimationCancel(android.animation.Animator a) {
                cut = true;
            }

            @Override
            public void onAnimationEnd(android.animation.Animator a) {
                if (cut) {
                    return;
                }
                slide = 0f;
                if (dir != 0) {
                    at += dir;
                    zoom = 1f;
                    offX = 0f;
                    offY = 0f;
                    read();
                    if (watcher != null) {
                        watcher.showing(at);
                    }
                }
                invalidate();
            }
        });
        settling = made;
        made.start();
        if (dir != 0) {
            performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (settling != null) {
            settling.cancel();
        }
        if (zooming != null) {
            zooming.cancel();
        }
        full = null;
        fullOf = -1;
        super.onDetachedFromWindow();
    }
}
