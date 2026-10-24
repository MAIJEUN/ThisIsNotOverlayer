package dev.customhud.js;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/** {@code Prefs}: like Store but saved to Script/prefs.json so script settings survive restarts. */
public final class PrefsStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private final Path path;
	private JsonObject values;

	public PrefsStore(Path path) {
		this.path = path;
	}

	public synchronized Object get(String key, Object fallback) {
		if (key == null || key.isEmpty()) {
			return fallback;
		}
		JsonElement e = values().get(key);
		if (e == null || !e.isJsonPrimitive()) {
			return fallback;
		}
		JsonPrimitive p = e.getAsJsonPrimitive();
		if (p.isBoolean()) {
			return p.getAsBoolean();
		}
		if (p.isNumber()) {
			return p.getAsDouble();
		}
		return p.getAsString();
	}

	public synchronized Object set(String key, Object value) {
		if (key == null || key.isEmpty()) {
			return value;
		}
		JsonElement e;
		if (value == null) {
			e = JsonNull.INSTANCE;
		} else if (value instanceof Boolean b) {
			e = new JsonPrimitive(b);
		} else if (value instanceof Number n) {
			e = new JsonPrimitive(n);
		} else {
			e = new JsonPrimitive(value.toString());
		}
		values().add(key, e);
		save();
		return value;
	}

	public synchronized boolean remove(String key) {
		if (key == null || values().remove(key) == null) {
			return false;
		}
		save();
		return true;
	}

	private JsonObject values() {
		if (values != null) {
			return values;
		}
		values = new JsonObject();
		if (path != null && Files.exists(path)) {
			try {
				values = GSON.fromJson(Files.readString(path), JsonObject.class);
			} catch (Exception e) {
				LoggerFactory.getLogger("customhud/js").warn("[Prefs] Failed to read {}: {}", path, e.getMessage());
			}
		}
		return values;
	}

	private void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(values));
		} catch (Exception e) {
			LoggerFactory.getLogger("customhud/js").warn("[Prefs] Failed to save {}: {}", path, e.getMessage());
		}
	}
}
