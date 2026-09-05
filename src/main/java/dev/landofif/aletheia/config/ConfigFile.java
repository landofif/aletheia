package dev.landofif.aletheia.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import dev.landofif.aletheia.Aletheia;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Reads and writes the settings as a flat JSON object keyed by field name.
 *
 * <p>That is the shape OneConfig wrote before it, so an existing {@code config/aletheia.json} --
 * HUD placements and all -- is picked up as it stands. Anything the file does not mention keeps the
 * default declared on the field, which is also how a new setting arrives without disturbing the
 * rest.
 */
public final class ConfigFile {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve(Aletheia.MOD_ID + ".json");

	/** The settings themselves: everything public and assignable on the config class. */
	private static final List<Field> FIELDS = Arrays.stream(AletheiaConfig.class.getDeclaredFields())
			.filter(field -> {
				int modifiers = field.getModifiers();
				return Modifier.isPublic(modifiers)
						&& Modifier.isStatic(modifiers)
						&& !Modifier.isFinal(modifiers)
						&& !field.isSynthetic();
			})
			.toList();

	private ConfigFile() {
	}

	public static void load() {
		if (!Files.isRegularFile(PATH)) {
			return;
		}

		JsonObject saved;
		try (Reader reader = Files.newBufferedReader(PATH)) {
			saved = GSON.fromJson(reader, JsonObject.class);
		} catch (IOException | JsonParseException failed) {
			Aletheia.LOGGER.warn("could not read {} -- carrying on with the defaults", PATH, failed);
			return;
		}
		if (saved == null) {
			return;
		}

		for (Field field : FIELDS) {
			JsonElement value = saved.get(field.getName());
			if (value == null) {
				continue;
			}
			try {
				field.set(null, GSON.fromJson(value, field.getType()));
			} catch (ReflectiveOperationException | RuntimeException failed) {
				// One unreadable setting -- a string where a number belongs, say -- is not worth
				// losing the rest of the file over.
				Aletheia.LOGGER.warn("ignoring unreadable setting {}", field.getName(), failed);
			}
		}
	}

	public static void save() {
		JsonObject json = new JsonObject();
		for (Field field : FIELDS) {
			try {
				json.add(field.getName(), GSON.toJsonTree(field.get(null)));
			} catch (ReflectiveOperationException failed) {
				Aletheia.LOGGER.warn("could not write setting {}", field.getName(), failed);
			}
		}

		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(json, writer);
			}
		} catch (IOException failed) {
			Aletheia.LOGGER.warn("could not write {}", PATH, failed);
		}
	}
}
