package io.github.shumtugle.mirabilia;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Outline;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * One room at a time, and one round button.
 *
 * Pictures, films, music and books each have a room, but only the room you
 * are in is named on the screen: its name stands in the field at the foot,
 * where an address would stand, and the other three wait behind the round
 * button, as capsules rising out of it. Someone reading books is not shown
 * films all the while.
 *
 * The mark in the upper corner is the way into the settings, from wherever
 * a piece of the collection is in front: a room, a book, a recording. A
 * screen of ours has the round button as its only door out, one step back
 * at a time, and while it is open the field names the screen instead of the
 * room — unless something sounds under it, a book read aloud or a
 * recording, whose strip keeps its place in the bar.
 *
 * Colour, shape, type and movement are not chosen here. They come from
 * four small classes that hold the rules, so a room cannot drift from the
 * others by accident: a new colour is a new role, not a new number.
 */
public final class Main extends Activity {

    static final String[] ROOM_WORDS = {"pictures", "films", "music", "books"};
    private static final String[] ROOM_EMPTY = {
        "empty_pictures", "empty_films", "empty_music", "empty_books",
    };
    private static final String[] ROOM_NUMBERS = {"I", "II", "III", "IV"};
    static final int[] ROOM_MARKS = {Glyph.PICTURE, Glyph.FILM, Glyph.NOTE, Glyph.BOOK};
    /** Each room's shape: how many corners it has and how deep it dips between them. */
    private static final int[] CORNERS = {9, 4, 6, 5};
    private static final float[] DEPTHS = {0.07f, 0.16f, 0.1f, 0.12f};
    private static final int[] BODIES = {
        Tone.PRIMARY_CONTAINER, Tone.TERTIARY_CONTAINER,
        Tone.SECONDARY_CONTAINER, Tone.SURFACE_HIGHEST,
    };
    private static final int[] INKS = {
        Tone.ON_PRIMARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER,
        Tone.ON_SECONDARY_CONTAINER, Tone.PRIMARY,
    };

    static final int LOAD_MODULE = 1;
    static final int SAVE_FORM = 2;
    private static final int OPEN_TREE = 3;
    static final int PICTURES = 0;
    static final int FILMS = 1;
    static final int MUSIC = 2;
    static final int BOOKS = 3;

    static final String SETTINGS = "settings";
    static final String LOOK = "look";
    static final String LANGUAGE = "language";
    static final String READING = "reading";
    static final String FOLDERS = "folders";
    static final String LISTENING = "listening";
    static final String FOLDER = "folder";
    static final String ALBUM = "album";
    static final String PHOTO = "photo";
    static final String FILM = "film";
    static final String WIDGET = "widget";
    static final String LOG = "log";
    static final String SEEING = "seeing";
    static final String SOUNDING = "sounding";
    /**
     * The rungs a name that came from outside may stand on at the head of a
     * screen. A room's name is ours and fits; a folder's is not, so it steps
     * down the ladder until it stands in one line, and no further.
     */
    private static final int[] PLATE = {Letter.HEADLINE_M, Letter.HEADLINE_S, Letter.TITLE_L};
    /** The screens behind the mark in the corner, built away from the rooms. */
    private final Settings settings = new Settings(this);
    /** The action a door on the home screen icon opens the application with. */
    static final String ROOM_DOOR = "io.github.shumtugle.mirabilia.ROOM";
    /** The camera's door on the icon: a picture taken for the collection, and touched up at once. */
    static final String SHOOT_DOOR = "io.github.shumtugle.mirabilia.SHOOT";

    static final int TONAL = 0;
    static final int OUTLINED = 1;
    static final int PLAIN = 2;

    private LinearLayout root;
    private FrameLayout stage;
    private View scene;
    private ScrollView scroller;
    private View shade;
    private LinearLayout pills;
    private LinearLayout note;
    private LinearLayout bar;
    private LinearLayout field;
    private Glyph hereMark;
    private TextView hereWord;
    private Blob blob;
    private Crest crest;

    private int room;
    /** The screens of ours open over the room, the one in front last. */
    private final ArrayList<String> trail = new ArrayList<String>();
    private boolean spread;

    /** The recordings of the music room, gathered like the shelf and kept the same way. */
    private final ArrayList<Shelf.Item> sounds = new ArrayList<Shelf.Item>();
    boolean soundsGathered;
    private boolean soundsGathering;
    /** The player's strip, and whether the dial in front belongs to it rather than to the voice. */
    private Voice tune;
    private boolean tuneDial;
    /** The recording the listening screen stands for. */
    private Shelf.Item listening;
    private boolean askedNotes;

    /** The shelf of the books room, gathered once and again when a folder is added. */
    private final ArrayList<Shelf.Item> books = new ArrayList<Shelf.Item>();
    boolean gathered;
    private boolean gathering;

    /** The book in front, and whichever of the three pages is showing it. */
    private Shelf.Item book;
    private View reading;
    private Reader.Page leaf;
    private Reader.Pdf pdf;
    private Reader.Cbz cbz;
    /** Where a long press landed, for the voice to start from. */
    private int aloudFrom = -1;
    /** Whether the service's voice is reading the book in front, and the paragraph it was on. */
    private boolean speaking;
    private int heardAt = -1;
    private Voice strip;
    private android.animation.ValueAnimator barFlow;
    /** The dial, while the voice's words are held, and how fast its edges keep it turning. */
    private Arc arc;
    private int dialTick = -1;
    private float edgeSpeed;
    private boolean turning;
    final android.os.Handler clock = new android.os.Handler();
    /** The words of a page laid out to be picked, over the page; the text itself, and where it begins in the book. */
    private View sheet;
    private TextView picked;
    private int pickedFrom;
    private android.view.ActionMode picking;
    /** Reading with nothing else on the screen: no bar, no phone bars, paper to the edges. */
    private boolean quiet;

    /** Words on the scene in front, each with the role its ink comes from. */
    private final ArrayList<TextView> inked = new ArrayList<TextView>();
    private final ArrayList<Integer> inkRoles = new ArrayList<Integer>();
    /** The colour specimen: each swatch with its fill role and its ink role. */

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Trace.watch(getApplicationContext());
        Round.measure(this);
        Tone.read(this);
        Words.load(this);
        Reader.theme(this);
        Snd.load(this);
        room = Math.max(0, Math.min(ROOM_WORDS.length - 1, Keep.room(this)));
        int lost = Shelf.dropLost(this);
        /* The shelf as it was last time, at once; the walk corrects it behind the screen. */
        books.addAll(Shelf.kept(this));
        gathered = !books.isEmpty();
        sounds.addAll(Shelf.keptSounds(this));
        soundsGathered = !sounds.isEmpty();
        Integer asked = asked(getIntent());
        if (asked != null) {
            room = asked.intValue();
            filmAsked = getIntent().getStringExtra("film");
            bookAsked = getIntent().getStringExtra("book");
        }
        build();
        swap(roomView(room));
        if (gathered || soundsGathered) {
            gather();
        }
        Sound.listen(new Sound.Ear() {
            public void heard() {
                syncTune(true);
                voiceShown();
            }

            public void spoke() {
                voiceShown();
            }
        });
        syncTune();
        /* A cover fetched for the widget reaches the picture on its screen too. */
        Desk.onLabel(new Runnable() {
            public void run() {
                settings.showWidget(Pace.ARRIVE);
            }
        });
        if (lost > 0) {
            flash(Glyph.BOOK, Words.s("folders_lost"));
        }
        if (!wished(getIntent())) {
            handed(getIntent());
        }
        if (bookAsked != null) {
            openAskedBook();
        }
        if (Shelf.prefs(this).getString("shot", null) != null) {
            Trace.note("camera: a place was waiting from before");
        }
        if (saved == null && SHOOT_DOOR.equals(getIntent().getAction())) {
            enter(PICTURES);
            shoot(false);
        }
        Doors.publish(this);
        Trace.note("started in room " + room);
    }

    // ------------------------------------------------------------------ build

    private void build() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        stage = new FrameLayout(this);
        stage.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View view, Outline shape) {
                shape.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                    quiet ? 0f : Round.px(Round.XL));
            }
        });
        stage.setClipToOutline(true);
        LinearLayout.LayoutParams stageParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        stageParams.setMargins(Round.dp(6), Round.dp(4), Round.dp(6), 0);
        root.addView(stage, stageParams);

        crest = new Crest(this, Round.px(56f));
        crest.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (!spread && sheet == null && !setting(front())
                    && (!quiet || (FILM.equals(front()) && filmChrome)
                        || (PHOTO.equals(front()) && photoCaption))) {
                    door(SETTINGS);
                }
            }
        });
        FrameLayout.LayoutParams crestParams = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP | Gravity.END);
        /* The corner is the end of the line, not the right of the screen: in a
           writing that runs the other way the mark and the gap left for it
           must move together, and a margin nailed to the right would not. */
        crestParams.setMargins(0, Round.dp(12), 0, 0);
        crestParams.setMarginEnd(Round.dp(12));
        stage.addView(crest, crestParams);

        shade = new View(this);
        shade.setVisibility(View.GONE);
        shade.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                calm();
            }
        });
        stage.addView(shade, cover());

        pills = new LinearLayout(this);
        pills.setOrientation(LinearLayout.VERTICAL);
        pills.setGravity(Gravity.END);
        pills.setPadding(Round.dp(16), Round.dp(16), Round.dp(16), Round.dp(12));
        pills.setClipToPadding(false);
        pills.setVisibility(View.GONE);
        stage.addView(pills, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.END));

        bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));

        /* Where an address would stand: the name of the room, or of the
           screen of ours in front. Touched, it takes the room back to its top. */
        field = new LinearLayout(this);
        field.setOrientation(LinearLayout.HORIZONTAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(Round.dp(16), 0, Round.dp(8), 0);
        field.setMinimumHeight(Round.dp(56));
        field.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                fieldTouched();
            }
        });
        hereMark = new Glyph(this, ROOM_MARKS[room], Round.px(24f), 0.92f,
            0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams hereMarkParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hereMarkParams.rightMargin = Round.dp(14);
        field.addView(hereMark, hereMarkParams);
        hereWord = Letter.set(new TextView(this), Letter.BODY_L);
        hereWord.setSingleLine(true);
        hereWord.setEllipsize(android.text.TextUtils.TruncateAt.END);
        field.addView(hereWord, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        /* The same field takes words: what to look for, or a file's new name. */
        ask = new android.widget.EditText(this);
        Letter.set(ask, Letter.BODY_L);
        ask.setSingleLine(true);
        ask.setBackground(null);
        ask.setPadding(0, 0, 0, 0);
        ask.setVisibility(View.GONE);
        ask.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence t, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence t, int a, int b, int c) {
            }

            public void afterTextChanged(android.text.Editable t) {
                if (asking == ASK_FIND) {
                    query = t.toString();
                    clock.removeCallbacks(refind);
                    clock.postDelayed(refind, 180L);
                }
            }
        });
        ask.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int action, android.view.KeyEvent e) {
                if (asking == ASK_NAME) {
                    commitName(ask.getText().toString());
                } else {
                    keyboard(false);
                }
                return true;
            }
        });
        field.addView(ask, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(field, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        blob = new Blob(this, Round.px(56f), Tone.of(Tone.PRIMARY_CONTAINER),
            Tone.of(Tone.ON_PRIMARY_CONTAINER));
        blob.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if (sheet != null) {
                    closeSheet();
                } else if (spread) {
                    calm();
                } else if (asking == ASK_NAME) {
                    /* Its mark says done, so it keeps the new name; back puts it away. */
                    commitName(ask.getText().toString());
                } else if (asking != ASK_NONE) {
                    endAsk();
                } else if (speaking && READING.equals(front())) {
                    /* Reading aloud, the button first puts the voice away,
                       keeping its place; a second press leaves the book. */
                    hush();
                } else if (open()) {
                    back();
                } else {
                    menu();
                }
            }
        });
        LinearLayout.LayoutParams blobParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blobParams.leftMargin = Round.dp(4);
        bar.addView(blob, blobParams);

        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.setMargins(Round.dp(8), Round.dp(8), Round.dp(8), Round.dp(10));
        root.addView(bar, barParams);

        retone();
        plate(false);
        setContentView(root);
        fitBars(root);
    }

    private static FrameLayout.LayoutParams cover() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private boolean open() {
        return !trail.isEmpty();
    }

    String front() {
        return trail.isEmpty() ? null : trail.get(trail.size() - 1);
    }

    /**
     * Whether a screen is one of the settings. A book or a recording is not:
     * it holds a piece of the collection, as a room does, and the way into
     * the settings stays open over it.
     */
    private static boolean setting(String screen) {
        return screen != null && !READING.equals(screen) && !LISTENING.equals(screen)
            && !FOLDER.equals(screen) && !ALBUM.equals(screen) && !PHOTO.equals(screen)
            && !FILM.equals(screen);
    }

    /** What the screens of the settings are open over: a book, a recording, or — null — the room. */
    private String under() {
        for (int i = trail.size() - 1; i >= 0; i--) {
            if (!setting(trail.get(i))) {
                return trail.get(i);
            }
        }
        return null;
    }

    /**
     * The mark stands over whatever holds the collection and steps aside
     * for the screens of the settings, which it is the way into, and for a
     * page read with nothing else on it. Over the paper of a book it is
     * printed in the ink of the running head, in the head's own line;
     * anywhere else it is lit on its disc. When it has to change, it leaves
     * and comes back changed, rather than changing in place.
     */
    private void placeCrest(boolean moving) {
        String screen = front();
        /* Over a picture or a video the mark comes and goes with the label or
           the strip: the way to the settings and the journal stands where
           the words do, and a bare picture has no disc on it. */
        final boolean want = !setting(screen)
            && (FILM.equals(screen) ? filmChrome
                : PHOTO.equals(screen) ? photoCaption : !quiet);
        final boolean print = READING.equals(screen);
        crest.animate().cancel();
        boolean shown = crest.getVisibility() == View.VISIBLE;
        if (shown && want && crest.printed() == print) {
            crest.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(0L)
                .setDuration(Pace.PRESS).setInterpolator(Pace.STANDARD).start();
            return;
        }
        if (!moving || !shown) {
            settleCrest(want, print);
            if (want && moving) {
                crest.setAlpha(0f);
                crest.setScaleX(0.8f);
                crest.setScaleY(0.8f);
                crest.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(70L)
                    .setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
            } else {
                crest.setAlpha(1f);
                crest.setScaleX(1f);
                crest.setScaleY(1f);
            }
            return;
        }
        crest.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    settleCrest(want, print);
                    if (want) {
                        crest.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(70L)
                            .setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
                    }
                }
            }).start();
    }

    /**
     * The mark put in its place at once. Printed, it stands at the right end
     * of the running head: its middle on the head's line, its edge on the
     * edge of the column of text.
     */
    private void settleCrest(boolean want, boolean print) {
        crest.printed(print, Reader.FAINT);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) crest.getLayoutParams();
        if (print) {
            params.setMargins(0, -Round.dp(10), Round.dp(10), 0);
        } else {
            params.setMargins(0, Round.dp(12), Round.dp(12), 0);
        }
        crest.setLayoutParams(params);
        crest.setVisibility(want ? View.VISIBLE : View.GONE);
    }

    private int markOf(String screen) {
        if (READING.equals(screen)) {
            return Glyph.BOOK;
        }
        if (LISTENING.equals(screen)) {
            return Glyph.NOTE;
        }
        if (WIDGET.equals(screen)) {
            return Glyph.SPARK;
        }
        if (LOG.equals(screen)) {
            return Glyph.CARET;
        }
        if (LOOK.equals(screen)) {
            return Glyph.LOOK;
        }
        if (LANGUAGE.equals(screen)) {
            return Glyph.LANGUAGE;
        }
        if (FOLDERS.equals(screen)) {
            return Glyph.BOOK;
        }
        if (SEEING.equals(screen)) {
            return Glyph.PICTURE;
        }
        if (SOUNDING.equals(screen)) {
            return Glyph.NOTE;
        }
        if (FOLDER.equals(screen)) {
            return Glyph.NOTE;
        }
        if (ALBUM.equals(screen) || PHOTO.equals(screen)) {
            return albumRoom == FILMS ? Glyph.FILM : Glyph.PICTURE;
        }
        if (FILM.equals(screen)) {
            return Glyph.FILM;
        }
        return Glyph.SETTINGS;
    }

    /**
     * The field says where you are. When that changes, the old name leaves
     * upward and the new one rises into its place, so the eye sees that it
     * changed and not only what it says now.
     */
    void plate(boolean moving) {
        brightness();
        boolean typing = asking == ASK_NAME || (asking == ASK_FIND && !open());
        if (ask != null) {
            ask.setVisibility(typing ? View.VISIBLE : View.GONE);
            hereWord.setVisibility(typing ? View.GONE : View.VISIBLE);
        }
        String screen = front();
        final String word = screen == null ? Words.s(ROOM_WORDS[room])
            : READING.equals(screen) && book != null ? Shelf.title(book.name)
            : LISTENING.equals(screen) && listening != null ? Shelf.label(listening)
            : FOLDER.equals(screen) && !folder.isEmpty() ? Shelf.plate(Shelf.homeTitle(folder.get(0)))
            : ALBUM.equals(screen) ? Shelf.plate(albumName)
            : PHOTO.equals(screen) && photoAt < album.size() ? Gallery.day(album.get(photoAt).taken)
            : FILM.equals(screen) && photoAt < album.size() ? Shelf.title(album.get(photoAt).name)
            : Words.s(screen);
        final int kind = screen == null ? ROOM_MARKS[room] : markOf(screen);
        hereWord.animate().cancel();
        hereMark.animate().cancel();
        if (!moving) {
            hereWord.setText(word);
            hereMark.kind(kind);
            hereWord.setAlpha(1f);
            hereMark.setAlpha(1f);
            hereWord.setTranslationY(0f);
            hereMark.setTranslationY(0f);
            return;
        }
        hereMark.animate().alpha(0f).translationY(-Round.px(8f)).setStartDelay(0L).setDuration(Pace.PRESS)
            .setInterpolator(Pace.AWAY).start();
        hereWord.animate().alpha(0f).translationY(-Round.px(8f)).setStartDelay(0L).setDuration(Pace.PRESS)
            .setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    hereWord.setText(word);
                    hereMark.kind(kind);
                    hereWord.setTranslationY(Round.px(10f));
                    hereMark.setTranslationY(Round.px(10f));
                    hereWord.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
                        .setInterpolator(Pace.STANDARD).start();
                    hereMark.animate().alpha(1f).translationY(0f).setDuration(Pace.ARRIVE)
                        .setInterpolator(Pace.STANDARD).start();
                }
            }).start();
    }

    /** Keeps the application's own edges clear of the phone's bars. */
    private void fitBars(final View view) {
        view.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                int top;
                int bottom;
                if (Build.VERSION.SDK_INT >= 30) {
                    android.graphics.Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars());
                    top = bars.top;
                    bottom = bars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                }
                v.setPadding(0, top, 0, bottom);
                return insets;
            }
        });
        view.requestApplyInsets();
    }

    /** Every lasting part in the colours of the moment. */
    void retone() {
        int ground = Tone.of(Tone.SURFACE_LOWEST);
        root.setBackgroundColor(ground);
        getWindow().setStatusBarColor(ground);
        getWindow().setNavigationBarColor(ground);
        stage.setBackground(Round.box(Tone.of(Tone.SURFACE_LOW), Round.XL));
        shade.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.72f));
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        field.setBackground(Round.touch(null, Tone.of(Tone.ON_SURFACE), Round.FULL));
        hereWord.setTextColor(Tone.of(Tone.ON_SURFACE));
        hereMark.tint(0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
        crest.tint();
        blob.tint(Tone.of(Tone.PRIMARY_CONTAINER), Tone.of(Tone.ON_PRIMARY_CONTAINER));
        for (int i = 0; i < inked.size(); i++) {
            inked.get(i).setTextColor(Tone.of(inkRoles.get(i)));
        }
        settings.paintLook();
    }

    // ------------------------------------------------------------------ rooms

    private void toRoom(int which) {
        if (open() || which == room) {
            return;
        }
        room = which;
        Keep.saveRoom(this, which);
        forgetScene();
        swap(roomView(which));
        plate(true);
        syncTune();
        Trace.note("room " + which);
    }

    /** The room in front goes back to its top, as a second touch on its name asks. */
    private void top() {
        if (scroller != null && !open()) {
            scroller.smoothScrollTo(0, 0);
        }
    }

    // ------------------------------------------------------------------ looking and naming

    private static final int ASK_NONE = 0;
    private static final int ASK_FIND = 1;
    private static final int ASK_NAME = 2;
    /** The field's words: what is looked for, or a file's new name. */
    private android.widget.EditText ask;
    private int asking = ASK_NONE;
    private String query;
    /** The file being named, or asked about by the system, and the name or change asked for. */
    private Shelf.Item changing;
    private String changingTo;
    private static final int ASK_WRITE = 26;
    private static final int ASK_REMOVE = 27;
    private final Runnable refind = new Runnable() {
        public void run() {
            if (asking == ASK_FIND && !open()) {
                replace(roomView(room));
            }
        }
    };

    /**
     * The field touched: a room scrolled down goes back to its top; at its
     * top, the field takes words — what to look for in this room, by the
     * name of a file or of a folder.
     */
    private void fieldTouched() {
        if (asking != ASK_NONE) {
            keyboard(true);
            return;
        }
        if (open()) {
            return;
        }
        if (scroller != null && scroller.getScrollY() > 0) {
            top();
            return;
        }
        asking = ASK_FIND;
        query = "";
        ask.setText("");
        ask.setHint(Words.s("find_hint"));
        ask.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        plate(false);
        blob.leaving(true);
        replace(roomView(room));
        keyboard(true);
        Trace.note("looking in room " + room);
    }

    private void endAsk() {
        int was = asking;
        asking = ASK_NONE;
        query = null;
        changing = null;
        clock.removeCallbacks(refind);
        keyboard(false);
        plate(false);
        blob.leaving(open());
        if (was == ASK_FIND && !open()) {
            replace(roomView(room));
        }
        if (was == ASK_NAME && (PHOTO.equals(front()) || FILM.equals(front()))) {
            quietOn(false);
        }
    }

    private void keyboard(boolean up) {
        android.view.inputmethod.InputMethodManager keys = (android.view.inputmethod.InputMethodManager)
            getSystemService(INPUT_METHOD_SERVICE);
        if (keys == null) {
            return;
        }
        if (up) {
            ask.requestFocus();
            keys.showSoftInput(ask, 0);
        } else {
            ask.clearFocus();
            keys.hideSoftInputFromWindow(ask.getWindowToken(), 0);
        }
    }

    /** A name made comparable: lower case, and the two ways of writing the same letter made one. */
    private static String plain(String s) {
        return s == null ? "" : s.toLowerCase(java.util.Locale.ROOT).replace('\u0451', '\u0435')
            .replace('_', ' ');
    }

    /** Whether every word looked for is somewhere in these names. */
    private static boolean found(String[] words, String... names) {
        StringBuilder all = new StringBuilder();
        for (int i = 0; i < names.length; i++) {
            all.append(plain(names[i])).append('\n');
        }
        String hay = all.toString();
        for (int i = 0; i < words.length; i++) {
            if (words[i].length() > 0 && hay.indexOf(words[i]) < 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * What was found in a room by name: the books whose name or folder has
     * every word looked for; the folders and recordings; the albums, and the
     * pictures or videos by their own names. Rows as the room's own, so a
     * touch does what it does there.
     */
    private View findView(int which) {
        LinearLayout column = column();
        String q = plain(query == null ? "" : query).trim();
        if (q.length() == 0) {
            TextView hint = words(Letter.BODY_M, Words.s("find_what"), Tone.ON_SURFACE_VARIANT);
            column.addView(hint, wide());
            return scroll(column);
        }
        String[] want = q.split("\\s+");
        ArrayList<View> rows = new ArrayList<View>();
        if (which == BOOKS) {
            for (int i = 0; i < books.size() && rows.size() < 200; i++) {
                Shelf.Item it = books.get(i);
                if (found(want, Shelf.title(it.name), it.folder)) {
                    rows.add(bookRow(it, false));
                }
            }
        } else if (which == MUSIC) {
            ArrayList<ArrayList<Shelf.Item>> homes = Shelf.homes(sounds);
            ArrayList<Shelf.Item> heard = Shelf.current(Shelf.recentSounds(this, 30), sounds);
            for (int i = 0; i < homes.size() && rows.size() < 200; i++) {
                ArrayList<Shelf.Item> here = homes.get(i);
                if (here.size() > 1 && found(want, Shelf.homeTitle(here.get(0)))) {
                    rows.add(folderRow(here, heard));
                }
            }
            for (int i = 0; i < sounds.size() && rows.size() < 200; i++) {
                Shelf.Item it = sounds.get(i);
                if (found(want, Shelf.label(it), it.name)) {
                    rows.add(soundRow(it, false));
                }
            }
        } else {
            ArrayList<Gallery.Album> by = albumsOf(which);
            for (int i = 0; i < by.size() && rows.size() < 200; i++) {
                if (found(want, by.get(i).name)) {
                    rows.add(albumRow(by.get(i).name, by.get(i).photos, which));
                }
            }
            ArrayList<Gallery.Photo> all = itemsOf(which);
            for (int i = 0; i < all.size() && rows.size() < 200; i++) {
                if (found(want, all.get(i).name)) {
                    rows.add(mediaRow(all.get(i), which));
                }
            }
        }
        TextView count = words(Letter.LABEL_L, rows.isEmpty() ? Words.s("found_none")
            : Words.s("found_n").replace("{n}", String.valueOf(rows.size())), Tone.PRIMARY);
        column.addView(count, wide());
        for (int i = 0; i < rows.size(); i++) {
            LinearLayout.LayoutParams params = wide();
            params.topMargin = Round.dp(i == 0 ? 12 : 6);
            column.addView(rows.get(i), params);
        }
        return scroll(column);
    }

    /** A picture or a video found by its name: its small picture, its name, its day. */
    private LinearLayout mediaRow(final Gallery.Photo p, final int which) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openMedia(p, which);
            }
        });
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        face.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.S));
        face.setClipToOutline(true);
        Gallery.into(face, p);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(Round.dp(56), Round.dp(56));
        faceParams.rightMargin = Round.dp(14);
        row.addView(face, faceParams);
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(Letter.TITLE_M, p.name, Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        said.addView(named);
        said.addView(words(Letter.BODY_S, Shelf.plate(p.album) + "  \u00B7  " + Gallery.day(p.taken),
            Tone.ON_SURFACE_VARIANT));
        row.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** A picture or a video opened from what was found: its album stands behind it. */
    private void openMedia(Gallery.Photo p, int which) {
        ArrayList<Gallery.Album> by = albumsOf(which);
        for (int a = 0; a < by.size(); a++) {
            ArrayList<Gallery.Photo> list = by.get(a).photos;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == p.id) {
                    album = list;
                    albumName = by.get(a).name;
                    albumRoom = which;
                    photoAt = i;
                    keyboard(false);
                    door(p.video ? FILM : PHOTO);
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ renaming and deleting

    /** The file's name, in the field, to be changed; its ending stays as it is. */
    private void askName(Shelf.Item it) {
        calm();
        if (quiet) {
            quietOff();
        }
        changing = it;
        asking = ASK_NAME;
        ask.setText(Change.stem(it.name));
        ask.setHint(Words.s("new_name"));
        ask.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        ask.selectAll();
        plate(false);
        blob.leaving(true);
        keyboard(true);
    }

    private void commitName(String typed) {
        final Shelf.Item it = changing;
        String stem = typed == null ? "" : typed.trim().replace('/', '_');
        if (it == null || stem.length() == 0) {
            endAsk();
            return;
        }
        String end = Change.ending(it.name);
        String to = stem.toLowerCase(java.util.Locale.ROOT).endsWith(end.toLowerCase(java.util.Locale.ROOT))
            ? stem : stem + end;
        if (to.equals(it.name)) {
            endAsk();
            return;
        }
        String[] placed = new String[2];
        int how = Change.rename(this, it, to, placed);
        if (how == Change.ASK) {
            changingTo = to;
            try {
                android.app.PendingIntent consent = MediaStore.createWriteRequest(getContentResolver(),
                    java.util.Collections.singletonList(it.uri));
                startIntentSenderForResult(consent.getIntentSender(), ASK_WRITE, null, 0, 0, 0);
                Trace.note("rename asked of the system");
            } catch (Exception refused) {
                Trace.note("rename not asked: " + refused.getClass().getSimpleName());
                flash(Glyph.CROSS, Words.s("not_done"));
            }
            asking = ASK_NONE;
            keyboard(false);
            plate(false);
            blob.leaving(open());
            if (PHOTO.equals(front()) || FILM.equals(front())) {
                quietOn(false);
            }
            return;
        }
        endAsk();
        settleName(it, to, how, placed, true);
    }

    /**
     * What a rename came to: done; a folder opened for reading only, which is
     * asked to be opened again for writing, once; or a refusal, said in words.
     * Answers whether it was done.
     */
    private boolean settleName(Shelf.Item it, String to, int how, String[] placed, boolean mayReopen) {
        if (how == Change.DONE) {
            renamed(it, placed[0], to, placed[1]);
            return true;
        }
        if (how == Change.READ_ONLY && mayReopen) {
            reopen(it, to);
            return false;
        }
        if (how == Change.GONE) {
            lost(it);
            return false;
        }
        flash(Glyph.CROSS, Words.s(how == Change.TAKEN ? "name_taken"
            : how == Change.READ_ONLY ? "read_only" : "not_done"));
        return false;
    }

    /**
     * A file no longer where it was known to lie: it leaves the lists of what
     * was read, heard and watched, the rooms are walked again, and it is said.
     */
    private void lost(Shelf.Item it) {
        Shelf.forgetRemembered(this, it);
        flash(Glyph.CROSS, Words.s("moved_away"));
        afterChange();
    }

    // ------------------------------------------------------------------ a folder opened again

    /** A folder opened again for writing, to finish a change it refused. */
    private static final int ASK_REOPEN = 29;
    /** The file waiting for its folder, and the name it waits to take; no name means it waits to be deleted. */
    private Shelf.Item reopening;
    private String reopeningTo;

    /**
     * A folder opened for reading only refuses a change; rather than send the
     * person to find it, the system's folder picker is opened where the file
     * lies, asking for writing as well, and the change is finished when it
     * comes back.
     */
    private void reopen(Shelf.Item it, String to) {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        Uri start = Change.folderOf(it.uri);
        if (start != null) {
            pick.putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, start);
        }
        reopening = it;
        reopeningTo = to;
        try {
            startActivityForResult(pick, ASK_REOPEN);
            Trace.note("folder asked for again, for writing");
        } catch (Exception none) {
            reopening = null;
            reopeningTo = null;
            Trace.note("no folder picker: " + none.getClass().getSimpleName());
            flash(Glyph.CROSS, Words.s("read_only"));
        }
    }

    /** Back from the folder picker with a folder for a waiting change. */
    private void reopened(Uri where) {
        Shelf.Item it = reopening;
        String to = reopeningTo;
        reopening = null;
        reopeningTo = null;
        if (it == null) {
            /* The window was made anew while the picker was open: the folder
               is still kept, the change is not guessed at. */
            keepTree(where, true);
            return;
        }
        keepTree(where, false);
        Uri again = Change.through(where, it.uri);
        boolean done = false;
        if (again == null) {
            Trace.note("the folder opened again does not hold the file");
            flash(Glyph.CROSS, Words.s("read_only"));
        } else {
            Shelf.Item same = new Shelf.Item(it.name, again, it.mime, it.size, it.modified,
                it.folder, it.place);
            if (to == null) {
                done = removeNow(same, false);
            } else {
                String[] placed = new String[2];
                done = settleName(same, to, Change.rename(this, same, to, placed), placed, false);
            }
        }
        if (!done) {
            /* Whatever the change came to, the folder may be new on the shelf. */
            settings.regather();
        }
    }

    private void renamed(Shelf.Item it, String place, String to, String uri) {
        Shelf.movePlace(this, it.key(), place);
        Shelf.renameRemembered(this, it, place == null || place.length() == 0 ? it.place : place, to, uri);
        Trace.note("renamed a file");
        flash(Glyph.TICK, Words.s("renamed"));
        afterChange();
    }

    private void remove(Shelf.Item it) {
        removeNow(it, true);
    }

    /** Deletes a file by the most direct road; answers whether it was done here and now. */
    private boolean removeNow(Shelf.Item it, boolean mayReopen) {
        int how = Change.delete(this, it);
        if (how == Change.ASK) {
            changing = it;
            try {
                android.app.PendingIntent consent = MediaStore.createDeleteRequest(getContentResolver(),
                    java.util.Collections.singletonList(it.uri));
                startIntentSenderForResult(consent.getIntentSender(), ASK_REMOVE, null, 0, 0, 0);
                Trace.note("delete asked of the system");
            } catch (Exception refused) {
                Trace.note("delete not asked: " + refused.getClass().getSimpleName());
                flash(Glyph.CROSS, Words.s("not_done"));
            }
            return false;
        }
        if (how == Change.DONE) {
            removed(it);
            return true;
        }
        if (how == Change.GONE) {
            lost(it);
            return false;
        }
        if (how == Change.READ_ONLY && mayReopen) {
            reopen(it, null);
            return false;
        }
        flash(Glyph.CROSS, Words.s(how == Change.READ_ONLY ? "read_only" : "not_done"));
        return false;
    }

    private void removed(Shelf.Item it) {
        String key = it.key();
        for (int i = books.size() - 1; i >= 0; i--) {
            if (books.get(i).key().equals(key)) {
                books.remove(i);
            }
        }
        for (int i = sounds.size() - 1; i >= 0; i--) {
            if (sounds.get(i).key().equals(key)) {
                sounds.remove(i);
            }
        }
        boolean inFolder = false;
        for (int i = folder.size() - 1; i >= 0; i--) {
            if (folder.get(i).key().equals(key)) {
                folder.remove(i);
                inFolder = true;
            }
        }
        if (inFolder && FOLDER.equals(front())) {
            if (folder.isEmpty()) {
                back();
            } else {
                forgetScene();
                replace(folderView());
            }
        }
        Shelf.forgetRemembered(this, it);
        Trace.note("deleted a file");
        flash(Glyph.TICK, Words.s("deleted"));
        if (PHOTO.equals(front()) || FILM.equals(front())) {
            back();
        }
        afterChange();
    }

    /** After a change: the rooms are walked and read again, and the one in front drawn anew. */
    private void afterChange() {
        settings.regather();
        photosRead = false;
        filmsRead = false;
        if (room == PICTURES || room == FILMS || ALBUM.equals(front())) {
            readMedia(ALBUM.equals(front()) ? albumRoom : room);
        } else if (!open()) {
            replace(roomView(room));
        }
    }

    private View roomView(int which) {
        if (asking == ASK_FIND && query != null) {
            return findView(which);
        }
        if (which == BOOKS) {
            return booksView();
        }
        if (which == MUSIC) {
            return musicView();
        }
        if (which == PICTURES && Gallery.maySee(this)) {
            return galleryView(PICTURES);
        }
        if (which == FILMS && Gallery.mayWatch(this)) {
            return galleryView(FILMS);
        }
        LinearLayout column = column();

        /* In the book face: a lone numeral one in the plain face is a bar. */
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[which], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        column.addView(number);

        TextView name = words(Letter.DISPLAY_S, Words.s(ROOM_WORDS[which]), Tone.ON_SURFACE);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        column.addView(name, nameParams);

        Wonder wonder = new Wonder(this, Round.px(208f), CORNERS[which], DEPTHS[which],
            ROOM_MARKS[which]);
        wonder.tint(Tone.of(BODIES[which]), Tone.of(INKS[which]));
        LinearLayout.LayoutParams wonderParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wonderParams.gravity = Gravity.CENTER_HORIZONTAL;
        wonderParams.topMargin = Round.dp(56);
        column.addView(wonder, wonderParams);

        TextView empty = words(Letter.TITLE_M, Words.s(ROOM_EMPTY[which]), Tone.ON_SURFACE);
        empty.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams emptyParams = wide();
        emptyParams.topMargin = Round.dp(36);
        column.addView(empty, emptyParams);

        final boolean films = which == FILMS;
        boolean asks = (which == PICTURES && !Gallery.maySee(this))
            || (films && !Gallery.mayWatch(this));
        TextView what = words(Letter.BODY_M, Words.s(asks ? (films ? "videos_what" : "photos_what")
            : "empty_what"), Tone.ON_SURFACE_VARIANT);
        what.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams whatParams = wide();
        whatParams.topMargin = Round.dp(6);
        column.addView(what, whatParams);
        if (asks) {
            /* The pictures are the phone's own: the room asks to see them, here, once asked for. */
            LinearLayout.LayoutParams askParams = buttonPlace(24);
            askParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(button(Words.s(films ? "allow_videos" : "allow_photos"), TONAL,
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (films) {
                            requestPermissions(new String[] {Gallery.watching()}, ASK_WATCH);
                        } else {
                            requestPermissions(new String[] {Gallery.seeing()}, ASK_SEE);
                        }
                    }
                }), askParams);
        }

        return scroll(column);
    }

    // ------------------------------------------------------------------ pictures

    /** The leave asked for the phone's pictures. */
    static final int ASK_SEE = 24;
    /** Every picture, newest first, and the albums; read once, away from the screen. */
    private final ArrayList<Gallery.Photo> photos = new ArrayList<Gallery.Photo>();
    private final ArrayList<Gallery.Album> albums = new ArrayList<Gallery.Album>();
    private boolean photosRead;
    private boolean photosReading;
    /** The pictures of the album whose screen is open, its name, and the picture opened from it. */
    private ArrayList<Gallery.Photo> album = new ArrayList<Gallery.Photo>();
    private String albumName = "";
    private int photoAt;
    private Viewer viewer;

    /** The films, newest first, and their albums; read the same way. */
    private static final int ASK_WATCH = 25;
    private final ArrayList<Gallery.Photo> films = new ArrayList<Gallery.Photo>();
    private final ArrayList<Gallery.Album> filmAlbums = new ArrayList<Gallery.Album>();
    private boolean filmsRead;
    private boolean filmsReading;
    /** Which room the album on the screen belongs to. */
    private int albumRoom = PICTURES;
    private Film film;

    private ArrayList<Gallery.Photo> itemsOf(int which) {
        return which == FILMS ? films : photos;
    }

    private ArrayList<Gallery.Album> albumsOf(int which) {
        return which == FILMS ? filmAlbums : albums;
    }

    private void readPhotos() {
        readMedia(PICTURES);
    }

    /** The phone's index of pictures or of films, read away from the screen; the room is drawn again when it is. */
    private void readMedia(final int which) {
        if (which == FILMS ? filmsReading : photosReading) {
            return;
        }
        if (which == FILMS) {
            filmsReading = true;
        } else {
            photosReading = true;
        }
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Gallery.Photo> all = which == FILMS ? Gallery.films(app) : Gallery.load(app);
                final ArrayList<Gallery.Album> by = Gallery.albums(all);
                runOnUiThread(new Runnable() {
                    public void run() {
                        itemsOf(which).clear();
                        itemsOf(which).addAll(all);
                        albumsOf(which).clear();
                        albumsOf(which).addAll(by);
                        if (which == FILMS) {
                            filmsRead = true;
                            filmsReading = false;
                        } else {
                            photosRead = true;
                            photosReading = false;
                        }
                        if (room == which && !open()) {
                            forgetScene();
                            swap(roomView(which));
                        }
                        if (which == FILMS && filmAsked != null) {
                            openAskedFilm();
                        }
                        if (ALBUM.equals(front()) && albumRoom == which) {
                            /* The album on the screen, as the index now has it. */
                            ArrayList<Gallery.Album> now = albumsOf(which);
                            for (int a = 0; a < now.size(); a++) {
                                if (now.get(a).name.equals(albumName)) {
                                    album = now.get(a).photos;
                                    photoAt = Math.min(photoAt, Math.max(0, album.size() - 1));
                                    forgetScene();
                                    replace(albumView());
                                    break;
                                }
                            }
                        }
                    }
                });
            }
        }, which == FILMS ? "films" : "pictures").start();
    }

    /** A video asked for by the widget's words, while its sound went on behind the home screen. */
    private String filmAsked;
    /** The next video opened stands still at its place: its sound was paused when it was asked for. */
    private boolean filmStill;
    /** The book asked for by the widget's words, while it was read aloud. */
    private String bookAsked;

    /**
     * The book the widget's words asked for opens at its page, a moment
     * after its room has come; if the voice is still reading it, the page
     * follows the voice at once.
     */
    private void openAskedBook() {
        final String key = bookAsked;
        bookAsked = null;
        if (key == null || key.length() == 0) {
            return;
        }
        Shelf.Item it = null;
        ArrayList<Shelf.Item> read = Shelf.current(Shelf.recent(this, 30), books);
        for (int i = 0; i < read.size() && it == null; i++) {
            if (read.get(i).key().equals(key)) {
                it = read.get(i);
            }
        }
        for (int i = 0; i < books.size() && it == null; i++) {
            if (books.get(i).key().equals(key)) {
                it = books.get(i);
            }
        }
        if (it == null) {
            return;
        }
        final Shelf.Item found = it;
        clock.postDelayed(new Runnable() {
            public void run() {
                read(found);
                Trace.note("book opened through the widget");
            }
        }, Pace.ARRIVE);
    }

    /**
     * The video whose sound was going on is opened again, over the whole
     * screen; its sound stops, and the picture goes on from where the sound
     * got to.
     */
    private void openAskedFilm() {
        String key = filmAsked;
        filmAsked = null;
        if (key == null) {
            return;
        }
        for (int i = 0; i < films.size(); i++) {
            Gallery.Photo f = films.get(i);
            if (!f.item().key().equals(key)) {
                continue;
            }
            Sound s = Sound.live();
            filmStill = false;
            if (s != null && s.current() != null && s.current().key().equals(key)) {
                filmStill = !s.playing();
                Shelf.setSoundAt(this, key, s.position());
                s.pause(true);
            }
            if (open()) {
                return;
            }
            openFilm(f);
            Trace.note("film taken back through the widget");
            return;
        }
    }

    /**
     * The room of pictures, or of films: every one of them first, as one row,
     * and then the albums the phone keeps them in, newest first, each a row
     * with its newest picture, its name and how many it holds. The films'
     * room first has the ones stopped halfway, to go on with.
     */
    private View galleryView(final int which) {
        boolean films = which == FILMS;
        LinearLayout column = column();
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[which], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        column.addView(number);
        TextView name = words(Letter.DISPLAY_S, Words.s(ROOM_WORDS[which]), Tone.ON_SURFACE);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        column.addView(name, nameParams);
        if (!(films ? filmsRead : photosRead)) {
            readMedia(which);
            return scroll(column);
        }
        ArrayList<Gallery.Photo> all = itemsOf(which);
        TextView count = words(Letter.BODY_M, Words.s(films ? "videos_n" : "photos_n")
            .replace("{n}", String.valueOf(all.size())), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams countParams = wide();
        countParams.topMargin = Round.dp(4);
        column.addView(count, countParams);
        if (all.isEmpty()) {
            TextView empty = words(Letter.TITLE_M, Words.s(ROOM_EMPTY[which]), Tone.ON_SURFACE);
            LinearLayout.LayoutParams emptyParams = wide();
            emptyParams.topMargin = Round.dp(36);
            column.addView(empty, emptyParams);
            return scroll(column);
        }
        boolean going = false;
        if (films) {
            /* The films stopped halfway, the last watched first. */
            ArrayList<Gallery.Photo> halfway = halfway();
            if (!halfway.isEmpty()) {
                going = true;
                TextView still = words(Letter.LABEL_L, Words.s("continue_video"), Tone.PRIMARY);
                LinearLayout.LayoutParams stillParams = wide();
                stillParams.topMargin = Round.dp(24);
                column.addView(still, stillParams);
                for (int i = 0; i < halfway.size(); i++) {
                    LinearLayout.LayoutParams params = wide();
                    params.topMargin = Round.dp(i == 0 ? 8 : 6);
                    column.addView(filmRow(halfway.get(i), i == 0), params);
                }
            }
        }
        ArrayList<Ruler.Entry> marks = new ArrayList<Ruler.Entry>();
        LinearLayout.LayoutParams allParams = wide();
        allParams.topMargin = Round.dp(24);
        column.addView(albumRow(Words.s(films ? "all_videos" : "all_photos"), all, which), allParams);
        String stretch = null;
        ArrayList<Gallery.Album> by = albumsOf(which);
        for (int i = 0; i < by.size(); i++) {
            Gallery.Album a = by.get(i);
            String now = Shelf.when(a.photos.get(0).taken);
            LinearLayout.LayoutParams params = wide();
            params.topMargin = Round.dp(6);
            if (!now.equals(stretch)) {
                stretch = now;
                column.addView(stretchLabel(now), stretchPlace(false));
                params.topMargin = Round.dp(8);
            }
            View row = albumRow(a.name, a.photos, which);
            column.addView(row, params);
            marks.add(new Ruler.Entry(row, a.name, Words.s(now), false, initial(a.name)));
        }
        return ruled(scroll(column), marks);
    }

    /** Up to three films stopped halfway, the last watched first. */
    private ArrayList<Gallery.Photo> halfway() {
        ArrayList<Gallery.Photo> out = new ArrayList<Gallery.Photo>();
        ArrayList<Shelf.Item> seen = Shelf.recentWatched(this, 30);
        java.util.HashMap<String, Gallery.Photo> byKey = new java.util.HashMap<String, Gallery.Photo>();
        for (int i = 0; i < films.size(); i++) {
            byKey.put(films.get(i).item().key(), films.get(i));
        }
        for (int i = 0; i < seen.size() && out.size() < 3; i++) {
            Gallery.Photo f = byKey.get(seen.get(i).key());
            if (f == null) {
                continue;
            }
            long at = Shelf.soundAt(this, seen.get(i).key());
            if (at > 5000L && (f.duration <= 0L || at < f.duration - 10000L)) {
                out.add(f);
            }
        }
        return out;
    }

    /** A film to go on with: its picture, its name, and where it stopped of how long. */
    private LinearLayout filmRow(final Gallery.Photo f, boolean big) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(big ? Tone.SURFACE_HIGH : Tone.SURFACE_CONTAINER),
            Round.L), Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openFilm(f);
            }
        });
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        face.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.S));
        face.setClipToOutline(true);
        Gallery.into(face, f);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(Round.dp(88), Round.dp(56));
        faceParams.rightMargin = Round.dp(14);
        row.addView(face, faceParams);
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(Letter.TITLE_M, Shelf.title(f.name), Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(named);
        long at = Shelf.soundAt(this, f.item().key());
        said.addView(words(Letter.BODY_S, Film.clock(at) + " / " + Film.clock(f.duration),
            Tone.ON_SURFACE_VARIANT));
        row.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** A film opened from the list of those stopped halfway: its album stands behind it. */
    private void openFilm(Gallery.Photo f) {
        for (int a = 0; a < filmAlbums.size(); a++) {
            ArrayList<Gallery.Photo> list = filmAlbums.get(a).photos;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == f.id) {
                    album = list;
                    albumName = filmAlbums.get(a).name;
                    albumRoom = FILMS;
                    photoAt = i;
                    if (!open()) {
                        door(FILM);
                    }
                    return;
                }
            }
        }
    }

    /** An album as one row: its newest picture, its name, and how many it holds. */
    private LinearLayout albumRow(final String title, final ArrayList<Gallery.Photo> list,
                                  final int which) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                album = list;
                albumName = title;
                albumRoom = which;
                photoAt = 0;
                if (!open()) {
                    door(ALBUM);
                }
            }
        });
        /* A cover is made from the picture itself and can take a moment to come;
           until it does the album stands by its initial, as a folder does, rather
           than as an empty grey square. */
        int side = Round.dp(56);
        FrameLayout plate = new FrameLayout(this);
        int[][] labels = {
            {Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER},
            {Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER},
            {Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER},
        };
        int[] label = labels[(title.hashCode() & 0x7FFFFFFF) % labels.length];
        plate.setBackground(Round.box(Tone.of(label[0]), Round.S));
        plate.setClipToOutline(true);
        TextView letter = Letter.serif(Letter.set(new TextView(this), Letter.TITLE_M));
        String stood = Shelf.initial(title);
        letter.setText(stood.length() > 0 ? stood : "\u25A1");
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Tone.of(label[1]));
        plate.addView(letter, cover());
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        face.setClipToOutline(true);
        plate.addView(face, cover());
        Gallery.intoFace(face, list.get(0), title);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(side, side);
        faceParams.rightMargin = Round.dp(14);
        row.addView(plate, faceParams);
        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(Letter.TITLE_M, Shelf.plate(title), Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(named);
        TextView under = words(Letter.BODY_S, Words.s(which == FILMS ? "videos_n" : "photos_n").replace("{n}",
            String.valueOf(list.size())) + "  \u00B7  " + Gallery.month(list.get(0).taken),
            Tone.ON_SURFACE_VARIANT);
        under.setSingleLine(true);
        said.addView(under);
        row.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /**
     * An album on a screen of its own: its name and count at the head, which
     * stays, and under it the grid of its pictures, newest first, with the
     * string of pearls along it — a month to a knot.
     */
    private View albumView() {
        LinearLayout whole = new LinearLayout(this);
        whole.setOrientation(LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Round.dp(24), Round.dp(24), Round.dp(24), Round.dp(12));
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[albumRoom], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        head.addView(number);
        TextView name = words(Letter.HEADLINE_M, Shelf.plate(albumName), Tone.ON_SURFACE);
        Letter.fit(name, PLATE, true);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(4);
        nameParams.setMarginEnd(Round.dp(56));
        head.addView(name, nameParams);
        head.addView(words(Letter.BODY_M, Words.s(albumRoom == FILMS ? "videos_n" : "photos_n")
            .replace("{n}", String.valueOf(album.size())), Tone.ON_SURFACE_VARIANT));
        whole.addView(head, wide());

        final android.widget.GridView grid = Gallery.grid(this, album, new Gallery.Touched() {
            public void touched(int i, boolean held) {
                if (held) {
                    heldPhoto(album.get(i));
                    return;
                }
                photoAt = i;
                door(album.get(i).video ? FILM : PHOTO);
            }
        });
        if (photoAt > 0 && photoAt < album.size()) {
            /* Back from a picture, the grid stands where that picture is. */
            final int back = photoAt;
            grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    grid.removeOnLayoutChangeListener(this);
                    int across = Math.max(1, grid.getNumColumns());
                    grid.setSelectionFromTop(back - back % across, grid.getHeight() / 3);
                }
            });
        }
        FrameLayout frame = new FrameLayout(this);
        frame.addView(grid, cover());
        if (album.size() >= RULED) {
            final Ruler ruler = new Ruler(this, new Gallery.Track(this, grid, album));
            frame.addView(ruler, cover());
            grid.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
                private int first = -1;

                public void onScrollStateChanged(android.widget.AbsListView v, int state) {
                    if (state != SCROLL_STATE_IDLE) {
                        ruler.moved();
                    }
                }

                public void onScroll(android.widget.AbsListView v, int firstVisible, int visible, int total) {
                    if (first >= 0 && firstVisible != first) {
                        ruler.moved();
                    }
                    first = firstVisible;
                }
            });
        }
        whole.addView(frame, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return whole;
    }

    /** Whether the label of the opened picture stands, and with it the mark. */
    private boolean photoCaption;
    private Caption caption;
    /** The opened picture's scene, and the touch-up lying over it, while there is one. */
    private FrameLayout print;
    private Mend mend;

    /**
     * A picture opened: over the whole screen at once — no bar, no mark, no
     * system bars — the album a swipe away on either side. A touch brings
     * the bar back over it, the picture's label in place of the field and
     * the round button at its end, and the mark in its corner; they stay
     * while the pictures turn under them, the label saying each in its turn,
     * and another touch puts them away. Upward, or the label touched, and
     * the sheet of facts rises. The round button, a pull downward, or back,
     * and the grid stands where the picture is.
     */
    private View photoView() {
        mend = null;
        print = new FrameLayout(this);
        viewer = new Viewer(this, album, photoAt, new Viewer.Watcher() {
            public void showing(int i) {
                photoAt = i;
                plate(false);
                if (photoCaption) {
                    label(i);
                }
            }

            public void held(int i) {
                heldPhoto(album.get(i));
            }

            public void done(int i) {
                back();
            }

            public void touched(int i) {
                photoCaption = !photoCaption;
                if (photoCaption) {
                    label(i);
                    caption.show(true);
                } else {
                    caption.hide();
                }
                placeCrest(true);
            }

            public void lifted(int i) {
                factsSheet(album.get(i));
            }

            public void closing(int i) {
                /* The bar goes down with the picture, not after it. */
                if (caption != null && caption.shown()) {
                    caption.hide();
                }
            }
        });
        print.addView(viewer, cover());
        caption = new Caption(this, new Caption.Hands() {
            public void more() {
                if (photoAt < album.size()) {
                    factsSheet(album.get(photoAt));
                }
            }

            public void done() {
                if (viewer != null) {
                    viewer.close();
                }
            }
        });
        /* Where the bar stands on every other screen, and as wide. */
        FrameLayout.LayoutParams captionAt = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        captionAt.setMargins(Round.dp(8), 0, Round.dp(8), Round.dp(16));
        print.addView(caption, captionAt);
        if (photoCaption && photoAt < album.size()) {
            /* Back from the settings over the picture: the label as it was. */
            label(photoAt);
            caption.show(false);
        }
        return print;
    }

    /**
     * The label says which picture this is: at once what the index knows,
     * and the rest as soon as the file has been read, if the picture is
     * still the one in front.
     */
    private void label(final int i) {
        if (caption == null || i >= album.size()) {
            return;
        }
        final Gallery.Photo p = album.get(i);
        Facts known = Facts.known(p);
        caption.say(i, album.size(), Shelf.title(p.name),
            known != null ? known.brief() : Gallery.day(p.taken));
        if (known != null) {
            return;
        }
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Facts f = Facts.read(app, p);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (caption != null && photoAt == i && PHOTO.equals(front())) {
                            caption.brief(f.brief());
                        }
                    }
                });
            }
        }).start();
    }

    /**
     * The sheet of what a picture says about itself, from below, as the
     * paper of a book is: its name, when it was taken, with what and how,
     * how large it is, where it was taken if the camera wrote it down, and
     * where it lies. Only what the file and the index know is said; a line
     * with nothing to say is not there.
     */
    private void factsSheet(final Gallery.Photo p) {
        closePaper();
        final FrameLayout layer = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.55f));
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closePaper();
            }
        });
        layer.addView(scrim, cover());
        final LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setPadding(Round.dp(24), Round.dp(10), Round.dp(24), Round.dp(20));
        sheet.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.XL));
        View handle = new View(this);
        handle.setBackground(Round.box(Tone.of(Tone.ON_SURFACE_VARIANT, 0.4f), Round.FULL));
        LinearLayout.LayoutParams handleAt = new LinearLayout.LayoutParams(Round.dp(32), Round.dp(4));
        handleAt.gravity = Gravity.CENTER_HORIZONTAL;
        handleAt.bottomMargin = Round.dp(14);
        sheet.addView(handle, handleAt);
        sheet.addView(words(Letter.TITLE_L, Words.s("about_picture"), Tone.ON_SURFACE));
        final LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView holder = new ScrollView(this);
        holder.setVerticalScrollBarEnabled(false);
        holder.setOverScrollMode(View.OVER_SCROLL_NEVER);
        holder.addView(rows);
        /* Weighted, so that a sheet cut to three quarters of the screen gives
           the lines what is left and they scroll inside it. */
        LinearLayout.LayoutParams holderAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        holderAt.topMargin = Round.dp(6);
        sheet.addView(holder, holderAt);
        Facts known = Facts.known(p);
        if (known != null) {
            facts(rows, p, known);
        } else {
            Facts first = new Facts();
            first.taken = Gallery.day(p.taken);
            facts(rows, p, first);
            final android.content.Context app = getApplicationContext();
            new Thread(new Runnable() {
                public void run() {
                    final Facts f = Facts.read(app, p);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (paperLayer == layer) {
                                facts(rows, p, f);
                            }
                        }
                    });
                }
            }).start();
        }
        /* Never taller than three quarters of the screen; a long sheet scrolls. */
        final int most = Math.round(getResources().getDisplayMetrics().heightPixels * 0.75f);
        FrameLayout.LayoutParams sheetAt = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        layer.addView(sheet, sheetAt);
        sheet.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                if (b - t > most && sheet.getLayoutParams().height != most) {
                    sheet.getLayoutParams().height = most;
                    sheet.requestLayout();
                }
            }
        });
        stage.addView(layer, cover());
        paperLayer = layer;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        sheet.setTranslationY(Round.px(420f));
        sheet.animate().translationY(0f).setDuration(Pace.GROW).setInterpolator(Pace.EMPHASIS).start();
        Trace.note("facts of a picture");
    }

    /** The lines of the sheet, laid again when more is known. */
    private void facts(LinearLayout rows, Gallery.Photo p, Facts f) {
        rows.removeAllViews();
        fact(rows, "fact_name", p.name);
        fact(rows, "fact_taken", f.taken);
        fact(rows, "fact_camera", f.camera);
        fact(rows, "fact_lens", f.lens);
        fact(rows, "fact_exposure", f.exposure);
        fact(rows, "fact_size", f.size);
        fact(rows, "fact_where", f.where);
        fact(rows, "fact_lies", f.lies);
    }

    /** One line of facts: what it is, small, and above it what it says. */
    private void fact(LinearLayout rows, String what, String said) {
        if (said == null || said.trim().length() == 0) {
            return;
        }
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.VERTICAL);
        line.setPadding(0, Round.dp(10), 0, Round.dp(10));
        line.addView(words(Letter.LABEL_M, Words.s(what), Tone.ON_SURFACE_VARIANT));
        TextView value = words(Letter.BODY_L, said, Tone.ON_SURFACE);
        value.setTextIsSelectable(true);
        line.addView(value);
        rows.addView(line, wide());
    }

    /**
     * While a picture is open, and if it was asked for, the screen lights at
     * its brightest, as a print is held up to the window; every other screen
     * leaves the brightness to the phone.
     */
    private void brightness() {
        android.view.WindowManager.LayoutParams lp = getWindow().getAttributes();
        float want = PHOTO.equals(front()) && Keep.brightNow(this)
            ? android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            : android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;
        if (lp.screenBrightness != want) {
            lp.screenBrightness = want;
            getWindow().setAttributes(lp);
        }
    }

    /** A film opened: over the whole screen, playing from where it stopped. */
    private View filmView() {
        still = null;
        final Gallery.Photo f = album.get(photoAt);
        if (film != null && film.item().id == f.id && film.getParent() == null && !film.closed()) {
            /* Back from the settings over it: the same video, where it stopped. */
            return film;
        }
        Shelf.rememberWatched(this, f.item());
        final boolean still = filmStill;
        filmStill = false;
        film = new Film(this, f, new Film.Watcher() {
            public void done() {
                back();
            }

            public void held() {
                heldFilm(f);
            }

            public void dialOpen() {
                openFilmDial();
            }

            public void dialTurn(float dx) {
                if (arc != null) {
                    ticked(arc.turn(dx));
                }
            }

            public void dialEdge(float across) {
                float was = edgeSpeed;
                edgeSpeed = across < 0.1f ? -(0.1f - across) * 12f
                    : (across > 0.9f ? (across - 0.9f) * 12f : 0f);
                if (was == 0f && edgeSpeed != 0f) {
                    clock.post(edgeRoll);
                }
            }

            public void dialClose(boolean take) {
                closeDial(take);
            }

            public void chrome(boolean shown) {
                filmChrome = shown;
                placeCrest(true);
            }

            public void refused() {
                if (filmRefusedOf != f.id) {
                    filmRefusedOf = f.id;
                    flash(Glyph.FILM, Words.s("speed_refused"));
                }
            }
        });
        if (still) {
            film.openStill();
        }
        return film;
    }

    /** A video held: its frame kept as a picture, handing it on, renaming, and apart, deleting. */
    private void heldFilm(final Gallery.Photo f) {
        if (spread) {
            return;
        }
        bloom(fileRows(f.item(), row(Glyph.PICTURE, Words.s("save_frame"), new Runnable() {
            public void run() {
                openStill(f);
            }
        }), sleepRow()));
    }

    /** The frame chooser over a video, while it is open. */
    private Still still;

    /**
     * A frame of a video to be kept: first the video's own frame rate is read,
     * then the chooser opens over it, frame by frame.
     */
    private void openStill(final Gallery.Photo f) {
        if (film == null || still != null) {
            return;
        }
        if (filmChrome) {
            filmChrome = false;
            placeCrest(true);
        }
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final float fps = Gallery.frameRate(app, f);
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (film == null || still != null || !FILM.equals(front())) {
                            return;
                        }
                        final Film under = film;
                        still = new Still(Main.this, under, fps, new Still.Hands() {
                            public void keep(final long ms) {
                                new Thread(new Runnable() {
                                    public void run() {
                                        final boolean kept = Gallery.keepFrame(app, f, ms);
                                        runOnUiThread(new Runnable() {
                                            public void run() {
                                                if (kept) {
                                                    photosRead = false;
                                                    Trace.note("frame kept at " + ms + " ms");
                                                } else {
                                                    flash(Glyph.CROSS, Words.s("unsaved"));
                                                }
                                            }
                                        });
                                    }
                                }, "frame").start();
                            }

                            public void gone() {
                                if (still != null && still.getParent() instanceof ViewGroup) {
                                    ((ViewGroup) still.getParent()).removeView(still);
                                }
                                still = null;
                            }
                        });
                        under.addView(still, cover());
                        still.open();
                        Trace.note("choosing a frame, " + fps + " a second");
                    }
                });
            }
        }, "frame rate").start();
    }

    /** A picture held: what can be done with it, grown from the finger. */
    private void heldPhoto(final Gallery.Photo p) {
        if (spread || mend != null) {
            return;
        }
        bloom(fileRows(p.item(), mendRow(p), Change.turnable(p.name) ? turnRow(p) : null));
    }

    /** A picture touched up where it lies: needs the index's way of adding a copy, Android 10 and later. */
    private Bloom.Row mendRow(final Gallery.Photo p) {
        if (Build.VERSION.SDK_INT < 29 || p.video || !PHOTO.equals(front())) {
            return null;
        }
        return row(Glyph.SPARK, Words.s("mend"), new Runnable() {
            public void run() {
                openMend(p, 0);
            }
        });
    }

    private void openMend(final Gallery.Photo p, int stop) {
        if (mend != null || print == null || viewer == null) {
            return;
        }
        if (photoCaption) {
            photoCaption = false;
            if (caption != null) {
                caption.hide();
            }
            placeCrest(true);
        }
        mend = new Mend(this, p, viewer.picture(), stop, new Mend.Hands() {
            public void kept(Uri made) {
                mended(made);
            }

            public void handed(Uri made) {
                if (made == null) {
                    flash(Glyph.CROSS, Words.s("not_done"));
                    return;
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("image/jpeg");
                send.putExtra(Intent.EXTRA_STREAM, made);
                send.setClipData(android.content.ClipData.newRawUri("", made));
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(send, null));
                    Trace.note("the touch-up was handed on");
                } catch (Exception none) {
                    Trace.note("nothing takes it: " + none.getClass().getSimpleName());
                    flash(Glyph.CROSS, Words.s("not_done"));
                }
            }

            public void gone() {
                if (print != null && mend != null) {
                    print.removeView(mend);
                }
                mend = null;
            }
        });
        print.addView(mend, cover());
        mend.open();
        Trace.note("touching up a picture");
    }

    /**
     * The touched-up copy kept: the album is read again and the copy opens in
     * the picture's place, its label up, so what was made is what is seen.
     */
    private void mended(final Uri made) {
        if (made == null) {
            flash(Glyph.CROSS, Words.s("not_done"));
            if (mend != null) {
                mend.back();
            }
            return;
        }
        long id = -1L;
        try {
            id = android.content.ContentUris.parseId(made);
        } catch (Exception odd) {
            Trace.note("touch-up: copy has no number");
        }
        final long copy = id;
        final String albumWas = albumName;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Gallery.Photo> all = Gallery.load(app);
                final ArrayList<Gallery.Album> by = Gallery.albums(all);
                runOnUiThread(new Runnable() {
                    public void run() {
                        itemsOf(PICTURES).clear();
                        itemsOf(PICTURES).addAll(all);
                        albumsOf(PICTURES).clear();
                        albumsOf(PICTURES).addAll(by);
                        photosRead = true;
                        ArrayList<Gallery.Photo> list = all;
                        for (int a = 0; a < by.size(); a++) {
                            if (by.get(a).name.equals(albumWas)) {
                                list = by.get(a).photos;
                                break;
                            }
                        }
                        int at = -1;
                        for (int i = 0; i < list.size(); i++) {
                            if (list.get(i).id == copy) {
                                at = i;
                                break;
                            }
                        }
                        if (at >= 0 && PHOTO.equals(front())) {
                            album = list;
                            photoAt = at;
                            photoCaption = true;
                            forgetScene();
                            replace(photoView());
                            placeCrest(true);
                            /* No note says it: the copy is on the screen, with its name up, and
                               the star that began the touching up signs it. */
                            if (caption != null) {
                                /* Once the new scene has arrived and the bar stands, not before. */
                                caption.postDelayed(new Runnable() {
                                    public void run() {
                                        if (caption != null && caption.shown()) {
                                            caption.sign();
                                        }
                                    }
                                }, Pace.ARRIVE);
                            }
                        } else {
                            flash(Glyph.TICK, Words.s("copy_kept"));
                            if (mend != null) {
                                mend.back();
                                mend.leave();
                            }
                        }
                    }
                });
            }
        }, "pictures").start();
    }

    // ------------------------------------------------------------------ books

    /**
     * The books room. With no folder opened to it yet, it shows its shape
     * and one button; with folders, the book read last comes first, and
     * then the whole shelf by title.
     */
    private View booksView() {
        LinearLayout column = column();
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[BOOKS], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        column.addView(number);
        TextView name = words(Letter.DISPLAY_S, Words.s(ROOM_WORDS[BOOKS]), Tone.ON_SURFACE);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        column.addView(name, nameParams);

        if (Shelf.trees(this).isEmpty()) {
            Wonder wonder = new Wonder(this, Round.px(208f), CORNERS[BOOKS], DEPTHS[BOOKS],
                ROOM_MARKS[BOOKS]);
            wonder.tint(Tone.of(BODIES[BOOKS]), Tone.of(INKS[BOOKS]));
            LinearLayout.LayoutParams wonderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wonderParams.gravity = Gravity.CENTER_HORIZONTAL;
            wonderParams.topMargin = Round.dp(48);
            column.addView(wonder, wonderParams);
            TextView empty = words(Letter.TITLE_M, Words.s(ROOM_EMPTY[BOOKS]), Tone.ON_SURFACE);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams emptyParams = wide();
            emptyParams.topMargin = Round.dp(32);
            column.addView(empty, emptyParams);
            TextView what = words(Letter.BODY_M, Words.s("folders_what"), Tone.ON_SURFACE_VARIANT);
            what.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams whatParams = wide();
            whatParams.topMargin = Round.dp(6);
            column.addView(what, whatParams);
            LinearLayout.LayoutParams askParams = buttonPlace(24);
            askParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(button(Words.s("open_folder"), TONAL, folderAsk()), askParams);
            return scroll(column);
        }

        if (!gathered) {
            TextView wait = words(Letter.BODY_M, Words.s("gathering"), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams waitParams = wide();
            waitParams.topMargin = Round.dp(8);
            column.addView(wait, waitParams);
            gather();
            return scroll(column);
        }

        TextView count = words(Letter.BODY_M,
            Words.s("on_shelf").replace("{n}", String.valueOf(books.size())),
            Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams countParams = wide();
        countParams.topMargin = Round.dp(4);
        column.addView(count, countParams);

        ArrayList<Shelf.Item> recent = Shelf.current(Shelf.recent(this, 3), books);
        Shelf.Item last = recent.isEmpty() ? null : recent.get(0);
        if (last != null) {
            TextView going = words(Letter.LABEL_L, Words.s("continue"), Tone.PRIMARY);
            LinearLayout.LayoutParams goingParams = wide();
            goingParams.topMargin = Round.dp(24);
            column.addView(going, goingParams);
            for (int i = 0; i < recent.size(); i++) {
                LinearLayout.LayoutParams lastParams = wide();
                lastParams.topMargin = Round.dp(i == 0 ? 8 : 6);
                column.addView(bookRow(recent.get(i), i == 0), lastParams);
            }
        }

        /* A shelf of chosen folders stands by title; a room fed by the
           phone's index flows by the day things came, newest first, parted
           by stretches of time. */
        boolean stream = Shelf.whole(this);
        final ArrayList<Shelf.Item> shown = stream ? new ArrayList<Shelf.Item>(books) : books;
        if (stream) {
            java.util.Collections.sort(shown, new java.util.Comparator<Shelf.Item>() {
                public int compare(Shelf.Item a, Shelf.Item b) {
                    return Long.compare(b.modified, a.modified);
                }
            });
        } else if (!shown.isEmpty()) {
            /* The shelf has a head of its own, so that the first of it is not
               read as another of the three above it. */
            TextView whole = words(Letter.LABEL_L, Words.s("whole_shelf"), Tone.ON_SURFACE_VARIANT);
            whole.setPadding(Round.dp(4), 0, 0, 0);
            column.addView(whole, stretchPlace(last == null));
        }

        /* Two and a half thousand rows are not built to be looked at three at
           a time: the shelf hands them out as the eye reaches them, and the
           head above stands with them as one list. */
        final ArrayList<Object> flat = new ArrayList<Object>();
        final ArrayList<Integer> rowAt = new ArrayList<Integer>();
        String stretch = null;
        for (int i = 0; i < shown.size(); i++) {
            Shelf.Item it = shown.get(i);
            if (stream) {
                String now = Shelf.when(it.modified);
                if (!now.equals(stretch)) {
                    stretch = now;
                    flat.add(now);
                }
            }
            rowAt.add(flat.size());
            flat.add(it);
        }
        final boolean flowing = stream;
        final android.widget.ListView list = new android.widget.ListView(this);
        list.setDivider(null);
        list.setDividerHeight(0);
        list.setVerticalScrollBarEnabled(false);
        list.setSelector(new android.graphics.drawable.ColorDrawable(0x00000000));
        list.setClipToPadding(false);
        list.setPadding(Round.dp(24), 0, Round.dp(24), Round.dp(40));
        column.setPadding(Round.dp(24), Round.dp(28), Round.dp(24), 0);
        list.addHeaderView(column, null, false);
        LinearLayout foot = new LinearLayout(this);
        foot.setOrientation(LinearLayout.VERTICAL);
        foot.addView(button(Words.s("open_folder"), OUTLINED, folderAsk()), buttonPlace(24));
        list.addFooterView(foot, null, false);
        list.setAdapter(new android.widget.BaseAdapter() {
            public int getCount() {
                return flat.size();
            }

            public Object getItem(int i) {
                return flat.get(i);
            }

            public long getItemId(int i) {
                return i;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getItemViewType(int i) {
                return flat.get(i) instanceof Shelf.Item ? 0 : 1;
            }

            @Override
            public boolean isEnabled(int i) {
                return false;
            }

            public View getView(int i, View was, ViewGroup parent) {
                Object what = flat.get(i);
                LinearLayout holder = was instanceof LinearLayout && was.getTag() == what
                    ? (LinearLayout) was : null;
                if (holder != null) {
                    return holder;
                }
                LinearLayout made = new LinearLayout(Main.this);
                made.setOrientation(LinearLayout.VERTICAL);
                made.setTag(what);
                if (what instanceof Shelf.Item) {
                    LinearLayout.LayoutParams at = wide();
                    at.topMargin = Round.dp(i == 0 ? 8 : 6);
                    made.addView(bookRow((Shelf.Item) what, false), at);
                } else {
                    made.addView(stretchLabel((String) what), stretchPlace(i == 0));
                }
                return made;
            }
        });
        if (shown.size() < RULED) {
            return list;
        }
        FrameLayout whole = new FrameLayout(this);
        whole.addView(list, cover());
        final Ruler ruler = new Ruler(this, new Shelf.Track(this, list, shown, rowAt,
            list.getHeaderViewsCount(), flowing));
        whole.addView(ruler, cover());
        list.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            public void onScrollStateChanged(android.widget.AbsListView v, int state) {
                Thumb.sweep(Main.this);
            }

            public void onScroll(android.widget.AbsListView v, int first, int visible, int total) {
                Thumb.sweep(Main.this);
                ruler.moved();
            }
        });
        return whole;
    }

    View.OnClickListener folderAsk() {
        return new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                try {
                    startActivityForResult(pick, OPEN_TREE);
                } catch (Exception none) {
                    Trace.note("no folder picker: " + none);
                }
            }
        };
    }

    /**
     * Walks the folders away from the screen, once for every room, and shows
     * what it found: the shelf and the music room are each redrawn only if
     * the walk found otherwise than they show.
     */
    void gather() {
        if (gathering) {
            return;
        }
        gathering = true;
        soundsGathering = true;
        final android.content.Context app = getApplicationContext();
        final long began = System.currentTimeMillis();
        new Thread(new Runnable() {
            public void run() {
                final Shelf.Found found = Shelf.gatherAll(app);
                Shelf.keep(app, found.books);
                Shelf.keepSounds(app, found.sounds);
                runOnUiThread(new Runnable() {
                    public void run() {
                        /* The placeholder is always replaced; a room already
                           showing is redrawn only if the walk found otherwise. */
                        boolean booksChanged = !gathered || !same(books, found.books);
                        boolean soundsChanged = !soundsGathered || !same(sounds, found.sounds);
                        books.clear();
                        books.addAll(found.books);
                        sounds.clear();
                        sounds.addAll(found.sounds);
                        gathered = true;
                        soundsGathered = true;
                        gathering = false;
                        soundsGathering = false;
                        Trace.note("one walk: " + found.books.size() + " books, "
                            + found.sounds.size() + " recordings in "
                            + (System.currentTimeMillis() - began) + " ms");
                        if (open()) {
                            return;
                        }
                        if ((room == BOOKS && booksChanged) || (room == MUSIC && soundsChanged)) {
                            forgetScene();
                            swap(roomView(room));
                        }
                    }
                });
            }
        }, "shelf").start();
    }

    /** Whether a fresh walk found what the shelf already shows, so nothing need be redrawn. */
    private static boolean same(ArrayList<Shelf.Item> a, ArrayList<Shelf.Item> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).uri.equals(b.get(i).uri)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A book on the shelf: a small cover with its initial in the book face,
     * the title, and a line saying what kind of file it is, where it stands
     * and where it was left.
     */
    private LinearLayout bookRow(final Shelf.Item it, boolean big) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(big ? Tone.SURFACE_HIGH
            : Tone.SURFACE_CONTAINER), Round.L), Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                read(it);
            }
        });
        row.setOnLongClickListener(held(it));

        String title = Shelf.title(it.name);
        int[][] covers = {
            {Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER},
            {Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER},
            {Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER},
            {Tone.SURFACE_HIGHEST, Tone.PRIMARY},
        };
        int[] cover = covers[(title.hashCode() & 0x7FFFFFFF) % covers.length];
        /* The book's letter on a coloured spine, and over it, once it is
           found, the book's own cover. */
        FrameLayout spine = new FrameLayout(this);
        spine.setBackground(Round.box(Tone.of(cover[0]), Round.S));
        spine.setClipToOutline(true);
        TextView letter = Letter.serif(Letter.set(new TextView(this), Letter.TITLE_L));
        letter.setText(title.length() > 0 ? title.substring(0, 1).toUpperCase() : "\u00B7");
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Tone.of(cover[1]));
        spine.addView(letter, cover());
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        spine.addView(face, cover());
        Thumb.watch(face, it);
        LinearLayout.LayoutParams spineParams = new LinearLayout.LayoutParams(
            Round.dp(big ? 48 : 40), Round.dp(big ? 64 : 54));
        spineParams.rightMargin = Round.dp(14);
        row.addView(spine, spineParams);

        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(big ? Letter.TITLE_L : Letter.TITLE_M, title, Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(named);
        StringBuilder line = new StringBuilder(Shelf.kind(it.name).toUpperCase());
        if (it.folder != null && it.folder.length() > 0) {
            line.append("  \u00B7  ").append(it.folder);
        }
        int at = Shelf.pageOf(this, it.key());
        if (at > 0) {
            line.append("  \u00B7  ").append(Words.s("page_n").replace("{n}",
                String.valueOf(at + 1)));
        }
        TextView under = words(Letter.BODY_S, line.toString(), Tone.ON_SURFACE_VARIANT);
        under.setSingleLine(true);
        under.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams underParams = wide();
        underParams.topMargin = Round.dp(2);
        said.addView(under, underParams);
        row.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** The leave asked for the phone's index of recordings. */
    static final int ASK_HEAR = 23;

    @Override
    public void onRequestPermissionsResult(int code, String[] asked, int[] given) {
        super.onRequestPermissionsResult(code, asked, given);
        if (code == ASK_SEE) {
            boolean yes = given.length > 0
                && given[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Trace.note("pictures: " + (yes ? "allowed" : "refused"));
            if (!yes) {
                flash(Glyph.PICTURE, Words.s("allow"));
                return;
            }
            photosRead = false;
            readPhotos();
            if (room == PICTURES && !open()) {
                forgetScene();
                swap(roomView(PICTURES));
            }
            return;
        }
        if (code == ASK_WATCH) {
            boolean yes = given.length > 0
                && given[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
            Trace.note("films: " + (yes ? "allowed" : "refused"));
            if (!yes) {
                flash(Glyph.FILM, Words.s("allow"));
                return;
            }
            filmsRead = false;
            readMedia(FILMS);
            if (room == FILMS && !open()) {
                forgetScene();
                swap(roomView(FILMS));
            }
            return;
        }
        if (code != ASK_HEAR) {
            return;
        }
        boolean yes = given.length > 0
            && given[0] == android.content.pm.PackageManager.PERMISSION_GRANTED;
        Shelf.setSoundsWhole(this, yes);
        Trace.note("index of recordings: " + (yes ? "allowed" : "refused"));
        if (!yes) {
            flash(Glyph.NOTE, Words.s("allow"));
        }
        gathered = false;
        soundsGathered = false;
        gather();
        if (FOLDERS.equals(front())) {
            forgetScene();
            swap(settings.foldersView());
        }
    }

    /**
     * The music room: every recording in the opened folders and everything
     * under them, one flat list, since a phone's recordings are rarely
     * sorted into albums. The three heard last come first, each with the
     * place it was left at.
     */
    private View musicView() {
        LinearLayout column = column();
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[MUSIC], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        column.addView(number);
        TextView name = words(Letter.DISPLAY_S, Words.s(ROOM_WORDS[MUSIC]), Tone.ON_SURFACE);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        column.addView(name, nameParams);

        if (Shelf.trees(this).isEmpty() && !Shelf.whole(this)) {
            Wonder wonder = new Wonder(this, Round.px(208f), CORNERS[MUSIC], DEPTHS[MUSIC],
                ROOM_MARKS[MUSIC]);
            wonder.tint(Tone.of(BODIES[MUSIC]), Tone.of(INKS[MUSIC]));
            LinearLayout.LayoutParams wonderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            wonderParams.gravity = Gravity.CENTER_HORIZONTAL;
            wonderParams.topMargin = Round.dp(48);
            column.addView(wonder, wonderParams);
            TextView empty = words(Letter.TITLE_M, Words.s(ROOM_EMPTY[MUSIC]), Tone.ON_SURFACE);
            empty.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams emptyParams = wide();
            emptyParams.topMargin = Round.dp(32);
            column.addView(empty, emptyParams);
            TextView what = words(Letter.BODY_M, Words.s("sounds_what"), Tone.ON_SURFACE_VARIANT);
            what.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams whatParams = wide();
            whatParams.topMargin = Round.dp(6);
            column.addView(what, whatParams);
            LinearLayout.LayoutParams askParams = buttonPlace(24);
            askParams.gravity = Gravity.CENTER_HORIZONTAL;
            column.addView(button(Words.s("open_folder"), TONAL, folderAsk()), askParams);
            return scroll(column);
        }

        if (!soundsGathered) {
            TextView wait = words(Letter.BODY_M, Words.s("gathering"), Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams waitParams = wide();
            waitParams.topMargin = Round.dp(8);
            column.addView(wait, waitParams);
            gatherSounds();
            return scroll(column);
        }

        TextView count = words(Letter.BODY_M,
            Words.s("in_room").replace("{n}", String.valueOf(sounds.size())),
            Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams countParams = wide();
        countParams.topMargin = Round.dp(4);
        column.addView(count, countParams);

        ArrayList<Shelf.Item> recent = Shelf.current(Shelf.recentSounds(this, 3), sounds);
        if (!recent.isEmpty()) {
            TextView going = words(Letter.LABEL_L, Words.s("continue_sound"), Tone.PRIMARY);
            LinearLayout.LayoutParams goingParams = wide();
            goingParams.topMargin = Round.dp(24);
            column.addView(going, goingParams);
            for (int i = 0; i < recent.size(); i++) {
                LinearLayout.LayoutParams params = wide();
                params.topMargin = Round.dp(i == 0 ? 8 : 6);
                column.addView(soundRow(recent.get(i), i == 0), params);
            }
        }
        /* Then the folders, each one row: a folder of one recording is that
           recording, and a folder of many opens as a screen of its own. */
        ArrayList<ArrayList<Shelf.Item>> homes = Shelf.homes(sounds);
        ArrayList<Shelf.Item> heard = Shelf.current(Shelf.recentSounds(this, 30), sounds);
        /* Fed by the phone's index, the room flows by the day things came:
           a folder that came at once is one row, dated by its newest file. */
        final boolean stream = Shelf.soundsWhole(this);
        if (stream) {
            java.util.Collections.sort(homes, new java.util.Comparator<ArrayList<Shelf.Item>>() {
                public int compare(ArrayList<Shelf.Item> a, ArrayList<Shelf.Item> b) {
                    return Long.compare(newest(b), newest(a));
                }
            });
        }
        ArrayList<Ruler.Entry> marks = new ArrayList<Ruler.Entry>();
        String stretch = null;
        for (int i = 0; i < homes.size(); i++) {
            LinearLayout.LayoutParams params = wide();
            params.topMargin = Round.dp(i == 0 ? (recent.isEmpty() ? 20 : 24) : 6);
            ArrayList<Shelf.Item> here = homes.get(i);
            String now = stream ? Shelf.when(newest(here)) : null;
            if (stream && !now.equals(stretch)) {
                stretch = now;
                column.addView(stretchLabel(now), stretchPlace(i == 0));
                params.topMargin = Round.dp(8);
            }
            View row = here.size() == 1 ? soundRow(here.get(0), false) : folderRow(here, heard);
            column.addView(row, params);
            String named = here.size() == 1 ? Shelf.label(here.get(0)) : Shelf.homeTitle(here.get(0));
            marks.add(new Ruler.Entry(row, named, stream ? Words.s(now) : initial(named), true,
                initial(named)));
        }
        LinearLayout.LayoutParams askParams = buttonPlace(24);
        column.addView(button(Words.s("open_folder"), OUTLINED, folderAsk()), askParams);
        return ruled(scroll(column), marks);
    }

    /** The recordings of the folder whose screen is open, and that folder's shared words. */
    private ArrayList<Shelf.Item> folder = new ArrayList<Shelf.Item>();
    private String folderCommon = "";
    /** The numbered rounds of the open folder, by place, so the one playing can be lit. */
    private final java.util.HashMap<String, TextView> folderRounds =
        new java.util.HashMap<String, TextView>();
    private String folderLit;

    /** When the newest of a folder's files came. */
    private static long newest(ArrayList<Shelf.Item> here) {
        long at = 0L;
        for (int i = 0; i < here.size(); i++) {
            at = Math.max(at, here.get(i).modified);
        }
        return at;
    }

    /** The name of a stretch of time in a stream, quiet, above the first row it holds. */
    private TextView stretchLabel(String stretch) {
        TextView made = words(Letter.LABEL_L, Words.s(stretch), Tone.ON_SURFACE_VARIANT);
        made.setPadding(Round.dp(4), 0, 0, 0);
        return made;
    }

    private static LinearLayout.LayoutParams stretchPlace(boolean first) {
        LinearLayout.LayoutParams params = wide();
        params.topMargin = Round.dp(first ? 24 : 28);
        return params;
    }

    // ------------------------------------------------------------ sharing

    /**
     * A file handed to another application — a chat, a mail, a drive — by
     * the system's own chooser. Its address is given with leave to read it,
     * which lasts as long as the one who takes it needs it; nothing is
     * copied and no address of our own is made.
     */
    private void share(Shelf.Item it) {
        if (it == null || it.uri == null) {
            return;
        }
        try {
            Intent send = new Intent(Intent.ACTION_SEND);
            send.setType(it.mime != null && it.mime.length() > 0 ? it.mime : kindOf(it.name));
            send.putExtra(Intent.EXTRA_STREAM, it.uri);
            send.setClipData(android.content.ClipData.newRawUri(it.name, it.uri));
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Intent chooser = Intent.createChooser(send, Words.s("share"));
            chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(chooser);
            Trace.note("shared a file");
        } catch (Exception refused) {
            Trace.note("not shared: " + refused.getClass().getSimpleName());
        }
    }

    /** A kind of file told by its name, for a file whose kind nobody said. */
    private static String kindOf(String name) {
        String n = name == null ? "" : name.toLowerCase(java.util.Locale.ROOT);
        String ext = n.lastIndexOf('.') >= 0 ? n.substring(n.lastIndexOf('.') + 1) : "";
        String known = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return known != null ? known : "application/octet-stream";
    }

    /** A row held: capsules rise with what can be done with its file. */
    private View.OnLongClickListener held(final Shelf.Item it) {
        return new View.OnLongClickListener() {
            public boolean onLongClick(View v) {
                if (spread) {
                    return true;
                }
                boolean sound = isSound(it) || (it.mime != null && it.mime.startsWith("audio/"));
                bloom(fileRows(it, recutRow(it), coverRow(it), cardRow(it),
                    sound ? sleepRow() : null, sound ? repeatRow() : null));
                return true;
            }
        };
    }

    /** A recording in the commonest compressed form can be touched up: a piece of it, and its words. */
    private Bloom.Row recutRow(final Shelf.Item it) {
        String n = it.name == null ? "" : it.name.toLowerCase(java.util.Locale.ROOT);
        boolean mp3 = n.endsWith(".mp3") || "audio/mpeg".equals(it.mime);
        if (!mp3) {
            return null;
        }
        return row(Glyph.SPARK, Words.s("mend"), new Runnable() {
            public void run() {
                openRecut(it);
            }
        });
    }

    /** A recording's cover taken out into the collection, as a book's words are into excerpts. */
    private Bloom.Row coverRow(final Shelf.Item it) {
        String m = it.mime == null ? "" : it.mime;
        if (Build.VERSION.SDK_INT < 29 || !(m.startsWith("audio/") || isSound(it))) {
            return null;
        }
        return row(Glyph.PICTURE, Words.s("to_covers"), new Runnable() {
            public void run() {
                ArrayList<Shelf.Item> one = new ArrayList<Shelf.Item>();
                one.add(it);
                takeCovers(one);
            }
        });
    }

    /** What a recording's tag says, written down as a card of plain text, when that is wanted. */
    private Bloom.Row cardRow(final Shelf.Item it) {
        String m = it.mime == null ? "" : it.mime;
        if (Build.VERSION.SDK_INT < 29 || !(m.startsWith("audio/") || isSound(it))) {
            return null;
        }
        return row(Glyph.BOOK, Words.s("tags_to_text"), new Runnable() {
            public void run() {
                ArrayList<Shelf.Item> one = new ArrayList<Shelf.Item>();
                one.add(it);
                takeCards(one);
            }
        });
    }

    /** Cards of the tags, one per album, away from the screen; what came of it said once. */
    private void takeCards(final ArrayList<Shelf.Item> these) {
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final int[] got = Covers.cards(app, these);
                Trace.note("cards: " + got[0] + " written, " + got[1] + " there, of " + these.size());
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (got[0] > 1) {
                            flash(Glyph.BOOK, Words.s("cards_n").replace("{n}", String.valueOf(got[0])));
                        } else if (got[0] == 1) {
                            flash(Glyph.BOOK, Words.s("card_kept"));
                        } else if (got[1] > 0) {
                            flash(Glyph.BOOK, Words.s("card_there"));
                        } else {
                            flash(Glyph.CROSS, Words.s("not_done"));
                        }
                        if (got[0] > 0) {
                            /* A card is plain text, so it is a book too; the shelf is walked again. */
                            afterChange();
                        }
                    }
                });
            }
        }, "cards").start();
    }

    private static boolean isSound(Shelf.Item it) {
        String n = it.name == null ? "" : it.name.toLowerCase(java.util.Locale.ROOT);
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".flac") || n.endsWith(".ogg")
            || n.endsWith(".opus") || n.endsWith(".aac") || n.endsWith(".m4b") || n.endsWith(".wma");
    }

    /**
     * Covers taken out of recordings, one or a folder of them, away from the
     * screen; one album gives one cover. What came of it is said once, at the end.
     */
    private void takeCovers(final ArrayList<Shelf.Item> these) {
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                java.util.HashSet<String> seen = new java.util.HashSet<String>();
                int taken = 0;
                int there = 0;
                for (int i = 0; i < these.size(); i++) {
                    int got = Covers.take(app, these.get(i), seen);
                    if (got == Covers.TAKEN) {
                        taken++;
                    } else if (got == Covers.THERE) {
                        there++;
                    }
                }
                final int t = taken;
                final int h = there;
                Trace.note("covers: " + t + " taken, " + h + " there, of " + these.size());
                runOnUiThread(new Runnable() {
                    public void run() {
                        if (t > 0) {
                            photosRead = false;
                            flash(Glyph.PICTURE, these.size() == 1 ? Words.s("cover_taken")
                                : Words.s("covers_n").replace("{n}", String.valueOf(t)));
                        } else if (h > 0) {
                            flash(Glyph.PICTURE, Words.s("cover_there"));
                        } else {
                            flash(Glyph.PICTURE, Words.s("cover_none"));
                        }
                    }
                });
            }
        }, "covers").start();
    }

    /** The recording editor over the room, while it is open. */
    private Recut recut;

    private void openRecut(final Shelf.Item it) {
        if (recut != null) {
            return;
        }
        /* What sounds stops: the piece is heard alone while its marks are set. */
        Sound s = Sound.live();
        if (s != null && s.playing()) {
            s.pause(true);
        }
        recut = new Recut(this, it, new Recut.Hands() {
            public void kept(Shelf.Item made, boolean piece) {
                closeRecut();
                afterChange();
                if (piece) {
                    /* The piece is heard at once: what was made is what is heard. */
                    listen(made);
                } else {
                    flash(Glyph.TICK, Words.s("words_kept"));
                }
            }

            public void gone() {
                closeRecut();
            }
        });
        stage.addView(recut, cover());
        /* The editor has its own bar and round button; the room's step aside for it. */
        bar.animate().cancel();
        bar.setVisibility(View.GONE);
        crest.setVisibility(View.GONE);
        recut.open();
        Trace.note("touching up a recording");
    }

    private void closeRecut() {
        boolean was = recut != null;
        if (recut != null && recut.getParent() instanceof ViewGroup) {
            ((ViewGroup) recut.getParent()).removeView(recut);
        }
        recut = null;
        if (was && !quiet) {
            bar.setVisibility(View.VISIBLE);
            bar.setAlpha(0f);
            bar.setTranslationY(Round.px(24f));
            bar.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
                .setInterpolator(Pace.STANDARD).start();
            placeCrest(true);
        }
    }

    /** The last of a folder's recordings heard, or none. */
    private static Shelf.Item lastIn(ArrayList<Shelf.Item> here, ArrayList<Shelf.Item> heard) {
        String home = here.get(0).home();
        for (int i = 0; i < heard.size(); i++) {
            if (heard.get(i).home().equals(home)) {
                return heard.get(i);
            }
        }
        return null;
    }

    /**
     * A folder of recordings, as one row: a round label with the picture its
     * first recording carries, the folder's name as a title, and under it how
     * many recordings it holds and which one was heard last.
     */
    private LinearLayout folderRow(final ArrayList<Shelf.Item> here, ArrayList<Shelf.Item> heard) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                openFolder(here);
            }
        });
        if (Build.VERSION.SDK_INT >= 29) {
            /* Held, a folder gives up its covers to the collection, all at once. */
            row.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    if (spread) {
                        return true;
                    }
                    ArrayList<Bloom.Row> only = new ArrayList<Bloom.Row>();
                    only.add(row(Glyph.PICTURE, Words.s("to_covers"), new Runnable() {
                        public void run() {
                            takeCovers(here);
                        }
                    }));
                    only.add(row(Glyph.BOOK, Words.s("tags_to_text"), new Runnable() {
                        public void run() {
                            takeCards(here);
                        }
                    }));
                    ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
                    groups.add(only);
                    bloom(groups);
                    return true;
                }
            });
        }
        String title = Shelf.homeTitle(here.get(0));
        if (title.length() == 0) {
            title = Words.s(ROOM_WORDS[MUSIC]);
        }
        int[][] labels = {
            {Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER},
            {Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER},
            {Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER},
        };
        int[] label = labels[(title.hashCode() & 0x7FFFFFFF) % labels.length];
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Round.box(Tone.of(label[0]), Round.FULL));
        disc.setClipToOutline(true);
        TextView letter = Letter.serif(Letter.set(new TextView(this), Letter.TITLE_M));
        letter.setText(Shelf.initial(title));
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Tone.of(label[1]));
        disc.addView(letter, cover());
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        disc.addView(face, cover());
        Thumb.watch(face, here.get(0));
        int side = Round.dp(52);
        LinearLayout.LayoutParams discParams = new LinearLayout.LayoutParams(side, side);
        discParams.rightMargin = Round.dp(14);
        row.addView(disc, discParams);

        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(Letter.TITLE_M, Shelf.plate(title), Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(named);
        StringBuilder line = new StringBuilder(
            Words.s("in_folder").replace("{n}", String.valueOf(here.size())));
        Shelf.Item last = lastIn(here, heard);
        if (last != null) {
            line.append("  \u00B7  ").append(Words.s("folder_on")
                .replace("{w}", Shelf.own(last, Shelf.common(here))));
        }
        TextView under = words(Letter.BODY_S, line.toString(), Tone.ON_SURFACE_VARIANT);
        under.setSingleLine(true);
        under.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams underParams = wide();
        underParams.topMargin = Round.dp(2);
        said.addView(under, underParams);
        row.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** A folder's own screen opens over the music room. */
    private void openFolder(ArrayList<Shelf.Item> here) {
        folder = new ArrayList<Shelf.Item>(here);
        folderCommon = Shelf.common(folder);
        if (!open()) {
            door(FOLDER);
        }
    }

    /**
     * A folder of recordings on a screen of its own. The folder's name stands
     * at the head, and under it what every title in it begins with; each
     * line then says only what is its own. The round before each line carries
     * the recording's number in the folder rather than an initial every line
     * would share: the one playing, or heard last, is lit in the accent, and
     * those heard to their end are quiet. The screen opens at that one.
     */
    private View folderView() {
        LinearLayout column = column();
        folderRounds.clear();
        folderLit = null;
        TextView number = Letter.serif(words(Letter.TITLE_M, ROOM_NUMBERS[MUSIC], Tone.PRIMARY));
        number.setLetterSpacing(0.15f);
        column.addView(number);
        String title = folder.isEmpty() ? "" : Shelf.homeTitle(folder.get(0));
        TextView name = words(Letter.HEADLINE_M, Shelf.plate(title), Tone.ON_SURFACE);
        Letter.fit(name, PLATE, true);
        LinearLayout.LayoutParams nameParams = wide();
        nameParams.topMargin = Round.dp(6);
        /* Clear of the mark in the corner, whichever corner that is. */
        nameParams.setMarginEnd(Round.dp(56));
        column.addView(name, nameParams);
        String shared = folderCommon.trim();
        while (shared.length() > 0 && "-.,(\u2014\u2013".indexOf(shared.charAt(shared.length() - 1)) >= 0) {
            shared = shared.substring(0, shared.length() - 1).trim();
        }
        StringBuilder about = new StringBuilder();
        if (shared.length() > 0 && !shared.equalsIgnoreCase(title)) {
            about.append(shared).append("  \u00B7  ");
        }
        about.append(Words.s("in_folder").replace("{n}", String.valueOf(folder.size())));
        TextView said = words(Letter.BODY_M, about.toString(), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams saidParams = wide();
        saidParams.topMargin = Round.dp(4);
        column.addView(said, saidParams);
        if (folder.size() > 1) {
            /* The folder's order: its names, read as numbers, or the order
               its files came in, for a folder whose names do not tell. */
            final String home = folder.get(0).home();
            Pair order = new Pair(this, new String[] {Words.s("order_numbers"), Words.s("order_time")},
                null, Shelf.timeOrdered(this, home) ? 1 : 0, new Pair.Picked() {
                    public void picked(int which) {
                        reorderFolder(home, which == 1);
                    }
                });
            LinearLayout.LayoutParams orderParams = wide();
            orderParams.topMargin = Round.dp(16);
            column.addView(order, orderParams);
        }

        Sound s = Sound.live();
        String playing = s != null && s.current() != null ? s.current().key() : null;
        Shelf.Item last = folder.isEmpty() ? null
            : lastIn(folder, Shelf.current(Shelf.recentSounds(this, 30), folder));
        String lit = playing != null ? playing : (last != null ? last.key() : null);
        View litRow = null;
        ArrayList<Ruler.Entry> marks = new ArrayList<Ruler.Entry>();
        for (int i = 0; i < folder.size(); i++) {
            Shelf.Item it = folder.get(i);
            LinearLayout.LayoutParams params = wide();
            params.topMargin = Round.dp(i == 0 ? 20 : 4);
            View row = numberedRow(it, i + 1);
            column.addView(row, params);
            String own = Shelf.own(it, folderCommon);
            marks.add(new Ruler.Entry(row, own, chapter(own), true, String.valueOf(i + 1)));
            if (it.key().equals(lit)) {
                litRow = row;
            }
        }
        lightFolder(lit);
        final ScrollView view = scroll(column);
        final View target = litRow;
        if (target != null) {
            /* Once the rows have their places, the screen stands at the one lit. */
            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int ol, int ot, int or, int ob) {
                    if (target.getTop() <= 0) {
                        return;
                    }
                    view.removeOnLayoutChangeListener(this);
                    view.scrollTo(0, Math.max(0, target.getTop() - Round.dp(160)));
                }
            });
        }
        return ruled(view, marks);
    }

    /**
     * A folder told to keep another order: its recordings are put in it, the
     * room's list takes it in their place, and the screen is drawn again a
     * moment later, once the capsule has slid.
     */
    private void reorderFolder(String home, boolean byTime) {
        Shelf.setTimeOrdered(this, home, byTime);
        Shelf.reorder(this, folder);
        int from = -1;
        for (int i = 0; i < sounds.size(); i++) {
            if (sounds.get(i).home().equals(home)) {
                from = i;
                break;
            }
        }
        if (from >= 0) {
            for (int i = 0; i < folder.size() && from + i < sounds.size(); i++) {
                sounds.set(from + i, folder.get(i));
            }
        }
        final ArrayList<Shelf.Item> kept = new ArrayList<Shelf.Item>(sounds);
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                Shelf.keepSounds(app, kept);
            }
        }, "keep").start();
        Trace.note("folder order: " + (byTime ? "time" : "numbers"));
        clock.postDelayed(new Runnable() {
            public void run() {
                if (FOLDER.equals(front())) {
                    forgetScene();
                    swap(folderView());
                }
            }
        }, Pace.ARRIVE);
    }

    /** One recording in its folder: its number in a round, its own words, and where it was left. */
    private LinearLayout numberedRow(final Shelf.Item it, int n) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(8), Round.dp(16), Round.dp(8));
        row.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                listen(it);
            }
        });
        row.setOnLongClickListener(held(it));
        TextView round = words(Letter.LABEL_L, String.valueOf(n), Tone.ON_SURFACE_VARIANT);
        round.setGravity(Gravity.CENTER);
        round.setSingleLine(true);
        int side = Round.dp(40);
        LinearLayout.LayoutParams roundParams = new LinearLayout.LayoutParams(side, side);
        roundParams.rightMargin = Round.dp(14);
        row.addView(round, roundParams);
        folderRounds.put(it.key(), round);

        long at = Shelf.soundAt(this, it.key());
        boolean done = at <= 0 && Shelf.done(this, it.key());
        TextView named = words(Letter.TITLE_M, Shelf.own(it, folderCommon), Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (at > 0) {
            LinearLayout said = new LinearLayout(this);
            said.setOrientation(LinearLayout.VERTICAL);
            said.addView(named);
            TextView under = words(Letter.BODY_S,
                Words.s("left_at").replace("{t}", clock(at)), Tone.ON_SURFACE_VARIANT);
            said.addView(under);
            row.addView(said, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        } else {
            row.addView(named, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        /* Heard to its end, a line steps back, but stays to be found. */
        row.setAlpha(done ? 0.55f : 1f);
        paintRound(round, false);
        return row;
    }

    private static void paintRound(TextView round, boolean lit) {
        round.setBackground(Round.box(Tone.of(lit ? Tone.PRIMARY : Tone.SURFACE_HIGHEST), Round.FULL));
        round.setTextColor(Tone.of(lit ? Tone.ON_PRIMARY : Tone.ON_SURFACE_VARIANT));
    }

    /** The round of the recording playing, or heard last, lit; the one lit before goes back. */
    private void lightFolder(String key) {
        if (folderLit != null && folderRounds.get(folderLit) != null) {
            paintRound(folderRounds.get(folderLit), false);
        }
        folderLit = key;
        if (key != null && folderRounds.get(key) != null) {
            paintRound(folderRounds.get(key), true);
        }
    }

    /** The recordings come from the same one walk as the shelf. */
    private void gatherSounds() {
        gather();
    }

    /**
     * A recording: a round label with its initial, as a record has, its
     * title, and a line with its kind, its folder and where it was left.
     */
    private LinearLayout soundRow(final Shelf.Item it, boolean big) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Round.dp(12), Round.dp(10), Round.dp(16), Round.dp(10));
        row.setBackground(Round.touch(Round.box(Tone.of(big ? Tone.SURFACE_HIGH
            : Tone.SURFACE_CONTAINER), Round.L), Tone.of(Tone.ON_SURFACE), Round.L));
        row.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                listen(it);
            }
        });
        row.setOnLongClickListener(held(it));
        boolean nameless = Shelf.nameless(it);
        String title = nameless ? it.folder : Shelf.title(it.name);
        int[][] labels = {
            {Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER},
            {Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER},
            {Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER},
        };
        int[] label = labels[(title.hashCode() & 0x7FFFFFFF) % labels.length];
        /* A round label with the initial, and over it, once found, the
           picture the recording carries. */
        FrameLayout disc = new FrameLayout(this);
        disc.setBackground(Round.box(Tone.of(label[0]), Round.FULL));
        disc.setClipToOutline(true);
        TextView letter = Letter.serif(Letter.set(new TextView(this), Letter.TITLE_M));
        letter.setText(title.length() > 0 ? title.substring(0, 1).toUpperCase() : "\u00B7");
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Tone.of(label[1]));
        disc.addView(letter, cover());
        android.widget.ImageView face = new android.widget.ImageView(this);
        face.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        disc.addView(face, cover());
        Thumb.watch(face, it);
        int side = Round.dp(big ? 52 : 44);
        LinearLayout.LayoutParams discParams = new LinearLayout.LayoutParams(side, side);
        discParams.rightMargin = Round.dp(14);
        row.addView(disc, discParams);

        LinearLayout said = new LinearLayout(this);
        said.setOrientation(LinearLayout.VERTICAL);
        TextView named = words(big ? Letter.TITLE_L : Letter.TITLE_M, title, Tone.ON_SURFACE);
        named.setMaxLines(2);
        named.setEllipsize(android.text.TextUtils.TruncateAt.END);
        said.addView(named);
        StringBuilder line = new StringBuilder(Shelf.kind(it.name).toUpperCase());
        if (nameless) {
            line.insert(0, Shelf.title(it.name) + "  \u00B7  ");
        } else if (it.folder != null && it.folder.length() > 0) {
            line.append("  \u00B7  ").append(it.folder);
        }
        long at = Shelf.soundAt(this, it.key());
        if (at > 0) {
            line.append("  \u00B7  ").append(Words.s("left_at").replace("{t}", clock(at)));
        }
        TextView under = words(Letter.BODY_S, line.toString(), Tone.ON_SURFACE_VARIANT);
        under.setSingleLine(true);
        under.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams underParams = wide();
        underParams.topMargin = Round.dp(2);
        said.addView(under, underParams);
        row.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Minutes and seconds, or hours too when there are any. */
    private static String clock(long ms) {
        long all = Math.max(0L, ms / 1000L);
        long h = all / 3600L;
        long m = (all % 3600L) / 60L;
        long sec = all % 60L;
        String ss = (sec < 10 ? "0" : "") + sec;
        if (h > 0) {
            return h + ":" + (m < 10 ? "0" : "") + m + ":" + ss;
        }
        return m + ":" + ss;
    }

    /** A recording is handed to the service, which plays it whether or not the window stays. */
    private void hear(Shelf.Item it) {
        if (Build.VERSION.SDK_INT >= 33 && !askedNotes
            && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            askedNotes = true;
            requestPermissions(new String[] {"android.permission.POST_NOTIFICATIONS"}, 21);
        }
        Intent go = new Intent(this, Sound.class);
        go.setAction(Sound.OPEN);
        go.putExtra("uri", it.uri.toString());
        go.putExtra("key", it.key());
        go.putExtra("name", it.name);
        startForegroundService(go);
        Trace.note("hear " + Shelf.kind(it.name));
    }

    /**
     * A recording touched in the room plays, and its own screen opens over the
     * room; the one already playing only opens its screen. The screen's round
     * button goes back to the room, and the sound goes on behind it.
     */
    private void listen(Shelf.Item it) {
        Sound s = Sound.live();
        boolean same = s != null && s.current() != null && s.current().uri.equals(it.uri);
        if (!same) {
            hear(it);
        }
        listening = it;
        if (!open() || FOLDER.equals(front())) {
            door(LISTENING);
        }
    }

    /**
     * The player's strip stands in the music room and on the listening
     * screen, and on the screens of the settings opened over either; nowhere
     * else.
     */
    private void syncTune() {
        syncTune(false);
    }

    /**
     * Only the service's own word — the next recording began — moves the
     * listening screen on; a recording just asked for may not have reached
     * the service yet, and the screen must not follow the one before it.
     */
    private void syncTune(boolean follow) {
        Sound s = Sound.live();
        if (follow && LISTENING.equals(front()) && s != null && s.current() != null
            && listening != null
            && !s.current().uri.equals(listening.uri)) {
            /* The next recording began while its screen was open: the screen follows it. */
            listening = s.current();
            forgetScene();
            swap(listenView(listening));
            plate(true);
        }
        if (FOLDER.equals(front()) && s != null && s.current() != null
            && !s.current().key().equals(folderLit) && folderRounds.containsKey(s.current().key())) {
            lightFolder(s.current().key());
        }
        String below = under();
        boolean want = ((room == MUSIC && below == null) || LISTENING.equals(below)
            || FOLDER.equals(below)) && s != null && s.current() != null;
        if (want) {
            showTune();
            tuneLine();
        } else {
            hideTune();
        }
    }

    private void showTune() {
        if (tune != null && tune.getVisibility() == View.VISIBLE) {
            return;
        }
        if (tune == null) {
            tune = new Voice(this, new Voice.Hands() {
                public void back() {
                    Sound s = Sound.live();
                    if (s != null) {
                        s.nudge(-Sound.BACK_MS);
                    }
                }

                public void hold() {
                    Sound s = Sound.live();
                    if (s != null) {
                        s.toggle();
                    }
                }

                public void on() {
                    Sound s = Sound.live();
                    if (s != null) {
                        s.nudge(Sound.ON_MS);
                    }
                }

                public void faster() {
                    showPace(false);
                }

                public void dialOpen() {
                    openTuneDial();
                }

                public void dialTurn(float dx) {
                    if (arc != null) {
                        ticked(arc.turn(dx));
                    }
                }

                public void dialEdge(float across) {
                    float was = edgeSpeed;
                    edgeSpeed = across < 0.1f ? -(0.1f - across) * 12f
                        : (across > 0.9f ? (across - 0.9f) * 12f : 0f);
                    if (was == 0f && edgeSpeed != 0f) {
                        clock.post(edgeRoll);
                    }
                }

                public void dialClose(boolean take) {
                    closeDial(take);
                }
            });
            bar.addView(tune, 0, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        tune.tint();
        tune.animate().cancel();
        field.animate().cancel();
        if (barFlow != null) {
            barFlow.cancel();
        }
        field.setVisibility(View.GONE);
        tune.setVisibility(View.VISIBLE);
        tune.setAlpha(0f);
        tune.setTranslationY(Round.px(10f));
        tune.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        tune.start();
        bar.setBackground(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL));
        clock.removeCallbacks(tuneBeat);
        clock.postDelayed(tuneBeat, 500L);
    }

    private void hideTune() {
        clock.removeCallbacks(tuneBeat);
        if (tune == null || tune.getVisibility() != View.VISIBLE) {
            return;
        }
        if (tuneDial) {
            closeDial(false);
        }
        final Voice gone = tune;
        gone.playing(false);
        gone.animate().alpha(0f).translationY(Round.px(18f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
            .withEndAction(new Runnable() {
                public void run() {
                    gone.end();
                    gone.setVisibility(View.GONE);
                    gone.setTranslationY(0f);
                    if (strip == null || strip.getVisibility() != View.VISIBLE) {
                        field.setVisibility(View.VISIBLE);
                        field.setAlpha(0f);
                        field.animate().alpha(1f).setStartDelay(0L).setDuration(Pace.ARRIVE)
                            .setInterpolator(Pace.STANDARD).start();
                    }
                }
            }).start();
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
    }

    /** The strip is told the name, the time and the pace, twice a second while it stands. */
    private void tuneLine() {
        Sound s = Sound.live();
        if (tune == null || s == null || s.current() == null) {
            return;
        }
        long pos = s.position();
        long all = s.duration();
        String place = clock(pos) + (all > 0 ? " / " + clock(all) : "") + "  \u00B7  "
            + String.format(java.util.Locale.ROOT, "%.1f\u00D7", Shelf.pace(this, s.current()));
        tune.line(Shelf.label(s.current()), place, all > 0 ? pos / (float) all : 0f);
        tune.playing(s.playing());
    }

    private final Runnable tuneBeat = new Runnable() {
        public void run() {
            if (tune == null || tune.getVisibility() != View.VISIBLE) {
                return;
            }
            tuneLine();
            clock.postDelayed(this, 500L);
        }
    };

    /** Whether the dial up is the film's time. */
    private boolean filmDial;
    /** Whether the video's strip stands, and with it the mark. */
    private boolean filmChrome;
    /** Whether this video has been found to refuse another speed; said once. */
    private long filmRefusedOf = -1L;

    /** The recordings' dial of time, over a film: the same drum, the same ticks, the film's own length. */
    private void openFilmDial() {
        final Film f = film;
        if (f == null || f.length() <= 0L) {
            return;
        }
        closeDial(false);
        final long all = f.length();
        final String title = Shelf.title(f.item().name);
        arc = new Arc(this, new Arc.Say() {
            public String page(int i) {
                return clock(i * 5000L) + " / " + clock(all);
            }

            public String words(int i) {
                return title;
            }

            public String distance(int i) {
                long d = i * 5000L - f.now();
                String sign = d < 0 ? "\u2212" : (d > 0 ? "+" : "");
                return sign + clock(Math.abs(d));
            }
        });
        int at = (int) (f.now() / 5000L);
        arc.set((int) (all / 5000L) + 1, at, at);
        arc.major(12);
        filmDial = true;
        dialTick = at;
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Round.dp(152), Gravity.BOTTOM);
        where.setMargins(Round.dp(10), 0, Round.dp(10), Round.dp(96));
        stage.addView(arc, where);
        arc.setElevation(Round.px(6f));
        arc.setAlpha(0f);
        arc.setTranslationY(Round.px(40f));
        arc.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.GROW)
            .setInterpolator(Pace.EMPHASIS).start();
    }

    /** The same dial as the voice's, in time: a tick every five seconds, a taller one every minute. */
    private void openTuneDial() {
        final Sound s = Sound.live();
        if (s == null || s.current() == null || s.duration() <= 0) {
            return;
        }
        closeDial(false);
        final long all = s.duration();
        final String title = Shelf.label(s.current());
        arc = new Arc(this, new Arc.Say() {
            public String page(int i) {
                return clock(i * 5000L) + " / " + clock(all);
            }

            public String words(int i) {
                return title;
            }

            public String distance(int i) {
                long d = i * 5000L - s.position();
                String sign = d < 0 ? "\u2212" : (d > 0 ? "+" : "");
                return sign + clock(Math.abs(d));
            }
        });
        int at = (int) (s.position() / 5000L);
        arc.set((int) (all / 5000L) + 1, at, at);
        arc.major(12);
        tuneDial = true;
        dialTick = at;
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Round.dp(152), Gravity.BOTTOM);
        where.setMargins(Round.dp(10), 0, Round.dp(10), Round.dp(10));
        stage.addView(arc, where);
        arc.setElevation(Round.px(6f));
        arc.setAlpha(0f);
        arc.setTranslationY(Round.px(40f));
        arc.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.GROW)
            .setInterpolator(Pace.EMPHASIS).start();
    }

    /**
     * A key of the widget pressed while the sound service was not alive: the
     * window was opened with the key's wish, and it wakes the service with it.
     */
    /** The video the widget was showing, opened where it stopped. */
    private void openWatched(String key) {
        ArrayList<Gallery.Photo> halfway = halfway();
        for (int i = 0; i < halfway.size(); i++) {
            if (halfway.get(i).item().key().equals(key)) {
                album = halfway;
                albumName = Words.s("continue_video");
                albumRoom = FILMS;
                photoAt = i;
                door(FILM);
                return;
            }
        }
    }

    private boolean wished(Intent intent) {
        String act = intent == null ? null : intent.getAction();
        if (!Sound.TOGGLE.equals(act) && !Sound.BACK.equals(act) && !Sound.ON.equals(act)
            && !Sound.NEXT.equals(act) && !Sound.PREV.equals(act)) {
            return false;
        }
        /* The last thing that sounded was a book read aloud: the voice
           cannot rise without the book's text, and only the reader has it.
           So the book is opened again, at its page, and the voice goes on
           from where it was put away. */
        if (Keep.lastVoice(this) && Keep.voiceBook(this).length() > 0) {
            Shelf.Item it = null;
            ArrayList<Shelf.Item> read = Shelf.current(Shelf.recent(this, 30), books);
            for (int i = 0; i < read.size() && it == null; i++) {
                if (read.get(i).key().equals(Keep.voiceBook(this))) {
                    it = read.get(i);
                }
            }
            if (it != null) {
                final boolean go = Sound.TOGGLE.equals(act);
                if (open()) {
                    enter(BOOKS);
                } else if (room != BOOKS) {
                    enter(BOOKS);
                }
                final Shelf.Item book0 = it;
                clock.postDelayed(new Runnable() {
                    public void run() {
                        read(book0);
                        if (go) {
                            clock.postDelayed(new Runnable() {
                                public void run() {
                                    aloudFrom = -1;
                                    aloud();
                                }
                            }, Pace.ARRIVE + 200L);
                        }
                    }
                }, Pace.ARRIVE);
                return true;
            }
        }
        if ("film".equals(Keep.lastKind(this))) {
            /* The last thing that sounded was a video: it is opened where it
               stopped, and a key that means play starts it there. */
            ArrayList<Shelf.Item> seen = Shelf.recentWatched(this, 1);
            if (!seen.isEmpty()) {
                final String key = seen.get(0).key();
                enter(FILMS);
                clock.postDelayed(new Runnable() {
                    public void run() {
                        openWatched(key);
                    }
                }, Pace.ARRIVE);
                return true;
            }
        }
        startForegroundService(new Intent(this, Sound.class).setAction(act));
        if (room != MUSIC || open()) {
            enter(MUSIC);
        }
        return true;
    }

    // ------------------------------------------------------------ listening

    /**
     * One recording on a screen of its own: its cover as large as the width
     * allows, its name in the book face, and what its tags say about it. The
     * keys are the strip's in the bar below; the round button goes back to
     * the room, and the sound goes on.
     */
    private View listenView(final Shelf.Item it) {
        LinearLayout column = column();
        if (it == null) {
            return scroll(column);
        }
        int side = getResources().getDisplayMetrics().widthPixels - Round.dp(12 + 48);
        final FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Round.box(Tone.of(Tone.TERTIARY_CONTAINER), Round.XL));
        frame.setClipToOutline(true);
        frame.setElevation(Round.px(4f));
        String named = Shelf.label(it);
        TextView initial = Letter.serif(Letter.set(new TextView(this), Letter.DISPLAY_L));
        String stood = Shelf.initial(named);
        initial.setText(stood.length() > 0 ? stood : "\u266A");
        initial.setTextColor(Tone.of(Tone.ON_TERTIARY_CONTAINER));
        initial.setGravity(Gravity.CENTER);
        frame.addView(initial, cover());
        final android.widget.ImageView picture = new android.widget.ImageView(this);
        picture.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        picture.setAlpha(0f);
        frame.addView(picture, cover());
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(side, side);
        frameParams.gravity = Gravity.CENTER_HORIZONTAL;
        /* The mark stands in the corner over a recording too; the cover starts below it. */
        frameParams.topMargin = Round.dp(52);
        column.addView(frame, frameParams);

        TextView title = words(Letter.HEADLINE_S, named, Tone.ON_SURFACE);
        LinearLayout.LayoutParams titleParams = wide();
        titleParams.topMargin = Round.dp(24);
        column.addView(title, titleParams);
        final TextView who = words(Letter.BODY_L, "", Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whoParams = wide();
        whoParams.topMargin = Round.dp(6);
        column.addView(who, whoParams);
        TextView where = words(Letter.BODY_S, Shelf.kind(it.name).toUpperCase()
            + (it.folder != null && it.folder.length() > 0 ? "  \u00B7  " + it.folder : ""),
            Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whereParams = wide();
        whereParams.topMargin = Round.dp(4);
        column.addView(where, whereParams);

        /* The tags and the picture inside the file are read away from the
           screen: a large cover can take a moment to come out. */
        final int want = side;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                android.media.MediaMetadataRetriever tags = new android.media.MediaMetadataRetriever();
                String artist = null;
                String album = null;
                android.graphics.Bitmap art = null;
                try {
                    tags.setDataSource(app, it.uri);
                    artist = tags.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    album = tags.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    byte[] raw = tags.getEmbeddedPicture();
                    if (raw != null) {
                        android.graphics.BitmapFactory.Options look = new android.graphics.BitmapFactory.Options();
                        look.inJustDecodeBounds = true;
                        android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, look);
                        int scale = 1;
                        while (look.outWidth / (scale * 2) >= want && look.outHeight / (scale * 2) >= want) {
                            scale *= 2;
                        }
                        android.graphics.BitmapFactory.Options take = new android.graphics.BitmapFactory.Options();
                        take.inSampleSize = scale;
                        art = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.length, take);
                    }
                } catch (Exception unreadable) {
                    Trace.note("no tags: " + unreadable.getClass().getSimpleName());
                } finally {
                    try {
                        tags.release();
                    } catch (Exception ignored) {
                        // the reader lets go of the file either way
                    }
                }
                final String line = join(artist, album);
                final android.graphics.Bitmap shown = art;
                runOnUiThread(new Runnable() {
                    public void run() {
                        who.setText(line);
                        if (shown != null) {
                            picture.setImageBitmap(shown);
                            picture.animate().alpha(1f).setDuration(Pace.ARRIVE)
                                .setInterpolator(Pace.STANDARD).start();
                        }
                    }
                });
            }
        }, "tags").start();
        LinearLayout.LayoutParams shareParams = buttonPlace(28);
        column.addView(button(Words.s("share"), OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                share(it);
            }
        }), shareParams);
        return scroll(column);
    }

    private static String join(String a, String b) {
        boolean hasA = a != null && a.trim().length() > 0;
        boolean hasB = b != null && b.trim().length() > 0;
        if (hasA && hasB && !a.trim().equals(b.trim())) {
            return a.trim() + "  \u00B7  " + b.trim();
        }
        return hasA ? a.trim() : (hasB ? b.trim() : "");
    }

    /**
     * A recording handed over by another application — a file manager, a
     * message: it plays, and its screen opens over the music room.
     */
    private boolean handed(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())
            || intent.getData() == null) {
            return false;
        }
        String type = intent.getType() == null ? getContentResolver().getType(intent.getData())
            : intent.getType();
        if (type == null || !type.startsWith("audio/")) {
            return false;
        }
        Uri where = intent.getData();
        String name = where.getLastPathSegment();
        android.database.Cursor q = null;
        try {
            q = getContentResolver().query(where,
                new String[] {android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst() && q.getString(0) != null) {
                name = q.getString(0);
            }
        } catch (Exception nameless) {
            Trace.note("a handed recording has no name: " + nameless);
        } finally {
            if (q != null) {
                q.close();
            }
        }
        final Shelf.Item it = new Shelf.Item(name == null ? "" : name, where, type, 0L, 0L, "");
        if (room != MUSIC || open()) {
            enter(MUSIC);
        }
        clock.postDelayed(new Runnable() {
            public void run() {
                hear(it);
                listening = it;
                if (!open()) {
                    door(LISTENING);
                }
            }
        }, Pace.ARRIVE);
        return true;
    }

    // ---------------------------------------------------------------- reading

    /**
     * Opens a book as a screen of ours: the page fills the stage, the field
     * names the book, and the round button is the door back to the shelf.
     * Text of every kind is laid out as a page of a printed book; a pdf and
     * a comic are shown as the pictures they are.
     */
    private void read(Shelf.Item it) {
        if (open() || spread) {
            return;
        }
        View made = pageFor(it);
        if (made == null) {
            flash(Glyph.BOOK, Words.s("unreadable"));
            return;
        }
        book = it;
        reading = made;
        Shelf.remember(this, it);
        Trace.note("reading " + Shelf.kind(it.name));
        door(READING);
        voiceShown();
    }

    private View pageFor(final Shelf.Item it) {
        final String key = it.key();
        String nm = it.name == null ? "" : it.name.toLowerCase(java.util.Locale.ROOT);
        Runnable clear = new Runnable() {
            public void run() {
                chrome();
            }
        };
        if (nm.endsWith(".cbz")) {
            final Reader.Cbz v = new Reader.Cbz(this);
            if (!v.open(this, it.uri, it.name)) {
                return null;
            }
            v.goTo(Shelf.pageOf(this, key));
            v.onChrome(clear);
            v.whenTurned(new Runnable() {
                public void run() {
                    Shelf.setPage(Main.this, key, v.pageIndex());
                }
            });
            cbz = v;
            return v;
        }
        if (Reader.isPdf(it)) {
            final Reader.Pdf v = new Reader.Pdf(this);
            if (!v.open(this, it.uri)) {
                return null;
            }
            v.goTo(Shelf.pageOf(this, key));
            v.onChrome(clear);
            v.whenTurned(new Runnable() {
                public void run() {
                    Shelf.setPage(Main.this, key, v.pageIndex());
                }
            });
            pdf = v;
            return v;
        }
        CharSequence body;
        if (Docs.isWrapped(nm)) {
            body = Docs.wrapped(this, it.uri, nm);
        } else if (nm.endsWith(".rtf")) {
            body = Docs.rtf(Shelf.read(this, it.uri));
        } else if (Docs.isMarked(nm)) {
            body = Docs.clean(Shelf.read(this, it.uri));
        } else if (nm.endsWith(".epub")) {
            body = Epub.read(this, it.uri);
        } else if (Reader.isFb2(it)) {
            body = Reader.fb2(this, it.uri);
        } else {
            String raw = Shelf.read(this, it.uri);
            body = raw == null ? null : Reader.markdown(raw);
        }
        if (body == null || body.length() == 0) {
            return null;
        }
        final Reader.Page v = new Reader.Page(this);
        v.set(body, Shelf.title(it.name));
        v.goTo(Shelf.pageOf(this, key));
        v.onChrome(clear);
        v.whenTurned(new Runnable() {
            public void run() {
                Shelf.setPage(Main.this, key, v.pageIndex());
            }
        });
        v.setOnLongClickListener(new View.OnLongClickListener() {
            public boolean onLongClick(View x) {
                aloudFrom = v.charAtTouch();
                readerMenu();
                return true;
            }
        });
        leaf = v;
        return v;
    }

    /**
     * The middle of the page is one switch: it takes everything but the
     * paper away, the phone's bars included, and brings it all back.
     */
    private void chrome() {
        if (sheet != null || spread) {
            return;
        }
        if (quiet) {
            quietOff();
        } else {
            quietOn(false);
        }
    }

    private void showBar() {
        bar.animate().cancel();
        bar.setVisibility(View.VISIBLE);
        bar.setAlpha(1f);
        bar.setTranslationY(0f);
    }

    /**
     * Reading without anything else: the bar goes, the phone's own bars go,
     * and the paper runs to the edges of the glass with square corners. The
     * middle of the page, or the back gesture, brings it all back.
     */
    private void quietOn(boolean asked) {
        boolean picture = PHOTO.equals(front()) || FILM.equals(front());
        if (quiet || (reading == null && !picture)) {
            return;
        }
        quiet = true;
        if (bar.getVisibility() == View.VISIBLE) {
            bar.animate().alpha(0f).translationY(Round.px(24f)).setStartDelay(0L)
                .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
                .withEndAction(new Runnable() {
                    public void run() {
                        bar.setVisibility(View.GONE);
                    }
                }).start();
        }
        edges(true);
        immersive(true);
        placeCrest(true);
        /* The way back is said a few times, and then it is known. A picture
           is always opened this way, so there is nothing to tell. */
        int told = Shelf.prefs(this).getInt("quietTold", 0);
        if (asked && !picture && told < 3) {
            Shelf.prefs(this).edit().putInt("quietTold", told + 1).apply();
            flash(Glyph.BOOK, Words.s("quiet_hint"));
        }
    }

    private void quietOff() {
        if (!quiet) {
            return;
        }
        quiet = false;
        immersive(false);
        edges(false);
        placeCrest(true);
        showBar();
        bar.setAlpha(0f);
        bar.animate().alpha(1f).setStartDelay(0L).setDuration(Pace.GROW)
            .setInterpolator(Pace.STANDARD).start();
    }

    /** The stage's margins and corners: none while reading quietly, the usual ones otherwise. */
    private void edges(boolean none) {
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) stage.getLayoutParams();
        if (none) {
            params.setMargins(0, 0, 0, 0);
        } else {
            params.setMargins(Round.dp(6), Round.dp(4), Round.dp(6), 0);
        }
        stage.setLayoutParams(params);
        stage.invalidateOutline();
    }

    /** The phone's own bars, hidden until a swipe from the edge brings them for a moment. */
    private void immersive(boolean hide) {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController bars = getWindow().getInsetsController();
            if (bars == null) {
                return;
            }
            if (hide) {
                bars.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                bars.setSystemBarsBehavior(
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                bars.show(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
            }
            return;
        }
        getWindow().getDecorView().setSystemUiVisibility(hide
            ? View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            : 0);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        /* A dialog of the system, or a pull of the shade, gives the bars back;
           coming back to the page takes them away again. */
        if (focused && quiet) {
            immersive(true);
        }
    }

    /** The book is put down: the voice stops, the pages stop turning, files are let go. */
    private void closeBook() {
        quietOff();
        hush();
        stopTurning();
        closeSheet();
        showBar();
        if (pdf != null) {
            pdf.close();
        }
        if (cbz != null) {
            cbz.close();
        }
        pdf = null;
        cbz = null;
        leaf = null;
        reading = null;
    }

    /**
     * Held on the page: what can be done with it, as capsules. The voice
     * starts from the words under the finger, not from the top.
     */
    /**
     * The page held: what can be done here and with this book, grown from
     * the finger. First what is about this place — mark this page, read
     * aloud from the word held, pick words — then the book's own marks and
     * handing it on, and apart, the paper, which opens a sheet of its own.
     */
    private void readerMenu() {
        if (leaf == null) {
            return;
        }
        final String key = book.key();
        boolean marked = Shelf.marks(this, key).contains(Integer.valueOf(leaf.pageIndex()));
        ArrayList<Bloom.Row> here = new ArrayList<Bloom.Row>();
        here.add(row(Glyph.RIBBON, Words.s(marked ? "unmark_page" : "mark_page"), new Runnable() {
            public void run() {
                boolean now = Shelf.toggleMark(Main.this, key, leaf.pageIndex());
                flash(Glyph.TICK, Words.s(now ? "marked" : "unmarked"));
            }
        }));
        here.add(row(Glyph.LANGUAGE, Words.s(speaking ? "aloud_stop" : "aloud"), new Runnable() {
            public void run() {
                if (speaking) {
                    hush();
                } else {
                    aloud();
                }
            }
        }));
        here.add(row(Glyph.CARET, Words.s("pick"), new Runnable() {
            public void run() {
                pick();
            }
        }));
        if (speaking) {
            here.add(sleepRow());
        }
        if (turning) {
            here.add(row(Glyph.DOWN, Words.s("turning_stop"), new Runnable() {
                public void run() {
                    stopTurning();
                }
            }));
        }
        ArrayList<Bloom.Row> whole = new ArrayList<Bloom.Row>();
        int count = Shelf.marks(this, key).size();
        whole.add(new Bloom.Row(Glyph.BOOK, Words.s("marks"), count > 0 ? String.valueOf(count) : null,
            false, new Bloom.Act() {
                public boolean act(Bloom.Row r, Bloom b) {
                    marksMenu();
                    return true;
                }
            }));
        whole.add(row(Glyph.SHARE, Words.s("share_book"), new Runnable() {
            public void run() {
                share(book);
            }
        }));
        ArrayList<Bloom.Row> paper = new ArrayList<Bloom.Row>();
        paper.add(new Bloom.Row(Glyph.LOOK, Words.s("paper"), "\u203A", false, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                paperSheet();
                return true;
            }
        }));
        ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
        groups.add(here);
        groups.add(whole);
        groups.add(paper);
        bloom(groups);
    }

    // ------------------------------------------------------------------ the sleep

    /** The steps of the sleep, in minutes; nought is never, and the last is the end of what plays. */
    private static final int[] SLEEP = {0, 5, 10, 15, 20, 30, 45, 60, 90, -1};

    /** A row for whatever sounds: the sleep, and how it now stands. */
    private Bloom.Row sleepRow() {
        return new Bloom.Row(Glyph.PAUSE, Words.s("sleep"), sleepSaid(), false, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                sleepSheet();
                return true;
            }
        });
    }

    /** How the sleep stands, in a word: the minutes left, the end of the piece, or nothing. */
    private String sleepSaid() {
        if (Keep.sleepEnd(this)) {
            return Words.s("sleep_end");
        }
        long at = Keep.sleepAt(this);
        if (at <= 0L) {
            return null;
        }
        long left = Math.max(0L, at - System.currentTimeMillis());
        return Words.s("minutes_n").replace("{n}", String.valueOf(Math.max(1L, Math.round(left / 60000.0))));
    }

    /**
     * The sleep, on a sheet from below: a drum of minutes, and at its end the
     * end of what is playing. Whatever sounds — a recording, a book read
     * aloud, a video on its own screen — goes quiet when the hour comes, the
     * sound fading over a few seconds first.
     */
    private void sleepSheet() {
        closePaper();
        final FrameLayout layer = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.55f));
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closePaper();
            }
        });
        layer.addView(scrim, cover());
        LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setPadding(Round.dp(24), Round.dp(10), Round.dp(24), Round.dp(24));
        sheet.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.XL));
        View handle = new View(this);
        handle.setBackground(Round.box(Tone.of(Tone.ON_SURFACE_VARIANT, 0.4f), Round.FULL));
        LinearLayout.LayoutParams handleAt = new LinearLayout.LayoutParams(Round.dp(32), Round.dp(4));
        handleAt.gravity = Gravity.CENTER_HORIZONTAL;
        handleAt.bottomMargin = Round.dp(14);
        sheet.addView(handle, handleAt);
        sheet.addView(words(Letter.TITLE_L, Words.s("sleep"), Tone.ON_SURFACE));
        final TextView when = words(Letter.BODY_M, sleepWhen(), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whenAt = wide();
        whenAt.topMargin = Round.dp(4);
        whenAt.bottomMargin = Round.dp(12);
        sheet.addView(when, whenAt);
        String[] steps = new String[SLEEP.length];
        for (int i = 0; i < SLEEP.length; i++) {
            steps[i] = SLEEP[i] == 0 ? Words.s("sleep_never")
                : SLEEP[i] < 0 ? Words.s("sleep_end") : String.valueOf(SLEEP[i]);
        }
        int start = Math.max(0, Math.min(SLEEP.length - 1, Keep.sleepStep(this)));
        sheet.addView(new Depth(this, steps, start, new Depth.Turned() {
            public void turned(int step, boolean done) {
                setSleep(step, done);
                when.setText(sleepWhen());
            }
        }), wide());
        layer.addView(sheet, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        stage.addView(layer, cover());
        paperLayer = layer;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        sheet.setTranslationY(Round.px(320f));
        sheet.animate().translationY(0f).setDuration(Pace.GROW).setInterpolator(Pace.EMPHASIS).start();
    }

    private void setSleep(int step, boolean done) {
        int minutes = SLEEP[Math.max(0, Math.min(SLEEP.length - 1, step))];
        if (minutes == 0) {
            Keep.setSleep(this, 0L, step, false);
        } else if (minutes < 0) {
            Keep.setSleep(this, 0L, step, true);
        } else {
            Keep.setSleep(this, System.currentTimeMillis() + minutes * 60000L, step, false);
        }
        if (done) {
            /* Written down where the drum came to rest, not at every step it passed. */
            Trace.note("sleep: " + (minutes == 0 ? "never" : minutes < 0 ? "at the end" : minutes + " minutes"));
        }
    }

    /** When the sound is to go quiet, said in full: at such an hour, or after this piece. */
    private String sleepWhen() {
        if (Keep.sleepEnd(this)) {
            return Words.s("sleep_after_this");
        }
        long at = Keep.sleepAt(this);
        if (at <= 0L) {
            return Words.s("sleep_not_set");
        }
        return Words.s("sleep_at").replace("{t}",
            new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(at)));
    }

    /** A row that turns the repeat: nothing, this recording, the folder — and says which it is now. */
    private Bloom.Row repeatRow() {
        final int how = Keep.repeat(this);
        String said = Words.s(how == Keep.AGAIN_ONE ? "again_one"
            : how == Keep.AGAIN_FOLDER ? "again_folder" : "again_none");
        return new Bloom.Row(Glyph.TURN, Words.s("again"), said, false, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                int next = (Keep.repeat(Main.this) + 1) % 3;
                Keep.setRepeat(Main.this, next);
                Trace.note("repeat: " + next);
                flash(Glyph.TURN, Words.s("again") + "  \u00B7  " + Words.s(next == Keep.AGAIN_ONE
                    ? "again_one" : next == Keep.AGAIN_FOLDER ? "again_folder" : "again_none"));
                return true;
            }
        });
    }

    /** The marked pages of the book, grown from where the marks were asked for; each opens the book there. */
    private void marksMenu() {
        final ArrayList<Integer> marks = Shelf.marks(this, book.key());
        if (marks.isEmpty()) {
            flash(Glyph.BOOK, Words.s("no_marks"));
            return;
        }
        ArrayList<Bloom.Row> list = new ArrayList<Bloom.Row>();
        for (int i = 0; i < marks.size() && i < 30; i++) {
            final int at = marks.get(i).intValue();
            list.add(row(Glyph.RIBBON, Words.s("page_n").replace("{n}", String.valueOf(at + 1)),
                new Runnable() {
                    public void run() {
                        if (leaf != null) {
                            leaf.showPage(at);
                            Shelf.setPage(Main.this, book.key(), at);
                        }
                    }
                }));
        }
        ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
        groups.add(list);
        bloom(groups);
    }

    // ------------------------------------------------------------------ the menu at the finger

    private Bloom bloom;
    /** Whether the finger that opened the menu is still down, and so chooses by sliding. */
    private boolean bloomFollow;
    private float downRawX;
    private float downRawY;
    private boolean fingerDown;

    /** A row that does a thing and lets the menu fold. */
    private Bloom.Row row(int mark, String words, final Runnable run) {
        return new Bloom.Row(mark, words, null, false, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                run.run();
                return true;
            }
        });
    }

    /** Deleting: the row asks once more itself, unless the system is about to ask. */
    private Bloom.Row deleteRow(final Shelf.Item it) {
        final boolean[] armed = {Change.systemAsks(this, it)};
        return new Bloom.Row(Glyph.CROSS, Words.s("delete"), null, true, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                if (!armed[0]) {
                    armed[0] = true;
                    b.say(r, Words.s("delete_sure"));
                    return false;
                }
                remove(it);
                return true;
            }
        });
    }

    /**
     * What can be done with a file: take it to a workshop, if the phone has
     * one for its kind; hand it on; rename it; and apart, delete it.
     */
    private ArrayList<ArrayList<Bloom.Row>> fileRows(final Shelf.Item it, Bloom.Row... first) {
        ArrayList<Bloom.Row> keep = new ArrayList<Bloom.Row>();
        if (first != null) {
            for (int i = 0; i < first.length; i++) {
                if (first[i] != null) {
                    keep.add(first[i]);
                }
            }
        }
        final String mime = mimeOf(it);
        final ArrayList<String> shops = workshops(it, mime);
        if (!shops.isEmpty()) {
            keep.add(row(Glyph.EDIT, Words.s("edit_in"), new Runnable() {
                public void run() {
                    editIn(it, mime, shops);
                }
            }));
        }
        keep.add(row(Glyph.SHARE, Words.s("share"), new Runnable() {
            public void run() {
                share(it);
            }
        }));
        keep.add(row(Glyph.CARET, Words.s("rename"), new Runnable() {
            public void run() {
                askName(it);
            }
        }));
        ArrayList<Bloom.Row> gone = new ArrayList<Bloom.Row>();
        gone.add(deleteRow(it));
        ArrayList<ArrayList<Bloom.Row>> groups = new ArrayList<ArrayList<Bloom.Row>>();
        groups.add(keep);
        groups.add(gone);
        return groups;
    }

    private static final int ASK_EDIT = 28;

    private String mimeOf(Shelf.Item it) {
        return it.mime != null && it.mime.length() > 0 ? it.mime : kindOf(it.name);
    }

    /** A file's kind as a workshop names it: the part of its type before the slash. */
    private static String kindOfWork(String mime) {
        int slash = mime == null ? -1 : mime.indexOf('/');
        return slash > 0 ? mime.substring(0, slash) : "";
    }

    /**
     * The workshops for this file: applications that say they edit its kind
     * by name — pictures, or recordings, or this very type. One that says it
     * edits anything at all is not a workshop for anything in particular and
     * is left out, and so is everything that only takes a file handed over:
     * that is sharing, which has its own row. Each as package and screen.
     */
    private ArrayList<String> workshops(Shelf.Item it, String mime) {
        ArrayList<String> out = new ArrayList<String>();
        String kind = kindOfWork(mime);
        if (kind.length() == 0) {
            return out;
        }
        java.util.HashSet<String> seen = new java.util.HashSet<String>();
        android.content.pm.PackageManager known = getPackageManager();
        Intent edit = new Intent(Intent.ACTION_EDIT).setDataAndType(it.uri, mime);
        java.util.List<android.content.pm.ResolveInfo> can = known.queryIntentActivities(edit,
            android.content.pm.PackageManager.GET_RESOLVED_FILTER);
        for (int i = 0; i < can.size(); i++) {
            android.content.pm.ResolveInfo r = can.get(i);
            android.content.pm.ActivityInfo a = r.activityInfo;
            if (getPackageName().equals(a.packageName) || !namesKind(r.filter, kind)
                || !seen.add(a.packageName)) {
                continue;
            }
            out.add(a.packageName + "/" + a.name);
        }
        return out;
    }

    /** Whether a filter names this kind of file, rather than taking every kind there is. */
    private static boolean namesKind(android.content.IntentFilter f, String kind) {
        if (f == null) {
            return false;
        }
        for (int i = 0; i < f.countDataTypes(); i++) {
            String t = f.getDataType(i);
            if (t != null && (t.equals(kind) || t.startsWith(kind + "/"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The door to the workshops. One workshop on the phone for this kind of
     * file: it opens with the file. Several: the system's own chooser, with
     * every application that would take any file at all left out of it. The
     * workshop gets the file to read and to write; what it makes is found
     * when the window comes back.
     */
    private void editIn(Shelf.Item it, String mime, ArrayList<String> shops) {
        Intent edit = new Intent(Intent.ACTION_EDIT).setDataAndType(it.uri, mime);
        edit.setClipData(android.content.ClipData.newRawUri(it.name, it.uri));
        edit.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        Intent go;
        if (shops.size() == 1) {
            String[] part = shops.get(0).split("/");
            go = edit.setClassName(part[0], part[1]);
        } else {
            ArrayList<android.content.ComponentName> apart = new ArrayList<android.content.ComponentName>();
            java.util.List<android.content.pm.ResolveInfo> all = getPackageManager()
                .queryIntentActivities(edit, 0);
            for (int i = 0; i < all.size(); i++) {
                android.content.pm.ActivityInfo a = all.get(i).activityInfo;
                if (!shops.contains(a.packageName + "/" + a.name)) {
                    apart.add(new android.content.ComponentName(a.packageName, a.name));
                }
            }
            go = Intent.createChooser(edit, Words.s("edit_in"));
            go.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,
                apart.toArray(new android.os.Parcelable[apart.size()]));
            go.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }
        try {
            startActivityForResult(go, ASK_EDIT);
            Trace.note("handed to a workshop, of " + shops.size());
        } catch (Exception refused) {
            Trace.note("workshop refused: " + refused.getClass().getSimpleName());
            flash(Glyph.EDIT, Words.s("not_done"));
        }
    }

    /** A picture turned a quarter, again at each touch; the menu stays for the next quarter. */
    private Bloom.Row turnRow(final Gallery.Photo p) {
        return new Bloom.Row(Glyph.TURN, Words.s("turn"), null, false, new Bloom.Act() {
            public boolean act(Bloom.Row r, Bloom b) {
                if (!Change.turn(Main.this, p.uri, p.place, p.name)) {
                    flash(Glyph.TURN, Words.s("not_done"));
                    return true;
                }
                Gallery.forget(p);
                photosRead = false;
                if (viewer != null && PHOTO.equals(front())) {
                    viewer.reread();
                }
                Trace.note("picture turned");
                return false;
            }
        });
    }

    /**
     * The menu grows from where the finger last came down. If the finger is
     * still down, whatever it was holding forgets the touch, and the finger
     * now belongs to the menu, to choose by sliding.
     */
    private void bloom(ArrayList<ArrayList<Bloom.Row>> groups) {
        bloom = null;
        /* One menu at a time: any card still on the stage — even one still
           folding away — goes before the new one grows, so no card is ever
           left lying under another. */
        for (int i = stage.getChildCount() - 1; i >= 0; i--) {
            if (stage.getChildAt(i) instanceof Bloom) {
                stage.removeViewAt(i);
            }
        }
        pills.setVisibility(View.GONE);
        pills.removeAllViews();
        int[] at = new int[2];
        stage.getLocationOnScreen(at);
        final Bloom made = new Bloom(this, downRawX - at[0], downRawY - at[1], groups, new Bloom.Gone() {
            public void gone() {
                if (bloom != null && bloom.leaving()) {
                    bloom = null;
                    bloomFollow = false;
                    spread = false;
                }
            }
        });
        stage.addView(made, cover());
        made.heldAt(downRawX, downRawY);
        made.open();
        bloom = made;
        spread = true;
        if (fingerDown) {
            bloomFollow = true;
            long now = android.os.SystemClock.uptimeMillis();
            android.view.MotionEvent cancel = android.view.MotionEvent.obtain(now, now,
                android.view.MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
        }
    }

    /** Every touch passes here first: where a finger came down, and a finger that belongs to the menu. */
    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent e) {
        int act = e.getActionMasked();
        if (act == android.view.MotionEvent.ACTION_DOWN) {
            downRawX = e.getRawX();
            downRawY = e.getRawY();
            fingerDown = true;
        }
        if (bloom != null && bloomFollow) {
            if (act == android.view.MotionEvent.ACTION_MOVE) {
                bloom.follow(e.getRawX(), e.getRawY());
            } else if (act == android.view.MotionEvent.ACTION_UP
                || act == android.view.MotionEvent.ACTION_CANCEL) {
                bloomFollow = false;
                fingerDown = false;
                if (act == android.view.MotionEvent.ACTION_UP) {
                    bloom.release(e.getRawX(), e.getRawY());
                }
            }
            return true;
        }
        if (act == android.view.MotionEvent.ACTION_UP || act == android.view.MotionEvent.ACTION_CANCEL) {
            fingerDown = false;
        }
        return super.dispatchTouchEvent(e);
    }

    // ------------------------------------------------------------------ the paper

    private FrameLayout paperLayer;

    /**
     * The paper of the book, on a sheet from below: the size of the letters
     * on a drum, day or night, and the three ways of reading — without
     * anything else on the screen, turning by itself, and with the sound of
     * turning pages. What was a column of commands is now a sheet of settings.
     */
    private void paperSheet() {
        closePaper();
        if (leaf == null) {
            return;
        }
        final FrameLayout layer = new FrameLayout(this);
        View scrim = new View(this);
        scrim.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.55f));
        scrim.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closePaper();
            }
        });
        layer.addView(scrim, cover());
        final LinearLayout sheet = new LinearLayout(this);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setPadding(Round.dp(24), Round.dp(10), Round.dp(24), Round.dp(24));
        sheet.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.XL));
        sheet.setElevation(Round.px(8f));
        View handle = new View(this);
        handle.setBackground(Round.box(Tone.of(Tone.ON_SURFACE_VARIANT, 0.4f), Round.FULL));
        LinearLayout.LayoutParams handleAt = new LinearLayout.LayoutParams(Round.dp(32), Round.dp(4));
        handleAt.gravity = Gravity.CENTER_HORIZONTAL;
        handleAt.bottomMargin = Round.dp(14);
        sheet.addView(handle, handleAt);
        sheet.addView(words(Letter.TITLE_L, Words.s("paper"), Tone.ON_SURFACE));

        TextView sizeWord = words(Letter.LABEL_L, Words.s("letter_size"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams sizeWordAt = wide();
        sizeWordAt.topMargin = Round.dp(18);
        sizeWordAt.bottomMargin = Round.dp(8);
        sheet.addView(sizeWord, sizeWordAt);
        final String[] sizes = new String[18];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = String.valueOf(11 + i);
        }
        int now = Math.round(Shelf.fontSize(this)) - 11;
        sheet.addView(new Depth(this, sizes, Math.max(0, Math.min(17, now)), new Depth.Turned() {
            public void turned(int step, boolean done) {
                if (done && leaf != null) {
                    Shelf.setFont(Main.this, 11 + step);
                    leaf.resize();
                }
            }
        }), wide());

        Pair light = new Pair(this, new String[] {Words.s("paper_day"), Words.s("paper_night")}, null,
            Reader.night(this) ? 1 : 0, new Pair.Picked() {
                public void picked(int which) {
                    Reader.setNight(Main.this, which == 1);
                    closePaper();
                    reopen();
                }
            });
        LinearLayout.LayoutParams lightAt = wide();
        lightAt.topMargin = Round.dp(16);
        sheet.addView(light, lightAt);

        LinearLayout.LayoutParams gapAt = wide();
        gapAt.topMargin = Round.dp(10);
        sheet.addView(sheetSwitch(Words.s("quiet"), quiet, new Flip() {
            public void flip(Toggle toggle) {
                closePaper();
                quietOn(true);
            }
        }), gapAt);
        sheet.addView(sheetSwitch(Words.s("turning"), turning, new Flip() {
            public void flip(Toggle toggle) {
                boolean on = !toggle.on();
                toggle.set(on, true);
                if (on) {
                    startTurning();
                } else {
                    stopTurning();
                }
            }
        }), wide());
        sheet.addView(sheetSwitch(Words.s("page_sound"), Snd.on(this), new Flip() {
            public void flip(Toggle toggle) {
                boolean on = !toggle.on();
                toggle.set(on, true);
                Snd.setOn(Main.this, on);
            }
        }), wide());

        FrameLayout.LayoutParams sheetAt = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        layer.addView(sheet, sheetAt);
        stage.addView(layer, cover());
        paperLayer = layer;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        sheet.setTranslationY(Round.px(420f));
        sheet.animate().translationY(0f).setDuration(Pace.GROW).setInterpolator(Pace.EMPHASIS).start();
    }

    /** A line of the sheet: its words, and a switch; the whole line is the switch's. */
    /** Whoever holds a switch decides what a touch on its line does. */
    interface Flip {
        void flip(Toggle toggle);
    }

    private LinearLayout sheetSwitch(String name, boolean on, final Flip flip) {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(Round.dp(56));
        line.addView(words(Letter.BODY_L, name, Tone.ON_SURFACE),
            new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final Toggle toggle = new Toggle(this, on);
        line.addView(toggle);
        line.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                flip.flip(toggle);
            }
        });
        return line;
    }

    private void closePaper() {
        final FrameLayout gone = paperLayer;
        paperLayer = null;
        if (gone == null) {
            return;
        }
        gone.animate().alpha(0f).setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
            .withEndAction(new Runnable() {
                public void run() {
                    stage.removeView(gone);
                }
            }).start();
    }

    /** The same book again at the same page: the paper changed under it. */
    private void reopen() {
        if (book == null) {
            return;
        }
        hush();
        stopTurning();
        Reader.theme(this);
        View made = pageFor(book);
        if (made == null) {
            return;
        }
        reading = made;
        forgetScene();
        swap(made);
    }

    // ------------------------------------------------------------ the voice

    /** How fast the voice may read, stepped through by a touch on its words. */

    /**
     * Reading aloud, from the words under the finger — or, if the voice was
     * put away on this page, from where it stopped, a few seconds earlier.
     * The book is handed to the sound service, which reads it whether or not
     * this window stays; the window only shows where the voice is: the
     * paragraph underlined, the page following the word, the strip in place
     * of the field.
     */
    private void aloud() {
        if (leaf == null || book == null) {
            return;
        }
        stopTurning();
        String whole = leaf.text().toString();
        int here = aloudFrom >= 0 ? aloudFrom : leaf.startChar(leaf.pageIndex());
        aloudFrom = -1;
        int kept = Shelf.voiceAt(this, book.key());
        if (kept >= 0 && kept < whole.length()
            && leaf.pageOfLine(leaf.lineOfChar(kept)) == leaf.pageIndex()) {
            int back = Math.round(Shelf.rollback(this) * 15f * Shelf.rate(this));
            here = Math.max(0, kept - back);
        }
        Sound.handOver(book.key(), Shelf.title(book.name), whole, here, leaf.pages());
        Intent go = new Intent(this, Sound.class);
        go.setAction(Sound.VOICE);
        startForegroundService(go);
        speaking = true;
        heardAt = -1;
        showVoice();
        Trace.note("aloud from character " + here + " of " + whole.length());
    }

    /** The service's voice, if it is reading the book in front. */
    private Sound reader() {
        Sound s = Sound.live();
        if (s == null || !s.voiceOn() || book == null || leaf == null
            || !book.key().equals(s.voiceKey())) {
            return null;
        }
        return s;
    }

    /**
     * Where the voice is, shown: its paragraph underlined, the page turned to
     * the word it is saying, the strip told the words and the place. When the
     * voice has been put away — by a key, a call, the end of the book — the
     * strip goes and the field comes back.
     */
    private void voiceShown() {
        Sound s = reader();
        if (s == null) {
            if (speaking) {
                speaking = false;
                heardAt = -1;
                closeDial(false);
                if (leaf != null) {
                    leaf.unmark();
                }
                hideVoice();
            }
            return;
        }
        if (!speaking) {
            speaking = true;
            showVoice();
        }
        ArrayList<Integer> starts = s.voiceStarts();
        int idx = Math.max(0, Math.min(s.voiceAt(), starts.size() - 1));
        if (starts.isEmpty()) {
            return;
        }
        int from = starts.get(idx).intValue();
        int to = idx + 1 < starts.size() ? starts.get(idx + 1).intValue() : Integer.MAX_VALUE / 2;
        leaf.mark(from, to);
        int ch = s.voiceChar();
        int p = leaf.pageOfLine(leaf.lineOfChar(Math.max(from, ch)));
        if (p != leaf.pageIndex()) {
            leaf.showPage(p);
            Shelf.setPage(this, book.key(), p);
        }
        if (strip != null) {
            String t = s.voicePieces().get(idx);
            String place = Words.s("page_short").replace("{n}", String.valueOf(p + 1))
                + "  \u00B7  " + String.format(java.util.Locale.ROOT, "%.1f\u00D7",
                    Shelf.rate(this));
            float along = s.voiceLength() == 0 ? 0f : ch / (float) s.voiceLength();
            strip.line(t.length() > 160 ? t.substring(0, 160) : t, place, along);
            strip.playing(!s.voiceHeld());
            if (idx != heardAt) {
                heardAt = idx;
                strip.pulse();
            }
        }
    }

    private void faster() {
        showPace(true);
    }

    /** The drum of pace, over the foot of the stage, while it stands. */
    private LinearLayout pacePanel;
    private final Runnable paceAway = new Runnable() {
        public void run() {
            closePace();
        }
    };

    /**
     * The pace of what sounds, on a drum under a needle, as a video's speed
     * is set: the words of the strip touched bring it, touched again take it
     * away, and it goes by itself a moment after the drum stops. For a book
     * read aloud it is the voice's pace; for a recording, its folder's. One
     * large thing to turn, rather than a small word to tap through the steps.
     */
    private void showPace(final boolean voice) {
        if (pacePanel != null) {
            closePace();
            return;
        }
        Sound s = voice ? reader() : Sound.live();
        final Shelf.Item it = voice || s == null ? null : s.current();
        if (!voice && it == null) {
            return;
        }
        float now = voice ? Shelf.rate(this) : Shelf.pace(this, it);
        int at = 0;
        for (int i = 0; i < Film.SPEEDS.length; i++) {
            if (Math.abs(Film.SPEEDS[i] - now) < Math.abs(Film.SPEEDS[at] - now)) {
                at = i;
            }
        }
        LinearLayout made = new LinearLayout(this);
        made.setOrientation(LinearLayout.VERTICAL);
        made.setPadding(Round.dp(16), Round.dp(12), Round.dp(16), Round.dp(16));
        made.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.XL));
        made.setElevation(Round.px(6f));
        TextView named = words(Letter.LABEL_L, Words.s("speed"), Tone.ON_SURFACE_VARIANT);
        named.setPadding(Round.dp(4), 0, 0, Round.dp(8));
        made.addView(named);
        made.addView(new Depth(this, Film.speedLabels(), at, new Depth.Turned() {
            public void turned(int step, boolean done) {
                float v = Film.SPEEDS[step];
                if (voice) {
                    Shelf.setRate(Main.this, v);
                    Sound r = reader();
                    if (r != null) {
                        r.voiceSpeed();
                    }
                    voiceShown();
                } else {
                    Shelf.setPaceOf(Main.this, it.home(), v);
                    Sound live = Sound.live();
                    if (live != null) {
                        live.speed();
                    }
                    tuneLine();
                }
                clock.removeCallbacks(paceAway);
                if (done) {
                    clock.postDelayed(paceAway, 1600L);
                }
            }
        }));
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        where.setMargins(Round.dp(12), 0, Round.dp(12), Round.dp(12));
        stage.addView(made, where);
        made.setAlpha(0f);
        made.setTranslationY(Round.px(24f));
        made.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        pacePanel = made;
        clock.removeCallbacks(paceAway);
        clock.postDelayed(paceAway, 4000L);
    }

    private void closePace() {
        clock.removeCallbacks(paceAway);
        final View gone = pacePanel;
        pacePanel = null;
        if (gone == null) {
            return;
        }
        gone.animate().alpha(0f).translationY(Round.px(24f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    stage.removeView(gone);
                }
            }).start();
    }

    /** The field steps aside and the voice's strip takes its place, at the same height. */
    private void showVoice() {
        showBar();
        if (strip == null) {
            strip = new Voice(this, new Voice.Hands() {
                public void back() {
                    Sound s = reader();
                    if (s != null) {
                        s.voiceJump(-15);
                    }
                }

                public void hold() {
                    Sound s = reader();
                    if (s != null) {
                        s.voiceHold();
                    }
                }

                public void on() {
                    Sound s = reader();
                    if (s != null) {
                        s.voiceJump(30);
                    }
                }

                public void faster() {
                    Main.this.faster();
                }

                public void dialOpen() {
                    /* The dial belongs to the page; over the settings the strip only keeps time. */
                    if (READING.equals(front())) {
                        openDial();
                    }
                }

                public void dialTurn(float dx) {
                    if (arc != null) {
                        ticked(arc.turn(dx));
                    }
                }

                public void dialEdge(float across) {
                    float was = edgeSpeed;
                    edgeSpeed = across < 0.1f ? -(0.1f - across) * 12f
                        : (across > 0.9f ? (across - 0.9f) * 12f : 0f);
                    if (was == 0f && edgeSpeed != 0f) {
                        clock.post(edgeRoll);
                    }
                }

                public void dialClose(boolean take) {
                    closeDial(take);
                }
            });
            bar.addView(strip, 0, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        strip.tint();
        /* A strip still leaving from a moment ago is stopped where it is. */
        strip.animate().cancel();
        field.animate().cancel();
        if (barFlow != null) {
            barFlow.cancel();
        }
        field.setVisibility(View.GONE);
        strip.setVisibility(View.VISIBLE);
        strip.setAlpha(0f);
        strip.setTranslationY(Round.px(10f));
        strip.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        strip.start();
        bar.setBackground(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL));
    }

    /**
     * The strip leaves as it came: it sinks a little and fades, its breath
     * settles, the bar's colour flows back from the voice's to its own, and
     * the field rises into the place the strip left.
     */
    private void hideVoice() {
        if (strip == null || strip.getVisibility() != View.VISIBLE) {
            return;
        }
        final Voice gone = strip;
        gone.playing(false);
        gone.animate().alpha(0f).translationY(Round.px(18f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
            .withEndAction(new Runnable() {
                public void run() {
                    gone.end();
                    gone.setVisibility(View.GONE);
                    gone.setTranslationY(0f);
                    field.setVisibility(View.VISIBLE);
                    field.setAlpha(0f);
                    field.setTranslationY(Round.px(12f));
                    field.animate().alpha(1f).translationY(0f).setStartDelay(0L)
                        .setDuration(Pace.ARRIVE).setInterpolator(Pace.STANDARD).start();
                }
            }).start();
        final android.graphics.drawable.GradientDrawable ground =
            Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL);
        bar.setBackground(ground);
        if (barFlow != null) {
            barFlow.cancel();
        }
        android.animation.ValueAnimator flow = android.animation.ValueAnimator.ofArgb(
            Tone.of(Tone.SECONDARY_CONTAINER), Tone.of(Tone.SURFACE_HIGH));
        flow.setDuration(Pace.ARRIVE);
        flow.setInterpolator(Pace.STANDARD);
        flow.addUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(android.animation.ValueAnimator a) {
                ground.setColor((Integer) a.getAnimatedValue());
            }
        });
        flow.start();
        barFlow = flow;
    }

    // ------------------------------------------------------------- the dial

    /** The dial rises over the page, above the strip, with the paragraph being said under the needle. */
    private void openDial() {
        final Sound s = reader();
        if (s == null || s.voicePieces().isEmpty()) {
            return;
        }
        closeDial(false);
        final ArrayList<String> pieces = s.voicePieces();
        final ArrayList<Integer> starts = s.voiceStarts();
        arc = new Arc(this, new Arc.Say() {
            public String page(int i) {
                if (leaf == null) {
                    return "";
                }
                int p = leaf.pageOfLine(leaf.lineOfChar(starts.get(i).intValue()));
                return Words.s("page_n").replace("{n}", String.valueOf(p + 1));
            }

            public String words(int i) {
                String t = pieces.get(i);
                return t.length() > 200 ? t.substring(0, 200) : t;
            }

            public String distance(int i) {
                int secs = Math.round((starts.get(i).intValue() - s.voiceChar())
                    / (15f * Shelf.rate(Main.this)));
                String sign = secs < 0 ? "\u2212" : (secs > 0 ? "+" : "");
                secs = Math.abs(secs);
                return sign + (secs / 60) + ":" + (secs % 60 < 10 ? "0" : "") + (secs % 60);
            }
        });
        arc.set(pieces.size(), s.voiceAt(), s.voiceAt());
        dialTick = s.voiceAt();
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Round.dp(152), Gravity.BOTTOM);
        where.setMargins(Round.dp(10), 0, Round.dp(10), Round.dp(10));
        stage.addView(arc, where);
        arc.setElevation(Round.px(6f));
        arc.setAlpha(0f);
        arc.setTranslationY(Round.px(40f));
        arc.animate().alpha(1f).translationY(0f).setStartDelay(0L).setDuration(Pace.GROW)
            .setInterpolator(Pace.EMPHASIS).start();
    }

    /** Lifting the finger chooses the paragraph under the needle; a taken gesture chooses nothing. */
    private void closeDial(boolean take) {
        edgeSpeed = 0f;
        clock.removeCallbacks(edgeRoll);
        if (arc == null) {
            return;
        }
        final Arc gone = arc;
        arc = null;
        int p = gone.at();
        if (filmDial) {
            filmDial = false;
            if (take && film != null) {
                film.seekTo(p * 5000L);
            }
        } else if (tuneDial) {
            tuneDial = false;
            Sound s = Sound.live();
            if (take && s != null) {
                s.seekTo(p * 5000L);
            }
        } else if (take) {
            Sound s = reader();
            if (s != null && p != s.voiceAt()) {
                s.voiceMove(s.voiceStarts().get(p).intValue());
            }
        }
        gone.animate().alpha(0f).translationY(Round.px(30f)).setStartDelay(0L)
            .setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                public void run() {
                    stage.removeView(gone);
                }
            }).start();
    }

    /** A tick under the finger for every paragraph that passes the needle, a firmer one every tenth. */
    private void ticked(int p) {
        if (arc == null || p == dialTick) {
            return;
        }
        dialTick = p;
        arc.performHapticFeedback(p % 10 == 0 ? android.view.HapticFeedbackConstants.VIRTUAL_KEY
            : android.view.HapticFeedbackConstants.CLOCK_TICK);
    }

    /** Held at an edge, the drum keeps turning, faster the nearer the finger is to the rim. */
    private final Runnable edgeRoll = new Runnable() {
        public void run() {
            if (arc == null || edgeSpeed == 0f) {
                return;
            }
            ticked(arc.roll(edgeSpeed));
            clock.postDelayed(this, 16L);
        }
    };

    /** The voice is put away; the service keeps its place in the book. */
    private void hush() {
        closeDial(false);
        Sound s = reader();
        if (s != null) {
            s.voiceStop();
        }
        speaking = false;
        heardAt = -1;
        if (leaf != null) {
            leaf.unmark();
        }
        hideVoice();
    }

    // --------------------------------------------------------- turning alone

    /**
     * The pages turn by themselves at the pace of an unhurried reader: each
     * page stays as long as its words take at two hundred a minute, never
     * less than eight seconds and never more than a minute and a half.
     */
    private void startTurning() {
        if (leaf == null) {
            return;
        }
        hush();
        turning = true;
        clock.removeCallbacks(tick);
        clock.postDelayed(tick, stay());
        flash(Glyph.DOWN, Words.s("turning"));
    }

    private void stopTurning() {
        turning = false;
        clock.removeCallbacks(tick);
    }

    private long stay() {
        if (leaf == null) {
            return 20000L;
        }
        int p = leaf.pageIndex();
        CharSequence t = leaf.text();
        int from = Math.max(0, Math.min(t.length(), leaf.startChar(p)));
        int to = Math.max(from, Math.min(t.length(), leaf.endChar(p)));
        String[] words = t.subSequence(from, to).toString().trim().split("\\s+");
        long ms = words.length * 60000L / 200L;
        return Math.max(8000L, Math.min(90000L, ms));
    }

    private final Runnable tick = new Runnable() {
        public void run() {
            if (!turning || leaf == null) {
                return;
            }
            if (!leaf.turn(1)) {
                stopTurning();
                return;
            }
            clock.postDelayed(this, stay());
        }
    };

    // ----------------------------------------------------------- picking words

    /**
     * The words of the page in front, laid out on paper of their own where
     * a finger can pick them and copy them. It lies over the page and goes
     * with a touch outside it, with the back gesture, or with the button.
     */
    private void pick() {
        if (leaf == null) {
            return;
        }
        int p = leaf.pageIndex();
        CharSequence t = leaf.text();
        int from = Math.max(0, Math.min(t.length(), leaf.startChar(p)));
        int to = Math.max(from, Math.min(t.length(), leaf.endChar(p)));

        FrameLayout over = new FrameLayout(this);
        over.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.66f));
        over.setClickable(true);
        over.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeSheet();
            }
        });
        ScrollView holder = new ScrollView(this);
        holder.setBackground(Round.box(Reader.PAPER, Round.XL));
        holder.setClipToOutline(true);
        holder.setElevation(Round.px(8f));
        final TextView words = new TextView(this);
        words.setText(t.subSequence(from, to));
        words.setTextColor(Reader.INK);
        words.setTypeface(android.graphics.Typeface.SERIF);
        words.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, Shelf.fontSize(this));
        words.setLineSpacing(0f, 1.25f);
        words.setTextIsSelectable(true);
        words.setPadding(Round.dp(24), Round.dp(24), Round.dp(24), Round.dp(24));
        inkPicking(words);
        holder.addView(words);
        /* The paper stops short of the edges, so there is ground left to
           touch for going away, and a key under it says so. */
        FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        where.setMargins(Round.dp(16), Round.dp(56), Round.dp(16), Round.dp(104));
        over.addView(holder, where);

        LinearLayout done = new LinearLayout(this);
        done.setOrientation(LinearLayout.HORIZONTAL);
        done.setGravity(Gravity.CENTER_VERTICAL);
        done.setPadding(Round.dp(18), 0, Round.dp(24), 0);
        done.setMinimumHeight(Round.dp(56));
        done.setBackground(Round.touch(Round.box(Tone.of(Tone.PRIMARY_CONTAINER), Round.FULL),
            Tone.of(Tone.ON_PRIMARY_CONTAINER), Round.FULL));
        done.setElevation(Round.px(6f));
        Glyph tick = new Glyph(this, Glyph.TICK, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_PRIMARY_CONTAINER));
        LinearLayout.LayoutParams tickParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tickParams.rightMargin = Round.dp(10);
        done.addView(tick, tickParams);
        TextView doneWord = Letter.set(new TextView(this), Letter.LABEL_L);
        doneWord.setText(Words.s("done"));
        doneWord.setTextColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
        done.addView(doneWord);
        done.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                closeSheet();
            }
        });
        FrameLayout.LayoutParams doneWhere = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        doneWhere.bottomMargin = Round.dp(28);
        over.addView(done, doneWhere);

        stage.addView(over, cover());
        over.setAlpha(0f);
        holder.setTranslationY(Round.px(40f));
        done.setTranslationY(Round.px(24f));
        over.animate().alpha(1f).setDuration(Pace.SHEET).start();
        holder.animate().translationY(0f).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        done.animate().translationY(0f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        sheet = over;
        picked = words;
        pickedFrom = from;
    }

    /**
     * The handles and the wash behind picked words in the seed's accent, as
     * the paper takes it. The platform lets the handles be replaced only
     * from Android 10; below that the theme's own brass ones stand.
     */
    private void inkPicking(TextView box) {
        int accent = Shelf.accent(this);
        box.setHighlightColor((0x55 << 24) | (accent & 0x00FFFFFF));
        if (Build.VERSION.SDK_INT < 29) {
            return;
        }
        int[] ids = {R.drawable.caret_mid, R.drawable.caret_left, R.drawable.caret_right};
        android.graphics.drawable.Drawable[] made = new android.graphics.drawable.Drawable[3];
        for (int i = 0; i < ids.length; i++) {
            made[i] = getDrawable(ids[i]).mutate();
            made[i].setTint(accent);
        }
        box.setTextSelectHandle(made[0]);
        box.setTextSelectHandleLeft(made[1]);
        box.setTextSelectHandleRight(made[2]);
    }

    // ------------------------------------------------------------ the picking bar

    /**
     * Words picked on the sheet get our own bar instead of the system's: the
     * text keeps its handles and its knowledge of the selection, and asks the
     * window for a floating bar; the window answers with ours. If ours fails
     * once, the system's answers from then on.
     */
    @Override
    public android.view.ActionMode onWindowStartingActionMode(
            android.view.ActionMode.Callback callback, int type) {
        if (type != android.view.ActionMode.TYPE_FLOATING || picked == null || Grip.failed()
            || !(callback instanceof android.view.ActionMode.Callback2)) {
            return super.onWindowStartingActionMode(callback, type);
        }
        try {
            Grip grip = new Grip(this, picked, (android.view.ActionMode.Callback2) callback, stage);
            if (grip.begin()) {
                return grip;
            }
        } catch (RuntimeException broken) {
            Grip.fail();
            Trace.note("own picking bar failed: " + broken.getClass().getSimpleName());
        }
        return super.onWindowStartingActionMode(callback, type);
    }

    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {
        picking = mode;
        if (mode instanceof Grip) {
            ArrayList<String> said = new ArrayList<String>();
            ArrayList<Runnable> acts = new ArrayList<Runnable>();
            int ours = picks(mode, said, acts);
            try {
                ((Grip) mode).show(said, acts, ours);
            } catch (RuntimeException broken) {
                Grip.fail();
                Trace.note("own picking bar would not show: " + broken.getClass().getSimpleName());
                mode.finish();
            }
        }
        super.onActionModeStarted(mode);
    }

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {
        super.onActionModeFinished(mode);
        picking = null;
    }

    /** The picked words as they stand, or nothing. */
    private CharSequence chosen() {
        if (picked == null) {
            return "";
        }
        int a = Math.max(0, Math.min(picked.getSelectionStart(), picked.getSelectionEnd()));
        int b = Math.max(picked.getSelectionStart(), picked.getSelectionEnd());
        CharSequence all = picked.getText();
        return b > a && b <= all.length() ? all.subSequence(a, b) : "";
    }

    /**
     * What the bar offers, in the order it stands: copy, the whole page,
     * read aloud from here, share; then, under a hairline, the applications
     * that asked the system to be offered picked words.
     */
    private int picks(final android.view.ActionMode mode, ArrayList<String> said,
                      ArrayList<Runnable> acts) {
        said.add(Words.s("copy"));
        acts.add(new Runnable() {
            public void run() {
                android.content.ClipboardManager board = (android.content.ClipboardManager)
                    getSystemService(CLIPBOARD_SERVICE);
                if (board != null) {
                    board.setPrimaryClip(android.content.ClipData.newPlainText(
                        Shelf.title(book == null ? "" : book.name), chosen()));
                }
                mode.finish();
                /* From Android 13 the system says it was copied, and saying it twice is noise. */
                if (Build.VERSION.SDK_INT < 33) {
                    flash(Glyph.TICK, Words.s("copied"));
                }
            }
        });
        said.add(Words.s("to_excerpts"));
        acts.add(new Runnable() {
            public void run() {
                final String words = chosen().toString().trim();
                mode.finish();
                if (words.length() == 0 || book == null) {
                    return;
                }
                java.util.Calendar now = java.util.Calendar.getInstance();
                String when = String.format(java.util.Locale.ROOT, "%02d.%02d.%d",
                    now.get(java.util.Calendar.DAY_OF_MONTH), now.get(java.util.Calendar.MONTH) + 1,
                    now.get(java.util.Calendar.YEAR));
                int page = leaf == null ? 0 : leaf.pageIndex();
                final String entry = "\u00AB" + words + "\u00BB\n\u2014 "
                    + Words.s("page_short").replace("{n}", String.valueOf(page + 1))
                    + ", " + when + "\n\n";
                final String folder = Keep.excerpts(Main.this, Words.s("excerpts"));
                final String title = Shelf.title(book.name);
                final android.content.Context app = getApplicationContext();
                new Thread(new Runnable() {
                    public void run() {
                        final boolean kept = Change.excerpt(app, folder, title, entry);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                flash(Glyph.BOOK, Words.s(kept ? "excerpt_kept" : "not_done"));
                            }
                        });
                    }
                }, "excerpt").start();
                Trace.note("excerpt");
            }
        });
        said.add(Words.s("all_text"));
        acts.add(new Runnable() {
            public void run() {
                if (picked != null && picked.getText() instanceof android.text.Spannable) {
                    android.text.Selection.selectAll((android.text.Spannable) picked.getText());
                    mode.invalidate();
                    mode.invalidateContentRect();
                }
            }
        });
        said.add(Words.s("aloud_here"));
        acts.add(new Runnable() {
            public void run() {
                int a = picked == null ? 0
                    : Math.max(0, Math.min(picked.getSelectionStart(), picked.getSelectionEnd()));
                mode.finish();
                closeSheet();
                aloudFrom = pickedFrom + a;
                Sound s = reader();
                if (s != null) {
                    s.voiceMove(aloudFrom);
                    aloudFrom = -1;
                } else {
                    aloud();
                }
            }
        });
        said.add(Words.s("share"));
        acts.add(new Runnable() {
            public void run() {
                CharSequence words = chosen();
                mode.finish();
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, words.toString());
                try {
                    startActivity(Intent.createChooser(send, null));
                } catch (Exception none) {
                    Trace.note("nothing to share with: " + none);
                }
            }
        });
        int ours = said.size();
        try {
            android.content.pm.PackageManager known = getPackageManager();
            Intent probe = new Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
            java.util.List<android.content.pm.ResolveInfo> apps =
                known.queryIntentActivities(probe, 0);
            for (int i = 0; i < apps.size(); i++) {
                final android.content.pm.ResolveInfo one = apps.get(i);
                if (getPackageName().equals(one.activityInfo.packageName)) {
                    continue;
                }
                said.add(String.valueOf(one.loadLabel(known)));
                acts.add(new Runnable() {
                    public void run() {
                        CharSequence words = chosen();
                        mode.finish();
                        try {
                            Intent out = new Intent(Intent.ACTION_PROCESS_TEXT);
                            out.setType("text/plain");
                            out.setClassName(one.activityInfo.packageName, one.activityInfo.name);
                            out.putExtra(Intent.EXTRA_PROCESS_TEXT, words);
                            out.putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true);
                            startActivity(out);
                        } catch (Exception broken) {
                            Trace.note("hand over failed: " + broken);
                        }
                    }
                });
            }
        } catch (Exception broken) {
            Trace.note("no helpers for picked words: " + broken);
        }
        return ours;
    }

    private void closeSheet() {
        if (sheet == null) {
            return;
        }
        if (picking != null) {
            picking.finish();
            picking = null;
        }
        picked = null;
        final View gone = sheet;
        sheet = null;
        gone.animate().alpha(0f).setDuration(Pace.LEAVE).setInterpolator(Pace.AWAY)
            .withEndAction(new Runnable() {
                public void run() {
                    stage.removeView(gone);
                }
            }).start();
    }

    // ----------------------------------------------------------------- scenes

    LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Round.dp(24), Round.dp(28), Round.dp(24), Round.dp(40));
        return column;
    }

    static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    ScrollView scroll(LinearLayout column) {
        ScrollView made = new ScrollView(this);
        made.setFillViewport(true);
        made.setVerticalScrollBarEnabled(false);
        made.addView(column, new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroller = made;
        arrive(column);
        /* Previews are fetched for the rows the eye can see, and again as they move. */
        made.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                Thumb.sweep(Main.this);
            }
        });
        made.postDelayed(new Runnable() {
            public void run() {
                Thumb.sweep(Main.this);
            }
        }, Pace.ARRIVE);
        return made;
    }

    /** Lists shorter than this are passed by the thumb alone and need no ruler. */
    private static final int RULED = 30;

    /**
     * A long list with the ruler along its right edge: the list moves as it
     * did, and the ruler shows while it moves and rises when it is held.
     */
    private View ruled(ScrollView list, ArrayList<Ruler.Entry> marks) {
        if (marks.size() < RULED) {
            return list;
        }
        FrameLayout whole = new FrameLayout(this);
        whole.addView(list, cover());
        final Ruler ruler = new Ruler(this, list, marks);
        whole.addView(ruler, cover());
        list.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            public void onScrollChange(View v, int x, int y, int oldX, int oldY) {
                Thumb.sweep(Main.this);
                ruler.moved();
            }
        });
        return whole;
    }

    /** The section a title stands in on a shelf in the order of the alphabet: its first letter, or a sign for a number. */
    private static String initial(String title) {
        String t = title == null ? "" : title.trim();
        if (t.length() == 0) {
            return "\u00B7";
        }
        char first = t.charAt(0);
        if (Character.isDigit(first)) {
            return "#";
        }
        return Character.isLetter(first) ? String.valueOf(Character.toUpperCase(first)) : "\u00B7";
    }

    /**
     * The chapter of a numbered title: everything before its last number,
     * so that in 03 04 05 the chapter is 03 04. A title with one number, or
     * none, has no chapter.
     */
    private static String chapter(String own) {
        int end = own.length();
        while (end > 0 && !Character.isDigit(own.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && Character.isDigit(own.charAt(start - 1))) {
            start--;
        }
        String before = own.substring(0, start).trim();
        while (before.length() > 0 && "-.,_(\u2014\u2013".indexOf(before.charAt(before.length() - 1)) >= 0) {
            before = before.substring(0, before.length() - 1).trim();
        }
        return before;
    }

    /** Words on a rung, in the ink of a role, remembered so a new seed reaches them. */
    TextView words(int rung, String text, int role) {
        TextView view = Letter.set(new TextView(this), rung);
        view.setText(text);
        view.setTextColor(Tone.of(role));
        inked.add(view);
        inkRoles.add(role);
        return view;
    }

    /**
     * One scene gives way to the next. The old one fades out quickly on
     * top; the new one grows in beneath it from a little smaller, and its
     * blocks come one after another.
     */
    void swap(View made) {
        final View old = scene;
        /* A scene whose leaving was interrupted — its animation cancelled,
           its end never reached — would stay under the next one for good,
           and show through it. Every scene is marked, and every marked
           view but the one leaving now is taken away before a new one comes. */
        for (int i = stage.getChildCount() - 1; i >= 0; i--) {
            View one = stage.getChildAt(i);
            if (one != old && one != made && Boolean.TRUE.equals(one.getTag(R.id.scene))) {
                one.animate().cancel();
                stage.removeView(one);
            }
        }
        scene = made;
        made.setTag(R.id.scene, Boolean.TRUE);
        if (made.getParent() instanceof ViewGroup) {
            ((ViewGroup) made.getParent()).removeView(made);
        }
        stage.addView(made, 0, cover());
        made.setAlpha(0f);
        made.setScaleX(0.96f);
        made.setScaleY(0.96f);
        made.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(70L).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        if (old != null && old != made) {
            old.animate().alpha(0f).scaleX(1.02f).scaleY(1.02f)
                .setStartDelay(0L).setDuration(Pace.PRESS)
                .setInterpolator(Pace.AWAY)
                .withEndAction(new Runnable() {
                    public void run() {
                        stage.removeView(old);
                    }
                }).start();
            /* Should the animation be cut short, the scene goes all the same. */
            stage.postDelayed(new Runnable() {
                public void run() {
                    if (old != scene && old.getParent() == stage) {
                        stage.removeView(old);
                    }
                }
            }, Pace.PRESS + 120L);
        }
    }

    /** A scene put in place at once, without a change: for a screen drawn again under the finger. */
    private void replace(View made) {
        View old = scene;
        scene = made;
        made.setTag(R.id.scene, Boolean.TRUE);
        int at = old == null ? 0 : Math.max(0, stage.indexOfChild(old));
        if (old != null) {
            old.animate().cancel();
            stage.removeView(old);
        }
        stage.addView(made, at, cover());
    }

    /**
     * The blocks of a scene arrive one after another, each a beat behind.
     * Only the head of a long scene is led in: past the first dozen the eye
     * is already reading, and a shelf of four hundred books must not take
     * half a minute to finish arriving.
     */
    void arrive(ViewGroup column) {
        for (int i = 0; i < column.getChildCount(); i++) {
            View one = column.getChildAt(i);
            if (i > 12) {
                break;
            }
            one.setAlpha(0f);
            one.setTranslationY(Round.px(24f));
            one.animate().alpha(1f).translationY(0f)
                .setStartDelay(70L + i * Pace.STAGGER).setDuration(Pace.ARRIVE)
                .setInterpolator(Pace.STANDARD).start();
        }
    }

    /** Clears what the scene in front remembered before the next one is made. */
    void forgetScene() {
        Thumb.reset();
        inked.clear();
        inkRoles.clear();
        settings.forget();
    }

    // ------------------------------------------------------------------ doors

    /** A screen of ours opens over the room, or over the screen before it. */
    void door(String which) {
        calm();
        closePace();
        if (asking == ASK_FIND) {
            keyboard(false);
        }
        if (quiet && setting(which)) {
            quietOff();
        }
        if (PHOTO.equals(which)) {
            photoCaption = false;
        }
        boolean first = trail.isEmpty();
        trail.add(which);
        if (first) {
            blob.leaving(true);
        }
        forgetScene();
        swap(viewOf(which));
        if (PHOTO.equals(which) || FILM.equals(which)) {
            quietOn(false);
            Trace.note((FILM.equals(which) ? "film " : "picture ") + (photoAt + 1) + " of " + album.size());
        }
        placeCrest(true);
        plate(true);
        syncTune();
        Trace.note("opened " + which);
    }

    /** One step back: to the screen before, or out to the room. */
    void back() {
        if (quiet && (PHOTO.equals(front()) || FILM.equals(front()))) {
            quietOff();
        }
        if (trail.isEmpty()) {
            return;
        }
        if (READING.equals(front())) {
            closeBook();
        }
        blob.depart();
        trail.remove(trail.size() - 1);
        forgetScene();
        if (trail.isEmpty()) {
            clock.postDelayed(new Runnable() {
                public void run() {
                    syncTune();
                }
            }, Pace.ARRIVE);
            blob.leaving(asking != ASK_NONE);
            swap(roomView(room));
        } else {
            swap(viewOf(front()));
            syncTune();
        }
        if (PHOTO.equals(front()) || FILM.equals(front())) {
            quietOn(false);
        }
        placeCrest(true);
        plate(true);
    }

    private View viewOf(String which) {
        if (READING.equals(which)) {
            return reading;
        }
        if (LISTENING.equals(which)) {
            return listenView(listening);
        }
        if (FOLDER.equals(which)) {
            return folderView();
        }
        if (ALBUM.equals(which)) {
            return albumView();
        }
        if (PHOTO.equals(which)) {
            return photoView();
        }
        if (FILM.equals(which)) {
            return filmView();
        }
        if (WIDGET.equals(which)) {
            return settings.widgetView();
        }
        if (LOG.equals(which)) {
            return settings.logView();
        }
        if (FOLDERS.equals(which)) {
            return settings.foldersView();
        }
        if (LOOK.equals(which)) {
            return settings.lookView();
        }
        if (SEEING.equals(which)) {
            return settings.seeingView();
        }
        if (SOUNDING.equals(which)) {
            return settings.soundingView();
        }
        if (LANGUAGE.equals(which)) {
            return settings.languageView();
        }
        return settings.settingsView();
    }

    @Override
    public void onBackPressed() {
        if (paperLayer != null) {
            closePaper();
        } else if (sheet != null) {
            closeSheet();
        } else if (spread) {
            calm();
        } else if (asking != ASK_NONE) {
            endAsk();
        } else if (recut != null) {
            recut.away();
        } else if (still != null && FILM.equals(front())) {
            still.leave();
        } else if (mend != null && PHOTO.equals(front())) {
            /* Back from a touch-up is no — asked once more if anything was changed. */
            mend.away();
        } else if (PHOTO.equals(front()) && viewer != null) {
            /* Back closes a picture the way a touch does. */
            viewer.close();
        } else if (FILM.equals(front()) && film != null) {
            film.close();
        } else if (quiet) {
            quietOff();
        } else if (open()) {
            back();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------------ pills

    /** Behind the round button: the other three rooms, and nothing else. */
    private void menu() {
        ArrayList<View> made = new ArrayList<View>();
        for (int i = 0; i < ROOM_WORDS.length; i++) {
            if (i == room) {
                continue;
            }
            final int which = i;
            made.add(capsule(ROOM_MARKS[i], ROOM_NUMBERS[i] + "  " + Words.s(ROOM_WORDS[i]),
                new View.OnClickListener() {
                public void onClick(View v) {
                    calm();
                    toRoom(which);
                }
            }));
        }
        /* In the rooms of pictures and videos the camera comes last, nearest the finger,
           apart from the rooms and in the accent: it is not a way somewhere but a thing to do. */
        boolean camera = (room == PICTURES || room == FILMS) && Build.VERSION.SDK_INT >= 29;
        if (camera) {
            final boolean video = room == FILMS;
            LinearLayout shot = capsule(Glyph.CAMERA, Words.s("shoot"), new View.OnClickListener() {
                public void onClick(View v) {
                    calm();
                    shoot(video);
                }
            });
            shot.setBackground(Round.touch(Round.box(Tone.of(Tone.PRIMARY_CONTAINER), Round.FULL),
                Tone.of(Tone.ON_PRIMARY_CONTAINER), Round.FULL));
            ((Glyph) shot.getChildAt(0)).tint(0x00000000, 0x00000000, Tone.of(Tone.ON_PRIMARY_CONTAINER));
            ((TextView) shot.getChildAt(1)).setTextColor(Tone.of(Tone.ON_PRIMARY_CONTAINER));
            made.add(shot);
        }
        spread(made);
        if (camera && pills.getChildCount() > 0) {
            View last = pills.getChildAt(pills.getChildCount() - 1);
            ((LinearLayout.LayoutParams) last.getLayoutParams()).topMargin = Round.dp(16);
            last.requestLayout();
        }
    }

    // ------------------------------------------------------------------ the camera's door

    private static final int ASK_SHOOT = 31;
    /** The camera is in front, asked by this window: its answer will come by the usual way. */
    private boolean cameraOut;

    /**
     * The phone's own camera, opened for the collection: a place for the picture
     * or the video is made first in the application's own folder, and the camera
     * writes straight into it. Nothing of a camera is built here; this is a door.
     */
    private void shoot(boolean video) {
        if (Build.VERSION.SDK_INT < 29) {
            flash(Glyph.CROSS, Words.s("not_done"));
            return;
        }
        String stamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH.mm.ss", java.util.Locale.ROOT)
            .format(new java.util.Date());
        android.content.ContentValues v = new android.content.ContentValues();
        Uri table;
        if (video) {
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, stamp + ".mp4");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/Mirabilia/");
            table = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        } else {
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, stamp + ".jpg");
            v.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Mirabilia/");
            table = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
        }
        v.put(MediaStore.MediaColumns.DATE_TAKEN, System.currentTimeMillis());
        Uri made;
        try {
            made = getContentResolver().insert(table, v);
        } catch (Exception refused) {
            Trace.note("camera: no place made: " + refused.getClass().getSimpleName());
            made = null;
        }
        if (made == null) {
            flash(Glyph.CROSS, Words.s("not_done"));
            return;
        }
        /* Written down, not held: if the phone lets the window go while the camera
           is open, the answer still finds its place. */
        Shelf.prefs(this).edit().putString("shot", made.toString()).putBoolean("shotVideo", video)
            .putLong("shotAt", System.currentTimeMillis()).commit();
        Intent go = new Intent(video ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
        go.putExtra(MediaStore.EXTRA_OUTPUT, made);
        go.setClipData(android.content.ClipData.newRawUri("", made));
        go.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            cameraOut = true;
            startActivityForResult(go, ASK_SHOOT);
            Trace.note("camera asked for " + (video ? "a video" : "a picture"));
        } catch (Exception none) {
            cameraOut = false;
            Trace.note("camera: none answers: " + none.getClass().getSimpleName());
            dropShot();
            flash(Glyph.CROSS, Words.s("no_camera"));
        }
    }

    /**
     * How much the camera wrote into a place made for it, as the file itself
     * says. The index's own count of the size is not asked first: it is
     * brought up to date after the camera has gone, and until then it says
     * nothing was written — which once cost a picture that had been.
     */
    private long written(Uri made) {
        android.os.ParcelFileDescriptor f = null;
        try {
            f = getContentResolver().openFileDescriptor(made, "r");
            if (f != null && f.getStatSize() >= 0L) {
                return f.getStatSize();
            }
        } catch (Exception unread) {
            Trace.note("camera: file unread: " + unread.getClass().getSimpleName());
        } finally {
            if (f != null) {
                try {
                    f.close();
                } catch (Exception ignored) {
                    // read only
                }
            }
        }
        android.database.Cursor q = null;
        try {
            q = getContentResolver().query(made, new String[] {android.provider.OpenableColumns.SIZE},
                null, null, null);
            if (q != null && q.moveToFirst() && !q.isNull(0)) {
                return q.getLong(0);
            }
        } catch (Exception unread) {
            Trace.note("camera: size unread: " + unread.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        return 0L;
    }

    /** A place made for the camera and left empty goes, so no empty picture stands in the collection. */
    private void dropShot() {
        String kept = Shelf.prefs(this).getString("shot", null);
        Shelf.prefs(this).edit().remove("shot").remove("shotVideo").remove("shotAt").apply();
        if (kept == null) {
            return;
        }
        Uri made = Uri.parse(kept);
        if (written(made) > 0L) {
            return;
        }
        try {
            getContentResolver().delete(made, null, null);
            Trace.note("camera: empty place taken away");
        } catch (Exception gone) {
            Trace.note("camera: empty place not taken away: " + gone.getClass().getSimpleName());
        }
    }

    /**
     * The camera answered. A picture taken is found in the collection, in the
     * application's album, and opens there — straight to be touched up, the
     * automatic light already on its first stop, for photographs; one touch of
     * the round button and it is kept. A video opens to be watched.
     */
    private void shot(boolean answered, boolean ok, Intent data) {
        String kept = Shelf.prefs(this).getString("shot", null);
        final boolean video = Shelf.prefs(this).getBoolean("shotVideo", false);
        long when = Shelf.prefs(this).getLong("shotAt", 0L);
        if (kept == null) {
            return;
        }
        Trace.note("camera: " + (answered ? "answered " + (ok ? "done" : "no") : "no answer came; looking"));
        final Uri made = Uri.parse(kept);
        long size = written(made);
        if (answered && !ok && size <= 0L && System.currentTimeMillis() - when < 2000L) {
            /* A "no" at once, while the camera is still coming up: some cameras run in
               a task of their own and answer before they have been used at all. The
               place is kept, and looked at again when this window comes back. */
            Trace.note("camera: answered no at once; the place waits");
            return;
        }
        if (size <= 0L && data != null && data.getData() != null && !made.equals(data.getData())) {
            /* A camera that kept the picture in a place of its own says where: its
               bytes are brought into ours, so the picture is in the collection's folder. */
            size = copyInto(data.getData(), made);
            Trace.note("camera: kept its own; brought over: " + size);
        }
        Shelf.prefs(this).edit().remove("shot").remove("shotVideo").remove("shotAt").apply();
        if (size <= 0L) {
            try {
                getContentResolver().delete(made, null, null);
            } catch (Exception gone) {
                Trace.note("camera: empty place not taken away: " + gone.getClass().getSimpleName());
            }
            Trace.note("camera: nothing taken" + (ok ? ", though it said it had" : ""));
            if (ok) {
                flash(Glyph.CROSS, Words.s("camera_empty"));
            }
            return;
        }
        Trace.note("camera: " + (video ? "a video" : "a picture") + " taken, " + size + " bytes");
        if (!answered && System.currentTimeMillis() - when > 10L * 60L * 1000L) {
            /* Taken long ago and only found now: it is kept, and not opened unasked. */
            if (video) {
                filmsRead = false;
            } else {
                photosRead = false;
            }
            Trace.note("camera: kept from before, not opened");
            return;
        }
        /* The index is told of the file before it is looked for, so its size, its
           shape and its turn are known when the collection is read again. */
        final boolean[] once = {false};
        final Runnable found = new Runnable() {
            public void run() {
                if (once[0]) {
                    return;
                }
                once[0] = true;
                openShot(made, video);
            }
        };
        String path = pathOf(made);
        if (path != null) {
            android.media.MediaScannerConnection.scanFile(getApplicationContext(), new String[] {path}, null,
                new android.media.MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String p, Uri u) {
                        runOnUiThread(found);
                    }
                });
        }
        clock.postDelayed(found, path != null ? 1500L : 0L);
    }

    /** Where a place in the index lies on the phone, if the index says. */
    private String pathOf(Uri made) {
        android.database.Cursor q = null;
        try {
            q = getContentResolver().query(made, new String[] {MediaStore.MediaColumns.DATA}, null, null, null);
            if (q != null && q.moveToFirst()) {
                return q.getString(0);
            }
        } catch (Exception unread) {
            Trace.note("camera: path unread: " + unread.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        return null;
    }

    /** The bytes of one place poured into another; answers how many. */
    private long copyInto(Uri from, Uri to) {
        java.io.InputStream in = null;
        java.io.OutputStream out = null;
        long n = 0L;
        try {
            in = getContentResolver().openInputStream(from);
            out = getContentResolver().openOutputStream(to, "w");
            if (in == null || out == null) {
                return 0L;
            }
            byte[] buf = new byte[65536];
            int got;
            while ((got = in.read(buf)) > 0) {
                out.write(buf, 0, got);
                n += got;
            }
        } catch (Exception broken) {
            Trace.note("camera: not brought over: " + broken.getClass().getSimpleName());
            return 0L;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (out != null) {
                    out.close();
                }
            } catch (Exception ignored) {
                // what was written is written
            }
        }
        return n;
    }

    /** The picture or video taken, found in the collection read again, and opened. */
    private void openShot(final Uri made, final boolean video) {
        long id = -1L;
        try {
            id = android.content.ContentUris.parseId(made);
        } catch (Exception odd) {
            Trace.note("camera: the place has no number");
        }
        final long taken = id;
        final int which = video ? FILMS : PICTURES;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final ArrayList<Gallery.Photo> all = video ? Gallery.films(app) : Gallery.load(app);
                final ArrayList<Gallery.Album> by = Gallery.albums(all);
                runOnUiThread(new Runnable() {
                    public void run() {
                        itemsOf(which).clear();
                        itemsOf(which).addAll(all);
                        albumsOf(which).clear();
                        albumsOf(which).addAll(by);
                        if (video) {
                            filmsRead = true;
                        } else {
                            photosRead = true;
                        }
                        for (int a = 0; a < by.size(); a++) {
                            ArrayList<Gallery.Photo> list = by.get(a).photos;
                            for (int i = 0; i < list.size(); i++) {
                                if (list.get(i).id != taken) {
                                    continue;
                                }
                                enter(which);
                                album = list;
                                albumName = by.get(a).name;
                                albumRoom = which;
                                photoAt = i;
                                door(ALBUM);
                                door(video ? FILM : PHOTO);
                                if (!video) {
                                    openMend(list.get(i), 1);
                                }
                                return;
                            }
                        }
                        flash(Glyph.TICK, Words.s("shot_kept"));
                    }
                });
            }
        }, "camera").start();
    }

    private LinearLayout capsule(int kind, String text, View.OnClickListener click) {
        LinearLayout made = new LinearLayout(this);
        made.setOrientation(LinearLayout.HORIZONTAL);
        made.setGravity(Gravity.CENTER_VERTICAL);
        made.setPadding(Round.dp(18), Round.dp(14), Round.dp(24), Round.dp(14));
        made.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        made.setOnClickListener(click);

        Glyph mark = new Glyph(this, kind, Round.px(24f), 0.92f,
            0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.rightMargin = Round.dp(12);
        made.addView(mark, markParams);

        TextView word = Letter.set(new TextView(this), Letter.LABEL_L);
        word.setText(text);
        word.setTextColor(Tone.of(Tone.ON_SURFACE));
        made.addView(word);
        return made;
    }

    /** Capsules arrive one after another, each from under the one before it. */
    private void spread(ArrayList<View> made) {
        pills.removeAllViews();
        shade.setVisibility(View.VISIBLE);
        shade.setAlpha(0f);
        shade.animate().alpha(1f).setDuration(Pace.SHEET).start();
        pills.setVisibility(View.VISIBLE);
        spread = true;
        blob.open(1f);
        for (int i = 0; i < made.size(); i++) {
            View one = made.get(i);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = Round.dp(8);
            pills.addView(one, params);
            one.setAlpha(0f);
            one.setTranslationY(Round.px(26f));
            one.setScaleX(0.9f);
            one.animate().alpha(1f).translationY(0f).scaleX(1f)
                .setStartDelay((made.size() - 1 - i) * 26L)
                .setDuration(190L)
                .setInterpolator(new DecelerateInterpolator(1.8f)).start();
        }
    }

    private void calm() {
        if (bloom != null) {
            bloom.dismiss();
        }
        bloomFollow = false;
        spread = false;
        blob.open(0f);
        pills.setVisibility(View.GONE);
        pills.removeAllViews();
        shade.setVisibility(View.GONE);
    }

    static LinearLayout.LayoutParams buttonPlace(int above) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(40));
        params.topMargin = Round.dp(above);
        return params;
    }

    /** A button is a capsule; its kind decides only how loudly it is coloured. */
    TextView button(String text, int kind, View.OnClickListener click) {
        TextView made = Letter.set(new TextView(this), Letter.LABEL_L);
        made.setText(text);
        made.setGravity(Gravity.CENTER);
        made.setPadding(Round.dp(24), 0, Round.dp(24), 0);
        made.setSingleLine(true);
        made.setOnClickListener(click);
        switch (kind) {
            case TONAL:
                made.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER),
                    Round.FULL), Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.FULL));
                made.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
                break;
            case OUTLINED:
                made.setBackground(Round.touch(Round.ring(0x00000000, Round.FULL,
                    Tone.of(Tone.OUTLINE_VARIANT)), Tone.of(Tone.PRIMARY), Round.FULL));
                made.setTextColor(Tone.of(Tone.PRIMARY));
                break;
            default:
                made.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.FULL));
                made.setTextColor(Tone.of(Tone.PRIMARY));
                break;
        }
        return made;
    }

    /** The language changed: every word already standing is said again. */
    void relabel() {
        plate(false);
        Doors.publish(this);
        Desk.push(this);
        if (LANGUAGE.equals(front())) {
            forgetScene();
            swap(settings.languageView());
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (SHOOT_DOOR.equals(intent.getAction())) {
            enter(PICTURES);
            shoot(false);
            return;
        }
        Integer asked = asked(intent);
        if (asked != null) {
            filmAsked = intent.getStringExtra("film");
            bookAsked = intent.getStringExtra("book");
            /* The book asked for is the one open already: whatever stands over
               it is put away, and it is left as it is, reading on. */
            if (bookAsked != null && book != null && bookAsked.equals(book.key())
                && trail.contains(READING)) {
                bookAsked = null;
                while (open() && !READING.equals(front())) {
                    back();
                }
                return;
            }
            enter(asked.intValue());
            if (filmAsked != null && filmsRead) {
                openAskedFilm();
            }
            if (bookAsked != null) {
                openAskedBook();
            }
        } else if (!wished(intent)) {
            handed(intent);
        }
    }

    /** The room a door on the home screen icon asks for, or nothing. */
    private static Integer asked(Intent intent) {
        if (intent == null || !ROOM_DOOR.equals(intent.getAction())) {
            return null;
        }
        int which = intent.getIntExtra("room", -1);
        return which >= 0 && which < ROOM_WORDS.length ? Integer.valueOf(which) : null;
    }

    /** Straight into a room from outside: whatever screen of ours was open is put away first. */
    private void enter(int which) {
        /* A touch-up lying over a picture, or a frame chooser over a video, goes with its scene;
           the recording editor lies over the stage itself, and is taken away. */
        mend = null;
        still = null;
        closeRecut();
        calm();
        closeSheet();
        if (asking != ASK_NONE) {
            endAsk();
        }
        if (open()) {
            if (READING.equals(front()) || trail.contains(READING)) {
                closeBook();
            }
            quietOff();
            trail.clear();
            blob.leaving(false);
            placeCrest(false);
        }
        room = which;
        Keep.saveRoom(this, which);
        forgetScene();
        swap(roomView(which));
        plate(true);
        syncTune();
        /* The first time the pictures room is entered, it asks to see them; after that, only its button does. */
        if (which == PICTURES && !Gallery.maySee(this) && !Keep.asked(this, "see")) {
            Keep.setAsked(this, "see");
            requestPermissions(new String[] {Gallery.seeing()}, ASK_SEE);
        }
        if (which == FILMS && !Gallery.mayWatch(this) && !Keep.asked(this, "watch")) {
            Keep.setAsked(this, "watch");
            requestPermissions(new String[] {Gallery.watching()}, ASK_WATCH);
        }
    }

    /** Whether the video on the screen went on as sound when the window went. */
    private boolean filmHanded;

    @Override
    protected void onPause() {
        Trace.keep(this);
        /* A video does not play on behind the home screen as a picture; as
           sound it may, if that was asked for — the sound service takes it
           from where it is, with the phone's own sound controls. */
        if (film != null && FILM.equals(front())) {
            boolean going = film.going();
            film.hold();
            /* Playing or paused, a video left is handed over when that was
               asked for: the widget then shows it, and its key plays it. */
            if (Keep.filmBehind(this) && film.length() > 0L) {
                Gallery.Photo f = film.item();
                Intent go = new Intent(this, Sound.class);
                go.setAction(Sound.OPEN);
                go.putExtra("uri", f.uri.toString());
                go.putExtra("key", f.item().key());
                go.putExtra("place", f.place);
                go.putExtra("name", f.name);
                go.putExtra("mime", f.mime == null ? "video/*" : f.mime);
                go.putExtra("paused", !going);
                startForegroundService(go);
                filmHanded = true;
                Trace.note(going ? "film goes on as sound" : "film handed over, paused");
            }
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!cameraOut && Shelf.prefs(this).getString("shot", null) != null) {
            /* The camera's answer never came — the phone let this window go while
               the camera was in front, or the camera kept its own counsel: the
               place is looked at all the same, and what is in it is taken up. */
            clock.post(new Runnable() {
                public void run() {
                    shot(false, false, null);
                }
            });
        }
        if (filmHanded) {
            /* Back on the video: its sound stops, and the picture goes on from there. */
            filmHanded = false;
            Sound s = Sound.live();
            if (film != null && FILM.equals(front()) && s != null && s.current() != null
                && s.current().key().equals(film.item().item().key())) {
                boolean was = s.playing();
                long at = s.position();
                s.pause(true);
                film.takeBack(at, was);
                Trace.note("film taken back from its sound");
            }
        }
        /* Back from the system's own page for all files: the answer is only
           known now, so the folders screen is drawn again with it, and if
           the leave was asked for there, the rooms are walked again. */
        if (settings.askedAllFiles) {
            settings.askedAllFiles = false;
            if (!Shelf.allFiles()) {
                Shelf.setWhole(this, false);
            }
            settings.regather();
        }
        if (FOLDERS.equals(front())) {
            forgetScene();
            swap(settings.foldersView());
        }
    }

    @Override
    protected void onDestroy() {
        Sound.listen(null);
        Desk.onLabel(null);
        clock.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /**
     * A folder given by the system's picker, kept on the shelf: for writing as
     * well, so its files can be renamed and deleted, or for reading only if
     * that is all it was given for. A folder inside a larger one already on
     * the shelf is not taken twice, and its grant goes straight back; a
     * larger one swallows the folders inside it. Walked at once, unless a
     * waiting change will have it walked.
     */
    private void keepTree(Uri where, boolean walk) {
        try {
            try {
                getContentResolver().takePersistableUriPermission(where,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            } catch (SecurityException readOnly) {
                getContentResolver().takePersistableUriPermission(where,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Trace.note("folder kept for reading only");
            }
        } catch (Exception refused) {
            Trace.note("the folder was not kept: " + refused);
        }
        if (Shelf.coveredBy(this, where.toString(), Shelf.trees(this)) != null) {
            try {
                getContentResolver().releasePersistableUriPermission(where,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (Exception gone) {
                Trace.note("the grant was already gone: " + gone);
            }
            if (walk) {
                flash(Glyph.BOOK, Words.s("folder_there"));
            }
            return;
        }
        Shelf.addTree(this, where.toString());
        int went = Shelf.absorb(this, where.toString());
        if (went > 0) {
            flash(Glyph.BOOK, Words.s("folders_joined").replace("{n}", String.valueOf(went)));
            Trace.note("folders swallowed by a larger one: " + went);
        }
        if (walk) {
            settings.regather();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == ASK_EDIT) {
            /* Back from an editor: whatever it made is found by walking again. */
            afterChange();
            return;
        }
        if (request == ASK_WRITE || request == ASK_REMOVE) {
            /* The system asked, and was answered. */
            Shelf.Item it = changing;
            changing = null;
            if (result != RESULT_OK || it == null) {
                Trace.note("the system was answered no");
                return;
            }
            if (request == ASK_REMOVE) {
                removed(it);
            } else if (Change.renameInIndex(this, it.uri, changingTo)) {
                renamed(it, Change.sibling(it.place, changingTo), changingTo, null);
            } else {
                flash(Glyph.CROSS, Words.s("not_done"));
            }
            return;
        }
        if (request == ASK_SHOOT) {
            /* A camera writing to the place it was given answers with no data at all;
               one that kept the picture elsewhere may say where. */
            cameraOut = false;
            shot(true, result == RESULT_OK, data);
            return;
        }
        if (request == ASK_REOPEN) {
            if (result != RESULT_OK || data == null || data.getData() == null) {
                reopening = null;
                reopeningTo = null;
                Trace.note("the folder was not opened again");
                return;
            }
            reopened(data.getData());
            return;
        }
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri where = data.getData();
        if (request == OPEN_TREE) {
            keepTree(where, true);
            return;
        }
        if (request == LOAD_MODULE) {
            String text = readText(where);
            if (!Words.isModule(text)) {
                flash(Glyph.LANGUAGE, Words.s("module_bad"));
                return;
            }
            Words.read(text);
            Words.save(this);
            relabel();
            flash(Glyph.TICK, Words.s("module_taken"));
            Trace.note("module taken: " + Words.name());
        } else if (request == SAVE_FORM) {
            boolean kept = writeText(where, Words.write());
            flash(kept ? Glyph.TICK : Glyph.LANGUAGE, Words.s(kept ? "module_saved" : "unsaved"));
        }
    }

    private String readText(Uri where) {
        try {
            InputStream in = getContentResolver().openInputStream(where);
            if (in == null) {
                return null;
            }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int got;
            while ((got = in.read(chunk)) > 0 && bytes.size() < 1024 * 1024) {
                bytes.write(chunk, 0, got);
            }
            in.close();
            return new String(bytes.toByteArray(), "UTF-8");
        } catch (Exception broken) {
            Trace.note("could not read a module: " + broken);
            return null;
        }
    }

    private boolean writeText(Uri where, String text) {
        try {
            OutputStream out = getContentResolver().openOutputStream(where);
            if (out == null) {
                return false;
            }
            out.write(text.getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Exception broken) {
            Trace.note("could not write the form: " + broken);
            return false;
        }
    }

    // ------------------------------------------------------------------ flash

    /**
     * A capsule that says what happened where the eye cannot see it, and
     * goes. It is drawn in the opposite of what lies under it — light over
     * the dark rooms and night paper, dark over day paper — and it floats
     * on a shadow of its own, so it is never mistaken for part of the scene.
     */
    void flash(int kind, String text) {
        if (note == null) {
            note = new LinearLayout(this);
            note.setOrientation(LinearLayout.HORIZONTAL);
            note.setGravity(Gravity.CENTER_VERTICAL);
            note.setPadding(Round.dp(18), Round.dp(14), Round.dp(24), Round.dp(14));
            note.setVisibility(View.GONE);
            FrameLayout.LayoutParams where = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            where.bottomMargin = Round.dp(18);
            stage.addView(note, where);
        }
        note.removeAllViews();
        /* Not a slab of the opposite light laid over the screen, but a capsule of
           the application's own tones, standing clear of any bar at the foot. */
        note.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.FULL));
        note.setElevation(Round.px(6f));
        FrameLayout.LayoutParams noteAt = (FrameLayout.LayoutParams) note.getLayoutParams();
        int lift = Round.dp(18);
        if (PHOTO.equals(front()) && caption != null && caption.shown()) {
            lift = Round.dp(16) + caption.getHeight() + Round.dp(12);
        } else if (FILM.equals(front()) && filmChrome) {
            lift = Round.dp(112);
        }
        if (noteAt.bottomMargin != lift) {
            noteAt.bottomMargin = lift;
            note.setLayoutParams(noteAt);
        }

        final Glyph mark = new Glyph(this, kind, Round.px(22f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.rightMargin = Round.dp(12);
        note.addView(mark, markParams);

        TextView says = Letter.set(new TextView(this), Letter.BODY_M);
        says.setText(text);
        says.setTextColor(Tone.of(Tone.ON_SURFACE));
        note.addView(says);
        /* The mark arrives a beat after the words' capsule, with a little give. */
        mark.setScaleX(0.4f);
        mark.setScaleY(0.4f);
        mark.animate().scaleX(1f).scaleY(1f).setStartDelay(Pace.STAGGER).setDuration(Pace.GROW)
            .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f)).start();

        note.bringToFront();
        note.setVisibility(View.VISIBLE);
        note.setAlpha(0f);
        note.setTranslationY(Round.px(14f));
        note.animate().alpha(1f).translationY(0f).setDuration(Pace.SHEET)
            .setInterpolator(Pace.STANDARD).start();
        note.removeCallbacks(hush);
        note.postDelayed(hush, 1900L);
    }

    private final Runnable hush = new Runnable() {
        public void run() {
            if (note == null) {
                return;
            }
            note.animate().alpha(0f).translationY(Round.px(10f)).setDuration(Pace.LEAVE)
                .setInterpolator(Pace.AWAY).withEndAction(new Runnable() {
                    public void run() {
                        note.setVisibility(View.GONE);
                    }
                }).start();
        }
    };
}
