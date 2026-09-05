package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

/** {@code Cherubim 63% → BLACK HOLE}: where the fight is, and what it turns into next. */
public final class BossPhaseHud extends AletheiaHud {
	public static final BossPhaseHud INSTANCE = new BossPhaseHud();

	private BossPhaseHud() {
	}

	private static final int IMMINENT_COLOUR = 0xFFFF5555;
	private static final int CLOSE_COLOUR = 0xFFFFAA00;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "boss_phase");
	}

	@Override
	public String label() {
		return "Boss phase";
	}

	@Override
	@Nullable
	public String text() {
		return BossPhaseTracker.statusLine();
	}

	/** Reddens as the threshold comes up, so the readout warns even with the titles switched off. */
	@Override
	public int colour() {
		BossPhases.Phase next = BossPhaseTracker.next();
		if (next == null) {
			return AletheiaConfig.argb(AletheiaConfig.phaseHudColour);
		}

		double lead = Math.max(1, AletheiaConfig.phaseWarningLead) / 100.0;
		double away = BossPhaseTracker.progress() - next.at();
		if (away <= lead) {
			return IMMINENT_COLOUR;
		}
		return away <= lead * 3 ? CLOSE_COLOUR : AletheiaConfig.argb(AletheiaConfig.phaseHudColour);
	}

	@Override
	public boolean shownInGame() {
		return AletheiaConfig.enabled && AletheiaConfig.showPhaseHud && BossPhaseTracker.inFight();
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showPhaseHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showPhaseHud = value;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.phaseAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.phaseAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.phaseOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.phaseOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.phaseOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.phaseOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.phaseScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.phaseScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.phaseAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.phaseOffsetX = 4;
		AletheiaConfig.phaseOffsetY = 28;
		AletheiaConfig.phaseScale = 100;
	}
}
