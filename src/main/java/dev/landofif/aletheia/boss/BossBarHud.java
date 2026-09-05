package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import dev.landofif.aletheia.ui.Colours;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The mod's own boss health bar, drawn where the server's cannot be moved to.
 *
 * <p>It is the same fight -- {@link BossBar} reads the health straight off the bar the server sent --
 * with the two things that bar cannot give you: it goes where you put it, and <b>it is coloured by
 * whether the boss can be hurt</b>. The server's own bar carries a colour field, but the pack draws a
 * picture over it, so the state has to come from the fill above the boss's head; see
 * {@link Vulnerability}.
 *
 * <p>The shape is drawn a row at a time rather than as a sprite, so the ends can be cut into any of
 * {@link Ends} at whatever size the bar is set to, and the outline follows whatever that shape turns
 * out to be -- there is no artwork to redraw when the numbers change.
 *
 * <p><b>A fight can put up more than one bar, and each gets a line of its own</b> -- Apostle and
 * Hierophant are one encounter with a boss each, and the server sends them as two bars on two lines.
 * They are drawn that way here: one bar per boss at the full width, stacked in the order the server
 * stacked them, with the gap setting between the lines. A second boss costs height rather than
 * halving the width, so each bar stays as readable as a lone one and says its own name and its own
 * percentage in full.
 *
 * <p>The stack grows away from whichever corner it is anchored to, like everything else here, so a
 * bar parked against the bottom of the screen stays that far up when the fight puts a second one up.
 *
 * <p><b>The bar is optional.</b> {@link AletheiaConfig#bossBarStyle} can drop the shape and keep the
 * words -- {@code Cherubim  ·  48%}, or the figure on its own -- for anybody who wants the one thing
 * the server's bar cannot tell you precisely without the 182 pixels of artwork that come with it.
 * Everything else still holds: it is the same reading, on the same lines, one per boss, dragged and
 * scaled the same way. What changes is that a readout with no shape is sized by its words like every
 * other line the mod draws, rather than by the width setting.
 */
public final class BossBarHud extends AletheiaHud {
	public static final BossBarHud INSTANCE = new BossBarHud();

	private BossBarHud() {
	}

	/** Between the bar and a line of text sitting outside it. */
	private static final int TEXT_GAP = 2;

	/** What separates the name, the percentage and the state on one line. */
	private static final String SEPARATOR = "  ·  ";

	private static final int TEXT_ABOVE = 0;
	private static final int TEXT_ON = 1;

	/**
	 * The two wordless settings of {@link AletheiaConfig#bossBarStyle} -- the labels on their own, and
	 * the percentage on its own. 0, the default, is the shape with its labels and is not named here
	 * because it is everything else: see {@link #barDrawn}.
	 */
	private static final int STYLE_WORDS = 1;
	private static final int STYLE_PERCENT = 2;

	/**
	 * Whether there is a shape to draw at all, or only the words that would have sat with it.
	 *
	 * <p>Asked that way round on purpose: a style index the settings file does not recognise draws the
	 * bar, and a readout that is unexpectedly a bar is a great deal easier to find than one that is
	 * unexpectedly nothing.
	 */
	private static boolean barDrawn() {
		int style = AletheiaConfig.bossBarStyle;
		return style != STYLE_WORDS && style != STYLE_PERCENT;
	}

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "boss_bar");
	}

	@Override
	public String label() {
		return "Boss bar";
	}

	/**
	 * The line of words on the first bar, which is empty when all three parts are switched off.
	 *
	 * <p>Only the editor and the blank test read this. What is actually drawn is worked out per bar in
	 * {@link #draw}, since a fight with two of them has two names and two percentages, a line each.
	 */
	@Override
	public String text() {
		return words(BossBar.fights().get(0)).full();
	}

	@Override
	public int colour() {
		return AletheiaConfig.bossBarTextByState
				? Vulnerability.colour()
				: AletheiaConfig.argb(AletheiaConfig.bossBarTextColour);
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showBossBar) {
			return false;
		}
		return BossBar.live() || Vulnerability.previewing();
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showBossBar;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showBossBar = value;
	}

	/** A bar with every label switched off is still a bar; its words with no bar under them are not. */
	@Override
	public boolean drawsWithoutText() {
		return barDrawn();
	}

	// ------------------------------------------------------------------ placement

	/**
	 * The bar is the size it is set to be, not the size of its words -- and it is held in the middle
	 * of the screen unless it has been dragged off it, since the corner-and-offset placement the other
	 * readouts use cannot keep anything centred on its own.
	 *
	 * <p>The height is the whole stack, so a fight with two bosses in it is grabbed and dragged as one
	 * thing rather than by whichever line happens to be on top.
	 *
	 * <p>With no bar to draw there is no set width to use, so the readout is measured like every other
	 * line the mod draws: the longest thing it has to say.
	 */
	@Override
	public Box box(Font font, String text, int screenWidth, int screenHeight) {
		float scale = scale();
		int width = Math.round(width(font, text) * scale);
		int height = Math.round(totalHeight(font) * scale);

		Box box = place(width, height, screenWidth, screenHeight);
		return AletheiaConfig.bossBarCentred
				? new Box((screenWidth - width) / 2, box.y(), width, height)
				: box;
	}

	/** Dragging it is how you say you want it somewhere other than the middle. */
	@Override
	public void moveTo(int x, int y, Box box, int screenWidth, int screenHeight) {
		AletheiaConfig.bossBarCentred = false;
		super.moveTo(x, y, box, screenWidth, screenHeight);
	}

	@Override
	public int anchor() {
		return AletheiaConfig.bossBarAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.bossBarAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.bossBarOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.bossBarOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.bossBarOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.bossBarOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.bossBarScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.bossBarScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.bossBarAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.bossBarOffsetX = 4;
		AletheiaConfig.bossBarOffsetY = 12;
		AletheiaConfig.bossBarScale = 100;
		AletheiaConfig.bossBarCentred = true;
	}

	private static int barWidth() {
		return Math.max(8, AletheiaConfig.bossBarWidth);
	}

	/**
	 * How wide the whole readout is before scaling: the bar's set width, or -- with no bar under them --
	 * the longest of the lines it has to say.
	 *
	 * @param fallback what a bar with nothing to say stands in with, which is the editor's own label
	 */
	private static int width(Font font, String fallback) {
		if (barDrawn()) {
			return barWidth();
		}
		int widest = 0;
		for (BossBar.Fight fight : BossBar.fights()) {
			widest = Math.max(widest, font.width(lineFor(fight, fallback)));
		}
		return widest;
	}

	private static int barHeight() {
		return Math.max(2, AletheiaConfig.bossBarHeight);
	}

	/** Between one boss's line and the next. */
	private static int gap() {
		return Math.max(0, AletheiaConfig.bossBarGap);
	}

	/** One boss's line -- the bar, plus its words when they sit outside it -- in unscaled pixels. */
	private static int lineHeight(Font font) {
		if (!barDrawn()) {
			return font.lineHeight;
		}
		return barHeight() + (AletheiaConfig.bossBarTextPlace == TEXT_ON ? 0 : font.lineHeight + TEXT_GAP);
	}

	/** Every line the fight has put up, stacked, with the gap between them. Never fewer than one. */
	private static int totalHeight(Font font) {
		int lines = BossBar.fights().size();
		return lines * lineHeight(font) + (lines - 1) * gap();
	}

	// ------------------------------------------------------------------ drawing

	/**
	 * Draws the fight: one bar, or a stack of them when the fight has more than one boss in it.
	 *
	 * <p>The line handed in is not drawn -- see {@link #text()}. Each bar says its own name and its own
	 * percentage, so the words are worked out alongside the shape they belong to; what the argument is
	 * for is standing in for a readout that has nothing to say, which is how the editor shows one.
	 */
	@Override
	public void draw(GuiGraphicsExtractor graphics, Font font, String text, int colour, Box box) {
		List<BossBar.Fight> fights = BossBar.fights();
		float scale = scale();
		int width = width(font, text);
		int height = barHeight();
		int line = lineHeight(font) + gap();
		int top = barDrawn() && AletheiaConfig.bossBarTextPlace == TEXT_ABOVE ? font.lineHeight + TEXT_GAP : 0;

		// Drawn at the origin and carried into place by the matrix, so the offsets are not scaled twice.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(box.x(), box.y());
		pose.scale(scale, scale);

		for (int index = 0; index < fights.size(); index++) {
			pose.pushMatrix();
			pose.translate(0, index * line);
			one(graphics, font, fights.get(index), text, width, height, top, colour);
			pose.popMatrix();
		}

		pose.popMatrix();
	}

	/** One line of the stack: a bar the full width where there is one, and that boss's own words. */
	private void one(GuiGraphicsExtractor graphics, Font font, BossBar.Fight fight, String fallback,
			int width, int height, int top, int colour) {
		if (barDrawn()) {
			bar(graphics, top, width, height, fight.progress());
		}

		String line = lineFor(fight, fallback);
		if (line.isBlank()) {
			return;
		}
		graphics.centeredText(font, Component.literal(line), width / 2, textY(font, height, top), colour);
	}

	/** Where a boss's words sit within its own line, which with no bar to dodge is the top of it. */
	private static int textY(Font font, int height, int top) {
		if (!barDrawn()) {
			return 0;
		}
		return switch (AletheiaConfig.bossBarTextPlace) {
			case TEXT_ABOVE -> 0;
			case TEXT_ON -> top + (height - font.lineHeight) / 2 + 1;
			default -> top + height + TEXT_GAP;
		};
	}

	/** The shape, its backing, its fill and its outline -- one row of pixels at a time. */
	private void bar(GuiGraphicsExtractor graphics, int top, int width, int height, float progress) {
		Ends ends = Ends.of(AletheiaConfig.bossBarEdges);
		int corner = Mth.clamp(AletheiaConfig.bossBarCorner, 1, Math.min(width / 2, Math.max(1, height)));
		int backing = backingColour();
		int fill = fillColour();
		int border = AletheiaConfig.bossBarBorder ? borderColour() : 0;

		// A single upright edge rather than one per row: the fill is how far along the bar the health
		// is, so a shaped end must not tilt it.
		int edge = Math.round(width * progress);

		for (int row = 0; row < height; row++) {
			Span span = ends.spanOf(row, height, width, corner);
			if (span.empty()) {
				continue;
			}
			int y = top + row;

			if (backing != 0) {
				graphics.fill(span.left(), y, span.right(), y + 1, backing);
			}
			int filled = Mth.clamp(edge, span.left(), span.right());
			if (filled > span.left()) {
				graphics.fill(span.left(), y, filled, y + 1, fill);
			}
			if (border != 0) {
				outline(graphics, y, span,
						row == 0 ? Span.EMPTY : ends.spanOf(row - 1, height, width, corner),
						row == height - 1 ? Span.EMPTY : ends.spanOf(row + 1, height, width, corner),
						border);
			}
		}
	}

	/**
	 * The border pixels of one row: its own two ends, plus whatever of it the rows above and below do
	 * not cover. Working it out from the neighbours is what lets one piece of code outline every shape
	 * -- a curve and a slant are just rows that do not line up.
	 */
	private void outline(GuiGraphicsExtractor graphics, int y, Span span, Span above, Span below, int colour) {
		graphics.fill(span.left(), y, span.left() + 1, y + 1, colour);
		graphics.fill(span.right() - 1, y, span.right(), y + 1, colour);
		uncovered(graphics, y, span, above, colour);
		uncovered(graphics, y, span, below, colour);
	}

	/** Draws the part of a row that sticks out past its neighbour, which is where the shape turns. */
	private void uncovered(GuiGraphicsExtractor graphics, int y, Span span, Span other, int colour) {
		if (other.empty()) {
			graphics.fill(span.left(), y, span.right(), y + 1, colour);
			return;
		}
		if (other.left() > span.left()) {
			graphics.fill(span.left(), y, Math.min(span.right(), other.left()), y + 1, colour);
		}
		if (other.right() < span.right()) {
			graphics.fill(Math.max(span.left(), other.right()), y, span.right(), y + 1, colour);
		}
	}

	// ------------------------------------------------------------------ the words

	/**
	 * The three things a bar can say about itself, kept apart so any of them can be dropped again.
	 *
	 * <p>Each part is empty when it is switched off or has nothing to say, and they read in this order:
	 * {@code Hierophant · 48% · HALF DAMAGE}.
	 */
	private record Words(String name, String percent, String state) {

		String full() {
			return join(name, percent, state);
		}

		private static String join(String... parts) {
			StringBuilder line = new StringBuilder();
			for (String part : parts) {
				if (part.isEmpty()) {
					continue;
				}
				if (!line.isEmpty()) {
					line.append(SEPARATOR);
				}
				line.append(part);
			}
			return line.toString();
		}
	}

	private static Words words(BossBar.Fight fight) {
		String percent = BossPhases.asPercent(fight.progress());
		if (AletheiaConfig.bossBarStyle == STYLE_PERCENT) {
			// The figure and nothing else: the point of asking for it is not having to switch the other
			// two off, and not having them come back when the style is switched away and back again.
			return new Words("", percent, "");
		}

		String name = AletheiaConfig.bossBarShowName ? fight.name().trim() : "";
		String state = AletheiaConfig.bossBarShowState ? Vulnerability.statusLine() : null;
		return new Words(name, AletheiaConfig.bossBarShowPercent ? percent : "", state == null ? "" : state);
	}

	/**
	 * What one boss's line says, or the stand-in handed in when it would say nothing.
	 *
	 * <p>Only the wordless styles fall back. A bar with every label switched off has itself to show and
	 * wants no words over it; a readout that is <i>only</i> words and has none is invisible, which in
	 * the editor means one you cannot find to switch back on.
	 */
	private static String lineFor(BossBar.Fight fight, String fallback) {
		String line = words(fight).full();
		return !line.isBlank() || barDrawn() ? line : fallback;
	}

	// ------------------------------------------------------------------ colours

	/** The fill, which is the state when the bar is set to say so and the healthy colour when not. */
	private static int fillColour() {
		if (!AletheiaConfig.bossBarColourByState) {
			return opaque(AletheiaConfig.bossBarVulnerableColour, DEFAULT_VULNERABLE);
		}
		return switch (BossBar.state()) {
			case INVULNERABLE -> opaque(AletheiaConfig.bossBarInvulnerableColour, DEFAULT_INVULNERABLE);
			case HALF -> opaque(AletheiaConfig.bossBarHalfColour, DEFAULT_HALF);
			case VULNERABLE -> opaque(AletheiaConfig.bossBarVulnerableColour, DEFAULT_VULNERABLE);
		};
	}

	/** The empty part: black at the set darkness, so the fill reads against the world behind it. */
	private static int backingColour() {
		int alpha = Math.round(Mth.clamp(AletheiaConfig.bossBarBackingAlpha, 0, 100) * 255 / 100.0F);
		return alpha == 0 ? 0 : (alpha << 24);
	}

	/** A dimmed cast of the fill, so the outline belongs to the bar rather than being drawn on it. */
	private static int borderColour() {
		int fill = fillColour();
		int red = ((fill >> 16) & 0xFF) / 3;
		int green = ((fill >> 8) & 0xFF) / 3;
		int blue = (fill & 0xFF) / 3;
		return 0xFF000000 | (red << 16) | (green << 8) | blue;
	}

	private static final int DEFAULT_VULNERABLE = 0x5AD022;
	private static final int DEFAULT_HALF = 0xB084E8;
	private static final int DEFAULT_INVULNERABLE = 0x1757A7;

	/** A setting read as an opaque colour, falling back rather than drawing nothing when it is junk. */
	private static int opaque(@Nullable String setting, int fallback) {
		return 0xFF000000 | Colours.parse(setting, fallback);
	}

	// ------------------------------------------------------------------ the shapes

	/** Where one row of the bar starts and stops, as a half-open range of columns. */
	private record Span(int left, int right) {
		static final Span EMPTY = new Span(0, 0);

		boolean empty() {
			return right <= left;
		}
	}

	/**
	 * The shapes an end can be cut into. Each one answers the same question -- how far in this row
	 * starts and stops -- and everything else about the bar follows from that.
	 */
	private enum Ends {
		/** The plain rectangle. */
		SQUARE,
		/** A quarter circle taken out of each corner. */
		ROUNDED,
		/** The corners cut off at forty-five degrees. */
		BEVELLED,
		/** Both ends leaning the same way, for a parallelogram. */
		SLANTED,
		/** A wedge cut into the middle of each end. */
		NOTCHED;

		static Ends of(int index) {
			Ends[] all = values();
			return index >= 0 && index < all.length ? all[index] : SQUARE;
		}

		Span spanOf(int row, int height, int width, int corner) {
			return switch (this) {
				case SQUARE -> new Span(0, width);
				case ROUNDED -> symmetric(round(row, height, corner), width);
				case BEVELLED -> symmetric(Math.max(0, corner - fromEnd(row, height)), width);
				case NOTCHED -> symmetric(notch(row, height, corner), width);
				case SLANTED -> {
					// Leans without narrowing: what comes off one end is put back on the other.
					int lean = height <= 1 ? 0 : Math.round(corner * (height - 1 - row) / (float) (height - 1));
					yield new Span(lean, width - (corner - lean));
				}
			};
		}

		private static Span symmetric(int inset, int width) {
			return new Span(inset, width - inset);
		}

		/** Rows from whichever end of the bar is nearer, so a shape need only be described once. */
		private static int fromEnd(int row, int height) {
			return Math.min(row, height - 1 - row);
		}

		/** A circle's edge, so the corner curves instead of stepping evenly like the bevel. */
		private static int round(int row, int height, int corner) {
			int depth = fromEnd(row, height);
			if (depth >= corner) {
				return 0;
			}
			int rise = corner - depth;
			return Math.max(0, corner - (int) Math.round(Math.sqrt((double) corner * corner - (double) rise * rise)));
		}

		/** Deepest at the middle of the end and nothing at its corners, which is the wedge. */
		private static int notch(int row, int height, int corner) {
			if (height <= 1) {
				return 0;
			}
			float middle = (height - 1) / 2.0F;
			float away = Math.abs(row - middle) / middle;
			return Math.max(0, Math.round(corner * (1.0F - away)));
		}
	}
}
