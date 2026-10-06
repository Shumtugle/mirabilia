package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Camera;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.InputStream;
import java.util.ArrayList;

/* The page as a printed book has it: paper, serif, justified column,
   running head above and a number below. What a stylesheet would do on
   the web is done here by hand, because there is no web page. */
final class Reader {

    static int PAPER = 0xFFF4ECD8;
    static int INK = 0xFF2A2520;
    static int FAINT = 0xFF8A7858;
    static int DROP = 0xFF5A4530;

    /* Two papers, one geometry. Night is not an inversion of day: the ink
       goes warm grey rather than white, or the page glares in the dark. */
    static void theme(android.content.Context c) {
        boolean night = Shelf.prefs(c).getBoolean("night", false);
        PAPER = night ? 0xFF1F1A15 : 0xFFF4ECD8;
        INK = night ? 0xFFD4C4A8 : 0xFF2A2520;
        FAINT = night ? 0xFF8A7858 : 0xFF8A7858;
        DROP = night ? 0xFFD4B896 : 0xFF5A4530;
    }

    static boolean night(android.content.Context c) {
        return Shelf.prefs(c).getBoolean("night", false);
    }

    static void setNight(android.content.Context c, boolean v) {
        Shelf.prefs(c).edit().putBoolean("night", v).apply();
    }

    private Reader() {
    }

    static boolean isPdf(Shelf.Item it) {
        String m = it.mime == null ? "" : it.mime;
        String n = it.name == null ? "" : it.name.toLowerCase();
        return m.equals("application/pdf") || n.endsWith(".pdf");
    }

    static boolean isFb2(Shelf.Item it) {
        String n = it.name == null ? "" : it.name.toLowerCase();
        return n.endsWith(".fb2");
    }

    /* fb2 is XML, so the encoding is declared inside the file — passing null
       lets the parser read that declaration instead of guessing, which is
       the whole reason half of these books arrive in windows-1251. */
    static String fb2(Context c, Uri doc) {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(doc);
            if (in == null) {
                return null;
            }
            XmlPullParserFactory f = XmlPullParserFactory.newInstance();
            XmlPullParser p = f.newPullParser();
            p.setInput(in, null);
            StringBuilder sb = new StringBuilder();
            boolean inBody = false;
            boolean inPara = false;
            StringBuilder para = new StringBuilder();
            int e = p.getEventType();
            while (e != XmlPullParser.END_DOCUMENT) {
                if (e == XmlPullParser.START_TAG) {
                    String t = p.getName();
                    if ("body".equals(t)) {
                        inBody = true;
                    } else if (inBody && ("p".equals(t) || "title".equals(t)
                            || "subtitle".equals(t))) {
                        inPara = true;
                        para.setLength(0);
                    }
                } else if (e == XmlPullParser.TEXT && inPara) {
                    para.append(p.getText());
                } else if (e == XmlPullParser.END_TAG) {
                    String t = p.getName();
                    if ("body".equals(t)) {
                        inBody = false;
                    } else if (inPara && ("p".equals(t) || "title".equals(t)
                            || "subtitle".equals(t))) {
                        inPara = false;
                        String s = para.toString().trim();
                        if (s.length() > 0) {
                            sb.append(s).append("\n\n");
                        }
                    }
                }
                e = p.next();
            }
            return sb.toString();
        } catch (Exception ex) {
            Trace.note("op_read: " + String.valueOf(ex));
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /* Markdown is stripped, not rendered. A reader shows the sentence, and
       the hashes and asterisks are instructions to a typesetter, not text. */
    static CharSequence markdown(String raw) {
        String[] lines = raw.split("\n", -1);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String l = lines[i];
            boolean head = l.startsWith("#");
            if (head) {
                l = l.replaceAll("^#+\\s*", "");
            }
            l = l.replace("**", "").replace("__", "");
            l = l.replaceAll("^\\s*[-*+]\\s+", "\u00b7 ");
            int from = sb.length();
            sb.append(l).append('\n');
            if (head && l.length() > 0) {
                sb.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),
                        from, from + l.length(), 0);
                sb.setSpan(new android.text.style.ForegroundColorSpan(DROP),
                        from, from + l.length(), 0);
            }
        }
        return sb;
    }

    /* ---- the page itself ---- */

    static final class Page extends View {

        private final TextPaint ink = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private CharSequence text = "";
        private String head = "";
        private StaticLayout layout;
        private final ArrayList<Integer> breaks = new ArrayList<Integer>();
        private int page = 0;
        private int padX, padTop, padBottom;

        /* The leaf that is leaving, kept as a picture and swung about the
           spine. Redrawing live text at every frame would stutter; a bitmap
           on a rotating plane does not. */
        private final Camera camera = new Camera();
        private final Matrix mat = new Matrix();
        private final Paint plate = new Paint(Paint.FILTER_BITMAP_FLAG);
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap under, over, blur;
        private float drag = 0f;
        private int dir = 0;
        private boolean dragging = false;
        private float downX = 0f;
        private float downY = 0f;
        /* What is being read right now, as a range of characters. The voice
           thinks in paragraphs and the eye in lines, but the page only
           needs to know which characters to underline. */
        private int markFrom = -1;
        private int markTo = -1;

        private int want = -1;
        private Runnable turned;
        private Runnable chrome;

        /* The view eats its own touches, so the built-in long press detector
           never runs. It has to be counted by hand or the capsules never rise. */
        private final android.os.Handler hold = new android.os.Handler();
        private boolean held = false;
        private final Runnable longPress = new Runnable() {
            public void run() {
                held = true;
                performLongClick();
            }
        };

        void onChrome(Runnable r) {
            chrome = r;
        }

        Page(Context c) {
            super(c);
            ink.setColor(INK);
            ink.setTypeface(Typeface.SERIF);
            ink.setTextSize(sp(Shelf.fontSize(c)));
            faint.setColor(FAINT);
            faint.setTypeface(Typeface.SERIF);
            faint.setTextSize(sp(10));
            faint.setTextAlign(Paint.Align.CENTER);
            padX = Round.dp(26);
            padTop = Round.dp(30);
            padBottom = Round.dp(44);
            setBackgroundColor(PAPER);
        }

        /* The accent, dimmed to sit under type rather than shout at it. */
        private int guide() {
            int c = Shelf.accent(getContext());
            if (!night(getContext())) {
                int r = (int) (((c >> 16) & 255) * 0.75f);
                int g = (int) (((c >> 8) & 255) * 0.7f);
                int b = (int) ((c & 255) * 0.6f);
                c = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            return (c & 0xFFFFFF) | 0xB0000000;
        }

        private float sp(float v) {
            return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v,
                    getResources().getDisplayMetrics());
        }

        void resize() {
            int keep = layout != null && breaks.size() >= 2 ? startChar(page) : -1;
            ink.setTextSize(sp(Shelf.fontSize(getContext())));
            layout = null;
            breaks.clear();
            build(getWidth(), getHeight());
            holdPlace(keep);
            invalidate();
        }

        void set(CharSequence t, String title) {
            text = t == null ? "" : t;
            head = title == null ? "" : title;
            page = 0;
            layout = null;
            requestLayout();
            invalidate();
        }

        CharSequence text() {
            return text;
        }

        int page() {
            return page + 1;
        }

        int pageIndex() {
            return page;
        }

        /* Where this page begins in the text. The voice runs on paragraphs
           and the page on lines, so one of them has to translate. */
        int startChar(int p) {
            if (layout == null || breaks.size() < 2) {
                return 0;
            }
            int i = Math.max(0, Math.min(p, breaks.size() - 2));
            return layout.getLineStart(breaks.get(i));
        }

        int endChar(int p) {
            if (layout == null || breaks.size() < 2) {
                return Integer.MAX_VALUE;
            }
            int i = Math.max(0, Math.min(p + 1, breaks.size() - 1));
            return layout.getLineStart(breaks.get(i));
        }

        /* Where the finger touched, in characters. A long press is a place,
           not just a signal: the voice should start from the phrase under
           the thumb rather than from the top of the page. */
        int charAtTouch() {
            if (layout == null || breaks.size() < 2) {
                return 0;
            }
            int from = breaks.get(Math.min(page, breaks.size() - 2));
            float top = layout.getLineTop(from);
            float y = downY - padTop + top;
            int line = layout.getLineForVertical((int) y);
            return layout.getOffsetForHorizontal(line, downX - padX);
        }

        void mark(int from, int to) {
            markFrom = from;
            markTo = to;
            invalidate();
        }

        void unmark() {
            markFrom = -1;
            markTo = -1;
            invalidate();
        }

        int lineOfChar(int off) {
            return layout == null ? 0 : layout.getLineForOffset(off);
        }

        int lineStartChar(int line) {
            return layout == null ? 0 : layout.getLineStart(line);
        }

        int lineEndChar(int line) {
            return layout == null ? 0 : layout.getLineEnd(line);
        }

        int lines() {
            return layout == null ? 0 : layout.getLineCount();
        }

        int firstLineOf(int p) {
            if (breaks.size() < 2) {
                return 0;
            }
            return breaks.get(Math.max(0, Math.min(p, breaks.size() - 2)));
        }

        int lastLineOf(int p) {
            if (breaks.size() < 2) {
                return 0;
            }
            return breaks.get(Math.max(1, Math.min(p + 1, breaks.size() - 1))) - 1;
        }

        int pageOfLine(int line) {
            for (int i = 0; i + 1 < breaks.size(); i++) {
                if (line < breaks.get(i + 1)) {
                    return i;
                }
            }
            return Math.max(0, breaks.size() - 2);
        }

        void showPage(int p) {
            if (breaks.isEmpty()) {
                goTo(p);
                return;
            }
            page = Math.max(0, Math.min(p, breaks.size() - 2));
            invalidate();
        }

        /* Restoring happens before the pagination is known, so the wish is
           kept and applied once the lines have been measured. */
        void goTo(int p) {
            want = p;
            if (!breaks.isEmpty()) {
                page = Math.max(0, Math.min(p, breaks.size() - 2));
                invalidate();
            }
        }

        void whenTurned(Runnable r) {
            turned = r;
        }

        int pages() {
            return Math.max(1, breaks.size() - 1);
        }

        /* The turn is not a sheet lifting off a table. The layer that was
           read falls back along z, goes out of focus and is swallowed by
           graphite; the next one slides in over that emptiness like a
           shutter. What flashes at their meeting is the accent line from
           the icon — a spark at the contact of two conductors. */
        boolean turn(int d) {
            int p = page + d;
            if (p < 0 || p > breaks.size() - 2) {
                return false;
            }
            if (!prepare(d)) {
                return false;
            }
            commit();
            return true;
        }

        private boolean prepare(int d) {
            int p = page + d;
            if (p < 0 || p > breaks.size() - 2 || getWidth() <= 0) {
                return false;
            }
            dir = d;
            under = snap(page);
            over = snap(p);
            blur = Portal.blur(under);
            return under != null && over != null;
        }

        private void commit() {
            Snd.turn(getContext());
            ValueAnimator a = ValueAnimator.ofFloat(drag, 1f);
            a.setDuration((long) (380 * (1f - drag) + 80));
            /* Sharp at the start, heavy at the end: a vault door sliding
               into its grooves, not a spring. */
            a.setInterpolator(new android.view.animation.PathInterpolator(
                    0.05f, 0.75f, 0.1f, 1f));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator v) {
                    drag = ((Float) v.getAnimatedValue()).floatValue();
                    invalidate();
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator v) {
                    page = page + dir;
                    release();
                    if (turned != null) {
                        turned.run();
                    }
                }
            });
            a.start();
        }

        private void back() {
            ValueAnimator a = ValueAnimator.ofFloat(drag, 0f);
            a.setDuration(260);
            a.setInterpolator(new android.view.animation.DecelerateInterpolator(2.2f));
            a.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                public void onAnimationUpdate(ValueAnimator v) {
                    drag = ((Float) v.getAnimatedValue()).floatValue();
                    invalidate();
                }
            });
            a.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator v) {
                    release();
                }
            });
            a.start();
        }

        private void release() {
            drag = 0f;
            dir = 0;
            dragging = false;
            if (under != null) {
                under.recycle();
                under = null;
            }
            if (over != null) {
                over.recycle();
                over = null;
            }
            if (blur != null) {
                blur.recycle();
                blur = null;
            }
            invalidate();
        }

        private Bitmap snap(int which) {
            if (getWidth() <= 0 || getHeight() <= 0) {
                return null;
            }
            try {
                Bitmap b = Bitmap.createBitmap(getWidth(), getHeight(),
                        Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(b);
                c.drawColor(PAPER);
                paint(c, which);
                return b;
            } catch (Exception e) {
                return null;
            }
        }

        private void build(int w, int h) {
            int cw = w - padX * 2;
            int ch = h - padTop - padBottom;
            if (cw <= 0 || ch <= 0) {
                return;
            }
            StaticLayout.Builder b = StaticLayout.Builder.obtain(
                    text, 0, text.length(), ink, cw);
            b.setLineSpacing(0f, 1.35f);
            b.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD);
            /* Justification without hyphenation opens rivers of white down
               the column — in Russian, where words are long, it is the
               difference between a page and a ransom note. */
            b.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL);
            layout = b.build();

            breaks.clear();
            breaks.add(0);
            int top = 0;
            int slack = Round.dp(3);
            for (int i = 0; i < layout.getLineCount(); i++) {
                if (layout.getLineBottom(i) - top > ch - slack) {
                    breaks.add(i);
                    top = layout.getLineTop(i);
                }
            }
            breaks.add(layout.getLineCount());
            if (want > 0) {
                page = Math.max(0, Math.min(want, breaks.size() - 2));
                want = -1;
            }
        }

        /* The page may change size under the reader — the bar comes and
           goes, the screen turns. The lines are laid out again, and the
           reader is put back on the page that holds the words they were
           reading, not on the page with the same number. */
        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            int keep = layout != null && breaks.size() >= 2 && want < 0 ? startChar(page) : -1;
            build(w, h);
            holdPlace(keep);
        }

        private void holdPlace(int keep) {
            if (keep < 0 || layout == null || breaks.size() < 2) {
                return;
            }
            int was = page;
            page = pageOfLine(layout.getLineForOffset(Math.min(keep, Math.max(0, text.length() - 1))));
            if (page != was && turned != null) {
                turned.run();
            }
        }

        private void paint(Canvas cv, int which) {
            if (layout == null || breaks.size() < 2) {
                return;
            }
            int from = breaks.get(Math.min(Math.max(which, 0), breaks.size() - 2));
            int top = layout.getLineTop(from);
            int bottomLine = breaks.get(Math.min(which + 1, breaks.size() - 1));

            cv.save();
            /* The page ends where its own last line ends. Clipping at the
               foot of the paper instead let the top half of the next page's
               first line peer in under it. */
            int end = bottomLine < layout.getLineCount()
                    ? layout.getLineTop(bottomLine) : layout.getHeight();
            cv.clipRect(padX, padTop, getWidth() - padX,
                    Math.min(getHeight() - padBottom, padTop + (end - top)));
            cv.translate(padX, padTop - top);

            /* A rule under the line being read. Drawn beneath the text so a
               descender never sits on top of it, and only across the width
               the line actually occupies. */
            if (markFrom >= 0 && markTo > markFrom) {
                int a = layout.getLineForOffset(markFrom);
                int b = layout.getLineForOffset(Math.max(markFrom, markTo - 1));
                Paint rule = new Paint(Paint.ANTI_ALIAS_FLAG);
                rule.setColor(guide());
                float th = Round.dp(1.6f);
                for (int ln = a; ln <= b; ln++) {
                    float y = layout.getLineBottom(ln) - Round.dp(3);
                    float x0 = layout.getLineLeft(ln);
                    float x1 = layout.getLineRight(ln);
                    cv.drawRect(x0, y, x1, y + th, rule);
                }
            }

            layout.draw(cv, null, null, 0);
            cv.restore();

            if (head.length() > 0) {
                cv.drawText(head, getWidth() / 2f,
                        padTop - Round.dp(12), faint);
            }
            cv.drawText((which + 1) + " / " + pages(), getWidth() / 2f,
                    getHeight() - Round.dp(16), faint);
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (layout == null) {
                build(getWidth(), getHeight());
            }
            if (dir == 0 || over == null) {
                paint(cv, page);
                return;
            }
            Portal.draw(cv, this, blur, over, drag, dir, PAPER);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            float slop = Round.dp(14);
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getX();
                    downY = e.getY();
                    held = false;
                    hold.removeCallbacks(longPress);
                    hold.postDelayed(longPress, 420);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    float dx = e.getX() - downX;
                    if (Math.abs(dx) > slop) {
                        hold.removeCallbacks(longPress);
                    }
                    if (held) {
                        return true;
                    }
                    if (!dragging && Math.abs(dx) > slop) {
                        dragging = prepare(dx < 0 ? 1 : -1);
                        if (!dragging) {
                            return true;
                        }
                    }
                    if (dragging) {
                        drag = Math.min(1f, Math.max(0f,
                                (Math.abs(dx) - slop) / (getWidth() * 0.7f)));
                        invalidate();
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    hold.removeCallbacks(longPress);
                    if (held) {
                        held = false;
                        return true;
                    }
                    if (!dragging) {
                        /* A quarter each side turns, the middle half is for
                           controls. A tap in the centre must never move the
                           page — that is the one thing every reader gets
                           wrong and nobody forgives. */
                        if (dir == 0) {
                            float x = e.getX();
                            if (x < getWidth() * 0.25f) {
                                turn(-1);
                            } else if (x > getWidth() * 0.75f) {
                                turn(1);
                            } else if (chrome != null) {
                                chrome.run();
                            }
                        }
                        return true;
                    }
                    /* Let go past a third and the movement is carried
                       through; short of it the plane settles back without
                       a bounce. */
                    if (drag > 0.33f) {
                        commit();
                    } else {
                        back();
                    }
                    return true;
            }
            return true;
        }
    }

    /* The transition, kept in one place so the page and the pdf move by the
       same law. */
    static final class Portal {

        private Portal() {
        }

        /* No blur. A page smeared down to a twelfth and stretched back is
           not soft, it is blotchy — letters turn into ink stains. The layer
           that leaves simply darkens and steps back; the shadow does the
           work the blur was pretending to do. */
        static Bitmap blur(Bitmap src) {
            return src;
        }

        /* The line takes its colour from the seed, like everything else. */
        static int spark(Context c) {
            return Shelf.accent(c);
        }

        static void draw(Canvas cv, View v, Bitmap blurred, Bitmap over,
                         float p, int dir, int paper) {
            int w = v.getWidth();
            int h = v.getHeight();
            Paint pt = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

            cv.drawColor(0xFF0F1012);
            if (blurred != null) {
                /* The layer that was read falls back along z and dims. */
                float k = 1f - 0.08f * p;
                cv.save();
                cv.translate(w * (1 - k) / 2f, h * (1 - k) / 2f);
                cv.scale(k, k);
                cv.drawBitmap(blurred, null,
                        new android.graphics.Rect(0, 0, w, h), pt);
                cv.restore();
                pt.setColor((((int) (0xB0 * p)) << 24));
                pt.setShader(null);
                cv.drawRect(0, 0, w, h, pt);
            }

            float edge = dir > 0 ? w * (1f - p) : -w * (1f - p);

            /* The shadow the arriving sheet casts on what it covers. This is
               what makes the move read as depth — the blur never did. */
            float lip = Round.dp(26);
            Paint drop = new Paint(Paint.ANTI_ALIAS_FLAG);
            float from = dir > 0 ? edge - lip : edge + w;
            drop.setShader(new LinearGradient(from, 0,
                    from + (dir > 0 ? lip : -lip), 0,
                    0x00000000, 0x77000000, Shader.TileMode.CLAMP));
            cv.drawRect(Math.min(from, from + (dir > 0 ? lip : -lip)), 0,
                    Math.max(from, from + (dir > 0 ? lip : -lip)), h, drop);

            cv.save();
            cv.translate(edge, 0);
            pt.setColor(paper);
            cv.drawRect(0, 0, w, h, pt);
            pt.setAlpha(255);
            cv.drawBitmap(over, 0, 0, pt);
            cv.restore();

            /* The seam is a zone of optical tension, not a cut: a hard line
               with a short bloom, brightest as the planes cross. */
            float x = dir > 0 ? edge : edge + w;
            int accent = spark(v.getContext());
            float heat = (float) Math.sin(Math.PI * Math.min(1f, p * 0.85f + 0.15f));
            int a = (int) (255 * Math.min(1f, heat * 1.15f));
            float bloom = Round.dp(18);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setShader(new LinearGradient(x - (dir > 0 ? bloom : -bloom), 0, x, 0,
                    0x00000000, (a / 3 << 24) | (accent & 0xFFFFFF),
                    Shader.TileMode.CLAMP));
            cv.drawRect(Math.min(x - bloom, x + bloom), 0,
                    Math.max(x - bloom, x + bloom), h, g);
            Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
            line.setColor((a << 24) | (accent & 0xFFFFFF));
            float t = Round.dp(1.5f);
            cv.drawRect(x - t / 2f, 0, x + t / 2f, h, line);
        }
    }

    static final class Cbz extends View {

        private java.util.zip.ZipFile zip;
        private final ArrayList<String> names = new ArrayList<String>();
        private java.io.File cache;
        private Bitmap shot;
        private int page = 0;
        private final Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flat = new Paint(Paint.FILTER_BITMAP_FLAG);


        private Runnable chrome;
        private Runnable turned;

        /* The same three handles the text page offers, so the room can
           keep the place and clear the screen whatever it is reading. */
        void onChrome(Runnable r) {
            chrome = r;
        }

        void whenTurned(Runnable r) {
            turned = r;
        }

        int pageIndex() {
            return page;
        }

        void goTo(int p) {
            page = Math.max(0, Math.min(p, count() - 1));
            shoot();
            invalidate();
        }

        private int count() {
            return Math.max(1, names == null ? 1 : names.size());
        }

        Cbz(Context c) {
            super(c);
            setBackgroundColor(0xFF0F1012);
            faint.setColor(FAINT);
            faint.setTypeface(Typeface.SERIF);
            faint.setTextAlign(Paint.Align.CENTER);
            faint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                    10, c.getResources().getDisplayMetrics()));
        }

        boolean open(Context c, Uri uri, String name) {
            try {
                cache = new java.io.File(c.getCacheDir(), "open.cbz");
                java.io.InputStream in = c.getContentResolver().openInputStream(uri);
                java.io.OutputStream out = new java.io.FileOutputStream(cache);
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) {
                    out.write(b, 0, n);
                }
                out.close();
                in.close();
                zip = new java.util.zip.ZipFile(cache);
                java.util.Enumeration<? extends java.util.zip.ZipEntry> e = zip.entries();
                while (e.hasMoreElements()) {
                    String en = e.nextElement().getName();
                    String l = en.toLowerCase();
                    if (l.endsWith(".jpg") || l.endsWith(".jpeg") || l.endsWith(".png")
                            || l.endsWith(".webp")) {
                        names.add(en);
                    }
                }
                java.util.Collections.sort(names);
                return !names.isEmpty();
            } catch (Exception ex) {
                Trace.note("epub: " + String.valueOf(ex));
                return false;
            }
        }

        void close() {
            try {
                if (zip != null) {
                    zip.close();
                }
            } catch (Exception ignored) {
            }
            zip = null;
            if (shot != null) {
                shot.recycle();
                shot = null;
            }
        }

        private void shoot() {
            if (zip == null || names.isEmpty() || getWidth() <= 0) {
                return;
            }
            try {
                java.io.InputStream in = zip.getInputStream(
                        zip.getEntry(names.get(page)));
                android.graphics.BitmapFactory.Options o =
                        new android.graphics.BitmapFactory.Options();
                o.inSampleSize = 1;
                Bitmap b = android.graphics.BitmapFactory.decodeStream(in, null, o);
                in.close();
                if (b == null) {
                    return;
                }
                float k = Math.min(getWidth() / (float) b.getWidth(),
                        getHeight() / (float) b.getHeight());
                Bitmap fit = Bitmap.createScaledBitmap(b,
                        Math.max(1, (int) (b.getWidth() * k)),
                        Math.max(1, (int) (b.getHeight() * k)), true);
                if (fit != b) {
                    b.recycle();
                }
                if (shot != null) {
                    shot.recycle();
                }
                shot = fit;
            } catch (Exception e) {
                Trace.note("epub: " + String.valueOf(e));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            shoot();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (shot == null) {
                shoot();
            }
            if (shot != null) {
                cv.drawBitmap(shot, (getWidth() - shot.getWidth()) / 2f,
                        (getHeight() - shot.getHeight()) / 2f, flat);
            }
            cv.drawText((page + 1) + " / " + names.size(), getWidth() / 2f,
                    getHeight() - Round.dp(16), faint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && zip != null) {
                float x = e.getX();
                if (x >= getWidth() * 0.25f && x <= getWidth() * 0.75f) {
                    if (chrome != null) {
                        chrome.run();
                    }
                    return true;
                }
                int d = x < getWidth() * 0.25f ? -1 : 1;
                int p = page + d;
                if (p >= 0 && p < names.size()) {
                    Snd.turn(getContext());
                    page = p;
                    shoot();
                    setTranslationX(d > 0 ? getWidth() * 0.35f : -getWidth() * 0.35f);
                    setAlpha(0.2f);
                    animate().translationX(0f).alpha(1f).setDuration(360)
                            .setInterpolator(new android.view.animation.PathInterpolator(
                                    0.05f, 0.75f, 0.1f, 1f)).start();
                    invalidate();
                    if (turned != null) {
                        turned.run();
                    }
                }
            }
            return true;
        }
    }

    /* ---- pdf ---- */

    /* A pdf is already paginated and cannot be reflowed: what arrives is a
       picture of a page, so the frame stays and the type inside is theirs. */
    static final class Pdf extends View {

        private ParcelFileDescriptor fd;
        private android.graphics.pdf.PdfRenderer doc;
        private android.graphics.Bitmap shot;
        private int page = 0;
        private final Paint faint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint flat = new Paint(Paint.FILTER_BITMAP_FLAG);


        private Runnable chrome;
        private Runnable turned;

        /* The same three handles the text page offers, so the room can
           keep the place and clear the screen whatever it is reading. */
        void onChrome(Runnable r) {
            chrome = r;
        }

        void whenTurned(Runnable r) {
            turned = r;
        }

        int pageIndex() {
            return page;
        }

        void goTo(int p) {
            page = Math.max(0, Math.min(p, count() - 1));
            shoot();
            invalidate();
        }

        private int count() {
            return Math.max(1, doc == null ? 1 : doc.getPageCount());
        }

        Pdf(Context c) {
            super(c);
            setBackgroundColor(PAPER);
            faint.setColor(FAINT);
            faint.setTypeface(Typeface.SERIF);
            faint.setTextSize(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_SP, 10, c.getResources().getDisplayMetrics()));
            faint.setTextAlign(Paint.Align.CENTER);
        }

        boolean open(Context c, Uri uri) {
            try {
                fd = c.getContentResolver().openFileDescriptor(uri, "r");
                if (fd == null) {
                    return false;
                }
                doc = new android.graphics.pdf.PdfRenderer(fd);
                page = 0;
                return true;
            } catch (Exception e) {
                Trace.note("op_read: " + String.valueOf(e));
                return false;
            }
        }

        void close() {
            try {
                if (doc != null) {
                    doc.close();
                }
                if (fd != null) {
                    fd.close();
                }
            } catch (Exception ignored) {
            }
            doc = null;
            fd = null;
        }

        private void shoot() {
            if (doc == null || getWidth() <= 0) {
                return;
            }
            try {
                android.graphics.pdf.PdfRenderer.Page p = doc.openPage(page);
                int w = getWidth();
                int h = (int) (w * (p.getHeight() / (float) p.getWidth()));
                android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(
                        w, Math.max(1, h), android.graphics.Bitmap.Config.ARGB_8888);
                b.eraseColor(Color.WHITE);
                p.render(b, null, null,
                        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                p.close();
                shot = b;
            } catch (Exception e) {
                Trace.note("op_read: " + String.valueOf(e));
            }
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            super.onSizeChanged(w, h, ow, oh);
            shoot();
        }

        @Override
        protected void onDraw(Canvas cv) {
            super.onDraw(cv);
            if (shot == null) {
                shoot();
            }
            if (shot != null) {
                cv.drawBitmap(shot, 0, (getHeight() - shot.getHeight()) / 2f, flat);
            }
            if (doc != null) {
                cv.drawText((page + 1) + " / " + doc.getPageCount(), getWidth() / 2f,
                        getHeight() - Round.dp(16), faint);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_UP && doc != null) {
                float x = e.getX();
                if (x >= getWidth() * 0.25f && x <= getWidth() * 0.75f) {
                    if (chrome != null) {
                        chrome.run();
                    }
                    return true;
                }
                int d = x < getWidth() * 0.25f ? -1 : 1;
                int p = page + d;
                if (p >= 0 && p < doc.getPageCount()) {
                    Snd.turn(getContext());
                    page = p;
                    shoot();
                    setTranslationX(d > 0 ? getWidth() * 0.35f : -getWidth() * 0.35f);
                    setAlpha(0.2f);
                    animate().translationX(0f).alpha(1f).setDuration(360)
                            .setInterpolator(new android.view.animation.PathInterpolator(
                                    0.05f, 0.75f, 0.1f, 1f)).start();
                    invalidate();
                    if (turned != null) {
                        turned.run();
                    }
                }
            }
            return true;
        }
    }
}
