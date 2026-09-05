package dev.landofif.aletheia.boss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The one comparison the whole invulnerability readout rests on.
 *
 * <p>Worth pinning down harder than most of this: calling purple blue tells you to stop attacking a
 * boss you could have been killing, which is the exact mistake this used to make, and calling blue
 * green tells you to burn a rotation into something that cannot be hurt.
 */
class FillStateTest {

	/** As the mod ships: the blue is pinned, the purple is left to the hue test. */
	private static final int BLUE = 0x1757A7;
	private static final int NONE = FillState.NO_EXACT_COLOUR;

	private static FillState loose(int colour) {
		return FillState.of(colour, NONE, BLUE, true);
	}

	@Nested
	@DisplayName("the health ramp")
	class Healthy {

		/** The shades the bar really passes through as a mob is killed, read off the game. */
		@ParameterizedTest(name = "{0}")
		@CsvSource({"0x5AD022", "0x5AD123", "0x5EC821", "0x62C120"})
		@DisplayName("every shade of the green-to-red ramp is hittable")
		void greenIsHittable(int colour) {
			assertEquals(FillState.VULNERABLE, loose(colour));
		}

		@Test
		@DisplayName("the red end of the ramp is hittable too")
		void redIsHittable() {
			assertEquals(FillState.VULNERABLE, loose(0xD02222));
			assertEquals(FillState.VULNERABLE, loose(0xD0D022));
		}

		@Test
		@DisplayName("a grey, where no channel wins, is not a state")
		void greyIsHittable() {
			assertEquals(FillState.VULNERABLE, loose(0xFFFFFF));
			assertEquals(FillState.VULNERABLE, loose(0x808080));
			assertEquals(FillState.VULNERABLE, loose(0x000000));
		}
	}

	@Nested
	@DisplayName("blue means untouchable")
	class Blue {

		@Test
		@DisplayName("the colour Telos uses")
		void telosBlue() {
			assertEquals(FillState.INVULNERABLE, loose(BLUE));
		}

		/** The point of the loose test: nothing says the server does not shade the blue as well. */
		@ParameterizedTest(name = "{0}")
		@CsvSource({"0x1757A7", "0x1B5FB2", "0x2166BE", "0x55AAFF", "0x0A2E5C"})
		@DisplayName("any shade of it counts, not just the one pinned")
		void shadesOfBlue(int colour) {
			assertEquals(FillState.INVULNERABLE, loose(colour));
		}
	}

	@Nested
	@DisplayName("purple means half damage")
	class Purple {

		/**
		 * The state that used to read as invulnerable. Every one of these clears the blue test -- blue
		 * beats both other channels -- so only the red step tells them apart from the real blue.
		 */
		@ParameterizedTest(name = "{0}")
		@CsvSource({"0xB084E8", "0xAA00FF", "0xC8A2E8", "0xD8C0F0", "0x9B59D0", "0xE0D0F8"})
		@DisplayName("light purple is its own state, not invulnerable")
		void purpleIsHalf(int colour) {
			assertEquals(FillState.HALF, loose(colour));
		}

		@Test
		@DisplayName("a pinned purple is taken exactly, whatever the hue test would say")
		void exactWins() {
			assertEquals(FillState.HALF, FillState.of(0x123456, 0x123456, BLUE, false));
		}
	}

	@Nested
	@DisplayName("with the loose test switched off")
	class Exact {

		@Test
		@DisplayName("only the pinned colours count")
		void onlyExact() {
			assertEquals(FillState.INVULNERABLE, FillState.of(BLUE, NONE, BLUE, false));
			assertEquals(FillState.VULNERABLE, FillState.of(0x1B5FB2, NONE, BLUE, false));
			assertEquals(FillState.VULNERABLE, FillState.of(0xB084E8, NONE, BLUE, false));
		}

		@Test
		@DisplayName("an unset half colour matches nothing rather than everything")
		void unsetMatchesNothing() {
			assertEquals(FillState.VULNERABLE, FillState.of(0x000000, NONE, BLUE, false));
			assertEquals(FillState.VULNERABLE, FillState.of(FillState.NO_EXACT_COLOUR + 1, NONE, BLUE, false));
		}
	}
}
