package game;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import broadphase.SAP;
import collisionshape.CylinderShape;
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
import objects.GhostObject3;
import objects.RigidBody3;
import objects.ShapedObject3;
import physics.PhysicsDebug;
import physics.PhysicsShapeCreator;
import physics.PhysicsSpace;
import positionalcorrection.ProjectionCorrection;
import resolution.SimpleLinearImpulseResolution;
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
import vector.Vector4f;

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

	Sphere goal;
	RigidBody3 goalbody;

	final Vector2f SCREEN_MIN = new Vector2f(-6, -5);
	final Vector2f SCREEN_MAX = new Vector2f(6, 5);

	final Vector2f PLAYER_SIZE = new Vector2f(0.5f, 0.5f);
	final float PLAYER_MOVE_SPEED_X = 4f;
	final float PLAYER_MOVE_SPEED_Z = 1.5f;
	final float PLAYER_CHECKER_SIZE = 0.1f;
	final float PLAYER_CHECKER_OFFSET = PLAYER_CHECKER_SIZE + 0.1f;
	final float PLAYER_CHECKER_SIDE_OFFSET = 0.05f;
	final float PLAYER_JUMP_STRENGTH = 6f;
	final float PLAYER_MAX_Y = SCREEN_MAX.y - PLAYER_SIZE.y + 0.5f;
	final float PLAYER_MAX_DIST_TO_CENTER = SCREEN_MAX.x - PLAYER_SIZE.x;
	final float PLAYER_MAX_DIST_TO_CENTER_SQUARED = PLAYER_MAX_DIST_TO_CENTER * PLAYER_MAX_DIST_TO_CENTER;

	int millisSinceLastJump = 0;
	final int MILLIS_BETWEEN_JUMPS = 200;
	int level = 1;
	List<ShapedObject3> levelObjects;
	List<RigidBody3> levelObjectBodies;

	@Override
	public void init() {
		useFBO = false;
		depthTestEnabled = false;
		initDisplay(new GLDisplay(), new DisplayMode(), new PixelFormat(),
				new VideoSettings(DefaultValues.DEFAULT_VIDEO_RESOLUTION_X, DefaultValues.DEFAULT_VIDEO_RESOLUTION_Y,
						DefaultValues.DEFAULT_VIDEO_FOVY, DefaultValues.DEFAULT_VIDEO_ZNEAR,
						DefaultValues.DEFAULT_VIDEO_ZFAR, false),
				new NullSoundEnvironment());
		layer3d.setProjectionMatrix(
				ProjectionHelper.ortho(SCREEN_MAX.x, SCREEN_MIN.x, SCREEN_MIN.y, SCREEN_MAX.y, -10, 10));
		// layer3d.setProjectionMatrix(ProjectionHelper.perspective(90, 3/4f,
		// 0.1f, 100f));
		display.bindMouse();
		cam.setFlyCam(false);
		cam.translateTo(0f, 0f, 0);
		cam.rotateTo(0, 0);

		setRendered(true, false, true);

		Shader defaultshader = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/defaultshader.vert", "res/shaders/defaultshader.frag"));
		addShader(defaultshader);
		cutshader = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/cutshader.vert", "res/shaders/cutshader.frag"));
		cutshader.addArgument("cameraNormal", cam.getDirection());
		addShader(cutshader);
		Shader defaultshaderInterface = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/defaultshader.vert", "res/shaders/defaultshader.frag"));
		addShaderInterface(defaultshaderInterface);

		Shader interfaceOverlay = new Shader(
				ShaderLoader.loadShaderFromFile("res/shaders/colorshader.vert", "res/shaders/colorshader.frag"));
		interfaceOverlay.addArgumentName("u_color");
		interfaceOverlay.addArgument(new Vector4f(1f, 1f, 1f, 0.5f));
		addShaderInterface(interfaceOverlay);

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

		space = new PhysicsSpace(new VerletIntegration(), new SAP(), new GJK(new EPA()),
				new SimpleLinearImpulseResolution(), new ProjectionCorrection(0.01f),
				new SimpleManifoldManager<Vector3f>());
		space.setGlobalGravitation(new Vector3f(0, -8f, 0));

		Font font = FontLoader.loadFont("res/fonts/DejaVuSans.ttf");
		debugger = new Debugger(inputs, defaultshader, defaultshaderInterface, font, cam);
		physicsdebug = new PhysicsDebug(inputs, font, space);
		GameProfiler gp = new SimpleGameProfiler();
		setProfiler(gp);
		PhysicsProfiler pp = new SimplePhysicsProfiler();
		space.setProfiler(pp);
		profiler = new Profiler(this, inputs, font, gp, pp);

		player = new Cylinder(0, 0, 0, PLAYER_SIZE.x, PLAYER_SIZE.y, 36);
		player.setColor(Color.red);
		playerbody = new RigidBody3(PhysicsShapeCreator.create(player));
		playerbody.setMass(1f);
		playerbody.setLinearFactor(new Vector3f(1, 1, 1));
		playerbody.setAngularFactor(new Vector3f(0, 0, 0));
		playerbody.setRestitution(0);
		playerbody.setStaticFriction(0f);
		playerbody.setDynamicFriction(0f);
		space.addRigidBody(player, playerbody);
		cutshader.addObject(player);

		playerJumpChecker = new GhostObject3(
				new CylinderShape(0, 0, 0, PLAYER_SIZE.x - PLAYER_CHECKER_SIDE_OFFSET, PLAYER_CHECKER_SIZE));
		playerJumpChecker.setMass(1f);
		playerJumpChecker.setLinearFactor(new Vector3f(0, 0, 0));
		playerJumpChecker.setAngularFactor(new Vector3f(0, 0, 0));
		playerJumpChecker.setRestitution(0);
		space.addGhostObject(playerJumpChecker);

		goal = new Sphere(0, 2, 0, 0.5f, 36, 36);
		goal.setColor(Color.blue);
		goalbody = new RigidBody3(PhysicsShapeCreator.create(goal));
		space.addRigidBody(goal, goalbody);
		cutshader.addObject(goal);

		levelObjects = new ArrayList<ShapedObject3>();
		levelObjectBodies = new ArrayList<RigidBody3>();
		loadLevel(1);

		/*
		 * Box box1 = new Box(4, -4, 4, 1, 0.3f, 1); RigidBody3 rb1 = new
		 * RigidBody3(PhysicsShapeCreator.create(box1));
		 * space.addRigidBody(box1, rb1); cutshader.addObject(box1);
		 * 
		 * Box box2 = new Box(4, 0, 4, 1, 1f, 1); RigidBody3 rb2 = new
		 * RigidBody3(PhysicsShapeCreator.create(box2));
		 * space.addRigidBody(box2, rb2); cutshader.addObject(box2);
		 * 
		 * Box box3 = new Box(4, 4, 4, 1, 1f, 1); box3.rotate(45, 45, 0);
		 * RigidBody3 rb3 = new RigidBody3(PhysicsShapeCreator.create(box3));
		 * space.addRigidBody(box3, rb3); cutshader.addObject(box3);
		 */

		onGround = false;

		// cutshader.addObject(new Sphere(0, 2, 0, 1, 36, 36));
		// cutshader.addObject(new Box(0, 4, 0, 1, 1, 1));

		interfaceOverlay.addObject(new Circle(55, 55, 50, 36));
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
		Vector2f pos = new Vector2f(playerbody.getTranslation().x, playerbody.getTranslation().z);
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
				Vector2f predictedPos = VecMath.addition(pos,
						new Vector2f(move.x * delta / 1000f, move.z * delta / 1000f));
				predictedPos.normalize();
				predictedPos.scale(distToMid);
				move.x = predictedPos.x - pos.x;
				move.z = predictedPos.y - pos.y;
				if (move.lengthSquared() > 0)
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
		if (jump.isActive() && onGround && millisSinceLastJump >= MILLIS_BETWEEN_JUMPS) {
			move.y += PLAYER_JUMP_STRENGTH;
			millisSinceLastJump = 0;
		}

		playerbody.setLinearVelocity(new Vector3f(move.x, playerbody.getLinearVelocity().y + move.y, move.z));

		playerJumpChecker.setTranslation(new Vector3f(playerbody.getTranslation().x,
				playerbody.getTranslation().y - PLAYER_SIZE.y - PLAYER_CHECKER_OFFSET, playerbody.getTranslation().z));

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

		if (space.hasCollision(playerbody, goalbody)) {
			loadLevel(level + 1);
		}
	}

	public void loadLevel(int level) {
		this.level = level;

		// clear previous level
		for (int i = 0; i < levelObjects.size(); i++) {
			ShapedObject3 so = levelObjects.get(i);
			RigidBody3 rb = levelObjectBodies.get(i);
			cutshader.removeObject(so);
			space.removeRigidBody(so, rb);
			so.delete();
		}
		levelObjects.clear();
		levelObjectBodies.clear();
		cam.rotateTo(0, 0);

		switch (level) {
		case 1:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		case 2:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			Box box1 = new Box(0, -5f, 0, 0.5f, 0.5f, 0.5f);
			RigidBody3 rb1 = new RigidBody3(PhysicsShapeCreator.create(box1));
			space.addRigidBody(box1, rb1);
			cutshader.addObject(box1);
			levelObjects.add(box1);
			levelObjectBodies.add(rb1);
			break;
		case 3:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			Cylinder c1 = new Cylinder(0, 0, 0, 4, 6, 36);
			RigidBody3 rb2 = new RigidBody3(PhysicsShapeCreator.create(c1));
			space.addRigidBody(c1, rb2);
			cutshader.addObject(c1);
			levelObjects.add(c1);
			levelObjectBodies.add(rb2);
			break;
		case 4:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			Box box2 = new Box(2.5f, 0, 2.5f, 0.5f, 6f, 3f);
			RigidBody3 rb3 = new RigidBody3(PhysicsShapeCreator.create(box2));
			space.addRigidBody(box2, rb3);
			cutshader.addObject(box2);
			levelObjects.add(box2);
			levelObjectBodies.add(rb3);

			Box box3 = new Box(-2.5f, 0, -2.5f, 0.5f, 6f, 3f);
			RigidBody3 rb4 = new RigidBody3(PhysicsShapeCreator.create(box3));
			space.addRigidBody(box3, rb4);
			cutshader.addObject(box3);
			levelObjects.add(box3);
			levelObjectBodies.add(rb4);
			break;
		case 5:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		case 6:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		case 7:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		case 8:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		}
		System.out.println("Loaded level: " + level);
	}
}
