package dev.customhud.js;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagManager;
import dev.customhud.tag.core.TagType;
import org.mozilla.javascript.BaseFunction;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.RhinoException;
import org.mozilla.javascript.Script;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The JavaScript runtime (Rhino), playing the role of Overlayer's V8Manager:
 * evaluates Fx expressions and {@code {JSExpr:...}} with a 500 ms time limit
 * (an expression that times out is skipped for 5 s), exposes every tag as
 * {@code Tag.Name(...)} and as a global {@code Name(...)}, and hosts
 * {@code Store}, {@code Prefs}, {@code console}/{@code Log} and {@code TagType}.
 */
public final class JsEngine {
	public static final long TIMEOUT_MS = 500;
	public static final long BACKOFF_MS = 5000;
	private static final int CACHE_LIMIT = 1024;

	private static final Logger LOG = LoggerFactory.getLogger("customhud/js");
	private static JsEngine instance;

	private final Factory factory = new Factory();
	private ScriptableObject global;
	private final FxStore store = new FxStore();
	private PrefsStore prefs;
	private final ScriptLoader loader;
	private int globalsVersion = -1;

	private record Compiled(Script script, String error) {
	}

	private final Map<String, Compiled> compiled = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Compiled> eldest) {
			return size() > CACHE_LIMIT;
		}
	};
	private final Map<String, Long> backoff = new LinkedHashMap<>();
	private final Map<String, String> runtimeErrors = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
			return size() > CACHE_LIMIT;
		}
	};

	private JsEngine(Path scriptFolder) {
		this.loader = new ScriptLoader(this, scriptFolder);
		this.prefs = new PrefsStore(scriptFolder == null ? null : scriptFolder.resolve("prefs.json"));
		reset();
	}

	/** Creates the engine; {@code scriptFolder} may be null (tests: no scripts, no prefs file). */
	public static synchronized JsEngine init(Path scriptFolder) {
		instance = new JsEngine(scriptFolder);
		return instance;
	}

	public static JsEngine get() {
		if (instance == null) {
			init(null);
		}
		return instance;
	}

	public ScriptLoader loader() {
		return loader;
	}

	public FxStore store() {
		return store;
	}

	// ---- context ----

	/** Interpreted mode so the instruction observer can enforce the time limit. */
	private static final class Factory extends ContextFactory {
		@Override
		protected Context makeContext() {
			Context cx = super.makeContext();
			cx.setLanguageVersion(Context.VERSION_ECMASCRIPT);
			cx.setInterpretedMode(true);
			cx.setInstructionObserverThreshold(10_000);
			return cx;
		}

		@Override
		protected void observeInstructionCount(Context cx, int instructionCount) {
			Object deadline = cx.getThreadLocal("deadline");
			if (deadline instanceof Long d && System.nanoTime() > d) {
				throw new TimeoutError();
			}
		}
	}

	private static final class TimeoutError extends Error {
		TimeoutError() {
			super("Script timed out (" + TIMEOUT_MS + " ms)", null, false, false);
		}
	}

	private <T> T withContext(java.util.function.Function<Context, T> action) {
		Context cx = factory.enterContext();
		try {
			return action.apply(cx);
		} finally {
			Context.exit();
		}
	}

	/** Rebuilds the global scope (used by reload). Scripts must be loaded again afterwards. */
	public synchronized void reset() {
		withContext(cx -> {
			global = cx.initSafeStandardObjects();
			ScriptableObject.putProperty(global, "Tag", new TagProxy(global));
			ScriptableObject.putProperty(global, "Store", JsHost.object(global)
					.fn("Get", a -> store.get(str(a, 0), a.length > 1 ? a[1] : null))
					.fn("Set", a -> store.set(str(a, 0), a.length > 1 ? a[1] : null))
					.fn("Has", a -> store.has(str(a, 0)))
					.fn("Remove", a -> store.remove(str(a, 0)))
					.fn("Clear", a -> {
						store.clear();
						return null;
					})
					.fn("Keys", a -> store.keys())
					.getter("Count", store::count)
					.build());
			ScriptableObject.putProperty(global, "Prefs", JsHost.object(global)
					.fn("Get", a -> prefs.get(str(a, 0), a.length > 1 ? a[1] : null))
					.fn("Set", a -> prefs.set(str(a, 0), a.length > 1 ? a[1] : null))
					.fn("Remove", a -> prefs.remove(str(a, 0)))
					.build());
			Scriptable log = JsHost.object(global)
					.fn("Msg", a -> log(LOG::info, a)).fn("Log", a -> log(LOG::info, a)).fn("log", a -> log(LOG::info, a))
					.fn("info", a -> log(LOG::info, a))
					.fn("Wrn", a -> log(LOG::warn, a)).fn("Warn", a -> log(LOG::warn, a)).fn("warn", a -> log(LOG::warn, a))
					.fn("Err", a -> log(LOG::error, a)).fn("Error", a -> log(LOG::error, a)).fn("error", a -> log(LOG::error, a))
					.build();
			ScriptableObject.putProperty(global, "Log", log);
			ScriptableObject.putProperty(global, "console", log);
			cx.evaluateString(global, TagType.SCRIPT, "TagType", 1, null);
			// RegisterTag only does something while a script file is loading.
			ScriptableObject.putProperty(global, "RegisterTag", JsHost.function(global, "RegisterTag", a -> null));
			return null;
		});
		synchronized (compiled) {
			compiled.clear();
			runtimeErrors.clear();
			backoff.clear();
		}
		globalsVersion = -1;
	}

	private static String str(Object[] a, int i) {
		return i < a.length && a[i] != null ? a[i].toString() : null;
	}

	private static Object log(Consumer<String> sink, Object[] args) {
		StringBuilder sb = new StringBuilder();
		for (Object o : args) {
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(dev.customhud.tag.compile.DotNet.toString(o));
		}
		sink.accept("[JS] " + sb);
		return null;
	}

	ScriptableObject global() {
		return global;
	}

	/** Defines (or refreshes) one global function per tag, like Overlayer's generated impl.js. */
	private void refreshGlobals() {
		int v = TagManager.version();
		if (v == globalsVersion) {
			return;
		}
		globalsVersion = v;
		for (TagCore tag : TagManager.all()) {
			if (tag.kind() == TagCore.Kind.ADVANCED) {
				continue;
			}
			ScriptableObject.putProperty(global, tag.name(), tagFunction(tag.name()));
		}
	}

	BaseFunction tagFunction(String name) {
		return JsHost.function(global, name, args -> {
			TagCore tag = TagManager.get(name);
			if (tag == null) {
				return null;
			}
			try {
				return tag.invoke(args);
			} catch (RuntimeException e) {
				LOG.warn("[TagAccessHelper] Error invoking {}: {} - {}", name, e.getClass().getSimpleName(), e.getMessage());
				return null;
			}
		});
	}

	// ---- evaluation ----

	private Compiled compile(String code) {
		synchronized (compiled) {
			Compiled c = compiled.get(code);
			if (c != null) {
				return c;
			}
		}
		Compiled c = withContext(cx -> {
			try {
				return new Compiled(cx.compileString(code, "fx", 1, null), null);
			} catch (RhinoException e) {
				return new Compiled(null, e.details());
			}
		});
		synchronized (compiled) {
			compiled.put(code, c);
		}
		return c;
	}

	/** Syntax error of {@code code}, or null. */
	public String compileError(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		return compile(code).error();
	}

	/** Last runtime error of {@code code}, or null. */
	public String runtimeError(String code) {
		synchronized (compiled) {
			return runtimeErrors.get(code);
		}
	}

	/**
	 * Overlayer's TryEvaluateFx: runs {@code code} (usually a wrapped block) and returns the
	 * Java-converted result, or null when it doesn't compile, throws, times out or yields undefined.
	 */
	public synchronized Object evaluate(String code) {
		if (code == null || code.isBlank()) {
			return null;
		}
		Long until = backoff.get(code);
		if (until != null) {
			if (System.currentTimeMillis() < until) {
				return null;
			}
			backoff.remove(code);
		}
		Compiled c = compile(code);
		if (c.script() == null) {
			return null;
		}
		refreshGlobals();
		return withContext(cx -> {
			cx.putThreadLocal("deadline", System.nanoTime() + TIMEOUT_MS * 1_000_000L);
			try {
				Object result = c.script().exec(cx, global, global);
				synchronized (compiled) {
					runtimeErrors.remove(code);
				}
				return JsValues.toJava(result);
			} catch (TimeoutError e) {
				backoff.put(code, System.currentTimeMillis() + BACKOFF_MS);
				recordError(code, e.getMessage());
				return null;
			} catch (RhinoException e) {
				recordError(code, e.details());
				return null;
			} catch (RuntimeException e) {
				recordError(code, e.toString());
				return null;
			} finally {
				cx.removeThreadLocal("deadline");
			}
		});
	}

	private void recordError(String code, String message) {
		synchronized (compiled) {
			runtimeErrors.put(code, message);
		}
	}

	/** Calls a JS function registered with RegisterTag. */
	public synchronized Object call(Function fn, Object[] args) {
		refreshGlobals();
		return withContext(cx -> {
			cx.putThreadLocal("deadline", System.nanoTime() + TIMEOUT_MS * 1_000_000L);
			try {
				Object[] jsArgs = new Object[args.length];
				for (int i = 0; i < args.length; i++) {
					jsArgs[i] = JsValues.toJs(args[i], global);
				}
				return JsValues.toJava(fn.call(cx, global, global, jsArgs));
			} catch (TimeoutError | RhinoException e) {
				LOG.warn("[JS] {}", e.getMessage());
				return null;
			} finally {
				cx.removeThreadLocal("deadline");
			}
		});
	}

	/** Runs a whole script file in the global scope (used by the loader). */
	synchronized void execute(String source, String name) {
		refreshGlobals();
		withContext(cx -> {
			cx.putThreadLocal("deadline", System.nanoTime() + 5_000L * 1_000_000L);
			try {
				cx.evaluateString(global, source, name, 1, null);
				return null;
			} finally {
				cx.removeThreadLocal("deadline");
			}
		});
	}

	/** Wraps code the way Overlayer does before evaluating Fx / JSExpr: {@code "{\n" + code + "\n}"}. */
	public static String wrapBlock(String code) {
		return "{\n" + (code == null ? "" : code) + "\n}";
	}

	/** Removes zero-width characters that sneak in when copying code. */
	public static String sanitize(String code) {
		if (code == null) {
			return null;
		}
		return code.replace("​", "").replace("‌", "").replace("‍", "").replace("﻿", "");
	}
}
