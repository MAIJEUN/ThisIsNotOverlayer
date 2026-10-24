package dev.customhud.tag;

import dev.customhud.fx.Fx;
import dev.customhud.fx.NCalc;
import dev.customhud.js.JsEngine;
import dev.customhud.tag.compile.Diagnostic;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import dev.customhud.tag.text.TextEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Text engine end-to-end with Java tags, JSExpr, RegisterTag scripts, Fx and NCalc. */
class EngineTest {
	static double fps = 143.75;

	@BeforeEach
	void setUp() {
		TagManager.clear();
		TagManager.register(TagCore.java("Fps", null, TagType.PROCESS_FORMAT, List.of(TagParam.optional("windowMs", Integer.class, 0)),
				Double.class, a -> fps));
		TagManager.register(TagCore.java("Name", null, 0, List.of(), String.class, a -> "Steve"));
		TagManager.register(TagCore.java("IsFlying", null, 0, List.of(), Boolean.class, a -> true));
		TagManager.register(TagCore.java("Add", null, 0, List.of(TagParam.required("a", Integer.class), TagParam.required("b", Integer.class)),
				Integer.class, a -> (int) a[0] + (int) a[1]));
		TagManager.register(dev.customhud.tag.impl.JsExprTag.create());
		JsEngine.init(null);
	}

	private static String render(String text) {
		return new TextEngine(text).get();
	}

	@Test
	void plainTags() {
		assertEquals("FPS: 143.75", render("FPS: {Fps}"));
		assertEquals("FPS: 144", render("FPS: {Fps:0}"));
		assertEquals("FPS: 143.8", render("FPS: {Fps:500,0.0}"));
		assertEquals("Hi Steve", render("Hi {Name}"));
		assertEquals("True", render("{IsFlying}"));
		assertEquals("5", render("{Add:2,3}"));
		assertEquals("5", render("{Add(2, 3)}"));
	}

	@Test
	void caseSensitiveAndRawOnError() {
		assertEquals("{fps} {Nope} {Add:x,1} {Name:N0}", render("{fps} {Nope} {Add:x,1} {Name:N0}"));
		TextEngine e = new TextEngine("{Nope} {Add:1}");
		List<Diagnostic> d = e.diagnostics();
		assertEquals(Diagnostic.Id.TAG_NOT_FOUND, d.get(0).id());
		assertEquals(Diagnostic.Id.ARG_TOO_FEW, d.get(1).id());
	}

	@Test
	void tooManyArgsIsOnlyAWarning() {
		TextEngine e = new TextEngine("{Add:1,2,3,4}");
		assertEquals("3", e.get());
		assertEquals(Diagnostic.Severity.WARNING, e.diagnostics().getFirst().severity());
	}

	@Test
	void jsExpr() {
		assertEquals("2", render("{JSExpr:1+1}"));
		assertEquals("0.30000000000000004", render("{JSExpr:0.1+0.2}"));
		assertEquals("True", render("{JSExpr:Fps() > 60}"));
		assertEquals("good", render("{JSExpr:Fps() > 60 ? 'good' : 'low'}"));
		assertEquals("Steve!", render("{JSExpr:Tag.Name() + '!'}"));
		assertEquals("6", render("{JSExpr:{ let o = {a: 2}; Add(o.a, 4) }}"));
		assertEquals("a b", render("{JSExpr(`a ${'b'}`)}"));
		assertEquals("", render("{JSExpr:undefined}"));
		TextEngine broken = new TextEngine("{JSExpr:1 +}");
		assertEquals("{JSExpr:1 +}", broken.get());
		assertFalse(broken.diagnostics().isEmpty());
	}

	@Test
	void jsStoreKeepsValuesBetweenEvaluations() {
		JsEngine.get().evaluate("Store.Set('n', (Store.Get('n', 0)) + 1)");
		JsEngine.get().evaluate("Store.Set('n', (Store.Get('n', 0)) + 1)");
		assertEquals(2, JsEngine.get().evaluate("Store.Get('n')"));
	}

	@Test
	void runawayScriptTimesOut() {
		long start = System.nanoTime();
		assertNull(JsEngine.get().evaluate("{\nwhile(true){}\n}"));
		assertTrue((System.nanoTime() - start) / 1e6 < 3000);
		assertNotNull(JsEngine.get().runtimeError("{\nwhile(true){}\n}"));
	}

	@Test
	void registerTagScripts(@TempDir Path dir) throws Exception {
		Files.writeString(dir.resolve("my.js"), """
				import "./impl";
				RegisterTag("Double", (x) => x * 2, { Type: TagType.ProcessFormat, ReturnType: "number" });
				RegisterTag("Greet", function(name, punct = "!") { return "Hi " + name + punct; });
				RegisterTag("Counter", () => { Store.Set("c", Store.Get("c", 0) + 1); return Store.Get("c"); });
				""");
		Files.writeString(dir.resolve("broken.js"), "RegisterTag('Bad', ;");
		JsEngine.init(dir).loader().reloadAll();
		assertEquals("10", render("{Double:5}"));
		assertEquals("10.00", render("{Double:5,F2}"));
		assertEquals("Hi Alex!", render("{Greet:Alex}"));
		assertEquals("Hi Alex?", render("{Greet:Alex,?}"));
		assertEquals("12", render("{JSExpr:Double(6)}"));
		assertEquals(1, JsEngine.get().loader().diagnostics().size());
		assertTrue(Files.exists(dir.resolve("impl.js")));
		assertTrue(Files.readString(dir.resolve("impl.d.ts")).contains("declare function Greet("));
		TagCore greet = TagManager.get("Greet");
		assertEquals(1, greet.requiredCount());
	}

	@Test
	void fxNumbersJsThenNCalc() {
		assertEquals(287.5, Fx.number("Fps() * 2").value(), 1e-9);
		assertEquals(3.0, Fx.number("if(1 > 0, 3, 4)").value(), 1e-9, "not JS → NCalc");
		assertEquals(Math.PI, Fx.number("PI").value(), 1e-9);
		assertEquals(1.0, Fx.number("Abs(-1)").value(), 1e-9);
		assertNotNull(Fx.number("Nope(").error());
		assertEquals(true, Fx.bool("IsFlying() && Fps() > 60").value());
		assertEquals(true, Fx.bool("1 = 1 and not false").value());
	}

	@Test
	void fxStringPrefersJsThenTemplate() {
		TextEngine engine = new TextEngine();
		assertEquals("x2", Fx.string("'x' + 2", engine));
		assertEquals("FPS 143.75", Fx.string("FPS {Fps}", engine));
	}

	@Test
	void fxComponents() {
		float[] c = Fx.components("[1, 0.5, 0, 1]", 3).value();
		assertEquals(0.5f, c[1], 1e-6);
		float[] js = Fx.components("[1, Fps() > 60 ? 1 : 0, 0]", 3).value();
		assertEquals(1f, js[1], 1e-6);
		assertNotNull(Fx.components("[1, 2]", 3).error());
	}

	@Test
	void ncalc() {
		assertEquals(7.0, NCalc.evaluate("1 + 2 * 3"));
		assertEquals(true, NCalc.evaluate("2 <> 3"));
		assertEquals(8.0, NCalc.evaluate("2 ** 3"));
		assertEquals(2.0, NCalc.evaluate("Round(2.5)"), "banker's rounding");
		assertEquals("ab", NCalc.evaluate("'a' + 'b'"));
		assertEquals(2.0, NCalc.evaluate("ifs(false, 1, true, 2, 3)"));
		assertEquals(true, NCalc.evaluate("in(2, 1, 2, 3)"));
	}
}
