package dev.customhud.tag.core;

import dev.customhud.tag.compile.AdvancedTag;
import dev.customhud.tag.compile.ArgConverter;
import dev.customhud.tag.compile.DotNet;

import java.util.List;

/**
 * A registered tag: a Java function, a JavaScript function (from {@code RegisterTag}),
 * or an advanced tag that compiles itself (JSExpr). Mirrors Overlayer's TagCore.
 */
public final class TagCore {
	public enum Kind { JAVA, JS, ADVANCED }

	@FunctionalInterface
	public interface Invoker {
		/** Arguments are already converted to the parameter types and padded with defaults. */
		Object invoke(Object[] args);
	}

	@FunctionalInterface
	public interface JsCaller {
		Object call(Object[] args);
	}

	private final String name;
	private final String description;
	private final int tagType;
	private final Kind kind;
	private final List<TagParam> params;
	private final Class<?> returnType;
	private final Invoker invoker;
	private final JsCaller jsCaller;
	private final AdvancedTag advanced;
	private final int requiredCount;

	private TagCore(String name, String description, int tagType, Kind kind, List<TagParam> params, Class<?> returnType,
					Invoker invoker, JsCaller jsCaller, AdvancedTag advanced) {
		this.name = name;
		this.description = description;
		this.kind = kind;
		this.tagType = kind == Kind.JAVA ? tagType : tagType & ~TagType.ADVANCED;
		this.params = List.copyOf(params);
		this.returnType = returnType;
		this.invoker = invoker;
		this.jsCaller = jsCaller;
		this.advanced = advanced;
		this.requiredCount = (int) params.stream().filter(p -> !p.hasDefault()).count();
	}

	public static TagCore java(String name, String description, int tagType, List<TagParam> params, Class<?> returnType, Invoker invoker) {
		return new TagCore(name, description, tagType, Kind.JAVA, params, returnType, invoker, null, null);
	}

	public static TagCore js(String name, String description, int tagType, List<TagParam> params, Class<?> returnType, JsCaller caller) {
		return new TagCore(name, description, tagType, Kind.JS, params, returnType, null, caller, null);
	}

	public static TagCore advanced(String name, String description, AdvancedTag advanced) {
		return new TagCore(name, description, TagType.ADVANCED, Kind.ADVANCED, List.of(), String.class, null, null, advanced);
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public int tagType() {
		return tagType;
	}

	public Kind kind() {
		return kind;
	}

	public List<TagParam> params() {
		return params;
	}

	public Class<?> returnType() {
		return returnType;
	}

	public AdvancedTag advanced() {
		return advanced;
	}

	public int requiredCount() {
		return requiredCount;
	}

	/** JS tags: the value type is only known at runtime. */
	public boolean isDynamic() {
		return kind == Kind.JS;
	}

	public boolean isHidden() {
		return TagType.has(tagType, TagType.HIDE);
	}

	/**
	 * Calls the tag with loosely typed arguments (from JS or Fx): each one is coerced
	 * to the parameter type, missing ones are filled with defaults and extras are ignored.
	 */
	public Object invoke(Object... args) {
		if (kind == Kind.JS) {
			return jsCaller.call(args == null ? new Object[0] : args);
		}
		if (kind != Kind.JAVA) {
			return null;
		}
		Object[] normalized = new Object[params.size()];
		for (int i = 0; i < params.size(); i++) {
			TagParam p = params.get(i);
			if (args != null && i < args.length) {
				normalized[i] = coerce(args[i], p.type());
			} else if (p.hasDefault()) {
				normalized[i] = p.defaultValue() == null ? defaultOf(p.type()) : coerce(p.defaultValue(), p.type());
			} else {
				normalized[i] = defaultOf(p.type());
			}
		}
		return invoker.invoke(normalized);
	}

	/** Calls with arguments that already have the parameter types (the compiled text path). */
	public Object invokeTyped(Object[] args) {
		if (kind == Kind.JS) {
			return jsCaller.call(args);
		}
		return invoker.invoke(args);
	}

	public static Object defaultOf(Class<?> type) {
		if (type == Integer.class) {
			return 0;
		}
		if (type == Long.class) {
			return 0L;
		}
		if (type == Double.class) {
			return 0d;
		}
		if (type == Float.class) {
			return 0f;
		}
		if (type == Boolean.class) {
			return false;
		}
		if (type.isEnum()) {
			return type.getEnumConstants()[0];
		}
		return null;
	}

	/** Overlayer's TagCore.CoerceArg: JS numbers arrive as doubles, strings get parsed, etc. */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static Object coerce(Object value, Class<?> target) {
		if (value == null) {
			return defaultOf(target);
		}
		if (target == Object.class || target.isInstance(value)) {
			return value;
		}
		if (target == String.class) {
			return DotNet.toString(value);
		}
		if (target.isEnum()) {
			if (value instanceof String s) {
				return ArgConverter.convert(s, target);
			}
			if (value instanceof Number n) {
				Object[] constants = target.getEnumConstants();
				int i = n.intValue();
				if (i >= 0 && i < constants.length) {
					return constants[i];
				}
			}
			throw new IllegalArgumentException("Cannot convert '" + value + "' to " + target.getSimpleName());
		}
		if (value instanceof String s) {
			return ArgConverter.convert(s, target);
		}
		if (value instanceof Number || value instanceof Boolean) {
			return DotNet.changeType(value, target);
		}
		throw new IllegalArgumentException("Cannot convert '" + value.getClass().getSimpleName() + "' to '" + target.getSimpleName() + "'.");
	}

	@Override
	public String toString() {
		return "Tag[" + name + ", " + kind + ", params=" + params + ", returns=" + returnType.getSimpleName() + "]";
	}
}
