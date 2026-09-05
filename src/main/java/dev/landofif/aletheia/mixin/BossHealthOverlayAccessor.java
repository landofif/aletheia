package dev.landofif.aletheia.mixin;

import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.UUID;

/** Reaches the boss bars currently on screen; the overlay only exposes them to its own renderer. */
@Mixin(BossHealthOverlay.class)
public interface BossHealthOverlayAccessor {

	@Accessor("events")
	Map<UUID, LerpingBossEvent> aletheia$events();
}
