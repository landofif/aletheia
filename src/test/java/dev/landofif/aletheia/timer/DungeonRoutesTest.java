package dev.landofif.aletheia.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The orders here were read off the running game: Melinoe's {@code Boss detected: X in area: Y} lines
 * and the server's own {@code ☠ <dungeon> ☠ ... Defeated <boss> in <time>} leaderboards, across every
 * log the instance had on 2026-08-29. If a dungeon is re-ordered this is the file to fix first -- a
 * run that never reaches its last stage never records a time.
 */
class DungeonRoutesTest {

	@Nested
	@DisplayName("finding the dungeon")
	class Finding {

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings = {
				"Celestial's Province",
				"celestials province",
				"CELESTIAL'S PROVINCE",
				// A curly apostrophe, which is what a resource-pack font is liable to hand back.
				"Celestial’s Province",
		})
		void ignoresCaseAndPunctuation(String area) {
			DungeonRoutes.Route route = DungeonRoutes.forDungeon(area);
			assertNotNull(route);
			assertEquals("Celestial's Province", route.dungeon());
		}

		@Test
		void hasNothingForADungeonItWasNotToldAbout() {
			// Not a mistake -- these run and time fine, they just have no list of what is still to come.
			assertNull(DungeonRoutes.forDungeon("Dreadwood Thicket"));
			// Deliberately absent: the number of Dark Champions before Raphael is not fixed. Runs in the
			// logs show two, one and none, so any table here would be a guess that stops the clock early
			// or never lets it finish.
			assertNull(DungeonRoutes.forDungeon("Raphael's Castle"));
			assertNull(DungeonRoutes.forDungeon(""));
			assertNull(DungeonRoutes.forDungeon(null));
		}

		/** The ribbon names the area, so the realm's biomes reach this too and must find nothing. */
		@Test
		void findsNothingForARealmBiome() {
			assertNull(DungeonRoutes.forDungeon("Permafrost"));
			assertNull(DungeonRoutes.forDungeon("The Nexus"));
		}
	}

	@Nested
	@DisplayName("the order of the stages")
	class Order {

		@ParameterizedTest(name = "{0} stage {1} is {2}")
		@CsvSource(delimiter = '|', value = {
				"Celestial's Province | 0 | Asmodeus",
				"Celestial's Province | 1 | Seraphim",
				"Celestial's Province | 2 | True Seraph",
				"Rustborn Kingdom     | 0 | Valerion",
				"Rustborn Kingdom     | 1 | Nebula",
				"Rustborn Kingdom     | 1 | Mithrion",
				"Rustborn Kingdom     | 2 | Ophanim",
				"Rustborn Kingdom     | 3 | True Ophan",
				"Neo Eden             | 0 | Apostle",
				"Neo Eden             | 0 | Hierophant",
				"Neo Eden             | 1 | Cherubim",
		})
		void matchesTheServersOwnOrder(String dungeon, int index, String boss) {
			DungeonRoutes.Route route = DungeonRoutes.forDungeon(dungeon.trim());
			assertNotNull(route);
			assertEquals(index, route.indexOf(boss));
		}

		/**
		 * Mithrion is fought alongside Nebula and the server has never announced its defeat -- 1027
		 * mentions in the logs and not one clear line. The row reads for both and answers to either, so
		 * the day Telos starts announcing it nothing here has to change.
		 */
		@Test
		void namesBothOfTheBossesFoughtTogether() {
			DungeonRoutes.Route rustborn = DungeonRoutes.forDungeon("Rustborn Kingdom");
			assertNotNull(rustborn);
			assertEquals("Mithrion & Nebula", rustborn.stages().get(1).label());
			assertEquals("Nebula", rustborn.stages().get(1).boss());
			assertTrue(rustborn.stages().get(1).matches("Mithrion"));
		}

		/**
		 * Neo Eden sends both of its first two clear lines in the same second, with the same time. The
		 * pair is one row, and {@code DungeonSplits} fills it once -- the second line finds the row
		 * already done and is dropped.
		 */
		@Test
		void treatsAPairAnnouncedTogetherAsOneStage() {
			DungeonRoutes.Route neoEden = DungeonRoutes.forDungeon("Neo Eden");
			assertNotNull(neoEden);
			assertEquals(2, neoEden.stages().size());
			assertTrue(neoEden.stages().get(0).matches("Apostle"));
			assertTrue(neoEden.stages().get(0).matches("Hierophant"));
			assertEquals("Cherubim", neoEden.last().boss());
		}

		/** A name that appears twice in a route fills its rows in order, not the first one twice. */
		@Test
		void findsTheNextRowForARepeatedBoss() {
			DungeonRoutes.Route province = DungeonRoutes.forDungeon("Celestial's Province");
			assertEquals(1, province.indexOf("Seraphim", 0));
			assertEquals(-1, province.indexOf("Seraphim", 2));
		}

		/** The stage that ends the run, and so the only one that records the dungeon's own time. */
		@Test
		void endsOnTheTrueBoss() {
			assertEquals("True Seraph", DungeonRoutes.forDungeon("Celestial's Province").last().boss());
			assertEquals("True Ophan", DungeonRoutes.forDungeon("Rustborn Kingdom").last().boss());
		}

		/**
		 * The pair that would break a {@code contains} match: Seraphim is a substring of nothing, but
		 * "True Seraph" shares its opening with "True Seraphim", the name people use for it. A clear is
		 * matched whole, so neither can be mistaken for the other.
		 */
		@Test
		void doesNotConfuseABossWithItsTrueForm() {
			DungeonRoutes.Route province = DungeonRoutes.forDungeon("Celestial's Province");
			assertEquals(1, province.indexOf("Seraphim"));
			assertEquals(2, province.indexOf("True Seraph"));
			assertEquals(-1, province.indexOf("Seraph"));
		}

		@Test
		void ignoresABossFromSomewhereElse() {
			assertEquals(-1, DungeonRoutes.forDungeon("Rustborn Kingdom").indexOf("Asmodeus"));
			assertEquals(-1, DungeonRoutes.forDungeon("Celestial's Province").indexOf("Chungus"));
		}
	}

	@Nested
	@DisplayName("every route")
	class Shape {

		@Test
		void isNamedAndOrdered() {
			for (DungeonRoutes.Route route : DungeonRoutes.all()) {
				assertTrue(!route.dungeon().isBlank(), "a route with no dungeon");
				assertTrue(!route.stages().isEmpty(), route.dungeon() + " has no stages");
				for (DungeonRoutes.Stage stage : route.stages()) {
					assertTrue(!stage.label().isBlank(), route.dungeon() + " has an unlabelled stage");
					assertTrue(!stage.bosses().isEmpty(), route.dungeon() + " has a stage with no boss to match");
					for (String boss : stage.bosses()) {
						assertTrue(stage.matches(boss), route.dungeon() + " cannot match its own " + boss);
						assertEquals(route.stages().indexOf(stage), route.indexOf(boss),
								route.dungeon() + " has an earlier stage also answering to " + boss);
					}
				}
			}
		}
	}
}
