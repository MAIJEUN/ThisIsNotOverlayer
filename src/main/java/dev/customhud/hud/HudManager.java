package dev.customhud.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.customhud.CustomHudClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/** The presets, one JSON file each in {@code config/customhud/presets}. */
public final class HudManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static HudManager instance;

	private final Path configRoot;
	private final Path dir;
	private final Path legacyFile;
	private final List<Preset> presets = new ArrayList<>();
	private final Set<Path> known = new HashSet<>();

	private HudManager(Path configRoot, Path legacyFile) {
		this.configRoot = configRoot;
		this.dir = configRoot.resolve("presets");
		this.legacyFile = legacyFile;
	}

	public static HudManager init(Path configRoot, Path legacyFile) {
		instance = new HudManager(configRoot, legacyFile);
		return instance;
	}

	public static HudManager get() {
		return instance;
	}

	public List<Preset> presets() {
		return presets;
	}

	/** Every object of every preset. */
	public void forEach(Consumer<HudObject> action) {
		for (Preset p : presets) {
			p.forEach(action);
		}
	}

	public Preset presetOf(HudObject o) {
		for (Preset p : presets) {
			if (p.contains(o)) {
				return p;
			}
		}
		return null;
	}

	/** The list an object lives in: its group's children, or its preset's top level. */
	public List<HudObject> siblings(HudObject o) {
		if (o.parent != null) {
			return o.parent.children;
		}
		Preset p = presetOf(o);
		return p != null ? p.objects : new ArrayList<>();
	}

	public Preset create(String name) {
		Preset p = new Preset(uniqueName(name));
		presets.add(p);
		save();
		return p;
	}

	public Preset duplicate(Preset source) {
		Preset p = source.copy();
		p.name = uniqueName(source.name);
		presets.add(presets.indexOf(source) + 1, p);
		save();
		return p;
	}

	public void delete(Preset p) {
		presets.remove(p);
		save();
	}

	public String uniqueName(String base) {
		Set<String> names = new HashSet<>();
		presets.forEach(p -> names.add(p.name));
		if (!names.contains(base)) {
			return base;
		}
		int i = 2;
		while (names.contains(base + " " + i)) {
			i++;
		}
		return base + " " + i;
	}

	// ---- files ----

	public void load() {
		presets.clear();
		known.clear();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
					known.add(f);
					try {
						Preset p = Preset.fromJson(JsonParser.parseString(Files.readString(f)).getAsJsonObject());
						p.file = f;
						presets.add(p);
					} catch (Exception e) {
						CustomHudClient.LOGGER.warn("Could not read preset {}", f, e);
					}
				}
			} catch (IOException e) {
				CustomHudClient.LOGGER.warn("Could not list presets", e);
			}
			return;
		}
		if (!migrateHudJson() && !migrateCanvases() && !migrateLegacy()) {
			Preset p = new Preset("Default");
			p.objects.add(defaultFps());
			presets.add(p);
		}
		save();
	}

	public void save() {
		try {
			Files.createDirectories(dir);
			Set<Path> used = new HashSet<>();
			int auto = 0;
			for (Preset p : presets) {
				Path f = p.file;
				if (f == null || used.contains(f)) {
					do {
						f = dir.resolve("preset" + auto++ + ".json");
					} while (used.contains(f) || (Files.exists(f) && !known.contains(f)));
				}
				used.add(f);
				Files.writeString(f, GSON.toJson(p.toJson()));
				p.file = f;
			}
			for (Path old : known) {
				if (!used.contains(old)) {
					Files.deleteIfExists(old);
				}
			}
			known.clear();
			known.addAll(used);
		} catch (IOException e) {
			CustomHudClient.LOGGER.warn("Could not save presets", e);
		}
	}

	public static HudObject defaultFps() {
		HudObject fps = new HudObject(HudObject.Type.TEXT, "FPS");
		fps.text.set("FPS: {Fps:0}");
		fps.x.set(4.0);
		fps.y.set(4.0);
		return fps;
	}

	// ---- migration from earlier versions ----

	/** The previous version kept one list of objects in {@code hud.json}: it becomes one preset. */
	private boolean migrateHudJson() {
		Path file = configRoot.resolve("hud.json");
		if (!Files.exists(file)) {
			return false;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			Preset p = new Preset("Default");
			for (JsonElement e : root.getAsJsonArray("objects")) {
				p.objects.add(HudObject.fromJson(e.getAsJsonObject()));
			}
			presets.add(p);
			Files.move(file, file.resolveSibling("hud.json.old"));
			return true;
		} catch (Exception e) {
			CustomHudClient.LOGGER.warn("Could not migrate {}", file, e);
			presets.clear();
			return false;
		}
	}

	/** The version before kept "canvases" with components; each becomes a preset. */
	private boolean migrateCanvases() {
		Path canvasDir = configRoot.resolve("canvases");
		if (!Files.isDirectory(canvasDir)) {
			return false;
		}
		try (Stream<Path> files = Files.list(canvasDir)) {
			for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
				JsonObject canvas = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
				Preset p = new Preset(canvas.has("name") ? canvas.get("name").getAsString() : "Canvas");
				if (canvas.has("enabled")) {
					p.enabled.fromJson(canvas.get("enabled"));
				}
				if (canvas.has("objects")) {
					for (JsonElement e : canvas.getAsJsonArray("objects")) {
						p.objects.add(fromCanvasObject(e.getAsJsonObject()));
					}
				}
				presets.add(p);
			}
			Files.move(canvasDir, canvasDir.resolveSibling("canvases.old"));
			return !presets.isEmpty();
		} catch (Exception e) {
			CustomHudClient.LOGGER.warn("Could not migrate {}", canvasDir, e);
			presets.clear();
			return false;
		}
	}

	private static HudObject fromCanvasObject(JsonObject o) {
		JsonObject comp = o.has("components") && !o.getAsJsonArray("components").isEmpty()
				? o.getAsJsonArray("components").get(0).getAsJsonObject() : null;
		String kind = comp == null ? "" : comp.get("type").getAsString();
		HudObject.Type type = switch (kind) {
			case "text" -> HudObject.Type.TEXT;
			case "item" -> HudObject.Type.ITEM;
			case "block" -> HudObject.Type.BLOCK;
			default -> HudObject.Type.GROUP;
		};
		HudObject obj = new HudObject(type, o.has("name") ? o.get("name").getAsString() : "");
		copy(o, "visible", obj.visible);
		copy(o, "anchor", obj.align);
		copy(o, "x", obj.x);
		copy(o, "y", obj.y);
		copy(o, "scale", obj.scale);
		copy(o, "rotation", obj.rotation);
		if (comp != null) {
			switch (type) {
				case TEXT -> {
					copy(comp, "text", obj.text);
					copy(comp, "shadow", obj.shadow);
					copy(comp, "background", obj.background);
					copy(comp, "background_color", obj.backgroundColor);
				}
				case ITEM -> {
					String source = comp.has("item_source") ? comp.get("item_source").getAsString().toLowerCase(Locale.ROOT) : "item";
					switch (source) {
						case "item" -> copy(comp, "item_id", obj.item);
						case "hotbar", "inventory" -> obj.item.setFx("SlotItem(\"" + (comp.has("slot") ? comp.get("slot").getAsInt() : 0) + "\")");
						default -> obj.item.setFx("SlotItem(\"" + source.replace("_", "") + "\")");
					}
				}
				case BLOCK -> {
					String source = comp.has("block_source") ? comp.get("block_source").getAsString().toLowerCase(Locale.ROOT) : "block";
					if (source.equals("looking_at")) {
						obj.block.setFx("TargetBlockId()");
					} else {
						copy(comp, "block_id", obj.block);
					}
				}
				default -> {
				}
			}
		}
		if (o.has("children")) {
			for (JsonElement e : o.getAsJsonArray("children")) {
				HudObject c = fromCanvasObject(e.getAsJsonObject());
				c.parent = obj;
				obj.children.add(c);
			}
		}
		return obj;
	}

	private static void copy(JsonObject from, String key, dev.customhud.prop.Prop<?> to) {
		if (from.has(key)) {
			try {
				to.fromJson(from.get(key));
			} catch (RuntimeException ignored) {
				to.reset();
			}
		}
	}

	/** The first version kept elements in {@code config/customhud.json}; its texts carry over. */
	private boolean migrateLegacy() {
		if (legacyFile == null || !Files.exists(legacyFile)) {
			return false;
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(legacyFile)).getAsJsonObject();
			int guiW = 480, guiH = 270;
			try {
				var window = net.minecraft.client.Minecraft.getInstance().getWindow();
				guiW = window.getGuiScaledWidth();
				guiH = window.getGuiScaledHeight();
			} catch (RuntimeException ignored) {
				// no window (tests)
			}
			Preset p = new Preset("Default");
			for (JsonElement e : root.getAsJsonArray("elements")) {
				JsonObject el = e.getAsJsonObject();
				if (!"text".equals(el.get("type").getAsString()) || !el.has("props")) {
					continue;
				}
				JsonElement text = el.getAsJsonObject("props").get("text");
				HudObject obj = new HudObject(HudObject.Type.TEXT, el.has("name") && !el.get("name").getAsString().isEmpty() ? el.get("name").getAsString() : "Text");
				obj.text.set(text.isJsonObject() ? text.getAsJsonObject().get("value").getAsString() : text.getAsString());
				obj.x.set((double) Math.round(el.get("x").getAsFloat() * guiW));
				obj.y.set((double) Math.round(el.get("y").getAsFloat() * guiH));
				p.objects.add(obj);
			}
			Files.move(legacyFile, legacyFile.resolveSibling(legacyFile.getFileName() + ".old"));
			if (p.objects.isEmpty()) {
				return false;
			}
			presets.add(p);
			return true;
		} catch (Exception e) {
			CustomHudClient.LOGGER.warn("Could not migrate {}", legacyFile, e);
			return false;
		}
	}
}
