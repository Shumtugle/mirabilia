package io.github.shumtugle.mirabilia;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Set;

/**
 * The picture a recording carries, taken out into the collection: a
 * recording's excerpt, as a book's is its words and a video's its frame.
 *
 * Covers go to one folder among the pictures, so they stand together as an
 * album in the room of pictures — a wall of the music's own faces. The file
 * is named for what it shows: who, the album, and the year, in whatever
 * language the recording itself names them; a recording with no album is
 * named for its title, and one with no words at all, for its file. The name
 * is the card: the words of an image's own marks are Latin letters only by
 * their rules, and would turn any other letters to question marks.
 *
 * One album has one cover however many recordings carry it: a cover whose
 * name is already there is not taken twice. Only what the files hold is
 * taken; nothing is looked up anywhere.
 */
final class Covers {

    private Covers() {
    }

    static final String FOLDER = "Pictures/Mirabilia/Covers/";

    /** A cover taken, one already there, or none in the recording. */
    static final int TAKEN = 1;
    static final int THERE = 0;
    static final int NONE = -1;

    /**
     * The cover of one recording into the collection. The names already seen
     * in this round are kept in seen, so a folder of one album asks the index
     * once, not once per recording.
     */
    static int take(Context c, Shelf.Item it, Set<String> seen) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        byte[] picture;
        String name;
        try {
            r.setDataSource(c, it.uri);
            picture = r.getEmbeddedPicture();
            if (picture == null || picture.length < 32) {
                return NONE;
            }
            String artist = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST));
            String album = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), null);
            String title = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), null);
            String year = year(pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)));
            name = nameOf(artist, album, title, year, Change.stem(it.name));
        } catch (Exception unread) {
            Trace.note("covers: not read: " + unread.getClass().getSimpleName());
            return NONE;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // released either way
            }
        }
        if (seen.contains(name) || there(c, name)) {
            seen.add(name);
            return THERE;
        }
        seen.add(name);
        byte[] jpeg = jpeg(picture);
        if (jpeg == null) {
            return NONE;
        }
        return keep(c, name, jpeg) ? TAKEN : NONE;
    }

    private static String pick(String a, String b) {
        if (a != null && a.trim().length() > 0) {
            return a.trim();
        }
        return b != null ? b.trim() : "";
    }

    /** The year out of whatever a recording says about its date. */
    private static String year(String said) {
        if (said == null) {
            return "";
        }
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(1[89]|20)\\d\\d").matcher(said);
        return m.find() ? m.group() : "";
    }

    /** Who — what (year), with nothing a file's name may not hold. */
    static String nameOf(String artist, String album, String title, String year, String stem) {
        String what = album.length() > 0 ? album : title;
        StringBuilder b = new StringBuilder();
        if (artist.length() > 0 && what.length() > 0) {
            b.append(artist).append(" \u2014 ").append(what);
        } else if (artist.length() > 0 || what.length() > 0) {
            b.append(artist).append(what);
        } else {
            b.append(stem);
        }
        if (year.length() > 0) {
            b.append(" (").append(year).append(')');
        }
        String clean = b.toString().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim();
        if (clean.length() > 120) {
            clean = clean.substring(0, 120).trim();
        }
        return clean.length() == 0 ? "cover" : clean;
    }

    /** Whether a cover of that name is already in the covers' folder. */
    private static boolean there(Context c, String name) {
        Cursor q = null;
        try {
            q = c.getContentResolver().query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                new String[] {MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[] {FOLDER, name + ".jpg"}, null);
            return q != null && q.moveToFirst();
        } catch (Exception unread) {
            return false;
        } finally {
            if (q != null) {
                q.close();
            }
        }
    }

    /** A picture as a JPEG: kept byte for byte if it is one, written as one if it is not. */
    private static byte[] jpeg(byte[] picture) {
        if ((picture[0] & 0xFF) == 0xFF && (picture[1] & 0xFF) == 0xD8) {
            return picture;
        }
        Bitmap b = BitmapFactory.decodeByteArray(picture, 0, picture.length);
        if (b == null) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 92, out);
        b.recycle();
        return out.toByteArray();
    }

    private static boolean keep(Context c, String name, byte[] jpeg) {
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ".jpg");
        v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, FOLDER);
        v.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis());
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri made = null;
        try {
            made = c.getContentResolver().insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (made == null) {
                return false;
            }
            OutputStream out = c.getContentResolver().openOutputStream(made);
            try {
                out.write(jpeg);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(made, done, null, null);
            return true;
        } catch (Exception unwritten) {
            Trace.note("covers: not kept: " + unwritten.getClass().getSimpleName());
            if (made != null) {
                try {
                    c.getContentResolver().delete(made, null, null);
                } catch (Exception ignored) {
                    // the index drops a half-made file by itself
                }
            }
            return false;
        }
    }

    // ------------------------------------------------------------ cards

    /** Where cards of tags lie: among the phone's documents, beside the excerpts, named as the covers are. */
    static final String CARDS = "Documents/Mirabilia/Covers/";

    /** What one recording's tag and sound say, read once for a card. */
    private static final class Track {
        String artist = "";
        String albumArtist = "";
        String album = "";
        String title = "";
        String year = "";
        String genre = "";
        String number = "";
        String composer = "";
        String name = "";
        String place = "";
        long ms;
        int kbps;
        int hz;
        int channels;
        String kind = "";
    }

    private static Track read(Context c, Shelf.Item it) {
        Track t = new Track();
        t.name = it.name == null ? "" : it.name;
        t.place = it.place == null ? "" : it.place;
        String ext = t.name.lastIndexOf('.') >= 0 ? t.name.substring(t.name.lastIndexOf('.') + 1) : "";
        t.kind = ext.toUpperCase(java.util.Locale.ROOT);
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(c, it.uri);
            t.artist = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST), null);
            t.albumArtist = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST), null);
            t.album = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM), null);
            t.title = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE), null);
            t.year = year(pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR),
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)));
            t.genre = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE), null);
            t.number = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER), null);
            t.composer = pick(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER), null);
            t.ms = number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            t.kbps = (int) (number(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)) / 1000L);
        } catch (Exception unread) {
            Trace.note("cards: not read: " + unread.getClass().getSimpleName());
            return null;
        } finally {
            try {
                r.release();
            } catch (Exception ignored) {
                // released either way
            }
        }
        android.media.MediaExtractor x = new android.media.MediaExtractor();
        try {
            x.setDataSource(c, it.uri, null);
            for (int i = 0; i < x.getTrackCount(); i++) {
                android.media.MediaFormat f = x.getTrackFormat(i);
                String mime = f.getString(android.media.MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    if (f.containsKey(android.media.MediaFormat.KEY_SAMPLE_RATE)) {
                        t.hz = f.getInteger(android.media.MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (f.containsKey(android.media.MediaFormat.KEY_CHANNEL_COUNT)) {
                        t.channels = f.getInteger(android.media.MediaFormat.KEY_CHANNEL_COUNT);
                    }
                    break;
                }
            }
        } catch (Exception unread) {
            // the sound's shape is left unsaid
        } finally {
            x.release();
        }
        return t;
    }

    private static long number(String s) {
        try {
            return s == null ? 0L : Long.parseLong(s.trim());
        } catch (Exception odd) {
            return 0L;
        }
    }

    private static String clock(long ms) {
        long s = ms / 1000L;
        return s >= 3600L
            ? (s / 3600L) + ":" + String.format(java.util.Locale.ROOT, "%02d:%02d", (s / 60L) % 60L, s % 60L)
            : (s / 60L) + ":" + String.format(java.util.Locale.ROOT, "%02d", s % 60L);
    }

    /**
     * Cards of what the recordings' tags say, as plain text: one for each
     * album among them, its tracks in their order with their lengths, and one
     * for a recording that belongs to no album. Named as the album's cover
     * is, so a picture and its card are found by the same name; a card of
     * that name already there is left as it is. The card's own words are
     * English, as the application's files are; what the tags say stays in
     * the tags' own language. Answers {cards written, cards already there}.
     */
    static int[] cards(Context c, java.util.List<Shelf.Item> items) {
        java.util.LinkedHashMap<String, java.util.ArrayList<Track>> albums =
            new java.util.LinkedHashMap<String, java.util.ArrayList<Track>>();
        for (int i = 0; i < items.size(); i++) {
            Track t = read(c, items.get(i));
            if (t == null) {
                continue;
            }
            String who = t.albumArtist.length() > 0 ? t.albumArtist : t.artist;
            String key = t.album.length() > 0 ? who + "\u0001" + t.album + "\u0001" + t.year : "\u0002" + t.name;
            java.util.ArrayList<Track> group = albums.get(key);
            if (group == null) {
                group = new java.util.ArrayList<Track>();
                albums.put(key, group);
            }
            group.add(t);
        }
        int written = 0;
        int there = 0;
        for (java.util.ArrayList<Track> group : albums.values()) {
            Track first = group.get(0);
            String who = first.albumArtist.length() > 0 ? first.albumArtist : first.artist;
            String name = nameOf(who, first.album, first.album.length() > 0 ? "" : first.title, first.year,
                Change.stem(first.name));
            String text = card(name, who, group);
            int got = writeCard(c, name + ".txt", text);
            if (got > 0) {
                written++;
            } else if (got == 0) {
                there++;
            }
        }
        return new int[] {written, there};
    }

    private static String card(String name, String who, java.util.ArrayList<Track> group) {
        Track first = group.get(0);
        StringBuilder b = new StringBuilder();
        b.append(name).append('\n');
        for (int i = 0; i < Math.min(60, name.length()); i++) {
            b.append('\u2500');
        }
        b.append("\n\n");
        line(b, "Artist", who);
        if (first.album.length() > 0) {
            line(b, "Album", first.album);
        } else {
            line(b, "Title", first.title);
        }
        line(b, "Year", first.year);
        line(b, "Genre", first.genre);
        line(b, "Composer", first.composer);
        if (first.album.length() > 0) {
            java.util.Collections.sort(group, new java.util.Comparator<Track>() {
                public int compare(Track x, Track y) {
                    long a = number(x.number.split("/")[0]);
                    long z = number(y.number.split("/")[0]);
                    return a != z ? Long.compare(a, z) : x.name.compareToIgnoreCase(y.name);
                }
            });
            b.append("\nTracks\n");
            long total = 0L;
            for (int i = 0; i < group.size(); i++) {
                Track t = group.get(i);
                String n = t.number.split("/")[0].trim();
                b.append(n.length() > 0 ? n : String.valueOf(i + 1)).append(".  ")
                    .append(t.title.length() > 0 ? t.title : Change.stem(t.name));
                if (t.artist.length() > 0 && !t.artist.equals(who)) {
                    b.append(" \u2014 ").append(t.artist);
                }
                if (t.ms > 0L) {
                    b.append("  \u00B7  ").append(clock(t.ms));
                }
                b.append('\n');
                total += t.ms;
            }
            b.append(group.size()).append(group.size() == 1 ? " track" : " tracks");
            if (total > 0L) {
                b.append("  \u00B7  ").append(clock(total));
            }
            b.append('\n');
        } else if (first.ms > 0L) {
            line(b, "Length", clock(first.ms));
        }
        StringBuilder sound = new StringBuilder(first.kind);
        if (first.kbps > 0) {
            sound.append("  \u00B7  ").append(first.kbps).append(" kbps");
        }
        if (first.hz > 0) {
            sound.append("  \u00B7  ").append(String.format(java.util.Locale.ROOT, "%.1f kHz", first.hz / 1000f));
        }
        if (first.channels == 1) {
            sound.append("  \u00B7  mono");
        } else if (first.channels == 2) {
            sound.append("  \u00B7  stereo");
        }
        b.append('\n');
        line(b, "Sound", sound.toString());
        String place = first.place;
        int colon = place.indexOf(':');
        String path = colon >= 0 ? place.substring(colon + 1) : place;
        int slash = path.lastIndexOf('/');
        line(b, "From", first.album.length() > 0 && slash > 0 ? path.substring(0, slash) : path);
        line(b, "Kept", new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(new java.util.Date()));
        return b.toString();
    }

    private static void line(StringBuilder b, String what, String said) {
        if (said != null && said.trim().length() > 0) {
            b.append(what).append(":  ").append(said.trim()).append('\n');
        }
    }

    /** A card written as a new file; answers 1 when written, 0 when one of that name is there, -1 when refused. */
    private static int writeCard(Context c, String name, String text) {
        byte[] bytes;
        try {
            bytes = text.getBytes("UTF-8");
        } catch (Exception never) {
            return -1;
        }
        if (Shelf.allFiles()) {
            java.io.File dir = new java.io.File("/storage/emulated/0/" + CARDS);
            if (dir.isDirectory() || dir.mkdirs()) {
                java.io.File f = new java.io.File(dir, name);
                if (f.exists()) {
                    return 0;
                }
                try {
                    java.io.FileOutputStream out = new java.io.FileOutputStream(f);
                    try {
                        out.write(bytes);
                    } finally {
                        out.close();
                    }
                    android.media.MediaScannerConnection.scanFile(c, new String[] {f.getAbsolutePath()},
                        new String[] {"text/plain"}, null);
                    return 1;
                } catch (Exception unwritten) {
                    Trace.note("cards: file refused: " + unwritten.getClass().getSimpleName());
                }
            }
        }
        Uri table = MediaStore.Files.getContentUri("external");
        Cursor q = null;
        try {
            q = c.getContentResolver().query(table, new String[] {MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[] {CARDS, name}, null);
            if (q != null && q.moveToFirst()) {
                return 0;
            }
        } catch (Exception ignored) {
            // looked for and not found
        } finally {
            if (q != null) {
                q.close();
            }
        }
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, CARDS);
            Uri made = c.getContentResolver().insert(table, v);
            if (made == null) {
                return -1;
            }
            OutputStream out = c.getContentResolver().openOutputStream(made);
            try {
                out.write(bytes);
            } finally {
                if (out != null) {
                    out.close();
                }
            }
            return 1;
        } catch (Exception unwritten) {
            Trace.note("cards: index refused: " + unwritten.getClass().getSimpleName());
            return -1;
        }
    }

    /**
     * A picture of the collection made into a cover: square, cut from its
     * middle, no larger than a thousand pixels a side, as a JPEG.
     */
    static byte[] fromPicture(Context c, Gallery.Photo p) {
        try {
            ImageDecoder.Source src = ImageDecoder.createSource(c.getContentResolver(), p.uri);
            Bitmap b = ImageDecoder.decodeBitmap(src, new ImageDecoder.OnHeaderDecodedListener() {
                public void onHeaderDecoded(ImageDecoder d, ImageDecoder.ImageInfo info, ImageDecoder.Source s) {
                    int shorter = Math.min(info.getSize().getWidth(), info.getSize().getHeight());
                    int sample = 1;
                    while (shorter / (sample * 2) >= 1000) {
                        sample *= 2;
                    }
                    if (sample > 1) {
                        d.setTargetSampleSize(sample);
                    }
                    d.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                }
            });
            int side = Math.min(b.getWidth(), b.getHeight());
            Bitmap square = Bitmap.createBitmap(b, (b.getWidth() - side) / 2, (b.getHeight() - side) / 2, side, side);
            Bitmap sized = side > 1000 ? Bitmap.createScaledBitmap(square, 1000, 1000, true) : square;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            sized.compress(Bitmap.CompressFormat.JPEG, 90, out);
            return out.toByteArray();
        } catch (Throwable broken) {
            Trace.note("covers: picture not made a cover: " + broken.getClass().getSimpleName());
            return null;
        }
    }
}
