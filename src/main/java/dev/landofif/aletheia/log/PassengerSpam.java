package dev.landofif.aletheia.log;

import dev.landofif.aletheia.Aletheia;

/**
 * Collapses vanilla's "Received passengers for unknown entity" warning into an occasional count.
 *
 * <p>The client logs that whenever the server sends a {@code SetPassengers} packet naming a vehicle
 * it has never been told about, or has already removed. On Telos that is constant -- the server
 * stacks entities to build its nametags and displays, and the riders routinely arrive before (or
 * after) the thing they ride. A measured session had <b>938 of them in six minutes</b>, from the
 * moment of joining, which buries everything else in the log including this mod's own debug output.
 *
 * <p>Nothing is lost by dropping the line: vanilla's own reaction to the packet is to ignore it and
 * return, and it does that either way -- only the logging is redirected. It is still <i>counted</i>,
 * and a single summary is printed at most once a minute, because a warning that vanishes without
 * trace is how a real problem goes unnoticed.
 *
 * <p><b>This is not a frame rate fix.</b> At two or three lines a second the logging costs
 * essentially nothing; it is the log's readability that this is for.
 */
public final class PassengerSpam {
	private PassengerSpam() {
	}

	/** How often the running count is allowed to say something. */
	private static final long SUMMARY_INTERVAL_MILLIS = 60_000L;

	private static long total;
	private static long sinceSummary;
	private static long lastSummaryAt;

	/** Counts one swallowed warning, and prints the running total if the interval has come round. */
	public static void swallow() {
		total++;
		sinceSummary++;

		long now = System.currentTimeMillis();
		if (lastSummaryAt == 0L) {
			// Say nothing on the first one -- the interval starts from here, so the summary lands a
			// minute in with a count worth reading rather than immediately with a count of one.
			lastSummaryAt = now;
		} else if (now - lastSummaryAt >= SUMMARY_INTERVAL_MILLIS) {
			Aletheia.LOGGER.info(
					"swallowed {} \"passengers for unknown entity\" warnings in the last {}s ({} this session)",
					sinceSummary, (now - lastSummaryAt) / 1000L, total);
			sinceSummary = 0;
			lastSummaryAt = now;
		}
	}

	/** How many have been swallowed since the game started, for {@code /aletheia status}. */
	public static long total() {
		return total;
	}
}
