package dev.landofif.aletheia;

import com.mojang.blaze3d.platform.InputConstants;
import dev.landofif.aletheia.config.ConfigScreen;
import dev.landofif.aletheia.hud.HudEditorScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

/**
 * Keys for the two screens, so neither depends on remembering a command.
 *
 * <p>Both ship <b>unbound</b>. A mod that claims a key on install is a mod that quietly breaks
 * whatever the player already had there; unbound still puts both entries in Controls under the
 * mod's own heading, which is where somebody looks for them anyway.
 */
public final class AletheiaKeys {

	/** Groups both entries under "Aletheia" in Controls rather than scattering them through Misc. */
	private static final KeyMapping.Category CATEGORY =
			KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Aletheia.MOD_ID, "main"));

	private static KeyMapping hudEditor;
	private static KeyMapping settings;

	private AletheiaKeys() {
	}

	public static void register() {
		hudEditor = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aletheia.hud_editor", InputConstants.UNKNOWN.getValue(), CATEGORY));
		settings = KeyMappingHelper.registerKeyMapping(new KeyMapping(
				"key.aletheia.settings", InputConstants.UNKNOWN.getValue(), CATEGORY));

		ClientTickEvents.END_CLIENT_TICK.register(AletheiaKeys::tick);
	}

	/**
	 * {@code consumeClick} is drained rather than read once: a key pressed twice inside one tick is
	 * still two presses, and leaving the second queued would open the screen again on the way out.
	 */
	private static void tick(Minecraft client) {
		boolean openEditor = false;
		boolean openSettings = false;

		while (hudEditor.consumeClick()) {
			openEditor = true;
		}
		while (settings.consumeClick()) {
			openSettings = true;
		}

		// Only from in the world, and only one of them: a key held while a screen is already up would
		// otherwise stack screens whose parent is each other.
		if (client.screen != null || client.player == null) {
			return;
		}
		if (openEditor) {
			HudEditorScreen.openLater(client);
		} else if (openSettings) {
			client.setScreen(ConfigScreen.create(null));
		}
	}
}
