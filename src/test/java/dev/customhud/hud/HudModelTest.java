package dev.customhud.hud;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import com.google.gson.JsonObject;
import dev.customhud.js.JsEngine;
import dev.customhud.prop.Colors;
import dev.customhud.prop.NumberProp;
import dev.customhud.prop.Prop;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Objects: placement, groups, JSON, migration from earlier versions, rich text and colours. */
class HudModelTest {
	@BeforeEach
	void setUp() {
		TagManager.clear();
		TagManager.register(TagCore.java("Health", null, 0, List.of(), Float.class, a -> 10f));
		JsEngine.init(null);
	}

	private static Vector2f at(HudObject o, float pw, float ph, float lx, float ly) {
		Matrix3x2f m = new Matrix3x2f();
		o.place(m, pw, ph);
		return m.transformPosition(lx, ly, new Vector2f());
	}

	@Test
	void alignmentIsBothTheScreenPointAndThePivot() {
		HudObject o = new HudObject(HudObject.Type.TEXT, "t");
		o.align.set(Anchor.BOTTOM_RIGHT);
		o.x.set(-4.0);
		o.y.set(-6.0);
		Vector2f p = at(o, 400, 300, 0, 0);
		assertEquals(396, p.x, 1e-4);
		assertEquals(294, p.y, 1e-4);
	}

	@Test
	void scaleAndRotationTurnAroundThePosition() {
		HudObject o = new HudObject(HudObject.Type.ITEM, "i");
		o.x.set(100.0);
		o.y.set(50.0);
		o.scale.set(2.0);
		o.rotation.set(90.0);
		Vector2f p = at(o, 400, 300, 10, 0);
		assertEquals(100, p.x, 1e-3);
		assertEquals(70, p.y, 1e-3);
	}

	@Test
	void childrenArePlacedFromTheirGroup() {
		HudObject g = new HudObject(HudObject.Type.GROUP, "g");
		g.align.set(Anchor.TOP_RIGHT);
		g.x.set(-50.0);
		g.y.set(10.0);
		g.scale.set(2.0);
		HudObject c = new HudObject(HudObject.Type.TEXT, "c");
		c.align.set(Anchor.BOTTOM_RIGHT);
		c.x.set(5.0);
		c.y.set(5.0);
		Matrix3x2f m = new Matrix3x2f();
		g.place(m, 400, 300);
		c.place(m, 0, 0);
		Vector2f p = m.transformPosition(0, 0, new Vector2f());
		// The group's alignment does not stretch its children: inside a group the parent box is its position point.
		assertEquals(350 + 10, p.x, 1e-3);
		assertEquals(10 + 10, p.y, 1e-3);
	}

	@Test
	void jsonKeepsTypesFxAndGroups() {
		HudObject g = new HudObject(HudObject.Type.GROUP, "Armor");
		HudObject item = new HudObject(HudObject.Type.ITEM, "Head");
		item.item.setFx("SlotItem(\"head\")");
		item.y.set(17.0);
		item.parent = g;
		g.children.add(item);
		HudObject text = new HudObject(HudObject.Type.TEXT, "t");
		text.text.set("<color=#FF5555>{Health}</color>");
		text.background.set(true);
		text.parent = g;
		g.children.add(text);

		JsonObject json = g.toJson();
		HudObject back = HudObject.fromJson(json);
		assertEquals(HudObject.Type.GROUP, back.type);
		assertEquals(2, back.children.size());
		HudObject i2 = back.children.get(0);
		assertSame(back, i2.parent);
		assertEquals(HudObject.Type.ITEM, i2.type);
		assertEquals("SlotItem(\"head\")", i2.item.fxSource());
		assertEquals(17.0, i2.y.fixedValue());
		assertTrue(back.children.get(1).background.fixedValue());
		assertFalse(json.getAsJsonArray("children").get(0).getAsJsonObject().has("scale"), "defaults are not written");
	}

	@Test
	void contentSettingsAreOnlyWhatEachTypeNeeds() {
		assertEquals(List.of("text", "shadow"),
				new HudObject(HudObject.Type.TEXT, "").contentProps().stream().map(Prop::id).toList());
		assertEquals(List.of("item"), new HudObject(HudObject.Type.ITEM, "").contentProps().stream().map(Prop::id).toList());
		HudObject block = new HudObject(HudObject.Type.BLOCK, "");
		assertEquals(List.of("block", "rot_x", "rot_y", "rot_z"), block.contentProps().stream().map(Prop::id).toList());
		assertEquals(30.0, block.rotX.fixedValue(), "blocks start at the inventory angle");
		assertEquals(225.0, block.rotY.fixedValue());
		HudObject entity = new HudObject(HudObject.Type.ENTITY, "");
		assertEquals(List.of("entity", "rot_x", "rot_y", "rot_z"), entity.contentProps().stream().map(Prop::id).toList());
		assertEquals("player", entity.entity.fixedValue());
		for (HudObject.Type t : HudObject.Type.values()) {
			assertEquals(List.of("background", "background_color", "outline", "outline_color", "padding"),
					new HudObject(t, "").boxProps().stream().map(Prop::id).toList(), "every type has a box: " + t);
		}
		assertTrue(new HudObject(HudObject.Type.GROUP, "").contentProps().isEmpty());
		assertEquals(List.of("align", "x", "y", "scale", "rotation"),
				new HudObject(HudObject.Type.GROUP, "").transformProps().stream().map(Prop::id).toList());
	}

	@Test
	void richTextUsesOverlayerTagsAndAmpersandCodes() {
		List<Component> lines = RichText.lines("<color=#FF5555>red</color> <b>bold</b> &aGreen<br>next <unknown>");
		assertEquals(2, lines.size());
		assertEquals("red bold Green", lines.get(0).getString());
		assertEquals("next <unknown>", lines.get(1).getString(), "unknown tags stay as text");
		List<TextColor> colors = new ArrayList<>();
		List<Boolean> bold = new ArrayList<>();
		lines.get(0).visit((style, s) -> {
			if (!s.isBlank()) {
				colors.add(style.getColor());
				bold.add(style.isBold());
			}
			return java.util.Optional.empty();
		}, net.minecraft.network.chat.Style.EMPTY);
		assertEquals(0xFF5555, colors.get(0).getValue());
		assertNull(colors.get(1));
		assertTrue(bold.get(1));
		assertEquals(0x55FF55, colors.get(2).getValue());
		assertEquals(0xFFFF0000, Colors.parse("red"), "TextMeshPro colour names, as in Overlayer");
		assertEquals(0xFFFFAA00, Colors.parse("gold"), "and Minecraft's");
	}

	@Test
	void fxIsEvaluatedOncePerFrame() {
		int[] calls = {0};
		TagManager.register(TagCore.java("Count", null, 0, List.of(), Integer.class, a -> ++calls[0]));
		NumberProp n = new NumberProp("n", 0, 0, 100, 1);
		n.setFx("Count()");
		Prop.nextFrame();
		n.get();
		n.get();
		assertEquals(1, calls[0]);
		Prop.nextFrame();
		n.get();
		assertEquals(2, calls[0]);
	}

	@Test
	void freshInstallHasADefaultPresetWithAnFpsText(@TempDir Path dir) {
		HudManager m = HudManager.init(dir, null);
		m.load();
		assertEquals(1, m.presets().size());
		Preset p = m.presets().getFirst();
		assertEquals("Default", p.name);
		assertEquals("FPS: {Fps:0}", p.objects.getFirst().text.fixedValue());
		assertTrue(Files.exists(dir.resolve("presets/preset0.json")));
		HudManager again = HudManager.init(dir, null);
		again.load();
		assertEquals("FPS", again.presets().getFirst().objects.getFirst().name);
	}

	@Test
	void presetsAreSavedOnePerFileAndDeletedFilesGo(@TempDir Path dir) {
		HudManager m = HudManager.init(dir, null);
		m.load();
		Preset second = m.create("Default");
		assertEquals("Default 2", second.name, "names stay unique");
		second.enabled.set(false);
		second.objects.add(new HudObject(HudObject.Type.ITEM, "Sword"));
		m.save();
		assertTrue(Files.exists(dir.resolve("presets/preset1.json")));
		HudManager again = HudManager.init(dir, null);
		again.load();
		assertEquals(2, again.presets().size());
		assertFalse(again.presets().get(1).enabled.fixedValue());
		assertEquals(again.presets().get(1), again.presetOf(again.presets().get(1).objects.getFirst()));
		again.delete(again.presets().getFirst());
		assertFalse(Files.exists(dir.resolve("presets/preset0.json")));
		Preset copy = again.duplicate(again.presets().getFirst());
		assertEquals("Sword", copy.objects.getFirst().name);
		assertNotSame(copy.objects.getFirst(), again.presets().getFirst().objects.getFirst());
	}

	@Test
	void migratesHudJsonIntoOnePreset(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("hud.json"), """
				{"objects": [{"type": "TEXT", "name": "Hi", "text": "Hello"}]}""");
		HudManager m = HudManager.init(dir, null);
		m.load();
		assertEquals(1, m.presets().size());
		assertEquals("Hello", m.presets().getFirst().objects.getFirst().text.fixedValue());
		assertTrue(Files.exists(dir.resolve("hud.json.old")));
	}

	@Test
	void migratesCanvasesIntoPresets(@TempDir Path dir) throws Exception {
		Path canvases = Files.createDirectories(dir.resolve("canvases"));
		Files.writeString(canvases.resolve("canvas0.json"), """
				{"name": "Main", "enabled": true, "objects": [
				  {"name": "Hello", "anchor": "TOP", "x": 3, "components": [{"type": "text", "text": "Hi {Health}", "background": true}]},
				  {"name": "Helmet", "components": [{"type": "item", "item_source": "HEAD"}]},
				  {"name": "Look", "components": [{"type": "block", "block_source": "LOOKING_AT"}]},
				  {"name": "Bar", "components": [{"type": "bar"}]}
				]}""");
		Files.writeString(canvases.resolve("canvas1.json"), """
				{"name": "Off", "enabled": false, "objects": []}""");
		HudManager m = HudManager.init(dir, null);
		m.load();
		assertEquals(2, m.presets().size());
		Preset main = m.presets().getFirst();
		assertEquals("Main", main.name);
		HudObject hello = main.objects.get(0);
		assertEquals(HudObject.Type.TEXT, hello.type);
		assertEquals(Anchor.TOP, hello.align.fixedValue());
		assertEquals("Hi {Health}", hello.text.fixedValue());
		assertTrue(hello.background.fixedValue());
		assertEquals("SlotItem(\"head\")", main.objects.get(1).item.fxSource(), "item sources became a tag");
		assertEquals("TargetBlockId()", main.objects.get(2).block.fxSource());
		assertEquals(HudObject.Type.GROUP, main.objects.get(3).type, "bars have no equivalent");
		assertFalse(m.presets().get(1).enabled.fixedValue());
		assertTrue(Files.exists(dir.resolve("canvases.old")));
	}

	@Test
	void migratesFirstVersionTexts(@TempDir Path dir) throws Exception {
		Path legacy = dir.resolve("customhud.json");
		Files.writeString(legacy, """
				{"version": 2, "elements": [
				  {"type": "text", "name": "", "x": 0.5, "y": 0.5, "props": {"text": "FPS : {Fps:F0}"}},
				  {"type": "bar", "name": "", "x": 0.1, "y": 0.1, "props": {}}
				]}""");
		HudManager m = HudManager.init(dir.resolve("customhud"), legacy);
		m.load();
		List<HudObject> objects = m.presets().getFirst().objects;
		assertEquals(1, objects.size());
		assertEquals("FPS : {Fps:F0}", objects.getFirst().text.fixedValue());
		assertTrue(Files.exists(dir.resolve("customhud.json.old")));
	}
}
