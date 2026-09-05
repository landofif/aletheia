package dev.landofif.aletheia.dreadwood;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.timer.DungeonDimension;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * How far through a Dreadwood Thicket run you are -- the {@code 1/2} on the room-cleared title.
 *
 * <p>The dungeon announces each room twice, once on entering and once on finishing, and nothing in
 * either line says which room of the run it is. So the count is kept here, by <b>room name</b>
 * rather than as a tally: the same room announced twice (a retry, or the server repeating itself
 * outside the de-duplication window) must not advance the run.
 *
 * <p><b>Why the reset is not simply "the dimension changed".</b> That is how
 * {@link dev.landofif.aletheia.gift.NaturesGift} ends its timer, and it would be wrong here --
 * the rooms of a Telos dungeon are separate dimensions, so walking from one to the next would wipe
 * the count mid-run. What matters is leaving the dungeon <i>entirely</i>, which is the one thing a
 * dimension id can be asked: a dungeon room is numbered ({@code telos:dungeon/2}) and the open world
 * is not, so the count survives every move whose destination is still a dungeon and is dropped the
 * moment one is not. See {@link DungeonDimension}.
 *
 * <p>Three other things end a run, so a stale count can never be left on screen: a fresh login (hub
 * to realm, realm to realm, or a disconnect), a completion arriving when the run is already full --
 * that is the first room of the next run -- and a long silence, in case the dimension filter never
 * matched anything in the first place.
 */
public final class DreadwoodRun {
	private DreadwoodRun() {
	}

	/** How far through the rooms a run got, as of one announcement. */
	public record Progress(int done, int total) {
		/** Renders as {@code 1/2}. */
		public String format() {
			return done + "/" + total;
		}
	}

	/**
	 * The rooms finished so far, keyed by {@link ChatText#lettersAndDigits} so "Bullet Hell" and
	 * "bullet hell" are one room. Ordered so {@code /aletheia dreadwood} can list them as they happened.
	 */
	private static final Set<String> clearedRooms = new LinkedHashSet<>();

	private static String lastRoom = "";
	private static long lastSeenAtMillis;

	/** Whether the last dimension seen was a Dreadwood one, to notice leaving rather than moving. */
	private static boolean inDungeon;
	private static String lastDimension = "";

	private static String lastResetReason = "";
	private static long lastResetAtMillis;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(DreadwoodRun::tick);

		// A change of server arrives as a second login on the same connection, so this covers hub to
		// realm and realm to realm as well as the first join.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> reset("joined a world"));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> reset("left the server"));
	}

	private static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}
		followLocation(player);
		forgetIfIdle();
	}

	/** Records a room being entered. Only the timeout and a finished run care, but both matter. */
	public static void started(String room) {
		endFinishedRun();
		lastRoom = room;
		lastSeenAtMillis = System.currentTimeMillis();
	}

	/**
	 * Records a room being finished.
	 *
	 * @return the run's progress including this room, e.g. {@code 1/2}
	 */
	public static Progress cleared(String room) {
		endFinishedRun();

		clearedRooms.add(ChatText.lettersAndDigits(room));
		lastRoom = room;
		lastSeenAtMillis = System.currentTimeMillis();

		int total = total();
		return new Progress(Math.min(clearedRooms.size(), total), total);
	}

	/** The count so far, for the placeholders on a room-entered title. */
	public static Progress progress() {
		int total = total();
		return new Progress(Math.min(clearedRooms.size(), total), total);
	}

	public static int total() {
		return Math.max(1, AletheiaConfig.dreadwoodRoomsPerRun);
	}

	/** Clears the count by hand, for {@code /aletheia dreadwood reset}. */
	public static void resetByHand() {
		reset("reset by hand");
	}

	/**
	 * Ends a run that already has all its rooms, so the announcement now arriving is counted as the
	 * first room of the next one rather than pushing the count past its total.
	 */
	private static void endFinishedRun() {
		if (clearedRooms.size() >= total()) {
			reset("a new run started");
		}
	}

	/**
	 * Drops the count on leaving the dungeon -- or on entering it, which starts a run from clean
	 * whatever an earlier one left behind. Moving between rooms is not either of those.
	 *
	 * <p><b>The dungeon is recognised by the shape of the id, not by its name.</b> This used to look
	 * for {@code dreadwood} in the dimension id, which never matched anything: Telos has no Dreadwood
	 * dimension, it hands out a numbered instance slot -- {@code telos:dungeon/2} -- and the same slot
	 * is a different dungeon on the next run. So for months the only thing ending a run here was the
	 * idle timeout, fifteen minutes after you had left. {@link DungeonDimension} asks the question the
	 * id can actually answer; the setting stays as an escape hatch for a dungeon shaped differently.
	 */
	private static void followLocation(LocalPlayer player) {
		lastDimension = player.level().dimension().identifier().toString();

		boolean nowInside = DungeonDimension.isDungeon(
				lastDimension, NeoEdenParser.splitPhrases(AletheiaConfig.dreadwoodDimensionFilter));
		if (nowInside != inDungeon) {
			inDungeon = nowInside;
			reset(nowInside ? "entered " + lastDimension : "left the dungeon (now " + lastDimension + ")");
		}
	}

	private static void forgetIfIdle() {
		if (clearedRooms.isEmpty()) {
			return;
		}
		long minutes = Math.max(1, AletheiaConfig.dreadwoodForgetMinutes);
		if (System.currentTimeMillis() - lastSeenAtMillis > minutes * 60_000L) {
			reset("no Dreadwood chat for " + minutes + " minutes");
		}
	}

	private static void reset(String reason) {
		boolean wasCounting = !clearedRooms.isEmpty();

		clearedRooms.clear();
		lastRoom = "";

		lastResetReason = reason;
		lastResetAtMillis = System.currentTimeMillis();
		if (wasCounting && AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("Dreadwood run count cleared -- {}", reason);
		}
	}

	// ------------------------------------------------------------------ /aletheia dreadwood

	/** The rooms finished so far, folded to their keys, oldest first. */
	public static List<String> clearedRooms() {
		return List.copyOf(clearedRooms);
	}

	public static String lastRoom() {
		return lastRoom;
	}

	/** The dimension being watched, e.g. {@code telos:dreadwood/1}. */
	public static String currentDimension() {
		return lastDimension;
	}

	/** Whether the dimension filter says you are in the dungeon right now. */
	public static boolean insideDungeon() {
		return inDungeon;
	}

	/** Why the count was last dropped, and how long ago -- empty if it never has been. */
	public static String lastReset() {
		if (lastResetReason.isEmpty()) {
			return "";
		}
		long secondsAgo = Math.max(0L, (System.currentTimeMillis() - lastResetAtMillis) / 1000L);
		return lastResetReason + " (" + secondsAgo + "s ago)";
	}
}
