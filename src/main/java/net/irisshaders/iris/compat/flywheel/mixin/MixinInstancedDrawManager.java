package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.api.material.Transparency;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDraw;
import dev.engine_room.flywheel.backend.engine.instancing.InstancedDrawManager;
import dev.engine_room.flywheel.backend.gl.TextureBuffer;
import net.irisshaders.iris.compat.flywheel.IrisFlwProgram;
import net.irisshaders.iris.compat.flywheel.IrisFlwPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InstancedDrawManager.class, remap = false)
public class MixinInstancedDrawManager {
	@Redirect(method = "render", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/api/material/Material;transparency()Ldev/engine_room/flywheel/api/material/Transparency;"))
	private Transparency oculus$noOitWithShaderPack(Material material) {
		Transparency transparency = material.transparency();
		return transparency == Transparency.ORDER_INDEPENDENT && IrisFlwPrograms.active() ? Transparency.TRANSLUCENT : transparency;
	}

	@Redirect(method = "submitDraws", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/backend/engine/instancing/InstancedDraw;render(Ldev/engine_room/flywheel/backend/gl/TextureBuffer;)V"))
	private void oculus$skipUnbuiltPrograms(InstancedDraw draw, TextureBuffer buffer) {
		if (!IrisFlwProgram.skipping()) {
			draw.render(buffer);
		}
	}

	@Inject(method = "render", at = @At("RETURN"))
	private void oculus$finishShaderPackDraws(CallbackInfo ci) {
		IrisFlwProgram.finish();
	}

	@Inject(method = "renderCrumbling", at = @At("HEAD"), cancellable = true)
	private void oculus$noCrumblingWithShaderPack(CallbackInfo ci) {
		if (IrisFlwPrograms.active()) {
			ci.cancel();
		}
	}
}
