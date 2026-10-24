package dev.customhud.prop;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.customhud.fx.Fx;
import dev.customhud.tag.compile.DotNet;

/** A number; the slider uses min/max/step, Fx results are not clamped (so animations can overshoot). */
public class NumberProp extends Prop<Double> {
	private final double min;
	private final double max;
	private final double step;
	private final String suffix;
	private boolean field;

	public NumberProp(String id, double defaultValue, double min, double max, double step) {
		this(id, defaultValue, min, max, step, "", null);
	}

	public NumberProp(String id, double defaultValue, double min, double max, double step, String suffix) {
		this(id, defaultValue, min, max, step, suffix, null);
	}

	public NumberProp(String id, double defaultValue, double min, double max, double step, String suffix, String defaultFx) {
		super(id, defaultValue, defaultFx);
		this.min = min;
		this.max = max;
		this.step = step;
		this.suffix = suffix;
	}

	/** Shown as a plain number field (drag to change) instead of a slider with a fill bar. */
	public NumberProp field() {
		field = true;
		return this;
	}

	public boolean isField() {
		return field;
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	public String suffix() {
		return suffix;
	}

	public float f() {
		return get().floatValue();
	}

	public int i() {
		return (int) Math.round(get());
	}

	@Override
	public void set(Double v) {
		double snapped = Math.round((v - min) / step) * step + min;
		snapped = Math.round(snapped * 1e6) / 1e6;
		value = Math.max(min, Math.min(max, snapped));
		invalidate();
	}

	public double progress() {
		return (value - min) / (max - min);
	}

	public void setProgress(double t) {
		set(min + Math.max(0, Math.min(1, t)) * (max - min));
	}

	public String display() {
		return DotNet.toString(value) + suffix;
	}

	@Override
	protected Evaluated<Double> evaluate(String expression) {
		Fx.Result<Double> r = Fx.number(expression);
		if (r.error() != null || r.value() == null) {
			return Evaluated.failed(r.error());
		}
		if (r.value().isNaN() || r.value().isInfinite()) {
			return Evaluated.failed("Not a finite number");
		}
		return Evaluated.of(r.value());
	}

	@Override
	protected String fixedAsExpression() {
		return DotNet.toString(value);
	}

	@Override
	protected JsonElement write(Double v) {
		return new JsonPrimitive(v);
	}

	@Override
	protected Double read(JsonElement json) {
		return json.getAsDouble();
	}
}
