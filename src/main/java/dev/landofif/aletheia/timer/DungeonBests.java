package dev.landofif.aletheia.timer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import dev.landofif.aletheia.Aletheia;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The best clear time seen for each dungeon, in whole seconds.
 *
 * <p><b>Its own file, not a setting.</b> {@code ConfigFile} writes every public field of
 * {@code AletheiaConfig} through Gson and reads it back against {@code Field.getType()}, which for a
 * map is the raw type -- the numbers would come back as {@code Double} inside a {@code LinkedTreeMap}
 * and the settings screen would have a map it could not draw. Records also are not settings: nothing
 * here is something you choose, and a "reset to default" button that wiped your times would be a
 * cruel thing to put next to a colour picker.
 *
 * <p><b>The times are the server's, never the mod's own clock.</b> {@code DungeonTimer} runs a clock
 * so there is something to read mid-run, but what gets written here is the number Telos states in its
 * leaderboard. A stopwatch started on a dimension change cannot agree with the server's to the second,
 * and a personal best that disagrees with the leaderboard is worse than no personal best at all.
 */
public final class DungeonBests {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type SHAPE = new TypeToken<LinkedHashMap<String, Integer>>() {
	}.getType();

	private static final Path PATH =
			FabricLoader.getInstance().getConfigDir().resolve(Aletheia.MOD_ID + "-bests.json");

	/** Keyed by the dungeon key {@code DungeonTimer} matched, e.g. {@code dreadwood}. */
	private static final Map<String, Integer> bests = new LinkedHashMap<>();

	private static boolean loaded;

	private DungeonBests() {
	}

	public static void load() {
		loaded = true;
		if (!Files.isRegularFile(PATH)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(PATH)) {
			Map<String, Integer> saved = GSON.fromJson(reader, SHAPE);
			if (saved != null) {
				saved.forEach((key, seconds) -> {
					if (key != null && seconds != null && seconds > 0) {
						bests.put(key, seconds);
					}
				});
			}
		} catch (IOException | JsonParseException | NumberFormatException failed) {
			Aletheia.LOGGER.warn("could not read {} -- starting with no personal bests", PATH, failed);
		}
	}

	/** @return the best time for this dungeon, or empty if it has never been cleared */
	public static OptionalInt best(String key) {
		if (!loaded) {
			load();
		}
		Integer seconds = bests.get(fold(key));
		return seconds == null ? OptionalInt.empty() : OptionalInt.of(seconds);
	}

	/**
	 * Records a clear.
	 *
	 * @return true if it beat what was there, which is the only case worth writing the file for
	 */
	public static boolean record(String key, int seconds) {
		if (!loaded) {
			load();
		}
		if (key == null || key.isBlank() || seconds <= 0) {
			return false;
		}

		String folded = fold(key);
		Integer previous = bests.get(folded);
		if (previous != null && previous <= seconds) {
			return false;
		}

		bests.put(folded, seconds);
		save();
		return true;
	}

	/** Everything on file, for {@code /aletheia timer}. */
	public static Map<String, Integer> all() {
		if (!loaded) {
			load();
		}
		return Map.copyOf(bests);
	}

	/** Wipes the lot, for {@code /aletheia timer clear}. */
	public static void forgetAll() {
		bests.clear();
		save();
	}

	private static String fold(String key) {
		return key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
	}

	private static void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(bests, SHAPE, writer);
			}
		} catch (IOException failed) {
			Aletheia.LOGGER.warn("could not write {}", PATH, failed);
		}
	}
}
