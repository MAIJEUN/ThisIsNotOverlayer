package dev.customhud.tag.impl;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;

import java.util.ArrayList;
import java.util.List;

/** Registers every built-in tag. */
public final class TagLibrary {
	private TagLibrary() {
	}

	public static void registerBuiltIns() {
		List<TagCore> all = new ArrayList<>();
		all.addAll(CoreTags.all());
		all.addAll(InputTags.all());
		all.addAll(MinecraftTags.all());
		all.add(JsExprTag.create());
		TagManager.registerAll(all);
	}

	/** Categories for the tag browser, by tag name. */
	public static String category(TagCore tag) {
		if (tag.kind() == TagCore.Kind.JS) {
			return "script";
		}
		return CATEGORY.getOrDefault(tag.name(), "minecraft");
	}

	private static final java.util.Map<String, String> CATEGORY = new java.util.HashMap<>();

	static {
		for (String n : List.of("Ticks", "UtcTicks", "Millisecond", "Second", "Minute", "Hour", "Hour12", "AmPm", "IsAm", "IsPm",
				"Day", "DayOfYear", "DayOfWeek", "Month", "Year")) {
			CATEGORY.put(n, "time");
		}
		for (String n : List.of("EasedValue", "ColorRange", "MovingMan", "JSExpr")) {
			CATEGORY.put(n, "effects");
		}
		for (String n : List.of("Kps", "FrameTime", "Fps", "ProcessorCount", "MemUsed", "MemMax", "MemPercent", "Ping")) {
			CATEGORY.put(n, "performance");
		}
		for (String n : List.of("MouseX", "MouseY", "IsKeyHeld", "IsKeyDown", "IsKeyUp", "IsMouseHeld", "IsMouseDown", "IsMouseUp",
				"IsAnyMouseHeld", "IsAnyMouseDown", "IsAnyMouseUp", "MouseScrollX", "MouseScrollY", "MouseDeltaX", "MouseDeltaY",
				"IsAnyKeyHeld", "IsAnyKeyDown", "Cps", "RightCps", "IsBindHeld", "BindKey")) {
			CATEGORY.put(n, "input");
		}
		for (String n : List.of("ScreenWidth", "ScreenHeight", "IsFullScreen", "TimeScale", "TimeSinceStart", "Time", "DeltaTime",
				"UnscaledDeltaTime", "FrameCount", "IsFocused", "IsCursorVisible", "VSyncCount", "TargetFrameRate", "MasterVolume",
				"IsAudioMuted", "OperatingSystem")) {
			CATEGORY.put(n, "application");
		}
	}

	public static final List<String> CATEGORIES = List.of("performance", "time", "effects", "input", "application", "minecraft", "script");
}
