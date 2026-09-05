package dev.landofif.aletheia.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tab list header and footer, which a server can use as a place to park HUD text. */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {

	@Accessor("header")
	Component aletheia$header();

	@Accessor("footer")
	Component aletheia$footer();
}
