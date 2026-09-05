package dev.landofif.aletheia.ui;

import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.Nullable;

/** Colour settings, which are typed in by hand and so have to survive being typed in wrong. */
public final class Colours {
	private Colours() {
	}

	/**
	 * A setting read as RGB.
	 *
	 * <p>Parsed with the game's own reader, so {@code #1757A7} and a plain colour name both work. An
	 * empty or unreadable setting falls back rather than matching nothing or drawing black -- these
	 * are edited in a text box, and a typo should not take a bar off the screen.
	 */
	public static int parse(@Nullable String setting, int fallback) {
		if (setting == null || setting.isBlank()) {
			return fallback;
		}
		DataResult<TextColor> parsed = TextColor.parseColor(setting.trim());
		return parsed.isSuccess() ? parsed.getOrThrow().getValue() : fallback;
	}
}
