package dev.customhud.js;

import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.util.HashMap;
import java.util.Map;

/** {@code Tag.Name(...)}: any property is a function that calls the tag of that name (null if unknown). */
final class TagProxy extends ScriptableObject {
	private final Scriptable scope;
	private final Map<String, Object> functions = new HashMap<>();

	TagProxy(Scriptable scope) {
		this.scope = scope;
		setParentScope(scope);
		setPrototype(getObjectPrototype(scope));
	}

	@Override
	public String getClassName() {
		return "Tag";
	}

	@Override
	public Object get(String name, Scriptable start) {
		return functions.computeIfAbsent(name, n -> JsEngine.get().tagFunction(n));
	}

	@Override
	public boolean has(String name, Scriptable start) {
		return true;
	}

	Scriptable scope() {
		return scope;
	}
}
