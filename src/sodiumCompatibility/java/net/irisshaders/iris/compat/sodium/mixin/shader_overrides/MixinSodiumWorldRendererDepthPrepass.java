package net.irisshaders.iris.compat.sodium.mixin.shader_overrides;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.PoseStack;
import me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.DepthPrepassTuner;
import net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides;
import net.irisshaders.iris.pipeline.PassTimers;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.embeddedt.embeddium.render.ShaderModBridge;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SodiumWorldRenderer.class)
public abstract class MixinSodiumWorldRendererDepthPrepass {
	@Shadow(remap = false)
	private RenderSectionManager renderSectionManager;

	@Unique
	private boolean ember$tuning;

	@Inject(method = "drawChunkLayer", at = @At("HEAD"), remap = false)
	private void ember$depthPrepass(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, CallbackInfo ci) {
		if (renderLayer != RenderType.solid() || !IrisChunkProgramOverrides.depthPrepass || !IrisChunkProgramOverrides.prepassReady
			|| this.renderSectionManager == null || ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			return;
		}
		this.ember$tuning = true;
		if (!DepthPrepassTuner.begin()) {
			return;
		}
		ChunkRenderMatrices matrices = ChunkRenderMatrices.from(matrixStack);
		GlStateManager._colorMask(false, false, false, false);
		PassTimers.begin("prepass");
		IrisChunkProgramOverrides.prepassActive = true;
		try {
			this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.SOLID, x, y, z);
			this.renderSectionManager.renderLayer(matrices, DefaultTerrainRenderPasses.CUTOUT, x, y, z);
			IrisChunkProgramOverrides.prepassDone = true;
		} finally {
			IrisChunkProgramOverrides.prepassActive = false;
			GlStateManager._colorMask(true, true, true, true);
		}
		PassTimers.end();
		PassTimers.begin("terrain_main");
	}

	@Redirect(method = "drawChunkLayer", at = @At(value = "INVOKE", target = "Lorg/embeddedt/embeddium/render/ShaderModBridge;areShadersEnabled()Z"), remap = false, require = 0)
	private boolean ember$skipBandsAfterPrepass() {
		return ShaderModBridge.areShadersEnabled() && !(IrisChunkProgramOverrides.prepassDone && IrisChunkProgramOverrides.prepassSkipBands);
	}

	@Inject(method = "drawChunkLayer", at = @At("RETURN"), remap = false)
	private void ember$endDepthPrepass(RenderType renderLayer, PoseStack matrixStack, double x, double y, double z, CallbackInfo ci) {
		IrisChunkProgramOverrides.prepassDone = false;
		if (renderLayer == RenderType.solid()) {
			PassTimers.end();
		}
		if (this.ember$tuning) {
			this.ember$tuning = false;
			DepthPrepassTuner.end();
		}
	}
}
