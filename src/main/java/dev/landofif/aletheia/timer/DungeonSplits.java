package dev.landofif.aletheia.timer;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Which bosses of the run you have put down, and how long each took.
 *
 * <p>Held apart from {@link DungeonTimer} because it answers a different question. The clock says
 * how long you have been in here; this says how far through you are, which for a dungeon like
 * Rustborn Kingdom -- four stages -- is the more useful of the two.
 *
 * <p><b>The times are the server's own, one per stage.</b> That matters more than it looks, because
 * what the server means by a stage's time is not the same everywhere: in Celestial's Province
 * Asmodeus and Seraphim share a room and a clock, so Seraphim's 7:18 <i>includes</i> Asmodeus' 3:41,
 * while in Rustborn each stage is timed from its own start and the four numbers add up to the run.
 * Nothing here tries to reconcile the two. Each row is the number the leaderboard put on the screen
 * for that boss, next to your best for the same boss -- like against like, and instantly checkable
 * against what you just read in chat.
 *
 * <p>Deriving splits from this mod's clock instead would give a tidier column and a worse readout:
 * it would disagree with the leaderboard in Rustborn by minutes, and the leaderboard is what the
 * player is looking at.
 */
public final class DungeonSplits {
	private DungeonSplits() {
	}

	/**
	 * One line of the readout.
	 *
	 * @param label   the stage's name
	 * @param seconds the server's stated time, or 0 while the stage is still to come
	 * @param best    whether that time became a new personal best for this boss
	 * @param delta   seconds against the best that stood before this run, when there was one
	 */
	public record Row(String label, int seconds, boolean best, OptionalInt delta) {
		public boolean done() {
			return seconds > 0;
		}
	}

	/** The dungeon these splits belong to, empty when nothing is being timed. */
	private static String dungeon = "";

	/** The table for it, or null for a dungeon with no table -- see {@link DungeonRoutes}. */
	@Nullable
	private static DungeonRoutes.Route route;

	/**
	 * The stages cleared so far.
	 *
	 * <p>With a route this is filled in at the stage's own index, so a boss killed out of order still
	 * lands on its own line. Without one it is simply the order they were announced in.
	 */
	private static final List<Row> rows = new ArrayList<>();

	/** How many of the route's stages are down, so the run knows when it is over. */
	private static int cleared;

	/** Starts a fresh set of splits for a named dungeon. */
	static void begin(String dungeonName) {
		forget();
		dungeon = dungeonName == null ? "" : dungeonName;
		route = DungeonRoutes.forDungeon(dungeon);
		if (route != null) {
			for (DungeonRoutes.Stage stage : route.stages()) {
				rows.add(new Row(stage.label(), 0, false, OptionalInt.empty()));
			}
		}
	}

	static void forget() {
		dungeon = "";
		route = null;
		rows.clear();
		cleared = 0;
	}

	/**
	 * Files a clear against its stage.
	 *
	 * <p>The best for a stage is only kept where there is a route to hang it on. A dungeon with one
	 * boss has no splits worth the name -- its one time is the run's time, which the clock already
	 * shows against the dungeon's own best, and filing it twice would only put the same number in the
	 * bests list twice under two spellings.
	 *
	 * @return whether this was the stage that ends the run
	 */
	static boolean record(String boss, int seconds) {
		if (seconds <= 0) {
			return false;
		}
		if (route == null) {
			// No table: the readout still lists what has fallen, in the order it fell.
			rows.add(new Row(boss, seconds, false, OptionalInt.empty()));
			return false;
		}

		// The first stage this boss could be that is not already down. Two things ride on the "not
		// already down": a stage announced twice (Neo Eden sends Apostle and Hierophant in the same
		// second for one row) must not count twice, and a route that names the same fight in two rows
		// must fill them in order rather than both landing on the first.
		int index = -1;
		for (int at = route.indexOf(boss); at >= 0; at = route.indexOf(boss, at + 1)) {
			if (!rows.get(at).done()) {
				index = at;
				break;
			}
		}
		if (index < 0) {
			// A boss this dungeon's table does not have, or one whose rows are all filled. Not an error
			// worth acting on -- Telos puts plenty on a leaderboard -- but it must not end the run.
			return false;
		}

		String key = bestKey(route.stages().get(index).boss());
		OptionalInt previous = DungeonBests.best(key);
		boolean best = DungeonBests.record(key, seconds);
		OptionalInt delta = previous.isPresent()
				? OptionalInt.of(seconds - previous.getAsInt())
				: OptionalInt.empty();

		rows.set(index, new Row(route.stages().get(index).label(), seconds, best, delta));
		cleared++;

		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("split {} {} = {}s ({}/{})",
					dungeon, boss, seconds, cleared, route.stages().size());
		}
		return index == route.stages().size() - 1;
	}

	/** How a stage's best is filed: {@code Celestial's Province > Seraphim}. */
	static String bestKey(String boss) {
		return dungeon + " > " + boss;
	}

	// ------------------------------------------------------------------ Readout

	/** Whether there is a table for the dungeon being run, so the stages still to come are known. */
	public static boolean hasRoute() {
		return route != null;
	}

	/** The dungeon these splits belong to. */
	public static String dungeon() {
		return dungeon;
	}

	/** Whether every stage of the route is down. */
	public static boolean complete() {
		return route != null && cleared >= route.stages().size();
	}

	/** Whether there is anything at all to draw. */
	public static boolean any() {
		return !rows.isEmpty();
	}

	/**
	 * Whether anything has actually been filed against these splits yet.
	 *
	 * <p>Asked by {@link DungeonTimer#name}, which stops re-reading the run's name once this is true:
	 * a set of splits with a boss in it is a set of splits worth keeping the name of.
	 */
	public static boolean touched() {
		return route == null ? !rows.isEmpty() : cleared > 0;
	}

	/** The lines, in order. Stages still to come have a time of 0. */
	public static List<Row> rows() {
		return List.copyOf(rows);
	}

	/** Which row the run is on, for highlighting it -- the first not yet done, else -1. */
	public static int current() {
		for (int index = 0; index < rows.size(); index++) {
			if (!rows.get(index).done()) {
				return index;
			}
		}
		return -1;
	}
}
