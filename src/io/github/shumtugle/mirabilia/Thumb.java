package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Previews, and why they are harder than they look: the lists are not
 * recycling ones, so a shelf of two thousand files builds two thousand rows
 * at once, and asking for two thousand pictures would freeze the phone and
 * then run it out of memory.
 *
 * So nothing is decoded until its row is on screen, three threads do the
 * work, and whatever is decoded is kept in a cache sized against the heap
 * rather than against optimism.
 *
 * Where a picture comes from: the storage provider is asked first, since it
 * often has one made already and hands it over without opening the file.
 * Otherwise the file itself is opened — the picture inside a recording, the
 * first page of a pdf, the first leaf of a comic, the cover of an epub or of
 * an fb2 — and a file with no picture keeps its letter.
 */
final class Thumb {

    private static final ExecutorService POOL = Executors.newFixedThreadPool(3);

    private static final LruCache<String, Bitmap> CACHE =
        new LruCache<String, Bitmap>((int) (Runtime.getRuntime().maxMemory() / 8192)) {
            @Override
            protected int sizeOf(String key, Bitmap b) {
                return Math.max(1, b.getByteCount() / 1024);
            }
        };

    /** A file with no picture is remembered as such, so it is not opened again. */
    private static final java.util.HashSet<String> NONE = new java.util.HashSet<String>();

    private static final class Slot {
        ImageView view;
        Shelf.Item item;
        boolean asked;
    }

    private static final ArrayList<Slot> SLOTS = new ArrayList<Slot>();

    private Thumb() {
    }

    /** A new scene: the rows of the last one are forgotten. */
    static void reset() {
        SLOTS.clear();
    }

    /** A picture wanted for a row, to be fetched when the row is seen. */
    static void watch(ImageView view, Shelf.Item it) {
        Slot s = new Slot();
        s.view = view;
        s.item = it;
        String key = key(it);
        Bitmap ready = CACHE.get(key);
        if (ready != null) {
            show(view, ready, false);
            s.asked = true;
        } else if (NONE.contains(key)) {
            s.asked = true;
        }
        SLOTS.add(s);
    }

    /** The picture already made for a row, if there is one; nothing is opened to answer. */
    static Bitmap known(Shelf.Item it) {
        return CACHE.get(key(it));
    }

    private static String key(Shelf.Item it) {
        return it.uri.toString();
    }

    /** Called when the rows move: only what the eye can see is fetched. */
    static void sweep(Context c) {
        final Context app = c.getApplicationContext();
        android.graphics.Rect box = new android.graphics.Rect();
        boolean seen = false;
        /* A room whose rows are handed out as the eye reaches them takes its
           rows back as well: a row no longer in the window is let go here,
           and what is left is few enough to look through whole. */
        for (int i = SLOTS.size() - 1; i >= 0; i--) {
            Slot s = SLOTS.get(i);
            if (s.view == null || (!s.view.isAttachedToWindow() && s.view.getParent() == null)) {
                SLOTS.remove(i);
            }
        }
        boolean few = SLOTS.size() <= 64;
        for (int i = 0; i < SLOTS.size(); i++) {
            final Slot s = SLOTS.get(i);
            boolean visible = s.view != null && s.view.isAttachedToWindow()
                && s.view.getGlobalVisibleRect(box);
            if (!visible) {
                if (seen && !few) {
                    /* Rows built all at once stand in order: past the last one seen, none is. */
                    break;
                }
                continue;
            }
            seen = true;
            if (s.asked) {
                continue;
            }
            s.asked = true;
            final int px = Math.max(Round.dp(96), Math.max(s.view.getWidth(), s.view.getHeight()));
            POOL.execute(new Runnable() {
                public void run() {
                    final Bitmap b = make(app, s.item, px);
                    if (b == null) {
                        NONE.add(key(s.item));
                        return;
                    }
                    CACHE.put(key(s.item), b);
                    s.view.post(new Runnable() {
                        public void run() {
                            show(s.view, b, true);
                        }
                    });
                }
            });
        }
    }

    private static void show(ImageView v, Bitmap b, boolean fade) {
        v.setImageBitmap(b);
        if (fade) {
            v.setAlpha(0f);
            v.animate().alpha(1f).setDuration(Pace.GROW).setInterpolator(Pace.STANDARD).start();
        } else {
            v.setAlpha(1f);
        }
    }

    // ------------------------------------------------------------ making

    /** A picture of a thing, at about a size, or none; also asked by the widget for its record. */
    static Bitmap make(Context c, Shelf.Item it, int px) {
        if ("content".equals(it.uri.getScheme())
            && DocumentsContract.isDocumentUri(c, it.uri)) {
            try {
                Bitmap b = DocumentsContract.getDocumentThumbnail(c.getContentResolver(), it.uri,
                    new Point(px, px), null);
                if (b != null) {
                    return b;
                }
            } catch (Throwable ignored) {
                // a provider with no picture to hand over is asked no further
            }
        }
        String n = it.name == null ? "" : it.name.toLowerCase(Locale.ROOT);
        try {
            if (Shelf.isSound(n)) {
                return inside(c, it.uri, px);
            }
            if (n.endsWith(".pdf")) {
                return firstPage(c, it.uri, px);
            }
            if (n.endsWith(".cbz") || n.endsWith(".epub")) {
                return fromZip(c, it.uri, px, n.endsWith(".epub"));
            }
            if (n.endsWith(".fb2")) {
                return fb2(c, it.uri, px);
            }
        } catch (Throwable broken) {
            Trace.note("thumb: " + broken.getClass().getSimpleName());
        }
        return null;
    }

    /** The picture a recording carries in its tags. */
    private static Bitmap inside(Context c, Uri uri, int px) {
        android.media.MediaMetadataRetriever tags = new android.media.MediaMetadataRetriever();
        try {
            tags.setDataSource(c, uri);
            byte[] art = tags.getEmbeddedPicture();
            return art == null ? null : decode(art, px);
        } finally {
            try {
                tags.release();
            } catch (Exception ignored) {
                // the reader lets go either way
            }
        }
    }

    /** The first page of a pdf, drawn on white. */
    private static Bitmap firstPage(Context c, Uri uri, int px) throws Exception {
        android.os.ParcelFileDescriptor fd = c.getContentResolver().openFileDescriptor(uri, "r");
        if (fd == null) {
            return null;
        }
        android.graphics.pdf.PdfRenderer doc = new android.graphics.pdf.PdfRenderer(fd);
        try {
            if (doc.getPageCount() == 0) {
                return null;
            }
            android.graphics.pdf.PdfRenderer.Page page = doc.openPage(0);
            int w = px;
            int h = Math.max(1, Math.round(px * page.getHeight() / (float) page.getWidth()));
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            b.eraseColor(0xFFFFFFFF);
            page.render(b, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            return b;
        } finally {
            doc.close();
            fd.close();
        }
    }

    /**
     * A picture out of a zip: for a comic, its first leaf by name; for an
     * epub, the image whose name says cover, or failing that the first
     * sizeable picture in it.
     */
    private static Bitmap fromZip(Context c, Uri uri, int px, boolean book) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            return null;
        }
        ZipInputStream zip = new ZipInputStream(in);
        String bestName = null;
        byte[] best = null;
        byte[] firstBig = null;
        try {
            ZipEntry e;
            int seen = 0;
            while ((e = zip.getNextEntry()) != null && seen < 400) {
                seen++;
                String name = e.getName().toLowerCase(Locale.ROOT);
                if (e.isDirectory() || !(name.endsWith(".jpg") || name.endsWith(".jpeg")
                    || name.endsWith(".png") || name.endsWith(".webp"))) {
                    continue;
                }
                if (book) {
                    boolean cover = name.contains("cover");
                    if (cover || firstBig == null) {
                        byte[] bytes = read(zip, 8 * 1024 * 1024);
                        if (cover) {
                            best = bytes;
                            break;
                        }
                        if (bytes.length > 20 * 1024) {
                            firstBig = bytes;
                        }
                    }
                } else if (bestName == null || name.compareTo(bestName) < 0) {
                    bestName = name;
                    best = read(zip, 8 * 1024 * 1024);
                }
            }
        } finally {
            zip.close();
        }
        byte[] chosen = best != null ? best : firstBig;
        return chosen == null ? null : decode(chosen, px);
    }

    /**
     * An fb2 keeps its cover as text inside itself: the cover page points
     * at a binary by its id, and the binary is the picture in base64.
     */
    private static Bitmap fb2(Context c, Uri uri, int px) throws Exception {
        InputStream in = c.getContentResolver().openInputStream(uri);
        if (in == null) {
            return null;
        }
        byte[] head;
        try {
            head = read(in, 6 * 1024 * 1024);
        } finally {
            in.close();
        }
        String text = new String(head, "ISO-8859-1");
        int cover = text.indexOf("coverpage");
        if (cover < 0) {
            return null;
        }
        java.util.regex.Matcher link = java.util.regex.Pattern
            .compile("href=\"#([^\"]+)\"").matcher(text);
        if (!link.find(cover)) {
            return null;
        }
        String id = link.group(1);
        java.util.regex.Matcher bin = java.util.regex.Pattern
            .compile("<binary[^>]*id=\"" + java.util.regex.Pattern.quote(id) + "\"[^>]*>")
            .matcher(text);
        if (!bin.find()) {
            return null;
        }
        int from = bin.end();
        int to = text.indexOf("</binary>", from);
        if (to < 0) {
            return null;
        }
        byte[] picture = android.util.Base64.decode(text.substring(from, to),
            android.util.Base64.DEFAULT);
        return decode(picture, px);
    }

    private static byte[] read(InputStream in, int most) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[32768];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() < most) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    /** A picture decoded no larger than it will be shown. */
    private static Bitmap decode(byte[] bytes, int px) {
        BitmapFactory.Options look = new BitmapFactory.Options();
        look.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, look);
        int sample = 1;
        while (Math.min(look.outWidth, look.outHeight) / (sample * 2) >= px) {
            sample *= 2;
        }
        BitmapFactory.Options take = new BitmapFactory.Options();
        take.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, take);
    }
}
