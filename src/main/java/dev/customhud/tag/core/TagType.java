package dev.customhud.tag.core;

/** Tag behaviour flags; the same bit values as Overlayer's TagType so JS scripts can share them. */
public final class TagType {
	public static final int NONE = 0;
	public static final int BLOCK_ON_NOT_PLAYING = 1;
	public static final int BLOCK_ON_PAUSED = 1 << 1;
	public static final int BLOCK_ON_ALL = BLOCK_ON_NOT_PLAYING | BLOCK_ON_PAUSED;
	/** A trailing argument may be a format string: {@code {Fps:500,0.00}}. */
	public static final int PROCESS_FORMAT = 1 << 8;
	/** Only callable from JavaScript; using it in text is an error. */
	public static final int JS_ONLY = 1 << 9;
	/** Not listed in the tag browser. */
	public static final int HIDE = 1 << 16;
	/** Compiled by the tag itself (e.g. JSExpr). */
	public static final int ADVANCED = 1 << 24;

	/** {@code globalThis.TagType} for scripts. */
	public static final String SCRIPT = "globalThis.TagType = Object.freeze({ None: 0, BlockOnNotPlaying: 1, BlockOnPaused: 2, "
			+ "BlockOnAll: 3, ProcessFormat: 256, JsOnly: 512, Hide: 65536, Advanced: 16777216 });";

	private TagType() {
	}

	public static boolean has(int flags, int flag) {
		return (flags & flag) != 0;
	}
}
