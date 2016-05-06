package game;

import broadphase.SAP;
import display.DisplayMode;
import display.GLDisplay;
import display.PixelFormat;
import display.VideoSettings;
import gui.Font;
import input.Input;
import input.InputEvent;
import input.KeyInput;
import integration.VerletIntegration;
import loader.FontLoader;
import loader.ShaderLoader;
import manifold.SimpleManifoldManager;
import math.VecMath;
import narrowphase.EPA;
import narrowphase.GJK;
import objects.RigidBody3;
import physics.PhysicsDebug;
import physics.PhysicsShapeCreator;
import physics.PhysicsSpace;
import positionalcorrection.ProjectionCorrection;
import resolution.ImpulseResolution;
import shader.Shader;
import shape.Box;
import shape.Cylinder;
import shape.Sphere;
import shape2d.Circle;
import shape2d.Quad;
import sound.NullSoundEnvironment;
import space.PhysicsProfiler;
import space.SimplePhysicsProfiler;
import utils.Debugger;
import utils.DefaultValues;
import utils.GameProfiler;
import utils.Profiler;
import utils.ProjectionHelper;
import utils.SimpleGameProfiler;
import vector.Vector2f;
import vector.Vector3f;

public class Game extends StandardGame {
	Debugger debugger;
	Profiler profiler;
	PhysicsSpace space;
	PhysicsDebug physicsdebug;

	Cylinder player;
	RigidBody3 playerbody;
	InputEvent up, down, left, right, jump;
	final Vector3f vecUp = new Vector3f(0, 1, 0);
	final Vector3f vecRight = new Vector3f(0, 0, -1);
	final Vector2f vecX = new Vector2f(1, 0);
	Quad intersectionInterface;
	
	final float playerMoveSpeedX = 4f;
	final float playerMoveSpeedZ = 6f;

	@Override
	public void init() {
		useFBO = false;
		initDisplay(new GLDisplay(), new DisplayMode(), new PixelFormat(),
				new VideoSettings(DefaultValues.DEFAULT_VIDEO_RESOLUTION_X, DefaultValues.DEFAULT_VIDEO_RESOLUTION_Y,
						DefaultValues.DEFAULT_VIDEO_FOVY, DefaultValues.DEFAULT_VIDEO_ZNEAR,
						DefaultValues.DEFAULT_VIDEO_ZFAR, false),
				new NullSoundEnvironment());
		layer3d.setProjectionMatrix(ProjectionHelper.ortho(6, -6, -5, 5, -1, 1));
		display.bindMouse();
		cam.setFlyCam(false);
		cam.translateTo(0f, 0f, 0);
		cam.rotateTo(0, 0);

		Shader defaultshader = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/defaultshader.vert", "res/shaders/defaultshader.frag"));
		addShader(defaultshader);
		Shader defaultshaderInterface = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/defaultshader.vert", "res/shaders/defaultshader.frag"));
		addShaderInterface(defaultshaderInterface);

		Input inputKeyW = new Input(Input.KEYBOARD_EVENT, "W", KeyInput.KEY_DOWN);
		Input inputKeyA = new Input(Input.KEYBOARD_EVENT, "A", KeyInput.KEY_DOWN);
		Input inputKeyS = new Input(Input.KEYBOARD_EVENT, "S", KeyInput.KEY_DOWN);
		Input inputKeyD = new Input(Input.KEYBOARD_EVENT, "D", KeyInput.KEY_DOWN);
		Input inputKeySpace = new Input(Input.KEYBOARD_EVENT, "Space", KeyInput.KEY_DOWN);
		Input inputKeyUp = new Input(Input.KEYBOARD_EVENT, "Up", KeyInput.KEY_DOWN);
		Input inputKeyLeft = new Input(Input.KEYBOARD_EVENT, "Left", KeyInput.KEY_DOWN);
		Input inputKeyDown = new Input(Input.KEYBOARD_EVENT, "Down", KeyInput.KEY_DOWN);
		Input inputKeyRight = new Input(Input.KEYBOARD_EVENT, "Right", KeyInput.KEY_DOWN);
		up = new InputEvent("Up", inputKeyW, inputKeyUp);
		down = new InputEvent("Down", inputKeyS, inputKeyDown);
		left = new InputEvent("Left", inputKeyA, inputKeyLeft);
		right = new InputEvent("Right", inputKeyD, inputKeyRight);
		jump = new InputEvent("Jump", inputKeySpace);
		inputs.addEvent(up);
		inputs.addEvent(down);
		inputs.addEvent(left);
		inputs.addEvent(right);
		inputs.addEvent(jump);

		space = new PhysicsSpace(new VerletIntegration(), new SAP(), new GJK(new EPA()), new ImpulseResolution(),
				new ProjectionCorrection(0.01f), new SimpleManifoldManager<Vector3f>());
		space.setGlobalGravitation(new Vector3f(0, -8f, 0));

		Font font = FontLoader.loadFont("res/fonts/DejaVuSans.ttf");
		debugger = new Debugger(inputs, defaultshader, defaultshaderInterface, font, cam);
		physicsdebug = new PhysicsDebug(inputs, font, space);
		GameProfiler gp = new SimpleGameProfiler();
		setProfiler(gp);
		PhysicsProfiler pp = new SimplePhysicsProfiler();
		space.setProfiler(pp);
		profiler = new Profiler(this, inputs, font, gp, pp);

		Cylinder ground = new Cylinder(0, -5, 0, 5, 1, 36);
		RigidBody3 rb = new RigidBody3(PhysicsShapeCreator.create(ground));
		space.addRigidBody(ground, rb);
		defaultshader.addObject(ground);

		player = new Cylinder(0, 0, 0, 1, 1, 36);
		playerbody = new RigidBody3(PhysicsShapeCreator.create(player));
		playerbody.setMass(1f);
		space.addRigidBody(player, playerbody);
		defaultshader.addObject(player);

		defaultshader.addObject(new Sphere(0, 2, 0, 1, 36, 36));
		defaultshader.addObject(new Box(0, 4, 0, 1, 1, 1));
		
		defaultshaderInterface.addObject(new Circle(55, 55, 50, 36));
		intersectionInterface = new Quad(55, 55, 55, 1);
		defaultshaderInterface.addObject(intersectionInterface);
	}

	@Override
	public void render() {
		debugger.begin();
		render3dLayer();
	}

	@Override
	public void render2d() {

	}

	@Override
	public void renderInterface() {
		renderInterfaceLayer();
		debugger.end();
	}

	@Override
	public void update(int delta) {
		debugger.update(fps, 0, 0);

		if (inputs.isKeyDown("I")) {
			cam.rotate(delta * 0.1f, 0);
		}
		if (inputs.isKeyDown("K")) {
			cam.rotate(-delta * 0.1f, 0);
		}
		if (inputs.isKeyDown("J")) {
			cam.getTranslation().z -= delta * 0.01f;
		}
		if (inputs.isKeyDown("L")) {
			cam.getTranslation().z += delta * 0.01f;
		}

		Vector3f frontVec = VecMath.transformVector(cam.getMatrix(), vecRight);
		Vector3f rightVec = VecMath.crossproduct(frontVec, vecUp);
		Vector2f move = new Vector2f();
		Vector2f pos = new Vector2f(playerbody.getTranslation().x, playerbody.getTranslation().z);
		boolean isPlayerNotInCenter = pos.lengthSquared() > 0.01f;
		
		if(isPlayerNotInCenter) {
			float distToMid = (float) pos.length();
			if (up.isActive()) {
				move.x += frontVec.x * distToMid * playerMoveSpeedZ;
				move.y += frontVec.z * distToMid * playerMoveSpeedZ;
			}
			if (down.isActive()) {
				move.x -= frontVec.x * distToMid * playerMoveSpeedZ;
				move.y -= frontVec.z * distToMid * playerMoveSpeedZ;
			}
			if(up.isActive() || down.isActive()) {
				float movespeed = (float) move.length();
				Vector2f predictedPos = VecMath.addition(pos, VecMath.scale(move, delta / 1000f));
				predictedPos.normalize();
				predictedPos.scale(distToMid);
				move = VecMath.subtraction(predictedPos, pos);
				move.setLength(movespeed);
			}
		}
		
		if (left.isActive()) {
			move.x += rightVec.x * playerMoveSpeedX;
			move.y += rightVec.z * playerMoveSpeedX;
		}
		if (right.isActive()) {
			move.x -= rightVec.x * playerMoveSpeedX;
			move.y -= rightVec.z * playerMoveSpeedX;
		}
		if(jump.isActive()) {
			// TODO: jump!
		}

		playerbody.getLinearVelocity().x = move.x;
		playerbody.getLinearVelocity().z = move.y;

		space.update(delta);

		// update pos after physics update
		pos = new Vector2f(playerbody.getTranslation().x, playerbody.getTranslation().z);
		isPlayerNotInCenter = pos.lengthSquared() > 0.01f;
		if((up.isActive() || down.isActive()) && isPlayerNotInCenter) {
			pos.normalize();
			if(VecMath.dotproduct(pos, new Vector2f(rightVec.x, rightVec.z)) < 0) pos.negate();
			float dotX = VecMath.dotproduct(pos, vecX);
			float angleX = (float) Math.toDegrees(Math.acos(dotX));
			float angle = 0;
			if(pos.y < 0) angle = angleX;
			else angle = 180 + (180 - angleX);
			cam.rotateTo(angle, 0);
			intersectionInterface.rotateTo(angle);
		}
		
		cam.updateBuffer();
	}
}
