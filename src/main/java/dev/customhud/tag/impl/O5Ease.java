package dev.customhud.tag.impl;

/** Easing names and curves of Overlayer's O5Ease (float math, as in Effects.EaseValue). */
public enum O5Ease {
	Linear, InSine, OutSine, InOutSine, InQuad, OutQuad, InOutQuad, InCubic, OutCubic, InOutCubic, OutExpo, OutCirc, OutBack,
	InQuart, OutQuart, InOutQuart, InQuint, OutQuint, InOutQuint, InExpo, InOutExpo, InCirc, InOutCirc, InBack, InOutBack,
	InElastic, OutElastic, InOutElastic, InBounce, OutBounce, InOutBounce;

	private static final float PI = (float) Math.PI;

	public float apply(float p) {
		return switch (this) {
			case Linear -> p;
			case InSine -> 1f - cos(p * PI * 0.5f);
			case OutSine -> sin(p * PI * 0.5f);
			case InOutSine -> -(cos(PI * p) - 1f) * 0.5f;
			case InQuad -> p * p;
			case OutQuad -> 1f - ((1f - p) * (1f - p));
			case InOutQuad -> p < 0.5f ? 2f * p * p : 1f - (pow(-2f * p + 2f, 2f) / 2f);
			case InCubic -> p * p * p;
			case OutCubic -> 1f - pow(1f - p, 3f);
			case InOutCubic -> p < 0.5f ? 4f * p * p * p : 1f - (pow(-2f * p + 2f, 3f) / 2f);
			case OutExpo -> p >= 1f ? 1f : 1f - pow(2f, -10f * p);
			case OutCirc -> (float) Math.sqrt(1f - pow(p - 1f, 2f));
			case OutBack -> 1f + (2.70158f * pow(p - 1f, 3f)) + (1.70158f * pow(p - 1f, 2f));
			case InQuart -> p * p * p * p;
			case OutQuart -> 1f - pow(1f - p, 4f);
			case InOutQuart -> p < 0.5f ? 8f * p * p * p * p : 1f - pow(-2f * p + 2f, 4f) / 2f;
			case InQuint -> p * p * p * p * p;
			case OutQuint -> 1f - pow(1f - p, 5f);
			case InOutQuint -> p < 0.5f ? 16f * p * p * p * p * p : 1f - pow(-2f * p + 2f, 5f) / 2f;
			case InExpo -> p <= 0f ? 0f : pow(2f, 10f * p - 10f);
			case InOutExpo -> p <= 0f ? 0f : p >= 1f ? 1f : p < 0.5f ? pow(2f, 20f * p - 10f) / 2f : (2f - pow(2f, -20f * p + 10f)) / 2f;
			case InCirc -> 1f - (float) Math.sqrt(1f - p * p);
			case InOutCirc -> p < 0.5f ? (1f - (float) Math.sqrt(1f - 4f * p * p)) / 2f : ((float) Math.sqrt(1f - pow(-2f * p + 2f, 2f)) + 1f) / 2f;
			case InBack -> 2.70158f * p * p * p - 1.70158f * p * p;
			case InOutBack -> p < 0.5f
					? (4f * p * p * ((3.59491f * 2f * p) - 2.59491f)) / 2f
					: (pow(2f * p - 2f, 2f) * ((3.59491f * (2f * p - 2f)) + 2.59491f) + 2f) / 2f;
			case InElastic -> p <= 0f ? 0f : p >= 1f ? 1f : -pow(2f, 10f * p - 10f) * sin((10f * p - 10.75f) * 2.0944f);
			case OutElastic -> p <= 0f ? 0f : p >= 1f ? 1f : pow(2f, -10f * p) * sin((10f * p - 0.75f) * 2.0944f) + 1f;
			case InOutElastic -> p <= 0f ? 0f : p >= 1f ? 1f : p < 0.5f
					? -(pow(2f, 20f * p - 10f) * sin((20f * p - 11.125f) * 1.39626f)) / 2f
					// Same as Overlayer (its second half lacks the "+ 1"); kept identical on purpose.
					: pow(2f, -20f * p + 10f) * sin((20f * p - 11.125f) * 1.39626f) / 2f;
			case InBounce -> 1f - bounceOut(1f - p);
			case OutBounce -> bounceOut(p);
			case InOutBounce -> p < 0.5f ? (1f - bounceOut(1f - 2f * p)) / 2f : (1f + bounceOut(2f * p - 1f)) / 2f;
		};
	}

	private static float sin(float v) {
		return (float) Math.sin(v);
	}

	private static float cos(float v) {
		return (float) Math.cos(v);
	}

	private static float pow(float a, float b) {
		return (float) Math.pow(a, b);
	}

	private static float bounceOut(float t) {
		float n1 = 7.5625f;
		float d1 = 2.75f;
		if (t < 1f / d1) {
			return n1 * t * t;
		}
		if (t < 2f / d1) {
			t -= 1.5f / d1;
			return n1 * t * t + 0.75f;
		}
		if (t < 2.5f / d1) {
			t -= 2.25f / d1;
			return n1 * t * t + 0.9375f;
		}
		t -= 2.625f / d1;
		return n1 * t * t + 0.984375f;
	}
}
