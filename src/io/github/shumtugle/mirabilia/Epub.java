package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/* epub is a zip of xhtml with a manifest, so it needs no library — only
   patience. The stream cannot seek, so everything readable is taken in one
   pass and put in order afterwards, when the spine is known. */
final class Epub {

    private static final int CAP = 6 * 1024 * 1024;

    private Epub() {
    }

    static String read(Context c, Uri doc) {
        HashMap<String, String> files = new HashMap<String, String>();
        InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(doc);
            if (in == null) {
                return null;
            }
            ZipInputStream z = new ZipInputStream(in);
            ZipEntry e;
            int total = 0;
            byte[] buf = new byte[32768];
            while ((e = z.getNextEntry()) != null) {
                String n = e.getName().toLowerCase();
                boolean wanted = n.endsWith(".xhtml") || n.endsWith(".html")
                        || n.endsWith(".htm") || n.endsWith(".opf")
                        || n.endsWith("container.xml");
                if (!wanted || total > CAP) {
                    z.closeEntry();
                    continue;
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int k;
                while ((k = z.read(buf)) > 0) {
                    out.write(buf, 0, k);
                    total += k;
                    if (total > CAP) {
                        break;
                    }
                }
                files.put(e.getName(), new String(out.toByteArray(), "UTF-8"));
                z.closeEntry();
            }
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

        if (files.isEmpty()) {
            Trace.note("epub: " + "no readable entries");
            return null;
        }

        /* Reading order comes from the spine; without it the chapters arrive
           in whatever order the zip happens to hold them. */
        ArrayList<String> order = new ArrayList<String>();
        String opfName = null;
        for (String k : files.keySet()) {
            if (k.toLowerCase().endsWith(".opf")) {
                opfName = k;
                break;
            }
        }
        if (opfName != null) {
            String opf = files.get(opfName);
            HashMap<String, String> byId = new HashMap<String, String>();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("<item\\s[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(opf);
            while (m.find()) {
                String tag = m.group();
                String id = attr(tag, "id");
                String href = attr(tag, "href");
                if (id != null && href != null) {
                    byId.put(id, href);
                }
            }
            java.util.regex.Matcher r = java.util.regex.Pattern
                    .compile("<itemref\\s[^>]*>", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(opf);
            String base = opfName.contains("/")
                    ? opfName.substring(0, opfName.lastIndexOf('/') + 1) : "";
            while (r.find()) {
                String id = attr(r.group(), "idref");
                String href = id == null ? null : byId.get(id);
                if (href == null) {
                    continue;
                }
                String full = base + href;
                if (files.containsKey(full)) {
                    order.add(full);
                } else if (files.containsKey(href)) {
                    order.add(href);
                }
            }
        }
        if (order.isEmpty()) {
            for (String k : files.keySet()) {
                String n = k.toLowerCase();
                if (n.endsWith(".xhtml") || n.endsWith(".html") || n.endsWith(".htm")) {
                    order.add(k);
                }
            }
            java.util.Collections.sort(order);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < order.size(); i++) {
            sb.append(strip(files.get(order.get(i))));
            sb.append("\n\n");
        }
        return sb.toString();
    }

    private static String attr(String tag, String name) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(name + "\\s*=\\s*\"([^\"]*)\"",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(tag);
        return m.find() ? m.group(1) : null;
    }

    /* Paragraph breaks are kept, everything else in the markup is thrown
       away: the reader shows the sentence, not the typesetting. */
    private static String strip(String html) {
        if (html == null) {
            return "";
        }
        String s = html.replaceAll("(?is)<(script|style|head)[^>]*>.*?</\\1>", " ");
        s = s.replaceAll("(?i)</(p|div|h[1-6]|li|br)\\s*>", "\n");
        s = s.replaceAll("(?i)<br\\s*/?>", "\n");
        s = s.replaceAll("(?s)<[^>]+>", "");
        s = s.replace("&nbsp;", " ").replace("&mdash;", "\u2014")
                .replace("&ndash;", "\u2013").replace("&laquo;", "\u00ab")
                .replace("&raquo;", "\u00bb").replace("&hellip;", "\u2026")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#8212;", "\u2014");
        s = s.replaceAll("[ \\t\\x0B\\f\\r]+", " ");
        s = s.replaceAll("\n{3,}", "\n\n");
        return s.trim();
    }
}
