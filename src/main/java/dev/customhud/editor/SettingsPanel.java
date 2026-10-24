package dev.customhud.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.fx.NCalc;
import dev.customhud.hud.Anchor;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudObject;
import dev.customhud.mixin.MultiLineEditBoxAccessor;
import dev.customhud.prop.BoolProp;
import dev.customhud.prop.ColorProp;
import dev.customhud.prop.EnumProp;
import dev.customhud.prop.NumberProp;
import dev.customhud.prop.Prop;
import dev.customhud.prop.StringProp;
import dev.customhud.prop.TextProp;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.compile.DotNet;
import dev.customhud.tag.core.TagCore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The selected object's settings, inside the window: name, content (text, item, block, entity),
 * position, and background / outline. Each setting has an "ƒ" toggle that swaps it for an Fx expression.
 */
final class SettingsPanel {
	private static final int FX_SIZE = 11;
	private static final int TEMPLATE_HEIGHT = 60;
	private static final int FOOTER = 22;
	private static final long DELETE_CONFIRM_MS = 3000;
	private static final String[] CHANNELS = {"R", "G", "B", "A"};
	private static final int[] CHANNEL_COLORS = {0xFFFF5555, 0xFF55FF55, 0xFF5599FF, 0xFFDDDDDD};

	private enum Kind { HEADER, NAME, TEMPLATE, TEMPLATE_STATUS, BOOL, ALIGN, NUMBER, FIELD, STRING, COLOR, COLOR_CHANNEL, FX }

	private record Row(Kind kind, Prop<?> prop, Component text, int sub, int y, int height) {
	}

	private final EditPage page;
	private final HudObject object;
	private final Minecraft mc = Minecraft.getInstance();
	private final Font font = mc.font;
	private final Set<ColorProp> expanded = new HashSet<>();
	private HudScreen screen;

	private EditBox nameBox;
	private MultiLineEditBox templateBox;
	private final Map<Prop<?>, EditBox> fxBoxes = new LinkedHashMap<>();
	private final Map<Prop<?>, EditBox> fieldBoxes = new LinkedHashMap<>();
	private Button tagsButton, copyButton, deleteButton;
	private Object lastTextTarget;
	private TagList tagPicker;
	private Button backButton;
	private boolean picking;

	private int x, y, w, h;
	private double scroll;
	private Prop<?> activeSlider;
	private int activeChannel = -1;
	private long deleteArmedAt;

	SettingsPanel(EditPage page, HudObject object) {
		this.page = page;
		this.object = object;
	}

	HudObject object() {
		return object;
	}

	private List<Prop<?>> allProps() {
		List<Prop<?>> l = new ArrayList<>(object.contentProps());
		l.add(object.visible);
		l.addAll(object.transformProps());
		l.addAll(object.boxProps());
		return l;
	}

	private static boolean isField(Prop<?> p) {
		return p instanceof StringProp || (p instanceof NumberProp && (p.id().equals("x") || p.id().equals("y")));
	}

	// ---- widgets ----

	void init(HudScreen screen) {
		this.screen = screen;
		fxBoxes.clear();
		fieldBoxes.clear();
		nameBox = screen.widget(new EditBox(font, 0, 0, 100, 16, Component.translatable("screen.customhud.settings.name")));
		nameBox.setMaxLength(64);
		nameBox.setValue(object.name);
		nameBox.setHint(Component.translatable("type.customhud." + object.type.name().toLowerCase(Locale.ROOT)));
		nameBox.setResponder(s -> object.name = s);
		if (object.type == HudObject.Type.TEXT) {
			templateBox = screen.widget(MultiLineEditBox.builder()
					.setPlaceholder(Component.translatable("screen.customhud.settings.template_hint"))
					.build(font, 100, TEMPLATE_HEIGHT, Component.translatable("prop.customhud.text")));
			templateBox.setCharacterLimit(4000);
			templateBox.setValue(object.text.fixedValue());
			templateBox.setValueListener(object.text::set);
			lastTextTarget = templateBox;
		} else {
			templateBox = null;
		}
		for (Prop<?> p : allProps()) {
			if (p.hasFx()) {
				fxBoxes.put(p, screen.widget(createFxBox(p)));
			} else if (isField(p)) {
				fieldBoxes.put(p, screen.widget(createFieldBox(p)));
			}
		}
		tagsButton = screen.widget(Button.builder(Component.translatable("screen.customhud.settings.tags"), b -> showTags(true)).build());
		copyButton = screen.widget(Button.builder(Component.translatable("screen.customhud.settings.duplicate"), b -> page.duplicateSelected()).build());
		deleteButton = screen.widget(Button.builder(Component.translatable("screen.customhud.settings.delete"), b -> {
			if (deleteArmed()) {
				page.remove(object);
			} else {
				deleteArmedAt = System.currentTimeMillis();
			}
		}).build());
		tagPicker = new TagList(this::insertTag);
		tagPicker.init(screen);
		backButton = screen.widget(Button.builder(Component.translatable("screen.customhud.settings.back"), b -> showTags(false)).build());
	}

	void focusName() {
		screen.focus(nameBox);
		nameBox.moveCursorToEnd(false);
	}

	private void showTags(boolean on) {
		picking = on;
		if (on) {
			screen.focus(tagPicker.searchBox());
		} else if (lastTextTarget instanceof AbstractWidget w) {
			screen.focus(w);
		}
	}

	private EditBox createFxBox(Prop<?> prop) {
		EditBox box = new EditBox(font, 0, 0, 100, 14, prop.label());
		box.setMaxLength(2000);
		box.setValue(prop.fxSource());
		box.setResponder(prop::setFx);
		return box;
	}

	/** Typed values: X / Y (numbers or simple maths like 16*3) and item / block / entity ids (Tab completes). */
	private EditBox createFieldBox(Prop<?> prop) {
		EditBox box = new EditBox(font, 0, 0, 80, 14, prop.label());
		box.setMaxLength(256);
		if (prop instanceof NumberProp n) {
			box.setValue(DotNet.toString(n.fixedValue()));
			box.setResponder(s -> {
				Double v = number(s);
				box.setTextColor(v == null ? Ui.DANGER : 0xFFE0E0E0);
				if (v != null) {
					n.set(v);
				}
			});
		} else if (prop instanceof StringProp s) {
			box.setValue(s.fixedValue());
			box.setResponder(v -> {
				s.set(v);
				box.setSuggestion(idSuggestion(s, v));
			});
		}
		return box;
	}

	private static Double number(String s) {
		try {
			return Double.parseDouble(s.trim());
		} catch (NumberFormatException e) {
			try {
				return ((Number) DotNet.changeType(NCalc.evaluate(s), Double.class)).doubleValue();
			} catch (RuntimeException ex) {
				return null;
			}
		}
	}

	/** The rest of the first id starting with what was typed ({@code diam} → {@code ond_sword}…), or null. */
	static String idSuggestion(StringProp prop, String typed) {
		String t = typed.trim().toLowerCase(Locale.ROOT);
		if (t.isEmpty() || t.contains("[")) {
			return null;
		}
		Set<Identifier> keys = switch (prop.id()) {
			case "block" -> BuiltInRegistries.BLOCK.keySet();
			case "entity" -> BuiltInRegistries.ENTITY_TYPE.keySet();
			default -> BuiltInRegistries.ITEM.keySet();
		};
		String best = prop.id().equals("entity") && "player".startsWith(t) && !t.equals("player") ? "player" : null;
		for (Identifier id : keys) {
			String candidate = t.contains(":") ? id.toString() : id.getPath();
			if (candidate.startsWith(t) && !candidate.equals(t) && (best == null || candidate.compareTo(best) < 0)) {
				best = candidate;
			}
		}
		return best == null ? null : best.substring(t.length());
	}

	// ---- layout ----

	private List<Row> layout() {
		List<Row> rows = new ArrayList<>();
		int[] yy = {0};
		add(rows, yy, new Row(Kind.NAME, null, null, -1, 0, 20));
		if (object.type == HudObject.Type.TEXT) {
			add(rows, yy, new Row(Kind.TEMPLATE, object.text, null, -1, 0, TEMPLATE_HEIGHT + 4));
			add(rows, yy, new Row(Kind.TEMPLATE_STATUS, object.text, null, -1, 0, 12 + 10 * Math.min(3, object.text.diagnostics().size())));
		}
		List<Prop<?>> content = object.contentProps().stream().filter(p -> !(p instanceof TextProp) && p.isVisible()).toList();
		if (!content.isEmpty()) {
			add(rows, yy, new Row(Kind.HEADER, null, Component.translatable("screen.customhud.settings.section.content"), -1, 0, 16));
			content.forEach(p -> addProp(rows, yy, p));
		}
		add(rows, yy, new Row(Kind.HEADER, null, Component.translatable("screen.customhud.settings.section.position"), -1, 0, 16));
		addProp(rows, yy, object.visible);
		object.transformProps().forEach(p -> addProp(rows, yy, p));
		add(rows, yy, new Row(Kind.HEADER, null, Component.translatable("screen.customhud.settings.section.box"), -1, 0, 16));
		object.boxProps().stream().filter(Prop::isVisible).forEach(p -> addProp(rows, yy, p));
		return rows;
	}

	private void addProp(List<Row> rows, int[] yy, Prop<?> p) {
		if (p.hasFx()) {
			add(rows, yy, new Row(Kind.FX, p, null, -1, 0, p.fxError() != null ? 40 : 30));
			return;
		}
		Kind kind = switch (p) {
			case BoolProp b -> Kind.BOOL;
			case EnumProp<?> e -> Kind.ALIGN;
			case StringProp s -> Kind.STRING;
			case NumberProp n -> isField(n) ? Kind.FIELD : Kind.NUMBER;
			case ColorProp c -> Kind.COLOR;
			default -> throw new IllegalStateException("Unknown property " + p);
		};
		int rh = switch (kind) {
			case NUMBER -> 24;
			case ALIGN, STRING -> 30;
			default -> 16;
		};
		add(rows, yy, new Row(kind, p, null, -1, 0, rh));
		if (p instanceof ColorProp c && expanded.contains(c)) {
			for (int i = 0; i < 4; i++) {
				add(rows, yy, new Row(Kind.COLOR_CHANNEL, p, null, i, 0, 14));
			}
		}
	}

	private static void add(List<Row> rows, int[] yy, Row row) {
		rows.add(new Row(row.kind(), row.prop(), row.text(), row.sub(), yy[0], row.height()));
		yy[0] += row.height();
	}

	private int top() {
		return y;
	}

	private int bottom() {
		return y + h - FOOTER;
	}

	private int contentHeight(List<Row> rows) {
		return rows.isEmpty() ? 0 : rows.getLast().y() + rows.getLast().height() + 4;
	}

	private void clampScroll(List<Row> rows) {
		scroll = Math.max(0, Math.min(Math.max(0, contentHeight(rows) - (bottom() - top())), scroll));
	}

	private int screenY(Row row) {
		return top() + row.y() - (int) scroll;
	}

	private Row rowAt(double mx, double my) {
		if (mx < x || mx >= x + w || my < top() || my >= bottom()) {
			return null;
		}
		for (Row row : layout()) {
			int ry = screenY(row);
			if (my >= ry && my < ry + row.height()) {
				return row;
			}
		}
		return null;
	}

	private int right() {
		return x + w - 8;
	}

	private int[] fxToggleBox(Row row) {
		int ry = screenY(row) + 2;
		return new int[]{right() - FX_SIZE, ry, right(), ry + FX_SIZE};
	}

	private int controlRight() {
		return right() - FX_SIZE - 4;
	}

	private int trackStart(Row row) {
		return x + (row.kind() == Kind.COLOR_CHANNEL ? 18 : 4);
	}

	private int trackEnd(Row row) {
		return row.kind() == Kind.COLOR_CHANNEL ? controlRight() - 22 : controlRight();
	}

	private int[] alignGrid(Row row) {
		return new int[]{controlRight() - 26, screenY(row) + 2, 9};
	}

	void layout(int x, int y, int w, int h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		boolean show = !picking;
		List<Row> rows = layout();
		clampScroll(rows);
		Set<Prop<?>> placedFx = new HashSet<>();
		Set<Prop<?>> placedFields = new HashSet<>();
		nameBox.visible = false;
		if (templateBox != null) {
			templateBox.visible = false;
		}
		for (Row row : rows) {
			int ry = screenY(row);
			switch (row.kind()) {
				case NAME -> place(nameBox, x + 4, ry + 2, w - 12, 16, show);
				case TEMPLATE -> place(templateBox, x + 4, ry + 2, w - 12, TEMPLATE_HEIGHT, show);
				case FX -> {
					// Fx may have been switched on elsewhere (a pasted preset, a script…).
					EditBox box = fxBoxes.computeIfAbsent(row.prop(), p -> screen.widget(createFxBox(p)));
					if (box != null) {
						place(box, x + 4, ry + 13, w - 12, 14, show);
						placedFx.add(row.prop());
					}
				}
				case FIELD, STRING -> {
					EditBox box = fieldBoxes.computeIfAbsent(row.prop(), p -> screen.widget(createFieldBox(p)));
					if (box != null) {
						if (row.kind() == Kind.FIELD) {
							place(box, controlRight() - 70, ry + 1, 70, 14, show);
						} else {
							place(box, x + 4, ry + 13, w - 12 - FX_SIZE - 4, 14, show);
						}
						placedFields.add(row.prop());
					}
				}
				default -> {
				}
			}
		}
		fxBoxes.forEach((p, b) -> b.visible = b.visible && placedFx.contains(p));
		fieldBoxes.forEach((p, b) -> b.visible = b.visible && placedFields.contains(p));
		int bw = (w - 8) / 3;
		int by = y + h - FOOTER + 2;
		placeButton(tagsButton, x, by, bw - 2, show);
		placeButton(copyButton, x + bw + 2, by, bw - 2, show);
		placeButton(deleteButton, x + (bw + 2) * 2, by, bw - 2, show);
		deleteButton.setMessage(Component.translatable(deleteArmed() ? "screen.customhud.settings.confirm_delete" : "screen.customhud.settings.delete"));
		tagPicker.layout(x, y, w, h - FOOTER);
		tagPicker.searchBox().visible = picking;
		placeButton(backButton, x, by, w, picking);
	}

	private void place(AbstractWidget wdg, int px, int py, int pw, int ph, boolean show) {
		wdg.setX(px);
		wdg.setY(py);
		wdg.setWidth(pw);
		wdg.visible = show && py >= top() && py + ph <= bottom();
	}

	private static void placeButton(Button b, int bx, int by, int bw, boolean show) {
		b.setX(bx);
		b.setY(by);
		b.setWidth(bw);
		b.setHeight(18);
		b.visible = show;
	}

	private boolean deleteArmed() {
		return System.currentTimeMillis() - deleteArmedAt < DELETE_CONFIRM_MS;
	}

	/** Inserts a tag where the user was last typing: {@code {Tag}} in the text, {@code Tag()} in Fx. */
	void insertTag(TagCore tag) {
		showTags(false);
		if (lastTextTarget instanceof EditBox box && fxBoxes.containsValue(box)) {
			box.insertText(TagText.forJs(tag));
			screen.focus(box);
		} else if (templateBox != null) {
			((MultiLineEditBoxAccessor) templateBox).customhud$textField().insertText(TagText.forText(tag));
			screen.focus(templateBox);
		}
	}

	void noteFocus() {
		Object f = screen.focused();
		if (f == templateBox || fxBoxes.containsValue(f)) {
			lastTextTarget = f;
		}
	}

	// ---- input ----

	boolean mouseClicked(MouseButtonEvent event) {
		double mx = event.x(), my = event.y();
		if (picking) {
			return tagPicker.click(mx, my);
		}
		boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		boolean right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
		Row row = rowAt(mx, my);
		if (row == null || row.prop() == null) {
			return false;
		}
		if (left && row.sub() < 0 && row.kind() != Kind.TEMPLATE && row.kind() != Kind.TEMPLATE_STATUS && Ui.hover(mx, my, fxToggleBox(row))) {
			toggleFx(row.prop());
			return true;
		}
		switch (row.kind()) {
			case BOOL -> ((BoolProp) row.prop()).toggle();
			case ALIGN -> {
				int[] grid = alignGrid(row);
				int c = (int) Math.floor((mx - grid[0]) / grid[2]);
				int r = (int) Math.floor((my - grid[1]) / grid[2]);
				@SuppressWarnings("unchecked")
				EnumProp<Anchor> align = (EnumProp<Anchor>) row.prop();
				if (c >= 0 && c < 3 && r >= 0 && r < 3) {
					setAlign(align, Anchor.at(c, r));
				} else {
					align.cycle(right ? -1 : 1);
				}
			}
			case COLOR -> {
				ColorProp c = (ColorProp) row.prop();
				if (!expanded.remove(c)) {
					expanded.add(c);
				}
			}
			case NUMBER, COLOR_CHANNEL -> {
				if (left) {
					activeSlider = row.prop();
					activeChannel = row.sub();
					updateSlider(row, mx);
				}
			}
			default -> {
				return false;
			}
		}
		HudManager.get().save();
		return true;
	}

	/** Changing the alignment keeps the object where it is on screen. */
	private void setAlign(EnumProp<Anchor> align, Anchor to) {
		Anchor from = align.fixedValue();
		float s = object.scale.f();
		double dx = 0, dy = 0;
		if (object.parent == null) {
			dx += screen.width * (from.fx - to.fx);
			dy += screen.height * (from.fy - to.fy);
		}
		double px = (to.fx - from.fx) * object.lastW * s, py = (to.fy - from.fy) * object.lastH * s;
		double rad = Math.toRadians(object.rotation.f());
		dx += px * Math.cos(rad) - py * Math.sin(rad);
		dy += px * Math.sin(rad) + py * Math.cos(rad);
		align.set(to);
		if (!object.x.hasFx()) {
			object.x.set((double) Math.round(object.x.fixedValue() + dx));
			refreshField(object.x);
		}
		if (!object.y.hasFx()) {
			object.y.set((double) Math.round(object.y.fixedValue() + dy));
			refreshField(object.y);
		}
	}

	/** After the object moved (dragged on screen), show the new X / Y. */
	void refreshPosition() {
		for (Prop<?> p : List.of(object.x, object.y)) {
			EditBox box = fieldBoxes.get(p);
			if (box != null && !box.isFocused()) {
				refreshField(p);
			}
		}
	}

	private void refreshField(Prop<?> p) {
		EditBox box = fieldBoxes.get(p);
		if (box != null && p instanceof NumberProp n) {
			String v = DotNet.toString(n.fixedValue());
			if (!v.equals(box.getValue())) {
				box.setValue(v);
			}
		}
	}

	private void toggleFx(Prop<?> prop) {
		if (prop.hasFx()) {
			prop.setFx(null);
			EditBox box = fxBoxes.remove(prop);
			if (box != null) {
				screen.removePageWidget(box);
			}
			if (isField(prop)) {
				fieldBoxes.put(prop, screen.widget(createFieldBox(prop)));
			}
		} else {
			prop.enableFx();
			EditBox field = fieldBoxes.remove(prop);
			if (field != null) {
				screen.removePageWidget(field);
			}
			EditBox box = screen.widget(createFxBox(prop));
			fxBoxes.put(prop, box);
			layout(x, y, w, h);
			screen.focus(box);
			box.moveCursorToEnd(false);
			lastTextTarget = box;
		}
		HudManager.get().save();
	}

	boolean mouseDragged(MouseButtonEvent event) {
		if (activeSlider == null) {
			return false;
		}
		for (Row row : layout()) {
			if (row.prop() == activeSlider && row.sub() == activeChannel) {
				updateSlider(row, event.x());
				break;
			}
		}
		return true;
	}

	boolean mouseReleased(MouseButtonEvent event) {
		if (activeSlider == null) {
			return false;
		}
		activeSlider = null;
		activeChannel = -1;
		HudManager.get().save();
		return true;
	}

	private void updateSlider(Row row, double mx) {
		double t = (mx - trackStart(row)) / (double) (trackEnd(row) - trackStart(row));
		t = Math.max(0, Math.min(1, t));
		if (row.kind() == Kind.NUMBER) {
			((NumberProp) row.prop()).setProgress(t);
		} else {
			((ColorProp) row.prop()).setChannel(row.sub(), (int) Math.round(t * 255));
		}
	}

	boolean mouseScrolled(double mx, double my, double amount) {
		if (picking) {
			return tagPicker.scroll(mx, my, amount);
		}
		if (mx < x || mx >= x + w || my < top() || my >= bottom()) {
			return false;
		}
		scroll -= amount * 16;
		clampScroll(layout());
		return true;
	}

	/** Tab completes an item / block / entity id. */
	boolean keyPressed(KeyEvent event) {
		int key = InputConstants.getKey(event).getValue();
		if (key == InputConstants.KEY_TAB && screen.focused() instanceof EditBox box) {
			for (Map.Entry<Prop<?>, EditBox> e : fieldBoxes.entrySet()) {
				if (e.getValue() == box && e.getKey() instanceof StringProp s) {
					String rest = idSuggestion(s, box.getValue());
					if (rest != null) {
						box.setValue(box.getValue() + rest);
						box.moveCursorToEnd(false);
					}
					return true;
				}
			}
		}
		return false;
	}

	// ---- rendering ----

	void render(GuiGraphicsExtractor g, int mx, int my) {
		noteFocus();
		refreshPosition();
		if (picking) {
			tagPicker.render(g, mx, my);
			return;
		}
		List<Row> rows = layout();
		g.enableScissor(x, top(), x + w, bottom());
		Row hovered = rowAt(mx, my);
		for (Row row : rows) {
			int ry = screenY(row);
			if (ry + row.height() < top() || ry > bottom()) {
				continue;
			}
			if (hovered != null && row.equals(hovered) && row.prop() != null && row.kind() != Kind.TEMPLATE
					&& row.kind() != Kind.TEMPLATE_STATUS && row.kind() != Kind.FX) {
				g.fill(x, ry, x + w - 6, ry + row.height(), 0x18FFFFFF);
			}
			renderRow(g, row, ry, mx, my);
		}
		g.disableScissor();
		int total = contentHeight(rows);
		int view = bottom() - top();
		Ui.scrollbar(g, x + w - 6, top(), view, scroll, Math.max(0, total - view), total);
	}

	private void renderRow(GuiGraphicsExtractor g, Row row, int ry, int mx, int my) {
		int textY = ry + (Math.min(row.height(), 16) - font.lineHeight) / 2 + 1;
		int cr = controlRight();
		switch (row.kind()) {
			case HEADER -> {
				g.text(font, row.text(), x + 4, ry + 5, Ui.ACCENT, false);
				g.fill(x + 8 + font.width(row.text()), ry + 9, right(), ry + 10, 0x40FFFFFF);
			}
			case NAME, TEMPLATE -> {
			}
			case TEMPLATE_STATUS -> {
				List<Diagnostic> problems = object.text.diagnostics();
				if (problems.isEmpty()) {
					g.text(font, Component.translatable("screen.customhud.settings.template_ok"), x + 4, ry + 2, Ui.OK, false);
				} else {
					for (int i = 0; i < Math.min(3, problems.size()); i++) {
						Diagnostic d = problems.get(i);
						int color = d.severity() == Diagnostic.Severity.ERROR ? Ui.DANGER : 0xFFFFCC55;
						g.text(font, Ui.ellipsize(font, "⚠ " + d.text().getString(), w - 12), x + 4, ry + 2 + i * 10, color, false);
					}
				}
			}
			case BOOL -> {
				BoolProp b = (BoolProp) row.prop();
				label(g, b, textY);
				Ui.checkbox(g, cr - 12, ry + 2, 12, b.fixedValue(), Ui.hover(mx, my, cr - 12, ry + 2, 12, 12));
				fxToggle(g, row, mx, my);
			}
			case ALIGN -> {
				@SuppressWarnings("unchecked")
				EnumProp<Anchor> e = (EnumProp<Anchor>) row.prop();
				label(g, e, ry + 3);
				g.text(font, e.valueLabel(), x + 4, ry + 15, Ui.MUTED, false);
				int[] grid = alignGrid(row);
				Ui.inset(g, grid[0] - 1, grid[1] - 1, grid[2] * 3 + 2, grid[2] * 3 + 2);
				for (Anchor a : Anchor.values()) {
					int cx = grid[0] + a.column() * grid[2], cy = grid[1] + a.row() * grid[2];
					boolean sel = a == e.fixedValue();
					boolean hover = mx >= cx && mx < cx + grid[2] && my >= cy && my < cy + grid[2];
					g.fill(cx + 2, cy + 2, cx + grid[2] - 2, cy + grid[2] - 2, sel ? Ui.ACCENT : hover ? 0xFFAAAAAA : 0xFF555555);
				}
				fxToggle(g, row, mx, my);
			}
			case NUMBER -> {
				NumberProp n = (NumberProp) row.prop();
				label(g, n, ry + 3);
				String value = n.display();
				g.text(font, value, cr - font.width(value), ry + 3, Ui.ACCENT, false);
				int ts = trackStart(row), te = trackEnd(row);
				Ui.slider(g, ts, ry + 13, te - ts, 10, n.progress(), activeSlider == n);
				fxToggle(g, row, mx, my);
			}
			case FIELD -> {
				label(g, row.prop(), textY);
				fxToggle(g, row, mx, my);
			}
			case STRING -> {
				label(g, row.prop(), ry + 3);
				ItemStack icon = icon((StringProp) row.prop());
				if (!icon.isEmpty()) {
					g.item(icon, cr - 16, ry - 1);
				}
				fxToggle(g, row, mx, my);
			}
			case COLOR -> {
				ColorProp c = (ColorProp) row.prop();
				label(g, c, textY);
				swatch(g, cr - 22, ry + 3, c.fixedValue(), expanded.contains(c));
				String arrow = expanded.contains(c) ? "▲" : "▼";
				g.text(font, arrow, cr - 26 - font.width(arrow), textY, Ui.MUTED, false);
				fxToggle(g, row, mx, my);
			}
			case COLOR_CHANNEL -> {
				ColorProp c = (ColorProp) row.prop();
				int ch = row.sub();
				g.text(font, CHANNELS[ch], x + 8, ry + 3, CHANNEL_COLORS[ch], false);
				int ts = trackStart(row), te = trackEnd(row);
				Ui.slider(g, ts, ry + 2, te - ts, 10, c.channel(ch) / 255.0, activeSlider == c && activeChannel == ch);
				String value = String.valueOf(c.channel(ch));
				g.text(font, value, cr - font.width(value), ry + 3, Ui.MUTED, false);
			}
			case FX -> {
				Prop<?> p = row.prop();
				label(g, p, ry + 3);
				fxToggle(g, row, mx, my);
				String error = p.fxError();
				if (error != null) {
					g.text(font, Ui.ellipsize(font, "⚠ " + error, w - 12), x + 4, ry + 29, Ui.DANGER, false);
				} else if (p instanceof ColorProp c) {
					swatch(g, cr - 22, ry + 2, c.argb(), false);
				} else {
					String preview = Ui.ellipsize(font, "= " + preview(p), cr - (x + 8 + font.width(p.label())) - 6);
					g.text(font, preview, cr - font.width(preview), ry + 3, Ui.OK, false);
				}
			}
		}
	}

	private ItemStack icon(StringProp p) {
		String id = p.get();
		return switch (p.id()) {
			case "block" -> new ItemStack(HudObject.blockById(id).getBlock().asItem());
			case "item" -> new ItemStack(HudObject.itemById(id));
			default -> ItemStack.EMPTY;
		};
	}

	private static String preview(Prop<?> p) {
		Object v = p.get();
		if (v instanceof Enum<?> e) {
			return EnumProp.label(e).getString();
		}
		if (v instanceof Double d) {
			return DotNet.toString(Math.round(d * 1000) / 1000.0);
		}
		return DotNet.toString(v);
	}

	private void label(GuiGraphicsExtractor g, Prop<?> p, int ly) {
		g.text(font, p.label(), x + 4, ly, Ui.TEXT, false);
	}

	private void fxToggle(GuiGraphicsExtractor g, Row row, int mx, int my) {
		int[] b = fxToggleBox(row);
		boolean on = row.prop().hasFx();
		Ui.button(g, font, b, Component.literal("ƒ").withColor(on ? Ui.ACCENT : Ui.MUTED), Ui.hover(mx, my, b), true);
	}

	private void swatch(GuiGraphicsExtractor g, int sx, int sy, int argb, boolean selected) {
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 2; j++) {
				g.fill(sx + 1 + i * 5, sy + 1 + j * 4, sx + 6 + i * 5, sy + 5 + j * 4, (i + j) % 2 == 0 ? 0xFF888888 : 0xFFCCCCCC);
			}
		}
		g.fill(sx + 1, sy + 1, sx + 21, sy + 9, argb);
		g.outline(sx, sy, 22, 10, selected ? Ui.ACCENT : 0xFF666670);
	}

	// ---- test hooks ----

	int[] fxToggleCenter(String propId) {
		List<Row> rows = layout();
		for (Row row : rows) {
			if (row.prop() != null && row.sub() < 0 && row.kind() != Kind.TEMPLATE_STATUS && row.kind() != Kind.TEMPLATE && row.prop().id().equals(propId)) {
				int view = bottom() - top();
				if (row.y() - scroll < 0 || row.y() + row.height() - scroll > view) {
					scroll = row.y() - view / 2.0;
					clampScroll(rows);
				}
				int[] b = fxToggleBox(row);
				return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
			}
		}
		return null;
	}

	/** Scrolls a setting into view (for tests). */
	void reveal(String propId) {
		fxToggleCenter(propId);
		layout(x, y, w, h);
	}

	EditBox fxBox(String propId) {
		return fxBoxes.entrySet().stream().filter(e -> e.getKey().id().equals(propId)).map(Map.Entry::getValue).findFirst().orElse(null);
	}

	EditBox fieldBox(String propId) {
		return fieldBoxes.entrySet().stream().filter(e -> e.getKey().id().equals(propId)).map(Map.Entry::getValue).findFirst().orElse(null);
	}

	MultiLineEditBox templateBox() {
		return templateBox;
	}

	Button tagsButton() {
		return tagsButton;
	}

	TagList tagPicker() {
		return tagPicker;
	}
}
