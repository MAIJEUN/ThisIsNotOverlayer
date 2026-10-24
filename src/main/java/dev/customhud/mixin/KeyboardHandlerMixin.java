package dev.customhud.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.customhud.tag.impl.Trackers;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void customhud$recordKey(long window, int action, KeyEvent event, CallbackInfo ci) {
		if (action != InputConstants.REPEAT) {
			Trackers.onKey(InputConstants.getKey(event).getValue(), action);
		}
	}
}
