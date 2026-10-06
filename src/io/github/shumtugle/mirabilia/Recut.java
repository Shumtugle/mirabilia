package io.github.shumtugle.mirabilia;

import android.animation.LayoutTransition;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Locale;

/**
 * A recording touched up, as a picture is: nothing of it changed in place
 * unless its words alone are, and what is cut is a copy beside it.
 *
 * A piece: the whole recording is a line, and two marks on it, A and B,
 * are taken by the finger and dragged; the drum under them turns the mark in
 * hand frame by frame, the smallest step the recording has, some twenty six
 * thousandths of a second. The piece between the marks plays over and over
 * while it is being set — which makes the marks a loop to learn by as well —
 * setting A plays from A, setting B plays the last two seconds up to it. The
 * round button keeps the piece as a new file beside the original, cut by
 * copying its frames byte for byte: nothing decoded, nothing encoded again.
 *
 * Its words: the name, who, the album, its number and year, read from the
 * recording's tag and written into the piece, or into the recording itself
 * when only the words were changed — never over it: a new file is written
 * beside it and put in its place, and only then is the old one let go.
 *
 * And its cover, over the words: the picture the recording carries, and the
 * way to give it another from the collection — the covers first, those
 * taken out of other recordings, then every picture. A picture chosen is cut
 * square from its middle.
 */
final class Recut extends FrameLayout {

    interface Hands {
        /** Kept: the new piece, or the recording with its new words; whether it was a piece. */
        void kept(Shelf.Item made, boolean piece);

        /** Put away, nothing kept. */
        void gone();
    }

    private static final int PIECE = 0;
    private static final int WORDS = 1;
    private static final String[] KEYS = {Mp3.TITLE, Mp3.ARTIST, Mp3.ALBUM, Mp3.TRACK, Mp3.YEAR};
    private static final String[] KEY_WORDS = {"tag_title", "tag_artist", "tag_album", "tag_track", "tag_year"};

    private final Shelf.Item item;
    private final Hands hands;
    private final FrameLayout content;
    private final Span span;
    private final ScrollView form;
    private final EditText[] fields = new EditText[KEYS.length];
    private final LinearLayout tools;
    private final FrameLayout drumRow;
    private final Pair marks;
    private final Glyph playMark;
    private final TextView line;
    private final TextView said;
    private final LinearLayout away;
    private final TextView awayWords;
    private final LinearLayout panel;
    private final Blob blob;

    private MediaPlayer player;
    private boolean prepared;
    private long length;
    private long a;
    private long b;
    private int mark;
    private float frameMs = 26.122f;
    private int kbps;
    private final LinkedHashMap<String, String> original = new LinkedHashMap<String, String>();
    /** The cover as it is shown, and a new one chosen, not yet written. */
    private final android.widget.ImageView coverFace;
    private final TextView coverWords;
    private byte[] newCover;
    private FrameLayout picker;
    private boolean filling;
    private boolean changed;
    private boolean asking;
    private boolean leaving;
    private boolean keeping;
    private int tab = PIECE;

    Recut(Context c, Shelf.Item item, Hands hands) {
        super(c);
        this.item = item;
        this.hands = hands;
        setClickable(true);
        setBackgroundColor(Tone.of(Tone.SURFACE));

        LinearLayout column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Round.dp(8), Round.dp(76), Round.dp(8), Round.dp(16));
        addView(column, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView title = Letter.serif(Letter.set(new TextView(c), Letter.TITLE_L));
        title.setText(Shelf.title(item.name));
        title.setTextColor(Tone.of(Tone.ON_SURFACE));
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setPadding(Round.dp(20), 0, Round.dp(20), 0);
        column.addView(title, wide());
        line = Letter.set(new TextView(c), Letter.LABEL_L);
        line.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        line.setPadding(Round.dp(20), Round.dp(4), Round.dp(20), 0);
        column.addView(line, wide());

        content = new FrameLayout(c);
        column.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        span = new Span(c);
        content.addView(span, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Round.dp(150),
            Gravity.CENTER_VERTICAL));

        form = new ScrollView(c);
        form.setVisibility(GONE);
        LinearLayout rows = new LinearLayout(c);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(Round.dp(12), Round.dp(16), Round.dp(12), Round.dp(16));
        LinearLayout coverRow = new LinearLayout(c);
        coverRow.setOrientation(LinearLayout.HORIZONTAL);
        coverRow.setGravity(Gravity.CENTER_VERTICAL);
        coverRow.setPadding(Round.dp(4), 0, 0, Round.dp(8));
        coverFace = new android.widget.ImageView(c);
        coverFace.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        coverFace.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.dp(16)));
        coverFace.setClipToOutline(true);
        coverRow.addView(coverFace, new LinearLayout.LayoutParams(Round.dp(96), Round.dp(96)));
        coverWords = Letter.set(new TextView(c), Letter.LABEL_L);
        coverWords.setText(Words.s("set_cover"));
        coverWords.setTextColor(Tone.of(Tone.PRIMARY));
        coverWords.setPadding(Round.dp(16), Round.dp(12), Round.dp(16), Round.dp(12));
        coverWords.setBackground(Round.touch(null, Tone.of(Tone.PRIMARY), Round.FULL));
        coverWords.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                pickCover();
            }
        });
        coverFace.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                pickCover();
            }
        });
        LinearLayout.LayoutParams coverWordsAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        coverWordsAt.leftMargin = Round.dp(8);
        coverRow.addView(coverWords, coverWordsAt);
        rows.addView(coverRow, wide());
        for (int i = 0; i < KEYS.length; i++) {
            TextView what = Letter.set(new TextView(c), Letter.LABEL_M);
            what.setText(Words.s(KEY_WORDS[i]));
            what.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
            what.setPadding(Round.dp(16), Round.dp(12), 0, Round.dp(6));
            rows.addView(what, wide());
            EditText field = Letter.set(new EditText(c), Letter.BODY_L);
            field.setTextColor(Tone.of(Tone.ON_SURFACE));
            field.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.dp(16)));
            field.setPadding(Round.dp(16), Round.dp(12), Round.dp(16), Round.dp(12));
            field.setSingleLine(true);
            field.setInputType(KEYS[i].equals(Mp3.YEAR) ? InputType.TYPE_CLASS_NUMBER
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            field.setImeOptions(i == KEYS.length - 1 ? android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                : android.view.inputmethod.EditorInfo.IME_ACTION_NEXT);
            field.addTextChangedListener(new TextWatcher() {
                public void beforeTextChanged(CharSequence s, int st, int co, int af) {
                }

                public void onTextChanged(CharSequence s, int st, int be, int co) {
                }

                public void afterTextChanged(Editable e) {
                    if (!filling) {
                        changed = true;
                    }
                }
            });
            fields[i] = field;
            rows.addView(field, wide());
        }
        form.addView(rows);
        content.addView(form, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(c);
        panel.setOrientation(LinearLayout.VERTICAL);
        tools = new LinearLayout(c);
        tools.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout play = new FrameLayout(c);
        play.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER), Round.FULL),
            Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.FULL));
        playMark = new Glyph(c, Glyph.PLAY, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SECONDARY_CONTAINER));
        play.addView(playMark, new FrameLayout.LayoutParams(Round.dp(24), Round.dp(24), Gravity.CENTER));
        play.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                toggle();
            }
        });
        row.addView(play, new LinearLayout.LayoutParams(Round.dp(56), Round.dp(48)));
        marks = new Pair(c, new String[] {"A", "B"}, null, 0, new Pair.Picked() {
            public void picked(int which) {
                mark = which;
                drum();
                span.invalidate();
                tell(which == 0 ? a : b);
            }
        });
        LinearLayout.LayoutParams marksAt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        marksAt.leftMargin = Round.dp(8);
        row.addView(marks, marksAt);
        tools.addView(row, wide());
        drumRow = new FrameLayout(c);
        LinearLayout.LayoutParams drumAt = wide();
        drumAt.topMargin = Round.dp(8);
        tools.addView(drumRow, drumAt);
        panel.addView(tools, wide());

        LinearLayout bar = new LinearLayout(c);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Round.dp(6), Round.dp(8), Round.dp(8), Round.dp(8));
        bar.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL));
        bar.setClickable(true);
        Pair tabs = new Pair(c, new String[] {Words.s("recut_piece"), Words.s("recut_words")}, null, PIECE,
            new Pair.Picked() {
                public void picked(int which) {
                    pick(which);
                }
            });
        bar.addView(tabs, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        blob = new Blob(c, Round.px(56f), Tone.of(Tone.PRIMARY_CONTAINER), Tone.of(Tone.ON_PRIMARY_CONTAINER));
        blob.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                keep();
            }
        });
        LinearLayout.LayoutParams blobAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blobAt.leftMargin = Round.dp(4);
        bar.addView(blob, blobAt);
        LinearLayout.LayoutParams barAt = wide();
        barAt.topMargin = Round.dp(8);
        panel.addView(bar, barAt);
        column.addView(panel, wide());

        said = Letter.set(new TextView(c), Letter.LABEL_L);
        said.setTextColor(Tone.of(Tone.ON_SURFACE));
        said.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGHEST), Round.FULL));
        said.setPadding(Round.dp(16), Round.dp(8), Round.dp(16), Round.dp(8));
        said.setAlpha(0f);
        said.setFontFeatureSettings("tnum");
        LayoutParams saidAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        saidAt.topMargin = Round.dp(20);
        addView(said, saidAt);

        away = new LinearLayout(c);
        away.setGravity(Gravity.CENTER_VERTICAL);
        away.setPadding(Round.dp(8), Round.dp(8), Round.dp(8), Round.dp(8));
        away.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.FULL),
            Tone.of(Tone.ON_SURFACE), Round.FULL));
        away.setLayoutTransition(new LayoutTransition());
        away.addView(new Glyph(c, Glyph.CROSS, Round.px(24f), 0.92f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT)), new LinearLayout.LayoutParams(Round.dp(24), Round.dp(24)));
        awayWords = Letter.set(new TextView(c), Letter.LABEL_L);
        awayWords.setText(Words.s("leave_unsaved"));
        awayWords.setTextColor(Tone.of(Tone.ON_SURFACE));
        awayWords.setVisibility(GONE);
        LinearLayout.LayoutParams wordsAt = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wordsAt.leftMargin = Round.dp(10);
        wordsAt.rightMargin = Round.dp(8);
        away.addView(awayWords, wordsAt);
        away.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                away();
            }
        });
        LayoutParams awayAt = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        awayAt.leftMargin = Round.dp(16);
        awayAt.topMargin = Round.dp(20);
        addView(away, awayAt);

        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                Mp3.Tag read = null;
                Mp3.Shape shape = null;
                try {
                    InputStream in = app.getContentResolver().openInputStream(Recut.this.item.uri);
                    read = Mp3.readTag(in);
                    in.close();
                    in = app.getContentResolver().openInputStream(Recut.this.item.uri);
                    shape = Mp3.shape(in);
                    in.close();
                } catch (Exception unread) {
                    Trace.note("recut: not read: " + unread.getClass().getSimpleName());
                }
                android.graphics.Bitmap face = null;
                android.media.MediaMetadataRetriever r = new android.media.MediaMetadataRetriever();
                try {
                    r.setDataSource(app, Recut.this.item.uri);
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) {
                        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                        o.inSampleSize = 2;
                        face = android.graphics.BitmapFactory.decodeByteArray(pic, 0, pic.length, o);
                    }
                } catch (Exception none) {
                    // no picture to show
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                        // released either way
                    }
                }
                final android.graphics.Bitmap shown = face;
                final Mp3.Tag tag = read;
                final Mp3.Shape s = shape;
                post(new Runnable() {
                    public void run() {
                        if (s != null && s.rate > 0) {
                            frameMs = s.frameMs();
                            kbps = s.kbps;
                        }
                        filling = true;
                        for (int i = 0; i < KEYS.length; i++) {
                            String v = tag == null ? null : tag.texts.get(KEYS[i]);
                            original.put(KEYS[i], v == null ? "" : v);
                            fields[i].setText(v == null ? "" : v);
                        }
                        filling = false;
                        if (shown != null) {
                            coverFace.setImageBitmap(shown);
                            coverWords.setText(Words.s("change_cover"));
                        }
                        if (prepared) {
                            drum();
                        }
                        describe();
                    }
                });
            }
        }, "recut").start();
        load(c);
    }

    private static LinearLayout.LayoutParams wide() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    // ------------------------------------------------------------ the sound

    private void load(Context c) {
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
            player.setDataSource(c, item.uri);
            player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                public void onPrepared(MediaPlayer mp) {
                    prepared = true;
                    length = Math.max(1L, mp.getDuration());
                    a = 0L;
                    b = length;
                    drum();
                    describe();
                    span.invalidate();
                }
            });
            player.prepareAsync();
        } catch (Exception broken) {
            Trace.note("recut: not opened: " + broken.getClass().getSimpleName());
        }
    }

    private final Runnable loop = new Runnable() {
        public void run() {
            if (player == null || !prepared) {
                return;
            }
            try {
                if (player.isPlaying()) {
                    if (player.getCurrentPosition() >= b) {
                        player.seekTo((int) a);
                    }
                    span.invalidate();
                    postDelayed(this, 30L);
                }
            } catch (IllegalStateException gone) {
                // the player went away between two looks
            }
        }
    };

    /** The piece plays from a moment, and goes round from A once it reaches B. */
    private void playFrom(long at) {
        if (player == null || !prepared) {
            return;
        }
        try {
            player.seekTo((int) Math.max(a, Math.min(b, at)));
            player.start();
            playMark.kind(Glyph.PAUSE);
            removeCallbacks(loop);
            post(loop);
        } catch (IllegalStateException gone) {
            // nothing to play
        }
    }

    private void toggle() {
        if (player == null || !prepared) {
            return;
        }
        try {
            if (player.isPlaying()) {
                player.pause();
                playMark.kind(Glyph.PLAY);
            } else {
                long now = player.getCurrentPosition();
                playFrom(now < a || now >= b ? a : now);
            }
        } catch (IllegalStateException gone) {
            // nothing to play
        }
    }

    private void stop() {
        removeCallbacks(loop);
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // released either way
            }
            player = null;
        }
    }

    // ------------------------------------------------------------ the marks

    /** A mark set: on a frame's edge, never closer to the other than a few frames. */
    private void setMark(int which, long t, boolean done) {
        long gap = Math.max(200L, Math.round(frameMs * 4));
        long snapped = Math.round(Math.round(t / frameMs) * frameMs);
        if (which == 0) {
            a = Math.max(0L, Math.min(b - gap, snapped));
        } else {
            b = Math.min(length, Math.max(a + gap, snapped));
        }
        changed = true;
        span.invalidate();
        tell(which == 0 ? a : b);
        describe();
        if (done) {
            playFrom(which == 0 ? a : Math.max(a, b - 2000L));
        }
    }

    /** The drum of the mark in hand: frame by frame, five seconds either way of it, the seconds named. */
    private void drum() {
        drumRow.removeAllViews();
        if (!prepared) {
            return;
        }
        final long at = mark == 0 ? a : b;
        int reach = Math.max(1, Math.round(5000f / frameMs));
        int before = (int) Math.min(reach, Math.floor(at / frameMs));
        int after = (int) Math.min(reach, Math.floor((length - at) / frameMs));
        final int centre = before;
        int count = before + after + 1;
        String[] ticks = new String[count];
        for (int i = 0; i < count; i++) {
            long t = Math.round(at + (i - centre) * frameMs);
            ticks[i] = (t % 1000L) < frameMs ? clock(t, false) : "";
        }
        Depth d = new Depth(getContext(), ticks, centre, 8f, 0, new Depth.Turned() {
            public void turned(int step, boolean done) {
                setMark(mark, Math.round(at + (step - centre) * frameMs), done);
            }
        });
        drumRow.addView(d, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private static String clock(long ms, boolean fine) {
        long s = ms / 1000L;
        String whole = s >= 3600L
            ? (s / 3600L) + ":" + String.format(Locale.ROOT, "%02d:%02d", (s / 60L) % 60L, s % 60L)
            : (s / 60L) + ":" + String.format(Locale.ROOT, "%02d", s % 60L);
        return fine ? whole + String.format(Locale.ROOT, ".%02d", (ms % 1000L) / 10L) : whole;
    }

    private final Runnable hush = new Runnable() {
        public void run() {
            said.animate().alpha(0f).setDuration(Pace.LEAVE).start();
        }
    };

    private void tell(long t) {
        said.setText((mark == 0 ? "A" : "B") + "  \u00B7  " + clock(t, true));
        say();
    }

    private void say() {
        said.animate().cancel();
        said.animate().alpha(1f).setDuration(Pace.PRESS).start();
        removeCallbacks(hush);
        postDelayed(hush, 1400L);
    }

    /** The line under the name: how long the whole is, and the piece if one is marked. */
    private void describe() {
        StringBuilder s = new StringBuilder();
        if (prepared) {
            s.append(clock(length, false));
        }
        if (kbps > 0) {
            s.append(s.length() > 0 ? "  \u00B7  " : "").append(kbps).append(" kbps");
        }
        if (prepared && (a > 0L || b < length)) {
            s.append("  \u00B7  ").append(Words.s("recut_piece")).append(' ').append(clock(b - a, true));
        }
        line.setText(s);
    }

    // ------------------------------------------------------------ the two ways

    private void pick(int which) {
        if (which == tab) {
            return;
        }
        tab = which;
        boolean piece = tab == PIECE;
        span.setVisibility(piece ? VISIBLE : GONE);
        tools.setVisibility(piece ? VISIBLE : GONE);
        form.setVisibility(piece ? GONE : VISIBLE);
        View shown = piece ? span : form;
        shown.setAlpha(0f);
        shown.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        if (!piece) {
            android.view.inputmethod.InputMethodManager keys = (android.view.inputmethod.InputMethodManager)
                getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keys != null) {
                keys.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        }
    }

    // ------------------------------------------------------------ the cover

    /**
     * The collection's pictures to choose a cover from, rising as a sheet over
     * the words: the covers first, those taken from other recordings, and all
     * the pictures beside them. The one touched becomes the cover, cut square.
     */
    private void pickCover() {
        if (picker != null || keeping) {
            return;
        }
        final Context c = getContext();
        final FrameLayout layer = new FrameLayout(c);
        View scrim = new View(c);
        scrim.setBackgroundColor(Tone.of(Tone.SURFACE_LOWEST, 0.55f));
        scrim.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                closePicker();
            }
        });
        layer.addView(scrim, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        final LinearLayout sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setClickable(true);
        sheet.setPadding(0, Round.dp(10), 0, 0);
        sheet.setBackground(Round.box(Tone.of(Tone.SURFACE_HIGH), Round.XL));
        View handle = new View(c);
        handle.setBackground(Round.box(Tone.of(Tone.ON_SURFACE_VARIANT, 0.4f), Round.FULL));
        LinearLayout.LayoutParams handleAt = new LinearLayout.LayoutParams(Round.dp(32), Round.dp(4));
        handleAt.gravity = Gravity.CENTER_HORIZONTAL;
        handleAt.bottomMargin = Round.dp(14);
        sheet.addView(handle, handleAt);
        TextView named = Letter.set(new TextView(c), Letter.TITLE_L);
        named.setText(Words.s("cover"));
        named.setTextColor(Tone.of(Tone.ON_SURFACE));
        named.setPadding(Round.dp(24), 0, Round.dp(24), Round.dp(12));
        sheet.addView(named, wide());
        final FrameLayout gridAt = new FrameLayout(c);
        final Pair which = new Pair(c, new String[] {"Covers", Words.s("all_photos")}, null, 0, null);
        LinearLayout.LayoutParams whichAt = wide();
        whichAt.leftMargin = Round.dp(16);
        whichAt.rightMargin = Round.dp(16);
        whichAt.bottomMargin = Round.dp(8);
        sheet.addView(which, whichAt);
        sheet.addView(gridAt, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        LayoutParams sheetAt = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            Math.round(getResources().getDisplayMetrics().heightPixels * 0.82f), Gravity.BOTTOM);
        layer.addView(sheet, sheetAt);
        addView(layer, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        picker = layer;
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(Pace.ARRIVE).start();
        sheet.setTranslationY(Round.px(420f));
        sheet.animate().translationY(0f).setDuration(Pace.GROW).setInterpolator(Pace.EMPHASIS).start();

        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final java.util.ArrayList<Gallery.Photo> all = Gallery.load(app);
                final java.util.ArrayList<Gallery.Photo> covers = new java.util.ArrayList<Gallery.Photo>();
                for (int i = 0; i < all.size(); i++) {
                    if (all.get(i).place != null && all.get(i).place.contains("/Mirabilia/Covers/")) {
                        covers.add(all.get(i));
                    }
                }
                post(new Runnable() {
                    public void run() {
                        if (picker != layer) {
                            return;
                        }
                        final java.util.ArrayList<java.util.ArrayList<Gallery.Photo>> lists =
                            new java.util.ArrayList<java.util.ArrayList<Gallery.Photo>>();
                        lists.add(covers);
                        lists.add(all);
                        int start = covers.isEmpty() ? 1 : 0;
                        which.choose(start);
                        which.setPicked(new Pair.Picked() {
                            public void picked(int w) {
                                showGrid(gridAt, lists.get(w));
                            }
                        });
                        showGrid(gridAt, lists.get(start));
                    }
                });
            }
        }, "pictures").start();
    }

    private void showGrid(FrameLayout at, final java.util.ArrayList<Gallery.Photo> list) {
        at.removeAllViews();
        android.widget.GridView grid = Gallery.grid(getContext(), list, new Gallery.Touched() {
            public void touched(int i, boolean held) {
                chose(list.get(i));
            }
        });
        at.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void chose(final Gallery.Photo p) {
        closePicker();
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final byte[] made = Covers.fromPicture(app, p);
                final android.graphics.Bitmap face = made == null ? null
                    : android.graphics.BitmapFactory.decodeByteArray(made, 0, made.length);
                post(new Runnable() {
                    public void run() {
                        if (made == null) {
                            said.setText(Words.s("not_done"));
                            say();
                            return;
                        }
                        newCover = made;
                        changed = true;
                        coverFace.setImageBitmap(face);
                        coverWords.setText(Words.s("change_cover"));
                        coverFace.setScaleX(0.9f);
                        coverFace.setScaleY(0.9f);
                        coverFace.animate().scaleX(1f).scaleY(1f).setDuration(Pace.GROW)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f)).start();
                    }
                });
            }
        }, "cover").start();
    }

    boolean closePicker() {
        if (picker == null) {
            return false;
        }
        final View gone = picker;
        picker = null;
        gone.animate().alpha(0f).setDuration(Pace.LEAVE).withEndAction(new Runnable() {
            public void run() {
                removeView(gone);
            }
        }).start();
        return true;
    }

    void open() {
        panel.setAlpha(0f);
        panel.setTranslationY(Round.px(48f));
        panel.animate().alpha(1f).translationY(0f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE)
            .setInterpolator(Pace.STANDARD).start();
        away.setAlpha(0f);
        away.animate().alpha(1f).setStartDelay(Pace.STAGGER).setDuration(Pace.ARRIVE).start();
        blob.leaving(true);
    }

    private final Runnable unask = new Runnable() {
        public void run() {
            asking = false;
            awayWords.setVisibility(GONE);
        }
    };

    /** The way out without keeping: at once if nothing was changed, asked once more if something was. */
    void away() {
        if (leaving || keeping) {
            return;
        }
        if (closePicker()) {
            return;
        }
        if (!changed || asking) {
            removeCallbacks(unask);
            leave();
            return;
        }
        asking = true;
        awayWords.setVisibility(VISIBLE);
        postDelayed(unask, 3000L);
    }

    private void leave() {
        if (leaving) {
            return;
        }
        leaving = true;
        stop();
        animate().alpha(0f).setDuration(Pace.LEAVE).withEndAction(new Runnable() {
            public void run() {
                hands.gone();
            }
        }).start();
    }

    // ------------------------------------------------------------ keeping

    private LinkedHashMap<String, String> words() {
        LinkedHashMap<String, String> w = new LinkedHashMap<String, String>();
        for (int i = 0; i < KEYS.length; i++) {
            w.put(KEYS[i], fields[i].getText().toString().trim());
        }
        return w;
    }

    private void keep() {
        if (keeping || leaving) {
            return;
        }
        final boolean piece = prepared && (a > frameMs || b < length - frameMs);
        final LinkedHashMap<String, String> w = words();
        boolean reworded = newCover != null;
        for (int i = 0; i < KEYS.length; i++) {
            String was = original.get(KEYS[i]);
            if (!w.get(KEYS[i]).equals(was == null ? "" : was.trim())) {
                reworded = true;
            }
        }
        if (!piece && !reworded) {
            leave();
            return;
        }
        keeping = true;
        blob.depart();
        if (player != null) {
            try {
                player.pause();
            } catch (IllegalStateException ignored) {
                // stopped already
            }
        }
        final long from = a;
        final long to = b;
        final byte[] cover = newCover;
        final Context app = getContext().getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                final Shelf.Item made = piece ? cut(app, item, from, to, w, cover) : reword(app, item, w, cover);
                post(new Runnable() {
                    public void run() {
                        keeping = false;
                        if (made == null) {
                            said.setText(Words.s("not_done"));
                            say();
                            return;
                        }
                        stop();
                        hands.kept(made, piece);
                    }
                });
            }
        }, "keep").start();
    }

    /** The piece between the marks, with its words, written beside the recording. */
    static Shelf.Item cut(Context c, Shelf.Item it, long from, long to, LinkedHashMap<String, String> w,
                          byte[] cover) {
        try {
            InputStream in = c.getContentResolver().openInputStream(it.uri);
            Mp3.Tag tag = Mp3.readTag(in);
            in.close();
            if (cover != null) {
                tag.cover(cover);
            }
            in = c.getContentResolver().openInputStream(it.uri);
            long[] bytes = Mp3.span(in, from, to, new Mp3.Shape());
            in.close();
            byte[] head = Mp3.writeTag(tag, w);
            return write(c, it, Change.stem(it.name) + "_cut", head, bytes[0], bytes[1]);
        } catch (Exception broken) {
            Trace.note("recut: piece not cut: " + broken.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * New words for the whole recording. With the leave for every file, into
     * the recording itself: a new file is written beside it, the old one set
     * aside, the new one put in its place, and only then is the old one let
     * go — if anything fails on the way, the old one goes back. Without that
     * leave, into a copy beside it.
     */
    static Shelf.Item reword(Context c, Shelf.Item it, LinkedHashMap<String, String> w, byte[] cover) {
        try {
            InputStream in = c.getContentResolver().openInputStream(it.uri);
            Mp3.Tag tag = Mp3.readTag(in);
            in.close();
            if (cover != null) {
                tag.cover(cover);
            }
            byte[] head = Mp3.writeTag(tag, w);
            File file = Shelf.allFiles() ? Change.fileOf(it.place) : null;
            if (file == null || !file.isFile() || file.getParentFile() == null) {
                return write(c, it, Change.stem(it.name) + "_edit", head, tag.end, -1L);
            }
            File dir = file.getParentFile();
            File part = new File(dir, "." + file.getName() + ".part");
            File was = new File(dir, "." + file.getName() + ".was");
            OutputStream out = new FileOutputStream(part);
            in = c.getContentResolver().openInputStream(it.uri);
            try {
                Mp3.pour(head, in, tag.end, -1L, out);
            } finally {
                in.close();
                out.close();
            }
            if (!file.renameTo(was)) {
                part.delete();
                return null;
            }
            if (!part.renameTo(file)) {
                was.renameTo(file);
                part.delete();
                return null;
            }
            was.delete();
            android.media.MediaScannerConnection.scanFile(c, new String[] {file.getAbsolutePath()}, null, null);
            Trace.note("recut: words written in place");
            return it;
        } catch (Exception broken) {
            Trace.note("recut: words not written: " + broken.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * A new recording beside the old one: in the same folder with the leave
     * for every file, among the application's own music otherwise.
     */
    private static Shelf.Item write(Context c, Shelf.Item it, String stem, byte[] head, long from, long to)
        throws Exception {
        File file = Shelf.allFiles() ? Change.fileOf(it.place) : null;
        if (file != null && file.isFile() && file.getParentFile() != null) {
            File dir = file.getParentFile();
            File made = new File(dir, stem + ".mp3");
            for (int n = 2; made.exists(); n++) {
                made = new File(dir, stem + " " + n + ".mp3");
            }
            OutputStream out = new FileOutputStream(made);
            InputStream in = c.getContentResolver().openInputStream(it.uri);
            try {
                Mp3.pour(head, in, from, to, out);
            } finally {
                in.close();
                out.close();
            }
            final Uri[] found = new Uri[1];
            final java.util.concurrent.CountDownLatch told = new java.util.concurrent.CountDownLatch(1);
            android.media.MediaScannerConnection.scanFile(c, new String[] {made.getAbsolutePath()},
                new String[] {"audio/mpeg"}, new android.media.MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        found[0] = uri;
                        told.countDown();
                    }
                });
            told.await(3, java.util.concurrent.TimeUnit.SECONDS);
            Uri uri = found[0] != null ? found[0] : Uri.fromFile(made);
            Trace.note("recut: kept beside it, " + made.length() + " bytes");
            return new Shelf.Item(made.getName(), uri, "audio/mpeg", made.length(), System.currentTimeMillis(),
                it.folder, Shelf.fromPath(made.getAbsolutePath()));
        }
        ContentValues v = new ContentValues();
        String name = stem + ".mp3";
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg");
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Mirabilia/");
        v.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri made = c.getContentResolver().insert(
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
        if (made == null) {
            return null;
        }
        OutputStream out = c.getContentResolver().openOutputStream(made);
        InputStream in = c.getContentResolver().openInputStream(it.uri);
        try {
            Mp3.pour(head, in, from, to, out);
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.MediaColumns.IS_PENDING, 0);
        c.getContentResolver().update(made, done, null, null);
        Trace.note("recut: kept among the application's music");
        return new Shelf.Item(name, made, "audio/mpeg", 0L, System.currentTimeMillis(), it.folder,
            "primary:Music/Mirabilia/" + name);
    }

    // ------------------------------------------------------------ the line

    /**
     * The recording as a line, the piece on it in the accent, A and B as two
     * discs to be taken and dragged, the moment playing as a hair across it;
     * A's time under it, B's over it, so they never lie on each other.
     */
    private final class Span extends View {

        private final Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint words = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int dragging = -1;

        Span(Context c) {
            super(c);
            ink.setStrokeCap(Paint.Cap.ROUND);
            words.setTextAlign(Paint.Align.CENTER);
            words.setTextSize(Round.px(12f) * getResources().getConfiguration().fontScale);
            words.setFontFeatureSettings("tnum");
        }

        private float x0() {
            return Round.px(36f);
        }

        private float x1() {
            return getWidth() - Round.px(36f);
        }

        private float xOf(long t) {
            return x0() + (x1() - x0()) * (length <= 0L ? 0f : t / (float) length);
        }

        private long tOf(float x) {
            float k = (x - x0()) / Math.max(1f, x1() - x0());
            return Math.round(Math.max(0f, Math.min(1f, k)) * length);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float y = getHeight() / 2f;
            ink.setStyle(Paint.Style.STROKE);
            ink.setStrokeWidth(Round.px(6f));
            ink.setColor(Tone.of(Tone.SURFACE_HIGHEST));
            canvas.drawLine(x0(), y, x1(), y, ink);
            if (!prepared) {
                return;
            }
            float xa = xOf(a);
            float xb = xOf(b);
            ink.setStrokeWidth(Round.px(14f));
            ink.setColor(Tone.of(Tone.PRIMARY_CONTAINER));
            canvas.drawLine(xa, y, xb, y, ink);
            if (player != null) {
                try {
                    float xp = xOf(player.getCurrentPosition());
                    ink.setStrokeWidth(Round.px(2f));
                    ink.setColor(Tone.of(Tone.ON_SURFACE));
                    canvas.drawLine(xp, y - Round.px(20f), xp, y + Round.px(20f), ink);
                } catch (IllegalStateException gone) {
                    // no moment to show
                }
            }
            disc(canvas, xa, y, "A", mark == 0);
            disc(canvas, xb, y, "B", mark == 1);
            words.setColor(Tone.of(Tone.ON_SURFACE_VARIANT));
            canvas.drawText(clock(a, true), xa, y + Round.px(44f), words);
            canvas.drawText(clock(b, true), xb, y - Round.px(32f), words);
        }

        private void disc(Canvas canvas, float x, float y, String letter, boolean held) {
            ink.setStyle(Paint.Style.FILL);
            ink.setColor(Tone.of(Tone.PRIMARY));
            canvas.drawCircle(x, y, Round.px(16f), ink);
            if (held) {
                ink.setStyle(Paint.Style.STROKE);
                ink.setStrokeWidth(Round.px(2.5f));
                ink.setColor(Tone.of(Tone.ON_SURFACE));
                canvas.drawCircle(x, y, Round.px(21f), ink);
            }
            words.setColor(Tone.of(Tone.ON_PRIMARY));
            android.graphics.Paint.FontMetrics m = words.getFontMetrics();
            canvas.drawText(letter, x, y - (m.ascent + m.descent) / 2f, words);
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (!prepared || keeping) {
                return true;
            }
            float x = e.getX();
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float da = Math.abs(x - xOf(a));
                    float db = Math.abs(x - xOf(b));
                    float reach = Round.px(36f);
                    dragging = da <= db ? (da < reach ? 0 : -1) : (db < reach ? 1 : -1);
                    if (dragging >= 0 && dragging != mark) {
                        mark = dragging;
                        marks.choose(mark);
                    }
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                case MotionEvent.ACTION_MOVE:
                    if (dragging >= 0) {
                        setMark(dragging, tOf(x), false);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (dragging >= 0) {
                        setMark(dragging, tOf(x), true);
                        drum();
                    } else {
                        playFrom(tOf(x));
                    }
                    dragging = -1;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = -1;
                    return true;
                default:
                    return true;
            }
        }
    }
}
