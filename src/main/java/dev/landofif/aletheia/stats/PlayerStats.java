package dev.landofif.aletheia.stats;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.StatText;
import dev.landofif.aletheia.mixin.PlayerTabOverlayAccessor;
import dev.landofif.aletheia.serverhud.HudGlyphs;
import dev.landofif.aletheia.serverhud.ServerHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Your stats, taken off the player list and put somewhere you can see them without holding Tab.
 *
 * <p><b>It arrives as tab list rows</b> -- not as the header or the footer, which carry only the
 * server's own advertising. Telos builds the two info columns you see behind the player names out of
 * <i>entries in the list</i>, one field to a row: the panel is a crowd of players who are not there.
 * That is why every row is read here rather than just your own. {@link StatText} does the reading.
 *
 * <p>The rows arrive whether or not anyone is holding Tab -- the list is state the server sends, and
 * only the drawing of it waits for the key -- which is the whole point of moving it to a corner.
 *
 * <p><b>Which stats to show is the player's choice</b>, because what is in that panel is the
 * server's to change: whatever is found is offered by name, {@code /aletheia stats} prints the list,
 * and the setting picks from it. Nothing here has a table of stat names in it, so a stat the server
 * adds tomorrow can be put on screen without a new build.
 *
 * <p>Read twice a second, and parsed only when the list has actually changed.
 */
public final class PlayerStats {
	private PlayerStats() {
	}

	/** Where a reading was found, for {@code /aletheia stats}. */
	public record Reading(String name, String value, String source) {
	}

	private static List<Reading> readings = List.of();

	/** The raw text the last reading was made from, so an unchanged panel is not read again. */
	private static String lastRaw = "";

	/**
	 * How often the list is looked at, in ticks. A stat changes when you swap a ring, so twice a second
	 * is far more often than it can matter -- and the list is sixty rows deep, which is not something to
	 * walk twenty times a second for a number that will be the same each time.
	 */
	private static final int EVERY_TICKS = 10;

	/** How many rows are read at all, so a crowded list cannot become the client's problem. */
	private static final int MAX_ROWS = 120;

	private static int ticks;

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(PlayerStats::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null) {
			forget();
			return;
		}
		// The panel is sixty rows deep and reading it is not free, so it is not read for a readout that
		// is switched off. /aletheia stats reads it on demand, which is the one other thing that wants it.
		if (!AletheiaConfig.enabled || !AletheiaConfig.showStatsHud) {
			return;
		}
		if (++ticks < EVERY_TICKS) {
			return;
		}
		ticks = 0;
		read(client);
	}

	/** Reads the panel now, whatever the timer and the settings say. Used by {@code /aletheia stats}. */
	public static void refresh() {
		Minecraft client = Minecraft.getInstance();
		if (client.player != null) {
			read(client);
		}
	}

	private static void read(Minecraft client) {
		List<Source> sources = sources(client);
		StringBuilder raw = new StringBuilder();
		for (Source source : sources) {
			raw.append(source.raw()).append(' ');
		}

		String now = raw.toString();
		if (now.equals(lastRaw)) {
			return;
		}
		lastRaw = now;

		Map<String, Reading> byName = new LinkedHashMap<>();
		for (Source source : sources) {
			for (StatText.Stat stat : StatText.read(piecesOf(source.text()), PlayerStats::textureOf)) {
				byName.putIfAbsent(stat.name(), new Reading(stat.name(), stat.value(), source.name()));
			}
		}
		readings = List.copyOf(byName.values());
	}

	private static void forget() {
		readings = List.of();
		lastRaw = "";
	}

	/** The pack's own font definition, which is what names the picture behind a glyph. */
	private static Optional<String> textureOf(String font, int codePoint) {
		return HudGlyphs.textureOf(font, codePoint);
	}

	/**
	 * One place a stat might be written.
	 *
	 * @param raw the text flattened, which is how an unchanged panel is recognised. Kept on the record
	 *            because it was being built once to compare and once to parse, for sixty rows a tick
	 */
	private record Source(String name, Component text, String raw) {
	}

	/**
	 * Everywhere a stat panel might be: every row of the list, and the header and footer around it.
	 *
	 * <p><b>Each row is read on its own</b> rather than the list being run together, because a row is
	 * the unit the server writes a field in. Run together, the value of the last field on one row would
	 * reach into the name of the first field on the next.
	 *
	 * <p>The rows come first because they are where the panel actually is. The header and footer are
	 * read after them for the sake of what they do carry -- the player count, the server's version --
	 * which is worth offering even though nobody asked for it.
	 */
	private static List<Source> sources(Minecraft client) {
		List<Source> sources = new ArrayList<>();

		ClientPacketListener connection = client.getConnection();
		if (connection != null) {
			int rows = 0;
			for (PlayerInfo entry : connection.getListedOnlinePlayers()) {
				if (rows++ >= MAX_ROWS) {
					break;
				}
				add(sources, "Tab row", entry.getTabListDisplayName());
			}
		}

		Gui gui = client.gui;
		if (gui != null) {
			PlayerTabOverlayAccessor tabList = (PlayerTabOverlayAccessor) gui.getTabList();
			add(sources, "Tab header", tabList.aletheia$header());
			add(sources, "Tab footer", tabList.aletheia$footer());
		}
		return sources;
	}

	private static void add(List<Source> sources, String name, Component text) {
		if (text == null) {
			return;
		}
		String raw = text.getString();
		if (!raw.isEmpty()) {
			sources.add(new Source(name, text, raw));
		}
	}

	/**
	 * A component as the pieces it is drawn in, <b>in order</b>.
	 *
	 * <p>Deliberately not {@link ServerHud#runsIn}, which gathers all the text of one font together:
	 * that is the right thing for reading a field out of the HUD and the wrong thing here, where the
	 * icon and the number that belongs to it are neighbours and the order between them is the whole
	 * point.
	 */
	private static List<StatText.Piece> piecesOf(Component text) {
		List<StatText.Piece> pieces = new ArrayList<>();
		text.visit((style, content) -> {
			if (!content.isEmpty()) {
				pieces.add(new StatText.Piece(ServerHud.fontName(style), content));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return pieces;
	}

	// ------------------------------------------------------------------ the readout

	/** Every reading found, in the order the panel draws them. */
	public static List<Reading> all() {
		return readings;
	}

	public static Optional<String> value(String name) {
		String wanted = ChatText.lettersAndDigits(name);
		for (Reading reading : readings) {
			if (reading.name().equals(wanted)) {
				return Optional.of(reading.value());
			}
		}
		return Optional.empty();
	}

	/**
	 * One stat as it is to be shown.
	 *
	 * @param name  what to look it up by
	 * @param label what to call it on screen; empty draws the number on its own
	 */
	public record Pick(String name, String label) {
	}

	/**
	 * What the setting asks for: {@code attack, defense} in the order given, or {@code attack=ATK} to
	 * rename one. An empty setting takes everything found, which is what makes the readout worth
	 * putting on screen before you know what is in the panel.
	 */
	public static List<Pick> picks() {
		String setting = AletheiaConfig.statsPicked;
		if (setting == null || setting.isBlank()) {
			List<Pick> all = new ArrayList<>(readings.size());
			for (Reading reading : readings) {
				all.add(new Pick(reading.name(), titleCase(reading.name())));
			}
			return all;
		}

		List<Pick> picks = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		for (String part : setting.split(",")) {
			String item = part.trim();
			if (item.isEmpty()) {
				continue;
			}

			int equals = item.indexOf('=');
			String name = ChatText.lettersAndDigits(equals < 0 ? item : item.substring(0, equals));
			String label = equals < 0 ? titleCase(name) : item.substring(equals + 1).trim();
			if (!name.isEmpty() && seen.add(name)) {
				picks.add(new Pick(name, label));
			}
		}
		return picks;
	}

	/**
	 * The readout itself: one stat a line, or all of them on one, as the setting says.
	 *
	 * <p>A picked stat the panel is not showing is left out rather than drawn empty -- the panel does
	 * not always carry everything, and a row reading "ATK" with nothing after it looks like a fault.
	 */
	public static String statusLine() {
		String picked = String.valueOf(AletheiaConfig.statsPicked);
		String separator = separator();

		// Asked once a frame, for an answer that can only change when the panel is read again -- twice a
		// second at most -- or when one of the four settings that shape it is edited. Composing it means
		// parsing the setting, looking every name up and joining the lot, which is a great deal of
		// rubbish to make for the same string sixty times a second. The readings are compared by
		// identity: they are replaced wholesale on each read, never edited in place.
		if (composedFrom == readings && composedPicked.equals(picked) && composedSeparator.equals(separator)
				&& composedFull == AletheiaConfig.statsFullValue
				&& composedOneLine == AletheiaConfig.statsOneLine) {
			return composed;
		}

		List<String> parts = new ArrayList<>();
		for (Pick pick : picks()) {
			Optional<String> value = value(pick.name());
			if (value.isEmpty()) {
				continue;
			}
			String shown = AletheiaConfig.statsFullValue ? value.get() : StatText.brief(value.get());
			parts.add(pick.label().isBlank() ? shown : pick.label() + " " + shown);
		}

		composed = parts.isEmpty() ? "" : String.join(AletheiaConfig.statsOneLine ? separator : "\n", parts);
		composedFrom = readings;
		composedPicked = picked;
		composedSeparator = separator;
		composedFull = AletheiaConfig.statsFullValue;
		composedOneLine = AletheiaConfig.statsOneLine;
		return composed;
	}

	/** The last composed readout, and everything it was composed from; see {@link #statusLine}. */
	private static String composed = "";
	private static List<Reading> composedFrom = List.of();
	private static String composedPicked = "";
	private static String composedSeparator = "";
	private static boolean composedFull;
	private static boolean composedOneLine;

	private static String separator() {
		return AletheiaConfig.statsSeparator == null || AletheiaConfig.statsSeparator.isEmpty()
				? "  "
				: AletheiaConfig.statsSeparator;
	}

	/** Whether there is anything to draw at all. */
	public static boolean has() {
		return !readings.isEmpty();
	}

	/** {@code critchance} to {@code Critchance} -- a name the server chose, tidied but not renamed. */
	private static String titleCase(String name) {
		if (name.isEmpty()) {
			return name;
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1).toLowerCase(Locale.ROOT);
	}
}
