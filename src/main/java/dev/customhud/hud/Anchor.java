package dev.customhud.hud;

/** One of nine points of a box: where an object attaches to its parent, and which of its own points sits there. */
public enum Anchor {
	TOP_LEFT(0, 0), TOP(0.5f, 0), TOP_RIGHT(1, 0),
	LEFT(0, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1, 0.5f),
	BOTTOM_LEFT(0, 1), BOTTOM(0.5f, 1), BOTTOM_RIGHT(1, 1);

	public final float fx;
	public final float fy;

	Anchor(float fx, float fy) {
		this.fx = fx;
		this.fy = fy;
	}

	public int column() {
		return ordinal() % 3;
	}

	public int row() {
		return ordinal() / 3;
	}

	public static Anchor at(int column, int row) {
		return values()[row * 3 + column];
	}
}
