package net.irisshaders.iris.mixin.vertices;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.ExtendedVertexLayout;
import net.irisshaders.iris.vertices.IrisExtendedBufferBuilder;
import org.lwjgl.system.MemoryUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

@Mixin(value = BufferBuilder.class, priority = 998)
public abstract class MixinBufferBuilder_FastEntityVertex extends DefaultedVertexConsumer implements BufferVertexConsumer {
	@Shadow
	private ByteBuffer buffer;

	@Shadow
	private int nextElementByte;

	@Shadow
	private int elementIndex;

	@Unique
	private static int iris$normalByte(float value) {
		return (int) (Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F) & 0xFF;
	}

	@Inject(method = "vertex(FFFFFFFFFIIFFF)V", at = @At("HEAD"), cancellable = true)
	private void iris$writeExtendedEntityVertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v,
												int overlay, int light, float normalX, float normalY, float normalZ, CallbackInfo ci) {
		if (!ExtendedVertexLayout.fastEntityVertices || !ExtendedVertexLayout.ENTITY_FAST_LAYOUT || this.defaultColorSet || this.elementIndex != 0) {
			return;
		}
		IrisExtendedBufferBuilder extended = (IrisExtendedBufferBuilder) this;
		int polygonSize = extended.iris$entityPolygonSize();
		if (polygonSize == 0 || this.nextElementByte + 56 > this.buffer.capacity()) {
			return;
		}
		long vertex = MemoryUtil.memAddress(this.buffer, 0) + this.nextElementByte;
		MemoryUtil.memPutFloat(vertex, x);
		MemoryUtil.memPutFloat(vertex + 4, y);
		MemoryUtil.memPutFloat(vertex + 8, z);
		MemoryUtil.memPutInt(vertex + 12, ((int) (red * 255.0F) & 0xFF) | ((int) (green * 255.0F) & 0xFF) << 8
			| ((int) (blue * 255.0F) & 0xFF) << 16 | ((int) (alpha * 255.0F) & 0xFF) << 24);
		MemoryUtil.memPutFloat(vertex + 16, u);
		MemoryUtil.memPutFloat(vertex + 20, v);
		MemoryUtil.memPutInt(vertex + 24, overlay);
		MemoryUtil.memPutInt(vertex + 28, light);
		MemoryUtil.memPutInt(vertex + 32, iris$normalByte(normalX) | iris$normalByte(normalY) << 8 | iris$normalByte(normalZ) << 16);
		CapturedRenderingState state = CapturedRenderingState.INSTANCE;
		MemoryUtil.memPutShort(vertex + 36, (short) state.getCurrentRenderedEntity());
		MemoryUtil.memPutShort(vertex + 38, (short) state.getCurrentRenderedBlockEntity());
		MemoryUtil.memPutShort(vertex + 40, (short) state.getCurrentRenderedItem());
		MemoryUtil.memPutLong(vertex + 42, 0L);
		MemoryUtil.memPutInt(vertex + 50, 0);
		this.nextElementByte += 56;
		this.endVertex();
		ci.cancel();
		extended.iris$incrementVertexCount();
		if (extended.iris$vertexCount() == polygonSize) {
			extended.iris$resetVertexCount();
			extended.iris$finishEntityPolygon(this.nextElementByte);
		}
	}
}
