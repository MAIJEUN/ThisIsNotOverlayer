package dev.customhud.tag.compile;

import dev.customhud.tag.text.ParsedTag;

import java.util.List;
import java.util.function.Supplier;

/** A tag that compiles its own placeholder (e.g. JSExpr); returns the per-frame text supplier. */
@FunctionalInterface
public interface AdvancedTag {
	Supplier<String> compile(ParsedTag parsed, List<Diagnostic> diagnostics);
}
