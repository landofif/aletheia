package dev.landofif.aletheia.gift;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.hud.AletheiaHud;
import net.minecraft.resources.Identifier;

/** The Nature's Gift readout: what it says, and what colour it says it in. */
public final class NaturesGiftHud extends AletheiaHud {
	public static final NaturesGiftHud INSTANCE = new NaturesGiftHud();

	private NaturesGiftHud() {
	}

	private static final int READY_COLOUR = 0xFF55FF55;
	private static final int COOLDOWN_COLOUR = 0xFFFF5555;
	private static final int NEARLY_READY_COLOUR = 0xFFFFAA00;

	/** Below this many seconds the readout turns amber, as a heads-up that it is about to come back. */
	private static final int NEARLY_READY_SECONDS = 15;

	@Override
	public Identifier id() {
		return Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "natures_gift");
	}

	@Override
	public String label() {
		return "Nature's Gift";
	}

	@Override
	public String text() {
		return NaturesGift.statusLine();
	}

	@Override
	public int colour() {
		int seconds = NaturesGift.remainingSeconds();
		if (seconds <= 0) {
			return READY_COLOUR;
		}
		return seconds <= NEARLY_READY_SECONDS ? NEARLY_READY_COLOUR : COOLDOWN_COLOUR;
	}

	@Override
	public boolean shownInGame() {
		if (!AletheiaConfig.enabled || !AletheiaConfig.showGiftHud) {
			return false;
		}
		// The second test is what keeps the line up after a swap: the timer is the mod's own, so it
		// runs on with the boots in your bag, and hiding it there would look exactly like the reset
		// this replaced.
		return NaturesGift.isEquipped()
				|| NaturesGift.remainingSeconds() > 0
				|| AletheiaConfig.giftAlwaysShow;
	}

	@Override
	public boolean enabled() {
		return AletheiaConfig.showGiftHud;
	}

	@Override
	public void enabled(boolean value) {
		AletheiaConfig.showGiftHud = value;
	}

	@Override
	public int anchor() {
		return AletheiaConfig.giftAnchor;
	}

	@Override
	public void anchor(int value) {
		AletheiaConfig.giftAnchor = value;
	}

	@Override
	public int offsetX() {
		return AletheiaConfig.giftOffsetX;
	}

	@Override
	public void offsetX(int value) {
		AletheiaConfig.giftOffsetX = value;
	}

	@Override
	public int offsetY() {
		return AletheiaConfig.giftOffsetY;
	}

	@Override
	public void offsetY(int value) {
		AletheiaConfig.giftOffsetY = value;
	}

	@Override
	public int scalePercent() {
		return AletheiaConfig.giftScale;
	}

	@Override
	public void scalePercent(int value) {
		AletheiaConfig.giftScale = value;
	}

	@Override
	public void resetPlacement() {
		AletheiaConfig.giftAnchor = ANCHOR_TOP_LEFT;
		AletheiaConfig.giftOffsetX = 4;
		AletheiaConfig.giftOffsetY = 4;
		AletheiaConfig.giftScale = 100;
	}
}
