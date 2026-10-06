package io.github.shumtugle.mirabilia;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.widget.RemoteViews;

/**
 * What stands on the home screen: one widget, over one account of what
 * sounds — a recording, or a book read aloud — what it is, where it is, how
 * far, and whether it sounds.
 *
 * It is a single picture in the application's language, with touch zones
 * laid over it in the same proportions: the room's numeral and a breath,
 * the name in the book face, the whole of it as a scale with the part heard
 * in the accent, and a key that holds and goes on, with two small discs
 * under it for back and on.
 *
 * Its look is chosen in the settings. The ground is the seed's own
 * container, or glass laid over the wallpaper — a near-black made nearly
 * clear, with one hairline of white, and everything on it in warm bone and
 * brass, so it can stand beside other glass widgets without jarring. The
 * key is the gold ball of the icon, or a record. The scale is a drum of
 * ticks, or a single thread.
 *
 * The record wears the cover of what sounds, when it has one: the picture
 * printed over the whole disc in the two inks of the ground, not in its own
 * colours, and in the middle, as the label of a record, a small disc of the
 * ground made half clear, carrying the key's mark. The middle of a cover is
 * where its face usually is; the label lets it show through. With no cover it is a plain disc. The
 * shape of the widget is the same either way; only what is inside the disc
 * changes, so nothing jumps when a file with a picture gives way to one
 * without. The ball wears nothing: it is the dot of the application's i.
 */
final class Desk {

    /** The warm light of the glass ground, and its one colour. */
    private static final int BONE = 0xFFF5F1E8;
    private static final int BRASS = 0xFFC9A86A;

    static final int GROUND_SEED = 0;
    static final int GROUND_GLASS = 1;
    static final int KEY_BALL = 0;
    static final int KEY_DISC = 1;
    static final int SCALE_DRUM = 0;
    static final int SCALE_THREAD = 1;

    private Desk() {
    }

    /** Draws every copy of the widget again. */
    static void push(Context c) {
        try {
            AppWidgetManager widgets = AppWidgetManager.getInstance(c);
            int[] ids = widgets.getAppWidgetIds(new ComponentName(c, Widget.class));
            if (ids.length == 0) {
                return;
            }
            Tone.read(c);
            Words.load(c);
            Now now = new Now(c);
            for (int i = 0; i < ids.length; i++) {
                widgets.updateAppWidget(ids[i], build(c, now, widgets.getAppWidgetOptions(ids[i])));
            }
        } catch (Exception broken) {
            Trace.note("desk: " + broken);
        }
    }

    /**
     * The widget drawn for the settings to show, sizes in points: with what
     * sounds now, or — when the other mode is asked for — with the book or
     * the recording that sounded last, so both faces can be seen.
     */
    static Bitmap preview(Context c, int wDp, int hDp, boolean voice) {
        float density = c.getResources().getDisplayMetrics().density;
        Now now = new Now(c);
        if (now.voice != voice) {
            now = new Now(c, voice);
        }
        return face(c, now, Math.round(wDp * density), Math.round(hDp * density), density);
    }

    /** What sounds, as the desk tells it: what, where, how far, and whether it sounds. */
    private static final class Now {
        final boolean voice;
        final Shelf.Item item;
        final boolean playing;
        final long position;
        final long duration;
        final float pace;
        final String bookTitle;
        final String saying;
        final int page;
        final int pages;
        final float voiceAlong;
        /** What the cover is asked of: the thing that sounds by its place, its address, and its file's name if known. */
        final String labelKey;
        final String labelUri;
        final String labelName;

        /** A face for the preview: the last book, or the last recording, standing still. */
        Now(Context c, boolean asVoice) {
            voice = asVoice;
            playing = true;
            if (asVoice) {
                item = null;
                position = 0L;
                duration = 0L;
                pace = Shelf.rate(c);
                String book = Keep.voiceBook(c);
                String named = Keep.voiceTitle(c);
                String file = null;
                if (named.length() == 0) {
                    java.util.ArrayList<Shelf.Item> read = Shelf.recent(c, 1);
                    named = read.isEmpty() ? Words.s("books") : Shelf.title(read.get(0).name);
                    book = read.isEmpty() ? "" : read.get(0).key();
                    file = read.isEmpty() ? null : read.get(0).name;
                }
                labelKey = book;
                labelUri = addressOf(c, book);
                labelName = file;
                bookTitle = named;
                saying = "";
                page = Math.max(1, Shelf.pageOf(c, book) + 1);
                pages = Math.max(page, Keep.voicePages(c) > 0 ? Keep.voicePages(c) : page * 3);
                voiceAlong = page / (float) pages;
            } else {
                java.util.ArrayList<Shelf.Item> last = Shelf.recentSounds(c, 1);
                item = last.isEmpty() ? null : last.get(0);
                labelKey = item == null ? "" : item.key();
                labelUri = item == null ? "" : item.uri.toString();
                labelName = item == null ? null : item.name;
                position = item == null ? 0L : Shelf.soundAt(c, item.key());
                duration = Math.max(position * 3, 30 * 60 * 1000L);
                pace = Shelf.pace(c, item);
                bookTitle = "";
                saying = "";
                page = 0;
                pages = 0;
                voiceAlong = 0f;
            }
        }

        Now(Context c) {
            Sound s = Sound.live();
            boolean asleep = s == null || (!s.voiceOn() && s.current() == null);
            if (asleep && Keep.lastVoice(c) && Keep.voiceBook(c).length() > 0) {
                /* Nothing sounds now, and the last thing that did was a book
                   read aloud: the widget shows the book where it was left. */
                voice = true;
                item = null;
                labelKey = Keep.voiceBook(c);
                labelUri = addressOf(c, labelKey);
                labelName = null;
                playing = false;
                position = 0L;
                duration = 0L;
                pace = Shelf.rate(c);
                bookTitle = Keep.voiceTitle(c);
                saying = "";
                page = Shelf.pageOf(c, Keep.voiceBook(c)) + 1;
                pages = Keep.voicePages(c);
                int length = Keep.voiceLength(c);
                int at = Shelf.voiceAt(c, Keep.voiceBook(c));
                voiceAlong = length > 0 && at > 0 ? at / (float) length : 0f;
                return;
            }
            voice = s != null && s.voiceOn();
            if (voice) {
                item = null;
                labelKey = s.voiceKey();
                labelUri = addressOf(c, labelKey);
                labelName = null;
                playing = !s.voiceHeld();
                position = 0L;
                duration = 0L;
                pace = Shelf.rate(c);
                bookTitle = s.voiceTitle();
                saying = s.voiceSaying();
                page = Shelf.pageOf(c, s.voiceKey()) + 1;
                pages = s.voicePages();
                voiceAlong = s.voiceLength() == 0 ? 0f : s.voiceChar() / (float) s.voiceLength();
                return;
            }
            bookTitle = "";
            saying = "";
            page = 0;
            pages = 0;
            voiceAlong = 0f;
            Shelf.Item it = s == null ? null : s.current();
            if (it == null && "film".equals(Keep.lastKind(c))) {
                /* The last thing that sounded was a video watched on its own
                   screen: the widget shows that, not the recording before it. */
                java.util.ArrayList<Shelf.Item> seen = Shelf.recentWatched(c, 1);
                it = seen.isEmpty() ? null : seen.get(0);
            }
            if (it == null) {
                java.util.ArrayList<Shelf.Item> last = Shelf.recentSounds(c, 1);
                it = last.isEmpty() ? null : last.get(0);
            }
            item = it;
            labelKey = it == null ? "" : it.key();
            labelUri = it == null ? "" : it.uri.toString();
            labelName = it == null ? null : it.name;
            playing = s != null && s.playing();
            boolean live = s != null && s.current() != null;
            position = live ? s.position() : (it == null ? 0L : Shelf.soundAt(c, it.key()));
            duration = live ? s.duration() : 0L;
            pace = Shelf.pace(c, live ? s.current() : it);
        }

        String title() {
            if (voice) {
                return bookTitle;
            }
            return item == null ? Words.s("music") : Shelf.label(item);
        }

        /** What the name comes from: the words being said, the chapter, or the folder. */
        String under() {
            if (voice) {
                return saying;
            }
            if (item == null) {
                return Words.s("empty_music");
            }
            if (Shelf.nameless(item)) {
                return Shelf.title(item.name);
            }
            return item.folder == null ? "" : item.folder;
        }

        /** Where it is, as the value in the light face. */
        String value() {
            return voice ? String.valueOf(page) : clock(position);
        }

        /** What that is out of, and the pace, as its caption. */
        String of() {
            String speed = String.format(java.util.Locale.ROOT, "%.1f\u00D7", pace);
            if (voice) {
                return Words.s("page_of").replace("{n}", String.valueOf(Math.max(1, pages)))
                    + "  \u00B7  " + speed;
            }
            return duration > 0 ? Words.s("time_of").replace("{t}", clock(duration))
                + "  \u00B7  " + speed : speed;
        }

        String time() {
            if (voice) {
                return Words.s("page_n").replace("{n}", String.valueOf(page))
                    + " / " + Math.max(1, pages);
            }
            return clock(position) + (duration > 0 ? " / " + clock(duration) : "");
        }

        float along() {
            if (voice) {
                return Math.max(0f, Math.min(1f, voiceAlong));
            }
            return duration > 0 ? Math.max(0f, Math.min(1f, position / (float) duration)) : 0f;
        }
    }

    // ------------------------------------------------------------ keys

    /**
     * Where the words lead: the book itself, at its page, while a book is read
     * aloud; the video itself while a video's sound goes on behind the home
     * screen; the recordings otherwise.
     */
    private static PendingIntent room(Context c, Now now) {
        Intent i = new Intent(c, Main.class);
        i.setAction(Main.ROOM_DOOR);
        boolean film = !now.voice && now.item != null && now.item.mime != null
            && now.item.mime.startsWith("video/");
        i.putExtra("room", now.voice ? 3 : (film ? 1 : 2));
        if (film) {
            i.putExtra("film", now.item.key());
        }
        if (now.voice) {
            /* The book read aloud itself, not the shelf it stands on. */
            i.putExtra("book", Keep.voiceBook(c));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, now.voice ? 91 : (film ? 92 : 90), i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteViews build(Context c, Now now, Bundle options) {
        float density = c.getResources().getDisplayMetrics().density;
        int wDp = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH);
        int hDp = options == null ? 0 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT);
        if (wDp <= 0) {
            wDp = 320;
        }
        if (hDp <= 0) {
            hDp = 150;
        }
        /* Drawn at the widget's own size, within a ceiling a home screen will
           carry across the process line. */
        float scale = Math.min(density, 1000f / wDp);
        int w = Math.round(wDp * scale);
        int h = Math.round(hDp * scale);
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.desk);
        v.setImageViewBitmap(R.id.w_face, face(c, now, w, h, scale));
        v.setOnClickPendingIntent(R.id.w_open, room(c, now));
        v.setOnClickPendingIntent(R.id.w_ball, Widget.key(c, Sound.TOGGLE));
        v.setOnClickPendingIntent(R.id.w_back, Widget.key(c, Sound.BACK));
        v.setOnClickPendingIntent(R.id.w_fwd, Widget.key(c, Sound.ON));
        return v;
    }

    // ------------------------------------------------------------ the picture

    /** The colours of one ground: the card, its edge, the words, the accent, the discs. */
    static final class Ink {
        int card;
        int edge;
        int text;
        int soft;
        int accent;
        int disc;
        int discEdge;
        int discInk;
        int key;
        int keyInk;
        /** The two inks a cover is printed in, the label in the middle, and the label's mark. */
        int printDark;
        int printLight;
        int hub;
        int hubEdge;
        int hubInk;

        Ink(int ground, int glass) {
            if (ground == GROUND_GLASS) {
                card = (glass << 24) | 0x0E1014;
                edge = 0x3DFFFFFF;
                text = BONE;
                soft = 0xB3F5F1E8;
                accent = BRASS;
                disc = 0x140E1014;
                discEdge = 0x3DFFFFFF;
                discInk = BONE;
                key = 0x140E1014;
                keyInk = BONE;
                printDark = 0xFF101216;
                printLight = 0xFFDCD3C1;
                hub = 0x730E1014;
                hubEdge = 0x3DFFFFFF;
                hubInk = BONE;
            } else {
                card = Tone.of(Tone.SURFACE_CONTAINER);
                edge = 0;
                text = Tone.of(Tone.ON_SURFACE);
                soft = Tone.of(Tone.ON_SURFACE_VARIANT);
                accent = Tone.of(Tone.PRIMARY);
                disc = Tone.of(Tone.SURFACE_HIGHEST);
                discEdge = 0;
                discInk = Tone.of(Tone.PRIMARY);
                key = Tone.of(Tone.PRIMARY_CONTAINER);
                keyInk = Tone.of(Tone.ON_PRIMARY_CONTAINER);
                /* The container and the ink on it, the darker taking the
                   shadows, whichever way round the scheme has them. */
                boolean inkLighter = light(keyInk) > light(key);
                printDark = inkLighter ? key : keyInk;
                printLight = inkLighter ? keyInk : key;
                hub = (key & 0x00FFFFFF) | 0x8C000000;
                hubEdge = (keyInk & 0x00FFFFFF) | 0x55000000;
                hubInk = keyInk;
            }
        }
    }

    /**
     * A wallpaper grown from the seed, for the settings to lay the widget
     * on: a slope between two containers and two soft blooms of the accents,
     * so glass shows as glass and not as a grey card.
     */
    static void wall(Canvas canvas, RectF r, float radius) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new android.graphics.LinearGradient(r.left, r.top, r.right, r.bottom,
            new int[] {Tone.of(Tone.TERTIARY_CONTAINER), Tone.of(Tone.PRIMARY_CONTAINER),
                Tone.of(Tone.SECONDARY_CONTAINER)}, null, Shader.TileMode.CLAMP));
        canvas.save();
        android.graphics.Path round = new android.graphics.Path();
        round.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
        canvas.clipPath(round);
        canvas.drawRect(r, p);
        float big = Math.max(r.width(), r.height());
        p.setShader(new RadialGradient(r.left + r.width() * 0.22f, r.top + r.height() * 0.28f,
            big * 0.45f, new int[] {(Tone.of(Tone.PRIMARY) & 0xFFFFFF) | 0x80000000, 0x00000000},
            null, Shader.TileMode.CLAMP));
        canvas.drawRect(r, p);
        p.setShader(new RadialGradient(r.left + r.width() * 0.82f, r.top + r.height() * 0.78f,
            big * 0.4f, new int[] {(Tone.of(Tone.TERTIARY) & 0xFFFFFF) | 0x70000000, 0x00000000},
            null, Shader.TileMode.CLAMP));
        canvas.drawRect(r, p);
        canvas.restore();
    }

    /** The card of a ground: its fill, and its hairline if it has one. */
    static void card(Canvas canvas, RectF r, float radius, Ink ink, float u) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(ink.card);
        canvas.drawRoundRect(r, radius, radius, p);
        if (ink.edge != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, u));
            p.setColor(ink.edge);
            canvas.drawRoundRect(r, radius, radius, p);
        }
    }

    /** The gold ball of the icon: a lit sphere with its highlight and its shadow. */
    static void ball(Canvas canvas, float cx, float cy, float r, float u) {
        Paint ball = new Paint(Paint.ANTI_ALIAS_FLAG);
        ball.setShadowLayer(8 * u, 3 * u, 4 * u, 0x70000000);
        ball.setColor(0xFFB8862E);
        canvas.drawCircle(cx, cy, r, ball);
        ball.clearShadowLayer();
        ball.setShader(new RadialGradient(cx - r * 0.35f, cy - r * 0.4f, r * 1.45f,
            new int[] {0xFFFFF1C2, 0xFFF4C04A, 0xFFB57A26, 0xFF7A4B16},
            new float[] {0f, 0.28f, 0.72f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, ball);
        ball.setShader(new RadialGradient(cx - r * 0.38f, cy - r * 0.42f, r * 0.35f,
            new int[] {0xCCFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r, ball);
    }

    /** A disc of the ground's material, with its hairline if the ground has one. */
    static void disc(Canvas canvas, float x, float y, float r, int fill, int edge, float u) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(fill);
        canvas.drawCircle(x, y, r, p);
        if (edge != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, u));
            p.setColor(edge);
            canvas.drawCircle(x, y, r, p);
        }
    }

    /** A mark drawn at a size, in one ink. */
    static void mark(Canvas canvas, int kind, float cx, float cy, float span, int ink) {
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = span / 24f;
        Glyph.pens(pen, solid, unit);
        pen.setColor(ink);
        solid.setColor(ink);
        Glyph.mark(canvas, kind, cx, cy, unit, pen, solid, cut);
    }

    /** The whole as a drum of ticks, the part heard in the accent. */
    static void drum(Canvas canvas, float x0, float x1, float mid, float u, float along,
                     int accent, int text) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int n = 34;
        float sag = 8 * u;
        double reach = Math.toRadians(55.0);
        for (int i = 0; i < n; i++) {
            float at = i / (float) (n - 1);
            double th = (at - 0.5) * 2 * reach;
            float cos = (float) ((Math.cos(th) - Math.cos(reach)) / (1 - Math.cos(reach)));
            float x = x0 + (x1 - x0) * (float) (0.5 + 0.5 * Math.sin(th) / Math.sin(reach));
            float y = mid + sag * (1 - cos);
            boolean here = Math.abs(at - along) <= 0.5f / (n - 1);
            float tall = (i % 8 == 0 ? 20 : 13) * u * (0.55f + 0.45f * cos);
            float wide = 2 * u;
            if (here) {
                tall = 26 * u;
                wide = 4.5f * u;
            }
            boolean heard = at <= along;
            p.setColor(heard || here ? accent : text);
            float alpha = here ? 1f : (heard ? 0.95f : 0.35f) * (0.35f + 0.65f * cos);
            p.setAlpha(Math.round(255 * alpha));
            canvas.drawRoundRect(new RectF(x - wide / 2, y - tall / 2, x + wide / 2, y + tall / 2),
                wide / 2, wide / 2, p);
        }
    }

    /** One thread for the whole, the part heard in the accent, a bead where it is. */
    static void thread(Canvas canvas, float x0, float x1, float mid, float u, float along,
                       int accent, int text) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(text);
        p.setAlpha(64);
        canvas.drawRoundRect(new RectF(x0, mid - 1 * u, x1, mid + 1 * u), u, u, p);
        p.setColor(accent);
        float at = x0 + (x1 - x0) * along;
        canvas.drawRoundRect(new RectF(x0, mid - 1.5f * u, Math.max(x0 + 3 * u, at), mid + 1.5f * u),
            1.5f * u, 1.5f * u, p);
        canvas.drawCircle(at, mid, 5 * u, p);
    }

    private static Bitmap face(Context c, Now now, int w, int h, float u) {
        Ink ink = new Ink(Keep.widgetGround(c), Keep.glass(c));
        Bitmap made = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        card(canvas, new RectF(0.5f * u, 0.5f * u, w - 0.5f * u, h - 0.5f * u), 28 * u, ink, u);

        float pad = 18 * u;
        float split = w * 0.62f;

        /* The room: a breath while it sounds, its numeral, its name. */
        float[] heights = {0.45f, 0.8f, 1f, 0.7f, 0.5f};
        p.setColor(ink.accent);
        for (int i = 0; i < heights.length; i++) {
            float bh = 12 * u * (now.playing ? heights[i] : 0.3f);
            float x = pad + i * 5.2f * u;
            float y = pad + 8 * u;
            canvas.drawRoundRect(new RectF(x, y - bh / 2, x + 2.6f * u, y + bh / 2),
                1.3f * u, 1.3f * u, p);
        }
        TextPaint numeral = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        numeral.setTypeface(Typeface.SERIF);
        numeral.setTextSize(13 * u);
        numeral.setColor(ink.accent);
        numeral.setLetterSpacing(0.15f);
        boolean film = !now.voice && now.item != null && now.item.mime != null
            && now.item.mime.startsWith("video/");
        canvas.drawText(now.voice ? "IV" : (film ? "II" : "III"), pad + 32 * u, pad + 12.5f * u, numeral);
        TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        small.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        small.setTextSize(12 * u);
        small.setColor(ink.soft);
        canvas.drawText(Words.s(now.voice ? "books" : (film ? "films" : "music")), pad + 58 * u, pad + 12.5f * u,
            small);

        /* The name, two lines at most, in the book face. */
        TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        title.setTypeface(Typeface.SERIF);
        title.setTextSize(20 * u);
        title.setColor(ink.text);
        int room = Math.round(split - pad - 8 * u);
        String named = now.title();
        StaticLayout lines = StaticLayout.Builder.obtain(named, 0, named.length(), title,
                Math.max(1, room))
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setLineSpacing(0f, 1.05f)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .build();
        canvas.save();
        canvas.translate(pad, pad + 24 * u);
        lines.draw(canvas);
        canvas.restore();

        float mid = h - pad - 30 * u;
        if (Keep.widgetScale(c) == SCALE_THREAD) {
            thread(canvas, pad, split - 10 * u, mid, u, now.along(), ink.accent, ink.text);
        } else {
            drum(canvas, pad, split - 10 * u, mid, u, now.along(), ink.accent, ink.text);
        }
        String line = !now.voice && now.item == null ? Words.s("empty_music") : now.time()
            + (now.voice || now.duration > 0 ? "  \u00B7  "
                + String.format(java.util.Locale.ROOT, "%.1f\u00D7", now.pace) : "");
        canvas.drawText(line, pad, h - pad, small);

        /* The key that holds the sound and lets it go on. */
        float right = w - split;
        float r = Math.min(right * 0.34f, h * 0.24f);
        float cx = split + right / 2f;
        float cy = pad + r + 2 * u;
        int markInk;
        float markSpan = r * 0.95f;
        if (Keep.widgetKey(c) == KEY_BALL) {
            ball(canvas, cx, cy, r, u);
            markInk = 0xFF5A3A12;
        } else {
            Bitmap art = label(c, now.labelKey, now.labelUri, now.labelName);
            if (art != null) {
                markSpan = record(canvas, cx, cy, r, art, ink, u) * 1.25f;
                markInk = ink.hubInk;
            } else {
                disc(canvas, cx, cy, r, ink.key, ink.discEdge, u);
                markInk = ink.keyInk;
            }
        }
        mark(canvas, now.playing ? Glyph.PAUSE : Glyph.PLAY, cx, cy, markSpan, markInk);

        /* Back fifteen and on thirty, two small discs under the key. */
        float sr = Math.min(right * 0.2f, h * 0.13f);
        float sy = h - pad - sr;
        float[] xs = {cx - sr * 1.3f, cx + sr * 1.3f};
        int[] kinds = {Glyph.REWIND, Glyph.SKIP};
        for (int i = 0; i < 2; i++) {
            disc(canvas, xs[i], sy, sr, ink.disc, ink.discEdge, u);
            mark(canvas, kinds[i], xs[i], sy, sr * 1.05f, ink.discInk);
        }
        return made;
    }

    /** How light a colour looks, from 0 to 1. */
    static float light(int colour) {
        return (0.2126f * ((colour >> 16) & 255) + 0.7152f * ((colour >> 8) & 255)
            + 0.0722f * (colour & 255)) / 255f;
    }

    /**
     * The disc as a record: the cover over the whole of it, printed in the
     * ground's two inks — the shadows in the darker, the lights in the
     * lighter — with a touch more contrast than the picture had, so its
     * shapes still read at this size; and in the middle the record's label,
     * a small disc of the ground made half clear, so the face a cover keeps
     * in its middle shows through it under the mark. Gives back the label's
     * radius.
     */
    static float record(Canvas canvas, float cx, float cy, float r, Bitmap art, Ink ink, float u) {
        int side = Math.min(art.getWidth(), art.getHeight());
        float left = (art.getWidth() - side) / 2f;
        float top = (art.getHeight() - side) / 2f;
        BitmapShader picture = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix fit = new Matrix();
        float k = 2f * r / side;
        fit.setScale(k, k);
        fit.postTranslate(cx - r - left * k, cy - r - top * k);
        picture.setLocalMatrix(fit);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(picture);
        p.setColorFilter(print(ink.printDark, ink.printLight, 1.15f));
        canvas.drawCircle(cx, cy, r, p);

        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, u));
        if (ink.discEdge != 0) {
            line.setColor(ink.discEdge);
            canvas.drawCircle(cx, cy, r, line);
        }
        float hub = r * 0.36f;
        Paint middle = new Paint(Paint.ANTI_ALIAS_FLAG);
        middle.setColor(ink.hub);
        canvas.drawCircle(cx, cy, hub, middle);
        line.setColor(ink.hubEdge);
        canvas.drawCircle(cx, cy, hub, line);
        return hub;
    }

    /**
     * A picture in two inks: its lightness, stretched a little about the
     * middle, laid from the dark ink to the light one. One matrix, so it
     * costs nothing to draw.
     */
    static ColorMatrixColorFilter print(int dark, int bright, float contrast) {
        float[] wr = {0.2126f, 0.7152f, 0.0722f};
        float shift = 127.5f * (1f - contrast);
        float[] m = new float[20];
        int[] d = {(dark >> 16) & 255, (dark >> 8) & 255, dark & 255};
        int[] b = {(bright >> 16) & 255, (bright >> 8) & 255, bright & 255};
        for (int ch = 0; ch < 3; ch++) {
            float span = (b[ch] - d[ch]) / 255f;
            for (int i = 0; i < 3; i++) {
                m[ch * 5 + i] = span * contrast * wr[i];
            }
            m[ch * 5 + 4] = d[ch] + span * shift;
        }
        m[18] = 1f;
        return new ColorMatrixColorFilter(new ColorMatrix(m));
    }

    // ------------------------------------------------------------ the covers

    /**
     * The covers the record wears, one for each thing that has sounded,
     * fetched away from the screen and kept — a few in memory, more on disk —
     * because the widget is drawn every few seconds and must open no file to
     * be drawn. A thing with no picture is remembered as having none. When a
     * cover arrives the widget is drawn again, and so is whatever window is
     * showing it.
     */
    private static final int LABEL_PX = 320;
    private static final int LABELS_KEPT = 48;
    private static final LruCache<String, Bitmap> LABELS = new LruCache<String, Bitmap>(8);
    private static final java.util.HashSet<String> BARE = new java.util.HashSet<String>();
    private static final java.util.HashSet<String> ASKED = new java.util.HashSet<String>();
    private static Runnable labelled;

    /** Who else draws the widget and wants to know when a cover has come: the window. */
    static void onLabel(Runnable r) {
        labelled = r;
    }

    /** Where a book the voice read can be opened, looked up by its place among what was read. */
    private static String addressOf(Context c, String key) {
        if (key == null || key.length() == 0) {
            return "";
        }
        java.util.ArrayList<Shelf.Item> read = Shelf.recent(c, 30);
        for (int i = 0; i < read.size(); i++) {
            if (read.get(i).key().equals(key)) {
                return read.get(i).uri.toString();
            }
        }
        return key.startsWith("content://") ? key : "";
    }

    /** The cover of a thing, known by its place, if it is known; otherwise it is asked for at its address, and nothing for now. */
    static Bitmap label(Context c, final String key, final String address, final String name) {
        if (key == null || key.length() == 0 || address == null || address.length() == 0) {
            return null;
        }
        synchronized (LABELS) {
            Bitmap known = LABELS.get(key);
            if (known != null) {
                return known;
            }
            if (BARE.contains(key) || ASKED.contains(key)) {
                return null;
            }
            ASKED.add(key);
        }
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                Bitmap got = fetch(app, key, address, name);
                synchronized (LABELS) {
                    ASKED.remove(key);
                    if (got != null) {
                        LABELS.put(key, got);
                    } else {
                        BARE.add(key);
                    }
                }
                if (got == null) {
                    return;
                }
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        push(app);
                        Runnable told = labelled;
                        if (told != null) {
                            told.run();
                        }
                    }
                });
            }
        }).start();
        return null;
    }

    /** From the disk if it was fetched before, else from the file itself, square and small. */
    private static Bitmap fetch(Context c, String key, String address, String name) {
        java.io.File shelf = new java.io.File(c.getCacheDir(), "labels");
        String stem = Long.toHexString(key.hashCode() & 0xFFFFFFFFL) + "-" + key.length();
        java.io.File kept = new java.io.File(shelf, stem + ".jpg");
        java.io.File none = new java.io.File(shelf, stem + ".none");
        if (kept.exists()) {
            Bitmap b = BitmapFactory.decodeFile(kept.getPath());
            if (b != null) {
                return b;
            }
        }
        if (none.exists()) {
            return null;
        }
        Bitmap made = null;
        try {
            Uri uri = Uri.parse(address);
            String file = name != null && name.length() > 0 ? name : nameOf(c, uri);
            Bitmap whole = Thumb.make(c, new Shelf.Item(file, uri, null, 0L, 0L, ""), LABEL_PX);
            if (whole != null) {
                made = square(whole, LABEL_PX);
            }
        } catch (Throwable broken) {
            Trace.note("desk: no cover: " + broken.getClass().getSimpleName());
        }
        try {
            if (!shelf.isDirectory()) {
                shelf.mkdirs();
            }
            tidy(shelf);
            if (made != null) {
                java.io.FileOutputStream out = new java.io.FileOutputStream(kept);
                try {
                    made.compress(Bitmap.CompressFormat.JPEG, 90, out);
                } finally {
                    out.close();
                }
            } else {
                none.createNewFile();
            }
        } catch (Exception unwritten) {
            Trace.note("desk: cover not kept: " + unwritten.getClass().getSimpleName());
        }
        return made;
    }

    /** The middle square of a picture, at a size. */
    private static Bitmap square(Bitmap whole, int px) {
        int side = Math.min(whole.getWidth(), whole.getHeight());
        int left = (whole.getWidth() - side) / 2;
        int top = (whole.getHeight() - side) / 2;
        Bitmap cut = Bitmap.createBitmap(whole, left, top, side, side);
        if (side == px) {
            return cut;
        }
        return Bitmap.createScaledBitmap(cut, px, px, true);
    }

    /** A file's name, asked of whoever keeps it, for knowing what kind of thing it is. */
    private static String nameOf(Context c, Uri uri) {
        android.database.Cursor q = null;
        try {
            q = c.getContentResolver().query(uri,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst() && q.getString(0) != null) {
                return q.getString(0);
            }
        } catch (Exception unasked) {
            // the name falls back to the end of the address
        } finally {
            if (q != null) {
                q.close();
            }
        }
        String last = uri.getLastPathSegment();
        return last == null ? "" : last;
    }

    /** The disk keeps the latest few dozen covers; the oldest go first. */
    private static void tidy(java.io.File shelf) {
        java.io.File[] all = shelf.listFiles();
        if (all == null || all.length < LABELS_KEPT) {
            return;
        }
        java.util.Arrays.sort(all, new java.util.Comparator<java.io.File>() {
            public int compare(java.io.File a, java.io.File b) {
                return Long.compare(a.lastModified(), b.lastModified());
            }
        });
        for (int i = 0; i <= all.length - LABELS_KEPT; i++) {
            all[i].delete();
        }
    }

    private static String clock(long ms) {
        long all = Math.max(0L, ms / 1000L);
        long hh = all / 3600L;
        long m = (all % 3600L) / 60L;
        long s = all % 60L;
        String ss = (s < 10 ? "0" : "") + s;
        return hh > 0 ? hh + ":" + (m < 10 ? "0" : "") + m + ":" + ss : m + ":" + ss;
    }
}
