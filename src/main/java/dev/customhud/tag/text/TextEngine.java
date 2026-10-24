package dev.customhud.tag.text;

import dev.customhud.tag.compile.Compiler;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.core.TagManager;

import java.util.ArrayList;
import java.util.List;

/**
 * A text with tags (Overlayer's TextEngineCore). Compiles when the text or the
 * registered tags change; {@link #get()} replaces each tag every frame. A tag that
 * fails to compile, or throws while running, is shown as its raw text.
 */
public final class TextEngine {
	private record Segment(int index, int length, Compiler.Compiled compiled) {
	}

	private String text = "";
	private List<Segment> segments = List.of();
	private int compiledVersion = -1;

	public TextEngine() {
	}

	public TextEngine(String text) {
		setText(text);
	}

	public String text() {
		return text;
	}

	public void setText(String value) {
		String v = value == null ? "" : value;
		if (!v.equals(text) || compiledVersion < 0) {
			text = v;
			compile();
		}
	}

	public void forceRecompile() {
		compile();
	}

	private void compile() {
		List<Segment> list = new ArrayList<>();
		for (ParsedTag t : Parser.parse(text)) {
			list.add(new Segment(t.index(), t.length(), Compiler.wrap(t)));
		}
		segments = list;
		compiledVersion = TagManager.version();
	}

	private void ensureFresh() {
		if (compiledVersion != TagManager.version()) {
			compile();
		}
	}

	public List<Diagnostic> diagnostics() {
		ensureFresh();
		List<Diagnostic> out = new ArrayList<>();
		for (Segment s : segments) {
			out.addAll(s.compiled().diagnostics());
		}
		return out;
	}

	public String get() {
		ensureFresh();
		if (segments.isEmpty()) {
			return text;
		}
		StringBuilder sb = new StringBuilder(text.length());
		int last = 0;
		for (Segment s : segments) {
			String replacement;
			try {
				replacement = s.compiled().get();
			} catch (RuntimeException e) {
				replacement = null;
			}
			int from = clamp(s.index());
			if (from > last) {
				sb.append(text, last, from);
			}
			int end = clamp(s.index() + s.length());
			if (replacement == null) {
				if (end > from) {
					sb.append(text, from, end);
				}
			} else {
				sb.append(replacement);
			}
			last = Math.max(last, end);
		}
		if (last < text.length()) {
			sb.append(text, last, text.length());
		}
		return sb.toString();
	}

	private int clamp(int i) {
		return Math.max(0, Math.min(text.length(), i));
	}
}
