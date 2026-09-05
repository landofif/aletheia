package dev.landofif.aletheia.serverhud;

import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.boss.BossBars;
import dev.landofif.aletheia.mixin.TextDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Dumps everything near you that could be carrying a boss's state, to the game log.
 *
 * <p>The bar over a mob's head is not the HUD boss bar. It is a name -- either the mob's own, or a
 * text display floating at its head -- drawn in the same pack fonts as everything else Telos puts on
 * screen, so a health bar there is a run of picture glyphs and a colour there is a <i>different
 * picture</i>, not a colour code. None of that shows up in {@link net.minecraft.world.BossEvent}.
 *
 * <p>So this prints both sides at once: the boss bars with the colour the server set on them, and
 * every named entity within range broken into its fonts and the textures behind its glyphs. Run it
 * while the boss is invulnerable and again while it is not, and whatever differs between the two is
 * the thing worth watching.
 *
 * <p>It goes to the log rather than to chat because these lines are long, there are a lot of them,
 * and chat only keeps a hundred.
 */
public final class MobScan {
	private MobScan() {
	}

	/** Far enough to take in a boss arena, close enough to leave the rest of the world out. */
	private static final double RANGE = 64.0;

	/** A busy arena has hundreds of entities; this is plenty to find the one over the boss. */
	private static final int MAX_ENTITIES = 40;

	/** One named thing in the world, as the scan sees it. */
	public record Named(String kind, double distance, Component name) {
	}

	/**
	 * Writes the report.
	 *
	 * @return how many named entities were found, for the line printed back in chat
	 */
	public static int dump(String note) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return 0;
		}

		Aletheia.LOGGER.info("===== aletheia mobscan {} =====", note);

		int bar = 1;
		for (LerpingBossEvent event : BossBars.onScreen()) {
			String identity = BossBars.identityOf(event).trim();
			Aletheia.LOGGER.info("boss bar {}: colour={} progress={} boss={} identity={}",
					bar++, BossBars.colourName(event), event.getProgress(),
					BossBars.isBoss(identity), identity);
			describe(event.getName());
		}
		if (bar == 1) {
			Aletheia.LOGGER.info("no boss bars on screen");
		}

		List<Named> named = nearbyNames(player, level);
		for (Named entity : named) {
			Aletheia.LOGGER.info("{} at {}m: \"{}\"",
					entity.kind(), String.format("%.1f", entity.distance()), entity.name().getString());
			describe(entity.name());
		}
		if (named.isEmpty()) {
			Aletheia.LOGGER.info("no named entities within {} blocks", (int) RANGE);
		}

		Aletheia.LOGGER.info("===== end mobscan ({} named entities) =====", named.size());
		return named.size();
	}

	/**
	 * Breaks a piece of text into the segments it is really made of, one line each.
	 *
	 * <p><b>Colour is printed first because it is the likeliest answer.</b> A health bar hanging over a
	 * mob is text, and the ordinary way to say "this one cannot be hurt" is to write the same
	 * characters in a different colour -- which is a style on the component and leaves no trace
	 * anywhere else. The font and the glyph textures are printed alongside it in case the bar is a
	 * picture that gets swapped instead.
	 *
	 * <p>Characters outside ASCII are escaped, since a private-use glyph is invisible in a log file.
	 */
	private static void describe(Component text) {
		if (text == null) {
			return;
		}
		text.visit((style, content) -> {
			if (!content.isBlank()) {
				String font = ServerHud.fontName(style);
				Aletheia.LOGGER.info("    colour={} font={} text=\"{}\" pictures={}",
						style.getColor() == null ? "(none)" : style.getColor().serialize(),
						font,
						escape(content),
						picturesIn(font, content));
			}
			return Optional.empty();
		}, Style.EMPTY);
	}

	/** The textures behind whichever characters of a run are pictures rather than letters. */
	private static Collection<String> picturesIn(String font, String content) {
		Map<Integer, String> pictures = new LinkedHashMap<>();
		content.codePoints().forEach(glyph ->
				HudGlyphs.textureOf(font, glyph).ifPresent(texture -> pictures.putIfAbsent(glyph, texture)));
		return pictures.values();
	}

	/** Spells out anything a log file would swallow, so a private-use glyph is still identifiable. */
	private static String escape(String raw) {
		StringBuilder out = new StringBuilder(raw.length());
		raw.codePoints().forEach(codepoint -> {
			if (codepoint >= 0x20 && codepoint < 0x7F) {
				out.appendCodePoint(codepoint);
			} else {
				out.append(String.format("\\u%04X", codepoint));
			}
		});
		return out.toString();
	}

	/**
	 * Every entity in range wearing a name, nearest first.
	 *
	 * <p>Both kinds are collected. A mob can carry its health bar as its own custom name, and a server
	 * can just as easily float a text display above it instead -- there is no telling which from here,
	 * so neither is assumed.
	 */
	public static List<Named> nearbyNames(LocalPlayer player, ClientLevel level) {
		List<Named> named = new ArrayList<>();

		for (Entity entity : level.entitiesForRendering()) {
			double distance = entity.distanceTo(player);
			if (distance > RANGE) {
				continue;
			}

			Component name = nameOf(entity);
			if (name == null || name.getString().isBlank()) {
				continue;
			}
			named.add(new Named(entity.getType().toShortString(), distance, name));
		}

		named.sort(Comparator.comparingDouble(Named::distance));
		return named.size() > MAX_ENTITIES ? named.subList(0, MAX_ENTITIES) : named;
	}

	/** The text an entity is showing, whichever way it is carrying it, or {@code null} for none. */
	private static Component nameOf(Entity entity) {
		if (entity instanceof Display.TextDisplay display) {
			return ((TextDisplayAccessor) display).aletheia$text();
		}
		return entity.getCustomName();
	}
}
