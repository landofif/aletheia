package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.config.AletheiaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Draws titles flat instead of with vanilla's drop shadow.
 *
 * <p>{@code Gui.extractTitle} puts both the title and the subtitle up with
 * {@code textWithBackdrop}, which is a background fill -- only drawn when the game's <i>Text
 * Background Opacity</i> is turned up, so normally nothing -- followed by the ordinary text call with
 * its shadow flag hardcoded to {@code true}. There is no seam between the two, hence redirecting the
 * whole call rather than flipping an argument.
 *
 * <p>Both call sites in the method are redirected, so the subtitle matches the title. This is the
 * vanilla title system, so it applies to any title on screen, the server's included -- which is the
 * point: they are what looked wrong.
 */
@Mixin(Gui.class)
public class GuiTitleShadowMixin {

	@Redirect(
			method = "extractTitle",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop("
							+ "Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
	private void aletheia$titleWithoutShadow(
			GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int colour) {
		if (AletheiaConfig.titleShadow) {
			graphics.textWithBackdrop(font, text, x, y, width, colour);
			return;
		}

		// Same backdrop vanilla would have drawn, so turning the shadow off does not also take away a
		// box the game's own Text Background Opacity asked for. It is transparent unless that is set.
		int backdrop = Minecraft.getInstance().options.getBackgroundColor(0.0F);
		if (backdrop != 0) {
			graphics.fill(x - 2, y - 2, x + width + 2, y + 9 + 2, ARGB.multiply(backdrop, colour));
		}
		graphics.text(font, text, x, y, colour, false);
	}
}
