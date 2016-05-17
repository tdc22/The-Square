#version 330

layout(location = 0)in vec4 in_Position;
layout(location = 1)in vec4 in_Color;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;

uniform float u_positionX;
uniform float u_maxPositionX;

out vec3 pass_Vertex;
out vec4 pass_Color;

void main(void) {
	pass_Vertex = in_Position.xyz;
	gl_Position = projection * view * model * in_Position;
	pass_Color = in_Color;
}