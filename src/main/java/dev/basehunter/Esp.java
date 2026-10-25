package dev.basehunter;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen-space ESP. Everything is projected to the 2D HUD, so it shows through walls and needs no custom
 * render pipeline. Accuracy is best in first person at the default FOV.
 */
public final class Esp {
	private static final int BASE_COLOR = 0xFF3030;
	private static final int PLAYER_COLOR = 0x30D0FF;
	private static final int CONTAINER_COLOR = 0xFFC030;
	private static final double NEAR = 0.05;

	// Camera basis for the current frame.
	private static Vec3 eye, fwd, right, up;
	private static double tanHalfFov, aspect;
	private static int sw, sh;

	private Esp() {}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		Config cfg = Config.INSTANCE;
		if (mc.player == null || mc.level == null || mc.options.hideGui) return;

		Alerts.renderFlash(g);
		if (cfg.hudInfo) renderInfo(g, mc);
		if (!mc.options.getCameraType().isFirstPerson()) return; // projection assumes first person

		float pt = delta.getGameTimeDeltaPartialTick(true);
		setupCamera(mc, g, pt);

		if (cfg.baseEsp) {
			for (BaseDetector.Base base : BaseDetector.INSTANCE.bases) {
				if (base.level != mc.level) continue;
				double dist = Math.sqrt(base.center.distToCenterSqr(eye.x, eye.y, eye.z));
				if (cfg.baseBlockBoxes) {
					for (BlockPos p : base.containers) drawBox(g, new AABB(p), CONTAINER_COLOR, null, false);
				}
				drawBox(g, base.bounds, BASE_COLOR,
					String.format("BASE  %d chests  %.0fm", base.chests, dist), cfg.tracers);
			}
		}

		if (cfg.playerEsp) {
			List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());
			int tracerBudget = 16;
			for (Player p : players) {
				if (p == mc.player) continue;
				double ix = Mth.lerp((double) pt, p.xo, p.getX()) - p.getX();
				double iy = Mth.lerp((double) pt, p.yo, p.getY()) - p.getY();
				double iz = Mth.lerp((double) pt, p.zo, p.getZ()) - p.getZ();
				AABB box = p.getBoundingBox().move(ix, iy, iz);
				double dist = eye.distanceTo(box.getCenter());
				boolean tracer = cfg.tracers && tracerBudget-- > 0;
				drawBox(g, box, PLAYER_COLOR, String.format("%s  %.0fm", p.getName().getString(), dist), tracer);
			}
		}
	}

	private static void renderInfo(GuiGraphicsExtractor g, Minecraft mc) {
		AutoFinder af = AutoFinder.INSTANCE;
		int y = 4;
		g.text(mc.font, "BaseHunter" + (af.active() ? "  [" + af.state() + "]  tps:" + af.teleports : ""), 4, y, 0xFFFFFFFF, true);
		y += 10;
		int shown = 0;
		for (BaseDetector.Base b : BaseDetector.INSTANCE.bases) {
			if (shown++ >= 5) break;
			g.text(mc.font, String.format("Base %d, %d, %d  (%d)", b.center.getX(), b.center.getY(), b.center.getZ(), b.score),
				4, y, 0xFFFF6060, true);
			y += 10;
		}
	}

	private static void setupCamera(Minecraft mc, GuiGraphicsExtractor g, float pt) {
		Player p = mc.player;
		eye = p.getEyePosition(pt);
		float yaw = Mth.rotLerp(pt, p.yRotO, p.getYRot());
		float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
		double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
		double cp = Math.cos(pr);
		fwd = new Vec3(-Math.sin(yr) * cp, -Math.sin(pr), Math.cos(yr) * cp);
		right = new Vec3(-Math.cos(yr), 0, -Math.sin(yr));
		up = right.cross(fwd);
		sw = g.guiWidth();
		sh = g.guiHeight();
		aspect = sw / (double) sh;
		tanHalfFov = Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);
	}

	/** Camera-space coordinates: x right, y up, z forward. */
	private static double[] toCam(Vec3 w) {
		Vec3 d = w.subtract(eye);
		return new double[]{d.dot(right), d.dot(up), d.dot(fwd)};
	}

	private static double[] toScreen(double[] c) {
		double nx = c[0] / (c[2] * tanHalfFov * aspect);
		double ny = c[1] / (c[2] * tanHalfFov);
		return new double[]{(nx * 0.5 + 0.5) * sw, (0.5 - ny * 0.5) * sh};
	}

	private static final int[][] EDGES = {
		{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}
	};

	/** Draws a 2D bounding rectangle of the projected box, plus an optional label and tracer. */
	private static void drawBox(GuiGraphicsExtractor g, AABB box, int rgb, String label, boolean tracer) {
		double[][] corners = new double[8][];
		for (int i = 0; i < 8; i++) {
			Vec3 w = new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
			corners[i] = toCam(w);
		}

		double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
		boolean any = false;
		for (int[] e : EDGES) {
			double[] a = corners[e[0]], b = corners[e[1]];
			if (a[2] < NEAR && b[2] < NEAR) continue;
			// Clip the edge against the near plane so boxes that straddle the camera still draw.
			if (a[2] < NEAR) a = lerpToNear(a, b);
			else if (b[2] < NEAR) b = lerpToNear(b, a);
			for (double[] c : new double[][]{a, b}) {
				double[] s = toScreen(c);
				minX = Math.min(minX, s[0]); maxX = Math.max(maxX, s[0]);
				minY = Math.min(minY, s[1]); maxY = Math.max(maxY, s[1]);
				any = true;
			}
		}
		if (!any) return;

		int x1 = clamp(minX), y1 = clamp(minY), x2 = clamp(maxX), y2 = clamp(maxY);
		int color = ARGB.color(255, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
		int fillColor = ARGB.color(36, (rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);

		g.fill(x1, y1, x2, y2, fillColor);
		g.fill(x1, y1, x2, y1 + 1, color);
		g.fill(x1, y2 - 1, x2, y2, color);
		g.fill(x1, y1, x1 + 1, y2, color);
		g.fill(x2 - 1, y1, x2, y2, color);

		if (label != null) {
			Minecraft mc = Minecraft.getInstance();
			int tx = (x1 + x2) / 2 - mc.font.width(label) / 2;
			g.text(mc.font, label, tx, Math.min(y2 + 3, sh - 10), color, true);
		}
		if (tracer) line(g, sw / 2, sh, (x1 + x2) / 2, (y1 + y2) / 2, color);
	}

	private static double[] lerpToNear(double[] behind, double[] front) {
		double t = (NEAR - behind[2]) / (front[2] - behind[2]);
		return new double[]{
			behind[0] + (front[0] - behind[0]) * t,
			behind[1] + (front[1] - behind[1]) * t,
			NEAR
		};
	}

	private static int clamp(double v) {
		return (int) Math.max(-4000, Math.min(4000, v));
	}

	/** Cheap line: a trail of 2x2 squares. Only used for a handful of tracers. */
	private static void line(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int color) {
		int steps = Math.min(Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1)) / 2, 700);
		if (steps <= 0) return;
		for (int i = 0; i <= steps; i++) {
			int x = x1 + (x2 - x1) * i / steps;
			int y = y1 + (y2 - y1) * i / steps;
			g.fill(x, y, x + 2, y + 2, color);
		}
	}
}
