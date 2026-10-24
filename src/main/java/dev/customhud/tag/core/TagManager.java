package dev.customhud.tag.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Name → tag (case-sensitive, like Overlayer). Every change bumps {@link #version()},
 * which makes text engines recompile on their next use.
 */
public final class TagManager {
	private static volatile Map<String, TagCore> tags = new LinkedHashMap<>();
	private static final AtomicInteger VERSION = new AtomicInteger();

	private TagManager() {
	}

	public static synchronized void register(TagCore tag) {
		Map<String, TagCore> copy = new LinkedHashMap<>(tags);
		copy.put(tag.name(), tag);
		tags = copy;
		VERSION.incrementAndGet();
	}

	public static synchronized void registerAll(Collection<TagCore> list) {
		Map<String, TagCore> copy = new LinkedHashMap<>(tags);
		for (TagCore t : list) {
			copy.put(t.name(), t);
		}
		tags = copy;
		VERSION.incrementAndGet();
	}

	public static synchronized boolean unregister(Collection<String> names) {
		Map<String, TagCore> copy = new LinkedHashMap<>(tags);
		boolean changed = false;
		for (String n : names) {
			changed |= copy.remove(n) != null;
		}
		if (changed) {
			tags = copy;
			VERSION.incrementAndGet();
		}
		return changed;
	}

	public static TagCore get(String name) {
		return tags.get(name);
	}

	public static boolean has(String name) {
		return tags.containsKey(name);
	}

	public static List<TagCore> all() {
		return new ArrayList<>(tags.values());
	}

	public static int version() {
		return VERSION.get();
	}

	/** For tests. */
	public static synchronized void clear() {
		tags = new LinkedHashMap<>();
		VERSION.incrementAndGet();
	}
}
