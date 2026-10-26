package net.irisshaders.iris.compat.flywheel;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.engine_room.flywheel.backend.Samplers;
import dev.engine_room.flywheel.backend.engine.uniform.Uniforms;
import dev.engine_room.flywheel.backend.gl.shader.GlProgram;
import net.irisshaders.iris.pipeline.programs.ExtendedShader;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Minecraft;

public final class IrisFlwProgram extends GlProgram {
	public static final IrisFlwProgram SKIP = new IrisFlwProgram();

	private static IrisFlwProgram current;

	final ExtendedShader shader;
	private final boolean shadow;

	private IrisFlwProgram() {
		super(0);
		this.shader = null;
		this.shadow = false;
	}

	IrisFlwProgram(ExtendedShader shader, boolean shadow) {
		super(shader.getId());
		this.shader = shader;
		this.shadow = shadow;
		ProgramManager.glUseProgram(handle());
		setSamplerBinding("_flw_instances", Samplers.INSTANCE_BUFFER);
		setSamplerBinding("_flw_lightLut", Samplers.LIGHT_LUT);
		setSamplerBinding("_flw_lightSections", Samplers.LIGHT_SECTIONS);
		Uniforms.setUniformBlockBindings(this);
		ProgramManager.glUseProgram(0);
	}

	public static boolean skipping() {
		return current == SKIP;
	}

	public static void finish() {
		IrisFlwProgram last = current;
		current = null;
		if (last != null && last.shader != null) {
			last.shader.clear();
		}
	}

	@Override
	public void bind() {
		current = this;
		if (shader == null) {
			return;
		}
		if (shader.MODEL_VIEW_MATRIX != null) {
			shader.MODEL_VIEW_MATRIX.set(shadow ? ShadowRenderer.MODELVIEW : CapturedRenderingState.INSTANCE.getGbufferModelView());
		}
		if (shader.PROJECTION_MATRIX != null) {
			shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
		}
		if (shader.TEXTURE_MATRIX != null) {
			shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
		}
		if (shader.COLOR_MODULATOR != null) {
			shader.COLOR_MODULATOR.set(1.0F, 1.0F, 1.0F, 1.0F);
		}
		if (shader.FOG_START != null) {
			shader.FOG_START.set(RenderSystem.getShaderFogStart());
		}
		if (shader.FOG_END != null) {
			shader.FOG_END.set(RenderSystem.getShaderFogEnd());
		}
		if (shader.FOG_COLOR != null) {
			shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
		}
		if (shader.FOG_SHAPE != null) {
			shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
		}
		if (shader.GAME_TIME != null) {
			shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
		}
		if (shader.SCREEN_SIZE != null) {
			Window window = Minecraft.getInstance().getWindow();
			shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
		}
		if (shader.CHUNK_OFFSET != null) {
			shader.CHUNK_OFFSET.set(0.0F, 0.0F, 0.0F);
		}
		ProgramManager.glUseProgram(handle());
		shader.apply();
	}

	@Override
	protected void deleteInternal(int handle) {
	}
}
