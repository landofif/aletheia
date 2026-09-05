package dev.landofif.aletheia.hud;

import dev.landofif.aletheia.config.AletheiaConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

/**
 * A readout that can be dragged and resized in {@link HudEditorScreen}.
 *
 * <p>Placement stays in the same anchor-plus-offset terms the settings sliders use: a corner of the
 * screen, and a distance in from it. Dragging just writes those numbers, picking whichever corner
 * the readout was dropped nearest, so the two ways of moving it agree and a readout parked against
 * an edge stays that far in when the window is resized.
 *
 * <p>Offsets are in the scaled GUI pixels everything else here is measured in, not physical ones,
 * so they hold up across GUI Scale settings.
 */
public abstract class AletheiaHud {
	public static final int ANCHOR_TOP_LEFT = 0;
	public static final int ANCHOR_TOP_RIGHT = 1;
	public static final int ANCHOR_BOTTOM_LEFT = 2;
	public static final int ANCHOR_BOTTOM_RIGHT = 3;

	public static final int MIN_SCALE = 25;
	public static final int MAX_SCALE = 400;

	/**
	 * Ceiling for a dragged offset, matching the sliders' range so neither can be pushed somewhere
	 * the other cannot express. Because dragging anchors to the nearest corner, an offset never has
	 * to reach further than half the screen, which this covers at any sane GUI scale.
	 */
	public static final int MAX_OFFSET = 1000;

	/** Where a readout sits, in scaled GUI pixels. */
	public record Box(int x, int y, int width, int height) {
		public boolean contains(double pointX, double pointY) {
			return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
		}
	}

	/** Distinguishes this element's Fabric HUD layer from every other mod's. */
	public abstract Identifier id();

	/** Names the element while it is being dragged. */
	public abstract String label();

	/**
	 * The live readout, or {@code null} when there is nothing to show right now -- in which case the
	 * editor stands the {@link #label()} in its place, so it can still be positioned.
	 */
	@Nullable
	public abstract String text();

	public abstract int colour();

	/** Whether the settings have this switched on at all. */
	public abstract boolean shownInGame();

	/**
	 * The readout's own switch, apart from whether it has anything to read right now.
	 *
	 * <p>{@link #shownInGame()} answers "is this on screen", which is the switch <i>and</i> a live
	 * reading to put in it. The editor needs the switch on its own: a readout nobody is wearing the
	 * boots for is still one you should be able to see, place and turn off while you are standing in
	 * the editor rather than hunting for its tick box in the settings.
	 */
	public abstract boolean enabled();

	public abstract void enabled(boolean value);

	/**
	 * Whether this element still has something to draw when its text is empty -- true of the boss bar,
	 * which is a shape first and a label second, and false of the plain readouts, which are nothing
	 * without their words.
	 */
	public boolean drawsWithoutText() {
		return false;
	}

	public abstract int anchor();

	public abstract void anchor(int value);

	public abstract int offsetX();

	public abstract void offsetX(int value);

	public abstract int offsetY();

	public abstract void offsetY(int value);

	public abstract int scalePercent();

	public abstract void scalePercent(int value);

	/** Back to where the settings put it out of the box. */
	public abstract void resetPlacement();

	public final float scale() {
		return Mth.clamp(scalePercent(), MIN_SCALE, MAX_SCALE) / 100.0F;
	}

	public final void resize(int deltaPercent) {
		scalePercent(Mth.clamp(scalePercent() + deltaPercent, MIN_SCALE, MAX_SCALE));
	}

	/**
	 * Works the anchor, offsets and scale up into the box the text will occupy.
	 *
	 * <p>Overridden by anything whose size is not its text -- the boss bar is the width it is set to
	 * be, label or no label -- which is why {@link #place} carries the anchor arithmetic separately.
	 */
	public Box box(Font font, String text, int screenWidth, int screenHeight) {
		float scale = scale();
		return place(Math.round(font.width(text) * scale), Math.round(font.lineHeight * scale),
				screenWidth, screenHeight);
	}

	/** Where a box of this size lands, given the corner and the offsets in from it. */
	protected final Box place(int width, int height, int screenWidth, int screenHeight) {
		int x = isRight() ? screenWidth - width - offsetX() : offsetX();
		int y = isBottom() ? screenHeight - height - offsetY() : offsetY();
		return new Box(x, y, width, height);
	}

	/**
	 * Files a dragged position back into the anchor and offsets, so the sliders keep up with the
	 * mouse. The readout is held on screen: dragged off an edge it stops flush against it.
	 */
	public void moveTo(int x, int y, Box box, int screenWidth, int screenHeight) {
		int left = Mth.clamp(x, 0, Math.max(0, screenWidth - box.width()));
		int top = Mth.clamp(y, 0, Math.max(0, screenHeight - box.height()));

		boolean right = left + box.width() / 2 > screenWidth / 2;
		boolean bottom = top + box.height() / 2 > screenHeight / 2;

		anchor(right
				? (bottom ? ANCHOR_BOTTOM_RIGHT : ANCHOR_TOP_RIGHT)
				: (bottom ? ANCHOR_BOTTOM_LEFT : ANCHOR_TOP_LEFT));
		offsetX(Math.min(MAX_OFFSET, right ? screenWidth - (left + box.width()) : left));
		offsetY(Math.min(MAX_OFFSET, bottom ? screenHeight - (top + box.height()) : top));
	}

	/**
	 * Draws the readout at its box. The editor calls this too, so what you line up is exactly what
	 * you get back in play.
	 */
	public void draw(GuiGraphicsExtractor graphics, Font font, String text, int colour, Box box) {
		float scale = scale();

		// The text is drawn at the origin and the matrix carries it into place -- scaling first and
		// positioning after would multiply the offsets by the scale as well.
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(box.x(), box.y());
		pose.scale(scale, scale);
		graphics.text(font, Component.literal(text), 0, 0, colour, AletheiaConfig.titleShadow);
		pose.popMatrix();
	}

	private boolean isRight() {
		return anchor() == ANCHOR_TOP_RIGHT || anchor() == ANCHOR_BOTTOM_RIGHT;
	}

	private boolean isBottom() {
		return anchor() == ANCHOR_BOTTOM_LEFT || anchor() == ANCHOR_BOTTOM_RIGHT;
	}
}
