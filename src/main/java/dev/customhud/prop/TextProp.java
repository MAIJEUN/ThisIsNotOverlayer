package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.text.TextEngine;

import java.util.List;

/** A text with tags ({@code "FPS: {Fps:0}"}), rendered by a TextEngine; it is dynamic by itself, so it has no Fx mode. */
public class TextProp extends Prop<String> {
	private final TextEngine engine = new TextEngine();

	public TextProp(String id, String defaultValue) {
		super(id, defaultValue, null);
		engine.setText(defaultValue);
	}

	@Override
	public void set(String v) {
		value = v;
		engine.setText(v);
	}

	@Override
	public void reset() {
		super.reset();
		engine.setText(value);
	}

	public String render() {
		return engine.get();
	}

	public List<Diagnostic> diagnostics() {
		return engine.diagnostics();
	}

	@Override
	protected Evaluated<String> evaluate(String expression) {
		return Evaluated.of(value);
	}

	@Override
	protected String fixedAsExpression() {
		return "";
	}

	@Override
	protected JsonElement write(String v) {
		return new JsonPrimitive(v);
	}

	@Override
	protected String read(JsonElement json) {
		return json.getAsString();
	}

	@Override
	public void fromJson(JsonElement json) {
		super.fromJson(json);
		engine.setText(value);
	}
}
