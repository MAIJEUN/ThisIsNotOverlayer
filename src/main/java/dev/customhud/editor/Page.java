package dev.customhud.editor;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/** One tab of the editor window. Its vanilla widgets are added in {@link #init} and placed every frame in {@link #layout}. */
abstract class Page {
	protected final HudScreen screen;
	protected int x, y, w, h;

	Page(HudScreen screen) {
		this.screen = screen;
	}

	/** Adds the page's vanilla widgets (through {@link HudScreen#widget}). */
	void init() {
	}

	void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
	}

	abstract void render(GuiGraphicsExtractor g, int mx, int my);

	/** Drawn over the vanilla widgets (lists that pop up). */
	void renderOverlay(GuiGraphicsExtractor g, int mx, int my) {
	}

	/** One line of help at the bottom of the window. */
	net.minecraft.network.chat.Component hint() {
		return null;
	}

	boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		return false;
	}

	boolean mouseDragged(MouseButtonEvent e) {
		return false;
	}

	boolean mouseReleased(MouseButtonEvent e) {
		return false;
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		return false;
	}

	boolean keyPressed(KeyEvent e) {
		return false;
	}

	boolean contains(double mx, double my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}
}
