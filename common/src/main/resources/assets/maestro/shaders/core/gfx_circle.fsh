#version 150

in vec2 circleCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    // circleCoord is in unit circle space
    // The circle edge is at radius = 1.0
    // Quad is expanded to ~1.1 to allow for AA padding

    // Distance from center
    float dist = length(circleCoord);

    // SDF: negative inside, positive outside
    float sdf = dist - 1.0;

    // Anti-aliasing using screen-space derivatives
    // This gives us the rate of change of the SDF per pixel
    float aa = fwidth(sdf);

    // Smooth transition at the edge
    // smoothstep(aa, -aa, gfx) gives:
    //   1.0 when gfx < -aa (fully inside)
    //   0.0 when sdf > aa (fully outside)
    //   smooth transition in between
    float alpha = smoothstep(aa, -aa, sdf);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;

    // Discard nearly transparent fragments for efficiency
    if (color.a < 0.01) {
        discard;
    }

    fragColor = color;
}
