package dev.customhud.mixin;

import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.MultilineTextField;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets the tag browser insert text at the cursor of the template editor. */
@Mixin(MultiLineEditBox.class)
public interface MultiLineEditBoxAccessor {
	@Accessor("textField")
	MultilineTextField customhud$textField();
}
