package net.irisshaders.iris.vertices;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

public final class ExtendedVertexLayout {
	public static final ExtendedVertexLayout TERRAIN = new ExtendedVertexLayout(IrisVertexFormats.TERRAIN, IrisVertexFormats.ENTITY_ELEMENT, true, false);
	public static final ExtendedVertexLayout ENTITY = new ExtendedVertexLayout(IrisVertexFormats.ENTITY, IrisVertexFormats.ENTITY_ID_ELEMENT, false, false);
	public static final ExtendedVertexLayout GLYPH = new ExtendedVertexLayout(IrisVertexFormats.GLYPH, IrisVertexFormats.ENTITY_ID_ELEMENT, false, true);

	public final int vertexSize;
	public final int firstPendingElement;
	public final int[] elementOffsets;
	public final int[] keptNormalBits;
	public final int[] remainingElements;
	public final int normalOffset;
	public final int idOffset;
	public final int midTextureOffset;
	public final int tangentOffset;
	public final int midBlockOffset;
	public final int terrainMask;

	private ExtendedVertexLayout(VertexFormat format, VertexFormatElement idElement, boolean terrain, boolean injectsNormal) {
		ImmutableList<VertexFormatElement> elements = format.getElements();
		this.vertexSize = format.getVertexSize();
		this.elementOffsets = new int[elements.size()];
		this.keptNormalBits = new int[elements.size()];
		this.remainingElements = new int[elements.size() + 1];

		int normalIndex = -1;
		int idIndex = -1;
		int normal = -1;
		int id = -1;
		int midTexture = -1;
		int tangent = -1;
		int midBlock = -1;
		int offset = 0;

		for (int i = 0; i < elements.size(); i++) {
			VertexFormatElement element = elements.get(i);
			this.elementOffsets[i] = offset;
			if (element == DefaultVertexFormat.ELEMENT_NORMAL) {
				normalIndex = i;
				normal = offset;
			} else if (element == idElement) {
				idIndex = i;
				id = offset;
			} else if (element == IrisVertexFormats.MID_TEXTURE_ELEMENT) {
				midTexture = offset;
			} else if (element == IrisVertexFormats.TANGENT_ELEMENT) {
				tangent = offset;
			} else if (element == IrisVertexFormats.MID_BLOCK_ELEMENT) {
				midBlock = offset;
			}
			offset += element.getByteSize();
		}

		if (normal < 0 || id < 0 || midTexture < 0 || tangent < 0 || (terrain && midBlock < 0) || offset != this.vertexSize) {
			throw new IllegalStateException("Unexpected extended vertex format layout: " + format);
		}

		for (int i = 0; i < elements.size(); i++) {
			this.keptNormalBits[i] = injectsNormal && i == normalIndex ? 0 : -1;
		}

		for (int i = elements.size() - 1; i >= 0; i--) {
			boolean padding = elements.get(i).getUsage() == VertexFormatElement.Usage.PADDING;
			this.remainingElements[i] = this.remainingElements[i + 1] + (padding ? 0 : 1);
		}

		this.firstPendingElement = injectsNormal ? normalIndex : idIndex;
		this.normalOffset = normal;
		this.idOffset = id;
		this.midTextureOffset = midTexture;
		this.tangentOffset = tangent;
		this.midBlockOffset = terrain ? midBlock : midTexture;
		this.terrainMask = terrain ? -1 : 0;
	}
}
