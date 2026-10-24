package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.fx.Fx;
import dev.customhud.js.JsEngine;
import net.minecraft.util.ARGB;

import java.util.Locale;

/**
 * An ARGB colour. Fx follows Overlayer's Color converter — a JS array or {@code [r, g, b, a]}
 * with components 0..1 (each may be an NCalc expression) — and additionally accepts a hex
 * string such as {@code "#FF5555"} or a {@code ColorRange} result.
 */
public class ColorProp extends Prop<Integer> {
	public ColorProp(String id, int defaultArgb) {
		this(id, defaultArgb, null);
	}

	public ColorProp(String id, int defaultArgb, String defaultFx) {
		super(id, defaultArgb, defaultFx);
	}

	public int argb() {
		return get();
	}

	public int channel(int index) {
		return switch (index) {
			case 0 -> ARGB.red(value);
			case 1 -> ARGB.green(value);
			case 2 -> ARGB.blue(value);
			default -> ARGB.alpha(value);
		};
	}

	public void setChannel(int index, int v) {
		v = Math.max(0, Math.min(255, v));
		int a = ARGB.alpha(value), r = ARGB.red(value), g = ARGB.green(value), b = ARGB.blue(value);
		switch (index) {
			case 0 -> r = v;
			case 1 -> g = v;
			case 2 -> b = v;
			default -> a = v;
		}
		value = ARGB.color(a, r, g, b);
		invalidate();
	}

	@Override
	protected Evaluated<Integer> evaluate(String expression) {
		Integer hex = hex(expression);
		if (hex != null) {
			return Evaluated.of(hex);
		}
		Fx.Result<float[]> r = Fx.components(expression, 3);
		if (r.error() != null || r.value() == null) {
			return Evaluated.failed(r.error());
		}
		float[] c = r.value();
		return Evaluated.of(fromFloats(c[0], c[1], c[2], c.length >= 4 ? c[3] : 1f));
	}

	/** A hex colour given directly ("#FF5555", 'FF5555') or returned by JS (e.g. ColorRange(...)). */
	static Integer hex(String expression) {
		String t = expression.trim();
		if (t.length() >= 2 && (t.startsWith("\"") && t.endsWith("\"") || t.startsWith("'") && t.endsWith("'"))) {
			t = t.substring(1, t.length() - 1);
		}
		Integer direct = parseHex(t);
		if (direct != null) {
			return direct;
		}
		if (t.startsWith("[")) {
			return null;
		}
		Object js = JsEngine.get().evaluate(JsEngine.wrapBlock(expression));
		return js instanceof String s ? parseHex(s) : null;
	}

	private static Integer parseHex(String t) {
		String s = t.trim();
		Integer named = Colors.named(s);
		if (named != null) {
			return named;
		}
		if (!s.startsWith("#")) {
			if (!s.matches("(?i)[0-9a-f]{6}|[0-9a-f]{8}")) {
				return null;
			}
			s = "#" + s;
		}
		return Colors.parse(s);
	}

	static int fromFloats(float r, float g, float b, float a) {
		return ARGB.color(to255(a), to255(r), to255(g), to255(b));
	}

	private static int to255(float v) {
		return Math.round(Math.max(0f, Math.min(1f, v)) * 255f);
	}

	static String floats(int argb) {
		return String.format(Locale.ROOT, "[%s, %s, %s, %s]", f(ARGB.red(argb)), f(ARGB.green(argb)), f(ARGB.blue(argb)), f(ARGB.alpha(argb)));
	}

	private static String f(int channel) {
		return dev.customhud.tag.compile.DotNet.toString(Math.round(channel / 255.0 * 1000) / 1000.0);
	}

	@Override
	protected String fixedAsExpression() {
		return "\"" + Colors.hex(value) + "\"";
	}

	@Override
	protected JsonElement write(Integer v) {
		return new JsonPrimitive(Colors.hex(v));
	}

	@Override
	protected Integer read(JsonElement json) {
		Integer c = Colors.parse(json.getAsString());
		return c == null ? defaultValue : c;
	}
}
