package dev.customhud.editor;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/** Minecraft's own GUI sprites and colours, for the editor window. */
final class Ui {
	static final int TEXT = 0xFFFFFFFF;
	static final int MUTED = 0xFFA0A0A0;
	static final int FAINT = 0xFF707070;
	static final int ACCENT = 0xFFFFFF55;
	static final int SELECT = 0xFF5B8CFF;
	static final int SELECT_DIM = 0xFF3F5FAF;
	static final int GUIDE = 0xA05B8CFF;
	static final int DANGER = 0xFFFF5555;
	static final int OK = 0xFF55FF55;
	static final int INSET = 0xFF1A1A1A;
	static final int ROW = 0xFF2A2A2A;
	static final int ROW_HOVER = 0xFF3A3A3A;
	static final int ROW_SELECTED = 0xFF2F4A86;

	static final Identifier PANEL = Identifier.withDefaultNamespace("popup/background");
	static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	static final Identifier BUTTON_OFF = Identifier.withDefaultNamespace("widget/button_disabled");
	static final Identifier TAB = Identifier.withDefaultNamespace("widget/tab");
	static final Identifier TAB_HOVER = Identifier.withDefaultNamespace("widget/tab_highlighted");
	static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("widget/tab_selected");
	static final Identifier TAB_SELECTED_HOVER = Identifier.withDefaultNamespace("widget/tab_selected_highlighted");
	static final Identifier CHECKBOX = Identifier.withDefaultNamespace("widget/checkbox");
	static final Identifier CHECKBOX_HOVER = Identifier.withDefaultNamespace("widget/checkbox_highlighted");
	static final Identifier CHECKBOX_ON = Identifier.withDefaultNamespace("widget/checkbox_selected");
	static final Identifier CHECKBOX_ON_HOVER = Identifier.withDefaultNamespace("widget/checkbox_selected_highlighted");
	static final Identifier CROSS = Identifier.withDefaultNamespace("widget/cross_button");
	static final Identifier CROSS_HOVER = Identifier.withDefaultNamespace("widget/cross_button_highlighted");
	static final Identifier SLIDER = Identifier.withDefaultNamespace("widget/slider");
	static final Identifier SLIDER_HANDLE = Identifier.withDefaultNamespace("widget/slider_handle");
	static final Identifier SLIDER_HANDLE_HOVER = Identifier.withDefaultNamespace("widget/slider_handle_highlighted");
	static final Identifier SCROLLER = Identifier.withDefaultNamespace("widget/scroller");
	static final Identifier SCROLLER_BG = Identifier.withDefaultNamespace("widget/scroller_background");
	static final Identifier HEADER_SEPARATOR = Identifier.withDefaultNamespace("textures/gui/inworld_header_separator.png");

	private Ui() {
	}

	static boolean hover(double mx, double my, int[] box) {
		return mx >= box[0] && mx < box[2] && my >= box[1] && my < box[3];
	}

	static boolean hover(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	static void sprite(GuiGraphicsExtractor g, Identifier sprite, int x, int y, int w, int h) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
	}

	/** The grey bevelled panel of Minecraft's pop-ups. */
	static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		sprite(g, PANEL, x, y, w, h);
	}

	/** A dark sunken area (lists, previews). */
	static void inset(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, 0xFF000000);
		g.fill(x + 1, y + 1, x + w, y + h, 0xFF5A5A5A);
		g.fill(x + 1, y + 1, x + w - 1, y + h - 1, INSET);
	}

	static void separator(GuiGraphicsExtractor g, int x, int y, int w) {
		g.blit(RenderPipelines.GUI_TEXTURED, HEADER_SEPARATOR, x, y, 0, 0, w, 2, 32, 2);
	}

	/** A vanilla button drawn at any size, with a label or symbol in the middle. */
	static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean hover, boolean enabled) {
		sprite(g, !enabled ? BUTTON_OFF : hover ? BUTTON_HOVER : BUTTON, x, y, w, h);
		int color = enabled ? TEXT : FAINT;
		g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, color);
	}

	static void button(GuiGraphicsExtractor g, Font font, int[] b, Component label, boolean hover, boolean enabled) {
		button(g, font, b[0], b[1], b[2] - b[0], b[3] - b[1], label, hover, enabled);
	}

	static void checkbox(GuiGraphicsExtractor g, int x, int y, int size, boolean on, boolean hover) {
		sprite(g, on ? (hover ? CHECKBOX_ON_HOVER : CHECKBOX_ON) : (hover ? CHECKBOX_HOVER : CHECKBOX), x, y, size, size);
	}

	static void tab(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, Component label, boolean selected, boolean hover) {
		sprite(g, selected ? (hover ? TAB_SELECTED_HOVER : TAB_SELECTED) : (hover ? TAB_HOVER : TAB), x, y, w, h);
		g.centeredText(font, label, x + w / 2, y + (h - 8) / 2 + 1, selected ? TEXT : MUTED);
	}

	/** A vanilla slider: track plus handle at {@code t} (0..1). */
	static void slider(GuiGraphicsExtractor g, int x, int y, int w, int h, double t, boolean hover) {
		sprite(g, SLIDER, x, y, w, h);
		int hx = x + (int) Math.round((w - 8) * Math.max(0, Math.min(1, t)));
		sprite(g, hover ? SLIDER_HANDLE_HOVER : SLIDER_HANDLE, hx, y, 8, h);
	}

	static void scrollbar(GuiGraphicsExtractor g, int x, int y, int h, double scroll, double max, int content) {
		if (max <= 0) {
			return;
		}
		sprite(g, SCROLLER_BG, x, y, 6, h);
		int barH = Math.max(12, h * h / Math.max(1, content));
		int barY = y + (int) ((h - barH) * (scroll / max));
		sprite(g, SCROLLER, x, barY, 6, barH);
	}

	/** Cuts {@code text} to fit {@code maxWidth}, adding "…" when shortened. */
	static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String dots = "…";
		int end = text.length();
		while (end > 0 && font.width(text.substring(0, end) + dots) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + dots;
	}
}
