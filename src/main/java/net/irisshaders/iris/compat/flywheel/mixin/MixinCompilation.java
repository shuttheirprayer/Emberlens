package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.backend.compile.core.Compilation;
import dev.engine_room.flywheel.backend.compile.core.ShaderResult;
import dev.engine_room.flywheel.backend.gl.shader.ShaderType;
import net.irisshaders.iris.compat.flywheel.FlywheelCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Compilation.class, remap = false)
public class MixinCompilation {
	@Shadow
	@Final
	private StringBuilder fullSource;

	@Inject(method = "compile", at = @At("RETURN"))
	private void oculus$captureVertexSource(ShaderType type, String name, CallbackInfoReturnable<ShaderResult> cir) {
		if (type == ShaderType.VERTEX && name.startsWith("pipeline/instancing/") && cir.getReturnValue() instanceof ShaderResult.Success success) {
			FlywheelCompat.onVertexShaderCompiled(success.shader().handle(), fullSource.toString());
		}
	}
}
