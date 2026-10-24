package dev.customhud.tag.impl;

import com.mojang.blaze3d.platform.InputConstants;

/** Unity KeyCode names (as used in Overlayer texts, e.g. {@code {IsKeyHeld:Space}}) mapped to Minecraft keys. */
public enum KeyCode {
	A(InputConstants.KEY_A), B(InputConstants.KEY_B), C(InputConstants.KEY_C), D(InputConstants.KEY_D), E(InputConstants.KEY_E),
	F(InputConstants.KEY_F), G(InputConstants.KEY_G), H(InputConstants.KEY_H), I(InputConstants.KEY_I), J(InputConstants.KEY_J),
	K(InputConstants.KEY_K), L(InputConstants.KEY_L), M(InputConstants.KEY_M), N(InputConstants.KEY_N), O(InputConstants.KEY_O),
	P(InputConstants.KEY_P), Q(InputConstants.KEY_Q), R(InputConstants.KEY_R), S(InputConstants.KEY_S), T(InputConstants.KEY_T),
	U(InputConstants.KEY_U), V(InputConstants.KEY_V), W(InputConstants.KEY_W), X(InputConstants.KEY_X), Y(InputConstants.KEY_Y),
	Z(InputConstants.KEY_Z),
	Alpha0(InputConstants.KEY_0), Alpha1(InputConstants.KEY_1), Alpha2(InputConstants.KEY_2), Alpha3(InputConstants.KEY_3),
	Alpha4(InputConstants.KEY_4), Alpha5(InputConstants.KEY_5), Alpha6(InputConstants.KEY_6), Alpha7(InputConstants.KEY_7),
	Alpha8(InputConstants.KEY_8), Alpha9(InputConstants.KEY_9),
	Keypad0(InputConstants.KEY_NUMPAD0), Keypad1(InputConstants.KEY_NUMPAD1), Keypad2(InputConstants.KEY_NUMPAD2),
	Keypad3(InputConstants.KEY_NUMPAD3), Keypad4(InputConstants.KEY_NUMPAD4), Keypad5(InputConstants.KEY_NUMPAD5),
	Keypad6(InputConstants.KEY_NUMPAD6), Keypad7(InputConstants.KEY_NUMPAD7), Keypad8(InputConstants.KEY_NUMPAD8),
	Keypad9(InputConstants.KEY_NUMPAD9), KeypadEnter(InputConstants.KEY_NUMPADENTER), KeypadPlus(InputConstants.KEY_ADD),
	KeypadMultiply(InputConstants.KEY_MULTIPLY),
	F1(InputConstants.KEY_F1), F2(InputConstants.KEY_F2), F3(InputConstants.KEY_F3), F4(InputConstants.KEY_F4),
	F5(InputConstants.KEY_F5), F6(InputConstants.KEY_F6), F7(InputConstants.KEY_F7), F8(InputConstants.KEY_F8),
	F9(InputConstants.KEY_F9), F10(InputConstants.KEY_F10), F11(InputConstants.KEY_F11), F12(InputConstants.KEY_F12),
	Space(InputConstants.KEY_SPACE), Return(InputConstants.KEY_RETURN), Escape(InputConstants.KEY_ESCAPE),
	Tab(InputConstants.KEY_TAB), Backspace(InputConstants.KEY_BACKSPACE), Delete(InputConstants.KEY_DELETE),
	Insert(InputConstants.KEY_INSERT), Home(InputConstants.KEY_HOME), End(InputConstants.KEY_END),
	PageUp(InputConstants.KEY_PAGEUP), PageDown(InputConstants.KEY_PAGEDOWN), CapsLock(InputConstants.KEY_CAPSLOCK),
	LeftShift(InputConstants.KEY_LSHIFT), RightShift(InputConstants.KEY_RSHIFT),
	LeftControl(InputConstants.KEY_LCONTROL), RightControl(InputConstants.KEY_RCONTROL),
	LeftAlt(InputConstants.KEY_LALT), RightAlt(InputConstants.KEY_RALT),
	UpArrow(InputConstants.KEY_UP), DownArrow(InputConstants.KEY_DOWN), LeftArrow(InputConstants.KEY_LEFT),
	RightArrow(InputConstants.KEY_RIGHT),
	Minus(InputConstants.KEY_MINUS), Equals(InputConstants.KEY_EQUALS), LeftBracket(InputConstants.KEY_LBRACKET),
	RightBracket(InputConstants.KEY_RBRACKET), Semicolon(InputConstants.KEY_SEMICOLON), Quote(InputConstants.KEY_APOSTROPHE),
	Comma(InputConstants.KEY_COMMA), Period(InputConstants.KEY_PERIOD), Slash(InputConstants.KEY_SLASH),
	Backslash(InputConstants.KEY_BACKSLASH), BackQuote(InputConstants.KEY_GRAVE),
	Mouse0(-1, InputConstants.MOUSE_BUTTON_LEFT), Mouse1(-1, InputConstants.MOUSE_BUTTON_RIGHT),
	Mouse2(-1, InputConstants.MOUSE_BUTTON_MIDDLE), Mouse3(-1, InputConstants.MOUSE_BUTTON_4), Mouse4(-1, InputConstants.MOUSE_BUTTON_5);

	public final int key;
	public final int mouseButton;

	KeyCode(int key) {
		this(key, -1);
	}

	KeyCode(int key, int mouseButton) {
		this.key = key;
		this.mouseButton = mouseButton;
	}

	/** Unity mouse button index (0 left, 1 right, 2 middle) → Minecraft button. */
	public static int unityMouse(int button) {
		return switch (button) {
			case 0 -> InputConstants.MOUSE_BUTTON_LEFT;
			case 1 -> InputConstants.MOUSE_BUTTON_RIGHT;
			case 2 -> InputConstants.MOUSE_BUTTON_MIDDLE;
			default -> button + 1;
		};
	}
}
