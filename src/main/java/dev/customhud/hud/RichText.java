package dev.customhud.hud;

import dev.customhud.prop.Colors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Overlayer's rich-text tags drawn with Minecraft's text styles: {@code <color=#FF5555>…</color>}
 * (also {@code <color=red>}), {@code <b> <i> <u> <s>} and {@code <br>}. Minecraft's {@code &} codes
 * work too. Tags that are not understood stay as text, like in Overlayer.
 */
public final class RichText {
	private RichText() {
	}

	/** One component per line ({@code <br>}, a newline, or the text {@code \n}). */
	public static List<Component> lines(String raw) {
		String text = raw.replace("\r", "").replace("\\n", "\n");
		List<Component> lines = new ArrayList<>();
		MutableComponent line = Component.empty();
		Deque<Integer> colors = new ArrayDeque<>();
		boolean bold = false, italic = false, underline = false, strike = false;
		Style codes = Style.EMPTY;
		StringBuilder run = new StringBuilder();

		int i = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			Style style = style(colors, bold, italic, underline, strike, codes);
			if (c == '\n') {
				flush(line, run, style);
				lines.add(line);
				line = Component.empty();
				i++;
				continue;
			}
			if (c == '<') {
				int end = text.indexOf('>', i);
				if (end > i) {
					String tag = text.substring(i + 1, end).trim();
					String lower = tag.toLowerCase(Locale.ROOT);
					boolean known = true;
					flush(line, run, style);
					if (lower.equals("br")) {
						lines.add(line);
						line = Component.empty();
					} else if (lower.startsWith("color=")) {
						Integer col = color(tag.substring(6));
						if (col == null) {
							known = false;
						} else {
							colors.push(col);
						}
					} else if (lower.equals("/color")) {
						colors.poll();
					} else if (lower.equals("b") || lower.equals("/b")) {
						bold = !lower.startsWith("/");
					} else if (lower.equals("i") || lower.equals("/i")) {
						italic = !lower.startsWith("/");
					} else if (lower.equals("u") || lower.equals("/u")) {
						underline = !lower.startsWith("/");
					} else if (lower.equals("s") || lower.equals("/s")) {
						strike = !lower.startsWith("/");
					} else {
						known = false;
					}
					if (known) {
						i = end + 1;
						continue;
					}
				}
			}
			if ((c == '&' || c == '§') && i + 1 < text.length()) {
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (code == '#' && i + 8 <= text.length() && text.substring(i + 2, i + 8).matches("[0-9a-fA-F]{6}")) {
					flush(line, run, style);
					codes = Style.EMPTY.withColor(TextColor.fromRgb(Integer.parseInt(text.substring(i + 2, i + 8), 16)));
					i += 8;
					continue;
				}
				ChatFormatting f = ChatFormatting.getByCode(code);
				if (f != null) {
					flush(line, run, style);
					if (f == ChatFormatting.RESET) {
						codes = Style.EMPTY;
					} else if (f.ordinal() <= ChatFormatting.WHITE.ordinal()) {
						codes = Style.EMPTY.withColor(f);
					} else {
						codes = codes.applyFormat(f);
					}
					i += 2;
					continue;
				}
			}
			run.append(c);
			i++;
		}
		flush(line, run, style(colors, bold, italic, underline, strike, codes));
		lines.add(line);
		return lines;
	}

	private static Style style(Deque<Integer> colors, boolean bold, boolean italic, boolean underline, boolean strike, Style codes) {
		Style s = codes;
		if (!colors.isEmpty()) {
			s = s.withColor(TextColor.fromRgb(colors.peek() & 0xFFFFFF));
		}
		if (bold) {
			s = s.withBold(true);
		}
		if (italic) {
			s = s.withItalic(true);
		}
		if (underline) {
			s = s.withUnderlined(true);
		}
		if (strike) {
			s = s.withStrikethrough(true);
		}
		return s;
	}

	/** {@code #RGB}, {@code #RRGGBB}, {@code #RRGGBBAA}, a quoted value or a colour name. */
	private static Integer color(String value) {
		String v = value.trim();
		if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'"))) {
			v = v.substring(1, v.length() - 1);
		}
		return Colors.parse(v);
	}

	private static void flush(MutableComponent line, StringBuilder run, Style style) {
		if (!run.isEmpty()) {
			line.append(Component.literal(run.toString()).withStyle(style));
			run.setLength(0);
		}
	}
}
