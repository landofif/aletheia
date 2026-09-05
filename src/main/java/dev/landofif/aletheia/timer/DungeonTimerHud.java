package dev.landofif.aletheia.timer;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.CooldownText;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.resources.Identifier;

import java.util.OptionalInt;

/**
 * The run clock: {@code run 1:23  PB 1:15}.
 *
 * <p>One line, like the other readouts here, rather than the three-line panel this was modelled on.
 * The dungeon's name is not on it because you are standing in the dungeon, and the label is yours to
 * set; what is worth the width is the number going up and the number to beat.
 *
 * <p>The clock turns to {@link AletheiaConfig#timerPastBestColour} the moment it passes the best.
 * That is the only comparison a timer without splits can honestly make -- it cannot tell you whether
 * you are ahead at this point in the run, because nothing here knows where in the run you are, but it
 * can tell you the moment the best is gone.
 */
public final class DungeonTimerHud extends AletheiaHud {
	public static final DungeonTimerHud INSTANCE = new DungeonTimerHud();

	private DungeonTimerHud() {
	}

	/** Green while the finished time stands as a new best, which the leaderboard also says. */
	private static final int BEST_COLOUR = 0xFF55FF55;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "dungeon_timer");
	}

	@Override
	public String label() {
		return "Dungeon timer";
	}

	@Override
	public String text() {
		if (!DungeonTimer.running()) {
			return AletheiaConfig.timerAlwaysShow ? line(0, OptionalInt.empty()) : null;
		}
		return line(DungeonTimer.elapsedSeconds(), DungeonTimer.best());
	}

	private static String line(int seconds, OptionalInt best) {
		StringBuilder out = new StringBuilder();

		String label = AletheiaConfig.timerLabel;
		if (label != null && !label.isBlank()) {
			out.append(label).append(' ');
		}
		out.append(time(seconds));

		if (AletheiaConfig.timerShowBest && best.isPresent()) {
			String bestLabel = AletheiaConfig.timerBestLabel;
			out.append("  ");
			if (bestLabel != null && !bestLabel.isBlank()) {
				out.append(bestLabel).append(' ');
			}
			out.append(time(best.getAsInt()));
		}

		return out.toString();
	}

	/** The same two formats Nature's Gift offers, for one habit rather than two. */
	private static String time(int seconds) {
		return AletheiaConfig.timerTimeFormat == 1
				? CooldownText.asSeconds(seconds)
				: CooldownText.asClock(seconds);
	}

	@Override
	public int colour() {
		if (DungeonTimer.finished()) {
			return DungeonTimer.finishedWasBest()
					? BEST_COLOUR
					: AletheiaConfig.argb(AletheiaConfig.timerColour);
		}
		return AletheiaConfig.argb(
				DungeonTimer.pastBest() ? AletheiaConfig.timerPastBestColour : AletheiaConfig.timerColour);
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showTimerHud) {
			return false;
		}
		return DungeonTimer.running() || AletheiaConfig.timerAlwaysShow;
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showTimerHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showTimerHud = value;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.timerAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.timerAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.timerOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.timerOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.timerOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.timerOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.timerScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.timerScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.timerAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.timerOffsetX = 4;
		AletheiaConfig.timerOffsetY = 52;
		AletheiaConfig.timerScale = 100;
	}
}
