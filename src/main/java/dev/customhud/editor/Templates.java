package dev.customhud.editor;

import dev.customhud.hud.Anchor;
import dev.customhud.hud.HudObject;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * What "+ Add" offers: each object type, and ready-made HUD parts built from them, tags and Fx; and
 * complete HUDs (several parts already placed) for a new preset. Nothing here is a special feature —
 * every part is ordinary objects the user can open and change.
 */
public final class Templates {
	private Templates() {
	}

	public enum Group { BASIC, INFO, PLAYER, COMBAT, WORLD }

	public record Template(String id, Group group, String preview, Supplier<HudObject> factory) {
		public Component name() {
			return Component.translatable("template.customhud." + id);
		}

		public HudObject create() {
			return factory.get();
		}
	}

	/** A whole HUD: the objects of a new preset, already placed around the screen. */
	public record Layout(String id, String preview, Supplier<List<HudObject>> factory) {
		public Component name() {
			return Component.translatable("layout.customhud." + id);
		}

		public List<HudObject> create() {
			return factory.get();
		}
	}

	private static final int BG = 0x70000000;
	private static final String GRAY = "AAAAAA";

	public static final List<Template> ALL = List.of(
			new Template("text", Group.BASIC, "", () -> new HudObject(HudObject.Type.TEXT, "")),
			new Template("item", Group.BASIC, "", () -> new HudObject(HudObject.Type.ITEM, "")),
			new Template("block", Group.BASIC, "", () -> new HudObject(HudObject.Type.BLOCK, "")),
			new Template("entity", Group.BASIC, "", () -> new HudObject(HudObject.Type.ENTITY, "")),
			new Template("group", Group.BASIC, "", () -> new HudObject(HudObject.Type.GROUP, "")),
			// info
			new Template("fps", Group.INFO, "144 FPS", Templates::fps),
			new Template("ping", Group.INFO, "Ping 42ms", Templates::ping),
			new Template("performance", Group.INFO, "FPS · ms · Mem", Templates::performance),
			new Template("clock", Group.INFO, "21:37:05", Templates::clock),
			new Template("date", Group.INFO, "2026.10.19 Monday", Templates::date),
			new Template("session", Group.INFO, "⌚ 1:23:45", Templates::session),
			new Template("server", Group.INFO, "Server · 12", Templates::server),
			// player
			new Template("coordinates", Group.PLAYER, "X 12 Y 64 Z -30", Templates::coordinates),
			new Template("nether_coords", Group.PLAYER, "Nether 1 -4", Templates::netherCoords),
			new Template("compass", Group.PLAYER, "↑ North 180°", Templates::compass),
			new Template("speed", Group.PLAYER, "5.61 b/s", Templates::speed),
			new Template("health", Group.PLAYER, "❤ 20/20", Templates::health),
			new Template("hunger", Group.PLAYER, "20 5.0", Templates::hunger),
			new Template("xp", Group.PLAYER, "Lv 30 45%", Templates::xp),
			new Template("vitals", Group.PLAYER, "❤ · food · armour · XP", Templates::vitals),
			new Template("effects", Group.PLAYER, "Speed II 1:23", Templates::effects),
			new Template("air", Group.PLAYER, "○ 7/10", Templates::air),
			new Template("paper_doll", Group.PLAYER, "Steve", Templates::paperDoll),
			// combat and input
			new Template("keystrokes", Group.COMBAT, "W A S D", Templates::keystrokes),
			new Template("cps", Group.COMBAT, "12 | 3 CPS", Templates::cps),
			new Template("armor", Group.COMBAT, "◆ 100% …", Templates::armor),
			new Template("held_item", Group.COMBAT, "◆ 1561/1561", Templates::heldItem),
			new Template("offhand", Group.COMBAT, "◆ ×16", Templates::offhand),
			new Template("totems", Group.COMBAT, "◆ 3", () -> counter("totems", "totem_of_undying")),
			new Template("arrows", Group.COMBAT, "◆ 64", () -> counter("arrows", "arrow")),
			new Template("target", Group.COMBAT, "⚔ Zombie", Templates::target),
			// world
			new Template("biome", Group.WORLD, "Plains", Templates::biome),
			new Template("world_time", Group.WORLD, "☀ 08:30 · Day 3", Templates::worldTime),
			new Template("light", Group.WORLD, "Light 12", Templates::light),
			new Template("looking_at", Group.WORLD, "◆ Stone 12, 64, -3", Templates::lookingAt),
			new Template("chunk", Group.WORLD, "Chunk 0 -2 (5, 11)", Templates::chunk),
			new Template("showcase", Group.WORLD, "■ ↻", Templates::showcase));

	public static final List<Layout> LAYOUTS = List.of(
			new Layout("survival", "coordinates · armour · effects · held item", Templates::survivalHud),
			new Layout("pvp", "keystrokes · CPS · armour · totems", Templates::pvpHud),
			new Layout("speedrun", "timer · coordinates · nether · speed", Templates::speedrunHud),
			new Layout("builder", "looking at · compass · chunk · light", Templates::builderHud),
			new Layout("minimal", "FPS · XYZ · clock", Templates::minimalHud),
			new Layout("streamer", "clock · session · keystrokes · you", Templates::streamerHud));

	public static Template byId(String id) {
		return ALL.stream().filter(t -> t.id().equals(id)).findFirst().orElseThrow();
	}

	// ---- building blocks ----

	/** A label in the player's language, written into the template when it is made. */
	private static String label(String key) {
		return Component.translatable("label.customhud.hud." + key).getString();
	}

	private static String name(String templateId) {
		return Component.translatable("template.customhud." + templateId).getString();
	}

	private static String color(String hex, String s) {
		return "<color=#" + hex + ">" + s + "</color>";
	}

	private static String gray(String s) {
		return color(GRAY, s);
	}

	/** JS that picks green / yellow / red for a percentage. */
	private static String jsTint(String percent) {
		return "(" + percent + " > 50 ? \"55FF55\" : " + percent + " > 20 ? \"FFFF55\" : \"FF5555\")";
	}

	private static HudObject text(String name, String template) {
		HudObject o = new HudObject(HudObject.Type.TEXT, name);
		o.text.set(template);
		return o;
	}

	private static HudObject item(String name, String id) {
		HudObject o = new HudObject(HudObject.Type.ITEM, name);
		o.item.set("minecraft:" + id);
		return o;
	}

	private static HudObject slotItem(String name, String slot) {
		HudObject o = new HudObject(HudObject.Type.ITEM, name);
		o.item.setFx("SlotItem(\"" + slot + "\")");
		return o;
	}

	private static HudObject group(String name) {
		return new HudObject(HudObject.Type.GROUP, name);
	}

	/** A dark, see-through box behind the object (or around a group's contents). */
	private static HudObject boxed(HudObject o) {
		o.background.set(true);
		o.backgroundColor.set(BG);
		o.padding.set(3.0);
		return o;
	}

	private static HudObject child(HudObject group, HudObject o, double x, double y) {
		o.x.set(x);
		o.y.set(y);
		o.parent = group;
		group.children.add(o);
		return o;
	}

	private static HudObject child(HudObject group, HudObject o, Anchor align, double x, double y) {
		o.align.set(align);
		return child(group, o, x, y);
	}

	private static HudObject at(HudObject o, Anchor align, double x, double y) {
		o.align.set(align);
		o.x.set(x);
		o.y.set(y);
		return o;
	}

	/** Flips a part built for the left side of the screen so it reads from the right edge. */
	private static HudObject mirror(HudObject o) {
		for (HudObject c : o.children) {
			c.x.set(-c.x.fixedValue());
			c.align.set(flip(c.align.fixedValue()));
			mirror(c);
		}
		return o;
	}

	private static Anchor flip(Anchor a) {
		return switch (a) {
			case TOP_LEFT -> Anchor.TOP_RIGHT;
			case TOP_RIGHT -> Anchor.TOP_LEFT;
			case LEFT -> Anchor.RIGHT;
			case RIGHT -> Anchor.LEFT;
			case BOTTOM_LEFT -> Anchor.BOTTOM_RIGHT;
			case BOTTOM_RIGHT -> Anchor.BOTTOM_LEFT;
			default -> a;
		};
	}

	/** An icon with a text to its right, centred on it. */
	private static void iconRow(HudObject g, HudObject icon, HudObject text, double y) {
		child(g, icon, 0, y);
		child(g, text, Anchor.LEFT, 19, y + 8);
	}

	// ---- info ----

	private static HudObject fps() {
		return boxed(text(name("fps"), color("{ColorRange:Fps,30,120,FF5555,55FF55}", "{Fps:500,0}") + " " + gray("FPS")));
	}

	private static HudObject ping() {
		HudObject o = boxed(text(name("ping"), gray(label("ping")) + " "
				+ color("{ColorRange:Ping,40,250,55FF55,FF5555}", "{Ping}") + gray("ms")));
		o.visible.setFx("!IsSingleplayer()");
		return o;
	}

	private static HudObject performance() {
		return boxed(text(name("performance"),
				color("{ColorRange:Fps,30,120,FF5555,55FF55}", "{Fps:500,0}") + " " + gray("FPS") + "  {FrameTime:500,0.0}" + gray("ms") + "\n"
						+ gray(label("memory")) + " {MemPercent:0}% " + gray("{MemUsed:0} / {MemMax:0} MB")));
	}

	private static HudObject clock() {
		return boxed(text(name("clock"), "{Hour:00}:{Minute:00}" + gray(":{Second:00}")));
	}

	private static HudObject date() {
		return boxed(text(name("date"), "{Year}.{Month:00}.{Day:00} " + gray("{DayOfWeek}")));
	}

	private static HudObject session() {
		return boxed(text(name("session"), gray("⌚") + " {SessionTime}"));
	}

	private static HudObject server() {
		return boxed(text(name("server"), "{ServerName}\n" + gray("{PlayerCount} " + label("online") + " · {Ping}ms")));
	}

	// ---- player ----

	private static HudObject coordinates() {
		return boxed(text(name("coordinates"),
				color("FF7777", "X") + " {BlockX}  " + color("77FF77", "Y") + " {BlockY}  " + color("7799FF", "Z") + " {BlockZ}\n"
						+ gray("{Facing} ({Axis})")));
	}

	private static HudObject netherCoords() {
		HudObject o = boxed(text(name("nether_coords"),
				"{JSExpr:Dimension() == \"the_nether\" ? \"<color=#77FF77>" + label("overworld") + "</color>\" : \"<color=#FF7755>"
						+ label("nether") + "</color>\"} {OtherX:0} {OtherZ:0}"));
		o.visible.setFx("Dimension() != \"the_end\"");
		return o;
	}

	private static HudObject compass() {
		return boxed(text(name("compass"),
				color("FFFF55", "{JSExpr:[\"↓\", \"↙\", \"←\", \"↖\", \"↑\", \"↗\", \"→\", \"↘\"][((Math.round(Yaw() / 45) % 8) + 8) % 8]}")
						+ " {Facing} " + gray("{Yaw:0}°")));
	}

	private static HudObject speed() {
		return boxed(text(name("speed"),
				"{JSExpr:IsSprinting() ? \"<color=#FFFF55>\" : \"<color=#FFFFFF>\"}{Speed:0.00}</color> " + gray("b/s")));
	}

	private static HudObject health() {
		return boxed(text(name("health"), color("FF5555", "❤") + " " + color("{ColorRange:Health,4,20,FF5555,FFFFFF}", "{Health:0}")
				+ gray("/{MaxHealth:0}")
				+ "{JSExpr:Absorption() > 0 ? \" <color=#FFAA00>+\" + Absorption().toFixed(0) + \"</color>\" : \"\"}"));
	}

	private static HudObject hunger() {
		HudObject g = boxed(group(name("hunger")));
		iconRow(g, item("Icon", "cooked_beef"), text("Food", "{Food} " + gray("{Saturation:0.0}")), 0);
		return g;
	}

	private static HudObject xp() {
		HudObject g = boxed(group(name("xp")));
		iconRow(g, item("Icon", "experience_bottle"),
				text("Level", color("80FF20", "{XpLevel}") + " " + gray("{JSExpr:(XpProgress() * 100).toFixed(0)}%")), 0);
		return g;
	}

	private static HudObject vitals() {
		HudObject g = boxed(group(name("vitals")));
		child(g, text("Heart", color("FF5555", "❤")), Anchor.CENTER, 8, 8);
		child(g, text("Health", color("{ColorRange:Health,4,20,FF5555,FFFFFF}", "{Health:0}") + gray("/{MaxHealth:0}")
				+ "{JSExpr:Absorption() > 0 ? \" <color=#FFAA00>+\" + Absorption().toFixed(0) + \"</color>\" : \"\"}"), Anchor.LEFT, 19, 8);
		iconRow(g, item("Food Icon", "cooked_beef"), text("Food", "{Food} " + gray("{Saturation:0.0}")), 17);
		HudObject armorIcon = item("Armor Icon", "iron_chestplate");
		HudObject armor = text("Armor", "{ArmorPoints}");
		iconRow(g, armorIcon, armor, 34);
		armorIcon.visible.setFx("ArmorPoints() > 0");
		armor.visible.setFx("ArmorPoints() > 0");
		iconRow(g, item("XP Icon", "experience_bottle"),
				text("Level", color("80FF20", "{XpLevel}") + " " + gray("{JSExpr:(XpProgress() * 100).toFixed(0)}%")), 51);
		return g;
	}

	private static HudObject effects() {
		return boxed(text(name("effects"), "{Effects}"));
	}

	private static HudObject air() {
		HudObject o = boxed(text(name("air"), color("55AAFF", "○") + " {JSExpr:Math.max(0, Math.ceil(Air() / 30))}" + gray("/10")));
		o.visible.setFx("Air() < MaxAir()");
		return o;
	}

	private static HudObject paperDoll() {
		// Centred on the group's position point, so a long name grows to both sides.
		HudObject g = boxed(group(name("paper_doll")));
		HudObject you = child(g, new HudObject(HudObject.Type.ENTITY, "You"), Anchor.TOP, 0, 0);
		you.rotY.set(-25.0);
		child(g, text("Name", "{PlayerName}"), Anchor.BOTTOM, 0, -3);
		return g;
	}

	// ---- combat and input ----

	/** A key that lights up while its binding is held. */
	private static HudObject key(String bind, String label, double cx, double cy) {
		String held = "IsBindHeld(\"" + bind + "\")";
		HudObject k = text(label, "{JSExpr:" + held + " ? \"<color=#000000>\" : \"<color=#FFFFFF>\"}" + label + "</color>");
		k.align.set(Anchor.CENTER);
		k.x.set(cx);
		k.y.set(cy);
		k.background.set(true);
		k.backgroundColor.setFx(held + " ? \"#FFFFFFC0\" : \"#00000080\"");
		k.padding.set(5.0);
		k.shadow.setFx("!" + held);
		return k;
	}

	private static HudObject keystrokes() {
		HudObject g = group(name("keystrokes"));
		for (HudObject k : List.of(
				key("forward", "{BindKey:forward}", 33, 10),
				key("left", "{BindKey:left}", 11, 32), key("back", "{BindKey:back}", 33, 32), key("right", "{BindKey:right}", 55, 32),
				key("attack", "LMB", 16, 54), key("use", "RMB", 50, 54),
				key("jump", "━━━━━", 33, 75))) {
			k.parent = g;
			g.children.add(k);
		}
		child(g, text("CPS", gray("{Cps} | {RightCps} CPS")), Anchor.TOP, 33, 88);
		return g;
	}

	private static HudObject cps() {
		return boxed(text(name("cps"), "{Cps} " + gray("|") + " {RightCps} " + gray("CPS")));
	}

	private static HudObject armor() {
		HudObject g = boxed(group(name("armor")));
		String[] slots = {"head", "chest", "legs", "feet"};
		for (int i = 0; i < slots.length; i++) {
			String s = "\"" + slots[i] + "\"";
			String pct = "ArmorDurability(" + s + ")";
			iconRow(g, slotItem(slots[i], slots[i]), text(slots[i] + " %",
					"{JSExpr:SlotItem(" + s + ") && " + pct + " > 0 ? \"<color=#\" + " + jsTint(pct) + " + \">\" + " + pct
							+ ".toFixed(0) + \"%</color>\" : \"\"}"), i * 17);
		}
		return g;
	}

	/** The item in a hand with its name, and its durability (tools) or count (stacks) under it. */
	private static HudObject hand(String id, String slot, String prefix) {
		HudObject g = boxed(group(name(id)));
		String info = "{JSExpr:" + prefix + "MaxDurability() > 0 ? \"<color=#\" + " + jsTint(prefix + "DurabilityPercent()") + " + \">\" + "
				+ prefix + "Durability() + \"</color><color=#AAAAAA>/\" + " + prefix + "MaxDurability() + \"</color>\" : " + prefix
				+ "Count() > 1 ? \"<color=#AAAAAA>×\" + " + prefix + "Count() + \"</color>\" : \"\"}";
		iconRow(g, slotItem("Item", slot), text("Name", "{" + prefix + "Item}\n" + info), 0);
		return g;
	}

	private static HudObject heldItem() {
		return hand("held_item", "mainhand", "Held");
	}

	private static HudObject offhand() {
		return hand("offhand", "offhand", "Offhand");
	}

	/** How many of an item you carry, shown only when you have some. */
	private static HudObject counter(String templateId, String itemId) {
		HudObject g = boxed(group(name(templateId)));
		iconRow(g, item("Icon", itemId), text("Count", "{ItemCount:" + itemId + "}"), 0);
		g.visible.setFx("ItemCount(\"" + itemId + "\") > 0");
		return g;
	}

	private static HudObject target() {
		HudObject o = boxed(text(name("target"), color("FF5555", "⚔") + " {TargetEntity}"));
		o.visible.setFx("TargetEntity() != \"\"");
		return o;
	}

	// ---- world ----

	private static HudObject biome() {
		return boxed(text(name("biome"), gray(label("biome")) + " {Biome}"));
	}

	private static HudObject worldTime() {
		return boxed(text(name("world_time"),
				"{JSExpr:IsDayTime() ? \"<color=#FFDD55>☀</color>\" : \"<color=#AACCFF>☾</color>\"} {WorldTime} "
						+ gray("· " + label("day") + " {WorldDay}")
						+ "{JSExpr:IsThundering() ? \" <color=#FFFF55>⚡</color>\" : IsRaining() ? \" <color=#55AAFF>☂</color>\" : \"\"}"));
	}

	private static HudObject light() {
		return boxed(text(name("light"), gray(label("light")) + " " + color("{ColorRange:Light,0,8,FF5555,FFFFFF}", "{Light}")));
	}

	private static HudObject lookingAt() {
		HudObject g = boxed(group(name("looking_at")));
		HudObject block = new HudObject(HudObject.Type.BLOCK, "Block");
		block.block.setFx("TargetBlockId()");
		iconRow(g, block, text("Name", "{TargetBlock}\n" + gray("{TargetPos}")), 0);
		g.visible.setFx("TargetBlockId() != \"\"");
		return g;
	}

	private static HudObject chunk() {
		return boxed(text(name("chunk"), gray(label("chunk")) + " {ChunkX} {ChunkZ} "
				+ gray("({JSExpr:((BlockX() % 16) + 16) % 16}, {JSExpr:((BlockZ() % 16) + 16) % 16})")));
	}

	private static HudObject showcase() {
		HudObject o = new HudObject(HudObject.Type.BLOCK, name("showcase"));
		o.block.set("minecraft:grass_block");
		o.scale.set(2.0);
		o.rotY.setFx("Time() * 40 % 360");
		return o;
	}

	// ---- complete HUDs ----
	// The top-right corner belongs to Minecraft's own effect icons (two rows, about 52 high) and toasts,
	// so parts there start below it.

	private static final double BELOW_ICONS = 54;

	private static List<HudObject> survivalHud() {
		List<HudObject> l = new ArrayList<>();
		l.add(at(coordinates(), Anchor.TOP_LEFT, 4, 4));
		l.add(at(biome(), Anchor.TOP_LEFT, 4, 31));
		l.add(at(worldTime(), Anchor.TOP_LEFT, 4, 48));
		l.add(at(effects(), Anchor.TOP_RIGHT, -4, BELOW_ICONS));
		l.add(at(mirror(armor()), Anchor.RIGHT, -7, -32));
		l.add(at(heldItem(), Anchor.BOTTOM, 100, -26));
		l.add(at(fps(), Anchor.BOTTOM_LEFT, 4, -4));
		return l;
	}

	private static List<HudObject> pvpHud() {
		List<HudObject> l = new ArrayList<>();
		l.add(at(keystrokes(), Anchor.LEFT, 6, -50));
		l.add(at(fps(), Anchor.TOP_LEFT, 4, 4));
		l.add(at(ping(), Anchor.TOP_LEFT, 4, 21));
		l.add(at(target(), Anchor.TOP, 0, 4));
		l.add(at(mirror(armor()), Anchor.RIGHT, -7, -32));
		l.add(at(counter("totems", "totem_of_undying"), Anchor.BOTTOM, 100, -26));
		l.add(at(counter("arrows", "arrow"), Anchor.BOTTOM, 145, -26));
		l.add(at(effects(), Anchor.TOP_RIGHT, -4, BELOW_ICONS));
		return l;
	}

	private static List<HudObject> speedrunHud() {
		List<HudObject> l = new ArrayList<>();
		HudObject timer = boxed(text(name("session"), "<b>{SessionTime}</b>"));
		timer.scale.set(2.0);
		l.add(at(timer, Anchor.TOP_LEFT, 4, 4));
		l.add(at(coordinates(), Anchor.TOP_LEFT, 4, 38));
		l.add(at(netherCoords(), Anchor.TOP_LEFT, 4, 65));
		l.add(at(speed(), Anchor.TOP_LEFT, 4, 82));
		l.add(at(worldTime(), Anchor.TOP_LEFT, 4, 99));
		l.add(at(effects(), Anchor.TOP_RIGHT, -4, BELOW_ICONS));
		return l;
	}

	private static List<HudObject> builderHud() {
		List<HudObject> l = new ArrayList<>();
		l.add(at(coordinates(), Anchor.TOP_LEFT, 4, 4));
		l.add(at(compass(), Anchor.TOP_LEFT, 4, 31));
		l.add(at(chunk(), Anchor.TOP_LEFT, 4, 48));
		l.add(at(light(), Anchor.TOP_LEFT, 4, 65));
		l.add(at(lookingAt(), Anchor.TOP, -40, 4));
		l.add(at(heldItem(), Anchor.BOTTOM, 100, -26));
		return l;
	}

	private static List<HudObject> minimalHud() {
		List<HudObject> l = new ArrayList<>();
		l.add(at(text(name("fps"), "{Fps:500,0} " + gray("FPS") + "  " + gray("XYZ") + " {BlockX} {BlockY} {BlockZ}"), Anchor.TOP_LEFT, 4, 4));
		l.add(at(text(name("clock"), "{Hour:00}:{Minute:00}"), Anchor.TOP_RIGHT, -4, 4));
		return l;
	}

	private static List<HudObject> streamerHud() {
		List<HudObject> l = new ArrayList<>();
		l.add(at(clock(), Anchor.BOTTOM_RIGHT, -4, -38));
		l.add(at(date(), Anchor.BOTTOM_RIGHT, -4, -21));
		l.add(at(session(), Anchor.BOTTOM_RIGHT, -4, -4));
		l.add(at(server(), Anchor.TOP_LEFT, 4, 4));
		HudObject doll = paperDoll();
		doll.scale.set(1.5);
		l.add(at(doll, Anchor.LEFT, 40, -40));
		l.add(at(keystrokes(), Anchor.BOTTOM_LEFT, 6, -102));
		return l;
	}
}
