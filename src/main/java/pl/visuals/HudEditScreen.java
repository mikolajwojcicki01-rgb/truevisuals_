package pl.visuals;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Edytor HUD: przeciągasz moduły myszką tam, gdzie chcesz. */
public class HudEditScreen extends Screen {
    private HudLayout.Elem drag;
    private int offX, offY;
    private boolean closing;

    public HudEditScreen() {
        super(Text.literal("Edytor HUD"));
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x55000000);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private boolean enabled(String id) {
        Config c = Config.I;
        return switch (id) {
            case "potions" -> c.potionsHud;
            case "combo" -> c.comboHud;
            case "cooldowns" -> c.cooldownsHud;
            case "keys" -> c.keystrokesHud;
            case "info" -> c.infoHud || c.coordsHud;
            case "armor" -> c.armorHud;
            default -> true;
        };
    }

    private int[] defaults(String id) {
        return switch (id) {
            case "potions" -> new int[]{6, 6};
            case "combo" -> new int[]{width / 2 + 24, height / 2 - 26};
            case "cooldowns" -> new int[]{width - 146, 40};
            case "keys" -> new int[]{8, height / 2 - 36};
            case "info" -> new int[]{6, height - 70};
            case "armor" -> new int[]{width - 64, height / 2 - 52};
            default -> new int[]{width / 2 - 70, (int) (height * 0.27f)};
        };
    }

    /** Pozycja i rozmiar ramki elementu (rzeczywiste, jeśli narysowany, inaczej nominalne). */
    private int[] box(HudLayout.Elem e) {
        if (HudLayout.drawnThisFrame(e)) return new int[]{e.x, e.y, e.w, e.h};
        int[] d = defaults(e.id);
        int[] p = HudLayout.place(e.id, e.nomW, e.nomH, d[0], d[1], width, height);
        e.frame = -100; // to tylko wyliczenie, nie prawdziwe rysowanie
        return new int[]{p[0], p[1], e.nomW, e.nomH};
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c, mx, my, delta);
        float t = VisualsRenderer.time();
        int acc = Config.color(0f, t), acc2 = Config.color(1f, t);
        // linie środka
        c.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        c.fill(0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        for (HudLayout.Elem e : HudLayout.ELEMS.values()) {
            if (!enabled(e.id)) continue;
            int[] b = box(e);
            boolean over = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            boolean on = e == drag;
            int col = on ? acc2 : acc;
            int a = on ? 0x90 : (over ? 0x70 : 0x45);
            c.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], (a / 3 << 24) | (col & 0xFFFFFF));
            int line = (0xC0 << 24) | (col & 0xFFFFFF);
            c.fill(b[0] - 1, b[1] - 1, b[0] + b[2] + 1, b[1], line);
            c.fill(b[0] - 1, b[1] + b[3], b[0] + b[2] + 1, b[1] + b[3] + 1, line);
            c.fill(b[0] - 1, b[1], b[0], b[1] + b[3], line);
            c.fill(b[0] + b[2], b[1], b[0] + b[2] + 1, b[1] + b[3], line);
            String lab = e.label;
            int ty = b[1] > 12 ? b[1] - 10 : b[1] + b[3] + 3;
            c.drawText(textRenderer, lab, b[0], ty, 0xFFFFFFFF, true);
        }

        String info = "Przeciągnij moduły myszką   |   R – reset pozycji   |   Esc / Prawy Shift – wróć";
        int iw = textRenderer.getWidth(info);
        c.fill(width / 2 - iw / 2 - 8, 6, width / 2 + iw / 2 + 8, 22, 0xB0000000);
        c.drawText(textRenderer, info, width / 2 - iw / 2, 10, 0xFFFFFFFF, false);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            HudLayout.Elem[] arr = HudLayout.ELEMS.values().toArray(new HudLayout.Elem[0]);
            for (int i = arr.length - 1; i >= 0; i--) {
                HudLayout.Elem e = arr[i];
                if (!enabled(e.id)) continue;
                int[] b = box(e);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    drag = e;
                    offX = (int) mx - b[0];
                    offY = (int) my - b[1];
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag != null) {
            int[] b = box(drag);
            int nx = (int) mx - offX, ny = (int) my - offY;
            // przyciąganie do środka i krawędzi
            int cx = nx + b[2] / 2, cy = ny + b[3] / 2;
            if (Math.abs(cx - width / 2) < 5) nx = width / 2 - b[2] / 2;
            if (Math.abs(cy - height / 2) < 5) ny = height / 2 - b[3] / 2;
            if (Math.abs(nx) < 5) nx = 0;
            if (Math.abs(ny) < 5) ny = 0;
            if (Math.abs(nx + b[2] - width) < 5) nx = width - b[2];
            if (Math.abs(ny + b[3] - height) < 5) ny = height - b[3];
            HudLayout.set(drag.id, nx, ny, b[2], b[3], width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (drag != null) {
            drag = null;
            Config.save();
        }
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 82 /* R */) {
            HudLayout.reset();
            return true;
        }
        if (keyCode == 256 || keyCode == 344) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        Config.save();
        if (client != null) client.setScreen(new VisualsScreen());
    }

    @Override
    public void removed() {
        Config.save();
    }
}
