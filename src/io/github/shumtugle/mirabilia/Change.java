package io.github.shumtugle.mirabilia;

import android.content.ContentValues;
import android.content.Context;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import java.io.File;

/**
 * Renaming and deleting a file, by the most direct road the phone allows,
 * tried in order.
 *
 * With the leave for every file, the file itself is renamed or deleted where
 * it lies, and the phone's index is told of it. A file of a folder opened to
 * the rooms is changed through that folder, if the folder was opened for
 * writing as well as reading; one opened only for reading is answered as
 * READ_ONLY, and the window asks for it to be opened again, for writing. A file known only from the phone's index
 * — a picture, a video, a recording found there — is the index's to change,
 * and the system asks the person first, in its own words; that is answered
 * as ASK, for the window to put the question.
 */
final class Change {

    private Change() {
    }

    /** Done here and now. */
    static final int DONE = 0;
    /** The system must ask first. */
    static final int ASK = 1;
    /** It could not be done. */
    static final int REFUSED = 2;
    /** The folder was opened for reading only. */
    static final int READ_ONLY = 3;
    /** A file of the new name is already there. */
    static final int TAKEN = 4;
    /** The file is no longer where it was known to lie: moved, renamed or deleted elsewhere. */
    static final int GONE = 5;

    /**
     * With the leave for every file, a place that holds no file means the
     * file has gone from it; the index would only be asked about a row that
     * is no longer the file, and would refuse.
     */
    private static boolean gone(Shelf.Item it) {
        if (!Shelf.allFiles() || it.place.length() == 0) {
            return false;
        }
        File f = fileOf(it.place);
        if (f == null || f.exists()) {
            return false;
        }
        Trace.note("change: the file is no longer at its place");
        return true;
    }

    /**
     * Whether the index can be asked to let a file be changed: it lets the
     * person answer for pictures, videos and recordings, and for nothing else.
     */
    private static boolean askable(Shelf.Item it) {
        if (!media(it.uri) || android.os.Build.VERSION.SDK_INT < 30) {
            Trace.note("change: the index cannot be asked on this platform");
            return false;
        }
        String m = it.mime == null ? "" : it.mime;
        if (m.length() > 0 && !m.startsWith("image/") && !m.startsWith("video/")
            && !m.startsWith("audio/")) {
            Trace.note("change: the index asks about media only, not " + m);
            return false;
        }
        return true;
    }

    /** The file behind a place, where the leave for every file lets it be touched directly. */
    static File fileOf(String place) {
        if (place == null) {
            return null;
        }
        int colon = place.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String volume = place.substring(0, colon);
        String path = place.substring(colon + 1);
        if ("path".equals(volume)) {
            return new File(path);
        }
        String root = "primary".equals(volume) ? "/storage/emulated/0" : "/storage/" + volume;
        return new File(root + "/" + path);
    }

    /** A name's ending, with its dot, or nothing. */
    static String ending(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot) : "";
    }

    /** A name without its ending. */
    static String stem(String name) {
        int dot = name == null ? -1 : name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : (name == null ? "" : name);
    }

    /** The place of a file of another name in the same folder. */
    static String sibling(String place, String name) {
        if (place == null || place.length() == 0) {
            return "";
        }
        int cut = Math.max(place.lastIndexOf('/'), place.indexOf(':'));
        return place.substring(0, cut + 1) + name;
    }

    private static boolean media(Uri uri) {
        return uri != null && "media".equals(uri.getAuthority());
    }

    /** Whether deleting this file will be asked by the system itself, so it need not be asked here too. */
    static boolean systemAsks(Context c, Shelf.Item it) {
        if (Shelf.allFiles()) {
            File f = fileOf(it.place);
            if (f != null && f.isFile()) {
                return false;
            }
        }
        return !DocumentsContract.isDocumentUri(c, it.uri) && media(it.uri)
            && android.os.Build.VERSION.SDK_INT >= 30;
    }

    /**
     * Renames a file; the new place, when it is known here, is left in
     * placed[0], and a new address, when the file has one now, in placed[1].
     */
    static int rename(Context c, Shelf.Item it, String to, String[] placed) {
        if (gone(it)) {
            return GONE;
        }
        if (Shelf.allFiles()) {
            File f = fileOf(it.place);
            if (f != null && f.isFile()) {
                File g = new File(f.getParentFile(), to);
                if (g.exists()) {
                    Trace.note("change: the name is taken");
                    return TAKEN;
                }
                if (f.renameTo(g)) {
                    scan(c, f.getAbsolutePath(), g.getAbsolutePath());
                    placed[0] = Shelf.fromPath(g.getAbsolutePath());
                    return DONE;
                }
                Trace.note("change: the file would not take the name");
            }
        }
        if (DocumentsContract.isDocumentUri(c, it.uri)) {
            try {
                /* A folder may give the file a name of its own choosing when
                   the one asked for is taken; the place follows what it gave. */
                Uri now = DocumentsContract.renameDocument(c.getContentResolver(), it.uri, to);
                placed[0] = sibling(it.place, now == null ? to : nameOf(c, now, to));
                if (now != null && placed.length > 1) {
                    placed[1] = now.toString();
                }
                return DONE;
            } catch (SecurityException closed) {
                Trace.note("change: the folder allows reading only");
                return READ_ONLY;
            } catch (Exception broken) {
                Trace.note("change: not renamed: " + broken.getClass().getSimpleName());
                return REFUSED;
            }
        }
        if (media(it.uri)) {
            return askable(it) ? ASK : REFUSED;
        }
        Trace.note("change: no road to this file");
        return REFUSED;
    }

    /** The name a document goes by now, or the one expected if it cannot be read. */
    private static String nameOf(Context c, Uri uri, String expected) {
        android.database.Cursor q = null;
        try {
            q = c.getContentResolver().query(uri,
                new String[] {DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst() && q.getString(0) != null) {
                return q.getString(0);
            }
        } catch (Exception unread) {
            Trace.note("change: new name unread: " + unread.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        return expected;
    }

    /**
     * The folder a document of an opened folder lies in, as the place the
     * system's folder picker starts from; null when it cannot be told.
     */
    static Uri folderOf(Uri document) {
        try {
            String id = DocumentsContract.getDocumentId(document);
            int slash = id.lastIndexOf('/');
            int colon = id.indexOf(':');
            String parent = slash > colon ? id.substring(0, slash) : id.substring(0, colon + 1);
            return DocumentsContract.buildDocumentUri(document.getAuthority(), parent);
        } catch (Exception unknown) {
            return null;
        }
    }

    /**
     * The same document reached through a folder just opened, or null when it
     * does not lie in that folder or under it.
     */
    static Uri through(Uri tree, Uri document) {
        try {
            if (!tree.getAuthority().equals(document.getAuthority())) {
                return null;
            }
            String inner = DocumentsContract.getDocumentId(document);
            String outer = DocumentsContract.getTreeDocumentId(tree);
            String stem = outer.endsWith(":") || outer.endsWith("/") ? outer : outer + "/";
            if (!inner.startsWith(stem)) {
                return null;
            }
            return DocumentsContract.buildDocumentUriUsingTree(tree, inner);
        } catch (Exception unknown) {
            return null;
        }
    }

    /** Once the system has said yes: the index renames the file when its name is changed there. */
    static boolean renameInIndex(Context c, Uri uri, String to) {
        try {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, to);
            return c.getContentResolver().update(uri, v, null, null) > 0;
        } catch (Exception refused) {
            Trace.note("change: index would not rename: " + refused.getClass().getSimpleName());
            return false;
        }
    }

    /** Deletes a file. */
    static int delete(Context c, Shelf.Item it) {
        if (gone(it)) {
            return GONE;
        }
        if (Shelf.allFiles()) {
            File f = fileOf(it.place);
            if (f != null && f.isFile()) {
                if (f.delete()) {
                    scan(c, f.getAbsolutePath());
                    return DONE;
                }
            }
        }
        if (DocumentsContract.isDocumentUri(c, it.uri)) {
            try {
                return DocumentsContract.deleteDocument(c.getContentResolver(), it.uri) ? DONE : REFUSED;
            } catch (SecurityException closed) {
                Trace.note("change: the folder allows reading only");
                return READ_ONLY;
            } catch (Exception broken) {
                Trace.note("change: not deleted: " + broken.getClass().getSimpleName());
                return REFUSED;
            }
        }
        if (media(it.uri)) {
            return askable(it) ? ASK : REFUSED;
        }
        Trace.note("change: no road to this file");
        return REFUSED;
    }

    /**
     * Words picked in a book, written at the end of the book's own file of
     * excerpts — one file for each book, in a folder of the application's
     * among the phone's documents — with where they stand and when. The
     * file is plain text, so it is a book itself.
     */
    static boolean excerpt(Context c, String folder, String book, String entry) {
        String name = book.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (name.length() == 0) {
            name = "_";
        }
        name = name + ".txt";
        byte[] bytes;
        try {
            bytes = entry.getBytes("UTF-8");
        } catch (Exception never) {
            return false;
        }
        if (Shelf.allFiles()) {
            File dir = new File("/storage/emulated/0/Documents/Mirabilia/" + folder);
            if (dir.isDirectory() || dir.mkdirs()) {
                File f = new File(dir, name);
                java.io.FileOutputStream out = null;
                try {
                    out = new java.io.FileOutputStream(f, true);
                    out.write(bytes);
                    scan(c, f.getAbsolutePath());
                    return true;
                } catch (Exception unwritten) {
                    Trace.note("excerpt: file refused: " + unwritten.getClass().getSimpleName());
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (Exception ignored) {
                        // closed either way
                    }
                }
            }
        }
        if (android.os.Build.VERSION.SDK_INT < 29) {
            return false;
        }
        String where = "Documents/Mirabilia/" + folder + "/";
        Uri table = MediaStore.Files.getContentUri("external");
        Uri found = null;
        android.database.Cursor q = null;
        try {
            q = c.getContentResolver().query(table, new String[] {MediaStore.MediaColumns._ID},
                MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                new String[] {where, name}, null);
            if (q != null && q.moveToFirst()) {
                found = android.content.ContentUris.withAppendedId(table, q.getLong(0));
            }
        } catch (Exception ignored) {
            // looked for and not found
        } finally {
            if (q != null) {
                q.close();
            }
        }
        try {
            if (found == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, where);
                found = c.getContentResolver().insert(table, v);
            }
            if (found == null) {
                return false;
            }
            java.io.OutputStream out = c.getContentResolver().openOutputStream(found, "wa");
            try {
                out.write(bytes);
            } finally {
                out.close();
            }
            return true;
        } catch (Exception unwritten) {
            Trace.note("excerpt: index refused: " + unwritten.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * A picture turned a quarter clockwise, without touching a pixel: only
     * the mark in the file that says how it lies is changed. Pictures that
     * carry no such mark are not turned here.
     */
    static boolean turn(Context c, Uri uri, String place, String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        if (!n.endsWith(".jpg") && !n.endsWith(".jpeg")) {
            return false;
        }
        try {
            File f = Shelf.allFiles() ? fileOf(place) : null;
            if (f != null && f.isFile()) {
                android.media.ExifInterface x = new android.media.ExifInterface(f.getAbsolutePath());
                quarter(x);
                x.saveAttributes();
                scan(c, f.getAbsolutePath());
                return true;
            }
            android.os.ParcelFileDescriptor fd = c.getContentResolver().openFileDescriptor(uri, "rw");
            if (fd == null) {
                return false;
            }
            try {
                android.media.ExifInterface x = new android.media.ExifInterface(fd.getFileDescriptor());
                quarter(x);
                x.saveAttributes();
            } finally {
                fd.close();
            }
            return true;
        } catch (Exception refused) {
            Trace.note("turn: refused: " + refused.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * The mark moved a quarter clockwise. A picture kept mirrored, as some
     * cameras facing the person keep theirs, turns within the mirrored marks,
     * so it stays mirrored: 1, 6, 3, 8 for the plain ones, 2, 7, 4, 5 for the
     * mirrored.
     */
    private static void quarter(android.media.ExifInterface x) {
        int now = x.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL);
        int[] plain = {1, 6, 3, 8};
        int[] mirrored = {2, 7, 4, 5};
        int next = 6;
        for (int i = 0; i < 4; i++) {
            if (plain[i] == now) {
                next = plain[(i + 1) % 4];
            } else if (mirrored[i] == now) {
                next = mirrored[(i + 1) % 4];
            }
        }
        x.setAttribute(android.media.ExifInterface.TAG_ORIENTATION, String.valueOf(next));
    }

    /** Whether the mark can be moved here: only a JPEG keeps a mark that can be rewritten in place. */
    static boolean turnable(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** The phone's index is told a file came or went, so every application sees it at once. */
    private static void scan(Context c, String... paths) {
        try {
            MediaScannerConnection.scanFile(c.getApplicationContext(), paths, null, null);
        } catch (Exception ignored) {
            // the index finds out by itself, later
        }
    }
}
