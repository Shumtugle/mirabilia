package io.github.shumtugle.mirabilia;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;

/**
 * A recording shared to be heard and nothing more. No window opens: the
 * player takes it as a passer-by and plays it behind whatever is on the
 * screen, and keeps nothing of it — no place, no mark, no line among the
 * recordings heard. The widget and the notification say what it is. The
 * collection is a box of things chosen; a passer-by is heard at the door.
 */
public final class Visitor extends Activity {

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            pass(getIntent());
        } catch (Exception e) {
            Trace.note("a passer-by was not let in: " + e);
        }
        /* A window without a face must be gone before it would be shown. */
        finish();
    }

    private void pass(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        Uri where = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (where == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0) {
            where = intent.getClipData().getItemAt(0).getUri();
        }
        if (where == null) {
            Trace.note("a passer-by came with nothing to hear");
            return;
        }
        String type = getContentResolver().getType(where);
        if (type == null) {
            type = intent.getType();
        }
        if (type == null || !type.startsWith("audio/")) {
            Trace.note("a passer-by is not a recording: " + type);
            return;
        }
        String name = where.getLastPathSegment();
        Cursor q = null;
        try {
            q = getContentResolver().query(where,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst() && q.getString(0) != null) {
                name = q.getString(0);
            }
        } catch (Exception nameless) {
            Trace.note("a passer-by has no name: " + nameless);
        } finally {
            if (q != null) {
                q.close();
            }
        }
        Intent go = new Intent(this, Sound.class);
        go.setAction(Sound.OPEN);
        go.putExtra("uri", where.toString());
        go.putExtra("name", name == null ? "" : name);
        go.putExtra("mime", type);
        go.putExtra("passing", true);
        /* The leave to read the file was given to this window; it goes on to the player. */
        go.setClipData(ClipData.newRawUri("", where));
        go.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startForegroundService(go);
        Trace.note("hear a passer-by, " + Shelf.kind(name));
    }
}
