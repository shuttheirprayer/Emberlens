package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.backend.Backends;
import dev.engine_room.flywheel.lib.util.ShadersModHelper;
import net.irisshaders.iris.compat.flywheel.IrisFlwPrograms;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Backends.class, remap = false)
public class MixinBackends {
	@Redirect(method = "lambda$static$1", at = @At(value = "INVOKE", target = "Ldev/engine_room/flywheel/lib/util/ShadersModHelper;isShaderPackInUse()Z"))
	private static boolean oculus$allowInstancingWithShaderPack() {
		return ShadersModHelper.isShaderPackInUse() && !IrisFlwPrograms.active();
	}
}
