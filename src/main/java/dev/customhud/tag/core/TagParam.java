package dev.customhud.tag.core;

/**
 * A tag parameter. {@code type} is a boxed type ({@code Integer.class}, {@code Double.class},
 * {@code String.class}, {@code Boolean.class}, an enum, or {@code Object.class} for JS tags).
 */
public record TagParam(String name, Class<?> type, boolean hasDefault, Object defaultValue) {
	public static TagParam required(String name, Class<?> type) {
		return new TagParam(name, type, false, null);
	}

	public static TagParam optional(String name, Class<?> type, Object defaultValue) {
		return new TagParam(name, type, true, defaultValue);
	}
}
