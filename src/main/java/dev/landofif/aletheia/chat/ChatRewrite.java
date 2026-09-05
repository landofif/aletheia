package dev.landofif.aletheia.chat;

import dev.landofif.aletheia.ChatWatcher;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Shortens the server's wordier chat lines to something you can read at a glance.
 *
 * <p>Toggling other players on and off is the case worth having: the server announces it with a full
 * sentence, which reads as news rather than as the state it is. "Players: [shown]" says the same
 * thing in a width your eye can take in mid-fight.
 *
 * <p>Two events, because they answer different questions. {@code MODIFY_GAME} swaps the text;
 * {@code ALLOW_GAME} drops the line entirely, which is what an empty replacement means and is not
 * something a modified message can express -- an empty component is still a blank line in chat.
 */
public final class ChatRewrite {
	private ChatRewrite() {
	}

	/** Matched against the folded, lower-cased line, so colour codes and glyphs cannot get in the way. */
	private static final String SHOWN = "can once again see other players";
	private static final String HIDDEN = "have hidden other players";

	public static void register() {
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
			Rewrite rewrite = match(message, overlay);
			// A rule with nothing to say is a rule that wants the line gone.
			return rewrite == null || !rewrite.text().isBlank();
		});

		ClientReceiveMessageEvents.MODIFY_GAME.register((message, overlay) -> {
			Rewrite rewrite = match(message, overlay);
			return rewrite == null ? message : Component.literal(rewrite.text()).withStyle(rewrite.colour());
		});
	}

	/** What a matched line should become. */
	private record Rewrite(String text, ChatFormatting colour) {
	}

	/** @return the replacement for this line, or {@code null} to leave it exactly as it came */
	@Nullable
	private static Rewrite match(Component message, boolean overlay) {
		// The action bar is the other feature's business, and these lines never arrive there anyway.
		if (overlay || !AletheiaConfig.enabled || !AletheiaConfig.shortenPlayerVisibility) {
			return null;
		}
		if (!ChatWatcher.onEnabledServer()) {
			return null;
		}

		String text = ChatText.normalize(message.getString()).toLowerCase(Locale.ROOT);
		if (text.contains(SHOWN)) {
			return new Rewrite(orEmpty(AletheiaConfig.playersShownText), ChatFormatting.GREEN);
		}
		if (text.contains(HIDDEN)) {
			return new Rewrite(orEmpty(AletheiaConfig.playersHiddenText), ChatFormatting.RED);
		}
		return null;
	}

	private static String orEmpty(String configured) {
		return configured == null ? "" : configured.trim();
	}
}
