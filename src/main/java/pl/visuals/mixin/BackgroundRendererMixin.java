package pl.visuals.mixin;

import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.visuals.Config;
import pl.visuals.VisualsRenderer;

/** Własny kolor i zasięg mgły. */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {
    @Inject(method = "getFogColor", at = @At("RETURN"), cancellable = true, require = 0)
    private static void visuals$fogColor(Camera camera, float tickDelta, ClientWorld world, int clampedViewDistance,
                                         float skyDarkness, CallbackInfoReturnable<Vector4f> cir) {
        Config c = Config.I;
        if (c == null || !c.fogOn) return;
        int rgb = Config.palette(c.fogColor, c.fogHue, 0.5f, VisualsRenderer.time());
        cir.setReturnValue(new Vector4f(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f));
    }

    @Inject(method = "applyFog", at = @At("RETURN"), cancellable = true, require = 0)
    private static void visuals$fog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color,
                                    float viewDistance, boolean thickenFog, float tickDelta,
                                    CallbackInfoReturnable<Fog> cir) {
        Config c = Config.I;
        if (c == null || !c.fogOn || fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        Fog old = cir.getReturnValue();
        if (old == null) return;
        float end = Math.max(16f, c.fogDistance);
        // przy mgle wodnej/lawy/ślepoty zostaw oryginał (ich zasięg jest mniejszy)
        if (old.end() < end * 0.5f) return;
        int rgb = Config.palette(c.fogColor, c.fogHue, 0.5f, VisualsRenderer.time());
        cir.setReturnValue(new Fog(end * 0.15f, end, old.shape(),
                ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f));
    }
}
