package dev.customhud.fx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A small NCalc-compatible evaluator, used like Overlayer uses NCalc: as the fallback for
 * numeric/boolean Fx values that aren't valid JavaScript. Supports NCalc operators
 * ({@code or and not = == != <> < <= > >= + - * / % ** & | ^ ~ << >> ?:}), single/double-quoted
 * strings, {@code true/false}, the parameters {@code PI} and {@code E} (also as {@code [PI]}) and
 * the built-in functions (case-insensitive): Abs, Acos, Asin, Atan, Atan2, Ceiling, Cos, Exp,
 * Floor, IEEERemainder, Ln, Log, Log10, Max, Min, Pow, Round, Sign, Sin, Sqrt, Tan, Truncate, in, if, ifs.
 */
public final class NCalc {
	private static final Map<String, Double> PARAMETERS = Map.of("PI", Math.PI, "E", Math.E);

	private final String src;
	private int pos;

	private NCalc(String src) {
		this.src = src;
	}

	/** Evaluates or throws IllegalArgumentException with a message. */
	public static Object evaluate(String expression) {
		NCalc p = new NCalc(expression);
		p.skipWs();
		if (p.pos >= p.src.length()) {
			throw new IllegalArgumentException("Empty expression");
		}
		Object v = p.ternary();
		p.skipWs();
		if (p.pos < p.src.length()) {
			throw new IllegalArgumentException("Unexpected '" + p.src.charAt(p.pos) + "' at " + p.pos);
		}
		return v;
	}

	// ---- grammar ----

	private Object ternary() {
		Object cond = or();
		if (accept("?")) {
			Object a = ternary();
			expect(":");
			Object b = ternary();
			return bool(cond) ? a : b;
		}
		return cond;
	}

	private Object or() {
		Object l = and();
		while (acceptWord("or") || accept("||")) {
			Object r = and();
			l = bool(l) || bool(r);
		}
		return l;
	}

	private Object and() {
		Object l = bitOr();
		while (acceptWord("and") || accept("&&")) {
			Object r = bitOr();
			l = bool(l) && bool(r);
		}
		return l;
	}

	private Object bitOr() {
		Object l = bitXor();
		while (!peek("||") && accept("|")) {
			l = (double) (lng(l) | lng(bitXor()));
		}
		return l;
	}

	private Object bitXor() {
		Object l = bitAnd();
		while (accept("^")) {
			l = (double) (lng(l) ^ lng(bitAnd()));
		}
		return l;
	}

	private Object bitAnd() {
		Object l = equality();
		while (!peek("&&") && accept("&")) {
			l = (double) (lng(l) & lng(equality()));
		}
		return l;
	}

	private Object equality() {
		Object l = relational();
		while (true) {
			if (accept("==") || (!peek("=>") && accept("="))) {
				l = equal(l, relational());
			} else if (accept("!=") || accept("<>")) {
				l = !equal(l, relational());
			} else {
				return l;
			}
		}
	}

	private Object relational() {
		Object l = shift();
		while (true) {
			if (accept("<=")) {
				l = num(l) <= num(shift());
			} else if (accept(">=")) {
				l = num(l) >= num(shift());
			} else if (!peek("<<") && !peek("<>") && accept("<")) {
				l = num(l) < num(shift());
			} else if (!peek(">>") && accept(">")) {
				l = num(l) > num(shift());
			} else {
				return l;
			}
		}
	}

	private Object shift() {
		Object l = additive();
		while (true) {
			if (accept("<<")) {
				l = (double) (lng(l) << lng(additive()));
			} else if (accept(">>")) {
				l = (double) (lng(l) >> lng(additive()));
			} else {
				return l;
			}
		}
	}

	private Object additive() {
		Object l = multiplicative();
		while (true) {
			if (accept("+")) {
				Object r = multiplicative();
				l = l instanceof String || r instanceof String ? str(l) + str(r) : num(l) + num(r);
			} else if (accept("-")) {
				l = num(l) - num(multiplicative());
			} else {
				return l;
			}
		}
	}

	private Object multiplicative() {
		Object l = unary();
		while (true) {
			if (!peek("**") && accept("*")) {
				l = num(l) * num(unary());
			} else if (accept("/")) {
				l = num(l) / num(unary());
			} else if (accept("%")) {
				l = num(l) % num(unary());
			} else {
				return l;
			}
		}
	}

	private Object unary() {
		if (acceptWord("not") || (!peek("!=") && accept("!"))) {
			return !bool(unary());
		}
		if (accept("-")) {
			return -num(unary());
		}
		if (accept("~")) {
			return (double) ~lng(unary());
		}
		return power();
	}

	private Object power() {
		Object base = primary();
		if (accept("**")) {
			return Math.pow(num(base), num(unary()));
		}
		return base;
	}

	private Object primary() {
		skipWs();
		if (pos >= src.length()) {
			throw new IllegalArgumentException("Unexpected end of expression");
		}
		char c = src.charAt(pos);
		if (c == '(') {
			pos++;
			Object v = ternary();
			expect(")");
			return v;
		}
		if (c == '\'' || c == '"') {
			int end = src.indexOf(c, pos + 1);
			if (end < 0) {
				throw new IllegalArgumentException("Unclosed string");
			}
			String s = src.substring(pos + 1, end);
			pos = end + 1;
			return s;
		}
		if (c == '[') {
			int end = src.indexOf(']', pos);
			if (end < 0) {
				throw new IllegalArgumentException("Unclosed parameter");
			}
			String name = src.substring(pos + 1, end);
			pos = end + 1;
			return parameter(name);
		}
		if (Character.isDigit(c) || c == '.') {
			int start = pos;
			while (pos < src.length() && (Character.isDigit(src.charAt(pos)) || src.charAt(pos) == '.')) {
				pos++;
			}
			if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
				int save = pos++;
				if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
					pos++;
				}
				if (pos < src.length() && Character.isDigit(src.charAt(pos))) {
					while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
						pos++;
					}
				} else {
					pos = save;
				}
			}
			try {
				return Double.parseDouble(src.substring(start, pos));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid number '" + src.substring(start, pos) + "'");
			}
		}
		if (Character.isLetter(c) || c == '_') {
			int start = pos;
			while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
				pos++;
			}
			String name = src.substring(start, pos);
			if (name.equalsIgnoreCase("true")) {
				return true;
			}
			if (name.equalsIgnoreCase("false")) {
				return false;
			}
			if (accept("(")) {
				List<Object> args = new ArrayList<>();
				if (!accept(")")) {
					do {
						args.add(lazy(name, args.size()) ? null : ternary());
					} while (accept(","));
					expect(")");
				}
				return function(name, args);
			}
			return parameter(name);
		}
		throw new IllegalArgumentException("Unexpected '" + c + "' at " + pos);
	}

	/** {@code if(...)} evaluates eagerly here (no side effects), so nothing is lazy. */
	private static boolean lazy(String name, int index) {
		return false;
	}

	private static Object parameter(String name) {
		Double v = PARAMETERS.get(name);
		if (v == null) {
			throw new IllegalArgumentException("Parameter '" + name + "' was not defined");
		}
		return v;
	}

	private static Object function(String name, List<Object> a) {
		String n = name.toLowerCase(Locale.ROOT);
		switch (n) {
			case "if" -> {
				arity(name, a, 3);
				return bool(a.get(0)) ? a.get(1) : a.get(2);
			}
			case "ifs" -> {
				if (a.size() < 3 || a.size() % 2 == 0) {
					throw new IllegalArgumentException("ifs() takes an odd number of arguments (at least 3)");
				}
				for (int i = 0; i + 1 < a.size(); i += 2) {
					if (bool(a.get(i))) {
						return a.get(i + 1);
					}
				}
				return a.getLast();
			}
			case "in" -> {
				if (a.isEmpty()) {
					throw new IllegalArgumentException("in() needs arguments");
				}
				for (int i = 1; i < a.size(); i++) {
					if (equal(a.getFirst(), a.get(i))) {
						return true;
					}
				}
				return false;
			}
			case "max" -> {
				arity(name, a, 2);
				return Math.max(num(a.get(0)), num(a.get(1)));
			}
			case "min" -> {
				arity(name, a, 2);
				return Math.min(num(a.get(0)), num(a.get(1)));
			}
			case "pow" -> {
				arity(name, a, 2);
				return Math.pow(num(a.get(0)), num(a.get(1)));
			}
			case "atan2" -> {
				arity(name, a, 2);
				return Math.atan2(num(a.get(0)), num(a.get(1)));
			}
			case "ieeeremainder" -> {
				arity(name, a, 2);
				return Math.IEEEremainder(num(a.get(0)), num(a.get(1)));
			}
			case "log" -> {
				arity(name, a, 2);
				return Math.log(num(a.get(0))) / Math.log(num(a.get(1)));
			}
			case "round" -> {
				if (a.size() != 1 && a.size() != 2) {
					throw new IllegalArgumentException("Round() takes 1 or 2 arguments");
				}
				double scale = Math.pow(10, a.size() == 2 ? (int) num(a.get(1)) : 0);
				return Math.rint(num(a.get(0)) * scale) / scale;
			}
			default -> {
			}
		}
		arity(name, a, 1);
		double x = num(a.getFirst());
		return switch (n) {
			case "abs" -> Math.abs(x);
			case "acos" -> Math.acos(x);
			case "asin" -> Math.asin(x);
			case "atan" -> Math.atan(x);
			case "ceiling" -> Math.ceil(x);
			case "cos" -> Math.cos(x);
			case "exp" -> Math.exp(x);
			case "floor" -> Math.floor(x);
			case "ln" -> Math.log(x);
			case "log10" -> Math.log10(x);
			case "sign" -> Math.signum(x);
			case "sin" -> Math.sin(x);
			case "sqrt" -> Math.sqrt(x);
			case "tan" -> Math.tan(x);
			case "truncate" -> x < 0 ? Math.ceil(x) : Math.floor(x);
			default -> throw new IllegalArgumentException("Function not found: " + name);
		};
	}

	private static void arity(String name, List<Object> a, int n) {
		if (a.size() != n) {
			throw new IllegalArgumentException(name + "() takes exactly " + n + " argument(s)");
		}
	}

	// ---- values ----

	private static double num(Object v) {
		if (v instanceof Double d) {
			return d;
		}
		if (v instanceof Boolean b) {
			return b ? 1 : 0;
		}
		if (v instanceof String s) {
			try {
				return Double.parseDouble(s.trim());
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("'" + s + "' is not a number");
			}
		}
		throw new IllegalArgumentException("Not a number");
	}

	private static long lng(Object v) {
		return (long) num(v);
	}

	private static boolean bool(Object v) {
		if (v instanceof Boolean b) {
			return b;
		}
		if (v instanceof Double d) {
			return d != 0;
		}
		if (v instanceof String s) {
			return Boolean.parseBoolean(s.trim());
		}
		return false;
	}

	private static String str(Object v) {
		return dev.customhud.tag.compile.DotNet.toString(v);
	}

	private static boolean equal(Object a, Object b) {
		if (a instanceof String || b instanceof String) {
			return str(a).equals(str(b));
		}
		if (a instanceof Boolean && b instanceof Boolean) {
			return a.equals(b);
		}
		return num(a) == num(b);
	}

	// ---- lexing helpers ----

	private void skipWs() {
		while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
			pos++;
		}
	}

	private boolean peek(String s) {
		skipWs();
		return src.startsWith(s, pos);
	}

	private boolean accept(String s) {
		if (peek(s)) {
			pos += s.length();
			return true;
		}
		return false;
	}

	private boolean acceptWord(String w) {
		skipWs();
		int end = pos + w.length();
		if (src.regionMatches(true, pos, w, 0, w.length()) && (end >= src.length() || !Character.isLetterOrDigit(src.charAt(end)))) {
			pos = end;
			return true;
		}
		return false;
	}

	private void expect(String s) {
		if (!accept(s)) {
			throw new IllegalArgumentException("Expected '" + s + "' at " + pos);
		}
	}
}
