package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.api.instance.InstanceType;
import dev.engine_room.flywheel.api.material.Material;
import dev.engine_room.flywheel.backend.compile.ContextShader;
import dev.engine_room.flywheel.backend.compile.InstancingPrograms;
import dev.engine_room.flywheel.backend.compile.PipelineCompiler;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import net.irisshaders.iris.compat.flywheel.IrisFlwPrograms;
import net.irisshaders.iris.shadows.ShadowRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InstancingPrograms.class, remap = false)
public class MixinInstancingPrograms {
	@Shadow
	@Final
	private PipelineCompiler pipeline;

	@Inject(method = "get(Ldev/engine_room/flywheel/api/instance/InstanceType;Ldev/engine_room/flywheel/backend/compile/ContextShader;Ldev/engine_room/flywheel/api/material/Material;Ldev/engine_room/flywheel/backend/compile/PipelineCompiler$OitMode;)Ldev/engine_room/flywheel/backend/gl/shader/GlProgram;", at = @At("HEAD"), cancellable = true)
	private void oculus$useShaderPackProgram(InstanceType<?> instanceType, ContextShader contextShader, Material material, PipelineCompiler.OitMode mode, CallbackInfoReturnable<GlProgram> cir) {
		if (IrisFlwPrograms.active()) {
			cir.setReturnValue(IrisFlwPrograms.get(pipeline.get(instanceType, contextShader, material, PipelineCompiler.OitMode.OFF), ShadowRenderer.ACTIVE));
		}
	}
}
