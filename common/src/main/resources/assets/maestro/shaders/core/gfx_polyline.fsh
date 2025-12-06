#version 150

// SDF-based polyline fragment shader with anti-aliasing
// Based on Unity Shapes' polyline rendering by Freya Holmér
//
// UV Encoding (geometry type signaled by UV.x offset):
// - Line segments (x < 1.5): x = 0 to 1 along segment, y = -1 to +1 perpendicular (edge at |y|=1)
// - Round joins/caps (1.5 <= x < 3.5): x offset by +2.0, unit circle coords (edge at length=1)
// - Bevel joins (x >= 3.5): x offset by +4.0, y = distance to outer edge (edge at y=1)

in vec2 lineCoord;
in vec4 vertexColor;

uniform vec4 ColorModulator;

out vec4 fragColor;

// Debug mode: 0 = normal, 1 = solid fill (no SDF), 2 = visualize geometry type
const int DEBUG_MODE = 0;

// Geometry type constants (must match Kotlin offsets)
const float LINE_SEGMENT_THRESHOLD = 1.5;
const float ROUND_GEOMETRY_OFFSET = 2.0;
const float BEVEL_GEOMETRY_THRESHOLD = 3.5;
const float BEVEL_GEOMETRY_OFFSET = 4.0;

void main() {
    // Detect geometry type from UV.x offset
    int geomType = 0; // 0 = line segment, 1 = round, 2 = bevel
    vec2 uv = lineCoord;

    if (lineCoord.x >= BEVEL_GEOMETRY_THRESHOLD) {
        geomType = 2; // Bevel
        uv = vec2(lineCoord.x - BEVEL_GEOMETRY_OFFSET, lineCoord.y);
    } else if (lineCoord.x >= LINE_SEGMENT_THRESHOLD) {
        geomType = 1; // Round
        uv = vec2(lineCoord.x - ROUND_GEOMETRY_OFFSET, lineCoord.y);
    }

    // Debug: Solid fill to test geometry without SDF
    if (DEBUG_MODE == 1) {
        fragColor = vertexColor * ColorModulator;
        return;
    }

    // Debug: Visualize geometry type (red = round, green = line, blue = bevel)
    if (DEBUG_MODE == 2) {
        vec4 debugColor;
        if (geomType == 2) {
            debugColor = vec4(0.0, 0.0, 1.0, 1.0); // Blue = bevel
        } else if (geomType == 1) {
            debugColor = vec4(1.0, 0.0, 0.0, 1.0); // Red = round
        } else {
            debugColor = vec4(0.0, 1.0, 0.0, 1.0); // Green = line segment
        }
        fragColor = debugColor * ColorModulator;
        return;
    }

    // SDF distance calculation based on geometry type
    float dist;
    if (geomType == 2) {
        // Bevel geometry: simple distance to outer edge
        // UV.y = 0 at inner corner, UV.y = aaPadding at outer edge (visible edge at y=1)
        dist = uv.y;
    } else if (geomType == 1) {
        // Round geometry (joins/caps): radial distance from center
        // Edge is at length = 1.0
        dist = length(uv);
    } else {
        // Line segment: perpendicular distance from center line
        // Edge is at |y| = 1.0
        dist = abs(uv.y);
    }

    // Anti-aliasing using screen-space derivatives
    // fwidth gives us the rate of change of dist across the fragment
    float aa = fwidth(dist);

    // Smoothstep from edge (1.0) inward
    // The AA band width is proportional to the derivative
    float alpha = smoothstep(1.0, 1.0 - aa * 1.5, dist);

    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;

    if (color.a < 0.01) {
        discard;
    }

    fragColor = color;
}
