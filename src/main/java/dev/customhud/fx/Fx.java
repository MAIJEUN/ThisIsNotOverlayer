package dev.customhud.fx;

import dev.customhud.js.JsEngine;
import dev.customhud.tag.compile.DotNet;
import dev.customhud.tag.text.TextEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Overlayer's FxValue.Evaluate for each value type. The expression text is used raw
 * (tags are reached from JS as {@code Fps()} / {@code Tag.Fps()}):
 * <ul>
 *   <li>text: JavaScript first; if that gives nothing, the text is rendered as a tag template</li>
 *   <li>numbers / booleans: JavaScript first, then NCalc</li>
 *   <li>enums: the name (or index) as plain text</li>
 *   <li>colours / vectors: a JS array or {@code [a, b, c]} whose parts are numbers or NCalc expressions</li>
 * </ul>
 * Each call reports why it fell back to the fixed value through {@link Result#error}.
 */
public final class Fx {
	public record Result<T>(T value, String error) {
		static <T> Result<T> ok(T v) {
			return new Result<>(v, null);
		}

		static <T> Result<T> fail(String error) {
			return new Result<>(null, error);
		}
	}

	private Fx() {
	}

	private static String jsError(String wrapped) {
		JsEngine js = JsEngine.get();
		String e = js.compileError(wrapped);
		return e != null ? e : js.runtimeError(wrapped);
	}

	/** Text: JS, else the rendered template ({@code engine} is reused between frames). */
	public static String string(String expression, TextEngine engine) {
		String wrapped = JsEngine.wrapBlock(expression);
		Object js = JsEngine.get().evaluate(wrapped);
		if (js != null) {
			String text = DotNet.toString(js);
			if (!text.isEmpty()) {
				return text;
			}
		}
		engine.setText(expression);
		return engine.get();
	}

	public static Result<Double> number(String expression) {
		Result<Object> r = scalar(expression, Double.class);
		return r.error() != null ? Result.fail(r.error()) : Result.ok((Double) r.value());
	}

	public static Result<Boolean> bool(String expression) {
		Result<Object> r = scalar(expression, Boolean.class);
		return r.error() != null ? Result.fail(r.error()) : Result.ok((Boolean) r.value());
	}

	private static Result<Object> scalar(String expression, Class<?> type) {
		String code = JsEngine.sanitize(expression);
		if (code == null || code.isEmpty()) {
			return Result.fail(null);
		}
		String wrapped = JsEngine.wrapBlock(code);
		Object js = JsEngine.get().evaluate(wrapped);
		if (js != null) {
			try {
				return Result.ok(DotNet.changeType(js, type));
			} catch (RuntimeException ignored) {
				// fall through to NCalc
			}
		}
		try {
			Object v = NCalc.evaluate(code);
			return Result.ok(DotNet.changeType(v, type));
		} catch (RuntimeException e) {
			String jsErr = jsError(wrapped);
			return Result.fail(jsErr != null ? jsErr : e.getMessage());
		}
	}

	/** Enum: index or name (case-insensitive); anything else gives the first constant, like Overlayer. */
	public static <E extends Enum<E>> E enumValue(String expression, Class<E> type) {
		E[] values = type.getEnumConstants();
		String raw = expression == null ? "" : expression.trim();
		if (raw.isEmpty()) {
			return values[0];
		}
		try {
			int i = Integer.parseInt(raw);
			return i >= 0 && i < values.length ? values[i] : values[0];
		} catch (NumberFormatException ignored) {
			// not an index
		}
		String key = raw.replace(" ", "").replace("_", "").toLowerCase(Locale.ROOT);
		for (E e : values) {
			if (e.name().replace("_", "").toLowerCase(Locale.ROOT).equals(key)) {
				return e;
			}
		}
		return values[0];
	}

	/**
	 * Numeric components: a JS array, else "[a, b, ...]" / "a, b; c" whose parts are numbers or NCalc.
	 * {@code minCount} components are required.
	 */
	public static Result<float[]> components(String expression, int minCount) {
		String text = expression == null ? "" : expression.trim();
		if (text.isEmpty()) {
			return Result.fail("Empty expression.");
		}
		String wrapped = JsEngine.wrapBlock(text);
		Object js = JsEngine.get().evaluate(wrapped);
		if (js instanceof List<?> list && !list.isEmpty() && list.size() <= 64) {
			float[] arr = new float[list.size()];
			try {
				for (int i = 0; i < arr.length; i++) {
					arr[i] = ((Number) DotNet.changeType(list.get(i), Float.class)).floatValue();
				}
				if (arr.length >= minCount) {
					return Result.ok(arr);
				}
			} catch (RuntimeException ignored) {
				// fall through
			}
		}
		try {
			List<String> parts = splitComponents(text);
			if (parts.size() < minCount) {
				String jsErr = jsError(wrapped);
				return Result.fail(jsErr != null ? jsErr : "Expected at least " + minCount + " components.");
			}
			float[] arr = new float[parts.size()];
			for (int i = 0; i < arr.length; i++) {
				arr[i] = numericComponent(parts.get(i));
			}
			return Result.ok(arr);
		} catch (RuntimeException e) {
			String jsErr = jsError(wrapped);
			return Result.fail(jsErr != null ? jsErr : e.getMessage());
		}
	}

	private static float numericComponent(String expr) {
		String t = JsEngine.sanitize(expr).trim();
		try {
			return Float.parseFloat(t);
		} catch (NumberFormatException ignored) {
			return ((Number) DotNet.changeType(NCalc.evaluate(t), Float.class)).floatValue();
		}
	}

	private static List<String> splitComponents(String input) {
		String text = input.trim();
		if (text.startsWith("[") && text.endsWith("]")) {
			text = text.substring(1, text.length() - 1);
		}
		List<String> parts = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(' || c == '[') {
				depth++;
			} else if (c == ')' || c == ']') {
				depth--;
			} else if ((c == ',' || c == ';') && depth == 0) {
				parts.add(text.substring(start, i));
				start = i + 1;
			}
		}
		parts.add(text.substring(start));
		return parts.stream().map(String::trim).filter(p -> !p.isEmpty()).toList();
	}
}
