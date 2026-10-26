package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.backend.compile.core.ProgramLinker;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import dev.engine_room.flywheel.backend.gl.shader.GlShader;
import net.irisshaders.iris.compat.flywheel.FlywheelCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.function.Consumer;

@Mixin(value = ProgramLinker.class, remap = false)
public class MixinProgramLinker {
	@Inject(method = "link", at = @At("RETURN"))
	private void oculus$captureProgram(List<GlShader> shaders, Consumer<GlProgram> preLink, CallbackInfoReturnable<GlProgram> cir) {
		int[] handles = new int[shaders.size()];
		for (int i = 0; i < handles.length; i++) {
			handles[i] = shaders.get(i).handle();
		}
		FlywheelCompat.onProgramLinked(cir.getReturnValue().handle(), handles);
	}
}
