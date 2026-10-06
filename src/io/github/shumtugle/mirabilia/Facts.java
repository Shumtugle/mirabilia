package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.util.Locale;

/**
 * What a picture says about itself: when it was taken, with what, how, how
 * large it is, and where — read from the marks the camera wrote into the
 * file and from the phone's index, nothing guessed. A line that has nothing
 * to say is left empty and is not shown.
 *
 * Reading opens the file, so it is done away from the screen, once for each
 * picture while the window lives.
 */
final class Facts {

    /** When, as the camera wrote it, or as the index has it. */
    String taken = "";
    /** The maker and the model, the maker not said twice. */
    String camera = "";
    /** The model alone, for the short line. */
    String model = "";
    /** The lens by its name, and the focal length as a full frame camera would name it. */
    String lens = "";
    /** Time, aperture and sensitivity. */
    String exposure = "";
    /** Pixels across and down, as the picture stands. */
    int width;
    int height;
    /** Size: megapixels, pixels, and the weight of the file. */
    String size = "";
    /** Where it was taken, if the camera wrote it down. */
    String where = "";
    /** Where it lies on the phone. */
    String lies = "";

    private static final java.util.HashMap<Long, Facts> READ = new java.util.HashMap<Long, Facts>();

    /** Facts already read for this picture, or null. */
    static synchronized Facts known(Gallery.Photo p) {
        return READ.get(p.id);
    }

    /** Reads the facts of a picture; slow, so never on the screen's own thread. */
    static Facts read(Context c, Gallery.Photo p) {
        Facts known = known(p);
        if (known != null) {
            return known;
        }
        Facts f = new Facts();
        f.taken = Gallery.day(p.taken);
        f.lies = lies(p.place);
        ExifInterface exif = null;
        ParcelFileDescriptor pfd = null;
        try {
            /* With the leave for every file the file itself is read, and with
               it the place it was taken; through the index that place is
               kept back from anyone who has not asked for it. */
            File file = Shelf.allFiles() ? Change.fileOf(p.place) : null;
            pfd = file != null && file.isFile()
                ? ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                : c.getContentResolver().openFileDescriptor(p.uri, "r");
            if (pfd != null) {
                exif = new ExifInterface(pfd.getFileDescriptor());
            }
        } catch (Exception unread) {
            Trace.note("facts: marks unread: " + unread.getClass().getSimpleName());
        } finally {
            close(pfd);
        }
        int turn = p.turn;
        if (exif != null) {
            String when = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
            if (when == null) {
                when = exif.getAttribute(ExifInterface.TAG_DATETIME);
            }
            String day = day(when);
            if (day.length() > 0) {
                f.taken = day;
            }
            String make = clean(exif.getAttribute(ExifInterface.TAG_MAKE));
            f.model = clean(exif.getAttribute(ExifInterface.TAG_MODEL));
            f.camera = f.model.toLowerCase(Locale.ROOT).startsWith(make.toLowerCase(Locale.ROOT))
                ? f.model : (make + " " + f.model).trim();
            f.lens = lens(exif);
            f.exposure = exposure(exif);
            f.width = exif.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0);
            f.height = exif.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0);
            if (f.width <= 0 || f.height <= 0) {
                f.width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                f.height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
            }
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            if (o == ExifInterface.ORIENTATION_ROTATE_90 || o == ExifInterface.ORIENTATION_ROTATE_270
                || o == ExifInterface.ORIENTATION_TRANSPOSE || o == ExifInterface.ORIENTATION_TRANSVERSE) {
                turn = 90;
            }
            float[] at = new float[2];
            if (exif.getLatLong(at) && (at[0] != 0f || at[1] != 0f)) {
                f.where = String.format(Locale.ROOT, "%.5f, %.5f", at[0], at[1]);
            }
        }
        if (f.width <= 0 || f.height <= 0) {
            bounds(c, p, f);
        }
        if (turn == 90 || turn == 270) {
            int w = f.width;
            f.width = f.height;
            f.height = w;
        }
        f.size = size(f.width, f.height, weight(c, p));
        synchronized (Facts.class) {
            READ.put(p.id, f);
        }
        return f;
    }

    /** The short line under the name: when, with what, how large. */
    String brief() {
        StringBuilder b = new StringBuilder(taken);
        if (model.length() > 0) {
            b.append(DOT).append(model);
        }
        if (width > 0 && height > 0) {
            b.append(DOT).append(width).append('\u00D7').append(height);
        }
        return b.toString();
    }

    static final String DOT = "  \u00B7  ";

    // ------------------------------------------------------------ reading

    /** "2026:05:12 14:30:22" as the rest of the application writes a day. */
    private static String day(String exif) {
        if (exif == null || exif.length() < 16) {
            return "";
        }
        try {
            String y = exif.substring(0, 4);
            String mo = exif.substring(5, 7);
            String d = exif.substring(8, 10);
            String hm = exif.substring(11, 16);
            if (!y.matches("\\d{4}") || y.equals("0000")) {
                return "";
            }
            return d + "." + mo + "." + y + "  " + hm;
        } catch (Exception odd) {
            return "";
        }
    }

    /** The lens's own tag, read by its name: the platform knows it but does not name it. */
    private static final String LENS_MODEL = "LensModel";

    private static String lens(ExifInterface exif) {
        String name = clean(exif.getAttribute(LENS_MODEL));
        int full = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0);
        double real = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0d);
        String focal = "";
        if (full > 0) {
            focal = Words.s("mm_n").replace("{n}", String.valueOf(full));
        } else if (real > 0d) {
            focal = Words.s("mm_n").replace("{n}", one(real));
        }
        if (name.length() == 0) {
            return focal;
        }
        return focal.length() == 0 ? name : name + DOT + focal;
    }

    private static String exposure(ExifInterface exif) {
        StringBuilder b = new StringBuilder();
        double t = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0d);
        if (t > 0d) {
            b.append(t >= 1d ? one(t) + "\u2033" : "1/" + Math.round(1d / t));
        }
        double f = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0d);
        if (f > 0d) {
            if (b.length() > 0) {
                b.append(DOT);
            }
            /* The aperture is written with a point everywhere; it is a name, not a sum. */
            b.append("f/").append(String.format(Locale.ROOT, f == Math.floor(f) ? "%.0f" : "%.1f", f));
        }
        int iso = exif.getAttributeInt(ExifInterface.TAG_ISO_SPEED_RATINGS, 0);
        if (iso > 0) {
            if (b.length() > 0) {
                b.append(DOT);
            }
            b.append("ISO ").append(iso);
        }
        return b.toString();
    }

    /** Pixels read from the head of the picture, when its marks do not say. */
    private static void bounds(Context c, Gallery.Photo p, Facts f) {
        java.io.InputStream in = null;
        try {
            in = c.getContentResolver().openInputStream(p.uri);
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(in, null, o);
            f.width = Math.max(0, o.outWidth);
            f.height = Math.max(0, o.outHeight);
        } catch (Exception unread) {
            Trace.note("facts: size unread: " + unread.getClass().getSimpleName());
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                    // nothing was written; closing cannot lose anything
                }
            }
        }
    }

    private static long weight(Context c, Gallery.Photo p) {
        Cursor q = null;
        try {
            q = c.getContentResolver().query(p.uri, new String[] {OpenableColumns.SIZE}, null, null, null);
            if (q != null && q.moveToFirst() && !q.isNull(0)) {
                return q.getLong(0);
            }
        } catch (Exception unread) {
            Trace.note("facts: weight unread: " + unread.getClass().getSimpleName());
        } finally {
            if (q != null) {
                q.close();
            }
        }
        return 0L;
    }

    private static String size(int w, int h, long bytes) {
        StringBuilder b = new StringBuilder();
        if (w > 0 && h > 0) {
            b.append(Words.s("mp_n").replace("{n}", one(w * (double) h / 1000000d)));
            b.append(DOT).append(w).append(" \u00D7 ").append(h);
        }
        if (bytes > 0L) {
            if (b.length() > 0) {
                b.append(DOT);
            }
            b.append(bytes >= 1024L * 1024L
                ? Words.s("mb_n").replace("{n}", one(bytes / (1024d * 1024d)))
                : Words.s("kb_n").replace("{n}", String.valueOf(Math.max(1L, bytes / 1024L))));
        }
        return b.toString();
    }

    /**
     * Where a picture lies: the phone's own storage or a card, and the
     * folders on the way to it, the last three at most.
     */
    private static String lies(String place) {
        if (place == null || place.length() == 0) {
            return "";
        }
        int colon = place.indexOf(':');
        String volume = colon >= 0 ? place.substring(0, colon) : "";
        String path = colon >= 0 ? place.substring(colon + 1) : place;
        int slash = path.lastIndexOf('/');
        path = slash >= 0 ? path.substring(0, slash) : "";
        String kind = "primary".equals(volume) ? Words.s("on_phone")
            : (volume.matches("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}") ? Words.s("on_card") : "");
        String[] steps = path.split("/");
        StringBuilder b = new StringBuilder();
        int from = Math.max(0, steps.length - 3);
        if (from > 0) {
            b.append('\u2026');
        }
        for (int i = from; i < steps.length; i++) {
            if (steps[i].length() == 0) {
                continue;
            }
            if (b.length() > 0) {
                b.append("  \u203A  ");
            }
            b.append(steps[i]);
        }
        if (kind.length() == 0) {
            return b.toString();
        }
        return b.length() == 0 ? kind : kind + DOT + b;
    }

    /** A number with one place after the point, the point as the phone writes it, and none when whole. */
    private static String one(double v) {
        double r = Math.round(v * 10d) / 10d;
        if (r == Math.floor(r)) {
            return String.valueOf((long) r);
        }
        return String.format(Locale.getDefault(), "%.1f", r);
    }

    private static String clean(String s) {
        return s == null ? "" : s.replace('\u0000', ' ').trim();
    }

    private static void close(ParcelFileDescriptor pfd) {
        if (pfd != null) {
            try {
                pfd.close();
            } catch (Exception ignored) {
                // read only; closing cannot lose anything
            }
        }
    }
}
