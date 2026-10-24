package dev.customhud.js;

import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.function.Function;

/** Builds plain JS objects whose methods are Java lambdas (no reflection, nothing else of Java exposed). */
final class JsHost {
	private final Scriptable scope;
	private final NativeObject object = new NativeObject();

	private JsHost(Scriptable scope) {
		this.scope = scope;
		ScriptRuntimeHelper.setup(object, scope);
	}

	static JsHost object(Scriptable scope) {
		return new JsHost(scope);
	}

	/** {@code fn} receives Java-converted arguments and returns a Java value. */
	JsHost fn(String name, Function<Object[], Object> fn) {
		object.defineProperty(name, function(scope, name, fn), ScriptableObject.READONLY | ScriptableObject.DONTENUM);
		return this;
	}

	JsHost getter(String name, java.util.function.Supplier<Object> getter) {
		object.defineProperty(name, getter, null, ScriptableObject.READONLY);
		return this;
	}

	NativeObject build() {
		return object;
	}

	static BaseFunction function(Scriptable scope, String name, Function<Object[], Object> fn) {
		BaseFunction f = new BaseFunction() {
			@Override
			public Object call(Context cx, Scriptable s, Scriptable thisObj, Object[] args) {
				Object[] converted = new Object[args.length];
				for (int i = 0; i < args.length; i++) {
					converted[i] = JsValues.toJava(args[i]);
				}
				return JsValues.toJs(fn.apply(converted), scope);
			}

			@Override
			public String getFunctionName() {
				return name;
			}
		};
		ScriptRuntimeHelper.setup(f, scope);
		return f;
	}

	/** Sets parent scope and Object/Function prototypes so the object behaves like a normal JS value. */
	static final class ScriptRuntimeHelper {
		private ScriptRuntimeHelper() {
		}

		static void setup(ScriptableObject obj, Scriptable scope) {
			obj.setParentScope(scope);
			obj.setPrototype(obj instanceof BaseFunction
					? ScriptableObject.getFunctionPrototype(scope)
					: ScriptableObject.getObjectPrototype(scope));
		}
	}
}
