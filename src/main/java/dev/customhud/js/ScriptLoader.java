package dev.customhud.js;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Loads every {@code *.js} under {@code config/customhud/Script} (sub-folders too; dot-folders and
 * node_modules skipped). Scripts call {@code RegisterTag(name, func, { Type, ReturnType, Desc })}
 * to add tags usable in text as {@code {Name:args}}. Also writes impl.js / impl.d.ts for editor autocomplete.
 */
public final class ScriptLoader {
	public record ScriptDiagnostic(String file, String message) {
	}

	private static final Logger LOG = LoggerFactory.getLogger("customhud/js");
	private static final Pattern IMPL_IMPORT = Pattern.compile(
			"^[\\t ]*import[\\t ]+[\"']\\.\\.?/impl(?:\\.js)?[\"'][\\t ]*;?[\\t ]*(?://[^\\r\\n]*)?(?:\\r?\\n|$)", Pattern.MULTILINE);

	private final JsEngine engine;
	private final Path folder;
	private final List<ScriptDiagnostic> diagnostics = new ArrayList<>();
	private final Map<String, List<String>> fileTags = new LinkedHashMap<>();
	private String currentFile;

	ScriptLoader(JsEngine engine, Path folder) {
		this.engine = engine;
		this.folder = folder;
	}

	public Path folder() {
		return folder;
	}

	public List<ScriptDiagnostic> diagnostics() {
		return List.copyOf(diagnostics);
	}

	public Map<String, List<String>> fileTags() {
		return Map.copyOf(fileTags);
	}

	/** Unregisters script tags, resets the JS scope and loads every script again. */
	public synchronized void reloadAll() {
		List<String> old = fileTags.values().stream().flatMap(List::stream).toList();
		TagManager.unregister(old);
		fileTags.clear();
		diagnostics.clear();
		engine.reset();
		if (folder == null) {
			return;
		}
		try {
			Files.createDirectories(folder);
		} catch (IOException e) {
			diagnostics.add(new ScriptDiagnostic(folder.toString(), e.getMessage()));
			return;
		}
		ScriptableObject.putProperty(engine.global(), "RegisterTag", registerTagFunction());
		for (Path file : findScripts()) {
			String name = folder.relativize(file).toString().replace('\\', '/');
			currentFile = name;
			fileTags.put(name, new ArrayList<>());
			try {
				String source = IMPL_IMPORT.matcher(Files.readString(file)).replaceAll("");
				engine.execute(source, name);
			} catch (RhinoException e) {
				diagnostics.add(new ScriptDiagnostic(name, e.details() + " (line " + e.lineNumber() + ")"));
			} catch (Exception | Error e) {
				diagnostics.add(new ScriptDiagnostic(name, e.getMessage()));
			}
		}
		currentFile = null;
		ScriptableObject.putProperty(engine.global(), "RegisterTag", JsHost.function(engine.global(), "RegisterTag", a -> null));
		for (ScriptDiagnostic d : diagnostics) {
			LOG.warn("[Script] {}: {}", d.file(), d.message());
		}
		writeImplFiles();
		LOG.info("[Script] Loaded {} script(s), {} tag(s)", fileTags.size(), fileTags.values().stream().mapToInt(List::size).sum());
	}

	private List<Path> findScripts() {
		try (Stream<Path> walk = Files.walk(folder)) {
			return walk.filter(p -> p.toString().endsWith(".js"))
					.filter(p -> {
						for (Path part : folder.relativize(p)) {
							String s = part.toString();
							if (s.startsWith(".") || s.equals("node_modules")) {
								return false;
							}
						}
						return !p.getFileName().toString().equals("impl.js");
					})
					.sorted(Comparator.comparing(Path::toString))
					.toList();
		} catch (IOException e) {
			diagnostics.add(new ScriptDiagnostic(folder.toString(), e.getMessage()));
			return List.of();
		}
	}

	/** The RegisterTag host function; receives the raw JS values (the function must stay a function). */
	private org.mozilla.javascript.BaseFunction registerTagFunction() {
		org.mozilla.javascript.BaseFunction f = new org.mozilla.javascript.BaseFunction() {
			@Override
			public Object call(org.mozilla.javascript.Context cx, org.mozilla.javascript.Scriptable scope,
							   org.mozilla.javascript.Scriptable thisObj, Object[] args) {
				Object rawName = args.length > 0 ? JsValues.toJava(args[0]) : null;
				if (rawName == null || rawName.toString().isBlank()) {
					diagnostics.add(new ScriptDiagnostic(currentFile == null ? "?" : currentFile, "RegisterTag: missing name"));
				} else {
					register(rawName.toString(), args.length > 1 ? args[1] : null, args.length > 2 ? args[2] : null);
				}
				return org.mozilla.javascript.Undefined.instance;
			}

			@Override
			public String getFunctionName() {
				return "RegisterTag";
			}
		};
		JsHost.ScriptRuntimeHelper.setup(f, engine.global());
		return f;
	}

	/** Called by the RegisterTag host function with the untouched JS values. */
	void register(String name, Object func, Object options) {
		String file = currentFile == null ? "?" : currentFile;
		if (!(func instanceof Function fn)) {
			diagnostics.add(new ScriptDiagnostic(file, "RegisterTag('" + name + "'): second argument must be a function"));
			return;
		}
		int type = TagType.NONE;
		String desc = null;
		Class<?> returnType = Object.class;
		if (options instanceof NativeObject obj) {
			Object t = obj.get("Type", obj);
			if (t instanceof Number n) {
				type = n.intValue();
			}
			Object d = obj.get("Desc", obj);
			if (d != null && d != NativeObject.NOT_FOUND && !(d instanceof org.mozilla.javascript.Undefined)) {
				desc = d.toString();
			}
			Object r = obj.get("ReturnType", obj);
			if (r != null && r != NativeObject.NOT_FOUND && !(r instanceof org.mozilla.javascript.Undefined)) {
				returnType = parseReturnType(r.toString());
			}
		}
		List<TagParam> params = new ArrayList<>();
		try {
			for (String p : extractParameterNames(org.mozilla.javascript.Context.toString(fn))) {
				boolean optional = p.endsWith("?");
				params.add(optional ? TagParam.optional(p.substring(0, p.length() - 1), Object.class, null) : TagParam.required(p, Object.class));
			}
		} catch (RuntimeException e) {
			diagnostics.add(new ScriptDiagnostic(file, "RegisterTag('" + name + "'): " + e.getMessage()));
			return;
		}
		TagManager.register(TagCore.js(name, desc, type, params, returnType, a -> engine.call(fn, a)));
		fileTags.computeIfAbsent(file, k -> new ArrayList<>()).add(name);
	}

	/** Only numeric declarations allow a format, like Overlayer's ParseReturnType. */
	private static Class<?> parseReturnType(String value) {
		return switch (value.trim().toLowerCase(Locale.ROOT)) {
			case "number", "double" -> Double.class;
			case "float", "single" -> Float.class;
			case "decimal" -> BigDecimal.class;
			case "int", "int32" -> Integer.class;
			case "long", "int64" -> Long.class;
			case "string" -> String.class;
			default -> Object.class;
		};
	}

	// ---- parameter names from function source (port of JSTagRegistrationHost) ----

	static List<String> extractParameterNames(String source) {
		if (source == null || source.isBlank()) {
			throw new IllegalStateException("Function source is unavailable");
		}
		int arrow = findTopLevelArrow(source);
		String parameters;
		if (arrow >= 0) {
			String head = source.substring(0, arrow).trim();
			if (head.startsWith("async ")) {
				head = head.substring(6).trim();
			}
			parameters = head.length() >= 2 && head.charAt(0) == '(' && head.charAt(head.length() - 1) == ')'
					? head.substring(1, head.length() - 1) : head;
		} else {
			int open = source.indexOf('(');
			int close = open < 0 ? -1 : findMatching(source, open);
			if (open < 0 || close < 0) {
				throw new IllegalStateException("Function parameters cannot be parsed");
			}
			parameters = source.substring(open + 1, close);
		}
		List<String> parts = splitTopLevel(parameters);
		List<String> out = new ArrayList<>();
		for (int i = 0; i < parts.size(); i++) {
			out.add(normalize(parts.get(i), i));
		}
		return out;
	}

	private static List<String> splitTopLevel(String s) {
		List<String> out = new ArrayList<>();
		if (s.isBlank()) {
			return out;
		}
		int depth = 0;
		int start = 0;
		char quote = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == '\\') {
					i++;
				} else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"' || c == '`') {
				quote = c;
			} else if (c == '(' || c == '[' || c == '{') {
				depth++;
			} else if (c == ')' || c == ']' || c == '}') {
				depth--;
			} else if (c == ',' && depth == 0) {
				out.add(s.substring(start, i));
				start = i + 1;
			}
		}
		out.add(s.substring(start));
		return out;
	}

	private static String normalize(String raw, int index) {
		String p = raw.trim();
		int eq = topLevelIndexOf(p, '=');
		boolean optional = eq >= 0;
		String name = (eq >= 0 ? p.substring(0, eq) : p).trim();
		if (name.startsWith("...")) {
			name = name.substring(3).trim();
			optional = true;
		}
		if (!name.matches("[A-Za-z_$][A-Za-z0-9_$]*")) {
			name = "arg" + (index + 1);
		}
		return optional ? name + "?" : name;
	}

	private static int topLevelIndexOf(String s, char target) {
		int depth = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (c == '(' || c == '[' || c == '{') {
				depth++;
			} else if (c == ')' || c == ']' || c == '}') {
				depth--;
			} else if (c == target && depth == 0) {
				return i;
			}
		}
		return -1;
	}

	private static int findTopLevelArrow(String s) {
		int depth = 0;
		char quote = 0;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == '\\') {
					i++;
				} else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"' || c == '`') {
				quote = c;
			} else if (c == '(' || c == '[' || c == '{') {
				depth++;
			} else if (c == ')' || c == ']' || c == '}') {
				depth--;
			}
			if (c == '=' && i + 1 < s.length() && s.charAt(i + 1) == '>' && depth == 0) {
				return i;
			}
			if (depth == 0 && c == '{') {
				break;
			}
		}
		return -1;
	}

	private static int findMatching(String s, int open) {
		int depth = 0;
		char quote = 0;
		for (int i = open; i < s.length(); i++) {
			char c = s.charAt(i);
			if (quote != 0) {
				if (c == '\\') {
					i++;
				} else if (c == quote) {
					quote = 0;
				}
				continue;
			}
			if (c == '\'' || c == '"' || c == '`') {
				quote = c;
			} else if (c == '(') {
				depth++;
			} else if (c == ')') {
				depth--;
				if (depth == 0) {
					return i;
				}
			}
		}
		return -1;
	}

	// ---- impl.js / impl.d.ts ----

	private void writeImplFiles() {
		if (folder == null) {
			return;
		}
		StringBuilder js = new StringBuilder("/* Auto-generated by Custom HUD, DO NOT EDIT */\n\n");
		js.append("globalThis.Tag = new Proxy({}, { get: function(target, prop) { return function(...args) { return undefined; }; } });\n\n");
		js.append(TagType.SCRIPT).append("\n\n");
		js.append("/** Registers a custom tag. options: { Type: TagType.ProcessFormat, ReturnType: 'number'|'int'|'string', Desc: '...' } */\n");
		js.append("globalThis.RegisterTag = function(name, func, options) {};\n\n");
		js.append("/* Store.Get/Set/Has/Remove/Clear/Keys/Count — shared between Fx expressions. Prefs.Get/Set/Remove — saved to prefs.json. */\n\n");
		StringBuilder dts = new StringBuilder("/* Auto-generated by Custom HUD, DO NOT EDIT */\n\n");
		dts.append("declare function RegisterTag(name: string, func: (...args: any[]) => any, options?: { Type?: number, ReturnType?: string, Desc?: string }): void;\n");
		dts.append("declare const Store: { Get(key: string, fallback?: any): any; Set(key: string, value: any): any; Has(key: string): boolean; Remove(key: string): boolean; Clear(): void; Keys(): string[]; readonly Count: number };\n");
		dts.append("declare const Prefs: { Get(key: string, fallback?: any): any; Set(key: string, value: any): any; Remove(key: string): boolean };\n");
		dts.append("declare const Log: { Msg(m: any): void; Wrn(m: any): void; Err(m: any): void };\n\n");
		for (TagCore tag : TagManager.all().stream().sorted(Comparator.comparing(TagCore::name)).toList()) {
			if (tag.kind() == TagCore.Kind.ADVANCED) {
				continue;
			}
			String params = String.join(", ", tag.params().stream().map(TagParam::name).toList());
			if (tag.description() != null) {
				js.append("/** ").append(tag.description().replace("\n", "\n * ")).append(" */\n");
			}
			js.append("function ").append(tag.name()).append('(').append(params).append(") { return Tag.").append(tag.name())
					.append('(').append(params).append("); }\n\n");
			String typed = String.join(", ", tag.params().stream()
					.map(p -> p.name() + (p.hasDefault() ? "?" : "") + ": " + jsType(p.type())).toList());
			dts.append("declare function ").append(tag.name()).append('(').append(typed).append("): ").append(jsType(tag.returnType())).append(";\n");
		}
		try {
			Files.writeString(folder.resolve("impl.js"), js);
			Files.writeString(folder.resolve("impl.d.ts"), dts);
		} catch (IOException e) {
			LOG.warn("[Script] Failed to write impl files: {}", e.getMessage());
		}
	}

	private static String jsType(Class<?> t) {
		if (t == String.class) {
			return "string";
		}
		if (t == Boolean.class) {
			return "boolean";
		}
		if (Number.class.isAssignableFrom(t)) {
			return "number";
		}
		return "any";
	}
}
