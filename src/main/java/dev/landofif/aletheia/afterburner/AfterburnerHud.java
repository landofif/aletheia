package dev.landofif.aletheia.afterburner;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.resources.Identifier;

/** The Afterburner readout: how long until the fire lands, and what colour to say it in. */
public final class AfterburnerHud extends AletheiaHud {
	public static final AfterburnerHud INSTANCE = new AfterburnerHud();

	private AfterburnerHud() {
	}

	private static final int READY_COLOUR = 0xFF55FF55;
	private static final int COUNTING_COLOUR = 0xFFFFAA00;
	private static final int IMMINENT_COLOUR = 0xFFFF5555;

	/**
	 * Under this many tenths the count turns red. A second is about as much warning as you can act
	 * on, and the colour change is easier to catch out of the corner of an eye than the digits are.
	 */
	private static final int IMMINENT_TENTHS = 10;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "afterburner");
	}

	@Override
	public String label() {
		return "Afterburner";
	}

	@Override
	public String text() {
		return Afterburner.statusLine();
	}

	@Override
	public int colour() {
		int tenths = Afterburner.remainingTenths();
		if (tenths <= 0) {
			return READY_COLOUR;
		}
		return tenths <= IMMINENT_TENTHS ? IMMINENT_COLOUR : COUNTING_COLOUR;
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showBurnHud) {
			return false;
		}
		// A count that started while the cloak was in hand carries on if it leaves, the same way the
		// shot in the air does -- so the count keeps the line up whatever is being held now.
		return Afterburner.isHeld() || Afterburner.counting() || AletheiaConfig.burnAlwaysShow;
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showBurnHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showBurnHud = value;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.burnAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.burnAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.burnOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.burnOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.burnOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.burnOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.burnScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.burnScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.burnAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.burnOffsetX = 4;
		AletheiaConfig.burnOffsetY = 28;
		AletheiaConfig.burnScale = 100;
	}
}
