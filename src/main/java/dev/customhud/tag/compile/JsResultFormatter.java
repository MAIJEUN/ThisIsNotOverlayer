package dev.customhud.tag.compile;

/** Formats a dynamic (JavaScript) result: null/undefined → "", numbers honour the format. */
public final class JsResultFormatter {
	private JsResultFormatter() {
	}

	public static String format(Object value, String format) {
		if (value == null) {
			return "";
		}
		if (format == null || format.isEmpty() || !(value instanceof Number)) {
			return DotNet.toString(value);
		}
		try {
			return DotNet.format(value, format);
		} catch (RuntimeException e) {
			return DotNet.toString(value);
		}
	}
}
