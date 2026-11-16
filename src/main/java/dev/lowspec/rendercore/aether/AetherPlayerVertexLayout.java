package dev.lowspec.rendercore.aether;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;

/**
 * Vertex declaration matching AetherPlayerVertexWriter's compact GPU bytes.
 * Attribute order and padding are part of the upload/shader contract.
 */
public final class AetherPlayerVertexLayout {
    public static final int STRIDE_BYTES = AetherPlayerVertexWriter.GPU_BYTES_PER_VERTEX;
    public static final VertexFormat FORMAT = VertexFormat.builder()
            .add("Position", VertexFormatElement.POSITION)
            .add("UV0", VertexFormatElement.UV0)
            .add("Normal", VertexFormatElement.NORMAL)
            .padding(1)
            .build();

    static {
        int stride = FORMAT.getVertexSize();
        int position = FORMAT.getOffset(VertexFormatElement.POSITION);
        int uv = FORMAT.getOffset(VertexFormatElement.UV0);
        int normal = FORMAT.getOffset(VertexFormatElement.NORMAL);
        if (stride != STRIDE_BYTES || position != 0 || uv != 12 || normal != 16) {
            throw new IllegalStateException("Aether player vertex format mismatch: stride=" + stride
                    + ", position=" + position + ", uv=" + uv + ", normal=" + normal);
        }
    }

    private AetherPlayerVertexLayout() { }
}
