package pl.visuals;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.shape.VoxelShape;
import org.joml.Matrix4f;

/**
 * Pojedyncza "ciuchcia" ragdoll: postać gracza (ze skinem) wyrzucona w górę, spadająca,
 * odbijająca się, leżąca przez ustawiony czas i znikająca.
 */
public final class Ragdoll {
    private static final float S = 0.9375f / 16f; // piksel modelu -> bloki
    private static final float ORIGIN_Y = 15f;    // punkt obrotu (w pikselach od stóp)

    public final Identifier skin;
    public double x, y, z, px, py, pz;
    private double vx, vy, vz;
    private float yaw, pyaw;
    private float pitch, ppitch, roll, proll;
    private float wPitch, wRoll, wYaw;
    private int age;
    private int bounces;
    private boolean lying;
    private int lieTicks;
    // stawy: 0 prawa ręka (przód/tył), 1 prawa ręka (na zewnątrz), 2-3 lewa ręka, 4-5 prawa noga, 6-7 lewa noga,
    // 8 głowa (przód/tył), 9 głowa (przechył)
    private final float[] lim = {0, 25, 0, 25, 0, 8, 0, 8, 0, 0};
    private final float[] limV = new float[10];
    private float[] plim = lim.clone();
    private final float[] lieT = new float[10];
    private final java.util.Random rng = new java.util.Random();
    private float lieRoll;
    public boolean dead;
    private double gravity = 0.08;

    /** Tworzy ragdoll. (sx, sy, sz) = pozycja stóp. */
    public Ragdoll(Identifier skin, double sx, double sy, double sz, float yawDeg,
                   double vx, double vy, double vz, double gravity, float spin) {
        this(skin, sx, sy, sz, yawDeg, vx, vy, vz);
        this.gravity = gravity;
        this.wPitch *= spin;
        this.wRoll *= spin;
        this.wYaw *= spin;
    }

    public Ragdoll(Identifier skin, double sx, double sy, double sz, float yawDeg,
                   double vx, double vy, double vz) {
        this.skin = skin;
        this.x = px = sx;
        this.y = py = sy + 0.95;
        this.z = pz = sz;
        this.yaw = pyaw = yawDeg;
        this.vx = vx;
        this.vy = vy;
        this.vz = vz;
        java.util.Random r = new java.util.Random();
        float sign = r.nextBoolean() ? 1 : -1;
        this.wPitch = sign * (6f + r.nextFloat() * 6f);
        this.wRoll = (r.nextFloat() - 0.5f) * 6f;
        this.wYaw = (r.nextFloat() - 0.5f) * 5f;
        // wyrzut: ręce rozrzucone, kończyny dostają losowy impuls
        lim[1] = lim[3] = 55f;
        for (int i = 0; i < limV.length; i++) limV[i] = (r.nextFloat() - 0.5f) * 10f;
    }

    private static boolean solidAt(ClientWorld w, double x, double y, double z) {
        BlockPos bp = BlockPos.ofFloored(x, y, z);
        return !w.getBlockState(bp).getCollisionShape(w, bp).isEmpty();
    }

    /** Wysokość górnej powierzchni najbliższego bloku pod (x,y,z); bardzo mała wartość jeśli brak. */
    private static double groundY(ClientWorld w, double x, double y, double z) {
        BlockPos start = BlockPos.ofFloored(x, y, z);
        for (int dy = 0; dy < 12; dy++) {
            BlockPos bp = start.down(dy);
            VoxelShape shape = w.getBlockState(bp).getCollisionShape(w, bp);
            if (!shape.isEmpty()) {
                double top = bp.getY() + shape.getMax(Direction.Axis.Y);
                if (top <= y + 0.5) return top;
            }
        }
        return -1.0e9;
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a > 180f) a -= 360f;
        if (a <= -180f) a += 360f;
        return a;
    }

    public void tick(ClientWorld world) {
        px = x; py = y; pz = z;
        ppitch = pitch; proll = roll; pyaw = yaw; plim = lim.clone();
        age++;

        if (!lying) {
            vy -= gravity;
            vy *= 0.985;
            if (Config.I.ragdollTrail && (age & 1) == 0) {
                world.addParticle(net.minecraft.particle.ParticleTypes.END_ROD, x, y, z, 0, 0, 0);
            }
            vx *= 0.992;
            vz *= 0.992;

            // zderzenie ze ścianą
            if (solidAt(world, x + vx + Math.signum(vx) * 0.3, y, z)) vx = -vx * 0.3;
            if (solidAt(world, x, y, z + vz + Math.signum(vz) * 0.3)) vz = -vz * 0.3;
            x += vx;
            y += vy;
            z += vz;

            // opór powietrza wyhamowuje obrót
            wPitch *= 0.992f;
            wRoll *= 0.99f;
            wYaw *= 0.99f;
            pitch += wPitch;
            roll += wRoll;
            yaw += wYaw;

            double g = groundY(world, x, y, z);
            if (y - g <= 0.3 && vy < 0) {
                y = g + 0.3;
                if (bounces < 2 && -vy > 0.3) {
                    vy = -vy * 0.36;
                    vx *= 0.65;
                    vz *= 0.65;
                    wPitch *= 0.45f;
                    wRoll *= 0.45f;
                    wYaw *= 0.4f;
                    kick(10f);
                    bounces++;
                } else {
                    lying = true;
                    vy = 0;
                    chooseLiePose();
                    kick(14f);
                    pitch = wrap(pitch);
                    roll = wrap(roll);
                    ppitch = pitch;
                    proll = roll;
                }
            }
            if (age > 220) dead = true; // spadł w pustkę
        } else {
            lieTicks++;
            double g = groundY(world, x, y, z);
            // lekkie wyślizgnięcie się
            vx *= 0.6;
            vz *= 0.6;
            if (!solidAt(world, x + vx + Math.signum(vx) * 0.3, y, z)) x += vx;
            if (!solidAt(world, x, y, z + vz + Math.signum(vz) * 0.3)) z += vz;

            // opada na ziemię, jak bezwładne ciało (bez sztywnego "przeskoku")
            float targetPitch = pitch > 0 ? 88f : -88f;
            pitch += (targetPitch - pitch) * 0.16f;
            roll += (lieRoll - roll) * 0.18f;
            yaw += wYaw;
            wYaw *= 0.8f;
            double targetY = g + 0.14;
            y += (targetY - y) * 0.3;

            int total = Math.round(Config.I.ragdollLie * 20f) + 10;
            if (lieTicks >= total) dead = true;
        }
        updateLimbs();
    }

    private float rn(float lo, float hi) {
        return lo + rng.nextFloat() * (hi - lo);
    }

    private void kick(float amount) {
        for (int i = 0; i < limV.length; i++) limV[i] += (rng.nextFloat() - 0.5f) * 2f * amount;
    }

    /** Losowa, rozluźniona poza leżącego ciała. */
    private void chooseLiePose() {
        lieT[0] = rn(-20, 30);
        lieT[1] = rn(8, 55);
        lieT[2] = rn(-20, 30);
        lieT[3] = rn(8, 55);
        lieT[4] = rn(-6, 12);
        lieT[5] = rn(3, 16);
        lieT[6] = rn(-6, 12);
        lieT[7] = rn(3, 16);
        lieT[8] = rn(-15, 25);
        lieT[9] = rn(-12, 12);
        lieRoll = rn(-10, 10);
    }

    private float flyTarget(int i) {
        float sp = Math.abs(wPitch) + Math.abs(wRoll);
        return switch (i) {
            case 0 -> Math.max(-70f, Math.min(70f, -wPitch * 1.8f));
            case 2 -> Math.max(-70f, Math.min(70f, -wPitch * 1.5f));
            case 1, 3 -> 28f + sp * 1.5f;
            case 4 -> Math.max(-40f, Math.min(40f, wPitch));
            case 6 -> Math.max(-40f, Math.min(40f, wPitch * 0.9f));
            case 5, 7 -> 6f + sp * 0.8f;
            case 8 -> Math.max(-30f, Math.min(30f, wPitch * 1.2f));
            default -> 0f;
        };
    }

    /** Kończyny jako tłumione sprężyny: bezwładnie ciągną się za ciałem, po upadku opadają i uspokajają się. */
    private void updateLimbs() {
        float k = lying ? 0.10f : 0.16f;
        float c = lying ? 0.32f : 0.10f;
        for (int i = 0; i < lim.length; i++) {
            float t = lying ? lieT[i] : flyTarget(i);
            limV[i] += k * (t - lim[i]) - c * limV[i];
            if (!lying) limV[i] += (rng.nextFloat() - 0.5f) * 1.2f;
            lim[i] += limV[i];
        }
        lim[1] = Math.max(-5f, Math.min(150f, lim[1]));
        lim[3] = Math.max(-5f, Math.min(150f, lim[3]));
        lim[5] = Math.max(-3f, Math.min(60f, lim[5]));
        lim[7] = Math.max(-3f, Math.min(60f, lim[7]));
    }

    /** Przezroczystość: 1 normalnie, zanika w ostatnich 10 tickach leżenia. */
    private float alpha(float td) {
        if (!lying) return 1f;
        int total = Math.round(Config.I.ragdollLie * 20f);
        float t = lieTicks + td - total;
        if (t <= 0) return 1f;
        return Math.max(0f, 1f - t / 10f);
    }

    public void render(MatrixStack ms, VertexConsumer vc, double camX, double camY, double camZ,
                       float td, int light) {
        float a = alpha(td);
        if (a <= 0.02f) return;
        double rx = px + (x - px) * td, ry = py + (y - py) * td, rz = pz + (z - pz) * td;
        float rPitch = ppitch + (pitch - ppitch) * td;
        float rRoll = proll + (roll - proll) * td;
        float rYaw = pyaw + (yaw - pyaw) * td;
        float[] l = new float[lim.length];
        for (int i = 0; i < l.length; i++) l[i] = plim[i] + (lim[i] - plim[i]) * td;

        renderPose2(ms, vc, camX, camY, camZ, rx, ry, rz, rYaw, rPitch, rRoll, a, light, l);
    }

    /** Rysuje model w podanej pozie (używane też przez fake playera). */
    public void renderPose(MatrixStack ms, VertexConsumer vc, double camX, double camY, double camZ,
                           double rx, double ry, double rz, float rYaw, float rPitch, float rRoll,
                           float a, int light, float armX, float armXr, float legX, float legXl, float splay) {
        renderPose2(ms, vc, camX, camY, camZ, rx, ry, rz, rYaw, rPitch, rRoll, a, light,
                new float[]{armX, splay, armXr, splay, legX, splay * 0.3f, legXl, splay * 0.3f, 0f, 0f});
    }

    /** Pozycja z pełną kontrolą stawów: patrz opis tablicy lim. */
    public void renderPose2(MatrixStack ms, VertexConsumer vc, double camX, double camY, double camZ,
                            double rx, double ry, double rz, float rYaw, float rPitch, float rRoll,
                            float a, int light, float[] l) {
        ms.push();
        ms.translate(rx - camX, ry - camY, rz - camZ);
        ms.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-rYaw));
        ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rPitch));
        ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rRoll));

        // głowa + hat
        part(ms, vc, a, light, 0, 0, -4, 24, -4, 4, 32, 4, 0, 0, 24, l[8], l[9], 0);
        part(ms, vc, a, light, 32, 0, -4, 24, -4, 4, 32, 4, 0.5f, 0, 24, l[8], l[9], 0);
        // tułów + jacket
        part(ms, vc, a, light, 16, 16, -4, 12, -2, 4, 24, 2, 0, 0, 18, 0, 0, 0);
        part(ms, vc, a, light, 16, 32, -4, 12, -2, 4, 24, 2, 0.25f, 0, 18, 0, 0, 0);
        // prawa ręka (-X)
        part(ms, vc, a, light, 40, 16, -8, 12, -2, -4, 24, 2, 0, -6, 22, l[0], -l[1], 0);
        part(ms, vc, a, light, 40, 32, -8, 12, -2, -4, 24, 2, 0.25f, -6, 22, l[0], -l[1], 0);
        // lewa ręka (+X)
        part(ms, vc, a, light, 32, 48, 4, 12, -2, 8, 24, 2, 0, 6, 22, l[2], l[3], 0);
        part(ms, vc, a, light, 48, 48, 4, 12, -2, 8, 24, 2, 0.25f, 6, 22, l[2], l[3], 0);
        // prawa noga
        part(ms, vc, a, light, 0, 16, -4, 0, -2, 0, 12, 2, 0, -2, 12, l[4], -l[5], 0);
        part(ms, vc, a, light, 0, 32, -4, 0, -2, 0, 12, 2, 0.25f, -2, 12, l[4], -l[5], 0);
        // lewa noga
        part(ms, vc, a, light, 16, 48, 0, 0, -2, 4, 12, 2, 0, 2, 12, l[6], l[7], 0);
        part(ms, vc, a, light, 0, 48, 0, 0, -2, 4, 12, 2, 0.25f, 2, 12, l[6], l[7], 0);

        ms.pop();
    }

    /**
     * Rysuje prostopadłościan o rogach (x0,y0,z0)-(x1,y1,z1) w pikselach modelu,
     * z siatką UV zaczynającą się w (u,v), obrócony wokół punktu (pivotX, pivotY).
     */
    private void part(MatrixStack ms, VertexConsumer vc, float a, int light,
                      int u, int v, float x0, float y0, float z0, float x1, float y1, float z1,
                      float inflate, float pivotX, float pivotY, float rotX, float rotZ, float unused) {
        ms.push();
        ms.translate(pivotX * S, (pivotY - ORIGIN_Y) * S, 0);
        if (rotX != 0) ms.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotX));
        if (rotZ != 0) ms.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotZ));

        float w = x1 - x0, h = y1 - y0, d = z1 - z0;
        float X0 = (x0 - inflate - pivotX) * S, X1 = (x1 + inflate - pivotX) * S;
        float Y0 = (y0 - inflate - pivotY) * S, Y1 = (y1 + inflate - pivotY) * S;
        float Z0 = (z0 - inflate) * S, Z1 = (z1 + inflate) * S;

        MatrixStack.Entry e = ms.peek();
        Matrix4f m = e.getPositionMatrix();
        int ai = (int) (a * 255f);

        // prawy bok (x = X0)
        quad(vc, e, m, ai, light, -1, 0, 0,
                X0, Y1, Z0, u, v + d, X0, Y1, Z1, u + d, v + d,
                X0, Y0, Z1, u + d, v + d + h, X0, Y0, Z0, u, v + d + h);
        // przód (z = Z1)
        quad(vc, e, m, ai, light, 0, 0, 1,
                X0, Y1, Z1, u + d, v + d, X1, Y1, Z1, u + d + w, v + d,
                X1, Y0, Z1, u + d + w, v + d + h, X0, Y0, Z1, u + d, v + d + h);
        // lewy bok (x = X1)
        quad(vc, e, m, ai, light, 1, 0, 0,
                X1, Y1, Z1, u + d + w, v + d, X1, Y1, Z0, u + 2 * d + w, v + d,
                X1, Y0, Z0, u + 2 * d + w, v + d + h, X1, Y0, Z1, u + d + w, v + d + h);
        // tył (z = Z0)
        quad(vc, e, m, ai, light, 0, 0, -1,
                X1, Y1, Z0, u + 2 * d + w, v + d, X0, Y1, Z0, u + 2 * d + 2 * w, v + d,
                X0, Y0, Z0, u + 2 * d + 2 * w, v + d + h, X1, Y0, Z0, u + 2 * d + w, v + d + h);
        // góra (y = Y1)
        quad(vc, e, m, ai, light, 0, 1, 0,
                X0, Y1, Z0, u + d, v, X1, Y1, Z0, u + d + w, v,
                X1, Y1, Z1, u + d + w, v + d, X0, Y1, Z1, u + d, v + d);
        // dół (y = Y0)
        quad(vc, e, m, ai, light, 0, -1, 0,
                X0, Y0, Z1, u + d + w, v, X1, Y0, Z1, u + d + 2 * w, v,
                X1, Y0, Z0, u + d + 2 * w, v + d, X0, Y0, Z0, u + d + w, v + d);

        ms.pop();
    }

    private static void quad(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int a, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float au, float av,
                             float bx, float by, float bz, float bu, float bv,
                             float cx, float cy, float cz, float cu, float cv,
                             float dx, float dy, float dz, float du, float dv) {
        vtx(vc, e, m, a, light, nx, ny, nz, ax, ay, az, au, av);
        vtx(vc, e, m, a, light, nx, ny, nz, bx, by, bz, bu, bv);
        vtx(vc, e, m, a, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vtx(vc, e, m, a, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vtx(VertexConsumer vc, MatrixStack.Entry e, Matrix4f m, int a, int light,
                            float nx, float ny, float nz, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z)
                .color(255, 255, 255, a)
                .texture(u / 64f, v / 64f)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(e, nx, ny, nz);
    }
}
