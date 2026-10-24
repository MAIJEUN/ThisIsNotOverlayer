package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.fx.Fx;

public class BoolProp extends Prop<Boolean> {
	public BoolProp(String id, boolean defaultValue) {
		super(id, defaultValue, null);
	}

	public boolean on() {
		return get();
	}

	public void toggle() {
		value = !value;
	}

	@Override
	protected Evaluated<Boolean> evaluate(String expression) {
		Fx.Result<Boolean> r = Fx.bool(expression);
		return r.error() != null || r.value() == null ? Evaluated.failed(r.error()) : Evaluated.of(r.value());
	}

	@Override
	protected String fixedAsExpression() {
		return String.valueOf(value);
	}

	@Override
	protected JsonElement write(Boolean v) {
		return new JsonPrimitive(v);
	}

	@Override
	protected Boolean read(JsonElement json) {
		return json.getAsBoolean();
	}
}
