package dev.customhud.editor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Every tag, searchable; clicking one copies {@code {Tag}} to the clipboard. */
final class TagsPage extends Page {
	private final TagList list = new TagList(tag -> Minecraft.getInstance().keyboardHandler.setClipboard(TagText.forText(tag)));

	TagsPage(HudScreen screen) {
		super(screen);
	}

	@Override
	Component hint() {
		return Component.translatable("screen.customhud.tags.hint");
	}

	@Override
	void init() {
		list.init(screen);
	}

	@Override
	void layout(int x, int y, int w, int h) {
		super.layout(x, y, w, h);
		list.layout(x, y, w, h);
	}

	@Override
	void render(GuiGraphicsExtractor g, int mx, int my) {
		list.render(g, mx, my);
	}

	@Override
	boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		return list.click(e.x(), e.y());
	}

	@Override
	boolean mouseScrolled(double mx, double my, double amount) {
		return list.scroll(mx, my, amount);
	}
}
