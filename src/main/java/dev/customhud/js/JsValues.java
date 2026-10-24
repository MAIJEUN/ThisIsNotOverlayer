package dev.customhud.js;

import org.mozilla.javascript.ConsString;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.Undefined;
import org.mozilla.javascript.Wrapper;

import java.util.ArrayList;
import java.util.List;

/**
 * JS ↔ Java values, converted the way ClearScript hands V8 values to Overlayer:
 * whole numbers that fit an int become Integer, other numbers Double, arrays List,
 * undefined/null → null, other objects → their JS string.
 */
public final class JsValues {
	private JsValues() {
	}

	public static Object toJava(Object v) {
		if (v == null || v instanceof Undefined || v == Scriptable.NOT_FOUND) {
			return null;
		}
		if (v instanceof Wrapper w) {
			return toJava(w.unwrap());
		}
		if (v instanceof CharSequence cs) {
			return cs.toString();
		}
		if (v instanceof Boolean) {
			return v;
		}
		if (v instanceof Number n) {
			double d = n.doubleValue();
			if (d == Math.rint(d) && !Double.isInfinite(d) && d >= Integer.MIN_VALUE && d <= Integer.MAX_VALUE && !(d == 0 && 1 / d < 0)) {
				return (int) d;
			}
			return d;
		}
		if (v instanceof NativeArray array) {
			List<Object> list = new ArrayList<>();
			for (long i = 0; i < array.getLength(); i++) {
				list.add(toJava(array.get((int) i, array)));
			}
			return list;
		}
		if (v instanceof Scriptable) {
			return Context.toString(v);
		}
		return v;
	}

	public static Object toJs(Object v, Scriptable scope) {
		if (v == null) {
			return Undefined.instance;
		}
		if (v instanceof String || v instanceof Boolean) {
			return v;
		}
		if (v instanceof Number n) {
			return n.doubleValue();
		}
		if (v instanceof Enum<?> e) {
			return e.name();
		}
		if (v instanceof ConsString cs) {
			return cs.toString();
		}
		if (v instanceof List<?> list) {
			Context cx = Context.getCurrentContext();
			Object[] items = new Object[list.size()];
			for (int i = 0; i < items.length; i++) {
				items[i] = toJs(list.get(i), scope);
			}
			return cx.newArray(scope, items);
		}
		if (v instanceof Scriptable) {
			return v;
		}
		return v.toString();
	}
}
