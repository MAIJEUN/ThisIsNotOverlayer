package dev.customhud.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.editor.HudScreen;
import dev.customhud.editor.Templates;
import dev.customhud.prop.Prop;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudObject;
import dev.customhud.hud.HudRenderer;
import dev.customhud.hud.Preset;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Drives the editor window with real mouse and keyboard input and screenshots each step. */
public class HudEditorGameTest implements FabricClientGameTest {
	private ClientGameTestContext ctx;
	private TestInput input;
	private int scale;

	@Override
	public void runTest(ClientGameTestContext context) {
		this.ctx = context;
		this.input = context.getInput();
		input.resizeWindow(1280, 720);
		context.runOnClient(mc -> {
			mc.options.guiScale().set(2);
			mc.resizeGui();
		});
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getServer().runCommand("time set day");
			world.getServer().runCommand("item replace entity @a armor.head with minecraft:diamond_helmet");
			world.getServer().runCommand("item replace entity @a armor.chest with minecraft:iron_chestplate");
			world.getServer().runCommand("item replace entity @a weapon.mainhand with minecraft:diamond_sword");
			context.waitTicks(140);
			scale = context.computeOnClient(mc -> mc.getWindow().getGuiScale());

			// 1. The window opens on the presets page with the default preset.
			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitForScreen(HudScreen.class);
			context.waitTicks(2);
			check(screen(HudScreen::tab) == HudScreen.Tab.PRESETS, "Opens on the presets page");
			check(presets().size() == 1 && presets().getFirst().name.equals("Default")
					&& presets().getFirst().objects.getFirst().text.fixedValue().equals("FPS: {Fps:0}"), "Fresh install has a Default preset with an FPS text");
			context.takeScreenshot("customhud-01-presets");

			// 2. A new preset opens in the Edit tab.
			click(at(s -> s.newPresetButton(0)));
			check(presets().size() == 2 && screen(HudScreen::tab) == HudScreen.Tab.EDIT && screen(HudScreen::editing) == presets().get(1),
					"+ New preset opens it for editing");
			Preset mine = presets().get(1);
			context.takeScreenshot("customhud-02-empty-preset");

			// 3. + Add → Text; type a template with Overlayer tags.
			click(at(HudScreen::addButtonCenter));
			check(screen(HudScreen::popupLabels).size() > 8, "+ Add lists the templates");
			context.takeScreenshot("customhud-03-add");
			click(at(s -> s.popupTemplate("text")));
			HudObject text = screen(HudScreen::selected);
			check(text != null && text.type == HudObject.Type.TEXT && mine.objects.contains(text), "A new text is selected in the preset");
			int[] box = context.computeOnClient(mc -> {
				var t = ((HudScreen) mc.gui.screen()).templateBox();
				return new int[]{t.getX() + 20, t.getY() + 8};
			});
			click(box);
			check(context.computeOnClient(HudEditorGameTest::textInputOn), "Focusing the text box turns on text input");
			context.runOnClient(mc -> ((HudScreen) mc.gui.screen()).templateBox().setValue(""));
			input.typeChars("<color=#FF5555>HP</color> <b>{Health:0}</b>");
			context.waitTicks(2);
			check(text.text.fixedValue().equals("<color=#FF5555>HP</color> <b>{Health:0}</b>"), "Template typed: " + text.text.fixedValue());
			check(context.computeOnClient(mc -> text.text.diagnostics().isEmpty() && text.text.render().equals("<color=#FF5555>HP</color> <b>20</b>")),
					"Tags replaced, rich text kept for drawing");

			// 4. Fx on the scale.
			click(at(s -> s.fxToggleCenter("scale")));
			check(text.scale.hasFx(), "ƒ turns Fx on");
			context.runOnClient(mc -> ((HudScreen) mc.gui.screen()).settingBox("scale").setValue(""));
			input.typeChars("1.5 + Math.sin(Date.now() / 300) * 0.25");
			context.waitTicks(3);
			check(context.computeOnClient(mc -> text.scale.fxError() == null) && text.scale.fxSource().startsWith("1.5 + Math.sin"), "Fx scale works: " + text.scale.fxSource());
			context.runOnClient(mc -> {
				text.x.set(20.0);
				text.y.set(30.0);
			});
			context.waitTicks(2);
			context.takeScreenshot("customhud-04-settings");

			// 5. An item, with the id completed by Tab.
			unselect();
			addTemplate("item");
			HudObject item = screen(HudScreen::selected);
			check(item.type == HudObject.Type.ITEM, "Item added");
			int[] idBox = context.computeOnClient(mc -> {
				EditBox b = ((HudScreen) mc.gui.screen()).settingBox("item");
				return new int[]{b.getX() + 10, b.getY() + 7};
			});
			click(idBox);
			context.runOnClient(mc -> ((HudScreen) mc.gui.screen()).settingBox("item").setValue(""));
			input.typeChars("golden_ap");
			context.waitTicks(2);
			context.takeScreenshot("customhud-05-item-id");
			input.pressKey(InputConstants.KEY_TAB);
			context.waitTicks(2);
			check(item.item.fixedValue().equals("golden_apple"), "Tab completes the id: " + item.item.fixedValue());
			context.runOnClient(mc -> {
				item.x.set(40.0);
				item.y.set(120.0);
				item.scale.set(2.0);
			});
			input.pressKey(InputConstants.KEY_ESCAPE); // stop typing

			// 6. Ready-made templates built from tags.
			for (String id : List.of("held_item", "armor", "looking_at", "keystrokes", "coordinates")) {
				unselect();
				addTemplate(id);
			}
			HudObject armor = byName(mine, "Armor");
			context.runOnClient(mc -> {
				byName(mine, "Held Item").x.set(10.0);
				byName(mine, "Held Item").y.set(165.0);
				armor.x.set(240.0);
				armor.background.set(false); // the text moved into it later would make its box cover the drag tests
				armor.y.set(150.0);
				byName(mine, "Looking At").x.set(120.0);
				byName(mine, "Looking At").y.set(220.0);
				byName(mine, "Keystrokes").x.set(10.0);
				byName(mine, "Keystrokes").y.set(260.0);
				byName(mine, "Coordinates").x.set(10.0);
				byName(mine, "Coordinates").y.set(60.0);
			});
			context.waitTicks(3);
			check(context.computeOnClient(mc -> armor.children.getFirst().item.get().equals("minecraft:diamond_helmet")), "SlotItem(\"head\") shows the helmet");
			context.takeScreenshot("customhud-06-templates");

			// 7. Drag an object on the screen.
			double before = item.x.fixedValue();
			float[] c = center(item);
			drag(c[0], c[1], c[0] + 37, c[1]);
			check(Math.abs(item.x.fixedValue() - before) > 20, "Dragging moves the object (" + before + " → " + item.x.fixedValue() + ")");
			check(screen(HudScreen::selected) == item, "…and selects it");

			// 8. Drag the text into the Armor group in the tree.
			int[] from = at(s -> s.rowCenter(text, "row"));
			int[] to = at(s -> s.rowCenter(armor, "row"));
			drag(from[0], from[1], to[0], to[1]);
			check(text.parent == armor && armor.children.contains(text), "Tree drag puts the text into the group");
			context.takeScreenshot("customhud-07-grouped");

			// 9. An entity, a turned block, and a background + outline.
			unselect();
			addTemplate("entity");
			HudObject entity = screen(HudScreen::selected);
			check(entity.type == HudObject.Type.ENTITY && entity.entity.fixedValue().equals("player"), "Entity added (you)");
			unselect();
			addTemplate("block");
			HudObject block = screen(HudScreen::selected);
			context.runOnClient(mc -> {
				entity.x.set(220.0);
				entity.y.set(40.0);
				entity.scale.set(1.5);
				entity.rotY.set(30.0);
				block.x.set(150.0);
				block.y.set(110.0);
				block.scale.set(2.0);
				block.rotY.setFx("Date.now() / 20 % 360");
				text.background.set(true);
				text.outline.set(true);
				text.outlineColor.set(0xFFFF5555);
			});
			context.waitTicks(3);
			context.takeScreenshot("customhud-08-entity-block-box");

			// 10. Box-select the item and the block, move both, then group them with Ctrl+G.
			float[] area = context.computeOnClient(mc -> {
				float[] a = item.bounds(HudRenderer.frame()), b = block.bounds(HudRenderer.frame());
				return new float[]{Math.min(a[0], b[0]) - 3, Math.min(a[1], b[1]) - 3, Math.max(a[2], b[2]) + 3, Math.max(a[3], b[3]) + 3};
			});
			drag(area[0], area[1], area[2], area[3]);
			Set<HudObject> sel = context.computeOnClient(mc -> Set.copyOf(((HudScreen) mc.gui.screen()).selection()));
			check(sel.contains(item) && sel.contains(block), "Box selection picks both (" + sel.size() + ")");
			double ix = item.x.fixedValue(), bx = block.x.fixedValue();
			float[] ic = center(item);
			drag(ic[0], ic[1], ic[0] + 13, ic[1] + 9);
			check(Math.abs(item.x.fixedValue() - ix - 13) < 6 && Math.abs(block.x.fixedValue() - bx - 13) < 6,
					"Both move together (" + ix + " → " + item.x.fixedValue() + ", " + bx + " → " + block.x.fixedValue() + ")");
			float[] beforeGroup = context.computeOnClient(mc -> item.bounds(HudRenderer.frame()));
			context.runOnClient(mc -> mc.gui.screen().keyPressed(new KeyEvent(InputConstants.KEY_G, 0, 0x40)));
			context.waitTicks(2);
			check(item.parent != null && item.parent == block.parent && item.parent.type == HudObject.Type.GROUP, "Ctrl+G groups the selection");
			float[] afterGroup = context.computeOnClient(mc -> item.bounds(HudRenderer.frame()));
			check(Math.abs(beforeGroup[0] - afterGroup[0]) < 1.5 && Math.abs(beforeGroup[1] - afterGroup[1]) < 1.5, "Grouping keeps them in place");
			context.takeScreenshot("customhud-09-grouped-selection");

			// 11. The presets page shows both with previews; switching one off hides it.
			click(at(s -> s.tabCenter(HudScreen.Tab.PRESETS)));
			check(screen(HudScreen::tab) == HudScreen.Tab.PRESETS, "Presets tab");
			click(at(s -> s.presetCheckbox(0)));
			check(!presets().getFirst().enabled.fixedValue(), "The box switches a preset off");
			context.waitTicks(2);
			context.takeScreenshot("customhud-10-previews");
			click(at(s -> s.presetCheckbox(0)));
			check(presets().getFirst().enabled.fixedValue(), "…and on again");

			// 12. Clicking an object on the screen opens its preset with it selected.
			click(at(s -> s.tabCenter(HudScreen.Tab.TAGS)));
			context.takeScreenshot("customhud-11-tags");
			click(at(s -> s.tabCenter(HudScreen.Tab.PRESETS)));
			float[] ec = center(entity);
			click(new int[]{Math.round(ec[0]), Math.round(ec[1])});
			check(screen(HudScreen::tab) == HudScreen.Tab.EDIT && screen(HudScreen::selected) == entity, "Clicking on the HUD opens the object's preset");

			// 13. The HUD alone, then saved and reloaded.
			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitFor(mc -> mc.gui.screen() == null);
			context.waitTicks(10);
			context.takeScreenshot("customhud-12-ingame");
			Path dir = FabricLoader.getInstance().getConfigDir().resolve("customhud").resolve("presets");
			String json = read(dir.resolve("preset1.json"));
			check(json.contains("<color=#FF5555>HP</color>") && json.contains("SlotItem"), "Saved to presets/preset1.json");
			int[] count = {0};
			mine.forEach(o -> count[0]++);
			int armorChildren = armor.children.size();
			context.runOnClient(mc -> HudManager.get().load());
			Preset reloaded = presets().get(1);
			int[] again = {0};
			reloaded.forEach(o -> again[0]++);
			check(presets().size() == 2 && again[0] == count[0] && byName(reloaded, "Armor").children.size() == armorChildren,
					"Reload restores presets, objects and groups (" + again[0] + "/" + count[0] + ")");

			// 14. Every ready-made part works: no tag errors, no Fx errors, no tag left unreplaced.
			world.getServer().runCommand("effect give @a minecraft:speed 300 1");
			world.getServer().runCommand("effect give @a minecraft:night_vision 90");
			world.getServer().runCommand("give @a minecraft:totem_of_undying 2");
			world.getServer().runCommand("give @a minecraft:arrow 48");
			world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:torch 24");
			world.getServer().runCommand("item replace entity @a armor.legs with minecraft:golden_leggings");
			context.runOnClient(mc -> {
				presets().forEach(p -> p.enabled.set(false));
				Preset all = HudManager.get().create("All parts");
				for (Templates.Template t : Templates.ALL) {
					all.objects.add(t.create());
				}
			});
			context.waitTicks(5);
			List<String> problems = context.computeOnClient(mc -> problems(presets().getLast()));
			check(problems.isEmpty(), "Every part (" + Templates.ALL.size() + ") works: " + problems);
			context.runOnClient(mc -> HudManager.get().delete(presets().getLast()));

			// 15. Every complete HUD, alone on screen.
			for (Templates.Layout layout : Templates.LAYOUTS) {
				context.runOnClient(mc -> {
					presets().forEach(p -> p.enabled.set(false));
					Preset p = HudManager.get().create(layout.name().getString());
					p.objects.addAll(layout.create());
				});
				context.waitTicks(5);
				List<String> layoutProblems = context.computeOnClient(mc -> problems(presets().getLast()));
				check(layoutProblems.isEmpty(), "Complete HUD '" + layout.id() + "' works: " + layoutProblems);
				context.takeScreenshot("customhud-20-layout-" + layout.id());
			}

			// 16. The presets page with all of them, and the template list.
			input.pressKey(InputConstants.KEY_RSHIFT);
			context.waitForScreen(HudScreen.class);
			click(at(s -> s.tabCenter(HudScreen.Tab.PRESETS)));
			context.takeScreenshot("customhud-21-preset-tiles");
			click(at(s -> s.newPresetButton(1)));
			context.takeScreenshot("customhud-22-template-list");
			List<String> labels = screen(HudScreen::popupLabels);
			check(Templates.LAYOUTS.stream().allMatch(l -> labels.contains(l.name().getString()))
					&& Templates.ALL.stream().filter(t -> t.group() != Templates.Group.BASIC).allMatch(t -> labels.contains(t.name().getString())),
					"The list shows every complete HUD and every part (" + labels.size() + " rows)");
		}
	}

	/** Tag errors, Fx errors and tags left unreplaced in a preset's objects. */
	private static List<String> problems(Preset preset) {
		List<String> out = new java.util.ArrayList<>();
		preset.forEach(o -> {
			List<Prop<?>> props = new java.util.ArrayList<>(o.contentProps());
			props.add(o.visible);
			props.addAll(o.transformProps());
			props.addAll(o.boxProps());
			for (Prop<?> p : props) {
				p.get();
				if (p.fxError() != null) {
					out.add(o.name + "." + p.id() + ": " + p.fxError());
				}
			}
			if (o.type == HudObject.Type.TEXT) {
				o.text.diagnostics().forEach(d -> out.add(o.name + ": " + d.text().getString()));
				String r = o.text.render();
				if (r.contains("{") || r.contains("}")) {
					out.add(o.name + " not replaced: " + r);
				}
			}
		});
		return out;
	}

	// ---- helpers ----

	private List<Preset> presets() {
		return HudManager.get().presets();
	}

	private static HudObject byName(Preset p, String name) {
		HudObject[] found = {null};
		p.forEach(o -> {
			if (o.name.equals(name)) {
				found[0] = o;
			}
		});
		if (found[0] == null) {
			throw new AssertionError("No object named " + name);
		}
		return found[0];
	}

	private <T> T screen(Function<HudScreen, T> f) {
		return ctx.computeOnClient(mc -> f.apply((HudScreen) mc.gui.screen()));
	}

	private int[] at(Function<HudScreen, int[]> f) {
		int[] p = screen(f);
		if (p == null) {
			throw new AssertionError("Nothing to click");
		}
		return p;
	}

	private float[] center(HudObject o) {
		return ctx.computeOnClient(mc -> {
			float[] b = o.bounds(HudRenderer.frame());
			return new float[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
		});
	}

	private void unselect() {
		ctx.runOnClient(mc -> ((HudScreen) mc.gui.screen()).select(null));
		ctx.waitTicks(1);
	}

	private void addTemplate(String id) {
		click(at(HudScreen::addButtonCenter));
		click(at(s -> s.popupTemplate(id)));
	}

	private static boolean textInputOn(Minecraft mc) {
		try {
			var f = mc.textInputManager().getClass().getDeclaredField("textInputEnabled");
			f.setAccessible(true);
			return f.getBoolean(mc.textInputManager());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError(e);
		}
	}

	private void setCursor(double guiX, double guiY) {
		input.setCursorPos(guiX * scale, guiY * scale);
		ctx.waitTick();
	}

	private void click(int[] at) {
		setCursor(at[0], at[1]);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private void drag(double fx, double fy, double tx, double ty) {
		setCursor(fx, fy);
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		int steps = 6;
		for (int i = 1; i <= steps; i++) {
			double t = (double) i / steps;
			input.setCursorPos((fx + (tx - fx) * t) * scale, (fy + (ty - fy) * t) * scale);
			ctx.waitTick();
		}
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (Exception e) {
			throw new AssertionError("Could not read " + path, e);
		}
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
		System.out.println("[customhud-test] OK: " + message);
	}
}
