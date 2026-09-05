package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which of the bars on screen are the fight, and everything {@link BossBarHud} needs to draw them
 * again.
 *
 * <p>The mod's own bar is not a second reading of anything: it is the server's bar, moved. The health
 * comes off the same {@link LerpingBossEvent} the vanilla overlay draws, and the state comes off
 * {@link Vulnerability}, which reads it from the fill over the boss's head. What changes is where it
 * sits, how big it is and what it is coloured by -- the server's own bar says nothing about
 * invulnerability at all, since the pack's artwork covers the one field that could have.
 *
 * <p><b>A fight can be more than one bar.</b> Apostle and Hierophant are a single encounter with a
 * boss and a bar each, and the server sends them as two bars on two lines -- so every boss bar on
 * screen is followed rather than only the first, and {@link BossBarHud} redraws them one to a line as
 * well. Both of those also die at half health, which the reading is corrected for; see
 * {@link BossPhases#endsAt}.
 *
 * <p>Which bars were picked up is written to the log whenever the answer changes, since a fight is a
 * bad time to be typing {@code /aletheia boss} and "the second boss never appeared" and "the second
 * boss appeared and was drawn wrong" are otherwise the same sentence.
 *
 * <p><b>Which bars are hidden</b> is decided here too, because it is the same question. Telos draws
 * its entire HUD as boss bars (see {@link dev.landofif.aletheia.serverhud.ServerHud}), so hiding "the
 * boss bar" has to mean hiding the fight's and nothing else -- the potion counter arrives the same
 * way. The set is worked out once a tick and the mixin only looks bars up in it, so nothing walks the
 * pack's glyph definitions once a frame.
 */
public final class BossBar {
	private BossBar() {
	}

	/**
	 * How many bars are drawn again at once. Two is the case that matters and the rest is room to
	 * spare; past this a bar is still hidden, since one left behind at the top of the screen on its own
	 * is worse than one missing from a row of four.
	 */
	private static final int MAX_BARS = 4;

	/**
	 * One bar being drawn again: what it is called, when its fight ends, and the event it reads from --
	 * {@code null} for the sample the editor places when there is no fight.
	 */
	public record Fight(String name, double endsAt, @Nullable LerpingBossEvent bar) {

		/**
		 * How full to draw it, read at draw time rather than on the tick: {@link LerpingBossEvent} slides
		 * towards its true value over a fifth of a second, and sampling it once a tick would step.
		 *
		 * <p>Rescaled for a fight that dies with health still on the bar, so what is drawn is what there
		 * is left to take.
		 */
		public float progress() {
			return BossPhases.remaining(sent(), AletheiaConfig.bossBarTrueHealth ? endsAt : 0.0);
		}

		/** The reading the server sent, before any rescaling -- for {@code /aletheia boss}. */
		public float sent() {
			return bar == null ? previewProgress : bar.getProgress();
		}

		/** Whether this bar is being rescaled at all, which is the only time the two readings differ. */
		public boolean endsEarly() {
			return endsAt > 0.0 && AletheiaConfig.bossBarTrueHealth;
		}
	}

	/** The bars being drawn again, in the order the server stacked them. */
	private static List<Fight> fights = List.of();

	/** The ids the vanilla overlay is to skip. Replaced wholesale each tick, never edited in place. */
	private static Set<UUID> hidden = Set.of();

	/** Where the sample bar's health stands, for placing it without a fight. */
	private static float previewProgress = 1.0F;

	/** What the editor draws when nothing is up, so the bar can be placed out of a fight. */
	private static final List<Fight> PREVIEW = List.of(new Fight("Boss", 0.0, null));

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(BossBar::tick);
	}

	private static void tick(Minecraft client) {
		if (!AletheiaConfig.enabled || client.player == null) {
			forget();
			return;
		}

		List<Fight> found = new ArrayList<>(2);
		Set<UUID> bosses = new HashSet<>();
		int onScreen = 0;
		for (LerpingBossEvent event : BossBars.onScreen()) {
			onScreen++;
			if (!BossBars.isBoss(event)) {
				continue;
			}
			bosses.add(event.getId());
			if (found.size() < MAX_BARS) {
				found.add(fightFor(event));
			}
		}

		hidden = bosses;
		fights = List.copyOf(found);
		note(found, onScreen);

		// The sample creeps down so the fill is visibly a fill while it is being placed.
		previewProgress = Vulnerability.previewing()
				? Math.max(0.05F, previewProgress - 0.004F)
				: 0.62F;
	}

	/**
	 * The fight a bar belongs to, kept from last tick where it is the same bar: naming one means walking
	 * the pack's font definition for the picture behind its glyph, and the answer cannot change under a
	 * live bar.
	 */
	private static Fight fightFor(LerpingBossEvent event) {
		for (Fight known : fights) {
			if (known.bar() == event) {
				return known;
			}
		}
		return new Fight(BossBars.displayName(event), BossBars.endsAtFor(event), event);
	}

	private static void forget() {
		fights = List.of();
		hidden = Set.of();
		noted = "";
	}

	/** The last thing {@link #note} said, so a fight in progress is not written down every tick. */
	private static String noted = "";

	/** An identity is a texture list and can run long; the front of it is enough to recognise a bar. */
	private static final int NOTE_LIMIT = 160;

	/**
	 * Writes down which bars are being followed, once per change.
	 *
	 * <p>This is here for the fights with two bosses in them. Afterwards, "the second bar was not
	 * drawn" and "the second bar never reached the client" look exactly the same on screen, and
	 * {@code /aletheia boss} can only answer while the fight is still up -- which is the one time you
	 * are not going to be typing it. So the bars that were not taken for bosses are written out
	 * alongside, and the log says which of the two it was.
	 *
	 * <p>Costs nothing while nothing is up: the common case is caught by the first line, and the walk
	 * over the bars that were left behind only happens on the tick the answer changes.
	 */
	private static void note(List<Fight> found, int onScreen) {
		if (found.isEmpty() && noted.isEmpty()) {
			return;
		}

		StringBuilder names = new StringBuilder();
		for (Fight fight : found) {
			names.append(names.isEmpty() ? "" : ", ").append(fight.name().isBlank() ? "(unnamed)" : fight.name());
		}

		String now = names.toString();
		if (now.equals(noted)) {
			return;
		}
		noted = now;
		if (found.isEmpty()) {
			return;
		}

		Aletheia.LOGGER.info("boss bars: following {} of the {} on screen -- {}", found.size(), onScreen, now);
		for (LerpingBossEvent event : BossBars.onScreen()) {
			if (BossBars.isBoss(event)) {
				continue;
			}
			String identity = BossBars.identityOf(event);
			if (identity.isBlank()) {
				continue;
			}
			Aletheia.LOGGER.info("  left alone: {}",
					identity.length() > NOTE_LIMIT ? identity.substring(0, NOTE_LIMIT) + "..." : identity);
		}
	}

	/**
	 * Whether anything is being taken off the vanilla overlay at all, asked once a frame so the usual
	 * case -- no fight up, or the setting off -- costs nothing but the check.
	 */
	public static boolean hidingAnything() {
		return AletheiaConfig.enabled && AletheiaConfig.showBossBar && AletheiaConfig.hideVanillaBossBar
				&& !hidden.isEmpty();
	}

	/** Whether the vanilla overlay should skip this particular bar. */
	public static boolean hides(UUID id) {
		return hidden.contains(id);
	}

	/** Whether there is a fight to draw. */
	public static boolean live() {
		return !fights.isEmpty();
	}

	/** The bars to draw, which is the sample one while there is no fight. Never empty. */
	public static List<Fight> fights() {
		return fights.isEmpty() ? PREVIEW : fights;
	}

	/** The state the fill over the boss's head is in, which is what the bars are coloured by. */
	public static FillState state() {
		return Vulnerability.state();
	}
}
