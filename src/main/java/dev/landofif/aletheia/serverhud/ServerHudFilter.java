package dev.landofif.aletheia.serverhud;

import dev.landofif.aletheia.ChatWatcher;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.detect.SpacingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Removes chosen parts of the server's HUD from the boss bar that carries it.
 *
 * <p>Hiding cannot simply drop the text. Every part of the HUD is a run of one component, laid out
 * by a chain of negative-space glyphs, and the whole string is centred -- so deleting a run would
 * drag everything after it sideways and re-centre the rest. Instead each hidden run is swapped for
 * an equal width of blank space, which leaves the pen exactly where it was and every other part
 * where the server put it.
 *
 * <p>The pack's spacing font ({@code mythichud:spaces}) has one codepoint per pixel of advance:
 * {@code U+E000 + n} moves right by {@code n}, {@code U+F000 + n} moves left, up to 1280 either way.
 * That is what makes an exact swap possible; see {@link #spacer}.
 */
public final class ServerHudFilter {
	private ServerHudFilter() {
	}

	/** How a spacing font identifies itself; the pack calls it {@code mythichud:spaces}. */
	private static final String SPACING_FONT_PATH = "spaces";

	/**
	 * Results kept from one frame to the next, keyed by the component they were made from.
	 *
	 * <p>A bar is re-rendered every frame and only changes when the server sends an update, so this is
	 * a cache with an excellent hit rate -- <b>as long as it holds more than one entry</b>. It used to
	 * hold exactly one, which on this server is the same as holding none: Telos puts up several bars at
	 * once (its whole HUD is bars), the overlay filters each of them in turn, and each one evicted the
	 * one before it. Every bar was rewritten from scratch, every frame.
	 *
	 * <p>Keyed by identity, since a new name component is the only signal that the text has changed.
	 */
	private static final Map<Component, Component> CACHE = new IdentityHashMap<>();

	/** Bars on screen at once; past this the lot is dropped, which costs one frame of rewriting. */
	private static final int MAX_REMEMBERED = 16;

	/** The settings the cache was filled under, and the phrases parsed out of them once. */
	private static String lastSettings = "";
	private static List<String> phrases = List.of();

	/**
	 * @param name the boss bar's name as the server sent it
	 * @return the name with the hidden parts blanked out, or the original if nothing is being hidden
	 */
	public static Component apply(Component name) {
		if (name == null || !AletheiaConfig.enabled || !AletheiaConfig.hideServerHud || !ChatWatcher.onEnabledServer()) {
			return name;
		}

		String settings = String.valueOf(AletheiaConfig.serverHudHideFilter);
		if (!settings.equals(lastSettings)) {
			// Editing the filter has to take effect on the next frame, so everything kept under the old
			// one goes -- and the phrases are split here rather than per bar per frame.
			lastSettings = settings;
			phrases = NeoEdenParser.splitPhrases(settings);
			CACHE.clear();
		}

		Component cached = CACHE.get(name);
		if (cached != null) {
			return cached;
		}

		Component filtered = rewrite(name, phrases);
		if (CACHE.size() >= MAX_REMEMBERED) {
			CACHE.clear();
		}
		CACHE.put(name, filtered);
		return filtered;
	}

	private static Component rewrite(Component name, List<String> phrases) {
		Identifier spacingFont = spacingFontIn(name);
		Font font = Minecraft.getInstance().font;

		MutableComponent out = Component.empty();
		boolean[] changed = {false};

		name.visit((style, text) -> {
			if (!text.isEmpty()) {
				// Character by character: a ribbon's picture and its caption are separate glyphs, and
				// the picture often shares a font with panels that are staying.
				appendFiltered(out, style, text, phrases, spacingFont, font, changed);
			}
			return Optional.empty();
		}, Style.EMPTY);

		return changed[0] ? out : name;
	}

	/**
	 * Copies a run across, swapping each hidden stretch for the width of space it occupied.
	 *
	 * <p>The two questions a glyph used to be put to are settled once for the whole run first, because
	 * neither of them is really about the glyph: whether this is the server's own font at all, and
	 * whether the filter names that font. Only the third -- whether the filter names the picture
	 * <i>this</i> glyph draws -- can differ within a run, and it is the one that is worth walking
	 * character by character for. That is what a ribbon and its caption are: one font, two pictures.
	 */
	private static void appendFiltered(MutableComponent out, Style style, String text, List<String> phrases,
			Identifier spacingFont, Font font, boolean[] changed) {
		// Anything drawn in the game's own font is not the server's HUD, so none of it is ever hidden.
		if (!ServerHud.isCustomFont(style)) {
			out.append(Component.literal(text).withStyle(style));
			return;
		}

		String fontId = style.getFont() instanceof FontDescription.Resource resource
				? resource.id().toString()
				: "";

		// An empty filter hides everything the server draws in its own font, which is the whole HUD --
		// and a filter naming this run's font hides the run entire. Either way there is nothing to walk.
		if (phrases.isEmpty() || namesFont(fontId, phrases)) {
			hide(out, style, text, spacingFont, font, changed);
			return;
		}

		int start = 0;
		while (start < text.length()) {
			boolean hiding = drawsHidden(fontId, text.codePointAt(start), phrases);

			// Longest stretch that is being treated the same way, so the output stays as few parts as
			// possible -- every one of them is another component the game has to lay out.
			int end = start;
			while (end < text.length() && drawsHidden(fontId, text.codePointAt(end), phrases) == hiding) {
				end += Character.charCount(text.codePointAt(end));
			}

			String stretch = text.substring(start, end);
			if (hiding) {
				hide(out, style, stretch, spacingFont, font, changed);
			} else {
				out.append(Component.literal(stretch).withStyle(style));
			}
			start = end;
		}
	}

	/** Blank space of the same width, so the parts that are staying do not move. */
	private static void hide(MutableComponent out, Style style, String stretch,
			Identifier spacingFont, Font font, boolean[] changed) {
		changed[0] = true;
		if (spacingFont == null) {
			return;
		}
		String spacer = SpacingText.advance(font.width(FormattedText.of(stretch, style)));
		if (!spacer.isEmpty()) {
			out.append(Component.literal(spacer)
					.withStyle(Style.EMPTY.withFont(new FontDescription.Resource(spacingFont))));
		}
	}

	/** Whether the filter names the font a run is written in -- "biome" names a font. */
	private static boolean namesFont(String fontId, List<String> phrases) {
		for (String phrase : phrases) {
			if (ChatText.containsIgnoreCase(fontId, phrase)) {
				return true;
			}
		}
		return false;
	}

	/** Whether the filter names the picture this particular glyph draws -- "ribbon" names a texture. */
	private static boolean drawsHidden(String fontId, int glyph, List<String> phrases) {
		String texture = HudGlyphs.texture(fontId, glyph);
		if (texture == null) {
			return false;
		}
		for (String phrase : phrases) {
			if (ChatText.containsIgnoreCase(texture, phrase)) {
				return true;
			}
		}
		return false;
	}

	/** @return the spacing font this HUD is laid out with, or {@code null} if it does not use one */
	private static Identifier spacingFontIn(Component name) {
		for (String font : ServerHud.fontsIn(name)) {
			Identifier id = Identifier.tryParse(font);
			if (id != null && (id.getPath().equals(SPACING_FONT_PATH) || id.getPath().endsWith("/" + SPACING_FONT_PATH))) {
				return id;
			}
		}
		return null;
	}

}
