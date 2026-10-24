package dev.customhud.tag.compile;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * .NET-compatible value → text conversion, so tags print exactly what Overlayer prints:
 * {@code ToString()} (shortest round-trip doubles, "True"/"False"), standard numeric
 * formats ({@code N0 F2 P1 E3 G4 D5 X4 C R}) and custom formats ({@code 0.## #,##0 0%;-0%;zero}).
 * Culture is ko-KR-like: '.' decimal, ',' group, "n%" percent, "₩" currency without decimals.
 */
public final class DotNet {
	private static final Pattern STANDARD = Pattern.compile("^([A-Za-z])(\\d{0,9})$");
	private static final String CURRENCY = "₩";
	private static final int CURRENCY_DIGITS = 0;
	private static final int NUMBER_DIGITS = 2;

	private DotNet() {
	}

	// ---- ToString() ----

	public static String toString(Object v) {
		if (v == null) {
			return "";
		}
		if (v instanceof String s) {
			return s;
		}
		if (v instanceof Boolean b) {
			return b ? "True" : "False";
		}
		if (v instanceof Double d) {
			return roundTrip(d, 15);
		}
		if (v instanceof Float f) {
			return roundTrip(f, 7);
		}
		if (v instanceof BigDecimal bd) {
			return bd.toPlainString();
		}
		if (v instanceof Number n) {
			return Long.toString(n.longValue());
		}
		if (v instanceof Enum<?> e) {
			return e.name();
		}
		if (v instanceof LocalDateTime t) {
			return formatDate(t, "G");
		}
		return v.toString();
	}

	/** Shortest round-trip text, switching to E notation outside [1e-5, 1e{sciAt}). */
	private static String roundTrip(double d, int sciAt) {
		if (Double.isNaN(d)) {
			return "NaN";
		}
		if (Double.isInfinite(d)) {
			return d > 0 ? "∞" : "-∞";
		}
		if (d == 0) {
			return (1 / d) < 0 ? "-0" : "0";
		}
		boolean neg = d < 0;
		String shortest = sciAt == 7 ? Float.toString((float) Math.abs(d)) : Double.toString(Math.abs(d));
		BigDecimal bd = new BigDecimal(shortest).stripTrailingZeros();
		String digits = bd.unscaledValue().toString();
		int exp = digits.length() - bd.scale() - 1;
		String body;
		if (exp < -4 || exp >= sciAt) {
			String mant = digits.length() > 1 ? digits.charAt(0) + "." + digits.substring(1) : digits;
			body = mant + "E" + (exp < 0 ? "-" : "+") + pad2(Math.abs(exp));
		} else {
			body = bd.toPlainString();
		}
		return neg ? "-" + body : body;
	}

	private static String pad2(int e) {
		return e < 10 ? "0" + e : Integer.toString(e);
	}

	// ---- ToString(format) ----

	public static boolean isFormattable(Class<?> type) {
		return type == Double.class || type == Float.class || type == Integer.class || type == Long.class
				|| type == BigDecimal.class || type == LocalDateTime.class;
	}

	/** {@code value.ToString(format)}; throws IllegalArgumentException where .NET throws FormatException. */
	public static String format(Object value, String format) {
		if (format == null || format.isEmpty()) {
			return toString(value);
		}
		if (value instanceof LocalDateTime t) {
			return formatDate(t, format);
		}
		if (!(value instanceof Number n)) {
			return toString(value);
		}
		boolean integral = value instanceof Integer || value instanceof Long || value instanceof Short || value instanceof Byte;
		Matcher m = STANDARD.matcher(format);
		if (m.matches()) {
			char spec = m.group(1).charAt(0);
			int precision = m.group(2).isEmpty() ? -1 : Integer.parseInt(m.group(2));
			return standard(n, integral, spec, precision);
		}
		return custom(n, integral, format);
	}

	private static BigDecimal exact(Number n) {
		if (n instanceof BigDecimal bd) {
			return bd;
		}
		if (n instanceof Double || n instanceof Float) {
			return new BigDecimal(n.doubleValue());
		}
		return BigDecimal.valueOf(n.longValue());
	}

	private static boolean negative(Number n) {
		if (n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			return d < 0 || (d == 0 && 1 / d < 0);
		}
		return exact(n).signum() < 0;
	}

	private static String special(Number n) {
		if (n instanceof Double || n instanceof Float) {
			double d = n.doubleValue();
			if (Double.isNaN(d)) {
				return "NaN";
			}
			if (Double.isInfinite(d)) {
				return d > 0 ? "∞" : "-∞";
			}
		}
		return null;
	}

	private static String standard(Number n, boolean integral, char spec, int precision) {
		String sp = special(n);
		char upper = Character.toUpperCase(spec);
		if (sp != null && "CEFGNPR".indexOf(upper) >= 0) {
			return sp;
		}
		boolean neg = negative(n);
		BigDecimal abs = exact(n).abs();
		switch (upper) {
			case 'F' -> {
				return sign(neg, fixed(abs, precision < 0 ? NUMBER_DIGITS : precision, false));
			}
			case 'N' -> {
				return sign(neg, fixed(abs, precision < 0 ? NUMBER_DIGITS : precision, true));
			}
			case 'P' -> {
				return sign(neg, fixed(abs.movePointRight(2), precision < 0 ? NUMBER_DIGITS : precision, true) + "%");
			}
			case 'C' -> {
				return (neg ? "-" : "") + CURRENCY + fixed(abs, precision < 0 ? CURRENCY_DIGITS : precision, true);
			}
			case 'E' -> {
				return sign(neg, scientific(abs, precision < 0 ? 6 : precision, spec == 'e' ? 'e' : 'E', 3, false));
			}
			case 'G' -> {
				if (precision <= 0) {
					return integral ? toString(n) : toString(n instanceof Float f ? (Object) f : (Object) n.doubleValue());
				}
				return sign(neg, general(abs, precision, spec == 'g' ? 'e' : 'E'));
			}
			case 'R' -> {
				if (integral) {
					throw new IllegalArgumentException("Format specifier was invalid.");
				}
				return toString(n instanceof Float f ? (Object) f : (Object) n.doubleValue());
			}
			case 'D' -> {
				if (!integral) {
					throw new IllegalArgumentException("Format specifier was invalid.");
				}
				String digits = abs.toBigInteger().toString();
				return sign(neg, padLeft(digits, Math.max(precision, 0)));
			}
			case 'X' -> {
				if (!integral) {
					throw new IllegalArgumentException("Format specifier was invalid.");
				}
				long v = n.longValue();
				String hex = n instanceof Long ? Long.toHexString(v) : Integer.toHexString((int) v);
				hex = spec == 'X' ? hex.toUpperCase(Locale.ROOT) : hex;
				return padLeft(hex, Math.max(precision, 0));
			}
			case 'B' -> {
				if (!integral) {
					throw new IllegalArgumentException("Format specifier was invalid.");
				}
				long v = n.longValue();
				String bin = n instanceof Long ? Long.toBinaryString(v) : Integer.toBinaryString((int) v);
				return padLeft(bin, Math.max(precision, 0));
			}
			default -> throw new IllegalArgumentException("Format specifier '" + spec + "' was invalid.");
		}
	}

	private static String sign(boolean neg, String body) {
		return neg ? "-" + body : body;
	}

	private static String padLeft(String s, int width) {
		return s.length() >= width ? s : "0".repeat(width - s.length()) + s;
	}

	private static String fixed(BigDecimal abs, int decimals, boolean group) {
		BigDecimal r = abs.setScale(decimals, RoundingMode.HALF_UP);
		String plain = r.toPlainString();
		if (!group) {
			return plain;
		}
		int dot = plain.indexOf('.');
		String intPart = dot < 0 ? plain : plain.substring(0, dot);
		String frac = dot < 0 ? "" : plain.substring(dot);
		return groupDigits(intPart) + frac;
	}

	private static String groupDigits(String digits) {
		StringBuilder sb = new StringBuilder();
		int len = digits.length();
		for (int i = 0; i < len; i++) {
			if (i > 0 && (len - i) % 3 == 0) {
				sb.append(',');
			}
			sb.append(digits.charAt(i));
		}
		return sb.toString();
	}

	/** d.ddddE+ddd with {@code decimals} digits after the point. */
	private static String scientific(BigDecimal abs, int decimals, char e, int minExpDigits, boolean trim) {
		if (abs.signum() == 0) {
			String mant = decimals > 0 && !trim ? "0." + "0".repeat(decimals) : "0";
			return mant + e + "+" + padLeft("0", minExpDigits);
		}
		BigDecimal rounded = abs.round(new MathContext(decimals + 1, RoundingMode.HALF_UP));
		String digits = rounded.unscaledValue().toString();
		int exp = digits.length() - rounded.scale() - 1;
		digits = digits.length() < decimals + 1 ? digits + "0".repeat(decimals + 1 - digits.length()) : digits.substring(0, decimals + 1);
		String frac = digits.substring(1);
		if (trim) {
			frac = frac.replaceAll("0+$", "");
		}
		String mant = frac.isEmpty() ? digits.substring(0, 1) : digits.charAt(0) + "." + frac;
		return mant + e + (exp < 0 ? "-" : "+") + padLeft(Integer.toString(Math.abs(exp)), minExpDigits);
	}

	private static String general(BigDecimal abs, int precision, char e) {
		if (abs.signum() == 0) {
			return "0";
		}
		BigDecimal rounded = abs.round(new MathContext(precision, RoundingMode.HALF_UP));
		String digits = rounded.unscaledValue().toString();
		int exp = digits.length() - rounded.scale() - 1;
		if (exp > -5 && exp < precision) {
			return rounded.stripTrailingZeros().toPlainString();
		}
		return scientific(abs, precision - 1, e, 2, true);
	}

	// ---- custom numeric formats ----

	private static String custom(Number n, boolean integral, String format) {
		String sp = special(n);
		if (sp != null) {
			return sp;
		}
		List<String> sections = splitSections(format);
		BigDecimal value = exact(n);
		if (!integral) {
			// .NET rounds doubles to 15 (floats to 7) significant digits before custom formatting.
			value = value.signum() == 0 ? value : value.round(new MathContext(n instanceof Float ? 7 : 15, RoundingMode.HALF_EVEN));
		}
		boolean neg = negative(n);
		String section = sections.getFirst();
		boolean explicitNegative = false;
		if (neg && sections.size() >= 2 && !sections.get(1).isEmpty()) {
			section = sections.get(1);
			explicitNegative = true;
		}
		BigDecimal abs = value.abs();
		String result = applySection(abs, section);
		if (sections.size() >= 3 && isZeroResult(abs, sections.getFirst()) && !sections.get(2).isEmpty()) {
			return applySection(abs, sections.get(2));
		}
		if (neg && !explicitNegative && containsNonZeroDigit(result)) {
			return "-" + result;
		}
		return result;
	}

	private static boolean containsNonZeroDigit(String s) {
		for (char c : s.toCharArray()) {
			if (c >= '1' && c <= '9') {
				return true;
			}
		}
		return false;
	}

	private static boolean isZeroResult(BigDecimal abs, String section) {
		return !containsNonZeroDigit(applySection(abs, section));
	}

	private static List<String> splitSections(String format) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		char quote = 0;
		for (int i = 0; i < format.length(); i++) {
			char c = format.charAt(i);
			if (quote != 0) {
				cur.append(c);
				if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '\\' && i + 1 < format.length()) {
				cur.append(c).append(format.charAt(++i));
			} else if (c == '\'' || c == '"') {
				quote = c;
				cur.append(c);
			} else if (c == ';') {
				out.add(cur.toString());
				cur.setLength(0);
			} else {
				cur.append(c);
			}
		}
		out.add(cur.toString());
		return out;
	}

	/** Formats a non-negative value with one section (no ';'). */
	private static String applySection(BigDecimal abs, String f) {
		// Pass 1: analyse the pattern.
		int intPlaceholders = 0;
		int intZeros = -1;
		int fracZeros = 0;
		int fracPlaceholders = 0;
		boolean pointSeen = false;
		boolean grouping = false;
		int scaleCommas = 0;
		boolean percent = false;
		boolean permille = false;
		int expPos = -1;
		int expDigits = 0;
		boolean expPlus = false;
		char expChar = 'E';
		int pendingCommas = 0;
		boolean digitSeen = false;
		char quote = 0;
		for (int i = 0; i < f.length(); i++) {
			char c = f.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				}
				continue;
			}
			switch (c) {
				case '\\' -> i++;
				case '\'', '"' -> quote = c;
				case '0', '#' -> {
					if (pendingCommas > 0 && digitSeen && !pointSeen) {
						grouping = true;
					}
					pendingCommas = 0;
					digitSeen = true;
					if (!pointSeen) {
						intPlaceholders++;
						if (c == '0' && intZeros < 0) {
							intZeros = intPlaceholders;
						}
					} else {
						fracPlaceholders++;
						if (c == '0') {
							fracZeros = fracPlaceholders;
						}
					}
				}
				case '.' -> {
					if (!pointSeen) {
						scaleCommas += pendingCommas;
						pendingCommas = 0;
						pointSeen = true;
					}
				}
				case ',' -> {
					if (!pointSeen && digitSeen) {
						pendingCommas++;
					}
				}
				case '%' -> percent = true;
				case '‰' -> permille = true;
				case 'E', 'e' -> {
					int j = i + 1;
					boolean plus = false;
					if (j < f.length() && (f.charAt(j) == '+' || f.charAt(j) == '-')) {
						plus = f.charAt(j) == '+';
						j++;
					}
					int k = j;
					while (k < f.length() && f.charAt(k) == '0') {
						k++;
					}
					if (k > j && expPos < 0) {
						expPos = i;
						expDigits = k - j;
						expPlus = plus;
						expChar = c;
						i = k - 1;
					}
				}
				default -> {
				}
			}
		}
		if (!pointSeen) {
			scaleCommas += pendingCommas;
		}
		int minInt = intZeros < 0 ? 0 : intPlaceholders - intZeros + 1;

		BigDecimal v = abs;
		if (percent) {
			v = v.movePointRight(2);
		}
		if (permille) {
			v = v.movePointRight(3);
		}
		if (scaleCommas > 0 && expPos < 0) {
			v = v.movePointLeft(3 * scaleCommas);
		}

		String intDigits;
		String fracDigits;
		String expText = "";
		if (expPos >= 0) {
			int exponent = 0;
			if (v.signum() != 0) {
				int lead = Math.max(1, intPlaceholders);
				exponent = v.precision() - v.scale() - lead;
				v = v.movePointLeft(exponent);
			}
			v = v.setScale(fracPlaceholders, RoundingMode.HALF_UP);
			if (v.signum() != 0 && v.precision() - v.scale() > Math.max(1, intPlaceholders)) {
				v = v.movePointLeft(1).setScale(fracPlaceholders, RoundingMode.HALF_UP);
				exponent++;
			}
			String plain = v.toPlainString();
			int dot = plain.indexOf('.');
			intDigits = dot < 0 ? plain : plain.substring(0, dot);
			fracDigits = dot < 0 ? "" : plain.substring(dot + 1);
			String ed = padLeft(Integer.toString(Math.abs(exponent)), expDigits);
			expText = expChar + (exponent < 0 ? "-" : expPlus ? "+" : "") + ed;
		} else {
			v = v.setScale(fracPlaceholders, RoundingMode.HALF_UP);
			String plain = v.toPlainString();
			int dot = plain.indexOf('.');
			intDigits = dot < 0 ? plain : plain.substring(0, dot);
			fracDigits = dot < 0 ? "" : plain.substring(dot + 1);
		}
		if (intDigits.equals("0")) {
			intDigits = "";
		}
		if (intDigits.length() < minInt) {
			intDigits = "0".repeat(minInt - intDigits.length()) + intDigits;
		}
		// Trim optional ('#') trailing fraction digits.
		int keep = fracDigits.length();
		while (keep > fracZeros && fracDigits.charAt(keep - 1) == '0') {
			keep--;
		}
		fracDigits = fracDigits.substring(0, keep);
		if (grouping && !intDigits.isEmpty()) {
			intDigits = groupDigits(intDigits);
		}

		// Pass 2: emit.
		StringBuilder out = new StringBuilder();
		int intSlot = 0;
		int fracIndex = 0;
		boolean intEmitted = false;
		pointSeen = false;
		quote = 0;
		for (int i = 0; i < f.length(); i++) {
			char c = f.charAt(i);
			if (quote != 0) {
				if (c == quote) {
					quote = 0;
				} else {
					out.append(c);
				}
				continue;
			}
			if (i == expPos) {
				out.append(expText);
				int j = i + 1;
				if (j < f.length() && (f.charAt(j) == '+' || f.charAt(j) == '-')) {
					j++;
				}
				while (j < f.length() && f.charAt(j) == '0') {
					j++;
				}
				i = j - 1;
				continue;
			}
			switch (c) {
				case '\\' -> {
					if (i + 1 < f.length()) {
						out.append(f.charAt(++i));
					}
				}
				case '\'', '"' -> quote = c;
				case '0', '#' -> {
					if (!pointSeen) {
						intSlot++;
						if (!intEmitted) {
							// The first integer placeholder takes every leading digit.
							int remainingSlots = intPlaceholders - intSlot;
							String digitsOnly = intDigits.replace(",", "");
							int take = Math.max(0, digitsOnly.length() - remainingSlots);
							out.append(takeGrouped(intDigits, take));
							intDigits = intDigits.substring(groupedLength(intDigits, take));
							intEmitted = true;
						} else if (!intDigits.isEmpty()) {
							int len = groupedLength(intDigits, 1);
							out.append(intDigits, 0, len);
							intDigits = intDigits.substring(len);
						}
					} else if (fracIndex < fracDigits.length()) {
						out.append(fracDigits.charAt(fracIndex++));
					}
				}
				case '.' -> {
					if (!pointSeen) {
						pointSeen = true;
						if (!fracDigits.isEmpty()) {
							out.append('.');
						}
					}
				}
				case ',' -> {
				}
				default -> out.append(c);
			}
		}
		return out.toString();
	}

	/** Takes {@code count} digits from the front of a grouped string, keeping their separators. */
	private static String takeGrouped(String grouped, int count) {
		return grouped.substring(0, groupedLength(grouped, count));
	}

	private static int groupedLength(String grouped, int digits) {
		int seen = 0;
		int i = 0;
		while (i < grouped.length() && seen < digits) {
			if (grouped.charAt(i) != ',') {
				seen++;
			}
			i++;
		}
		// Include a separator right after the taken digits so it stays with the left part.
		while (i < grouped.length() && grouped.charAt(i) == ',' && seen > 0 && i < grouped.length() - 1) {
			i++;
			break;
		}
		return i;
	}

	// ---- dates ----

	public static String formatDate(LocalDateTime t, String format) {
		if (format.length() == 1) {
			String pattern = switch (format.charAt(0)) {
				case 'd' -> "yyyy-MM-dd";
				case 'D' -> "yyyy'년' M'월' d'일' EEEE";
				case 'f' -> "yyyy'년' M'월' d'일' EEEE a h:mm";
				case 'F' -> "yyyy'년' M'월' d'일' EEEE a h:mm:ss";
				case 'g' -> "yyyy-MM-dd a h:mm";
				case 'G' -> "yyyy-MM-dd a h:mm:ss";
				case 'M', 'm' -> "M'월' d'일'";
				case 'O', 'o' -> "yyyy-MM-dd'T'HH:mm:ss.SSSSSSS";
				case 'R', 'r' -> "EEE, dd MMM yyyy HH:mm:ss 'GMT'";
				case 's' -> "yyyy-MM-dd'T'HH:mm:ss";
				case 't' -> "a h:mm";
				case 'T' -> "a h:mm:ss";
				case 'u' -> "yyyy-MM-dd HH:mm:ss'Z'";
				case 'U' -> "yyyy'년' M'월' d'일' EEEE a h:mm:ss";
				case 'Y', 'y' -> "yyyy'년' M'월'";
				default -> throw new IllegalArgumentException("Input string was not in a correct format.");
			};
			return DateTimeFormatter.ofPattern(pattern, Locale.KOREA).format(t);
		}
		return DateTimeFormatter.ofPattern(toJavaDatePattern(format), Locale.KOREA).format(t);
	}

	/** Translates a .NET custom date pattern to java.time. */
	static String toJavaDatePattern(String f) {
		StringBuilder out = new StringBuilder();
		int i = 0;
		while (i < f.length()) {
			char c = f.charAt(i);
			int run = 1;
			while (i + run < f.length() && f.charAt(i + run) == c) {
				run++;
			}
			switch (c) {
				case 'y' -> out.append(run == 2 ? "yy" : "y".repeat(Math.max(run, 4) == 4 && run < 4 ? 4 : run));
				case 'M' -> out.append("M".repeat(run));
				case 'd' -> out.append(run >= 4 ? "EEEE" : run == 3 ? "EEE" : "d".repeat(run));
				case 'H', 'h', 'm', 's' -> out.append(String.valueOf(c).repeat(Math.min(run, 2)));
				case 'f', 'F' -> out.append("S".repeat(Math.min(run, 9)));
				case 't' -> out.append("a");
				case 'z' -> out.append(run >= 3 ? "xxx" : "x");
				case 'K' -> out.append("XXX");
				case '\\' -> {
					if (i + 1 < f.length()) {
						out.append('\'').append(f.charAt(i + 1)).append('\'');
					}
					run = 2;
				}
				case '\'', '"' -> {
					int end = f.indexOf(c, i + 1);
					String lit = end < 0 ? f.substring(i + 1) : f.substring(i + 1, end);
					out.append('\'').append(lit.replace("'", "''")).append('\'');
					run = (end < 0 ? f.length() : end + 1) - i;
				}
				default -> {
					if (Character.isLetter(c)) {
						out.append('\'').append(String.valueOf(c).repeat(run)).append('\'');
					} else {
						out.append(String.valueOf(c).repeat(run));
					}
				}
			}
			i += run;
		}
		return out.toString();
	}

	// ---- Convert.ChangeType ----

	/** Convert.ChangeType for numbers/booleans/strings with .NET rules (banker's rounding to integers). */
	public static Object changeType(Object value, Class<?> target) {
		if (value == null) {
			throw new IllegalArgumentException("Null object cannot be converted to a value type.");
		}
		if (target == Object.class || target.isInstance(value)) {
			return value;
		}
		if (target == String.class) {
			return toString(value);
		}
		if (value instanceof String s) {
			return ArgConverter.convert(s, target);
		}
		double d;
		if (value instanceof Boolean b) {
			d = b ? 1 : 0;
		} else if (value instanceof Number n) {
			d = n.doubleValue();
		} else {
			throw new IllegalArgumentException("Invalid cast from '" + value.getClass().getSimpleName() + "' to '" + target.getSimpleName() + "'.");
		}
		if (target == Double.class) {
			return d;
		}
		if (target == Float.class) {
			return (float) d;
		}
		if (target == Boolean.class) {
			return d != 0;
		}
		if (target == BigDecimal.class) {
			return BigDecimal.valueOf(d);
		}
		if (target == Integer.class || target == Long.class || target == Short.class || target == Byte.class) {
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw new ArithmeticException("Value was either too large or too small.");
			}
			double r = Math.rint(d);
			if (target == Integer.class) {
				if (r < Integer.MIN_VALUE || r > Integer.MAX_VALUE) {
					throw new ArithmeticException("Value was either too large or too small for an Int32.");
				}
				return (int) r;
			}
			if (target == Long.class) {
				return BigDecimal.valueOf(r).toBigInteger().compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0 ? Long.MAX_VALUE : (long) r;
			}
			if (target == Short.class) {
				return (short) r;
			}
			return (byte) r;
		}
		throw new IllegalArgumentException("Invalid cast to '" + target.getSimpleName() + "'.");
	}
}
