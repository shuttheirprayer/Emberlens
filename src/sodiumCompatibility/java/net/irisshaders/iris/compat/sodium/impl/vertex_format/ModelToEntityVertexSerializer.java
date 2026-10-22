package net.irisshaders.iris.compat.sodium.impl.vertex_format;

import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormalHelper;
import org.lwjgl.system.MemoryUtil;

public class ModelToEntityVertexSerializer implements VertexSerializer {
	@Override
	public void serialize(long src, long dst, int vertexCount) {
		// Only accept quads, to be safe
		int quadCount = vertexCount / 4;
		int dstStride = IrisVertexFormats.ENTITY.getVertexSize();
		boolean disjoint = src + (long) ModelVertex.STRIDE * quadCount * 4 <= dst || dst + (long) dstStride * quadCount * 4 <= src;
		for (int i = 0; i < quadCount; i++) {
			int normal = MemoryUtil.memGetInt(src + 32);
			float u0 = MemoryUtil.memGetFloat(src + 16);
			float v0 = MemoryUtil.memGetFloat(src + 20);
			float u1 = MemoryUtil.memGetFloat(src + 16 + ModelVertex.STRIDE);
			float v1 = MemoryUtil.memGetFloat(src + 20 + ModelVertex.STRIDE);
			float u2 = MemoryUtil.memGetFloat(src + 16 + ModelVertex.STRIDE * 2);
			float v2 = MemoryUtil.memGetFloat(src + 20 + ModelVertex.STRIDE * 2);
			int tangent = NormalHelper.computeTangent(NormI8.unpackX(normal), NormI8.unpackY(normal), NormI8.unpackZ(normal), MemoryUtil.memGetFloat(src), MemoryUtil.memGetFloat(src + 4), MemoryUtil.memGetFloat(src + 8), u0, v0,
				MemoryUtil.memGetFloat(src + ModelVertex.STRIDE), MemoryUtil.memGetFloat(src + 4 + ModelVertex.STRIDE), MemoryUtil.memGetFloat(src + 8 + ModelVertex.STRIDE), u1, v1,
				MemoryUtil.memGetFloat(src + ModelVertex.STRIDE * 2), MemoryUtil.memGetFloat(src + 4 + ModelVertex.STRIDE * 2), MemoryUtil.memGetFloat(src + 8 + ModelVertex.STRIDE * 2), u2, v2);
			float midU = 0, midV = 0;
			midU += u0;
			midV += v0;
			midU += u1;
			midV += v1;
			midU += u2;
			midV += v2;
			midU += MemoryUtil.memGetFloat(src + 16 + ModelVertex.STRIDE * 3);
			midV += MemoryUtil.memGetFloat(src + 20 + ModelVertex.STRIDE * 3);

			midU /= 4;
			midV /= 4;

			short entity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedEntity();
			short blockEntity = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedBlockEntity();
			short item = (short) CapturedRenderingState.INSTANCE.getCurrentRenderedItem();

			for (int j = 0; j < 4; j++) {
				if (disjoint) {
					MemoryUtil.memPutLong(dst, MemoryUtil.memGetLong(src));
					MemoryUtil.memPutLong(dst + 8, MemoryUtil.memGetLong(src + 8));
					MemoryUtil.memPutLong(dst + 16, MemoryUtil.memGetLong(src + 16));
					MemoryUtil.memPutLong(dst + 24, MemoryUtil.memGetLong(src + 24));
					MemoryUtil.memPutInt(dst + 32, MemoryUtil.memGetInt(src + 32));
				} else {
					MemoryUtil.memCopy(src, dst, 36);
				}
				MemoryUtil.memPutShort(dst + 36, entity);
				MemoryUtil.memPutShort(dst + 38, blockEntity);
				MemoryUtil.memPutShort(dst + 40, item);
				MemoryUtil.memPutFloat(dst + 42, midU);
				MemoryUtil.memPutFloat(dst + 46, midV);
				MemoryUtil.memPutInt(dst + 50, tangent);

				src += ModelVertex.STRIDE;
				dst += dstStride;
			}
		}
	}
}
