package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import net.irisshaders.iris.compat.flywheel.IrisFlwPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = PipelineCompiler.class, remap = false)
public class MixinPipelineCompiler {
	@Inject(method = "delete", at = @At("HEAD"))
	private void oculus$dropShaderPackPrograms(CallbackInfo ci) {
		IrisFlwPrograms.clear();
	}
}
