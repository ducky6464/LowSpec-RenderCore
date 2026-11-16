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
        if (FORMAT.getVertexSize() != STRIDE_BYTES
                || FORMAT.getOffset(VertexFormatElement.POSITION) != 0
                || FORMAT.getOffset(VertexFormatElement.UV0) != 12
                || FORMAT.getOffset(VertexFormatElement.NORMAL) != 16) {
            throw new IllegalStateException("Aether player vertex format does not match packed upload layout");
        }
    }

    private AetherPlayerVertexLayout() { }
}
