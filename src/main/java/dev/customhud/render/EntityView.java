package dev.customhud.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;

/** Draws an entity in a GUI box like the inventory's player preview, turned by X / Y / Z angles. */
public final class EntityView {
	private static final Map<EntityType<?>, Entity> CACHE = new HashMap<>();
	private static ClientLevel cachedLevel;

	private EntityView() {
	}

	/** {@code player} (or empty) is you; anything else an entity type id such as {@code minecraft:zombie}. */
	public static Entity entity(String id) {
		Minecraft mc = Minecraft.getInstance();
		String key = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
		if (key.isEmpty() || key.equals("player") || key.equals("self") || key.equals("me")) {
			return mc.player;
		}
		if (mc.level == null) {
			return null;
		}
		if (mc.level != cachedLevel) {
			CACHE.clear();
			cachedLevel = mc.level;
		}
		Identifier rl = Identifier.tryParse(key);
		EntityType<?> type = rl == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(rl).orElse(null);
		if (type == null) {
			return null;
		}
		return CACHE.computeIfAbsent(type, t -> {
			try {
				return t.create(mc.level, EntitySpawnReason.LOAD);
			} catch (RuntimeException e) {
				return null;
			}
		});
	}

	/** The entity fitted into the box {@code (x, y, w, h)} of the current pose (drawn axis-aligned). */
	public static void draw(GuiGraphicsExtractor g, Entity entity, float x, float y, float w, float h, float rx, float ry, float rz) {
		Minecraft mc = Minecraft.getInstance();
		@SuppressWarnings({"rawtypes", "unchecked"})
		EntityRenderState state = ((net.minecraft.client.renderer.entity.EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(entity))
				.createRenderState(entity, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			// Face the viewer with the head straight; the X / Y / Z angles turn the whole model.
			living.bodyRot = 180;
			living.yRot = 0;
			living.xRot = 0;
		}
		Matrix3x2f pose = new Matrix3x2f(g.pose());
		Vector2f a = pose.transformPosition(x, y, new Vector2f());
		Vector2f b = pose.transformPosition(x + w, y + h, new Vector2f());
		int x0 = Math.round(Math.min(a.x, b.x)), y0 = Math.round(Math.min(a.y, b.y));
		int x1 = Math.round(Math.max(a.x, b.x)), y1 = Math.round(Math.max(a.y, b.y));
		if (x1 - x0 < 1 || y1 - y0 < 1) {
			return;
		}
		float bw = Math.max(0.1f, state.boundingBoxWidth), bh = Math.max(0.1f, state.boundingBoxHeight);
		float size = Math.min((x1 - x0) / bw, (y1 - y0) / bh) * 0.9f;
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI)
				.rotateX((float) Math.toRadians(rx)).rotateY((float) Math.toRadians(ry)).rotateZ((float) Math.toRadians(rz));
		Quaternionf camera = new Quaternionf().rotateX((float) Math.toRadians(rx));
		g.pose().pushMatrix();
		g.pose().identity();
		g.entity(state, size, new Vector3f(0, bh / 2f, 0), rotation, camera, x0, y0, x1, y1);
		g.pose().popMatrix();
	}
}
