package dev.landofif.aletheia.afterburner;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Counts down Afterburner's <b>second</b> shot -- the fire that lands a few seconds after the balls.
 *
 * <p>One press of the cloak fires twice. The balls go out as you press it, and the fire follows on a
 * fixed delay, which means the only thing standing between you and knowing when it lands is a clock.
 * This starts one the moment the ability goes off and says so when the fire arrives.
 *
 * <p><b>The delay is measured, not documented</b> -- 7.0 seconds, timed in game on 2026-08-22 -- so
 * it is a setting rather than a constant here. If a patch retunes the cloak, that number is the one
 * to change, and nothing else has to move.
 *
 * <p><b>Spotting the cast</b> works the way {@link dev.landofif.aletheia.gift.NaturesGift} spots a
 * proc: off the vanilla item cooldown system, which is what Telos starts its abilities through. Two
 * things differ. An ability item is <i>held</i> rather than worn -- main hand or off hand, which is
 * where Melinoe looks for it too -- and it is recognised by its <b>item model</b>
 * ({@code telos:material/ability/cloak/ut-fire}) rather than by its name, since names arrive in the
 * pack's own font and are a poor thing to match on.
 *
 * <p>A <b>rise</b> in the cooldown reading is the cast. The reading only ever falls while a cooldown
 * runs, so any increase is the ability going off again -- which works whatever the cloak's cooldown
 * happens to be, and keeps working if a patch changes it. Nothing here needs to know that number.
 *
 * <p>An ability that comes into view already part-way through its cooldown -- switched into the hand,
 * or a relog mid-fight -- is picked up where it stands rather than announced from the start: the lore
 * says how long the cooldown runs for, the reading says how much of it is left, and the difference is
 * how long ago the ability fired. If the fire is still to come, the count is joined in progress.
 *
 * <p>Because the count is the mod's own, the things that end a fight end it too: a change of
 * dimension (which is how entering a dungeon, moving between its rooms and being sent to another
 * world all arrive) and a fresh login. {@code /aletheia burn start} is there for the other direction,
 * when the cast is not seen at all -- bind it to a key and it is as good as the reading.
 */
public final class Afterburner {
	private Afterburner() {
	}

	/**
	 * How much of a rise in the cooldown reading counts as a cast, so the last bit of float noise in
	 * a falling reading can never look like one.
	 */
	private static final float CAST_RISE = 0.001F;

	/** What a Telos ability item's model path always contains, whichever ability it is. */
	private static final String ANY_ABILITY = "ability";

	/** When the fire lands, as wall-clock time. {@code 0} while nothing is counting. */
	private static long fireAtMillis;

	/** Whether the ability being watched for is in a hand right now. */
	private static boolean held;

	private static float lastPercent;
	private static Identifier lastGroup;

	/** The dimension the player was in last tick, to notice a change of dungeon or world. */
	private static ResourceKey<Level> lastDimension;

	/** Kept for {@code /aletheia burn}. */
	private static String lastItemName = "";
	private static String lastModelId = "";
	private static List<String> lastLoreLines = List.of();
	private static int lastTotalSeconds;
	/** What last happened to the count, started or dropped, for {@code /aletheia burn}. */
	private static String lastCountNote = "";
	private static long lastFiredAtMillis;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(Afterburner::tick);

		// A change of server is a second login on the same connection, so this covers hub to realm and
		// realm to realm as well as the first join.
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

		ItemStack ability = heldAbility(player);
		held = !ability.isEmpty();
		remember(ability);

		// 0 both when the ability is ready and when nothing matching is in hand, so it is never read as
		// the cooldown having ended -- only a rise is ever acted on.
		float percent = ability.isEmpty() ? 0.0F : player.getCooldowns().getCooldownPercent(ability, 0.0F);
		Identifier group = ability.isEmpty() ? null : player.getCooldowns().getCooldownGroup(ability);
		boolean sameAbility = group != null && group.equals(lastGroup);

		if (percent > 0.0F) {
			if (sameAbility) {
				if (percent > lastPercent + CAST_RISE) {
					start("the ability fired", delayMillis());
				}
			} else {
				// First sight of this ability's cooldown. It may be a cast we were not watching for, so
				// what is left of the delay is worked out rather than assumed -- see the class comment.
				joinInProgress(percent);
			}
		}

		lastPercent = percent;
		lastGroup = group;

		if (fireAtMillis > 0L && System.currentTimeMillis() >= fireAtMillis) {
			fire();
		}
	}

	/** The ability item in hand, main hand first, or empty if neither hand holds the one wanted. */
	private static ItemStack heldAbility(LocalPlayer player) {
		ItemStack main = player.getMainHandItem();
		if (matches(main)) {
			return main;
		}
		ItemStack off = player.getOffhandItem();
		return matches(off) ? off : ItemStack.EMPTY;
	}

	/**
	 * Whether a stack is the ability this is counting for.
	 *
	 * <p>The <b>item model</b> is what is matched: {@code telos:material/ability/cloak/ut-fire} is
	 * plain ASCII the server sets itself, where the name on the item is drawn in the pack's font and
	 * folds badly. The name is tried as well, so a filter typed as "afterburner" works too -- which is
	 * what anyone reading the settings screen will type first.
	 *
	 * <p>An empty filter means <b>any</b> ability item, since every one of them carries {@code ability}
	 * in its model path. That is the setting to use for a different two-stage ability, or to find out
	 * what the one in your hand is called.
	 */
	private static boolean matches(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}

		String model = modelOf(stack).toLowerCase(Locale.ROOT);
		String filter = AletheiaConfig.burnItemFilter == null ? "" : AletheiaConfig.burnItemFilter.trim();
		if (filter.isEmpty()) {
			return model.contains(ANY_ABILITY);
		}
		if (model.contains(filter.toLowerCase(Locale.ROOT))) {
			return true;
		}

		String wanted = ChatText.lettersAndDigits(filter);
		return !wanted.isEmpty()
				&& ChatText.lettersAndDigits(stack.getHoverName().getString()).contains(wanted);
	}

	/** e.g. {@code telos:material/ability/cloak/ut-fire}, or empty for an item with no model set. */
	private static String modelOf(ItemStack stack) {
		if (stack.isEmpty()) {
			return "";
		}
		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		return model == null ? "" : model.toString();
	}

	/**
	 * Picks up a cast already under way, from a cooldown seen for the first time part-run.
	 *
	 * <p>Silent on purpose: the fire is still announced when it lands, but nothing claims the ability
	 * has just been pressed, because it has not. Needs the cooldown's full length off the lore -- with
	 * no {@code Cooldown} stat there is nothing to measure the reading against, and a guess here would
	 * put the fire at the wrong moment, which is worse than saying nothing.
	 */
	private static void joinInProgress(float percent) {
		if (lastTotalSeconds <= 0) {
			return;
		}

		long sinceCast = (long) ((1.0F - percent) * lastTotalSeconds * 1000.0F);
		long left = delayMillis() - sinceCast;
		if (left > 0L) {
			start("picked up a cast already " + (sinceCast / 1000L) + "s old", left);
		}
	}

	private static void start(String reason, long millis) {
		fireAtMillis = System.currentTimeMillis() + millis;
		lastCountNote = "started -- " + reason;
		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("Afterburner: counting {}ms to the fire -- {}", millis, reason);
		}
	}

	/** Starts a count by hand, for {@code /aletheia burn start}. */
	public static void startByHand() {
		start("started by hand", delayMillis());
	}

	/** Ends a count by hand, for {@code /aletheia burn reset}. */
	public static void resetByHand() {
		clear("reset by hand");
	}

	private static void fire() {
		fireAtMillis = 0L;
		lastFiredAtMillis = System.currentTimeMillis();

		if (AletheiaConfig.enabled && AletheiaConfig.showBurnFireTitle) {
			showFireTitle();
		}
	}

	/**
	 * Announces the fire. Public and unconditional so {@code /aletheia test burn} can show exactly what
	 * a real one looks like; the setting that switches it off is checked by {@link #fire}.
	 */
	public static void showFireTitle() {
		String[] fields = {"time", CooldownText.asTenths(AletheiaConfig.burnDelayTenths)};
		Alerts.showTitle(
				Alerts.fill(AletheiaConfig.burnFireTitleText, fields),
				Alerts.fill(AletheiaConfig.burnFireSubtitle, fields),
				AletheiaConfig.colour(AletheiaConfig.burnFireColour),
				AletheiaConfig.titleStayTicks);
		if (AletheiaConfig.burnFireSound) {
			Alerts.playSound();
		}
	}

	/**
	 * Ends the count when the player changes dungeon or world. Both reach the client as a change of
	 * dimension, and neither leaves a shot in the air worth counting.
	 */
	private static void followLocation(LocalPlayer player) {
		ResourceKey<Level> dimension = player.level().dimension();
		if (lastDimension != null && !lastDimension.equals(dimension) && fireAtMillis > 0L) {
			clear("moved from " + lastDimension.identifier() + " to " + dimension.identifier());
		}
		lastDimension = dimension;
	}

	private static void clear(String reason) {
		if (fireAtMillis > 0L && AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("Afterburner count dropped -- {}", reason);
		}
		fireAtMillis = 0L;
		lastCountNote = "dropped -- " + reason;
	}

	/** Dropped when there is no player -- the title screen, or a connection ending. */
	private static void forget() {
		held = false;
		lastPercent = 0.0F;
		lastGroup = null;
		lastDimension = null;
		fireAtMillis = 0L;
	}

	private static long delayMillis() {
		return Math.max(1, AletheiaConfig.burnDelayTenths) * 100L;
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
		lastModelId = modelOf(stack);
		lastLoreLines = stack.isEmpty() ? List.of() : List.copyOf(loreOf(stack));
		lastTotalSeconds = CooldownText.parseTotalSeconds(lastLoreLines).orElse(0);
	}

	// ------------------------------------------------------------------ the readout

	/** @return tenths of a second until the fire lands; {@code 0} means nothing is counting */
	public static int remainingTenths() {
		if (fireAtMillis <= 0L) {
			return 0;
		}
		long left = fireAtMillis - System.currentTimeMillis();
		return left <= 0L ? 0 : (int) Math.ceil(left / 100.0);
	}

	/** Whether a count is running, which is the only time the readout has anything to say. */
	public static boolean counting() {
		return fireAtMillis > 0L;
	}

	/** Whether the ability is in a hand, so the readout can stay up while it is worth watching. */
	public static boolean isHeld() {
		return held;
	}

	/** e.g. {@code burn: 3.4s}, or {@code burn: ready}. */
	public static String statusLine() {
		String label = AletheiaConfig.burnLabel == null || AletheiaConfig.burnLabel.isBlank()
				? "burn"
				: AletheiaConfig.burnLabel.trim();

		int tenths = remainingTenths();
		if (tenths > 0) {
			return label + ": " + CooldownText.asTenths(tenths);
		}

		// An empty ready text means the label alone -- the colon would be left dangling otherwise.
		String ready = AletheiaConfig.burnReadyText == null ? "" : AletheiaConfig.burnReadyText.trim();
		return ready.isEmpty() ? label : label + ": " + ready;
	}

	// ------------------------------------------------------------------ /aletheia burn

	/**
	 * One hand and what it is holding, for the diagnostic.
	 *
	 * @param matched whether this is the ability being counted for
	 * @param percent the vanilla cooldown reading on it, whether or not it is matched
	 */
	public record Held(String slot, String name, String model, boolean matched, float percent) {
	}

	/** Both hands, so the command can say what is in them and why neither is being followed. */
	public static List<Held> hands() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return List.of();
		}

		List<Held> hands = new ArrayList<>(2);
		hands.add(describe(player, "Main hand", player.getMainHandItem()));
		hands.add(describe(player, "Off hand", player.getOffhandItem()));
		return hands;
	}

	private static Held describe(LocalPlayer player, String slot, ItemStack stack) {
		return new Held(
				slot,
				stack.isEmpty() ? "" : stack.getHoverName().getString(),
				modelOf(stack),
				matches(stack),
				stack.isEmpty() ? 0.0F : player.getCooldowns().getCooldownPercent(stack, 0.0F));
	}

	public static String lastItemName() {
		return lastItemName;
	}

	/** The model id of the ability being followed, which is what the filter is matched against. */
	public static String lastModelId() {
		return lastModelId;
	}

	public static List<String> lastLoreLines() {
		return lastLoreLines;
	}

	public static float lastCooldownPercent() {
		return lastPercent;
	}

	/** The cloak's own cooldown off its lore -- not the delay, but what a cast is measured against. */
	public static int lastTotalSeconds() {
		return lastTotalSeconds;
	}

	/** What last happened to the count and why -- empty if it has never run. */
	public static String lastCount() {
		return lastCountNote;
	}

	/** How long ago the fire last landed, in seconds, or {@code -1} if it has not this session. */
	public static long secondsSinceFire() {
		return lastFiredAtMillis == 0L
				? -1L
				: Math.max(0L, (System.currentTimeMillis() - lastFiredAtMillis) / 1000L);
	}

	/** The dimension the count is watching for a change, e.g. {@code telos:neo_eden/1}. */
	public static String currentDimension() {
		return lastDimension == null ? "" : lastDimension.identifier().toString();
	}
}
