package io.github.shumtugle.mirabilia;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The rooms of pictures and of films: what the phone's own index knows, by
 * the day each was taken, newest first, and gathered into the albums the
 * phone keeps them in — the camera's, the screenshots', a messenger's.
 *
 * Nothing is walked for it: the phone has already listed and dated every
 * picture and made a small one of each, so the room asks the index and
 * takes its small pictures, and decodes a picture in full only when it is
 * opened. The small ones are kept in memory while they fit, a few at a time
 * fetched away from the screen; a cell that has scrolled away before its
 * picture came is not given the wrong one.
 */
final class Gallery {

    private Gallery() {
    }

    /**
     * One picture, or one film: where it is, what it is called, the album it
     * is in, when it was taken, how it lies, and for a film how long it runs.
     */
    static final class Photo {
        final long id;
        final boolean video;
        final long duration;
        final Uri uri;
        final String name;
        final String mime;
        final String album;
        final String albumId;
        final long taken;
        final int turn;
        final String place;

        Photo(long id, Uri uri, String name, String mime, String album, String albumId,
              long taken, int turn, String place) {
            this(id, uri, name, mime, album, albumId, taken, turn, place, false, 0L);
        }

        Photo(long id, Uri uri, String name, String mime, String album, String albumId,
              long taken, int turn, String place, boolean video, long duration) {
            this.id = id;
            this.video = video;
            this.duration = duration;
            this.uri = uri;
            this.name = name == null ? "" : name;
            this.mime = mime;
            this.album = album == null ? "" : album;
            this.albumId = albumId == null ? "" : albumId;
            this.taken = taken;
            this.turn = turn;
            this.place = place == null ? "" : place;
        }

        /** The picture as a file on the shelf, for handing to another application. */
        Shelf.Item item() {
            return new Shelf.Item(name, uri, mime, 0L, taken, album, place);
        }
    }

    /** An album: the pictures of one folder the phone keeps them in, newest first. */
    static final class Album {
        final String id;
        final String name;
        final ArrayList<Photo> photos = new ArrayList<Photo>();

        Album(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    // ------------------------------------------------------------ leave

    /** The permission the index of pictures asks for, by the platform's age. */
    static String seeing() {
        return android.os.Build.VERSION.SDK_INT >= 33
            ? "android.permission.READ_MEDIA_IMAGES"
            : "android.permission.READ_EXTERNAL_STORAGE";
    }

    static boolean maySee(Context c) {
        return Shelf.allFiles() || c.checkSelfPermission(seeing())
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /** The permission the index of films asks for. */
    static String watching() {
        return android.os.Build.VERSION.SDK_INT >= 33
            ? "android.permission.READ_MEDIA_VIDEO"
            : "android.permission.READ_EXTERNAL_STORAGE";
    }

    static boolean mayWatch(Context c) {
        return Shelf.allFiles() || c.checkSelfPermission(watching())
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Every film the index knows, newest first, less what lies in the
     * applications' own folders — a messenger's cache of clips is not a
     * collection. Films run long, so each carries its length.
     */
    static ArrayList<Photo> films(Context c) {
        ArrayList<Photo> out = new ArrayList<Photo>();
        Uri table = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.DATA,
        };
        Cursor q = null;
        try {
            q = c.getContentResolver().query(table, columns, null, null,
                MediaStore.Video.Media.DATE_ADDED + " DESC");
            if (q == null) {
                return out;
            }
            while (q.moveToNext()) {
                String path = q.isNull(8) ? "" : q.getString(8);
                if (path.contains("/Android/")) {
                    continue;
                }
                long id = q.getLong(0);
                long taken = q.isNull(5) ? 0L : q.getLong(5);
                if (taken <= 0L) {
                    taken = q.getLong(6) * 1000L;
                }
                out.add(new Photo(id, ContentUris.withAppendedId(table, id), q.getString(1),
                    q.getString(2), q.getString(3), q.getString(4), taken, 0, Shelf.fromPath(path),
                    true, q.isNull(7) ? 0L : q.getLong(7)));
            }
        } catch (Exception e) {
            Trace.note("films: index: " + e.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Photo>() {
            public int compare(Photo a, Photo b) {
                return Long.compare(b.taken, a.taken);
            }
        });
        Trace.note("films: " + out.size());
        return out;
    }

    // ------------------------------------------------------------ the index

    /** Every picture the index knows, newest first by the day it was taken, or came when that is not known. */
    static ArrayList<Photo> load(Context c) {
        ArrayList<Photo> out = new ArrayList<Photo>();
        Uri table = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] columns = {
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.DATA,
        };
        Cursor q = null;
        try {
            q = c.getContentResolver().query(table, columns, null, null,
                MediaStore.Images.Media.DATE_ADDED + " DESC");
            if (q == null) {
                return out;
            }
            while (q.moveToNext()) {
                long id = q.getLong(0);
                long taken = q.isNull(5) ? 0L : q.getLong(5);
                if (taken <= 0L) {
                    taken = q.getLong(6) * 1000L;
                }
                String path = q.isNull(8) ? "" : q.getString(8);
                out.add(new Photo(id, ContentUris.withAppendedId(table, id), q.getString(1),
                    q.getString(2), q.getString(3), q.getString(4), taken,
                    q.isNull(7) ? 0 : q.getInt(7), Shelf.fromPath(path)));
            }
        } catch (Exception e) {
            Trace.note("pictures: index: " + e.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        java.util.Collections.sort(out, new java.util.Comparator<Photo>() {
            public int compare(Photo a, Photo b) {
                return Long.compare(b.taken, a.taken);
            }
        });
        Trace.note("pictures: " + out.size());
        return out;
    }

    /** The albums, each newest first, and the albums themselves by their newest picture. */
    static ArrayList<Album> albums(ArrayList<Photo> all) {
        HashMap<String, Album> by = new HashMap<String, Album>();
        ArrayList<Album> out = new ArrayList<Album>();
        for (int i = 0; i < all.size(); i++) {
            Photo p = all.get(i);
            Album a = by.get(p.albumId);
            if (a == null) {
                a = new Album(p.albumId, p.album.length() > 0 ? p.album : "\u00B7");
                by.put(p.albumId, a);
                out.add(a);
            }
            a.photos.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------ small pictures

    private static final LruCache<Long, Bitmap> SMALL = new LruCache<Long, Bitmap>(
        (int) Math.min(64L * 1024L, Runtime.getRuntime().maxMemory() / 1024L / 7L)) {
        @Override
        protected int sizeOf(Long key, Bitmap b) {
            return Math.max(1, b.getByteCount() / 1024);
        }
    };
    /**
     * The face of an album is kept under the album, not under the picture it
     * was made from. An album's newest picture changes whenever one is taken,
     * and the room would then come back with letters where faces stood a
     * moment ago; with the face kept under the album, the old one stands
     * until the new one has been made. They are kept apart from every other
     * picture, so that walking a grid of three thousand cannot push them out.
     */
    private static final LruCache<String, Bitmap> FACES = new LruCache<String, Bitmap>(192);
    /** A face is kept small — a row shows it no larger — so that hundreds of albums cost little. */
    private static final int FACE_PX = 128;

    private static void keepFace(String key, Bitmap b) {
        if (key == null || key.length() == 0 || b == null) {
            return;
        }
        try {
            FACES.put(key, b.getWidth() > FACE_PX * 2
                ? Bitmap.createScaledBitmap(b, FACE_PX, FACE_PX, true) : b);
        } catch (Throwable none) {
            // a face that cannot be made small is not kept
        }
    }
    private static final HashSet<Long> COMING = new HashSet<Long>();
    private static final HashMap<Long, ArrayList<Runnable>> WAITING = new HashMap<Long, ArrayList<Runnable>>();

    /**
     * What is waited for. The newest wish is taken first: leaving a grid of
     * three thousand pictures leaves as many wishes behind it, and the room
     * one comes back to must not stand in that queue. Wishes older than a few
     * hundred are let go, and whoever waited for them told so.
     */
    private static final class Wishes extends java.util.concurrent.LinkedBlockingDeque<Runnable> {
        @Override
        public boolean offer(Runnable r) {
            while (size() > 240) {
                Runnable old = pollLast();
                if (old instanceof Wish) {
                    ((Wish) old).dropped();
                }
            }
            return offerFirst(r);
        }
    }

    /* Thumbnails a phone has not made yet are made from the pictures
       themselves, which takes a moment each: a few at a time, so a room full
       of albums fills in quickly rather than one row after another. */
    private static final ExecutorService POOL = new java.util.concurrent.ThreadPoolExecutor(
        5, 5, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new Wishes());
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /** The size a small picture is fetched at, in pixels. */
    static final int SMALL_PX = 256;

    /** A small picture forgotten, so it is fetched again as the picture now lies. */
    static void forget(Photo p) {
        if (p != null) {
            SMALL.remove(Long.valueOf(p.id));
        }
    }

    /** A picture's small one, if it has come. */
    static Bitmap cached(Photo p) {
        return p == null ? null : SMALL.get(Long.valueOf(p.id));
    }

    private static String faceKey(String album, Photo p) {
        return album != null && album.length() > 0 ? album : (p == null ? "" : p.albumId);
    }

    /**
     * A picture set into an album's row. What the album's face was last time
     * stands at once, so a room never comes back bare; the album's newest
     * picture is asked for, and takes its place when it has been made.
     */
    static void intoFace(final ImageView cell, final Photo p, final String album) {
        final String key = faceKey(album, p);
        cell.setTag(Long.valueOf(p.id));
        cell.animate().cancel();
        cell.setAlpha(1f);
        Bitmap now = cached(p);
        Bitmap was = FACES.get(key);
        if (now != null) {
            keepFace(key, now);
            cell.setImageBitmap(now);
            return;
        }
        cell.setImageBitmap(was);
        want(cell.getContext(), p, new Runnable() {
            public void run() {
                Bitmap came = cached(p);
                if (came == null) {
                    return;
                }
                keepFace(key, came);
                if (!Long.valueOf(p.id).equals(cell.getTag())) {
                    return;
                }
                cell.setImageBitmap(came);
                cell.setAlpha(0f);
                cell.animate().alpha(1f).setDuration(Pace.PRESS).start();
            }
        });
    }

    /** Asks for a picture's small one; whoever asked is told on the screen's thread when it comes. */
    static void want(final Context c, final Photo p, Runnable told) {
        final Long key = Long.valueOf(p.id);
        if (SMALL.get(key) != null) {
            if (told != null) {
                told.run();
            }
            return;
        }
        synchronized (COMING) {
            if (told != null) {
                ArrayList<Runnable> list = WAITING.get(key);
                if (list == null) {
                    list = new ArrayList<Runnable>();
                    WAITING.put(key, list);
                }
                list.add(told);
            }
            if (!COMING.add(key)) {
                return;
            }
        }
        final Context app = c.getApplicationContext();
        POOL.execute(new Wish(key, app, p));
    }

    /** One picture wished for: made away from the screen, or let go unmade. */
    private static final class Wish implements Runnable {

        private final Long key;
        private final Context app;
        private final Photo p;

        Wish(Long key, Context app, Photo p) {
            this.key = key;
            this.app = app;
            this.p = p;
        }

        /** Let go without being made: whoever waited is told, so they may ask again. */
        void dropped() {
            tell(null);
        }

        public void run() {
            Bitmap b = small(app, p);
            if (b != null) {
                SMALL.put(key, b);
            }
            tell(b);
        }

        private void tell(Bitmap b) {
            final ArrayList<Runnable> list;
            synchronized (COMING) {
                COMING.remove(key);
                list = WAITING.remove(key);
            }
            if (list != null) {
                MAIN.post(new Runnable() {
                    public void run() {
                        for (int i = 0; i < list.size(); i++) {
                            list.get(i).run();
                        }
                    }
                });
            }
        }
    }

    /** The small one the index keeps, turned as the picture lies. */
    private static Bitmap small(Context c, Photo p) {
        try {
            Bitmap b;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                /* The platform turns it already. */
                return c.getContentResolver().loadThumbnail(p.uri,
                    new android.util.Size(SMALL_PX, SMALL_PX), null);
            }
            if (p.video) {
                return MediaStore.Video.Thumbnails.getThumbnail(c.getContentResolver(), p.id,
                    MediaStore.Video.Thumbnails.MINI_KIND, null);
            }
            b = MediaStore.Images.Thumbnails.getThumbnail(c.getContentResolver(), p.id,
                MediaStore.Images.Thumbnails.MINI_KIND, null);
            return turned(b, p.turn);
        } catch (Throwable none) {
            return null;
        }
    }

    /** A picture set into a cell: the one it has, or the one it asks for, if the cell still wants it then. */
    static void into(final ImageView cell, final Photo p) {
        cell.setTag(Long.valueOf(p.id));
        cell.animate().cancel();
        cell.setAlpha(1f);
        Bitmap b = cached(p);
        cell.setImageBitmap(b);
        if (b == null) {
            /* Nothing is drawn while it is on its way: whatever stands behind the
               cell — an initial, a plate of colour — is better than an empty square,
               and the cell's own ground must stay visible, so only the picture fades. */
            want(cell.getContext(), p, new Runnable() {
                public void run() {
                    if (!Long.valueOf(p.id).equals(cell.getTag())) {
                        return;
                    }
                    Bitmap came = cached(p);
                    if (came == null) {
                        return;
                    }
                    cell.setImageBitmap(came);
                    cell.setAlpha(0f);
                    cell.animate().alpha(1f).setDuration(Pace.PRESS).start();
                }
            });
        }
    }

    // ------------------------------------------------------------ the picture itself

    /** The picture in full, no larger than asked, turned as it lies. Away from the screen's thread. */
    static Bitmap decode(Context c, Photo p, final int maxW, final int maxH) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.graphics.ImageDecoder.Source src =
                    android.graphics.ImageDecoder.createSource(c.getContentResolver(), p.uri);
                return android.graphics.ImageDecoder.decodeBitmap(src,
                    new android.graphics.ImageDecoder.OnHeaderDecodedListener() {
                        public void onHeaderDecoded(android.graphics.ImageDecoder d,
                                                    android.graphics.ImageDecoder.ImageInfo info,
                                                    android.graphics.ImageDecoder.Source s) {
                            int w = info.getSize().getWidth();
                            int h = info.getSize().getHeight();
                            int sample = 1;
                            while (w / (sample * 2) >= maxW && h / (sample * 2) >= maxH) {
                                sample *= 2;
                            }
                            d.setTargetSampleSize(sample);
                        }
                    });
            }
            BitmapFactory.Options look = new BitmapFactory.Options();
            look.inJustDecodeBounds = true;
            java.io.InputStream in = c.getContentResolver().openInputStream(p.uri);
            BitmapFactory.decodeStream(in, null, look);
            if (in != null) {
                in.close();
            }
            int sample = 1;
            while (look.outWidth / (sample * 2) >= maxW && look.outHeight / (sample * 2) >= maxH) {
                sample *= 2;
            }
            BitmapFactory.Options take = new BitmapFactory.Options();
            take.inSampleSize = sample;
            in = c.getContentResolver().openInputStream(p.uri);
            Bitmap b = BitmapFactory.decodeStream(in, null, take);
            if (in != null) {
                in.close();
            }
            return turned(b, p.turn);
        } catch (Throwable broken) {
            Trace.note("pictures: not decoded: " + broken.getClass().getSimpleName());
            return null;
        }
    }

    private static Bitmap turned(Bitmap b, int degrees) {
        if (b == null || degrees % 360 == 0) {
            return b;
        }
        Matrix m = new Matrix();
        m.postRotate(degrees);
        return Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(), m, true);
    }

    // ------------------------------------------------------------ a frame kept

    /**
     * The frame of a film at a moment, kept as a picture among the pictures,
     * in a folder of the application's own name, named after the film and the
     * moment. Away from the screen's thread; answers whether it was kept.
     */
    /**
     * How many frames a second a video holds, as its own track says; thirty
     * when it does not say, which is what most do.
     */
    static float frameRate(Context c, Photo film) {
        android.media.MediaExtractor x = new android.media.MediaExtractor();
        try {
            x.setDataSource(c, film.uri, null);
            for (int i = 0; i < x.getTrackCount(); i++) {
                android.media.MediaFormat f = x.getTrackFormat(i);
                String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("video/")
                    || !f.containsKey(android.media.MediaFormat.KEY_FRAME_RATE)) {
                    continue;
                }
                try {
                    return f.getInteger(android.media.MediaFormat.KEY_FRAME_RATE);
                } catch (ClassCastException notWhole) {
                    return f.getFloat(android.media.MediaFormat.KEY_FRAME_RATE);
                }
            }
        } catch (Exception unread) {
            Trace.note("frame rate unread: " + unread.getClass().getSimpleName());
        } finally {
            x.release();
        }
        return 30f;
    }

    static boolean keepFrame(Context c, Photo film, long at) {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            return false;
        }
        android.media.MediaMetadataRetriever reel = new android.media.MediaMetadataRetriever();
        Bitmap frame = null;
        try {
            reel.setDataSource(c, film.uri);
            frame = reel.getFrameAtTime(Math.max(0L, at) * 1000L,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST);
        } catch (Exception unread) {
            Trace.note("frame: not read: " + unread.getClass().getSimpleName());
        } finally {
            try {
                reel.release();
            } catch (Exception ignored) {
                // released either way
            }
        }
        if (frame == null) {
            return false;
        }
        String title = Shelf.title(film.name).replaceAll("[\\/:*?\"<>|]", "_");
        long s = Math.max(0L, at / 1000L);
        String name = String.format(java.util.Locale.ROOT, "%s %02d-%02d-%02d.jpg", title,
            s / 3600L, (s / 60L) % 60L, s % 60L);
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Mirabilia");
        v.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.Images.Media.IS_PENDING, 1);
        Uri made = null;
        try {
            made = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (made == null) {
                return false;
            }
            java.io.OutputStream out = c.getContentResolver().openOutputStream(made);
            try {
                frame.compress(Bitmap.CompressFormat.JPEG, 94, out);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            android.content.ContentValues done = new android.content.ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            c.getContentResolver().update(made, done, null, null);
            Trace.note("frame kept");
            return true;
        } catch (Exception unwritten) {
            Trace.note("frame: not kept: " + unwritten.getClass().getSimpleName());
            if (made != null) {
                try {
                    c.getContentResolver().delete(made, null, null);
                } catch (Exception ignored) {
                    // nothing half-written stays
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------ words for time

    /** A month and its year, in the language of the application: the section a picture stands in. */
    static String month(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return Words.s("month_" + (c.get(Calendar.MONTH) + 1)) + " " + c.get(Calendar.YEAR);
    }

    /** A day and the hour, in figures only, so they need no language. */
    static String day(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        return String.format(java.util.Locale.ROOT, "%02d.%02d.%d  %02d:%02d",
            c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }

    // ------------------------------------------------------------ the grid

    interface Touched {
        void touched(int i, boolean held);
    }

    /**
     * The pictures as a grid of squares, three across, rounded a little, the
     * rows made again as they scroll so thousands cost what a screenful does.
     */
    static GridView grid(final Context c, final ArrayList<Photo> photos, final Touched touched) {
        final GridView grid = new GridView(c);
        final int across = 3;
        final int gap = Round.dp(3);
        grid.setNumColumns(across);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalScrollBarEnabled(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        grid.setClipToPadding(false);
        grid.setPadding(Round.dp(12), Round.dp(4), Round.dp(12), Round.dp(24));
        final int corner = Round.dp(8);
        grid.setAdapter(new BaseAdapter() {
            public int getCount() {
                return photos.size();
            }

            public Object getItem(int i) {
                return photos.get(i);
            }

            public long getItemId(int i) {
                return photos.get(i).id;
            }

            public View getView(final int i, View reuse, ViewGroup parent) {
                Photo p = photos.get(i);
                if (p.video) {
                    return filmCell(c, grid, reuse, p, corner);
                }
                ImageView cell = reuse instanceof ImageView ? (ImageView) reuse : null;
                if (cell == null) {
                    cell = new ImageView(c);
                    cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    cell.setBackgroundColor(Tone.of(Tone.SURFACE_HIGH));
                    cell.setOutlineProvider(new ViewOutlineProvider() {
                        public void getOutline(View v, Outline o) {
                            o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
                        }
                    });
                    cell.setClipToOutline(true);
                    cell.setForeground(Round.touch(Round.box(0x00000000, corner),
                        Tone.of(Tone.ON_SURFACE), corner));
                }
                int side = Math.max(1, grid.getColumnWidth());
                cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, side));
                Gallery.into(cell, photos.get(i));
                return cell;
            }
        });
        /* The grid, not its cells, answers a touch: a cell is made again and
           again as the grid scrolls, and the first one is made once more for
           measuring, so a touch kept on a cell can be kept on the wrong one. */
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            public void onItemClick(android.widget.AdapterView<?> parent, View v, int i, long id) {
                touched.touched(i, false);
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View v, int i, long id) {
                touched.touched(i, true);
                return true;
            }
        });
        return grid;
    }

    /** A film's cell: its small picture, and its length on a dark pill in the corner. */
    private static View filmCell(Context c, GridView grid, View reuse, Photo p, final int corner) {
        android.widget.FrameLayout cell = reuse instanceof android.widget.FrameLayout
            ? (android.widget.FrameLayout) reuse : null;
        ImageView face;
        android.widget.TextView length;
        if (cell == null) {
            cell = new android.widget.FrameLayout(c);
            cell.setBackgroundColor(Tone.of(Tone.SURFACE_HIGH));
            cell.setOutlineProvider(new ViewOutlineProvider() {
                public void getOutline(View v, Outline o) {
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
                }
            });
            cell.setClipToOutline(true);
            cell.setForeground(Round.touch(Round.box(0x00000000, corner), Tone.of(Tone.ON_SURFACE), corner));
            face = new ImageView(c);
            face.setScaleType(ImageView.ScaleType.CENTER_CROP);
            cell.addView(face, new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            length = new android.widget.TextView(c);
            Letter.set(length, Letter.LABEL_S);
            length.setTextColor(0xFFFFFFFF);
            length.setPadding(Round.dp(7), Round.dp(2), Round.dp(7), Round.dp(2));
            length.setBackground(Round.box(0x99000000, Round.FULL));
            android.widget.FrameLayout.LayoutParams at = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.Gravity.BOTTOM | android.view.Gravity.END);
            at.setMargins(0, 0, Round.dp(6), Round.dp(6));
            cell.addView(length, at);
            View trace = new View(c);
            trace.setBackgroundColor(Tone.of(Tone.PRIMARY));
            cell.addView(trace, new android.widget.FrameLayout.LayoutParams(0, Round.dp(3),
                android.view.Gravity.BOTTOM | android.view.Gravity.START));
        } else {
            face = (ImageView) cell.getChildAt(0);
            length = (android.widget.TextView) cell.getChildAt(1);
        }
        int side = Math.max(1, grid.getColumnWidth());
        cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, side));
        length.setText(Film.clock(p.duration));
        Gallery.into(face, p);
        /* How far it was watched, as a line along its foot; watched to the end, it steps back. */
        String key = p.item().key();
        long at = Shelf.soundAt(c, key);
        View trace = cell.getChildAt(2);
        android.widget.FrameLayout.LayoutParams line =
            (android.widget.FrameLayout.LayoutParams) trace.getLayoutParams();
        line.width = at > 0L && p.duration > 0L
            ? Math.max(Round.dp(4), Math.round(side * Math.min(1f, at / (float) p.duration))) : 0;
        trace.setLayoutParams(line);
        cell.setAlpha(at <= 0L && Shelf.done(c, key) ? 0.55f : 1f);
        return cell;
    }

    /** The grid as the ruler sees it: a picture for every row, the month for its section. */
    static final class Track implements Ruler.Track {
        private final GridView grid;
        private final ArrayList<Photo> photos;
        private final Context context;
        private final String[] months;

        Track(Context context, GridView grid, ArrayList<Photo> photos) {
            this.context = context;
            this.grid = grid;
            this.photos = photos;
            months = new String[photos.size()];
            for (int i = 0; i < photos.size(); i++) {
                months[i] = month(photos.get(i).taken);
            }
        }

        public int count() {
            return photos.size();
        }

        public String title(int i) {
            return day(photos.get(i).taken);
        }

        public String section(int i) {
            return months[i];
        }

        public boolean round(int i) {
            return false;
        }

        public String badge(int i) {
            return "";
        }

        public Bitmap picture(int i) {
            Photo p = photos.get(i);
            Bitmap b = cached(p);
            if (b == null) {
                want(context, p, null);
            }
            return b;
        }

        private int side() {
            return Math.max(1, grid.getColumnWidth() + grid.getVerticalSpacing());
        }

        public int beside(float y) {
            int at = grid.pointToPosition(grid.getWidth() / 2, Math.round(y));
            for (int d = 1; at == AbsListView.INVALID_POSITION && d < 12; d++) {
                at = grid.pointToPosition(grid.getWidth() / 2, Math.round(y) + d * 2);
            }
            if (at == AbsListView.INVALID_POSITION) {
                at = grid.getFirstVisiblePosition();
            }
            return Math.max(0, Math.min(photos.size() - 1, at));
        }

        public void bring(int i, float y) {
            int across = Math.max(1, grid.getNumColumns());
            final int start = i - i % across;
            final int off = Math.round(y - side() / 2f);
            /*
             * A grid has no selection while a finger is on the screen, and asking
             * it to place a row it has not selected sends it to the very top —
             * which is what it did, every time, however far the pearl was drawn.
             * So the row is chosen instead, which a grid understands with no
             * selection at all: it stands the row at the top. The last hair —
             * standing it where the pearl is rather than at the top — is given
             * afterwards, by moving the rows themselves.
             */
            grid.setSelection(start);
            grid.post(new Runnable() {
                public void run() {
                    int first = grid.getFirstVisiblePosition();
                    if (Math.abs(first - start) <= across * 2) {
                        if (off > 0) {
                            grid.scrollListBy(-off);
                        }
                        return;
                    }
                    if (System.currentTimeMillis() - said < 1500L) {
                        return;
                    }
                    said = System.currentTimeMillis();
                    Trace.note("ruler: asked " + start + " at " + off + "; grid "
                        + Integer.toHexString(System.identityHashCode(grid))
                        + " stands at " + first + " of " + grid.getCount() + "/" + photos.size()
                        + ", cells " + grid.getChildCount() + ", high " + grid.getHeight());
                }
            });
        }

        private long said;

        public float along() {
            int shown = Math.max(1, grid.getChildCount());
            int range = Math.max(1, photos.size() - shown);
            return Math.max(0f, Math.min(1f, grid.getFirstVisiblePosition() / (float) range));
        }

        public void still() {
            grid.smoothScrollBy(0, 0);
        }

        public void answer(int i) {
            final View cell = grid.getChildAt(i - grid.getFirstVisiblePosition());
            if (cell == null) {
                return;
            }
            cell.drawableHotspotChanged(cell.getWidth() / 2f, cell.getHeight() / 2f);
            cell.setPressed(true);
            cell.postDelayed(new Runnable() {
                public void run() {
                    cell.setPressed(false);
                }
            }, 320L);
        }
    }
}
