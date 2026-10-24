package dev.customhud.tag.compile;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import dev.customhud.tag.text.ParsedTag;

import java.util.List;

/**
 * Splits a placeholder's arguments into values and an optional format, Overlayer's rules:
 * <ul>
 *   <li>no parameters: the first argument is the format (JS tags only with ProcessFormat)</li>
 *   <li>ProcessFormat: the last argument becomes the format unless every argument binds as a value
 *       (a lone argument for optional-only parameters stays a format, e.g. {@code {Fps:0}})</li>
 *   <li>too few arguments → error; too many → warning; unconvertible argument or invalid format → error</li>
 * </ul>
 */
public final class SignatureResolver {
	public enum State { VALID, WARNING, ERROR }

	public record Resolved(List<String> args, String format, State state) {
		public static final Resolved INVALID = new Resolved(List.of(), null, State.ERROR);

		public boolean hasFormat() {
			return format != null;
		}

		public boolean executable() {
			return state != State.ERROR;
		}
	}

	private SignatureResolver() {
	}

	public static Resolved resolve(TagCore tag, ParsedTag parsed, List<Diagnostic> diag) {
		if (TagType.has(tag.tagType(), TagType.JS_ONLY)) {
			diag.add(error(Diagnostic.Id.JS_ONLY_BLOCKED, parsed, tag.name()));
			return Resolved.INVALID;
		}
		List<String> raw = parsed.args();
		List<TagParam> params = tag.params();
		boolean processFormat = TagType.has(tag.tagType(), TagType.PROCESS_FORMAT);
		int minRequired = tag.requiredCount();

		List<String> args = raw;
		String format = null;
		if (params.isEmpty()) {
			if (!raw.isEmpty() && (processFormat || !tag.isDynamic())) {
				format = raw.getFirst();
			}
		} else if (processFormat && !raw.isEmpty()) {
			if (!tryTakeAllAsValues(raw, params, minRequired)) {
				format = raw.getLast();
				args = raw.subList(0, raw.size() - 1);
			}
		}

		int valueParamCount = params.size() + (format != null ? 1 : 0);
		if (args.size() < minRequired) {
			diag.add(error(Diagnostic.Id.ARG_TOO_FEW, parsed, minRequired, args.size()));
			return Resolved.INVALID;
		}
		boolean warning = false;
		if (args.size() > valueParamCount) {
			diag.add(new Diagnostic(Diagnostic.Id.ARG_TOO_MANY, Diagnostic.Severity.WARNING, parsed.name(), parsed.index(), parsed.length(),
					valueParamCount, args.size()));
			warning = true;
		}
		if (format != null) {
			String problem = tag.isDynamic() ? FormatValidator.validateJs(format) : FormatValidator.validate(tag.returnType(), format);
			if (problem != null) {
				diag.add(error(Diagnostic.Id.FORMAT_FAIL, parsed, format, problem));
				return Resolved.INVALID;
			}
		}
		for (int i = 0; i < args.size() && i < params.size(); i++) {
			try {
				ArgConverter.convert(args.get(i), params.get(i).type());
			} catch (RuntimeException e) {
				diag.add(error(Diagnostic.Id.ARG_CONVERT_FAIL, parsed, i, args.get(i), FormatValidator.typeName(params.get(i).type())));
				return Resolved.INVALID;
			}
		}
		return new Resolved(List.copyOf(args), format, warning ? State.WARNING : State.VALID);
	}

	private static boolean tryTakeAllAsValues(List<String> raw, List<TagParam> params, int minRequired) {
		if (raw.size() > params.size() || raw.size() < minRequired) {
			return false;
		}
		if (raw.size() <= 1 && minRequired == 0) {
			return false;
		}
		for (int i = 0; i < raw.size(); i++) {
			if (!ArgConverter.canConvert(raw.get(i), params.get(i).type())) {
				return false;
			}
		}
		return true;
	}

	private static Diagnostic error(Diagnostic.Id id, ParsedTag parsed, Object... data) {
		return new Diagnostic(id, Diagnostic.Severity.ERROR, parsed.name(), parsed.index(), parsed.length(), data);
	}
}
