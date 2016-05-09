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
out vec4 pass_Vertex_Model;
out vec4 pass_Vertex_View;

void main(void) {
	pass_Color = in_Color;
    pass_Normal = in_Normal;
    pass_Vertex_Model = model * in_Position;
    pass_Vertex_View = view * pass_Vertex_Model;
    
    // TODO: project vertex to plane
    float l = dot(pass_Vertex_Model, vec4(cameraNormal, 0));
    vec4 projected_Vertex = pass_Vertex_Model - l * vec4(cameraNormal, 0);
    vec4 projected_Vertex_View = view * projected_Vertex;
    projected_Vertex_View.z += 0.1;
	gl_Position = projection * projected_Vertex_View;
}