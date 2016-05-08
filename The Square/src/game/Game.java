package game;

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
import objects.GhostObject3;
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
import broadphase.SAP;
import collisionshape.CylinderShape;
import display.DisplayMode;
import display.GLDisplay;
import display.PixelFormat;
import display.VideoSettings;

public class Game extends StandardGame {
	Debugger debugger;
	Profiler profiler;
	PhysicsSpace space;
	PhysicsDebug physicsdebug;

	Cylinder player;
	RigidBody3 playerbody;
	GhostObject3 playerJumpChecker;
	boolean onGround;
	InputEvent up, down, left, right, jump;
	final Vector3f vecUp = new Vector3f(0, 1, 0);
	final Vector3f vecRight = new Vector3f(0, 0, -1);
	final Vector2f vecX = new Vector2f(1, 0);
	Quad intersectionInterface;
	Shader cutshader;

	final Vector2f SCREEN_MIN = new Vector2f(-6, -5);
	final Vector2f SCREEN_MAX = new Vector2f(6, 5);

	final Vector2f PLAYER_SIZE = new Vector2f(0.5f, 0.5f);
	final float PLAYER_MOVE_SPEED_X = 4f;
	final float PLAYER_MOVE_SPEED_Z = 4f;
	final float PLAYER_CHECKER_SIZE = 0.1f;
	final float PLAYER_CHECKER_OFFSET = PLAYER_CHECKER_SIZE + 0.1f;
	final float PLAYER_CHECKER_SIDE_OFFSET = 0.02f;
	final float PLAYER_JUMP_STRENGTH = 6f;
	final float PLAYER_MAX_Y = SCREEN_MAX.y - PLAYER_SIZE.y;
	final float PLAYER_MAX_DIST_TO_CENTER = SCREEN_MAX.x - PLAYER_SIZE.x;
	final float PLAYER_MAX_DIST_TO_CENTER_SQUARED = PLAYER_MAX_DIST_TO_CENTER
			* PLAYER_MAX_DIST_TO_CENTER;

	int millisSinceLastJump = 0;
	final int MILLIS_BETWEEN_JUMPS = 100;

	@Override
	public void init() {
		useFBO = false;
		initDisplay(new GLDisplay(), new DisplayMode(), new PixelFormat(),
				new VideoSettings(DefaultValues.DEFAULT_VIDEO_RESOLUTION_X,
						DefaultValues.DEFAULT_VIDEO_RESOLUTION_Y,
						DefaultValues.DEFAULT_VIDEO_FOVY,
						DefaultValues.DEFAULT_VIDEO_ZNEAR,
						DefaultValues.DEFAULT_VIDEO_ZFAR, false),
				new NullSoundEnvironment());
		layer3d.setProjectionMatrix(ProjectionHelper.ortho(SCREEN_MAX.x,
				SCREEN_MIN.x, SCREEN_MIN.y, SCREEN_MAX.y, -10, 10));
		// layer3d.setProjectionMatrix(ProjectionHelper.perspective(90, 3/4f,
		// 0.1f, 100f));
		display.bindMouse();
		cam.setFlyCam(false);
		cam.translateTo(0f, 0f, 0);
		cam.rotateTo(0, 0);

		Shader defaultshader = new Shader(ShaderLoader.loadShaderFromFile(
				"res/shaders/defaultshader.vert",
				"res/shaders/defaultshader.frag"));
		addShader(defaultshader);
		Shader defaultshaderInterface = new Shader(
				ShaderLoader.loadShaderFromFile(
						"res/shaders/defaultshader.vert",
						"res/shaders/defaultshader.frag"));
		addShaderInterface(defaultshaderInterface);
		cutshader = new Shader(ShaderLoader.loadShaderFromFile(
				"res/shaders/cutshader.vert", "res/shaders/cutshader.frag"));
		addShader(cutshader);
		cutshader.addArgument("cameraNormal", cam.getDirection());

		Input inputKeyW = new Input(Input.KEYBOARD_EVENT, "W",
				KeyInput.KEY_DOWN);
		Input inputKeyA = new Input(Input.KEYBOARD_EVENT, "A",
				KeyInput.KEY_DOWN);
		Input inputKeyS = new Input(Input.KEYBOARD_EVENT, "S",
				KeyInput.KEY_DOWN);
		Input inputKeyD = new Input(Input.KEYBOARD_EVENT, "D",
				KeyInput.KEY_DOWN);
		Input inputKeySpace = new Input(Input.KEYBOARD_EVENT, "Space",
				KeyInput.KEY_DOWN);
		Input inputKeyUp = new Input(Input.KEYBOARD_EVENT, "Up",
				KeyInput.KEY_DOWN);
		Input inputKeyLeft = new Input(Input.KEYBOARD_EVENT, "Left",
				KeyInput.KEY_DOWN);
		Input inputKeyDown = new Input(Input.KEYBOARD_EVENT, "Down",
				KeyInput.KEY_DOWN);
		Input inputKeyRight = new Input(Input.KEYBOARD_EVENT, "Right",
				KeyInput.KEY_DOWN);
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

		space = new PhysicsSpace(new VerletIntegration(), new SAP(), new GJK(
				new EPA()), new ImpulseResolution(), new ProjectionCorrection(
				0.01f), new SimpleManifoldManager<Vector3f>());
		space.setGlobalGravitation(new Vector3f(0, -8f, 0));

		Font font = FontLoader.loadFont("res/fonts/DejaVuSans.ttf");
		debugger = new Debugger(inputs, defaultshader, defaultshaderInterface,
				font, cam);
		physicsdebug = new PhysicsDebug(inputs, font, space);
		GameProfiler gp = new SimpleGameProfiler();
		setProfiler(gp);
		PhysicsProfiler pp = new SimplePhysicsProfiler();
		space.setProfiler(pp);
		profiler = new Profiler(this, inputs, font, gp, pp);

		player = new Cylinder(0, 0, 0, PLAYER_SIZE.x, PLAYER_SIZE.y, 36);
		playerbody = new RigidBody3(PhysicsShapeCreator.create(player));
		playerbody.setMass(1f);
		playerbody.setAngularFactor(new Vector3f(0, 0, 0));
		playerbody.setRestitution(0);
		space.addRigidBody(player, playerbody);
		cutshader.addObject(player);

		playerJumpChecker = new GhostObject3(
				new CylinderShape(0, 0, 0, PLAYER_SIZE.x
						- PLAYER_CHECKER_SIDE_OFFSET, PLAYER_CHECKER_SIZE));
		playerJumpChecker.setMass(1f);
		playerJumpChecker.setLinearFactor(new Vector3f(0, 0, 0));
		playerJumpChecker.setAngularFactor(new Vector3f(0, 0, 0));
		playerJumpChecker.setRestitution(0);
		space.addGhostObject(playerJumpChecker);

		Box box = new Box(4, -4, 4, 1, 0.3f, 1);
		RigidBody3 rb1 = new RigidBody3(PhysicsShapeCreator.create(box));
		space.addRigidBody(box, rb1);
		cutshader.addObject(box);

		onGround = false;

		cutshader.addObject(new Sphere(0, 2, 0, 1, 36, 36));
		cutshader.addObject(new Box(0, 4, 0, 1, 1, 1));

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
		Vector3f move = new Vector3f();
		Vector2f pos = new Vector2f(playerbody.getTranslation().x,
				playerbody.getTranslation().z);
		boolean isPlayerNotInCenter = pos.lengthSquared() > 0.01f;

		if (isPlayerNotInCenter) {
			float distToMid = (float) pos.length();
			if (up.isActive()) {
				move.x += frontVec.x * distToMid * PLAYER_MOVE_SPEED_Z;
				move.z += frontVec.z * distToMid * PLAYER_MOVE_SPEED_Z;
			}
			if (down.isActive()) {
				move.x -= frontVec.x * distToMid * PLAYER_MOVE_SPEED_Z;
				move.z -= frontVec.z * distToMid * PLAYER_MOVE_SPEED_Z;
			}
			if (up.isActive() || down.isActive()) {
				float movespeed = (float) new Vector2f(move.x, move.z).length();
				Vector2f predictedPos = VecMath.addition(pos, new Vector2f(
						move.x * delta / 1000f, move.z * delta / 1000f));
				predictedPos.normalize();
				predictedPos.scale(distToMid);
				move.x = predictedPos.x - pos.x;
				move.z = predictedPos.y - pos.y;
				move.setLength(movespeed);
			}
		}

		if (left.isActive()) {
			move.x += rightVec.x * PLAYER_MOVE_SPEED_X;
			move.z += rightVec.z * PLAYER_MOVE_SPEED_X;
		}
		if (right.isActive()) {
			move.x -= rightVec.x * PLAYER_MOVE_SPEED_X;
			move.z -= rightVec.z * PLAYER_MOVE_SPEED_X;
		}
		if (millisSinceLastJump < MILLIS_BETWEEN_JUMPS) {
			millisSinceLastJump += delta;
		}
		if (jump.isActive() && onGround
				&& millisSinceLastJump >= MILLIS_BETWEEN_JUMPS) {
			move.y += PLAYER_JUMP_STRENGTH;
			millisSinceLastJump = 0;
		}

		playerbody.setLinearVelocity(new Vector3f(move.x, playerbody
				.getLinearVelocity().y + move.y, move.z));

		playerJumpChecker.setTranslation(new Vector3f(playerbody
				.getTranslation().x, playerbody.getTranslation().y
				- PLAYER_SIZE.y - PLAYER_CHECKER_OFFSET, playerbody
				.getTranslation().z));

		space.update(delta);

		onGround = space.hasCollision(playerJumpChecker);

		// update pos after physics update
		pos = new Vector2f(player.getTranslation().x, player.getTranslation().z);
		float distToCenterSquared = (float) pos.lengthSquared();
		if (distToCenterSquared > PLAYER_MAX_DIST_TO_CENTER_SQUARED) {
			pos.setLength(PLAYER_MAX_DIST_TO_CENTER);
			player.getTranslation().x = pos.x;
			player.getTranslation().z = pos.y;
		}
		if (player.getTranslation().y > PLAYER_MAX_Y) {
			player.getTranslation().y = PLAYER_MAX_Y;
			playerbody.getLinearVelocity().y = 0;
		}
		if (player.getTranslation().y < -PLAYER_MAX_Y) {
			player.getTranslation().y = -PLAYER_MAX_Y;
			playerbody.getLinearVelocity().y = 0;
			onGround = true;
		}

		isPlayerNotInCenter = distToCenterSquared > 0.01f;
		if (isPlayerNotInCenter) {
			pos.normalize();
			if (VecMath.dotproduct(pos, new Vector2f(rightVec.x, rightVec.z)) < 0)
				pos.negate();
			float dotX = VecMath.dotproduct(pos, vecX);
			float angleX = (float) Math.toDegrees(Math.acos(dotX));
			float angle = 0;
			if (pos.y < 0)
				angle = angleX;
			else
				angle = 180 + (180 - angleX);
			cam.rotateTo(angle, 0);
			intersectionInterface.rotateTo(angle);
		}

		player.updateBuffer();
		cam.updateBuffer();
		cutshader.setArgument("cameraNormal", cam.getDirection());
	}
}
