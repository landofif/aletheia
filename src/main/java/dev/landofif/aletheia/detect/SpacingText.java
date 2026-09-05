package dev.landofif.aletheia.detect;

/**
 * Writes blank space of an exact width, using the negative-space font a server ships for HUD layout.
 *
 * <p>Such a font is a plain advance table with one codepoint per pixel: on Telos Realms
 * ({@code mythichud:spaces}) {@code U+E000 + n} moves the pen right by {@code n} pixels and
 * {@code U+F000 + n} moves it left, for {@code n} up to 1280. Anything wider is spelled with several
 * glyphs.
 *
 * <p>Kept free of Minecraft types so the arithmetic can be tested on its own; the caller measures
 * the width. Used by {@link dev.landofif.aletheia.serverhud.ServerHudFilter} to swap a hidden
 * part of the HUD for the space it used to take up, leaving the rest of the layout untouched.
 */
public final class SpacingText {
	private SpacingText() {
	}

	/** Largest advance one glyph carries, in GUI pixels. */
	public static final int MAX_ADVANCE = 1280;

	private static final int RIGHT_BASE = 0xE000;
	private static final int LEFT_BASE = 0xF000;

	/**
	 * @param pixels how far the pen should move; negative moves left
	 * @return glyphs whose advances sum to exactly {@code pixels}, or an empty string for zero
	 */
	public static String advance(int pixels) {
		if (pixels == 0) {
			return "";
		}

		StringBuilder out = new StringBuilder();
		int remaining = pixels;
		while (remaining > 0) {
			int step = Math.min(remaining, MAX_ADVANCE);
			out.append((char) (RIGHT_BASE + step));
			remaining -= step;
		}
		while (remaining < 0) {
			int step = Math.min(-remaining, MAX_ADVANCE);
			out.append((char) (LEFT_BASE + step));
			remaining += step;
		}
		return out.toString();
	}
}
