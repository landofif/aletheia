package dev.landofif.aletheia.serverhud;

import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;

import java.util.Optional;

/**
 * The name of the place you are standing in, read off the server's own ribbon.
 *
 * <p>Telos writes it in the bottom right -- "Permafrost", "Celestial's Province", "Raphael's
 * Chamber" -- and it is the only thing on screen that says <i>which</i> dungeon you are in. The
 * dimension id does not: a dungeon is handed out as an instance slot, so the same dungeon is
 * {@code telos:dungeon/2} on one run and {@code telos:dungeon/11} on the next, and fourteen
 * different dungeons share the fourteen names. That is why {@link dev.landofif.aletheia.timer.DungeonTimer}
 * asks here rather than reading the id.
 *
 * <p><b>It arrives as part of a boss bar</b>, like the rest of the server's HUD -- see
 * {@link ServerHud} for why nothing about the text says where on screen it lands. The ribbon is one
 * run of that bar's name, written in the pack's {@code ...rotmc2-hud-biome/biome-text} font, sitting
 * between the panels and the health number. Splitting the bar by font is what separates it out;
 * {@code getString()} on the whole bar would give you {@code "Radiant Isles 30.2 0"}.
 *
 * <p>Read on demand rather than on a tick. The one caller that wants it asks only while it is still
 * waiting for an answer, so a ticker would spend its time re-reading a string nobody had asked for.
 */
public final class HudArea {
	private HudArea() {
	}

	/**
	 * The tail of the ribbon's font id.
	 *
	 * <p>Matched on the last segment rather than the whole path, because everything in front of it is
	 * the pack's own layout tree ({@code mythichud:layout/rotmc2-layout/fonts/rotmc2-hud-biome/...})
	 * and a re-organised pack would move it without renaming the field.
	 */
	private static final String RIBBON_FONT = "biome-text";

	/** @return the area the ribbon names, e.g. {@code Celestial's Province}, or empty if it is not up */
	public static String name() {
		Minecraft client = Minecraft.getInstance();
		Gui gui = client.gui;
		if (gui == null) {
			return "";
		}

		// The server's HUD is one of the bars, not always the first: a boss fight puts its own bar up
		// alongside it, and which of them arrives first is the server's business.
		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) gui.getBossOverlay()).aletheia$events().values()) {
			String area = areaIn(event.getName());
			if (!area.isEmpty()) {
				return area;
			}
		}
		return "";
	}

	/**
	 * Pulls the ribbon out of one component.
	 *
	 * <p>Every part in the font is appended rather than the first one returned: the server splits a
	 * field across several components, so a name can arrive a few letters at a time.
	 *
	 * @param name a boss bar's name as the server sent it
	 * @return the area written in the ribbon font, folded to plain text, or empty for a bar that has none
	 */
	public static String areaIn(Component name) {
		if (name == null) {
			return "";
		}

		StringBuilder ribbon = new StringBuilder();
		name.visit((style, content) -> {
			if (!content.isEmpty() && isRibbonFont(style)) {
				ribbon.append(content);
			}
			return Optional.empty();
		}, Style.EMPTY);

		// The ribbon is padded with the pack's spacing glyphs, which fold to whitespace and are then
		// collapsed away; the names themselves are ordinary ASCII, apostrophes and all.
		return ChatText.normalize(ribbon.toString());
	}

	private static boolean isRibbonFont(Style style) {
		return style.getFont() instanceof FontDescription.Resource resource
				&& resource.id().getPath().endsWith(RIBBON_FONT);
	}
}
