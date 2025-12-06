#version 150

in vec3 Position;
in vec2 UV0;
in vec4 Color;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 circleCoord;
out vec4 vertexColor;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);

    // UV0 encodes position in unit circle space
    // Range: approximately [-1.1, 1.1] to allow for AA padding
    // length(UV0) < 1.0 means inside the circle
    circleCoord = UV0;
    vertexColor = Color;
}
