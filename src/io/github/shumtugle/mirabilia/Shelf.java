package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

/**
 * Where the books come from, and what the application remembers about each.
 *
 * The phone does not hand over its books; the owner opens a folder to the
 * application, once, and the grant outlives restarts. Every folder opened
 * this way is walked, subfolders included, and whatever in it can be read
 * stands on the shelf. Nothing is copied: a book is its address in the
 * owner's own folder, and it is read from there each time.
 *
 * For each book the shelf keeps the page it was left at and the pages the
 * reader marked, keyed by its address, and for the room the book read last.
 */
final class Shelf {

    /**
     * One file on the shelf.
     *
     * A file has an address, by which it is opened, and a place, by which it
     * is known. The address depends on the way it was reached: the same file
     * through two opened folders, or through a folder and the phone's index,
     * has two addresses. Its place — the volume and the path — is one. So
     * everything kept about a file, where it was left, its page, its marks,
     * is kept under its place, and outlives the folder it was opened through.
     */
    static final class Item {
        final String name;
        final Uri uri;
        final String mime;
        final long size;
        final long modified;
        final String folder;
        final String place;
        /**
         * Handed over from outside to be heard and nothing more: no place is
         * kept for it, no mark, no line among the recordings heard.
         */
        boolean passing;

        Item(String name, Uri uri, String mime, long size, long modified, String folder) {
            this(name, uri, mime, size, modified, folder, placeOf(uri));
        }

        Item(String name, Uri uri, String mime, long size, long modified, String folder,
             String place) {
            this.name = name;
            this.uri = uri;
            this.mime = mime;
            this.size = size;
            this.modified = modified;
            this.folder = folder;
            this.place = place == null ? "" : place;
        }

        /** What everything kept about the file is kept under: its place, or its address if that is all there is. */
        String key() {
            return place.length() > 0 ? place : String.valueOf(uri);
        }

        /** The folder the file lies in, as a place of its own. */
        String home() {
            if (place.length() == 0) {
                return "?" + (folder == null ? "" : folder);
            }
            int cut = place.lastIndexOf('/');
            if (cut >= 0) {
                return place.substring(0, cut);
            }
            int colon = place.indexOf(':');
            return colon >= 0 ? place.substring(0, colon + 1) : "";
        }
    }

    /** The provider of the phone's own storage, whose ids already read as volume and path. */
    private static final String STORAGE = "com.android.externalstorage.documents";

    /**
     * The place of a file reached through the storage framework: its
     * document's own id, which is the same through whichever folder the file
     * was opened. The phone's own storage writes it as volume, colon, path;
     * another provider's id is taken with the provider's name before it, so
     * two providers cannot share one. An address from the phone's index
     * carries no such id; its place comes from the index, and here it is none.
     */
    static String placeOf(Uri uri) {
        if (uri == null) {
            return "";
        }
        try {
            java.util.List<String> steps = uri.getPathSegments();
            int at = steps.lastIndexOf("document");
            if (at >= 0 && at + 1 < steps.size()) {
                String id = steps.get(at + 1);
                if (id.startsWith("raw:")) {
                    return fromPath(id.substring(4));
                }
                if (STORAGE.equals(uri.getAuthority())) {
                    return id;
                }
                return uri.getAuthority() + "|" + id;
            }
        } catch (Exception odd) {
            // an address of another shape has no place of this kind
        }
        return "";
    }

    /**
     * A path on the phone as a place, in the storage framework's own
     * spelling: the shared storage is the primary volume, and a card is named
     * by its label, as the framework names it.
     */
    static String fromPath(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        String primary = "/storage/emulated/0";
        if (path.equals(primary)) {
            return "primary:";
        }
        if (path.startsWith(primary + "/")) {
            return "primary:" + path.substring(primary.length() + 1);
        }
        if (path.startsWith("/storage/")) {
            String rest = path.substring("/storage/".length());
            int slash = rest.indexOf('/');
            if (slash > 0) {
                return rest.substring(0, slash) + ":" + rest.substring(slash + 1);
            }
        }
        return "path:" + path;
    }

    /** A place known even for an address from the phone's index, by asking the index where the file lies. */
    static String placeFor(Context c, Uri uri) {
        String known = placeOf(uri);
        if (known.length() > 0 || uri == null || !"media".equals(uri.getAuthority())) {
            return known;
        }
        Cursor q = null;
        try {
            q = c.getContentResolver().query(uri,
                new String[] {android.provider.MediaStore.MediaColumns.DATA}, null, null, null);
            if (q != null && q.moveToFirst() && !q.isNull(0)) {
                return fromPath(q.getString(0));
            }
        } catch (Exception unasked) {
            Trace.note("shelf: no place in the index: " + unasked.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        return "";
    }

    /** What stands on the shelf: books, and the documents a reader would open as one. */
    private static final String[] BOOKS = {
        ".epub", ".fb2", ".pdf", ".cbz", ".docx", ".odt", ".rtf", ".txt", ".md", ".markdown",
    };
    /**
     * Books in forms the reader cannot open. They stand on the shelf all the
     * same: a book that exists should be seen, and the refusal belongs to
     * the moment it is touched, not to the listing.
     */
    private static final String[] CLOSED = {
        ".djvu", ".djv", ".mobi", ".azw", ".azw3", ".kfx", ".cbr", ".cb7", ".chm", ".doc",
    };

    /** What the music room holds: recordings the platform's player can open. */
    private static final String[] SOUNDS = {
        ".mp3", ".m4a", ".m4b", ".aac", ".ogg", ".oga", ".opus", ".flac", ".wav", ".amr", ".mka",
    };

    private static final String TREES = "trees";
    private static final String SEP = "\u0001";
    /** How far down a walk goes, and how much it gathers, before it stops looking. */
    private static final int DEPTH = 8;
    private static final int MOST = 4000;
    /** The reader takes a bounded bite of a plain file. */
    private static final int READ_LIMIT = 512 * 1024;

    private Shelf() {
    }

    private static boolean retold;

    static SharedPreferences prefs(Context c) {
        SharedPreferences kept = c.getSharedPreferences("shelf", Context.MODE_PRIVATE);
        if (!retold) {
            retold = true;
            if (kept.getInt("keys", 1) < 2) {
                retell(c, kept);
            }
        }
        return kept;
    }

    /**
     * Once, on the first start after files came to be known by their place:
     * whatever was kept under a file's address — where it was left, its page,
     * its marks, where the voice stopped — moves to its place, and so do the
     * lists of what was read and heard, and the book the voice read last. It
     * runs before anything is read from what it moves, whichever way the
     * application was woken. An address whose place cannot be learnt keeps
     * what it had; nothing is thrown away.
     */
    private static void retell(Context c, SharedPreferences kept) {
        String[] kinds = {"at:", "voice:", "page:", "marks:"};
        java.util.Map<String, ?> all = kept.getAll();
        java.util.HashMap<String, String> learnt = new java.util.HashMap<String, String>();
        SharedPreferences.Editor e = kept.edit();
        int moved = 0;
        int stayed = 0;
        for (java.util.Map.Entry<String, ?> entry : all.entrySet()) {
            String name = entry.getKey();
            for (int k = 0; k < kinds.length; k++) {
                if (!name.startsWith(kinds[k] + "content://")) {
                    continue;
                }
                String address = name.substring(kinds[k].length());
                String place = learnt.get(address);
                if (place == null) {
                    place = placeFor(c, Uri.parse(address));
                    learnt.put(address, place);
                }
                if (place.length() == 0) {
                    stayed++;
                    break;
                }
                String to = kinds[k] + place;
                if (!all.containsKey(to)) {
                    Object v = entry.getValue();
                    if (v instanceof Integer) {
                        e.putInt(to, (Integer) v);
                    } else if (v instanceof Long) {
                        e.putLong(to, (Long) v);
                    } else if (v instanceof String) {
                        e.putString(to, (String) v);
                    } else if (v instanceof Float) {
                        e.putFloat(to, (Float) v);
                    } else if (v instanceof Boolean) {
                        e.putBoolean(to, (Boolean) v);
                    }
                }
                e.remove(name);
                moved++;
                break;
            }
        }
        String[] lists = {"read", "heard"};
        for (int l = 0; l < lists.length; l++) {
            Object v = all.get(lists[l]);
            if (!(v instanceof String) || ((String) v).length() == 0) {
                continue;
            }
            String[] lines = ((String) v).split("\n");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String[] f = lines[i].split("\t", -1);
                if (f.length < 3) {
                    continue;
                }
                String place = f.length > 4 ? f[4] : null;
                if (place == null) {
                    place = learnt.get(f[0]);
                    if (place == null) {
                        place = placeFor(c, Uri.parse(f[0]));
                        learnt.put(f[0], place);
                    }
                }
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(f[0]).append('\t').append(f[1]).append('\t').append(f[2]).append('\t')
                    .append(f.length > 3 ? f[3] : "").append('\t').append(place);
            }
            e.putString(lists[l], b.toString());
        }
        String book = Keep.voiceBook(c);
        if (book.startsWith("content://")) {
            String place = learnt.get(book);
            if (place == null) {
                place = placeFor(c, Uri.parse(book));
            }
            if (place.length() > 0) {
                Keep.retellVoiceBook(c, place);
            }
        }
        e.putInt("keys", 2);
        e.commit();
        Trace.note("keys: " + moved + " moved to places, " + stayed + " kept by address");
    }

    // ------------------------------------------------------------ the page

    static float fontSize(Context c) {
        return prefs(c).getInt("font", 17);
    }

    /** The size of the page's letters, set outright, within the bounds the page can take. */
    static void setFont(Context c, int size) {
        prefs(c).edit().putInt("font", Math.max(11, Math.min(28, size))).apply();
    }

    static void font(Context c, int step) {
        int v = Math.max(11, Math.min(28, prefs(c).getInt("font", 17) + step));
        prefs(c).edit().putInt("font", v).apply();
    }

    /** Where a recording was left, in milliseconds; nought is its beginning. */
    static long soundAt(Context c, String key) {
        return prefs(c).getLong("at:" + key, 0L);
    }

    static void setSoundAt(Context c, String key, long ms) {
        prefs(c).edit().putLong("at:" + key, ms).apply();
    }

    /**
     * How fast a folder's recordings play, one being as they were made. The
     * pace belongs to the folder, as its order does: a course of lectures
     * kept quicker does not hurry the music beside it, and a book read aloud
     * has a pace of its own.
     */
    static float paceOf(Context c, String home) {
        return prefs(c).getFloat("pace:" + (home == null ? "" : home), 1.0f);
    }

    static void setPaceOf(Context c, String home, float p) {
        prefs(c).edit().putFloat("pace:" + (home == null ? "" : home), p).apply();
    }

    /** The pace of a recording: its folder's. */
    static float pace(Context c, Item it) {
        return it == null ? 1.0f : paceOf(c, it.home());
    }

    /** Where the voice was put away in a book, as a character of its text, or none. */
    static int voiceAt(Context c, String key) {
        return prefs(c).getInt("voice:" + key, -1);
    }

    static void setVoiceAt(Context c, String key, int at) {
        prefs(c).edit().putInt("voice:" + key, at).apply();
    }

    /** How many seconds the voice steps back when it goes on after a pause. */
    static float rollback(Context c) {
        return prefs(c).getFloat("rollback", 3.5f);
    }

    /** How fast the voice reads, one being the voice's own pace. */
    static float rate(Context c) {
        return prefs(c).getFloat("rate", 1.0f);
    }

    static void setRate(Context c, float r) {
        prefs(c).edit().putFloat("rate", r).apply();
    }

    /**
     * The accent on paper. Day paper is light, so the accent is taken a step
     * darker than on the ground of the application; night paper takes it as
     * it is.
     */
    static int accent(Context c) {
        return Reader.night(c) ? Tone.of(Tone.PRIMARY) : Tone.of(Tone.INVERSE_PRIMARY);
    }

    // ------------------------------------------------------------ folders

    /**
     * The folders opened to the application. The same folder opened twice
     * arrives under two different addresses and would stand on the list
     * twice; the folder itself is what counts, so the later grant replaces
     * the earlier one.
     */
    static ArrayList<String> trees(Context c) {
        String kept = prefs(c).getString(TREES, "");
        ArrayList<String> out = new ArrayList<String>();
        if (kept.length() == 0) {
            return out;
        }
        String[] parts = kept.split(SEP);
        java.util.HashMap<String, Integer> seen = new java.util.HashMap<String, Integer>();
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].length() == 0) {
                continue;
            }
            String id = folderId(parts[i]);
            Integer was = seen.get(id);
            if (was != null) {
                out.set(was.intValue(), parts[i]);
            } else {
                seen.put(id, Integer.valueOf(out.size()));
                out.add(parts[i]);
            }
        }
        return out;
    }

    private static String folderId(String tree) {
        try {
            return DocumentsContract.getTreeDocumentId(Uri.parse(tree));
        } catch (Exception odd) {
            return tree;
        }
    }

    static void addTree(Context c, String uri) {
        ArrayList<String> now = trees(c);
        now.add(uri);
        prefs(c).edit().putString(TREES, join(now)).apply();
        prefs(c).edit().putString(TREES, join(trees(c))).apply();
    }

    /** A folder given back: it leaves the list, and the grant is returned to the system. */
    static void forget(Context c, String uri) {
        ArrayList<String> now = trees(c);
        now.remove(uri);
        prefs(c).edit().putString(TREES, join(now)).remove("deep:" + uri).apply();
        try {
            c.getContentResolver().releasePersistableUriPermission(Uri.parse(uri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception gone) {
            Trace.note("shelf: grant already gone: " + gone);
        }
    }

    /**
     * Folders whose grant the system no longer holds — after a reinstall, or
     * when the person took it back in the system settings — are dropped from
     * the list, so the shelf does not keep promising what it cannot open.
     * Answers how many went.
     */
    static int dropLost(Context c) {
        java.util.HashSet<String> held = new java.util.HashSet<String>();
        java.util.List<android.content.UriPermission> grants =
            c.getContentResolver().getPersistedUriPermissions();
        for (int i = 0; i < grants.size(); i++) {
            if (grants.get(i).isReadPermission()) {
                held.add(grants.get(i).getUri().toString());
            }
        }
        ArrayList<String> now = trees(c);
        ArrayList<String> keep = new ArrayList<String>();
        for (int i = 0; i < now.size(); i++) {
            if (held.contains(now.get(i))) {
                keep.add(now.get(i));
            }
        }
        if (keep.size() != now.size()) {
            prefs(c).edit().putString(TREES, join(keep)).apply();
        }
        return now.size() - keep.size();
    }

    /** Whether a folder is taken with everything under it, or alone. */
    static boolean deep(Context c, String tree) {
        return prefs(c).getBoolean("deep:" + tree, true);
    }

    static void setDeep(Context c, String tree, boolean on) {
        prefs(c).edit().putBoolean("deep:" + tree, on).apply();
    }

    /** The name of the folder as the person picked it. */
    static String folderName(Context c, String tree) {
        Cursor cur = null;
        try {
            Uri t = Uri.parse(tree);
            Uri doc = DocumentsContract.buildDocumentUriUsingTree(t,
                DocumentsContract.getTreeDocumentId(t));
            cur = c.getContentResolver().query(doc,
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (cur != null && cur.moveToFirst() && cur.getString(0) != null) {
                return cur.getString(0);
            }
        } catch (Exception gone) {
            Trace.note("shelf: no name for a folder: " + gone);
        } finally {
            if (cur != null) {
                cur.close();
            }
        }
        return tail(folderId(tree));
    }

    /**
     * Two folders can both be called Books. The folder's own id carries its
     * path, so the last two steps of it tell them apart without asking the
     * storage again.
     */
    static String tail(String docId) {
        if (docId == null) {
            return "";
        }
        int colon = docId.indexOf(':');
        String path = colon >= 0 ? docId.substring(colon + 1) : docId;
        String[] parts = path.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + " / " + parts[parts.length - 1];
        }
        return parts.length == 1 ? parts[0] : path;
    }

    static String treeTail(String tree) {
        return tail(folderId(tree));
    }

    /**
     * Where an opened folder lies, said so two folders of one name cannot be
     * taken for each other: the kind of storage — the phone's own, or a card —
     * and the whole path inside it, its steps joined by a small arrow. For a
     * provider other than the phone's storage, its path as it gives it.
     */
    static String[] where(String tree) {
        String id = folderId(tree);
        if (id == null) {
            return new String[] {"", ""};
        }
        int colon = id.indexOf(':');
        String volume = colon >= 0 ? id.substring(0, colon) : "";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        String kind = "primary".equals(volume) ? "on_phone"
            : (volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}") ? "on_card" : "");
        String[] steps = path.split("/");
        StringBuilder b = new StringBuilder();
        int from = Math.max(0, steps.length - 3);
        if (from > 0) {
            b.append("\u2026");
        }
        for (int i = from; i < steps.length; i++) {
            if (steps[i].length() == 0) {
                continue;
            }
            if (b.length() > 0) {
                b.append("  \u203A  ");
            }
            b.append(steps[i]);
        }
        return new String[] {kind, b.toString()};
    }

    // ------------------------------------------------------------ the whole phone

    /**
     * The other way of finding books: not the folders handed over one at a
     * time, but every book the phone's own index knows of, wherever it lies.
     * Reading a document found that way needs access to all files — a
     * permission the system grants only on a page of its own settings, so it
     * is asked for out loud, from the folders screen, and never in passing.
     */
    static boolean whole(Context c) {
        return prefs(c).getBoolean("whole", false) && allFiles();
    }

    static void setWhole(Context c, boolean on) {
        prefs(c).edit().putBoolean("whole", on).apply();
    }

    static boolean allFiles() {
        try {
            return android.os.Build.VERSION.SDK_INT < 30
                || android.os.Environment.isExternalStorageManager();
        } catch (Throwable t) {
            return false;
        }
    }

    static void askAllFiles(android.app.Activity a) {
        try {
            a.startActivity(new android.content.Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + a.getPackageName())));
        } catch (Exception e) {
            try {
                a.startActivity(new android.content.Intent(
                    android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
            } catch (Exception e2) {
                Trace.note("shelf: no all-files page: " + e2);
            }
        }
    }

    /** Every book in the phone's index, newest first, with a ceiling. */
    private static void indexed(Context c, ArrayList<Item> found, boolean sounds) {
        Uri table = android.provider.MediaStore.Files.getContentUri("external");
        String[] columns = {
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.MIME_TYPE,
            android.provider.MediaStore.MediaColumns.SIZE,
            android.provider.MediaStore.MediaColumns.DATE_ADDED,
            android.provider.MediaStore.MediaColumns.DATA,
        };
        Cursor q = null;
        try {
            /* By the day each file came to the phone: that is the order a
               room fed by the index is shown in. */
            q = c.getContentResolver().query(table, columns, null, null,
                android.provider.MediaStore.MediaColumns.DATE_ADDED + " DESC");
            if (q == null) {
                return;
            }
            while (q.moveToNext() && found.size() < MOST) {
                String name = q.getString(1);
                if (name == null || !wanted(name, sounds)) {
                    continue;
                }
                String path = q.isNull(5) ? "" : q.getString(5);
                String folder = "";
                int cut = path.lastIndexOf('/');
                if (cut > 0) {
                    int before = path.lastIndexOf('/', cut - 1);
                    folder = path.substring(before + 1, cut);
                }
                /* The index keeps seconds; everything else counts in
                   milliseconds and would put these in the year the clock
                   began. */
                found.add(new Item(name, android.content.ContentUris.withAppendedId(table,
                    q.getLong(0)), q.getString(2), q.getLong(3), q.getLong(4) * 1000L, folder,
                    fromPath(path)));
            }
        } catch (Exception e) {
            Trace.note("shelf: index: " + e.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
    }

    /** Whether the room of recordings takes, beside its folders, what the phone's index knows of. */
    static boolean soundsWhole(Context c) {
        return prefs(c).getBoolean("soundsWhole", false);
    }

    static void setSoundsWhole(Context c, boolean on) {
        prefs(c).edit().putBoolean("soundsWhole", on).apply();
    }

    /** The permission the index of recordings asks for, by the platform's age. */
    static String hearing() {
        return android.os.Build.VERSION.SDK_INT >= 33
            ? "android.permission.READ_MEDIA_AUDIO"
            : "android.permission.READ_EXTERNAL_STORAGE";
    }

    /** Whether the index of recordings may be read: by its own permission, or by the one for every file. */
    static boolean mayHear(Context c) {
        return allFiles() || c.checkSelfPermission(hearing())
            == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * The recordings the phone's index knows of, less what is not listened
     * to: ringtones, notification and alarm sounds, the recorder's notes, and
     * whatever lives under the applications' own folders, where messengers
     * keep their voice messages. The index itself already leaves out what a
     * folder's mark hides from it. Each is dated by the day it came.
     */
    private static void indexedSounds(Context c, ArrayList<Item> found) {
        Uri table = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        ArrayList<String> columns = new ArrayList<String>();
        columns.add(android.provider.MediaStore.Audio.Media._ID);
        columns.add(android.provider.MediaStore.Audio.Media.DISPLAY_NAME);
        columns.add(android.provider.MediaStore.Audio.Media.MIME_TYPE);
        columns.add(android.provider.MediaStore.Audio.Media.SIZE);
        columns.add(android.provider.MediaStore.Audio.Media.DATE_ADDED);
        columns.add(android.provider.MediaStore.Audio.Media.DATA);
        columns.add(android.provider.MediaStore.Audio.Media.IS_RINGTONE);
        columns.add(android.provider.MediaStore.Audio.Media.IS_NOTIFICATION);
        columns.add(android.provider.MediaStore.Audio.Media.IS_ALARM);
        boolean recordings = android.os.Build.VERSION.SDK_INT >= 31;
        if (recordings) {
            columns.add("is_recording");
        }
        Cursor q = null;
        int kept = 0;
        try {
            q = c.getContentResolver().query(table, columns.toArray(new String[0]), null, null,
                android.provider.MediaStore.Audio.Media.DATE_ADDED + " DESC");
            if (q == null) {
                return;
            }
            while (q.moveToNext() && found.size() < MOST) {
                String name = q.getString(1);
                String path = q.isNull(5) ? "" : q.getString(5);
                if (name == null || !isSound(name) || path.contains("/Android/")
                    || q.getInt(6) != 0 || q.getInt(7) != 0 || q.getInt(8) != 0
                    || (recordings && !q.isNull(9) && q.getInt(9) != 0)) {
                    continue;
                }
                String folder = "";
                int cut = path.lastIndexOf('/');
                if (cut > 0) {
                    int before = path.lastIndexOf('/', cut - 1);
                    folder = path.substring(before + 1, cut);
                }
                found.add(new Item(name, android.content.ContentUris.withAppendedId(table,
                    q.getLong(0)), q.getString(2), q.getLong(3), q.getLong(4) * 1000L, folder,
                    fromPath(path)));
                kept++;
            }
        } catch (Exception e) {
            Trace.note("shelf: index of recordings: " + e.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        Trace.note("index of recordings: " + kept);
    }

    /** Whether a folder was told to play in the order its files came rather than by their numbers. */
    static boolean timeOrdered(Context c, String home) {
        return prefs(c).getBoolean("byTime:" + home, false);
    }

    static void setTimeOrdered(Context c, String home, boolean on) {
        if (on) {
            prefs(c).edit().putBoolean("byTime:" + home, true).apply();
        } else {
            prefs(c).edit().remove("byTime:" + home).apply();
        }
    }

    /** A folder's recordings put again in the folder's order, as it now is. */
    static void reorder(Context c, ArrayList<Item> here) {
        sortHome(c, here);
    }

    /**
     * Which stretch of time a moment falls in, seen from now: today, this
     * week, this month, this year, or earlier. A room fed by the phone's
     * index is shown as a stream, newest first, parted by these.
     */
    static String when(long at) {
        java.util.Calendar now = java.util.Calendar.getInstance();
        java.util.Calendar then = java.util.Calendar.getInstance();
        then.setTimeInMillis(at);
        long ago = now.getTimeInMillis() - at;
        boolean year = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR);
        if (year && now.get(java.util.Calendar.DAY_OF_YEAR) == then.get(java.util.Calendar.DAY_OF_YEAR)) {
            return "today";
        }
        if (ago >= 0 && ago < 7L * 24L * 60L * 60L * 1000L) {
            return "this_week";
        }
        if (year && now.get(java.util.Calendar.MONTH) == then.get(java.util.Calendar.MONTH)) {
            return "this_month";
        }
        if (year) {
            return "this_year";
        }
        return "earlier";
    }

    private static String join(ArrayList<String> list) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                b.append(SEP);
            }
            b.append(list.get(i));
        }
        return b.toString();
    }

    static boolean isBook(String name) {
        return ends(name, BOOKS);
    }

    static boolean isSound(String name) {
        return ends(name, SOUNDS);
    }

    /** Whether a file belongs to the room being gathered. */
    private static boolean wanted(String name, boolean sounds) {
        return sounds ? isSound(name) : shelved(name);
    }

    /** Whether a file stands on the shelf: a book the reader opens, or one it cannot. */
    static boolean shelved(String name) {
        return ends(name, BOOKS) || ends(name, CLOSED);
    }

    private static boolean ends(String name, String[] set) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        for (int i = 0; i < set.length; i++) {
            if (n.endsWith(set[i])) {
                return true;
            }
        }
        return false;
    }

    /** What one walk finds: the books for the shelf and the recordings for the music room. */
    static final class Found {
        final ArrayList<Item> books = new ArrayList<Item>();
        final ArrayList<Item> sounds = new ArrayList<Item>();
    }

    /**
     * Walks every folder opened to the application once, and sorts what it
     * finds by kind as it goes: every room takes its share of the same walk,
     * since the walk is what costs — one question to the storage per folder.
     * A folder already inside another taken whole is not walked twice. Called
     * away from the screen's thread; a folder taken back is passed over in
     * silence. The phone's index adds to the books only, as its switch says.
     */
    static Found gatherAll(Context c) {
        Found walked = new Found();
        ArrayList<String> roots = trees(c);
        for (int i = 0; i < roots.size(); i++) {
            if (coveredBy(c, roots.get(i), roots) != null) {
                continue;
            }
            try {
                Uri tree = Uri.parse(roots.get(i));
                String top = DocumentsContract.getTreeDocumentId(tree);
                walk(c, tree, top, "", deep(c, roots.get(i)) ? 0 : DEPTH, walked, false);
            } catch (Exception gone) {
                Trace.note("shelf: a folder could not be walked: " + gone);
            }
        }
        if (whole(c)) {
            indexed(c, walked.books, false);
        }
        if (soundsWhole(c) && mayHear(c)) {
            indexedSounds(c, walked.sounds);
        }
        Found out = new Found();
        out.books.addAll(once(walked.books));
        out.sounds.addAll(inOrder(c, once(walked.sounds)));
        Collections.sort(out.books, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                return natural(title(a.name), title(b.name));
            }
        });
        return out;
    }

    /**
     * One file stands once, however many ways it was found: by its place,
     * and by name and size where a place is not known, since two addresses
     * of one file share nothing else.
     */
    private static ArrayList<Item> once(ArrayList<Item> walked) {
        ArrayList<Item> found = new ArrayList<Item>();
        java.util.HashSet<String> have = new java.util.HashSet<String>();
        for (int i = 0; i < walked.size(); i++) {
            Item it = walked.get(i);
            String seen = it.place.length() > 0 ? it.place : it.name + "|" + it.size;
            if (have.add(seen)) {
                found.add(it);
            }
        }
        return found;
    }

    /**
     * Recordings in the order they are heard in: folder by folder, never
     * scattered, and within a folder the folder's own order. That is the
     * order of the names, read as a person reads numbers — the second before
     * the tenth; where no name in the folder carries a number and every file
     * carries the number of its track, the tracks. The music room shows this
     * order and the player follows it, a folder at a time.
     */
    private static ArrayList<Item> inOrder(Context c, ArrayList<Item> found) {
        java.util.LinkedHashMap<String, ArrayList<Item>> homes =
            new java.util.LinkedHashMap<String, ArrayList<Item>>();
        for (int i = 0; i < found.size(); i++) {
            Item it = found.get(i);
            ArrayList<Item> here = homes.get(it.home());
            if (here == null) {
                here = new ArrayList<Item>();
                homes.put(it.home(), here);
            }
            here.add(it);
        }
        ArrayList<String> order = new ArrayList<String>(homes.keySet());
        Collections.sort(order, new Comparator<String>() {
            public int compare(String a, String b) {
                return natural(a, b);
            }
        });
        ArrayList<Item> out = new ArrayList<Item>();
        for (int h = 0; h < order.size(); h++) {
            ArrayList<Item> here = homes.get(order.get(h));
            sortHome(c, here);
            out.addAll(here);
        }
        return out;
    }

    private static void sortHome(Context c, ArrayList<Item> here) {
        if (!here.isEmpty() && timeOrdered(c, here.get(0).home())) {
            /* The folder was told its names lie: the order the files came in. */
            Collections.sort(here, new Comparator<Item>() {
                public int compare(Item a, Item b) {
                    int d = Long.compare(a.modified, b.modified);
                    return d != 0 ? d : natural(title(a.name), title(b.name));
                }
            });
            return;
        }
        boolean numbered = false;
        for (int i = 0; i < here.size() && !numbered; i++) {
            numbered = hasDigit(title(here.get(i).name));
        }
        if (!numbered && here.size() > 1) {
            final java.util.HashMap<String, Integer> tracks = new java.util.HashMap<String, Integer>();
            boolean all = true;
            for (int i = 0; i < here.size() && all; i++) {
                int t = track(c, here.get(i));
                all = t > 0;
                tracks.put(here.get(i).key(), Integer.valueOf(t));
            }
            if (all) {
                Collections.sort(here, new Comparator<Item>() {
                    public int compare(Item a, Item b) {
                        int d = tracks.get(a.key()).intValue() - tracks.get(b.key()).intValue();
                        return d != 0 ? d : natural(title(a.name), title(b.name));
                    }
                });
                return;
            }
        }
        Collections.sort(here, new Comparator<Item>() {
            public int compare(Item a, Item b) {
                return natural(title(a.name), title(b.name));
            }
        });
    }

    private static boolean hasDigit(String t) {
        for (int i = 0; i < t.length(); i++) {
            if (Character.isDigit(t.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The number of a recording's track, from its own tags, or none. Asked of
     * the file once and remembered under its place, since only folders whose
     * names carry no number ask at all, and they ask at every walk.
     */
    private static int track(Context c, Item it) {
        SharedPreferences kept = prefs(c);
        String at = "track:" + it.key();
        if (kept.contains(at)) {
            return kept.getInt(at, 0);
        }
        int n = 0;
        android.media.MediaMetadataRetriever tags = new android.media.MediaMetadataRetriever();
        try {
            tags.setDataSource(c, it.uri);
            String t = tags.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
            if (t != null) {
                int end = 0;
                while (end < t.length() && Character.isDigit(t.charAt(end))) {
                    end++;
                }
                n = end > 0 ? Integer.parseInt(t.substring(0, Math.min(end, 6))) : 0;
            }
        } catch (Exception untagged) {
            n = 0;
        } finally {
            try {
                tags.release();
            } catch (Exception ignored) {
                // released already
            }
        }
        kept.edit().putInt(at, n).apply();
        return n;
    }

    /**
     * Whether an opened folder already stands on the shelf inside another,
     * taken with everything under it. Answers the one it stands in, or none.
     * A folder opened twice is the same folder, not one inside the other.
     */
    static String coveredBy(Context c, String tree, ArrayList<String> roots) {
        String inner = folderId(tree);
        for (int i = 0; i < roots.size(); i++) {
            String outer = folderId(roots.get(i));
            if (outer.equals(inner) || !deep(c, roots.get(i))) {
                continue;
            }
            String stem = outer.endsWith(":") || outer.endsWith("/") ? outer : outer + "/";
            if (inner.startsWith(stem)) {
                return roots.get(i);
            }
        }
        return null;
    }

    /**
     * A folder just opened, taken with everything under it, swallows the
     * opened folders that lie inside it: they leave the list and their grants
     * go back to the system. What was kept about their files stays, because
     * it is kept under places. Answers how many went.
     */
    static int absorb(Context c, String tree) {
        if (!deep(c, tree)) {
            return 0;
        }
        ArrayList<String> now = trees(c);
        ArrayList<String> alone = new ArrayList<String>();
        alone.add(tree);
        int went = 0;
        for (int i = 0; i < now.size(); i++) {
            String other = now.get(i);
            if (!other.equals(tree) && coveredBy(c, other, alone) != null) {
                forget(c, other);
                went++;
            }
        }
        return went;
    }

    /**
     * Names compared the way a person reads them: a run of digits is one
     * number, so the second lecture stands before the tenth and not after
     * the first.
     */
    static int natural(String a, String b) {
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char x = a.charAt(i);
            char y = b.charAt(j);
            if (Character.isDigit(x) && Character.isDigit(y)) {
                int si = i;
                int sj = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String na = a.substring(si, i).replaceFirst("^0+(?=.)", "");
                String nb = b.substring(sj, j).replaceFirst("^0+(?=.)", "");
                if (na.length() != nb.length()) {
                    return na.length() - nb.length();
                }
                int d = na.compareTo(nb);
                if (d != 0) {
                    return d;
                }
            } else {
                int d = Character.toLowerCase(x) - Character.toLowerCase(y);
                if (d != 0) {
                    return d;
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    /**
     * One folder and, unless the depth is spent, everything under it. A
     * folder that holds the platform's mark against being scanned for media
     * keeps its recordings out of the music room, with everything under it —
     * that is where applications keep their caches and voice messages — but
     * its books stay on the shelf: the mark is about media, not reading.
     */
    private static void walk(Context c, Uri tree, String parent, String folder, int depth,
                             Found found, boolean muted) {
        if (found.books.size() >= MOST && found.sounds.size() >= MOST) {
            return;
        }
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parent);
        String[] columns = {
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        };
        Cursor cursor = null;
        ArrayList<String[]> below = new ArrayList<String[]>();
        ArrayList<Item> books = new ArrayList<Item>();
        ArrayList<Item> sounds = new ArrayList<Item>();
        boolean hidden = muted;
        try {
            cursor = c.getContentResolver().query(children, columns, null, null, null);
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                String id = cursor.getString(0);
                String name = cursor.getString(1);
                String mime = cursor.getString(2);
                if (name == null) {
                    continue;
                }
                if (name.equalsIgnoreCase(".nomedia")) {
                    hidden = true;
                    continue;
                }
                if (name.startsWith(".")) {
                    continue;
                }
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                    below.add(new String[] {id, name});
                    continue;
                }
                boolean book = shelved(name);
                boolean sound = !book && isSound(name);
                if (book || sound) {
                    long size = cursor.isNull(3) ? 0L : cursor.getLong(3);
                    long when = cursor.isNull(4) ? 0L : cursor.getLong(4);
                    Item it = new Item(name, DocumentsContract.buildDocumentUriUsingTree(tree, id),
                        mime, size, when, folder);
                    (book ? books : sounds).add(it);
                }
            }
        } catch (Exception broken) {
            Trace.note("shelf: " + broken);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        for (int i = 0; i < books.size() && found.books.size() < MOST; i++) {
            found.books.add(books.get(i));
        }
        for (int i = 0; i < sounds.size() && !hidden && found.sounds.size() < MOST; i++) {
            found.sounds.add(sounds.get(i));
        }
        if (depth >= DEPTH) {
            return;
        }
        for (int i = 0; i < below.size(); i++) {
            walk(c, tree, below.get(i)[0], below.get(i)[1], depth + 1, found, hidden);
        }
    }

    /** A title from a file name: no extension, no underscores standing in for spaces. */
    static String title(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        String t = dot > 0 ? name.substring(0, dot) : name;
        return t.replace('_', ' ').trim();
    }

    /**
     * What a recording is called on the screen. Audiobooks are often cut
     * into files named only by their number, and the name of the book is
     * the folder's; a title without a single letter in it is taken from the
     * folder then, with the number after it.
     */
    static String label(Item it) {
        String t = title(it.name);
        if (hasLetter(t) || it.folder == null || it.folder.length() == 0) {
            return t;
        }
        return it.folder + " \u2014 " + t;
    }

    /** Whether the file's own name says nothing a person would read. */
    static boolean nameless(Item it) {
        return !hasLetter(title(it.name)) && it.folder != null && it.folder.length() > 0;
    }

    /**
     * The title of the folder a file lies in: its name as the walk saw it,
     * or, for a file lying in an opened folder itself, the last step of the
     * folder's place.
     */
    static String homeTitle(Item it) {
        String named = folderTitle(it.folder);
        if (named.length() > 0) {
            return named;
        }
        String home = it.home();
        int cut = Math.max(home.lastIndexOf('/'), home.lastIndexOf(':'));
        return folderTitle(cut >= 0 && cut + 1 < home.length() ? home.substring(cut + 1) : "");
    }

    /** A folder's name as a title: underscores read as spaces, and no run of spaces. */
    static String folderTitle(String folder) {
        if (folder == null) {
            return "";
        }
        return folder.replace('_', ' ').replaceAll("\\s+", " ").trim();
    }

    /**
     * A name as it stands on a plate at the head of a screen.
     *
     * This is a reading of the name, never the name itself: what is kept,
     * looked up and renamed stays what the walk found. A plate may be set
     * in a kinder hand than a file system uses; an inventory may not.
     *
     * Three things happen here, each of which can decide to do nothing:
     * underscores are read as spaces, capitals a machine chose are let
     * down into ordinary letters, and a word too long for any line is
     * given the seams its own spelling offers.
     */
    static String plate(String name) {
        return seams(quieten(folderTitle(name)));
    }

    /**
     * Capitals that a camera chose rather than a person.
     *
     * A name is let down only when every letter in it stands in the upper
     * case and those letters have a lower case to be let down into. A
     * single small letter anywhere means the case was chosen, and the name
     * is left exactly as it is; a writing that knows only one case reports
     * no upper case at all, and so is never touched. Under five letters in
     * all, the name is taken for initials and left standing.
     *
     * The judgement is passed on the whole name at once, never word by
     * word: a rule applied to each word in turn lets one word down and
     * leaves the next standing, and half a name in capitals looks worse
     * than all of it. Only a word carrying digits stays as it is, a date
     * being no louder in capitals than out of them.
     *
     * The lower case is the one the phone's own tongue uses, because a name
     * is likelier to be in that tongue than in any other.
     */
    private static String quieten(String name) {
        int caps = 0;
        for (int i = 0; i < name.length(); ) {
            int ch = name.codePointAt(i);
            if (Character.isLowerCase(ch)) {
                return name;
            }
            if (Character.isUpperCase(ch) || Character.isTitleCase(ch)) {
                caps++;
            }
            i += Character.charCount(ch);
        }
        if (caps < 5) {
            return name;
        }
        StringBuilder out = new StringBuilder(name.length());
        int at = 0;
        while (at < name.length()) {
            int end = name.indexOf(' ', at);
            if (end < 0) {
                end = name.length();
            }
            out.append(word(name.substring(at, end)));
            if (end < name.length()) {
                out.append(' ');
            }
            at = end + 1;
        }
        return out.toString();
    }

    /** One word let down, its first letter left standing; a word with digits is not touched. */
    private static String word(String w) {
        int first = -1;
        for (int i = 0; i < w.length(); ) {
            int ch = w.codePointAt(i);
            if (Character.isDigit(ch)) {
                return w;
            }
            if (Character.isLetter(ch) && first < 0) {
                first = i;
            }
            i += Character.charCount(ch);
        }
        if (first < 0) {
            return w;
        }
        int after = first + Character.charCount(w.codePointAt(first));
        return w.substring(0, after) + w.substring(after).toLowerCase(Locale.getDefault());
    }

    /** Where a line may turn inside a word: after these, and before a capital. */
    private static final String SEAM = ".-/,+&";
    /** Shorter than this a word needs no seams: it will fit, or very nearly. */
    private static final int LONG = 12;

    /**
     * A word long enough to leave a line no room to break gets the seams
     * its own spelling gives: after a dot, a dash, a slash, and where a
     * small letter is followed by a capital. What is put in has no width
     * and says nothing; it only says that here a line may turn — so that a
     * break, when one is needed, falls where a person would have put it
     * and not through the middle of a word.
     */
    private static String seams(String name) {
        int run = 0;
        boolean need = false;
        for (int i = 0; i < name.length() && !need; i++) {
            run = name.charAt(i) == ' ' ? 0 : run + 1;
            need = run >= LONG;
        }
        if (!need) {
            return name;
        }
        StringBuilder out = new StringBuilder(name.length() + 8);
        for (int i = 0; i < name.length(); ) {
            int ch = name.codePointAt(i);
            int step = Character.charCount(ch);
            if (i > 0 && Character.isUpperCase(ch)
                && Character.isLowerCase(name.codePointBefore(i))) {
                out.append('\u200B');
            }
            out.appendCodePoint(ch);
            if (step == 1 && SEAM.indexOf((char) ch) >= 0
                && i + 1 < name.length() && name.charAt(i + 1) != ' ') {
                out.append('\u200B');
            }
            i += step;
        }
        return out.toString();
    }

    /**
     * The letter a name stands by while its face is on its way. Taken as a
     * whole letter, not as half of one: a name may begin outside the first
     * sixty-five thousand signs, and half of such a sign is a blank box.
     */
    static String initial(String name) {
        if (name == null) {
            return "";
        }
        for (int i = 0; i < name.length(); ) {
            int ch = name.codePointAt(i);
            if (Character.isLetterOrDigit(ch)) {
                return new String(Character.toChars(Character.toUpperCase(ch)));
            }
            i += Character.charCount(ch);
        }
        return "";
    }

    /**
     * The recordings of the room, folder by folder, in the order they stand:
     * each folder a list of its own. The room shows a folder as one row, and
     * the folder's own screen shows its list.
     */
    static ArrayList<ArrayList<Item>> homes(ArrayList<Item> sounds) {
        ArrayList<ArrayList<Item>> out = new ArrayList<ArrayList<Item>>();
        ArrayList<Item> here = null;
        String at = null;
        for (int i = 0; i < sounds.size(); i++) {
            Item it = sounds.get(i);
            if (here == null || !it.home().equals(at)) {
                here = new ArrayList<Item>();
                at = it.home();
                out.add(here);
            }
            here.add(it);
        }
        return out;
    }

    /**
     * What the titles of a folder's files all begin with, cut back to where a
     * word ends, so that no number is split: the part a list need not repeat
     * on every line, since the folder already says it once.
     */
    static String common(ArrayList<Item> here) {
        if (here.size() < 2) {
            return "";
        }
        String first = title(here.get(0).name);
        int end = first.length();
        for (int i = 1; i < here.size() && end > 0; i++) {
            String t = title(here.get(i).name);
            int k = 0;
            while (k < end && k < t.length()
                && Character.toLowerCase(t.charAt(k)) == Character.toLowerCase(first.charAt(k))) {
                k++;
            }
            end = k;
        }
        while (end > 0 && !isBreak(first.charAt(end - 1))) {
            end--;
        }
        /* A prefix that would leave some title with nothing of its own is no prefix. */
        for (int i = 0; i < here.size(); i++) {
            if (title(here.get(i).name).length() <= end) {
                return "";
            }
        }
        return first.substring(0, end);
    }

    private static boolean isBreak(char ch) {
        return ch == ' ' || ch == '-' || ch == '.' || ch == ',' || ch == '(' || ch == '['
            || ch == '\u2014' || ch == '\u2013';
    }

    /** A file's title without what its whole folder shares. */
    static String own(Item it, String common) {
        String t = title(it.name);
        if (common.length() > 0 && t.length() > common.length()
            && t.substring(0, common.length()).equalsIgnoreCase(common)) {
            t = t.substring(common.length());
        }
        t = t.trim();
        while (t.length() > 0 && isBreak(t.charAt(0))) {
            t = t.substring(1).trim();
        }
        return t.length() > 0 ? t : title(it.name);
    }

    /** Whether a recording was heard to its end; told by the player, forgotten when it is started again. */
    static boolean done(Context c, String key) {
        return prefs(c).getBoolean("done:" + key, false);
    }

    static void setDone(Context c, String key, boolean on) {
        if (on) {
            prefs(c).edit().putBoolean("done:" + key, true).apply();
        } else if (prefs(c).contains("done:" + key)) {
            prefs(c).edit().remove("done:" + key).apply();
        }
    }

    private static boolean hasLetter(String t) {
        for (int i = 0; i < t.length(); i++) {
            if (Character.isLetter(t.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    /** The kind of a file, as three or four letters. */
    static String kind(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    // ------------------------------------------------------------ memory

    static int pageOf(Context c, String key) {
        return prefs(c).getInt("page:" + key, 0);
    }

    static void setPage(Context c, String key, int page) {
        prefs(c).edit().putInt("page:" + key, page).apply();
    }

    /**
     * What was read, newest first, thirty deep. Kept as lines rather than as
     * a set, because the order is the whole point of it.
     */
    static void remember(Context c, Item it) {
        remember(c, it, "read");
    }

    static void heard(Context c, Item it) {
        remember(c, it, "heard");
    }

    static ArrayList<Item> recent(Context c, int most) {
        return recent(c, most, "read");
    }

    static ArrayList<Item> recentSounds(Context c, int most) {
        return recent(c, most, "heard");
    }

    /**
     * A file renamed keeps what was kept under its old place — where it was
     * left, its page, its marks, whether it was heard to the end — under the
     * new one.
     */
    static void movePlace(Context c, String from, String to) {
        if (from == null || to == null || from.length() == 0 || to.length() == 0 || from.equals(to)) {
            return;
        }
        android.content.SharedPreferences p = prefs(c);
        android.content.SharedPreferences.Editor e = p.edit();
        java.util.Map<String, ?> all = p.getAll();
        int moved = 0;
        for (java.util.Map.Entry<String, ?> one : all.entrySet()) {
            String k = one.getKey();
            if (!k.endsWith(":" + from)) {
                continue;
            }
            String head = k.substring(0, k.length() - from.length());
            Object v = one.getValue();
            if (v instanceof Long) {
                e.putLong(head + to, (Long) v);
            } else if (v instanceof Integer) {
                e.putInt(head + to, (Integer) v);
            } else if (v instanceof Boolean) {
                e.putBoolean(head + to, (Boolean) v);
            } else if (v instanceof Float) {
                e.putFloat(head + to, (Float) v);
            } else if (v instanceof String) {
                e.putString(head + to, (String) v);
            } else {
                continue;
            }
            e.remove(k);
            moved++;
        }
        e.apply();
        Trace.note("kept things moved with the file: " + moved);
    }

    private static final String[] REMEMBERED = {"read", "heard", "watched"};

    /**
     * A file renamed stands under its new name and place in the lists of
     * what was read, heard and watched, and under its new address if it has
     * one, so the list does not keep offering a name that is no longer there.
     */
    static void renameRemembered(Context c, Item it, String place, String name, String uri) {
        rewriteRemembered(c, it, place, name, uri);
    }

    /** A file deleted, or gone from its place, leaves the lists of what was read, heard and watched. */
    static void forgetRemembered(Context c, Item it) {
        rewriteRemembered(c, it, null, null, null);
    }

    private static void rewriteRemembered(Context c, Item it, String place, String name, String uri) {
        android.content.SharedPreferences p = prefs(c);
        android.content.SharedPreferences.Editor e = p.edit();
        int touched = 0;
        for (int l = 0; l < REMEMBERED.length; l++) {
            String kept = p.getString(REMEMBERED[l], "");
            if (kept.length() == 0) {
                continue;
            }
            String[] lines = kept.split("\n");
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                String[] f = line.split("\t", -1);
                String was = f.length > 4 && f[4].length() > 0 ? f[4] : f[0];
                boolean same = was.equals(it.key()) || f[0].equals(it.uri.toString());
                if (same) {
                    touched++;
                    if (place == null) {
                        continue;
                    }
                    line = (uri != null ? uri : f[0]) + "\t" + clean(name)
                        + "\t" + (f.length > 2 ? f[2] : "") + "\t" + (f.length > 3 ? f[3] : "")
                        + "\t" + place;
                }
                if (b.length() > 0) {
                    b.append('\n');
                }
                b.append(line);
            }
            e.putString(REMEMBERED[l], b.toString());
        }
        e.apply();
        if (touched > 0) {
            Trace.note((place == null ? "let go from the lists: " : "renamed in the lists: ") + touched);
        }
    }

    /**
     * The shelf as the pearl string reads it: a list that hands out its rows
     * as the eye reaches them, so the string must ask the shelf itself where
     * each title stands rather than look at rows that are not there.
     */
    static final class Track implements Ruler.Track {

        private final android.widget.ListView list;
        private final ArrayList<Item> items;
        private final ArrayList<Integer> at;
        private final int head;
        private final boolean flowing;
        private final Context context;

        Track(Context context, android.widget.ListView list, ArrayList<Item> items,
              ArrayList<Integer> at, int head, boolean flowing) {
            this.context = context;
            this.list = list;
            this.items = items;
            this.at = at;
            this.head = head;
            this.flowing = flowing;
        }

        public int count() {
            return items.size();
        }

        public String title(int i) {
            return Shelf.title(items.get(i).name);
        }

        public String section(int i) {
            return flowing ? Words.s(Shelf.when(items.get(i).modified)) : initial(title(i));
        }

        public boolean round(int i) {
            return false;
        }

        public String badge(int i) {
            return initial(title(i));
        }

        public android.graphics.Bitmap picture(int i) {
            return Thumb.known(items.get(i));
        }

        /** The section a title stands in: its first letter, or a sign for what begins with a number. */
        private static String initial(String title) {
            String t = title == null ? "" : title.trim();
            if (t.length() == 0) {
                return "\u2014";
            }
            char c = Character.toUpperCase(t.charAt(0));
            return Character.isLetter(c) ? String.valueOf(c) : "#";
        }

        /** Which of the shelf's rows stands at a height of the screen. */
        public int beside(float y) {
            int where = list.pointToPosition(list.getWidth() / 2, Math.round(y));
            for (int d = 1; where == android.widget.AbsListView.INVALID_POSITION && d < 12; d++) {
                where = list.pointToPosition(list.getWidth() / 2, Math.round(y) + d * 4);
            }
            if (where == android.widget.AbsListView.INVALID_POSITION) {
                where = list.getFirstVisiblePosition();
            }
            return nearest(where - head);
        }

        /** The row of the list that holds a title, and the title nearest a row of the list. */
        private int nearest(int row) {
            int best = 0;
            for (int i = 0; i < at.size(); i++) {
                if (at.get(i) <= row) {
                    best = i;
                } else {
                    break;
                }
            }
            return Math.max(0, Math.min(items.size() - 1, best));
        }

        public void bring(int i, float y) {
            int where = head + at.get(Math.max(0, Math.min(items.size() - 1, i)));
            list.setSelectionFromTop(where, Math.round(y));
        }

        public float along() {
            int first = Math.max(0, list.getFirstVisiblePosition() - head);
            return items.isEmpty() ? 0f : Math.max(0f, Math.min(1f, nearest(first) / (float) items.size()));
        }

        public void still() {
            list.smoothScrollBy(0, 0);
        }

        public void answer(int i) {
            list.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
        }
    }

    /** A film opened, remembered for the list of those to go on with. */    /** A film opened, remembered for the list of those to go on with. */
    static void rememberWatched(Context c, Item it) {
        remember(c, it, "watched");
    }

    static ArrayList<Item> recentWatched(Context c, int most) {
        return recent(c, most, "watched");
    }

    /**
     * What was read or heard last, as it stands on the shelf now: an entry
     * remembered by an address that has since changed — its folder swallowed
     * by a larger one — is given the address the shelf has for the same
     * place, so it still opens.
     */
    static ArrayList<Item> current(ArrayList<Item> remembered, ArrayList<Item> shelf) {
        java.util.HashMap<String, Item> here = new java.util.HashMap<String, Item>();
        for (int i = 0; i < shelf.size(); i++) {
            here.put(shelf.get(i).key(), shelf.get(i));
        }
        ArrayList<Item> out = new ArrayList<Item>();
        for (int i = 0; i < remembered.size(); i++) {
            Item now = here.get(remembered.get(i).key());
            out.add(now != null ? now : remembered.get(i));
        }
        return out;
    }

    private static void remember(Context c, Item it, String list) {
        String line = it.uri.toString() + "\t" + clean(it.name) + "\t" + (it.mime == null ? "" : it.mime)
            + "\t" + clean(it.folder) + "\t" + it.place;
        ArrayList<String> lines = new ArrayList<String>();
        lines.add(line);
        String kept = prefs(c).getString(list, "");
        String[] old = kept.length() == 0 ? new String[0] : kept.split("\n");
        for (int i = 0; i < old.length && lines.size() < 30; i++) {
            String[] f = old[i].split("\t", -1);
            String was = f.length > 4 && f[4].length() > 0 ? f[4] : f[0];
            if (!was.equals(it.key()) && !f[0].equals(it.uri.toString())) {
                lines.add(old[i]);
            }
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(lines.get(i));
        }
        prefs(c).edit().putString(list, b.toString()).apply();
    }

    private static ArrayList<Item> recent(Context c, int most, String list) {
        ArrayList<Item> out = new ArrayList<Item>();
        String kept = prefs(c).getString(list, "");
        if (kept.length() == 0) {
            return out;
        }
        String[] lines = kept.split("\n");
        for (int i = 0; i < lines.length && out.size() < most; i++) {
            String[] f = lines[i].split("\t", -1);
            if (f.length >= 3) {
                Uri at = Uri.parse(f[0]);
                out.add(new Item(f[1], at, f[2], 0L, 0L, f.length > 3 ? f[3] : "",
                    f.length > 4 && f[4].length() > 0 ? f[4] : placeOf(at)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------ remembered shelf

    /**
     * Walking folders through the storage framework is slow — one question
     * per folder, and no way to ask for everything at once — and it would be
     * paid again at every start. So the last shelf is written down, shown at
     * once, and walked again behind the screen: instantly a little wrong is
     * better than right in nine seconds, as long as it corrects itself.
     */
    static void keep(Context c, ArrayList<Item> list) {
        keep(c, list, "shelf.v1");
    }

    static ArrayList<Item> kept(Context c) {
        return kept(c, "shelf.v1");
    }

    static void keepSounds(Context c, ArrayList<Item> list) {
        keep(c, list, "sounds.v1");
    }

    static ArrayList<Item> keptSounds(Context c) {
        return kept(c, "sounds.v1");
    }

    private static void keep(Context c, ArrayList<Item> list, String file) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            Item it = list.get(i);
            b.append(it.uri).append('\t').append(clean(it.name)).append('\t')
                .append(it.mime == null ? "" : it.mime).append('\t').append(it.size)
                .append('\t').append(it.modified).append('\t').append(clean(it.folder))
                .append('\t').append(it.place).append('\n');
        }
        try {
            java.io.FileOutputStream out = new java.io.FileOutputStream(
                new java.io.File(c.getFilesDir(), file));
            out.write(b.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception e) {
            Trace.note("shelf: not kept: " + e);
        }
    }

    private static ArrayList<Item> kept(Context c, String file) {
        ArrayList<Item> out = new ArrayList<Item>();
        java.io.File f = new java.io.File(c.getFilesDir(), file);
        if (!f.exists() || f.length() > 8 * 1024 * 1024) {
            return out;
        }
        try {
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            byte[] all = new byte[(int) f.length()];
            int at = 0;
            while (at < all.length) {
                int got = in.read(all, at, all.length - at);
                if (got < 0) {
                    break;
                }
                at += got;
            }
            in.close();
            String[] lines = new String(all, 0, at, "UTF-8").split("\n");
            for (int i = 0; i < lines.length; i++) {
                String[] p = lines[i].split("\t", -1);
                if (p.length < 6) {
                    continue;
                }
                Uri address = Uri.parse(p[0]);
                out.add(new Item(p[1], address, p[2], parse(p[3]), parse(p[4]), p[5],
                    p.length > 6 && p[6].length() > 0 ? p[6] : placeOf(address)));
            }
        } catch (Exception e) {
            Trace.note("shelf: kept list unreadable: " + e);
        }
        return out;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ');
    }

    private static long parse(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    static ArrayList<Integer> marks(Context c, String key) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        String kept = prefs(c).getString("marks:" + key, "");
        if (kept.length() == 0) {
            return out;
        }
        String[] parts = kept.split(",");
        for (int i = 0; i < parts.length; i++) {
            try {
                Integer p = Integer.valueOf(parts[i].trim());
                if (!out.contains(p)) {
                    out.add(p);
                }
            } catch (NumberFormatException ignored) {
                // a damaged entry is left behind rather than read wrongly
            }
        }
        Collections.sort(out);
        return out;
    }

    /** Marks a page, or takes the mark away if it is there already. Answers whether it is marked now. */
    static boolean toggleMark(Context c, String key, int page) {
        ArrayList<Integer> now = marks(c, key);
        Integer p = Integer.valueOf(page);
        boolean marked;
        if (now.contains(p)) {
            now.remove(p);
            marked = false;
        } else {
            now.add(p);
            marked = true;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < now.size(); i++) {
            if (i > 0) {
                b.append(',');
            }
            b.append(now.get(i));
        }
        prefs(c).edit().putString("marks:" + key, b.toString()).apply();
        return marked;
    }

    // ------------------------------------------------------------ reading a file

    static String read(Context c, Uri doc) {
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(doc);
            if (in == null) {
                return null;
            }
            byte[] buf = new byte[READ_LIMIT];
            int got = 0;
            int n;
            while (got < buf.length && (n = in.read(buf, got, buf.length - got)) > 0) {
                got += n;
            }
            String s = new String(buf, 0, got, "UTF-8");
            if (got >= READ_LIMIT) {
                s = s + "\n\n\u2026";
            }
            return s;
        } catch (Exception e) {
            Trace.note("shelf: could not read: " + e);
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
                // closing a stream that is already gone has nothing to say
            }
        }
    }
}
