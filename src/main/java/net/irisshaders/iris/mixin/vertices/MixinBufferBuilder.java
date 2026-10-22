package net.irisshaders.iris.mixin.vertices;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferVertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.DefaultedVertexConsumer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.irisshaders.iris.vertices.BlockSensitiveBufferBuilder;
import net.irisshaders.iris.vertices.BufferBuilderPolygonView;
import net.irisshaders.iris.vertices.ExtendedDataHelper;
import net.irisshaders.iris.vertices.ExtendedVertexLayout;
import net.irisshaders.iris.vertices.ExtendingBufferBuilder;
import net.irisshaders.iris.vertices.IrisExtendedBufferBuilder;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.irisshaders.iris.vertices.NormI8;
import net.irisshaders.iris.vertices.NormalHelper;
import org.jetbrains.annotations.NotNull;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.joml.Vector3f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;

/**
 * Dynamically and transparently extends the vanilla vertex formats with additional data
 */
@Mixin(BufferBuilder.class) // TODO OCULUS: ???
public abstract class MixinBufferBuilder extends DefaultedVertexConsumer implements BufferVertexConsumer, BlockSensitiveBufferBuilder, ExtendingBufferBuilder, IrisExtendedBufferBuilder {
	@Unique
	private final BufferBuilderPolygonView polygon = new BufferBuilderPolygonView();
	@Unique
	private final Vector3f normal = new Vector3f();
	@Unique
	private boolean iris$shouldNotExtend;
	@Unique
	private boolean extending;
	@Unique
	private boolean iris$isTerrain;
	@Unique
	private boolean injectNormalAndUV1;
	@Unique
	private int vertexCount;
	@Unique
	private ExtendedVertexLayout iris$layout;
	@Unique
	private int iris$polygonSize;
	@Unique
	private short currentBlock = -1;
	@Unique
	private short currentRenderType = -1;
	@Unique
	private int currentLocalPosX;
	@Unique
	private int currentLocalPosY;
	@Unique
	private int currentLocalPosZ;
	@Shadow
	private ByteBuffer buffer;

	@Shadow
	private VertexFormat.Mode mode;

	@Shadow
	private VertexFormat format;

	@Shadow
	private int nextElementByte;

	@Shadow
	private int elementIndex;

	@Shadow
	public abstract void begin(VertexFormat.Mode drawMode, VertexFormat vertexFormat);

	@Override
	public void iris$beginWithoutExtending(VertexFormat.Mode drawMode, VertexFormat vertexFormat) {
		iris$shouldNotExtend = true;
		begin(drawMode, vertexFormat);
		iris$shouldNotExtend = false;
	}

	@Override
	public @NotNull VertexConsumer uv2(int pBufferVertexConsumer0, int pInt1) {
		return BufferVertexConsumer.super.uv2(pBufferVertexConsumer0, pInt1);
	}

	@ModifyVariable(method = "begin", at = @At("HEAD"), argsOnly = true)
	private VertexFormat iris$extendFormat(VertexFormat format) {
		if (iris$layout != null) {
			extending = false;
			iris$isTerrain = false;
			injectNormalAndUV1 = false;
			iris$layout = null;
		}

		if (iris$shouldNotExtend || !WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) {
			return format;
		}

		if (format == DefaultVertexFormat.BLOCK) {
			extending = true;
			iris$isTerrain = true;
			injectNormalAndUV1 = false;
			iris$layout = ExtendedVertexLayout.TERRAIN;
			return IrisVertexFormats.TERRAIN;
		} else if (format == DefaultVertexFormat.NEW_ENTITY) {
			extending = true;
			iris$isTerrain = false;
			injectNormalAndUV1 = false;
			iris$layout = ExtendedVertexLayout.ENTITY;
			return IrisVertexFormats.ENTITY;
		} else if (format == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) {
			extending = true;
			iris$isTerrain = false;
			injectNormalAndUV1 = true;
			iris$layout = ExtendedVertexLayout.GLYPH;
			return IrisVertexFormats.GLYPH;
		}

		return format;
	}

	@Inject(method = "begin", at = @At("RETURN"))
	private void iris$capturePolygonSize(VertexFormat.Mode drawMode, VertexFormat vertexFormat, CallbackInfo ci) {
		if (iris$layout == null) {
			iris$polygonSize = 0;
		} else if (drawMode == VertexFormat.Mode.QUADS) {
			iris$polygonSize = 4;
		} else if (drawMode == VertexFormat.Mode.TRIANGLES) {
			iris$polygonSize = 3;
		} else {
			iris$polygonSize = 0;
		}
	}

	@Inject(method = "reset()V", at = @At("HEAD"))
	private void iris$onReset(CallbackInfo ci) {
		vertexCount = 0;
	}

	@ModifyExpressionValue(method = "endVertex", at = @At(value = "FIELD", target = "Lcom/mojang/blaze3d/vertex/BufferBuilder;elementIndex:I", opcode = Opcodes.GETFIELD))
	private int iris$finishExtendedVertex(int pendingElement) {
		ExtendedVertexLayout layout = iris$layout;
		if (layout == null || pendingElement < layout.firstPendingElement) {
			return pendingElement;
		}

		ByteBuffer buffer = this.buffer;
		int start = this.nextElementByte - layout.elementOffsets[pendingElement];
		int normalAt = start + layout.normalOffset;
		buffer.putInt(normalAt, buffer.getInt(normalAt) & layout.keptNormalBits[pendingElement]);

		int terrain = layout.terrainMask;
		CapturedRenderingState state = CapturedRenderingState.INSTANCE;
		int idAt = start + layout.idOffset;
		buffer.putShort(idAt, (short) ((currentBlock & terrain) | (state.getCurrentRenderedEntity() & ~terrain)));
		buffer.putShort(idAt + 2, (short) ((currentRenderType & terrain) | (state.getCurrentRenderedBlockEntity() & ~terrain)));
		buffer.putShort(idAt + 4, (short) state.getCurrentRenderedItem());
		buffer.putInt(start + layout.midBlockOffset, ExtendedDataHelper.computeMidBlock(buffer.getFloat(start), buffer.getFloat(start + 4), buffer.getFloat(start + 8), currentLocalPosX, currentLocalPosY, currentLocalPosZ));
		buffer.putLong(start + layout.midTextureOffset, 0L);
		buffer.putInt(start + layout.tangentOffset, 0);

		for (int remaining = layout.remainingElements[pendingElement]; remaining > 0; remaining--) {
			this.nextElement();
		}

		int stride = layout.vertexSize;
		int end = start + stride;

		int polygonSize = iris$polygonSize;
		if (++vertexCount != polygonSize) {
			return this.elementIndex;
		}
		vertexCount = 0;

		polygon.setup(buffer, end, stride, polygonSize);

		float midU = 0;
		float midV = 0;
		for (int vertex = 0; vertex < polygonSize; vertex++) {
			midU += polygon.u(vertex);
			midV += polygon.v(vertex);
		}
		midU /= polygonSize;
		midV /= polygonSize;

		int midTextureOffset = layout.midTextureOffset;
		int tangentOffset = layout.tangentOffset;
		int normalOffset = layout.normalOffset;

		if (polygonSize == 3) {
			for (int vertex = 1; vertex <= 3; vertex++) {
				int base = end - stride * vertex;
				int packedNormal = buffer.getInt(base + normalOffset);
				int tangent = NormalHelper.computeTangentSmooth(NormI8.unpackX(packedNormal), NormI8.unpackY(packedNormal), NormI8.unpackZ(packedNormal), polygon);
				buffer.putFloat(base + midTextureOffset, midU);
				buffer.putFloat(base + midTextureOffset + 4, midV);
				buffer.putInt(base + tangentOffset, tangent);
			}
		} else {
			Vector3f faceNormal = normal;
			NormalHelper.computeFaceNormal(faceNormal, polygon);
			int packedNormal = NormI8.pack(faceNormal.x, faceNormal.y, faceNormal.z, 0.0f);
			int tangent = NormalHelper.computeTangent(faceNormal.x, faceNormal.y, faceNormal.z, polygon);
			for (int vertex = 1; vertex <= 4; vertex++) {
				int base = end - stride * vertex;
				buffer.putFloat(base + midTextureOffset, midU);
				buffer.putFloat(base + midTextureOffset + 4, midV);
				buffer.putInt(base + normalOffset, packedNormal);
				buffer.putInt(base + tangentOffset, tangent);
			}
		}

		return this.elementIndex;
	}

	@Override
	public void beginBlock(short block, short renderType, int localPosX, int localPosY, int localPosZ) {
		this.currentBlock = block;
		this.currentRenderType = renderType;
		this.currentLocalPosX = localPosX;
		this.currentLocalPosY = localPosY;
		this.currentLocalPosZ = localPosZ;
	}

	@Override
	public void endBlock() {
		this.currentBlock = -1;
		this.currentRenderType = -1;
		this.currentLocalPosX = 0;
		this.currentLocalPosY = 0;
		this.currentLocalPosZ = 0;
	}

	@Override
	public VertexFormat iris$format() {
		return format;
	}

	@Override
	public VertexFormat.Mode iris$mode() {
		return mode;
	}

	@Override
	public boolean iris$extending() {
		return extending;
	}

	@Override
	public boolean iris$isTerrain() {
		return iris$isTerrain;
	}

	@Override
	public boolean iris$injectNormalAndUV1() {
		return injectNormalAndUV1;
	}

	@Override
	public int iris$vertexCount() {
		return vertexCount;
	}

	@Override
	public void iris$incrementVertexCount() {
		vertexCount++;
	}

	@Override
	public void iris$resetVertexCount() {
		vertexCount = 0;
	}

	@Override
	public short iris$currentBlock() {
		return currentBlock;
	}

	@Override
	public short iris$currentRenderType() {
		return currentRenderType;
	}

	@Override
	public int iris$currentLocalPosX() {
		return currentLocalPosX;
	}

	@Override
	public int iris$currentLocalPosY() {
		return currentLocalPosY;
	}

	@Override
	public int iris$currentLocalPosZ() {
		return currentLocalPosZ;
	}
}
