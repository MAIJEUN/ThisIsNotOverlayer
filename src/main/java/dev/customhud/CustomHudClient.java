package dev.customhud;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.editor.HudScreen;
import dev.customhud.hud.HudManager;
import dev.customhud.hud.HudRenderer;
import dev.customhud.render.BlockPip;
import dev.customhud.js.JsEngine;
import dev.customhud.tag.compile.Compiler;
import dev.customhud.tag.impl.TagLibrary;
import dev.customhud.tag.impl.Trackers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomHudClient implements ClientModInitializer {
	public static final String MOD_ID = "customhud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(id("main"));
	public static KeyMapping openEditorKey;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		// Tags first, then scripts (which may register more tags), then the elements that use them.
		TagLibrary.registerBuiltIns();
		Compiler.isPlaying = () -> Minecraft.getInstance().level != null;
		Compiler.isPaused = () -> Minecraft.getInstance().isPaused();
		java.nio.file.Path config = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
		JsEngine.init(config.resolve("Script")).loader().reloadAll();
		HudManager.init(config, FabricLoader.getInstance().getConfigDir().resolve("customhud.json")).load();
		BlockPip.register();

		openEditorKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.customhud.open_editor",
				InputConstants.Type.KEYBOARD,
				InputConstants.KEY_RSHIFT,
				KEY_CATEGORY));

		HudElementRegistry.addLast(id("elements"), new HudRenderer());

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Trackers.tick(client);
			while (openEditorKey.consumeClick()) {
				if (client.gui.screen() == null) {
					client.gui.setScreen(new HudScreen());
				}
			}
		});
	}
}
