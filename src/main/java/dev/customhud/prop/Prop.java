package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * An element property that is either a fixed value or an Fx expression, evaluated like
 * Overlayer's FxValue (see {@link dev.customhud.fx.Fx}): JavaScript first, then NCalc / the
 * type's own parser, and the fixed value when both fail ({@link #fxError()} says why).
 */
public abstract class Prop<T> {
	private final String id;
	protected final T defaultValue;
	private final String defaultFx;
	protected T value;
	private String fx;
	private String fxError;
	private BooleanSupplier visible = () -> true;
	private long cachedFrame = -1;
	private T cached;

	private static long frame;

	/** Starts a new frame: Fx values are evaluated at most once per frame, however often they are read. */
	public static void nextFrame() {
		frame++;
	}

	protected Prop(String id, T defaultValue, String defaultFx) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.defaultFx = defaultFx;
		this.value = defaultValue;
		this.fx = defaultFx;
	}

	public String id() {
		return id;
	}

	public Component label() {
		return Component.translatable("prop.customhud." + id);
	}

	/** A copy with the same id, fixed value and expression (for cloning objects). */
	@SuppressWarnings("unchecked")
	public void copyFrom(Prop<?> other) {
		fromJson(other.toJson());
	}

	/** Current value: the Fx result when an expression is set and works, otherwise the fixed value. */
	public T get() {
		if (fx == null) {
			return value;
		}
		if (cachedFrame == frame) {
			return cached;
		}
		cachedFrame = frame;
		try {
			Evaluated<T> r = evaluate(fx);
			fxError = r.error;
			cached = r.value != null ? r.value : value;
		} catch (RuntimeException e) {
			fxError = e.getMessage();
			cached = value;
		}
		return cached;
	}

	/** Forgets this frame's cached Fx result (after the expression or fixed value changed). */
	protected void invalidate() {
		cachedFrame = -1;
	}

	/** Result of evaluating the expression; {@code value == null} means "use the fixed value". */
	protected record Evaluated<T>(T value, String error) {
		static <T> Evaluated<T> of(T v) {
			return new Evaluated<>(v, null);
		}

		static <T> Evaluated<T> failed(String error) {
			return new Evaluated<>(null, error == null ? "Invalid expression" : error);
		}
	}

	protected abstract Evaluated<T> evaluate(String expression);

	/** The fixed value written as an Fx expression, used when switching Fx on. */
	protected abstract String fixedAsExpression();

	protected abstract JsonElement write(T v);

	protected abstract T read(JsonElement json);

	public T fixedValue() {
		return value;
	}

	public void set(T v) {
		value = v;
		invalidate();
	}

	public T defaultValue() {
		return defaultValue;
	}

	public boolean isDefault() {
		return java.util.Objects.equals(fx, defaultFx) && java.util.Objects.equals(value, defaultValue);
	}

	public boolean hasFx() {
		return fx != null;
	}

	public String fxSource() {
		return fx == null ? "" : fx;
	}

	/** Sets the expression (Fx on, even if blank); {@code null} turns Fx off. */
	public void setFx(String source) {
		fx = source;
		fxError = null;
		invalidate();
	}

	public void enableFx() {
		setFx(fixedAsExpression());
	}

	/** Why the last evaluation fell back to the fixed value, or null. */
	public String fxError() {
		return fx == null ? null : fxError;
	}

	public void reset() {
		value = defaultValue;
		setFx(defaultFx);
	}

	public Prop<T> visibleIf(BooleanSupplier condition) {
		this.visible = condition;
		return this;
	}

	public boolean isVisible() {
		return visible.getAsBoolean();
	}

	public JsonElement toJson() {
		if (fx == null) {
			return write(value);
		}
		JsonObject obj = new JsonObject();
		obj.addProperty("fx", fx);
		obj.add("value", write(value));
		return obj;
	}

	public void fromJson(JsonElement json) {
		if (json.isJsonObject() && json.getAsJsonObject().has("fx")) {
			JsonObject obj = json.getAsJsonObject();
			if (obj.has("value")) {
				value = read(obj.get("value"));
			}
			setFx(obj.get("fx").getAsString());
		} else {
			value = read(json);
			fx = null;
		}
	}
}
