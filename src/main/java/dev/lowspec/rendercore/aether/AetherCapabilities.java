package dev.lowspec.rendercore.aether;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
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
        // Query the actual context version instead of reporting the highest
        // feature milestone as though it were the exact driver version.
        int major = GL11.glGetInteger(GL30.GL_MAJOR_VERSION);
        int minor = GL11.glGetInteger(GL30.GL_MINOR_VERSION);
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
