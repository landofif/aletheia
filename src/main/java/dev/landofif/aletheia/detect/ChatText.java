package dev.landofif.aletheia.detect;

import java.util.regex.Pattern;

/**
 * Flattens the decorated text servers send into something a regex can reason about.
 *
 * <p>Telos Realms leans on a custom resource-pack font, so a line that reads
 * {@code <glyph> CHERUBIM  Enough!} in game is really a private-use glyph, a run of Unicode
 * small-capital letters, and then plain ASCII. Matching that directly is miserable, so
 * every line is folded down to ASCII first.
 *
 * <p>Codepoints are written as escapes on purpose -- these glyphs are invisible in most
 * diffs and easy to mangle when a file gets re-encoded.
 */
public final class ChatText {
	private ChatText() {
	}

	/**
	 * Unicode small capitals A-Z in order, skipping X -- there is no small-capital X assigned,
	 * so generators just emit a plain lowercase "x", which already survives as ASCII.
	 */
	private static final String SMALL_CAPS =
			"\u1D00\u0299\u1D04\u1D05\u1D07\uA730\u0262\u029C\u026A\u1D0A\u1D0B\u029F\u1D0D"
					+ "\u0274\u1D0F\u1D18\u01EB\u0280\uA731\u1D1B\u1D1C\u1D20\u1D21\u028F\u1D22";
	private static final String SMALL_CAPS_PLAIN = "abcdefghijklmnopqrstuvwyz";

	/** Glyphs that mean the same letter but come from a different generator. */
	private static final String SMALL_CAPS_ALT = "\uA7AF";
	private static final String SMALL_CAPS_ALT_PLAIN = "q";

	private static final char SECTION_SIGN = '\u00A7';

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/**
	 * Dense fold table. Every small-cap glyph we know about lives below U+A800, so the table
	 * stays small; an entry that still equals its own index simply was not remapped.
	 */
	private static final char[] LOOKUP = buildLookup();

	private static char[] buildLookup() {
		char[] table = new char[0xA800];
		for (int i = 0; i < table.length; i++) {
			table[i] = (char) i;
		}
		map(table, SMALL_CAPS, SMALL_CAPS_PLAIN);
		map(table, SMALL_CAPS_ALT, SMALL_CAPS_ALT_PLAIN);
		return table;
	}

	private static void map(char[] table, String from, String to) {
		if (from.length() != to.length()) {
			throw new IllegalStateException("small-caps table is out of sync: " + from.length() + " vs " + to.length());
		}
		for (int i = 0; i < from.length(); i++) {
			table[from.charAt(i)] = to.charAt(i);
		}
	}

	/**
	 * Folds a raw chat line to plain ASCII.
	 *
	 * <p>Small capitals become their ASCII letter, legacy section-sign colour codes are dropped, and
	 * anything else outside ASCII collapses to a space so it can never glue two words together.
	 * Runs of whitespace are collapsed and the result is trimmed. ASCII casing is preserved,
	 * because the player name in "Foxiani has defeated..." is worth keeping intact.
	 */
	public static String normalize(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "";
		}

		StringBuilder out = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);

			if (c == SECTION_SIGN) {
				// Legacy colour code -- drop it along with the formatting character that follows.
				i++;
				continue;
			}
			if (c < 0x80) {
				out.append(c);
			} else if (c < LOOKUP.length && LOOKUP[c] < 0x80) {
				out.append(LOOKUP[c]);
			} else {
				out.append(' ');
			}
		}

		return WHITESPACE.matcher(out).replaceAll(" ").trim();
	}

	/**
	 * Whether {@code phrase} appears anywhere in {@code text}, ignoring case.
	 *
	 * <p>The obvious spelling of this -- lower-casing both sides and calling {@code contains} -- builds
	 * two throwaway strings every time it is asked, which is nothing on its own and a great deal in the
	 * loops that ask it: the HUD filter puts every glyph of every boss bar through it once a frame.
	 * {@link String#regionMatches(boolean, int, String, int, int)} answers the same question without
	 * allocating anything, and is case-insensitive on <i>both</i> sides, so neither argument has to be
	 * folded first.
	 */
	public static boolean containsIgnoreCase(String text, String phrase) {
		if (text == null || phrase == null) {
			return false;
		}
		int span = phrase.length();
		if (span == 0) {
			return true;
		}
		int last = text.length() - span;
		for (int start = 0; start <= last; start++) {
			if (text.regionMatches(true, start, phrase, 0, span)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Reduces text to just its letters and digits, lower-cased.
	 *
	 * <p>For comparing item names, where the decoration is not worth fighting. Spaces, brackets,
	 * kerning glyphs and -- importantly -- apostrophes all disappear, so a name written with a
	 * typographic {@code U+2019} still matches one typed with a plain {@code '}. "Nature's Gift",
	 * "[nature's gift]" and "NATURE’S GIFT" all reduce to {@code naturesgift}.
	 */
	public static String lettersAndDigits(String raw) {
		String folded = normalize(raw);
		StringBuilder out = new StringBuilder(folded.length());
		for (int i = 0; i < folded.length(); i++) {
			char c = folded.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				out.append(Character.toLowerCase(c));
			}
		}
		return out.toString();
	}
}
