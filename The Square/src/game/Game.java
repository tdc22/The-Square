package game;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import broadphase.SAP;
import collisionshape.CylinderShape;
import curves.BezierCurve3;
import curves.SimpleCurvePath3;
import curves.SquadCurve3;
import display.DisplayMode;
import display.GLDisplay;
import display.PixelFormat;
import display.VideoSettings;
import gui.Font;
import gui.Text;
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
import quaternion.Quaternionf;
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
	Font font;
	Shader cutshader, defaultshaderInterface;

	Sphere goal;
	RigidBody3 goalbody;

	final Vector2f SCREEN_MIN = new Vector2f(-6, -5);
	final Vector2f SCREEN_MAX = new Vector2f(6, 5);

	final Vector2f PLAYER_SIZE = new Vector2f(0.5f, 0.5f);
	final float PLAYER_MOVE_SPEED_X = 4f;
	final float PLAYER_MOVE_SPEED_Z = 1.5f;
	final float PLAYER_CHECKER_SIZE = 0.01f;
	final float PLAYER_CHECKER_OFFSET = PLAYER_CHECKER_SIZE + 0.01f;
	final float PLAYER_CHECKER_SIDE_OFFSET = 0.15f;
	final float PLAYER_JUMP_STRENGTH = 6f;
	final float PLAYER_MAX_Y = SCREEN_MAX.y - PLAYER_SIZE.y + 0.5f;
	final float PLAYER_MAX_DIST_TO_CENTER = SCREEN_MAX.x - PLAYER_SIZE.x;
	final float PLAYER_MAX_DIST_TO_CENTER_SQUARED = PLAYER_MAX_DIST_TO_CENTER * PLAYER_MAX_DIST_TO_CENTER;

	int millisSinceLastJump = 0;
	final int MILLIS_BETWEEN_JUMPS = 400;
	int level = 1;
	List<ShapedObject3> levelObjects;
	List<RigidBody3> levelObjectBodies;
	List<Text> levelTexts;

	SimpleCurvePath3 cameraCurvePath;
	SquadCurve3 cameraAngularCurvePath;
	boolean lastLevel = false;

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
		defaultshaderInterface = new Shader(
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

		font = FontLoader.loadFont("res/fonts/DejaVuSans.ttf");
		debugger = new Debugger(inputs, defaultshader, defaultshaderInterface, font, cam);
		physicsdebug = new PhysicsDebug(inputs, font, space);
		GameProfiler gp = new SimpleGameProfiler();
		setProfiler(gp);
		PhysicsProfiler pp = new SimplePhysicsProfiler();
		space.setProfiler(pp);
		profiler = new Profiler(this, inputs, font, gp, pp);

		player = new Cylinder(0, 0, 0, PLAYER_SIZE.x, PLAYER_SIZE.y, 36);
		player.setColor(Color.red);
		player.setRenderHints(true, false, true);
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
		goal.setRenderHints(true, false, true);
		goalbody = new RigidBody3(PhysicsShapeCreator.create(goal));
		space.addRigidBody(goal, goalbody);
		cutshader.addObject(goal);

		levelObjects = new ArrayList<ShapedObject3>();
		levelObjectBodies = new ArrayList<RigidBody3>();
		levelTexts = new ArrayList<Text>();
		loadLevel(6);

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
		if (lastLevel)
			frontVec.set(0, 0, -1);
		frontVec.normalize();
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
			playerbody.getLinearVelocity().y = 0;
			move.y = PLAYER_JUMP_STRENGTH;
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

		if (!lastLevel) {
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
			cutshader.setArgument("cameraNormal", cam.getDirection());
		} else {
			float t = (5.5f - player.getTranslation().x) / 10f;
			System.out.println(player.getTranslation().x + "; " + t + "; "
					+ cameraCurvePath.getPoint((5.5f - player.getTranslation().x) / 10f) + "; "
					+ cameraCurvePath.getPoint(t) + "; " + cameraAngularCurvePath.getRotation(t));
			cam.translateTo(cameraCurvePath.getPoint(t));
			cam.rotateTo(cameraAngularCurvePath.getRotation(t));
		}

		player.updateBuffer();
		cam.updateBuffer();

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
		for (Text t : levelTexts) {
			defaultshaderInterface.removeObject(t);
			t.delete();
		}
		levelObjects.clear();
		levelObjectBodies.clear();
		levelTexts.clear();
		cam.rotateTo(0, 0);

		switch (level) {
		case 1:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			Text t1 = new Text("You are a square.", 20, 500, font, 24);
			defaultshaderInterface.addObject(t1);
			levelTexts.add(t1);
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

			Text t2 = new Text("You have one goal.", 280, 460, font, 24);
			defaultshaderInterface.addObject(t2);
			levelTexts.add(t2);
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

			Text t3 = new Text("Being\nwith B.", 700, 500, font, 24);
			defaultshaderInterface.addObject(t3);
			levelTexts.add(t3);
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

			Text t4 = new Text("Walls separating you.", 270, 500, font, 24);
			defaultshaderInterface.addObject(t4);
			levelTexts.add(t4);
			break;
		case 5:
			player.translateTo(5, -5f, 0);
			goal.translateTo(0, 4f, 0);

			Cylinder cyl1 = new Cylinder(0f, -4.75f, -4f, 1, 0.75f, 36);
			RigidBody3 rb9 = new RigidBody3(PhysicsShapeCreator.create(cyl1));
			space.addRigidBody(cyl1, rb9);
			cutshader.addObject(cyl1);
			levelObjects.add(cyl1);
			levelObjectBodies.add(rb9);

			Cylinder cyl2 = new Cylinder(4f, -4f, 0f, 1, 1.5f, 36);
			RigidBody3 rb10 = new RigidBody3(PhysicsShapeCreator.create(cyl2));
			space.addRigidBody(cyl2, rb10);
			cutshader.addObject(cyl2);
			levelObjects.add(cyl2);
			levelObjectBodies.add(rb10);

			Cylinder cyl3 = new Cylinder(0f, -3.25f, 4f, 1, 2.25f, 36);
			RigidBody3 rb11 = new RigidBody3(PhysicsShapeCreator.create(cyl3));
			space.addRigidBody(cyl3, rb11);
			cutshader.addObject(cyl3);
			levelObjects.add(cyl3);
			levelObjectBodies.add(rb11);

			Cylinder cyl4 = new Cylinder(-4f, -2.75f, -0f, 1, 3f, 36);
			RigidBody3 rb12 = new RigidBody3(PhysicsShapeCreator.create(cyl4));
			space.addRigidBody(cyl4, rb12);
			cutshader.addObject(cyl4);
			levelObjects.add(cyl4);
			levelObjectBodies.add(rb12);

			Cylinder cyl5 = new Cylinder(0f, -2.25f, -0f, 1, 3.75f, 36);
			RigidBody3 rb13 = new RigidBody3(PhysicsShapeCreator.create(cyl5));
			space.addRigidBody(cyl5, rb13);
			cutshader.addObject(cyl5);
			levelObjects.add(cyl5);
			levelObjectBodies.add(rb13);

			Text t6 = new Text("B is round.         Beautiful.", 240, 90, font, 24);
			defaultshaderInterface.addObject(t6);
			levelTexts.add(t6);
			break;
		case 6:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, 1f, 0);

			Box box4 = new Box(-3.5f, -2.5f, 0, 2.5f, 3f, 6f);
			RigidBody3 rb5 = new RigidBody3(PhysicsShapeCreator.create(box4));
			space.addRigidBody(box4, rb5);
			cutshader.addObject(box4);
			levelObjects.add(box4);
			levelObjectBodies.add(rb5);

			Box box5 = new Box(0, -4, 0, 1, 1.5f, 2);
			RigidBody3 rb6 = new RigidBody3(PhysicsShapeCreator.create(box5));
			space.addRigidBody(box5, rb6);
			cutshader.addObject(box5);
			levelObjects.add(box5);
			levelObjectBodies.add(rb6);

			Box box6 = new Box(0, -4.75f, 4, 1, 0.75f, 2);
			RigidBody3 rb7 = new RigidBody3(PhysicsShapeCreator.create(box6));
			space.addRigidBody(box6, rb7);
			cutshader.addObject(box6);
			levelObjects.add(box6);
			levelObjectBodies.add(rb7);

			Box box7 = new Box(0, -3.25f, -4, 1, 2.25f, 2);
			RigidBody3 rb8 = new RigidBody3(PhysicsShapeCreator.create(box7));
			space.addRigidBody(box7, rb8);
			cutshader.addObject(box7);
			levelObjects.add(box7);
			levelObjectBodies.add(rb8);

			Text t5 = new Text("You are different.\n\n                               Ugly.", 465, 260, font, 24);
			defaultshaderInterface.addObject(t5);
			levelTexts.add(t5);
			break;
		case 7:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		case 8:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);
			break;
		default:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			cutshader.removeObject(player);
			cutshader.removeObject(goal);
			layer3d.getShader().remove(cutshader);
			cutshader.delete();

			Shader phongshader = new Shader(
					ShaderLoader.loadShaderFromFile("res/shaders/phongshader.vert", "res/shaders/phongshader.frag"));
			phongshader.addArgumentName("u_lightpos");
			phongshader.addArgument(new Vector3f(9, -5, 0));
			phongshader.addArgumentName("u_ambient");
			phongshader.addArgument(new Vector3f(0.2f, 0.2f, 0.2f));
			phongshader.addArgumentName("u_diffuse");
			phongshader.addArgument(new Vector3f(0.5f, 0.5f, 0.5f));
			phongshader.addArgumentName("u_shininess");
			phongshader.addArgument(20f);
			addShader(phongshader);

			phongshader.addObject(player);
			phongshader.addObject(goal);

			setDepthTestEnabled(true);
			Vector3f newCamPos = new Vector3f(0, 0, -10);
			cameraCurvePath = new SimpleCurvePath3();
			// cameraCurvePath.addCurve(new BezierCurve3(newCamPos, new
			// Vector3f(-1, 1, 1), new Vector3f(1, -1, -1), new Vector3f(0, 10,
			// 0)));
			cameraCurvePath.addCurve(new BezierCurve3(newCamPos, newCamPos, newCamPos, newCamPos));
			Quaternionf noRot = new Quaternionf();
			noRot.rotate(180, new Vector3f(0, 1, 0));
			Quaternionf fullRot = new Quaternionf();
			fullRot.rotate(0, new Vector3f(0, 1, 0));
			// cameraAngularCurvePath = new SquadCurve3(noRot, noRot, fullRot,
			// fullRot);
			cameraAngularCurvePath = new SquadCurve3(noRot, noRot, noRot, noRot);

			lastLevel = true;
			layer3d.setProjectionMatrix(ProjectionHelper.perspective(90, 3 / 4f, 0.1f, 100f));
			cam.translateTo(newCamPos);
			cam.rotateTo(180, 0);
			break;
		}
		System.out.println("Loaded level: " + level);
	}
}
