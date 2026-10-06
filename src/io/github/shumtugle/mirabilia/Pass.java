package io.github.shumtugle.mirabilia;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * How a thing made here is handed to another application without being kept
 * in the collection first.
 *
 * A message, a note, a workshop — each wants an address it may read, not a
 * file of ours. So the work is written into the application's own scratch
 * and passed by an address of this provider's, with leave to read it and
 * nothing else. Nothing of the collection is reachable this way: only what
 * lies in that one folder, which is swept of everything older than half a
 * day whenever something new is put there.
 */
public final class Pass extends ContentProvider {

    static final String WHO = "io.github.shumtugle.mirabilia.pass";
    private static final String FOLDER = "pass";
    private static final long KEEP = 12L * 60L * 60L * 1000L;

    /** The folder the handed things lie in, swept of the stale ones. */
    static File room(android.content.Context c) {
        File dir = new File(c.getCacheDir(), FOLDER);
        File[] were = dir.listFiles();
        long now = System.currentTimeMillis();
        if (were != null) {
            for (int i = 0; i < were.length; i++) {
                if (now - were[i].lastModified() > KEEP) {
                    were[i].delete();
                }
            }
        }
        return dir.isDirectory() || dir.mkdirs() ? dir : null;
    }

    /** The address another application is given for a file in that folder. */
    static Uri uriOf(File f) {
        return Uri.parse("content://" + WHO + "/" + Uri.encode(f.getName()));
    }

    private File fileOf(Uri uri) {
        String name = uri.getLastPathSegment();
        if (name == null || name.contains("/") || name.contains("..")) {
            return null;
        }
        return new File(new File(getContext().getCacheDir(), FOLDER), name);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        File f = fileOf(uri);
        if (f == null || !f.isFile()) {
            throw new FileNotFoundException();
        }
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** What the other application asks before it takes the file: its name and its size. */
    @Override
    public Cursor query(Uri uri, String[] wanted, String where, String[] args, String order) {
        File f = fileOf(uri);
        if (f == null || !f.isFile()) {
            return null;
        }
        String[] columns = wanted != null ? wanted
            : new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor said = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(columns[i])) {
                row[i] = f.length();
            }
        }
        said.addRow(row);
        return said;
    }

    @Override
    public String getType(Uri uri) {
        String name = uri.getLastPathSegment();
        String end = name != null && name.lastIndexOf('.') >= 0
            ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT) : "";
        if (end.equals("jpg") || end.equals("jpeg")) {
            return "image/jpeg";
        }
        if (end.equals("png")) {
            return "image/png";
        }
        if (end.equals("mp3")) {
            return "audio/mpeg";
        }
        if (end.equals("txt")) {
            return "text/plain";
        }
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String where, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] args) {
        return 0;
    }
}
