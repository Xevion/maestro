#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 quadCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // UV0 encodes position within the quad
    // Range: [-1, 1] for standard quads
    quadCoord = UV0;
    vertexColor = Color;
}
