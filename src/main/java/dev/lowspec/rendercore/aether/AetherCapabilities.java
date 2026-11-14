package dev.lowspec.rendercore.aether;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;

/** Immutable snapshot of graphics features exposed by the active OpenGL context. */
public record AetherCapabilities(
        int majorVersion,
        int minorVersion,
        boolean instancedArrays,
        boolean shaderStorageBuffers
) {
    public static AetherCapabilities detect() {
        GLCapabilities gl = GL.getCapabilities();
        int major = gl.OpenGL43 ? 4 : gl.OpenGL33 ? 3 : gl.OpenGL20 ? 2 : 0;
        int minor = gl.OpenGL43 ? 3 : gl.OpenGL33 ? 3 : gl.OpenGL20 ? 0 : 0;
        return new AetherCapabilities(
                major,
                minor,
                gl.OpenGL33 || gl.GL_ARB_instanced_arrays,
                gl.OpenGL43 || gl.GL_ARB_shader_storage_buffer_object
        );
    }

    public boolean supportsInstancedEntityPrototype() {
        return instancedArrays && (majorVersion > 3 || (majorVersion == 3 && minorVersion >= 3));
    }
}
