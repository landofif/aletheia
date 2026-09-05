package dev.landofif.aletheia.boss;

import dev.landofif.aletheia.detect.ChatText;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * When each endgame boss changes what it is doing, as a fraction of its health bar.
 *
 * <p>These fights are not damage races -- they are a sequence of scripted phases, and every phase
 * change is at a <b>fixed</b> percentage. Which means the boss bar, a number the client already has,
 * says exactly how long you have before the arena turns into something else. That is the whole idea
 * here: read the bar, watch for a threshold coming up, and say so before it lands rather than after.
 *
 * <p>No Minecraft types, so the table and the matching can be exercised on their own.
 *
 * <p>Only the transitions that <i>change what you must do</i> are listed. A boss swapping from one
 * attack set to another needs no announcement -- you can see it -- but the arena going dark, or the
 * fire ring starting to close, is something you want a second's warning of.
 */
public final class BossPhases {
	private BossPhases() {
	}

	/**
	 * @param at   the health fraction the phase begins at, e.g. {@code 0.60}
	 * @param name what to put on screen -- the whole of it; the title carries no explanation
	 */
	public record Phase(double at, String name) {
	}

	/**
	 * @param key    the name of the bar's glyph texture, folded to letters and digits -- the whole of
	 *               it, since {@link #forName} matches picture names exactly rather than by fragment
	 * @param name   what to call it on the readout
	 * @param phases in descending order of health, which {@link #next} relies on
	 */
	public record Boss(String key, String name, List<Phase> phases) {
	}

	/**
	 * The endgame dungeons, and the two ordinary bosses that gate two of them.
	 *
	 * <p>Keys are the <b>texture names the server's own pack uses</b>, taken from
	 * {@code telos:glyph/bossbar/}: the wiki's "True Seraph" and "True Ophan" are
	 * {@code hardmode_seraphim} and {@code hardmode_ophanim} there. Matching on those rather than on
	 * the wiki's wording is what makes this work at all, since the bar has no wording -- see
	 * {@link #forName}.
	 *
	 * <p><b>A key is the whole of a picture's name, never a fragment of one.</b> {@code seraphim} sits
	 * inside {@code hardmode_seraphim}, {@code omnipotent} inside {@code voided_omnipotent} and
	 * {@code onyx} inside {@code onyx_guardian} -- and in every one of those pairs the two names are
	 * different fights, which is why {@link #forName} compares whole names. Order is only a tiebreak
	 * now, but it is left in the shape that reads best and there are tests holding the near-misses
	 * apart.
	 *
	 * <p>Seraphim and Ophanim run the same set pieces as the hardmode versions they lead to -- the QR
	 * code, the walls and the clock happen in both -- so they are worth calling in the ordinary fight.
	 * Their <b>thresholds are taken from the hardmode version</b>, which is an assumption: the phase
	 * is known to be there, the percentage it starts at is not.
	 *
	 * <p>The Shadowlands Defender is absent for the stronger reason that nothing says where its one
	 * transition is at all, and a title at the wrong moment is worse than no title.
	 */
	private static final List<Boss> BOSSES = List.of(
			new Boss("cherubim", "Cherubim", List.of(
					new Phase(0.60, "BLACK HOLE"),
					new Phase(0.20, "DESPERATION"))),

			new Boss("hardmodeseraphim", "True Seraph", List.of(
					new Phase(0.50, "QR CODE"),
					new Phase(0.20, "DESPERATION"))),

			new Boss("hardmodeophanim", "True Ophan", List.of(
					new Phase(0.85, "WALLS"),
					new Phase(0.60, "CLOCK"),
					new Phase(0.15, "DESPERATION"))),

			new Boss("seraphim", "Seraphim", List.of(
					new Phase(0.50, "QR CODE"))),

			new Boss("ophanim", "Ophanim", List.of(
					new Phase(0.85, "WALLS"),
					new Phase(0.60, "CLOCK"))),

			// The wiki names these two itself: phase 2 is "Shulker", phase 4 is "Arrows". The 50% mark
			// is left out on purpose -- it is a return to the ordinary attack cycle, which you can see.
			new Boss("sylvaris", "Sylvaris", List.of(
					new Phase(0.75, "SHULKER"),
					new Phase(0.25, "ARROWS"))),

			// Raphael's Chamber, the fight past Raphael's Castle. Six phases, of which three change what
			// you have to do; the other three are the attack cycles between them and are not called.
			// The names are the wiki's own -- "Memorise" is the one to dodge beams from portals three
			// times over, "Bell" fires a volley on every ring, and the last is the usual invulnerable
			// bleed-out.
			//
			// NOTE: the key is "onyx" because that is what the pack calls this bar's art --
			// telos:glyph/bossbar/onyx.png, which draws "RAPHAEL / THE SANGUINE LORD". The file name is
			// not the fight's name here, and the two names next to it are neither this fight nor each
			// other: onyx2.png is the bar that really does say "ONYX", and onyx_guardian.png says "ORION
			// AND OSIRIS", the pair of champions in Raphael's Castle. Whole-name matching is what keeps
			// those three apart.
			//
			// This was matched on the text "raphael" before, on the belief that the bar had no glyph and
			// spelled itself out. What that really matched was the server's HUD bar, whose text begins
			// with the area name: inside "Raphael's Castle" and "Raphael's Chamber" the entire HUD was
			// taken for the fight, drawn as a bar stuck at 0%, and hidden off the screen it belongs on.
			new Boss("onyx", "Raphael", List.of(
					new Phase(0.75, "MEMORISE"),
					new Phase(0.50, "BELL"),
					new Phase(0.15, "DESPERATION"))),

			// Tenebris, the dungeon Raphael's Chamber drops the key to. Six phases, of which three change
			// what you have to do. The wiki's own names are "Chase" and "Bells"; the last one it describes
			// as the Void erupting -- lasers, and its eyes to right-click three times -- which is called
			// DESPERATION here to match the other fights' final phase.
			//
			// The 65% mark is left out for the same reason as Sylvaris's 50%: it is the ordinary cycle of
			// snakes, pillars and black holes, which you can see. So is the opening phase above 85%, and
			// the unchaining before the bar moves at all.
			//
			// NOTE: the pack has *both* telos:glyph/bossbar/omnipotent.png and voided_omnipotent.png. They
			// are two fights, and only this one has phases worth calling -- which is safe because a key is
			// a whole picture name: "omnipotent" no more matches this than "onyx" matches onyx_guardian.
			new Boss("voidedomnipotent", "Voided Omnipotent", List.of(
					new Phase(0.85, "CHASE"),
					new Phase(0.30, "BELL"),
					new Phase(0.15, "DESPERATION"))));

	/**
	 * The endgame fights, for the calls that belong to the party rather than to the boss -- see
	 * {@link BossReminders}.
	 *
	 * <p>This is a wider list than {@link #BOSSES} and a different kind of thing. That table says what
	 * a fight <i>does</i> at a percentage, which has to be known before it can be called. This one only
	 * says which fights are long enough to be worth planning against, so it can name the three bosses
	 * whose phases are not written down anywhere -- Asmodeus, Valerion and Nebula -- alongside the eight
	 * that are.
	 *
	 * <p>Every dungeon's whole route is here, in the order {@code DungeonRoutes} runs them: Celestial's
	 * Province, Rustborn Kingdom, Neo Eden, Tenebris and Raphael's Chamber. The realm bosses are left
	 * out because a call at 65% of a bar that empties in fifteen seconds is a title in the way of the
	 * fight, and titles you learn to ignore are worse than none.
	 *
	 * <p>Keys are picture names, folded and matched whole exactly as {@link #BOSSES} keys are, so
	 * {@code seraphim} is the ordinary fight and never {@code hardmode_seraphim}, and {@code onyx} is
	 * Raphael and never {@code onyx2} or {@code onyx_guardian}. <b>The pack spells Valerion with the
	 * vowels in that order</b> -- {@code valerion.png} -- whatever the server's own leaderboards say.
	 */
	private static final Set<String> ENDGAME = Set.of(
			// Celestial's Province, and the Seraph's Domain it opens.
			"asmodeus",
			"seraphim",
			"hardmodeseraphim",
			// Rustborn Kingdom, and the Dawn of Creation. Mithrion is fought alongside Nebula and its bar
			// is not listed on its own, since Nebula's is up for the same stage and one call is enough.
			"valerion",
			"nebula",
			"ophanim",
			"hardmodeophanim",
			// Neo Eden. Apostle and Hierophant are the stage before, and are over at half a bar each.
			"cherubim",
			// The Voidlands.
			"sylvaris",
			// Tenebris, and Raphael's Chamber -- whose art is filed as onyx.png; see BOSSES above.
			"voidedomnipotent",
			"onyx");

	/** Pulls {@code onyx} out of {@code telos:glyph/bossbar/onyx.png}. */
	private static final Pattern PICTURE = Pattern.compile("([A-Za-z0-9_]+)\\.png");

	/**
	 * The pictures a bar draws, each folded the way the keys above are folded.
	 *
	 * <p><b>Only pictures.</b> An identity is a bar's text and its textures together, and the text is
	 * not the bar's alone: the server draws its whole HUD as a bar too, and that one's text opens with
	 * the name of the area you are standing in. "Raphael's Castle" carries "raphael" inside it, which
	 * is all it took to read the HUD as the fight -- so what is matched is the name of the artwork,
	 * which only the bar it belongs to draws.
	 */
	private static List<String> picturesIn(String bossBarName) {
		if (bossBarName == null || bossBarName.isEmpty()) {
			return List.of();
		}

		List<String> pictures = new ArrayList<>(1);
		Matcher matcher = PICTURE.matcher(bossBarName);
		while (matcher.find()) {
			String key = ChatText.lettersAndDigits(matcher.group(1));
			if (!key.isEmpty()) {
				pictures.add(key);
			}
		}
		return pictures;
	}

	/**
	 * @param bossBarName everything that identifies a bar: its text, and the names of the textures
	 *                    behind its glyphs. <b>The name is a picture</b> -- Lotil's bar is one
	 *                    codepoint drawing {@code telos:glyph/bossbar/lotil.png} and no text at all,
	 *                    and even Raphael's, which looks spelled out, is really
	 *                    {@code bossbar/onyx.png} -- so the texture is the only thing here matched,
	 *                    and the caller is expected to have looked it up.
	 * @return the fight it belongs to, or {@code null} for a bar this does not know about
	 */
	public static Boss forName(String bossBarName) {
		return forPictures(picturesIn(bossBarName));
	}

	/**
	 * The same lookup for a caller that already has the picture names.
	 *
	 * <p>{@link BossBars} reads the textures off a bar once and keeps them, so asking it again three
	 * times a tick need not run the regex above over a string built for the purpose.
	 *
	 * @param pictures picture names folded the way {@link Boss#key()} is folded
	 */
	@Nullable
	public static Boss forPictures(List<String> pictures) {
		for (String picture : pictures) {
			for (Boss boss : BOSSES) {
				if (picture.equals(boss.key())) {
					return boss;
				}
			}
		}
		return null;
	}

	/**
	 * Whether these pictures name a fight the ambush and deathmark calls are made in.
	 *
	 * @param pictures picture names folded the way {@link Boss#key()} is folded
	 * @return the key that matched, so the caller can say which fight it is following, or {@code null}
	 */
	@Nullable
	public static String endgameKey(List<String> pictures) {
		for (String picture : pictures) {
			if (ENDGAME.contains(picture)) {
				return picture;
			}
		}
		return null;
	}

	/** {@link #endgameKey} for a caller holding a raw bar name, as {@link #forName}. */
	@Nullable
	public static String endgameKeyOf(String bossBarName) {
		return endgameKey(picturesIn(bossBarName));
	}

	/**
	 * The fights that are over before their bar is empty.
	 *
	 * <p>Apostle and Hierophant are one encounter with two bosses in it, and <b>both die at half
	 * health</b>: the bar stops at 50% and the fight ends there. Drawn as the server sends it, such a
	 * bar spends the whole fight claiming there is twice as much left as there really is -- and the
	 * half that reads as full is the half you never get to take. {@link #remaining} rescales it.
	 *
	 * <p>Matched the way {@link #forName} matches: on the whole name of the texture behind the bar's
	 * glyph, folded to letters and digits.
	 */
	private record Floor(String key, double at) {
	}

	private static final List<Floor> FLOORS = List.of(
			new Floor("apostle", 0.50),
			new Floor("hierophant", 0.50));

	/**
	 * The bar reading this fight is already over at, or {@code 0} for one that has to be taken all the
	 * way down like everything else.
	 *
	 * @param bossBarName the same thing {@link #forName} takes -- normally a texture name
	 */
	public static double endsAt(String bossBarName) {
		return endsAtPictures(picturesIn(bossBarName));
	}

	/** {@link #endsAt} for a caller that already has the picture names, as {@link #forPictures}. */
	public static double endsAtPictures(List<String> pictures) {
		for (String picture : pictures) {
			for (Floor floor : FLOORS) {
				if (picture.equals(floor.key())) {
					return floor.at();
				}
			}
		}
		return 0.0;
	}

	/**
	 * The health there is left to take, as a fraction of the health there was: the bar rescaled so the
	 * reading the fight dies at reads as empty.
	 *
	 * @param progress the bar as the server sends it
	 * @param endsAt   what {@link #endsAt} said about this fight -- {@code 0} leaves the bar alone
	 */
	public static float remaining(float progress, double endsAt) {
		if (endsAt <= 0.0) {
			return clamp(progress);
		}
		if (endsAt >= 1.0) {
			return 0.0F;
		}
		return clamp((float) ((progress - endsAt) / (1.0 - endsAt)));
	}

	private static float clamp(float fraction) {
		return fraction < 0.0F ? 0.0F : Math.min(fraction, 1.0F);
	}

	/**
	 * The next phase at or below the current health.
	 *
	 * @param progress the boss bar, {@code 1.0} down to {@code 0.0}
	 * @return the phase being counted down to, or {@code null} once the last one has been passed
	 */
	public static Phase next(Boss boss, double progress) {
		for (Phase phase : boss.phases()) {
			if (progress > phase.at()) {
				return phase;
			}
		}
		return null;
	}

	/**
	 * The phase the fight is in right now.
	 *
	 * @return the deepest phase whose threshold has been passed, or {@code null} while the fight is
	 *         still above the first one
	 */
	public static Phase current(Boss boss, double progress) {
		Phase inside = null;
		for (Phase phase : boss.phases()) {
			if (progress <= phase.at()) {
				inside = phase;
			}
		}
		return inside;
	}

	/** Every fight this knows, for the settings screen and the tests. */
	public static List<Boss> all() {
		return BOSSES;
	}

	/** e.g. {@code 63%} -- how the readout writes a bar. */
	public static String asPercent(double progress) {
		return Math.round(progress * 100.0) + "%";
	}

	/** Folded the same way {@link #forName} folds, for callers holding a raw name. */
	public static String key(String bossBarName) {
		return ChatText.lettersAndDigits(bossBarName).toLowerCase(Locale.ROOT);
	}
}
