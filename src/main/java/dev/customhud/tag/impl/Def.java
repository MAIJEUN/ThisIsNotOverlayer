package dev.customhud.tag.impl;

import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Small builder for Java tags: {@code Def.of("Fps", Double.class).format().opt("windowMs", Integer.class, 0).desc("...").fn(a -> ...)}. */
final class Def {
	private final String name;
	private final Class<?> returnType;
	private final List<TagParam> params = new ArrayList<>();
	private int type = TagType.NONE;
	private String desc;

	private Def(String name, Class<?> returnType) {
		this.name = name;
		this.returnType = returnType;
	}

	static Def of(String name, Class<?> returnType) {
		return new Def(name, returnType);
	}

	/** Zero-argument tag. */
	static TagCore value(String name, Class<?> returnType, String desc, Supplier<?> getter) {
		return of(name, returnType).desc(desc).fn(a -> getter.get());
	}

	Def format() {
		type |= TagType.PROCESS_FORMAT;
		return this;
	}

	Def flags(int flags) {
		type |= flags;
		return this;
	}

	Def param(String name, Class<?> type) {
		params.add(TagParam.required(name, type));
		return this;
	}

	Def opt(String name, Class<?> type, Object defaultValue) {
		params.add(TagParam.optional(name, type, defaultValue));
		return this;
	}

	Def desc(String desc) {
		this.desc = desc;
		return this;
	}

	TagCore fn(TagCore.Invoker invoker) {
		return TagCore.java(name, desc, type, params, returnType, invoker);
	}
}
