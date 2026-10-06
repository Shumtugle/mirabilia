package io.github.shumtugle.mirabilia;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * A recording in the commonest compressed form of all, read as what it is:
 * a tag at its head, and then frames, each a small fixed slice of sound with
 * its own four-byte header saying how long it is.
 *
 * A piece of it is cut by copying whole frames, byte for byte: nothing is
 * decoded and nothing is encoded again, so nothing is lost and no codec is
 * needed. The first frame of a file may be no sound at all but a table of
 * the whole file's frames; it is left out of a piece, since it would tell
 * a player the length of the whole. A frame may borrow a few bytes from the
 * frames before it, so the first few milliseconds of a piece can be quieter
 * than they were; that is the price of not decoding, and every cutter of
 * this kind pays it.
 *
 * The tag is read and written again in its own version, the frames not
 * edited here — a cover, a comment, lyrics — carried over as they were.
 * Text in the oldest encoding is often, in this part of the world, not
 * Latin at all but Cyrillic in the old eight-bit table, and is read so when
 * its bytes say it must be.
 */
final class Mp3 {

    private Mp3() {
    }

    private static final int[] BITRATE_1 = {0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, -1};
    private static final int[] BITRATE_2 = {0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160, -1};
    private static final int[][] RATE = {{11025, 12000, 8000}, {0, 0, 0}, {22050, 24000, 16000},
        {44100, 48000, 32000}};

    /** One frame's header, read: how long the frame is, how much sound it holds, and at what rate. */
    static final class Head {
        int length;
        int samples;
        int rate;
        int kbps;
        boolean mpeg1;
        boolean mono;
    }

    /** A header from four bytes, or null when they are not the head of a frame of this kind. */
    static Head head(int b0, int b1, int b2, int b3) {
        if (b0 != 0xFF || (b1 & 0xE0) != 0xE0) {
            return null;
        }
        int version = (b1 >> 3) & 3;
        int layer = (b1 >> 1) & 3;
        if (version == 1 || layer != 1) {
            return null;
        }
        int bi = (b2 >> 4) & 15;
        int ri = (b2 >> 2) & 3;
        if (bi == 0 || bi == 15 || ri == 3) {
            return null;
        }
        Head h = new Head();
        h.mpeg1 = version == 3;
        h.kbps = h.mpeg1 ? BITRATE_1[bi] : BITRATE_2[bi];
        h.rate = RATE[version][ri];
        int pad = (b2 >> 1) & 1;
        h.samples = h.mpeg1 ? 1152 : 576;
        h.length = (h.mpeg1 ? 144000 : 72000) * h.kbps / h.rate + pad;
        h.mono = ((b3 >> 6) & 3) == 3;
        return h.length > 4 ? h : null;
    }

    /** Whether a frame is the table of all the frames rather than sound: "Xing", "Info" or "VBRI". */
    private static boolean table(byte[] body, Head h) {
        int at = h.mpeg1 ? (h.mono ? 17 : 32) : (h.mono ? 9 : 17);
        return is(body, at, "Xing") || is(body, at, "Info") || is(body, 32, "VBRI");
    }

    private static boolean is(byte[] b, int at, String word) {
        if (at + 4 > b.length) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (b[at + i] != (byte) word.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** What the frames say about the whole: where the sound starts, its rate, and the length of a frame. */
    static final class Shape {
        long start;
        int rate;
        int samples;
        int kbps;

        float frameMs() {
            return rate > 0 ? samples * 1000f / rate : 26.122f;
        }
    }

    /**
     * The bytes that hold the frames beginning between two moments, as
     * {from, to}; the moments in milliseconds from the start of the sound.
     */
    static long[] span(InputStream raw, long fromMs, long toMs, Shape shape) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 1 << 16);
        long pos = skipTag(in);
        shape.start = pos;
        long samples = 0L;
        long from = -1L;
        long to = -1L;
        boolean first = true;
        int[] b = new int[4];
        while (true) {
            in.mark(8);
            if (!read4(in, b)) {
                break;
            }
            Head h = head(b[0], b[1], b[2], b[3]);
            if (h == null) {
                in.reset();
                if (in.skip(1) < 1) {
                    break;
                }
                pos += 1;
                continue;
            }
            if (first) {
                first = false;
                shape.rate = h.rate;
                shape.samples = h.samples;
                shape.kbps = h.kbps;
                in.mark(64);
                byte[] body = new byte[Math.min(40, h.length - 4)];
                int got = readFully(in, body);
                in.reset();
                if (got == body.length && table(body, h)) {
                    skipFully(in, h.length - 4);
                    pos += h.length;
                    continue;
                }
            }
            long ms = samples * 1000L / h.rate;
            if (from < 0L && ms >= fromMs) {
                from = pos;
            }
            if (ms >= toMs) {
                to = pos;
                break;
            }
            samples += h.samples;
            if (!skipFully(in, h.length - 4)) {
                pos += h.length;
                break;
            }
            pos += h.length;
        }
        if (from < 0L) {
            from = pos;
        }
        if (to < 0L) {
            to = pos;
        }
        return new long[] {from, to};
    }

    /** The first frame's shape, for the length of a frame, read without walking the rest. */
    static Shape shape(InputStream raw) throws IOException {
        Shape s = new Shape();
        span(raw, 0L, 0L, s);
        return s;
    }

    // ------------------------------------------------------------ the tag

    /** The texts a tag is edited for here. */
    static final String TITLE = "TIT2";
    static final String ARTIST = "TPE1";
    static final String ALBUM = "TALB";
    static final String TRACK = "TRCK";
    static final String YEAR = "YEAR";

    /** A tag read: its version, the texts edited here, every other frame as it was, and where the sound starts. */
    static final class Tag {
        int major = 3;
        final LinkedHashMap<String, String> texts = new LinkedHashMap<String, String>();
        final ArrayList<byte[]> others = new ArrayList<byte[]>();
        long end;

        /**
         * A new front cover: every picture the tag held goes, and this one
         * stands in their place, as a JPEG with no words of its own.
         */
        void cover(byte[] jpeg) {
            for (int i = others.size() - 1; i >= 0; i--) {
                byte[] f = others.get(i);
                if (f.length >= 4 && f[0] == 'A' && f[1] == 'P' && f[2] == 'I' && f[3] == 'C') {
                    others.remove(i);
                }
            }
            byte[] mime = {'i', 'm', 'a', 'g', 'e', '/', 'j', 'p', 'e', 'g'};
            int len = 1 + mime.length + 1 + 1 + 1 + jpeg.length;
            byte[] f = new byte[10 + len];
            f[0] = 'A';
            f[1] = 'P';
            f[2] = 'I';
            f[3] = 'C';
            byte[] size = major == 4 ? syncsafe4(len) : be4(len);
            System.arraycopy(size, 0, f, 4, 4);
            int at = 10;
            f[at++] = 0;
            System.arraycopy(mime, 0, f, at, mime.length);
            at += mime.length;
            f[at++] = 0;
            f[at++] = 3;
            f[at++] = 0;
            System.arraycopy(jpeg, 0, f, at, jpeg.length);
            others.add(0, f);
        }
    }

    /** Past a tag at the head, if there is one; answers where the sound starts. */
    private static long skipTag(BufferedInputStream in) throws IOException {
        in.mark(16);
        byte[] h = new byte[10];
        if (readFully(in, h) < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') {
            in.reset();
            return 0L;
        }
        long size = syncsafe(h, 6);
        long total = 10L + size + ((h[5] & 0x10) != 0 ? 10L : 0L);
        skipFully(in, total - 10L);
        return total;
    }

    static Tag readTag(InputStream raw) throws IOException {
        BufferedInputStream in = new BufferedInputStream(raw, 1 << 16);
        Tag t = new Tag();
        byte[] h = new byte[10];
        if (readFully(in, h) < 10 || h[0] != 'I' || h[1] != 'D' || h[2] != '3') {
            t.end = 0L;
            return t;
        }
        int major = h[3];
        int flags = h[5] & 0xFF;
        long size = syncsafe(h, 6);
        t.end = 10L + size + ((flags & 0x10) != 0 ? 10L : 0L);
        byte[] body = new byte[(int) Math.min(size, 64L * 1024L * 1024L)];
        readFully(in, body);
        if ((major != 3 && major != 4) || (flags & 0x80) != 0) {
            /* An older or unsynchronised tag is not taken apart: a new one is written in its place. */
            return t;
        }
        t.major = major;
        int at = 0;
        if ((flags & 0x40) != 0 && body.length >= 4) {
            int ext = major == 4 ? (int) syncsafe(body, 0) : (int) be32(body, 0) + 4;
            at = Math.max(0, Math.min(body.length, ext));
        }
        while (at + 10 <= body.length) {
            if (body[at] == 0) {
                break;
            }
            String id = new String(body, at, 4, Charset.forName("ISO-8859-1"));
            long len = major == 4 ? syncsafe(body, at + 4) : be32(body, at + 4);
            if (len < 0 || at + 10 + len > body.length) {
                break;
            }
            int dataAt = at + 10;
            String key = id.equals("TYER") || id.equals("TDRC") ? YEAR : id;
            if (key.equals(TITLE) || key.equals(ARTIST) || key.equals(ALBUM) || key.equals(TRACK)
                || key.equals(YEAR)) {
                t.texts.put(key, text(body, dataAt, (int) len));
            } else {
                byte[] frame = new byte[10 + (int) len];
                System.arraycopy(body, at, frame, 0, frame.length);
                t.others.add(frame);
            }
            at = dataAt + (int) len;
        }
        return t;
    }

    /** A tag of the same version with the texts given, every other frame carried over. */
    static byte[] writeTag(Tag old, LinkedHashMap<String, String> texts) throws IOException {
        int major = old.major == 4 ? 4 : 3;
        ByteArrayOutputStream frames = new ByteArrayOutputStream();
        String[] keys = {TITLE, ARTIST, ALBUM, TRACK, YEAR};
        for (int i = 0; i < keys.length; i++) {
            String v = texts.get(keys[i]);
            if (v == null || v.trim().length() == 0) {
                continue;
            }
            String id = keys[i].equals(YEAR) ? (major == 4 ? "TDRC" : "TYER") : keys[i];
            byte[] data;
            if (major == 4) {
                byte[] u = v.trim().getBytes("UTF-8");
                data = new byte[1 + u.length];
                data[0] = 3;
                System.arraycopy(u, 0, data, 1, u.length);
            } else {
                byte[] u = v.trim().getBytes("UTF-16LE");
                data = new byte[3 + u.length];
                data[0] = 1;
                data[1] = (byte) 0xFF;
                data[2] = (byte) 0xFE;
                System.arraycopy(u, 0, data, 3, u.length);
            }
            frames.write(id.getBytes("ISO-8859-1"));
            frames.write(major == 4 ? syncsafe4(data.length) : be4(data.length));
            frames.write(0);
            frames.write(0);
            frames.write(data);
        }
        for (int i = 0; i < old.others.size(); i++) {
            frames.write(old.others.get(i));
        }
        byte[] all = frames.toByteArray();
        int padding = 512;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {'I', 'D', '3', (byte) major, 0, 0});
        out.write(syncsafe4(all.length + padding));
        out.write(all);
        out.write(new byte[padding]);
        return out.toByteArray();
    }

    /** A text frame's words, whichever of the four encodings it was written in. */
    private static String text(byte[] b, int at, int len) {
        if (len < 1) {
            return "";
        }
        int enc = b[at];
        try {
            String s;
            if (enc == 0) {
                /* The oldest encoding: Latin by the book, but Cyrillic in the old table
                   when its bytes lie where Cyrillic letters do. */
                boolean cyrillic = false;
                for (int i = at + 1; i < at + len; i++) {
                    int v = b[i] & 0xFF;
                    if (v >= 0xC0) {
                        cyrillic = true;
                        break;
                    }
                }
                s = new String(b, at + 1, len - 1, cyrillic ? "windows-1251" : "ISO-8859-1");
            } else if (enc == 1) {
                s = new String(b, at + 1, len - 1, "UTF-16");
            } else if (enc == 2) {
                s = new String(b, at + 1, len - 1, "UTF-16BE");
            } else {
                s = new String(b, at + 1, len - 1, "UTF-8");
            }
            int zero = s.indexOf('\u0000');
            return (zero >= 0 ? s.substring(0, zero) : s).trim();
        } catch (Exception unread) {
            return "";
        }
    }

    // ------------------------------------------------------------ bytes

    /** A tag, then the bytes of the source from one place to another. */
    static void pour(byte[] tag, InputStream source, long from, long to, OutputStream out) throws IOException {
        out.write(tag);
        BufferedInputStream in = new BufferedInputStream(source, 1 << 16);
        skipFully(in, from);
        byte[] buf = new byte[1 << 16];
        long left = to < 0L ? Long.MAX_VALUE : to - from;
        while (left > 0L) {
            int got = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (got <= 0) {
                break;
            }
            out.write(buf, 0, got);
            left -= got;
        }
        out.flush();
    }

    private static boolean read4(InputStream in, int[] b) throws IOException {
        for (int i = 0; i < 4; i++) {
            int v = in.read();
            if (v < 0) {
                return false;
            }
            b[i] = v;
        }
        return true;
    }

    private static int readFully(InputStream in, byte[] b) throws IOException {
        int got = 0;
        while (got < b.length) {
            int n = in.read(b, got, b.length - got);
            if (n <= 0) {
                break;
            }
            got += n;
        }
        return got;
    }

    private static boolean skipFully(InputStream in, long n) throws IOException {
        while (n > 0L) {
            long s = in.skip(n);
            if (s <= 0L) {
                if (in.read() < 0) {
                    return false;
                }
                s = 1L;
            }
            n -= s;
        }
        return true;
    }

    private static long syncsafe(byte[] b, int at) {
        return ((b[at] & 0x7FL) << 21) | ((b[at + 1] & 0x7FL) << 14) | ((b[at + 2] & 0x7FL) << 7)
            | (b[at + 3] & 0x7FL);
    }

    private static long be32(byte[] b, int at) {
        return ((b[at] & 0xFFL) << 24) | ((b[at + 1] & 0xFFL) << 16) | ((b[at + 2] & 0xFFL) << 8)
            | (b[at + 3] & 0xFFL);
    }

    private static byte[] syncsafe4(int v) {
        return new byte[] {(byte) ((v >> 21) & 0x7F), (byte) ((v >> 14) & 0x7F), (byte) ((v >> 7) & 0x7F),
            (byte) (v & 0x7F)};
    }

    private static byte[] be4(int v) {
        return new byte[] {(byte) (v >> 24), (byte) (v >> 16), (byte) (v >> 8), (byte) v};
    }
}
