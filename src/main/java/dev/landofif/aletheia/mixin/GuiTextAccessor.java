package dev.landofif.aletheia.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The text the Gui is currently holding. There are setters for all three but no getters, and what a
 * scan needs is what is on screen <i>now</i>, not what happened to arrive while it was watching.
 */
@Mixin(Gui.class)
public interface GuiTextAccessor {

	@Accessor("overlayMessageString")
	Component aletheia$overlayMessage();

	@Accessor("overlayMessageTime")
	int aletheia$overlayMessageTime();

	@Accessor("title")
	Component aletheia$title();

	@Accessor("subtitle")
	Component aletheia$subtitle();
}
