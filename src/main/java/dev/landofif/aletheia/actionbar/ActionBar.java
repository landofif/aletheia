package dev.landofif.aletheia.actionbar;

import dev.landofif.aletheia.ChatWatcher;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.NeoEdenParser;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Decides what to do with text headed for the area above the hotbar.
 *
 * <p>Called from a mixin on {@code Gui.setOverlayMessage} rather than from Fabric's message events,
 * because those miss most of it. {@code ClientPacketListener.setActionBarText} calls the Gui
 * directly without going through {@code ChatListener} -- which is what Fabric's events hook. Only
 * messages sent as system chat with the overlay flag reach those events. The Gui method is where
 * both routes finally meet.
 *
 * <p>This is <b>not</b> where the Telos readouts come from. Those are drawn wherever the server's
 * replaced text shader puts them, from whatever channel happened to carry the text; see
 * {@link dev.landofif.aletheia.serverhud.ServerHud} and {@code /aletheia hudscan}.
 */
public final class ActionBar {
	private ActionBar() {
	}

	/** How many distinct lines {@code /aletheia actionbar} can show. */
	private static final int HISTORY = 8;

	private static final Deque<String> recent = new ArrayDeque<>();

	/**
	 * @return {@code true} if this line should be swallowed instead of displayed
	 */
	public static boolean handle(Component message) {
		String text = ChatText.normalize(message.getString());
		remember(text);

		if (!AletheiaConfig.enabled || !ChatWatcher.onEnabledServer()) {
			return false;
		}

		// The dungeon can put score updates here too. Done before the hide check so it still works
		// when a line is about to be swallowed, and regardless of whether hiding is on at all.
		if (AletheiaConfig.scanActionBar) {
			ChatWatcher.inspect(message);
		}

		if (!AletheiaConfig.hideActionBar) {
			return false;
		}

		String filter = AletheiaConfig.actionBarHideFilter;
		if (filter == null || filter.isBlank()) {
			// No filter means hide the lot, including vanilla held-item names.
			return true;
		}

		String lower = text.toLowerCase(Locale.ROOT);
		for (String phrase : NeoEdenParser.splitPhrases(filter)) {
			if (lower.contains(phrase.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private static void remember(String text) {
		if (text.isEmpty() || text.equals(recent.peekLast())) {
			return;
		}
		recent.addLast(text);
		while (recent.size() > HISTORY) {
			recent.removeFirst();
		}
	}

	/** Most recent first, for {@code /aletheia actionbar}. */
	public static List<String> recentMessages() {
		List<String> newestFirst = new ArrayList<>(recent);
		Collections.reverse(newestFirst);
		return newestFirst;
	}
}
