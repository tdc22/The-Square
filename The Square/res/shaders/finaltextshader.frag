#version 330

uniform float u_positionX;
uniform float u_maxPositionX;

in vec3 pass_Vertex;
in vec4 pass_Color;
out vec4 out_Color;

void main(void) {
	out_Color = pass_Color;
	float halfX = pass_Vertex.x/5.0;
	float dist = halfX - u_positionX*4.0;
	float distMax = halfX - u_maxPositionX*4.0;
	out_Color.a = max(1 - abs(dist), 0) + min(max((1 - abs(distMax)) * 0.5, 0), 0.5);
	if(distMax < 0)
		out_Color.a = max(out_Color.a, 0.5);
}