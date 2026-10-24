package dev.customhud.hud;

import dev.customhud.prop.Prop;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Draws every enabled preset. While the editor is open, empty texts and items show placeholders. */
public class HudRenderer implements HudElement {
	private static long frame;
	public static boolean editing;
	/** The preset open in the editor: drawn even while switched off, so it can be edited. */
	public static Preset editingPreset;

	public static long frame() {
		return frame;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
		frame++;
		Prop.nextFrame();
		if (HudManager.get() == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		editing = mc.gui.screen() instanceof dev.customhud.editor.EditingScreen;
		if (!editing) {
			editingPreset = null;
		}
		float w = g.guiWidth(), h = g.guiHeight();
		for (Preset p : HudManager.get().presets()) {
			if (!p.enabled.on() && !(editing && p == editingPreset)) {
				continue;
			}
			for (HudObject o : p.objects) {
				try {
					o.draw(g, mc, w, h, frame, editing, true);
				} catch (RuntimeException e) {
					dev.customhud.CustomHudClient.LOGGER.debug("HUD object failed to draw", e);
				}
			}
		}
	}
}
