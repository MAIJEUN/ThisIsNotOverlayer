package dev.customhud.editor;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.hud.Anchor;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudObject;
import dev.customhud.hud.HudRenderer;
import dev.customhud.hud.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * One preset being edited: its objects as a tree on the left, the selected object's settings on the
 * right. On the screen (outside the window) objects are dragged to move them (snapping to the screen and
 * to each other), a drag on empty space selects several, Ctrl / Shift + click adds to the selection.
 */
final class EditPage extends Page {
	private static final int ROW_H = 14;
	private static final int BAR = 18;
	private static final int ICON = 10;
	private static final int INDENT = 8;
	private static final int SNAP = 4;
	private static final Set<HudObject> COLLAPSED = Collections.newSetFromMap(new IdentityHashMap<>());

	private record Row(HudObject obj, int depth) {
	}

	private final Minecraft mc = Minecraft.getInstance();
	/** Selected objects; the last one is the "primary" (whose settings are shown). */
	private final LinkedHashSet<HudObject> selection = new LinkedHashSet<>();
	private SettingsPanel panel;
	private double listScroll;

	// preset settings (nothing selected)
	private EditBox presetName;
	private EditBox enabledFx;

	// moving on screen
	private List<HudObject> moving = List.of();
	private double[] startXs, startYs;
	private double pressX, pressY;
	private float[] startBounds;
	private boolean moved;
	private Integer guideX, guideY;

	// box selection on screen
	private boolean boxSelecting;
	private boolean boxAdds;

	// dragging in the tree
	private HudObject listDrag;
	private double listPressY;
	private boolean listDragging;
	private Row dropRow;
	private int dropZone;

	EditPage(HudScreen screen) {
		super(screen);
	}

	private HudManager hud() {
		return HudManager.get();
	}

	private Preset preset() {
		return screen.editing();
	}

	private Font font() {
		return mc.font;
	}

	@Override
	Component hint() {
		return Component.translatable("screen.customhud.edit.hint");
	}

	// ---- selection ----

	HudObject selected() {
		HudObject last = null;
		for (HudObject o : selection) {
			last = o;
		}
		return last;
	}

	Set<HudObject> selection() {
		return selection;
	}

	void select(HudObject o) {
		selection.clear();
		if (o != null) {
			addToSelection(o);
		}
		syncPanel();
	}

	private void addToSelection(HudObject o) {
		selection.remove(o);
		selection.add(o);
		for (HudObject p = o.parent; p != null; p = p.parent) {
			COLLAPSED.remove(p);
		}
	}

	private void toggle(HudObject o) {
		if (!selection.remove(o)) {
			addToSelection(o);
		}
	}

	/** The selected objects without those already inside a selected group (what moves, copies and deletes). */
	private List<HudObject> roots() {
		List<HudObject> out = new ArrayList<>();
		for (HudObject o : selection) {
			boolean inner = false;
			for (HudObject p = o.parent; p != null; p = p.parent) {
				if (selection.contains(p)) {
					inner = true;
				}
			}
			if (!inner) {
				out.add(o);
			}
		}
		return out;
	}

	private static boolean multiKey(MouseButtonEvent e) {
		return e.hasControlDown() || e.hasShiftDown();
	}

	/** Makes the settings follow the primary selection (rebuilding the window's widgets when it changes). */
	private void syncPanel() {
		Preset p = preset();
		selection.removeIf(o -> p == null || !p.contains(o));
		HudObject primary = selected();
		if (panel != null && panel.object() == primary) {
			return;
		}
		panel = primary == null ? null : new SettingsPanel(this, primary);
		if (screen.tab() == HudScreen.Tab.EDIT && screen.width > 0) {
			screen.rebuild();
		}
	}

	SettingsPanel panel() {
		return panel;
	}

	// ---- widgets ----

	@Override
	void init() {
		Preset p = preset();
		presetName = null;
		enabledFx = null;
		if (p == null) {
			return;
		}
		if (panel != null) {
			panel.init(screen);
			return;
		}
		presetName = screen.widget(new EditBox(font(), 0, 0, 100, 16, Component.translatable("screen.customhud.edit.preset_name")));
		presetName.setMaxLength(64);
		presetName.setValue(p.name);
		presetName.setResponder(s -> {
			p.name = s;
			hud().save();
		});
		if (p.enabled.hasFx()) {
			enabledFx = screen.widget(new EditBox(font(), 0, 0, 100, 14, Component.translatable("screen.customhud.edit.enabled")));
			enabledFx.setMaxLength(2000);
			enabledFx.setValue(p.enabled.fxSource());
			enabledFx.setResponder(s -> {
				p.enabled.setFx(s);
				hud().save();
			});
		}
	}

	// ---- layout ----

	private int treeW() {
		return Math.max(100, Math.min(170, (int) (w * 0.4)));
	}

	private int sideX() {
		return x + treeW() + 6;
	}

	private int sideW() {
		return w - treeW() - 6;
	}

	private int[] addButton() {
		return new int[]{x, y, x + treeW(), y + BAR - 2};
	}

	private int listTop() {
		return y + BAR + 1;
	}

	private int listBottom() {
		return y + h - 1;
	}

	private List<Row> rows() {
		List<Row> out = new ArrayList<>();
		if (preset() != null) {
			for (HudObject o : preset().objects) {
				addRows(out, o, 0);
			}
		}
		return out;
	}

	private void addRows(List<Row> out, HudObject o, int depth) {
		out.add(new Row(o, depth));
		if (o.type == HudObject.Type.GROUP && !COLLAPSED.contains(o)) {
			for (HudObject c : o.children) {
				addRows(out, c, depth + 1);
			}
		}
	}

	private double maxListScroll() {
		return Math.max(0, rows().size() * ROW_H - (listBottom() - listTop() - 2));
	}

	private int rowTop(int index) {
		return listTop() + 1 + index * ROW_H - (int) listScroll;
	}

	private boolean inTree(double mx, double my) {
		return mx >= x && mx < x + treeW() && my >= listTop() && my < listBottom();
	}

	private int rowIndexAt(double mx, double my) {
		if (!inTree(mx, my)) {
			return -1;
		}
		int index = (int) Math.floor((my - listTop() - 1 + listScroll) / ROW_H);
		return index >= 0 && index < rows().size() ? index : -1;
	}

	private int indentX(Row r) {
		return x + 3 + r.depth() * INDENT;
	}

	private boolean onFold(Row r, double mx) {
		return r.obj().type == HudObject.Type.GROUP && mx >= indentX(r) && mx < indentX(r) + 9;
	}

	private int eyeX(Row r) {
		return indentX(r) + (r.obj().type == HudObject.Type.GROUP ? 9 : 0);
	}

	private boolean onEye(Row r, double mx) {
		return mx >= eyeX(r) && mx < eyeX(r) + ICON + 2;
	}

	// preset settings
	private int[] enabledBox() {
		return new int[]{sideX() + sideW() - 30, y + 46, sideX() + sideW() - 18, y + 58};
	}

	private int[] enabledFxToggle() {
		return new int[]{sideX() + sideW() - 13, y + 46, sideX() + sideW() - 2, y + 57};
	}

	@Override
	void layout(int x, int y, int w, int h) {
		super.layout(x, y, w, h);
		syncPanel();
		if (panel != null) {
			panel.layout(sideX(), y, sideW(), h);
		}
		if (presetName != null) {
			presetName.setX(sideX() + 2);
			presetName.setY(y + 22);
			presetName.setWidth(sideW() - 4);
			presetName.visible = true;
		}
		if (enabledFx != null) {
			enabledFx.setX(sideX() + 2);
			enabledFx.setY(y + 62);
			enabledFx.setWidth(sideW() - 4);
			enabledFx.visible = true;
		}
	}

	// ---- picking on screen ----

	/** The topmost object of this preset drawn under the point. */
	private HudObject pick(double px, double py) {
		Preset p = preset();
		if (p == null) {
			return null;
		}
		long frame = HudRenderer.frame();
		HudObject[] found = {null};
		p.forEach(o -> {
			if (o.hit(px, py, frame)) {
				found[0] = o;
			}
		});
		return found[0];
	}

	/** What a click on {@code hit} picks: a selected group that contains it, otherwise the object itself. */
	private HudObject clickTarget(HudObject hit) {
		for (HudObject p = hit; p != null; p = p.parent) {
			if (selection.contains(p)) {
				return p;
			}
		}
		return hit;
	}

	// ---- input: screen ----

	boolean worldDragging() {
		return !moving.isEmpty() || boxSelecting;
	}

	boolean worldClicked(MouseButtonEvent event) {
		double mx = event.x(), my = event.y();
		boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT;
		HudObject hit = pick(mx, my);
		if (hit == null && !multiKey(event)) {
			// An object of another switched-on preset opens that preset.
			HudObject[] other = {null};
			long frame = HudRenderer.frame();
			for (Preset p : hud().presets()) {
				if (p != preset() && p.enabled.on()) {
					p.forEach(o -> {
						if (o.hit(mx, my, frame)) {
							other[0] = o;
						}
					});
				}
			}
			if (other[0] != null) {
				screen.edit(hud().presetOf(other[0]), other[0]);
				return true;
			}
		}
		if (hit != null && !left) {
			select(clickTarget(hit));
			return true;
		}
		if (!left) {
			return false;
		}
		if (hit != null) {
			HudObject target = clickTarget(hit);
			if (multiKey(event)) {
				toggle(target);
				syncPanel();
				return true;
			}
			if (!selection.contains(target)) {
				select(target);
			} else {
				addToSelection(target);
				syncPanel();
			}
			startMove(mx, my);
			return true;
		}
		if (!multiKey(event)) {
			select(null);
		}
		boxSelecting = true;
		boxAdds = multiKey(event);
		pressX = mx;
		pressY = my;
		return true;
	}

	private void startMove(double mx, double my) {
		moving = roots();
		startXs = new double[moving.size()];
		startYs = new double[moving.size()];
		float[] b = null;
		long frame = HudRenderer.frame();
		for (int i = 0; i < moving.size(); i++) {
			HudObject o = moving.get(i);
			startXs[i] = o.x.fixedValue();
			startYs[i] = o.y.fixedValue();
			float[] ob = o.bounds(frame);
			if (ob != null) {
				b = b == null ? ob.clone() : new float[]{Math.min(b[0], ob[0]), Math.min(b[1], ob[1]), Math.max(b[2], ob[2]), Math.max(b[3], ob[3])};
			}
		}
		startBounds = b;
		pressX = mx;
		pressY = my;
		moved = false;
	}

	void worldDragged(MouseButtonEvent event) {
		if (!moving.isEmpty()) {
			dragMove(event.x(), event.y());
		}
	}

	void worldReleased(MouseButtonEvent event) {
		if (!moving.isEmpty()) {
			moving = List.of();
			guideX = null;
			guideY = null;
			hud().save();
		}
		if (boxSelecting) {
			boxSelecting = false;
			finishBox(event.x(), event.y());
		}
	}

	/** Selects every object whose box touches the dragged rectangle (groups are picked through their contents). */
	private void finishBox(double mx, double my) {
		float x1 = (float) Math.min(pressX, mx), x2 = (float) Math.max(pressX, mx);
		float y1 = (float) Math.min(pressY, my), y2 = (float) Math.max(pressY, my);
		if ((x2 - x1 < 2 && y2 - y1 < 2) || preset() == null) {
			return;
		}
		long frame = HudRenderer.frame();
		if (!boxAdds) {
			selection.clear();
		}
		preset().forEach(o -> {
			if (o.type == HudObject.Type.GROUP || !o.visible.on()) {
				return;
			}
			float[] b = o.bounds(frame);
			if (b != null && b[0] < x2 && b[2] > x1 && b[1] < y2 && b[3] > y1) {
				addToSelection(o);
			}
		});
		syncPanel();
	}

	/** A screen movement in the coordinates of the object's group (which may be scaled or rotated). */
	private static Vector2f toLocal(HudObject o, double dx, double dy) {
		if (o.parent == null) {
			return new Vector2f((float) dx, (float) dy);
		}
		return new Matrix3x2f(o.parent.lastOrigin).invert().transformDirection((float) dx, (float) dy, new Vector2f());
	}

	private void dragMove(double mx, double my) {
		double dx = mx - pressX, dy = my - pressY;
		if (!moved && Math.abs(dx) + Math.abs(dy) < 2) {
			return;
		}
		moved = true;
		guideX = null;
		guideY = null;
		if (startBounds != null) {
			double[] snapped = snap(startBounds, dx, dy);
			dx = snapped[0];
			dy = snapped[1];
		}
		for (int i = 0; i < moving.size(); i++) {
			HudObject o = moving.get(i);
			Vector2f local = toLocal(o, dx, dy);
			if (!o.x.hasFx()) {
				o.x.set((double) Math.round(startXs[i] + local.x));
			}
			if (!o.y.hasFx()) {
				o.y.set((double) Math.round(startYs[i] + local.y));
			}
		}
		if (panel != null) {
			panel.refreshPosition();
		}
	}

	/** Snaps the moved box's edges or centre to the screen's edges / centre and to the preset's other objects. */
	private double[] snap(float[] b, double dx, double dy) {
		List<Float> xs = new ArrayList<>(List.of(0f, screen.width / 2f, (float) screen.width));
		List<Float> ys = new ArrayList<>(List.of(0f, screen.height / 2f, (float) screen.height));
		long frame = HudRenderer.frame();
		preset().forEach(o -> {
			for (HudObject m : moving) {
				if (o == m || o.isAncestorOf(m) || m.isAncestorOf(o)) {
					return;
				}
			}
			float[] ob = o.bounds(frame);
			if (ob != null) {
				xs.add(ob[0]);
				xs.add(ob[2]);
				ys.add(ob[1]);
				ys.add(ob[3]);
			}
		});
		double bestX = SNAP + 1, sx = dx;
		for (float line : xs) {
			for (float edge : new float[]{b[0], (b[0] + b[2]) / 2, b[2]}) {
				double d = Math.abs(edge + dx - line);
				if (d < bestX) {
					bestX = d;
					sx = line - edge;
					guideX = Math.round(line);
				}
			}
		}
		double bestY = SNAP + 1, sy = dy;
		for (float line : ys) {
			for (float edge : new float[]{b[1], (b[1] + b[3]) / 2, b[3]}) {
				double d = Math.abs(edge + dy - line);
				if (d < bestY) {
					bestY = d;
					sy = line - edge;
					guideY = Math.round(line);
				}
			}
		}
		return new double[]{sx, sy};
	}

	// ---- input: window ----

	@Override
	boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
		double mx = e.x(), my = e.y();
		boolean left = e.button() == InputConstants.MOUSE_BUTTON_LEFT;
		if (preset() == null) {
			return false;
		}
		if (left && Ui.hover(mx, my, addButton())) {
			openTemplates();
			return true;
		}
		int index = rowIndexAt(mx, my);
		if (index >= 0) {
			clickRow(e, index, doubleClick);
			return true;
		}
		if (inTree(mx, my)) {
			if (left && !multiKey(e)) {
				select(null);
			}
			return true;
		}
		if (mx >= sideX()) {
			if (panel != null) {
				return panel.mouseClicked(e);
			}
			Preset p = preset();
			if (left && Ui.hover(mx, my, enabledBox()) && !p.enabled.hasFx()) {
				p.enabled.set(!p.enabled.fixedValue());
				hud().save();
				return true;
			}
			if (left && Ui.hover(mx, my, enabledFxToggle())) {
				if (p.enabled.hasFx()) {
					p.enabled.setFx(null);
				} else {
					p.enabled.enableFx();
				}
				hud().save();
				screen.rebuild();
				if (enabledFx != null) {
					screen.focus(enabledFx);
				}
				return true;
			}
		}
		return false;
	}

	private void clickRow(MouseButtonEvent event, int index, boolean doubleClick) {
		double mx = event.x();
		List<Row> rows = rows();
		Row r = rows.get(index);
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			select(r.obj());
			return;
		}
		if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
			return;
		}
		if (onFold(r, mx)) {
			if (!COLLAPSED.add(r.obj())) {
				COLLAPSED.remove(r.obj());
			}
		} else if (onEye(r, mx)) {
			if (r.obj().visible.hasFx()) {
				select(r.obj());
			} else {
				boolean show = !r.obj().visible.fixedValue();
				for (HudObject o : selection.contains(r.obj()) ? List.copyOf(selection) : List.of(r.obj())) {
					if (!o.visible.hasFx()) {
						o.visible.set(show);
					}
				}
				hud().save();
			}
		} else if (event.hasShiftDown() && selected() != null) {
			// Range from the primary selection to this row.
			int from = -1;
			for (int i = 0; i < rows.size(); i++) {
				if (rows.get(i).obj() == selected()) {
					from = i;
				}
			}
			if (from >= 0) {
				for (int i = Math.min(from, index); i <= Math.max(from, index); i++) {
					if (i != from) {
						addToSelection(rows.get(i).obj());
					}
				}
				addToSelection(r.obj());
				syncPanel();
			}
		} else if (event.hasControlDown()) {
			toggle(r.obj());
			syncPanel();
		} else {
			if (!selection.contains(r.obj())) {
				select(r.obj());
			} else {
				addToSelection(r.obj());
				syncPanel();
			}
			if (doubleClick && panel != null) {
				panel.focusName();
				return;
			}
			listDrag = r.obj();
			listPressY = event.y();
			listDragging = false;
		}
	}

	@Override
	boolean mouseDragged(MouseButtonEvent e) {
		if (listDrag != null) {
			if (!listDragging && Math.abs(e.y() - listPressY) > 3) {
				listDragging = true;
			}
			if (listDragging) {
				updateDrop(e.x(), e.y());
			}
			return true;
		}
		return panel != null && panel.mouseDragged(e);
	}

	@Override
	boolean mouseReleased(MouseButtonEvent e) {
		if (listDrag != null) {
			if (listDragging && dropRow != null) {
				List<HudObject> movers = selection.contains(listDrag) ? roots() : List.of(listDrag);
				if (!movers.contains(dropRow.obj())) {
					moveInList(movers, dropRow.obj(), dropZone);
				}
			}
			listDrag = null;
			listDragging = false;
			dropRow = null;
			return true;
		}
		return panel != null && panel.mouseReleased(e);
	}

	@Override
	boolean mouseScrolled(double mx, double my, double amount) {
		if (mx < sideX()) {
			listScroll = Math.max(0, Math.min(maxListScroll(), listScroll - amount * ROW_H));
			return true;
		}
		return panel != null && panel.mouseScrolled(mx, my, amount);
	}

	@Override
	boolean keyPressed(KeyEvent event) {
		if (screen.typing()) {
			return panel != null && panel.keyPressed(event);
		}
		int key = InputConstants.getKey(event).getValue();
		if (event.isSelectAll() && preset() != null) {
			preset().forEach(this::addToSelection);
			syncPanel();
			return true;
		}
		if (selection.isEmpty()) {
			return false;
		}
		if (key == InputConstants.KEY_DELETE) {
			roots().forEach(this::removeQuietly);
			hud().save();
			syncPanel();
			return true;
		}
		if (event.hasControlDown() && key == InputConstants.KEY_D) {
			duplicateSelected();
			return true;
		}
		if (event.hasControlDown() && key == InputConstants.KEY_G) {
			if (event.hasShiftDown()) {
				ungroupSelected();
			} else {
				groupSelected();
			}
			return true;
		}
		int step = event.hasShiftDown() ? 10 : 1;
		int dx = key == InputConstants.KEY_LEFT ? -step : key == InputConstants.KEY_RIGHT ? step : 0;
		int dy = key == InputConstants.KEY_UP ? -step : key == InputConstants.KEY_DOWN ? step : 0;
		if (dx != 0 || dy != 0) {
			for (HudObject o : roots()) {
				Vector2f local = toLocal(o, dx, dy);
				if (!o.x.hasFx()) {
					o.x.set((double) Math.round(o.x.fixedValue() + local.x));
				}
				if (!o.y.hasFx()) {
					o.y.set((double) Math.round(o.y.fixedValue() + local.y));
				}
			}
			if (panel != null) {
				panel.refreshPosition();
			}
			hud().save();
			return true;
		}
		return false;
	}

	// ---- editing ----

	private void openTemplates() {
		List<ListPopup.Entry> entries = new ArrayList<>();
		Templates.Group group = null;
		for (Templates.Template tpl : Templates.ALL) {
			if (tpl.group() != group) {
				group = tpl.group();
				entries.add(ListPopup.Entry.header(Component.translatable("screen.customhud.edit.group." + group.name().toLowerCase(Locale.ROOT))));
			}
			entries.add(new ListPopup.Entry(tpl.name(), Component.literal(tpl.preview()), () -> add(tpl.create())));
		}
		int[] b = addButton();
		screen.openPopup(new ListPopup(entries, b[0], b[3] + 2, Math.max(220, treeW()), screen.height));
	}

	private void updateDrop(double mx, double my) {
		int index = rowIndexAt(mx, my);
		dropRow = index >= 0 ? rows().get(index) : null;
		if (dropRow == null) {
			return;
		}
		for (HudObject m : selection.contains(listDrag) ? roots() : List.of(listDrag)) {
			if (m.isAncestorOf(dropRow.obj())) {
				dropRow = null;
				return;
			}
		}
		double rel = (my - rowTop(index)) / (double) ROW_H;
		boolean group = dropRow.obj().type == HudObject.Type.GROUP;
		dropZone = rel < (group ? 0.3 : 0.5) ? -1 : group && rel < 0.7 ? 0 : 1;
	}

	/** Moves objects before / into (groups only) / after another one, keeping them where they are on screen. */
	private void moveInList(List<HudObject> movers, HudObject target, int zone) {
		HudObject anchor = target;
		for (HudObject o : movers) {
			HudObject oldParent = o.parent;
			hud().siblings(o).remove(o);
			HudObject newParent = zone == 0 ? target : target.parent;
			List<HudObject> list = zone == 0 ? target.children : hud().siblings(anchor);
			int at = zone == 0 ? list.size() : list.indexOf(anchor) + (zone > 0 ? 1 : 0);
			o.parent = newParent;
			list.add(Math.max(0, Math.min(list.size(), at)), o);
			if (oldParent != newParent) {
				keepOnScreen(o, oldParent, newParent);
			}
			if (zone > 0) {
				anchor = o;
			}
		}
		if (zone == 0) {
			COLLAPSED.remove(target);
		}
		hud().save();
	}

	/**
	 * After changing groups, offsets mean something else (they are measured from the new group's position);
	 * re-measure so the object does not jump.
	 */
	private void keepOnScreen(HudObject o, HudObject oldParent, HudObject newParent) {
		Anchor a = o.align.get();
		Vector2f pos = oldParent == null
				? new Vector2f(screen.width * a.fx + o.x.f(), screen.height * a.fy + o.y.f())
				: oldParent.lastOrigin.transformPosition(o.x.f(), o.y.f(), new Vector2f());
		if (newParent == null) {
			o.x.set((double) Math.round(pos.x - screen.width * a.fx));
			o.y.set((double) Math.round(pos.y - screen.height * a.fy));
		} else {
			Vector2f local = new Matrix3x2f(newParent.lastOrigin).invert().transformPosition(pos.x, pos.y, new Vector2f());
			o.x.set((double) Math.round(local.x));
			o.y.set((double) Math.round(local.y));
		}
	}

	/** Ctrl+G: puts the selected objects into a new group (in the group of the first one), keeping them in place. */
	void groupSelected() {
		List<HudObject> items = roots();
		if (items.isEmpty()) {
			return;
		}
		HudObject first = items.getFirst();
		HudObject parent = first.parent;
		List<HudObject> list = hud().siblings(first);
		HudObject group = new HudObject(HudObject.Type.GROUP, "");
		group.parent = parent;
		list.add(list.indexOf(first), group);
		// The new group sits at its parent's position point (or the screen's top-left corner), unturned.
		if (parent != null) {
			group.lastOrigin.set(parent.lastOrigin);
		} else {
			group.lastOrigin.identity();
		}
		for (HudObject o : items) {
			HudObject old = o.parent;
			hud().siblings(o).remove(o);
			o.parent = group;
			group.children.add(o);
			keepOnScreen(o, old, group);
		}
		select(group);
		hud().save();
	}

	/** Ctrl+Shift+G: moves the children of the selected groups out of them, keeping them in place. */
	void ungroupSelected() {
		List<HudObject> freed = new ArrayList<>();
		for (HudObject g : roots()) {
			if (g.type != HudObject.Type.GROUP) {
				continue;
			}
			List<HudObject> list = hud().siblings(g);
			int at = list.indexOf(g);
			for (HudObject c : List.copyOf(g.children)) {
				g.children.remove(c);
				c.parent = g.parent;
				list.add(++at, c);
				keepOnScreen(c, g, g.parent);
				freed.add(c);
			}
			list.remove(g);
		}
		if (!freed.isEmpty()) {
			selection.clear();
			freed.forEach(this::addToSelection);
			hud().save();
			syncPanel();
		}
	}

	/** Ctrl+D: copies of the selected objects, a little to the side. */
	void duplicateSelected() {
		List<HudObject> copies = new ArrayList<>();
		for (HudObject o : roots()) {
			HudObject copy = o.copy();
			copy.parent = o.parent;
			if (!copy.x.hasFx()) {
				copy.x.set(copy.x.fixedValue() + 8);
			}
			if (!copy.y.hasFx()) {
				copy.y.set(copy.y.fixedValue() + 8);
			}
			List<HudObject> list = hud().siblings(o);
			list.add(list.indexOf(o) + 1, copy);
			copies.add(copy);
		}
		selection.clear();
		copies.forEach(this::addToSelection);
		hud().save();
		syncPanel();
	}

	private void removeQuietly(HudObject o) {
		hud().siblings(o).remove(o);
		selection.removeIf(o::isAncestorOf);
		selection.remove(o);
	}

	void remove(HudObject o) {
		removeQuietly(o);
		hud().save();
		syncPanel();
	}

	/** Adds a new object: into the selected group, or next to the selected object, or at the top level. */
	void add(HudObject o) {
		HudObject sel = selected();
		HudObject group = sel != null && sel.type == HudObject.Type.GROUP ? sel : sel != null ? sel.parent : null;
		if (group != null) {
			o.parent = group;
			group.children.add(o);
		} else if (preset() != null) {
			preset().objects.add(o);
		}
		select(o);
		hud().save();
	}

	// ---- rendering: on screen ----

	void renderWorld(GuiGraphicsExtractor g, int mx, int my) {
		Preset p = preset();
		if (p == null) {
			return;
		}
		long frame = HudRenderer.frame();
		boolean overWindow = screen.inWindow(mx, my);
		HudObject hover = moving.isEmpty() && !boxSelecting && listDrag == null && !overWindow ? pick(mx, my) : null;
		HudObject hoverTarget = hover == null ? null : clickTarget(hover);
		int rowHover = rowIndexAt(mx, my);
		HudObject listHover = rowHover >= 0 ? rows().get(rowHover).obj() : null;
		p.forEach(o -> {
			if (o.lastFrame == frame && o.lastW > 0 && o != hoverTarget && !selection.contains(o)) {
				outline(g, o, o == listHover ? 0xA0FFFFFF : 0x40FFFFFF);
			}
		});
		if (hoverTarget != null && !selection.contains(hoverTarget)) {
			box(g, hoverTarget.bounds(frame), 0xA0FFFFFF);
		}
		HudObject primary = selected();
		for (HudObject o : selection) {
			box(g, o.bounds(frame), o == primary ? Ui.ACCENT : Ui.SELECT);
		}
		if (primary != null && primary.lastFrame == frame) {
			Vector2f pt = primary.lastOrigin.transformPosition(0, 0, new Vector2f());
			g.fill(Math.round(pt.x) - 1, Math.round(pt.y) - 1, Math.round(pt.x) + 2, Math.round(pt.y) + 2, Ui.ACCENT);
		}
		if (boxSelecting) {
			int x1 = (int) Math.min(pressX, mx), x2 = (int) Math.max(pressX, mx);
			int y1 = (int) Math.min(pressY, my), y2 = (int) Math.max(pressY, my);
			g.fill(x1, y1, x2, y2, 0x205B8CFF);
			g.outline(x1, y1, x2 - x1, y2 - y1, Ui.SELECT);
		}
		if (!moving.isEmpty() && moved) {
			if (guideX != null) {
				g.fill(guideX, 0, guideX + 1, screen.height, Ui.GUIDE);
			}
			if (guideY != null) {
				g.fill(0, guideY, screen.width, guideY + 1, Ui.GUIDE);
			}
			HudObject m = moving.getLast();
			String pos = Math.round(m.x.f()) + ", " + Math.round(m.y.f()) + (moving.size() > 1 ? "  (" + moving.size() + ")" : "");
			g.text(font(), pos, mx + 10, my + 10, Ui.TEXT, true);
		}
	}

	/** The object's rotated box, as lines. */
	private void outline(GuiGraphicsExtractor g, HudObject o, int color) {
		g.pose().pushMatrix();
		g.pose().set(o.lastPose);
		g.outline(-1, -1, Math.round(o.lastW) + 2, Math.round(o.lastH) + 2, color);
		g.pose().popMatrix();
	}

	private static void box(GuiGraphicsExtractor g, float[] b, int color) {
		if (b != null) {
			g.outline(Math.round(b[0]) - 1, Math.round(b[1]) - 1, Math.round(b[2] - b[0]) + 2, Math.round(b[3] - b[1]) + 2, color);
		}
	}

	// ---- rendering: window ----

	@Override
	void render(GuiGraphicsExtractor g, int mx, int my) {
		Preset p = preset();
		if (p == null) {
			return;
		}
		Font font = font();
		int[] add = addButton();
		Ui.button(g, font, add, Component.translatable("screen.customhud.edit.add"), Ui.hover(mx, my, add), true);
		renderTree(g, mx, my);
		// divider
		g.fill(sideX() - 4, y, sideX() - 3, y + h, 0x40FFFFFF);
		if (panel != null) {
			panel.render(g, mx, my);
			if (selection.size() > 1) {
				String n = Component.translatable("screen.customhud.edit.selected", selection.size()).getString();
				int nx = x + treeW() - font.width(n) - 4, ny = listBottom() - font.lineHeight - 2;
				g.fill(nx - 2, ny - 1, x + treeW() - 2, ny + font.lineHeight, 0xC0000000);
				g.text(font, n, nx, ny, Ui.ACCENT, false);
			}
			return;
		}
		// the preset itself
		int sx = sideX(), sw = sideW();
		g.text(font, Component.translatable("screen.customhud.edit.preset"), sx + 2, y + 5, Ui.ACCENT, false);
		g.fill(sx + 8 + font.width(Component.translatable("screen.customhud.edit.preset")), y + 9, sx + sw - 2, y + 10, 0x40FFFFFF);
		g.text(font, Component.translatable("screen.customhud.edit.enabled"), sx + 2, y + 48, Ui.TEXT, false);
		int[] cb = enabledBox();
		if (!p.enabled.hasFx()) {
			Ui.checkbox(g, cb[0], cb[1], 12, p.enabled.fixedValue(), Ui.hover(mx, my, cb));
		} else if (p.enabled.fxError() != null) {
			g.text(font, Ui.ellipsize(font, "⚠ " + p.enabled.fxError(), sw - 4), sx + 2, y + 79, Ui.DANGER, false);
		} else {
			String v = "= " + p.enabled.on();
			g.text(font, v, cb[0] - font.width(v) + 10, y + 48, Ui.OK, false);
		}
		int[] fx = enabledFxToggle();
		Ui.button(g, font, fx, Component.literal("ƒ").withColor(p.enabled.hasFx() ? Ui.ACCENT : Ui.MUTED), Ui.hover(mx, my, fx), true);
		int ty = y + (p.enabled.hasFx() ? 94 : 70);
		int[] count = {0};
		p.forEach(o -> count[0]++);
		g.text(font, Component.translatable("screen.customhud.edit.count", count[0]), sx + 2, ty, Ui.MUTED, false);
		List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("screen.customhud.edit.nothing_selected"), sw - 4);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), sx + 2, ty + 14 + i * 10, Ui.FAINT, false);
		}
	}

	private void renderTree(GuiGraphicsExtractor g, int mx, int my) {
		Font font = font();
		int tw = treeW();
		List<Row> rows = rows();
		listScroll = Math.max(0, Math.min(maxListScroll(), listScroll));
		Ui.inset(g, x, listTop(), tw, listBottom() - listTop());
		g.enableScissor(x + 1, listTop() + 1, x + tw - 1, listBottom() - 1);
		if (rows.isEmpty()) {
			List<net.minecraft.util.FormattedCharSequence> lines = font.split(Component.translatable("screen.customhud.edit.empty"), tw - 10);
			for (int i = 0; i < lines.size(); i++) {
				g.text(font, lines.get(i), x + 5, listTop() + 5 + i * 10, Ui.MUTED, false);
			}
		}
		int hoverIndex = listDrag == null ? rowIndexAt(mx, my) : -1;
		HudObject primary = selected();
		int right = x + tw - (maxListScroll() > 0 ? 8 : 2);
		for (int i = 0; i < rows.size(); i++) {
			Row r = rows.get(i);
			HudObject o = r.obj();
			int top = rowTop(i);
			if (top + ROW_H < listTop() || top > listBottom()) {
				continue;
			}
			int x1 = indentX(r), y2 = top + ROW_H - 1;
			boolean hover = i == hoverIndex;
			boolean target = listDragging && dropRow == r && dropZone == 0;
			int bg = o == primary ? Ui.ROW_SELECTED : selection.contains(o) ? Ui.SELECT_DIM : target ? 0xFF3A3A60 : hover ? Ui.ROW_HOVER : 0;
			if (listDragging && selection.contains(o)) {
				bg = 0xFF202020;
			}
			if (bg != 0) {
				g.fill(x1, top, right, y2, bg);
			}
			int textY = top + (ROW_H - 1 - font.lineHeight) / 2 + 1;
			int tx = x1;
			if (o.type == HudObject.Type.GROUP) {
				g.text(font, COLLAPSED.contains(o) ? "▸" : "▾", tx + 2, textY, Ui.MUTED, false);
				tx += 9;
			}
			boolean shown = o.visible.on();
			String eye = o.visible.hasFx() ? "ƒ" : shown ? "●" : "○";
			boolean eyeHover = hover && onEye(r, mx);
			g.centeredText(font, Component.literal(eye), tx + ICON / 2 + 1, textY, eyeHover ? Ui.TEXT : shown ? Ui.ACCENT : Ui.MUTED);
			tx += ICON + 3;
			String icon = typeIcon(o) + " ";
			g.text(font, icon, tx, textY, Ui.MUTED, false);
			tx += font.width(icon);
			Component label = displayName(o);
			g.text(font, Ui.ellipsize(font, label.getString(), right - tx - 2), tx, textY, !shown ? Ui.MUTED : o.name.isEmpty() ? 0xFFD0D0D0 : Ui.TEXT, false);
		}
		if (listDragging && dropRow != null && dropZone != 0) {
			int index = rows.indexOf(dropRow);
			int ly = rowTop(index) + (dropZone < 0 ? -1 : ROW_H - 1);
			g.fill(indentX(dropRow), ly - 1, right, ly + 1, Ui.ACCENT);
		}
		g.disableScissor();
		int view = listBottom() - listTop() - 2;
		Ui.scrollbar(g, x + tw - 7, listTop() + 1, view, listScroll, maxListScroll(), (int) (view + maxListScroll()));
	}

	private static String typeIcon(HudObject o) {
		return switch (o.type) {
			case GROUP -> "▤";
			case TEXT -> "T";
			case ITEM -> "◆";
			case BLOCK -> "■";
			case ENTITY -> "☺";
		};
	}

	/** The object's name, or what it shows when it has none (the text's first line, the item's id…). */
	static Component displayName(HudObject o) {
		if (!o.name.isEmpty()) {
			return Component.literal(o.name);
		}
		String what = switch (o.type) {
			case TEXT -> o.text.fixedValue().split("\n", 2)[0].replaceAll("<[^>]*>|&#?[0-9a-fA-Fk-orK-OR]{1,6}", "");
			case ITEM -> o.item.fixedValue();
			case BLOCK -> o.block.fixedValue();
			case ENTITY -> o.entity.fixedValue();
			case GROUP -> "";
		};
		what = what.replace("minecraft:", "").trim();
		return what.isEmpty() ? Component.translatable("type.customhud." + o.type.name().toLowerCase(Locale.ROOT)) : Component.literal(what);
	}

	// ---- test hooks ----

	/** Centre of an object's tree row (scrolled into view), or of its eye. */
	int[] rowCenter(HudObject o, String part) {
		List<Row> rows = rows();
		int index = -1;
		for (int i = 0; i < rows.size(); i++) {
			if (rows.get(i).obj() == o) {
				index = i;
			}
		}
		int view = listBottom() - listTop() - 2;
		double top = index * ROW_H;
		if (top < listScroll || top + ROW_H > listScroll + view) {
			listScroll = Math.max(0, Math.min(maxListScroll(), top - view / 2.0));
		}
		Row r = rows.get(index);
		int cx = part.equals("eye") ? eyeX(r) + ICON / 2 + 1 : x + treeW() / 2 + 10;
		return new int[]{cx, rowTop(index) + ROW_H / 2};
	}

	int[] addButtonCenter() {
		int[] b = addButton();
		return new int[]{(b[0] + b[2]) / 2, (b[1] + b[3]) / 2};
	}

	EditBox presetNameBox() {
		return presetName;
	}
}
