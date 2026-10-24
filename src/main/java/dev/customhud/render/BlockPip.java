package dev.customhud.render;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector2f;

/** Draws a block model in 3D inside a GUI rectangle (like an inventory block, with free yaw and pitch). */
public final class BlockPip {
	private BlockPip() {
	}

	record State(BlockState block, float rx, float ry, float rz, float zoom, int x0, int y0, int x1, int y1, float scale,
	             ScreenRectangle scissorArea, ScreenRectangle bounds) implements PictureInPictureRenderState {
	}

	public static void register() {
		PictureInPictureRendererRegistry.register(ctx -> new Renderer());
	}

	/**
	 * Adds a block view; the box is given in the current pose but drawn axis-aligned (picture-in-picture
	 * textures cannot rotate), so the pose's translation and scale are applied and its rotation is not.
	 */
	/**
	 * A block turned by X / Y / Z angles (30 / 225 / 0 is the inventory look), filling the box. The box is
	 * given in the current pose but drawn axis-aligned: picture-in-picture textures cannot be rotated.
	 */
	public static void draw(GuiGraphicsExtractor g, BlockState block, float x, float y, float w, float h, float rx, float ry, float rz) {
		Vector2f a = g.pose().transformPosition(x, y, new Vector2f());
		Vector2f b = g.pose().transformPosition(x + w, y + h, new Vector2f());
		int x0 = Math.round(Math.min(a.x, b.x)), y0 = Math.round(Math.min(a.y, b.y));
		int x1 = Math.round(Math.max(a.x, b.x)), y1 = Math.round(Math.max(a.y, b.y));
		if (x1 - x0 < 1 || y1 - y0 < 1) {
			return;
		}
		// Seen from the inventory angle a unit cube is about 1.6 units tall; fit that in the box.
		float scale = Math.min(x1 - x0, y1 - y0) / 1.7f;
		ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
		((dev.customhud.mixin.GuiGraphicsAccessor) g).customhud$renderState().addPicturesInPictureState(
				new State(block, rx, ry, rz, 1, x0, y0, x1, y1, scale, null, bounds));
	}

	static final class Renderer extends PictureInPictureRenderer<State> {
		private BlockModelResolver resolver;
		private final BlockModelRenderState model = new BlockModelRenderState();

		@Override
		public Class<State> getRenderStateClass() {
			return State.class;
		}

		@Override
		protected void renderToTexture(State state, PoseStack pose, SubmitNodeCollector collector) {
			Minecraft mc = Minecraft.getInstance();
			if (resolver == null) {
				resolver = new BlockModelResolver(mc.getModelManager());
			}
			mc.gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);
			pose.rotateDegrees(Axis.ZP, 180);
			pose.rotateDegrees(Axis.XP, -state.rx());
			pose.rotateDegrees(Axis.YP, state.ry());
			pose.rotateDegrees(Axis.ZP, state.rz());
			pose.translate(-0.5f, -0.5f, -0.5f);
			model.clear();
			resolver.update(model, state.block(), BlockDisplayContext.create());
			model.submit(pose, collector, 0xF000F0, OverlayTexture.NO_OVERLAY, 0);
		}

		/** Centre the model vertically (the default puts the origin at the bottom, for entities' feet). */
		@Override
		protected float getTranslateY(int height, int guiScale) {
			return height / 2f;
		}

		@Override
		protected String getTextureLabel() {
			return "customhud block";
		}
	}
}
