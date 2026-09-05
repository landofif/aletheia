package dev.landofif.aletheia.boss;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the bars are called, as against what their files are called.
 *
 * <p>Every one of the pack's 65 bar glyphs was read off its own artwork on 2026-08-22. These are the
 * three that do not match the file they live in -- if a pack update adds a fourth, this is where it
 * goes.
 */
class BossNamesTest {

	@ParameterizedTest(name = "{0}.png is really {1}")
	@CsvSource(delimiter = '|', value = {
			// The art says one thing and the file says another.
			"onyx_guardian  | Orion and Osiris",
			"onyx2          | Onyx",
			// And the ordinary case: the file name is the name, tidied up.
			"arctic_colossus| Arctic Colossus",
			"cog_sentinel   | Cog Sentinel",
			"lotil          | Lotil",
	})
	@DisplayName("a bar is named by its art, not by its file name")
	void namesThePicture(String picture, String expected) {
		assertEquals(expected, BossBars.nameOfPicture(picture));
	}

	@Test
	@DisplayName("Raphael's bar is named by the phase table, which knows the file is lying")
	void raphaelComesFromThePhaseTable() {
		// onyx.png draws "RAPHAEL / THE SANGUINE LORD". It is not in the name table because the fight
		// is in BossPhases, which displayName asks first -- and which would otherwise call it Onyx.
		assertEquals("Raphael", BossPhases.forName("telos:glyph/bossbar/onyx.png").name());
		assertEquals("Onyx", BossBars.nameOfPicture("onyx2"));
	}
}
