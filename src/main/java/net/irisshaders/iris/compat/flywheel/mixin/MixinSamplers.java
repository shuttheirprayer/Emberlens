package net.irisshaders.iris.compat.flywheel.mixin;

import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.gl.GlTextureUnit;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Samplers.class, remap = false)
public class MixinSamplers {
	@Shadow
	@Final
	@Mutable
	public static GlTextureUnit INSTANCE_BUFFER;

	@Shadow
	@Final
	@Mutable
	public static GlTextureUnit LIGHT_LUT;

	@Shadow
	@Final
	@Mutable
	public static GlTextureUnit LIGHT_SECTIONS;

	@Inject(method = "<clinit>", at = @At("TAIL"))
	private static void oculus$moveBufferUnitsAboveShaderPackSamplers(CallbackInfo ci) {
		INSTANCE_BUFFER = GlTextureUnit.T31;
		LIGHT_LUT = GlTextureUnit.T30;
		LIGHT_SECTIONS = GlTextureUnit.T29;
	}
}
