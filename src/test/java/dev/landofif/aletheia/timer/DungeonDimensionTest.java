package dev.landofif.aletheia.timer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ids here are the ones the running game actually reported, read out of the instance's logs on
 * 2026-08-29: {@code realm} for the open world, and {@code dungeon/1} through {@code dungeon/14}
 * plus {@code neo_eden/1..2} for everything instanced. Nothing else has ever been seen.
 */
class DungeonDimensionTest {

	@Nested
	@DisplayName("a numbered room is a dungeon")
	class Numbered {

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings = {
				"telos:dungeon/1",
				"telos:dungeon/2",
				"telos:dungeon/14",
				"telos:neo_eden/1",
				"telos:neo_eden/2",
		})
		void counts(String dimension) {
			assertTrue(DungeonDimension.isDungeon(dimension, List.of()));
		}

		@ParameterizedTest(name = "{0}")
		@ValueSource(strings = {
				"telos:realm",
				"minecraft:overworld",
				"minecraft:the_nether",
		})
		void doesNot(String dimension) {
			assertFalse(DungeonDimension.isDungeon(dimension, List.of()));
		}

		/** The name in front of the number is a slot, not a dungeon, so it is never read as one. */
		@Test
		void doesNotNeedAKnownName() {
			assertTrue(DungeonDimension.isDungeon("telos:something_new/3", List.of()));
		}

		@Test
		void wantsTheNumberOnTheEnd() {
			assertFalse(DungeonDimension.isDungeon("telos:dungeon/two", List.of()));
			assertFalse(DungeonDimension.isDungeon("telos:dungeon", List.of()));
			assertFalse(DungeonDimension.isDungeon("telos:1", List.of()));
		}

		@Test
		void survivesNonsense() {
			assertFalse(DungeonDimension.isDungeon(null, List.of()));
			assertFalse(DungeonDimension.isDungeon("", List.of()));
		}
	}

	@Nested
	@DisplayName("the extra list")
	class Extra {

		@Test
		void addsDimensionsTheShapeMisses() {
			assertTrue(DungeonDimension.isDungeon("telos:catacombs", List.of("catacombs")));
			assertFalse(DungeonDimension.isDungeon("telos:catacombs", List.of()));
		}

		@Test
		void ignoresCaseAndPadding() {
			assertTrue(DungeonDimension.isDungeon("telos:Catacombs", List.of("  CATACOMBS  ")));
		}

		/**
		 * The old default. It matched nothing then and it matches nothing now -- the point of the test
		 * is that a leftover config file carrying it cannot turn anything on or off.
		 */
		@Test
		void aLeftoverDreadwoodChangesNothing() {
			assertFalse(DungeonDimension.isDungeon("telos:realm", List.of("dreadwood")));
			assertTrue(DungeonDimension.isDungeon("telos:dungeon/2", List.of("dreadwood")));
		}

		@Test
		void emptyTermsCannotMatchEverything() {
			assertFalse(DungeonDimension.isDungeon("telos:realm", List.of("", "   ")));
		}
	}

	@Nested
	@DisplayName("paths")
	class Paths {

		@Test
		void dropTheNamespace() {
			assertEquals("dungeon/2", DungeonDimension.pathOf("telos:dungeon/2"));
			assertEquals("dungeon/2", DungeonDimension.pathOf("dungeon/2"));
			assertEquals("", DungeonDimension.pathOf(null));
		}
	}
}
