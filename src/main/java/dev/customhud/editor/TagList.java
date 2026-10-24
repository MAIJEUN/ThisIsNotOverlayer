package dev.customhud.editor;

import dev.customhud.js.JsEngine;
import dev.customhud.js.ScriptLoader;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import dev.customhud.tag.impl.TagLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Every tag by category with a search box; clicking one hands it to {@code onPick}. */
final class TagList {
	private static final int ROW = 22;
	private static final int HEADER = 13;

	private record Row(TagCore tag, Component header, int y, int height) {
	}

	private final Minecraft mc = Minecraft.getInstance();
	private final Consumer<TagCore> onPick;
	private EditBox search;
	private int x, y, w, h;
	private double scroll;
	private TagCore flashed;
	private long flashedAt;

	TagList(Consumer<TagCore> onPick) {
		this.onPick = onPick;
	}

	void init(HudScreen screen) {
		String old = search == null ? "" : search.getValue();
		search = screen.widget(new EditBox(mc.font, 0, 0, 100, 16, Component.translatable("screen.customhud.tags.search")));
		search.setHint(Component.translatable("screen.customhud.tags.search"));
		search.setValue(old);
		search.setResponder(s -> scroll = 0);
	}

	void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		search.setX(x);
		search.setY(y);
		search.setWidth(w - 70);
		search.visible = true;
	}

	private int listTop() {
		return y + 20;
	}

	private int listBottom() {
		return y + h - scriptErrors() * 10;
	}

	private int scriptErrors() {
		return Math.min(2, JsEngine.get().loader().diagnostics().size());
	}

	int[] reloadButton() {
		return new int[]{x + w - 66, y, x + w, y + 16};
	}

	private List<Row> rows() {
		String q = search.getValue().trim().toLowerCase(Locale.ROOT);
		List<Row> out = new ArrayList<>();
		int yy = 0;
		for (String category : TagLibrary.CATEGORIES) {
			List<TagCore> tags = TagManager.all().stream()
					.filter(t -> !t.isHidden() && TagLibrary.category(t).equals(category))
					.filter(t -> q.isEmpty() || t.name().toLowerCase(Locale.ROOT).contains(q) || TagText.description(t).toLowerCase(Locale.ROOT).contains(q))
					.sorted(Comparator.comparing(TagCore::name))
					.toList();
			if (tags.isEmpty()) {
				continue;
			}
			out.add(new Row(null, Component.translatable("tagcat.customhud." + category), yy, HEADER));
			yy += HEADER;
			for (TagCore t : tags) {
				out.add(new Row(t, null, yy, ROW));
				yy += ROW;
			}
			yy += 3;
		}
		return out;
	}

	private double maxScroll(List<Row> rows) {
		if (rows.isEmpty()) {
			return 0;
		}
		Row last = rows.getLast();
		return Math.max(0, last.y() + last.height() - (listBottom() - listTop()));
	}

	private Row rowAt(double mx, double my) {
		if (mx < x || mx >= x + w - 8 || my < listTop() || my >= listBottom()) {
			return null;
		}
		for (Row r : rows()) {
			int ry = listTop() + r.y() - (int) scroll;
			if (r.tag() != null && my >= ry && my < ry + r.height() - 1) {
				return r;
			}
		}
		return null;
	}

	void render(GuiGraphicsExtractor g, int mx, int my) {
		Font font = mc.font;
		int[] reload = reloadButton();
		Ui.button(g, font, reload, Component.translatable("screen.customhud.tags.reload"), Ui.hover(mx, my, reload), true);
		List<Row> rows = rows();
		scroll = Math.max(0, Math.min(maxScroll(rows), scroll));
		Ui.inset(g, x, listTop() - 2, w, listBottom() - listTop() + 2);
		g.enableScissor(x + 1, listTop() - 1, x + w - 1, listBottom() - 1);
		Row hot = rowAt(mx, my);
		if (rows.isEmpty()) {
			g.text(font, Component.translatable("screen.customhud.tags.none"), x + 6, listTop() + 4, Ui.MUTED, false);
		}
		for (Row r : rows) {
			int ry = listTop() + r.y() - (int) scroll;
			if (ry + r.height() < listTop() || ry > listBottom()) {
				continue;
			}
			if (r.tag() == null) {
				g.text(font, r.header(), x + 4, ry + 3, Ui.ACCENT, false);
				continue;
			}
			TagCore t = r.tag();
			boolean flash = t == flashed && System.currentTimeMillis() - flashedAt < 1000;
			g.fill(x + 2, ry, x + w - 9, ry + r.height() - 1, flash ? 0xFF2E5A2E : r == hot ? Ui.ROW_HOVER : Ui.ROW);
			g.text(font, Ui.ellipsize(font, TagText.signature(t), w - 20), x + 6, ry + 2, Ui.TEXT, false);
			String desc = TagText.description(t).split("\n")[0];
			g.text(font, Ui.ellipsize(font, desc, w - 20), x + 6, ry + 12, Ui.MUTED, false);
		}
		g.disableScissor();
		Ui.scrollbar(g, x + w - 7, listTop() - 1, listBottom() - listTop(), scroll, maxScroll(rows), (int) (listBottom() - listTop() + maxScroll(rows)));
		List<ScriptLoader.ScriptDiagnostic> errors = JsEngine.get().loader().diagnostics();
		for (int i = 0; i < scriptErrors(); i++) {
			ScriptLoader.ScriptDiagnostic d = errors.get(i);
			g.text(font, Ui.ellipsize(font, "⚠ " + d.file() + ": " + d.message(), w), x, listBottom() + 2 + i * 10, Ui.DANGER, false);
		}
		if (hot != null) {
			g.setTooltipForNextFrame(font, Component.literal(TagText.description(hot.tag())), mx, my);
		}
	}

	boolean click(double mx, double my) {
		if (Ui.hover(mx, my, reloadButton())) {
			JsEngine.get().loader().reloadAll();
			return true;
		}
		Row r = rowAt(mx, my);
		if (r != null) {
			flashed = r.tag();
			flashedAt = System.currentTimeMillis();
			onPick.accept(r.tag());
			return true;
		}
		return false;
	}

	boolean scroll(double mx, double my, double amount) {
		if (mx < x || mx >= x + w || my < y || my >= y + h) {
			return false;
		}
		scroll = Math.max(0, Math.min(maxScroll(rows()), scroll - amount * ROW));
		return true;
	}

	EditBox searchBox() {
		return search;
	}

	/** Centre of the first tag row (for tests). */
	int[] firstTagCenter() {
		for (Row r : rows()) {
			if (r.tag() != null) {
				return new int[]{x + w / 2, listTop() + r.y() - (int) scroll + r.height() / 2};
			}
		}
		return null;
	}
}
