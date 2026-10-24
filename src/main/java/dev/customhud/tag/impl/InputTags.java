package dev.customhud.tag.impl;

import com.mojang.blaze3d.platform.Window;
import dev.customhud.tag.core.TagCore;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;

import java.util.ArrayList;
import java.util.List;

/** Overlayer's Input and Unity tags, mapped onto Minecraft (same names; mouse buttons use Unity's 0/1/2). */
public final class InputTags {
	private InputTags() {
	}

	private static Minecraft mc() {
		return Minecraft.getInstance();
	}

	public static List<TagCore> all() {
		List<TagCore> t = new ArrayList<>();
		t.add(Def.value("MouseX", Float.class, "Current mouse X position in screen coordinates", () -> (float) mc().mouseHandler.xpos()));
		t.add(Def.value("MouseY", Float.class, "Current mouse Y position in screen coordinates (from the bottom, like Unity)",
				() -> (float) (mc().getWindow().getHeight() - mc().mouseHandler.ypos())));
		t.add(Def.of("IsKeyHeld", Boolean.class).param("key", KeyCode.class)
				.desc("Returns true while the specified key is held down\nEx) {IsKeyHeld:A}, {IsKeyHeld:Space}")
				.fn(a -> held((KeyCode) a[0])));
		t.add(Def.of("IsKeyDown", Boolean.class).param("key", KeyCode.class)
				.desc("Returns true only on the frame the specified key was pressed\nEx) {IsKeyDown:A}")
				.fn(a -> {
					KeyCode k = (KeyCode) a[0];
					return k.mouseButton >= 0 ? Trackers.mousePressedThisFrame(k.mouseButton) : Trackers.keyPressedThisFrame(k.key);
				}));
		t.add(Def.of("IsKeyUp", Boolean.class).param("key", KeyCode.class)
				.desc("Returns true only on the frame the specified key was released\nEx) {IsKeyUp:A}")
				.fn(a -> {
					KeyCode k = (KeyCode) a[0];
					return k.mouseButton >= 0 ? Trackers.mouseReleasedThisFrame(k.mouseButton) : Trackers.keyReleasedThisFrame(k.key);
				}));
		t.add(Def.of("IsMouseHeld", Boolean.class).opt("button", Integer.class, 0)
				.desc("Returns true while the specified mouse button is held down\n0: Left, 1: Right, 2: Middle\nEx) {IsMouseHeld:0}")
				.fn(a -> Trackers.mouseHeld(KeyCode.unityMouse((int) a[0]))));
		t.add(Def.of("IsMouseDown", Boolean.class).opt("button", Integer.class, 0)
				.desc("Returns true only on the frame the specified mouse button was pressed\n0: Left, 1: Right, 2: Middle\nEx) {IsMouseDown:0}")
				.fn(a -> Trackers.mousePressedThisFrame(KeyCode.unityMouse((int) a[0]))));
		t.add(Def.of("IsMouseUp", Boolean.class).opt("button", Integer.class, 0)
				.desc("Returns true only on the frame the specified mouse button was released\n0: Left, 1: Right, 2: Middle\nEx) {IsMouseUp:0}")
				.fn(a -> Trackers.mouseReleasedThisFrame(KeyCode.unityMouse((int) a[0]))));
		t.add(Def.value("IsAnyMouseHeld", Boolean.class, "Returns true while any mouse button (Left/Right/Middle) is held down",
				() -> anyMouse(Trackers::mouseHeld)));
		t.add(Def.value("IsAnyMouseDown", Boolean.class, "Returns true only on the frame any mouse button (Left/Right/Middle) was pressed",
				() -> anyMouse(Trackers::mousePressedThisFrame)));
		t.add(Def.value("IsAnyMouseUp", Boolean.class, "Returns true only on the frame any mouse button (Left/Right/Middle) was released",
				() -> anyMouse(Trackers::mouseReleasedThisFrame)));
		t.add(Def.value("MouseScrollX", Float.class, "Mouse scroll wheel delta on the X axis", () -> (float) Trackers.scrollX()));
		t.add(Def.value("MouseScrollY", Float.class, "Mouse scroll wheel delta on the Y axis", () -> (float) Trackers.scrollY()));
		t.add(Def.value("MouseDeltaX", Float.class, "Mouse movement delta on the X axis since the last frame", () -> (float) Trackers.mouseDX()));
		t.add(Def.value("MouseDeltaY", Float.class, "Mouse movement delta on the Y axis since the last frame", () -> (float) -Trackers.mouseDY()));
		t.add(Def.value("IsAnyKeyHeld", Boolean.class, "Returns true while any key or mouse button is held down", Trackers::anyHeld));
		t.add(Def.value("IsAnyKeyDown", Boolean.class, "Returns true only on the frame any key or mouse button was pressed", Trackers::anyKeyDownThisFrame));

		// Unity-style application / screen / time
		t.add(Def.value("ScreenWidth", Integer.class, "Current screen width in pixels", () -> mc().getWindow().getWidth()));
		t.add(Def.value("ScreenHeight", Integer.class, "Current screen height in pixels", () -> mc().getWindow().getHeight()));
		t.add(Def.value("IsFullScreen", Boolean.class, "Returns true if the game is in fullscreen mode", () -> mc().options.fullscreen().get()));
		t.add(Def.value("TimeScale", Float.class, "Game speed (tick rate / 20)",
				() -> mc().level == null ? 1f : mc().level.tickRateManager().tickrate() / 20f));
		t.add(Def.value("TimeSinceStart", Double.class, "Total time elapsed since game start, in seconds", Trackers::now));
		t.add(Def.value("Time", Double.class, "Time in seconds since the game started", Trackers::now));
		t.add(Def.value("DeltaTime", Float.class, "Time in seconds since the last frame", () -> (float) Trackers.deltaTime()));
		t.add(Def.value("UnscaledDeltaTime", Float.class, "Unscaled time in seconds since the last frame", () -> (float) Trackers.deltaTime()));
		t.add(Def.value("FrameCount", Integer.class, "Total number of frames that have passed since game start", () -> (int) Trackers.frameCount()));
		t.add(Def.value("IsFocused", Boolean.class, "Returns true if the game window currently has focus", () -> {
			Window w = mc().getWindow();
			return w.isFocused();
		}));
		t.add(Def.value("IsCursorVisible", Boolean.class, "Returns true if the mouse cursor is currently visible", () -> !mc().mouseHandler.isMouseGrabbed()));
		t.add(Def.value("VSyncCount", Integer.class, "VSync setting (0: Disabled, 1: Enabled)", () -> mc().options.enableVsync().get() ? 1 : 0));
		t.add(Def.value("TargetFrameRate", Integer.class, "Max framerate setting (-1 if unlimited)", () -> {
			int limit = mc().options.framerateLimit().get();
			return limit >= 260 ? -1 : limit;
		}));
		t.add(Def.value("MasterVolume", Float.class, "Master volume (0.0 to 1.0)", () -> mc().options.getSoundSourceVolume(SoundSource.MASTER)));
		t.add(Def.value("IsAudioMuted", Boolean.class, "Returns true if the master volume is 0", () -> mc().options.getSoundSourceVolume(SoundSource.MASTER) <= 0f));
		t.add(Def.value("OperatingSystem", String.class, "Operating system version",
				() -> System.getProperty("os.name") + " " + System.getProperty("os.version")));
		return t;
	}

	private static boolean held(KeyCode k) {
		return k.mouseButton >= 0 ? Trackers.mouseHeld(k.mouseButton) : Trackers.keyHeld(k.key);
	}

	private static boolean anyMouse(java.util.function.IntPredicate test) {
		return test.test(KeyCode.unityMouse(0)) || test.test(KeyCode.unityMouse(1)) || test.test(KeyCode.unityMouse(2));
	}
}
