#version 150

uniform sampler2D Sampler0;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 skinColor = texture(Sampler0, texCoord);
    // Player skins use transparent texels for the outer-layer cutouts.
    // Discard them so they do not write depth as opaque black pixels.
    if (skinColor.a < 0.1) {
        discard;
    }
    fragColor = skinColor;
}
