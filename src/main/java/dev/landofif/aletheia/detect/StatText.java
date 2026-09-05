package dev.landofif.aletheia.detect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Reads a stat panel a server has drawn, and works out what each reading is called.
 *
 * <p><b>Telos writes its panel in words</b>, a field to a line -- {@code Attack: +60.6 (48.8%)},
 * {@code Critical Chance: +41.5 (35.6%)}, {@code Server: [Germany, Hub-1]} -- so a label is
 * everything up to a colon and a value is everything after it. Labels run to more than one word and
 * values are not always numbers, which is why neither is assumed here: whatever stands to the left of
 * the colon names whatever stands to the right, and what to do with it is the caller's business.
 *
 * <p>A line can carry several fields at once, as the tab list's header does
 * ({@code Version: 26.1.2 | Players: 58 | Server IP: ...}), so a second label ends the value before
 * it. A label is only ever the words immediately before its colon, which is what keeps
 * {@code 26.1.2 | Players} from being read as one.
 *
 * <p>Failing a colon, a panel drawn as <b>pictures and numbers</b> is read instead: the codepoint
 * {@code U+16115} draws {@code telos:glyph/tooltip/symbol/attack.png}, so the digits after it are
 * your attack and the name of the stat is the file behind the glyph -- exactly as a boss bar's name
 * is (see {@link dev.landofif.aletheia.boss.BossBars}). Telos does not draw its tab panel that way,
 * but it does have the glyphs and it does use them on item tooltips, so the reading is kept.
 *
 * <p>Those glyphs live in {@code minecraft:default} rather than in a font of the server's own, so
 * "is this the server's HUD" cannot be answered by the font here. It does not need to be: anything
 * that names something and puts a value beside it is a reading worth having, and which of them to
 * put on screen is the player's choice.
 *
 * <p>No Minecraft types: the glyph lookup arrives as {@link Glyphs}, so the whole of this can be
 * exercised on a table of made-up codepoints.
 */
public final class StatText {
	private StatText() {
	}

	/**
	 * One reading.
	 *
	 * @param name  folded to letters and digits, e.g. {@code attack}, {@code criticalchance}
	 * @param value as it was written, e.g. {@code +60.6 (48.8%)}, {@code [TS Eden]}, {@code 52}
	 */
	public record Stat(String name, String value) {
	}

	/** A stretch of text in one font, in the order it is drawn. */
	public record Piece(String font, String text) {
	}

	/** Names the picture a glyph draws -- the pack's font definition in game, a table in a test. */
	public interface Glyphs {
		Optional<String> textureOf(String font, int codePoint);
	}

	/** Every reading in the order it appears; the first of a repeated name wins. */
	public static List<Stat> read(List<Piece> pieces, Glyphs glyphs) {
		StringBuilder raw = new StringBuilder();
		for (Piece piece : pieces) {
			raw.append(piece.text());
		}

		List<Stat> labelled = readLabelled(raw.toString());
		if (!labelled.isEmpty()) {
			return labelled;
		}

		Reader reader = new Reader(glyphs);
		for (Piece piece : pieces) {
			reader.accept(piece);
		}
		return reader.finish();
	}

	/**
	 * Where a label may reach back to: the words immediately before a colon, and nothing else.
	 *
	 * <p>Letters and spaces only. A digit or a bracket ends it, which is what stops the value of one
	 * field being read as part of the name of the next -- in {@code 26.1.2 | Players:} the label is
	 * "Players", and the version number belongs to the field before it.
	 */
	private static boolean labelChar(char c) {
		return Character.isLetter(c) || c == ' ' || c == '\'' || c == '.';
	}

	/** A colon, or the guillemet the server writes its item stats with. */
	private static boolean labelEnd(char c) {
		return c == ':' || c == '»';
	}

	/**
	 * Every {@code Label: value} in one line.
	 *
	 * <p>Two passes: find where each label begins and ends first, then take each value as everything
	 * between its colon and the beginning of the next label. That is the only way round that works --
	 * a value cannot know where it stops until the field after it has been found.
	 */
	private static List<Stat> readLabelled(String line) {
		record Label(int start, int colon, String name) {
		}

		List<Label> labels = new ArrayList<>();
		for (int i = 0; i < line.length(); i++) {
			if (!labelEnd(line.charAt(i))) {
				continue;
			}

			int start = i;
			while (start > 0 && labelChar(line.charAt(start - 1))) {
				start--;
			}
			String name = ChatText.lettersAndDigits(line.substring(start, i));
			if (!name.isEmpty()) {
				labels.add(new Label(start, i, name));
			}
		}

		Map<String, String> found = new LinkedHashMap<>();
		for (int i = 0; i < labels.size(); i++) {
			Label label = labels.get(i);
			int end = i + 1 < labels.size() ? labels.get(i + 1).start() : line.length();
			String value = tidyValue(line.substring(Math.min(label.colon() + 1, end), end));
			if (!value.isEmpty()) {
				found.putIfAbsent(label.name(), value);
			}
		}

		List<Stat> stats = new ArrayList<>(found.size());
		found.forEach((name, value) -> stats.add(new Stat(name, value)));
		return stats;
	}

	/** Trims a value of the whitespace and the separators the line's layout left on it. */
	private static String tidyValue(String value) {
		int start = 0;
		int end = value.length();
		while (start < end && (Character.isWhitespace(value.charAt(start)) || value.charAt(start) == '|')) {
			start++;
		}
		while (end > start && (Character.isWhitespace(value.charAt(end - 1))
				|| value.charAt(end - 1) == '|'
				|| value.charAt(end - 1) == ',')) {
			end--;
		}
		return value.substring(start, end);
	}

	/** The bracketed aside a value can carry: {@code +60.6 (48.8%)} without the {@code (48.8%)}. */
	private static final Pattern ASIDE = Pattern.compile("\\s*\\([^()]*\\)\\s*$");

	/**
	 * A value with its bracketed aside taken off, for a readout with no room for it.
	 *
	 * <p>Telos writes what a stat is worth twice -- {@code +60.6 (48.8%)} is the points and then what
	 * they come to -- and only the first of those fits in a corner of the screen.
	 */
	public static String brief(String value) {
		return ASIDE.matcher(value).replaceAll("").trim();
	}

	/** {@code telos:glyph/tooltip/symbol/attack.png} to {@code attack}. */
	public static String pictureName(String texture) {
		String name = texture;
		int slash = name.lastIndexOf('/');
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		int dot = name.lastIndexOf('.');
		if (dot > 0) {
			name = name.substring(0, dot);
		}
		return ChatText.lettersAndDigits(name);
	}

	/**
	 * The state machine.
	 *
	 * <p>Three things are being carried at once: the number being read, the word being read, and what
	 * the last thing to name a stat was. A number ends when anything that is not part of one arrives,
	 * and it is filed under the picture before it if there was one, or the word before it if there was
	 * not -- which is the difference between reading an icon panel and reading {@code Attack 52}.
	 */
	private static final class Reader {
		private final Glyphs glyphs;

		/** Insertion-ordered so the readout can offer them in the order the panel draws them. */
		private final Map<String, String> found = new LinkedHashMap<>();

		private final StringBuilder word = new StringBuilder();
		private final StringBuilder number = new StringBuilder();

		/** A picture, or a word that ended in a colon: something that plainly names what follows. */
		private String named;

		/** The last ordinary word, used only when nothing better named the number. */
		private String previousWord;

		Reader(Glyphs glyphs) {
			this.glyphs = glyphs;
		}

		void accept(Piece piece) {
			piece.text().codePoints().forEach(codePoint -> read(piece.font(), codePoint));
		}

		private void read(String font, int codePoint) {
			Optional<String> texture = glyphs.textureOf(font, codePoint);
			if (texture.isPresent()) {
				endNumber();
				endWord(false);
				named = pictureName(texture.get());
				return;
			}

			if (Character.isDigit(codePoint)) {
				endWord(false);
				number.appendCodePoint(codePoint);
				return;
			}
			// Only ever part of a number already under way, so a full stop ending a sentence and a
			// percent sign on its own are both left where they are.
			if ((codePoint == '.' || codePoint == '%' || codePoint == ',') && !number.isEmpty()) {
				number.appendCodePoint(codePoint);
				return;
			}
			if (Character.isLetter(codePoint)) {
				endNumber();
				word.appendCodePoint(codePoint);
				return;
			}

			// Anything else ends what was being read. A colon or a guillemet also says the word before
			// it was naming what comes next, which an ordinary space does not.
			endNumber();
			endWord(codePoint == ':' || codePoint == '»' || codePoint == '=');
		}

		private void endNumber() {
			if (number.isEmpty()) {
				return;
			}

			String value = tidy(number.toString());
			number.setLength(0);

			String name = named != null ? named : previousWord;
			named = null;
			if (name != null && !name.isEmpty() && !value.isEmpty()) {
				found.putIfAbsent(name, value);
			}
		}

		private void endWord(boolean names) {
			if (word.isEmpty()) {
				return;
			}

			String folded = ChatText.lettersAndDigits(word.toString());
			word.setLength(0);
			if (folded.isEmpty()) {
				return;
			}
			if (names) {
				named = folded;
			} else {
				previousWord = folded;
			}
		}

		/** Drops the punctuation a number can pick up at its end: {@code 52,} is {@code 52}. */
		private static String tidy(String value) {
			int end = value.length();
			while (end > 0 && (value.charAt(end - 1) == '.' || value.charAt(end - 1) == ',')) {
				end--;
			}
			return value.substring(0, end);
		}

		List<Stat> finish() {
			endNumber();

			List<Stat> stats = new ArrayList<>(found.size());
			found.forEach((name, value) -> stats.add(new Stat(name, value)));
			return stats;
		}
	}
}
