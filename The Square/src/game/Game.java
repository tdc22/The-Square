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
import narrowphase.SupportRaycast;
import objects.GhostObject3;
import objects.RigidBody3;
import objects.ShapedObject3;
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
import utils.Debugger;
import utils.DefaultValues;
import utils.ProjectionHelper;
import vector.Vector2f;
import vector.Vector3f;
import vector.Vector4f;

public class Game extends StandardGame {
	// Sorry for the awful code.
	// Made by tdc (@tdc_22) inspired by Happie's multidimensional gamedev
	// challenge.

	Debugger debugger;
	PhysicsSpace space;

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
	Shader cutshader, defaultshaderInterface, finalTextInterface;

	Sphere goal;
	RigidBody3 goalbody;

	final Vector2f SCREEN_MIN = new Vector2f(-6, -5);
	final Vector2f SCREEN_MAX = new Vector2f(6, 5);

	final Vector2f PLAYER_SIZE = new Vector2f(0.5f, 0.5f);
	final float PLAYER_MOVE_SPEED_X = 4f;
	final float PLAYER_MOVE_SPEED_Z = 1.5f;
	final float PLAYER_MOVE_SPEED_IN_FINAL_LEVEL = 1.8f;
	final float PLAYER_CHECKER_SIZE = 0.01f;
	final float PLAYER_CHECKER_OFFSET = PLAYER_CHECKER_SIZE + 0.01f;
	final float PLAYER_CHECKER_SIDE_OFFSET = 0.15f;
	final float PLAYER_JUMP_STRENGTH = 6f;
	final float PLAYER_MAX_Y = SCREEN_MAX.y - PLAYER_SIZE.y + 0.5f;
	final float PLAYER_MAX_DIST_TO_CENTER = SCREEN_MAX.x - PLAYER_SIZE.x;
	final float PLAYER_MAX_DIST_TO_CENTER_SQUARED = PLAYER_MAX_DIST_TO_CENTER * PLAYER_MAX_DIST_TO_CENTER;
	final int MILLIS_BETWEEN_JUMPS = 400;
	int millisSinceLastJump = 0;
	float lastLevelPositionXExtension, lastLevelPositionXMax;

	int level;
	List<ShapedObject3> levelObjects;
	List<RigidBody3> levelObjectBodies;
	List<Text> levelTexts;

	SimpleCurvePath3 cameraCurvePath;
	SquadCurve3 cameraAngularCurvePath;
	boolean lastLevel = false;
	boolean isRunning = true;
	boolean lockedDepth;

	@Override
	public void init() {
		useFBO = false;
		depthTestEnabled = false;
		initDisplay(new GLDisplay(), new DisplayMode(800, 600, "The Square", true), new PixelFormat(),
				new VideoSettings(DefaultValues.DEFAULT_VIDEO_RESOLUTION_X, DefaultValues.DEFAULT_VIDEO_RESOLUTION_Y,
						DefaultValues.DEFAULT_VIDEO_FOVY, DefaultValues.DEFAULT_VIDEO_ZNEAR,
						DefaultValues.DEFAULT_VIDEO_ZFAR, false),
				new NullSoundEnvironment());
		layer3d.setProjectionMatrix(
				ProjectionHelper.ortho(SCREEN_MAX.x, SCREEN_MIN.x, SCREEN_MIN.y, SCREEN_MAX.y, -10, 10));
		
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

		space = new PhysicsSpace(new VerletIntegration(), new SAP(), new GJK(new EPA()), new SupportRaycast(),
				new SimpleLinearImpulseResolution(), new ProjectionCorrection(0.01f),
				new SimpleManifoldManager<Vector3f>());
		space.setGlobalGravitation(new Vector3f(0, -8f, 0));

		font = FontLoader.loadFont("res/fonts/DejaVuSans.ttf");
		debugger = new Debugger(inputs, defaultshader, defaultshaderInterface, font, cam);

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
		loadLevel(1); // If you instantly load the last level, depth test is
						// disabled and the level might look buggy.

		onGround = false;

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

	private Vector2f pos = new Vector2f();
	private Vector3f move = new Vector3f();

	@Override
	public void update(int delta) {
		debugger.update(fps, 0, 0);

		if (isRunning) {
			Vector3f frontVec = VecMath.transformVector(cam.getMatrix(), vecRight);
			if (lastLevel)
				frontVec.set(0, 0, -1);
			frontVec.normalize();
			Vector3f rightVec = VecMath.crossproduct(frontVec, vecUp);
			move.set(0, 0, 0);
			pos.set(playerbody.getTranslation().x, playerbody.getTranslation().z);
			boolean isPlayerNotInCenter = pos.lengthSquared() > 0.01f;

			if (!lastLevel) {
				if (!up.isActive() && !down.isActive())
					lockedDepth = false;
				if (isPlayerNotInCenter) {
					float distToMid = (float) pos.length();
					if (up.isActive() && !lockedDepth) {
						move.x += frontVec.x * distToMid * PLAYER_MOVE_SPEED_Z;
						move.z += frontVec.z * distToMid * PLAYER_MOVE_SPEED_Z;
					}
					if (down.isActive() && !lockedDepth) {
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
			} else {
				if (up.isActive()) {
					move.x -= frontVec.x;
					move.z -= frontVec.z;
				}
				if (down.isActive()) {
					move.x += frontVec.x;
					move.z += frontVec.z;
				}
				if (left.isActive()) {
					move.x += rightVec.x;
					move.z += rightVec.z;
				}
				if (right.isActive()) {
					move.x -= rightVec.x;
					move.z -= rightVec.z;
				}
				move.scale(PLAYER_MOVE_SPEED_IN_FINAL_LEVEL);
			}

			if (millisSinceLastJump < MILLIS_BETWEEN_JUMPS) {
				millisSinceLastJump += delta;
			}
			if (jump.isActive() && onGround && millisSinceLastJump >= MILLIS_BETWEEN_JUMPS) {
				playerbody.getLinearVelocity().y = 0;
				move.y = PLAYER_JUMP_STRENGTH;
				millisSinceLastJump = 0;
			}

			playerbody.setLinearVelocity(move.x, playerbody.getLinearVelocity().y + move.y, move.z);

			playerJumpChecker.setTranslation(new Vector3f(playerbody.getTranslation().x,
					playerbody.getTranslation().y - PLAYER_SIZE.y - PLAYER_CHECKER_OFFSET,
					playerbody.getTranslation().z));

			space.update(delta);

			onGround = space.hasCollision(playerJumpChecker);

			// update pos after physics update
			pos.set(player.getTranslation().x, player.getTranslation().z);
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
				float t = getRelativePosition(player.getTranslation().x);
				cam.translateTo(cameraCurvePath.getPoint(t));
				cam.rotateTo(cameraAngularCurvePath.getRotation(t));
				finalTextInterface.setArgument("u_positionX", t);
				if (player.getTranslation().x < lastLevelPositionXMax) {
					lastLevelPositionXMax = player.getTranslation().x;
					finalTextInterface.setArgument("u_maxPositionX", getRelativePosition(lastLevelPositionXMax));
				}
			}

			player.updateBuffer();
			cam.updateBuffer();

			if (space.hasCollision(playerbody, goalbody)) {
				if (!lastLevel) {
					loadLevel(level + 1);
					intersectionInterface.rotateTo(0);
				} else {
					isRunning = false;

					Text tend = new Text("THE END", 280, 260, font, 64);
					defaultshaderInterface.addObject(tend);

					Text ttdc = new Text("Made by tdc. (@tdc_22)", 275, 400, font, 24);
					defaultshaderInterface.addObject(ttdc);

					Text tesc = new Text("Press Escape to quit.", 290, 460, font, 24);
					defaultshaderInterface.addObject(tesc);

					lastLevelPositionXExtension = player.getTranslation().x;
					lastLevelPositionXMax = player.getTranslation().x;
				}
			}
		} else {
			if (lastLevelPositionXExtension > -10) {
				lastLevelPositionXExtension -= PLAYER_MOVE_SPEED_IN_FINAL_LEVEL * delta / 1000f;
				finalTextInterface.setArgument("u_positionX", getRelativePosition(lastLevelPositionXExtension));
			}
		}
	}

	private float getRelativePosition(float transX) {
		return (5.5f - transX) / 10f;
	}

	public void loadLevel(int level) {
		this.level = level;
		lockedDepth = true;

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

		// load current level
		switch (level) {
		case 1:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			addLevelText("You are a square.", 20, 500);
			break;
		case 2:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			addBox(0, -5f, 0, 0.5f, 0.5f, 0.5f);

			addLevelText("You have one goal.", 280, 460);
			break;
		case 3:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			addCylinder(0, 0, 0, 4, 6);

			addLevelText("Being\nwith B.", 700, 500);
			break;
		case 4:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, -5f, 0);

			addBox(2.5f, 0, 2.5f, 0.5f, 6f, 3f);
			addBox(-2.5f, 0, -2.5f, 0.5f, 6f, 3f);

			addLevelText("Walls separating you.", 270, 500);
			break;
		case 5:
			player.translateTo(5, -5f, 0);
			goal.translateTo(0, 4f, 0);

			addCylinder(0f, -4.75f, -4f, 1, 0.75f);
			addCylinder(4f, -4f, 0f, 1, 1.5f);
			addCylinder(0f, -3.25f, 4f, 1, 2.25f);
			addCylinder(-4f, -2.75f, -0f, 1, 3f);
			addCylinder(0f, -2.25f, -0f, 1, 3.75f);

			addLevelText("B is round.         Perfect.", 240, 90);
			break;
		case 6:
			player.translateTo(5, -5f, 0);
			goal.translateTo(-5, 1f, 0);

			addBox(-3.5f, -2.5f, 0, 2.5f, 3f, 6f);
			addBox(0, -4, 0, 1, 1.5f, 2);
			addBox(0, -4.75f, 4, 1, 0.75f, 2);
			addBox(0, -3.25f, -4, 1, 2.25f, 2);

			addLevelText("You are different.\n\n                             Useless.", 465, 260);
			break;
		case 7:
			player.translateTo(5, -5f, 0);
			goal.translateTo(0, 5f, 0);

			addCylinder(0f, -2.2f, 0f, 4, 2);
			addBox(3, -4, 0, 3, 0.2f, 3);
			addBox(0, -2f, 3, 3, 0.2f, 3);
			addBox(0, -0.41f, 3, 3, 0.2f, 3);
			addBox(-3, -2f, 0, 3, 0.2f, 3);
			addBox(0, 1.4f, 0, 6, 0.2f, 2);
			addBox(0, 3.8f, 1.8f, 6, 2.2f, 0.2f);
			addBox(0, 3.8f, -1.8f, 1, 2.2f, 0.2f);

			addLevelText("4 Corners. 4 Edges. Ugly.", 240, 575);
			break;
		case 8:
			player.translateTo(5, -5f, 0);
			goal.translateTo(0, -5f, 5);

			addCylinder(-4f, -4f, -0f, 1, 1.5f);
			addCylinder(-4f, -4.75f, -0f, 2f, 0.75f);
			addCylinder(0f, -3.25f, -4f, 1, 2.25f);
			addCylinder(0f, -1f, -0f, 1, 1.5f);
			addBox(0, -1, 3.8f, 6, 5, 0.2f);
			addCylinder(4f, 0.75f, -0f, 1, 1.5f);

			addLevelText("Time passes.\nAnd finally you realise...", 260, 180);
			break;
		default:
			// final level
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
			Vector3f newCamPos = new Vector3f(0, -5, -10);
			cameraCurvePath = new SimpleCurvePath3();
			cameraCurvePath
					.addCurve(new BezierCurve3(newCamPos, newCamPos, new Vector3f(0, 10, 0), new Vector3f(0, 10, 0)));
			Quaternionf noRot = new Quaternionf();
			noRot.rotate(180, new Vector3f(0, 1, 0));
			Quaternionf fullRot = new Quaternionf();
			fullRot.rotate(180, new Vector3f(0, 1, 0));
			fullRot.rotate(-90, new Vector3f(1, 0, 0));
			cameraAngularCurvePath = new SquadCurve3(noRot, fullRot, noRot, fullRot);

			lastLevel = true;
			layer3d.setProjectionMatrix(ProjectionHelper.perspective(90, 3 / 4f, 0.1f, 100f));
			cam.translateTo(newCamPos);
			cam.rotateTo(180, 0);

			finalTextInterface = new Shader(ShaderLoader.loadShaderFromFile("res/shaders/finaltextshader.vert",
					"res/shaders/finaltextshader.frag"));
			finalTextInterface.addArgument("u_positionX", 10.0f);
			finalTextInterface.addArgument("u_maxPositionX", 10.0f);
			addShaderInterface(finalTextInterface);
			lastLevelPositionXMax = 10;

			Text t9 = new Text("that it's just a matter of perspective.", 180, 180, font, 24);
			finalTextInterface.addObject(t9);
			levelTexts.add(t9);
			break;
		}
		System.out.println("Loaded level: " + level);
	}

	public void addBox(float x, float y, float z, float sizeX, float sizeY, float sizeZ) {
		Box box = new Box(x, y, z, sizeX, sizeY, sizeZ);
		RigidBody3 rb = new RigidBody3(PhysicsShapeCreator.create(box));
		space.addRigidBody(box, rb);
		cutshader.addObject(box);
		levelObjects.add(box);
		levelObjectBodies.add(rb);
	}

	public void addCylinder(float x, float y, float z, float radius, float halfheight) {
		Cylinder cyl = new Cylinder(x, y, z, radius, halfheight, 72);
		RigidBody3 rb = new RigidBody3(PhysicsShapeCreator.create(cyl));
		space.addRigidBody(cyl, rb);
		cutshader.addObject(cyl);
		levelObjects.add(cyl);
		levelObjectBodies.add(rb);
	}

	public void addLevelText(String text, float x, float y) {
		Text levelText = new Text(text, x, y, font, 24);
		defaultshaderInterface.addObject(levelText);
		levelTexts.add(levelText);
	}
}
