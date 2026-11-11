package pl.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Menu Truevisuals: tło void z gwiazdami, płynne animacje (otwieranie, zakładki, hover, przewijanie),
 * lista motywów, przełączniki i suwaki.
 */
public class VisualsScreen extends Screen {
    private static final int PW = 500, PH = 352;
    private static final String[] TABS = {"Visuals", "Kolory", "Moduły", "Walka", "Zabicie", "Fake player",
            "Ragdoll", "Aura & Hit", "Świat", "Info"};

    // ---- gwiazdy ----
    private static final int STARS = 260;
    private final float[] sx = new float[STARS], sy = new float[STARS], sz = new float[STARS], sph = new float[STARS];
    private final int[] scol = new int[STARS];

    // ---- stan ----
    private final long openMs = Util.getMeasuringTimeMs();
    private long lastMs = openMs;
    private boolean closing;
    private long closeMs;
    private int tab;
    private long tabMs = openMs;
    private float tabInd;
    private final float[] tabHover = new float[TABS.length];
    private float scroll, scrollTarget, contentH, viewH;
    private float curScale = 1f, curCx, curCy;
    private int panelX, panelY;
    private final List<Row> rows = new ArrayList<>();
    private Row dragging;
    private int preset;

    public VisualsScreen() {
        super(Text.literal("Truevisuals"));
        Random r = new Random(1337);
        int[] pal = {0xFFFFFF, 0xFFFFFF, 0xBFE8FF, 0xD9C8FF, 0xFFD9F2, 0x9FFFEF};
        for (int i = 0; i < STARS; i++) {
            sx[i] = r.nextFloat();
            sy[i] = r.nextFloat();
            sz[i] = 0.15f + r.nextFloat() * 0.85f;
            sph[i] = r.nextFloat() * 6.28f;
            scol[i] = pal[r.nextInt(pal.length)];
        }
        buildRows();
    }

    // =====================================================================
    //  Narzędzia
    // =====================================================================

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** Wygładzanie niezależne od FPS. */
    private static float k(float dt, float speed) {
        return 1f - (float) Math.exp(-dt * speed);
    }

    private static float easeOutBack(float p) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        float q = p - 1f;
        return 1f + c3 * q * q * q + c1 * q * q;
    }

    private static float easeOutCubic(float p) {
        float q = 1f - p;
        return 1f - q * q * q;
    }

    private static int ca(int rgb, float a) {
        int ai = (int) (clamp(a, 0f, 1f) * 255f);
        return (ai << 24) | (rgb & 0xFFFFFF);
    }

    private static void rr(DrawContext c, int x1, int y1, int x2, int y2, int r, int col) {
        r = Math.min(r, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            c.fill(x1, y1, x2, y2, col);
            return;
        }
        c.fill(x1, y1 + r, x2, y2 - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, col);
            c.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, col);
        }
    }

    private static void glow(DrawContext c, int cx, int cy, int radius, int rgb, float maxA) {
        int layers = 10;
        float a = maxA / layers;
        for (int kk = 0; kk < layers; kk++) {
            float r = radius * (1f - kk / (float) layers);
            int col = ca(rgb, a);
            int ri = (int) r;
            for (int dy = -ri; dy < ri; dy += 2) {
                int half = (int) Math.sqrt(Math.max(0, ri * ri - dy * dy));
                c.fill(cx - half, cy + dy, cx + half, cy + dy + 2, col);
            }
        }
    }

    private void txt(DrawContext c, String s, int x, int y, int rgb, float a, boolean shadow) {
        if (a < 0.04f) return;
        c.drawText(textRenderer, s, x, y, ca(rgb, a), shadow);
    }

    private void txtC(DrawContext c, String s, int cx, int y, int rgb, float a) {
        txt(c, s, cx - textRenderer.getWidth(s) / 2, y, rgb, a, true);
    }

    private static float rnd(int n) {
        float x = (float) Math.sin(n * 12.9898f) * 43758.5453f;
        return x - (float) Math.floor(x);
    }

    // =====================================================================
    //  Wiersze menu
    // =====================================================================

    private abstract static class Row {
        static int acc = 0x8FD8FF;
        int x, y, w, h = 22;
        float hover;

        boolean hit(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        abstract void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB);

        boolean click(VisualsScreen s, double mx, double my, int button) {
            return false;
        }

        void drag(double mx) {}

        void updateHover(boolean over, float dt) {
            hover += ((over ? 1f : 0f) - hover) * k(dt, 16f);
        }

        void bg(DrawContext c, float a) {
            rr(c, x, y, x + w, y + h, 7, ca(0xFFFFFF, (0.04f + 0.07f * hover) * a));
            if (hover > 0.02f) rr(c, x, y + 5, x + 2, y + h - 5, 1, ca(acc, hover * a));
        }
    }

    private static final class HeadRow extends Row {
        final String text;

        HeadRow(String text) {
            this.text = text;
            this.h = 18;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            String up = text.toUpperCase();
            s.txt(c, up, x + 4, y + 7, accA, a * 0.95f, false);
            int lx = x + 12 + s.textRenderer.getWidth(up);
            for (int i = lx; i < x + w; i += 3) {
                float f = 1f - (i - lx) / (float) Math.max(1, x + w - lx);
                c.fill(i, y + 11, Math.min(x + w, i + 3), y + 12, ca(accA, 0.28f * f * a));
            }
        }
    }

    private static final class ToggleRow extends Row {
        final String label;
        final BooleanSupplier get;
        final Consumer<Boolean> set;
        float on = -1;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            float target = get.getAsBoolean() ? 1f : 0f;
            if (on < 0) on = target;
            on += (target - on) * k(dt, 16f);
            bg(c, a);
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            int sw = 32, sh = 14;
            int sx0 = x + w - sw - 9, sy0 = y + (h - sh) / 2;
            int bgc = Config.lerp(0x2A2A3C, accA, on);
            if (on > 0.02f) rr(c, sx0 - 2, sy0 - 2, sx0 + sw + 2, sy0 + sh + 2, 9, ca(accA, 0.16f * on * a));
            rr(c, sx0, sy0, sx0 + sw, sy0 + sh, 7, ca(bgc, a));
            int kx = sx0 + 2 + (int) ((sw - sh) * on);
            rr(c, kx, sy0 + 2, kx + sh - 4, sy0 + sh - 2, 5, ca(0xFFFFFF, a));
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            set.accept(!get.getAsBoolean());
            Config.save();
            return true;
        }
    }

    private static final class SliderRow extends Row {
        final String label;
        final float min, max, step;
        final DoubleSupplier get;
        final DoubleConsumer set;
        final String fmt;
        int tx, tw;
        float disp = Float.NaN;

        SliderRow(String label, float min, float max, float step, String fmt, DoubleSupplier get, DoubleConsumer set) {
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.fmt = fmt;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            bg(c, a);
            float real = (float) get.getAsDouble();
            if (Float.isNaN(disp)) disp = real;
            disp += (real - disp) * k(dt, 22f);
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            String val = String.format(fmt, real);
            int vw = s.textRenderer.getWidth(val);
            s.txt(c, val, x + w - 9 - vw, y + 7, 0xFFFFFF, a * 0.9f, false);
            tx = x + (int) (w * 0.42f);
            tw = w - (int) (w * 0.42f) - vw - 22;
            int ty = y + h / 2;
            rr(c, tx, ty - 2, tx + tw, ty + 2, 2, ca(0x2A2A3C, a));
            float f = clamp((disp - min) / (max - min), 0f, 1f);
            int fw = (int) (tw * f);
            for (int i = 0; i < fw; i += 2) {
                c.fill(tx + i, ty - 2, tx + Math.min(fw, i + 2), ty + 2,
                        ca(Config.lerp(accA, accB, i / (float) Math.max(1, tw)), a));
            }
            int kx = tx + fw;
            rr(c, kx - 8, ty - 8, kx + 8, ty + 8, 8, ca(accA, a * (0.14f + 0.2f * hover)));
            rr(c, kx - 4, ty - 4, kx + 4, ty + 4, 4, ca(0xFFFFFF, a));
        }

        private void setFromMouse(double mx) {
            float f = clamp((float) ((mx - tx) / Math.max(1, tw)), 0f, 1f);
            float v = min + f * (max - min);
            v = Math.round(v / step) * step;
            set.accept(clamp(v, min, max));
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            setFromMouse(mx);
            return true;
        }

        @Override
        void drag(double mx) {
            setFromMouse(mx);
        }
    }

    private static final class CycleRow extends Row {
        final String label;
        final String[] options;
        final IntSupplier get;
        final IntConsumer set;

        CycleRow(String label, String[] options, IntSupplier get, IntConsumer set) {
            this.label = label;
            this.options = options;
            this.get = get;
            this.set = set;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            bg(c, a);
            s.txt(c, label, x + 9, y + 7, 0xE8E8F5, a, false);
            String val = "<  " + options[Math.floorMod(get.getAsInt(), options.length)] + "  >";
            int vw = s.textRenderer.getWidth(val);
            rr(c, x + w - vw - 18, y + 3, x + w - 6, y + h - 3, 5, ca(accA, 0.18f * a + 0.12f * hover * a));
            s.txt(c, val, x + w - vw - 12, y + 7, 0xFFFFFF, a, false);
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            int d = button == 1 ? -1 : 1;
            set.accept(Math.floorMod(get.getAsInt() + d, options.length));
            Config.save();
            return true;
        }
    }

    private static final class ButtonRow extends Row {
        final String label;
        final Runnable action;

        ButtonRow(String label, Runnable action) {
            this.label = label;
            this.action = action;
            this.h = 26;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            rr(c, x, y, x + w, y + h, 7, ca(accA, (0.20f + 0.30f * hover) * a));
            rr(c, x + 1, y + 1, x + w - 1, y + h - 1, 6, ca(0x0B0B16, 0.55f * a));
            rr(c, x + 1, y + h / 2, x + w - 1, y + h - 1, 6, ca(accB, (0.10f + 0.20f * hover) * a));
            s.txtC(c, label, x + w / 2, y + 9, 0xFFFFFF, a);
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            action.run();
            return true;
        }
    }

    /** Pozycja listy motywów (nazwa + kolorowy pasek po prawej, jak w PhantomVisuals). */
    private static final class ThemeItem extends Row {
        final int idx;
        float sel = -1;

        ThemeItem(int idx) {
            this.idx = idx;
            this.h = 22;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            updateHover(hit(mx, my), dt);
            float target = Config.I.theme == idx ? 1f : 0f;
            if (sel < 0) sel = target;
            sel += (target - sel) * k(dt, 16f);
            rr(c, x, y, x + w, y + h, 6, ca(0xFFFFFF, (0.03f + 0.06f * hover + 0.07f * sel) * a));
            if (sel > 0.02f) rr(c, x, y + 4, x + 3, y + h - 4, 1, ca(accA, sel * a));
            s.txt(c, Config.THEMES[idx], x + 12, y + 7, sel > 0.5f ? 0xFFFFFF : 0xD0D0E4, a, false);
            int cw = 70, cx0 = x + w - cw - 10;
            rr(c, cx0 - 1, y + 5, cx0 + cw + 1, y + h - 5, 4, ca(0xFFFFFF, (0.10f + 0.5f * sel) * a));
            for (int i = 0; i < cw; i += 2) {
                int col = Config.themeColor(idx, i / (float) cw);
                c.fill(cx0 + i, y + 6, cx0 + Math.min(cw, i + 2), y + h - 6, ca(col, 0.95f * a));
            }
        }

        @Override
        boolean click(VisualsScreen s, double mx, double my, int button) {
            Config.I.theme = idx;
            Config.save();
            return true;
        }
    }

    private static final class TextRow extends Row {
        final String text;

        TextRow(String text) {
            this.text = text;
            this.h = 14;
        }

        @Override
        void draw(VisualsScreen s, DrawContext c, int mx, int my, float a, float dt, int accA, int accB) {
            s.txt(c, text, x + 9, y + 2, 0xB9B9D0, a, false);
        }
    }

    // =====================================================================
    //  Budowanie zakładek
    // =====================================================================

    private void buildRows() {
        rows.clear();
        Config c = Config.I;
        switch (tab) {
            case 0 -> {
                rows.add(new HeadRow("Efekty wokół gracza"));
                rows.add(new ToggleRow("Wirujące pierścienie", () -> c.rings, v -> c.rings = v));
                rows.add(new ToggleRow("Okrąg pod graczem", () -> c.groundRing, v -> c.groundRing = v));
                rows.add(new ToggleRow("Magiczny krąg z napisem", () -> c.magicCircle, v -> c.magicCircle = v));
                rows.add(new ToggleRow("Gwiazdki wokół gracza", () -> c.playerStars, v -> c.playerStars = v));
                rows.add(new ToggleRow("Gwiazdki w powietrzu", () -> c.ambientStars, v -> c.ambientStars = v));
                rows.add(new HeadRow("Trail za graczem"));
                rows.add(new ToggleRow("Trail (smuga za graczem)", () -> c.trailOn, v -> c.trailOn = v));
                rows.add(new CycleRow("Kolor trailu", Config.PALETTE, () -> c.trailColor, v -> c.trailColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.trailHue, v -> { c.trailHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Długość", 6, 60, 1, "%.0f",
                        () -> c.trailLength, v -> { c.trailLength = (int) Math.round(v); Config.save(); }));
                rows.add(new SliderRow("Wysokość", 0.1f, 1.8f, 0.05f, "%.2f",
                        () -> c.trailHeight, v -> { c.trailHeight = (float) v; Config.save(); }));
                rows.add(new SliderRow("Szerokość", 0.3f, 2f, 0.05f, "%.2f",
                        () -> c.trailWidth, v -> { c.trailWidth = (float) v; Config.save(); }));
                rows.add(new HeadRow("Ustawienia"));
                rows.add(new SliderRow("Liczba pierścieni", 1, 6, 1, "%.0f",
                        () -> c.ringCount, v -> { c.ringCount = (int) Math.round(v); Config.save(); }));
                rows.add(new SliderRow("Intensywność", 0.3f, 1.5f, 0.05f, "%.2f",
                        () -> c.intensity, v -> { c.intensity = (float) v; Config.save(); }));
                rows.add(new SliderRow("Prędkość", 0.3f, 3f, 0.05f, "%.2f",
                        () -> c.speed, v -> { c.speed = (float) v; Config.save(); }));
            }
            case 1 -> {
                rows.add(new HeadRow("Motyw"));
                for (int i = 0; i < Config.THEMES.length; i++) rows.add(new ThemeItem(i));
                rows.add(new HeadRow("Pozostałe"));
                rows.add(new CycleRow("Pokazuj na", Config.TARGETS, () -> c.target, v -> c.target = v));
                rows.add(new CycleRow("Kolor menu", Config.PALETTE, () -> c.menuColor, v -> c.menuColor = v));
                rows.add(new SliderRow("Odcień menu (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.menuHue, v -> { c.menuHue = (float) v; Config.save(); }));
            }
            case 2 -> {
                rows.add(new HeadRow("Pozycje HUD"));
                rows.add(new ButtonRow("Edytuj pozycje HUD (przeciągnij myszką)", () -> client.setScreen(new HudEditScreen())));
                rows.add(new ButtonRow("Resetuj pozycje HUD", HudLayout::reset));
                rows.add(new HeadRow("HUD"));
                rows.add(new ToggleRow("Combo counter", () -> c.comboHud, v -> c.comboHud = v));
                rows.add(new ToggleRow("Cooldowns", () -> c.cooldownsHud, v -> c.cooldownsHud = v));
                rows.add(new ToggleRow("Potions", () -> c.potionsHud, v -> c.potionsHud = v));
                rows.add(new ToggleRow("Keystrokes + CPS", () -> c.keystrokesHud, v -> c.keystrokesHud = v));
                rows.add(new ToggleRow("FPS i ping", () -> c.infoHud, v -> c.infoHud = v));
                rows.add(new ToggleRow("Koordynaty", () -> c.coordsHud, v -> c.coordsHud = v));
                rows.add(new ToggleRow("Armor HUD", () -> c.armorHud, v -> c.armorHud = v));
                rows.add(new HeadRow("Fake player"));
                rows.add(new ButtonRow("Postaw fake playera (zamyka menu)", () -> {
                    FakePlayer.spawn(client);
                    close();
                }));
                rows.add(new ButtonRow("Usuń fake playera", FakePlayer::remove));
            }
            case 3 -> {
                rows.add(new HeadRow("Dźwięk uderzenia"));
                rows.add(new CycleRow("Dźwięk", Fx.names(Fx.HIT_SOUNDS), () -> c.hitSound, v -> c.hitSound = v));
                rows.add(new SliderRow("Głośność", 0f, 2f, 0.05f, "%.2f",
                        () -> c.hitVolume, v -> { c.hitVolume = (float) v; Config.save(); }));
                rows.add(new SliderRow("Wysokość dźwięku", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.hitPitch, v -> { c.hitPitch = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Losowa wysokość", () -> c.hitPitchRandom, v -> c.hitPitchRandom = v));
                rows.add(new ButtonRow("Odtwórz dźwięk uderzenia", Fx::playHitSound));
                rows.add(new HeadRow("Damage Indicator"));
                rows.add(new ToggleRow("Liczby obrażeń nad graczem", () -> c.dmgOn, v -> c.dmgOn = v));
                rows.add(new CycleRow("Kolor liczb", Config.PALETTE, () -> c.dmgColor, v -> c.dmgColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.dmgHue, v -> { c.dmgHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Rozmiar", 0.5f, 2.5f, 0.05f, "%.2f",
                        () -> c.dmgSize, v -> { c.dmgSize = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Tylko moje uderzenia", () -> c.dmgOnlyMine, v -> c.dmgOnlyMine = v));
                rows.add(new HeadRow("Cząsteczki"));
                rows.add(new CycleRow("Cząsteczki uderzenia", Fx.HIT_PARTICLES, () -> c.hitParticle, v -> c.hitParticle = v));
                rows.add(new TextRow("Klik lewym = następny, prawym = poprzedni"));
            }
            case 4 -> {
                rows.add(new HeadRow("Gotowy styl"));
                rows.add(new CycleRow("Styl zabicia", new String[]{"Własny", "Aura (jak na filmie)", "Złota eksplozja", "Sam piorun"},
                        () -> preset, v -> {
                            preset = v;
                            if (v == 1) { c.killEffect = 11; c.killSound = 1; c.hitSound = 1; c.ragdollMode = 0; c.killTitle = true; }
                            if (v == 2) { c.killEffect = 10; c.killSound = 13; c.ragdollMode = 0; c.killTitle = true; }
                            if (v == 3) { c.killEffect = 1; c.killSound = 4; c.ragdollMode = 1; c.killTitle = false; }
                        }));
                rows.add(new HeadRow("Efekt"));
                rows.add(new CycleRow("Efekt zabicia", Fx.KILL_EFFECTS, () -> c.killEffect, v -> { c.killEffect = v; preset = 0; }));
                rows.add(new ToggleRow("Napis ZABÓJSTWO", () -> c.killTitle, v -> c.killTitle = v));
                rows.add(new ButtonRow("Test efektu zabicia (zamyka menu)", () -> {
                    close();
                    RagdollManager.testKillEffect(client);
                }));
                rows.add(new HeadRow("Dźwięk zabicia"));
                rows.add(new CycleRow("Dźwięk", Fx.names(Fx.KILL_SOUNDS), () -> c.killSound, v -> { c.killSound = v; preset = 0; }));
                rows.add(new SliderRow("Głośność", 0f, 2f, 0.05f, "%.2f",
                        () -> c.killVolume, v -> { c.killVolume = (float) v; Config.save(); }));
                rows.add(new SliderRow("Wysokość dźwięku", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.killPitch, v -> { c.killPitch = (float) v; Config.save(); }));
                rows.add(new ButtonRow("Odtwórz dźwięk zabicia", Fx::playKillSound));
            }
            case 5 -> {
                rows.add(new HeadRow("Fake player"));
                rows.add(new ButtonRow("Postaw fake playera (zamyka menu)", () -> {
                    FakePlayer.spawn(client);
                    close();
                }));
                rows.add(new ButtonRow("Usuń fake playera", FakePlayer::remove));
                rows.add(new SliderRow("Obrażenia na cios", 1f, 10f, 0.5f, "%.1f",
                        () -> c.fakeDamage, v -> { c.fakeDamage = (float) v; Config.save(); }));
                rows.add(new ToggleRow("Odnawia się po zabiciu", () -> c.fakeRespawn, v -> c.fakeRespawn = v));
                rows.add(new TextRow("Stoi jak zwykły gracz, twarzą do Ciebie."));
                rows.add(new TextRow("Bij go LPM: dźwięk, combo, efekt zabicia"));
                rows.add(new TextRow("i ragdoll po zabiciu."));
            }
            case 6 -> {
                rows.add(new HeadRow("Ragdoll po zabiciu"));
                rows.add(new ToggleRow("Ragdoll po zabiciu gracza", () -> c.ragdoll, v -> c.ragdoll = v));
                rows.add(new CycleRow("Tryb lotu", Fx.RAGDOLL_MODES, () -> c.ragdollMode, v -> c.ragdollMode = v));
                rows.add(new ToggleRow("Błyszczący ślad w locie", () -> c.ragdollTrail, v -> c.ragdollTrail = v));
                rows.add(new SliderRow("Leży (sekundy)", 1f, 6f, 0.5f, "%.1f",
                        () -> c.ragdollLie, v -> { c.ragdollLie = (float) v; Config.save(); }));
                rows.add(new SliderRow("Siła wyrzutu", 0.5f, 2f, 0.05f, "%.2f",
                        () -> c.ragdollPower, v -> { c.ragdollPower = (float) v; Config.save(); }));
                rows.add(new ButtonRow("Test ragdolla (zamyka menu)", () -> {
                    RagdollManager.spawnTest(client);
                    close();
                }));
            }
            case 7 -> {
                rows.add(new HeadRow("Aura wokół gracza"));
                rows.add(new ToggleRow("Aura", () -> c.auraOn, v -> c.auraOn = v));
                rows.add(new CycleRow("Kolor aury", Config.PALETTE, () -> c.auraColor, v -> c.auraColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.auraHue, v -> { c.auraHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Rozmiar aury", 0.6f, 1.6f, 0.05f, "%.2f",
                        () -> c.auraSize, v -> { c.auraSize = (float) v; Config.save(); }));
                rows.add(new SliderRow("Jasność aury", 0.3f, 1.5f, 0.05f, "%.2f",
                        () -> c.auraIntensity, v -> { c.auraIntensity = (float) v; Config.save(); }));
                rows.add(new HeadRow("Hit Color"));
                rows.add(new ToggleRow("Hit Color (podświetlenie po uderzeniu)", () -> c.hitFlash, v -> c.hitFlash = v));
                rows.add(new CycleRow("Kolor Hit Color", Config.PALETTE, () -> c.hitFlashColor, v -> c.hitFlashColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.hitFlashHue, v -> { c.hitFlashHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Czas podświetlenia (s)", 0.1f, 1.0f, 0.05f, "%.2f",
                        () -> c.hitFlashTime, v -> { c.hitFlashTime = (float) v; Config.save(); }));
            }
            case 8 -> {
                rows.add(new HeadRow("Zoom"));
                rows.add(new ToggleRow("Zoom (przytrzymaj klawisz)", () -> c.zoomOn, v -> c.zoomOn = v));
                rows.add(new CycleRow("Klawisz zoomu", Config.ZOOM_KEYS, () -> c.zoomKey, v -> c.zoomKey = v));
                rows.add(new SliderRow("Przybliżenie", 1.5f, 8f, 0.25f, "%.2fx",
                        () -> c.zoomLevel, v -> { c.zoomLevel = (float) v; Config.save(); }));
                rows.add(new TextRow("Maks. przybliżenie ogranicza FOV 30 w grze."));
                rows.add(new HeadRow("Niebo"));
                rows.add(new ToggleRow("Własne niebo", () -> c.skyOn, v -> c.skyOn = v));
                rows.add(new CycleRow("Kolor nieba", Config.PALETTE, () -> c.skyColor, v -> c.skyColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.skyHue, v -> { c.skyHue = (float) v; Config.save(); }));
                rows.add(new HeadRow("Mgła"));
                rows.add(new ToggleRow("Własna mgła", () -> c.fogOn, v -> c.fogOn = v));
                rows.add(new CycleRow("Kolor mgły", Config.PALETTE, () -> c.fogColor, v -> c.fogColor = v));
                rows.add(new SliderRow("Odcień (dla \"Własny\")", 0f, 1f, 0.01f, "%.2f",
                        () -> c.fogHue, v -> { c.fogHue = (float) v; Config.save(); }));
                rows.add(new SliderRow("Zasięg mgły (bloki)", 16f, 256f, 4f, "%.0f",
                        () -> c.fogDistance, v -> { c.fogDistance = (float) v; Config.save(); }));
            }
            default -> {
                rows.add(new HeadRow("Truevisuals"));
                rows.add(new TextRow("Prawy Shift – otwórz / zamknij menu"));
                rows.add(new TextRow("Kółko myszy – przewijanie listy"));
                rows.add(new TextRow("Lista: lewy klik = dalej, prawy = wstecz"));
                rows.add(new TextRow(" "));
                rows.add(new TextRow("Po zabiciu gracza: efekt, dźwięk i ragdoll,"));
                rows.add(new TextRow("którego postać wylatuje w górę i leży kilka sekund."));
                rows.add(new TextRow(" "));
                rows.add(new TextRow("Ustawienia: config/visuals.json"));
            }
        }
        dragging = null;
        scroll = 0f;
        scrollTarget = 0f;
    }

    private void setTab(int t) {
        if (t == tab) return;
        tab = t;
        tabMs = Util.getMeasuringTimeMs();
        buildRows();
    }

    // =====================================================================
    //  Rysowanie
    // =====================================================================

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // własne tło rysujemy w render()
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (!closing) {
            closing = true;
            closeMs = Util.getMeasuringTimeMs();
        }
    }

    @Override
    public void removed() {
        Config.save();
    }

    private int viewTop() {
        return panelY + 46;
    }

    private int viewBottom() {
        return panelY + PH - 30;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = Util.getMeasuringTimeMs();
        float dt = clamp((now - lastMs) / 1000f, 0f, 0.1f);
        lastMs = now;
        float t = (now % 1_000_000L) / 1000f;

        float openP = clamp((now - openMs) / 560f, 0f, 1f);
        float closeP = closing ? clamp((now - closeMs) / 240f, 0f, 1f) : 0f;
        if (closing && closeP >= 1f) {
            client.setScreen(null);
            return;
        }
        float vis = easeOutCubic(openP) * (1f - closeP);
        float warp = 1f + (1f - easeOutCubic(openP)) * 0.7f + closeP * 0.25f;

        int W = width, H = height;
        int accA = Config.palette(Config.I.menuColor, Config.I.menuHue, 0.0f, t);
        int accB = Config.palette(Config.I.menuColor, Config.I.menuHue, 1.0f, t);
        if (accB == accA) accB = Config.lerp(accA, 0xFFFFFF, 0.35f);

        drawVoid(c, W, H, t, vis, warp, mouseX, mouseY, accA, accB);

        // ---- panel ----
        float base = Math.min(1f, Math.min((W - 16f) / PW, (H - 16f) / PH));
        float s = base * (0.86f + 0.14f * easeOutBack(openP)) * (1f - 0.06f * closeP);
        curScale = s;
        curCx = W / 2f;
        curCy = H / 2f;
        panelX = (int) (curCx - PW / 2f);
        panelY = (int) (curCy - PH / 2f);
        float a = vis;

        double lmx = (mouseX - curCx) / s + curCx;
        double lmy = (mouseY - curCy) / s + curCy;

        c.getMatrices().push();
        c.getMatrices().translate(curCx, curCy, 0);
        c.getMatrices().scale(s, s, 1f);
        c.getMatrices().translate(-curCx, -curCy, 0);

        int x = panelX, y = panelY;
        // cień i poświata
        for (int i = 4; i >= 1; i--) {
            rr(c, x - i * 3, y - i * 3 + 4, x + PW + i * 3, y + PH + i * 3 + 4, 14 + i * 3, ca(0x000000, 0.10f * a));
        }
        glow(c, x + PW / 2, y + PH / 2, 250, accA, 0.15f * a);
        glow(c, x + PW - 40, y + 30, 150, accB, 0.10f * a);
        // ramka z delikatnym przesuwającym się blaskiem
        rr(c, x - 1, y - 1, x + PW + 1, y + PH + 1, 13, ca(accA, 0.50f * a));
        rr(c, x, y, x + PW, y + PH, 12, ca(0x07070F, 0.94f * a));
        for (int i = 0; i < 46; i++) {
            c.fill(x + 12, y + 1 + i, x + PW - 12, y + 2 + i, ca(accA, 0.055f * a * (1f - i / 46f)));
        }
        // unoszące się drobinki
        for (int i = 0; i < 16; i++) {
            float sp = 6f + 10f * rnd(i + 5);
            float yy = PH - ((t * sp + rnd(i + 20) * PH) % PH);
            float xx = rnd(i + 40) * PW + 6f * (float) Math.sin(t * 0.8f + i);
            float al = (float) Math.sin(Math.PI * (yy / PH)) * 0.35f * a;
            c.fill(x + (int) xx, y + (int) yy, x + (int) xx + 2, y + (int) yy + 2, ca(Config.lerp(accA, accB, rnd(i)), al));
        }

        // ---- pasek boczny ----
        rr(c, x + 8, y + 8, x + 130, y + PH - 8, 10, ca(0x000000, 0.36f * a));
        drawTitle(c, x + 69, y + 20, t, a, accA);

        Row.acc = accA;
        float indTarget = tab * 25f;
        tabInd += (indTarget - tabInd) * k(dt, 18f);
        int ty0 = y + 62;
        rr(c, x + 14, ty0 + (int) tabInd, x + 124, ty0 + (int) tabInd + 22, 7, ca(accA, 0.20f * a));
        rr(c, x + 14, ty0 + (int) tabInd + 4, x + 17, ty0 + (int) tabInd + 18, 1, ca(accA, a));
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * 25;
            boolean over = lmx >= x + 14 && lmx < x + 124 && lmy >= ty && lmy < ty + 22;
            tabHover[i] += ((over ? 1f : 0f) - tabHover[i]) * k(dt, 16f);
            if (tabHover[i] > 0.02f && i != tab) rr(c, x + 14, ty, x + 124, ty + 22, 7, ca(0xFFFFFF, 0.05f * tabHover[i] * a));
            int col = i == tab ? 0xFFFFFF : Config.lerp(0x9A9AB5, 0xFFFFFF, tabHover[i] * 0.7f);
            int ic = Config.lerp(accA, accB, i / (float) (TABS.length - 1));
            float ia = i == tab ? 1f : 0.45f + 0.4f * tabHover[i];
            rr(c, x + 24, ty + 7, x + 32, ty + 15, 3, ca(ic, ia * a));
            if (i == tab) rr(c, x + 22, ty + 5, x + 34, ty + 17, 5, ca(ic, 0.18f * a));
            txt(c, TABS[i], x + 40 + (int) (3 * tabHover[i]), ty + 7, col, a, false);
        }
        txt(c, "RShift – zamknij", x + 16, y + PH - 24, 0x6F6F8C, a, false);

        // ---- zawartość ----
        float ce = easeOutCubic(clamp((now - tabMs) / 340f, 0f, 1f));
        int cx0 = x + 146, cw = PW - 146 - 22;
        txt(c, TABS[tab], cx0 + 2, y + 18, 0xFFFFFF, a * ce, true);
        // plakietka z klawiszem
        {
            String kb = "RShift";
            int kw = textRenderer.getWidth(kb) + 12;
            rr(c, x + PW - 22 - kw, y + 14, x + PW - 22, y + 28, 6, ca(0xFFFFFF, 0.07f * a));
            txt(c, kb, x + PW - 22 - kw + 6, y + 17, 0x9A9AB5, a, false);
        }
        int ul = (int) (70 * ce);
        for (int i = 0; i < ul; i += 2) {
            c.fill(cx0 + 2 + i, y + 33, cx0 + 4 + i, y + 34, ca(Config.lerp(accA, accB, i / 70f), 0.9f * a));
        }

        int vt = viewTop(), vb = viewBottom();
        viewH = vb - vt;
        float total = 0;
        for (Row r : rows) total += r.h + 2;
        contentH = total;
        float maxScroll = Math.max(0f, contentH - viewH);
        scrollTarget = clamp(scrollTarget, 0f, maxScroll);
        scroll += (scrollTarget - scroll) * k(dt, 14f);

        boolean mouseInView = lmx >= cx0 - 4 && lmx < cx0 + cw + 8 && lmy >= vt && lmy < vb;
        int hmx = mouseInView ? (int) lmx : -10000, hmy = mouseInView ? (int) lmy : -10000;

        c.enableScissor(cx0 - 6, vt, cx0 + cw + 10, vb);
        float ry = vt - scroll;
        int idx = 0;
        for (Row r : rows) {
            float st = clamp((now - tabMs - idx * 22L) / 260f, 0f, 1f);
            float ea = easeOutCubic(st);
            r.x = cx0 + (int) ((1f - ea) * 18f);
            r.y = (int) ry;
            r.w = cw;
            if (r.y + r.h > vt - 4 && r.y < vb + 4) {
                r.draw(this, c, hmx, hmy, a * ea, dt, accA, accB);
            }
            ry += r.h + 2;
            idx++;
        }
        c.disableScissor();

        // pasek przewijania
        if (maxScroll > 1f) {
            int trackX = cx0 + cw + 8;
            rr(c, trackX, vt, trackX + 3, vb, 1, ca(0xFFFFFF, 0.06f * a));
            float th = Math.max(24f, viewH * viewH / contentH);
            float ty = vt + (viewH - th) * (scroll / maxScroll);
            rr(c, trackX, (int) ty, trackX + 3, (int) (ty + th), 1, ca(accA, 0.7f * a));
        }

        c.getMatrices().pop();
    }

    private void drawTitle(DrawContext c, int cx, int y, float t, float a, int accA) {
        String s = "Truevisuals";
        float sc = 1.6f;
        int total = (int) (textRenderer.getWidth(s) * sc);
        float x = cx - total / 2f;
        glow(c, cx, y + 6, 46, accA, 0.10f * a);
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            int col = Config.color(((float) i / s.length() + t * 0.2f) % 1f, t);
            c.getMatrices().push();
            c.getMatrices().translate(x, y, 0);
            c.getMatrices().scale(sc, sc, 1f);
            txt(c, ch, 0, 0, col, a, true);
            c.getMatrices().pop();
            x += textRenderer.getWidth(ch) * sc;
        }
        int ww = total;
        for (int i = 0; i < ww; i += 2) {
            c.fill(cx - ww / 2 + i, y + 16, cx - ww / 2 + i + 2, y + 17,
                    ca(Config.color(i / (float) ww, t), 0.55f * a));
        }
    }

    private void drawVoid(DrawContext c, int W, int H, float t, float vis, float warp,
                          int mx, int my, int accA, int accB) {
        int bgA = (int) (clamp(vis, 0f, 1f) * 247f);
        c.fillGradient(0, 0, W, H, (bgA << 24) | 0x010106, (bgA << 24) | 0x0B0420);

        // mgławice
        glow(c, (int) (W * (0.25 + 0.05 * Math.sin(t * 0.17))), (int) (H * (0.30 + 0.05 * Math.cos(t * 0.13))),
                (int) (Math.min(W, H) * 0.55), Config.lerp(0x6A2CFF, accA, 0.6f), 0.20f * vis);
        glow(c, (int) (W * (0.78 + 0.04 * Math.cos(t * 0.15))), (int) (H * (0.70 + 0.05 * Math.sin(t * 0.11))),
                (int) (Math.min(W, H) * 0.50), Config.lerp(0x00A8FF, accB, 0.6f), 0.15f * vis);
        glow(c, (int) (W * 0.55), (int) (H * (0.15 + 0.04 * Math.sin(t * 0.2))),
                (int) (Math.min(W, H) * 0.35), Config.lerp(0xC83CFF, accA, 0.6f), 0.10f * vis);

        // gwiazdy
        float cxm = W / 2f, cym = H / 2f;
        for (int i = 0; i < STARS; i++) {
            float z = sz[i];
            float fx = ((sx[i] - t * 0.006f * z) % 1f + 1f) % 1f;
            float px = fx * W + (mx - cxm) * z * -0.04f;
            float py = sy[i] * H + (my - cym) * z * -0.04f;
            px = cxm + (px - cxm) * warp;
            py = cym + (py - cym) * warp;
            float tw = 0.55f + 0.45f * (float) Math.sin(t * (1.0f + z * 2.2f) + sph[i]);
            float al = tw * vis * (0.25f + 0.75f * z);
            int size = z > 0.78f ? 2 : 1;
            int ix = (int) px, iy = (int) py;
            if (ix < 0 || iy < 0 || ix >= W || iy >= H) continue;
            c.fill(ix, iy, ix + size, iy + size, ca(scol[i], al));
            if (z > 0.93f && i % 3 == 0) {
                int len = 3 + (int) (2 * tw);
                int col = ca(scol[i], al * 0.55f);
                c.fill(ix - len, iy, ix + size + len, iy + 1, col);
                c.fill(ix, iy - len, ix + 1, iy + size + len, col);
                glow(c, ix, iy, 7, scol[i], al * 0.22f);
            }
        }

        // spadające gwiazdy
        for (int k = 0; k < 2; k++) {
            float period = 3.4f + k * 1.9f;
            float tt = t + k * 1.7f;
            int slot = (int) (tt / period);
            float p = (tt % period) / period;
            if (p < 0.3f) {
                Random r = new Random(slot * 31L + k * 977L);
                float x0 = r.nextFloat() * W * 0.8f + W * 0.1f;
                float y0 = r.nextFloat() * H * 0.4f;
                float ang = 0.45f + r.nextFloat() * 0.35f;
                float q = p / 0.3f;
                float dist = q * W * 0.35f;
                for (int i = 0; i < 18; i++) {
                    float d = dist - i * 4f;
                    if (d < 0) break;
                    int sx2 = (int) (x0 + Math.cos(ang) * d);
                    int sy2 = (int) (y0 + Math.sin(ang) * d);
                    float fa = (1f - i / 18f) * (1f - q * q) * vis;
                    c.fill(sx2, sy2, sx2 + 2, sy2 + 2, ca(i < 2 ? 0xFFFFFF : accA, fa));
                }
            }
        }

        // winieta
        c.fillGradient(0, 0, W, H / 4, ca(0x000000, 0.55f * vis), ca(0x000000, 0f));
        c.fillGradient(0, H - H / 4, W, H, ca(0x000000, 0f), ca(0x000000, 0.65f * vis));
    }


    // =====================================================================
    //  Sterowanie
    // =====================================================================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closing) return true;
        double lx = (mouseX - curCx) / curScale + curCx;
        double ly = (mouseY - curCy) / curScale + curCy;
        int ty0 = panelY + 62;
        for (int i = 0; i < TABS.length; i++) {
            int ty = ty0 + i * 25;
            if (lx >= panelX + 14 && lx < panelX + 124 && ly >= ty && ly < ty + 22) {
                setTab(i);
                return true;
            }
        }
        if (ly >= viewTop() && ly < viewBottom()) {
            for (Row r : rows) {
                if (r.hit(lx, ly) && r.click(this, lx, ly, button)) {
                    if (r instanceof SliderRow) dragging = r;
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragging != null) {
            double lx = (mouseX - curCx) / curScale + curCx;
            dragging.drag(lx);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging != null) {
            dragging = null;
            Config.save();
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float maxScroll = Math.max(0f, contentH - viewH);
        scrollTarget = clamp(scrollTarget - (float) verticalAmount * 34f, 0f, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 /* ESC */ || keyCode == 344 /* prawy shift */) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
