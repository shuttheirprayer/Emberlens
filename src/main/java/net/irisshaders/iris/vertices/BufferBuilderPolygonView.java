package net.irisshaders.iris.vertices;

import net.irisshaders.iris.vertices.views.QuadView;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

public class BufferBuilderPolygonView implements QuadView {
	private long base;
	private int stride = 48;

	public void setup(ByteBuffer buffer, int writePointer, int stride, int vertexAmount) {
		this.base = MemoryUtil.memAddress(buffer, 0) + writePointer - (long) stride * vertexAmount;
		this.stride = stride;
	}

	@Override
	public float x(int index) {
		return MemoryUtil.memGetFloat(base + (long) stride * index);
	}

	@Override
	public float y(int index) {
		return MemoryUtil.memGetFloat(base + (long) stride * index + 4);
	}

	@Override
	public float z(int index) {
		return MemoryUtil.memGetFloat(base + (long) stride * index + 8);
	}

	@Override
	public float u(int index) {
		return MemoryUtil.memGetFloat(base + (long) stride * index + 16);
	}

	@Override
	public float v(int index) {
		return MemoryUtil.memGetFloat(base + (long) stride * index + 20);
	}
}
