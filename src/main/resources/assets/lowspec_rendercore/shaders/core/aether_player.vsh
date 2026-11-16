#version 150

in vec3 Position;
in vec2 UV0;
in vec3 Normal;

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

layout(std140) uniform Projection {
    mat4 ProjMat;
};

out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position + ModelOffset, 1.0);
    texCoord = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}
