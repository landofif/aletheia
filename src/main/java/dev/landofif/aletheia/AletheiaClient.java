package dev.landofif.aletheia;

import dev.landofif.aletheia.afterburner.Afterburner;
import dev.landofif.aletheia.boss.BossBar;
import dev.landofif.aletheia.boss.BossPhaseTracker;
import dev.landofif.aletheia.boss.BossReminders;
import dev.landofif.aletheia.boss.Vulnerability;
import dev.landofif.aletheia.chat.ChatRewrite;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.dreadwood.DreadwoodRun;
import dev.landofif.aletheia.gift.NaturesGift;
import dev.landofif.aletheia.hud.AletheiaHuds;
import dev.landofif.aletheia.pots.HpPots;
import dev.landofif.aletheia.serverhud.HudGlyphs;
import dev.landofif.aletheia.stats.PlayerStats;
import dev.landofif.aletheia.timer.DungeonTimer;
import dev.landofif.aletheia.world.Domains;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class AletheiaClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Before anything reads a setting: the saved file replaces the defaults on the fields.
		AletheiaConfig.load();

		ChatWatcher.register();
		ChatRewrite.register();
		AletheiaCommands.register();
		AletheiaKeys.register();

		AletheiaHuds.register();
		NaturesGift.register();
		Afterburner.register();
		PlayerStats.register();
		HpPots.register();
		BossPhaseTracker.register();
		BossReminders.register();
		Vulnerability.register();
		BossBar.register();
		DreadwoodRun.register();
		DungeonTimer.register();
		Domains.register();
		forgetGlyphsOnReload();

		Aletheia.LOGGER.info("{} ready -- watching Neo-Eden chat.", Aletheia.MOD_NAME);
	}

	/**
	 * Font definitions are read straight from the server's resource pack, so the names they supply
	 * stop being true the moment a different pack is loaded.
	 */
	private static void forgetGlyphsOnReload() {
		PreparableReloadListener listener = new SimpleReloadListener<Void>() {
			@Override
			protected Void prepare(SharedState state) {
				return null;
			}

			@Override
			protected void apply(Void prepared, SharedState state) {
				HudGlyphs.forget();
			}
		};

		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(
				Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "hud_glyphs"), listener);
	}
}
