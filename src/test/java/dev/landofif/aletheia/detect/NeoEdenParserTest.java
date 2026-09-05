package dev.landofif.aletheia.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The chat lines below are the real thing, copied from Neo-Eden. If the dungeon changes its wording
 * these are the tests that should be updated first.
 */
class NeoEdenParserTest {

	/** The custom-font prefix the server puts in front of Cherubim's lines: a glyph plus small caps. */
	private static final String CHERUBIM = "肔 ᴄʜᴇʀᴜʙɪᴍ  ";

	@Nested
	@DisplayName("score progress")
	class Progress {

		@ParameterizedTest(name = "{0}")
		@CsvSource(delimiter = '|', value = {
				"Neo-Eden acknowledges your persistence. (175/UNDEFINED) | 175 | -1",
				"Your worth has been noted. (105/UNDEFINED)              | 105 | -1",
				"Your persistence has been recorded. (259/265)           | 259 | 265",
				"Cherubim's attention has been secured. (259/265)        | 259 | 265",
		})
		void readsTheCounter(String line, int current, int total) {
			var progress = assertInstanceOf(DungeonEvent.Progress.class, NeoEdenParser.parse(line));
			assertEquals(current, progress.current());
			assertEquals(total, progress.total());
		}

		@Test
		@DisplayName("an unknown total renders as the placeholder")
		void formatsUnknownTotal() {
			var progress = (DungeonEvent.Progress) NeoEdenParser.parse("Neo-Eden acknowledges your persistence. (175/UNDEFINED)");
			assertEquals("175/???", progress.format("???"));
		}

		@Test
		@DisplayName("a known total renders both numbers")
		void formatsKnownTotal() {
			var progress = (DungeonEvent.Progress) NeoEdenParser.parse("Your persistence has been recorded. (259/265)");
			assertEquals("259/265", progress.format("???"));
		}

		@Test
		@DisplayName("the counter has to be at the end of the line")
		void ignoresCountersMidLine() {
			assertNull(NeoEdenParser.parse("(3/5) players are ready"));
		}

		@Test
		@DisplayName("strict mode requires a Neo-Eden phrase")
		void strictModeFiltersUnrelatedCounters() {
			assertNull(NeoEdenParser.parse("Party progress (3/5)", true));
			assertInstanceOf(DungeonEvent.Progress.class, NeoEdenParser.parse("Party progress (3/5)", false));
			assertInstanceOf(DungeonEvent.Progress.class,
					NeoEdenParser.parse("Your persistence has been recorded. (259/265)", true));
		}
	}

	@Nested
	@DisplayName("room announcements")
	class Completions {

		@Test
		void puzzleStarted() {
			var started = assertInstanceOf(DungeonEvent.PuzzleStarted.class,
					NeoEdenParser.parse("Kyle_Clash is attempting to solve the edenic light puzzle!"));
			assertEquals("Kyle_Clash", started.player());
			assertEquals("light", started.puzzle());
		}

		@Test
		void barracksStarted() {
			var started = assertInstanceOf(DungeonEvent.BarracksStarted.class,
					NeoEdenParser.parse("LunaEpitaph is attempting to defeat the edenic warriors in the edenic barracks battle room!"));
			assertEquals("LunaEpitaph", started.player());
		}

		@Test
		@DisplayName("starting a room is not finishing it")
		void startsAndFinishesStayApart() {
			assertInstanceOf(DungeonEvent.PuzzleSolved.class,
					NeoEdenParser.parse("Kyle_Clash has solved the edenic light puzzle!"));
			assertInstanceOf(DungeonEvent.BarracksCleared.class,
					NeoEdenParser.parse("LunaEpitaph has defeated the edenic warriors in the edenic barracks battle room!"));
		}

		@Test
		void puzzleSolved() {
			var solved = assertInstanceOf(DungeonEvent.PuzzleSolved.class,
					NeoEdenParser.parse("Kyle_Clash Has solved the edenic light puzzle!"));
			assertEquals("Kyle_Clash", solved.player());
			assertEquals("light", solved.puzzle());
		}

		@Test
		@DisplayName("a rank prefix in front of the name is tolerated")
		void puzzleSolvedWithRankPrefix() {
			var solved = assertInstanceOf(DungeonEvent.PuzzleSolved.class,
					NeoEdenParser.parse("[MVP+] Kyle_Clash has solved the edenic light puzzle!"));
			assertEquals("Kyle_Clash", solved.player());
		}

		@Test
		void barracksCleared() {
			var cleared = assertInstanceOf(DungeonEvent.BarracksCleared.class,
					NeoEdenParser.parse("Foxiani has defeated the edenic warriors in the edenic barracks battle room!"));
			assertEquals("Foxiani", cleared.player());
		}

		@Test
		@DisplayName("a player quoting the message in chat does not count")
		void ignoresQuotedAnnouncements() {
			assertNull(NeoEdenParser.parse("<Bob> lol Kyle_Clash Has solved the edenic light puzzle!"));
			assertNull(NeoEdenParser.parse("<Bob> LunaEpitaph is attempting to defeat the edenic warriors"));
		}
	}

	@Nested
	@DisplayName("Dreadwood Thicket rooms")
	class Dreadwood {

		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource(delimiter = '|', value = {
				"[Dreadwood Civilian] landofif is attempting the Colour Room!        | landofif   | Colour",
				"[Dreadwood Civilian] iTzMaXl0rd is attempting the Wave Room!        | iTzMaXl0rd | Wave",
				"[Dreadwood Civilian] iTzMaXl0rd is attempting the Demon Room!       | iTzMaXl0rd | Demon",
				"[Dreadwood Civilian] landofif is attempting the Bullet Hell Room!   | landofif   | Bullet Hell",
		})
		void roomStarted(String line, String player, String room) {
			var started = assertInstanceOf(DungeonEvent.DreadwoodRoomStarted.class, NeoEdenParser.parse(line));
			assertEquals(player, started.player());
			assertEquals(room, started.room());
		}

		@Test
		@DisplayName("the civilian's tag is optional")
		void worksWithoutTheTag() {
			var started = assertInstanceOf(DungeonEvent.DreadwoodRoomStarted.class,
					NeoEdenParser.parse("landofif is attempting the Colour Room!"));
			assertEquals("Colour", started.room());
		}

		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource(delimiter = '|', value = {
				"[Dreadwood Civilian] iTzMaXl0rd has completed the Demon Room!        | iTzMaXl0rd | Demon",
				"[Dreadwood Civilian] iTzMaXl0rd has completed the Wave Room!         | iTzMaXl0rd | Wave",
				"[Dreadwood Civilian] oSzabi has completed the Bullet Hell Room!      | oSzabi     | Bullet Hell",
				"[Dreadwood Civilian] landofif has completed the Colour Room!         | landofif   | Colour",
		})
		void roomCleared(String line, String player, String room) {
			var cleared = assertInstanceOf(DungeonEvent.DreadwoodRoomCleared.class, NeoEdenParser.parse(line));
			assertEquals(player, cleared.player());
			assertEquals(room, cleared.room());
		}

		@ParameterizedTest(name = "also cleared: {0}")
		@ValueSource(strings = {
				"[Dreadwood Civilian] landofif completed the Colour Room!",
				"[Dreadwood Civilian] landofif has cleared the Colour Room!",
				"[Dreadwood Civilian] landofif finished the Colour Room!",
		})
		@DisplayName("a little slack around the confirmed wording")
		void roomClearedVariants(String line) {
			assertInstanceOf(DungeonEvent.DreadwoodRoomCleared.class, NeoEdenParser.parse(line));
		}

		@Test
		@DisplayName("entering a room is never read as finishing it")
		void startIsNotACompletion() {
			assertInstanceOf(DungeonEvent.DreadwoodRoomStarted.class,
					NeoEdenParser.parse("[Dreadwood Civilian] landofif is attempting the Bullet Hell Room!"));
		}

		@Test
		@DisplayName("a player quoting the announcement does not count")
		void ignoresQuotedAnnouncements() {
			assertNull(NeoEdenParser.parse("<Bob> landofif is attempting the Colour Room!"));
		}

		@Test
		@DisplayName("an unrelated sentence about a room is not an announcement")
		void ignoresOtherRoomTalk() {
			assertNull(NeoEdenParser.parse("landofif is in the Colour Room"));
			assertNull(NeoEdenParser.parse("anyone want to do the wave room"));
		}
	}

	@Nested
	@DisplayName("Shadowlands spawns")
	class Shadowlands {

		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource(delimiter = '|', value = {
				"[Reaper] The spectres watch closely. | Reaper   | The spectres watch closely.",
				"[Herald] The torch burns bright.     | Herald   | The torch burns bright.",
				"[Warden] The flag flies once more.   | Warden   | The flag flies once more.",
		})
		void announcerSpeaking(String line, String mob, String said) {
			var spawn = assertInstanceOf(DungeonEvent.ShadowlandsSpawn.class, NeoEdenParser.parse(line));
			assertEquals(mob, spawn.mob());
			assertEquals(said, spawn.line());
		}

		@ParameterizedTest(name = "not a spawn: {0}")
		@ValueSource(strings = {
				"[Reaper] So we shall persist in spirit.",
				"[Herald] Never let go of light.",
				"[Warden] Remember us, warriors.",
				"[Warden] Anything else at all.",
		})
		@DisplayName("their dying words, and anything else they say, are not spawns")
		void otherLinesFromTheSameMob(String line) {
			assertNull(NeoEdenParser.parse(line));
		}

		@Test
		@DisplayName("punctuation and casing do not have to match")
		void spawnLineIsComparedLoosely() {
			assertInstanceOf(DungeonEvent.ShadowlandsSpawn.class,
					NeoEdenParser.parse("[Herald] THE TORCH BURNS BRIGHT"));
		}

		@ParameterizedTest(name = "the Defender spawns wordlessly: {0}")
		@ValueSource(strings = {
				"[Defender] ...",
				"[Defender] …",   // a single ellipsis glyph reads the same once folded
				"[Defender]",
		})
		void defenderSaysNothingAtAll(String line) {
			var spawn = assertInstanceOf(DungeonEvent.ShadowlandsSpawn.class, NeoEdenParser.parse(line));
			assertEquals("Defender", spawn.mob());
		}

		@Test
		@DisplayName("the Defender speaking in words is not it spawning")
		void defenderTalkingIsNotASpawn() {
			assertNull(NeoEdenParser.parse("[Defender] Master, I require strength."));
		}

		@Test
		@DisplayName("the tag may sit behind another one")
		void tagBehindAnotherTag() {
			assertInstanceOf(DungeonEvent.ShadowlandsSpawn.class,
					NeoEdenParser.parse("[Shadowlands] [Warden] The flag flies once more."));
		}

		@ParameterizedTest(name = "ignores: {0}")
		@ValueSource(strings = {
				"<Bob> [Herald] The torch burns bright.",
				"[MVP+] Warden joined the game",
				"the herald is over there",
		})
		@DisplayName("only a line that opens with the tag counts")
		void ignoresEverythingElse(String line) {
			assertNull(NeoEdenParser.parse(line));
		}
	}

	@Nested
	@DisplayName("Cherubim's shouts")
	class Cherubim {

		@Test
		void enough() {
			var shout = assertInstanceOf(DungeonEvent.CherubimShout.class, NeoEdenParser.parse(CHERUBIM + "Enough!"));
			assertEquals(DungeonEvent.Shout.ENOUGH, shout.shout());
		}

		@Test
		void silence() {
			var shout = assertInstanceOf(DungeonEvent.CherubimShout.class, NeoEdenParser.parse(CHERUBIM + "Silence!"));
			assertEquals(DungeonEvent.Shout.SILENCE, shout.shout());
		}

		@Test
		@DisplayName("the shout has to follow the name closely")
		void ignoresIncidentalMentions() {
			assertNull(NeoEdenParser.parse("cherubim is rough, i have had enough of this boss"));
		}

		@Test
		@DisplayName("a longer line that merely opens the same way is not the shout")
		void ignoresLinesThatCarryOn() {
			assertNull(NeoEdenParser.parse(CHERUBIM + "Enough performance to earn a reward."));
			assertNull(NeoEdenParser.parse("cherubim » enough performance"));
		}

		@Test
		@DisplayName("trailing punctuation is still the shout")
		void allowsTrailingPunctuation() {
			assertInstanceOf(DungeonEvent.CherubimShout.class, NeoEdenParser.parse(CHERUBIM + "Enough..."));
			assertInstanceOf(DungeonEvent.CherubimShout.class, NeoEdenParser.parse(CHERUBIM + "Silence"));
		}
	}

	@Nested
	@DisplayName("Cog Sentinel stabilisers")
	class CogStabilisers {

		/** The five lines exactly as the fight sends them. */
		@ParameterizedTest(name = "{0}")
		@CsvSource(delimiter = '|', value = {
				"(1/5) Cog Stabilisers destroyed | 1 | 5",
				"(2/5) Cog Stabilisers destroyed | 2 | 5",
				"(3/5) Cog Stabilisers destroyed | 3 | 5",
				"(4/5) Cog Stabilisers destroyed | 4 | 5",
				"(5/5) Cog Stabilisers destroyed | 5 | 5",
		})
		void readsTheCount(String line, int destroyed, int total) {
			var cog = assertInstanceOf(DungeonEvent.CogStabilisers.class, NeoEdenParser.parse(line));
			assertEquals(destroyed, cog.destroyed());
			assertEquals(total, cog.total());
			assertEquals(destroyed == total, cog.allDestroyed());
		}

		@Test
		@DisplayName("the leading counter is never read as dungeon score")
		void isNotProgress() {
			// PROGRESS is anchored to the end of the line, and this counter is at the front -- but the
			// stabiliser pattern is tried first regardless, so a re-word cannot quietly swap the two.
			assertInstanceOf(DungeonEvent.CogStabilisers.class, NeoEdenParser.parse("(3/5) Cog Stabilisers destroyed"));
			assertInstanceOf(DungeonEvent.CogStabilisers.class,
					NeoEdenParser.parse("(3/5) Cog Stabilisers destroyed", true));
		}

		@Test
		@DisplayName("the American spelling and a singular still count")
		void allowsSpellingDrift() {
			assertInstanceOf(DungeonEvent.CogStabilisers.class, NeoEdenParser.parse("(5/5) Cog Stabilizers destroyed"));
			assertInstanceOf(DungeonEvent.CogStabilisers.class, NeoEdenParser.parse("(1/5) cog stabiliser destroyed!"));
		}

		@Test
		@DisplayName("an unrelated counter at the front of a line is not a stabiliser")
		void ignoresOtherCounters() {
			assertNull(NeoEdenParser.parse("(3/5) players are ready"));
			assertNull(NeoEdenParser.parse("(3/5) Cog Stabilisers remaining"));
		}

		@Test
		@DisplayName("a player quoting the line does not count")
		void ignoresPlayersQuotingIt() {
			assertNull(NeoEdenParser.parse("landofif: (1/5) Cog Stabilisers destroyed"));
		}
	}

	@Nested
	@DisplayName("text folding")
	class Folding {

		@Test
		void foldsSmallCapsToAscii() {
			assertEquals("cherubim Enough!", ChatText.normalize(CHERUBIM + "Enough!"));
		}

		@Test
		void stripsLegacyColourCodes() {
			assertEquals("Hello world", ChatText.normalize("§aHello §lworld"));
		}

		@Test
		@DisplayName("unknown glyphs become spaces rather than joining words")
		void separatesOnUnknownGlyphs() {
			assertEquals("a b", ChatText.normalize("a肔b"));
		}

		@ParameterizedTest(name = "{0} -> naturesgift")
		@ValueSource(strings = {
				"Nature's Gift",
				"nature's gift",
				"NATURE'S GIFT",
				"[nature's gift]",
				"nature's gift",          // the kerning glyph the server injects
				"nature’s gift",           // typographic apostrophe
				"ɴᴀᴛᴜʀᴇ's ɢɪꜰᴛ",                // small capitals
				"  nature's   gift  ",
		})
		@DisplayName("item names reduce to the same key however they are decorated")
		void foldsItemNamesForComparison(String name) {
			assertEquals("naturesgift", ChatText.lettersAndDigits(name));
		}

		@Test
		void foldingAnEmptyNameGivesEmpty() {
			assertEquals("", ChatText.lettersAndDigits(""));
			assertEquals("", ChatText.lettersAndDigits(null));
		}
	}

	@Nested
	@DisplayName("realm boss kills")
	class RealmBosses {

		@ParameterizedTest(name = "ignores: {0}")
		@ValueSource(strings = {
				"[boss] has been defeated. (4/10)",
				"[boss] has been defeated. (10/10)",
				"The Verdant Tyrant has been defeated. (1/10)",
		})
		@DisplayName("a boss kill counter is not dungeon score progress")
		void bossKillsAreNotProgress(String line) {
			assertNull(NeoEdenParser.parse(line));
		}

		@Test
		@DisplayName("\"has been defeated\" must not shadow the barracks \"has defeated\"")
		void barracksStillMatches() {
			assertInstanceOf(DungeonEvent.BarracksCleared.class,
					NeoEdenParser.parse("Foxiani has defeated the edenic warriors in the edenic barracks battle room!"));
		}
	}

	@Nested
	@DisplayName("transcendence broadcasts")
	class Transcendence {

		@ParameterizedTest(name = "ignores: {0}")
		@ValueSource(strings = {
				"[Singapore, Hub-1]弒聖 vlifepain Has just fully transcended Assassin! (1/6)",
				"[Hub-1] Someone has just fully transcended Mage! (6/6)",
				"vlifepain is transcending Assassin (3/6)",
		})
		@DisplayName("the class counter is not dungeon score progress")
		void transcendenceIsNotProgress(String line) {
			assertNull(NeoEdenParser.parse(line));
		}
	}

	@Nested
	@DisplayName("user-supplied ignore phrases")
	class IgnorePhrases {

		@Test
		void silenceAMatchingLine() {
			assertNull(NeoEdenParser.parse("Guild raid progress (3/5)", false, List.of("guild raid")));
		}

		@Test
		void leaveOtherLinesAlone() {
			assertInstanceOf(DungeonEvent.Progress.class,
					NeoEdenParser.parse("Your persistence has been recorded. (259/265)", false, List.of("guild raid")));
		}

		@Test
		void splitsAndTrimsTheSettingsField() {
			assertEquals(List.of("guild raid", "auction"), NeoEdenParser.splitPhrases(" guild raid , auction ,, "));
			assertEquals(List.of(), NeoEdenParser.splitPhrases("   "));
			assertEquals(List.of(), NeoEdenParser.splitPhrases(null));
		}
	}

	@Nested
	@DisplayName("the Cooldown stat on an item")
	class Cooldowns {

		@ParameterizedTest(name = "{0} -> {1}s")
		@CsvSource(delimiter = '|', value = {
				"Cooldown \u00BB 360s      | 360",
				"Cooldown: 360s          | 360",
				"Cooldown \u00BB 360      | 360",
				"Cooldown \u00BB 12.5s    | 12",
				"cooldown 45s            | 45",
		})
		@DisplayName("reads the ability's total duration")
		void readsTheStatLine(String line, int seconds) {
			assertEquals(OptionalInt.of(seconds), CooldownText.parseTotalSeconds(List.of(line)));
		}

		@Test
		@DisplayName("finds the stat among the item's other lore")
		void findsItAmongRealLore() {
			List<String> lore = List.of(
					"Health \u00BB +2 + 0.6",
					"Attack \u00BB +6",
					"Defense \u00BB +7",
					"Vitality \u00BB +4 + 3",
					"Cooldown \u00BB 360s",
					"Ability: When below 30% health, you heal 9 HP over 1.5s.");
			assertEquals(OptionalInt.of(360), CooldownText.parseTotalSeconds(lore));
		}

		@Test
		@DisplayName("the ability text's \"1.5s\" must not be mistaken for the cooldown")
		void ignoresOtherDurations() {
			assertEquals(OptionalInt.empty(), CooldownText.parseTotalSeconds(
					List.of("Ability: When below 30% health, you heal 9 HP over 1.5s.")));
		}

		@Test
		@DisplayName("prose mentioning the word cooldown is not a stat line")
		void ignoresProseAboutTheCooldown() {
			assertEquals(OptionalInt.empty(), CooldownText.parseTotalSeconds(
					List.of("The cooldown also resets when changing worlds or dungeons.")));
		}

		@Test
		@DisplayName("the stat wins even when prose about the cooldown comes first")
		void prefersTheStatLine() {
			List<String> lore = List.of(
					"Ability: When below 30% health, you heal 9 HP over 1.5s. The cooldown also",
					"resets when changing worlds or dungeons.",
					"Cooldown » 360s");
			assertEquals(OptionalInt.of(360), CooldownText.parseTotalSeconds(lore));
		}

		@ParameterizedTest(name = "no cooldown stat in: {0}")
		@ValueSource(strings = {"", "Heals you for 9 health", "Nature's Gift", "12 Tier Points"})
		void reportsNothingWithoutTheStat(String line) {
			assertEquals(OptionalInt.empty(), CooldownText.parseTotalSeconds(List.of(line)));
		}

		@Test
		void formatsForDisplay() {
			assertEquals("5:23", CooldownText.asClock(323));
			assertEquals("0:07", CooldownText.asClock(7));
			assertEquals("6:00", CooldownText.asClock(360));
			assertEquals("323s", CooldownText.asSeconds(323));
		}

		@Test
		@DisplayName("a short count keeps its tenth")
		void formatsTenths() {
			// Afterburner's second shot: seven seconds is short enough that rounding to whole ones
			// would lose the part you time the shot by.
			assertEquals("7.0s", CooldownText.asTenths(70));
			assertEquals("3.4s", CooldownText.asTenths(34));
			assertEquals("0.1s", CooldownText.asTenths(1));
			assertEquals("0.0s", CooldownText.asTenths(0));
			assertEquals("0.0s", CooldownText.asTenths(-5), "a count past its end reads as done");
		}
	}

	@Nested
	@DisplayName("negative-space spacing")
	class Spacing {

		@Test
		@DisplayName("one glyph per direction, U+E000 right and U+F000 left")
		void encodesASingleStep() {
			assertEquals("\uE005", SpacingText.advance(5));
			assertEquals("\uF005", SpacingText.advance(-5));
			assertEquals("", SpacingText.advance(0));
		}

		@Test
		@DisplayName("the widest single glyph is 1280 pixels")
		void encodesTheLargestStep() {
			assertEquals("\uE500", SpacingText.advance(SpacingText.MAX_ADVANCE));
			assertEquals("\uF500", SpacingText.advance(-SpacingText.MAX_ADVANCE));
		}

		@Test
		@DisplayName("anything wider is spelled with several glyphs")
		void splitsWiderGaps() {
			assertEquals("\uE500\uE001", SpacingText.advance(SpacingText.MAX_ADVANCE + 1));
			assertEquals("\uF500\uF500\uF00A", SpacingText.advance(-(2 * SpacingText.MAX_ADVANCE + 10)));
		}

		@Test
		@DisplayName("the glyphs always sum back to the width asked for")
		void addsUpToTheRequestedWidth() {
			for (int width : new int[] {-3000, -1281, -1, 0, 1, 7, 1280, 1281, 4000}) {
				assertEquals(width, totalAdvance(SpacingText.advance(width)), "width " + width);
			}
		}

		/** Reads the advances back out of the encoded string, the way the font's table would. */
		private int totalAdvance(String spacing) {
			int total = 0;
			for (int i = 0; i < spacing.length(); i++) {
				char c = spacing.charAt(i);
				total += c >= 0xF000 ? -(c - 0xF000) : c - 0xE000;
			}
			return total;
		}
	}

	/**
	 * Telos' end-of-run leaderboard, which arrives as one message with the lines inside it. The whole
	 * thing is reproduced because that is what the parser actually receives -- the clear line sits in
	 * the middle of it, and {@code ChatText.normalize} will have flattened the newlines into spaces
	 * long before any pattern gets a look.
	 */
	@Nested
	@DisplayName("the server's own clear line")
	class Cleared {

		private static final String LEADERBOARD = String.join("\n",
				"\u2620 Abyss of Demons \u2620",
				"\u2620 Defeated Malfas in 24s",
				"",
				"\ud80c\udc70 Damage   \u25c6 Contribs   \ud80c\udcf1 Loot Boost",
				"\ud815\udc71 landofif (you)   100.0%   100%   +20%");

		@Test
		@DisplayName("is found inside the whole leaderboard message")
		void readsTheClearLineOutOfTheLeaderboard() {
			var cleared = assertInstanceOf(DungeonEvent.DungeonCleared.class, NeoEdenParser.parse(LEADERBOARD));
			assertEquals("Malfas", cleared.boss());
			assertEquals(24, cleared.seconds());
		}

		@ParameterizedTest(name = "{0} -> {1}s")
		@CsvSource(delimiter = '|', value = {
				"Defeated Malfas in 24s          | Malfas          | 24",
				"Defeated Malfas in 1m24s        | Malfas          | 84",
				"Defeated Malfas in 1m 24s       | Malfas          | 84",
				"Defeated Malfas in 1h2m3s       | Malfas          | 3723",
				"Defeated Malfas in 2m           | Malfas          | 120",
				"Defeated The Rustborn King in 90s | The Rustborn King | 90",
		})
		void readsBossAndTime(String line, String boss, int seconds) {
			var cleared = assertInstanceOf(DungeonEvent.DungeonCleared.class, NeoEdenParser.parse(line));
			assertEquals(boss, cleared.boss());
			assertEquals(seconds, cleared.seconds());
		}

		@Test
		@DisplayName("a fraction of a second is dropped, not rounded up")
		void truncatesFractions() {
			var cleared = (DungeonEvent.DungeonCleared) NeoEdenParser.parse("Defeated Malfas in 24.6s");
			assertEquals(24, cleared.seconds(),
					"a best the leaderboard disagrees with is worse than no best");
		}

		@Test
		@DisplayName("survives the \"has been defeated\" wording the leaderboard carries")
		void isNotEatenByTheScoreGuard() {
			// ALWAYS_IGNORED exists to keep this wording away from the score title. It must not take
			// the clear line with it, which is why the clear is matched ahead of that gate.
			assertInstanceOf(DungeonEvent.DungeonCleared.class, NeoEdenParser.parse(
					"Malfas has been defeated. (4/10) \u2620 Defeated Malfas in 24s"));
		}

		@Test
		@DisplayName("a boss kill without a time is still just a boss kill")
		void ignoresDefeatWithoutATime() {
			assertNull(NeoEdenParser.parse("Malfas has been defeated. (4/10)"));
		}

		@ParameterizedTest(name = "ignores: {0}")
		@ValueSource(strings = {
				"Defeated Malfas in the barracks",
				"Defeated Malfas in 0s",
				"You were defeated in 24s",
		})
		void ignoresNearMisses(String line) {
			assertNull(NeoEdenParser.parse(line));
		}

		@Test
		@DisplayName("the boss name cannot run away across the flattened message")
		void keepsTheBossNameShort() {
			var cleared = (DungeonEvent.DungeonCleared) NeoEdenParser.parse(
					"Defeated Malfas in 24s Damage Contribs Loot Boost landofif 100.0%");
			assertEquals("Malfas", cleared.boss());
		}
	}

	@ParameterizedTest(name = "ignores: {0}")
	@ValueSource(strings = {
			"",
			"hey does anyone have a spare pickaxe",
			"Player123 joined the game",
			"You are now in party with Foxiani",
	})
	void ignoresEverythingElse(String line) {
		assertNull(NeoEdenParser.parse(line));
	}
}
