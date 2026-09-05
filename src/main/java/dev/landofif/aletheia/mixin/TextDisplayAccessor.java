package dev.landofif.aletheia.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the text on a text display entity -- a floating line with no block or mob under it, which
 * is one of the ways a server can hang a health bar over a boss. The getter is private to the class
 * and only its own renderer calls it.
 */
@Mixin(Display.TextDisplay.class)
public interface TextDisplayAccessor {

	@Invoker("getText")
	Component aletheia$text();
}
