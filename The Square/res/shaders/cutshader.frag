#version 330

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
uniform vec3 cameraNormal;

in vec4 pass_Color;
in vec4 pass_Normal;
in vec4 pass_Vertex_Model;
in vec4 pass_Vertex_View;
out vec4 out_Color;

void main(void) {
	out_Color = pass_Color;
	vec3 center = model[3].xyz;
	bool front = dot(center, cameraNormal) > 0;
	int pass_Valid = 0;
	vec3 vert = pass_Vertex_Model.xyz;
	if(front) {
		pass_Valid = (dot(vert, cameraNormal) > 0) ? 1 : 0;
	}
	else {
		pass_Valid = (dot(vert, cameraNormal) < 0) ? 1 : 0;
	}
	//if(front) out_Color.r = 0;
	if(pass_Valid == 1) out_Color.a = 0;
}