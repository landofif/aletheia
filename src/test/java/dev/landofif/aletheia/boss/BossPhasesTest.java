package dev.landofif.aletheia.boss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The thresholds are the wiki's. If a fight is re-tuned these are the tests to update first --
 * a phase called at the wrong percentage is worse than not calling it at all.
 */
class BossPhasesTest {

	@Nested
	@DisplayName("finding the fight")
	class Naming {

		/**
		 * What the game actually supplies: the bar's name is a picture, so the identity is the texture
		 * behind its glyph. These paths are the server pack's own, from {@code telos:glyph/bossbar/}.
		 */
		@ParameterizedTest(name = "{0} -> {1}")
		@CsvSource(delimiter = '|', value = {
				"telos:glyph/bossbar/cherubim.png          | Cherubim",
				"telos:glyph/bossbar/hardmode_seraphim.png | True Seraph",
				"telos:glyph/bossbar/hardmode_ophanim.png  | True Ophan",
				"telos:glyph/bossbar/seraphim.png          | Seraphim",
				"telos:glyph/bossbar/ophanim.png           | Ophanim",
				"telos:glyph/bossbar/sylvaris.png          | Sylvaris",
				"telos:glyph/bossbar/voided_omnipotent.png | Voided Omnipotent",
				// Raphael's art is filed under a name that is not the fight's: open onyx.png and it reads
				// "RAPHAEL / THE SANGUINE LORD". The key is the file; the name on screen is the fight.
				"telos:glyph/bossbar/onyx.png              | Raphael",
				// The bar's own text comes along for the ride, and must not get in the picture's way.
				"Raphael's Chamber 37 4 0 telos:glyph/bossbar/onyx.png | Raphael",
		})
		@DisplayName("the bar is identified by its glyph's texture")
		void matchesTheBossBar(String identity, String expected) {
			BossPhases.Boss boss = BossPhases.forName(identity);
			assertNotNull(boss, identity);
			assertEquals(expected, boss.name());
		}

		@Test
		@DisplayName("hardmode is not the ordinary fight, though it contains its name")
		void hardmodeWinsOverTheOrdinaryFight() {
			// "seraphim" sits inside "hardmode_seraphim": match on fragments and every True fight is
			// called at the ordinary fight's percentages.
			assertEquals("True Seraph", BossPhases.forName("telos:glyph/bossbar/hardmode_seraphim.png").name());
			assertEquals("Seraphim", BossPhases.forName("telos:glyph/bossbar/seraphim.png").name());
			assertEquals("True Ophan", BossPhases.forName("telos:glyph/bossbar/hardmode_ophanim.png").name());
			assertEquals("Ophanim", BossPhases.forName("telos:glyph/bossbar/ophanim.png").name());
		}

		@Test
		@DisplayName("the other two onyx bars are not Raphael")
		void theOnyxFamilyIsThreeDifferentBars() {
			// The pack files three bars under names beginning "onyx", and they are three fights: onyx.png
			// draws "RAPHAEL", onyx2.png draws "ONYX", and onyx_guardian.png draws "ORION AND OSIRIS", the
			// pair of champions in Raphael's Castle. Match on a fragment and all three are called at
			// Raphael's marks, two of them in fights that are not his.
			assertEquals("Raphael", BossPhases.forName("telos:glyph/bossbar/onyx.png").name());
			assertNull(BossPhases.forName("telos:glyph/bossbar/onyx2.png"));
			assertNull(BossPhases.forName("telos:glyph/bossbar/onyx_guardian.png"));
		}

		@Test
		@DisplayName("the plain Omnipotent bar is not the Voided one")
		void omnipotentIsNotVoided() {
			// The pack ships both glyphs and "omnipotent" sits inside "voidedomnipotent". Only the voided
			// fight has phases here, so the plain bar must stay unmatched rather than borrow them.
			assertEquals("Voided Omnipotent",
					BossPhases.forName("telos:glyph/bossbar/voided_omnipotent.png").name());
			assertNull(BossPhases.forName("telos:glyph/bossbar/omnipotent.png"));
		}

		@Test
		@DisplayName("an unrelated bar is left alone")
		void ignoresOtherBars() {
			// A realm boss: same shape of bar, no phases worth calling.
			assertNull(BossPhases.forName("telos:glyph/bossbar/lotil.png"));
			// The server's whole HUD arrives as a bar of its own, text and pictures together.
			assertNull(BossPhases.forName("Permafrost 32 0 mythichud:assets/rotmc2/plate.png "
					+ "mythichud:font/default/hud_hp5.png"));
			// The same bar inside Raphael's dungeons, which is the one that broke: the HUD's text opens
			// with the area you are standing in, so matching "raphael" as a fragment read the entire HUD
			// as the fight -- a bar stuck at 0%, and every HUD element hidden along with it.
			assertNull(BossPhases.forName("Raphael's Castle 32 0 mythichud:assets/rotmc2/plate.png "
					+ "mythichud:font/default/hud_hp5.png"));
			assertNull(BossPhases.forName("Raphael's Chamber 32 0 mythichud:assets/rotmc2/plate.png "
					+ "mythichud:font/default/hud_hp5.png"));
			// A name in plain text names nothing: only the picture a bar draws does.
			assertNull(BossPhases.forName("Raphael"));
			assertNull(BossPhases.forName(""));
		}
	}

	@Nested
	@DisplayName("what comes next")
	class Upcoming {

		@Test
		@DisplayName("Cherubim: the black hole at 60%, desperation at 20%")
		void cherubim() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/cherubim.png");

			assertEquals("BLACK HOLE", BossPhases.next(boss, 1.00).name());
			assertEquals("BLACK HOLE", BossPhases.next(boss, 0.63).name());
			// On the threshold the phase has begun, so what is next is the one after it.
			assertEquals("DESPERATION", BossPhases.next(boss, 0.60).name());
			assertEquals("DESPERATION", BossPhases.next(boss, 0.21).name());
			assertNull(BossPhases.next(boss, 0.20), "nothing follows the last phase");
			assertNull(BossPhases.next(boss, 0.01));
		}

		@Test
		@DisplayName("True Ophan: the walls at 85%, the clock at 60%, gives up at 15%")
		void ophan() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/hardmode_ophanim.png");
			assertEquals("WALLS", BossPhases.next(boss, 1.00).name());
			assertEquals("WALLS", BossPhases.next(boss, 0.88).name());
			assertEquals("CLOCK", BossPhases.next(boss, 0.85).name());
			assertEquals("CLOCK", BossPhases.next(boss, 0.62).name());
			assertEquals("DESPERATION", BossPhases.next(boss, 0.40).name());
			assertNull(BossPhases.next(boss, 0.15));
		}

		@Test
		@DisplayName("the phase you are in is the deepest one passed")
		void currentPhase() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/hardmode_seraphim.png");
			assertNull(BossPhases.current(boss, 0.80), "still before the first threshold");
			assertEquals("QR CODE", BossPhases.current(boss, 0.50).name());
			assertEquals("QR CODE", BossPhases.current(boss, 0.21).name());
			assertEquals("DESPERATION", BossPhases.current(boss, 0.20).name());
			assertEquals("DESPERATION", BossPhases.current(boss, 0.00).name());
		}

		@Test
		@DisplayName("Sylvaris: the shulker phase at 75%, arrows at 25%")
		void sylvaris() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/sylvaris.png");
			assertNotNull(boss);

			assertEquals("SHULKER", BossPhases.next(boss, 1.00).name());
			assertEquals("SHULKER", BossPhases.next(boss, 0.78).name());
			assertEquals("ARROWS", BossPhases.next(boss, 0.75).name());
			assertEquals("ARROWS", BossPhases.next(boss, 0.26).name());
			assertNull(BossPhases.next(boss, 0.25), "arrows is the last phase");

			// The 50% return to the ordinary attack cycle is deliberately not called.
			assertEquals("SHULKER", BossPhases.current(boss, 0.50).name());
		}

		@Test
		@DisplayName("Raphael: memorise at 75%, the bell at 50%, desperation at 15%")
		void raphael() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/onyx.png");
			assertNotNull(boss);

			assertEquals("MEMORISE", BossPhases.next(boss, 1.00).name());
			assertEquals("MEMORISE", BossPhases.next(boss, 0.78).name());
			assertEquals("BELL", BossPhases.next(boss, 0.75).name());
			assertEquals("BELL", BossPhases.next(boss, 0.51).name());
			assertEquals("DESPERATION", BossPhases.next(boss, 0.50).name());
			assertEquals("DESPERATION", BossPhases.next(boss, 0.16).name());
			assertNull(BossPhases.next(boss, 0.15), "desperation is the last phase");

			// The two attack-cycle phases between them change nothing you must do, so the fight reads as
			// still being in the set piece before it.
			assertEquals("MEMORISE", BossPhases.current(boss, 0.60).name());
			assertEquals("BELL", BossPhases.current(boss, 0.30).name());
		}

		@Test
		@DisplayName("Voided Omnipotent: the chase at 85%, the bells at 30%, the Void at 15%")
		void voidedOmnipotent() {
			BossPhases.Boss boss = BossPhases.forName("telos:glyph/bossbar/voided_omnipotent.png");
			assertNotNull(boss);

			assertEquals("CHASE", BossPhases.next(boss, 1.00).name());
			assertEquals("CHASE", BossPhases.next(boss, 0.88).name());
			assertEquals("BELL", BossPhases.next(boss, 0.85).name());
			assertEquals("BELL", BossPhases.next(boss, 0.31).name());
			assertEquals("DESPERATION", BossPhases.next(boss, 0.30).name());
			assertNull(BossPhases.next(boss, 0.15), "desperation is the last phase");

			// 65% is the snakes/pillars/black-holes cycle, which is not called: the fight still reads as
			// being in the chase until the bells begin.
			assertEquals("CHASE", BossPhases.current(boss, 0.50).name());
		}

		@Test
		@DisplayName("the ordinary fights only carry the set piece they share")
		void ordinaryFights() {
			BossPhases.Boss seraphim = BossPhases.forName("telos:glyph/bossbar/seraphim.png");
			assertEquals("QR CODE", BossPhases.next(seraphim, 1.00).name());
			assertNull(BossPhases.next(seraphim, 0.50), "no desperation phase is documented for it");

			BossPhases.Boss ophanim = BossPhases.forName("telos:glyph/bossbar/ophanim.png");
			assertEquals("WALLS", BossPhases.next(ophanim, 0.90).name());
			assertEquals("CLOCK", BossPhases.next(ophanim, 0.61).name());
			assertNull(BossPhases.next(ophanim, 0.60));
		}
	}

	@Nested
	@DisplayName("fights that end above an empty bar")
	class EndsEarly {

		/** The pair that shares an encounter: two bosses, a bar each, both dead at half health. */
		@ParameterizedTest(name = "{0}")
		@CsvSource({"telos:glyph/bossbar/apostle.png", "telos:glyph/bossbar/hierophant.png"})
		@DisplayName("Apostle and Hierophant die at 50%")
		void thePair(String identity) {
			assertEquals(0.50, BossPhases.endsAt(identity));
		}

		@Test
		@DisplayName("every other fight is taken all the way down")
		void everythingElse() {
			assertEquals(0.0, BossPhases.endsAt("telos:glyph/bossbar/cherubim.png"));
			assertEquals(0.0, BossPhases.endsAt("telos:glyph/bossbar/onyx.png"));
			assertEquals(0.0, BossPhases.endsAt("Raphael's Castle 32 0 mythichud:assets/rotmc2/plate.png"));
			assertEquals(0.0, BossPhases.endsAt(""));
		}

		@Test
		@DisplayName("half a bar is a full one when the fight ends at half")
		void rescales() {
			assertEquals(1.00F, BossPhases.remaining(1.00F, 0.50), 0.001F);
			assertEquals(0.50F, BossPhases.remaining(0.75F, 0.50), 0.001F);
			assertEquals(0.00F, BossPhases.remaining(0.50F, 0.50), 0.001F);
			// The whole point: what used to read as half the fight left is the killing blow.
			assertEquals("50%", BossPhases.asPercent(BossPhases.remaining(0.75F, 0.50)));
			assertEquals("0%", BossPhases.asPercent(BossPhases.remaining(0.50F, 0.50)));
		}

		@Test
		@DisplayName("an ordinary fight's bar is left exactly as it came")
		void leavesTheRestAlone() {
			assertEquals(0.62F, BossPhases.remaining(0.62F, 0.0), 0.001F);
			assertEquals(1.00F, BossPhases.remaining(1.00F, 0.0), 0.001F);
		}

		@Test
		@DisplayName("nothing lands outside the bar")
		void staysInRange() {
			// Below the floor the fight is over, however the server got there -- a lerp undershooting, or
			// a boss that hangs about at 40% after its partner has gone.
			assertEquals(0.0F, BossPhases.remaining(0.40F, 0.50), 0.001F);
			assertEquals(0.0F, BossPhases.remaining(-1.0F, 0.0), 0.001F);
			assertEquals(1.0F, BossPhases.remaining(2.0F, 0.50), 0.001F);
			assertEquals(0.0F, BossPhases.remaining(1.0F, 1.0), 0.001F);
		}
	}

	/**
	 * The list the ambush and deathmark calls are held to. These names are the eleven the user named,
	 * read off the pack's own {@code telos:glyph/bossbar/} folder -- so what is really pinned here is
	 * the spelling: the server's leaderboards say "Valerion" and the artwork agrees, but if that ever
	 * came out "vlaerion" the calls would go quiet in a whole dungeon and nothing else would change.
	 */
	@Nested
	@DisplayName("fights the party's own calls are made in")
	class Endgame {

		@ParameterizedTest(name = "{0}")
		@CsvSource({
				"telos:glyph/bossbar/asmodeus.png",
				"telos:glyph/bossbar/seraphim.png",
				"telos:glyph/bossbar/hardmode_seraphim.png",
				"telos:glyph/bossbar/valerion.png",
				"telos:glyph/bossbar/nebula.png",
				"telos:glyph/bossbar/ophanim.png",
				"telos:glyph/bossbar/hardmode_ophanim.png",
				"telos:glyph/bossbar/cherubim.png",
				"telos:glyph/bossbar/sylvaris.png",
				"telos:glyph/bossbar/voided_omnipotent.png",
				"telos:glyph/bossbar/onyx.png",
		})
		@DisplayName("every dungeon boss is called in")
		void theEleven(String identity) {
			assertNotNull(BossPhases.endgameKeyOf(identity), identity);
		}

		/**
		 * The realm bosses, which are the whole reason there is a list. A run through one of these is
		 * over inside a minute, so a title at 65% lands on a fight that is already won.
		 */
		@ParameterizedTest(name = "{0}")
		@CsvSource({
				"telos:glyph/bossbar/lotil.png",
				"telos:glyph/bossbar/defender.png",
				"telos:glyph/bossbar/arctic_colossus.png",
				"telos:glyph/bossbar/cog_sentinel.png",
				"telos:glyph/bossbar/apostle.png",
				"telos:glyph/bossbar/hierophant.png",
				"telos:glyph/bossbar/mithrion.png",
		})
		@DisplayName("everything else is left alone")
		void theRest(String identity) {
			assertNull(BossPhases.endgameKeyOf(identity), identity);
		}

		@Test
		@DisplayName("the near-misses stay apart, since a key is a whole picture name")
		void wholeNamesOnly() {
			// The three "onyx" bars again: only the one drawing Raphael is a dungeon boss, and the other
			// two are Raphael's Castle's champions and an unrelated fight.
			assertEquals("onyx", BossPhases.endgameKeyOf("telos:glyph/bossbar/onyx.png"));
			assertNull(BossPhases.endgameKeyOf("telos:glyph/bossbar/onyx2.png"));
			assertNull(BossPhases.endgameKeyOf("telos:glyph/bossbar/onyx_guardian.png"));

			// And the pair the pack ships both of: Tenebris' boss is the voided one.
			assertEquals("voidedomnipotent", BossPhases.endgameKeyOf("telos:glyph/bossbar/voided_omnipotent.png"));
			assertNull(BossPhases.endgameKeyOf("telos:glyph/bossbar/omnipotent.png"));
		}

		@Test
		@DisplayName("the server's own HUD bar is not a fight")
		void ignoresTheHud() {
			// The HUD arrives as a bar too and its text opens with the area you are standing in, so the
			// words "Seraph's Domain" must not be enough to start calling.
			assertNull(BossPhases.endgameKeyOf("Seraph's Domain 37 4 0 mythichud:assets/rotmc2/plate.png"));
			assertNull(BossPhases.endgameKeyOf("Raphael's Castle"));
			assertNull(BossPhases.endgameKeyOf(""));
		}

		@Test
		@DisplayName("every fight with phases in a dungeon is on the list")
		void coversThePhaseTable() {
			// The two lists are different kinds of thing -- one says what a boss does, the other only
			// which fights are worth planning against -- but nothing with phases worth calling should be
			// a fight these are withheld from.
			for (BossPhases.Boss boss : BossPhases.all()) {
				assertNotNull(BossPhases.endgameKey(java.util.List.of(boss.key())), boss.name());
			}
		}
	}

	@Test
	@DisplayName("every phase is named and they descend")
	void tableIsWellFormed() {
		for (BossPhases.Boss boss : BossPhases.all()) {
			double previous = 1.0;
			for (BossPhases.Phase phase : boss.phases()) {
				assertNotNull(phase.name());
				assertEquals(phase.name().toUpperCase(), phase.name(), "phases are titles, so capitals");
				org.junit.jupiter.api.Assertions.assertTrue(phase.at() < previous,
						boss.name() + " phases must descend");
				org.junit.jupiter.api.Assertions.assertTrue(phase.at() > 0.0 && phase.at() < 1.0);
				previous = phase.at();
			}
		}
	}

	@Test
	void writesTheBarAsAPercentage() {
		assertEquals("63%", BossPhases.asPercent(0.6284));
		assertEquals("100%", BossPhases.asPercent(1.0));
		assertEquals("0%", BossPhases.asPercent(0.0));
	}
}
