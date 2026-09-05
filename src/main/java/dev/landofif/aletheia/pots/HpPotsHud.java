package dev.landofif.aletheia.pots;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.resources.Identifier;

/** The potion counter: {@code Pots: 1/5}, placed and sized exactly like the Nature's Gift line. */
public final class HpPotsHud extends AletheiaHud {
	public static final HpPotsHud INSTANCE = new HpPotsHud();

	private HpPotsHud() {
	}

	private static final int EMPTY_COLOUR = 0xFFFF5555;
	private static final int LOW_COLOUR = 0xFFFFAA00;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "hp_pots");
	}

	@Override
	public String label() {
		return "HP pots";
	}

	@Override
	public String text() {
		return HpPots.has() || AletheiaConfig.potsAlwaysShow ? HpPots.statusLine() : null;
	}

	/**
	 * The chosen colour normally, but amber at a third left and red at none -- the point of having the
	 * count on your own HUD is noticing it before it runs out.
	 */
	@Override
	public int colour() {
		int chosen = AletheiaConfig.argb(AletheiaConfig.potsColour);
		if (!AletheiaConfig.potsWarnWhenLow || !HpPots.has()) {
			return chosen;
		}
		if (HpPots.count() <= 0) {
			return EMPTY_COLOUR;
		}
		return HpPots.count() * 3 <= HpPots.total() ? LOW_COLOUR : chosen;
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showPotsHud) {
			return false;
		}
		return HpPots.has() || AletheiaConfig.potsAlwaysShow;
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showPotsHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showPotsHud = value;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.potsAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.potsAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.potsOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.potsOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.potsOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.potsOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.potsScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.potsScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.potsAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.potsOffsetX = 4;
		AletheiaConfig.potsOffsetY = 16;
		AletheiaConfig.potsScale = 100;
	}
}
