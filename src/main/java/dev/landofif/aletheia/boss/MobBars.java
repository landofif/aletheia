package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.mixin.TextDisplayAccessor;
import dev.landofif.aletheia.serverhud.HudGlyphs;
import dev.landofif.aletheia.serverhud.ServerHud;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The health bars floating over mobs, and the colour each one is drawn in.
 *
 * <p>This is where a Telos fight says it cannot be hurt, and it is nowhere near the boss bar. A bar
 * over a mob is <b>three text display entities standing in the same spot</b>: a frame, a single
 * fill glyph, and the mob's name spelled out. All three are drawn in the {@code telos:text} font, so
 * each character is a picture -- {@code telos:text/xikage/miniboss/t.png} is the letter t.
 *
 * <p>The fill is one glyph, {@code xikage/default/inner.png}, and <b>its text colour is the state</b>:
 * {@code #5AD022} green while the mob can be hurt, {@code #1757A7} blue while it cannot. Nothing else
 * about the fight changes -- not the boss bar's colour, not any texture -- which is why watching the
 * boss bar found nothing.
 *
 * <p>The frame carries the mob's grade in its path: {@code xikage/boss/}, {@code xikage/miniboss/} or
 * {@code xikage/default/}. That is how the boss is told apart from its adds, which all carry bars of
 * their own, and is far steadier than matching names -- the pack's own key for a fight is nothing
 * like the name over its head ({@code hardmode_ophanim} against "True Ophan").
 */
public final class MobBars {
	private MobBars() {
	}

	/** How near two of these displays have to be to count as one bar. They stand in the same spot. */
	private static final double SAME_SPOT = 1.5;

	/** A busy arena is full of these; enough to take in a boss and its adds, and no more. */
	private static final int MAX_DISPLAYS = 256;

	/**
	 * Grades of mob, worst to best. A bar's frame says which it is, and it is a clean split in
	 * practice: in the Cog Sentinel's arena the Sentinel reads {@code boss} and every mob milling
	 * around it reads {@code ordinary}.
	 */
	private static final String[] TIERS = {"xikage/default/", "xikage/miniboss/", "xikage/boss/"};

	public static final int TIER_ORDINARY = 0;
	public static final int TIER_MINIBOSS = 1;
	public static final int TIER_BOSS = 2;

	/**
	 * One bar over a mob.
	 *
	 * @param fill     the display drawing the coloured part, kept so its colour can be re-read cheaply
	 * @param colour   that colour as RGB, or {@code -1} when it is drawn in no colour at all
	 * @param tier     how good a mob it belongs to: an index into {@link #TIERS}, {@code -1} if unknown
	 * @param name     what is written next to it, letters recovered from the pack's glyphs
	 * @param distance how far from the player, for choosing between bars of the same grade
	 */
	public record Bar(Display.TextDisplay fill, int colour, int tier, String name, double distance) {
	}

	/** {@link #pick} with this as the floor takes any bar at all, graded or not. */
	public static final int ANY_TIER = Integer.MIN_VALUE;

	/**
	 * The bar worth following: the best grade of mob in range, nearest first among equals.
	 *
	 * <p>Falls back to plain distance when no frame names a grade, which is what happens if the pack
	 * ever renames these -- following the nearest bar is wrong less often than following none.
	 *
	 * <p>A bar <b>named by one of the boss bars on screen</b> outranks everything, since that is the
	 * server naming the fight rather than this guessing at it. It only sometimes helps -- Hierophant's
	 * bar spells "hierophant" next to it, but the Cog Sentinel's carries its health instead of its
	 * name -- so it is a preference on top of the grade, not a requirement.
	 *
	 * @param minTier the worst grade of mob worth following, or {@link #ANY_TIER}. A bar whose frame
	 *                names no grade counts as below all of them, so asking for a grade excludes it
	 * @param named   folded names the boss bars are claiming, from {@link BossBars#keysOnScreen}
	 */
	@Nullable
	public static Bar pick(LocalPlayer player, ClientLevel level, int minTier, Set<String> named) {
		Bar best = null;
		boolean bestNamed = false;

		for (Bar bar : nearby(player, level)) {
			if (bar.tier() < minTier) {
				continue;
			}
			boolean isNamed = names(bar, named);
			if (best == null
					|| (isNamed && !bestNamed)
					|| (isNamed == bestNamed && bar.tier() > best.tier())
					|| (isNamed == bestNamed && bar.tier() == best.tier() && bar.distance() < best.distance())) {
				best = bar;
				bestNamed = isNamed;
			}
		}
		return best;
	}

	/** Whether what is written next to a bar is one of the fights the boss bars are naming. */
	private static boolean names(Bar bar, Set<String> named) {
		if (named.isEmpty() || bar.name().isBlank()) {
			return false;
		}
		String folded = ChatText.lettersAndDigits(bar.name());
		if (folded.isEmpty()) {
			return false;
		}
		for (String key : named) {
			if (folded.contains(key)) {
				return true;
			}
		}
		return false;
	}

	/** Every mob bar within range, unranked. */
	public static List<Bar> nearby(LocalPlayer player, ClientLevel level) {
		double range = Math.max(8, AletheiaConfig.invulnerableRange);
		double rangeSqr = range * range;

		// Collected once: the pieces of a bar are separate entities, so each fill has to look around
		// itself for the frame and the name standing with it.
		//
		// Squared distances throughout: this is the loop that walks every entity the client is drawing,
		// and the square root it would otherwise take on each of them buys nothing -- the comparison is
		// the same either way.
		List<Display.TextDisplay> displays = new ArrayList<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (displays.size() >= MAX_DISPLAYS) {
				break;
			}
			if (entity instanceof Display.TextDisplay display && display.distanceToSqr(player) <= rangeSqr) {
				displays.add(display);
			}
		}

		// Read once rather than per display: it is a setting, and folding it is not free.
		String wanted = filter(AletheiaConfig.barFillTexture, DEFAULT_FILL_TEXTURE);

		List<Bar> bars = new ArrayList<>();
		for (Display.TextDisplay display : displays) {
			int colour = fillColourOf(display, wanted);
			if (colour == NOT_A_FILL) {
				continue;
			}
			// One walk of the neighbours for both answers. There are only ever a handful of fills among
			// the displays, but each one used to walk the whole list twice over to find the two pieces
			// standing with it, and in a busy arena that list is hundreds long.
			Piece piece = piecesNear(display, displays);
			bars.add(new Bar(display, colour, piece.tier(), piece.name(), Math.sqrt(display.distanceToSqr(player))));
		}
		return bars;
	}

	/** The two things standing with a fill: what grade its frame names, and what its label spells. */
	private record Piece(int tier, String name) {
	}

	private static Piece piecesNear(Display.TextDisplay fill, List<Display.TextDisplay> displays) {
		int tier = -1;
		String name = "";

		for (Display.TextDisplay display : displays) {
			if (!sameSpot(fill, display)) {
				continue;
			}
			Component text = ((TextDisplayAccessor) display).aletheia$text();
			if (text == null) {
				continue;
			}

			if (tier < 0) {
				for (int grade = TIERS.length - 1; grade >= 0; grade--) {
					if (drawsAnywhere(text, TIERS[grade] + "frame")) {
						tier = grade;
						break;
					}
				}
			}
			if (name.isEmpty() && display != fill) {
				String spelled = spell(text);
				if (!spelled.isBlank()) {
					name = spelled.trim();
				}
			}
			if (tier >= 0 && !name.isEmpty()) {
				break;
			}
		}
		return new Piece(tier, name);
	}

	/** Returned by {@link #fillColourOf} for a display that is not the coloured part of a bar. */
	public static final int NOT_A_FILL = Integer.MIN_VALUE;

	/** Drawn in no colour at all -- which is not a state, just a bar that says nothing. */
	public static final int NO_COLOUR = -1;

	/**
	 * The colour a bar's fill is drawn in, re-read from the live entity.
	 *
	 * @return the RGB value, {@link #NO_COLOUR} if the fill carries no colour, or {@link #NOT_A_FILL}
	 *         if this display is not a fill at all
	 */
	public static int fillColourOf(Display.TextDisplay display) {
		return fillColourOf(display, filter(AletheiaConfig.barFillTexture, DEFAULT_FILL_TEXTURE));
	}

	/** {@link #fillColourOf} for a caller that has already folded the setting -- see {@link #nearby}. */
	private static int fillColourOf(Display.TextDisplay display, String wanted) {
		Component text = ((TextDisplayAccessor) display).aletheia$text();
		if (text == null) {
			return NOT_A_FILL;
		}

		int[] found = {NOT_A_FILL};
		text.visit((style, content) -> {
			if (!draws(style, content, wanted)) {
				return Optional.empty();
			}
			found[0] = style.getColor() == null ? NO_COLOUR : style.getColor().getValue();
			return Optional.of(true);
		}, Style.EMPTY);
		return found[0];
	}

	/**
	 * Reads a run of pack letter glyphs back as text.
	 *
	 * <p>Each character of one of these labels is its own picture, named after what it draws:
	 * {@code .../t.png} is a t, {@code .../slash.png} a slash. The name of the file is the only place
	 * the letter survives -- {@code getString()} on the component gives private-use codepoints.
	 */
	public static String spell(Component text) {
		StringBuilder out = new StringBuilder();
		text.visit((style, content) -> {
			String font = ServerHud.fontName(style);
			content.codePoints().forEach(glyph ->
					HudGlyphs.textureOf(font, glyph).ifPresent(texture -> out.append(letterOf(texture))));
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString();
	}

	/** The character a letter picture stands for; empty for the frame, the fill and the bar's ends. */
	private static String letterOf(String texture) {
		String name = texture.substring(texture.lastIndexOf('/') + 1).replace(".png", "");
		return switch (name) {
			case "space" -> " ";
			case "slash" -> "/";
			case "dot" -> ".";
			case "dash" -> "-";
			default -> name.length() == 1 ? name : "";
		};
	}

	/**
	 * Whether two displays are standing in the same place.
	 *
	 * <p>Straight off the coordinates rather than through {@link Vec3}: every fill asks this of every
	 * display around it, which in a busy arena is thousands of comparisons on the rescan, and the
	 * obvious spelling makes two vectors per comparison to throw both away again.
	 */
	private static boolean sameSpot(Entity one, Entity other) {
		double x = one.getX() - other.getX();
		double y = one.getY() - other.getY();
		double z = one.getZ() - other.getZ();
		return x * x + y * y + z * z <= SAME_SPOT * SAME_SPOT;
	}

	/** Whether any glyph anywhere in a piece of text draws a picture whose name contains the phrase. */
	private static boolean drawsAnywhere(Component text, String phrase) {
		return text.visit((style, content) -> draws(style, content, phrase) ? Optional.of(true) : Optional.empty(),
				Style.EMPTY).isPresent();
	}

	private static boolean draws(Style style, String content, String phrase) {
		String font = ServerHud.fontName(style);
		return content.codePoints().anyMatch(glyph -> HudGlyphs.textureMatches(font, glyph, phrase));
	}

	/** The glyph Telos draws the coloured part of a bar with, when the setting does not say otherwise. */
	private static final String DEFAULT_FILL_TEXTURE = "xikage/default/inner";

	/** A blank setting means the default rather than "match everything", which would match nothing useful. */
	private static String filter(String setting, String fallback) {
		return setting == null || setting.isBlank() ? fallback : setting.trim().toLowerCase(Locale.ROOT);
	}
}
