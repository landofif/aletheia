package dev.landofif.aletheia.config;

import net.minecraft.ChatFormatting;

import java.util.Arrays;

/**
 * Every setting the mod exposes, as plain static fields.
 *
 * <p>{@link ConfigScreen} binds the settings screen to these fields and {@link ConfigFile} reads and
 * writes them, so everything else in the mod just reads the field it cares about.
 */
public final class AletheiaConfig {

	private AletheiaConfig() {
	}

	// ------------------------------------------------------------------ General

	public static boolean enabled = true;

	public static boolean restrictToServer = false;
	public static String serverAddressFilter = "telos";

	public static boolean scanPlayerChat = false;
	public static boolean scanActionBar = false;

	public static boolean strictProgressMatching = false;
	public static String extraIgnoredPhrases = "";
	public static boolean quietPassengerWarnings = true;
	public static boolean debugLogging = false;

	// ------------------------------------------------------------------ Chat

	public static boolean shortenPlayerVisibility = true;
	public static String playersShownText = "Players: [shown]";
	public static String playersHiddenText = "Players: [hidden]";

	// ------------------------------------------------------------------ Action bar

	public static boolean hideActionBar = false;
	public static String actionBarHideFilter = "";

	// ------------------------------------------------------------------ Server HUD

	public static boolean hideServerHud = false;
	public static String serverHudHideFilter = "";

	// ------------------------------------------------------------------ World

	/** Whether the props named by {@link #propHideFilter} are drawn. */
	public static boolean hideProps = false;

	/**
	 * Which things in the world to leave undrawn, as a comma-separated list, matched against an
	 * entity's type and against the item model, item or block a display entity is holding.
	 *
	 * <p>The default is the Arcanist monolith. Trimming a phrase covers more -- {@code arcanist_orb}
	 * takes every tier of the orb rather than the one -- and {@code /aletheia props} prints the
	 * strings being matched, so a phrase can be read off rather than guessed at.
	 *
	 * <p>An empty filter hides nothing at all, since everything here means every entity in the world.
	 */
	public static String propHideFilter = "arcanist_orb_n1a";

	// ------------------------------------------------------------------ Magnificat domain

	/** Whether a ring is drawn where Magnificat's fire circle is. */
	public static boolean showDomainRing = true;

	/** Whether the server's own fire circle is drawn underneath it. */
	public static boolean hideDomainCircle = true;

	/**
	 * Which item display is a domain circle, as a comma-separated list matched against its item model.
	 *
	 * <p>The default is Magnificat's outer circle. {@code /aletheia domain} prints the model id of
	 * everything it has found, so this can be read off rather than guessed at, and an empty filter
	 * switches the whole feature off however the two settings above are left.
	 */
	public static String domainFilter = "magic_fire_circle2/out";

	/**
	 * How far out the ring is drawn, in blocks. <b>0 measures it from the circle itself</b>, which is
	 * what you want: the size comes off the display's scale, so the ring is whatever size the fire
	 * actually is. Set a number here only if a patch ever makes that measurement wrong.
	 */
	public static int domainRadius = 0;

	/** The ring's colour while you are standing inside the domain. */
	public static String domainInsideColour = "#5AD022";

	/** The ring's colour while you are outside it. */
	public static String domainOutsideColour = "#E03C31";

	public static int domainRingAlpha = 60;

	/** How tall the ring stands, in tenths of a block. 0 lays it flat on the floor. */
	public static int domainRingHeight = 2;

	// ------------------------------------------------------------------ Titles

	public static int titleFadeInTicks = 2;
	public static int titleStayTicks = 30;
	public static int titleFadeOutTicks = 8;
	public static boolean titleShadow = false;

	public static int alertSound = 0;
	public static int alertVolume = 100;

	// ------------------------------------------------------------------ Score progress

	public static boolean showProgressTitle = true;
	public static String unknownTotalPlaceholder = "???";
	public static String progressSubtitle = "SCORE";
	public static int progressColour = 2;
	public static boolean progressSound = false;

	// ------------------------------------------------------------------ Puzzle

	public static boolean showPuzzleStartTitle = true;
	public static String puzzleStartTitleText = "PUZZLE STARTED";
	public static boolean puzzleStartShowPlayer = true;
	public static int puzzleStartColour = 1;
	public static boolean puzzleStartSound = true;

	public static boolean showPuzzleTitle = true;
	public static String puzzleTitleText = "PUZZLE SOLVED";
	public static boolean puzzleShowPlayer = true;
	public static int puzzleColour = 4;
	public static boolean puzzleSound = true;

	// ------------------------------------------------------------------ Barracks

	public static boolean showBarracksStartTitle = true;
	public static String barracksStartTitleText = "BARRACKS STARTED";
	public static boolean barracksStartShowPlayer = true;
	public static int barracksStartColour = 1;
	public static boolean barracksStartSound = true;

	public static boolean showBarracksTitle = true;
	public static String barracksTitleText = "BARRACKS DONE";
	public static boolean barracksShowPlayer = true;
	public static int barracksColour = 4;
	public static boolean barracksSound = true;

	// ------------------------------------------------------------------ Dungeon timer

	public static boolean showTimerHud = true;
	public static String timerLabel = "run";

	/**
	 * <b>Extra</b> dimensions that count as a dungeon, as a comma-separated list matched against the
	 * dimension id.
	 *
	 * <p>Empty by default, and normally left that way: a Telos dungeon room is
	 * {@code <name>/<number>} and the open world is not, so {@code DungeonDimension} recognises one
	 * without being told any names. This is the escape hatch for a dungeon that does not follow that
	 * shape -- {@code /aletheia timer} prints the dimension you are standing in, ready to paste.
	 *
	 * <p>It is no longer what the personal best is filed under. It could not be: the id names an
	 * instance slot rather than a dungeon, so it changes from one run of the same dungeon to the next.
	 * The name comes from the server's own ribbon instead, via {@code HudArea}.
	 */
	public static String timerDimensionFilter = "";

	public static boolean timerShowBest = true;
	public static String timerBestLabel = "PB";
	public static int timerTimeFormat = 0;
	public static int timerColour = 0;
	public static int timerPastBestColour = 3;
	public static boolean timerAlwaysShow = false;

	public static int timerAnchor = 0;
	public static int timerOffsetX = 4;
	public static int timerOffsetY = 52;
	public static int timerScale = 100;

	// ------------------------------------------------------------------ Dungeon splits

	public static boolean showSplitsHud = true;
	public static boolean splitsShowTitle = true;
	public static boolean splitsShowDelta = true;

	/** What a stage you have not reached yet shows instead of a time. */
	public static String splitsPendingText = "--";

	public static int splitsColour = 0;
	public static int splitsBehindColour = 3;
	public static boolean splitsAlwaysShow = false;

	public static int splitsAnchor = 0;
	public static int splitsOffsetX = 4;
	public static int splitsOffsetY = 68;
	public static int splitsScale = 100;

	// ------------------------------------------------------------------ Dreadwood Thicket

	public static boolean showDreadwoodStartTitle = true;
	public static String dreadwoodStartTitleText = "{room} ROOM";
	public static String dreadwoodStartSubtitle = "{player}";
	public static int dreadwoodStartColour = 1;
	public static boolean dreadwoodStartSound = true;

	public static boolean showDreadwoodClearedTitle = true;
	public static String dreadwoodClearedTitleText = "{done}/{total}";
	public static String dreadwoodClearedSubtitle = "{room} ROOM DONE";
	public static int dreadwoodClearedColour = 4;
	public static boolean dreadwoodClearedSound = true;

	public static int dreadwoodRoomsPerRun = 2;

	/**
	 * <b>Extra</b> dimensions that count as being still in the dungeon, as a comma-separated list.
	 *
	 * <p>Empty by default, and the same escape hatch as {@link #timerDimensionFilter} -- a numbered
	 * room is recognised without it. It held {@code dreadwood} until 2026-08-29, which matched no
	 * dimension Telos has: the Thicket is handed out as {@code telos:dungeon/<n>} like every other
	 * dungeon, so the count was only ever dropped by the idle timeout below.
	 */
	public static String dreadwoodDimensionFilter = "";

	public static int dreadwoodForgetMinutes = 15;

	// ------------------------------------------------------------------ Shadowlands

	public static boolean showShadowlandsTitle = true;
	public static String shadowlandsTitleText = "{mob} SPAWNED";
	public static String shadowlandsSubtitle = "";
	public static int shadowlandsColour = 6;
	public static boolean shadowlandsSound = true;
	public static int shadowlandsRepeatSeconds = 0;

	// ------------------------------------------------------------------ Cherubim

	public static boolean showCherubimTitle = true;
	public static String enoughTitleText = "ENOUGH!";
	public static String silenceTitleText = "SILENCE!";
	public static String cherubimSubtitle = "STOP ATTACKING";
	public static int cherubimColour = 3;
	public static int cherubimStayTicks = 40;
	public static boolean cherubimSound = true;

	// ------------------------------------------------------------------ Cog Sentinel

	public static boolean showCogTitle = true;
	public static String cogTitleText = "{done}/{total} COG";
	public static String cogFinalTitleText = "COG ACTIVE";
	public static String cogSubtitle = "";
	public static int cogColour = 1;
	public static int cogFinalColour = 4;
	public static boolean cogSound = true;

	// ------------------------------------------------------------------ Nature's Gift

	public static boolean showGiftHud = true;
	public static String giftLabel = "ngift";
	public static String giftReadyText = "ready";
	public static String giftNameFilter = "";
	public static int giftTimeFormat = 0;
	public static boolean giftAlwaysShow = false;

	public static int giftAnchor = 0;
	public static int giftOffsetX = 4;
	public static int giftOffsetY = 4;
	public static int giftScale = 100;

	public static int giftFallbackCooldownSeconds = 360;

	public static boolean showGiftProcTitle = true;
	public static String giftProcTitleText = "NATURE'S GIFT";
	public static String giftProcSubtitle = "{time}";
	public static int giftProcColour = 4;
	public static boolean giftProcSound = true;

	// ------------------------------------------------------------------ Afterburner

	public static boolean showBurnHud = true;
	public static String burnLabel = "burn";
	public static String burnReadyText = "ready";

	/**
	 * Matched against the ability item's model id first -- {@code telos:material/ability/cloak/ut-fire}
	 * is Afterburner -- and against its name second, so "afterburner" works as well. Empty means any
	 * ability item at all.
	 */
	public static String burnItemFilter = "ability/cloak/ut-fire";

	/** How long after the cast the fire lands. Timed in game rather than taken from any wiki. */
	public static int burnDelayTenths = 70;

	public static boolean burnAlwaysShow = false;

	public static int burnAnchor = 0;
	public static int burnOffsetX = 4;
	public static int burnOffsetY = 28;
	public static int burnScale = 100;

	public static boolean showBurnFireTitle = true;
	public static String burnFireTitleText = "FIRE";
	public static String burnFireSubtitle = "";
	public static int burnFireColour = 2;
	public static boolean burnFireSound = true;

	// ------------------------------------------------------------------ Player stats

	public static boolean showStatsHud = true;

	/**
	 * Which readings to show and in what order, by the names {@code /aletheia stats} prints:
	 * {@code attack, defense} -- or {@code attack=ATK} to put something shorter on screen. Empty shows
	 * everything the player list is carrying.
	 */
	public static String statsPicked = "attack=ATK, defense=DEF, speed=SPD, vitality=VIT";

	/**
	 * Whether to keep the bracketed part of a reading: {@code +60.6 (48.8%)} rather than {@code +60.6}.
	 * Telos writes a stat twice, as points and as what they come to, and only one of those fits.
	 */
	public static boolean statsFullValue = false;

	public static boolean statsOneLine = false;
	public static String statsSeparator = "  ";
	public static int statsColour = 0;

	public static int statsAnchor = 1;
	public static int statsOffsetX = 4;
	public static int statsOffsetY = 4;
	public static int statsScale = 100;

	// ------------------------------------------------------------------ Boss phases

	public static boolean showPhaseTitles = true;
	public static int phaseWarningLead = 3;
	public static int phaseColour = 3;
	public static int phaseStayTicks = 40;
	public static boolean phaseSound = true;

	// ------------------------------------------------------------------ Ambush and deathmark

	/**
	 * The party's own calls off the boss's bar -- see {@link dev.landofif.aletheia.boss.BossReminders}.
	 *
	 * <p>Unlike the phase warnings above, the percentages here are settings rather than a table: they
	 * are what a group has agreed to use, not something the fight does. Emptying a wording turns that
	 * one call off and leaves the other.
	 */
	public static boolean showReminderTitles = true;
	public static int ambushAt = 65;
	public static String ambushText = "AMBUSH";
	public static int deathmarkAt = 40;
	public static String deathmarkText = "DEATHMARK";
	public static int reminderColour = 2;
	public static int reminderStayTicks = 40;
	public static boolean reminderSound = true;

	public static boolean showPhaseHud = true;
	public static int phaseHudColour = 0;
	public static int phaseAnchor = 0;
	public static int phaseOffsetX = 4;
	public static int phaseOffsetY = 28;
	public static int phaseScale = 100;

	// ------------------------------------------------------------------ Invulnerability

	public static boolean showInvulnerableHud = true;
	public static int invulnerableMinTier = 2;
	public static String invulnerableDimensionFilter = "";
	public static String invulnerableFillColour = "#1757A7";
	public static String halfFillColour = "";
	public static boolean invulnerableLooseMatch = true;

	/**
	 * How many ticks apart the fill over the boss's head is read, 1 being every tick.
	 *
	 * <p>Reading it walks the bar's text and looks its glyph up in the resource pack, which is work the
	 * client does not need to do twenty times a second: a boss going untouchable is a thing that lasts
	 * seconds, so every other tick sees the change a twentieth of a second later and does half the
	 * work. Turn it up on a machine that needs the frames, down to 1 if you would rather it were exact.
	 */
	public static int invulnerableCheckTicks = 2;
	public static String invulnerableText = "INVULNERABLE";
	public static String halfText = "HALF DAMAGE";
	public static String vulnerableText = "ATTACK";
	public static int vulnerableSeconds = 3;
	public static int invulnerableHudColour = 3;
	public static int halfHudColour = 6;
	public static int vulnerableHudColour = 4;
	public static boolean invulnerableSound = false;
	public static int invulnerableRange = 64;
	public static String barFillTexture = "xikage/default/inner";
	public static String bossBarTextureFilter = "glyph/bossbar";

	public static int invulnerableAnchor = 0;
	public static int invulnerableOffsetX = 4;
	public static int invulnerableOffsetY = 40;
	public static int invulnerableScale = 100;

	// ------------------------------------------------------------------ Boss bar

	public static boolean showBossBar = true;
	public static boolean hideVanillaBossBar = true;

	/**
	 * What the readout is: the bar and its labels, the labels on their own, or the percentage alone.
	 *
	 * <p>The two wordless settings draw no bar at all, so the shape, size and colour settings below
	 * stop applying and the readout is sized by what it says, like every other line the mod draws.
	 */
	public static int bossBarStyle = 0;

	public static int bossBarWidth = 182;
	public static int bossBarHeight = 10;
	public static int bossBarGap = 3;
	public static boolean bossBarTrueHealth = true;
	public static int bossBarEdges = 1;
	public static int bossBarCorner = 3;
	public static boolean bossBarBorder = true;
	public static int bossBarBackingAlpha = 70;
	public static boolean bossBarCentred = true;

	public static boolean bossBarColourByState = true;
	public static String bossBarVulnerableColour = "#5AD022";
	public static String bossBarHalfColour = "#B084E8";
	public static String bossBarInvulnerableColour = "#1757A7";

	public static boolean bossBarShowName = true;
	public static boolean bossBarShowPercent = true;
	public static boolean bossBarShowState = true;
	public static int bossBarTextPlace = 0;
	public static boolean bossBarTextByState = false;
	public static int bossBarTextColour = 0;

	public static int bossBarAnchor = 0;
	public static int bossBarOffsetX = 4;
	public static int bossBarOffsetY = 12;
	public static int bossBarScale = 100;

	// ------------------------------------------------------------------ HP pots

	public static boolean showPotsHud = true;
	public static String potsLabel = "Pots";
	public static int potsMax = 5;
	public static int potsColour = 0;
	public static boolean potsWarnWhenLow = true;
	public static boolean potsAlwaysShow = false;

	public static int potsAnchor = 0;
	public static int potsOffsetX = 4;
	public static int potsOffsetY = 16;
	public static int potsScale = 100;

	public static String potsFontFilter = "hppot";

	/**
	 * How many ticks apart the potion counter is read, 1 being every tick.
	 *
	 * <p>Finding it means walking every channel the server has text on -- every boss bar, every row of
	 * the player list, the whole sidebar -- to pick out the one run drawn in the font above. The number
	 * changes when you drink, so five times a second is already far more often than it can matter.
	 */
	public static int potsCheckTicks = 4;

	// ------------------------------------------------------------------ Plumbing

	/** Indexed by the colour settings above; {@link #colourNames()} labels the same list. */
	private static final ChatFormatting[] COLOURS = {
			ChatFormatting.WHITE,
			ChatFormatting.YELLOW,
			ChatFormatting.GOLD,
			ChatFormatting.RED,
			ChatFormatting.GREEN,
			ChatFormatting.AQUA,
			ChatFormatting.LIGHT_PURPLE,
	};

	public static ChatFormatting colour(int index) {
		return index >= 0 && index < COLOURS.length ? COLOURS[index] : ChatFormatting.WHITE;
	}

	/** The same colour as opaque ARGB, for the HUD readouts -- those take a packed int, not a style. */
	public static int argb(int index) {
		Integer rgb = colour(index).getColor();
		return 0xFF000000 | (rgb == null ? 0xFFFFFF : rgb);
	}

	/** The dropdown labels, derived from {@link #COLOURS} so the two cannot drift apart. */
	static String[] colourNames() {
		return Arrays.stream(COLOURS).map(AletheiaConfig::readable).toArray(String[]::new);
	}

	/** {@code LIGHT_PURPLE} reads as "Light Purple". */
	private static String readable(ChatFormatting formatting) {
		String[] words = formatting.name().toLowerCase().split("_");
		return String.join(" ", Arrays.stream(words)
				.map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
				.toArray(String[]::new));
	}

	public static void load() {
		ConfigFile.load();
	}

	public static void save() {
		ConfigFile.save();
	}
}
