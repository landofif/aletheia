package dev.landofif.aletheia.stats;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

/**
 * The stat readout.
 *
 * <p>The one element here that is more than one line, since a stat panel stacked in a corner is what
 * was asked for and a row of them is the alternative rather than the shape. Both are drawn from the
 * same text: {@link PlayerStats#statusLine} joins the stats with a newline or with the separator, and
 * everything below simply honours whichever it gets.
 *
 * <p>Lines are drawn away from the corner the readout is anchored to, and <b>right-aligned against a
 * right-hand corner</b>, so a column of numbers lines up on the edge it is parked against instead of
 * fraying out into the middle of the screen.
 */
public final class PlayerStatsHud extends AletheiaHud {
	public static final PlayerStatsHud INSTANCE = new PlayerStatsHud();

	private PlayerStatsHud() {
	}

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "player_stats");
	}

	@Override
	public String label() {
		return "Player stats";
	}

	@Override
	public String text() {
		return PlayerStats.statusLine();
	}

	@Override
	public int colour() {
		return AletheiaConfig.argb(AletheiaConfig.statsColour);
	}

	@Override
	public boolean shownInGame() {
		return AletheiaConfig.enabled && AletheiaConfig.showStatsHud && PlayerStats.has();
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showStatsHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showStatsHud = value;
	}

	/** Every line, at the width of the longest. */
	@Override
	public Box box(Font font, String text, int screenWidth, int screenHeight) {
		float scale = scale();
		String[] lines = lines(text);

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}
		return place(Math.round(width * scale), Math.round(lines.length * font.lineHeight * scale),
				screenWidth, screenHeight);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, String text, int colour, Box box) {
		float scale = scale();
		String[] lines = lines(text);

		// Measured again rather than taken off the box, because the box has the scale worked into it
		// and the lines are being placed inside the matrix, before that scale applies.
		int width = 0;
		for (String line : lines) {
			width = Math.max(width, font.width(line));
		}

		boolean right = anchor() == ANCHOR_TOP_RIGHT || anchor() == ANCHOR_BOTTOM_RIGHT;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(box.x(), box.y());
		pose.scale(scale, scale);
		int y = 0;
		for (String line : lines) {
			int x = right ? width - font.width(line) : 0;
			graphics.text(font, Component.literal(line), x, y, colour, AletheiaConfig.titleShadow);
			y += font.lineHeight;
		}
		pose.popMatrix();
	}

	/**
	 * The readout split into its lines.
	 *
	 * <p>Kept from the last call, because both {@link #box} and {@link #draw} ask for it and both are
	 * called every frame -- so the same string was being split twice a frame for an answer that changes
	 * twice a second. Compared by identity, which is exact here: {@link PlayerStats#statusLine} hands
	 * back the same instance until the readings or the settings behind them change.
	 */
	private static String splitFrom;
	private static String[] split = {""};

	private static String[] lines(String text) {
		if (text == null || text.isEmpty()) {
			return new String[] {""};
		}
		if (text != splitFrom) {
			split = text.split("\n", -1);
			splitFrom = text;
		}
		return split;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.statsAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.statsAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.statsOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.statsOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.statsOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.statsOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.statsScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.statsScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.statsAnchor = ANCHOR_TOP_RIGHT;
		AletheiaConfig.statsOffsetX = 4;
		AletheiaConfig.statsOffsetY = 4;
		AletheiaConfig.statsScale = 100;
	}
}
