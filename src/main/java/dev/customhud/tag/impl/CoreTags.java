package dev.customhud.tag.impl;

import dev.customhud.tag.compile.ArgConverter;
import dev.customhud.tag.compile.DotNet;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Overlayer's game-independent tags (Time, Effects, Kps, Performance), same names, parameters and output. */
public final class CoreTags {
	/** .NET DateTime ticks (100 ns) between 0001-01-01 and the Unix epoch. */
	private static final long EPOCH_TICKS = 621_355_968_000_000_000L;

	private CoreTags() {
	}

	public static List<TagCore> all() {
		List<TagCore> t = new ArrayList<>();
		time(t);
		effects(t);
		performance(t);
		return t;
	}

	// ---- Time ----

	private static long ticks(Instant i) {
		return EPOCH_TICKS + i.getEpochSecond() * 10_000_000L + i.getNano() / 100;
	}

	private static void time(List<TagCore> t) {
		t.add(Def.value("Ticks", Long.class, "Current system time in ticks (100-nanosecond intervals).", () -> {
			Instant now = Instant.now();
			int offset = java.time.ZoneId.systemDefault().getRules().getOffset(now).getTotalSeconds();
			return ticks(now) + offset * 10_000_000L;
		}));
		t.add(Def.value("UtcTicks", Long.class, "Current UTC time in ticks (100-nanosecond intervals).", () -> ticks(Instant.now())));
		t.add(Def.value("Millisecond", Integer.class, "Current millisecond (0-999).", () -> LocalDateTime.now().getNano() / 1_000_000));
		t.add(Def.value("Second", Integer.class, "Current second (0-59).", () -> LocalDateTime.now().getSecond()));
		t.add(Def.value("Minute", Integer.class, "Current minute (0-59).", () -> LocalDateTime.now().getMinute()));
		t.add(Def.value("Hour", Integer.class, "Current hour in 24-hour format (0-23).", () -> LocalDateTime.now().getHour()));
		t.add(Def.value("Hour12", Integer.class, "Current hour in 12-hour format (1-12).", () -> {
			int h = LocalDateTime.now().getHour() % 12;
			return h == 0 ? 12 : h;
		}));
		t.add(Def.value("AmPm", String.class, "Returns 'AM' or 'PM' based on the current time.", () -> LocalDateTime.now().getHour() < 12 ? "AM" : "PM"));
		t.add(Def.value("IsAm", Boolean.class, "Returns true if it is currently AM.", () -> LocalDateTime.now().getHour() < 12));
		t.add(Def.value("IsPm", Boolean.class, "Returns true if it is currently PM.", () -> LocalDateTime.now().getHour() >= 12));
		t.add(Def.value("Day", Integer.class, "Current day of the month (1-31).", () -> LocalDateTime.now().getDayOfMonth()));
		t.add(Def.value("DayOfYear", Integer.class, "Current day of the year (1-366).", () -> LocalDateTime.now().getDayOfYear()));
		t.add(Def.value("DayOfWeek", String.class, "Current day of the week (e.g., Sunday).",
				() -> LocalDateTime.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH)));
		t.add(Def.value("Month", Integer.class, "Current month (1-12).", () -> LocalDateTime.now().getMonthValue()));
		t.add(Def.value("Year", Integer.class, "Current year (4 digits).", () -> LocalDateTime.now().getYear()));
	}

	// ---- Effects ----

	private static final class AnimationState {
		double previous;
		double target;
		double startedAt;
	}

	private static final Map<String, AnimationState> EASED = new HashMap<>();
	private static final Map<String, AnimationState> MOVING = new HashMap<>();

	private static void effects(List<TagCore> t) {
		t.add(Def.of("EasedValue", Double.class).format()
				.param("tagName", String.class).opt("digits", Integer.class, -1).opt("speed", Double.class, 500d)
				.opt("ease", O5Ease.class, O5Ease.Linear)
				.desc("Smoothly follows a tag value with easing\nEx) {EasedValue:Combo} / {EasedValue:Combo,0,500,OutExpo}\nargs are comma-separated: tag, digits, milliseconds, ease")
				.fn(a -> easedValue((String) a[0], (int) a[1], (double) a[2], (O5Ease) a[3])));
		t.add(Def.of("ColorRange", String.class)
				.param("tagName", String.class).param("minimum", Double.class).param("maximum", Double.class)
				.param("minimumHex", String.class).param("maximumHex", String.class)
				.opt("ease", O5Ease.class, O5Ease.Linear).opt("maxLength", Integer.class, -1)
				.fn(a -> colorRange((String) a[0], (double) a[1], (double) a[2], (String) a[3], (String) a[4], (O5Ease) a[5], (int) a[6])));
		t.add(Def.of("MovingMan", Double.class)
				.param("tagName", String.class).param("startSize", Double.class).param("endSize", Double.class)
				.param("defaultSize", Double.class).param("speed", Double.class)
				.opt("invert", Boolean.class, false).opt("ease", O5Ease.class, O5Ease.OutExpo)
				.fn(a -> movingMan((String) a[0], (double) a[1], (double) a[2], (double) a[3], (double) a[4], (boolean) a[5], (O5Ease) a[6])));
	}

	public static double easedValue(String tagName, int digits, double speed, O5Ease ease) {
		Double value = readNumber(tagName);
		if (value == null) {
			return 0;
		}
		AnimationState state = state(EASED, tagName, value);
		double now = Trackers.now() * 1000d;
		double current = interpolate(state, now, speed, ease);
		if (value != state.target) {
			state.previous = current;
			state.target = value;
			state.startedAt = now;
			current = state.previous;
		}
		return digits < 0 ? current : roundEven(current, digits);
	}

	/** Math.Round(value, digits): banker's rounding. */
	private static double roundEven(double v, int digits) {
		return new java.math.BigDecimal(Double.toString(v)).setScale(digits, java.math.RoundingMode.HALF_EVEN).doubleValue();
	}

	public static String colorRange(String tagName, double min, double max, String minHex, String maxHex, O5Ease ease, int maxLength) {
		Float progress = colorRangeProgress(tagName, min, max, ease);
		float[] from = unityColor(minHex);
		float[] to = unityColor(maxHex);
		if (progress == null || from == null || to == null) {
			return "";
		}
		boolean alpha = hasAlpha(minHex) || hasAlpha(maxHex);
		float[] c = new float[4];
		for (int i = 0; i < 4; i++) {
			c[i] = from[i] + (to[i] - from[i]) * progress;
		}
		String result = toHtml(c, alpha);
		return maxLength < 0 || result.length() <= maxLength ? result : result.substring(0, maxLength);
	}

	public static Float colorRangeProgress(String tagName, double min, double max, O5Ease ease) {
		Double value = readNumber(tagName);
		if (value == null) {
			return null;
		}
		if (max <= min) {
			return 0f;
		}
		float p = (float) ((value - min) / (max - min));
		return ease.apply(Math.max(0f, Math.min(1f, p)));
	}

	public static double movingMan(String tagName, double start, double end, double def, double speed, boolean invert, O5Ease ease) {
		Double value = readNumber(tagName);
		if (value == null) {
			return def;
		}
		AnimationState state = state(MOVING, tagName, value);
		double now = Trackers.now() * 1000d;
		if (value != state.target) {
			state.target = value;
			state.startedAt = now;
		}
		if (speed <= 0 || now - state.startedAt >= speed) {
			return def;
		}
		float progress = (float) Math.max(0, Math.min(1, (now - state.startedAt) / speed));
		float eased = ease.apply(invert ? 1f - progress : progress);
		return start + (end - start) * eased;
	}

	private static AnimationState state(Map<String, AnimationState> states, String key, double value) {
		return states.computeIfAbsent(key, k -> {
			AnimationState s = new AnimationState();
			s.previous = value;
			s.target = value;
			s.startedAt = Trackers.now() * 1000d;
			return s;
		});
	}

	private static double interpolate(AnimationState s, double now, double speed, O5Ease ease) {
		if (speed <= 0) {
			return s.target;
		}
		float p = (float) Math.max(0, Math.min(1, (now - s.startedAt) / speed));
		return s.previous + (s.target - s.previous) * ease.apply(p);
	}

	/** A zero-argument tag's value as a number (Overlayer's TryReadNumber). */
	static Double readNumber(String tagName) {
		TagCore tag = TagManager.get(tagName);
		if (tag == null || tag.requiredCount() != 0) {
			return null;
		}
		try {
			Object result = tag.invoke();
			return result == null ? null : ArgConverter.parseDouble(DotNet.toString(result));
		} catch (RuntimeException e) {
			return null;
		}
	}

	// ---- Unity ColorUtility ----

	private static boolean hasAlpha(String hex) {
		String h = hex == null ? "" : hex.replace("#", "");
		return h.length() == 4 || h.length() == 8;
	}

	/** ColorUtility.TryParseHtmlString("#" + hex): RGB, RGBA, RRGGBB, RRGGBBAA → floats 0..1. */
	static float[] unityColor(String hex) {
		if (hex == null) {
			return null;
		}
		String h = hex.trim();
		while (h.startsWith("#")) {
			h = h.substring(1);
		}
		if (h.length() != 3 && h.length() != 4 && h.length() != 6 && h.length() != 8) {
			return null;
		}
		if (h.length() <= 4) {
			StringBuilder sb = new StringBuilder();
			for (char ch : h.toCharArray()) {
				sb.append(ch).append(ch);
			}
			h = sb.toString();
		}
		try {
			float r = Integer.parseInt(h.substring(0, 2), 16) / 255f;
			float g = Integer.parseInt(h.substring(2, 4), 16) / 255f;
			float b = Integer.parseInt(h.substring(4, 6), 16) / 255f;
			float a = h.length() == 8 ? Integer.parseInt(h.substring(6, 8), 16) / 255f : 1f;
			return new float[]{r, g, b, a};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/** ColorUtility.ToHtmlStringRGB / RGBA (via Color32 rounding). */
	static String toHtml(float[] c, boolean alpha) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < (alpha ? 4 : 3); i++) {
			int v = Math.round(Math.max(0f, Math.min(1f, c[i])) * 255f);
			sb.append(String.format("%02X", v));
		}
		return sb.toString();
	}

	// ---- Kps / Performance ----

	private static void performance(List<TagCore> t) {
		t.add(Def.of("Kps", Double.class).format().opt("windowMs", Integer.class, 0)
				.desc("Keyboard key presses (value refreshes every window)\nEx) {Kps} / {Kps:0.00} / {Kps:500,0.00}\nwindow: milliseconds (default 0 = every frame, max 10000)")
				.fn(a -> Trackers.kps((int) a[0])));
		t.add(Def.of("FrameTime", Double.class).format().opt("windowMs", Integer.class, 0)
				.desc("Average unscaled frame time in milliseconds (value refreshes every window)\nEx) {FrameTime} / {FrameTime:0.00} / {FrameTime:500,0.00}\nwindow: milliseconds (default 0 = every frame, max 10000)")
				.fn(a -> Trackers.frameTime((int) a[0])));
		t.add(Def.of("Fps", Double.class).format().opt("windowMs", Integer.class, 0)
				.desc("Average unscaled frames per second (value refreshes every window)\nEx) {Fps} / {Fps:0} / {Fps:500,0}\nwindow: milliseconds (default 0 = every frame, max 10000)")
				.fn(a -> Trackers.fps((int) a[0])));
		t.add(Def.value("ProcessorCount", Integer.class, "Total number of logical processor cores available on the system",
				() -> Runtime.getRuntime().availableProcessors()));
	}
}
