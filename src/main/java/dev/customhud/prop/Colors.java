package dev.customhud.prop;

import net.minecraft.network.chat.TextColor;

import java.util.Locale;

/** Colour parsing: {@code #RGB}, {@code #RRGGBB}, {@code #RRGGBBAA} and Minecraft's chat colour names ({@code gold}, {@code dark_aqua}). */
public final class Colors {
	private Colors() {
	}

	/** A chat colour by name ({@code "gold"}, {@code "Dark Aqua"}), or null. */
	public static Integer named(String name) {
		if (name == null) {
			return null;
		}
		String key = name.trim().replace(' ', '_').toLowerCase(Locale.ROOT);
		if (key.isEmpty() || key.startsWith("#")) {
			return null;
		}
		// TextMeshPro's names first (as in Overlayer's <color=red>), then Minecraft's chat colours (gold, aqua…).
		Integer tmp = switch (key) {
			case "black" -> 0xFF000000;
			case "blue" -> 0xFF0000FF;
			case "green" -> 0xFF00FF00;
			case "orange" -> 0xFFFF8000;
			case "purple" -> 0xFFA020F0;
			case "red" -> 0xFFFF0000;
			case "white" -> 0xFFFFFFFF;
			case "yellow" -> 0xFFFFFF00;
			default -> null;
		};
		if (tmp != null) {
			return tmp;
		}
		return TextColor.parseColor(key).result().map(c -> 0xFF000000 | c.getValue()).orElse(null);
	}

	/** {@code #RGB}, {@code #RGBA}, {@code #RRGGBB}, {@code #RRGGBBAA} or a chat colour name; null if it is neither. */
	public static Integer parse(String v) {
		if (v == null) {
			return null;
		}
		String t = v.trim();
		Integer named = named(t);
		if (named != null) {
			return named;
		}
		if (!t.startsWith("#")) {
			return null;
		}
		String h = t.substring(1);
		if (h.length() == 3 || h.length() == 4) {
			StringBuilder sb = new StringBuilder();
			for (char ch : h.toCharArray()) {
				sb.append(ch).append(ch);
			}
			h = sb.toString();
		}
		try {
			if (h.length() == 6) {
				return 0xFF000000 | Integer.parseInt(h, 16);
			}
			if (h.length() == 8) {
				int rgb = Integer.parseInt(h.substring(0, 6), 16);
				int a = Integer.parseInt(h.substring(6, 8), 16);
				return (a << 24) | rgb;
			}
		} catch (NumberFormatException e) {
			return null;
		}
		return null;
	}

	/** {@code #RRGGBB}, or {@code #RRGGBBAA} when not opaque. */
	public static String hex(int argb) {
		int a = argb >>> 24;
		String rgb = String.format("#%06X", argb & 0xFFFFFF);
		return a == 255 ? rgb : rgb + String.format("%02X", a);
	}

	/** Multiplies the alpha channel by {@code alpha} (0..1). */
	public static int withAlpha(int argb, float alpha) {
		int a = Math.round((argb >>> 24) * Math.max(0f, Math.min(1f, alpha)));
		return (a << 24) | (argb & 0xFFFFFF);
	}

	public static int lerp(int from, int to, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
		int r = Math.round(((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
		int g = Math.round(((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
		int b = Math.round((from & 255) + ((to & 255) - (from & 255)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}
}
