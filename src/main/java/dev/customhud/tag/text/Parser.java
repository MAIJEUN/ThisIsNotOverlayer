package dev.customhud.tag.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds tags in a text exactly like Overlayer's parser:
 * {@code {Name}}, {@code {Name:a, b}} and {@code {Name(a, b)}} (literal, comma-separated arguments),
 * plus {@code {JSExpr:...}} / {@code {JSExpr(...)}} whose code may contain balanced braces.
 */
public final class Parser {
	private static final Pattern SIMPLE = Pattern.compile("\\{(?<name>[A-Za-z0-9_]+)\\}");
	private static final Pattern COLON = Pattern.compile("\\{(?<name>[A-Za-z0-9_]+):(?<arg>[^}]*)\\}");
	private static final Pattern FUNC = Pattern.compile("\\{(?<name>[A-Za-z0-9_]+)\\((?<args>[^}]*)\\)\\}");

	private record Range(int index, int length, String name, String argsRaw) {
	}

	private Parser() {
	}

	public static List<ParsedTag> parse(String input) {
		List<Range> js = findJsExprRanges(input);
		List<ParsedTag> tags = new ArrayList<>();
		collect(COLON, input, js, tags);
		collect(FUNC, input, js, tags);
		collect(SIMPLE, input, js, tags);
		for (Range r : js) {
			List<String> args = r.argsRaw().isBlank() ? List.of() : List.of(r.argsRaw());
			tags.add(new ParsedTag(input.substring(r.index(), r.index() + r.length()), r.name(), args, r.index(), r.length()));
		}
		tags.sort(Comparator.comparingInt(ParsedTag::index));
		return tags;
	}

	private static void collect(Pattern pattern, String input, List<Range> js, List<ParsedTag> out) {
		Matcher m = pattern.matcher(input);
		while (m.find()) {
			int start = m.start();
			int end = m.end();
			boolean insideJs = js.stream().anyMatch(r -> start >= r.index() && end <= r.index() + r.length());
			if (insideJs) {
				continue;
			}
			String argsRaw = groupOrNull(m, "arg");
			if (argsRaw == null) {
				argsRaw = groupOrNull(m, "args");
			}
			List<String> args = argsRaw == null || argsRaw.isBlank()
					? List.of()
					: Arrays.stream(argsRaw.split(",", -1)).map(String::strip).toList();
			out.add(new ParsedTag(m.group(), m.group("name"), args, start, end - start));
		}
	}

	private static String groupOrNull(Matcher m, String group) {
		try {
			return m.group(group);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static List<Range> findJsExprRanges(String input) {
		List<Range> ranges = new ArrayList<>();
		int i = 0;
		while (i < input.length()) {
			if (input.charAt(i) != '{') {
				i++;
				continue;
			}
			int nameStart = i + 1;
			int nameEnd = nameStart;
			while (nameEnd < input.length() && (Character.isLetterOrDigit(input.charAt(nameEnd)) || input.charAt(nameEnd) == '_')) {
				nameEnd++;
			}
			if (nameEnd == nameStart || nameEnd >= input.length()
					|| (input.charAt(nameEnd) != ':' && input.charAt(nameEnd) != '(')
					|| !input.substring(nameStart, nameEnd).equalsIgnoreCase("JSExpr")) {
				i++;
				continue;
			}
			int close = findBalancedClose(input, nameEnd + 1);
			if (close < 0) {
				i++;
				continue;
			}
			ranges.add(new Range(i, close - i + 1, input.substring(nameStart, nameEnd), input.substring(nameEnd + 1, close)));
			i = close + 1;
		}
		return ranges;
	}

	private static int findBalancedClose(String input, int start) {
		int depth = 1;
		int i = start;
		while (i < input.length()) {
			char c = input.charAt(i);
			if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '/') {
				while (i < input.length() && input.charAt(i) != '\n') {
					i++;
				}
				continue;
			}
			if (c == '/' && i + 1 < input.length() && input.charAt(i + 1) == '*') {
				i += 2;
				while (i + 1 < input.length() && !(input.charAt(i) == '*' && input.charAt(i + 1) == '/')) {
					i++;
				}
				i = Math.min(input.length(), i + 2);
				continue;
			}
			if (c == '\'' || c == '"' || c == '`') {
				i = skipJsString(input, i);
				continue;
			}
			if (c == '{') {
				depth++;
			} else if (c == '}') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
			i++;
		}
		return -1;
	}

	private static int skipJsString(String input, int start) {
		char quote = input.charAt(start);
		int i = start + 1;
		while (i < input.length()) {
			char c = input.charAt(i);
			if (c == '\\') {
				i += 2;
				continue;
			}
			if (c == quote) {
				return i + 1;
			}
			if (quote != '`' && c == '\n') {
				return i;
			}
			i++;
		}
		return input.length();
	}
}
