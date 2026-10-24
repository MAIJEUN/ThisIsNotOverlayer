package dev.customhud.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.CustomHudClient;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudObject;
import dev.customhud.hud.HudRenderer;
import dev.customhud.hud.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The editor window (Right Shift), like Overlayer's but in Minecraft's style: a movable, resizable
 * panel with tabs — Presets (tiles with live previews), Edit (the objects of one preset) and Tags.
 * Outside the window the HUD stays visible; while editing, its objects can be picked and dragged there.
 */
public class HudScreen extends Screen implements EditingScreen {
	static final int TITLE = 22;
	private static final int HINT = 11;
	private static final int GRIP = 8;

	public enum Tab { PRESETS, EDIT, TAGS }

	private static int[] rect;
	private static int[] restoreRect;
	private static boolean minimized;
	private static Tab lastTab = Tab.PRESETS;
	private static Preset lastPreset;

	private final Minecraft mc = Minecraft.getInstance();
	final PresetsPage presetsPage = new PresetsPage(this);
	final EditPage editPage = new EditPage(this);
	final TagsPage tagsPage = new TagsPage(this);
	private Tab tab = Tab.PRESETS;
	private Preset editing;
	private ListPopup popup;
	private final List<AbstractWidget> pageWidgets = new ArrayList<>();

	private int dragMode; // 1 move, 2 resize
	private double pressX, pressY;
	private int[] pressRect;
	private long lastTitleClick;

	public HudScreen() {
		super(Component.translatable("screen.customhud.title"));
		if (lastPreset != null && HudManager.get().presets().contains(lastPreset)) {
			editing = lastPreset;
			tab = lastTab;
		}
	}

	// ---- state ----

	public Tab tab() {
		return tab;
	}

	public Preset editing() {
		return editing;
	}

	private Page current() {
		return switch (tab) {
			case PRESETS -> presetsPage;
			case EDIT -> editPage;
			case TAGS -> tagsPage;
		};
	}

	public void showTab(Tab t) {
		if (t == Tab.EDIT && editing == null) {
			return;
		}
		tab = t;
		lastTab = t;
		popup = null;
		rebuild();
	}

	/** Opens a preset in the Edit tab (optionally selecting one of its objects). */
	public void edit(Preset p, HudObject select) {
		if (p != editing) {
			editPage.select(null);
		}
		editing = p;
		lastPreset = p;
		HudRenderer.editingPreset = p;
		if (select != null) {
			editPage.select(select);
		}
		showTab(Tab.EDIT);
	}

	/** Leaves the Edit tab when its preset is deleted. */
	void forget(Preset p) {
		if (editing == p) {
			editing = null;
			lastPreset = null;
			HudRenderer.editingPreset = null;
			if (tab == Tab.EDIT) {
				showTab(Tab.PRESETS);
			}
		}
	}

	void openPopup(ListPopup p) {
		popup = p;
	}

	/** Rebuilds the vanilla widgets of the current tab (after the page's contents change). */
	void rebuild() {
		pageWidgets.forEach(this::removeWidget);
		pageWidgets.clear();
		setFocused(null);
		current().init();
	}

	<T extends AbstractWidget> T widget(T w) {
		pageWidgets.add(w);
		addRenderableWidget(w);
		return w;
	}

	void removePageWidget(AbstractWidget w) {
		pageWidgets.remove(w);
		removeWidget(w);
	}

	void focus(GuiEventListener w) {
		setFocused(w);
	}

	GuiEventListener focused() {
		return getFocused();
	}

	boolean typing() {
		return getFocused() instanceof EditBox || getFocused() instanceof MultiLineEditBox;
	}

	// ---- window geometry ----

	private void defaultRect() {
		int w = Math.max(Math.min(width - 16, 340), Math.min(width - 16, (int) (width * 0.5)));
		int h = Math.max(Math.min(height - 16, 220), Math.min(height - 16, (int) (height * 0.7)));
		rect = new int[]{width - w - 8, (height - h) / 2, w, h};
	}

	private void clampRect() {
		rect[2] = Math.max(Math.min(260, width), Math.min(rect[2], width));
		rect[3] = Math.max(Math.min(160, height), Math.min(rect[3], height));
		rect[0] = Math.max(-rect[2] + 60, Math.min(width - 60, rect[0]));
		rect[1] = Math.max(0, Math.min(height - TITLE, rect[1]));
	}

	int wx() {
		return rect[0];
	}

	int wy() {
		return rect[1];
	}

	int ww() {
		return rect[2];
	}

	int wh() {
		return minimized ? TITLE + 2 : rect[3];
	}

	boolean inWindow(double mx, double my) {
		return mx >= wx() && mx < wx() + ww() && my >= wy() && my < wy() + wh();
	}

	private int[] closeButton() {
		int x = wx() + ww() - 18;
		return new int[]{x, wy() + 5, x + 12, wy() + 17};
	}

	private int[] minimizeButton() {
		int x = wx() + ww() - 34;
		return new int[]{x, wy() + 5, x + 12, wy() + 17};
	}

	private int tabX(int i) {
		int titleW = font.width(title) + 16;
		return wx() + titleW + i * 64;
	}

	private int tabAt(double mx, double my) {
		if (my < wy() + 3 || my >= wy() + TITLE) {
			return -1;
		}
		for (int i = 0; i < Tab.values().length; i++) {
			if (mx >= tabX(i) && mx < tabX(i) + 62) {
				return i;
			}
		}
		return -1;
	}

	private boolean onGrip(double mx, double my) {
		return !minimized && mx >= wx() + ww() - GRIP && mx < wx() + ww() && my >= wy() + wh() - GRIP && my < wy() + wh();
	}

	private void layoutPage() {
		int hint = current().hint() != null ? HINT : 0;
		current().layout(wx() + 7, wy() + TITLE + 4, ww() - 14, wh() - TITLE - 11 - hint);
		if (minimized) {
			pageWidgets.forEach(w -> w.visible = false);
		}
	}

	// ---- lifecycle ----

	@Override
	protected void init() {
		if (rect == null) {
			defaultRect();
		}
		clampRect();
		pageWidgets.clear();
		HudRenderer.editingPreset = editing;
		current().init();
	}

	@Override
	public void onClose() {
		HudManager.get().save();
		super.onClose();
	}

	@Override
	public void removed() {
		HudManager.get().save();
		HudRenderer.editingPreset = null;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---- rendering ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float delta) {
		// No dimming: the HUD being edited stays as it is.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float delta) {
		if (tab == Tab.EDIT) {
			editPage.renderWorld(g, mx, my);
		}
		g.nextStratum();
		Ui.panel(g, wx(), wy(), ww(), wh());
		g.text(font, title, wx() + 8, wy() + 8, Ui.TEXT, true);
		for (int i = 0; i < Tab.values().length; i++) {
			Tab t = Tab.values()[i];
			boolean enabled = t != Tab.EDIT || editing != null;
			Component label = Component.translatable("screen.customhud.tab." + t.name().toLowerCase(java.util.Locale.ROOT));
			if (t == Tab.EDIT && editing != null) {
				label = Component.literal(Ui.ellipsize(font, editing.name, 54));
			}
			boolean hot = enabled && tabAt(mx, my) == i;
			Ui.tab(g, font, tabX(i), wy() + 3, 62, TITLE - 3, label, t == tab, hot);
		}
		int[] min = minimizeButton();
		Ui.button(g, font, min, Component.literal(minimized ? "□" : "_"), Ui.hover(mx, my, min), true);
		int[] close = closeButton();
		Ui.sprite(g, Ui.hover(mx, my, close) ? Ui.CROSS_HOVER : Ui.CROSS, close[0], close[1], 12, 12);
		if (!minimized) {
			Ui.separator(g, wx() + 3, wy() + TITLE, ww() - 6);
		}
		layoutPage();
		if (!minimized) {
			current().render(g, mx, my);
			Component hint = current().hint();
			if (hint != null) {
				g.text(font, Ui.ellipsize(font, hint.getString(), ww() - 24), wx() + 8, wy() + wh() - HINT - 3, Ui.FAINT, false);
			}
			// resize grip
			int gx = wx() + ww() - 4, gy = wy() + wh() - 4;
			for (int i = 0; i < 3; i++) {
				g.fill(gx - 2 - i * 2, gy - 1, gx - 1 - i * 2, gy, 0xFF8B8B8B);
				g.fill(gx - 1, gy - 2 - i * 2, gx, gy - 1 - i * 2, 0xFF8B8B8B);
			}
		}
		super.extractRenderState(g, mx, my, delta);
		if (!minimized) {
			current().renderOverlay(g, mx, my);
		}
		if (popup != null) {
			popup.render(g, font, mx, my);
		}
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x(), my = event.y();
		if (popup != null) {
			ListPopup p = popup;
			popup = null;
			if (p.click(mx, my)) {
				return true;
			}
			if (inWindow(mx, my)) {
				return true;
			}
		}
		if (inWindow(mx, my)) {
			boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
			if (left && Ui.hover(mx, my, closeButton())) {
				onClose();
				return true;
			}
			if (left && Ui.hover(mx, my, minimizeButton())) {
				minimized = !minimized;
				setFocused(null);
				return true;
			}
			int t = tabAt(mx, my);
			if (left && t >= 0) {
				Tab target = Tab.values()[t];
				if (target != Tab.EDIT || editing != null) {
					showTab(target);
				}
				return true;
			}
			if (left && onGrip(mx, my)) {
				startDrag(2, mx, my);
				return true;
			}
			if (my < wy() + TITLE) {
				if (left) {
					long now = System.currentTimeMillis();
					if (now - lastTitleClick < 300) {
						toggleMaximize();
					} else {
						startDrag(1, mx, my);
					}
					lastTitleClick = now;
				}
				return true;
			}
			if (minimized) {
				return true;
			}
			if (super.mouseClicked(event, doubleClick)) {
				return true;
			}
			setFocused(null);
			current().mouseClicked(event, doubleClick);
			return true;
		}
		setFocused(null);
		if (tab == Tab.EDIT) {
			return editPage.worldClicked(event);
		}
		return presetsPage.worldClicked(event);
	}

	private void startDrag(int mode, double mx, double my) {
		dragMode = mode;
		pressX = mx;
		pressY = my;
		pressRect = rect.clone();
	}

	private void toggleMaximize() {
		if (restoreRect == null) {
			restoreRect = rect.clone();
			rect = new int[]{0, 0, width, height};
		} else {
			rect = restoreRect;
			restoreRect = null;
		}
		minimized = false;
		clampRect();
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragMode == 1) {
			rect[0] = pressRect[0] + (int) (event.x() - pressX);
			rect[1] = pressRect[1] + (int) (event.y() - pressY);
			restoreRect = null;
			clampRect();
			return true;
		}
		if (dragMode == 2) {
			rect[2] = pressRect[2] + (int) (event.x() - pressX);
			rect[3] = pressRect[3] + (int) (event.y() - pressY);
			restoreRect = null;
			clampRect();
			return true;
		}
		if (tab == Tab.EDIT && editPage.worldDragging()) {
			editPage.worldDragged(event);
			return true;
		}
		if (current().mouseDragged(event)) {
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragMode != 0) {
			dragMode = 0;
			return true;
		}
		if (tab == Tab.EDIT && editPage.worldDragging()) {
			editPage.worldReleased(event);
			return true;
		}
		if (current().mouseReleased(event)) {
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (popup != null && popup.contains(mx, my)) {
			popup.scroll(sy);
			return true;
		}
		if (inWindow(mx, my) && !minimized) {
			if (getFocused() instanceof MultiLineEditBox box && box.isMouseOver(mx, my)) {
				return super.mouseScrolled(mx, my, sx, sy);
			}
			return current().mouseScrolled(mx, my, sy) || super.mouseScrolled(mx, my, sx, sy);
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = InputConstants.getKey(event).getValue();
		if (typing()) {
			if (key == InputConstants.KEY_ESCAPE) {
				setFocused(null);
				return true;
			}
			if (current().keyPressed(event)) {
				return true;
			}
			return super.keyPressed(event);
		}
		if (key == InputConstants.KEY_ESCAPE && popup != null) {
			popup = null;
			return true;
		}
		if (CustomHudClient.openEditorKey.matches(event) || key == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (key == InputConstants.KEY_TAB) {
			minimized = !minimized;
			return true;
		}
		if (current().keyPressed(event)) {
			return true;
		}
		return super.keyPressed(event);
	}

	// ---- test hooks ----

	public int[] tabCenter(Tab t) {
		return new int[]{tabX(t.ordinal()) + 31, wy() + 12};
	}

	public int[] popupCenter(int index) {
		return popup == null ? null : popup.center(index);
	}

	public List<String> popupLabels() {
		return popup == null ? List.of() : popup.entries().stream().map(e -> e.label().getString()).toList();
	}

	/** Centre of the popup entry for a template ({@code "text"}, {@code "armor"}…). */
	public int[] popupTemplate(String id) {
		String label = Component.translatable("template.customhud." + id).getString();
		int i = popupLabels().indexOf(label);
		return i < 0 ? null : popupCenter(i);
	}

	public int[] presetTile(int index) {
		return presetsPage.tileCenter(index);
	}

	public int[] presetCheckbox(int index) {
		return presetsPage.checkboxCenter(index);
	}

	/** The "new" tile's buttons: 0 new, 1 from a template, 2 paste. */
	public int[] newPresetButton(int index) {
		return presetsPage.newTileButton(index);
	}

	public int[] addButtonCenter() {
		return editPage.addButtonCenter();
	}

	public int[] rowCenter(HudObject o, String part) {
		return editPage.rowCenter(o, part);
	}

	public HudObject selected() {
		return editPage.selected();
	}

	public java.util.Set<HudObject> selection() {
		return editPage.selection();
	}

	public void select(HudObject o) {
		editPage.select(o);
	}

	public int[] fxToggleCenter(String propId) {
		SettingsPanel p = editPage.panel();
		return p == null ? null : p.fxToggleCenter(propId);
	}

	/** Scrolls a setting into view and returns its text box (Fx or typed value). */
	public EditBox settingBox(String propId) {
		SettingsPanel p = editPage.panel();
		if (p == null) {
			return null;
		}
		p.reveal(propId);
		EditBox fx = p.fxBox(propId);
		return fx != null ? fx : p.fieldBox(propId);
	}

	public MultiLineEditBox templateBox() {
		SettingsPanel p = editPage.panel();
		return p == null ? null : p.templateBox();
	}

	/** Moves and resizes the window. */
	public void setWindow(int x, int y, int w, int h) {
		rect = new int[]{x, y, w, h};
		restoreRect = null;
		minimized = false;
		clampRect();
	}
}
