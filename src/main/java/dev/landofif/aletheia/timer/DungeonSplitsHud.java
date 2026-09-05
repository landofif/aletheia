package dev.landofif.aletheia.timer;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.CooldownText;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2fStack;

import java.util.List;
import java.util.OptionalInt;

/**
 * The splits: every boss of the dungeon you are in, and what the leaderboard said each one took.
 *
 * <pre>
 * Celestial's Province
 * Asmodeus                3:41
 * Seraphim         7:18  -0:12
 * True Seraph               --
 * </pre>
 *
 * <p><b>Two columns, drawn rather than spelled.</b> The names go against the left edge and the times
 * against the right, worked out from the font's own measurements -- padding a proportional font with
 * spaces lines nothing up, and a split readout whose numbers do not form a column is most of the
 * point thrown away. That is also why this overrides {@link #box} and {@link #draw}: the base class
 * draws one string in one colour, and every row here has its own.
 *
 * <p>The stages still to come are listed, greyed, before you reach them. Knowing that Rustborn has
 * two more after Nebula is worth as much mid-run as knowing what Nebula took.
 */
public final class DungeonSplitsHud extends AletheiaHud {
	public static final DungeonSplitsHud INSTANCE = new DungeonSplitsHud();

	private DungeonSplitsHud() {
	}

	/** Separates a row's name from its time. Never measured or drawn -- both halves are split off first. */
	private static final String COLUMN = "\t";

	/** Space between the two columns, in unscaled pixels. */
	private static final int GAP = 8;

	/** A stage you have not reached. Grey rather than a colour setting: it is absence, not a state. */
	private static final int PENDING_COLOUR = 0xFF808080;

	/** Green for a stage that just beat its own best, matching the run clock's. */
	private static final int BEST_COLOUR = 0xFF55FF55;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "dungeon_splits");
	}

	@Override
	public String label() {
		return "Dungeon splits";
	}

	@Override
	public String text() {
		List<DungeonSplits.Row> rows = DungeonSplits.rows();
		if (rows.isEmpty()) {
			return AletheiaConfig.splitsAlwaysShow ? sample() : null;
		}

		StringBuilder out = new StringBuilder();
		if (AletheiaConfig.splitsShowTitle && !DungeonSplits.dungeon().isEmpty()) {
			out.append(DungeonSplits.dungeon()).append(COLUMN).append('\n');
		}
		for (DungeonSplits.Row row : rows) {
			out.append(row.label()).append(COLUMN).append(value(row)).append('\n');
		}
		return out.substring(0, out.length() - 1);
	}

	/** What one row's right-hand column says. */
	private static String value(DungeonSplits.Row row) {
		if (!row.done()) {
			return AletheiaConfig.splitsPendingText;
		}
		String time = AletheiaConfig.timerTimeFormat == 1
				? CooldownText.asSeconds(row.seconds())
				: CooldownText.asClock(row.seconds());

		OptionalInt delta = row.delta();
		if (!AletheiaConfig.splitsShowDelta || delta.isEmpty() || delta.getAsInt() == 0) {
			return time;
		}
		int behind = delta.getAsInt();
		return time + "  " + (behind > 0 ? "+" : "-") + CooldownText.asClock(Math.abs(behind));
	}

	/**
	 * What the editor shows when no run is under way.
	 *
	 * <p>A real dungeon's rows rather than lorem ipsum, so what you place is the width you will get.
	 */
	private static String sample() {
		DungeonRoutes.Route route = DungeonRoutes.all().get(0);
		StringBuilder out = new StringBuilder();
		if (AletheiaConfig.splitsShowTitle) {
			out.append(route.dungeon()).append(COLUMN).append('\n');
		}
		for (DungeonRoutes.Stage stage : route.stages()) {
			out.append(stage.label()).append(COLUMN).append(AletheiaConfig.splitsPendingText).append('\n');
		}
		return out.substring(0, out.length() - 1);
	}

	@Override
	public int colour() {
		return AletheiaConfig.argb(AletheiaConfig.splitsColour);
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showSplitsHud) {
			return false;
		}
		return DungeonSplits.any() || AletheiaConfig.splitsAlwaysShow;
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showSplitsHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showSplitsHud = value;
	}

	// ------------------------------------------------------------------ Drawing

	@Override
	public Box box(Font font, String text, int screenWidth, int screenHeight) {
		String[][] rows = rows(text);
		float scale = scale();
		return place(Math.round(width(font, rows) * scale),
				Math.round(rows.length * font.lineHeight * scale), screenWidth, screenHeight);
	}

	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, String text, int colour, Box box) {
		String[][] rows = rows(text);
		float scale = scale();
		int width = width(font, rows);

		// The editor draws a switched-off readout faint by handing in a faded colour. Every row here
		// picks its own, so the fade has to be carried across by hand or the editor would show this one
		// at full strength while every other readout dimmed.
		int alpha = colour >>> 24;

		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(box.x(), box.y());
		pose.scale(scale, scale);

		// The title is the one row with nothing in its right-hand column; every stage has at least a
		// dash. So the text says whether it is there, and nothing has to be passed alongside it.
		boolean titled = rows.length > 0 && rows[0][1].isEmpty();
		int current = titled ? DungeonSplits.current() + 1 : DungeonSplits.current();

		// Taken once for the whole readout rather than once a row: it is a copy, and this is a frame.
		List<DungeonSplits.Row> splits = DungeonSplits.rows();

		int y = 0;
		for (int index = 0; index < rows.length; index++) {
			String name = rows[index][0];
			String value = rows[index][1];
			int rowColour = withAlpha(colourOf(splits, index, current, titled), alpha);

			graphics.text(font, Component.literal(name), 0, y, rowColour, AletheiaConfig.titleShadow);
			if (!value.isEmpty()) {
				graphics.text(font, Component.literal(value), width - font.width(value), y, rowColour,
						AletheiaConfig.titleShadow);
			}
			y += font.lineHeight;
		}
		pose.popMatrix();
	}

	/**
	 * The colour of one row.
	 *
	 * <p>Read off the live splits rather than off the text, because the text is only what the row
	 * says: two rows reading {@code 3:41} can be a new best and a bad one.
	 */
	private static int colourOf(List<DungeonSplits.Row> rows, int index, int current, boolean titled) {
		if (titled && index == 0) {
			return AletheiaConfig.argb(AletheiaConfig.splitsColour);
		}
		int row = titled ? index - 1 : index;
		if (row < 0 || row >= rows.size()) {
			return PENDING_COLOUR;
		}

		DungeonSplits.Row split = rows.get(row);
		if (!split.done()) {
			// The one you are fighting stands out from the ones you have not reached.
			return index == current
					? AletheiaConfig.argb(AletheiaConfig.splitsColour)
					: PENDING_COLOUR;
		}
		if (split.best()) {
			return BEST_COLOUR;
		}
		if (split.delta().isPresent() && split.delta().getAsInt() > 0) {
			return AletheiaConfig.argb(AletheiaConfig.splitsBehindColour);
		}
		return AletheiaConfig.argb(AletheiaConfig.splitsColour);
	}

	private static int withAlpha(int colour, int alpha) {
		return (colour & 0x00FFFFFF) | (Math.min(alpha, colour >>> 24) << 24);
	}

	private static int width(Font font, String[][] rows) {
		int width = 0;
		for (String[] row : rows) {
			int value = row[1].isEmpty() ? 0 : GAP + font.width(row[1]);
			width = Math.max(width, font.width(row[0]) + value);
		}
		return width;
	}

	/**
	 * The readout split into rows and columns.
	 *
	 * <p>Kept from the last call the way the stat panel keeps its lines: {@link #box} and {@link #draw}
	 * both ask for this every frame, and the answer changes only when a boss dies.
	 */
	private static String splitFrom;
	private static String[][] split = {{"", ""}};

	private static String[][] rows(String text) {
		if (text == null || text.isEmpty()) {
			return new String[][] {{"", ""}};
		}
		if (!text.equals(splitFrom)) {
			String[] lines = text.split("\n", -1);
			String[][] rows = new String[lines.length][2];
			for (int index = 0; index < lines.length; index++) {
				int tab = lines[index].indexOf(COLUMN);
				rows[index][0] = tab < 0 ? lines[index] : lines[index].substring(0, tab);
				rows[index][1] = tab < 0 ? "" : lines[index].substring(tab + 1);
			}
			split = rows;
			splitFrom = text;
		}
		return split;
	}

	// ------------------------------------------------------------------ Placement

	@Override
	public int anchor() {
		return AletheiaConfig.splitsAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.splitsAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.splitsOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.splitsOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.splitsOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.splitsOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.splitsScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.splitsScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.splitsAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.splitsOffsetX = 4;
		AletheiaConfig.splitsOffsetY = 68;
		AletheiaConfig.splitsScale = 100;
	}
}
