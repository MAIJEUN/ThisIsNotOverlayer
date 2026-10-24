package dev.customhud.hud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.customhud.prop.BoolProp;
import dev.customhud.prop.ColorProp;
import dev.customhud.prop.EnumProp;
import dev.customhud.prop.NumberProp;
import dev.customhud.prop.Prop;
import dev.customhud.prop.StringProp;
import dev.customhud.prop.TextProp;
import dev.customhud.render.BlockPip;
import dev.customhud.render.EntityView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Something on the HUD: a text, an item, a block, or a group of other objects. Every object has only
 * alignment, position, scale and rotation (each can be an Fx expression). A top-level object is placed
 * from its alignment point on the screen; a child from its group's position. The alignment is also the
 * point of the object that sits there, and the point it scales and rotates around.
 */
public final class HudObject {
	public enum Type { GROUP, TEXT, ITEM, BLOCK, ENTITY }

	public final Type type;
	public String name;
	public final BoolProp visible = new BoolProp("visible", true);
	public final EnumProp<Anchor> align = new EnumProp<>("align", Anchor.TOP_LEFT);
	public final NumberProp x = new NumberProp("x", 0, -10000, 10000, 1);
	public final NumberProp y = new NumberProp("y", 0, -10000, 10000, 1);
	public final NumberProp scale = new NumberProp("scale", 1, 0.1, 10, 0.05);
	public final NumberProp rotation = new NumberProp("rotation", 0, -180, 180, 1, "°");

	public final TextProp text = new TextProp("text", "Text");
	public final BoolProp shadow = new BoolProp("shadow", true);
	public final StringProp item = new StringProp("item", "minecraft:diamond_sword");
	public final StringProp block = new StringProp("block", "minecraft:grass_block");
	public final StringProp entity = new StringProp("entity", "player");
	/** 3D turn of blocks and entities (degrees around X, Y, Z). */
	public final NumberProp rotX;
	public final NumberProp rotY;
	public final NumberProp rotZ;

	/** A box around any object (for groups, around all their children). */
	public final BoolProp background = new BoolProp("background", false);
	public final ColorProp backgroundColor = new ColorProp("background_color", 0x40000000);
	public final BoolProp outline = new BoolProp("outline", false);
	public final ColorProp outlineColor = new ColorProp("outline_color", 0xFFFFFFFF);
	public final NumberProp padding = new NumberProp("padding", 2, 0, 50, 1);

	public final List<HudObject> children = new ArrayList<>();
	public HudObject parent;

	/** Where the content box was drawn last frame (screen pose of its corner, and its size), for the editor. */
	public final Matrix3x2f lastPose = new Matrix3x2f();
	/** Screen pose of the object's position point (what children are placed from). */
	public final Matrix3x2f lastOrigin = new Matrix3x2f();
	public float lastW;
	public float lastH;
	public long lastFrame = -1;

	private long laidOut = -1;
	private boolean laidOutEditing;
	private List<FormattedCharSequence> lines = List.of();
	private float textW;
	private boolean emptyText;
	private String itemId;
	private ItemStack itemStack = ItemStack.EMPTY;
	private String blockId;
	private BlockState blockState;

	public HudObject(Type type, String name) {
		this.type = type;
		this.name = name;
		boolean isBlock = type == Type.BLOCK;
		rotX = new NumberProp("rot_x", isBlock ? 30 : 0, -180, 180, 1, "°");
		rotY = new NumberProp("rot_y", isBlock ? 225 : 0, -360, 360, 1, "°");
		rotZ = new NumberProp("rot_z", 0, -180, 180, 1, "°");
		backgroundColor.visibleIf(() -> background.on() || background.hasFx());
		outlineColor.visibleIf(() -> outline.on() || outline.hasFx());
		padding.visibleIf(() -> hasBox() || background.hasFx() || outline.hasFx());
	}

	public List<Prop<?>> transformProps() {
		return List.of(align, x, y, scale, rotation);
	}

	/** What the object shows, by type. */
	public List<Prop<?>> contentProps() {
		return switch (type) {
			case TEXT -> List.of(text, shadow);
			case ITEM -> List.of(item);
			case BLOCK -> List.of(block, rotX, rotY, rotZ);
			case ENTITY -> List.of(entity, rotX, rotY, rotZ);
			case GROUP -> List.of();
		};
	}

	/** Background and outline, for every type. */
	public List<Prop<?>> boxProps() {
		return List.of(background, backgroundColor, outline, outlineColor, padding);
	}

	private List<Prop<?>> allProps() {
		List<Prop<?>> l = new ArrayList<>();
		l.add(visible);
		l.addAll(transformProps());
		l.addAll(contentProps());
		l.addAll(boxProps());
		return l;
	}

	public boolean isAncestorOf(HudObject other) {
		for (HudObject p = other; p != null; p = p.parent) {
			if (p == this) {
				return true;
			}
		}
		return false;
	}

	// ---- content ----

	public boolean hasBox() {
		return background.on() || outline.on();
	}

	private float pad() {
		return hasBox() ? Math.max(0, padding.f()) : 0;
	}

	private void layoutText(Font font, long frame, boolean editing) {
		if (laidOut == frame && laidOutEditing == editing) {
			return;
		}
		laidOut = frame;
		laidOutEditing = editing;
		layoutLines(font, text.render());
		// Empty once tags and rich-text tags are gone ("{TargetBlock}" while looking at the sky).
		emptyText = textW == 0;
		if (emptyText && editing) {
			layoutLines(font, "<color=#808080>∅ " + name + "</color>");
		}
	}

	private void layoutLines(Font font, String rendered) {
		List<FormattedCharSequence> out = new ArrayList<>();
		float w = 0;
		for (Component line : RichText.lines(rendered)) {
			FormattedCharSequence seq = line.getVisualOrderText();
			out.add(seq);
			w = Math.max(w, font.width(seq));
		}
		lines = out;
		textW = w;
	}

	/**
	 * Nothing to show (outside the editor): a text that came out empty, an empty slot, air, no such entity.
	 * Such an object takes no space and draws no background, so a group around it shrinks.
	 */
	private boolean blank(Minecraft mc, long frame, boolean editing) {
		if (editing) {
			return false;
		}
		return switch (type) {
			case TEXT -> {
				layoutText(mc.font, frame, false);
				yield emptyText;
			}
			case ITEM -> stack().isEmpty();
			case BLOCK -> blockState().isAir();
			case ENTITY -> EntityView.entity(entity.get()) == null;
			case GROUP -> false;
		};
	}

	public static Item itemById(String id) {
		Identifier key = Identifier.tryParse(id == null ? "" : id.trim());
		return key == null ? Items.AIR : BuiltInRegistries.ITEM.getOptional(key).orElse(Items.AIR);
	}

	/** {@code stone}, {@code minecraft:oak_stairs[facing=east]}; air when it does not parse. */
	public static BlockState blockById(String id) {
		try {
			return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, id == null ? "" : id.trim(), false).blockState();
		} catch (Exception e) {
			return Blocks.AIR.defaultBlockState();
		}
	}

	private ItemStack stack() {
		String id = item.get();
		if (!Objects.equals(id, itemId)) {
			itemId = id;
			Item it = itemById(id);
			itemStack = it == Items.AIR ? ItemStack.EMPTY : new ItemStack(it);
		}
		return itemStack;
	}

	private BlockState blockState() {
		String id = block.get();
		if (blockState == null || !Objects.equals(id, blockId)) {
			blockId = id;
			blockState = blockById(id);
		}
		return blockState;
	}

	/** Size of what the object shows, without its box (0 for groups). */
	private float contentWidth(Minecraft mc, long frame, boolean editing) {
		return switch (type) {
			case TEXT -> {
				layoutText(mc.font, frame, editing);
				yield textW;
			}
			case ITEM, BLOCK -> 16;
			case ENTITY -> 20;
			case GROUP -> 0;
		};
	}

	private float contentHeight(Minecraft mc, long frame, boolean editing) {
		return switch (type) {
			case TEXT -> {
				layoutText(mc.font, frame, editing);
				yield lines.size() * 10 - 1;
			}
			case ITEM, BLOCK -> 16;
			case ENTITY -> 32;
			case GROUP -> 0;
		};
	}

	/** Size of the box before scaling: the content plus the padding when there is a background or outline (0 for groups). */
	public float width(Minecraft mc, long frame, boolean editing) {
		return type == Type.GROUP ? 0 : contentWidth(mc, frame, editing) + pad() * 2;
	}

	public float height(Minecraft mc, long frame, boolean editing) {
		return type == Type.GROUP ? 0 : contentHeight(mc, frame, editing) + pad() * 2;
	}

	// ---- drawing ----

	/** Moves the pose to this object's position point: the alignment point of the parent box plus X / Y, then rotation and scale. */
	public void place(Matrix3x2f pose, float parentW, float parentH) {
		Anchor a = align.get();
		pose.translate(parentW * a.fx + x.f(), parentH * a.fy + y.f());
		float rot = rotation.f();
		if (rot != 0) {
			pose.rotate((float) Math.toRadians(rot));
		}
		float s = scale.f();
		if (s != 1) {
			pose.scale(s, s);
		}
	}

	/** Where the box lies around the parent's position point: {@code [x1, y1, x2, y2]}, or null. */
	private float[] boxIn(Matrix3x2f parent, Minecraft mc, long frame, boolean editing) {
		if (!visible.on()) {
			return null;
		}
		Matrix3x2f m = new Matrix3x2f(parent);
		place(m, 0, 0);
		float[] local;
		if (type == Type.GROUP) {
			local = childrenBox(mc, frame, editing);
			if (local == null) {
				return null;
			}
			float p = pad();
			local = new float[]{local[0] - p, local[1] - p, local[2] + p, local[3] + p};
		} else {
			if (blank(mc, frame, editing)) {
				return null;
			}
			Anchor a = align.get();
			float w = width(mc, frame, editing), h = height(mc, frame, editing);
			local = new float[]{-w * a.fx, -h * a.fy, w - w * a.fx, h - h * a.fy};
		}
		float[] out = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		Vector2f v = new Vector2f();
		for (float[] c : new float[][]{{local[0], local[1]}, {local[2], local[1]}, {local[2], local[3]}, {local[0], local[3]}}) {
			m.transformPosition(c[0], c[1], v);
			out[0] = Math.min(out[0], v.x);
			out[1] = Math.min(out[1], v.y);
			out[2] = Math.max(out[2], v.x);
			out[3] = Math.max(out[3], v.y);
		}
		return out;
	}

	/** Where this top-level object would lie on a screen of the given size, without drawing it (for previews), or null. */
	public float[] measure(Minecraft mc, float screenW, float screenH, long frame) {
		Anchor a = align.get();
		return boxIn(new Matrix3x2f().translation(screenW * a.fx, screenH * a.fy), mc, frame, false);
	}

	/** The children's boxes around this group's position point, or null when there are none. */
	private float[] childrenBox(Minecraft mc, long frame, boolean editing) {
		float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		Matrix3x2f identity = new Matrix3x2f();
		for (HudObject c : children) {
			float[] cb = c.boxIn(identity, mc, frame, editing);
			if (cb != null) {
				b[0] = Math.min(b[0], cb[0]);
				b[1] = Math.min(b[1], cb[1]);
				b[2] = Math.max(b[2], cb[2]);
				b[3] = Math.max(b[3], cb[3]);
			}
		}
		return b[0] > b[2] ? null : b;
	}

	/** Draws this object and its children; {@code parentW/H} is the screen size for top-level objects, 0 inside a group. */
	public void draw(GuiGraphicsExtractor g, Minecraft mc, float parentW, float parentH, long frame, boolean editing) {
		draw(g, mc, parentW, parentH, frame, editing, true);
	}

	/** {@code record = false} for previews: the editor keeps picking from where the object really is on screen. */
	public void draw(GuiGraphicsExtractor g, Minecraft mc, float parentW, float parentH, long frame, boolean editing, boolean record) {
		if (!visible.on()) {
			return;
		}
		Anchor a = align.get();
		g.pose().pushMatrix();
		Matrix3x2f m = new Matrix3x2f(g.pose());
		place(m, parentW, parentH);
		g.pose().set(m);
		Matrix3x2f boxPose = new Matrix3x2f(m);
		float bw, bh;
		if (type == Type.GROUP) {
			// A group's box wraps its children (when it has a background or outline).
			float[] box = hasBox() ? childrenBox(mc, frame, editing) : null;
			if (box != null) {
				float p = pad();
				boxPose.translate(box[0] - p, box[1] - p);
				bw = box[2] - box[0] + p * 2;
				bh = box[3] - box[1] + p * 2;
			} else {
				bw = 0;
				bh = 0;
			}
		} else if (blank(mc, frame, editing)) {
			bw = 0;
			bh = 0;
		} else {
			bw = width(mc, frame, editing);
			bh = height(mc, frame, editing);
			boxPose.translate(-bw * a.fx, -bh * a.fy);
		}
		if (record) {
			lastOrigin.set(m);
			lastPose.set(boxPose);
			lastW = bw;
			lastH = bh;
			lastFrame = frame;
		}
		if (type == Type.GROUP) {
			drawBox(g, false, boxPose, bw, bh);
			for (HudObject child : children) {
				child.draw(g, mc, 0, 0, frame, editing, record);
			}
			drawBox(g, true, boxPose, bw, bh);
		} else if (bw > 0 && bh > 0) {
			drawBox(g, false, boxPose, bw, bh);
			g.pose().pushMatrix();
			g.pose().set(boxPose);
			float pad = pad();
			g.pose().translate(pad, pad);
			drawContent(g, mc, bw - pad * 2, bh - pad * 2, a, editing);
			g.pose().popMatrix();
			drawBox(g, true, boxPose, bw, bh);
		}
		g.pose().popMatrix();
	}

	/** The background (under the content) or the outline (over it). */
	private void drawBox(GuiGraphicsExtractor g, boolean front, Matrix3x2f pose, float bw, float bh) {
		if (bw <= 0 || bh <= 0 || !(front ? outline.on() : background.on())) {
			return;
		}
		g.pose().pushMatrix();
		g.pose().set(pose);
		int w = Math.round(bw), h = Math.round(bh);
		if (front) {
			g.outline(0, 0, w, h, outlineColor.argb());
		} else {
			g.fill(0, 0, w, h, backgroundColor.argb());
		}
		g.pose().popMatrix();
	}

	private void drawContent(GuiGraphicsExtractor g, Minecraft mc, float w, float h, Anchor a, boolean editing) {
		switch (type) {
			case TEXT -> {
				int yy = 0;
				for (FormattedCharSequence line : lines) {
					float lx = (textW - mc.font.width(line)) * a.fx;
					g.text(mc.font, line, Math.round(lx), yy, 0xFFFFFFFF, shadow.on());
					yy += 10;
				}
			}
			case ITEM -> {
				ItemStack st = stack();
				if (!st.isEmpty()) {
					g.item(st, 0, 0);
				} else if (editing) {
					g.outline(0, 0, 16, 16, 0x60FFFFFF);
				}
			}
			case BLOCK -> {
				BlockState st = blockState();
				if (!st.isAir()) {
					BlockPip.draw(g, st, 0, 0, w, h, rotX.f(), rotY.f(), rotZ.f());
				} else if (editing) {
					g.outline(0, 0, Math.round(w), Math.round(h), 0x60FFFFFF);
				}
			}
			case ENTITY -> {
				Entity e = EntityView.entity(entity.get());
				if (e != null) {
					EntityView.draw(g, e, 0, 0, w, h, rotX.f(), rotY.f(), rotZ.f());
				} else if (editing) {
					g.outline(0, 0, Math.round(w), Math.round(h), 0x60FFFFFF);
				}
			}
			case GROUP -> {
			}
		}
	}

	/** True when the screen point is inside where this object's box was last drawn. */
	public boolean hit(double sx, double sy, long frame) {
		if (lastFrame != frame || lastW <= 0 || lastH <= 0) {
			return false;
		}
		Vector2f p = new Matrix3x2f(lastPose).invert().transformPosition((float) sx, (float) sy, new Vector2f());
		return p.x >= 0 && p.y >= 0 && p.x <= lastW && p.y <= lastH;
	}

	/** Screen-space bounds {@code [x1, y1, x2, y2]} of what this object (and, for groups, its children) drew. */
	public float[] bounds(long frame) {
		float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
		collect(b, frame);
		return b[0] > b[2] ? null : b;
	}

	private void collect(float[] b, long frame) {
		if (lastFrame != frame || !visible.on()) {
			return;
		}
		if (lastW > 0 && lastH > 0) {
			Vector2f v = new Vector2f();
			float[][] corners = {{0, 0}, {lastW, 0}, {lastW, lastH}, {0, lastH}};
			for (float[] c : corners) {
				lastPose.transformPosition(c[0], c[1], v);
				b[0] = Math.min(b[0], v.x);
				b[1] = Math.min(b[1], v.y);
				b[2] = Math.max(b[2], v.x);
				b[3] = Math.max(b[3], v.y);
			}
		}
		for (HudObject c : children) {
			c.collect(b, frame);
		}
	}

	// ---- copy & JSON ----

	public HudObject copy() {
		HudObject c = fromJson(toJson());
		c.parent = null;
		return c;
	}

	public JsonObject toJson() {
		JsonObject o = new JsonObject();
		o.addProperty("type", type.name().toLowerCase(Locale.ROOT));
		o.addProperty("name", name);
		for (Prop<?> p : allProps()) {
			if (!p.isDefault()) {
				o.add(p.id(), p.toJson());
			}
		}
		if (!children.isEmpty()) {
			JsonArray arr = new JsonArray();
			children.forEach(c -> arr.add(c.toJson()));
			o.add("children", arr);
		}
		return o;
	}

	public static HudObject fromJson(JsonObject o) {
		Type type;
		try {
			type = Type.valueOf(o.get("type").getAsString().toUpperCase(Locale.ROOT));
		} catch (RuntimeException e) {
			type = Type.TEXT;
		}
		HudObject obj = new HudObject(type, o.has("name") ? o.get("name").getAsString() : "");
		for (Prop<?> p : obj.allProps()) {
			JsonElement e = o.get(p.id());
			if (e != null) {
				try {
					p.fromJson(e);
				} catch (RuntimeException ignored) {
					p.reset();
				}
			}
		}
		if (o.has("children")) {
			for (JsonElement e : o.getAsJsonArray("children")) {
				HudObject child = fromJson(e.getAsJsonObject());
				child.parent = obj;
				obj.children.add(child);
			}
		}
		return obj;
	}
}
