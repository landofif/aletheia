package dev.landofif.aletheia.timer;

import dev.landofif.aletheia.detect.ChatText;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The bosses a dungeon puts in front of you, in the order it puts them there.
 *
 * <p>This is the table the split readout is built from, and it exists because <b>a Telos dungeon is
 * not one fight</b>. Celestial's Province is Asmodeus, then Seraphim in the same room, then True
 * Seraph through a portal that only opens once Seraphim is down; Rustborn Kingdom is four stages
 * ending in the Dawn of Creation. Until now the clock stopped dead on the first {@code Defeated}
 * line and called the run over -- which for these two dungeons was somewhere near the beginning.
 *
 * <p><b>A stage is not always one boss.</b> Mithrion and Nebula are fought together in Rustborn and
 * the server announces only Nebula, so the stage is labelled for both and keyed on the name the
 * clear line actually carries. That is the split between {@link Stage#label} and {@link Stage#boss}:
 * one is for reading, the other is for matching, and they are allowed to differ.
 *
 * <p><b>The names are the server's own</b>, taken from its clear lines rather than from the wiki or
 * from what people call them -- "True Seraph" and "True Ophan", not Seraphim and Ophanim with
 * "true" in front. {@link #forDungeon} keys on the area the ribbon shows, which is where
 * {@link DungeonTimer} gets the run's name; both sides are folded through
 * {@link ChatText#lettersAndDigits}, so the apostrophe in "Celestial's Province" cannot matter.
 *
 * <p>A dungeon that is not in here still gets a clock and still gets a split line per boss it
 * announces -- it just cannot show you what is still to come, because nothing here knows.
 */
public final class DungeonRoutes {
	private DungeonRoutes() {
	}

	/**
	 * One stage of a run.
	 *
	 * @param label  what the readout shows
	 * @param bosses the names the server's {@code Defeated <boss> in <time>} line can carry for this
	 *               stage -- more than one where a stage is more than one fight. Neo Eden announces
	 *               Apostle and Hierophant in the same second; either line finishes the stage, and
	 *               the other is then ignored rather than counted twice
	 */
	public record Stage(String label, List<String> bosses) {

		public Stage(String label, String boss) {
			this(label, List.of(boss));
		}

		/** Whether a clear line names this stage. Whole names, never a substring -- see the test. */
		public boolean matches(String boss) {
			String folded = ChatText.lettersAndDigits(boss);
			if (folded.isEmpty()) {
				return false;
			}
			for (String name : bosses) {
				if (ChatText.lettersAndDigits(name).equals(folded)) {
					return true;
				}
			}
			return false;
		}

		/** The name the stage's own best is filed under; the first, so a re-labelling cannot move it. */
		public String boss() {
			return bosses.get(0);
		}
	}

	/**
	 * @param dungeon the area name the run is filed under, e.g. {@code Celestial's Province}
	 * @param stages  in the order they are fought; the last one ends the run
	 */
	public record Route(String dungeon, List<Stage> stages) {

		public Stage last() {
			return stages.get(stages.size() - 1);
		}

		/** The first stage this clear could belong to, or -1 for a boss the route does not have. */
		public int indexOf(String boss) {
			return indexOf(boss, 0);
		}

		/**
		 * The same, from a given stage on.
		 *
		 * <p>Needed because a name can appear twice in a route -- a dungeon that puts the same fight in
		 * front of you two rooms running -- and the second clear belongs to the second row.
		 */
		public int indexOf(String boss, int from) {
			for (int index = Math.max(0, from); index < stages.size(); index++) {
				if (stages.get(index).matches(boss)) {
					return index;
				}
			}
			return -1;
		}
	}

	private static final List<Route> ROUTES = List.of(
			// Asmodeus and Seraphim share a room -- no portal between them, which is why a clock that
			// stopped on the first clear stopped less than half way through the run.
			new Route("Celestial's Province", List.of(
					new Stage("Asmodeus", "Asmodeus"),
					new Stage("Seraphim", "Seraphim"),
					// Seraph's Domain, through the portal that drops when Seraphim dies. The server gives
					// it a leaderboard of its own, but you never go back to the realm, so it is the same run.
					new Stage("True Seraph", "True Seraph"))),

			// Apostle and Hierophant fall in the same second and share a time; Cherubim's is counted
			// from the same start, so it reads as the whole run.
			new Route("Neo Eden", List.of(
					new Stage("Apostle & Hierophant", List.of("Apostle", "Hierophant")),
					new Stage("Cherubim", "Cherubim"))),

			new Route("Rustborn Kingdom", List.of(
					new Stage("Valerion", "Valerion"),
					// Both are on screen; only Nebula's death has ever been announced, but the pair is
					// listed so a line for either one lands on the right row.
					new Stage("Mithrion & Nebula", List.of("Nebula", "Mithrion")),
					new Stage("Ophanim", "Ophanim"),
					// The Dawn of Creation, the same way True Seraph follows Seraphim.
					new Stage("True Ophan", "True Ophan"))));

	/** @return the route for this dungeon, or {@code null} for one with no table here */
	@Nullable
	public static Route forDungeon(String dungeon) {
		if (dungeon == null || dungeon.isEmpty()) {
			return null;
		}
		String folded = ChatText.lettersAndDigits(dungeon);
		if (folded.isEmpty()) {
			return null;
		}
		for (Route route : ROUTES) {
			if (ChatText.lettersAndDigits(route.dungeon()).equals(folded)) {
				return route;
			}
		}
		return null;
	}

	/** Every route, for {@code /aletheia timer}. */
	public static List<Route> all() {
		return ROUTES;
	}
}
