package dev.customhud.tag.impl;

import dev.customhud.js.JsEngine;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.compile.DotNet;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.text.ParsedTag;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/** {@code {JSExpr:code}} / {@code {JSExpr(code)}}: evaluates JavaScript every frame (Overlayer's JavaScirpt.JSExpr). */
public final class JsExprTag {
	private JsExprTag() {
	}

	public static TagCore create() {
		return TagCore.advanced("JSExpr", "Evaluates JavaScript expressions.\nEx) {JSExpr:Fps() > 60 ? 'good' : 'low'}", JsExprTag::compile);
	}

	private static Supplier<String> compile(ParsedTag parsed, List<Diagnostic> diags) {
		if (parsed.args().isEmpty()) {
			diags.add(error(parsed, "JSExpr: missing expression"));
			return parsed::raw;
		}
		String code = JsEngine.wrapBlock(extractCode(parsed));
		String syntax = JsEngine.get().compileError(code);
		if (syntax != null) {
			diags.add(error(parsed, syntax));
			return parsed::raw;
		}
		String[] lastLogged = {null};
		return () -> {
			JsEngine js = JsEngine.get();
			if (js.compileError(code) != null) {
				return parsed.raw();
			}
			Object result = js.evaluate(code);
			if (result != null) {
				return DotNet.toString(result);
			}
			String err = js.runtimeError(code);
			if (err != null && !err.equals(lastLogged[0])) {
				lastLogged[0] = err;
				LoggerFactory.getLogger("customhud/js").warn("[JavaScirpt] Runtime error, Tag '{}': {}", parsed.raw(), err);
			}
			return "";
		};
	}

	private static String extractCode(ParsedTag parsed) {
		String raw = parsed.raw();
		int prefix = parsed.name().length() + 1;
		if (raw.length() < prefix + 2 || raw.charAt(0) != '{') {
			return String.join(", ", parsed.args());
		}
		char separator = raw.charAt(prefix);
		if (separator == ':') {
			return raw.substring(prefix + 1, raw.length() - 1);
		}
		if (separator == '(' && raw.endsWith(")}")) {
			return raw.substring(prefix + 1, raw.length() - 2);
		}
		return String.join(", ", parsed.args());
	}

	private static Diagnostic error(ParsedTag parsed, String message) {
		return new Diagnostic(Diagnostic.Id.ADVANCED_TAG_EXCEPTION, Diagnostic.Severity.ERROR, parsed.name(), parsed.index(), parsed.length(), message);
	}
}
