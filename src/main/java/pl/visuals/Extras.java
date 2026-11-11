package pl.visuals;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/** Damage indicator, trail za graczem i zoom. */
public final class Extras {
    private Extras() {}

    private static final Random RNG = new Random();

    // =================================================================== Damage indicator
    private static final class Dmg {
        double x, y, z;
        String text;
        int life = 34;
        final int maxLife = 34;
    }

    private static final List<Dmg> DMG = new ArrayList<>();
    private static final Map<Integer, Float> LAST_HP = new HashMap<>();
    private static int myHitId = -1;
    private static long myHitTime = -100;

    public static void noteMyHit(int entityId, long worldTime) {
        myHitId = entityId;
        myHitTime = worldTime;
    }

    private static String fmt(float v) {
        if (Math.abs(v - Math.round(v)) < 0.05f) return String.valueOf(Math.round(v));
        return String.format("%.1f", v).replace(',', '.');
    }

    public static void addDamage(double x, double y, double z, float amount) {
        Dmg d = new Dmg();
        d.x = x + (RNG.nextDouble() - 0.5) * 0.6;
        d.y = y;
        d.z = z + (RNG.nextDouble() - 0.5) * 0.6;
        d.text = "-" + fmt(amount);
        DMG.add(d);
    }

    // =================================================================== Trail
    private static final Map<Integer, ArrayDeque<double[]>> TRAILS = new HashMap<>();

    // =================================================================== kwadrat na celu
    private static int tbId = -2;
    private static int tbAge;

    private static void tbTick(MinecraftClient mc) {
        int id = -2;
        if (mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult ehr
                && ehr.getEntity() instanceof net.minecraft.entity.LivingEntity le && le != mc.player
                && !le.isDead()) {
            id = le.getId();
        } else if (FakePlayer.I != null && FakePlayer.I.alive() && FakePlayer.I.rayHit(mc) > 0) {
            id = -1000;
        }
        if (id != tbId) {
            tbId = id;
            tbAge = 0;
        } else {
            tbAge++;
        }
    }

    private static void rect(double cx, double cy, double cz, double[] r, double[] u,
                             double u0, double v0, double u1, double v1, int rgb, float a) {
        double[][] pp = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (double[] q : pp) {
            Draw.vert(cx + r[0] * q[0] + u[0] * q[1], cy + r[1] * q[0] + u[1] * q[1],
                    cz + r[2] * q[0] + u[2] * q[1], rgb, a);
        }
    }

    public static void renderTargetBox(MinecraftClient mc, ClientWorld world, float td, float time, float inten,
                                       double[] right, double[] up) {
        Config c = Config.I;
        if (!c.tbOn || tbId == -2) return;
        double x, y, z;
        float h, w;
        if (tbId == -1000) {
            FakePlayer f = FakePlayer.I;
            if (f == null || !f.alive()) return;
            x = f.x();
            y = f.y();
            z = f.z();
            h = 1.8f;
            w = 0.6f;
        } else {
            net.minecraft.entity.Entity e = world.getEntityById(tbId);
            if (!(e instanceof net.minecraft.entity.LivingEntity le) || le.isDead()) return;
            Vec3d p = le.getLerpedPos(td);
            x = p.x;
            y = p.y;
            z = p.z;
            h = le.getHeight();
            w = le.getWidth();
        }
        float anim = Math.min(1f, (tbAge + td) / 4f);
        float ease = 1f - (1f - anim) * (1f - anim);
        float a = ease * inten;
        int col = Config.palette(c.tbColor, c.tbHue, 0.3f, time);
        int col2 = Config.palette(c.tbColor, c.tbHue, 0.9f, time);

        if (c.tbStyle == 1) {
            // ramka 3D wokół hitboxu
            double hw = w / 2 + 0.03, hh = h + 0.03;
            double[][] cs = {{-hw, 0, -hw}, {hw, 0, -hw}, {hw, 0, hw}, {-hw, 0, hw}};
            double th = 0.018 * c.tbSize;
            for (int i = 0; i < 4; i++) {
                double[] a0 = cs[i], a1 = cs[(i + 1) % 4];
                PlayerFx.seg(x + a0[0], y, z + a0[2], x + a1[0], y, z + a1[2], right, th, col, a, a);
                PlayerFx.seg(x + a0[0], y + hh, z + a0[2], x + a1[0], y + hh, z + a1[2], right, th, col2, a, a);
                PlayerFx.seg(x + a0[0], y, z + a0[2], x + a0[0], y + hh, z + a0[2], right, th, col, a, a);
            }
            return;
        }

        // kwadrat zwrócony do kamery z narożnikami
        double s = (h * 0.62 + 0.08) * c.tbSize * (0.85 + 0.15 * ease) * (1 + 0.02 * Math.sin(time * 4));
        double cx = x, cy = y + h * 0.5, cz = z;
        double L = s * 0.42, t = 0.035 * c.tbSize * (0.8 + 0.4 * Math.max(0.5, s));
        // delikatne wypełnienie
        rect(cx, cy, cz, right, up, -s, -s, s, s, col, 0.05f * a);
        // cienka pełna ramka
        double t2 = t * 0.35;
        rect(cx, cy, cz, right, up, -s, s - t2, s, s, col, 0.30f * a);
        rect(cx, cy, cz, right, up, -s, -s, s, -s + t2, col, 0.30f * a);
        rect(cx, cy, cz, right, up, -s, -s, -s + t2, s, col, 0.30f * a);
        rect(cx, cy, cz, right, up, s - t2, -s, s, s, col, 0.30f * a);
        // narożniki
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sy = -1; sy <= 1; sy += 2) {
                double ex = sx * s, ey = sy * s;
                // pozioma belka
                rect(cx, cy, cz, right, up, Math.min(ex, ex - sx * L), Math.min(ey, ey - sy * t),
                        Math.max(ex, ex - sx * L), Math.max(ey, ey - sy * t), col2, 0.95f * a);
                // pionowa belka
                rect(cx, cy, cz, right, up, Math.min(ex, ex - sx * t), Math.min(ey, ey - sy * L),
                        Math.max(ex, ex - sx * t), Math.max(ey, ey - sy * L), col2, 0.95f * a);
            }
        }
    }

    // =================================================================== tick
    public static void tick(MinecraftClient mc) {
        if (mc.world != null && mc.player != null) tbTick(mc);
        if (mc.world == null || mc.player == null) {
            DMG.clear();
            LAST_HP.clear();
            TRAILS.clear();
            return;
        }
        Config c = Config.I;
        long now = mc.world.getTime();
        DMG.removeIf(d -> --d.life <= 0);

        Set<Integer> present = new HashSet<>();
        for (AbstractClientPlayerEntity p : mc.world.getPlayers()) {
            int id = p.getId();
            present.add(id);

            // ---- obrażenia ----
            if (p != mc.player) {
                float hp = p.getHealth() + p.getAbsorptionAmount();
                Float prev = LAST_HP.get(id);
                if (c.dmgOn && prev != null && hp < prev - 0.05f && !p.isDead()) {
                    boolean mine = id == myHitId && now - myHitTime <= 10;
                    if (!c.dmgOnlyMine || mine) {
                        addDamage(p.getX(), p.getY() + p.getHeight() + 0.1, p.getZ(), prev - hp);
                    }
                }
                LAST_HP.put(id, hp);
            }

            // ---- trail ----
            ArrayDeque<double[]> dq = TRAILS.computeIfAbsent(id, k -> new ArrayDeque<>());
            if (c.trailOn) {
                double[] last = dq.peekLast();
                if (last == null || Math.hypot(p.getX() - last[0], p.getZ() - last[2]) > 0.06
                        || Math.abs(p.getY() - last[1]) > 0.06) {
                    dq.addLast(new double[]{p.getX(), p.getY(), p.getZ(), now});
                }
            }
            while (!dq.isEmpty() && (now - dq.peekFirst()[3] > c.trailLength || !c.trailOn)) dq.pollFirst();
        }
        LAST_HP.keySet().retainAll(present);
        TRAILS.keySet().retainAll(present);
    }

    // =================================================================== render: trail (faza 1, Draw.begin już wywołane)
    public static void renderTrails(MinecraftClient mc, ClientWorld world, float td, float time, float inten,
                                    List<AbstractClientPlayerEntity> targets) {
        Config c = Config.I;
        if (!c.trailOn) return;
        double nowT = world.getTime() + td;
        for (AbstractClientPlayerEntity p : targets) {
            ArrayDeque<double[]> dq = TRAILS.get(p.getId());
            if (dq == null || dq.isEmpty()) continue;
            List<double[]> pts = new ArrayList<>(dq);
            Vec3d head = p.getLerpedPos(td);
            pts.add(new double[]{head.x, head.y, head.z, nowT});
            int n = pts.size();
            if (n < 2) continue;

            double[] wx = new double[n], wz = new double[n];   // przesunięcie boczne
            for (int i = 0; i < n; i++) {
                double[] a = pts.get(Math.max(0, i - 1)), b = pts.get(Math.min(n - 1, i + 1));
                double dx = b[0] - a[0], dz = b[2] - a[2];
                double len = Math.sqrt(dx * dx + dz * dz);
                if (len < 1e-6) {
                    wx[i] = 0;
                    wz[i] = 0;
                } else {
                    wx[i] = -dz / len;
                    wz[i] = dx / len;
                }
            }

            double w = 0.22 * c.trailWidth;
            double h = c.trailHeight;
            for (int i = 0; i < n - 1; i++) {
                double[] a = pts.get(i), b = pts.get(i + 1);
                float fa = (float) Math.max(0, Math.min(1, (nowT - a[3]) / Math.max(1, c.trailLength)));
                float fb = (float) Math.max(0, Math.min(1, (nowT - b[3]) / Math.max(1, c.trailLength)));
                float aa = (float) Math.pow(1f - fa, 1.3) * 0.6f * inten;
                float ab = (float) Math.pow(1f - fb, 1.3) * 0.6f * inten;
                int ca = Config.palette(c.trailColor, c.trailHue, fa, time);
                int cb = Config.palette(c.trailColor, c.trailHue, fb, time);
                double ta = 0.4 + 0.6 * (1 - fa), tb = 0.4 + 0.6 * (1 - fb);

                // pionowa wstęga: dół jasny, góra przezroczysta
                Draw.vert(a[0], a[1] + 0.05, a[2], ca, aa);
                Draw.vert(a[0], a[1] + 0.05 + h * ta, a[2], ca, 0f);
                Draw.vert(b[0], b[1] + 0.05 + h * tb, b[2], cb, 0f);
                Draw.vert(b[0], b[1] + 0.05, b[2], cb, ab);

                // płaska wstęga przy ziemi (widoczna z tyłu i z góry), środek jasny
                double ax0 = a[0] - wx[i] * w * ta, az0 = a[2] - wz[i] * w * ta;
                double ax1 = a[0] + wx[i] * w * ta, az1 = a[2] + wz[i] * w * ta;
                double bx0 = b[0] - wx[i + 1] * w * tb, bz0 = b[2] - wz[i + 1] * w * tb;
                double bx1 = b[0] + wx[i + 1] * w * tb, bz1 = b[2] + wz[i + 1] * w * tb;
                double ay = a[1] + 0.06, by = b[1] + 0.06;
                // lewa połowa
                Draw.vert(ax0, ay, az0, ca, 0f);
                Draw.vert(a[0], ay, a[2], 0xFFFFFF, aa);
                Draw.vert(b[0], by, b[2], 0xFFFFFF, ab);
                Draw.vert(bx0, by, bz0, cb, 0f);
                // prawa połowa
                Draw.vert(a[0], ay, a[2], 0xFFFFFF, aa);
                Draw.vert(ax1, ay, az1, ca, 0f);
                Draw.vert(bx1, by, bz1, cb, 0f);
                Draw.vert(b[0], by, b[2], 0xFFFFFF, ab);
            }
        }
    }

    // =================================================================== render: damage (faza 2)
    public static void renderDamage(MatrixStack ms, VertexConsumerProvider.Immediate imm, TextRenderer tr,
                                    Camera cam, Vec3d cp, float td, float time) {
        Config c = Config.I;
        if (!c.dmgOn || DMG.isEmpty()) return;
        for (Dmg d : DMG) {
            float age = d.maxLife - d.life + td;
            float f = Math.max(0f, Math.min(1f, age / d.maxLife));
            float alpha = f < 0.7f ? 1f : 1f - (f - 0.7f) / 0.3f;
            float pop = f < 0.15f ? 1.5f - 0.5f * (f / 0.15f) : 1f;
            double y = d.y + age * 0.028;
            ms.push();
            ms.translate(d.x - cp.x, y - cp.y, d.z - cp.z);
            ms.multiply(cam.getRotation());
            float s = 0.03f * c.dmgSize * pop;
            ms.scale(-s, -s, s);
            int rgb = Config.palette(c.dmgColor, c.dmgHue, f, time);
            int argb = ((int) (alpha * 255) << 24) | (rgb & 0xFFFFFF);
            float w = tr.getWidth(d.text);
            tr.draw(d.text, -w / 2f, 0f, argb, true, ms.peek().getPositionMatrix(), imm,
                    TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            ms.pop();
        }
    }

    // =================================================================== zoom (wołane co klatkę)
    private static int baseFov = -1;
    private static float curFov;
    private static long lastNs;

    public static void zoomFrame(MinecraftClient mc) {
        Config c = Config.I;
        long ns = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.1f, (ns - lastNs) / 1e9f);
        lastNs = ns;
        if (mc.player == null || mc.world == null) {
            restoreFov(mc);
            return;
        }
        boolean zooming = false;
        if (c.zoomOn && mc.currentScreen == null) {
            int key = Config.ZOOM_GLFW[Math.floorMod(c.zoomKey, Config.ZOOM_GLFW.length)];
            zooming = InputUtil.isKeyPressed(mc.getWindow().getHandle(), key);
        }
        if (zooming && baseFov < 0) {
            baseFov = mc.options.getFov().getValue();
            curFov = baseFov;
        }
        if (baseFov < 0) return;
        float target = zooming ? Math.max(30f, baseFov / Math.max(1f, c.zoomLevel)) : baseFov;
        curFov += (target - curFov) * (1f - (float) Math.exp(-dt * 14f));
        int v = Math.max(30, Math.min(110, Math.round(curFov)));
        if (mc.options.getFov().getValue() != v) mc.options.getFov().setValue(v);
        if (!zooming && Math.abs(curFov - baseFov) < 0.6f) {
            restoreFov(mc);
        }
    }

    public static void restoreFov(MinecraftClient mc) {
        if (baseFov >= 0) {
            mc.options.getFov().setValue(Math.max(30, Math.min(110, baseFov)));
            baseFov = -1;
        }
    }
}
