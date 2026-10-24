package dev.customhud.mixin;

import dev.customhud.tag.impl.Trackers;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks the start of every frame (frame timing for Fps/FrameTime, key down/up edges, mouse deltas). */
@Mixin(Minecraft.class)
public class MinecraftMixin {
	@Inject(method = "runTick", at = @At("HEAD"))
	private void customhud$newFrame(boolean advanceGameTime, CallbackInfo ci) {
		Trackers.newFrame();
	}
}
