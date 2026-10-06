package io.github.shumtugle.mirabilia;

import android.content.Intent;
import android.os.Build;
import android.view.Gravity;
import java.util.ArrayList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The screens behind the mark in the corner.
 *
 * A room is a place a person spends time in; these are the rooms behind the
 * door marked staff. They share nothing with one another but the way they
 * are built, and keeping them here leaves the rooms themselves to be read
 * on their own. Each screen is made when it is opened and forgotten when it
 * is left; what it remembers is kept elsewhere, so this class holds no state
 * of its own but the window it draws into.
 */
final class Settings {

    private final Main at;

    /** The colour screen's live parts: the specimens it repaints, the two dials, the wallpaper chip. */
    private final java.util.ArrayList<TextView> swatches = new java.util.ArrayList<TextView>();
    private final java.util.ArrayList<int[]> swatchRoles = new java.util.ArrayList<int[]>();
    /**
     * A little after the last turn of a colour dial: the shortcuts and the
     * widget are drawn again in the new colours. Not at every step of the
     * dial — that would be dozens of drawings a second — but once the hand
     * has let go.
     */
    private final Runnable publish = new Runnable() {
        public void run() {
            Doors.publish(at);
            Desk.push(at);
        }
    };
    private Dial hueDial;
    private Dial richDial;
    private LinearLayout wallChip;
    private Glyph wallMark;
    private TextView wallWord;

    Settings(Main at) {
        this.at = at;
    }

    /**
     * What the application did, as it wrote it down: the last few hundred
     * lines, the newest last, in a face where columns line up, and two keys
     * to take it away — to the clipboard, or to anyone who will read it.
     */
    View logView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("log"), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("log_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);
        final String all = Trace.whole(at);
        LinearLayout keys = new LinearLayout(at);
        keys.setOrientation(LinearLayout.HORIZONTAL);
        keys.addView(at.button(Words.s("log_copy"), Main.TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                android.content.ClipboardManager board = (android.content.ClipboardManager)
                    at.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (board != null) {
                    board.setPrimaryClip(android.content.ClipData.newPlainText("log", all));
                }
                if (Build.VERSION.SDK_INT < 33) {
                    at.flash(Glyph.TICK, Words.s("copied"));
                }
            }
        }));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shareParams.leftMargin = Round.dp(8);
        keys.addView(at.button(Words.s("share"), Main.OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_TEXT, all);
                try {
                    at.startActivity(Intent.createChooser(send, null));
                } catch (Exception none) {
                    Trace.note("nothing to share the log with: " + none);
                }
            }
        }), shareParams);
        LinearLayout.LayoutParams keysParams = Main.wide();
        keysParams.topMargin = Round.dp(20);
        column.addView(keys, keysParams);
        LinearLayout lines = new LinearLayout(at);
        lines.setOrientation(LinearLayout.VERTICAL);
        lines.setPadding(Round.dp(16), Round.dp(10), Round.dp(16), Round.dp(14));
        lines.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        logLines(lines, all);
        LinearLayout.LayoutParams linesParams = Main.wide();
        linesParams.topMargin = Round.dp(16);
        column.addView(lines, linesParams);
        return at.scroll(column);
    }

    /**
     * The journal laid out to be read rather than deciphered: the hour apart
     * from the words, the subject of a line lit, the runs of the application
     * parted by a line of their own, and a drum turned forty times folded
     * into one line with the count of how often it said the same thing.
     */
    private void logLines(LinearLayout into, String all) {
        String[] raw = all.split("\n");
        int most = 260;
        String lastWhen = null;
        String lastWords = null;
        int same = 0;
        TextView row = null;
        for (int i = 0; i < raw.length && into.getChildCount() < most; i++) {
            String line = raw[i];
            if (line.trim().length() == 0) {
                continue;
            }
            if (line.startsWith("\u2014")) {
                lastWords = null;
                same = 0;
                into.addView(logBreak(), Main.wide());
                continue;
            }
            int gap = line.indexOf("  ");
            String when = gap > 0 ? line.substring(0, gap) : "";
            String words = gap > 0 ? line.substring(gap + 2).trim() : line.trim();
            String subject = subjectOf(words);
            if (row != null && lastWords != null && subject.equals(subjectOf(lastWords))
                && subject.length() > 0) {
                /* The same subject over and over: one line, and how many times. */
                same++;
                row.setText(logWords(lastWhen == null ? when : lastWhen, lastWords, same));
                continue;
            }
            same = 1;
            lastWords = words;
            lastWhen = when;
            row = Letter.set(new TextView(at), Letter.BODY_S);
            row.setTextColor(Tone.of(Tone.ON_SURFACE));
            row.setPadding(0, Round.dp(5), 0, Round.dp(5));
            row.setText(logWords(when, words, 1));
            row.setTextIsSelectable(true);
            into.addView(row, Main.wide());
        }
        if (into.getChildCount() == 0) {
            into.addView(at.words(Letter.BODY_M, Words.s("log_what"), Tone.ON_SURFACE_VARIANT), Main.wide());
        }
    }

    /** What a line is about: the word before its colon, when it has one. */
    private static String subjectOf(String words) {
        int colon = words.indexOf(':');
        return colon > 0 ? words.substring(0, colon) : "";
    }

    /** One line: the hour quiet, the subject in the accent, the rest plain, and the count if it repeated. */
    private CharSequence logWords(String when, String words, int times) {
        String hour = when.length() >= 8 ? when.substring(0, 8) : when;
        String said = times > 1 ? words + "   \u00D7" + times : words;
        android.text.SpannableStringBuilder made = new android.text.SpannableStringBuilder();
        made.append(hour).append("   ").append(said);
        made.setSpan(new android.text.style.ForegroundColorSpan(Tone.of(Tone.ON_SURFACE_VARIANT)),
            0, hour.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        int colon = said.indexOf(':');
        if (colon > 0) {
            int from = hour.length() + 3;
            made.setSpan(new android.text.style.ForegroundColorSpan(Tone.of(Tone.PRIMARY)),
                from, from + colon, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (times > 1) {
            int from = made.length() - (String.valueOf(times).length() + 1);
            made.setSpan(new android.text.style.ForegroundColorSpan(Tone.of(Tone.ON_SURFACE_VARIANT)),
                from, made.length(), android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return made;
    }

    /** Where one run of the application ends and the one before it begins. */
    private View logBreak() {
        LinearLayout made = new LinearLayout(at);
        made.setOrientation(LinearLayout.HORIZONTAL);
        made.setGravity(Gravity.CENTER_VERTICAL);
        made.setPadding(0, Round.dp(14), 0, Round.dp(10));
        View left = new View(at);
        left.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        LinearLayout.LayoutParams lineAt = new LinearLayout.LayoutParams(0, Math.max(1, Round.dp(1)), 1f);
        made.addView(left, lineAt);
        TextView said = Letter.set(new TextView(at), Letter.LABEL_S);
        said.setText(Words.s("log_before"));
        said.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
        said.setPadding(Round.dp(10), 0, Round.dp(10), 0);
        made.addView(said);
        View right = new View(at);
        right.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        made.addView(right, new LinearLayout.LayoutParams(0, Math.max(1, Round.dp(1)), 1f));
        return made;
    }

    // ------------------------------------------------------------------ widget

    /** Whether the widget screen shows the widget with a book read aloud, or with a recording. */
    private boolean widgetVoice;
    /** The widget screen's live parts: the picture at its head, and what its choices redraw. */
    private Showcase showcase;
    private View depthBlock;
    private Pair groundPair;
    private Pair keyPair;
    private Pair scalePair;
    /** The depths of glass, in hundredths of darkness, and the one last shown. */
    private static final int[] DEPTHS_OF_GLASS = {10, 20, 40, 60, 80, 90};
    private int shownDepth = -1;

    /**
     * How the widget on the home screen looks.
     *
     * At the head, standing still while the rest scrolls, the widget itself
     * on a wallpaper grown from the seed, with a switch to see it with a
     * recording or with a book read aloud. It is the only picture on the
     * screen. Under it the choices are plain: each named above its row, each
     * row one capsule of two halves with the thing drawn small in each, and
     * the depth of glass a drum under a needle. Whatever is chosen answers
     * in the picture at the head, so the eye never has to leave it.
     */
    View widgetView() {
        LinearLayout whole = new LinearLayout(at);
        whole.setOrientation(LinearLayout.VERTICAL);

        /* The head: the name and the widget itself. It does not scroll. */
        LinearLayout head = new LinearLayout(at);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(Round.dp(24), Round.dp(28), Round.dp(24), Round.dp(4));
        head.addView(at.words(Letter.HEADLINE_M, Words.s("widget"), Tone.ON_SURFACE));

        showcase = new Showcase(at, Round.px(Round.XL));
        showcase.show(Desk.preview(at, 356, 164, widgetVoice), 0L);
        int heroTall = Math.round((at.getResources().getDisplayMetrics().widthPixels
            - Round.dp(12 + 48)) * 0.5f);
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, heroTall);
        heroParams.topMargin = Round.dp(16);
        head.addView(showcase, heroParams);

        Pair modes = new Pair(at, new String[] {
            "III  " + Words.s("music"), "IV  " + Words.s("aloud"),
        }, null, widgetVoice ? 1 : 0, new Pair.Picked() {
            public void picked(int which) {
                widgetVoice = which == 1;
                showWidget(Pace.ARRIVE);
            }
        });
        LinearLayout.LayoutParams modesParams = Main.wide();
        modesParams.topMargin = Round.dp(12);
        head.addView(modes, modesParams);
        at.arrive(head);
        whole.addView(head, Main.wide());

        /* Under it, the choices, plain. */
        LinearLayout column = at.column();
        column.setPadding(Round.dp(24), Round.dp(4), Round.dp(24), Round.dp(40));
        android.animation.LayoutTransition motion = new android.animation.LayoutTransition();
        motion.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        motion.setDuration(Pace.ARRIVE);
        motion.setInterpolator(android.animation.LayoutTransition.CHANGE_APPEARING, Pace.STANDARD);
        motion.setInterpolator(android.animation.LayoutTransition.CHANGE_DISAPPEARING, Pace.STANDARD);
        motion.setInterpolator(android.animation.LayoutTransition.CHANGING, Pace.STANDARD);

        column.addView(heading(Words.s("ground")), headingPlace());
        groundPair = new Pair(at, new String[] {
            Words.s("ground_seed"), Words.s("ground_glass"),
        }, new Pair.Icon[] {groundIcon(Desk.GROUND_SEED), groundIcon(Desk.GROUND_GLASS)},
            Keep.widgetGround(at), new Pair.Picked() {
                public void picked(int which) {
                    pickWidget("widgetGround", which);
                    if (depthBlock != null) {
                        depthBlock.setVisibility(which == Desk.GROUND_GLASS ? View.VISIBLE : View.GONE);
                    }
                }
            });
        column.addView(groundPair, Main.wide());

        /* The depth of glass, only while the ground is glass. */
        LinearLayout depth = new LinearLayout(at);
        depth.setOrientation(LinearLayout.VERTICAL);
        depth.addView(heading(Words.s("glass")), headingPlace());
        String[] numbers = new String[DEPTHS_OF_GLASS.length];
        int nearest = 0;
        int glass = Keep.glass(at);
        for (int i = 0; i < DEPTHS_OF_GLASS.length; i++) {
            numbers[i] = String.valueOf(DEPTHS_OF_GLASS[i]);
            if (Math.abs(alphaOf(i) - glass) < Math.abs(alphaOf(nearest) - glass)) {
                nearest = i;
            }
        }
        shownDepth = nearest;
        depth.addView(new Depth(at, numbers, nearest, new Depth.Turned() {
            public void turned(int step, boolean done) {
                if (step != shownDepth) {
                    shownDepth = step;
                    Keep.saveGlass(at, alphaOf(step));
                    showWidget(Pace.LEAVE);
                    if (groundPair != null) {
                        groundPair.invalidate();
                    }
                }
                if (done) {
                    Desk.push(at);
                }
            }
        }), Main.wide());
        depth.setVisibility(Keep.widgetGround(at) == Desk.GROUND_GLASS ? View.VISIBLE : View.GONE);
        depthBlock = depth;
        column.addView(depth, Main.wide());

        column.addView(heading(Words.s("key_face")), headingPlace());
        keyPair = new Pair(at, new String[] {Words.s("key_ball"), Words.s("key_disc")},
            new Pair.Icon[] {keyIcon(Desk.KEY_BALL), keyIcon(Desk.KEY_DISC)},
            Keep.widgetKey(at), new Pair.Picked() {
                public void picked(int which) {
                    pickWidget("widgetKey", which);
                }
            });
        column.addView(keyPair, Main.wide());

        column.addView(heading(Words.s("scale")), headingPlace());
        scalePair = new Pair(at, new String[] {Words.s("scale_drum"), Words.s("scale_thread")},
            new Pair.Icon[] {scaleIcon(Desk.SCALE_DRUM), scaleIcon(Desk.SCALE_THREAD)},
            Keep.widgetScale(at), new Pair.Picked() {
                public void picked(int which) {
                    pickWidget("widgetScale", which);
                }
            });
        column.addView(scalePair, Main.wide());

        /* Set only now, so the rows arrive as every scene's do; from here on the
           depth of glass opens and closes and the rows under it make way. */
        column.setLayoutTransition(motion);
        ScrollView below = at.scroll(column);
        /* What scrolls goes under the head softly rather than being cut. */
        below.setVerticalFadingEdgeEnabled(true);
        below.setFadingEdgeLength(Round.dp(24));
        whole.addView(below, new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        return whole;
    }

    private static int alphaOf(int step) {
        return Math.round(255f * DEPTHS_OF_GLASS[step] / 100f);
    }

    /** A row's name, above it: quiet, so that the widget stays the loudest thing on the screen. */
    private TextView heading(String text) {
        TextView made = at.words(Letter.LABEL_L, text, Tone.ON_SURFACE_VARIANT);
        made.setPadding(Round.dp(4), 0, 0, 0);
        return made;
    }

    private static LinearLayout.LayoutParams headingPlace() {
        LinearLayout.LayoutParams params = Main.wide();
        params.topMargin = Round.dp(24);
        params.bottomMargin = Round.dp(10);
        return params;
    }

    /**
     * A ground, drawn small: the seed's card as a solid block, the glass as a
     * darker pane with its hairline, at the depth now chosen.
     */
    private Pair.Icon groundIcon(final int kind) {
        return new Pair.Icon() {
            public void paint(android.graphics.Canvas canvas, float cx, float cy, float span) {
                float u = at.getResources().getDisplayMetrics().density;
                Desk.Ink ink = new Desk.Ink(kind, Keep.glass(at));
                if (kind == Desk.GROUND_SEED) {
                    ink.card = Tone.of(Tone.SURFACE_HIGHEST);
                }
                float w = span;
                float h = span * 0.62f;
                Desk.card(canvas, new android.graphics.RectF(cx - w / 2f, cy - h / 2f,
                    cx + w / 2f, cy + h / 2f), 6f * u, ink, u);
                android.graphics.Paint line = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
                line.setColor(ink.text);
                line.setAlpha(170);
                float t = 1.5f * u;
                canvas.drawRoundRect(new android.graphics.RectF(cx - w * 0.3f, cy - t / 2f,
                    cx + w * 0.3f, cy + t / 2f), t / 2f, t / 2f, line);
            }
        };
    }

    /**
     * A key, drawn small, of the ground now chosen. The record shows itself
     * as one: grooves round a label, the mark on the label.
     */
    private Pair.Icon keyIcon(final int kind) {
        return new Pair.Icon() {
            public void paint(android.graphics.Canvas canvas, float cx, float cy, float span) {
                float u = at.getResources().getDisplayMetrics().density;
                Desk.Ink ink = new Desk.Ink(Keep.widgetGround(at), Keep.glass(at));
                float r = span * 0.45f;
                if (kind == Desk.KEY_BALL) {
                    Desk.ball(canvas, cx, cy, r, u * 0.5f);
                    Desk.mark(canvas, Glyph.PLAY, cx, cy, r * 0.95f, 0xFF5A3A12);
                    return;
                }
                boolean glassy = Keep.widgetGround(at) == Desk.GROUND_GLASS;
                Desk.disc(canvas, cx, cy, r, glassy ? 0xFF1A1C20 : ink.printDark, ink.discEdge, u);
                android.graphics.Paint groove = new android.graphics.Paint(
                    android.graphics.Paint.ANTI_ALIAS_FLAG);
                groove.setStyle(android.graphics.Paint.Style.STROKE);
                groove.setStrokeWidth(Math.max(1f, 0.75f * u));
                groove.setColor((ink.printLight & 0x00FFFFFF) | 0x40000000);
                canvas.drawCircle(cx, cy, r * 0.78f, groove);
                canvas.drawCircle(cx, cy, r * 0.62f, groove);
                float hub = r * 0.42f;
                Desk.disc(canvas, cx, cy, hub, ink.hub, ink.hubEdge, u);
                Desk.mark(canvas, Glyph.PLAY, cx, cy, hub * 1.25f, ink.hubInk);
            }
        };
    }

    /** A scale, drawn small, in the colours of the ground now chosen. */
    private Pair.Icon scaleIcon(final int kind) {
        return new Pair.Icon() {
            public void paint(android.graphics.Canvas canvas, float cx, float cy, float span) {
                float u = at.getResources().getDisplayMetrics().density;
                Desk.Ink ink = new Desk.Ink(Keep.widgetGround(at), Keep.glass(at));
                int text = Keep.widgetGround(at) == Desk.GROUND_GLASS
                    ? ink.text : Tone.of(Tone.ON_SURFACE);
                float half = span * 0.6f;
                if (kind == Desk.SCALE_DRUM) {
                    Desk.drum(canvas, cx - half, cx + half, cy, u * 0.42f, 0.42f, ink.accent, text);
                } else {
                    Desk.thread(canvas, cx - half, cx + half, cy, u * 0.8f, 0.42f, ink.accent, text);
                }
            }
        };
    }

    /** A choice kept: the home screen is told, and the picture at the head answers. */
    private void pickWidget(String what, int value) {
        Keep.saveWidget(at, what, value);
        Desk.push(at);
        showWidget(Pace.ARRIVE);
        if (keyPair != null) {
            keyPair.invalidate();
        }
        if (scalePair != null) {
            scalePair.invalidate();
        }
    }

    /** The picture at the head of the widget screen, drawn again as it now is. */
    void showWidget(long over) {
        if (showcase != null && Main.WIDGET.equals(at.front())) {
            showcase.show(Desk.preview(at, 356, 164, widgetVoice), over);
        }
    }

    /** A scene left behind: the screen's live parts are let go with it. */
    void forget() {
        swatches.clear();
        swatchRoles.clear();
        hueDial = null;
        richDial = null;
        wallChip = null;
        wallMark = null;
        wallWord = null;
        showcase = null;
        depthBlock = null;
        groundPair = null;
        keyPair = null;
        scalePair = null;
    }
    // --------------------------------------------------------------- settings

    /** The settings: one card for each thing that can be set, and the version at the foot. */
    View settingsView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("settings"), Tone.ON_SURFACE));

        /* What the application looks like first, the colour and the widget
           side by side; then what it holds, the folders; then its words;
           and last the journal, which is for when something went wrong. */
        LinearLayout.LayoutParams lookParams = Main.wide();
        lookParams.topMargin = Round.dp(28);
        column.addView(card(Glyph.LOOK, Words.s("look"), Words.s("look_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.LOOK);
                }
            }), lookParams);

        LinearLayout.LayoutParams widgetParams = Main.wide();
        widgetParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.SPARK, Words.s("widget"), Words.s("widget_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.WIDGET);
                }
            }), widgetParams);

        LinearLayout.LayoutParams seeingParams = Main.wide();
        seeingParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.PICTURE, Words.s(Main.ROOM_WORDS[Main.PICTURES]), Words.s("seeing_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.SEEING);
                }
            }), seeingParams);

        LinearLayout.LayoutParams soundingParams = Main.wide();
        soundingParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.NOTE, Words.s("sounding"), Words.s("sounding_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.SOUNDING);
                }
            }), soundingParams);

        LinearLayout.LayoutParams foldersParams = Main.wide();
        foldersParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.BOOK, Words.s("folders"), Words.s("folders_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.FOLDERS);
                }
            }), foldersParams);

        LinearLayout.LayoutParams languageParams = Main.wide();
        languageParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.LANGUAGE, Words.s("language"), Words.s("language_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.LANGUAGE);
                }
            }), languageParams);

        LinearLayout.LayoutParams logParams = Main.wide();
        logParams.topMargin = Round.dp(8);
        column.addView(card(Glyph.CARET, Words.s("log"), Words.s("log_what"),
            new View.OnClickListener() {
                public void onClick(View v) {
                    at.door(Main.LOG);
                }
            }), logParams);

        String version = "";
        try {
            version = at.getPackageManager().getPackageInfo(at.getPackageName(), 0).versionName;
        } catch (Exception unknown) {
            version = "";
        }
        TextView made = at.words(Letter.LABEL_M, at.getString(R.string.app_name) + "  " + version,
            Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams madeParams = Main.wide();
        madeParams.topMargin = Round.dp(32);
        column.addView(made, madeParams);
        return at.scroll(column);
    }

    /** A card: a mark in its disc, a name, and one line about it. */
    private LinearLayout card(int kind, String name, String caption, View.OnClickListener click) {
        LinearLayout made = new LinearLayout(at);
        made.setOrientation(LinearLayout.HORIZONTAL);
        made.setGravity(Gravity.CENTER_VERTICAL);
        made.setPadding(Round.dp(16), Round.dp(16), Round.dp(20), Round.dp(16));
        made.setBackground(Round.touch(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L),
            Tone.of(Tone.ON_SURFACE), Round.L));
        made.setOnClickListener(click);

        Glyph mark = new Glyph(at, kind, Round.px(44f), 0.5f,
            Tone.of(Tone.SURFACE_HIGHEST), 0x00000000, Tone.of(Tone.PRIMARY));
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        markParams.rightMargin = Round.dp(16);
        made.addView(mark, markParams);

        LinearLayout said = new LinearLayout(at);
        said.setOrientation(LinearLayout.VERTICAL);
        said.addView(at.words(Letter.TITLE_M, name, Tone.ON_SURFACE));
        TextView line = at.words(Letter.BODY_M, caption, Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams lineParams = Main.wide();
        lineParams.topMargin = Round.dp(2);
        said.addView(line, lineParams);
        made.addView(said, new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return made;
    }

    // ------------------------------------------------------------------- look

    /**
     * The seed, and everything that grows from it, on one screen. The top is
     * a specimen of the roles themselves — the three accents, their quieter
     * containers with ink on them, and the ground in its steps — and under
     * it the two dials. Every movement of a dial regrows the whole scheme at
     * once, the bar and the button included, so the choice is seen in place
     * and not in a preview.
     */
    View lookView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("look"), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("look_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        LinearLayout accents = row();
        accents.addView(swatch(Tone.PRIMARY, Tone.ON_PRIMARY, Words.s("sw_primary"),
            Letter.LABEL_L, Round.L), cell(88f, 0));
        accents.addView(swatch(Tone.SECONDARY, Tone.ON_SECONDARY, Words.s("sw_secondary"),
            Letter.LABEL_L, Round.L), cell(88f, 8));
        accents.addView(swatch(Tone.TERTIARY, Tone.ON_TERTIARY, Words.s("sw_tertiary"),
            Letter.LABEL_L, Round.L), cell(88f, 8));
        LinearLayout.LayoutParams accentsParams = Main.wide();
        accentsParams.topMargin = Round.dp(28);
        column.addView(accents, accentsParams);

        LinearLayout containers = row();
        containers.addView(swatch(Tone.PRIMARY_CONTAINER, Tone.ON_PRIMARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 0));
        containers.addView(swatch(Tone.SECONDARY_CONTAINER, Tone.ON_SECONDARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 8));
        containers.addView(swatch(Tone.TERTIARY_CONTAINER, Tone.ON_TERTIARY_CONTAINER, "Aa",
            Letter.TITLE_L, Round.M), cell(56f, 8));
        LinearLayout.LayoutParams containersParams = Main.wide();
        containersParams.topMargin = Round.dp(8);
        column.addView(containers, containersParams);

        LinearLayout ground = row();
        int[] steps = {
            Tone.SURFACE_LOWEST, Tone.SURFACE, Tone.SURFACE_CONTAINER,
            Tone.SURFACE_HIGH, Tone.SURFACE_HIGHEST, Tone.SURFACE_BRIGHT,
        };
        for (int i = 0; i < steps.length; i++) {
            ground.addView(swatch(steps[i], Tone.ON_SURFACE, "", Letter.LABEL_S, Round.S),
                cell(36f, i == 0 ? 0 : 4));
        }
        LinearLayout.LayoutParams groundParams = Main.wide();
        groundParams.topMargin = Round.dp(8);
        column.addView(ground, groundParams);

        TextView groundWord = at.words(Letter.LABEL_M, Words.s("sw_ground"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams groundWordParams = Main.wide();
        groundWordParams.topMargin = Round.dp(6);
        column.addView(groundWord, groundWordParams);

        TextView hueWord = at.words(Letter.LABEL_L, Words.s("hue"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams hueWordParams = Main.wide();
        hueWordParams.topMargin = Round.dp(32);
        column.addView(hueWord, hueWordParams);
        hueDial = new Dial(at, Tone.hue() / 360f, new Dial.Moved() {
            public void moved(float value, boolean done) {
                seed(value * 360f, Tone.rich());
            }
        });
        column.addView(hueDial, Main.wide());

        TextView richWord = at.words(Letter.LABEL_L, Words.s("richness"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams richWordParams = Main.wide();
        richWordParams.topMargin = Round.dp(16);
        column.addView(richWord, richWordParams);
        richDial = new Dial(at, Tone.rich(), new Dial.Moved() {
            public void moved(float value, boolean done) {
                seed(Tone.hue(), value);
            }
        });
        column.addView(richDial, Main.wide());

        if (Build.VERSION.SDK_INT >= 31) {
            wallChip = new LinearLayout(at);
            wallChip.setOrientation(LinearLayout.HORIZONTAL);
            wallChip.setGravity(Gravity.CENTER_VERTICAL);
            wallChip.setPadding(Round.dp(10), 0, Round.dp(16), 0);
            wallChip.setMinimumHeight(Round.dp(32));
            wallMark = new Glyph(at, Glyph.SPARK, Round.px(18f), 0.92f,
                0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
            LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            markParams.rightMargin = Round.dp(8);
            wallChip.addView(wallMark, markParams);
            wallWord = Letter.set(new TextView(at), Letter.LABEL_L);
            wallWord.setText(Words.s("wallpaper"));
            wallChip.addView(wallWord);
            wallChip.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Keep.saveLook(at, Tone.hue(), Tone.rich(), true);
                    Tone.read(at);
                    if (hueDial != null) {
                        hueDial.value(Tone.hue() / 360f);
                        richDial.value(Tone.rich());
                    }
                    at.retone();
                    at.clock.removeCallbacks(publish);
                    at.clock.post(publish);
                }
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Round.dp(32));
            chipParams.topMargin = Round.dp(20);
            column.addView(wallChip, chipParams);
        }

        paintLook();
        return at.scroll(column);
    }

    /** A hand moved a dial: the seed is chosen, and no longer follows the wallpaper. */
    private void seed(float hue, float rich) {
        Keep.saveLook(at, hue, rich, false);
        Tone.read(at);
        at.retone();
        at.clock.removeCallbacks(publish);
        at.clock.postDelayed(publish, 1200L);
    }

    private LinearLayout row() {
        LinearLayout made = new LinearLayout(at);
        made.setOrientation(LinearLayout.HORIZONTAL);
        return made;
    }

    private static LinearLayout.LayoutParams cell(float tall, int before) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, Round.dp(tall), 1f);
        params.leftMargin = Round.dp(before);
        return params;
    }

    private TextView swatch(int fill, int ink, String text, int rung, float radius) {
        TextView made = Letter.set(new TextView(at), rung);
        made.setText(text);
        made.setGravity(Gravity.BOTTOM | Gravity.START);
        made.setPadding(Round.dp(12), 0, Round.dp(12), Round.dp(10));
        made.setTag(Float.valueOf(radius));
        swatches.add(made);
        swatchRoles.add(new int[] {fill, ink});
        return made;
    }

    /** The specimen and the dials in the colours the seed gives now. */
    void paintLook() {
        for (int i = 0; i < swatches.size(); i++) {
            TextView one = swatches.get(i);
            int[] pair = swatchRoles.get(i);
            float radius = ((Float) one.getTag()).floatValue();
            one.setBackground(Round.box(Tone.of(pair[0]), radius));
            one.setTextColor(Tone.of(pair[1]));
        }
        if (hueDial != null) {
            int[] circle = new int[25];
            for (int i = 0; i < circle.length; i++) {
                circle[i] = Tone.at(72f, 44.0, i * 15f);
            }
            hueDial.colours(circle);
            hueDial.ink(Tone.of(Tone.PRIMARY));
        }
        if (richDial != null) {
            int[] way = new int[9];
            for (int i = 0; i < way.length; i++) {
                way[i] = Tone.at(72f, Tone.chromaOf(i / 8f), Tone.hue());
            }
            richDial.colours(way);
            richDial.ink(Tone.of(Tone.PRIMARY));
        }
        if (wallChip != null) {
            boolean on = Keep.wall(at);
            if (on) {
                wallChip.setBackground(Round.touch(Round.box(Tone.of(Tone.SECONDARY_CONTAINER),
                    Round.S), Tone.of(Tone.ON_SECONDARY_CONTAINER), Round.S));
                wallWord.setTextColor(Tone.of(Tone.ON_SECONDARY_CONTAINER));
                wallMark.tint(0x00000000, 0x00000000, Tone.of(Tone.ON_SECONDARY_CONTAINER));
            } else {
                wallChip.setBackground(Round.touch(Round.ring(0x00000000, Round.S,
                    Tone.of(Tone.OUTLINE_VARIANT)), Tone.of(Tone.ON_SURFACE_VARIANT), Round.S));
                wallWord.setTextColor(Tone.of(Tone.ON_SURFACE_VARIANT));
                wallMark.tint(0x00000000, 0x00000000, Tone.of(Tone.PRIMARY));
            }
        }
    }

    // --------------------------------------------------------------- language

    View languageView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("language"), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("language_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        LinearLayout card = new LinearLayout(at);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Round.dp(20), Round.dp(18), Round.dp(20), Round.dp(18));
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        String called = !Words.active() ? Words.s("english")
            : (Words.name().length() > 0 ? Words.name() : "untitled");
        card.addView(at.words(Letter.TITLE_L, called, Tone.ON_SURFACE));
        if (Words.active()) {
            String count = Words.s("words_of")
                .replace("{n}", String.valueOf(Words.filled()))
                .replace("{m}", String.valueOf(Words.total()));
            TextView counted = at.words(Letter.BODY_M, count, Tone.ON_SURFACE_VARIANT);
            LinearLayout.LayoutParams countedParams = Main.wide();
            countedParams.topMargin = Round.dp(4);
            card.addView(counted, countedParams);
            /* A word whose English moved on since the module was made is said
               out loud, so a rename is not hidden behind a full count. */
            int stale = Words.stale();
            if (stale > 0) {
                TextView old = at.words(Letter.BODY_M,
                    Words.s("stale").replace("{n}", String.valueOf(stale)), Tone.PRIMARY);
                LinearLayout.LayoutParams oldParams = Main.wide();
                oldParams.topMargin = Round.dp(2);
                card.addView(old, oldParams);
            }
        }
        LinearLayout.LayoutParams cardParams = Main.wide();
        cardParams.topMargin = Round.dp(28);
        column.addView(card, cardParams);

        column.addView(at.button(Words.s("module_load"), Main.TONAL, new View.OnClickListener() {
            public void onClick(View v) {
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                at.startActivityForResult(pick, Main.LOAD_MODULE);
            }
        }), Main.buttonPlace(24));
        column.addView(at.button(Words.s("module_save"), Main.OUTLINED, new View.OnClickListener() {
            public void onClick(View v) {
                Intent make = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                make.addCategory(Intent.CATEGORY_OPENABLE);
                make.setType("text/plain");
                make.putExtra(Intent.EXTRA_TITLE, "language.txt");
                at.startActivityForResult(make, Main.SAVE_FORM);
            }
        }), Main.buttonPlace(8));
        if (Words.active()) {
            column.addView(at.button(Words.s("use_english"), Main.PLAIN, new View.OnClickListener() {
                public void onClick(View v) {
                    Words.forget();
                    Words.save(at);
                    at.relabel();
                }
            }), Main.buttonPlace(8));
        }
        return at.scroll(column);
    }

    // ---------------------------------------------------------------- folders

    /** A folder armed to be forgotten by a second press, and when it was armed. */
    private String forgetting;
    private long forgettingAt;

    /**
     * The folders the books come from. Each can be taken with everything
     * under it or alone, and given back; below them, the other way of
     * finding books — the whole phone — asked for out loud.
     */
    View foldersView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("folders"), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("folders_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);

        ArrayList<String> trees = Shelf.trees(at);
        for (int i = 0; i < trees.size(); i++) {
            LinearLayout.LayoutParams params = Main.wide();
            params.topMargin = Round.dp(i == 0 ? 24 : 8);
            column.addView(folderRow(trees.get(i)), params);
        }
        LinearLayout.LayoutParams askParams = Main.buttonPlace(trees.isEmpty() ? 24 : 16);
        column.addView(at.button(Words.s("open_folder"), Main.TONAL, at.folderAsk()), askParams);

        /* The whole phone: one card, a line for each room that can take
           it, each with a switch — on or off, and nothing else to read. */
        TextView section = at.words(Letter.LABEL_L, Words.s("whole"), Tone.ON_SURFACE_VARIANT);
        section.setPadding(Round.dp(4), 0, 0, 0);
        LinearLayout.LayoutParams sectionParams = Main.wide();
        sectionParams.topMargin = Round.dp(36);
        sectionParams.bottomMargin = Round.dp(10);
        column.addView(section, sectionParams);

        LinearLayout whole = new LinearLayout(at);
        whole.setOrientation(LinearLayout.VERTICAL);
        whole.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        whole.setClipToOutline(true);
        whole.addView(switchLine(Glyph.BOOK, Words.s(Main.ROOM_WORDS[Main.BOOKS]), Words.s("whole_what"),
            Shelf.whole(at), new Main.Flip() {
                public void flip(Toggle toggle) {
                    if (toggle.on()) {
                        Shelf.setWhole(at, false);
                        toggle.set(false, true);
                        regather();
                    } else if (Shelf.allFiles()) {
                        Shelf.setWhole(at, true);
                        toggle.set(true, true);
                        regather();
                    } else {
                        /* The leave for every file is given on a page of the
                           system's own; the switch turns once it is given. */
                        Shelf.setWhole(at, true);
                        askedAllFiles = true;
                        Shelf.askAllFiles(at);
                    }
                }
            }));
        View between = new View(at);
        between.setBackgroundColor(Tone.of(Tone.OUTLINE_VARIANT));
        LinearLayout.LayoutParams betweenParams = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Round.dp(1) / 2));
        betweenParams.leftMargin = Round.dp(72);
        whole.addView(between, betweenParams);
        whole.addView(switchLine(Glyph.NOTE, Words.s(Main.ROOM_WORDS[Main.MUSIC]), Words.s("sounds_whole_what"),
            Shelf.soundsWhole(at) && Shelf.mayHear(at), new Main.Flip() {
                public void flip(Toggle toggle) {
                    if (toggle.on()) {
                        Shelf.setSoundsWhole(at, false);
                        toggle.set(false, true);
                        regather();
                    } else if (Shelf.mayHear(at)) {
                        Shelf.setSoundsWhole(at, true);
                        toggle.set(true, true);
                        regather();
                    } else {
                        at.requestPermissions(new String[] {Shelf.hearing()}, Main.ASK_HEAR);
                    }
                }
            }));
        column.addView(whole, Main.wide());
        return at.scroll(column);
    }

    /**
     * How pictures are shown: for now, whether an opened one lights the
     * screen at its brightest. A card of switch lines, as the folders' card
     * is, so more can join it.
     */
    View seeingView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s(Main.ROOM_WORDS[Main.PICTURES]), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("seeing_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);
        LinearLayout card = new LinearLayout(at);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        card.setClipToOutline(true);
        /* Under the switch, while it is on: always, or by the clock; and by
           the clock, the hours on two drums, as the glass's depth is set. */
        final LinearLayout when = new LinearLayout(at);
        when.setOrientation(LinearLayout.VERTICAL);
        when.setPadding(Round.dp(20), 0, Round.dp(20), Round.dp(20));
        final LinearLayout hours = new LinearLayout(at);
        hours.setOrientation(LinearLayout.VERTICAL);
        String[] clock = new String[24];
        for (int h = 0; h < 24; h++) {
            clock[h] = String.format(java.util.Locale.ROOT, "%02d:00", h);
        }
        hours.addView(hourRow(Words.s("bright_from"), clock, Keep.brightFrom(at), true), Main.wide());
        LinearLayout.LayoutParams toParams = Main.wide();
        toParams.topMargin = Round.dp(8);
        hours.addView(hourRow(Words.s("bright_to"), clock, Keep.brightTo(at), false), toParams);
        hours.setVisibility(Keep.brightByClock(at) ? View.VISIBLE : View.GONE);
        Pair mode = new Pair(at, new String[] {Words.s("bright_always"), Words.s("bright_hours")},
            null, Keep.brightByClock(at) ? 1 : 0, new Pair.Picked() {
                public void picked(int which) {
                    Keep.setBrightByClock(at, which == 1);
                    hours.setVisibility(which == 1 ? View.VISIBLE : View.GONE);
                }
            });
        when.addView(mode, Main.wide());
        LinearLayout.LayoutParams hoursParams = Main.wide();
        hoursParams.topMargin = Round.dp(12);
        when.addView(hours, hoursParams);
        when.setVisibility(Keep.bright(at) ? View.VISIBLE : View.GONE);

        card.addView(switchLine(Glyph.SPARK, Words.s("bright"), Words.s("bright_what"),
            Keep.bright(at), new Main.Flip() {
                public void flip(Toggle toggle) {
                    boolean now = !toggle.on();
                    Keep.setBright(at, now);
                    toggle.set(now, true);
                    when.setVisibility(now ? View.VISIBLE : View.GONE);
                }
            }));
        card.addView(when, Main.wide());
        android.animation.LayoutTransition motion = new android.animation.LayoutTransition();
        motion.enableTransitionType(android.animation.LayoutTransition.CHANGING);
        motion.setDuration(Pace.ARRIVE);
        card.setLayoutTransition(motion);
        when.setLayoutTransition(motion);
        LinearLayout.LayoutParams cardParams = Main.wide();
        cardParams.topMargin = Round.dp(24);
        column.addView(card, cardParams);
        return at.scroll(column);
    }

    /**
     * What sounds when the window goes: for now, whether a video left while
     * it plays goes on as sound. The card will take the sleep timer and the
     * rest of the sound's settings as they come.
     */
    View soundingView() {
        LinearLayout column = at.column();
        column.addView(at.words(Letter.HEADLINE_M, Words.s("sounding"), Tone.ON_SURFACE));
        TextView what = at.words(Letter.BODY_M, Words.s("sounding_what"), Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams whatParams = Main.wide();
        whatParams.topMargin = Round.dp(4);
        column.addView(what, whatParams);
        LinearLayout card = new LinearLayout(at);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));
        card.setClipToOutline(true);
        card.addView(switchLine(Glyph.FILM, Words.s("film_behind"), Words.s("film_behind_what"),
            Keep.filmBehind(at), new Main.Flip() {
                public void flip(Toggle toggle) {
                    boolean now = !toggle.on();
                    Keep.setFilmBehind(at, now);
                    toggle.set(now, true);
                }
            }));
        LinearLayout.LayoutParams cardParams = Main.wide();
        cardParams.topMargin = Round.dp(24);
        column.addView(card, cardParams);
        return at.scroll(column);
    }

    /** One hour to choose: its word before it, and a drum of the day's hours. */
    private LinearLayout hourRow(String word, String[] clock, int start, final boolean first) {
        LinearLayout row = new LinearLayout(at);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView said = at.words(Letter.LABEL_L, word, Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams saidParams = new LinearLayout.LayoutParams(Round.dp(40),
            ViewGroup.LayoutParams.WRAP_CONTENT);
        row.addView(said, saidParams);
        Depth drum = new Depth(at, clock, start, new Depth.Turned() {
            public void turned(int step, boolean done) {
                if (done) {
                    if (first) {
                        Keep.setBrightHours(at, step, Keep.brightTo(at));
                    } else {
                        Keep.setBrightHours(at, Keep.brightFrom(at), step);
                    }
                }
            }
        });
        row.addView(drum, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return row;
    }

    /** Set while the system's page for every file is open, so coming back walks again. */
    boolean askedAllFiles;

    /** The folders or their sources changed: walk again, and draw the rooms anew when they are shown. */
    void regather() {
        at.gathered = false;
        at.soundsGathered = false;
        at.gather();
    }

    /**
     * One line of a card with a switch at its end, in the design language's
     * way: the room's mark, its name, a line on what turning it on means, and
     * the switch. The whole line is the switch's to touch, not only the switch.
     */
    private LinearLayout switchLine(int mark, String name, String what, boolean on, final Main.Flip flip) {
        LinearLayout line = new LinearLayout(at);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(Round.dp(20), Round.dp(16), Round.dp(20), Round.dp(16));
        line.setBackground(Round.touch(Round.box(0x00000000, 0f), Tone.of(Tone.ON_SURFACE), 0f));
        Glyph icon = new Glyph(at, mark, Round.px(24f), 1f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(Round.dp(24), Round.dp(24));
        iconParams.rightMargin = Round.dp(28);
        line.addView(icon, iconParams);
        LinearLayout said = new LinearLayout(at);
        said.setOrientation(LinearLayout.VERTICAL);
        said.addView(at.words(Letter.TITLE_M, name, Tone.ON_SURFACE));
        TextView under = at.words(Letter.BODY_M, what, Tone.ON_SURFACE_VARIANT);
        LinearLayout.LayoutParams underParams = Main.wide();
        underParams.topMargin = Round.dp(2);
        said.addView(under, underParams);
        LinearLayout.LayoutParams saidParams = new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saidParams.rightMargin = Round.dp(16);
        line.addView(said, saidParams);
        final Toggle toggle = new Toggle(at, on);
        line.addView(toggle);
        line.setContentDescription(name);
        line.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                flip.flip(toggle);
            }
        });
        return line;
    }
    /**
     * One opened folder: its name; where it lies, the storage and the whole
     * path, so two folders of one name are never mistaken; a capsule of two
     * halves for taking it with its subfolders or without; and the cross that
     * gives it back, which asks once more before it does.
     */
    private LinearLayout folderRow(final String tree) {
        LinearLayout card = new LinearLayout(at);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Round.dp(20), Round.dp(14), Round.dp(8), Round.dp(18));
        card.setBackground(Round.box(Tone.of(Tone.SURFACE_CONTAINER), Round.L));

        LinearLayout head = new LinearLayout(at);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout said = new LinearLayout(at);
        said.setOrientation(LinearLayout.VERTICAL);
        said.addView(at.words(Letter.TITLE_M, Shelf.folderName(at, tree), Tone.ON_SURFACE));
        String[] where = Shelf.where(tree);
        String place = where[0].length() > 0 ? Words.s(where[0]) + "  \u00B7  " + where[1] : where[1];
        TextView path = at.words(Letter.BODY_S, place, Tone.ON_SURFACE_VARIANT);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.START);
        said.addView(path);
        head.addView(said, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Glyph cross = new Glyph(at, Glyph.CROSS, Round.px(44f), 0.44f, 0x00000000, 0x00000000,
            Tone.of(Tone.ON_SURFACE_VARIANT));
        cross.setBackground(Round.disc(Tone.of(Tone.ON_SURFACE)));
        cross.setClickable(true);
        cross.setContentDescription(Words.s("forget"));
        cross.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                long now = android.os.SystemClock.uptimeMillis();
                if (!tree.equals(forgetting) || now - forgettingAt > 3000L) {
                    forgetting = tree;
                    forgettingAt = now;
                    at.flash(Glyph.CROSS, Words.s("forget_sure"));
                    return;
                }
                forgetting = null;
                Shelf.forget(at, tree);
                regather();
                at.forgetScene();
                at.swap(foldersView());
            }
        });
        head.addView(cross);
        card.addView(head, Main.wide());

        Pair depth = new Pair(at, new String[] {Words.s("deep_on"), Words.s("deep_off")}, null,
            Shelf.deep(at, tree) ? 0 : 1, new Pair.Picked() {
                public void picked(int which) {
                    Shelf.setDeep(at, tree, which == 0);
                    regather();
                }
            });
        LinearLayout.LayoutParams depthParams = Main.wide();
        depthParams.topMargin = Round.dp(12);
        depthParams.rightMargin = Round.dp(12);
        card.addView(depth, depthParams);
        return card;
    }

}
