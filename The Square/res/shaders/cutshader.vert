#version 330

layout(location = 0)in vec4 in_Position;
layout(location = 1)in vec4 in_Color;
layout(location = 3)in vec4 in_Normal;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
uniform vec3 cameraNormal;

out vec4 pass_Color;
out vec4 pass_Normal;
out vec4 pass_Position;
out vec4 pass_Vertex;

void main(void) {
//wenn cameranormale richtung vertex-mittelpunkt zeigt rendern, sonst nicht
	//vec3 center = model[3].xyz;
	//bool front = dot(center, cameraNormal) > 0;
	//vec4 pos = model * in_Position;
	//if(front) {
	//	pass_Valid = (dot(pos.xyz, cameraNormal) > 0) ? 1 : 0;
	//}
	//else {
	//	pass_Valid = (dot(pos.xyz, cameraNormal) < 0) ? 1 : 0;
	//}
	//gl_Position = projection * view * model * in_Position;
	//pass_Color = in_Color;
	//if(pass_Valid == 0) pass_Color.a = 0;
	pass_Color = in_Color;
    pass_Normal = in_Normal;
    pass_Position = model * in_Position;
    pass_Vertex = view * pass_Position;
	gl_Position = projection * pass_Vertex;
}