package dev.lowspec.rendercore.aether;

import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * Reusable CPU mesh storage populated from the live cuboids of a ModelPart.
 * Each source quad becomes two triangles. Vertex layout: xyz, uv, normal xyz.
 * The caller supplies the root model part and a scratch MatrixStack; no model
 * objects or live entities are retained after capture.
 */
public final class AetherModelGeometryBuffer {
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int VERTICES_PER_QUAD = 6;

    private final float[] vertices;
    private final Vector3f position = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private int vertexCount;
    private int quadCount;
    private int cuboidCount;
    private long overflowCount;
    private boolean overflowed;

    public AetherModelGeometryBuffer(int quadCapacity) {
        if (quadCapacity < 1) throw new IllegalArgumentException("quad capacity must be positive");
        this.vertices = new float[Math.multiplyExact(
                Math.multiplyExact(quadCapacity, VERTICES_PER_QUAD), FLOATS_PER_VERTEX)];
    }

    /** Captures the current animated pose of every visible ModelPart cuboid. */
    public boolean capture(ModelPart root, MatrixStack matrices) {
        AetherRenderThread.assertCurrent();
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(matrices, "matrices");
        vertexCount = quadCount = cuboidCount = 0;
        overflowed = false;
        root.forEachCuboid(matrices, this::appendCuboid);
        return !overflowed;
    }

    private void appendCuboid(MatrixStack.Entry matrix, String path, int index, ModelPart.Cuboid cuboid) {
        cuboidCount++;
        for (ModelPart.Quad quad : cuboid.sides) {
            if (quad == null || quad.vertices().length != 4) continue;
            if (vertexCount > vertices.length / FLOATS_PER_VERTEX - VERTICES_PER_QUAD) {
                overflowed = true;
                overflowCount++;
                return;
            }
            matrix.transformNormal(quad.direction(), normal);
            if (normal.lengthSquared() > 0.0f) normal.normalize();
            ModelPart.Vertex[] face = quad.vertices();
            appendVertex(matrix, face[0]);
            appendVertex(matrix, face[1]);
            appendVertex(matrix, face[2]);
            appendVertex(matrix, face[0]);
            appendVertex(matrix, face[2]);
            appendVertex(matrix, face[3]);
            quadCount++;
        }
    }

    private void appendVertex(MatrixStack.Entry matrix, ModelPart.Vertex vertex) {
        matrix.getPositionMatrix().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), position);
        int offset = vertexCount++ * FLOATS_PER_VERTEX;
        vertices[offset] = position.x;
        vertices[offset + 1] = position.y;
        vertices[offset + 2] = position.z;
        vertices[offset + 3] = vertex.u();
        vertices[offset + 4] = vertex.v();
        vertices[offset + 5] = normal.x;
        vertices[offset + 6] = normal.y;
        vertices[offset + 7] = normal.z;
    }

    /** Read-only-by-contract backing data; valid values occupy [0, usedFloats()). */
    public float[] data() { return vertices; }
    public int usedFloats() { return vertexCount * FLOATS_PER_VERTEX; }
    public int vertexCount() { return vertexCount; }
    public int quadCount() { return quadCount; }
    public int cuboidCount() { return cuboidCount; }
    public int capacityQuads() { return vertices.length / (FLOATS_PER_VERTEX * VERTICES_PER_QUAD); }
    public long overflowCount() { return overflowCount; }
    public boolean overflowed() { return overflowed; }
}
