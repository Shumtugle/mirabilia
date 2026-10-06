package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Settings that belong to the owner of the phone, not to the source: the
 * seed of the colours, whether it follows the wallpaper, and the room the
 * application was last left in.
 */
public final class Keep {

    private Keep() {
    }

    private static SharedPreferences store(Context context) {
        return context.getSharedPreferences("keep", Context.MODE_PRIVATE);
    }

    /** Hue in degrees and richness from nought to one. */
    public static float[] look(Context context) {
        SharedPreferences kept = store(context);
        return new float[] {kept.getFloat("hue", Tone.HUE), kept.getFloat("rich", Tone.RICH)};
    }

    /**
     * Whether the seed follows the wallpaper. It does until a hand moves a
     * dial: a colour chosen on purpose outranks one arrived at by accident.
     */
    public static boolean wall(Context context) {
        return store(context).getBoolean("wall", true);
    }

    public static void saveLook(Context context, float hue, float rich, boolean wall) {
        store(context).edit().putFloat("hue", hue).putFloat("rich", rich)
            .putBoolean("wall", wall).apply();
    }

    /**
     * How dark the glass of the classic widget is, as an alpha: the clock
     * widget's six steps run from a tenth to nine tenths; four tenths is
     * the one that reads on most wallpapers.
     */
    public static int glass(Context context) {
        return store(context).getInt("glass", 0x66);
    }

    public static void saveGlass(Context context, int alpha) {
        store(context).edit().putInt("glass", alpha).apply();
    }

    /**
     * What sounded last, a book read aloud or a recording, and for a book its
     * key, name, length and pages: what the widget shows and what a key of
     * it wakes when the sound service is gone.
     */
    public static void heardVoice(Context context, String book, String title, int length, int pages) {
        store(context).edit().putBoolean("lastVoice", true).putString("lastKind", "voice")
            .putString("voiceBook", book)
            .putString("voiceTitle", title).putInt("voiceLength", length)
            .putInt("voicePages", pages).apply();
    }

    /** A video watched on its own screen: it, too, is a thing that sounded. */
    public static void heardFilm(Context context) {
        store(context).edit().putBoolean("lastVoice", false).putString("lastKind", "film").apply();
    }

    /** Which of the three sounded last: a book read aloud, a recording, or a video. */
    public static String lastKind(Context context) {
        return store(context).getString("lastKind", "sound");
    }

    public static void heardMusic(Context context) {
        store(context).edit().putBoolean("lastVoice", false).putString("lastKind", "sound").apply();
    }

    public static boolean lastVoice(Context context) {
        return store(context).getBoolean("lastVoice", false);
    }

    public static String voiceBook(Context context) {
        /* The shelf's memory is moved to places before this is read, so the
           book is never asked for by an address it has already left. */
        Shelf.prefs(context);
        return store(context).getString("voiceBook", "");
    }

    /** Whether an opened picture lights the screen at its brightest. */
    static boolean bright(Context context) {
        return store(context).getBoolean("bright", false);
    }

    static void setBright(Context context, boolean on) {
        store(context).edit().putBoolean("bright", on).apply();
    }

    /** Whether the brightness keeps to the clock, and its hours: from one, until another. */
    static boolean brightByClock(Context context) {
        return store(context).getBoolean("brightClock", false);
    }

    static void setBrightByClock(Context context, boolean on) {
        store(context).edit().putBoolean("brightClock", on).apply();
    }

    static int brightFrom(Context context) {
        return store(context).getInt("brightFrom", 8);
    }

    static int brightTo(Context context) {
        return store(context).getInt("brightTo", 21);
    }

    static void setBrightHours(Context context, int from, int to) {
        store(context).edit().putInt("brightFrom", from).putInt("brightTo", to).apply();
    }

    /**
     * Whether the full brightness holds now: always, or within its hours —
     * from the first hour up to the last, over midnight if the first comes
     * after the last.
     */
    static boolean brightNow(Context context) {
        if (!bright(context)) {
            return false;
        }
        if (!brightByClock(context)) {
            return true;
        }
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        int from = brightFrom(context);
        int to = brightTo(context);
        if (from == to) {
            return true;
        }
        return from < to ? (h >= from && h < to) : (h >= from || h < to);
    }

    /**
     * The speed an album's videos play at, as a step of the speeds; one, the
     * second step, is as filmed. The speed belongs to the album, as a
     * recording's pace belongs to its folder.
     */
    static int filmSpeed(Context context, String album) {
        return Math.max(0, Math.min(Film.SPEEDS.length - 1,
            store(context).getInt("filmSpeed:" + album, 1)));
    }

    static void setFilmSpeed(Context context, String album, int step) {
        store(context).edit().putInt("filmSpeed:" + album, step).apply();
    }

    /** Whether a video left while it plays goes on as sound behind the home screen. */
    static boolean filmBehind(Context context) {
        return store(context).getBoolean("filmBehind", false);
    }

    static void setFilmBehind(Context context, boolean on) {
        store(context).edit().putBoolean("filmBehind", on).apply();
    }

    /**
     * When what sounds is to fall silent: the moment itself, or nought for
     * never; and which step of the drum was chosen, so the drum stands where
     * it was left. A sleep set for the end of what is playing has no moment,
     * only the mark that says so.
     */
    static long sleepAt(Context context) {
        return store(context).getLong("sleepAt", 0L);
    }

    static int sleepStep(Context context) {
        return store(context).getInt("sleepStep", 0);
    }

    static boolean sleepEnd(Context context) {
        return store(context).getBoolean("sleepEnd", false);
    }

    static void setSleep(Context context, long at, int step, boolean end) {
        store(context).edit().putLong("sleepAt", at).putInt("sleepStep", step)
            .putBoolean("sleepEnd", end).apply();
    }

    /** What is played again when it ends: nothing, this recording, or the folder it is in. */
    static final int ONCE = 0;
    static final int AGAIN_ONE = 1;
    static final int AGAIN_FOLDER = 2;

    static int repeat(Context context) {
        return store(context).getInt("repeat", ONCE);
    }

    static void setRepeat(Context context, int how) {
        store(context).edit().putInt("repeat", Math.max(0, Math.min(2, how))).apply();
    }

    /** Whether films fill the screen, cropping what does not fit, rather than stand whole in it. */
    static boolean filmFill(Context context) {
        return store(context).getBoolean("filmFill", false);
    }

    static void setFilmFill(Context context, boolean on) {
        store(context).edit().putBoolean("filmFill", on).apply();
    }

    /**
     * The name of the folder the excerpts go to, in the language the first
     * excerpt was made in, and kept so from then: a folder does not change
     * its name when the application changes its language.
     */
    static String excerpts(Context context, String now) {
        String kept = store(context).getString("excerpts", null);
        if (kept == null || kept.length() == 0) {
            kept = now;
            store(context).edit().putString("excerpts", kept).apply();
        }
        return kept;
    }

    /** Whether a question has been put to the person once already, so it is not put again unasked. */
    static boolean asked(Context context, String what) {
        return store(context).getBoolean("asked:" + what, false);
    }

    static void setAsked(Context context, String what) {
        store(context).edit().putBoolean("asked:" + what, true).apply();
    }

    /** The book the voice read last, known from now on by its place rather than its address. */
    static void retellVoiceBook(Context context, String place) {
        store(context).edit().putString("voiceBook", place).commit();
    }

    public static String voiceTitle(Context context) {
        return store(context).getString("voiceTitle", "");
    }

    public static int voiceLength(Context context) {
        return store(context).getInt("voiceLength", 0);
    }

    public static int voicePages(Context context) {
        return store(context).getInt("voicePages", 0);
    }

    /** The widget's ground, key and scale, as the settings chose them. */
    public static int widgetGround(Context context) {
        return store(context).getInt("widgetGround", 0);
    }

    public static int widgetKey(Context context) {
        return store(context).getInt("widgetKey", 0);
    }

    public static int widgetScale(Context context) {
        return store(context).getInt("widgetScale", 0);
    }

    public static void saveWidget(Context context, String what, int value) {
        store(context).edit().putInt(what, value).apply();
    }

    public static int room(Context context) {
        return store(context).getInt("room", 0);
    }

    public static void saveRoom(Context context, int room) {
        store(context).edit().putInt("room", room).apply();
    }
}
