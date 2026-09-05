package dev.landofif.aletheia.detect;

import java.util.List;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the cooldown <b>stat</b> off an item's lore, e.g. the {@code Cooldown » 360s} line on
 * Nature's Gift.
 *
 * <p>This is the ability's full duration, not how long is left -- the lore never counts down. The
 * remaining time comes from the vanilla item cooldown system; see
 * {@link dev.landofif.aletheia.gift.NaturesGift}.
 */
public final class CooldownText {
	private CooldownText() {
	}

	/**
	 * The stat line. Matched against the raw string rather than folded ASCII, because the separator
	 * is usually a {@code »} that folding would turn into a space; {@code [^0-9]{0,4}} covers that,
	 * a colon, or nothing at all.
	 */
	private static final Pattern COOLDOWN_STAT = Pattern.compile(
			"cooldown[^0-9]{0,4}(\\d{1,5})(?:\\.\\d+)?\\s*s?\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * @return the ability's total cooldown in seconds, from the first lore line that states one
	 */
	public static OptionalInt parseTotalSeconds(List<String> loreLines) {
		if (loreLines == null) {
			return OptionalInt.empty();
		}
		for (String line : loreLines) {
			if (line == null) {
				continue;
			}
			Matcher matcher = COOLDOWN_STAT.matcher(line);
			if (matcher.find()) {
				int seconds = Integer.parseInt(matcher.group(1));
				if (seconds > 0) {
					return OptionalInt.of(seconds);
				}
			}
		}
		return OptionalInt.empty();
	}

	/** Formats as {@code 5:23}, or {@code 0:07} under a minute. */
	public static String asClock(int totalSeconds) {
		int safe = Math.max(0, totalSeconds);
		return (safe / 60) + ":" + String.format("%02d", safe % 60);
	}

	/** Formats as {@code 323s}. */
	public static String asSeconds(int totalSeconds) {
		return Math.max(0, totalSeconds) + "s";
	}

	/**
	 * Formats tenths of a second as {@code 3.4s}.
	 *
	 * <p>For counts short enough that the tenth is the point -- Afterburner's second shot is seven
	 * seconds off, and "7s" for most of a second either side of the truth is no use for timing one.
	 */
	public static String asTenths(int tenths) {
		int safe = Math.max(0, tenths);
		return (safe / 10) + "." + (safe % 10) + "s";
	}
}
