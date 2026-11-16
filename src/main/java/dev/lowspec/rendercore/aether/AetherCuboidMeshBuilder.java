package dev.lowspec.rendercore.aether;

import java.util.Objects;

/**
 * Allocation-free triangle-list writer for one axis-aligned cuboid.
 *
 * Output vertex layout is position (x,y,z), UV (u,v), and face normal (nx,ny,nz).
 * Face UV rectangles are supplied in +X, -X, +Y, -Y, +Z, -Z order, four floats
 * per face: u0, v0, u1, v1. Coordinates are model-space units.
 */
public final class AetherCuboidMeshBuilder {
    public static final int FLOATS_PER_VERTEX = 8;
    public static final int VERTICES_PER_CUBOID = 36;
    public static final int FLOATS_PER_CUBOID = FLOATS_PER_VERTEX * VERTICES_PER_CUBOID;
    public static final int FACE_UV_FLOATS = 24;

    private AetherCuboidMeshBuilder() { }

    /**
     * Writes 12 outward-facing triangles and returns the next free float offset.
     * The destination and UV array are caller-owned and are never retained.
     */
    public static int writeCuboid(
            float minX, float minY, float minZ,
            float maxX, float maxY, float maxZ,
            float[] faceUvs, float[] destination, int floatOffset) {
        Objects.requireNonNull(faceUvs, "faceUvs");
        Objects.requireNonNull(destination, "destination");
        if (!(Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ))
                || minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("cuboid bounds must be finite and have positive dimensions");
        }
        if (faceUvs.length < FACE_UV_FLOATS) {
            throw new IllegalArgumentException("six face UV rectangles are required");
        }
        for (int i = 0; i < FACE_UV_FLOATS; i++) {
            if (!Float.isFinite(faceUvs[i])) throw new IllegalArgumentException("face UV values must be finite");
        }
        if (floatOffset < 0 || floatOffset > destination.length - FLOATS_PER_CUBOID) {
            throw new IndexOutOfBoundsException("destination cannot fit one cuboid mesh");
        }

        int out = floatOffset;
        // +X
        out = writeFace(destination, out,
                maxX,minY,minZ, maxX,maxY,minZ, maxX,maxY,maxZ, maxX,minY,maxZ,
                faceUvs, 0, 1,0,0);
        // -X
        out = writeFace(destination, out,
                minX,minY,maxZ, minX,maxY,maxZ, minX,maxY,minZ, minX,minY,minZ,
                faceUvs, 4, -1,0,0);
        // +Y
        out = writeFace(destination, out,
                minX,maxY,minZ, minX,maxY,maxZ, maxX,maxY,maxZ, maxX,maxY,minZ,
                faceUvs, 8, 0,1,0);
        // -Y
        out = writeFace(destination, out,
                minX,minY,maxZ, minX,minY,minZ, maxX,minY,minZ, maxX,minY,maxZ,
                faceUvs, 12, 0,-1,0);
        // +Z
        out = writeFace(destination, out,
                maxX,minY,maxZ, maxX,maxY,maxZ, minX,maxY,maxZ, minX,minY,maxZ,
                faceUvs, 16, 0,0,1);
        // -Z
        out = writeFace(destination, out,
                minX,minY,minZ, minX,maxY,minZ, maxX,maxY,minZ, maxX,minY,minZ,
                faceUvs, 20, 0,0,-1);
        return out;
    }

    private static int writeFace(
            float[] out, int offset,
            float x0,float y0,float z0, float x1,float y1,float z1,
            float x2,float y2,float z2, float x3,float y3,float z3,
            float[] uv, int uvOffset, float nx,float ny,float nz) {
        float u0 = uv[uvOffset], v0 = uv[uvOffset + 1];
        float u1 = uv[uvOffset + 2], v1 = uv[uvOffset + 3];
        offset = writeVertex(out, offset, x0,y0,z0, u0,v1, nx,ny,nz);
        offset = writeVertex(out, offset, x1,y1,z1, u0,v0, nx,ny,nz);
        offset = writeVertex(out, offset, x2,y2,z2, u1,v0, nx,ny,nz);
        offset = writeVertex(out, offset, x0,y0,z0, u0,v1, nx,ny,nz);
        offset = writeVertex(out, offset, x2,y2,z2, u1,v0, nx,ny,nz);
        return writeVertex(out, offset, x3,y3,z3, u1,v1, nx,ny,nz);
    }

    private static int writeVertex(
            float[] out, int offset,
            float x,float y,float z, float u,float v, float nx,float ny,float nz) {
        out[offset++] = x; out[offset++] = y; out[offset++] = z;
        out[offset++] = u; out[offset++] = v;
        out[offset++] = nx; out[offset++] = ny; out[offset++] = nz;
        return offset;
    }
}
