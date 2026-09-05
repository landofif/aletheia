package dev.landofif.aletheia.detect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading the stat panel out of the player list.
 *
 * <p>The codepoints below are the pack's own, from {@code assets/minecraft/font/default.json}: the
 * stat icons are hung on {@code U+1610F} upwards and are <b>not</b> in a private-use area, so nothing
 * here may assume a range -- only the font definition says which codepoint draws which picture, which
 * is why {@link StatText} takes the lookup rather than owning one.
 */
class StatTextTest {

	private static final String DEFAULT_FONT = "minecraft:default";

	/** What the pack really maps, for the four stats the panel shows. */
	private static final Map<Integer, String> PACK = Map.of(
			0x1610F, "telos:glyph/tooltip/symbol/vitality.png",
			0x16112, "telos:glyph/tooltip/symbol/health.png",
			0x16114, "telos:glyph/tooltip/symbol/speed.png",
			0x16115, "telos:glyph/tooltip/symbol/attack.png",
			0x16116, "telos:glyph/tooltip/symbol/defense.png");

	private static final StatText.Glyphs GLYPHS = (font, codePoint) ->
			DEFAULT_FONT.equals(font) ? Optional.ofNullable(PACK.get(codePoint)) : Optional.empty();

	private static final int VITALITY = 0x1610F;
	private static final int HEALTH = 0x16112;
	private static final int SPEED = 0x16114;
	private static final int ATTACK = 0x16115;
	private static final int DEFENSE = 0x16116;

	/**
	 * Written out of the number rather than pasted in: these glyphs are invisible in a diff and are
	 * easily mangled by a re-encoding, the same reason {@link ChatText} keeps its table in escapes.
	 */
	private static String icon(int codePoint) {
		return new String(Character.toChars(codePoint));
	}

	private static List<StatText.Stat> read(String text) {
		return StatText.read(List.of(new StatText.Piece(DEFAULT_FONT, text)), GLYPHS);
	}

	private static String valueOf(List<StatText.Stat> stats, String name) {
		return stats.stream()
				.filter(stat -> stat.name().equals(name))
				.map(StatText.Stat::value)
				.findFirst()
				.orElse(null);
	}

	@Nested
	@DisplayName("an icon and a number")
	class Icons {

		@Test
		@DisplayName("the picture behind the glyph names the number after it")
		void namesByPicture() {
			// What the panel really looks like: icon, number, icon, number.
			List<StatText.Stat> stats = read(icon(ATTACK) + " 52 " + icon(DEFENSE) + " 30");

			assertEquals(2, stats.size());
			assertEquals("attack", stats.get(0).name());
			assertEquals("52", stats.get(0).value());
			assertEquals("defense", stats.get(1).name());
			assertEquals("30", stats.get(1).value());
		}

		@Test
		@DisplayName("the order the panel draws them in is kept")
		void keepsPanelOrder() {
			List<StatText.Stat> stats = read(icon(SPEED) + " 45 " + icon(VITALITY) + " 40 " + icon(ATTACK) + " 52");
			assertEquals(List.of("speed", "vitality", "attack"),
					stats.stream().map(StatText.Stat::name).toList());
		}

		@Test
		@DisplayName("a decimal keeps its point and a percentage its sign")
		void keepsTheWholeReading() {
			assertEquals("1.5", valueOf(read(icon(SPEED) + " 1.5"), "speed"));
			assertEquals("45%", valueOf(read(icon(HEALTH) + " 45%"), "health"));
			// Trailing punctuation is not part of the number.
			assertEquals("30", valueOf(read(icon(DEFENSE) + " 30, and so on"), "defense"));
		}

		@Test
		@DisplayName("an icon with no number is not a reading")
		void needsANumber() {
			assertTrue(read(icon(ATTACK) + " " + icon(DEFENSE)).isEmpty());
		}

		@Test
		@DisplayName("a number with nothing naming it is dropped")
		void needsAName() {
			assertTrue(read("52 30 45").isEmpty());
		}
	}

	@Nested
	@DisplayName("panels written out in words")
	class Words {

		@Test
		@DisplayName("a word before a colon names what follows")
		void namesByLabel() {
			List<StatText.Stat> stats = read("Attack: 52  Defense: 30");
			assertEquals("52", valueOf(stats, "attack"));
			assertEquals("30", valueOf(stats, "defense"));
		}

		@Test
		@DisplayName("a guillemet does the same, as it does on item lore")
		void namesByGuillemet() {
			assertEquals("40", valueOf(read("Vitality » 40"), "vitality"));
		}

		@Test
		@DisplayName("a word on its own still names the number after it")
		void namesByPlainWord() {
			assertEquals("52", valueOf(read("Attack 52"), "attack"));
		}

		@Test
		@DisplayName("the pack's small capitals fold to ordinary letters")
		void foldsTheServerFont() {
			// The panel is written in the pack's font, so the name arrives as small capitals.
			assertEquals("52", valueOf(read("ᴀᴛᴛᴀᴄᴋ: 52"), "attack"));
		}
	}

	@Nested
	@DisplayName("the panel is not the only thing in the tab list")
	class Noise {

		@Test
		@DisplayName("a picture wins over a word standing before it")
		void picturesWin() {
			// "Lvl 56" and then the real panel: the icon names its own number, not the word before it.
			List<StatText.Stat> stats = read("Lvl 56 " + icon(ATTACK) + " 52");
			assertEquals("52", valueOf(stats, "attack"));
			assertEquals("56", valueOf(stats, "lvl"));
		}

		@Test
		@DisplayName("the first reading of a name wins")
		void firstReadingWins() {
			// The same stat in the header and again in a row: the header was read first.
			List<StatText.Stat> stats = read(icon(ATTACK) + " 52 " + icon(ATTACK) + " 999");
			assertEquals(1, stats.size());
			assertEquals("52", stats.get(0).value());
		}

		@Test
		@DisplayName("pieces are read in the order they are drawn, across fonts")
		void readsAcrossPieces() {
			// The icon and its number regularly arrive as separate components in different fonts.
			List<StatText.Stat> stats = StatText.read(List.of(
					new StatText.Piece(DEFAULT_FONT, icon(ATTACK)),
					new StatText.Piece("mythichud:layout/rotmc2-layout/fonts/rotmc2-hud/stat", "52")),
					GLYPHS);
			assertEquals(1, stats.size());
			assertEquals("attack", stats.get(0).name());
			assertEquals("52", stats.get(0).value());
		}
	}

	@Nested
	@DisplayName("the panel as Telos really writes it")
	class RealRows {

		/** Copied off the screen on 2026-08-22: every one of these is a row of the player list. */
		@ParameterizedTest(name = "{0}")
		@CsvSource(delimiter = ';', value = {
				"Attack: +60.6 (48.8%)          ; attack          ; +60.6 (48.8%)",
				"Speed: +87.3 (69.8%)           ; speed           ; +87.3 (69.8%)",
				"Defense: +65 (39.4%)           ; defense         ; +65 (39.4%)",
				"Vitality: +12.5 (0.25 hp/s)    ; vitality        ; +12.5 (0.25 hp/s)",
				"Critical Chance: +41.5 (35.6%) ; criticalchance  ; +41.5 (35.6%)",
				"Critical Damage: +49 (1.49x)   ; criticaldamage  ; +49 (1.49x)",
				"Loot Boost: +15                ; lootboost       ; +15",
				"Fame Boost: +0                 ; fameboost       ; +0",
				"Soul Points: 2,000,197         ; soulpoints      ; 2,000,197",
				"Glory: 989,547.4               ; glory           ; 989,547.4",
				"TPS: 20                        ; tps             ; 20",
				"Tier Points: 74                ; tierpoints      ; 74",
				"Gilded Fragments: 84/120       ; gildedfragments ; 84/120",
				// A value that is not a number at all, and has a digit inside it: reading the number out
				// of this one would say the server is called "1".
				"Server: [Germany, Hub-1]       ; server          ; [Germany, Hub-1]",
				"Guild: [TS Eden]               ; guild           ; [TS Eden]",
				"Class: Level 102 Samurai       ; class           ; Level 102 Samurai",
		})
		@DisplayName("a row is one field: everything before the colon names everything after it")
		void readsARow(String row, String name, String value) {
			assertEquals(value, valueOf(read(row), name));
		}

		@Test
		@DisplayName("the bracketed aside comes off for a readout with no room for it")
		void briefValues() {
			assertEquals("+60.6", StatText.brief("+60.6 (48.8%)"));
			assertEquals("+12.5", StatText.brief("+12.5 (0.25 hp/s)"));
			// Nothing to take off, so nothing is taken.
			assertEquals("+15", StatText.brief("+15"));
			assertEquals("2,000,197", StatText.brief("2,000,197"));
			assertEquals("[Germany, Hub-1]", StatText.brief("[Germany, Hub-1]"));
		}

		@Test
		@DisplayName("one line can carry several fields, as the tab header does")
		void readsSeveralFieldsInALine() {
			List<StatText.Stat> stats =
					read("Version: 26.1.2 | Players: 58 | Server IP: play.telosrealms.com");

			assertEquals("26.1.2", valueOf(stats, "version"));
			assertEquals("58", valueOf(stats, "players"));
			assertEquals("play.telosrealms.com", valueOf(stats, "serverip"));
		}

		@Test
		@DisplayName("the bullet the row opens with is not part of the name")
		void ignoresTheRowsBullet() {
			// Every stat row starts with a coloured square from the pack.
			assertEquals("+65", valueOf(read("\uE001Attack: +65"), "attack"));
		}

		@Test
		@DisplayName("a heading row has no field in it")
		void headingsAreNotFields() {
			assertTrue(read("Stats Info").isEmpty());
			assertTrue(read("General Info").isEmpty());
		}
	}

	@Test
	@DisplayName("a texture path reduces to the stat's name")
	void namesThePicture() {
		assertEquals("attack", StatText.pictureName("telos:glyph/tooltip/symbol/attack.png"));
		assertEquals("critchance", StatText.pictureName("telos:glyph/tooltip/symbol/critchance.png"));
	}
}
