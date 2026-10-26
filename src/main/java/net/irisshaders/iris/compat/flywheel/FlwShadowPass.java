package net.irisshaders.iris.compat.flywheel;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.impl.event.RenderContextImpl;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class FlwShadowPass {
	private FlwShadowPass() {
	}

	public static void render(Camera camera, float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !IrisFlwPrograms.active()) {
			return;
		}
		VisualizationManager manager = VisualizationManager.get(mc.level);
		if (manager == null) {
			return;
		}
		PoseStack stack = new PoseStack();
		stack.last().pose().set(CapturedRenderingState.INSTANCE.getGbufferModelView());
		stack.last().normal().set(CapturedRenderingState.INSTANCE.getGbufferModelView()).invert().transpose();
		Matrix4f projection = new Matrix4f(CapturedRenderingState.INSTANCE.getGbufferProjection());
		manager.renderDispatcher().afterEntities(RenderContextImpl.create(mc.levelRenderer, mc.level, mc.renderBuffers(), stack, projection, camera, partialTick));
	}
}
