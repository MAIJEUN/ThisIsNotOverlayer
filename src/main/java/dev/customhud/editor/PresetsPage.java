package dev.customhud.editor;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudObject;
import dev.customhud.hud.HudRenderer;
import dev.customhud.hud.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Presets as tiles with a live preview (Overlayer's canvas page): click to edit, the box to switch it
 * on or off, and buttons to copy it, export it to the clipboard or delete it. The last tile makes a new
 * preset — empty, from a template, or pasted from the clipboard.
 */
final class PresetsPage extends Page {
	private static final int GAP = 6;
	private static final int FOOTER = 16;
	private final Minecraft mc = Minecraft.getInstance();
	private double scroll;
	private Preset armed;
	private long armedAt;
	private Preset copied;
	private long copiedAt;
	private long importFailedAt;

	PresetsPage(HudScreen screen) {
		super(screen);
	}

	@Override
	Component hint() {
		return Component.translatable("screen.customhud.presets.hint");
	}

	private List<Preset> presets() {
		return HudManager.get().presets();
	}

	private int columns() {
		return w >= 360 ? 3 : 2;
	}

	private int tileW() {
		return (w - 8 - GAP * (columns() - 1)) / columns();
	}

	private int tileH() {
		return tileW() * 9 / 16 + FOOTER;
	}

	/** Tile {@code i} (the last one is the "new" tile): {@code x, y}. */
	private int[] tile(int i) {
		int c = i % columns(), r = i / columns();
		return new int[]{x + c * (tileW() + GAP), y + r * (tileH() + GAP) - (int) scroll};
	}

	private double maxScroll() {
		int rows = (presets().size() + 1 + columns() - 1) / columns();
		return Math.max(0, rows * (tileH() + GAP) - GAP - h);
	}

	private int tileAt(double mx, double my) {
		if (!contains(mx, my)) {
			return -1;
		}
		for (int i = 0; i <= presets().size(); i++) {
			int[] t = tile(i);
			if (mx >= t[0] && mx < t[0] + tileW() && my >= t[1] && my < t[1] + tileH()) {
				return i;
			}
		}
		return -1;
	}

	// tile parts: buttons in the footer, the on/off box in the corner of the preview
	private int[] checkbox(int[] t) {
		return new int[]{t[0] + tileW() - 15, t[1] + 3, t[0] + tileW() - 3, t[1] + 15};
	}

	private int[] footerButton(int[] t, int index) {
		int bx = t[0] + tileW() - 14 * (3 - index) - 1;
		int by = t[1] + tileH() - FOOTER + 2;
		return new int[]{bx, by, bx + 13, by + 13};
	}

	private int[] newButton(int[] t, int index) {
		int bh = Math.min(20, (tileH() - 12) / 3);
		int by = t[1] + 4 + index * (bh + 2);
		return new int[]{t[0] + 6, by, t[0] + tileW() - 6, by + bh};
	}

	private boolean armed(Preset p) {
		return armed == p && System.currentTimeMillis() - armedAt < 4000;
	}

	@Override
	void render(GuiGraphicsExtractor g, int mx, int my) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll));
		g.enableScissor(x, y, x + w, y + h);
		List<Preset> list = presets();
		Component tip = null;
		for (int i = 0; i < list.size(); i++) {
			Preset p = list.get(i);
			int[] t = tile(i);
			if (t[1] + tileH() < y || t[1] > y + h) {
				continue;
			}
			boolean hot = tileAt(mx, my) == i;
			int pw = tileW(), ph = tileH() - FOOTER;
			Ui.inset(g, t[0], t[1], pw, ph);
			preview(g, p, t[0] + 1, t[1] + 1, pw - 2, ph - 2);
			if (hot) {
				g.outline(t[0], t[1], pw, ph, 0xFFFFFFFF);
			}
			boolean on = p.enabled.on();
			int[] cb = checkbox(t);
			Ui.checkbox(g, cb[0], cb[1], 12, on, Ui.hover(mx, my, cb));
			if (p.enabled.hasFx()) {
				g.text(font(), "ƒ", cb[0] - 7, cb[1] + 2, Ui.ACCENT, true);
			}
			if (Ui.hover(mx, my, cb)) {
				tip = Component.translatable("screen.customhud.presets.toggle");
			}
			// footer: name and buttons
			int fy = t[1] + ph + 4;
			g.text(font(), Ui.ellipsize(font(), p.name.isEmpty() ? "-" : p.name, pw - 46), t[0] + 2, fy, on ? Ui.TEXT : Ui.MUTED, true);
			String[] glyphs = {"⧉", copied == p && System.currentTimeMillis() - copiedAt < 1500 ? "✔" : "⇪", "✕"};
			String[] tips = {"screen.customhud.presets.clone", "screen.customhud.presets.copy",
					armed(p) ? "screen.customhud.presets.delete_confirm" : "screen.customhud.presets.delete"};
			for (int b = 0; b < 3; b++) {
				int[] fb = footerButton(t, b);
				boolean bh = Ui.hover(mx, my, fb);
				Ui.button(g, font(), fb, Component.literal(glyphs[b]).withColor(b == 2 && armed(p) ? Ui.DANGER : Ui.TEXT), bh, true);
				if (bh) {
					tip = Component.translatable(tips[b]);
				}
			}
		}
		// the "new" tile
		int[] t = tile(list.size());
		Ui.inset(g, t[0], t[1], tileW(), tileH());
		String[] keys = {"screen.customhud.presets.new", "screen.customhud.presets.from_template", "screen.customhud.presets.import"};
		for (int b = 0; b < 3; b++) {
			int[] nb = newButton(t, b);
			Component label = Component.translatable(keys[b]);
			if (b == 2 && System.currentTimeMillis() - importFailedAt < 2000) {
				label = Component.translatable("screen.customhud.presets.import_failed").withColor(Ui.DANGER);
			}
			Ui.button(g, font(), nb, label, Ui.hover(mx, my, nb), true);
		}
		g.disableScissor();
		Ui.scrollbar(g, x + w - 6, y, h, scroll, maxScroll(), (int) (h + maxScroll()));
		if (tip != null) {
			g.setTooltipForNextFrame(font(), tip, mx, my);
		}
	}

	/**
	 * The preset drawn small, as it looks on screen — zoomed in on its objects when they take up only
	 * part of the screen (a whole screen shrunk into a tile makes text unreadable).
	 */
	private void preview(GuiGraphicsExtractor g, Preset p, int px, int py, int pw, int ph) {
		float sw = screen.width, sh = screen.height;
		float s = Math.min(pw / sw, ph / sh);
		float cx = sw / 2, cy = sh / 2;
		float[] area = contentArea(p);
		if (area != null) {
			float margin = 6;
			float aw = area[2] - area[0] + margin * 2, ah = area[3] - area[1] + margin * 2;
			float zoom = Math.min(1.5f, Math.min(pw / aw, ph / ah));
			if (zoom > s) {
				s = zoom;
				cx = (area[0] + area[2]) / 2;
				cy = (area[1] + area[3]) / 2;
			}
		}
		g.fill(px, py, px + pw, py + ph, 0xFF20242C);
		g.enableScissor(px, py, px + pw, py + ph);
		g.pose().pushMatrix();
		g.pose().translate(px + pw / 2f - cx * s, py + ph / 2f - cy * s);
		g.pose().scale(s, s);
		for (HudObject o : p.objects) {
			try {
				o.draw(g, mc, sw, sh, HudRenderer.frame(), false, false);
			} catch (RuntimeException ignored) {
				// a broken object should not break the page
			}
		}
		g.pose().popMatrix();
		g.disableScissor();
		if (!p.enabled.on()) {
			g.fill(px, py, px + pw, py + ph, 0x90000000);
		}
	}

	/** Where the preset's objects lie on this screen (measured, so switched-off presets work too), or null. */
	private float[] contentArea(Preset p) {
		long frame = HudRenderer.frame();
		float[] a = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		boolean[] any = {false};
		for (HudObject o : p.objects) {
			float[] b;
			try {
				b = o.measure(mc, screen.width, screen.height, frame);
			} catch (RuntimeException e) {
				b = null;
			}
			if (b != null) {
				a[0] = Math.min(a[0], b[0]);
				a[1] = Math.min(a[1], b[1]);
				a[2] = Math.max(a[2], b[2]);
				a[3] = Math.max(a[3], b[3]);
				any[0] = true;
			}
		}
		return any[0] ? a : null;
	}

	private net.minecraft.client.gui.Font font() {
		return mc.font;
	}

	@Override
	boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		if (e.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		double mx = e.x(), my = e.y();
		int i = tileAt(mx, my);
		if (i < 0) {
			return false;
		}
		List<Preset> list = presets();
		int[] t = tile(i);
		if (i == list.size()) {
			if (Ui.hover(mx, my, newButton(t, 0))) {
				Preset p = HudManager.get().create(Component.translatable("screen.customhud.presets.new_name").getString());
				screen.edit(p, null);
			} else if (Ui.hover(mx, my, newButton(t, 1))) {
				openTemplates(newButton(t, 1));
			} else if (Ui.hover(mx, my, newButton(t, 2))) {
				importClipboard();
			}
			return true;
		}
		Preset p = list.get(i);
		if (Ui.hover(mx, my, checkbox(t))) {
			if (p.enabled.hasFx()) {
				p.enabled.setFx(null);
			}
			p.enabled.set(!p.enabled.fixedValue());
			HudManager.get().save();
		} else if (Ui.hover(mx, my, footerButton(t, 0))) {
			HudManager.get().duplicate(p);
		} else if (Ui.hover(mx, my, footerButton(t, 1))) {
			mc.keyboardHandler.setClipboard(new GsonBuilder().setPrettyPrinting().create().toJson(p.toJson()));
			copied = p;
			copiedAt = System.currentTimeMillis();
		} else if (Ui.hover(mx, my, footerButton(t, 2))) {
			if (armed(p) || e.hasShiftDown()) {
				HudManager.get().delete(p);
				screen.forget(p);
				armed = null;
			} else {
				armed = p;
				armedAt = System.currentTimeMillis();
			}
		} else {
			screen.edit(p, null);
		}
		return true;
	}

	private void openTemplates(int[] anchor) {
		List<ListPopup.Entry> entries = new ArrayList<>();
		entries.add(ListPopup.Entry.header(Component.translatable("screen.customhud.presets.layouts")));
		for (Templates.Layout layout : Templates.LAYOUTS) {
			entries.add(new ListPopup.Entry(layout.name(), Component.literal(layout.preview()), () -> {
				Preset p = HudManager.get().create(layout.name().getString());
				p.objects.addAll(layout.create());
				HudManager.get().save();
				screen.edit(p, null);
			}));
		}
		Templates.Group group = Templates.Group.BASIC;
		for (Templates.Template tpl : Templates.ALL) {
			if (tpl.group() == Templates.Group.BASIC) {
				continue;
			}
			if (tpl.group() != group) {
				group = tpl.group();
				entries.add(ListPopup.Entry.header(Component.translatable("screen.customhud.edit.group." + group.name().toLowerCase(java.util.Locale.ROOT))));
			}
			entries.add(new ListPopup.Entry(tpl.name(), Component.literal(tpl.preview()), () -> {
				Preset p = HudManager.get().create(tpl.name().getString());
				HudObject o = tpl.create();
				p.objects.add(o);
				HudManager.get().save();
				screen.edit(p, o);
			}));
		}
		screen.openPopup(new ListPopup(entries, anchor[0], anchor[3] + 2, Math.max(220, anchor[2] - anchor[0]), screen.height));
	}

	/** A preset (or a single object, which gets a preset of its own) from JSON on the clipboard. */
	private void importClipboard() {
		try {
			JsonObject json = JsonParser.parseString(mc.keyboardHandler.getClipboard()).getAsJsonObject();
			Preset p;
			if (json.has("objects")) {
				p = Preset.fromJson(json);
				p.name = HudManager.get().uniqueName(p.name);
			} else if (json.has("type")) {
				p = new Preset(HudManager.get().uniqueName(Component.translatable("screen.customhud.presets.new_name").getString()));
				p.objects.add(HudObject.fromJson(json));
			} else {
				throw new IllegalArgumentException();
			}
			HudManager.get().presets().add(p);
			HudManager.get().save();
		} catch (Exception ex) {
			importFailedAt = System.currentTimeMillis();
		}
	}

	@Override
	boolean mouseScrolled(double mx, double my, double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * 20));
		return true;
	}

	/** Clicking an object of the HUD (outside the window) opens its preset with the object selected. */
	boolean worldClicked(MouseButtonEvent e) {
		HudObject[] hit = {null};
		long frame = HudRenderer.frame();
		for (Preset p : presets()) {
			if (p.enabled.on()) {
				p.forEach(o -> {
					if (o.hit(e.x(), e.y(), frame)) {
						hit[0] = o;
					}
				});
			}
		}
		if (hit[0] == null) {
			return false;
		}
		screen.edit(HudManager.get().presetOf(hit[0]), hit[0]);
		return true;
	}

	// ---- test hooks ----

	/** Scrolls tile {@code index} into view. */
	private void reveal(int index) {
		int top = (index / columns()) * (tileH() + GAP);
		if (top < scroll || top + tileH() > scroll + h) {
			scroll = Math.max(0, Math.min(maxScroll(), top - (h - tileH()) / 2.0));
		}
	}

	int[] tileCenter(int index) {
		reveal(index);
		int[] t = tile(index);
		return new int[]{t[0] + tileW() / 2, t[1] + (tileH() - FOOTER) / 2};
	}

	int[] checkboxCenter(int index) {
		reveal(index);
		int[] b = checkbox(tile(index));
		return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
	}

	int[] newTileButton(int index) {
		reveal(presets().size());
		int[] b = newButton(tile(presets().size()), index);
		return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
	}
}
