package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.boss.BossBar;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps the fight's own bar off the top of the screen while the mod is drawing it somewhere else.
 *
 * <p>Done by filtering the collection the overlay walks rather than by cancelling a draw, because the
 * overlay stacks the bars down the screen as it goes: skipping one halfway through would leave a gap
 * where it had been. Taking it out of the list instead moves the ones below it up, exactly as they
 * move when the server takes a bar away itself.
 *
 * <p><b>Only the fight's bar goes.</b> Telos sends its entire HUD as boss bars -- the potion counter
 * among them -- so which of them is the fight is {@link BossBar}'s decision, made once a tick; this
 * looks each one up and nothing more.
 *
 * <p>Filtered at render time, so nothing the server sent is thrown away and turning the setting off
 * puts the bar back on the next frame.
 */
@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayHideMixin {

	@Redirect(
			method = "extractRenderState",
			at = @At(value = "INVOKE", target = "Ljava/util/Map;values()Ljava/util/Collection;"))
	private Collection<LerpingBossEvent> aletheia$hideReplacedBars(Map<UUID, LerpingBossEvent> events) {
		Collection<LerpingBossEvent> all = events.values();
		if (!BossBar.hidingAnything()) {
			return all;
		}

		List<LerpingBossEvent> kept = new ArrayList<>(all.size());
		for (LerpingBossEvent event : all) {
			if (!BossBar.hides(event.getId())) {
				kept.add(event);
			}
		}
		return kept;
	}
}
