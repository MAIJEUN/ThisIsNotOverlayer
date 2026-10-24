package dev.customhud.tag.impl;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * State that tags read but that is collected over time: frame timing (Overlayer's FpsTracker),
 * key presses per second (KpsTracker, exponential decay), Unity-style key/mouse down/up edges,
 * mouse movement/scroll, clicks per second, movement speed and session time.
 */
public final class Trackers {
	private static final double START = System.nanoTime() / 1e9;

	// ---- frames ----
	private static final int MAX_WINDOW_MS = 10_000;
	private static final Deque<Double> FRAME_TIMES = new ArrayDeque<>();
	private static final Map<Integer, double[]> FPS_HELD = new HashMap<>();
	private static final Map<Integer, double[]> MS_HELD = new HashMap<>();
	private static double lastFrame = Double.NaN;
	private static double deltaTime;
	private static long frameCount;

	// ---- input ----
	private static final Deque<Double> KEY_TIMES = new ArrayDeque<>();
	private static final Map<Integer, double[]> KPS_HELD = new HashMap<>();
	private static final Map<Integer, Long> KEY_PRESS_FRAME = new HashMap<>();
	private static final Map<Integer, Long> KEY_RELEASE_FRAME = new HashMap<>();
	private static final Map<Integer, Long> MOUSE_PRESS_FRAME = new HashMap<>();
	private static final Map<Integer, Long> MOUSE_RELEASE_FRAME = new HashMap<>();
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();
	private static double accDX;
	private static double accDY;
	private static double accScrollX;
	private static double accScrollY;
	private static double mouseDX;
	private static double mouseDY;
	private static double scrollX;
	private static double scrollY;
	private static long anyKeyDownFrame = -1;
	private static final java.util.Set<Integer> HELD_KEYS = new java.util.HashSet<>();
	private static final java.util.Set<Integer> HELD_MOUSE = new java.util.HashSet<>();

	// ---- world ----
	private static final int SPEED_SAMPLES = 5;
	private static final Deque<double[]> MOVES = new ArrayDeque<>();
	private static Vec3 lastPos;
	private static long joinedAt = -1;

	private Trackers() {
	}

	public static double now() {
		return System.nanoTime() / 1e9 - START;
	}

	// ---- per frame (Minecraft.runTick HEAD) ----

	public static void newFrame() {
		double t = now();
		deltaTime = Double.isNaN(lastFrame) ? 0 : t - lastFrame;
		lastFrame = t;
		frameCount++;
		FRAME_TIMES.addLast(t);
		while (FRAME_TIMES.size() > 4096) {
			FRAME_TIMES.removeFirst();
		}
		prune(FRAME_TIMES, MAX_WINDOW_MS);
		mouseDX = accDX;
		mouseDY = accDY;
		scrollX = accScrollX;
		scrollY = accScrollY;
		accDX = accDY = accScrollX = accScrollY = 0;
		prune(KEY_TIMES, MAX_WINDOW_MS);
	}

	public static long frameCount() {
		return frameCount;
	}

	public static double deltaTime() {
		return deltaTime;
	}

	private static void prune(Deque<Double> q, double windowMs) {
		double cutoff = now() - windowMs / 1000.0;
		while (!q.isEmpty() && q.peekFirst() < cutoff) {
			q.removeFirst();
		}
	}

	/** Overlayer's FpsTracker.Fps: 0 → this frame; otherwise the average over the window, refreshed once per window. */
	public static double fps(int windowMs) {
		if (windowMs <= 0) {
			return deltaTime > 0 ? 1.0 / deltaTime : 0;
		}
		return held(FPS_HELD, windowMs, () -> {
			int w = Math.min(Math.max(windowMs, 1), MAX_WINDOW_MS);
			Deque<Double> q = window(w);
			double span = q.size() < 2 ? 0 : q.peekLast() - q.peekFirst();
			return span < 1e-6 ? 0 : (q.size() - 1) / span;
		});
	}

	public static double frameTime(int windowMs) {
		if (windowMs <= 0) {
			return deltaTime * 1000.0;
		}
		return held(MS_HELD, windowMs, () -> {
			int w = Math.min(Math.max(windowMs, 1), MAX_WINDOW_MS);
			Deque<Double> q = window(w);
			double span = q.size() < 2 ? 0 : q.peekLast() - q.peekFirst();
			return span < 1e-6 ? 0 : span / (q.size() - 1) * 1000.0;
		});
	}

	private static Deque<Double> window(int windowMs) {
		double cutoff = now() - windowMs / 1000.0;
		Deque<Double> q = new ArrayDeque<>();
		for (double t : FRAME_TIMES) {
			if (t >= cutoff) {
				q.addLast(t);
			}
		}
		return q;
	}

	private static double held(Map<Integer, double[]> cache, int windowMs, java.util.function.DoubleSupplier compute) {
		int w = Math.min(Math.max(windowMs, 1), MAX_WINDOW_MS);
		double t = now();
		double[] e = cache.get(w);
		if (e == null || t >= e[1]) {
			if (cache.size() > 32) {
				cache.clear();
			}
			double v = compute.getAsDouble();
			cache.put(w, new double[]{v, t + w / 1000.0});
			return v;
		}
		return e[0];
	}

	/** Overlayer's KpsTracker.Rate: each press counts e^-age (τ = 1 s). */
	public static double kps(int windowMs) {
		if (windowMs <= 0) {
			return decaySum();
		}
		return held(KPS_HELD, windowMs, Trackers::decaySum);
	}

	private static double decaySum() {
		double t = now();
		double v = 0;
		for (double pressed : KEY_TIMES) {
			double age = t - pressed;
			if (age >= 0) {
				v += Math.exp(-age);
			}
		}
		return v;
	}

	// ---- input events (mixins) ----

	public static void onKey(int key, int action) {
		long next = frameCount + 1;
		if (action == InputConstants.PRESS) {
			HELD_KEYS.add(key);
			KEY_PRESS_FRAME.put(key, next);
			anyKeyDownFrame = next;
			// Like Overlayer's KpsTracker: every physical key press counts, menus or not.
			if (KEY_TIMES.size() >= 1024) {
				KEY_TIMES.removeFirst();
			}
			KEY_TIMES.addLast(now());
		} else if (action == InputConstants.RELEASE) {
			HELD_KEYS.remove(key);
			KEY_RELEASE_FRAME.put(key, next);
		}
	}

	public static void onMouseButton(int button, int action) {
		long next = frameCount + 1;
		if (action == InputConstants.PRESS) {
			HELD_MOUSE.add(button);
			MOUSE_PRESS_FRAME.put(button, next);
			anyKeyDownFrame = next;
			if (Minecraft.getInstance().gui.screen() == null) {
				long ms = System.currentTimeMillis();
				if (button == InputConstants.MOUSE_BUTTON_LEFT) {
					LEFT.addLast(ms);
				} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
					RIGHT.addLast(ms);
				}
			}
		} else if (action == InputConstants.RELEASE) {
			HELD_MOUSE.remove(button);
			MOUSE_RELEASE_FRAME.put(button, next);
		}
	}

	public static void onMouseMove(double dx, double dy) {
		accDX += dx;
		accDY += dy;
	}

	public static void onScroll(double x, double y) {
		accScrollX += x;
		accScrollY += y;
	}

	public static boolean keyHeld(int key) {
		return HELD_KEYS.contains(key);
	}

	public static boolean mouseHeld(int button) {
		return HELD_MOUSE.contains(button);
	}

	public static boolean anyHeld() {
		return !HELD_KEYS.isEmpty() || !HELD_MOUSE.isEmpty();
	}

	public static boolean keyPressedThisFrame(int key) {
		return KEY_PRESS_FRAME.getOrDefault(key, -1L) == frameCount;
	}

	public static boolean keyReleasedThisFrame(int key) {
		return KEY_RELEASE_FRAME.getOrDefault(key, -1L) == frameCount;
	}

	public static boolean mousePressedThisFrame(int button) {
		return MOUSE_PRESS_FRAME.getOrDefault(button, -1L) == frameCount;
	}

	public static boolean mouseReleasedThisFrame(int button) {
		return MOUSE_RELEASE_FRAME.getOrDefault(button, -1L) == frameCount;
	}

	public static boolean anyKeyDownThisFrame() {
		return anyKeyDownFrame == frameCount;
	}

	public static double mouseDX() {
		return mouseDX;
	}

	public static double mouseDY() {
		return mouseDY;
	}

	public static double scrollX() {
		return scrollX;
	}

	public static double scrollY() {
		return scrollY;
	}

	public static int leftCps() {
		return count(LEFT);
	}

	public static int rightCps() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> presses) {
		long cutoff = System.currentTimeMillis() - 1000;
		while (!presses.isEmpty() && presses.peekFirst() < cutoff) {
			presses.removeFirst();
		}
		return presses.size();
	}

	// ---- per tick ----

	public static void tick(Minecraft mc) {
		if (mc.player == null || mc.level == null) {
			lastPos = null;
			MOVES.clear();
			joinedAt = -1;
			return;
		}
		if (joinedAt < 0) {
			joinedAt = System.currentTimeMillis();
		}
		Vec3 pos = mc.player.position();
		if (lastPos != null) {
			Vec3 d = pos.subtract(lastPos);
			MOVES.addLast(new double[]{Math.sqrt(d.x * d.x + d.z * d.z), d.length()});
			while (MOVES.size() > SPEED_SAMPLES) {
				MOVES.removeFirst();
			}
		}
		lastPos = pos;
	}

	public static double speed(Minecraft mc, int index) {
		double perTick = MOVES.stream().mapToDouble(m -> m[index]).average().orElse(0);
		float tickrate = mc.level != null ? mc.level.tickRateManager().tickrate() : 20f;
		return perTick * tickrate;
	}

	public static long sessionMillis() {
		return joinedAt < 0 ? 0 : System.currentTimeMillis() - joinedAt;
	}
}
