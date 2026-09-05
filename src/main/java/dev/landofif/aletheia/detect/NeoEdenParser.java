package dev.landofif.aletheia.detect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recognises the Neo-Eden chat lines worth surfacing as an on-screen title.
 *
 * <p>Deliberately free of any Minecraft types so the matching rules can be exercised on their own.
 * Every line is folded through {@link ChatText#normalize} first, so the patterns only ever deal
 * with trimmed, whitespace-collapsed ASCII.
 */
public final class NeoEdenParser {
	private NeoEdenParser() {
	}

	/**
	 * The trailing score counter, e.g. {@code (175/UNDEFINED)} or {@code (259/265)}.
	 *
	 * <p>Anchored to the end of the line, since that is where the dungeon always puts it, and that
	 * anchor is most of what keeps unrelated server messages from matching. A trailing period or
	 * exclamation mark is tolerated in case the wording shifts.
	 */
	private static final Pattern PROGRESS = Pattern.compile(
			"\\((\\d{1,7})\\s*/\\s*(\\d{1,7}|undefined|\\?+)\\)\\s*[.!]?$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * Lines that end in a counter but are not dungeon score updates.
	 *
	 * <p>Realm boss kills read {@code [boss] has been defeated. (4/10)}, which the counter pattern
	 * alone happily matches. Note this is "has <b>been</b> defeated" -- the barracks announcement is
	 * "has defeated the edenic warriors", so the two do not collide.
	 *
	 * <p>Transcendence is broadcast network-wide as
	 * {@code [Singapore, Hub-1]<glyphs> vlifepain Has just fully transcended Assassin! (1/6)}, where
	 * the counter is how many classes that player has transcended -- nothing to do with a dungeon.
	 * Matched on the verb in any form, since the announcement is worded differently in places
	 * ("is transcending", "has transcended").
	 */
	private static final Pattern ALWAYS_IGNORED = Pattern.compile(
			"\\bhas\\s+been\\s+defeated\\b|\\btranscend\\w*",
			Pattern.CASE_INSENSITIVE);

	/**
	 * A realm boss kill: {@code The Verdant Tyrant has been defeated. (1/10)}.
	 *
	 * <p>These were being thrown away wholesale, because the trailing counter made them look like
	 * dungeon score. They are worth having: the counter is the run-up to Raphael, and the kill is what
	 * starts the respawn clock. {@link #ALWAYS_IGNORED} still keeps the wording away from
	 * {@link DungeonEvent.Progress}, which is all it was ever for.
	 *
	 * <p>{@link #LINE_LEAD} is not used, since it would swallow a bracketed <i>name</i> along with the
	 * rank tags it is meant to skip; the brackets are allowed round the name instead. Starting the
	 * match at the name is still what stops a player quoting the line in chat.
	 */
	private static final Pattern REALM_BOSS_DEFEATED = Pattern.compile(
			"^\\[?([A-Za-z][A-Za-z' ]{1,31}?)\\]?\\s+has\\s+been\\s+defeated\\b[^(]*"
					+ "\\((\\d{1,3})\\s*/\\s*(\\d{1,3})\\)\\s*[.!]?$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The server's own clear line, inside the leaderboard: {@code Defeated Malfas in 24s}.
	 *
	 * <p>Telos sends the whole leaderboard as a <b>single multi-line message</b>, so by the time this
	 * runs {@link ChatText#normalize} has collapsed the newlines into spaces and this line sits in the
	 * middle of a long one. Hence no start anchor: there is nothing to anchor to.
	 *
	 * <p>The boss name is deliberately narrow -- letters, spaces, apostrophes, hyphens -- rather than
	 * {@code .+?}, so it cannot run away across the rest of the collapsed leaderboard.
	 */
	private static final Pattern DUNGEON_CLEARED = Pattern.compile(
			"\\bdefeated\\s+([A-Za-z][A-Za-z' -]{0,31}?)\\s+in\\s+"
					+ "((?:\\d{1,4}(?:\\.\\d+)?\\s*[hms]\\s*){1,3})",
			Pattern.CASE_INSENSITIVE);

	/** One {@code 90s} / {@code 1m30s} / {@code 1h2m3s} run of number-and-unit pairs. */
	private static final Pattern DURATION_PART = Pattern.compile(
			"(\\d{1,4})(?:\\.(\\d+))?\\s*([hms])",
			Pattern.CASE_INSENSITIVE);

	/** Nothing sane clears a dungeon in longer than this; a match past it is a misread. */
	private static final int MAX_CLEAR_SECONDS = 24 * 60 * 60;

	/**
	 * Words seen in the score lines. Only consulted in strict mode -- the dungeon has more phrasings
	 * than anyone has catalogued, so the default is to trust the counter format alone.
	 */
	private static final String[] PROGRESS_HINTS = {
			"neo-eden", "neo eden", "persistence", "worth", "noted", "recorded",
			"cherubim", "attention", "acknowledges", "secured", "eden",
	};

	/**
	 * Optional junk ahead of a player name: leftovers from a stripped glyph, and/or rank tags
	 * like {@code [MVP+]}. Keeping a start anchor is what stops a player from firing these titles
	 * by simply typing the sentence in chat.
	 */
	private static final String LINE_LEAD = "^[^A-Za-z0-9\\[]{0,4}(?:\\[[^\\]]{0,32}\\]\\s*)*";

	/** Minecraft names are 3-16 of {@code [A-Za-z0-9_]}; the range is widened slightly for nicknames. */
	private static final String PLAYER = "([A-Za-z0-9_]{2,16})";

	/**
	 * The room announcements come in pairs -- one when someone walks in, one when they finish -- and
	 * the only thing separating them is the verb: "is attempting to solve" against "has solved".
	 * Neither can match the other's line, so the order they are tried in does not matter.
	 */
	private static final Pattern PUZZLE_STARTED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+is\\s+attempting\\s+to\\s+solve\\s+the\\s+(?:edenic\\s+)?([A-Za-z]+)\\s+puzzle\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern PUZZLE_SOLVED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+has\\s+solved\\s+the\\s+(?:edenic\\s+)?([A-Za-z]+)\\s+puzzle\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern BARRACKS_STARTED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+is\\s+attempting\\s+to\\s+defeat\\s+the\\s+edenic\\s+warriors\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern BARRACKS_CLEARED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+has\\s+defeated\\s+the\\s+edenic\\s+warriors\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * A Dreadwood room name: the words between "the" and "Room", so {@code Colour}, {@code Wave},
	 * {@code Demon} or {@code Bullet Hell}. Captured rather than listed, so a room nobody has written
	 * down yet still announces itself.
	 */
	private static final String ROOM = "([A-Za-z][A-Za-z ]{0,23}?)";

	/**
	 * {@code [Dreadwood Civilian] landofif is attempting the Colour Room!}
	 *
	 * <p>The civilian's tag is not required -- {@link #LINE_LEAD} merely tolerates it -- since the
	 * wording is distinctive on its own and the announcer may not always be the same NPC.
	 */
	private static final Pattern DREADWOOD_STARTED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+is\\s+attempting\\s+the\\s+" + ROOM + "\\s+room\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The matching "finished it" announcement:
	 * {@code [Dreadwood Civilian] iTzMaXl0rd has completed the Demon Room!}
	 *
	 * <p>"completed" is the wording the Thicket actually uses, for every room; the two synonyms and
	 * the optional "has" are slack against a re-word, and cost nothing because the room name and the
	 * word "Room" are what identify the line anyway. "attempting" is not in the list, so a room being
	 * entered can never be read as one being finished.
	 */
	private static final Pattern DREADWOOD_CLEARED = Pattern.compile(
			LINE_LEAD + PLAYER + "\\s+(?:has\\s+|have\\s+|just\\s+)*(?:completed|cleared|finished)"
					+ "\\s+the\\s+" + ROOM + "\\s+room\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * A Shadowlands spawn, which the server announces as the mob itself speaking:
	 * {@code [Reaper] The spectres watch closely.}, {@code [Herald] The torch burns bright.},
	 * {@code [Warden] The flag flies once more.}
	 *
	 * <p>The tag is what finds the line; {@link #SPAWN_LINES} is what decides whether it is a spawn.
	 * The four names are spelled out instead of taking any {@code [Tag]}, which would swallow rank
	 * prefixes and guild chat; a fifth announcer is a one-word change here.
	 *
	 * <p>{@link #LINE_LEAD} in front lets the tag sit behind another one -- {@code [Shadowlands]
	 * [Herald] ...} -- while still refusing a player who types the sentence mid-message.
	 */
	private static final Pattern SHADOWLANDS_SPAWN = Pattern.compile(
			LINE_LEAD + "\\[(defender|reaper|herald|warden)\\]\\s*(.*)$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * What each of them says <i>on spawning</i>, which is the only line of theirs worth a title. They
	 * talk at other times as well -- the Reaper's "So we shall persist in spirit." is it dying -- so
	 * the tag alone is not enough to go on.
	 *
	 * <p>Compared through {@link ChatText#lettersAndDigits}, so punctuation, casing and any decoration
	 * the server wraps the sentence in make no difference.
	 *
	 * <p>The Defender's is the odd one: it spawns saying literally {@code ...} and speaks in words
	 * ("Master, I require strength.") only afterwards. Folded that leaves nothing at all, which
	 * {@link #isSpawnLine} turns into the right test -- it said no words -- rather than "contains the
	 * empty string", which every line does.
	 */
	private static final Map<String, String> SPAWN_LINES = foldValues(Map.of(
			"defender", "...",
			"reaper", "The spectres watch closely.",
			"herald", "The torch burns bright.",
			"warden", "The flag flies once more."));

	/**
	 * @param said      what the mob just said
	 * @param spawnLine its spawn line, already folded to letters and digits
	 * @return whether the two are the same line
	 *
	 *         <p>A wordless spawn line -- the Defender's {@code ...} -- has to be matched by the
	 *         <i>absence</i> of words, since folding leaves nothing to compare and any line at all
	 *         "contains" that. Which also means it does not matter whether the server writes three
	 *         dots or a single ellipsis glyph: neither survives the fold, and no sentence it says
	 *         later can look like it.
	 */
	private static boolean isSpawnLine(String said, String spawnLine) {
		String key = ChatText.lettersAndDigits(said);
		return spawnLine.isEmpty() ? key.isEmpty() : key.contains(spawnLine);
	}

	private static Map<String, String> foldValues(Map<String, String> lines) {
		Map<String, String> folded = new HashMap<>(lines.size());
		lines.forEach((mob, line) -> folded.put(mob, ChatText.lettersAndDigits(line)));
		return Map.copyOf(folded);
	}

	/**
	 * Cherubim's shouts. The name and the word are adjacent once the custom-font glyph has been
	 * folded away, so only a short run of punctuation/space is allowed between them -- that stops
	 * a sentence merely mentioning Cherubim from triggering the alert.
	 *
	 * <p>The shout is also the <i>whole</i> line: {@code CHERUBIM » ENOUGH!} and nothing after it.
	 * Anchoring to the end is what keeps an unrelated line that happens to open the same way --
	 * {@code Cherubim » Enough performance ...} -- from being read as the shout. Trailing
	 * punctuation is allowed; a decorative glyph after the word has already folded to a space and
	 * been trimmed away by the time this runs.
	 */
	private static final Pattern CHERUBIM_SHOUT = Pattern.compile(
			"\\bcherubim\\b[^A-Za-z0-9]{0,8}(enough|silence)\\b[^A-Za-z0-9]{0,4}$",
			Pattern.CASE_INSENSITIVE);

	/**
	 * The Cog Sentinel's stabiliser counter, exactly as the fight sends it:
	 * {@code (1/5) Cog Stabilisers destroyed}.
	 *
	 * <p>The counter is at the <b>front</b> here, which is the one thing keeping it clear of
	 * {@link #PROGRESS} -- that pattern is anchored to the end of the line. It is still tried first, so
	 * a re-word that moves the counter to the end cannot silently turn the fight into dungeon score.
	 *
	 * <p>The American spelling is allowed alongside the server's own, and the total is captured rather
	 * than fixed at five, so the fight changing how many it has costs nothing here.
	 */
	private static final Pattern COG_STABILISERS = Pattern.compile(
			LINE_LEAD + "\\((\\d{1,2})\\s*/\\s*(\\d{1,2})\\)\\s*cog\\s+stabili[sz]ers?\\s+destroyed\\b",
			Pattern.CASE_INSENSITIVE);

	/**
	 * @param rawMessage      the chat line exactly as received
	 * @param strictProgress  require a known Neo-Eden phrase before treating a trailing
	 *                        {@code (n/m)} as a score update
	 * @param ignoredPhrases  extra substrings that disqualify a line entirely, matched
	 *                        case-insensitively; lets a new false positive be silenced from the
	 *                        settings screen instead of needing a code change
	 * @return the recognised event, or {@code null} if the line is of no interest
	 */
	public static DungeonEvent parse(String rawMessage, boolean strictProgress, List<String> ignoredPhrases) {
		String text = ChatText.normalize(rawMessage);
		if (text.isEmpty() || mentionsIgnoredPhrase(text, ignoredPhrases)) {
			return null;
		}

		// Ahead of the ALWAYS_IGNORED gate on purpose. That gate exists to keep "has been defeated"
		// wording away from the score title, and the leaderboard this line arrives in carries exactly
		// that wording -- but this is not a title, so the gate has no business eating it.
		Matcher cleared = DUNGEON_CLEARED.matcher(text);
		if (cleared.find()) {
			int seconds = parseDurationSeconds(cleared.group(2));
			if (seconds > 0 && seconds <= MAX_CLEAR_SECONDS) {
				return new DungeonEvent.DungeonCleared(cleared.group(1).trim(), seconds);
			}
		}

		if (ALWAYS_IGNORED.matcher(text).find()) {
			return null;
		}

		Matcher shout = CHERUBIM_SHOUT.matcher(text);
		if (shout.find()) {
			return new DungeonEvent.CherubimShout(
					shout.group(1).equalsIgnoreCase("silence") ? DungeonEvent.Shout.SILENCE : DungeonEvent.Shout.ENOUGH);
		}

		Matcher puzzleStart = PUZZLE_STARTED.matcher(text);
		if (puzzleStart.find()) {
			return new DungeonEvent.PuzzleStarted(puzzleStart.group(1), puzzleStart.group(2).toLowerCase(Locale.ROOT));
		}

		Matcher puzzle = PUZZLE_SOLVED.matcher(text);
		if (puzzle.find()) {
			return new DungeonEvent.PuzzleSolved(puzzle.group(1), puzzle.group(2).toLowerCase(Locale.ROOT));
		}

		Matcher barracksStart = BARRACKS_STARTED.matcher(text);
		if (barracksStart.find()) {
			return new DungeonEvent.BarracksStarted(barracksStart.group(1));
		}

		Matcher barracks = BARRACKS_CLEARED.matcher(text);
		if (barracks.find()) {
			return new DungeonEvent.BarracksCleared(barracks.group(1));
		}

		Matcher roomStart = DREADWOOD_STARTED.matcher(text);
		if (roomStart.find()) {
			return new DungeonEvent.DreadwoodRoomStarted(roomStart.group(1), roomStart.group(2).trim());
		}

		Matcher roomCleared = DREADWOOD_CLEARED.matcher(text);
		if (roomCleared.find()) {
			return new DungeonEvent.DreadwoodRoomCleared(roomCleared.group(1), roomCleared.group(2).trim());
		}

		Matcher spawn = SHADOWLANDS_SPAWN.matcher(text);
		if (spawn.find()) {
			String mob = spawn.group(1);
			String said = spawn.group(2).trim();
			String spawnLine = SPAWN_LINES.get(mob.toLowerCase(Locale.ROOT));

			// One of them talking, but not to announce itself. Nothing else can match a line that opens
			// with their tag, so this is the end of the road for it either way.
			if (spawnLine != null && !isSpawnLine(said, spawnLine)) {
				return null;
			}
			return new DungeonEvent.ShadowlandsSpawn(mob, said);
		}

		// Ahead of PROGRESS on purpose -- see the pattern's note.
		Matcher cog = COG_STABILISERS.matcher(text);
		if (cog.find()) {
			Integer destroyed = parseCount(cog.group(1));
			Integer total = parseCount(cog.group(2));
			if (destroyed == null || total == null || total < 1) {
				return null;
			}
			return new DungeonEvent.CogStabilisers(destroyed, total);
		}

		Matcher progress = PROGRESS.matcher(text);
		if (progress.find()) {
			if (strictProgress && !mentionsNeoEden(text)) {
				return null;
			}
			Integer current = parseCount(progress.group(1));
			if (current == null) {
				return null;
			}
			// "UNDEFINED" / "???" both mean the dungeon has not revealed the requirement yet.
			Integer total = parseCount(progress.group(2));
			return new DungeonEvent.Progress(current, total == null ? DungeonEvent.Progress.UNKNOWN_TOTAL : total);
		}

		return null;
	}

	public static DungeonEvent parse(String rawMessage, boolean strictProgress) {
		return parse(rawMessage, strictProgress, List.of());
	}

	public static DungeonEvent parse(String rawMessage) {
		return parse(rawMessage, false, List.of());
	}

	/** Splits a comma-separated settings field into phrases, dropping blanks. */
	public static List<String> splitPhrases(String commaSeparated) {
		if (commaSeparated == null || commaSeparated.isBlank()) {
			return List.of();
		}
		List<String> phrases = new ArrayList<>();
		for (String part : commaSeparated.split(",")) {
			String trimmed = part.trim();
			if (!trimmed.isEmpty()) {
				phrases.add(trimmed);
			}
		}
		return phrases;
	}

	/** The reader's own ignore list, which is checked before anything else is tried. */
	private static boolean mentionsIgnoredPhrase(String text, List<String> ignoredPhrases) {
		if (ignoredPhrases.isEmpty()) {
			return false;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		for (String phrase : ignoredPhrases) {
			if (lower.contains(phrase.toLowerCase(Locale.ROOT))) {
				return true;
			}
		}
		return false;
	}

	private static boolean mentionsNeoEden(String text) {
		String lower = text.toLowerCase(Locale.ROOT);
		for (String hint : PROGRESS_HINTS) {
			if (lower.contains(hint)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Adds up a duration written as number-and-unit pairs -- {@code 24s}, {@code 1m30s},
	 * {@code 1h 2m 3s} -- into whole seconds.
	 *
	 * <p>Fractions of a second are dropped rather than rounded: the server states the clear time to
	 * the second in every example seen, and a mod that rounded 24.6s up to 25s would be reporting a
	 * personal best the leaderboard disagrees with.
	 *
	 * @return the total, or 0 if nothing parsed
	 */
	static int parseDurationSeconds(String duration) {
		Matcher part = DURATION_PART.matcher(duration);
		long total = 0L;
		while (part.find()) {
			long value = Long.parseLong(part.group(1));
			total += switch (Character.toLowerCase(part.group(3).charAt(0))) {
				case 'h' -> value * 3600L;
				case 'm' -> value * 60L;
				default -> value;
			};
			if (total > MAX_CLEAR_SECONDS) {
				return 0;
			}
		}
		return (int) total;
	}

	/** @return the parsed number, or {@code null} if the group was not numeric (or overflowed). */
	private static Integer parseCount(String group) {
		for (int i = 0; i < group.length(); i++) {
			if (group.charAt(i) < '0' || group.charAt(i) > '9') {
				return null;
			}
		}
		try {
			return Integer.valueOf(group);
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
