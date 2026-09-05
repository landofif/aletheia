package dev.landofif.aletheia.gift;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.CooldownText;
import dev.landofif.aletheia.ui.Alerts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks the Nature's Gift cooldown with a timer of our own.
 *
 * <p>Telos starts the ability through the <b>vanilla item cooldown system</b> -- the same mechanism
 * behind the sweeping overlay on a thrown ender pearl -- so
 * {@link net.minecraft.world.item.ItemCooldowns#getCooldownPercent} is how the proc is spotted, and
 * the item's lore ("Cooldown &raquo; 360s") says how long it runs for. The lore never counts down,
 * so it cannot be read as the remaining time.
 *
 * <p>What the server's reading cannot be trusted for is the <i>end</i> of the cooldown: taking the
 * boots off clears it, and putting them back on does not bring it back, so following it live made
 * the readout snap to {@code ready} on every swap while the ability was in fact still down. So the
 * server's number is only ever allowed to <b>add</b> time here, never to take it away: the moment it
 * reports more time left than this timer has, the timer is set to match, and otherwise the timer
 * just runs on its own clock. That covers the proc, a relog part-way through (the server hands back
 * however much is genuinely left), and anything that extends the cooldown, without the swap
 * resetting it.
 *
 * <p>Because the count is ours, the resets the server does have to be followed deliberately. Nearly
 * all of them come down to the client changing dimension -- entering or leaving a dungeon, moving
 * between the rooms of one, and being sent to another world all arrive that way -- and changing
 * server hands the client a fresh login instead, which {@link #register()} watches for as well.
 *
 * <p>Nothing can be checked back against the server once the move has happened, either:
 * {@code ClientPacketListener.handleRespawn} builds a new {@code LocalPlayer}, and the cooldown map
 * lives on the player, so vanilla's own reading is empty on the far side whatever the server still
 * thinks. The clear stands until the ability procs again; {@code /aletheia ngift reset} is there for
 * when it needs ending by hand.
 */
public final class NaturesGift {
	private NaturesGift() {
	}

	private static boolean equipped;
	private static int remainingSeconds;

	/**
	 * How full a freshly seen cooldown has to be to count as the ability firing, rather than one that
	 * was already running. On a 360 second ability this leaves ten seconds of slack, which is far more
	 * than the tick it normally takes to notice, and still nowhere near the fraction a relog part-way
	 * through a cooldown would report.
	 */
	private static final float PROC_PERCENT = 0.97F;

	/**
	 * When each running count ends, as wall-clock time, keyed by the cooldown group -- the id vanilla
	 * files the cooldown under, read off the item.
	 *
	 * <p>Keyed by group rather than by slot so a count belongs to the <i>piece</i>: swapping to
	 * another ability item leaves the first one's time running rather than throwing it away, and
	 * swapping back picks it up where it got to. Two pieces sharing a group share a count, which is
	 * exactly what the server does with them.
	 */
	private static final Map<Identifier, Long> endsAtByGroup = new HashMap<>();

	/** The ability boots the readout is about, kept after they leave the slot. */
	private static ItemStack tracked = ItemStack.EMPTY;
	private static int trackedTotalSeconds;
	private static Identifier trackedGroup;

	/** The dimension the player was in last tick, to notice a change of dungeon or world. */
	private static ResourceKey<Level> lastDimension;

	/** Kept for the diagnostic command. */
	private static String lastItemName = "";
	private static List<String> lastLoreLines = List.of();
	private static float lastPercent;
	private static int lastTotalSeconds;
	private static String lastClearReason = "";
	private static long lastClearedAtMillis;

	/** Hooks up the tick, plus the two connection events that end a timer. */
	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(NaturesGift::tick);

		// A change of server is a second login on the same connection, which is where this fires
		// again -- so it covers hub to realm and realm to realm, not just the first join.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clear("joined a world"));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear("left the server"));
	}

	public static void tick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null) {
			forget();
			return;
		}

		followLocation(player);

		ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);

		// Always recorded, matched or not, so /aletheia ngift can explain why nothing is showing.
		remember(boots);

		lastTotalSeconds = CooldownText.parseTotalSeconds(lastLoreLines)
				.orElse(boots.isEmpty() ? 0 : AletheiaConfig.giftFallbackCooldownSeconds);

		long now = System.currentTimeMillis();
		endsAtByGroup.values().removeIf(endsAt -> endsAt <= now);

		equipped = isTrackedItem(boots, lastTotalSeconds);
		if (equipped) {
			// Re-taken every tick, so a swap to a different ability piece hands the readout over.
			tracked = boots.copy();
			trackedGroup = player.getCooldowns().getCooldownGroup(boots);
			trackedTotalSeconds = lastTotalSeconds;
		} else if (trackedGroup != null && !endsAtByGroup.containsKey(trackedGroup)) {
			// Nothing left to count and nothing worn to count for: stop naming boots that are put away.
			stopFollowing();
		}

		// 0 when the server has no cooldown for these boots -- which happens both when the ability is
		// genuinely ready and when they are simply not being worn, so it is never read as "ready".
		lastPercent = tracked.isEmpty() ? 0.0F : player.getCooldowns().getCooldownPercent(tracked, 0.0F);
		if (lastPercent > 0.0F && trackedGroup != null) {
			boolean wasCounting = endsAtByGroup.containsKey(trackedGroup);

			long serverEndsAt = now + (long) (lastPercent * trackedTotalSeconds * 1000.0F);
			if (serverEndsAt > endsAtByGroup.getOrDefault(trackedGroup, 0L)) {
				endsAtByGroup.put(trackedGroup, serverEndsAt);
			}

			// A count starting from (near enough) the full cooldown is the ability going off. The
			// fullness test is what separates a proc from a cooldown that was already running and has
			// only just come back into view -- a relog part-way through hands back whatever is left of
			// it, and announcing that would be a lie about when the ability fired.
			if (!wasCounting && lastPercent >= PROC_PERCENT
					&& AletheiaConfig.enabled && AletheiaConfig.showGiftProcTitle) {
				showProcTitle(trackedTotalSeconds);
			}
		}

		long endsAt = trackedGroup == null ? 0L : endsAtByGroup.getOrDefault(trackedGroup, 0L);
		remainingSeconds = endsAt <= now ? 0 : (int) Math.ceil((endsAt - now) / 1000.0);
	}

	/**
	 * Ends the count when the player changes dungeon or world, which the server treats as ending the
	 * cooldown. Both reach the client as a change of dimension -- {@code telos:realm} to
	 * {@code telos:neo_eden/1} and so on.
	 */
	private static void followLocation(LocalPlayer player) {
		ResourceKey<Level> dimension = player.level().dimension();
		if (lastDimension != null && !lastDimension.equals(dimension)) {
			clear("moved from " + lastDimension.identifier() + " to " + dimension.identifier());
		}
		lastDimension = dimension;
	}

	/** Drops every running count and whatever they were counting for. */
	private static void clear(String reason) {
		boolean wasRunning = !endsAtByGroup.isEmpty();

		endsAtByGroup.clear();
		remainingSeconds = 0;
		lastPercent = 0.0F;
		stopFollowing();

		lastClearReason = reason;
		lastClearedAtMillis = System.currentTimeMillis();
		if (wasRunning && AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("Nature's Gift timer cleared -- {}", reason);
		}
	}

	private static void stopFollowing() {
		tracked = ItemStack.EMPTY;
		trackedGroup = null;
		trackedTotalSeconds = 0;
	}

	/** Clears the timer by hand, for {@code /aletheia ngift reset}. */
	public static void resetByHand() {
		clear("reset by hand");
	}

	/** Dropped when there is no player -- the title screen, or a connection ending. */
	private static void forget() {
		equipped = false;
		lastDimension = null;
		remainingSeconds = 0;
		lastPercent = 0.0F;

		// Guarded so sitting on the title screen does not overwrite the reason the timer really ended.
		if (!endsAtByGroup.isEmpty() || !tracked.isEmpty()) {
			clear("no player");
		}
	}

	/**
	 * Identifies an ability piece by the fact that its lore states a cooldown, rather than by name.
	 *
	 * <p>Item names come through a custom resource-pack font -- private-use glyphs, small capitals,
	 * kerning characters, typographic apostrophes -- so matching on the text is fragile and was the
	 * reason this never triggered. The cooldown stat is plain and reliable, and it is what makes a
	 * piece worth tracking anyway.
	 *
	 * <p>The name filter is only an optional narrowing, for wearing more than one ability piece.
	 */
	private static boolean isTrackedItem(ItemStack stack, int totalSeconds) {
		if (stack.isEmpty() || totalSeconds <= 0) {
			return false;
		}

		String wanted = ChatText.lettersAndDigits(AletheiaConfig.giftNameFilter);
		if (wanted.isEmpty()) {
			return true;
		}
		return ChatText.lettersAndDigits(stack.getHoverName().getString()).contains(wanted);
	}

	private static List<String> loreOf(ItemStack stack) {
		List<String> lines = new ArrayList<>();
		for (Component line : stack.getOrDefault(DataComponents.LORE, ItemLore.EMPTY).lines()) {
			lines.add(line.getString());
		}
		return lines;
	}

	private static void remember(ItemStack stack) {
		lastItemName = stack.isEmpty() ? "" : stack.getHoverName().getString();
		lastLoreLines = stack.isEmpty() ? List.of() : List.copyOf(loreOf(stack));
	}

	/** What the boots slot holds, whether or not it is being tracked. Used by {@code /aletheia ngift}. */
	public static ItemStack bootsStack() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? ItemStack.EMPTY : player.getItemBySlot(EquipmentSlot.FEET);
	}

	public static boolean isEquipped() {
		return equipped;
	}

	/** The boots the timer belongs to, worn or not; empty when nothing is being counted. */
	public static ItemStack trackedStack() {
		return tracked;
	}

	/** @return seconds until the ability is usable again; {@code 0} means ready */
	public static int remainingSeconds() {
		return remainingSeconds;
	}

	/**
	 * Announces a proc. Public and unconditional so {@code /aletheia test ngift} can show exactly what
	 * a real one looks like; the setting that switches it off is checked by the caller in {@link #tick}.
	 *
	 * @param totalSeconds the ability's full cooldown, for the {@code {time}} placeholder
	 */
	public static void showProcTitle(int totalSeconds) {
		String[] fields = {"time", formatTime(totalSeconds)};
		Alerts.showTitle(
				Alerts.fill(AletheiaConfig.giftProcTitleText, fields),
				Alerts.fill(AletheiaConfig.giftProcSubtitle, fields),
				AletheiaConfig.colour(AletheiaConfig.giftProcColour),
				AletheiaConfig.titleStayTicks);
		if (AletheiaConfig.giftProcSound) {
			Alerts.playSound();
		}
	}

	/** In whichever of the two formats the readout is set to, so a title and the line below it agree. */
	private static String formatTime(int seconds) {
		return AletheiaConfig.giftTimeFormat == 0
				? CooldownText.asClock(seconds)
				: CooldownText.asSeconds(seconds);
	}

	/** e.g. {@code ngift: ready} or {@code ngift: 5:23}. */
	public static String statusLine() {
		String label = AletheiaConfig.giftLabel == null || AletheiaConfig.giftLabel.isBlank()
				? "ngift"
				: AletheiaConfig.giftLabel.trim();

		if (remainingSeconds > 0) {
			return label + ": " + formatTime(remainingSeconds);
		}

		// An empty ready text means the label alone -- the colon would be left dangling otherwise.
		String ready = AletheiaConfig.giftReadyText == null ? "" : AletheiaConfig.giftReadyText.trim();
		return ready.isEmpty() ? label : label + ": " + ready;
	}

	public static String lastItemName() {
		return lastItemName;
	}

	public static List<String> lastLoreLines() {
		return lastLoreLines;
	}

	public static float lastCooldownPercent() {
		return lastPercent;
	}

	public static int lastTotalSeconds() {
		return lastTotalSeconds;
	}

	/** Ability pieces still counting down that the readout is not currently about. */
	public static int otherCounts() {
		int others = 0;
		for (Identifier group : endsAtByGroup.keySet()) {
			if (!group.equals(trackedGroup)) {
				others++;
			}
		}
		return others;
	}

	/** The dimension the timer is watching for a change, e.g. {@code telos:neo_eden/1}. */
	public static String currentDimension() {
		return lastDimension == null ? "" : lastDimension.identifier().toString();
	}

	/** Why the timer was last dropped, and how long ago -- empty if it never has been. */
	public static String lastClear() {
		if (lastClearReason.isEmpty()) {
			return "";
		}
		long secondsAgo = Math.max(0L, (System.currentTimeMillis() - lastClearedAtMillis) / 1000L);
		return lastClearReason + " (" + secondsAgo + "s ago)";
	}
}
