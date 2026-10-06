package io.github.shumtugle.mirabilia;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * The widget on the home screen. The drawing is the desk's; this class
 * receives the widget's keys.
 *
 * A key does not start the sound service by itself. Android 12 and later
 * refuse to start a foreground service from the background, and a key that
 * throws looks broken. So each key is a broadcast to this receiver: while
 * the service lives, the receiver speaks to it directly — to the voice if a
 * book is being read aloud, to the player otherwise; when it does not, the
 * window is opened with the key's wish, which is always allowed, and the
 * window wakes the service.
 */
public final class Widget extends AppWidgetProvider {

    static final String KEY = "io.github.shumtugle.mirabilia.WIDGET_KEY";
    static final String WISH = "wish";

    @Override
    public void onUpdate(Context c, AppWidgetManager widgets, int[] ids) {
        Desk.push(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager widgets, int id,
                                          Bundle options) {
        Desk.push(c);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (!KEY.equals(intent.getAction())) {
            return;
        }
        String wish = intent.getStringExtra(WISH);
        Sound s = Sound.live();
        if (s == null) {
            try {
                c.startActivity(new Intent(c, Main.class).setAction(wish)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            } catch (Exception refused) {
                Trace.note("widget: the window would not open: " + refused);
            }
            return;
        }
        if (Sound.TOGGLE.equals(wish)) {
            s.toggle();
        } else if (Sound.BACK.equals(wish)) {
            s.nudge(-Sound.BACK_MS);
        } else if (Sound.ON.equals(wish)) {
            s.nudge(Sound.ON_MS);
        } else if (Sound.NEXT.equals(wish)) {
            s.next();
        } else if (Sound.PREV.equals(wish)) {
            s.prev();
        }
        Desk.push(c);
    }

    /** A key of the widget: a broadcast to this receiver carrying one wish. */
    static PendingIntent key(Context c, String wish) {
        Intent i = new Intent(c, Widget.class).setAction(KEY).putExtra(WISH, wish);
        return PendingIntent.getBroadcast(c, wish.hashCode(), i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
