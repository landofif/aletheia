package dev.landofif.aletheia.ui;

import dev.landofif.aletheia.config.AletheiaConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/** Puts a title on screen and optionally plays a sound, using the vanilla title system. */
public final class Alerts {
	private Alerts() {
	}

	/** Indexed by {@link AletheiaConfig#alertSound}; keep in step with that dropdown's option list. */
	private static final Supplier<SoundEvent>[] SOUNDS = sounds();

	@SuppressWarnings("unchecked")
	private static Supplier<SoundEvent>[] sounds() {
		// Some of these are plain SoundEvent constants and some are registry Holders, hence suppliers.
		return new Supplier[] {
				(Supplier<SoundEvent>) () -> SoundEvents.NOTE_BLOCK_PLING.value(),
				(Supplier<SoundEvent>) () -> SoundEvents.NOTE_BLOCK_BELL.value(),
				(Supplier<SoundEvent>) () -> SoundEvents.PLAYER_LEVELUP,
				(Supplier<SoundEvent>) () -> SoundEvents.ANVIL_LAND,
				(Supplier<SoundEvent>) () -> SoundEvents.TOTEM_USE,
				(Supplier<SoundEvent>) () -> SoundEvents.ENDER_DRAGON_GROWL,
		};
	}

	/**
	 * Shows a title, replacing whatever is currently on screen.
	 *
	 * @param subtitle may be {@code null} or blank for no subtitle
	 * @param stayTicks how long to hold, before fades
	 */
	public static void showTitle(String title, @Nullable String subtitle, ChatFormatting colour, int stayTicks) {
		Minecraft client = Minecraft.getInstance();

		client.execute(() -> {
			Gui gui = client.gui;

			// Order matters: setTimes first, then the subtitle, then the title -- setTitle is what
			// (re)starts the countdown from the times currently configured.
			gui.setTimes(
					Math.max(0, AletheiaConfig.titleFadeInTicks),
					Math.max(1, stayTicks),
					Math.max(0, AletheiaConfig.titleFadeOutTicks));
			gui.setSubtitle(subtitle == null || subtitle.isBlank()
					? Component.empty()
					: Component.literal(subtitle).withStyle(ChatFormatting.GRAY));
			gui.setTitle(Component.literal(title).withStyle(colour, ChatFormatting.BOLD));
		});
	}

	/**
	 * Replaces the {@code {name}} placeholders in a title template, given the fields as alternating
	 * name and value.
	 *
	 * <p>Lives here rather than with any one caller because every configurable title in the mod is
	 * written the same way -- {@code {room} ROOM}, {@code {done}/{total}}, {@code {time}} -- and they
	 * all end up at {@link #showTitle}.
	 */
	public static String fill(String template, String... fields) {
		if (template == null || template.isBlank()) {
			return "";
		}
		String filled = template;
		for (int i = 0; i + 1 < fields.length; i += 2) {
			filled = filled.replace("{" + fields[i] + "}", fields[i + 1]);
		}
		return filled;
	}

	public static void playSound() {
		Minecraft client = Minecraft.getInstance();
		float volume = Math.clamp(AletheiaConfig.alertVolume, 0, 100) / 100.0F;
		if (volume <= 0.0F) {
			return;
		}

		int index = AletheiaConfig.alertSound;
		SoundEvent sound = (index >= 0 && index < SOUNDS.length ? SOUNDS[index] : SOUNDS[0]).get();

		client.execute(() -> client.getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0F, volume)));
	}
}
