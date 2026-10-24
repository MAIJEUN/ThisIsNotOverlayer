package dev.customhud.tag.compile;

import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** A compile problem in a text (Overlayer's CompileDiagnostic). */
public record Diagnostic(Id id, Severity severity, String tagName, int index, int length, Object... data) {
	public enum Severity { WARNING, ERROR }

	public enum Id {
		INTERNAL_ERROR, TAG_NOT_FOUND, ARG_CONVERT_FAIL, ARG_TOO_FEW, ARG_TOO_MANY, FORMAT_FAIL, JS_ONLY_BLOCKED, ADVANCED_TAG_EXCEPTION, JS_ERROR
	}

	/** Plain-text message (also used by tests and logs). */
	public String message() {
		return switch (id) {
			case TAG_NOT_FOUND -> "Tag '" + data[0] + "' not found";
			case ARG_CONVERT_FAIL -> "Argument " + ((int) data[0] + 1) + " '" + data[1] + "' is not a " + data[2];
			case ARG_TOO_FEW -> tagName + " needs at least " + data[0] + " argument(s), got " + data[1];
			case ARG_TOO_MANY -> tagName + " takes at most " + data[0] + " argument(s), got " + data[1] + " (extra ignored)";
			case FORMAT_FAIL -> "Invalid format '" + data[0] + "': " + data[1];
			case JS_ONLY_BLOCKED -> "'" + data[0] + "' can only be used from JavaScript";
			case ADVANCED_TAG_EXCEPTION, JS_ERROR -> String.valueOf(data.length > 0 ? data[0] : "error");
			case INTERNAL_ERROR -> "Internal error: " + (data.length > 0 ? data[0] : "");
		};
	}

	/** Localised message for the editor. */
	public Component text() {
		String key = "diagnostic.customhud." + id.name().toLowerCase();
		Object[] args = switch (id) {
			case TAG_NOT_FOUND, JS_ONLY_BLOCKED -> new Object[]{data[0]};
			case ARG_CONVERT_FAIL -> new Object[]{(int) data[0] + 1, data[1], data[2]};
			case ARG_TOO_FEW, ARG_TOO_MANY -> new Object[]{tagName, data[0], data[1]};
			case FORMAT_FAIL -> new Object[]{data[0], String.valueOf(data[1])};
			default -> new Object[]{data.length > 0 ? String.valueOf(data[0]) : ""};
		};
		return Component.translatable(key, args);
	}

	@Override
	public String toString() {
		return severity + " " + id + " @" + index + "+" + length + " " + Arrays.toString(data);
	}
}
