#version 450 core

layout(binding = 0) uniform sampler2D depthTex;
in vec2 UV;
out vec4 colour;

#import <voxy:util/depthutils.glsl>

void main() {
    colour = vec4(texture(depthTex, UV).r == FAR ? 1.0 : 0.0);
}
