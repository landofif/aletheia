package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.actionbar.ActionBar;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single place all action bar text passes through.
 *
 * <p>Both routes end up here: {@code ChatListener.handleOverlay} for system chat with the overlay
 * flag, and {@code ClientPacketListener.setActionBarText} for the action bar title packet. The
 * latter never touches {@code ChatListener}, so hooking this instead of Fabric's message events is
 * what catches everything that is genuinely above the hotbar -- which the Telos readouts are not.
 */
@Mixin(Gui.class)
public class GuiOverlayMessageMixin {

	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void aletheia$filterOverlayMessage(Component message, boolean animated, CallbackInfo info) {
		if (ActionBar.handle(message)) {
			info.cancel();
		}
	}
}
