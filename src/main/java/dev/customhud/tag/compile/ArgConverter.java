package dev.customhud.tag.compile;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Pattern;

/** Text argument → parameter type, with .NET invariant-culture parsing rules (Overlayer's ArgConverter). */
public final class ArgConverter {
	private static final Pattern INTEGER = Pattern.compile("^\\s*[+-]?\\d+\\s*$");
	private static final Pattern FLOAT = Pattern.compile("^\\s*[+-]?(\\d[\\d,]*\\.?\\d*|\\.\\d+)([eE][+-]?\\d+)?\\s*$");

	private ArgConverter() {
	}

	/** Throws IllegalArgumentException / NumberFormatException where .NET throws. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object convert(String value, Class<?> type) {
		if (type == String.class || type == Object.class) {
			return value;
		}
		if (type == Integer.class) {
			return Integer.parseInt(integer(value));
		}
		if (type == Long.class) {
			return Long.parseLong(integer(value));
		}
		if (type == Short.class) {
			return Short.parseShort(integer(value));
		}
		if (type == Byte.class) {
			int v = Integer.parseInt(integer(value));
			if (v < 0 || v > 255) {
				throw new ArithmeticException("Value was either too large or too small for an unsigned byte.");
			}
			return (byte) v;
		}
		if (type == Double.class) {
			return parseDouble(value);
		}
		if (type == Float.class) {
			return (float) parseDouble(value);
		}
		if (type == BigDecimal.class) {
			return new BigDecimal(floatText(value));
		}
		if (type == Boolean.class) {
			String t = value.trim();
			if (t.equalsIgnoreCase("true")) {
				return true;
			}
			if (t.equalsIgnoreCase("false")) {
				return false;
			}
			throw new IllegalArgumentException("String '" + value + "' was not recognized as a valid Boolean.");
		}
		if (type.isEnum()) {
			String t = value.trim();
			Object[] constants = type.getEnumConstants();
			for (Object c : constants) {
				if (((Enum) c).name().equalsIgnoreCase(t)) {
					return c;
				}
			}
			if (INTEGER.matcher(t).matches()) {
				int i = Integer.parseInt(integer(t));
				if (i >= 0 && i < constants.length) {
					return constants[i];
				}
			}
			throw new IllegalArgumentException("Requested value '" + value + "' was not found.");
		}
		throw new IllegalArgumentException("Cannot convert to " + type.getSimpleName());
	}

	private static String integer(String value) {
		if (!INTEGER.matcher(value).matches()) {
			throw new NumberFormatException("The input string '" + value + "' was not in a correct format.");
		}
		String t = value.trim();
		return t.startsWith("+") ? t.substring(1) : t;
	}

	public static double parseDouble(String value) {
		String t = value.trim();
		String lower = t.toLowerCase(Locale.ROOT);
		switch (lower) {
			case "nan", "-nan", "+nan" -> {
				return Double.NaN;
			}
			case "infinity", "+infinity", "∞", "+∞" -> {
				return Double.POSITIVE_INFINITY;
			}
			case "-infinity", "-∞" -> {
				return Double.NEGATIVE_INFINITY;
			}
			default -> {
			}
		}
		return Double.parseDouble(floatText(value));
	}

	private static String floatText(String value) {
		if (!FLOAT.matcher(value).matches()) {
			throw new NumberFormatException("The input string '" + value + "' was not in a correct format.");
		}
		return value.trim().replace(",", "");
	}

	/** True when {@code value} converts to {@code type} without throwing. */
	public static boolean canConvert(String value, Class<?> type) {
		try {
			convert(value, type);
			return true;
		} catch (RuntimeException e) {
			return false;
		}
	}
}
