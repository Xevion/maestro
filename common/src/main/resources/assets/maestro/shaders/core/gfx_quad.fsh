#version 150

in vec2 quadCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

// SDF for axis-aligned box centered at origin
// p: point to test
// size: half-size of box
float sdBox(vec2 p, vec2 size) {
    vec2 d = abs(p) - size;
    return length(max(d, 0.0)) + min(max(d.x, d.y), 0.0);
}

void main() {
    // quadCoord is in [-1, 1] space (with possible expansion for AA)
    // The actual shape edge is at ±1

    // SDF for rectangle with edges at ±1
    float sdf = sdBox(quadCoord, vec2(1.0));

    // Anti-aliasing using screen-space derivatives
    // fwidth gives us the rate of change per pixel
    float aa = fwidth(sdf);

    // Smooth transition at the edge
    // Inside (sdf < 0): alpha = 1
    // Outside (sdf > 0): alpha = 0
    // Transition over ~1 pixel width
    float alpha = smoothstep(aa, -aa, sdf);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;

    // Discard nearly transparent fragments
    if (color.a < 0.01) {
        discard;
    }

    fragColor = color;
}
