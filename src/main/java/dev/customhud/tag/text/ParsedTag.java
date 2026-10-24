package dev.customhud.tag.text;

import java.util.List;

/** One {@code {...}} occurrence in a text. */
public record ParsedTag(String raw, String name, List<String> args, int index, int length) {
}
