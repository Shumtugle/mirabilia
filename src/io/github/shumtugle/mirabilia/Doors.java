package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;

import java.util.ArrayList;

/**
 * Doors on the application's own icon: hold it on the home screen and the
 * camera and each room are one touch away, without passing through the one
 * you left.
 *
 * The doors are painted, not drawn from resources: the seed's container is
 * the ground and the room's mark stands on it, so the doors change colour
 * with the look of the whole. They carry the rooms' names in the language
 * in use, and are hung again whenever either changes.
 */
final class Doors {

    /** A launcher crops a door by whatever mask it likes; the mark keeps to the middle third. */
    private static final int SIDE = 216;

    private Doors() {
    }

    static void publish(Context c) {
        try {
            ShortcutManager shelf = c.getSystemService(ShortcutManager.class);
            if (shelf == null) {
                return;
            }
            ArrayList<ShortcutInfo> doors = new ArrayList<ShortcutInfo>();
            /* The first door is the camera's: a picture taken from the home screen lands
               in the collection and opens to be touched up, without the application
               having been opened first. Pictures kept this way need Android 10. */
            int most = shelf.getMaxShortcutCountPerActivity();
            boolean camera = android.os.Build.VERSION.SDK_INT >= 29 && most > 1;
            if (camera) {
                Intent shoot = new Intent(c, Main.class);
                shoot.setAction(Main.SHOOT_DOOR);
                shoot.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                String name = Words.s("shoot");
                doors.add(new ShortcutInfo.Builder(c, "shoot")
                    .setShortLabel(name.length() > 0 ? name : "shoot")
                    .setIcon(Icon.createWithAdaptiveBitmap(paint(Glyph.CAMERA)))
                    .setIntent(shoot)
                    .setRank(0)
                    .build());
            }
            /* Launchers show a handful and no more; the rooms take what is left. */
            int room = Math.min(Main.ROOM_WORDS.length, most - (camera ? 1 : 0));
            for (int i = 0; i < room; i++) {
                Intent go = new Intent(c, Main.class);
                go.setAction(Main.ROOM_DOOR);
                go.putExtra("room", i);
                go.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                String name = Words.s(Main.ROOM_WORDS[i]);
                doors.add(new ShortcutInfo.Builder(c, "room" + i)
                    .setShortLabel(name.length() > 0 ? name : Main.ROOM_WORDS[i])
                    .setIcon(Icon.createWithAdaptiveBitmap(paint(Main.ROOM_MARKS[i])))
                    .setIntent(go)
                    .setRank(i + (camera ? 1 : 0))
                    .build());
            }
            /* Cleared and set again, not only set: a launcher keeps an old
               door by its id and would go on showing yesterday's colour. */
            shelf.removeAllDynamicShortcuts();
            shelf.setDynamicShortcuts(doors);
        } catch (Exception refused) {
            /* Some launchers refuse; a door that will not hang is not a fault. */
            Trace.note("doors not hung: " + refused);
        }
    }

    private static Bitmap paint(int kind) {
        Bitmap made = Bitmap.createBitmap(SIDE, SIDE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        canvas.drawColor(Tone.of(Tone.PRIMARY_CONTAINER));
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = SIDE * 0.34f / 24f;
        Glyph.pens(pen, solid, unit);
        pen.setColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
        solid.setColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
        cut.setColor(Tone.of(Tone.PRIMARY_CONTAINER));
        Glyph.mark(canvas, kind, SIDE / 2f, SIDE / 2f, unit, pen, solid, cut);
        return made;
    }
}
