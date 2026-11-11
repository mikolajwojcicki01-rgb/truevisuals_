package pl.visuals;

/** Dodatkowe efekty wokół gracza do wyboru (halo, spirala, orbity, płomienie, filar, deszcz gwiazd). */
public final class PlayerFx {
    private PlayerFx() {}

    private static final double TAU = Math.PI * 2;

    private static float rnd(int n) {
        float x = (float) Math.sin(n * 12.9898f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    public static boolean any() {
        Config c = Config.I;
        return c.fxHalo || c.fxHelix || c.fxOrbs || c.fxFlames || c.fxPillar || c.fxRain;
    }

    private static int col(float s, float time) {
        Config c = Config.I;
        return Config.palette(c.fxColor, c.fxHue, s, time);
    }

    /** Wstęga zwrócona do kamery między dwoma punktami; alfa a1 -> a2. */
    static void seg(double x1, double y1, double z1, double x2, double y2, double z2,
                            double[] right, double w, int rgb, float a1, float a2) {
        Draw.vert(x1 - right[0] * w, y1 - right[1] * w, z1 - right[2] * w, rgb, a1);
        Draw.vert(x1 + right[0] * w, y1 + right[1] * w, z1 + right[2] * w, rgb, a1);
        Draw.vert(x2 + right[0] * w, y2 + right[1] * w, z2 + right[2] * w, rgb, a2);
        Draw.vert(x2 - right[0] * w, y2 - right[1] * w, z2 - right[2] * w, rgb, a2);
    }

    public static void draw(double px, double py, double pz, float height, float time, float inten,
                            double[] right, double[] up, int seed) {
        Config c = Config.I;
        if (c.fxHalo) halo(px, py, pz, height, time, inten);
        if (c.fxPillar) pillar(px, py, pz, height, time, inten);
        if (c.fxHelix) helix(px, py, pz, height, time, inten, right);
        if (c.fxOrbs) orbs(px, py, pz, height, time, inten, right, up);
        if (c.fxFlames) flames(px, py, pz, height, time, inten, right, up, seed);
        if (c.fxRain) rain(px, py, pz, height, time, inten, right, up, seed);
    }

    // ---- halo nad głową ----
    private static void halo(double x, double y, double z, float h, float time, float inten) {
        double yy = y + h + 0.28 + 0.03 * Math.sin(time * 2.2);
        int cc = col(0.2f, time);
        Draw.glowRing(x, yy, z, 0.30, 0.022, 32, cc, 0.9f * inten);
        Draw.flatDisc(x, yy, z, 0.30, 24, cc, 0.0f, 0.10f * inten);
    }

    // ---- filar światła ----
    private static void pillar(double x, double y, double z, float h, float time, float inten) {
        int seg = 28;
        double r = 0.78 + 0.03 * Math.sin(time * 2.0);
        double top = y + h * 1.5;
        for (int i = 0; i < seg; i++) {
            double a0 = i * TAU / seg, a1 = (i + 1) * TAU / seg;
            int c0 = col((float) i / seg, time);
            double x0 = x + Math.cos(a0) * r, z0 = z + Math.sin(a0) * r;
            double x1 = x + Math.cos(a1) * r, z1 = z + Math.sin(a1) * r;
            Draw.vert(x0, y, z0, c0, 0.30f * inten);
            Draw.vert(x1, y, z1, c0, 0.30f * inten);
            Draw.vert(x1, top, z1, c0, 0f);
            Draw.vert(x0, top, z0, c0, 0f);
        }
        Draw.glowRing(x, y + 0.04, z, r, 0.05, 40, col(0.5f, time), 0.7f * inten);
    }

    // ---- podwójna spirala ----
    private static void helix(double x, double y, double z, float h, float time, float inten, double[] right) {
        int steps = 40;
        double r = 0.62;
        for (int s = 0; s < 2; s++) {
            double off = s * Math.PI;
            double lx = 0, ly = 0, lz = 0;
            for (int i = 0; i <= steps; i++) {
                double f = i / (double) steps;
                double ang = off + time * 1.8 + f * TAU * 2.2;
                double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
                double py = y + 0.05 + f * (h + 0.2);
                if (i > 0) {
                    float a1 = (float) Math.sin((i - 1) / (double) steps * Math.PI) * 0.9f * inten;
                    float a2 = (float) Math.sin(f * Math.PI) * 0.9f * inten;
                    int cc = col((float) f, time);
                    seg(lx, ly, lz, px, py, pz, right, 0.035, cc, a1, a2);
                    seg(lx, ly, lz, px, py, pz, right, 0.012, 0xFFFFFF, a1, a2);
                }
                lx = px;
                ly = py;
                lz = pz;
            }
        }
    }

    // ---- orbity: małe kule z ogonem ----
    private static void orbs(double x, double y, double z, float h, float time, float inten,
                             double[] right, double[] up) {
        for (int k = 0; k < 3; k++) {
            double dir = k % 2 == 0 ? 1 : -1;
            double baseA = time * (1.6 + 0.3 * k) * dir + k * TAU / 3;
            double r = 0.85 + 0.1 * k;
            double yy = y + h * (0.25 + 0.25 * k) + 0.12 * Math.sin(time * 2 + k);
            int cc = col(k / 3f, time);
            double lx = 0, lz = 0;
            for (int i = 0; i <= 14; i++) {
                double a = baseA - dir * i * 0.10;
                double ox = x + Math.cos(a) * r, oz = z + Math.sin(a) * r;
                if (i > 0) {
                    float a1 = (1f - (i - 1) / 14f) * 0.8f * inten, a2 = (1f - i / 14f) * 0.8f * inten;
                    seg(lx, yy, lz, ox, yy, oz, right, 0.05 * (1f - i / 16f), cc, a1, a2);
                }
                lx = ox;
                lz = oz;
            }
            double hx = x + Math.cos(baseA) * r, hz = z + Math.sin(baseA) * r;
            double d = 0.17;
            Draw.glowDisc(hx, yy, hz, right[0] * d, right[1] * d, right[2] * d, up[0] * d, up[1] * d, up[2] * d,
                    cc, 0.9f * inten);
            double d2 = 0.07;
            Draw.glowDisc(hx, yy, hz, right[0] * d2, right[1] * d2, right[2] * d2, up[0] * d2, up[1] * d2, up[2] * d2,
                    0xFFFFFF, inten);
        }
    }

    // ---- płomienie / iskry unoszące się od stóp ----
    private static void flames(double x, double y, double z, float h, float time, float inten,
                               double[] right, double[] up, int seed) {
        int n = 26;
        for (int i = 0; i < n; i++) {
            float p = (time * (0.35f + 0.25f * rnd(seed + i)) + rnd(seed + i * 7)) % 1f;
            double ang = rnd(seed + i * 3) * TAU + p * 2.0;
            double r = 0.18 + 0.38 * rnd(seed + i * 5) * (1f - p * 0.5f);
            double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            double py = y + 0.05 + p * (h + 0.3);
            float a = (float) Math.sin(Math.min(1f, p * 1.15f) * Math.PI) * 0.95f * inten;
            double d = 0.075 * (1.0 - p * 0.55);
            int cc = col(p, time);
            Draw.glowDisc(px, py, pz, right[0] * d, right[1] * d, right[2] * d, up[0] * d, up[1] * d, up[2] * d, cc, a);
        }
    }

    // ---- deszcz gwiazdek ----
    private static void rain(double x, double y, double z, float h, float time, float inten,
                             double[] right, double[] up, int seed) {
        int n = 14;
        for (int i = 0; i < n; i++) {
            float p = (time * (0.25f + 0.2f * rnd(seed + i * 11)) + rnd(seed + i * 13)) % 1f;
            double ang = rnd(seed + i * 17) * TAU;
            double r = 0.7 + 0.5 * rnd(seed + i * 19);
            double px = x + Math.cos(ang) * r, pz = z + Math.sin(ang) * r;
            double py = y + (h + 1.2) * (1f - p);
            float a = (float) Math.sin(p * Math.PI) * inten;
            Draw.star(px, py, pz, right, up, 0.10, col(rnd(seed + i), time), a);
        }
    }
}
