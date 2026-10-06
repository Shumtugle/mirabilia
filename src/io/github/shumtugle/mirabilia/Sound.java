package io.github.shumtugle.mirabilia;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Icon;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.view.KeyEvent;

import java.util.ArrayList;

/**
 * Everything that must go on sounding when the screen goes dark lives here:
 * the player and the voice that reads a book aloud. A window is not allowed
 * to outlive itself, so a four hour lecture and a novel read aloud both
 * belong to a service or to nobody. One service for both, because they
 * compete for the same ear: the voice starting holds the player, the player
 * starting puts the voice away, and the notification, the headset and the
 * widgets speak to whichever of the two is on.
 *
 * The recordings play in the order the music room shows them, one after
 * another. Each keeps its own place, written every five seconds and at
 * every pause, and coming back to one — after a pause, or another day —
 * steps back a few seconds first: a thought broken off mid-word is picked
 * up by its end, not by its last syllable.
 *
 * The phone is a shared room. A call takes the sound away and gives it back;
 * a headphone pulled out stops it rather than letting a lecture spill into a
 * tram; the buttons on a headset, wired or not, speak to this service while
 * it plays.
 */
public final class Sound extends Service
    implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    static final String OPEN = "io.github.shumtugle.mirabilia.sound.OPEN";
    static final String TOGGLE = "io.github.shumtugle.mirabilia.sound.TOGGLE";
    static final String BACK = "io.github.shumtugle.mirabilia.sound.BACK";
    static final String ON = "io.github.shumtugle.mirabilia.sound.ON";
    static final String NEXT = "io.github.shumtugle.mirabilia.sound.NEXT";
    static final String PREV = "io.github.shumtugle.mirabilia.sound.PREV";
    static final String STOP = "io.github.shumtugle.mirabilia.sound.STOP";
    static final String VOICE = "io.github.shumtugle.mirabilia.sound.VOICE";

    /** Fifteen back and thirty on: you go back because you missed a phrase, on because you know it. */
    static final long BACK_MS = 15000L;
    static final long ON_MS = 30000L;

    private static final String CHANNEL = "sound";
    private static final int NOTE = 7;
    private static final long SAVE_EVERY = 5000L;

    /** Told whenever what is playing, or whether it plays, changes; and, often, where the voice is. */
    interface Ear {
        void heard();

        void spoke();
    }

    private static Sound live;
    private static Ear ear;

    static Sound live() {
        return live;
    }

    static void listen(Ear e) {
        ear = e;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ArrayList<Shelf.Item> list = new ArrayList<Shelf.Item>();
    private int index = -1;
    private MediaPlayer player;
    private ParcelFileDescriptor opened;
    private MediaSession session;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean prepared;
    private boolean wanted;
    private boolean resuming;
    private boolean pausedByFocus;
    private boolean ducked;
    private boolean noisyOn;
    private boolean foreground;

    // ------------------------------------------------------------ life

    @Override
    public void onCreate() {
        super.onCreate();
        live = this;
        /* The service may be woken without the window, by a headset key:
           the words of the notification must still be in the reader's language. */
        Words.load(this);
        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        NotificationManager notes = getSystemService(NotificationManager.class);
        if (notes != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL,
                Words.s("music"), NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notes.createNotificationChannel(channel);
        }
        session = new MediaSession(this, "sound");
        /* Without these two flags the session is a label on a notification
           and not a receiver: the headset button went to whichever
           application asked for it last. */
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
            | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                if (voiceOn) {
                    if (voiceHeld) {
                        voiceHold();
                    }
                } else {
                    play();
                }
            }

            @Override
            public void onPause() {
                if (voiceOn) {
                    if (!voiceHeld) {
                        voiceHold();
                    }
                } else {
                    pause(true);
                }
            }

            @Override
            public void onStop() {
                onPause();
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                prev();
            }

            @Override
            public void onSeekTo(long ms) {
                seekTo(ms);
            }

            @Override
            public void onFastForward() {
                nudge(ON_MS);
            }

            @Override
            public void onRewind() {
                nudge(-BACK_MS);
            }

            @Override
            public boolean onMediaButtonEvent(Intent i) {
                /* Some earpieces send a key rather than a transport call,
                   and a single click comes as the hook key rather than as
                   play-pause. Both are the same wish. Play and pause stay
                   apart from the toggle: a headset that says play while
                   something plays must not stop it. */
                KeyEvent k = (KeyEvent) i.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                if (k != null && k.getAction() == KeyEvent.ACTION_DOWN) {
                    switch (k.getKeyCode()) {
                        case KeyEvent.KEYCODE_HEADSETHOOK:
                        case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                            toggle();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PLAY:
                            if (voiceOn) {
                                if (voiceHeld) {
                                    voiceHold();
                                }
                            } else {
                                play();
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PAUSE:
                            if (voiceOn) {
                                if (!voiceHeld) {
                                    voiceHold();
                                }
                            } else {
                                pause(true);
                            }
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_NEXT:
                            next();
                            return true;
                        case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                            prev();
                            return true;
                        default:
                            break;
                    }
                }
                return super.onMediaButtonEvent(i);
            }
        });
        session.setActive(true);
        ui.postDelayed(ticker, SAVE_EVERY);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        /* A service started for playing has a few seconds to show itself,
           and what to play may take longer to find than that. */
        showNote();
        String act = intent == null ? null : intent.getAction();
        if (VOICE.equals(act)) {
            voiceTake();
            return START_STICKY;
        }
        if (voiceOn && act != null && !OPEN.equals(act)) {
            /* The voice is on: the keys are its keys. */
            if (TOGGLE.equals(act)) {
                voiceHold();
            } else if (BACK.equals(act)) {
                voiceJump(-15);
            } else if (ON.equals(act)) {
                voiceJump(30);
            } else if (NEXT.equals(act)) {
                voiceStep(1);
            } else if (PREV.equals(act)) {
                voiceStep(-1);
            } else if (STOP.equals(act)) {
                voiceStop();
            }
            return START_STICKY;
        }
        if (OPEN.equals(act)) {
            voiceStop();
            String uri = intent.getStringExtra("uri");
            String key = intent.getStringExtra("key");
            reload();
            int at = find(key != null ? key : uri);
            if (at < 0) {
                at = find(uri);
            }
            if (at < 0 && uri != null) {
                /* A file from outside the room — handed over, or a video's sound
                   going on behind the home screen — keeps its own place and kind. */
                String mime = intent.getStringExtra("mime");
                list.add(new Shelf.Item(intent.getStringExtra("name"), Uri.parse(uri),
                    mime == null ? "" : mime, 0L, 0L, "", intent.getStringExtra("place")));
                at = list.size() - 1;
            }
            /* A video left paused is taken as it was: ready, and still. */
            openAt(at, !intent.getBooleanExtra("paused", false));
        } else if (act != null && list.isEmpty() && !STOP.equals(act)) {
            /* Killed while paused, and woken by a headset or a notification
               key: the last recording is taken up from memory. */
            reload();
            ArrayList<Shelf.Item> last = Shelf.recentSounds(this, 1);
            int at = last.isEmpty() ? -1 : find(last.get(0).key());
            if (at >= 0) {
                openAt(at, TOGGLE.equals(act));
            } else {
                stopAll();
            }
        } else if (TOGGLE.equals(act)) {
            toggle();
        } else if (BACK.equals(act)) {
            nudge(-BACK_MS);
        } else if (ON.equals(act)) {
            nudge(ON_MS);
        } else if (NEXT.equals(act)) {
            next();
        } else if (PREV.equals(act)) {
            prev();
        } else if (STOP.equals(act)) {
            stopAll();
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent i) {
        return null;
    }

    @Override
    public void onDestroy() {
        voiceStop();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        save();
        ui.removeCallbacks(ticker);
        noisy(false);
        dropFocus();
        release();
        if (session != null) {
            session.setActive(false);
            session.release();
            session = null;
        }
        live = null;
        told();
        super.onDestroy();
    }

    private void reload() {
        ArrayList<Shelf.Item> kept = Shelf.keptSounds(this);
        String now = current() == null ? null : current().key();
        list.clear();
        list.addAll(kept);
        index = now == null ? -1 : find(now);
    }

    /** Where a recording stands in the list, found by its place or by its address. */
    private int find(String known) {
        if (known == null) {
            return -1;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).key().equals(known) || list.get(i).uri.toString().equals(known)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The recording after one, or before it, in its own folder, or none. A
     * folder is heard to its end and no further: a book of lectures ends
     * where its last lecture ends, and does not run on into the next book.
     */
    private int after(int i) {
        return i >= 0 && i + 1 < list.size() && list.get(i + 1).home().equals(list.get(i).home())
            ? i + 1 : -1;
    }

    private int before(int i) {
        return i > 0 && i < list.size() && list.get(i - 1).home().equals(list.get(i).home())
            ? i - 1 : -1;
    }

    // ------------------------------------------------------------ what plays

    Shelf.Item current() {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    boolean playing() {
        try {
            return player != null && prepared && player.isPlaying();
        } catch (Exception e) {
            return false;
        }
    }

    long position() {
        try {
            return player != null && prepared ? player.getCurrentPosition() : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    long duration() {
        try {
            return player != null && prepared ? Math.max(0, player.getDuration()) : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private void openAt(int at, boolean start) {
        if (at < 0 || at >= list.size()) {
            return;
        }
        save();
        index = at;
        final Shelf.Item it = list.get(at);
        /* Started again, a recording heard to its end is no longer that. */
        Shelf.setDone(this, it.key(), false);
        prepared = false;
        wanted = start;
        resuming = false;
        release();
        player = new MediaPlayer();
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
        player.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build());
        if (!source(it.uri)) {
            Trace.note("sound: no source for " + Shelf.kind(it.name));
            told();
            return;
        }
        player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer p) {
                prepared = true;
                long kept = Shelf.soundAt(Sound.this, it.key());
                long d = duration();
                /* Coming back to a recording steps back a little, as coming
                   back from a pause does; nearly finished is the same as not
                   started, since nobody wants to open a lecture at its last
                   sentence. */
                if (kept > 0 && d > 0 && kept < d - 15000L) {
                    seekRaw(Math.max(0L, kept - rollback()));
                }
                speed();
                if (wanted) {
                    play();
                } else {
                    showNote();
                    told();
                }
            }
        });
        try {
            player.prepareAsync();
        } catch (Exception e) {
            Trace.note("sound: prepare refused: " + e);
        }
        /* A video's sound is not a recording heard: it is not put among them. */
        if (it.mime == null || !it.mime.startsWith("video/")) {
            Shelf.heard(this, it);
        }
        Keep.heardMusic(this);
        describe(it);
        told();
    }

    /**
     * Some files refuse to prepare from a document address and open perfectly
     * from a plain descriptor of the same file — recordings on a memory card
     * especially. The second road costs nothing.
     */
    private boolean source(Uri uri) {
        try {
            player.setDataSource(this, uri);
            return true;
        } catch (Exception first) {
            try {
                opened = getContentResolver().openFileDescriptor(uri, "r");
                if (opened != null) {
                    player.reset();
                    player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
                    player.setDataSource(opened.getFileDescriptor());
                    return true;
                }
            } catch (Exception second) {
                Trace.note("sound: both roads closed: " + second);
            }
        }
        return false;
    }

    private void release() {
        if (player != null) {
            try {
                player.release();
            } catch (Exception ignored) {
                // a player that will not let go is let go of all the same
            }
            player = null;
        }
        if (opened != null) {
            try {
                opened.close();
            } catch (Exception ignored) {
                // the descriptor dies with the file it named
            }
            opened = null;
        }
    }

    // ------------------------------------------------------------ keys

    void toggle() {
        if (voiceOn) {
            voiceHold();
            return;
        }
        if (playing()) {
            pause(true);
        } else {
            play();
        }
    }

    void play() {
        if (voiceOn) {
            voiceStop();
        }
        if (player == null || !prepared) {
            wanted = true;
            return;
        }
        if (!takeFocus()) {
            Trace.note("sound: focus refused");
            return;
        }
        noisy(true);
        if (resuming) {
            resuming = false;
            long p = position();
            if (p > 0) {
                seekRaw(Math.max(0L, p - rollback()));
            }
        }
        try {
            player.setVolume(1f, 1f);
            player.start();
            speed();
        } catch (Exception e) {
            Trace.note("sound: start refused: " + e);
            return;
        }
        wanted = false;
        pausedByFocus = false;
        showNote();
        told();
    }

    /** A pause asked for by hand is final; one caused by the phone gives the sound back when it can. */
    void pause(boolean byHand) {
        try {
            if (player != null && prepared && player.isPlaying()) {
                player.pause();
                resuming = true;
            }
        } catch (Exception ignored) {
            // a player already stopped is paused enough
        }
        wanted = false;
        if (byHand) {
            pausedByFocus = false;
            dropFocus();
        }
        noisy(false);
        save();
        showNote();
        told();
    }

    void nudge(long delta) {
        if (voiceOn) {
            voiceJump((int) (delta / 1000L));
            return;
        }
        if (player == null || !prepared) {
            return;
        }
        seekTo(position() + delta);
    }

    void seekTo(long ms) {
        if (player == null || !prepared) {
            return;
        }
        long d = duration();
        long to = Math.max(0L, d > 0 ? Math.min(ms, d - 500L) : ms);
        seekRaw(to);
        resuming = false;
        save();
        showNote();
        told();
    }

    private void seekRaw(long ms) {
        try {
            player.seekTo((int) ms);
        } catch (Exception ignored) {
            // a seek refused leaves the place where it was
        }
    }

    void next() {
        if (voiceOn) {
            voiceStep(1);
            return;
        }
        int then = after(index);
        if (then >= 0) {
            openAt(then, true);
        }
    }

    /** Back is the start of this recording when some of it has played, the one before otherwise. */
    void prev() {
        if (voiceOn) {
            voiceStep(-1);
            return;
        }
        int was = before(index);
        if (position() > 5000L || was < 0) {
            seekTo(0L);
        } else {
            openAt(was, true);
        }
    }

    /** The pace of the moment: the folder's of the recording playing. */
    void speed() {
        if (player == null || !prepared) {
            return;
        }
        try {
            boolean was = player.isPlaying();
            PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(Shelf.pace(this, current()));
            /* On some phones setting the pace of a paused player starts it:
               the state is remembered, the pace set, the state restored. */
            player.setPlaybackParams(params);
            if (!was && player.isPlaying()) {
                player.pause();
            }
        } catch (Exception e) {
            Trace.note("sound: pace refused: " + e);
        }
        showNote();
    }

    void stopAll() {
        save();
        pause(true);
        release();
        prepared = false;
        index = -1;
        stopForeground(true);
        foreground = false;
        stopSelf();
        told();
    }

    private long rollback() {
        return Math.round(Shelf.rollback(this) * 1000f);
    }

    @Override
    public void onCompletion(MediaPlayer p) {
        Shelf.Item it = current();
        if (it != null) {
            Shelf.setSoundAt(this, it.key(), 0L);
            Shelf.setDone(this, it.key(), true);
        }
        resuming = false;
        if (Keep.sleepEnd(this)) {
            /* The sleep was set for the end of this one: it has come. */
            Keep.setSleep(this, 0L, Keep.sleepStep(this), false);
            Trace.note("sleep: the recording ended");
            pause(true);
            return;
        }
        int how = Keep.repeat(this);
        if (how == Keep.AGAIN_ONE) {
            seekTo(0L);
            play();
            return;
        }
        int then = after(index);
        if (then < 0 && how == Keep.AGAIN_FOLDER) {
            then = first(index);
        }
        if (then >= 0) {
            openAt(then, true);
        } else {
            pause(true);
        }
    }

    /** The first recording of the folder this one is in. */
    private int first(int i) {
        if (i < 0 || i >= list.size()) {
            return -1;
        }
        int at = i;
        while (at > 0 && list.get(at - 1).home().equals(list.get(i).home())) {
            at--;
        }
        return at;
    }

    @Override
    public boolean onError(MediaPlayer p, int what, int extra) {
        Trace.note("sound: player fell " + what + "/" + extra);
        prepared = false;
        told();
        return true;
    }

    private void save() {
        Shelf.Item it = current();
        if (it != null && prepared) {
            Shelf.setSoundAt(this, it.key(), position());
        }
    }

    /**
     * The sleep watched: when its moment comes, whatever sounds here — a
     * recording, a video's sound, a book read aloud — goes quiet, the sound
     * fading over a few seconds first, so it ends rather than stops.
     */
    private void watchSleep() {
        long at = Keep.sleepAt(this);
        if (at <= 0L || System.currentTimeMillis() < at || fading) {
            return;
        }
        if (!playing() && !(voiceOn && !voiceHeld())) {
            return;
        }
        Trace.note("sleep: the hour came");
        fade();
    }

    private boolean fading;

    /** The sound let down gently, then held, and the sleep let go. */
    private void fade() {
        fading = true;
        final int steps = 12;
        ui.post(new Runnable() {
            int step = steps;

            public void run() {
                float left = Math.max(0f, step / (float) steps);
                try {
                    if (player != null && prepared) {
                        player.setVolume(left, left);
                    }
                } catch (Exception ignored) {
                    // the sound will be held either way
                }
                if (step-- > 0) {
                    ui.postDelayed(this, 500L);
                    return;
                }
                fading = false;
                Keep.setSleep(Sound.this, 0L, Keep.sleepStep(Sound.this), false);
                if (voiceOn) {
                    voiceHold();
                } else {
                    pause(true);
                }
                try {
                    if (player != null && prepared) {
                        player.setVolume(1f, 1f);
                    }
                } catch (Exception ignored) {
                    // the next play sets it anyway
                }
                Trace.note("sleep: gone quiet");
            }
        });
    }

    private final Runnable ticker = new Runnable() {
        public void run() {
            watchSleep();
            if (voiceOn) {
                voiceKeep();
                Desk.push(Sound.this);
            }
            if (playing()) {
                save();
                state();
                /* The home screen moves with the sound: a frozen bar reads as a dead widget. */
                Desk.push(Sound.this);
            }
            ui.postDelayed(this, SAVE_EVERY);
        }
    };

    private void spoken() {
        final Ear e = ear;
        if (e != null) {
            ui.post(new Runnable() {
                public void run() {
                    e.spoke();
                }
            });
        }
    }

    private void told() {
        Desk.push(this);
        final Ear e = ear;
        if (e != null) {
            ui.post(new Runnable() {
                public void run() {
                    e.heard();
                }
            });
        }
    }

    // ------------------------------------------------------------ the voice

    /** A book handed over by the window to be read aloud: its key, name, text and where to begin. */
    private static String handKey;
    private static String handTitle;
    private static String handText;
    private static int handFrom;
    private static int handPages;

    /**
     * The window hands the whole text over before it asks for the voice. It
     * is the same process, so the text passes as a reference; an intent
     * would refuse a novel.
     */
    static void handOver(String key, String title, String text, int from, int pages) {
        handKey = key;
        handTitle = title;
        handText = text;
        handFrom = from;
        handPages = pages;
    }

    private android.speech.tts.TextToSpeech tts;
    /**
     * Holds the processor while a book is read aloud. Sound keeps the phone
     * awake only while it plays; between paragraphs the voice is still
     * computing the next one, and with the screen off the phone would fall
     * asleep right there. Timed, and renewed with every paragraph.
     */
    private PowerManager.WakeLock voiceAwake;
    private static final long VOICE_AWAKE_MS = 30 * 60 * 1000L;
    private boolean ttsReady;
    private boolean voiceOn;
    private boolean voiceHeld;
    private String voiceKey = "";
    private String voiceTitle = "";
    private String voiceText = "";
    private int voicePages;
    private final ArrayList<String> pieces = new ArrayList<String>();
    private final ArrayList<Integer> starts = new ArrayList<Integer>();
    private volatile int voiceAt;
    private volatile int voiceChar = -1;
    private volatile int firstId = -1;
    private volatile int firstBase;
    private int queuedTo;
    private int pendingFrom = -1;

    boolean voiceOn() {
        return voiceOn;
    }

    boolean voiceHeld() {
        return voiceHeld;
    }

    String voiceKey() {
        return voiceKey;
    }

    String voiceTitle() {
        return voiceTitle;
    }

    int voicePages() {
        return voicePages;
    }

    int voiceAt() {
        return voiceAt;
    }

    /** The character the voice is saying, as it reports it, or the head of its paragraph. */
    int voiceChar() {
        if (voiceChar >= 0) {
            return voiceChar;
        }
        return starts.isEmpty() ? 0 : starts.get(Math.min(voiceAt, starts.size() - 1)).intValue();
    }

    int voiceLength() {
        return voiceText.length();
    }

    ArrayList<String> voicePieces() {
        return pieces;
    }

    ArrayList<Integer> voiceStarts() {
        return starts;
    }

    String voiceSaying() {
        if (pieces.isEmpty()) {
            return "";
        }
        String t = pieces.get(Math.max(0, Math.min(voiceAt, pieces.size() - 1)));
        return t.length() > 160 ? t.substring(0, 160) : t;
    }

    /** The book handed over is cut into paragraphs, and the voice begins where it was asked to. */
    private void voiceTake() {
        if (handText == null) {
            return;
        }
        if (voiceOn) {
            voiceKeep();
        }
        if (playing()) {
            pause(true);
        }
        voiceKey = handKey == null ? "" : handKey;
        voiceTitle = handTitle == null ? "" : handTitle;
        voiceText = handText;
        voicePages = handPages;
        int from = handFrom;
        handText = null;
        pieces.clear();
        starts.clear();
        int cursor = 0;
        while (cursor < voiceText.length()) {
            int nl = voiceText.indexOf('\n', cursor);
            if (nl < 0) {
                nl = voiceText.length();
            }
            String t = voiceText.substring(cursor, nl).trim();
            if (t.length() > 0) {
                pieces.add(t);
                starts.add(Integer.valueOf(cursor));
            }
            cursor = nl + 1;
        }
        if (pieces.isEmpty()) {
            return;
        }
        voiceOn = true;
        voiceHeld = false;
        Keep.heardVoice(this, voiceKey, voiceTitle, voiceText.length(), voicePages);
        voiceFrom(from);
    }

    /**
     * Everything from one place on is handed to the voice at once, a few
     * hundred paragraphs deep, so it never pauses between them. The place is
     * a character; the voice starts at the head of the sentence that holds
     * it, since a sentence begun in the middle is not understood.
     */
    void voiceFrom(int offset) {
        if (!voiceOn || pieces.isEmpty()) {
            return;
        }
        if (tts == null || !ttsReady) {
            pendingFrom = offset;
            if (tts == null) {
                tts = new android.speech.tts.TextToSpeech(getApplicationContext(),
                    new android.speech.tts.TextToSpeech.OnInitListener() {
                        public void onInit(int status) {
                            ttsReady = status == android.speech.tts.TextToSpeech.SUCCESS;
                            ui.post(new Runnable() {
                                public void run() {
                                    if (ttsReady && pendingFrom >= 0) {
                                        int at = pendingFrom;
                                        pendingFrom = -1;
                                        voiceFrom(at);
                                    } else if (!ttsReady) {
                                        Trace.note("sound: no voice");
                                        voiceStop();
                                    }
                                }
                            });
                        }
                    });
            }
            return;
        }
        if (!takeFocus()) {
            Trace.note("sound: focus refused to the voice");
        }
        noisy(true);
        int idx = paragraphAt(offset);
        int start = sentenceStart(idx, offset, -1);
        voiceAt = idx;
        voiceChar = start;
        firstId = idx;
        firstBase = start;
        voiceHeld = false;
        voiceAwake(true);
        tts.setSpeechRate(Shelf.rate(this));
        tts.setAudioAttributes(new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        queuedTo = Math.min(pieces.size(), idx + 600) - 1;
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            public void onStart(final String id) {
                ui.post(new Runnable() {
                    public void run() {
                        int at = Integer.parseInt(id);
                        if (voiceOn && !voiceHeld) {
                            voiceAwake(true);
                        }
                        if (at != voiceAt) {
                            voiceAt = at;
                            voiceChar = starts.get(at).intValue();
                            told();
                        }
                        spoken();
                    }
                });
            }

            @Override
            public void onRangeStart(String id, int start, int end, int frame) {
                int at = Integer.parseInt(id);
                int base = at == firstId ? firstBase : starts.get(at).intValue();
                voiceChar = base + start;
                spoken();
            }

            public void onDone(final String id) {
                ui.post(new Runnable() {
                    public void run() {
                        if (voiceOn && !voiceHeld && Integer.parseInt(id) >= queuedTo) {
                            if (queuedTo + 1 < pieces.size()) {
                                /* The queue ran out before the book did: hand over the next stretch. */
                                voiceFrom(starts.get(queuedTo + 1).intValue());
                            } else {
                                voiceStop();
                            }
                        }
                    }
                });
            }

            public void onError(String id) {
                Trace.note("sound: the voice fell on piece " + id);
            }
        });
        int most = android.speech.tts.TextToSpeech.getMaxSpeechInputLength() - 16;
        for (int i = idx; i <= queuedTo; i++) {
            String t = i == idx
                ? voiceText.substring(start, Math.max(start, paragraphEnd(idx))).trim()
                : pieces.get(i);
            if (t.length() == 0) {
                t = pieces.get(i);
            }
            if (t.length() > most) {
                t = t.substring(0, most);
            }
            tts.speak(t, i == idx ? android.speech.tts.TextToSpeech.QUEUE_FLUSH
                : android.speech.tts.TextToSpeech.QUEUE_ADD, null, String.valueOf(i));
        }
        showNote();
        told();
        spoken();
    }

    private void voiceAwake(boolean on) {
        try {
            if (on) {
                if (voiceAwake == null) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    voiceAwake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mirabilia:voice");
                    voiceAwake.setReferenceCounted(false);
                }
                voiceAwake.acquire(VOICE_AWAKE_MS);
            } else if (voiceAwake != null && voiceAwake.isHeld()) {
                voiceAwake.release();
            }
        } catch (RuntimeException refused) {
            Trace.note("sound: no wake lock: " + refused);
        }
    }

    /** Held, the voice stops mid-word and waits; touched again, it goes on a few seconds earlier. */
    void voiceHold() {
        if (!voiceOn) {
            return;
        }
        if (voiceHeld) {
            voiceFrom(Math.max(0, voiceChar() - rollbackChars()));
            return;
        }
        voiceHeld = true;
        voiceAwake(false);
        if (tts != null) {
            tts.stop();
        }
        noisy(false);
        voiceKeep();
        showNote();
        told();
        spoken();
    }

    /**
     * Back fifteen seconds or on thirty. The voice gives no clock, so the
     * seconds are counted in characters at the pace of the moment — about
     * fifteen a second at the voice's own pace — and the place lands on the
     * head of a sentence; going on never moves it backwards.
     */
    void voiceJump(int seconds) {
        if (!voiceOn || pieces.isEmpty()) {
            return;
        }
        int now = voiceChar();
        int target = Math.round(now + seconds * 15f * Shelf.rate(this));
        target = Math.max(0, Math.min(voiceText.length() - 1, target));
        int idx = paragraphAt(target);
        int start = sentenceStart(idx, target, seconds > 0 ? now : -1);
        voiceMove(start);
    }

    /** A paragraph on or back. */
    void voiceStep(int by) {
        if (!voiceOn || pieces.isEmpty()) {
            return;
        }
        int idx = Math.max(0, Math.min(pieces.size() - 1, voiceAt + by));
        voiceMove(starts.get(idx).intValue());
    }

    /** To a place: speaking, the voice goes there; held, it waits there. */
    void voiceMove(int at) {
        if (voiceHeld) {
            voiceAt = paragraphAt(at);
            voiceChar = at;
            voiceKeep();
            told();
            spoken();
        } else {
            voiceFrom(at);
        }
    }

    /** A new pace takes effect from the sentence being said. */
    void voiceSpeed() {
        if (voiceOn && !voiceHeld) {
            voiceFrom(voiceChar());
        } else {
            told();
            spoken();
        }
    }

    /** The voice is put away, and its place in the book kept for another day. */
    void voiceStop() {
        if (!voiceOn) {
            return;
        }
        voiceKeep();
        voiceOn = false;
        voiceHeld = false;
        voiceAwake(false);
        if (tts != null) {
            tts.stop();
        }
        noisy(false);
        showNote();
        told();
        spoken();
    }

    private void voiceKeep() {
        if (voiceOn && voiceKey.length() > 0) {
            Shelf.setVoiceAt(this, voiceKey, voiceChar());
        }
    }

    private int rollbackChars() {
        return Math.round(Shelf.rollback(this) * 15f * Shelf.rate(this));
    }

    private void voiceDescribe() {
        if (session == null) {
            return;
        }
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, voiceTitle)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, voiceSaying())
            .build());
    }

    /** The paragraph that holds a character. */
    private int paragraphAt(int offset) {
        int low = 0;
        int high = starts.size() - 1;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (starts.get(mid).intValue() <= offset) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return Math.max(0, low);
    }

    private int paragraphEnd(int idx) {
        return idx + 1 < starts.size() ? starts.get(idx + 1).intValue() : voiceText.length();
    }

    /**
     * Where the sentence holding a character begins. Going on, a sentence
     * that began before the place the voice already was would move it
     * backwards; then the word is taken instead.
     */
    private int sentenceStart(int idx, int offset, int notBefore) {
        int from = starts.get(idx).intValue();
        int at = Math.max(from, Math.min(offset, voiceText.length()));
        int found = from;
        for (int j = at - 1; j > from; j--) {
            char c = voiceText.charAt(j);
            if (Character.isWhitespace(c)) {
                char before = voiceText.charAt(j - 1);
                if (before == '.' || before == '!' || before == '?' || before == '\u2026') {
                    found = j + 1;
                    break;
                }
            }
        }
        if (notBefore >= 0 && found <= notBefore) {
            found = at;
            while (found > from && !Character.isWhitespace(voiceText.charAt(found - 1))) {
                found--;
            }
        }
        return found;
    }

    // ------------------------------------------------------------ the phone

    private final AudioManager.OnAudioFocusChangeListener shared =
        new AudioManager.OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int change) {
                switch (change) {
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        if (voiceOn && !voiceHeld) {
                            voiceHold();
                            pausedByFocus = true;
                        } else if (playing()) {
                            pause(false);
                            pausedByFocus = true;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                        if (playing() && player != null) {
                            player.setVolume(0.3f, 0.3f);
                            ducked = true;
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_GAIN:
                        if (ducked && player != null) {
                            player.setVolume(1f, 1f);
                            ducked = false;
                        }
                        if (pausedByFocus) {
                            pausedByFocus = false;
                            if (voiceOn) {
                                if (voiceHeld) {
                                    voiceHold();
                                }
                            } else {
                                play();
                            }
                        }
                        break;
                    case AudioManager.AUDIOFOCUS_LOSS:
                        if (voiceOn && !voiceHeld) {
                            voiceHold();
                        } else {
                            pause(true);
                        }
                        break;
                    default:
                        break;
                }
            }
        };

    private boolean takeFocus() {
        if (focus == null) {
            focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setOnAudioFocusChangeListener(shared, ui)
                .setWillPauseWhenDucked(false)
                .build();
        }
        return audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    private void dropFocus() {
        if (focus != null) {
            audio.abandonAudioFocusRequest(focus);
        }
    }

    /** A headphone pulled out stops the sound rather than letting it spill into the room. */
    private final BroadcastReceiver unplugged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(i.getAction())) {
                if (voiceOn && !voiceHeld) {
                    voiceHold();
                } else {
                    pause(true);
                }
            }
        }
    };

    private void noisy(boolean on) {
        if (on == noisyOn) {
            return;
        }
        noisyOn = on;
        try {
            if (on) {
                registerReceiver(unplugged, new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
            } else {
                unregisterReceiver(unplugged);
            }
        } catch (Exception ignored) {
            // a receiver already gone is gone
        }
    }

    // ------------------------------------------------------------ the shade

    private void describe(Shelf.Item it) {
        if (session == null) {
            return;
        }
        session.setMetadata(new MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, Shelf.label(it))
            .putString(MediaMetadata.METADATA_KEY_ALBUM, it.folder == null ? "" : it.folder)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration())
            .build());
    }

    private void state() {
        if (session == null) {
            return;
        }
        session.setPlaybackState(new PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SEEK_TO
                | PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS
                | PlaybackState.ACTION_FAST_FORWARD | PlaybackState.ACTION_REWIND)
            .setState(voiceOn ? (voiceHeld ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING)
                    : (playing() ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED),
                voiceOn ? 0L : position(), voiceOn ? 0f : (playing() ? Shelf.pace(this, current()) : 0f))
            .build());
    }

    /**
     * The notification: the recording's name, and three keys drawn with the
     * application's own marks — fifteen back, hold or go on, thirty on. It
     * holds the service in front while the sound plays, and lets go of it,
     * without disappearing, while it is held.
     */
    private void showNote() {
        Shelf.Item it = current();
        if (voiceOn) {
            voiceDescribe();
        } else if (it != null) {
            describe(it);
        }
        state();
        /* The note leads where the widget's words lead: to the book read
           aloud, to the video whose sound this is, or to the recordings. */
        Intent back = new Intent(this, Main.class);
        back.setAction(Main.ROOM_DOOR);
        boolean film = !voiceOn && it != null && it.mime != null && it.mime.startsWith("video/");
        back.putExtra("room", voiceOn ? 3 : (film ? 1 : 2));
        if (voiceOn) {
            back.putExtra("book", voiceKey);
        } else if (film) {
            back.putExtra("film", it.key());
        }
        back.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(this, 0, back,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean on = voiceOn ? !voiceHeld : (playing() || wanted);
        Notification.Builder note = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mono)
            .setContentTitle(voiceOn ? voiceTitle : (it == null ? Words.s("music") : Shelf.label(it)))
            .setContentText(voiceOn ? voiceSaying() : (it == null || it.folder == null ? "" : it.folder))
            .setContentIntent(open)
            .setOngoing(on)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .addAction(key(Glyph.REWIND, BACK, Words.s("voice_back")))
            .addAction(key(on ? Glyph.PAUSE : Glyph.PLAY, TOGGLE,
                Words.s(on ? "voice_hold" : "voice_go")))
            .addAction(key(Glyph.SKIP, ON, Words.s("voice_on")))
            .setStyle(new Notification.MediaStyle()
                .setMediaSession(session == null ? null : session.getSessionToken())
                .setShowActionsInCompactView(0, 1, 2));
        Notification made = note.build();
        try {
            if (on || !foreground) {
                startForeground(NOTE, made);
                foreground = true;
            } else {
                NotificationManager notes = getSystemService(NotificationManager.class);
                if (notes != null) {
                    notes.notify(NOTE, made);
                }
            }
            if (!on && foreground && (it != null || voiceOn)) {
                stopForeground(STOP_FOREGROUND_DETACH);
                foreground = false;
            }
        } catch (Exception refused) {
            Trace.note("sound: the shade refused: " + refused);
        }
    }

    private Notification.Action key(int kind, String act, String name) {
        Intent i = new Intent(this, Sound.class).setAction(act);
        PendingIntent pi = PendingIntent.getService(this, kind, i,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(Icon.createWithBitmap(mark(kind)), name, pi).build();
    }

    /** One of the application's marks, drawn white on nothing, for the notification to tint. */
    private static Bitmap mark(int kind) {
        int side = 96;
        Bitmap made = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(made);
        Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint solid = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint cut = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = side * 0.8f / 24f;
        Glyph.pens(pen, solid, unit);
        pen.setColor(0xFFFFFFFF);
        solid.setColor(0xFFFFFFFF);
        cut.setColor(0x00000000);
        Glyph.mark(canvas, kind, side / 2f, side / 2f, unit, pen, solid, cut);
        return made;
    }
}
