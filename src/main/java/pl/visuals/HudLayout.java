package pl.visuals;

import java.util.LinkedHashMap;
import java.util.Map;

/** Pozycje elementów HUD: domyślne, zapisane w configu i przeciągane w edytorze. */
public final class HudLayout {
    private HudLayout() {}

    public static final class Elem {
        public final String id, label;
        public final int nomW, nomH;
        public int x, y, w, h;
        public long frame = -100;

        Elem(String id, String label, int nomW, int nomH) {
            this.id = id;
            this.label = label;
            this.nomW = nomW;
            this.nomH = nomH;
            this.w = nomW;
            this.h = nomH;
        }
    }

    public static final Map<String, Elem> ELEMS = new LinkedHashMap<>();
    public static long frame;

    static {
        add("potions", "Potions", 110, 40);
        add("combo", "Combo", 70, 14);
        add("cooldowns", "Cooldowns", 138, 60);
        add("keys", "Keystrokes", 74, 104);
        add("info", "FPS / Koordynaty", 110, 30);
        add("armor", "Armor", 60, 100);
        add("kill", "Napis ZABÓJSTWO", 140, 46);
    }

    private static void add(String id, String label, int w, int h) {
        ELEMS.put(id, new Elem(id, label, w, h));
    }

    /** Kolejna klatka HUD (wołane raz na klatkę przed rysowaniem). */
    public static void nextFrame() {
        frame++;
    }

    /** Oblicza pozycję elementu; domyślną podaje wołający. */
    public static int[] place(String id, int w, int h, int defX, int defY, int W, int H) {
        Elem e = ELEMS.get(id);
        int x = defX, y = defY;
        float[] f = Config.I.hudPos.get(id);
        if (f != null && f.length >= 2) {
            x = Math.round(f[0] * Math.max(0, W - w));
            y = Math.round(f[1] * Math.max(0, H - h));
        }
        x = Math.max(0, Math.min(Math.max(0, W - w), x));
        y = Math.max(0, Math.min(Math.max(0, H - h), y));
        if (e != null) {
            e.x = x;
            e.y = y;
            e.w = w;
            e.h = h;
            e.frame = frame;
        }
        return new int[]{x, y};
    }

    public static boolean drawnThisFrame(Elem e) {
        return frame - e.frame <= 1;
    }

    /** Zapisuje pozycję (lewy górny róg w pikselach) jako ułamek wolnego miejsca. */
    public static void set(String id, int x, int y, int w, int h, int W, int H) {
        float fx = W - w <= 0 ? 0f : Math.max(0f, Math.min(1f, x / (float) (W - w)));
        float fy = H - h <= 0 ? 0f : Math.max(0f, Math.min(1f, y / (float) (H - h)));
        Config.I.hudPos.put(id, new float[]{fx, fy});
    }

    public static void reset() {
        Config.I.hudPos.clear();
        Config.save();
    }
}
