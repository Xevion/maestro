#version 150

in vec2 lineCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

void main() {
    // SDF for a line segment with rounded caps
    // lineCoord.x: position along line (0-1, with padding for caps)
    // lineCoord.y: perpendicular distance from center (-1 to 1)

    // Distance from center line (absolute perpendicular distance)
    float dist = abs(lineCoord.y);

    // Anti-aliasing using screen-space derivatives
    // fwidth gives us the rate of change of dist per pixel
    float aa = fwidth(dist);

    // Smoothstep from edge (1.0) to center, with AA width
    // At dist=1.0 (edge): alpha approaches 0
    // At dist=0.0 (center): alpha = 1
    float alpha = smoothstep(1.0, 1.0 - aa * 2.0, dist);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;

    if (color.a < 0.01) {
        discard;
    }

    fragColor = color;
}
