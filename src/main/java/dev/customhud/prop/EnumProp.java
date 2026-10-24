package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.fx.Fx;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Cycles through enum constants; Fx is the plain name or index (no JS), like Overlayer's enum converter. */
public class EnumProp<E extends Enum<E>> extends Prop<E> {
	private final E[] values;
	private final Class<E> type;

	public EnumProp(String id, E defaultValue) {
		super(id, defaultValue, null);
		this.type = defaultValue.getDeclaringClass();
		this.values = type.getEnumConstants();
	}

	public E[] values() {
		return values;
	}

	public void cycle(int direction) {
		value = values[Math.floorMod(value.ordinal() + direction, values.length)];
	}

	public Component valueLabel() {
		return label(value);
	}

	public static Component label(Enum<?> e) {
		return Component.translatable("option.customhud." + e.name().toLowerCase(Locale.ROOT));
	}

	@Override
	protected Evaluated<E> evaluate(String expression) {
		return Evaluated.of(Fx.enumValue(expression, type));
	}

	@Override
	protected String fixedAsExpression() {
		return pascal(value.name());
	}

	/** TOP_LEFT → TopLeft (the Unity/TMP spelling users know). */
	public static String pascal(String name) {
		StringBuilder sb = new StringBuilder();
		for (String part : name.toLowerCase(Locale.ROOT).split("_")) {
			if (!part.isEmpty()) {
				sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
			}
		}
		return sb.toString();
	}

	@Override
	protected JsonElement write(E v) {
		return new JsonPrimitive(v.name());
	}

	@Override
	protected E read(JsonElement json) {
		String name = json.getAsString();
		for (E e : values) {
			if (e.name().equalsIgnoreCase(name)) {
				return e;
			}
		}
		return defaultValue;
	}
}
