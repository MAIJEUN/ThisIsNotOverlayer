package dev.customhud.tag.compile;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Checks a {@code {Tag:format}} against the tag's return type (Overlayer's FormatValidator). */
public final class FormatValidator {
	private static final LocalDateTime SAMPLE_DATE = LocalDateTime.of(2000, 1, 2, 3, 4, 5);

	private FormatValidator() {
	}

	/** @return null when valid, otherwise the reason */
	public static String validate(Class<?> type, String format) {
		if (format == null || format.isEmpty()) {
			return null;
		}
		try {
			if (type == LocalDateTime.class) {
				DotNet.format(SAMPLE_DATE, format);
			} else if (type == Float.class) {
				DotNet.format(1f, format);
			} else if (type == Double.class) {
				DotNet.format(1d, format);
			} else if (type == BigDecimal.class) {
				DotNet.format(BigDecimal.ONE, format);
			} else if (type == Integer.class) {
				DotNet.format(1, format);
			} else if (type == Long.class) {
				DotNet.format(1L, format);
			} else {
				return "Values of type " + typeName(type) + " cannot be formatted";
			}
			return null;
		} catch (RuntimeException e) {
			return e.getMessage();
		}
	}

	/** JS values are dynamic: accept a format that is valid for any common formattable type. */
	public static String validateJs(String format) {
		if (format == null || format.isEmpty()) {
			return null;
		}
		String last = null;
		for (Class<?> t : new Class<?>[]{Double.class, Long.class, BigDecimal.class, LocalDateTime.class}) {
			last = validate(t, format);
			if (last == null) {
				return null;
			}
		}
		return last;
	}

	public static String typeName(Class<?> type) {
		if (type == Integer.class) {
			return "int";
		}
		if (type == Long.class) {
			return "long";
		}
		if (type == Double.class) {
			return "double";
		}
		if (type == Float.class) {
			return "float";
		}
		if (type == Boolean.class) {
			return "bool";
		}
		if (type == String.class) {
			return "string";
		}
		if (type == Object.class) {
			return "object";
		}
		return type.getSimpleName();
	}
}
