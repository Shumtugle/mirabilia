package io.github.shumtugle.mirabilia;

import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * The bar over picked words, drawn by our hand.
 *
 * The text asks the window for a floating bar and talks to it through the
 * bar's public face: where the picked words are, when to step aside while
 * the words move, when to go. This class is that face. The text keeps its
 * handles and its knowledge of the selection; the bar only shows our words,
 * in our capsules, above the picked text.
 *
 * Whatever goes wrong while it is being set up, the window falls back to the
 * system's own bar. A bar that is missing is worse than a bar that is not
 * ours.
 */
public final class Grip extends ActionMode {

    private static final long SHOW = 170L;
    private static final long FADE = 140L;
    private static final long LONGEST_HIDE = 3000L;
    private static final long USUAL_HIDE = 2000L;

    private final Activity host;
    private final View page;
    private final ActionMode.Callback2 engine;
    private final FrameLayout stage;
    private final Menu menu;
    private final float density;

    private final LinearLayout body;
    private final LinearLayout row;
    private final ScrollView sheet;
    private final LinearLayout column;
    private int sheetWide;
    private int sheetTall;
    private final Rect spot = new Rect();
    private final int[] at = new int[2];
    private final int[] base = new int[2];

    private CharSequence title;
    private CharSequence subtitle;
    private View custom;
    private boolean shown;
    private boolean done;
    private boolean resting;
    private boolean unfocused;

    /** Set once anything in the bar has failed; the window then uses the system's bar. */
    private static boolean failed;

    public static boolean failed() {
        return failed;
    }

    /** Marks the bar as not to be trusted for the rest of the run. */
    public static void fail() {
        failed = true;
    }

    private final Runnable back = new Runnable() {
        public void run() {
            resting = false;
            appear();
        }
    };

    private final ViewTreeObserver.OnScrollChangedListener moved =
        new ViewTreeObserver.OnScrollChangedListener() {
            public void onScrollChanged() {
                place();
            }
        };

    private final ViewTreeObserver.OnGlobalLayoutListener laid =
        new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                place();
            }
        };

    public Grip(Activity host, View page, ActionMode.Callback2 engine, FrameLayout stage) {
        this.host = host;
        this.page = page;
        this.engine = engine;
        this.stage = stage;
        this.density = host.getResources().getDisplayMetrics().density;
        /* A real menu for the engine to fill, taken from a public source. */
        this.menu = new android.widget.PopupMenu(host, page).getMenu();
        setType(TYPE_FLOATING);

        body = new LinearLayout(host);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.END);
        body.setVisibility(View.GONE);
        row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(0, dp(6), 0, dp(6));
        sheet = new ScrollView(host);
        sheet.setVerticalScrollBarEnabled(true);
        sheet.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        /* The list keeps to its rounded shape, press marks included. */
        sheet.setClipToOutline(true);
        sheet.addView(column, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        sheet.setVisibility(View.GONE);
        body.addView(row, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        body.addView(sheet, new LinearLayout.LayoutParams(0, 0));
    }

    /** Lets the engine fill its menu; false when it declines to have a bar at all. */
    public boolean begin() {
        return engine.onCreateActionMode(this, menu);
    }

    /**
     * Puts the words up. The row carries what fits across the screen, the
     * first word filled as the main one; the rest wait behind the last
     * mark and open as a plain list, our own words first, then, under a
     * hairline, what other applications offer.
     */
    public void show(ArrayList<String> words, ArrayList<Runnable> acts, int ours) {
        if (done) {
            return;
        }
        row.removeAllViews();
        column.removeAllViews();
        sheet.setVisibility(View.GONE);
        /* Solid and lifted: it floats over paper, and paper showing through
           words makes neither readable. */
        row.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.FULL));
        row.setElevation(dp(6));
        sheet.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.XL));
        sheet.setElevation(dp(6));
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            GradientDrawable thumb = new GradientDrawable();
            thumb.setColor(Tone.of(Tone.PRIMARY));
            thumb.setCornerRadius(dp(2));
            sheet.setVerticalScrollbarThumbDrawable(thumb);
            sheet.setEdgeEffectColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        }

        int room = stage.getWidth() - dp(24) - dp(8);
        TextView more = capsule("\u22EF", false, null);
        int moreWide = wide(more);
        int used = 0;
        int widest = 0;
        boolean spilled = false;
        boolean parted = false;
        for (int i = 0; i < words.size(); i++) {
            boolean last = i == words.size() - 1;
            if (!spilled) {
                TextView one = capsule(words.get(i), i == 0, acts.get(i));
                int w = wide(one);
                if (used + w + (last ? 0 : moreWide) <= room) {
                    row.addView(one);
                    used += w;
                    continue;
                }
                spilled = true;
            }
            if (i >= ours && !parted && column.getChildCount() > 0) {
                parted = true;
                View line = new View(host);
                line.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
                LinearLayout.LayoutParams hair = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(0.5f)));
                hair.setMargins(dp(18), dp(6), dp(18), dp(6));
                column.addView(line, hair);
            }
            TextView entry = entry(words.get(i), acts.get(i));
            widest = Math.max(widest, wide(entry));
            column.addView(entry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (spilled) {
            more.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    unfold(sheet.getVisibility() != View.VISIBLE);
                }
            });
            row.addView(more);
            int rowWide = used + moreWide + dp(8);
            sheetWide = Math.min(Math.max(rowWide, widest), room + dp(8));
            column.measure(View.MeasureSpec.makeMeasureSpec(sheetWide, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            sheetTall = column.getMeasuredHeight();
        }

        if (body.getParent() == null) {
            stage.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
            page.getViewTreeObserver().addOnScrollChangedListener(moved);
            page.getViewTreeObserver().addOnGlobalLayoutListener(laid);
        }
        shown = true;
        body.setVisibility(View.VISIBLE);
        body.setAlpha(0f);
        body.setScaleX(0.94f);
        body.setScaleY(0.94f);
        place();
        body.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(SHOW)
            .setInterpolator(Pace.STANDARD).start();
    }

    private void unfold(boolean open) {
        sheet.setVisibility(open ? View.VISIBLE : View.GONE);
        place();
        if (open) {
            sheet.scrollTo(0, 0);
            sheet.setAlpha(0f);
            sheet.setTranslationY(dp(-6));
            sheet.animate().alpha(1f).translationY(0f).setDuration(SHOW)
                .setInterpolator(Pace.STANDARD).start();
        }
    }

    /** A word in the row: the main one filled, the others bare with a press mark. */
    private TextView capsule(String word, boolean main, final Runnable act) {
        TextView one = Letter.set(new TextView(host), Letter.LABEL_L);
        one.setText(word);
        one.setSingleLine(true);
        one.setGravity(Gravity.CENTER);
        one.setMinHeight(dp(40));
        one.setPadding(dp(14), 0, dp(14), 0);
        one.setTextColor(Tone.of(main ? Tone.ON_PRIMARY : Tone.ON_SURFACE));
        one.setBackground(main
            ? Round.touch(Round.box(Tone.of(Tone.PRIMARY), Round.FULL), Tone.of(Tone.ON_PRIMARY),
                Round.FULL)
            : Round.touch(null, Tone.of(Tone.ON_SURFACE), Round.FULL));
        listen(one, act);
        return one;
    }

    /** A line of the list: words only, the whole width pressable. */
    private TextView entry(String word, final Runnable act) {
        TextView one = Letter.set(new TextView(host), Letter.BODY_L);
        one.setText(word);
        one.setSingleLine(true);
        one.setEllipsize(android.text.TextUtils.TruncateAt.END);
        one.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        one.setMinHeight(dp(46));
        one.setPadding(dp(18), 0, dp(18), 0);
        one.setTextColor(Tone.of(Tone.ON_SURFACE));
        one.setBackground(Round.touch(null, Tone.of(Tone.ON_SURFACE), Round.NONE));
        listen(one, act);
        return one;
    }

    private int wide(View one) {
        one.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return one.getMeasuredWidth();
    }

    private void listen(View one, final Runnable act) {
        if (act != null) {
            one.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    act.run();
                }
            });
        }
    }

    /**
     * Stands the bar over the picked words, or under them when there is no
     * room above, clear of the handles; out of sight when the words are.
     */
    private void place() {
        if (done || !shown || body.getParent() == null) {
            return;
        }
        spot.setEmpty();
        try {
            engine.onGetContentRect(this, page, spot);
        } catch (RuntimeException broken) {
            failed = true;
            Trace.note("own selection bar lost its place " + broken.getClass().getSimpleName());
            finish();
            return;
        }
        page.getLocationInWindow(at);
        stage.getLocationInWindow(base);
        int left = spot.left + at[0] - base[0];
        int right = spot.right + at[0] - base[0];
        int top = spot.top + at[1] - base[1];
        int bottom = spot.bottom + at[1] - base[1];
        int tall = stage.getHeight();
        int broad = stage.getWidth();
        int edge = dp(12);

        row.measure(View.MeasureSpec.makeMeasureSpec(broad - 2 * edge, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int rw = row.getMeasuredWidth();
        int rh = row.getMeasuredHeight();

        boolean gone = spot.isEmpty() || bottom < 0 || top > tall;
        body.setVisibility(gone ? View.INVISIBLE : View.VISIBLE);
        if (gone) {
            return;
        }
        int ry = top - rh - dp(10);
        if (ry < edge) {
            ry = bottom + dp(40);
        }
        if (ry + rh > tall - edge) {
            ry = Math.max(edge, tall - rh - edge);
        }
        int rx = (left + right) / 2 - rw / 2;
        rx = Math.max(edge, Math.min(rx, broad - rw - edge));

        int bw = rw;
        int by = ry;
        if (sheet.getVisibility() == View.VISIBLE) {
            /* The list opens where there is more room, and never past the
               edge: what does not fit scrolls inside it. */
            int gap = dp(6);
            int below = tall - edge - (ry + rh + gap);
            int above = ry - edge - gap;
            boolean down = below >= Math.min(sheetTall, dp(220)) || below >= above;
            int sh = Math.max(dp(46), Math.min(sheetTall, down ? below : above));
            /* Laying the list out is itself a layout the page hears about;
               asking again with the same size would never end. */
            LinearLayout.LayoutParams fit = (LinearLayout.LayoutParams) sheet.getLayoutParams();
            int topGap = down ? gap : 0;
            int bottomGap = down ? 0 : gap;
            if (fit.width != sheetWide || fit.height != sh
                || fit.topMargin != topGap || fit.bottomMargin != bottomGap) {
                fit.width = sheetWide;
                fit.height = sh;
                fit.topMargin = topGap;
                fit.bottomMargin = bottomGap;
                sheet.setLayoutParams(fit);
            }
            int at = body.indexOfChild(sheet);
            if (down && at != 1) {
                body.removeView(sheet);
                body.addView(sheet, 1);
            } else if (!down && at != 0) {
                body.removeView(sheet);
                body.addView(sheet, 0);
            }
            bw = Math.max(rw, sheetWide);
            by = down ? ry : ry - sh - gap;
        }
        /* The row keeps its place; the body grows to the left of its end. */
        int bx = Math.max(edge, Math.min(rx + rw - bw, broad - bw - edge));
        body.setTranslationX(bx);
        body.setTranslationY(by);
    }

    private void appear() {
        if (done || resting || unfocused || !shown) {
            return;
        }
        place();
        body.animate().cancel();
        body.animate().alpha(1f).setDuration(FADE).start();
    }

    private void vanish() {
        body.animate().cancel();
        body.animate().alpha(0f).setDuration(FADE).start();
    }

    // ------------------------------------------------------ the engine's calls

    @Override
    public void hide(long duration) {
        if (done) {
            return;
        }
        long wait = duration == DEFAULT_HIDE_DURATION ? USUAL_HIDE
            : Math.min(LONGEST_HIDE, duration);
        body.removeCallbacks(back);
        if (wait <= 0) {
            resting = false;
            appear();
            return;
        }
        resting = true;
        vanish();
        body.postDelayed(back, wait);
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        unfocused = !hasWindowFocus;
        if (unfocused) {
            vanish();
        } else {
            appear();
        }
    }

    @Override
    public void invalidateContentRect() {
        place();
    }

    @Override
    public void invalidate() {
        if (!done) {
            engine.onPrepareActionMode(this, menu);
            place();
        }
    }

    @Override
    public void finish() {
        if (done) {
            return;
        }
        done = true;
        body.removeCallbacks(back);
        page.getViewTreeObserver().removeOnScrollChangedListener(moved);
        page.getViewTreeObserver().removeOnGlobalLayoutListener(laid);
        body.animate().cancel();
        if (body.getParent() != null) {
            stage.removeView(body);
        }
        engine.onDestroyActionMode(this);
    }

    @Override
    public Menu getMenu() {
        return menu;
    }

    @Override
    public MenuInflater getMenuInflater() {
        return new MenuInflater(host);
    }

    @Override
    public void setTitle(CharSequence value) {
        title = value;
    }

    @Override
    public void setTitle(int resId) {
        title = text(resId);
    }

    @Override
    public void setSubtitle(CharSequence value) {
        subtitle = value;
    }

    @Override
    public void setSubtitle(int resId) {
        subtitle = text(resId);
    }

    /** A title by number may belong to the engine's package; a miss is no title. */
    private CharSequence text(int resId) {
        try {
            return resId == 0 ? null : host.getString(resId);
        } catch (RuntimeException broken) {
            return null;
        }
    }

    @Override
    public void setCustomView(View view) {
        custom = view;
    }

    @Override
    public CharSequence getTitle() {
        return title;
    }

    @Override
    public CharSequence getSubtitle() {
        return subtitle;
    }

    @Override
    public View getCustomView() {
        return custom;
    }

    private int dp(float value) {
        return Math.round(value * density);
    }
}
