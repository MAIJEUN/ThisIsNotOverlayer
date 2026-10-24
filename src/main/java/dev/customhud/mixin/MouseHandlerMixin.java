package dev.customhud.mixin;

import dev.customhud.tag.impl.Trackers;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void customhud$button(long window, MouseButtonInfo info, int action, CallbackInfo ci) {
		Trackers.onMouseButton(info.button(), action);
	}

	@Inject(method = "onMove", at = @At("HEAD"))
	private void customhud$move(long window, double x, double y, double dx, double dy, CallbackInfo ci) {
		Trackers.onMouseMove(dx, dy);
	}

	@Inject(method = "onScroll", at = @At("HEAD"))
	private void customhud$scroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
		Trackers.onScroll(xOffset, yOffset);
	}
}
