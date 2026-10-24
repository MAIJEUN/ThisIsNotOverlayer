package dev.customhud.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/** A list that drops down from a button (in a pop-up panel); headers are entries with a null action. */
final class ListPopup {
	record Entry(Component label, Component detail, Runnable action) {
		static Entry header(Component label) {
			return new Entry(label, null, null);
		}
	}

	private static final int ROW = 13;
	private final List<Entry> entries;
	private final int x, y, w, h;
	private double scroll;

	ListPopup(List<Entry> entries, int x, int y, int w, int screenH) {
		this.entries = entries;
		int full = entries.size() * ROW + 8;
		int hh = Math.min(full, Math.max(60, screenH - y - 6));
		if (y + hh > screenH - 4) {
			y = Math.max(4, screenH - 4 - hh);
		}
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = hh;
	}

	boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	private double maxScroll() {
		return Math.max(0, entries.size() * ROW + 8 - h);
	}

	private int indexAt(double my) {
		int i = (int) ((my - y - 4 + scroll) / ROW);
		return i >= 0 && i < entries.size() ? i : -1;
	}

	void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
		g.nextStratum();
		Ui.panel(g, x - 2, y - 2, w + 4, h + 4);
		g.enableScissor(x, y + 2, x + w, y + h - 2);
		int hot = contains(mx, my) ? indexAt(my) : -1;
		for (int i = 0; i < entries.size(); i++) {
			Entry e = entries.get(i);
			int ey = y + 4 + i * ROW - (int) scroll;
			if (e.action() == null) {
				g.text(font, e.label(), x + 4, ey + 3, Ui.ACCENT, false);
				continue;
			}
			if (i == hot) {
				g.fill(x + 2, ey, x + w - 8, ey + ROW, 0x40FFFFFF);
			}
			g.text(font, e.label(), x + 8, ey + 3, Ui.TEXT, false);
			if (e.detail() != null) {
				int lw = font.width(e.label());
				g.text(font, Ui.ellipsize(font, e.detail().getString(), w - lw - 24), x + 14 + lw, ey + 3, Ui.FAINT, false);
			}
		}
		g.disableScissor();
		Ui.scrollbar(g, x + w - 7, y + 2, h - 4, scroll, maxScroll(), entries.size() * ROW + 8);
	}

	/** Runs the clicked entry; returns false when the click was outside (the caller closes the list). */
	boolean click(double mx, double my) {
		if (!contains(mx, my)) {
			return false;
		}
		int i = indexAt(my);
		if (i >= 0 && entries.get(i).action() != null) {
			entries.get(i).action().run();
		}
		return true;
	}

	void scroll(double amount) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - amount * ROW * 2));
	}

	/** Centre of an entry, scrolled into view (for tests). */
	int[] center(int index) {
		double top = index * ROW, view = h - 8;
		if (top < scroll || top + ROW > scroll + view) {
			scroll = Math.max(0, Math.min(maxScroll(), top - view / 2));
		}
		return new int[]{x + w / 2, y + 4 + index * ROW - (int) scroll + ROW / 2};
	}

	List<Entry> entries() {
		return entries;
	}
}
