package dev.landofif.aletheia.hud;

import dev.landofif.aletheia.afterburner.AfterburnerHud;
import dev.landofif.aletheia.boss.BossBarHud;
import dev.landofif.aletheia.boss.BossPhaseHud;
import dev.landofif.aletheia.boss.VulnerabilityHud;
import dev.landofif.aletheia.gift.NaturesGiftHud;
import dev.landofif.aletheia.pots.HpPotsHud;
import dev.landofif.aletheia.stats.PlayerStatsHud;
import dev.landofif.aletheia.timer.DungeonSplitsHud;
import dev.landofif.aletheia.timer.DungeonTimerHud;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** Every movable readout the mod draws, and the one place they are hooked into the game's HUD. */
public final class AletheiaHuds {
	private AletheiaHuds() {
	}

	/** Editor draw and hit-test order; later entries sit on top. */
	public static final List<AletheiaHud> ALL = List.of(
			BossBarHud.INSTANCE, NaturesGiftHud.INSTANCE, AfterburnerHud.INSTANCE, HpPotsHud.INSTANCE,
			PlayerStatsHud.INSTANCE, DungeonTimerHud.INSTANCE, DungeonSplitsHud.INSTANCE,
			BossPhaseHud.INSTANCE, VulnerabilityHud.INSTANCE);

	public static void register() {
		for (AletheiaHud hud : ALL) {
			HudElementRegistry.addLast(hud.id(), (graphics, deltaTracker) -> draw(hud, graphics));
		}
	}

	private static void draw(AletheiaHud hud, GuiGraphicsExtractor graphics) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		// The editor draws these itself, from the same code, so they are not also drawn beneath it.
		if (HudEditorScreen.isOpen() || !hud.shownInGame()) {
			return;
		}

		String text = hud.text();
		if (text == null) {
			text = "";
		}
		// A bar with its labels turned off is still a bar; a readout with nothing to read is nothing.
		if (text.isBlank() && !hud.drawsWithoutText()) {
			return;
		}

		Font font = client.font;
		hud.draw(graphics, font, text, hud.colour(),
				hud.box(font, text, graphics.guiWidth(), graphics.guiHeight()));
	}
}
