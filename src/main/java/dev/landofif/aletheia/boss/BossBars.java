package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.mixin.BossHealthOverlayAccessor;
import dev.landofif.aletheia.serverhud.HudGlyphs;
import dev.landofif.aletheia.serverhud.ServerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.BossEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The boss bars on screen, and how to tell which of them is a boss.
 *
 * <p>Two things read these -- {@link BossPhaseTracker} for the fight's health and
 * {@link Vulnerability} for its colour -- and both have the same problem to solve first: Telos draws
 * its <i>entire</i> HUD as a boss bar (see {@link ServerHud}), so there is always at least one bar on
 * screen that is not a fight at all.
 */
public final class BossBars {
	private BossBars() {
	}

	/**
	 * Every bar the overlay is holding, in the order it draws them.
	 *
	 * <p>The overlay's own collection, not a copy of it. Everything here reads these on the client
	 * thread -- which is where the bar packets are applied and where the overlay itself walks the same
	 * collection to draw -- so there is nothing to guard against, and four callers a tick each taking a
	 * copy of it is four pieces of rubbish a tick for nothing.
	 */
	public static Collection<LerpingBossEvent> onScreen() {
		Gui gui = Minecraft.getInstance().gui;
		if (gui == null) {
			return List.of();
		}
		return ((BossHealthOverlayAccessor) gui.getBossOverlay()).aletheia$events().values();
	}

	// ------------------------------------------------------------------ what a bar is

	/**
	 * What was worked out about one bar, kept until the server sends that bar again.
	 *
	 * <p>Reading a bar is not cheap -- it walks the component, looks every glyph up in the pack's font
	 * definition and builds a string out of what it finds -- and <b>three separate tickers ask the same
	 * question of the same bars every tick</b>: {@link Vulnerability} whether a boss is up,
	 * {@link BossPhaseTracker} which fight it is, {@link BossBar} which bars to redraw. Answering once
	 * and handing the same answer to all three is the difference between four walks of the server's
	 * whole HUD per tick and none.
	 *
	 * <p>The {@code name} is held to compare against, <b>by identity</b>: the component is the one the
	 * packet arrived in, so a different instance means the server has sent the bar again and everything
	 * here has to be worked out afresh. The filter is held for the same reason -- it is a setting, and
	 * changing it in the settings screen has to take effect on the next tick rather than the next fight.
	 *
	 * @param pictures the textures this bar draws, in the order it draws them
	 * @param keys     those picture names folded for matching, the filter-matching ones only
	 * @param allKeys  the same for every picture, which is what the phase table is looked up by
	 */
	private record BarInfo(Component name, String filter, List<String> pictures, List<String> keys,
			List<String> allKeys, @Nullable BossPhases.Boss known, boolean boss) {
	}

	/** Keyed by the bar's own id, so a bar that stays up keeps its answer across ticks. */
	private static final Map<UUID, BarInfo> INFO = new HashMap<>();

	/**
	 * How many bars are remembered before the lot is dropped. Telos puts up a handful; this is room to
	 * spare, and dropping them all is fine -- the next tick works out whatever is still on screen.
	 */
	private static final int MAX_REMEMBERED = 16;

	private static BarInfo info(BossEvent event) {
		Component name = event.getName();
		String filter = String.valueOf(AletheiaConfig.bossBarTextureFilter);

		BarInfo cached = INFO.get(event.getId());
		if (cached != null && cached.name() == name && cached.filter().equals(filter)) {
			return cached;
		}

		List<String> pictures = picturesOf(name);

		// Both lists in one pass over the pictures, since the folding is the same work for each and the
		// filtered names are a subset of the rest.
		List<String> keys = new ArrayList<>(1);
		List<String> allKeys = new ArrayList<>(1);
		boolean filtering = !filter.isBlank();
		for (String texture : pictures) {
			Matcher matcher = BOSS_PICTURE.matcher(texture);
			if (!matcher.find()) {
				continue;
			}
			String key = ChatText.lettersAndDigits(matcher.group(1));
			if (key.isEmpty()) {
				continue;
			}
			allKeys.add(key);
			if (filtering && ChatText.containsIgnoreCase(texture, filter) && !keys.contains(key)) {
				keys.add(key);
			}
		}

		// Every picture, not only the filtered ones: a fight this knows the phases of is a boss whatever
		// path its artwork lives on, which is what the filter being emptied has to keep working.
		BossPhases.Boss known = BossPhases.forPictures(allKeys);
		boolean boss = known != null || !keys.isEmpty();

		BarInfo fresh = new BarInfo(name, filter, pictures, keys, allKeys, known, boss);
		if (INFO.size() >= MAX_REMEMBERED) {
			INFO.clear();
		}
		INFO.put(event.getId(), fresh);
		return fresh;
	}

	/**
	 * The textures a bar's glyphs draw, in the order they are drawn.
	 *
	 * <p>One walk of the component rather than {@link ServerHud#runsIn}'s two -- that gathers the text
	 * of each font together first, which this has no use for, and building those strings is most of
	 * what it costs. Repeats are dropped, so a plate drawn twice is named once.
	 */
	private static List<String> picturesOf(Component name) {
		if (name == null) {
			return List.of();
		}
		// A set that keeps its order: the server's own HUD bar draws dozens of distinct pictures, and
		// scanning the list being built for each of them is quadratic in exactly the wrong place.
		Set<String> pictures = new LinkedHashSet<>();
		name.visit((style, content) -> {
			if (content.isEmpty()) {
				return Optional.empty();
			}
			String font = ServerHud.fontName(style);
			for (int index = 0; index < content.length(); ) {
				int glyph = content.codePointAt(index);
				index += Character.charCount(glyph);

				String texture = HudGlyphs.texture(font, glyph);
				if (texture != null) {
					pictures.add(texture);
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		return pictures.isEmpty() ? List.of() : new ArrayList<>(pictures);
	}

	/**
	 * Everything that says which fight a bar belongs to.
	 *
	 * <p><b>The name is a picture.</b> Lotil's bar is a single codepoint drawing
	 * {@code telos:glyph/bossbar/lotil.png} and no letters at all, which is why this went unmatched to
	 * begin with. The texture behind the glyph carries the name, and
	 * {@link dev.landofif.aletheia.serverhud.HudGlyphs} reads it out of the pack's font definition,
	 * exactly as the potion counter does.
	 *
	 * <p>That codepoint is <b>not</b> in a private-use area, which is worth knowing before writing any
	 * test that assumes it: the pack hangs its glyphs wherever it likes -- {@code U+160E4} on one of
	 * these bars, ordinary CJK ideographs on the chat lines -- so the only thing that can tell a
	 * picture from a letter is the pack's own font definition.
	 *
	 * <p>Any real text is kept as well, but only so {@code /aletheia boss} and the log can show what a
	 * bar carries. <b>It is not what the fight is matched on</b>: the server's HUD arrives as a bar too
	 * and its text opens with the name of the area you are in, so "Raphael's Castle" was enough to have
	 * the whole HUD read as Raphael, drawn at 0% and hidden. {@link BossPhases#forName} matches the
	 * picture only.
	 */
	public static String identityOf(BossEvent event) {
		StringBuilder identity = new StringBuilder(event.getName().getString());
		for (String texture : info(event).pictures()) {
			identity.append(' ').append(texture);
		}
		return identity.toString();
	}

	/**
	 * Whether a bar belongs to a boss rather than to the server's HUD.
	 *
	 * <p>A fight with phases is one by definition. Anything else has to be recognised by the picture
	 * it draws -- the pack keeps its 65 bar glyphs under {@code telos:glyph/bossbar/}, which the HUD's
	 * own plates and ribbons are nowhere near -- so bosses this mod knows nothing else about still get
	 * their colour watched.
	 */
	public static boolean isBoss(String identity) {
		return BossPhases.forName(identity) != null || matchesFilter(identity);
	}

	/**
	 * The same question asked of a live bar, which is how the tickers ask it.
	 *
	 * <p>Answered off the picture names alone rather than off the identity string, which is both
	 * cheaper -- nothing has to be built to ask -- and closer to what the rest of this class already
	 * says it means: the filter names an artwork path, and the phase table matches an artwork name, so
	 * neither has any business reading the words on the bar. The one thing that changes in practice is
	 * that a bar whose <i>text</i> happened to carry the filter no longer counts as a fight.
	 */
	public static boolean isBoss(BossEvent event) {
		return info(event).boss();
	}

	/** The fight this bar's artwork names, or {@code null} -- {@link BossPhases#forName} off the cache. */
	@Nullable
	public static BossPhases.Boss phasesOf(BossEvent event) {
		return info(event).known();
	}

	/**
	 * The names of the pictures this bar draws, folded the way {@link BossPhases} folds its keys.
	 *
	 * <p>Every picture rather than the filtered ones, for the same reason {@link #phasesOf} uses those:
	 * a fight this mod knows by name is that fight whatever path its artwork is filed under.
	 */
	public static List<String> keysOf(BossEvent event) {
		return info(event).allKeys();
	}

	/** Where this fight's bar really runs out, for {@link BossPhases#endsAt} off the cache. */
	public static double endsAtFor(BossEvent event) {
		return BossPhases.endsAtPictures(info(event).allKeys());
	}

	/** Whether a texture -- or a whole identity built out of them -- is under the configured path. */
	private static boolean matchesFilter(String text) {
		String filter = AletheiaConfig.bossBarTextureFilter;
		return filter != null && !filter.isBlank() && ChatText.containsIgnoreCase(text, filter);
	}

	/** e.g. {@code blue} -- the colour the server set on the bar, whatever the pack draws. */
	public static String colourName(BossEvent event) {
		return event.getColor().getName();
	}

	/**
	 * Pulls {@code hierophant} out of {@code telos:glyph/bossbar/hierophant.png}.
	 *
	 * <p>Capitals are accepted so a raw texture name can be matched without folding it first. Every
	 * caller either lower-cases what it captures or hands it to {@link ChatText#lettersAndDigits},
	 * which does, so nothing downstream can tell the difference.
	 */
	private static final Pattern BOSS_PICTURE = Pattern.compile("([A-Za-z0-9_]+)\\.png");

	/**
	 * What a bar is really called, where the file its art lives in says otherwise.
	 *
	 * <p>The name is written into the artwork rather than into the file name, and the two do not always
	 * agree. All 65 of the pack's bar glyphs were read off their own art on 2026-08-22 and only the
	 * three beginning "onyx" disagree -- so this table is short on purpose, and it is complete for that
	 * pack rather than a place to put guesses.
	 *
	 * <p>{@code onyx.png} is the third of them: it draws "RAPHAEL" and is named by {@link BossPhases},
	 * which knows that fight's phases as well. These two have no phases worth calling, so a name is all
	 * they need -- {@code onyx2.png} is the bar that really does say "ONYX", and
	 * {@code onyx_guardian.png} says "ORION AND OSIRIS", the pair of champions in Raphael's Castle.
	 *
	 * <p>Keyed by the folded picture name, the way {@link BossPhases} keys its fights.
	 */
	private static final Map<String, String> PICTURE_NAMES = Map.of(
			"onyxguardian", "Orion and Osiris",
			"onyx2", "Onyx");

	/**
	 * The boss pictures one bar draws, in the order it draws them.
	 *
	 * <p>Held to the configured path, because a bar's name is not only its own -- the server packs HUD
	 * glyphs into the same components, and {@code assets/rotmc2/plate.png} would otherwise be as good a
	 * name for the fight as the boss's own portrait.
	 */
	private static List<String> bossPictures(BossEvent event) {
		List<String> pictures = new ArrayList<>(1);
		for (String texture : info(event).pictures()) {
			if (matchesFilter(texture)) {
				pictures.add(texture);
			}
		}
		return pictures;
	}

	/**
	 * What to call the fight this bar belongs to, in words fit to put on screen.
	 *
	 * <p>Three sources, best first. A fight {@link BossPhases} knows is named properly there -- that is
	 * where Raphael's name comes from, since the art for it is filed as {@code bossbar/onyx.png}.
	 * Failing that it is the <b>picture this bar draws</b>, which is the file name title-cased
	 * ({@code arctic_colossus.png} becomes "Arctic Colossus") unless {@link #PICTURE_NAMES} knows the
	 * file to be lying. Only then is the bar's own text used, for a bar with no picture at all.
	 *
	 * <p>The picture goes above the text on purpose. A fight with two bosses in it puts up a bar each,
	 * and the picture is the one thing on those bars that differs -- any words that come with them are
	 * as likely to be the arena's name, which would give both bosses the same name and lose one of them.
	 */
	public static String displayName(BossEvent event) {
		BossPhases.Boss known = info(event).known();
		if (known != null) {
			return known.name();
		}

		for (String texture : bossPictures(event)) {
			Matcher matcher = BOSS_PICTURE.matcher(texture.toLowerCase(Locale.ROOT));
			if (matcher.find()) {
				return nameOfPicture(matcher.group(1));
			}
		}
		return readable(event.getName());
	}

	/**
	 * The parts of a bar's name that are actually words, for drawing in the game's own font.
	 *
	 * <p><b>A picture glyph is not a private-use character</b>, which was the first guess and is why
	 * these leaked through as a box with a codepoint in it. Telos hangs its pack glyphs on ordinary
	 * codepoints -- {@code U+160E4} on one bar, plain CJK ideographs on the chat lines -- so no range
	 * of numbers can pick them out. The pack itself can: {@link HudGlyphs} already knows which
	 * codepoint draws which texture in which font, since that is how the bar was identified to begin
	 * with, so anything it names a picture is dropped here.
	 *
	 * <p>Whatever survives that is then held to what the vanilla font can be expected to draw --
	 * letters, digits, spaces and the punctuation a name might carry. That is the backstop: a glyph the
	 * pack definition does not cover cannot reach the screen as a box, it simply leaves the name empty
	 * and the texture below is used instead.
	 */
	private static String readable(Component name) {
		if (name == null) {
			return "";
		}

		StringBuilder out = new StringBuilder();
		name.visit((style, content) -> {
			String font = ServerHud.fontName(style);
			content.codePoints().forEach(codePoint -> {
				if (HudGlyphs.textureOf(font, codePoint).isPresent() || !isWordy(codePoint)) {
					return;
				}
				out.appendCodePoint(codePoint);
			});
			return Optional.empty();
		}, Style.EMPTY);
		return out.toString().trim();
	}

	/** Punctuation a boss's name might really contain, as opposed to anything else that got in. */
	private static final String NAME_PUNCTUATION = " '’-_.,:!?()&";

	private static boolean isWordy(int codePoint) {
		return Character.isLetterOrDigit(codePoint) || NAME_PUNCTUATION.indexOf(codePoint) >= 0;
	}

	/**
	 * What to call the fight a picture draws: the name written into the art, where that is known to
	 * differ from the file, and the file name title-cased otherwise.
	 *
	 * @param picture a bar glyph's file name without its extension, e.g. {@code onyx_guardian}
	 */
	public static String nameOfPicture(String picture) {
		String named = PICTURE_NAMES.get(ChatText.lettersAndDigits(picture));
		return named != null ? named : titleCase(picture);
	}

	/** {@code arctic_colossus} to {@code Arctic Colossus}. */
	private static String titleCase(String key) {
		StringBuilder out = new StringBuilder(key.length());
		for (String word : key.split("[_\\-]+")) {
			if (word.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
		}
		return out.toString();
	}

	/**
	 * What the boss bars on screen say the fight is called, folded for matching.
	 *
	 * <p>The bars over the mobs sometimes spell the same name -- Hierophant's does -- so this is worth
	 * having as a way of picking the boss out of its arena. Only bars that count as bosses contribute,
	 * so the server's HUD cannot put its own textures in here.
	 */
	public static Set<String> keysOnScreen() {
		Set<String> keys = null;
		for (LerpingBossEvent event : onScreen()) {
			BarInfo info = info(event);
			if (!info.boss() || info.keys().isEmpty()) {
				continue;
			}
			if (keys == null) {
				keys = new LinkedHashSet<>();
			}
			keys.addAll(info.keys());
		}
		// The usual answer out of a fight, and the one the rescan is asked for most: nothing at all.
		return keys == null ? Set.of() : keys;
	}
}
