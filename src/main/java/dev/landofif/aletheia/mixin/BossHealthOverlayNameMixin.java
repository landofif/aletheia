package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.serverhud.ServerHudFilter;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Filters a boss bar's name on its way to the screen.
 *
 * <p>Done at render time rather than when the packet arrives, so nothing the server sent is thrown
 * away -- turning the setting off puts the hidden parts back on the next frame. The name is read
 * once per bar and used for both the width and the draw, so this single redirect covers both.
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayNameMixin {

	@Redirect(
			method = "extractRenderState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/components/LerpingBossEvent;getName()Lnet/minecraft/network/chat/Component;"))
	private Component aletheia$filterBossBarName(LerpingBossEvent event) {
		return ServerHudFilter.apply(event.getName());
	}
}
