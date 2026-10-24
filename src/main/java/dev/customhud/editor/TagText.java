package dev.customhud.editor;

import dev.customhud.tag.compile.DotNet;
import dev.customhud.tag.compile.FormatValidator;
import dev.customhud.tag.core.TagCore;
import dev.customhud.tag.core.TagParam;
import dev.customhud.tag.core.TagType;
import net.minecraft.locale.Language;

import java.util.List;
import java.util.Locale;

/** How tags are shown and inserted by the editor. */
final class TagText {
	private TagText() {
	}

	/** {@code {Fps}} or {@code {ColorRange:tagName,minimum,maximum,minimumHex,maximumHex}} for text. */
	static String forText(TagCore tag) {
		List<String> required = tag.params().stream().filter(p -> !p.hasDefault()).map(TagParam::name).toList();
		if (tag.kind() == TagCore.Kind.ADVANCED) {
			return "{" + tag.name() + ":}";
		}
		return required.isEmpty() ? "{" + tag.name() + "}" : "{" + tag.name() + ":" + String.join(",", required) + "}";
	}

	/** {@code Fps()} or {@code ColorRange(tagName, minimum, ...)} for JavaScript / Fx. */
	static String forJs(TagCore tag) {
		List<String> required = tag.params().stream().filter(p -> !p.hasDefault()).map(TagParam::name).toList();
		return tag.name() + "(" + String.join(", ", required) + ")";
	}

	/** {@code Fps(windowMs = 0) : double  [format]} */
	static String signature(TagCore tag) {
		StringBuilder sb = new StringBuilder(tag.name());
		if (!tag.params().isEmpty()) {
			sb.append('(');
			for (int i = 0; i < tag.params().size(); i++) {
				TagParam p = tag.params().get(i);
				if (i > 0) {
					sb.append(", ");
				}
				sb.append(p.name());
				if (p.hasDefault() && p.defaultValue() != null) {
					sb.append(" = ").append(DotNet.toString(p.defaultValue()));
				} else if (p.hasDefault()) {
					sb.append('?');
				}
			}
			sb.append(')');
		}
		if (tag.kind() != TagCore.Kind.ADVANCED) {
			sb.append(" : ").append(FormatValidator.typeName(tag.returnType()));
		}
		if (TagType.has(tag.tagType(), TagType.PROCESS_FORMAT) || (tag.params().isEmpty() && DotNet.isFormattable(tag.returnType()))) {
			sb.append("  [format]");
		}
		return sb.toString();
	}

	/** Localised description (lang file) or the tag's own English one. */
	static String description(TagCore tag) {
		String key = "tag.customhud." + tag.name().toLowerCase(Locale.ROOT);
		Language lang = Language.getInstance();
		if (lang.has(key)) {
			return lang.getOrDefault(key);
		}
		return tag.description() == null ? "" : tag.description();
	}
}
