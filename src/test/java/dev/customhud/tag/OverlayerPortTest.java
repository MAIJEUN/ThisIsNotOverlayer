package dev.customhud.tag;

import dev.customhud.tag.compile.ArgConverter;
import dev.customhud.tag.compile.Compiler;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.compile.FormatValidator;
import dev.customhud.tag.compile.SignatureResolver;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import dev.customhud.tag.text.ParsedTag;
import dev.customhud.tag.text.Parser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Overlayer.Tests ported 1:1 (ParserTests, CompilerTests, SignatureResolverTests, FormatValidatorTests, ArgConverterTests, TagInvokeTests). */
class OverlayerPortTest {
	enum DummyEase { Linear, OutExpo }

	enum Judgement { Perfect, Good, Miss }

	static TagCore prop(String name, Class<?> type, Object value, int tagType) {
		return TagCore.java(name, null, tagType, List.of(), type, a -> value);
	}

	static TagCore method(String name, int tagType, Class<?> ret, TagCore.Invoker inv, TagParam... params) {
		return TagCore.java(name, null, tagType, List.of(params), ret, inv);
	}

	static TagCore js(String name, int tagType, String... params) {
		List<TagParam> ps = new ArrayList<>();
		for (String p : params) {
			ps.add(TagParam.required(p, Object.class));
		}
		return TagCore.js(name, null, tagType, ps, Object.class, a -> null);
	}

	// Stream(double a, double b), Meter(int windowMs = 0), Margin(string), Smooth(string tag, int digits = -1, int speed = 500, DummyEase ease)
	static final TagCore STREAM = method("Stream", TagType.NONE, Double.class, a -> (double) a[0] + (double) a[1],
			TagParam.required("a", Double.class), TagParam.required("b", Double.class));
	static final TagCore STREAM_FMT = method("Stream", TagType.PROCESS_FORMAT, Double.class, a -> (double) a[0] + (double) a[1],
			TagParam.required("a", Double.class), TagParam.required("b", Double.class));
	static final TagCore METER = method("Meter", TagType.PROCESS_FORMAT, Double.class, a -> (double) (int) a[0],
			TagParam.optional("windowMs", Integer.class, 0));
	static final TagCore MARGIN = method("Margin", TagType.PROCESS_FORMAT, Integer.class, a -> ((String) a[0]).length(),
			TagParam.required("margin", String.class));
	static final TagCore SMOOTH = method("Smooth", TagType.PROCESS_FORMAT, Double.class, a -> (double) (int) a[2],
			TagParam.required("tag", String.class), TagParam.optional("digits", Integer.class, -1),
			TagParam.optional("speed", Integer.class, 500), TagParam.optional("ease", DummyEase.class, DummyEase.Linear));

	static SignatureResolver.Resolved resolve(TagCore tag, List<String> args, List<Diagnostic> diag) {
		return SignatureResolver.resolve(tag, new ParsedTag("{" + tag.name() + "}", tag.name(), args, 0, 8), diag);
	}

	@Nested
	class ParserTests {
		@Test
		void plainText() {
			assertTrue(Parser.parse("dropped the beat and died").isEmpty());
		}

		@Test
		void simpleTag() {
			ParsedTag t = Parser.parse("{Bpm}").getFirst();
			assertEquals("Bpm", t.name());
			assertTrue(t.args().isEmpty());
		}

		@Test
		void colonTagSplitsArgs() {
			ParsedTag t = Parser.parse("{Stream:200, 174}").getFirst();
			assertEquals("Stream", t.name());
			assertEquals(List.of("200", "174"), t.args());
		}

		@Test
		void funcForm() {
			ParsedTag t = Parser.parse("{Stream(200,174)}").getFirst();
			assertEquals(List.of("200", "174"), t.args());
		}

		@Test
		void jsExprKeepsBraces() {
			List<ParsedTag> tags = Parser.parse("{JSExpr:{gauge: 100}}");
			assertEquals(1, tags.size());
			assertEquals("JSExpr", tags.getFirst().name());
			assertEquals("{JSExpr:{gauge: 100}}", tags.getFirst().raw());
		}

		@Test
		void multipleInOrder() {
			List<ParsedTag> tags = Parser.parse("{FullCombo} nice {Miss:fail}");
			assertEquals(2, tags.size());
			assertEquals("FullCombo", tags.get(0).name());
			assertEquals("Miss", tags.get(1).name());
			assertEquals(List.of("fail"), tags.get(1).args());
		}

		@Test
		void colonArgumentKeepsColons() {
			assertEquals(List.of("HH:mm"), Parser.parse("{Time:HH:mm}").getFirst().args());
		}
	}

	@Nested
	class CompilerTests {
		@Test
		void numericWithFormat() {
			Compiler.Compiled c = Compiler.compile(prop("FullCombo", Double.class, 1557.0, 0), new ParsedTag("{FullCombo:F1}", "FullCombo", List.of("F1"), 0, 14));
			assertTrue(c.valid());
			assertEquals("1557.0", c.get());
		}

		@Test
		void numericWithoutFormat() {
			Compiler.Compiled c = Compiler.compile(prop("FullCombo", Double.class, 1557.0, 0), new ParsedTag("{FullCombo}", "FullCombo", List.of(), 0, 11));
			assertTrue(c.valid());
			assertEquals("1557", c.get());
		}

		@Test
		void blockedTagRendersRawWhenNotPlaying() {
			var old = Compiler.isPlaying;
			Compiler.isPlaying = () -> false;
			try {
				Compiler.Compiled c = Compiler.compile(prop("FullCombo", Double.class, 1557.0, TagType.BLOCK_ON_NOT_PLAYING),
						new ParsedTag("{FullCombo:F1}", "FullCombo", List.of("F1"), 0, 14));
				assertTrue(c.valid());
				assertEquals("{FullCombo:F1}", c.get());
			} finally {
				Compiler.isPlaying = old;
			}
		}

		@Test
		void stringWithFormatIsInvalid() {
			Compiler.Compiled c = Compiler.compile(prop("Miss", String.class, "FullCombo", 0), new ParsedTag("{Miss:N0}", "Miss", List.of("N0"), 0, 9));
			assertFalse(c.valid());
			assertEquals("{Miss:N0}", c.get());
		}
	}

	@Nested
	class SignatureResolverTests {
		@Test
		void zeroParamNumericFormatIsValid() {
			List<Diagnostic> d = new ArrayList<>();
			var sig = resolve(prop("FullCombo", Double.class, 1.0, 0), List.of("N0"), d);
			assertTrue(sig.executable());
			assertEquals("N0", sig.format());
			assertTrue(d.isEmpty());
		}

		@Test
		void zeroParamStringFormatFails() {
			List<Diagnostic> d = new ArrayList<>();
			var sig = resolve(prop("Miss", String.class, "x", 0), List.of("N0"), d);
			assertFalse(sig.executable());
			assertTrue(d.stream().anyMatch(x -> x.id() == Diagnostic.Id.FORMAT_FAIL));
		}

		@Test
		void withFlagSplitsLastArgAsFormat() {
			var sig = resolve(STREAM_FMT, List.of("200", "174", "N2"), new ArrayList<>());
			assertTrue(sig.executable());
			assertEquals("N2", sig.format());
			assertEquals(List.of("200", "174"), sig.args());
		}

		@Test
		void withoutFlagKeepsAllArgs() {
			var sig = resolve(STREAM, List.of("200", "174"), new ArrayList<>());
			assertTrue(sig.executable());
			assertFalse(sig.hasFormat());
			assertEquals(2, sig.args().size());
		}

		@Test
		void jsZeroParamFormatNeedsNothingSpecial() {
			var sig = resolve(js("Offbeat", TagType.NONE), List.of("N2"), new ArrayList<>());
			assertTrue(sig.executable());
			// JS tags without ProcessFormat keep the argument as an (ignored) value, not a format.
			assertFalse(sig.hasFormat());
		}

		@Test
		void jsWithFlagSplitsFormat() {
			var sig = resolve(js("Knob", TagType.PROCESS_FORMAT, "left", "right"), List.of("200", "174", "N2"), new ArrayList<>());
			assertEquals("N2", sig.format());
			assertEquals(List.of("200", "174"), sig.args());
		}

		@Test
		void jsWithoutFlagKeepsValues() {
			var sig = resolve(js("Knob", TagType.NONE, "left", "right"), List.of("200", "174"), new ArrayList<>());
			assertTrue(sig.executable());
			assertFalse(sig.hasFormat());
		}

		@Test
		void jsOnlyBlocked() {
			List<Diagnostic> d = new ArrayList<>();
			var sig = resolve(prop("JsThing", Double.class, 1.0, TagType.JS_ONLY), List.of(), d);
			assertFalse(sig.executable());
			assertTrue(d.stream().anyMatch(x -> x.id() == Diagnostic.Id.JS_ONLY_BLOCKED));
		}

		@Test
		void missingRequiredArg() {
			List<Diagnostic> d = new ArrayList<>();
			assertFalse(resolve(STREAM_FMT, List.of(), d).executable());
			assertTrue(d.stream().anyMatch(x -> x.id() == Diagnostic.Id.ARG_TOO_FEW));
		}

		@Test
		void allArgsBindAsValues() {
			List<Diagnostic> d = new ArrayList<>();
			var sig = resolve(SMOOTH, List.of("Combo", "0", "500", "OutExpo"), d);
			assertFalse(sig.hasFormat());
			assertEquals(List.of("Combo", "0", "500", "OutExpo"), sig.args());
			assertTrue(d.isEmpty());
		}

		@Test
		void fullArgsPlusFormat() {
			var sig = resolve(SMOOTH, List.of("Combo", "0", "500", "OutExpo", "F1"), new ArrayList<>());
			assertEquals("F1", sig.format());
			assertEquals(List.of("Combo", "0", "500", "OutExpo"), sig.args());
		}

		@Test
		void partialArgsBindAsValues() {
			var sig = resolve(SMOOTH, List.of("Combo", "0"), new ArrayList<>());
			assertFalse(sig.hasFormat());
			assertEquals(List.of("Combo", "0"), sig.args());
		}

		@Test
		void loneArgOnOptionalParamsStaysFormat() {
			var sig = resolve(METER, List.of("0"), new ArrayList<>());
			assertEquals("0", sig.format());
			assertTrue(sig.args().isEmpty());
		}

		@Test
		void loneArgOnRequiredParamBindsAsValue() {
			var sig = resolve(MARGIN, List.of("Perfect"), new ArrayList<>());
			assertFalse(sig.hasFormat());
			assertEquals(List.of("Perfect"), sig.args());
		}

		@Test
		void badValueArgFallsBackToFormat() {
			var sig = resolve(SMOOTH, List.of("Combo", "xyz"), new ArrayList<>());
			assertTrue(sig.executable());
			assertEquals("xyz", sig.format());
			assertEquals(List.of("Combo"), sig.args());
		}
	}

	@Nested
	class FormatValidatorTests {
		@Test
		void numericTypesAcceptStandardFormats() {
			for (String f : List.of("N0", "F2", "P1")) {
				assertNull(FormatValidator.validate(Double.class, f));
				assertNull(FormatValidator.validate(Integer.class, f));
				assertNull(FormatValidator.validate(Long.class, f));
			}
		}

		@Test
		void dateAcceptsPattern() {
			assertNull(FormatValidator.validate(java.time.LocalDateTime.class, "yyyy-MM-dd"));
		}

		@Test
		void stringRejectsNumericFormat() {
			assertNotNull(FormatValidator.validate(String.class, "N0"));
		}

		@Test
		void emptyFormatAcceptsAny() {
			assertNull(FormatValidator.validate(String.class, ""));
			assertNull(FormatValidator.validate(Double.class, null));
			assertNull(FormatValidator.validateJs(null));
			assertNull(FormatValidator.validateJs(""));
		}

		@Test
		void jsAcceptsNumericAndDateFormats() {
			for (String f : List.of("N0", "F2", "D5", "yyyy-MM-dd")) {
				assertNull(FormatValidator.validateJs(f), f);
			}
		}

		@Test
		void unknownStandardSpecifierFails() {
			assertNotNull(FormatValidator.validate(Double.class, "K2"));
			assertNotNull(FormatValidator.validate(Double.class, "D5"));
		}
	}

	@Nested
	class ArgConverterTests {
		@Test
		void conversions() {
			assertEquals("FullCombo", ArgConverter.convert("FullCombo", String.class));
			assertEquals(200, ArgConverter.convert("200", Integer.class));
			assertEquals(1.5, ArgConverter.convert("1.5", Double.class));
			assertEquals(true, ArgConverter.convert("True", Boolean.class));
			assertEquals(Judgement.Miss, ArgConverter.convert("MiSs", Judgement.class));
			assertThrows(NumberFormatException.class, () -> ArgConverter.convert("FullCombo", Integer.class));
			assertThrows(NumberFormatException.class, () -> ArgConverter.convert("1.0", Integer.class));
			assertThrows(NumberFormatException.class, () -> ArgConverter.convert("1d", Double.class));
		}
	}

	@Nested
	class TagInvokeTests {
		final TagCore intOfInt = method("IntOfInt", 0, Integer.class, a -> (int) a[0] * 2, TagParam.required("x", Integer.class));
		final TagCore doubleOfDouble = method("D", 0, Double.class, a -> (double) a[0] * 2, TagParam.required("x", Double.class));
		final TagCore nameOfString = method("S", 0, String.class, a -> "hi " + a[0], TagParam.required("s", String.class));
		final TagCore optSecond = method("O", 0, Integer.class, a -> a[1], TagParam.required("a", String.class), TagParam.optional("b", Integer.class, 7));
		final TagCore enumEcho = method("E", 0, Judgement.class, a -> a[0], TagParam.required("d", Judgement.class));
		final TagCore boolEcho = method("B", 0, Boolean.class, a -> a[0], TagParam.required("b", Boolean.class));

		@Test
		void coercions() {
			assertEquals(1000, intOfInt.invoke(500d));
			assertEquals(84, intOfInt.invoke(42));
			assertEquals("hi A", nameOfString.invoke("A"));
			assertEquals(400, intOfInt.invoke("200"));
			assertEquals(6d, doubleOfDouble.invoke(3));
			assertEquals("hi 42", nameOfString.invoke(42));
			assertEquals(Judgement.Miss, enumEcho.invoke("miss"));
			assertEquals(Judgement.Good, enumEcho.invoke(1));
			assertEquals(true, boolEcho.invoke("true"));
			assertEquals(7, optSecond.invoke("x"));
			assertEquals(0, intOfInt.invoke());
			assertEquals(10, intOfInt.invoke(5, "junk", 1.5d));
			assertEquals(0, intOfInt.invoke((Object) null));
			assertEquals(true, boolEcho.invoke(1.5d));
			assertEquals(4, intOfInt.invoke(2.5d), "banker's rounding: 2.5 → 2, ×2 = 4");
			assertThrows(IllegalArgumentException.class, () -> intOfInt.invoke(new Object()));
		}
	}
}
