package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.ui.Alerts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.UUID;

/**
 * The calls a party makes for itself off the boss's health bar -- AMBUSH at 65%, DEATHMARK at 40%.
 *
 * <p><b>These are not the fight's phases.</b> {@link BossPhaseTracker} calls what the boss is about to
 * do, and every one of those percentages is the server's: it is in the fight's script and you can be
 * told it wrongly. These two are the group's own, agreed rather than discovered, so they are settings
 * -- a percentage and a wording each -- instead of a table, and they are fired from here rather than
 * folded in beside the phases, where they would read as something the boss does.
 *
 * <p><b>Only in the endgame dungeons.</b> A realm boss is dead before a reminder has finished being
 * read, and a title over every bar in the world teaches you to look past titles, which costs you the
 * ones that matter. Which fights count is {@link BossPhases#endgameKey} -- the eleven bosses of the
 * five dungeon routes -- matched on the pack's artwork like everything else here.
 *
 * <p>Each call is made <b>once per fight</b>, at or below its percentage. There is no lead the way the
 * phase warnings have one: a phase lands whether you are ready or not, so it is worth calling early,
 * while these are yours to make when you like and the percentage is the whole of the instruction. Set
 * it higher if you want more notice.
 */
public final class BossReminders {
	private BossReminders() {
	}

	/** How many calls there are, and which is which. Each has its own two settings. */
	private static final int CALLS = 2;
	private static final int AMBUSH = 0;
	private static final int DEATHMARK = 1;

	/** The bar being watched, or {@code null} when no fight these are called in is on screen. */
	@Nullable
	private static UUID watching;

	/** Where that bar stood last tick, which is how a boss starting again is recognised. */
	private static float progress = 1.0F;

	/**
	 * Which calls have been made for the fight in hand, so a bar wobbling either side of a percentage
	 * -- healing, or the lerp overshooting -- cannot make the same one twice.
	 */
	private static final boolean[] called = new boolean[CALLS];

	/** Which wording {@link #preview} shows next, so running it twice shows both. */
	private static int previewing;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BossReminders::tick);
	}

	private static void tick(Minecraft client) {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showReminderTitles || client.player == null) {
			forget();
			return;
		}

		LerpingBossEvent bar = endgameBar();
		if (bar == null) {
			forget();
			return;
		}

		float now = bar.getProgress();
		if (!bar.getId().equals(watching)) {
			// A different bar: a different fight, or the next stage of the dungeon. Nothing called yet.
			watching = bar.getId();
			Arrays.fill(called, false);
			if (AletheiaConfig.debugLogging) {
				Aletheia.LOGGER.info("reminders following {} at {}",
						BossBars.displayName(bar), BossPhases.asPercent(now));
			}
		} else if (now > progress + 0.05F) {
			// The same bar back at the top: a fresh boss of the same kind, so the calls come round again.
			Arrays.fill(called, false);
		}

		progress = now;
		announce();
	}

	/**
	 * Makes whichever calls the bar has just dropped past.
	 *
	 * <p>Two landing on the same tick is one title's worth of screen either way, so both are marked as
	 * made and the <b>deeper</b> one is what goes up: it is the later instruction, and the shallower
	 * one is already stale by the time it could be read. Which of the two settings is deeper is not
	 * assumed -- both percentages are yours to set, in either order.
	 */
	private static void announce() {
		String due = null;
		double deepest = Double.MAX_VALUE;

		for (int call = 0; call < CALLS; call++) {
			double at = threshold(call) / 100.0;
			if (called[call] || progress > at) {
				continue;
			}
			called[call] = true;

			String wording = wording(call);
			// An emptied wording is how a call is turned off on its own, and it is still marked as made
			// so that filling it back in mid-fight does not fire it late.
			if (wording.isEmpty() || at >= deepest) {
				continue;
			}
			deepest = at;
			due = wording;
		}

		if (due != null) {
			show(due);
		}
	}

	private static void show(String wording) {
		Alerts.showTitle(
				wording,
				null,
				AletheiaConfig.colour(AletheiaConfig.reminderColour),
				AletheiaConfig.reminderStayTicks);
		if (AletheiaConfig.reminderSound) {
			Alerts.playSound();
		}
	}

	/** The first bar on screen belonging to a fight these calls are made in. */
	@Nullable
	private static LerpingBossEvent endgameBar() {
		for (LerpingBossEvent event : BossBars.onScreen()) {
			if (BossPhases.endgameKey(BossBars.keysOf(event)) != null) {
				return event;
			}
		}
		return null;
	}

	private static void forget() {
		watching = null;
		progress = 1.0F;
		Arrays.fill(called, false);
	}

	/** Where a call is made, held to somewhere a bar can actually reach. */
	private static int threshold(int call) {
		int at = call == AMBUSH ? AletheiaConfig.ambushAt : AletheiaConfig.deathmarkAt;
		return Math.clamp(at, 1, 100);
	}

	/** What it says, or empty for a call that has been switched off by emptying its wording. */
	private static String wording(int call) {
		String text = call == AMBUSH ? AletheiaConfig.ambushText : AletheiaConfig.deathmarkText;
		return text == null ? "" : text.trim();
	}

	// ------------------------------------------------------------------ asking about it

	/** Whether a fight these calls are made in is on screen, for {@code /aletheia boss}. */
	public static boolean inFight() {
		return watching != null;
	}

	/** What the fight on screen is called, or {@code null} -- the same question {@link #inFight} asks. */
	@Nullable
	public static String fightName() {
		LerpingBossEvent bar = endgameBar();
		return bar == null ? null : BossBars.displayName(bar);
	}

	/**
	 * One call's title, for {@code /aletheia test reminder}.
	 *
	 * <p>These fire off a health bar rather than off anything that can be faked from a command, and
	 * only in eleven fights, so this is the only way to see the wording and the colour without standing
	 * in one. A call per run, so both can be looked at.
	 */
	public static void preview() {
		int call = previewing;
		previewing = (previewing + 1) % CALLS;

		String wording = wording(call);
		show(wording.isEmpty() ? "(" + (call == AMBUSH ? "ambush" : "deathmark") + " wording is empty)" : wording);
	}
}
