package dev.landofif.aletheia.serverhud;

import dev.landofif.aletheia.mixin.BossHealthOverlayAccessor;
import dev.landofif.aletheia.mixin.GuiTextAccessor;
import dev.landofif.aletheia.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds text a server has put on screen, whichever channel it arrived through.
 *
 * <p>Telos does not draw its persistent readouts -- the stat panel, the biome ribbon, the shard
 * counter -- with a mod. They are ordinary chat components, drawn with fonts from the server's
 * resource pack whose glyphs carry an absurd negative {@code ascent} (around -105500). The pack also
 * replaces {@code rendertype_text}, and that shader turns the ascent back into an id and moves the
 * glyph to a fixed corner of the screen. So a line that is sent as, say, a centred boss bar title
 * can be drawn as a ribbon in the bottom right, and nothing about the text says where it landed.
 *
 * <p>That is why hooking the action bar caught nothing: the position on screen says nothing about
 * which channel carried it. What it does leave behind is the font -- a {@code mythichud:...} id
 * instead of {@code minecraft:default} -- which is a reliable marker of server HUD text and is what
 * {@link #isCustomFont} looks for.
 */
public final class ServerHud {
	private ServerHud() {
	}

	/** Sidebar rows are capped the same way vanilla caps them, so a long objective cannot flood chat. */
	private static final int MAX_SIDEBAR_LINES = 15;

	/**
	 * One piece of on-screen text.
	 *
	 * @param source human-readable channel it came from, e.g. {@code "Boss bar 1"}
	 * @param text   the component as the server sent it
	 */
	public record Line(String source, Component text) {

		/** Font ids other than {@code minecraft:default}, in the order they appear. */
		public Set<String> fonts() {
			return fontsIn(text);
		}

		public boolean custom() {
			return !fonts().isEmpty();
		}

		/** The separate fields the line is built from, one per font. */
		public List<Run> runs() {
			return runsIn(text);
		}
	}

	/**
	 * A stretch of text in one font.
	 *
	 * <p>The server packs its whole HUD into a single component: the biome name, the health number
	 * and the ability cooldown are consecutive runs of it, each in its own font, and each landing in
	 * a different corner of the screen. Splitting by font is what separates them back out --
	 * {@code getString()} would just give you {@code "Radiant Isles 30.2 0"}.
	 *
	 * @param font the font id, e.g. {@code mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-biome/biome-text}
	 * @param text everything drawn in it, joined
	 */
	public record Run(String font, String text) {

		/**
		 * The glyphs in this run that draw a picture rather than a letter, keyed by codepoint and
		 * named by the texture behind them -- a ribbon, a panel, a bar segment.
		 */
		public Map<Integer, String> pictures() {
			Map<Integer, String> pictures = new LinkedHashMap<>();
			text.codePoints().forEach(glyph ->
					HudGlyphs.textureOf(font, glyph).ifPresent(texture -> pictures.putIfAbsent(glyph, texture)));
			return pictures;
		}
	}

	/** Every channel worth looking at, empty ones included -- "nothing here" is half the answer. */
	public static List<Line> snapshot() {
		List<Line> lines = new ArrayList<>();
		Minecraft client = Minecraft.getInstance();
		Gui gui = client.gui;
		if (gui == null) {
			return lines;
		}

		GuiTextAccessor guiText = (GuiTextAccessor) gui;
		// The Gui keeps the last action bar message after it has faded out, so only report a live one.
		if (guiText.aletheia$overlayMessageTime() > 0) {
			add(lines, "Action bar", guiText.aletheia$overlayMessage());
		}
		add(lines, "Title", guiText.aletheia$title());
		add(lines, "Subtitle", guiText.aletheia$subtitle());

		BossHealthOverlay bossBars = gui.getBossOverlay();
		int index = 1;
		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) bossBars).aletheia$events().values()) {
			add(lines, "Boss bar " + index++, event.getName());
		}

		PlayerTabOverlayAccessor tabList = (PlayerTabOverlayAccessor) gui.getTabList();
		add(lines, "Tab header", tabList.aletheia$header());
		add(lines, "Tab footer", tabList.aletheia$footer());
		addTabRows(lines, client);

		addSidebar(lines, client.level);
		return lines;
	}

	/** How many rows of the list are shown, since a busy server's is far too long for chat. */
	private static final int MAX_TAB_ROWS = 12;

	/**
	 * The rows of the player list that are drawn in the server's own font.
	 *
	 * <p>Worth the space because a row is not always a player: Telos builds its stat panel out of
	 * entries in the list, one field to a row, and a scan that showed only the header and the footer
	 * said the panel was not there at all.
	 *
	 * <p>Held to rows in a resource-pack font so a crowd of ordinary players cannot flood the report.
	 */
	private static void addTabRows(List<Line> lines, Minecraft client) {
		if (client.getConnection() == null) {
			return;
		}

		int shown = 0;
		for (PlayerInfo entry : client.getConnection().getListedOnlinePlayers()) {
			Component name = entry.getTabListDisplayName();
			if (isEmpty(name) || !hasCustomFont(name)) {
				continue;
			}
			if (shown++ >= MAX_TAB_ROWS) {
				break;
			}
			add(lines, "Tab row " + shown, name);
		}
	}

	private static void addSidebar(List<Line> lines, ClientLevel level) {
		if (level == null) {
			return;
		}
		Scoreboard scoreboard = level.getScoreboard();
		Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) {
			return;
		}

		add(lines, "Sidebar title", sidebar.getDisplayName());
		int shown = 0;
		for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
			if (entry.isHidden()) {
				continue;
			}
			if (shown++ >= MAX_SIDEBAR_LINES) {
				break;
			}
			add(lines, "Sidebar row " + shown, entry.ownerName());
		}
	}

	private static void add(List<Line> lines, String source, Component text) {
		if (text != null && !isEmpty(text)) {
			lines.add(new Line(source, text));
		}
	}

	/**
	 * Whether a component draws nothing at all.
	 *
	 * <p>Asked instead of {@code getString().isEmpty()}, which flattens the whole component into a
	 * string to look at its length: this walk stops at the first character it finds, and a scan asks it
	 * of every row of the player list.
	 */
	public static boolean isEmpty(Component text) {
		if (text == null) {
			return true;
		}
		return text.visit(content -> content.isEmpty() ? Optional.empty() : Optional.of(Boolean.FALSE)).isEmpty();
	}

	/**
	 * Whether any part of a component is drawn in a font the server's resource pack supplies.
	 *
	 * <p>The same question {@link #fontsIn} answers, without building the set of names -- which is all
	 * the callers that only want a yes or no were using it for.
	 */
	public static boolean hasCustomFont(Component text) {
		if (text == null) {
			return false;
		}
		return text.visit((style, content) ->
				!content.isEmpty() && isCustomFont(style) ? Optional.of(Boolean.TRUE) : Optional.empty(),
				Style.EMPTY).isPresent();
	}

	/**
	 * Splits a component into one entry per font, in the order the fonts first appear.
	 *
	 * <p>Runs in the same font are joined even when they are not adjacent, because the server splits
	 * a single field across several components -- a number arrives digit by digit.
	 */
	public static List<Run> runsIn(Component text) {
		Map<String, StringBuilder> byFont = new LinkedHashMap<>();
		if (text == null) {
			return List.of();
		}

		text.visit((style, content) -> {
			if (!content.isEmpty()) {
				byFont.computeIfAbsent(describeFont(style), font -> new StringBuilder()).append(content);
			}
			return Optional.empty();
		}, Style.EMPTY);

		List<Run> runs = new ArrayList<>(byFont.size());
		byFont.forEach((font, content) -> runs.add(new Run(font, content.toString())));
		return runs;
	}

	/**
	 * @return the non-default font ids used anywhere in a component, e.g.
	 *         {@code mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-biome/biome-text}
	 */
	public static Set<String> fontsIn(Component text) {
		Set<String> fonts = new LinkedHashSet<>();
		if (text == null) {
			return fonts;
		}
		text.visit((style, content) -> {
			if (!content.isEmpty() && isCustomFont(style)) {
				fonts.add(describeFont(style));
			}
			return Optional.empty();
		}, Style.EMPTY);
		return fonts;
	}

	/** @return whether this style asks for a font the server's resource pack supplies */
	public static boolean isCustomFont(Style style) {
		return style.getFont() instanceof FontDescription.Resource resource
				&& !"minecraft".equals(resource.id().getNamespace());
	}

	/** The font id a style asks for, or the class name of one that has no id worth printing. */
	public static String fontName(Style style) {
		return describeFont(style);
	}

	private static String describeFont(Style style) {
		FontDescription font = style.getFont();
		if (font instanceof FontDescription.Resource resource) {
			Identifier id = resource.id();
			return id.toString();
		}
		// Sprite fonts (player heads, atlas sprites) have no id worth printing.
		return font.getClass().getSimpleName();
	}
}
