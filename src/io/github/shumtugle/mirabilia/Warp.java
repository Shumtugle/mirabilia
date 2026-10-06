package io.github.shumtugle.mirabilia;

/**
 * Where every point of a picture goes when it is straightened: first the
 * bend of the lens, then the lean of its two axes, then the turn.
 *
 * The lens moves a point along its radius from the middle, r to
 * r(1 + a(1 - r²)), with r measured to the middle of each edge: the middles
 * of the edges stay, the centre swells or sinks, the corners come in or go
 * out. The lean narrows one edge against the other, as a wall photographed
 * from below narrows towards its top. The turn is about the middle.
 *
 * A straightened picture no longer fills its rectangle, so the frame has to
 * shrink until it lies wholly on the picture. The three are fitted together,
 * once, to the edge of the picture as all three leave it: fitted one after
 * another, each would eat into the frame on its own and the corners would be
 * paid for three times.
 */
final class Warp {

    /** How far each drum reaches, at a hundred. */
    static final float LENS = 0.30f;
    static final float TILT = 0.20f;

    /** From -100 to 100, as the drums give them; the angle in degrees. */
    final float lens;
    final float tiltV;
    final float tiltH;
    final float angle;

    Warp(float lens, float tiltV, float tiltH, float angle) {
        this.lens = lens;
        this.tiltV = tiltV;
        this.tiltH = tiltH;
        this.angle = angle;
    }

    boolean plain() {
        return lens == 0f && tiltV == 0f && tiltH == 0f && angle == 0f;
    }

    boolean same(Warp o) {
        return o != null && o.lens == lens && o.tiltV == tiltV && o.tiltH == tiltH && o.angle == angle;
    }

    /** Where the point (x, y) of a picture w by h goes; the answer in out[0], out[1]. */
    void map(float x, float y, float w, float h, float[] out) {
        float cx = w / 2f;
        float cy = h / 2f;
        float a = Math.max(-1f, Math.min(1f, lens / 100f)) * LENS;
        if (a != 0f) {
            float dx = (x - cx) / cx;
            float dy = (y - cy) / cy;
            float r = (float) Math.sqrt(dx * dx + dy * dy);
            if (r > 1e-6f) {
                float f = 1f + a * (1f - r * r);
                x = cx + dx * f * cx;
                y = cy + dy * f * cy;
            }
        }
        float t = Math.max(-1f, Math.min(1f, tiltV / 100f)) * TILT;
        float th = Math.max(-1f, Math.min(1f, tiltH / 100f)) * TILT;
        if (t != 0f || th != 0f) {
            float fx = 1f + t * (2f * y / h - 1f);
            float fy = 1f + th * (2f * x / w - 1f);
            x = cx + (x - cx) * fx;
            y = cy + (y - cy) * fy;
        }
        if (angle != 0f) {
            double q = Math.toRadians(angle);
            float co = (float) Math.cos(q);
            float si = (float) Math.sin(q);
            float dx = x - cx;
            float dy = y - cy;
            x = cx + dx * co - dy * si;
            y = cy + dx * si + dy * co;
        }
        out[0] = x;
        out[1] = y;
    }

    /** The mesh a picture w by h is drawn through: (n+1) by (n+1) points, where each lands. */
    float[] mesh(float w, float h, int n) {
        float[] v = new float[(n + 1) * (n + 1) * 2];
        float[] p = new float[2];
        int k = 0;
        for (int j = 0; j <= n; j++) {
            for (int i = 0; i <= n; i++) {
                map(w * i / n, h * j / n, w, h, p);
                v[k++] = p[0];
                v[k++] = p[1];
            }
        }
        return v;
    }

    /** The picture's edge as it lands, in fractions of w and h, walked round in m steps a side. */
    float[] edge(float w, float h, int m) {
        float[] e = new float[m * 4 * 2];
        float[] p = new float[2];
        int k = 0;
        for (int side = 0; side < 4; side++) {
            for (int i = 0; i < m; i++) {
                float u = i / (float) m;
                float x;
                float y;
                if (side == 0) {
                    x = u * w;
                    y = 0f;
                } else if (side == 1) {
                    x = w;
                    y = u * h;
                } else if (side == 2) {
                    x = (1f - u) * w;
                    y = h;
                } else {
                    x = 0f;
                    y = (1f - u) * h;
                }
                map(x, y, w, h, p);
                e[k++] = p[0] / w;
                e[k++] = p[1] / h;
            }
        }
        return e;
    }

    /** Whether a point lies inside the edge. */
    static boolean inside(float[] e, float x, float y) {
        boolean in = false;
        int n = e.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            float xi = e[2 * i];
            float yi = e[2 * i + 1];
            float xj = e[2 * j];
            float yj = e[2 * j + 1];
            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                in = !in;
            }
        }
        return in;
    }

    /**
     * Whether a frame — its middle and its size in fractions — lies wholly on
     * the picture, with a hair to spare so no empty line shows at its edge.
     */
    static boolean covers(float[] e, float cx, float cy, float w, float h) {
        float hw = w / 2f * 1.004f;
        float hh = h / 2f * 1.004f;
        int m = 16;
        for (int i = 0; i <= m; i++) {
            float u = -1f + 2f * i / m;
            if (!inside(e, cx + u * hw, cy - hh) || !inside(e, cx + u * hw, cy + hh)
                || !inside(e, cx - hw, cy + u * hh) || !inside(e, cx + hw, cy + u * hh)) {
                return false;
            }
        }
        return true;
    }

    /** How much a frame of this size, standing in the middle, must shrink to lie on the picture. */
    static float fit(float[] e, float w, float h) {
        if (covers(e, 0.5f, 0.5f, w, h)) {
            return 1f;
        }
        float lo = 0.02f;
        float hi = 1f;
        for (int i = 0; i < 18; i++) {
            float mid = (lo + hi) / 2f;
            if (covers(e, 0.5f, 0.5f, w * mid, h * mid)) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return lo;
    }
}
