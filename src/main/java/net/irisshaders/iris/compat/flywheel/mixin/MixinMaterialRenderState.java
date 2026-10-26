package net.irisshaders.iris.compat.flywheel.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.backend.engine.MaterialRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MaterialRenderState.class, remap = false)
public class MixinMaterialRenderState {
	@Inject(method = "setupTransparency", at = @At("HEAD"), cancellable = true)
	private static void oculus$blendOrderIndependentAsTranslucent(Transparency transparency, CallbackInfo ci) {
		if (transparency == Transparency.ORDER_INDEPENDENT) {
			RenderSystem.enableBlend();
			RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
			ci.cancel();
		}
	}
}
