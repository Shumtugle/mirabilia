package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

/* One short sample, loaded once and kept. A MediaPlayer per turn would
   arrive late and out of breath; SoundPool fires from memory. */
final class Snd {

    private static SoundPool pool;
    private static final int[] knocks = new int[3];
    private static int loaded = 0;
    private static final java.util.Random DICE = new java.util.Random();

    private Snd() {
    }

    static boolean on(Context c) {
        return Shelf.prefs(c).getBoolean("turnSound", true);
    }

    static void setOn(Context c, boolean v) {
        Shelf.prefs(c).edit().putBoolean("turnSound", v).apply();
    }

    static void load(Context c) {
        if (pool != null) {
            return;
        }
        try {
            AudioAttributes a = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            pool = new SoundPool.Builder().setMaxStreams(2)
                    .setAudioAttributes(a).build();
            pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                public void onLoadComplete(SoundPool p, int sample, int status) {
                    if (status == 0) {
                        loaded++;
                    }
                    Trace.note("snd_load: " + status + " " + loaded);
                }
            });
            knocks[0] = pool.load(c, R.raw.knock, 1);
            knocks[1] = pool.load(c, R.raw.knock2, 1);
            knocks[2] = pool.load(c, R.raw.knock3, 1);
        } catch (Exception e) {
            pool = null;
        }
    }

    /* Only text pages knock. A picture, a video frame and a comic page have
       no paper to strike, and a sound there is decoration — which on the
       fortieth photograph is simply noise. */
    static void turn(Context c) {
        play(c, knocks[DICE.nextInt(knocks.length)], 0.9f);
    }


    private static void play(Context c, int id, float base) {
        if (pool == null || loaded == 0 || id == 0 || !on(c)) {
            return;
        }
        try {
            float rate = 0.96f + DICE.nextFloat() * 0.08f;
            float vol = base * (0.9f + DICE.nextFloat() * 0.1f);
            if (pool.play(id, vol, vol, 1, 0, rate) == 0) {
                Trace.note("snd_play: " + "0");
            }
        } catch (Exception ignored) {
        }
    }
}
