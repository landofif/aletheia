package dev.landofif.aletheia.boss;

/**
 * What the colour of a mob's health bar says the mob is doing with your damage.
 *
 * <p>Pure arithmetic on a colour, with no game types anywhere near it, so the rule that separates the
 * three can be tested directly -- which matters more here than anywhere else in the mod, since the
 * whole readout is one comparison of three numbers and getting it wrong tells you to stop attacking a
 * boss you could have been killing.
 */
public enum FillState {
	/** The green-to-red health ramp: everything lands. */
	VULNERABLE,
	/** Light purple: it still takes damage, but only half of it. */
	HALF,
	/** Blue: nothing lands at all. */
	INVULNERABLE;

	/** No RGB value can equal this, so an unset exact colour matches nothing rather than everything. */
	public static final int NO_EXACT_COLOUR = Integer.MIN_VALUE;

	/**
	 * How far ahead of green blue has to be for a fill to be off the health ramp at all. Telos's blue
	 * clears it by a mile ({@code #1757A7} is 167 against 87); the margin is there to keep a grey or a
	 * white -- where all three channels are level -- from drifting over the line.
	 */
	static final int BLUE_MARGIN = 32;

	/**
	 * How far ahead of green red has to be to call a cool colour purple rather than blue. Slacker than
	 * {@link #BLUE_MARGIN} on purpose: a pale lavender carries plenty of green, so the two run closest
	 * there, while the blue has its red the wrong side of green entirely and so cannot be caught by
	 * loosening this.
	 */
	static final int PURPLE_MARGIN = 12;

	/**
	 * The state a fill colour stands for.
	 *
	 * <p><b>The healthy colour is a gradient</b>, which is what makes the loose test worth having. The
	 * bar shades from green through yellow to red as the mob's health drops -- {@code #5AD123} at full,
	 * {@code #5EC821} at 96%, {@code #62C120} at 92% -- so an exact colour can only ever match one
	 * point on that ramp, and nothing says the server does not shade the other two the same way.
	 *
	 * <p>So the loose test sorts by hue instead, in two steps. Nowhere on a green-to-red ramp is blue
	 * anywhere near the front, so <b>blue clearly beating green</b> is what lifts a fill off that ramp;
	 * then <b>red</b> splits what is left. Purple is red and blue together, so its red sits well above
	 * its green ({@code #B084E8} is 176 against 132), where the invulnerable blue has almost none
	 * ({@code #1757A7} is 23 against 87, the wrong way round by a mile). Both steps are a hue rather
	 * than a shade, so they hold however light or dark the server draws either one.
	 *
	 * @param colour            the fill's colour as RGB
	 * @param exactHalf         the colour that is known to mean half damage, or {@link #NO_EXACT_COLOUR}
	 * @param exactInvulnerable the colour that is known to mean invulnerable
	 * @param loose             whether to fall back on the hue test when neither exact colour matches
	 */
	public static FillState of(int colour, int exactHalf, int exactInvulnerable, boolean loose) {
		if (colour == exactHalf) {
			return HALF;
		}
		if (colour == exactInvulnerable) {
			return INVULNERABLE;
		}
		if (!loose) {
			return VULNERABLE;
		}

		int red = (colour >> 16) & 0xFF;
		int green = (colour >> 8) & 0xFF;
		int blue = colour & 0xFF;
		if (blue <= green + BLUE_MARGIN) {
			// Somewhere on the health ramp, or a grey where the three channels are level.
			return VULNERABLE;
		}
		return red > green + PURPLE_MARGIN ? HALF : INVULNERABLE;
	}
}
