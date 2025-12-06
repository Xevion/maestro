#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 lineCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // UV0.x: position along line segment (0 = start, 1 = end)
    // UV0.y: perpendicular distance from center (-1 to 1)
    lineCoord = UV0;
    vertexColor = Color;
}
