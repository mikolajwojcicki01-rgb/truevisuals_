package pl.visuals.mixin;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pl.visuals.Config;
import pl.visuals.VisualsRenderer;

/** Własny kolor nieba. */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {
    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void visuals$sky(Vec3d cameraPos, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
        Config c = Config.I;
        if (c == null || !c.skyOn) return;
        int rgb = Config.palette(c.skyColor, c.skyHue, 0.5f, VisualsRenderer.time());
        cir.setReturnValue(new Vec3d(((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0));
    }
}
