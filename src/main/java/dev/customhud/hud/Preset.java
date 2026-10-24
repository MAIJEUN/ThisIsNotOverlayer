package dev.customhud.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.customhud.prop.BoolProp;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A preset (Overlayer's "canvas"): a named set of objects that can be switched on and off as a whole. */
public final class Preset {
	public String name;
	/** On / off; with Fx it can follow a condition such as {@code IsInWorld()}. */
	public final BoolProp enabled = new BoolProp("enabled", true);
	public final List<HudObject> objects = new ArrayList<>();
	Path file;

	public Preset(String name) {
		this.name = name;
	}

	public void forEach(Consumer<HudObject> action) {
		for (HudObject o : objects) {
			walk(o, action);
		}
	}

	private static void walk(HudObject o, Consumer<HudObject> action) {
		action.accept(o);
		for (HudObject c : o.children) {
			walk(c, action);
		}
	}

	public boolean contains(HudObject o) {
		HudObject top = o;
		while (top.parent != null) {
			top = top.parent;
		}
		return objects.contains(top);
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("name", name);
		o.add("enabled", enabled.toJson());
		JsonArray arr = new JsonArray();
		objects.forEach(obj -> arr.add(obj.toJson()));
		o.add("objects", arr);
		return o;
	}

	public static Preset fromJson(JsonObject o) {
		Preset p = new Preset(o.has("name") ? o.get("name").getAsString() : "Preset");
		if (o.has("enabled")) {
			p.enabled.fromJson(o.get("enabled"));
		}
		if (o.has("objects")) {
			for (JsonElement e : o.getAsJsonArray("objects")) {
				p.objects.add(HudObject.fromJson(e.getAsJsonObject()));
			}
		}
		return p;
	}

	public Preset copy() {
		Preset p = fromJson(toJson());
		p.file = null;
		return p;
	}
}
