package dev.landofif.aletheia.detect;

/** Something worth putting on screen that {@link NeoEdenParser} recognised in a chat line. */
public sealed interface DungeonEvent {

	/**
	 * A score checkpoint, e.g. {@code Your persistence has been recorded. (259/265)}.
	 *
	 * @param current the score so far
	 * @param total   the score needed, or {@link #UNKNOWN_TOTAL} while the dungeon still reports
	 *                it as {@code UNDEFINED}
	 */
	record Progress(int current, int total) implements DungeonEvent {
		public static final int UNKNOWN_TOTAL = -1;

		public boolean isTotalKnown() {
			return total != UNKNOWN_TOTAL;
		}

		/** Renders as {@code 259/265}, or {@code 259/???} while the requirement is still undefined. */
		public String format(String unknownPlaceholder) {
			return current + "/" + (isTotalKnown() ? Integer.toString(total) : unknownPlaceholder);
		}
	}

	/** e.g. {@code Kyle_Clash is attempting to solve the edenic light puzzle!} */
	record PuzzleStarted(String player, String puzzle) implements DungeonEvent {
	}

	/** e.g. {@code Kyle_Clash Has solved the edenic light puzzle!} */
	record PuzzleSolved(String player, String puzzle) implements DungeonEvent {
	}

	/**
	 * e.g. {@code LunaEpitaph is attempting to defeat the edenic warriors in the edenic barracks
	 * battle room!}
	 */
	record BarracksStarted(String player) implements DungeonEvent {
	}

	/** e.g. {@code Foxiani has defeated the edenic warriors in the edenic barracks battle room!} */
	record BarracksCleared(String player) implements DungeonEvent {
	}

	/**
	 * A Dreadwood Thicket room being entered, e.g.
	 * {@code [Dreadwood Civilian] landofif is attempting the Colour Room!}
	 *
	 * @param room the room's name as chat gave it, without the trailing "Room" -- {@code Colour},
	 *             {@code Wave}, {@code Demon}, {@code Bullet Hell}
	 */
	record DreadwoodRoomStarted(String player, String room) implements DungeonEvent {
	}

	/**
	 * The same room announced as finished.
	 *
	 * <p>How many rooms of the run that makes is <i>not</i> carried here: it is state that spans
	 * several lines, so it lives in {@code DreadwoodRun} and is asked for when the title is shown.
	 */
	record DreadwoodRoomCleared(String player, String room) implements DungeonEvent {
	}

	/**
	 * A Shadowlands announcer speaking, which is how a spawn is broadcast:
	 * {@code [Herald] The torch burns bright.}
	 *
	 * @param mob  the tag the line opened with -- {@code Defender}, {@code Reaper}, {@code Herald} or
	 *             {@code Warden}
	 * @param line what it said, kept for the {@code {line}} placeholder
	 */
	record ShadowlandsSpawn(String mob, String line) implements DungeonEvent {
	}

	/**
	 * One of the Cog Sentinel's stabilisers going down: {@code (3/5) Cog Stabilisers destroyed}.
	 *
	 * <p>Unlike {@link Progress} the counter is at the <i>front</i> of the line, and it counts the five
	 * peripheral stabilisers rather than dungeon score. The last one is what the fight is waiting for --
	 * the server follows it with "PERIPHERAL STABILISERS DESTROYED" and the boss becomes attackable --
	 * so {@link #allDestroyed} is worth a title of its own rather than another number.
	 *
	 * @param destroyed how many are down
	 * @param total     how many there are, as the line itself states it -- read rather than assumed, so
	 *                  a re-tune to a different number still reads correctly
	 */
	record CogStabilisers(int destroyed, int total) implements DungeonEvent {
		public boolean allDestroyed() {
			return destroyed >= total;
		}
	}

	/**
	 * The server's own end-of-run line: {@code Defeated Malfas in 24s}.
	 *
	 * <p>Telos reports the clear time itself now, as one line inside the single multi-line message
	 * that carries the whole leaderboard. That is the reason there is no completion title here and
	 * no split reporting: the server already says all of it, better than a client mod can. This event
	 * exists only so {@code DungeonTimer} can stop its clock on the same number the leaderboard shows,
	 * rather than on its own guess at one.
	 *
	 * @param boss    the boss named in the line
	 * @param seconds the clear time the server stated, in whole seconds
	 */
	record DungeonCleared(String boss, int seconds) implements DungeonEvent {
	}

	/** Cherubim's "Enough!" / "Silence!" -- the cue to stop attacking during the boss fight. */
	record CherubimShout(Shout shout) implements DungeonEvent {
	}

	enum Shout {
		ENOUGH,
		SILENCE
	}
}
