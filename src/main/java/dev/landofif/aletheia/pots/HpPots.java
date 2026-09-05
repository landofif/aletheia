package dev.landofif.aletheia.pots;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.serverhud.HudGlyphs;
import dev.landofif.aletheia.serverhud.ServerHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How many health potions the server says you have left.
 *
 * <p>The count is already on screen -- it is one of the fields of the MythicHUD boss bar, drawn in
 * the pack font {@code mythichud:layout/rotmc2-layout/fonts/rotmc2-hud/hppot}. So this does not
 * count anything itself: it finds that run by its font, which is the same trick
 * {@code /aletheia hudscan} uses and the only dependable marker the server leaves behind (see
 * {@link ServerHud}). Matching is on a substring of the font name, so the readout survives the pack
 * renaming its layout folder, and can be pointed somewhere else from the settings.
 *
 * <p><b>The field is not text.</b> It is a single private-use codepoint that draws a picture -- five
 * pots left is {@code U+1606A}, whose texture is {@code hud_hp5.png} -- so reading the characters
 * gives nothing at all. The number has to come from the <i>texture</i> the pack maps that codepoint
 * to, which {@link HudGlyphs} reads out of the font definition.
 *
 * <p>Read on a timer and cached, rather than per frame: the scan walks every channel on screen. See
 * {@link AletheiaConfig#potsCheckTicks}.
 */
public final class HpPots {
	private HpPots() {
	}

	/**
	 * The count is a <b>picture</b>, not a number: the pack carries one glyph per amount, named
	 * {@code hud_hp0.png} through {@code hud_hp10.png} (codepoints {@code U+16065}..{@code U+1606F}),
	 * and the server sends whichever one is right. So the reading is the digits at the end of the
	 * texture's file name, which the font definition gives up via {@link HudGlyphs}.
	 */
	private static final Pattern TEXTURE_COUNT = Pattern.compile("(\\d{1,3})\\.png$", Pattern.CASE_INSENSITIVE);

	/** Kept as a fallback in case the field is ever sent as text: "3/5" or a bare "3". */
	private static final Pattern PAIR = Pattern.compile("(\\d{1,3})\\s*/\\s*(\\d{1,3})");
	private static final Pattern SINGLE = Pattern.compile("\\d{1,3}");

	/**
	 * How long a reading stands after the server stops sending it. The bar is redrawn constantly, so
	 * a gap this long means it has genuinely gone -- but a frame or two of absence should not make
	 * the readout blink.
	 */
	private static final long GRACE_MILLIS = 3000L;

	private static int count = -1;
	private static int serverMax = -1;
	private static long seenAtMillis;

	/** Ticks to go before the panel is read again; see {@link AletheiaConfig#potsCheckTicks}. */
	private static int untilRead;

	/** Ceiling on that setting, so the count cannot be set so stale it is wrong when you need it. */
	private static final int MAX_CHECK_TICKS = 20;

	/** Kept for {@code /aletheia pots}. */
	private static String lastFont = "";
	private static String lastText = "";
	private static String lastTexture = "";

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(HpPots::tick);
	}

	private static void tick(Minecraft client) {
		if (client.player == null) {
			forget();
			untilRead = 0;
			return;
		}
		if (!AletheiaConfig.enabled || !AletheiaConfig.showPotsHud) {
			return;
		}

		// Reading means walking every channel the server has text on -- every boss bar, every row of the
		// player list, the whole sidebar -- and picking the one run drawn in the potion font out of them.
		// That is a lot of work for a number that changes when you drink, so it is not done twenty times
		// a second. The grace period below is far longer than the gap, so nothing blinks in between.
		if (untilRead > 0) {
			untilRead--;
			return;
		}
		untilRead = Mth.clamp(AletheiaConfig.potsCheckTicks, 1, MAX_CHECK_TICKS) - 1;

		read();
	}

	private static void read() {
		String filter = AletheiaConfig.potsFontFilter;
		if (filter == null || filter.isBlank()) {
			forget();
			return;
		}
		String wanted = filter.toLowerCase(Locale.ROOT);

		for (ServerHud.Line line : ServerHud.snapshot()) {
			for (ServerHud.Run run : line.runs()) {
				if (!run.font().toLowerCase(Locale.ROOT).contains(wanted)) {
					continue;
				}
				lastFont = run.font();
				lastText = ChatText.normalize(run.text());
				if (take(run)) {
					return;
				}
			}
		}

		// The field is there but unreadable, or gone entirely. Either way the last number stands for a
		// moment before the readout gives up on it.
		if (count >= 0 && System.currentTimeMillis() - seenAtMillis > GRACE_MILLIS) {
			forget();
		}
	}

	/** @return whether the run held a reading worth keeping */
	private static boolean take(ServerHud.Run run) {
		// The picture the server chose is the count, so this is the path that fires on Telos.
		for (Map.Entry<Integer, String> picture : run.pictures().entrySet()) {
			Matcher texture = TEXTURE_COUNT.matcher(picture.getValue());
			if (texture.find()) {
				lastTexture = picture.getValue();
				count = Integer.parseInt(texture.group(1));
				// One glyph per amount, so the picture says how many are left and nothing else.
				serverMax = -1;
				seenAtMillis = System.currentTimeMillis();
				return true;
			}
		}
		return takeFromText(ChatText.normalize(run.text()));
	}

	/** The same reading as plain digits, for a field the pack draws with the ordinary font. */
	private static boolean takeFromText(String text) {
		Matcher pair = PAIR.matcher(text);
		if (pair.find()) {
			count = Integer.parseInt(pair.group(1));
			serverMax = Integer.parseInt(pair.group(2));
			seenAtMillis = System.currentTimeMillis();
			return true;
		}

		Matcher single = SINGLE.matcher(text);
		if (single.find()) {
			count = Integer.parseInt(single.group());
			// Only the count is published, so the total has to come from the settings.
			serverMax = -1;
			seenAtMillis = System.currentTimeMillis();
			return true;
		}
		return false;
	}

	private static void forget() {
		count = -1;
		serverMax = -1;
	}

	/** Whether there is a reading recent enough to show. */
	public static boolean has() {
		return count >= 0 && System.currentTimeMillis() - seenAtMillis <= GRACE_MILLIS;
	}

	public static int count() {
		return count;
	}

	/** The server's own total where it sends one, and the configured total otherwise. */
	public static int total() {
		return serverMax > 0 ? serverMax : Math.max(1, AletheiaConfig.potsMax);
	}

	/** e.g. {@code Pots: 1/5}, or {@code Pots: ?/5} when the server is not reporting. */
	public static String statusLine() {
		String label = AletheiaConfig.potsLabel == null ? "" : AletheiaConfig.potsLabel.trim();
		String reading = (has() ? Integer.toString(count) : "?") + "/" + total();
		return label.isEmpty() ? reading : label + ": " + reading;
	}

	/** The font the number was last read from -- empty if it has never been found. */
	public static String lastFont() {
		return lastFont;
	}

	/** That run's text, folded to ASCII -- empty when the field is drawn as a picture, as here. */
	public static String lastText() {
		return lastText;
	}

	/** The texture the count was last read off, e.g. {@code mythichud:font/default/hud_hp4.png}. */
	public static String lastTexture() {
		return lastTexture;
	}
}
