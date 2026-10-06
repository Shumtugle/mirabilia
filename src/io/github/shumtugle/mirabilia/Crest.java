package io.github.shumtugle.mirabilia;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * The mark of the application, standing in the upper corner of every room.
 *
 * It is the front layer of the home screen's icon, taken whole: the stem of
 * a letter i, its dot a gold ball, and a star beside them. On the home
 * screen they lie on lilac; here they lie on a disc of the seed's own
 * container, and the mark changes with the look of the whole. The disc
 * shows what a round mask on the home screen would show: the middle two
 * thirds of the layer, the stem running out at the bottom edge.
 *
 * It is the way into the settings. Pressed, it gives a little under the
 * finger, as the round button does.
 *
 * Over the paper of a book it is printed rather than lit, as a printer's
 * device stands in a running head: the silhouette alone, the stem cut
 * short, in the ink of the head and hardly taller than a line of it. A
 * disc of colour on a page would be a stain on the book.
 */
final class Crest extends View {

    /** The layer is a square of this many units, of which the middle SEEN are shown. */
    private static final float SQUARE = 108f;
    private static final float SEEN = 72f;

    private final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Drawable wonders;
    private final Path round = new Path();
    private final float size;

    /** The printed mark: how tall it stands on the paper, and the part of the layer it keeps. */
    private static final float PRINT_TALL = 15f;
    private static final float[] PRINT_CUT = {0.243f, 0.224f, 0.813f, 0.764f};
    private static final float PRINT_SIDE = 48f;
    private boolean printed;
    private int printInk;
    private Bitmap print;
    private final Paint printPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF printAt = new RectF();

    Crest(Context context, float sizePx) {
        super(context);
        size = sizePx;
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1f, Round.px(0.5f)));
        wonders = context.getDrawable(R.drawable.ic_fg);
        float unit = sizePx / SEEN;
        int whole = Math.round(SQUARE * unit);
        int left = Math.round(sizePx / 2f - whole / 2f);
        wonders.setBounds(left, left, left + whole, left + whole);
        round.addCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, Path.Direction.CW);
        setClickable(true);
        tint();
    }

    void tint() {
        disc.setColor(Tone.of(Tone.PRIMARY_CONTAINER));
        edge.setColor(Tone.of(Tone.OUTLINE_VARIANT));
        setBackground(Round.disc(printed ? printInk : Tone.of(Tone.ON_SURFACE)));
        invalidate();
    }

    /** Printed in an ink on paper, or lit on its disc. */
    void printed(boolean on, int ink) {
        if (printed == on && printInk == ink) {
            return;
        }
        printed = on;
        printInk = ink;
        printPaint.setColorFilter(new PorterDuffColorFilter(ink, PorterDuff.Mode.SRC_IN));
        tint();
        requestLayout();
    }

    boolean printed() {
        return printed;
    }

    /**
     * The silhouette, cut once from the layer. Its soft shadows are dropped —
     * in one ink they would print as a smudge — and it is kept at full size,
     * so the paper's own scaling smooths its edges.
     */
    private Bitmap silhouette() {
        if (print != null) {
            return print;
        }
        Bitmap layer = BitmapFactory.decodeResource(getResources(), R.drawable.ic_fg);
        if (layer == null) {
            return null;
        }
        int w = layer.getWidth();
        int h = layer.getHeight();
        int left = Math.round(w * PRINT_CUT[0]);
        int top = Math.round(h * PRINT_CUT[1]);
        int right = Math.round(w * PRINT_CUT[2]);
        int bottom = Math.round(h * PRINT_CUT[3]);
        Bitmap made = Bitmap.createBitmap(right - left, bottom - top, Bitmap.Config.ARGB_8888);
        Canvas onto = new Canvas(made);
        Paint cut = new Paint(Paint.FILTER_BITMAP_FLAG);
        cut.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 0, 0,
            0, 0, 0, 6f, -720f,
        })));
        onto.drawBitmap(layer, -left, -top, cut);
        layer.recycle();
        made.setHasMipMap(true);
        print = made;
        return print;
    }

    @Override
    public void setPressed(boolean pressed) {
        boolean was = isPressed();
        super.setPressed(pressed);
        if (was == pressed) {
            return;
        }
        float to = pressed ? 0.9f : 1f;
        animate().scaleX(to).scaleY(to).setStartDelay(0L).setDuration(Pace.PRESS)
            .setInterpolator(Pace.STANDARD).start();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int side = printed ? Round.dp(PRINT_SIDE) : Math.round(size);
        setMeasuredDimension(side, side);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (printed) {
            Bitmap mark = silhouette();
            if (mark == null) {
                return;
            }
            float tall = Round.px(PRINT_TALL);
            float wide = tall * mark.getWidth() / (float) mark.getHeight();
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            printAt.set(cx - wide / 2f, cy - tall / 2f, cx + wide / 2f, cy + tall / 2f);
            canvas.drawBitmap(mark, null, printAt, printPaint);
            return;
        }
        float middle = size / 2f;
        canvas.drawCircle(middle, middle, middle, disc);
        canvas.drawCircle(middle, middle, middle - edge.getStrokeWidth(), edge);
        canvas.save();
        canvas.clipPath(round);
        wonders.draw(canvas);
        canvas.restore();
    }
}
