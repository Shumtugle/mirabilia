package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The string table.
 *
 * English lives in the source, keyed by a stable id. Every other tongue lives
 * outside the source, in a module anyone can fill in a text editor and hand
 * to a stranger. A missing key falls back to English silently, so a module is
 * useful from its first line.
 */
public final class Words {

    /** The line a module opens with; it is how a module is told from any other text. */
    public static final String HEAD = "# a language for this collection";

    public static final String[] IDS = {
        "pictures", "films", "music", "books",
        "empty_pictures", "empty_films", "empty_music", "empty_books", "empty_what",
        "settings", "look", "look_what", "hue", "richness", "wallpaper",
        "sw_primary", "sw_secondary", "sw_tertiary", "sw_ground",
        "language", "language_what", "english", "words_of", "module_load", "module_save",
        "use_english", "module_taken", "module_bad", "module_saved", "unsaved",
        "open_folder", "folders_what", "gathering", "on_shelf", "continue", "page_n", "unreadable",
        "mark_page", "unmark_page", "marked", "unmarked", "marks", "no_marks",
        "aloud", "aloud_stop", "turning", "turning_stop",
        "pick",
        "voice_back", "voice_on", "voice_hold", "voice_go",
        "folders", "folders_lost", "folder_there", "folders_joined", "in_folder", "folder_on", "row_of", "stale",
        "share_book", "sounds_whole_what", "order_numbers", "order_time",
        "today", "this_week", "this_month", "this_year", "earlier",
        "photos_n", "all_photos", "allow_photos", "photos_what",
        "videos_n", "all_videos", "allow_videos", "videos_what", "continue_video", "speed", "save_frame", "frame_kept", "sounding", "sounding_what", "film_behind", "film_behind_what", "speed_refused", "paper", "letter_size", "paper_day", "paper_night", "page_sound", "edit_in", "turn",
        "to_excerpts", "excerpts", "excerpt_kept",
        "find_hint", "find_what", "found_n", "found_none", "new_name", "rename", "delete",
        "delete_sure", "renamed", "deleted", "not_done", "read_only", "name_taken", "moved_away",
        "about_picture", "fact_name", "fact_taken", "fact_camera", "fact_lens", "fact_exposure",
        "fact_size", "fact_where", "fact_lies", "mm_n", "mp_n", "mb_n", "kb_n",
        "mend", "mend_light", "mend_frame", "as_is", "for_photo", "for_paper", "copy_kept",
        "mend_geometry", "p_auto", "p_bright", "p_contrast", "p_warm", "p_saturation",
        "p_shape", "p_closer", "p_angle", "p_tilt_v", "p_tilt_h", "p_lens",
        "p_sharp", "p_size", "shoot", "no_camera", "shot_kept", "camera_empty", "free", "leave_unsaved", "frame_n",
        "recut_piece", "recut_words", "tag_title", "tag_artist", "tag_album", "tag_track", "tag_year",
        "words_kept", "to_covers", "cover_taken", "covers_n", "cover_there", "cover_none", "cover",
        "set_cover", "change_cover", "tags_to_text", "card_kept", "cards_n", "card_there",
        "sleep", "sleep_never", "sleep_end", "sleep_not_set", "sleep_at", "sleep_after_this",
        "minutes_n", "again", "again_none", "again_one", "again_folder", "log_before", "whole_shelf",
        "seeing", "seeing_what", "bright", "bright_what", "bright_always", "bright_hours", "bright_from", "bright_to",
        "month_1", "month_2", "month_3", "month_4", "month_5", "month_6",
        "month_7", "month_8", "month_9", "month_10", "month_11", "month_12",
        "deep_on", "deep_off", "forget", "forget_sure",
        "whole", "whole_what", "allow", "on_phone", "on_card",
        "quiet", "quiet_hint", "quiet_off", "page_short",
        "in_room", "continue_sound", "left_at", "sounds_what",
        "done", "copy", "copied", "all_text", "aloud_here", "share",
        "glass", "glass_what", "page_of", "time_of",
        "widget", "widget_what", "ground", "ground_seed", "ground_glass",
        "key_face", "key_ball", "key_disc", "scale", "scale_drum", "scale_thread",
        "log", "log_what", "log_copy",
        "passer_by", "voice_silent",
    };

    private static final String[] EN = {
        "pictures", "videos", "recordings", "books",
        "nothing framed yet", "no reels yet", "silence, for now", "the shelf is bare",
        "what you gather will stand here",
        "settings", "colour", "one seed, and every colour grows from it", "hue", "richness",
        "from the wallpaper",
        "primary", "secondary", "tertiary", "ground",
        "language", "english lives inside; every other language is a module",
        "english", "{n} of {m} words", "load a module", "save the template",
        "use english", "the language is taken", "this is not a language module",
        "the template is saved", "it could not be saved",
        "open a folder", "the rooms are gathered from the folders you open to them",
        "gathering the shelf", "on the shelf: {n}", "still reading", "page {n}",
        "this one cannot be read here",
        "mark this page", "take the mark away", "page marked", "mark taken away", "marks",
        "no marks in this book yet",
        "read aloud", "stop reading aloud", "turn the pages by themselves", "stop turning",
        "pick words",
        "fifteen seconds back", "thirty seconds on", "hold the voice", "go on reading",
        "folders", "a folder was taken back and has left the shelf",
        "this folder is on the shelf already, inside another",
        "folders inside it are now part of it: {n}",
        "recordings: {n}", "at {w}", "{n} of {m}",
        "words whose english has changed since the module was made: {n}",
        "share the book",
        "every recording the phone knows of, less ringtones, notifications, voice messages and "
            + "the recorder; turning it on asks the system for leave to read audio",
        "by number", "by time",
        "today", "this week", "this month", "this year", "earlier",
        "pictures: {n}", "every picture", "show the pictures",
        "the phone's own pictures, by the day each was taken; the system will ask for leave to see them",
        "videos: {n}", "every video", "show the videos",
        "the phone's own videos, by the day each was made; the system will ask for leave to see them",
        "still watching", "speed", "keep the frame", "the frame is among the pictures",
        "sound", "what sounds when the window goes", "videos in the background",
        "leaving a video while it plays, its sound goes on, with the phone's own sound keys; "
            + "back on it, the picture goes on from where the sound got to",
        "this video does not let its speed be changed",
        "paper", "size of the letters", "day", "night", "sound of the pages",
        "edit in\u2026", "turn",
        "into the excerpts", "excerpts", "it is among the excerpts",
        "the name of a file or a folder",
        "words from the name of a file or of its folder; what has every one of them is found",
        "found: {n}", "nothing by that name", "new name", "rename", "delete",
        "again, and it is deleted", "renamed", "deleted", "it could not be done",
        "the folder still allows reading only", "a file of that name is already there",
        "the file is no longer where it was",
        "about the picture", "name", "taken", "camera", "lens", "exposure",
        "size", "where it was taken", "where it lies", "{n} mm", "{n} MP", "{n} MB", "{n} KB",
        "touch up", "light", "frame", "as it is", "photograph", "paper", "kept as a copy",
        "geometry", "automatic", "brightness", "contrast", "warmth", "saturation",
        "shape", "closer", "angle", "lean, up and down", "lean, side to side", "lens",
        "sharpness", "size", "take a picture", "no camera answers on this phone", "taken and kept",
        "the camera gave nothing back", "free", "leave without keeping", "frame {n} of {m}",
        "piece", "words", "title", "artist", "album", "number", "year", "the words are written",
        "to covers", "the cover is in the collection", "covers taken: {n}", "that cover is there already",
        "no cover in it", "cover", "give it a cover", "another cover",
        "tags to text", "the tags are written down", "cards written: {n}", "that card is there already",
        "sleep", "never", "at the end", "not set", "quiet at {t}", "after this one",
        "{n} min", "again", "no", "this one", "the folder", "the run before", "the whole shelf",
        "pictures", "how pictures are shown", "full brightness",
        "an opened picture lights the screen at its brightest, for as long as it is open",
        "always", "by the clock", "from", "until",
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
        "with subfolders", "without subfolders", "forget the folder",
        "press again to forget", "from the whole phone",
        "every book the phone knows of, wherever it lies; turning it on opens the system's "
            + "page for access to every file",
        "allow in the system settings", "phone", "card",
        "read without distractions", "the middle of the page brings everything back",
        "bring everything back", "p. {n}",
        "in the room: {n}", "still listening", "stopped at {t}",
        "recordings are gathered from the folders you open, and from everything inside them",
        "done", "copy", "copied", "the whole page", "read aloud from here", "share",
        "depth of glass", "how dark the widget lies over the wallpaper, in tenths",
        "of {n} pages", "of {t}",
        "widget", "how the widget on the home screen looks", "ground", "the seed", "glass",
        "key", "gold ball", "record", "scale", "drum", "thread",
        "journal", "what the application did, to send when something goes wrong", "copy the journal",
        "passer-by", "the voice did not answer, and reading aloud stopped",
    };

    private static final Map<String, String> TABLE = new LinkedHashMap<String, String>();
    private static final Map<String, String> MINE = new LinkedHashMap<String, String>();
    /** For every word of the module, the English it was translated from, as the module said. */
    private static final Map<String, String> FROM = new LinkedHashMap<String, String>();
    private static String moduleName = "";

    static {
        for (int i = 0; i < IDS.length; i++) {
            TABLE.put(IDS[i], EN[i]);
        }
    }

    private Words() {
    }

    public static String en(String id) {
        String s = TABLE.get(id);
        return s == null ? id : s;
    }

    /** The word as the reader should see it: their module first, English behind it. */
    public static String s(String id) {
        String s = MINE.get(id);
        if (s != null && s.length() > 0) {
            return s;
        }
        return en(id);
    }

    public static String name() {
        return moduleName;
    }

    public static boolean active() {
        return MINE.size() > 0;
    }

    public static int filled() {
        return MINE.size();
    }

    public static int total() {
        return IDS.length;
    }

    public static void forget() {
        MINE.clear();
        FROM.clear();
        moduleName = "";
    }

    /**
     * How many words of the module were translated from an English that has
     * changed since: the key is the same, the meaning moved on, and the old
     * translation still stands until a newer module comes. Unknown for a
     * module loaded before the English was kept with it.
     */
    public static int stale() {
        int n = 0;
        for (Map.Entry<String, String> entry : FROM.entrySet()) {
            if (MINE.containsKey(entry.getKey()) && !entry.getValue().equals(en(entry.getKey()))) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- storage

    public static void load(Context context) {
        SharedPreferences p = context.getSharedPreferences("words", Context.MODE_PRIVATE);
        moduleName = p.getString("!name", "");
        MINE.clear();
        FROM.clear();
        for (int i = 0; i < IDS.length; i++) {
            String v = p.getString(IDS[i], "");
            if (v.length() > 0) {
                MINE.put(IDS[i], v);
            }
            String was = p.getString("~" + IDS[i], null);
            if (was != null) {
                FROM.put(IDS[i], was);
            }
        }
    }

    public static void save(Context context) {
        SharedPreferences.Editor e =
            context.getSharedPreferences("words", Context.MODE_PRIVATE).edit();
        e.clear();
        e.putString("!name", moduleName);
        for (Map.Entry<String, String> entry : MINE.entrySet()) {
            e.putString(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : FROM.entrySet()) {
            e.putString("~" + entry.getKey(), entry.getValue());
        }
        e.apply();
    }

    // ----------------------------------------------------------------- module

    /**
     * A module is plain text, and it carries every line whether filled or not:
     * the English above as a comment, the key and the word below. Written out
     * whole, it can be finished in any editor by someone who has never seen
     * the application, and brought back in.
     */
    public static String write() {
        StringBuilder b = new StringBuilder();
        b.append(HEAD).append('\n');
        b.append("# fill the empty lines and load the file back\n\n");
        b.append("module ").append(moduleName.length() > 0 ? moduleName : "untitled")
            .append("\n\n");
        for (int i = 0; i < IDS.length; i++) {
            String mine = MINE.get(IDS[i]);
            b.append("# ").append(en(IDS[i])).append('\n');
            b.append(IDS[i]).append('\t').append(mine == null ? "" : mine).append("\n\n");
        }
        return b.toString();
    }

    /** Whether a text is a module at all, judged by its first line. */
    public static boolean isModule(String text) {
        if (text == null) {
            return false;
        }
        String t = text.startsWith("\uFEFF") ? text.substring(1) : text;
        return t.trim().startsWith(HEAD);
    }

    public static void read(String text) {
        if (text == null) {
            return;
        }
        MINE.clear();
        FROM.clear();
        moduleName = "";
        String[] lines = text.split("\n");
        /* The English a line was made from stands as a comment just above it. */
        String above = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.length() == 0) {
                continue;
            }
            if (line.charAt(0) == '#') {
                above = line.substring(1).trim();
                continue;
            }
            int cut = line.indexOf('\t');
            if (cut < 0) {
                cut = line.indexOf(' ');
            }
            if (cut <= 0) {
                continue;
            }
            String key = line.substring(0, cut).trim();
            String value = line.substring(cut + 1).trim();
            if (key.equals("module")) {
                moduleName = value;
            } else if (TABLE.containsKey(key) && value.length() > 0) {
                MINE.put(key, value);
                if (above != null) {
                    FROM.put(key, above);
                }
            }
            above = null;
        }
    }
}
