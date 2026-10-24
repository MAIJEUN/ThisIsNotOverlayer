package dev.customhud.js;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@code Store}: values shared between Fx expressions for the session (Overlayer's FxStore). */
public final class FxStore {
	private static final int MAX_ENTRIES = 1024;
	private final Map<String, Object> values = new HashMap<>();

	public synchronized Object get(String key, Object fallback) {
		if (key == null || key.isEmpty()) {
			return fallback;
		}
		return values.getOrDefault(key, fallback);
	}

	public synchronized Object set(String key, Object value) {
		if (key == null || key.isEmpty()) {
			return value;
		}
		if (values.size() >= MAX_ENTRIES && !values.containsKey(key)) {
			values.clear();
		}
		values.put(key, value);
		return value;
	}

	public synchronized boolean has(String key) {
		return key != null && values.containsKey(key);
	}

	public synchronized boolean remove(String key) {
		return key != null && values.remove(key) != null;
	}

	public synchronized void clear() {
		values.clear();
	}

	public synchronized List<Object> keys() {
		return new ArrayList<>(values.keySet());
	}

	public synchronized Object count() {
		return values.size();
	}
}
