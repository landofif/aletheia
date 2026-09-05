package dev.landofif.aletheia.timer;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Whether the dimension you are standing in is a dungeon.
 *
 * <p>Split out from {@link DungeonTimer} because it is the one part of the question that is plain
 * Java, and it is the part most likely to need changing when the server does.
 *
 * <p><b>A Telos dungeon is an instance, and it says so in its id.</b> The open world is
 * {@code telos:realm} and every dungeon room is {@code <name>/<number>} -- {@code telos:dungeon/2},
 * {@code telos:neo_eden/1}. So the room number on the end is the test, and it is a test that needs
 * no list: a dungeon the server adds tomorrow is timed the day it appears.
 *
 * <p><b>The name in front of it is not the dungeon.</b> {@code dungeon/1} through {@code dungeon/14}
 * are instance slots handed out as you enter, so the same dungeon is a different id every run and
 * fourteen unrelated dungeons share the same fourteen ids. Nothing here tries to name a dungeon from
 * that; {@link dev.landofif.aletheia.serverhud.HudArea} reads the name off the server's own ribbon.
 */
public final class DungeonDimension {
	private DungeonDimension() {
	}

	/** {@code dungeon/2}, {@code neo_eden/1} -- a room number on the end of the path. */
	private static final Pattern NUMBERED_ROOM = Pattern.compile(".+/\\d+");

	/**
	 * @param dimension  the dimension id, e.g. {@code telos:dungeon/2}
	 * @param extraTerms ids that count as a dungeon whatever their shape, from the setting of that
	 *                   name -- an escape hatch for a server that numbers its rooms differently
	 */
	public static boolean isDungeon(String dimension, List<String> extraTerms) {
		if (dimension == null || dimension.isEmpty()) {
			return false;
		}
		String folded = dimension.toLowerCase(Locale.ROOT);
		for (String term : extraTerms) {
			String needle = term.toLowerCase(Locale.ROOT).trim();
			if (!needle.isEmpty() && folded.contains(needle)) {
				return true;
			}
		}
		return NUMBERED_ROOM.matcher(pathOf(folded)).matches();
	}

	/** {@code telos:dungeon/2} to {@code dungeon/2}; an id with no namespace is already its own path. */
	public static String pathOf(String dimension) {
		if (dimension == null) {
			return "";
		}
		int colon = dimension.indexOf(':');
		return colon < 0 ? dimension : dimension.substring(colon + 1);
	}
}
