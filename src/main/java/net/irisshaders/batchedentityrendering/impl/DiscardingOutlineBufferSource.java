package net.irisshaders.batchedentityrendering.impl;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraft.client.renderer.RenderType;

public class DiscardingOutlineBufferSource extends OutlineBufferSource {
	private static final VertexConsumer DISCARD = new VertexConsumer() {
		@Override
		public VertexConsumer vertex(double x, double y, double z) {
			return this;
		}

		@Override
		public VertexConsumer color(int r, int g, int b, int a) {
			return this;
		}

		@Override
		public VertexConsumer uv(float u, float v) {
			return this;
		}

		@Override
		public VertexConsumer overlayCoords(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer uv2(int u, int v) {
			return this;
		}

		@Override
		public VertexConsumer normal(float x, float y, float z) {
			return this;
		}

		@Override
		public void endVertex() {
		}

		@Override
		public void defaultColor(int r, int g, int b, int a) {
		}

		@Override
		public void unsetDefaultColor() {
		}
	};

	private final MultiBufferSource.BufferSource bufferSource;

	public DiscardingOutlineBufferSource(MultiBufferSource.BufferSource bufferSource) {
		super(bufferSource);
		this.bufferSource = bufferSource;
	}

	@Override
	public VertexConsumer getBuffer(RenderType type) {
		return type.isOutline() ? DISCARD : bufferSource.getBuffer(type);
	}
}
