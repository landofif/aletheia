package dev.landofif.aletheia.timer;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.serverhud.HudArea;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.List;
import java.util.OptionalInt;

/**
 * How long the run you are in has taken so far.
 *
 * <p><b>Why this exists at all.</b> Telos now prints the clear time, and your personal best, in its
 * own end-of-dungeon leaderboard -- which is why the mod this was modelled on deleted its timer
 * outright. What the server still does not give you is any of it <i>while you are running</i>: a
 * clock you can pace against, and the time to beat sitting next to it. That gap is the whole feature,
 * and it is why there is no completion title, no split reporting and no chat rewriting here. All of
 * that would be the mod saying, worse and a second later, what the leaderboard already said.
 *
 * <p><b>The dimension says whether you are in a dungeon; it cannot say which one.</b> Telos hands
 * out a dungeon as an instance slot, so Celestial's Province is {@code telos:dungeon/2} on one run
 * and {@code telos:dungeon/11} on the next, and every other dungeon draws from the same fourteen
 * ids. Matching those ids against a list of names -- which is what this used to do -- could never
 * work anywhere: the one name it shipped with, {@code dreadwood}, is not a dimension at all.
 * {@link DungeonDimension} therefore asks only the question the id can answer, and the run is
 * <i>named</i> from the ribbon in the corner of the screen, via {@link HudArea}.
 *
 * <p><b>The name is latched, not followed.</b> A dungeon spans more than one area: Celestial's
 * Province gives way to Seraph's Domain for the boss, Rustborn Kingdom to The Dawn of Creation, and
 * each is a fresh dimension too. Following either would restart the clock at the boss door and file
 * half a run as a personal best, so the first area named is the run's name until you leave.
 */
public final class DungeonTimer {
	private DungeonTimer() {
	}

	/** How long a run's numbers stay on screen after you walk out, before the readouts give up. */
	private static final long HOLD_AFTER_LEAVING_MILLIS = 30_000L;

	/** Whether a run is being timed at all. */
	private static boolean running;

	/**
	 * What the run is filed under -- the area the ribbon named, empty until it has named one.
	 *
	 * <p>Separate from {@link #running} because the two genuinely differ for a moment: the clock
	 * starts the tick you arrive, and the server's HUD takes a beat longer to follow you in. A run
	 * with no name still shows its clock; it just has no best to compare against yet.
	 */
	private static String runKey = "";

	private static long startedAtNanos;

	/** The run's total once it is over, else 0 -- see {@link #cleared}. */
	private static int finishedSeconds;
	private static boolean finishedWasBest;

	/** When the last dungeon dimension was left, which is what the hold below is measured from. */
	private static long leftAtMillis;

	/**
	 * The clock's reading at the moment the dungeon was left, for a run that never finished.
	 *
	 * <p>Without it the held readout would keep counting up while you stand in the hub, which is a
	 * clock telling you how long ago you gave up.
	 */
	private static int frozenSeconds;

	private static String lastDimension = "";

	/**
	 * Whether the last dimension seen was a dungeon one.
	 *
	 * <p>Needed on top of {@link #running}: a finished run is held on screen for a moment after you
	 * walk out, so a run is still under way while you stand in the hub. Without this, walking straight
	 * back into a dungeon would leave the old, frozen time up instead of starting the new run.
	 */
	private static boolean inside;

	public static void register() {
		DungeonBests.load();
		ClientTickEvents.END_CLIENT_TICK.register(DungeonTimer::tick);

		// A change of server arrives as a second login on the same connection, so this covers hub to
		// realm and realm to realm as well as the first join. Moving between the rooms of a dungeon is
		// a respawn rather than a login, so it does not reach here -- which is what keeps the clock
		// running across the boss door.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> forget("joined a world"));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> forget("left the server"));
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		lastDimension = player.level().dimension().identifier().toString();

		if (!inDungeon()) {
			// Only when there is something to end: this runs every tick you spend in the open world,
			// and the reason string is not worth building twenty times a second to throw away.
			if (inside || running) {
				stop("left the dungeon (now " + lastDimension + ")");
			}
			return;
		}
		if (!inside) {
			start();
		}
		inside = true;

		// Four times a second is far more often than the ribbon changes, and this stops for good the
		// moment the first boss falls -- so it costs a few reads at the start of a run and nothing after.
		if (++nameTicks >= NAME_EVERY_TICKS) {
			nameTicks = 0;
			name();
		}
	}

	/** How often the ribbon is re-read while the run is still being named. */
	private static final int NAME_EVERY_TICKS = 5;

	private static int nameTicks;

	/**
	 * Takes a {@code Defeated <boss> in <time>} line off the leaderboard.
	 *
	 * <p><b>A clear is not the end of the run.</b> It used to be, and in the busiest dungeons on the
	 * server that stopped the clock near the beginning: Asmodeus dies and Seraphim spawns in the same
	 * room with no portal in between, Rustborn Kingdom announces four of these before you are out, and
	 * Neo Eden's first two arrive in the same second. So a clear is filed as a split, and only the
	 * <i>last</i> stage of a known route ends the run. A dungeon with no route in {@link DungeonRoutes}
	 * keeps the old behaviour, because for a one-boss dungeon the first clear really is the end.
	 *
	 * <p>Only a run that is actually under way is recorded: without that guard, somebody quoting the
	 * line in chat could write a personal best you never ran.
	 */
	public static void cleared(String boss, int seconds) {
		if (!running || finishedSeconds > 0 || seconds <= 0) {
			return;
		}
		// Last chance to put a name to it -- a run cleared before the ribbon was ever read has a time
		// worth showing but nowhere to file it.
		name();

		boolean lastStage = DungeonSplits.record(boss, seconds);
		if (DungeonSplits.hasRoute() && !lastStage) {
			// More of the dungeon to come. The clock keeps running; the split readout shows what fell.
			return;
		}

		// The run's own total. Where the route has several stages the server never states one -- its
		// numbers are per stage, and in Rustborn they are timed from each stage's own start -- so the
		// only figure for the whole run is this clock, read before it is frozen below.
		int total = DungeonSplits.hasRoute() ? elapsedSeconds() : seconds;

		finishedSeconds = total;
		finishedWasBest = !runKey.isEmpty() && DungeonBests.record(runKey, total);

		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("{} cleared in {}s ({}), last boss {}",
					runKey.isEmpty() ? "unnamed run" : runKey, total,
					finishedWasBest ? "new best" : "not a best", boss);
		}
	}

	/** Drops the clock by hand, for {@code /aletheia timer reset}. */
	public static void resetByHand() {
		// forget rather than stop: being asked directly outranks the hold below, and a reset that
		// silently did nothing for half a minute after a run would be a reset nobody could trust.
		forget("reset by hand");
	}

	// ------------------------------------------------------------------ Readout

	public static boolean running() {
		return running;
	}

	/**
	 * Whether the run is over and the clock frozen on its total.
	 *
	 * <p>That total is the server's own number for a one-boss dungeon, and this clock's for a dungeon
	 * with stages -- there the server times each stage separately and never states a figure for the
	 * whole run. See {@link #cleared}.
	 */
	public static boolean finished() {
		return finishedSeconds > 0;
	}

	public static boolean finishedWasBest() {
		return finishedWasBest;
	}

	/** The dungeon being timed, e.g. {@code Celestial's Province}. Empty when nothing is. */
	public static String key() {
		return runKey;
	}

	/**
	 * How long the run has taken, in whole seconds -- counting up while it is under way, and frozen
	 * on the server's stated time once it has finished.
	 */
	public static int elapsedSeconds() {
		if (finishedSeconds > 0) {
			return finishedSeconds;
		}
		if (!running) {
			return 0;
		}
		if (frozenSeconds > 0) {
			return frozenSeconds;
		}
		return (int) ((System.nanoTime() - startedAtNanos) / 1_000_000_000L);
	}

	/** The time to beat for the run in progress, if there is one on file. */
	public static OptionalInt best() {
		return runKey.isEmpty() ? OptionalInt.empty() : DungeonBests.best(runKey);
	}

	/** Whether the clock has already passed the best -- the one comparison a run without splits can make. */
	public static boolean pastBest() {
		OptionalInt best = best();
		return best.isPresent() && elapsedSeconds() > best.getAsInt();
	}

	/** The dimension last seen, so {@code /aletheia timer} can say why it is or is not timing. */
	public static String currentDimension() {
		return lastDimension;
	}

	/** The area the server's ribbon names right now, read fresh -- for {@code /aletheia timer}. */
	public static String currentArea() {
		return HudArea.name();
	}

	/** Whether the dimension you are standing in counts as a dungeon. */
	public static boolean inDungeon() {
		return DungeonDimension.isDungeon(lastDimension, filterTerms());
	}

	/**
	 * The extra dimensions the setting names, for {@code /aletheia timer} and for the check above.
	 *
	 * <p>Split once per edit rather than once per tick. Asked twenty times a second for a setting
	 * that is normally empty and almost never changes, which is the same reason
	 * {@link dev.landofif.aletheia.serverhud.ServerHudFilter} keeps its phrases.
	 */
	public static List<String> filterTerms() {
		String setting = String.valueOf(AletheiaConfig.timerDimensionFilter);
		if (!setting.equals(lastFilterSetting)) {
			lastFilterSetting = setting;
			filterTerms = NeoEdenParser.splitPhrases(setting);
		}
		return filterTerms;
	}

	private static String lastFilterSetting = "";
	private static List<String> filterTerms = List.of();

	// ------------------------------------------------------------------ Internals

	private static void start() {
		running = true;
		runKey = "";
		DungeonSplits.forget();
		startedAtNanos = System.nanoTime();
		finishedSeconds = 0;
		frozenSeconds = 0;
		leftAtMillis = 0L;
		finishedWasBest = false;

		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("dungeon timer started in {}", lastDimension);
		}
	}

	/**
	 * Puts a name to the run in progress, and keeps correcting it until the first boss falls.
	 *
	 * <p><b>The ribbon lags the dimension change.</b> You arrive in {@code telos:dungeon/1} a beat
	 * before the server's HUD catches up, so the first reading taken inside is still the biome you
	 * walked in from. Latching it -- which is what this did -- named a Celestial's Province run
	 * "Shadowlands", and that had a second consequence worse than the label: no route is filed under
	 * Shadowlands, so Asmodeus' clear was taken for the end of the run and the clock stopped two
	 * minutes in. Seen in the log for 2026-08-29 22:18:50, where the clock reads {@code Shadowlands:
	 * running, 0:15} while a fresh read of the same ribbon says Celestial's Province.
	 *
	 * <p><b>Why not simply wait a second.</b> Because there is nothing to wait for that can be timed:
	 * the lag is the server's, and a fixed pause is either too short on a bad connection or a pause
	 * for nothing on a good one. What can be said exactly is when the name stops being a guess --
	 * when the run has produced something filed under it. So the ribbon is re-read until the first
	 * split lands, and frozen from then on.
	 *
	 * <p>Frozen at that point rather than never, because a dungeon is several areas: Celestial's
	 * Province gives way to Seraph's Domain for the boss and Rustborn Kingdom to the Dawn of Creation,
	 * both of them well after their first clear. Following the ribbon that far would rename the run at
	 * the boss door and file the boss room alone as the personal best.
	 */
	private static void name() {
		if (!runKey.isEmpty() && DungeonSplits.touched()) {
			return;
		}
		String area = HudArea.name();
		if (area.isEmpty() || area.equals(runKey)) {
			return;
		}
		runKey = area;
		DungeonSplits.begin(area);

		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("dungeon timer naming this run {} ({}), {}", area, lastDimension,
					DungeonSplits.hasRoute() ? "splits known" : "no split table for it");
		}
	}

	/**
	 * Ends the run, unless it has only just been walked out of.
	 *
	 * <p>Half a minute's grace, because the numbers are worth reading after the fact: Telos' own
	 * leaderboard is on screen at the same moment, and stepping through the exit should not take the
	 * time and the splits with it. Measured from <b>leaving</b> rather than from the final clear,
	 * which is what it used to be -- a run you stood around in for a minute after killing the boss
	 * lost its readouts the instant you crossed the threshold.
	 *
	 * <p>A run with nothing to show for it -- no splits, no total -- is simply dropped.
	 */
	private static void stop(String reason) {
		if (inside) {
			inside = false;
			leftAtMillis = System.currentTimeMillis();
			frozenSeconds = elapsedSeconds();
		}
		boolean worthHolding = finishedSeconds > 0 || DungeonSplits.any();
		if (worthHolding && System.currentTimeMillis() - leftAtMillis < HOLD_AFTER_LEAVING_MILLIS) {
			return;
		}
		forget(reason);
	}

	/** Ends the run now, hold or no hold. */
	private static void forget(String reason) {
		inside = false;
		if (!running) {
			return;
		}
		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("dungeon timer stopped -- {}", reason);
		}
		running = false;
		runKey = "";
		DungeonSplits.forget();
		startedAtNanos = 0L;
		finishedSeconds = 0;
		frozenSeconds = 0;
		leftAtMillis = 0L;
		finishedWasBest = false;
	}
}
