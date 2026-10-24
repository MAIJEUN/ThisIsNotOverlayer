package dev.customhud.tag.compile;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import dev.customhud.tag.text.ParsedTag;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Placeholder → per-frame text supplier (Overlayer's Wrapper + Compiler + ExpressionBuilder). */
public final class Compiler {
	/** Compiled placeholder; {@code get()} returning null means "show the raw text". */
	public record Compiled(Supplier<String> supplier, List<Diagnostic> diagnostics) {
		public boolean valid() {
			return diagnostics.stream().noneMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
		}

		public String get() {
			return supplier.get();
		}
	}

	/** Whether the game counts as "playing" / "paused" for BlockOn* tags. */
	public static BooleanSupplier isPlaying = () -> true;
	public static BooleanSupplier isPaused = () -> false;

	private Compiler() {
	}

	public static Compiled wrap(ParsedTag parsed) {
		TagCore tag = TagManager.get(parsed.name());
		if (tag == null) {
			return new Compiled(parsed::raw, List.of(new Diagnostic(Diagnostic.Id.TAG_NOT_FOUND, Diagnostic.Severity.ERROR,
					parsed.name(), parsed.index(), parsed.length(), parsed.name())));
		}
		if (tag.kind() == TagCore.Kind.ADVANCED) {
			List<Diagnostic> diag = new ArrayList<>();
			Supplier<String> s;
			try {
				s = tag.advanced().compile(parsed, diag);
			} catch (RuntimeException e) {
				diag.add(new Diagnostic(Diagnostic.Id.ADVANCED_TAG_EXCEPTION, Diagnostic.Severity.ERROR, parsed.name(), parsed.index(),
						parsed.length(), e.getMessage()));
				s = parsed::raw;
			}
			return new Compiled(s, List.copyOf(diag));
		}
		return compile(tag, parsed);
	}

	public static Compiled compile(TagCore tag, ParsedTag parsed) {
		List<Diagnostic> diag = new ArrayList<>();
		SignatureResolver.Resolved sig = SignatureResolver.resolve(tag, parsed, diag);
		if (!sig.executable()) {
			return new Compiled(parsed::raw, List.copyOf(diag));
		}
		Supplier<String> inner;
		try {
			inner = build(tag, sig);
		} catch (RuntimeException e) {
			diag.add(new Diagnostic(Diagnostic.Id.INTERNAL_ERROR, Diagnostic.Severity.ERROR, parsed.name(), parsed.index(), parsed.length(),
					String.valueOf(e.getMessage())));
			return new Compiled(parsed::raw, List.copyOf(diag));
		}
		if ((tag.tagType() & TagType.BLOCK_ON_ALL) == 0) {
			return new Compiled(inner, List.copyOf(diag));
		}
		// While blocked keep the last good value instead of flashing raw text.
		String[] last = {null};
		Supplier<String> blocked = () -> {
			boolean block = (TagType.has(tag.tagType(), TagType.BLOCK_ON_NOT_PLAYING) && !isPlaying.getAsBoolean())
					|| (TagType.has(tag.tagType(), TagType.BLOCK_ON_PAUSED) && isPaused.getAsBoolean());
			if (block) {
				return last[0] != null ? last[0] : parsed.raw();
			}
			return last[0] = inner.get();
		};
		return new Compiled(blocked, List.copyOf(diag));
	}

	/** Converts arguments once, then formats every call like Overlayer's ExpressionBuilder. */
	private static Supplier<String> build(TagCore tag, SignatureResolver.Resolved sig) {
		List<TagParam> params = tag.params();
		Object[] values = new Object[params.size()];
		for (int i = 0; i < params.size(); i++) {
			TagParam p = params.get(i);
			if (i < sig.args().size()) {
				values[i] = ArgConverter.convert(sig.args().get(i), p.type());
			} else if (p.hasDefault()) {
				values[i] = p.defaultValue() == null && tag.kind() == TagCore.Kind.JAVA ? TagCore.defaultOf(p.type()) : p.defaultValue();
			} else {
				values[i] = tag.isDynamic() ? null : TagCore.defaultOf(p.type());
			}
		}
		String format = sig.format();
		if (tag.isDynamic()) {
			return () -> JsResultFormatter.format(tag.invokeTyped(values.clone()), format);
		}
		if (tag.returnType() == String.class) {
			return () -> {
				Object v = tag.invokeTyped(values.clone());
				return v == null ? "" : v.toString();
			};
		}
		boolean formattable = format != null && DotNet.isFormattable(tag.returnType());
		return () -> {
			Object v = tag.invokeTyped(values.clone());
			return formattable ? DotNet.format(v, format) : DotNet.toString(v);
		};
	}
}
