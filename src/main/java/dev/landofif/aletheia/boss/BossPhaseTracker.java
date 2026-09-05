package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.serverhud.ServerHud;
import dev.landofif.aletheia.ui.Alerts;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Watches the boss bar and calls the phase changes before they land.
 *
 * <p>The bar is the only thing on screen that already knows how the fight is going, and
 * {@link BossPhases} knows what each percentage means, so the two together give a warning with a
 * second or two of notice -- long enough to stop attacking and start moving, which is the entire
 * difference in these fights.
 *
 * <p>Titles fire at the threshold <b>plus a lead</b>: the black hole at 60% is announced at 63%, so
 * the words are on screen while you still have somewhere to stand. The lead is in percentage points
 * of the bar rather than seconds, since damage is what moves the bar and no clock can predict it.
 *
 * <p>The progress is read <i>unlerped</i> where possible. {@link LerpingBossEvent} animates the bar
 * towards its true value for the look of the thing, and following the animation would announce a
 * phase late by however long the slide takes.
 */
public final class BossPhaseTracker {
	private BossPhaseTracker() {
	}

	/** The fight being followed, and how far into it the announcements have got. */
	@Nullable
	private static BossPhases.Boss boss;
	private static float progress = 1.0F;
	private static boolean live;

	/**
	 * The last phase announced. Kept rather than recomputed so a bar that wobbles either side of a
	 * threshold -- healing, or the lerp overshooting -- cannot announce the same phase twice.
	 */
	@Nullable
	private static BossPhases.Phase announced;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BossPhaseTracker::tick);
	}

	private static void tick(Minecraft client) {
		if (!AletheiaConfig.enabled || client.player == null || client.gui == null) {
			forget();
			return;
		}

		Fight fight = findKnownBoss();
		if (fight == null) {
			forget();
			return;
		}

		BossPhases.Boss found = fight.boss();
		float now = fight.bar().getProgress();
		if (boss != found) {
			// A different fight, or the same one started again: nothing announced yet either way.
			boss = found;
			announced = null;
			live = true;
			if (AletheiaConfig.debugLogging) {
				Aletheia.LOGGER.info("following {} at {}", found.name(), BossPhases.asPercent(now));
			}
		} else if (now > progress + 0.05F) {
			// The bar jumped back up: a fresh boss of the same kind, so the phases come round again.
			announced = null;
		}

		progress = now;
		announce();
	}

	/**
	 * Fires the title for a phase once the bar is within the lead of it, and not again until a
	 * different phase comes up.
	 */
	private static void announce() {
		if (boss == null || !AletheiaConfig.showPhaseTitles) {
			return;
		}

		BossPhases.Phase next = BossPhases.next(boss, progress);
		if (next == null || next.equals(announced)) {
			return;
		}

		double lead = Math.max(0, AletheiaConfig.phaseWarningLead) / 100.0;
		if (progress > next.at() + lead) {
			return;
		}

		announced = next;
		Alerts.showTitle(
				next.name(),
				null,
				AletheiaConfig.colour(AletheiaConfig.phaseColour),
				AletheiaConfig.phaseStayTicks);
		if (AletheiaConfig.phaseSound) {
			Alerts.playSound();
		}
	}

	/** A bar on screen and the fight it turned out to be. */
	private record Fight(LerpingBossEvent bar, BossPhases.Boss boss) {
	}

	/**
	 * The first boss bar this knows the phases of.
	 *
	 * <p>There is always more than one bar to sift through on Telos, since the server draws its whole
	 * HUD as one (see {@link ServerHud}) -- but a bar cannot be told apart by <i>having</i> a
	 * resource-pack font, because the real boss bar has one too. It is picked out by what it is
	 * instead: {@link BossBars#phasesOf} looks the bar's name up as a picture, and only a bar naming a
	 * fight in {@link BossPhases} is followed.
	 */
	@Nullable
	private static Fight findKnownBoss() {
		for (LerpingBossEvent event : BossBars.onScreen()) {
			BossPhases.Boss boss = BossBars.phasesOf(event);
			if (boss != null) {
				return new Fight(event, boss);
			}
		}
		return null;
	}

	private static void forget() {
		boss = null;
		announced = null;
		live = false;
		progress = 1.0F;
	}

	// ------------------------------------------------------------------ the readout

	/** Whether a fight this knows the phases of is on screen. */
	public static boolean inFight() {
		return live && boss != null;
	}

	/** e.g. {@code Cherubim 63% -> BLACK HOLE}, or {@code null} when there is no such fight. */
	@Nullable
	public static String statusLine() {
		if (!inFight()) {
			return null;
		}
		String line = boss.name() + " " + BossPhases.asPercent(progress);

		BossPhases.Phase next = BossPhases.next(boss, progress);
		if (next != null) {
			return line + " → " + next.name();
		}
		BossPhases.Phase current = BossPhases.current(boss, progress);
		return current == null ? line : line + " · " + current.name();
	}

	/** How far through the fight is, for colouring the readout. */
	public static float progress() {
		return progress;
	}

	/**
	 * One bar on screen as the tracker sees it, for {@code /aletheia boss}.
	 *
	 * @param identity what it is matched on -- normally a texture name
	 * @param fight    the fight it was recognised as, or empty
	 * @param colour   the colour the server set on it, e.g. {@code blue} -- which the pack's artwork
	 *                 hides, and which {@link Vulnerability} reads as the boss being untouchable
	 * @param boss     whether this is a boss at all, rather than the server's HUD in a bar's clothing
	 */
	public record Bar(String identity, String fight, float progress, String colour, boolean boss) {
		public boolean known() {
			return !fight.isEmpty();
		}
	}

	/**
	 * Every bar on screen and whether it is one this knows the phases of -- the thing to check when
	 * nothing is being called, since a pack update renaming a glyph is all it would take.
	 */
	public static List<Bar> bars() {
		List<Bar> bars = new ArrayList<>();
		for (LerpingBossEvent event : BossBars.onScreen()) {
			// Asked of the bar rather than of its identity string, so what this prints is the answer the
			// tracker itself is working from -- a diagnostic that decides for itself is worse than none.
			BossPhases.Boss boss = BossBars.phasesOf(event);
			bars.add(new Bar(
					BossBars.identityOf(event).trim(),
					boss == null ? "" : boss.name(),
					event.getProgress(),
					BossBars.colourName(event),
					BossBars.isBoss(event)));
		}
		return bars;
	}

	/** The phase being counted down to, or {@code null} past the last one. */
	@Nullable
	public static BossPhases.Phase next() {
		return boss == null ? null : BossPhases.next(boss, progress);
	}
}
