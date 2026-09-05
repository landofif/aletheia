package dev.landofif.aletheia.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * The settings button on the mod list.
 *
 * <p>Reached through the {@code modmenu} entrypoint, which nothing asks for unless Mod Menu is
 * installed -- so this class is never loaded without it, and Mod Menu stays optional.
 */
public final class ModMenuIntegration implements ModMenuApi {

	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return ConfigScreen::create;
	}
}
