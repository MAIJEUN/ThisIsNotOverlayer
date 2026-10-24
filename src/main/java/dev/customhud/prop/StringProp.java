package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.fx.Fx;
import dev.customhud.tag.text.TextEngine;

/** A plain string (an item id, a name). Fx is JavaScript first, then a tag template, like Overlayer's string Fx. */
public class StringProp extends Prop<String> {
	private final TextEngine engine = new TextEngine();

	public StringProp(String id, String defaultValue) {
		this(id, defaultValue, null);
	}

	public StringProp(String id, String defaultValue, String defaultFx) {
		super(id, defaultValue, defaultFx);
	}

	@Override
	protected Evaluated<String> evaluate(String expression) {
		return Evaluated.of(Fx.string(expression, engine));
	}

	@Override
	protected String fixedAsExpression() {
		return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	@Override
	protected JsonElement write(String v) {
		return new JsonPrimitive(v);
	}

	@Override
	protected String read(JsonElement json) {
		return json.getAsString();
	}
}
