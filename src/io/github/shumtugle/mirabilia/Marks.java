package io.github.shumtugle.mirabilia;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * The drawings on the parts of a touch-up: what each drum turns, shown
 * rather than named, in the ink of the capsule it stands in, so a chosen part
 * is lit as its words would be. Drawn on a grid of twenty four, as the other
 * marks of the application are.
 */
final class Marks implements Pair.Icon {

    static final int AUTO = 0;
    static final int BRIGHT = 1;
    static final int CONTRAST = 2;
    static final int WARMTH = 3;
    static final int SATURATION = 4;
    static final int SHAPE = 5;
    static final int CLOSER = 6;
    static final int ANGLE = 7;
    static final int TILT_V = 8;
    static final int TILT_H = 9;
    static final int LENS = 10;
    static final int SHARP = 11;
    static final int SIZE = 12;

    private final int kind;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();

    Marks(int kind) {
        this.kind = kind;
        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void paint(Canvas c, float cx, float cy, float span) {
        float u = span / 24f;
        pen.setColor(Pair.ink);
        fill.setColor(Pair.ink);
        pen.setStrokeWidth(Math.max(1f, 1.8f * u));
        path.reset();
        switch (kind) {
            case AUTO: {
                /* The four points of the application's star: what finds the light by itself. */
                float k = 1.4f * u;
                float r = 8.5f * u;
                path.moveTo(cx, cy - r);
                path.quadTo(cx + k, cy - k, cx + r, cy);
                path.quadTo(cx + k, cy + k, cx, cy + r);
                path.quadTo(cx - k, cy + k, cx - r, cy);
                path.quadTo(cx - k, cy - k, cx, cy - r);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case BRIGHT: {
                c.drawCircle(cx, cy, 4f * u, pen);
                for (int i = 0; i < 8; i++) {
                    double q = Math.PI / 4 * i;
                    float co = (float) Math.cos(q);
                    float si = (float) Math.sin(q);
                    c.drawLine(cx + co * 7f * u, cy + si * 7f * u, cx + co * 9.5f * u, cy + si * 9.5f * u, pen);
                }
                break;
            }
            case CONTRAST: {
                c.drawCircle(cx, cy, 8.5f * u, pen);
                box.set(cx - 8.5f * u, cy - 8.5f * u, cx + 8.5f * u, cy + 8.5f * u);
                c.drawArc(box, 90f, 180f, true, fill);
                break;
            }
            case WARMTH: {
                /* A thermometer: the stem and the bulb, the bulb full. */
                box.set(cx - 2.5f * u, cy - 9.5f * u, cx + 2.5f * u, cy + 4f * u);
                c.drawRoundRect(box, 2.5f * u, 2.5f * u, pen);
                c.drawCircle(cx, cy + 6f * u, 3.8f * u, fill);
                c.drawLine(cx + 5f * u, cy - 6f * u, cx + 7.5f * u, cy - 6f * u, pen);
                c.drawLine(cx + 5f * u, cy - 2f * u, cx + 7.5f * u, cy - 2f * u, pen);
                break;
            }
            case SATURATION: {
                /* A drop. */
                path.moveTo(cx, cy - 9.5f * u);
                path.cubicTo(cx + 3f * u, cy - 5f * u, cx + 7.5f * u, cy - 1f * u, cx + 7.5f * u, cy + 3f * u);
                path.cubicTo(cx + 7.5f * u, cy + 7.2f * u, cx + 4f * u, cy + 9.5f * u, cx, cy + 9.5f * u);
                path.cubicTo(cx - 4f * u, cy + 9.5f * u, cx - 7.5f * u, cy + 7.2f * u, cx - 7.5f * u, cy + 3f * u);
                path.cubicTo(cx - 7.5f * u, cy - 1f * u, cx - 3f * u, cy - 5f * u, cx, cy - 9.5f * u);
                path.close();
                c.drawPath(path, pen);
                break;
            }
            case SHAPE: {
                /* A frame's four corners. */
                float w = 9f * u;
                float h = 7f * u;
                float a = 4f * u;
                corner(c, cx - w, cy - h, a, a);
                corner(c, cx + w, cy - h, -a, a);
                corner(c, cx - w, cy + h, a, -a);
                corner(c, cx + w, cy + h, -a, -a);
                break;
            }
            case CLOSER: {
                c.drawCircle(cx - 2f * u, cy - 2f * u, 6.5f * u, pen);
                c.drawLine(cx + 2.8f * u, cy + 2.8f * u, cx + 8.5f * u, cy + 8.5f * u, pen);
                c.drawLine(cx - 5f * u, cy - 2f * u, cx + 1f * u, cy - 2f * u, pen);
                c.drawLine(cx - 2f * u, cy - 5f * u, cx - 2f * u, cy + 1f * u, pen);
                break;
            }
            case ANGLE: {
                /* A level horizon and a picture turned against it. */
                c.drawLine(cx - 10f * u, cy + 6f * u, cx + 10f * u, cy + 6f * u, pen);
                c.save();
                c.rotate(-12f, cx, cy);
                box.set(cx - 7f * u, cy - 7f * u, cx + 7f * u, cy + 3f * u);
                c.drawRoundRect(box, 1.5f * u, 1.5f * u, pen);
                c.restore();
                break;
            }
            case TILT_V: {
                /* Narrower at the top: a wall seen from below. */
                path.moveTo(cx - 5f * u, cy - 8f * u);
                path.lineTo(cx + 5f * u, cy - 8f * u);
                path.lineTo(cx + 9f * u, cy + 8f * u);
                path.lineTo(cx - 9f * u, cy + 8f * u);
                path.close();
                c.drawPath(path, pen);
                break;
            }
            case TILT_H: {
                path.moveTo(cx - 9f * u, cy - 5f * u);
                path.lineTo(cx + 8f * u, cy - 9f * u);
                path.lineTo(cx + 8f * u, cy + 9f * u);
                path.lineTo(cx - 9f * u, cy + 5f * u);
                path.close();
                c.drawPath(path, pen);
                break;
            }
            case LENS: {
                /* A frame whose sides bow out, as a wide lens bows them. */
                float w = 8f * u;
                float h = 7f * u;
                float b = 3f * u;
                path.moveTo(cx - w, cy - h);
                path.quadTo(cx, cy - h - b, cx + w, cy - h);
                path.quadTo(cx + w + b, cy, cx + w, cy + h);
                path.quadTo(cx, cy + h + b, cx - w, cy + h);
                path.quadTo(cx - w - b, cy, cx - w, cy - h);
                path.close();
                c.drawPath(path, pen);
                break;
            }
            case SHARP: {
                /* A point: a fine edge, drawn with a fine edge. */
                path.moveTo(cx, cy - 9.5f * u);
                path.lineTo(cx + 8.5f * u, cy + 7.5f * u);
                path.lineTo(cx - 8.5f * u, cy + 7.5f * u);
                path.close();
                c.drawPath(path, pen);
                c.drawLine(cx, cy - 9.5f * u, cx, cy + 7.5f * u, pen);
                path.reset();
                path.moveTo(cx, cy - 9.5f * u);
                path.lineTo(cx + 8.5f * u, cy + 7.5f * u);
                path.lineTo(cx, cy + 7.5f * u);
                path.close();
                c.drawPath(path, fill);
                break;
            }
            case SIZE: {
                /* A small frame inside a large one, and the way between them. */
                box.set(cx - 10f * u, cy - 8f * u, cx + 10f * u, cy + 8f * u);
                c.drawRoundRect(box, 1.5f * u, 1.5f * u, pen);
                box.set(cx - 7f * u, cy + 0.5f * u, cx - 1f * u, cy + 5f * u);
                c.drawRect(box, fill);
                c.drawLine(cx + 1f * u, cy - 1f * u, cx + 6.5f * u, cy - 5.5f * u, pen);
                c.drawLine(cx + 6.5f * u, cy - 5.5f * u, cx + 3f * u, cy - 5.5f * u, pen);
                c.drawLine(cx + 6.5f * u, cy - 5.5f * u, cx + 6.5f * u, cy - 2f * u, pen);
                break;
            }
            default:
                break;
        }
    }

    private void corner(Canvas c, float x, float y, float dx, float dy) {
        c.drawLine(x, y, x + dx, y, pen);
        c.drawLine(x, y, x, y + dy, pen);
    }
}
