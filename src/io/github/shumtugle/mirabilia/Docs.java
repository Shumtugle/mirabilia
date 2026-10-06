package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/* What the reader accepts, sorted by how much work the file demands.

   Plain: already text, only the encoding may differ.
   Wrapped: a zip with xml inside — docx and odt open by hand, no library.
   Marked: html and rtf, where the markup is stripped off the sentence.
   Refused: formats needing a decoder we have no way to obtain here. */
final class Docs {

    private Docs() {
    }

    private static final String[] PLAIN = {
            ".txt", ".md", ".markdown", ".log", ".ini", ".cfg", ".conf",
            ".yaml", ".yml", ".toml", ".properties", ".json", ".csv", ".tsv",
            ".srt", ".vtt", ".ass", ".ssa", ".tex", ".xml"
    };
    private static final String[] MARKED = {".html", ".htm", ".xhtml", ".rtf"};
    private static final String[] WRAPPED = {".docx", ".odt"};
    private static final String[] BOOKS = {".epub", ".fb2", ".pdf", ".cbz"};

    /* Named plainly so the answer to "why does this file not open" is one
       list and not a hunt through three of them. */
    static final String[] REFUSED = {
            ".cbr", ".cb7", ".chm", ".djvu", ".djv", ".kfx", ".doc",
            ".mobi", ".azw", ".azw3"
    };

    static boolean has(String name, String[] set) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase();
        for (int i = 0; i < set.length; i++) {
            if (n.endsWith(set[i])) {
                return true;
            }
        }
        return false;
    }

    static boolean isPlain(String n) {
        return has(n, PLAIN);
    }

    static boolean isMarked(String n) {
        return has(n, MARKED);
    }

    static boolean isWrapped(String n) {
        return has(n, WRAPPED);
    }

    static boolean readable(String n) {
        return isPlain(n) || isMarked(n) || isWrapped(n) || has(n, BOOKS);
    }

    static boolean refused(String n) {
        return has(n, REFUSED);
    }

    /* docx and odt are both zips holding one xml of interest; only the entry
       name and the paragraph tag differ. */
    static String wrapped(Context c, Uri doc, String name) {
        boolean word = name.toLowerCase().endsWith(".docx");
        String want = word ? "word/document.xml" : "content.xml";
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(doc);
            if (in == null) {
                return null;
            }
            ZipInputStream z = new ZipInputStream(in);
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!e.getName().equals(want)) {
                    z.closeEntry();
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[32768];
                int k;
                while ((k = z.read(buf)) > 0) {
                    out.write(buf, 0, k);
                }
                String xml = new String(out.toByteArray(), "UTF-8");
                String para = word ? "w:p" : "text:p";
                xml = xml.replaceAll("(?i)</" + para + ">", "\n");
                xml = xml.replaceAll("(?i)<w:br[^>]*>", "\n");
                return clean(xml);
            }
            Trace.note("epub: " + "entry missing: " + want);
            return null;
        } catch (Exception ex) {
            Trace.note("epub: " + String.valueOf(ex));
            return null;
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    /* rtf carries its text between control words; the escapes matter more
       than the tags, since Cyrillic arrives as \\'e0 byte pairs. */
    static String rtf(String raw) {
        if (raw == null) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        int i = 0;
        int skip = 0;
        while (i < raw.length()) {
            char ch = raw.charAt(i);
            if (ch == '\\') {
                if (i + 1 < raw.length() && raw.charAt(i + 1) == '\'') {
                    try {
                        bytes.write(Integer.parseInt(raw.substring(i + 2, i + 4), 16));
                    } catch (Exception ignored) {
                    }
                    i += 4;
                    continue;
                }
                int j = i + 1;
                StringBuilder word = new StringBuilder();
                while (j < raw.length() && Character.isLetter(raw.charAt(j))) {
                    word.append(raw.charAt(j));
                    j++;
                }
                StringBuilder num = new StringBuilder();
                while (j < raw.length()
                        && (Character.isDigit(raw.charAt(j)) || raw.charAt(j) == '-')) {
                    num.append(raw.charAt(j));
                    j++;
                }
                if (j < raw.length() && raw.charAt(j) == ' ') {
                    j++;
                }
                String w = word.toString();
                flush(bytes, out);
                if ("par".equals(w) || "line".equals(w) || "pard".equals(w)) {
                    out.append('\n');
                } else if ("u".equals(w) && num.length() > 0) {
                    try {
                        int cp = Integer.parseInt(num.toString());
                        out.append((char) (cp < 0 ? cp + 65536 : cp));
                    } catch (Exception ignored) {
                    }
                } else if ("fonttbl".equals(w) || "colortbl".equals(w)
                        || "stylesheet".equals(w) || "info".equals(w)
                        || "pict".equals(w)) {
                    skip = 1;
                }
                i = j;
                continue;
            }
            if (ch == '{') {
                i++;
                continue;
            }
            if (ch == '}') {
                skip = 0;
                i++;
                continue;
            }
            if (skip == 0 && ch != '\r' && ch != '\n') {
                bytes.write(ch);
            }
            i++;
        }
        flush(bytes, out);
        return out.toString().replaceAll("\n{3,}", "\n\n").trim();
    }

    private static void flush(ByteArrayOutputStream b, StringBuilder out) {
        if (b.size() == 0) {
            return;
        }
        try {
            /* Legacy rtf from this part of the world is nearly always cp1251;
               utf-8 would turn every second letter into a question mark. */
            out.append(new String(b.toByteArray(), "windows-1251"));
        } catch (Exception e) {
            out.append(new String(b.toByteArray()));
        }
        b.reset();
    }

    static String clean(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replaceAll("(?s)<[^>]+>", "");
        t = t.replace("&nbsp;", " ").replace("&mdash;", "\u2014")
                .replace("&ndash;", "\u2013").replace("&laquo;", "\u00ab")
                .replace("&raquo;", "\u00bb").replace("&hellip;", "\u2026")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"");
        t = t.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        t = t.replaceAll("\n{3,}", "\n\n");
        return t.trim();
    }
}
