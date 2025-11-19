#version 150

in vec3 inPosition;
in vec4 inColor;

uniform mat4 uProjectionMatrix;

out vec4 passColor;

void main() {
    gl_Position = uProjectionMatrix * vec4(inPosition, 1.0);
    passColor = inColor;
}
