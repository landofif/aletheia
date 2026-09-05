package dev.landofif.aletheia.config;

import dev.isxander.yacl3.api.ButtonOption;
import dev.isxander.yacl3.api.ConfigCategory;
import dev.isxander.yacl3.api.LabelOption;
import dev.isxander.yacl3.api.Option;
import dev.isxander.yacl3.api.OptionDescription;
import dev.isxander.yacl3.api.OptionGroup;
import dev.isxander.yacl3.api.YetAnotherConfigLib;
import dev.isxander.yacl3.api.controller.CyclingListControllerBuilder;
import dev.isxander.yacl3.api.controller.IntegerSliderControllerBuilder;
import dev.isxander.yacl3.api.controller.StringControllerBuilder;
import dev.isxander.yacl3.api.controller.TickBoxControllerBuilder;
import dev.landofif.aletheia.Aletheia;
import dev.landofif.aletheia.afterburner.AfterburnerHud;
import dev.landofif.aletheia.boss.BossBarHud;
import dev.landofif.aletheia.boss.BossPhaseHud;
import dev.landofif.aletheia.boss.VulnerabilityHud;
import dev.landofif.aletheia.detect.CooldownText;
import dev.landofif.aletheia.gift.NaturesGiftHud;
import dev.landofif.aletheia.hud.AletheiaHud;
import dev.landofif.aletheia.hud.HudEditorScreen;
import dev.landofif.aletheia.pots.HpPotsHud;
import dev.landofif.aletheia.stats.PlayerStatsHud;
import dev.landofif.aletheia.timer.DungeonSplitsHud;
import dev.landofif.aletheia.timer.DungeonTimerHud;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * The settings screen, built with YACL.
 *
 * <p>Every option binds straight to its field on {@link AletheiaConfig}, so the screen edits the
 * same values the rest of the mod reads. YACL holds changes until Save is pressed, and saving runs
 * {@link AletheiaConfig#save()}.
 *
 * <p>Every option also carries a description. There are close to two hundred settings here and most
 * of them name something the server does rather than something Minecraft does, so a name on its own
 * ("Only for mobs this good", "The bar's fill picture contains") tells you nothing unless you
 * already knew. The description panel is where that knowledge goes; the shorthands below take it as
 * a required argument so a new option cannot quietly ship without one.
 */
public final class ConfigScreen {

	private static final String[] COLOURS = AletheiaConfig.colourNames();
	private static final String[] ANCHORS = {"Top left", "Top right", "Bottom left", "Bottom right"};
	private static final String[] SOUNDS = {"Note Block Pling", "Note Block Bell", "Player Level Up",
			"Anvil Land", "Totem Use", "Ender Dragon Growl"};
	private static final String[] TIME_FORMATS = {"5:23", "323s"};
	private static final String[] TIERS = {"Any mob", "Miniboss or better", "Boss only"};
	private static final String[] EDGES = {"Square", "Rounded", "Bevelled", "Slanted", "Notched"};
	private static final String[] TEXT_PLACES = {"Above the bar", "On the bar", "Below the bar"};
	private static final String[] BAR_STYLES = {"Bar and words", "Words only", "Percentage only"};

	/** Every title option repeats this, so it is written once. */
	private static final String TITLE_TIMING =
			"How long it stays and how it fades is set for all titles at once, under Titles.";

	private ConfigScreen() {
	}

	public static Screen create(Screen parent) {
		return YetAnotherConfigLib.createBuilder()
				.title(Component.literal(Aletheia.MOD_NAME))
				.category(startHere())
				.category(general())
				.category(chat())
				.category(actionBar())
				.category(serverHud())
				.category(world())
				.category(titles())
				.category(neoEden())
				.category(dreadwood())
				.category(shadowlands())
				.category(bossFight())
				.category(bossPhases())
				.category(bossBar())
				.category(naturesGift())
				.category(afterburner())
				.category(playerStats())
				.category(hpPots())
				.category(dungeonTimer())
				.category(dungeonSplits())
				.save(AletheiaConfig::save)
				.build()
				.generateScreen(parent);
	}

	// ------------------------------------------------------------------ Categories

	/**
	 * The landing tab.
	 *
	 * <p>Deliberately short. It answers the three questions somebody has thirty seconds after
	 * installing -- what is this, does it need anything, where did my readouts go -- and sends them at
	 * the editor, which is the one screen that explains the rest of the mod by showing it.
	 */
	private static ConfigCategory startHere() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Start here"))
				.tooltip(Component.literal("What this does, and the two things worth setting first."))
				.option(LabelOption.create(Component.literal(
						"Aletheia surfaces what Telos Realms buries.")
						.withStyle(ChatFormatting.WHITE)))
				.option(LabelOption.create(Component.literal(
						"Dungeon chat becomes titles you cannot miss, and the numbers the server hides in its "
								+ "own HUD become readouts you can put where you want them. It works out of the "
								+ "box -- the tabs down the left are one feature each, and none of them needs "
								+ "touching.")
						.withStyle(ChatFormatting.GRAY)))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("The switch"))
						.option(tick("Enable mod",
								"Everything off at once, without uninstalling. Every readout, title and rewrite "
										+ "below is held behind this.",
								true,
								() -> AletheiaConfig.enabled, value -> AletheiaConfig.enabled = value))
						.option(tick("Only on Telos Realms",
								"Worth turning on if you play elsewhere too.\n\n"
										+ "Off, the mod watches chat on every server. It is matching phrases specific "
										+ "to Telos, so it will almost never fire anywhere else -- but almost is not "
										+ "never, and another server saying \"has been defeated. (4/10)\" would put a "
										+ "title on your screen.\n\n"
										+ "On, it only wakes up on servers whose address contains the filter below.",
								false,
								() -> AletheiaConfig.restrictToServer,
								value -> AletheiaConfig.restrictToServer = value))
						.option(text("Server address filter",
								"Matched against the address you connected to, ignoring case. \"telos\" catches "
										+ "telosrealms.com and any of its subdomains.\n\n"
										+ "Only used when \"Only on Telos Realms\" is on.",
								"telos",
								() -> AletheiaConfig.serverAddressFilter,
								value -> AletheiaConfig.serverAddressFilter = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Putting the readouts where you want them"))
						.option(editorButton())
						.option(LabelOption.create(Component.literal(
								"Also on /aletheia hud, and on a key of your choosing under "
										+ "Options → Controls → Aletheia.")
								.withStyle(ChatFormatting.DARK_GRAY)))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Commands"))
						.option(LabelOption.create(Component.literal(
								"/aletheia opens this screen. /telos, /ale and /a do the same.")
								.withStyle(ChatFormatting.GRAY)))
						.option(LabelOption.create(Component.literal(
								"/a hud places the readouts · /a status says what it can see right now · "
										+ "/a test <thing> fires a title so you can aim it without waiting for a dungeon")
								.withStyle(ChatFormatting.GRAY)))
						.build())
				.build();
	}

	private static ConfigCategory general() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("General"))
				.tooltip(Component.literal("Where the mod looks, and what it ignores."))
				// The master switch and the server filter live on Start here and only there. Binding a
				// field to two options would let the screen hold two pending values for it and write
				// whichever happened to be saved last.
				.option(LabelOption.create(Component.literal(
						"The main switch and \"only on Telos\" are on Start here.")
						.withStyle(ChatFormatting.DARK_GRAY)))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Message sources"))
						.option(tick("Also scan player chat",
								"Off, only the server's own messages are read.\n\n"
										+ "On, what other players type is read too -- which means anyone can put a title "
										+ "on your screen by typing the right words. Useful for testing, a bad idea in "
										+ "public.",
								false,
								() -> AletheiaConfig.scanPlayerChat,
								value -> AletheiaConfig.scanPlayerChat = value))
						.option(tick("Also scan the action bar",
								"Read the line above your hotbar as well as chat. Some dungeons announce there "
										+ "instead; /aletheia actionbar prints what has been arriving, so you can see "
										+ "whether this would help before turning it on.",
								false,
								() -> AletheiaConfig.scanActionBar,
								value -> AletheiaConfig.scanActionBar = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Matching"))
						.option(tick("Strict score matching",
								"The score title normally fires on any line ending in a (current/total) counter, "
										+ "so wordings nobody has written down still work.\n\n"
										+ "On, only the exact phrasings the mod knows count. Turn this on if something "
										+ "on the server keeps setting the score title off wrongly.",
								false,
								() -> AletheiaConfig.strictProgressMatching,
								value -> AletheiaConfig.strictProgressMatching = value))
						.option(text("Ignore lines containing",
								"A comma-separated list. Any chat line containing one of these is dropped before "
										+ "anything else looks at it.\n\n"
										+ "The gentler fix when one message keeps triggering a title: add a word only it "
										+ "uses, rather than turning strict matching on for everything.",
								"",
								() -> AletheiaConfig.extraIgnoredPhrases,
								value -> AletheiaConfig.extraIgnoredPhrases = value))
						.option(tick("Quieten the passenger warning",
								"Telos rides players on invisible entities, and every time it does, vanilla writes "
										+ "\"Trying to add entity as passenger\" to your log. Harmless, constant, and "
										+ "it buries everything else in there.\n\n"
										+ "On, that one line is dropped. Nothing else about the log changes.",
								true,
								() -> AletheiaConfig.quietPassengerWarnings,
								value -> AletheiaConfig.quietPassengerWarnings = value))
						.option(tick("Log matches to the console",
								"Writes every line the mod matched, and what it made of it, to the game log.\n\n"
										+ "For working out why a title did or did not fire. Noisy -- leave it off "
										+ "otherwise.",
								false,
								() -> AletheiaConfig.debugLogging,
								value -> AletheiaConfig.debugLogging = value))
						.build())
				.build();
	}

	private static ConfigCategory chat() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Chat"))
				.tooltip(Component.literal("Cutting the server's wordier messages down to size."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Shortening"))
						.option(tick("Shorten the player toggle messages",
								"Telos writes a whole sentence every time you hide or show other players, which on "
										+ "a busy hub is most of what you see.\n\n"
										+ "On, both are replaced with the short forms below.",
								true,
								() -> AletheiaConfig.shortenPlayerVisibility,
								value -> AletheiaConfig.shortenPlayerVisibility = value))
						.option(text("When players come back",
								"What to print instead. Blank swallows the message entirely.",
								"Players: [shown]",
								() -> AletheiaConfig.playersShownText,
								value -> AletheiaConfig.playersShownText = value))
						.option(text("When players are hidden",
								"What to print instead. Blank swallows the message entirely.",
								"Players: [hidden]",
								() -> AletheiaConfig.playersHiddenText,
								value -> AletheiaConfig.playersHiddenText = value))
						.build())
				.build();
	}

	private static ConfigCategory actionBar() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Action bar"))
				.tooltip(Component.literal("Hiding what the server writes above your hotbar."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Hiding"))
						.option(tick("Hide the server's action bar",
								"The server keeps the line above your hotbar busy. Once its numbers are readouts "
										+ "you have placed yourself, the original is in the way.\n\n"
										+ "On with an empty filter hides all of it.",
								false,
								() -> AletheiaConfig.hideActionBar,
								value -> AletheiaConfig.hideActionBar = value))
						.option(text("Only hide lines containing",
								"A comma-separated list. Leave blank to hide every action bar message; fill it in "
										+ "to hide only the ones that match and let the rest through.\n\n"
										+ "/aletheia actionbar prints the last messages that arrived, so you can copy a "
										+ "phrase out of one rather than guess at it.",
								"",
								() -> AletheiaConfig.actionBarHideFilter,
								value -> AletheiaConfig.actionBarHideFilter = value))
						.build())
				.build();
	}

	private static ConfigCategory serverHud() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Server HUD"))
				.tooltip(Component.literal("Hiding parts of the HUD the server draws with its own font."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Hiding"))
						.option(tick("Hide parts of the server's HUD",
								"Telos draws its HUD as characters in a custom font rather than as a picture, which "
										+ "is what lets the mod read it -- and also what lets it drop pieces of it.\n\n"
										+ "On with an empty filter hides nothing; name the parts below.",
								false,
								() -> AletheiaConfig.hideServerHud,
								value -> AletheiaConfig.hideServerHud = value))
						.option(text("Parts to hide",
								"A comma-separated list, matched against the name of the font each piece is drawn "
										+ "in -- \"hppot\", \"mana\", and so on.\n\n"
										+ "/aletheia hudscan prints the fonts currently on screen, so this can be read "
										+ "off rather than guessed at.",
								"",
								() -> AletheiaConfig.serverHudHideFilter,
								value -> AletheiaConfig.serverHudHideFilter = value))
						.build())
				.build();
	}

	private static ConfigCategory world() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("World"))
				.tooltip(Component.literal("Leaving things in the world undrawn."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Hidden props"))
						.option(tick("Hide props standing in the world",
								"Some of the decorations the server places -- the Arcanist monolith above all -- are "
										+ "large, solid and parked where you need to see.\n\n"
										+ "This stops them being drawn for you. They are still there, and everyone else "
										+ "still sees them.",
								false,
								() -> AletheiaConfig.hideProps,
								value -> AletheiaConfig.hideProps = value))
						.option(text("Types or models to hide",
								"A comma-separated list, matched against an entity's type and against the model an "
										+ "item or block display is holding.\n\n"
										+ "Trimming a phrase catches more: \"arcanist_orb\" takes every tier of the orb, "
										+ "where \"arcanist_orb_n1a\" takes the one.\n\n"
										+ "/aletheia props prints what is around you and the exact strings being matched. "
										+ "An empty filter hides nothing.",
								"arcanist_orb_n1a",
								() -> AletheiaConfig.propHideFilter,
								value -> AletheiaConfig.propHideFilter = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Magnificat domain"))
						.option(LabelOption.create(Component.literal(
								"Magnificat puts a circle of fire on the floor, and the only thing that matters "
										+ "about it is whether you are inside it. This draws that answer as a ring.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Draw the ring",
								"A plain circle on the ground where the fire is, coloured by which side of it you "
										+ "are standing on.\n\n"
										+ "It is drawn from the client, so it stays readable at any camera angle and "
										+ "through anything standing on top of the real one.",
								true,
								() -> AletheiaConfig.showDomainRing,
								value -> AletheiaConfig.showDomainRing = value))
						.option(tick("Hide the server's fire circle",
								"Leaves the circle itself undrawn, so only the ring is left.\n\n"
										+ "Turn this off with the ring on to see the two together, which is how you "
										+ "check they line up.",
								true,
								() -> AletheiaConfig.hideDomainCircle,
								value -> AletheiaConfig.hideDomainCircle = value))
						.option(text("Circles to replace",
								"A comma-separated list, matched against the item model an item display is holding. "
										+ "The default is Magnificat's own circle.\n\n"
										+ "/aletheia domain prints what it has found and the exact strings being "
										+ "matched. An empty filter switches the whole thing off.",
								"magic_fire_circle2/out",
								() -> AletheiaConfig.domainFilter,
								value -> AletheiaConfig.domainFilter = value))
						.option(slider("Ring radius",
								"How far out the ring is drawn.\n\n"
										+ "0 measures it from the circle itself, which is what you want: the size comes "
										+ "off the display the server spawned, so the ring is whatever size the fire "
										+ "actually is and grows with it as it comes in. Set a number here only if that "
										+ "measurement ever goes wrong -- /aletheia domain prints both.",
								0, 0, 64, 1, " blocks",
								() -> AletheiaConfig.domainRadius,
								value -> AletheiaConfig.domainRadius = value))
						.option(text("Colour inside",
								"The ring's colour while you are standing in the domain, as #RRGGBB or a colour "
										+ "name. Unreadable falls back to green.",
								"#5AD022",
								() -> AletheiaConfig.domainInsideColour,
								value -> AletheiaConfig.domainInsideColour = value))
						.option(text("Colour outside",
								"The ring's colour while you are outside it. Unreadable falls back to red.",
								"#E03C31",
								() -> AletheiaConfig.domainOutsideColour,
								value -> AletheiaConfig.domainOutsideColour = value))
						.option(slider("Ring solidity",
								"How solid the ring is drawn. Low enough to see the fight through it, high enough "
										+ "to pick out across the arena.",
								60, 5, 100, 5, "%",
								() -> AletheiaConfig.domainRingAlpha,
								value -> AletheiaConfig.domainRingAlpha = value))
						.option(slider("Ring height",
								"How tall the ring stands, in tenths of a block.\n\n"
										+ "0 lays it flat, which vanishes as the camera comes level with the floor. A "
										+ "low wall stays visible from any angle.",
								2, 0, 20, 1, " tenths",
								() -> AletheiaConfig.domainRingHeight,
								value -> AletheiaConfig.domainRingHeight = value))
						.build())
				.build();
	}

	private static ConfigCategory titles() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Titles"))
				.tooltip(Component.literal("Timing, look and sound for every title the mod draws."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Timing"))
						.option(LabelOption.create(Component.literal(
								"Applies to every title below, in ticks -- 20 ticks is one second.")
								.withStyle(ChatFormatting.GRAY)))
						.option(slider("Fade in",
								"How long a title takes to appear. 0 puts it up instantly, which is what you want "
										+ "for anything you have to react to.",
								2, 0, 20, 1, " ticks",
								() -> AletheiaConfig.titleFadeInTicks,
								value -> AletheiaConfig.titleFadeInTicks = value))
						.option(slider("Stay",
								"How long it holds at full strength before fading. 30 ticks is a second and a half.",
								30, 5, 120, 1, " ticks",
								() -> AletheiaConfig.titleStayTicks,
								value -> AletheiaConfig.titleStayTicks = value))
						.option(slider("Fade out",
								"How long it takes to disappear.",
								8, 0, 40, 1, " ticks",
								() -> AletheiaConfig.titleFadeOutTicks,
								value -> AletheiaConfig.titleFadeOutTicks = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Look"))
						.option(tick("Drop shadow behind text",
								"Vanilla puts a shadow behind every letter of a title. Off here by default: these "
										+ "are short words in strong colours, and the shadow only muddies them.\n\n"
										+ "This one reaches further than the rest of the mod: it covers the readouts, and "
										+ "every title on screen whether or not the mod put it there.",
								false,
								() -> AletheiaConfig.titleShadow,
								value -> AletheiaConfig.titleShadow = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Sound"))
						.option(choice("Alert sound",
								"The sound every \"play sound\" switch below plays. One sound for all of them, so "
										+ "you learn it once.",
								0, SOUNDS,
								() -> AletheiaConfig.alertSound,
								value -> AletheiaConfig.alertSound = value))
						.option(slider("Alert volume",
								"How loud, as a share of your Master volume. 0 is silent.",
								100, 0, 100, 5, "%",
								() -> AletheiaConfig.alertVolume,
								value -> AletheiaConfig.alertVolume = value))
						.build())
				.build();
	}

	private static ConfigCategory neoEden() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Neo-Eden"))
				.tooltip(Component.literal("Score, the light puzzle and the barracks."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Score progress"))
						.option(LabelOption.create(Component.literal(
								"\"Neo-Eden acknowledges your persistence. (175/UNDEFINED)\"  →  175/???")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show score title",
								"Turn the run's score into a title.\n\n"
										+ "It is driven by the trailing (current/total) counter rather than by any one "
										+ "sentence, so phrasings the mod has never seen still work.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showProgressTitle,
								value -> AletheiaConfig.showProgressTitle = value))
						.option(text("Unknown total placeholder",
								"The dungeon sometimes reports the requirement as UNDEFINED. This stands in for it, "
										+ "so the title reads 175/??? rather than 175/UNDEFINED.",
								"???",
								() -> AletheiaConfig.unknownTotalPlaceholder,
								value -> AletheiaConfig.unknownTotalPlaceholder = value))
						.option(text("Score subtitle",
								"The smaller line under the number. Blank for none.",
								"SCORE",
								() -> AletheiaConfig.progressSubtitle,
								value -> AletheiaConfig.progressSubtitle = value))
						.option(colour("Score colour",
								"What colour the number is drawn in.",
								2,
								() -> AletheiaConfig.progressColour,
								value -> AletheiaConfig.progressColour = value))
						.option(tick("Play sound on score",
								"Off by default -- score updates arrive constantly, and a sound on each one is a lot.",
								false,
								() -> AletheiaConfig.progressSound,
								value -> AletheiaConfig.progressSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Light puzzle"))
						.option(LabelOption.create(Component.literal(
								"Announced twice: once when somebody walks in, once when it is solved.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show puzzle start title",
								"A title when somebody starts the edenic light puzzle.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showPuzzleStartTitle,
								value -> AletheiaConfig.showPuzzleStartTitle = value))
						.option(text("Puzzle start title text",
								"The big line.",
								"PUZZLE STARTED",
								() -> AletheiaConfig.puzzleStartTitleText,
								value -> AletheiaConfig.puzzleStartTitleText = value))
						.option(tick("Name who started it",
								"Puts the player's name in the subtitle, so a group can see who is where.",
								true,
								() -> AletheiaConfig.puzzleStartShowPlayer,
								value -> AletheiaConfig.puzzleStartShowPlayer = value))
						.option(colour("Puzzle start colour",
								"Yellow by default. The finish titles are green, so the two read apart at a glance.",
								1,
								() -> AletheiaConfig.puzzleStartColour,
								value -> AletheiaConfig.puzzleStartColour = value))
						.option(tick("Play sound on puzzle start",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.puzzleStartSound,
								value -> AletheiaConfig.puzzleStartSound = value))
						.option(tick("Show puzzle title",
								"A title when the puzzle is solved.",
								true,
								() -> AletheiaConfig.showPuzzleTitle,
								value -> AletheiaConfig.showPuzzleTitle = value))
						.option(text("Puzzle title text",
								"The big line.",
								"PUZZLE SOLVED",
								() -> AletheiaConfig.puzzleTitleText,
								value -> AletheiaConfig.puzzleTitleText = value))
						.option(tick("Name who solved it",
								"Puts the player's name in the subtitle.",
								true,
								() -> AletheiaConfig.puzzleShowPlayer,
								value -> AletheiaConfig.puzzleShowPlayer = value))
						.option(colour("Puzzle colour",
								"Green by default, matching the other \"done\" titles.",
								4,
								() -> AletheiaConfig.puzzleColour,
								value -> AletheiaConfig.puzzleColour = value))
						.option(tick("Play sound on puzzle",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.puzzleSound,
								value -> AletheiaConfig.puzzleSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Barracks"))
						.option(LabelOption.create(Component.literal(
								"The edenic barracks battle room, announced on the way in and on the way out.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show barracks start title",
								"A title when somebody starts the barracks.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showBarracksStartTitle,
								value -> AletheiaConfig.showBarracksStartTitle = value))
						.option(text("Barracks start title text",
								"The big line.",
								"BARRACKS STARTED",
								() -> AletheiaConfig.barracksStartTitleText,
								value -> AletheiaConfig.barracksStartTitleText = value))
						.option(tick("Name who started it",
								"Puts the player's name in the subtitle.",
								true,
								() -> AletheiaConfig.barracksStartShowPlayer,
								value -> AletheiaConfig.barracksStartShowPlayer = value))
						.option(colour("Barracks start colour",
								"Yellow by default, like the other start titles.",
								1,
								() -> AletheiaConfig.barracksStartColour,
								value -> AletheiaConfig.barracksStartColour = value))
						.option(tick("Play sound on barracks start",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.barracksStartSound,
								value -> AletheiaConfig.barracksStartSound = value))
						.option(tick("Show barracks title",
								"A title when the barracks is cleared.",
								true,
								() -> AletheiaConfig.showBarracksTitle,
								value -> AletheiaConfig.showBarracksTitle = value))
						.option(text("Barracks title text",
								"The big line.",
								"BARRACKS DONE",
								() -> AletheiaConfig.barracksTitleText,
								value -> AletheiaConfig.barracksTitleText = value))
						.option(tick("Name who cleared it",
								"Puts the player's name in the subtitle.",
								true,
								() -> AletheiaConfig.barracksShowPlayer,
								value -> AletheiaConfig.barracksShowPlayer = value))
						.option(colour("Barracks colour",
								"Green by default, matching the other \"done\" titles.",
								4,
								() -> AletheiaConfig.barracksColour,
								value -> AletheiaConfig.barracksColour = value))
						.option(tick("Play sound on barracks",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.barracksSound,
								value -> AletheiaConfig.barracksSound = value))
						.build())
				.build();
	}

	private static ConfigCategory dreadwood() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Dreadwood"))
				.tooltip(Component.literal("Dreadwood Thicket: room titles and how far through a run you are."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Entering a room"))
						.option(LabelOption.create(Component.literal(
								"The room's name is captured, not listed -- one nobody has written down yet still "
										+ "announces itself.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show room start title",
								"A title when somebody starts a Thicket room -- Colour, Wave, Demon, Bullet Hell "
										+ "and anything else the dungeon names.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showDreadwoodStartTitle,
								value -> AletheiaConfig.showDreadwoodStartTitle = value))
						.option(text("Room start title text",
								"The big line. {room} is the room's name in capitals, {player} who it was, and "
										+ "{done}/{total} how far through the run you are.",
								"{room} ROOM",
								() -> AletheiaConfig.dreadwoodStartTitleText,
								value -> AletheiaConfig.dreadwoodStartTitleText = value))
						.option(text("Room start subtitle",
								"The smaller line. Same {room}, {player}, {done} and {total} placeholders. Blank "
										+ "for none.",
								"{player}",
								() -> AletheiaConfig.dreadwoodStartSubtitle,
								value -> AletheiaConfig.dreadwoodStartSubtitle = value))
						.option(colour("Room start colour",
								"Yellow by default, like the other start titles.",
								1,
								() -> AletheiaConfig.dreadwoodStartColour,
								value -> AletheiaConfig.dreadwoodStartColour = value))
						.option(tick("Play sound on room start",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.dreadwoodStartSound,
								value -> AletheiaConfig.dreadwoodStartSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Finishing a room"))
						.option(tick("Show room cleared title",
								"A title when a room is finished. By default it is the run counter -- 1/2, then "
										+ "2/2 -- with the room's name underneath, so a run can be followed without "
										+ "reading chat.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showDreadwoodClearedTitle,
								value -> AletheiaConfig.showDreadwoodClearedTitle = value))
						.option(text("Room cleared title text",
								"The big line. {done}/{total} is the run counter, {room} the room's name in "
										+ "capitals, {player} who finished it.",
								"{done}/{total}",
								() -> AletheiaConfig.dreadwoodClearedTitleText,
								value -> AletheiaConfig.dreadwoodClearedTitleText = value))
						.option(text("Room cleared subtitle",
								"The smaller line. Same placeholders. Blank for none.",
								"{room} ROOM DONE",
								() -> AletheiaConfig.dreadwoodClearedSubtitle,
								value -> AletheiaConfig.dreadwoodClearedSubtitle = value))
						.option(colour("Room cleared colour",
								"Green by default, matching the other \"done\" titles.",
								4,
								() -> AletheiaConfig.dreadwoodClearedColour,
								value -> AletheiaConfig.dreadwoodClearedColour = value))
						.option(tick("Play sound on room cleared",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.dreadwoodClearedSound,
								value -> AletheiaConfig.dreadwoodClearedSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("The count"))
						.option(LabelOption.create(Component.literal(
								"Nothing in the server's messages says which room of the run it is, so the count is "
										+ "kept by room name -- a room announced twice cannot advance it.")
								.withStyle(ChatFormatting.GRAY)))
						.option(slider("Rooms per run",
								"What {total} counts up to. Set this to however many rooms a run of the Thicket "
										+ "has for you.",
								2, 1, 8, 1, "",
								() -> AletheiaConfig.dreadwoodRoomsPerRun,
								value -> AletheiaConfig.dreadwoodRoomsPerRun = value))
						.option(text("Extra dimensions that count as the dungeon",
								"How the mod knows you are still inside.\n\n"
										+ "Each room is its own dimension, so the count must not reset when you move "
										+ "between them -- a numbered room (telos:dungeon/2) counts as still inside, "
										+ "and anything else is the way out.\n\n"
										+ "Empty by default and normally left that way; this is for a dungeon shaped "
										+ "differently.",
								"",
								() -> AletheiaConfig.dreadwoodDimensionFilter,
								value -> AletheiaConfig.dreadwoodDimensionFilter = value))
						.option(slider("Forget the count after",
								"Drop the count after this long with no Thicket chat, so a run you walked away "
										+ "from does not become the first room of the next one.\n\n"
										+ "/aletheia dreadwood prints the count and why it was last cleared; "
										+ "/aletheia dreadwood reset clears it by hand.",
								15, 1, 60, 1, " min",
								() -> AletheiaConfig.dreadwoodForgetMinutes,
								value -> AletheiaConfig.dreadwoodForgetMinutes = value))
						.build())
				.build();
	}

	private static ConfigCategory shadowlands() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Shadowlands"))
				.tooltip(Component.literal("Calling out what has just spawned."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Spawns"))
						.option(LabelOption.create(Component.literal(
								"\"[Herald] The torch burns bright.\"  →  HERALD SPAWNED")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show spawn titles",
								"A title when something announces itself in the Shadowlands.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showShadowlandsTitle,
								value -> AletheiaConfig.showShadowlandsTitle = value))
						.option(text("Spawn title text",
								"The big line. {mob} is the name in capitals.",
								"{mob} SPAWNED",
								() -> AletheiaConfig.shadowlandsTitleText,
								value -> AletheiaConfig.shadowlandsTitleText = value))
						.option(text("Spawn subtitle",
								"The smaller line. Same {mob} placeholder. Blank for none.",
								"",
								() -> AletheiaConfig.shadowlandsSubtitle,
								value -> AletheiaConfig.shadowlandsSubtitle = value))
						.option(colour("Spawn colour",
								"What colour the title is drawn in.",
								6,
								() -> AletheiaConfig.shadowlandsColour,
								value -> AletheiaConfig.shadowlandsColour = value))
						.option(tick("Play sound on spawn",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.shadowlandsSound,
								value -> AletheiaConfig.shadowlandsSound = value))
						.option(slider("Ignore the same mob for",
								"A quiet period per mob name, for when the server announces the same spawn several "
										+ "times over. 0 announces every one.",
								0, 0, 120, 1, "s",
								() -> AletheiaConfig.shadowlandsRepeatSeconds,
								value -> AletheiaConfig.shadowlandsRepeatSeconds = value))
						.build())
				.build();
	}

	private static ConfigCategory bossFight() {
		return ConfigCategory.createBuilder()
				.name(Component.literal("Boss fight"))
				.tooltip(Component.literal("Cherubim's shouts and the Cog Sentinel's stabilisers."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Cherubim"))
						.option(LabelOption.create(Component.literal(
								"Cherubim punishes anyone still attacking after it shouts. These are the two lines "
										+ "you actually have to react to, so they get longer on screen and a sound by "
										+ "default.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show Cherubim warnings",
								"Turn \"Enough!\" and \"Silence!\" into titles.",
								true,
								() -> AletheiaConfig.showCherubimTitle,
								value -> AletheiaConfig.showCherubimTitle = value))
						.option(text("\"Enough\" title text",
								"The big line for the Enough shout.",
								"ENOUGH!",
								() -> AletheiaConfig.enoughTitleText,
								value -> AletheiaConfig.enoughTitleText = value))
						.option(text("\"Silence\" title text",
								"The big line for the Silence shout.",
								"SILENCE!",
								() -> AletheiaConfig.silenceTitleText,
								value -> AletheiaConfig.silenceTitleText = value))
						.option(text("Warning subtitle",
								"The smaller line under both. This is the one that says what to do about it.",
								"STOP ATTACKING",
								() -> AletheiaConfig.cherubimSubtitle,
								value -> AletheiaConfig.cherubimSubtitle = value))
						.option(colour("Warning colour",
								"Red by default.",
								3,
								() -> AletheiaConfig.cherubimColour,
								value -> AletheiaConfig.cherubimColour = value))
						.option(slider("Warning stay time",
								"How long these hold, overriding the shared Stay under Titles. Longer, because you "
										+ "need to stop attacking for as long as it is up. 20 ticks is a second.",
								40, 5, 120, 1, " ticks",
								() -> AletheiaConfig.cherubimStayTicks,
								value -> AletheiaConfig.cherubimStayTicks = value))
						.option(tick("Play sound on warning",
								"On by default. Plays the alert sound; Titles \u2192 Sound picks which one.",
								true,
								() -> AletheiaConfig.cherubimSound,
								value -> AletheiaConfig.cherubimSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Cog Sentinel"))
						.option(LabelOption.create(Component.literal(
								"\"(1/5) Cog Stabilisers destroyed\"  →  1/5 COG,  then COG ACTIVE on the last one.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Count the stabilisers",
								"Turn the stabiliser count into a title.\n\n" + TITLE_TIMING,
								true,
								() -> AletheiaConfig.showCogTitle,
								value -> AletheiaConfig.showCogTitle = value))
						.option(text("Stabiliser title text",
								"The big line for every stabiliser but the last. {done} and {total} are the count.",
								"{done}/{total} COG",
								() -> AletheiaConfig.cogTitleText,
								value -> AletheiaConfig.cogTitleText = value))
						.option(text("Last stabiliser title text",
								"The big line for the last one, which is the moment worth calling separately.",
								"COG ACTIVE",
								() -> AletheiaConfig.cogFinalTitleText,
								value -> AletheiaConfig.cogFinalTitleText = value))
						.option(text("Stabiliser subtitle",
								"The smaller line. Same {done} and {total} placeholders. Blank for none.",
								"",
								() -> AletheiaConfig.cogSubtitle,
								value -> AletheiaConfig.cogSubtitle = value))
						.option(colour("Stabiliser colour",
								"For the counting titles.",
								1,
								() -> AletheiaConfig.cogColour,
								value -> AletheiaConfig.cogColour = value))
						.option(colour("Last stabiliser colour",
								"For the last one, so it reads apart from the count.",
								4,
								() -> AletheiaConfig.cogFinalColour,
								value -> AletheiaConfig.cogFinalColour = value))
						.option(tick("Play sound on a stabiliser",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.cogSound,
								value -> AletheiaConfig.cogSound = value))
						.build())
				.build();
	}

	private static ConfigCategory bossPhases() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Boss phases"))
				.tooltip(Component.literal(
						"Phase changes called before they land, and whether the boss can be hurt right now."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Warnings"))
						.option(LabelOption.create(Component.literal(
								"Read off the boss's health bar, so this works on any boss whose phases are health "
										+ "thresholds -- no list of bosses to keep up to date.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Call the phase changes",
								"A title as the boss approaches a phase threshold, rather than as it crosses it.",
								true,
								() -> AletheiaConfig.showPhaseTitles,
								value -> AletheiaConfig.showPhaseTitles = value))
						.option(slider("Warn this early",
								"How far ahead of the threshold to call it, in percentage points of the boss's "
										+ "health. 3% is roughly one good hit's warning; 0 calls it as it happens.",
								3, 0, 15, 1, "%",
								() -> AletheiaConfig.phaseWarningLead,
								value -> AletheiaConfig.phaseWarningLead = value))
						.option(colour("Warning colour",
								"Red by default.",
								3,
								() -> AletheiaConfig.phaseColour,
								value -> AletheiaConfig.phaseColour = value))
						.option(slider("Warning stay time",
								"How long these hold, overriding the shared Stay under Titles.",
								40, 5, 120, 1, " ticks",
								() -> AletheiaConfig.phaseStayTicks,
								value -> AletheiaConfig.phaseStayTicks = value))
						.option(tick("Play sound on a phase change",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.phaseSound,
								value -> AletheiaConfig.phaseSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Ambush and deathmark"))
						.option(LabelOption.create(Component.literal(
								"The calls a party makes for itself, rather than anything the boss does -- so the "
										+ "percentages are yours to set. Only in the eleven dungeon bosses: Asmodeus, "
										+ "Seraphim, True Seraph, Valerion, Nebula, Ophanim, True Ophan, Cherubim, "
										+ "Sylvaris, Voided Omnipotent and Raphael.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Call ambush and deathmark",
								"A title when the boss's bar drops past each of the two percentages below.\n\n"
										+ "These fire once per fight, as the bar crosses -- there is no early lead the "
										+ "way the phase warnings have one, since the percentage is the whole of the "
										+ "instruction. Set it higher if you want more notice.\n\n"
										+ "Left alone in a realm boss: those are over before a reminder is worth "
										+ "reading. /aletheia test reminder shows the wording without a fight.",
								true,
								() -> AletheiaConfig.showReminderTitles,
								value -> AletheiaConfig.showReminderTitles = value))
						.option(slider("Call ambush at",
								"Where in the boss's health bar the first call is made.",
								65, 1, 100, 1, "%",
								() -> AletheiaConfig.ambushAt,
								value -> AletheiaConfig.ambushAt = value))
						.option(text("Ambush text",
								"What that call says. Empty turns this one off and leaves the other.",
								"AMBUSH",
								() -> AletheiaConfig.ambushText,
								value -> AletheiaConfig.ambushText = value))
						.option(slider("Call deathmark at",
								"Where in the bar the second call is made.",
								40, 1, 100, 1, "%",
								() -> AletheiaConfig.deathmarkAt,
								value -> AletheiaConfig.deathmarkAt = value))
						.option(text("Deathmark text",
								"What that one says. Empty turns it off.",
								"DEATHMARK",
								() -> AletheiaConfig.deathmarkText,
								value -> AletheiaConfig.deathmarkText = value))
						.option(colour("Call colour",
								"Gold by default, so these do not read as one of the red phase warnings.",
								2,
								() -> AletheiaConfig.reminderColour,
								value -> AletheiaConfig.reminderColour = value))
						.option(slider("Call stay time",
								"How long these hold, overriding the shared Stay under Titles.",
								40, 5, 120, 1, " ticks",
								() -> AletheiaConfig.reminderStayTicks,
								value -> AletheiaConfig.reminderStayTicks = value))
						.option(tick("Play sound on a call",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.reminderSound,
								value -> AletheiaConfig.reminderSound = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(tick("Show the phase readout",
								"A standing line saying which phase the fight is in, for when the title has faded "
										+ "and you need to check.",
								true,
								() -> AletheiaConfig.showPhaseHud,
								value -> AletheiaConfig.showPhaseHud = value))
						.option(colour("Readout colour",
								"What colour the line is drawn in.",
								0,
								() -> AletheiaConfig.phaseHudColour,
								value -> AletheiaConfig.phaseHudColour = value))
						.build());

		placement("the phase readout", BossPhaseHud.INSTANCE, 0, 4, 28).forEach(builder::group);

		builder.group(OptionGroup.createBuilder()
				.name(Component.literal("Invulnerability"))
				.option(LabelOption.create(Component.literal(
						"Telos recolours a boss's bar while it cannot be hurt. The mod watches that colour and "
								+ "says so in words, so you are not squinting at a bar mid-fight.")
						.withStyle(ChatFormatting.GRAY)))
				.option(tick("Show when the boss is invulnerable",
						"Turn the bar's colour into INVULNERABLE / HALF DAMAGE / ATTACK on your own HUD.",
						true,
						() -> AletheiaConfig.showInvulnerableHud,
						value -> AletheiaConfig.showInvulnerableHud = value))
				.option(choice("Which mobs to watch",
						"How important a mob has to be before its bar is watched. \"Boss only\" keeps trash mobs "
								+ "with recoloured bars from taking over the readout.",
						2, TIERS,
						() -> AletheiaConfig.invulnerableMinTier,
						value -> AletheiaConfig.invulnerableMinTier = value))
				.option(text("Only in dimensions containing",
						"Narrow this to the dungeons you care about, matched against the dimension id. Blank "
								+ "watches everywhere.",
						"",
						() -> AletheiaConfig.invulnerableDimensionFilter,
						value -> AletheiaConfig.invulnerableDimensionFilter = value))
				.option(text("Invulnerable when the bar fill is",
						"The colour Telos paints the bar while the boss cannot be hurt, as a hex code.\n\n"
								+ "This is the one setting to change if a fight reads wrongly -- the server picks these "
								+ "colours, and it can change them.",
						"#1757A7",
						() -> AletheiaConfig.invulnerableFillColour,
						value -> AletheiaConfig.invulnerableFillColour = value))
				.option(text("Half damage when the bar fill is",
						"The same, for fights with a state where the boss takes reduced damage. Blank if the "
								+ "fight has no such state.",
						"",
						() -> AletheiaConfig.halfFillColour,
						value -> AletheiaConfig.halfFillColour = value))
				.option(tick("Match the colour loosely",
						"On, a near-enough colour counts -- which survives the bar being drawn slightly darker "
								+ "or lighter than the hex code says.\n\n"
								+ "Off, only an exact match counts. Turn it off if two states of a fight use colours "
								+ "close enough to be confused.",
						true,
						() -> AletheiaConfig.invulnerableLooseMatch,
						value -> AletheiaConfig.invulnerableLooseMatch = value))
				.option(text("Invulnerable text",
						"What the readout says while the boss cannot be hurt.",
						"INVULNERABLE",
						() -> AletheiaConfig.invulnerableText,
						value -> AletheiaConfig.invulnerableText = value))
				.option(text("Half damage text",
						"What it says while the boss takes reduced damage.",
						"HALF DAMAGE",
						() -> AletheiaConfig.halfText,
						value -> AletheiaConfig.halfText = value))
				.option(text("Vulnerable again text",
						"What it says the moment the boss can be hurt again -- the cue to go back in.",
						"ATTACK",
						() -> AletheiaConfig.vulnerableText,
						value -> AletheiaConfig.vulnerableText = value))
				.option(slider("Hold \"vulnerable again\" for",
						"How long that cue stays up before the readout goes quiet. It is a prompt, not a state, "
								+ "so it does not need to stand there for the rest of the fight.",
						3, 1, 15, 1, "s",
						() -> AletheiaConfig.vulnerableSeconds,
						value -> AletheiaConfig.vulnerableSeconds = value))
				.option(colour("Invulnerable colour",
						"Red by default: stop.",
						3,
						() -> AletheiaConfig.invulnerableHudColour,
						value -> AletheiaConfig.invulnerableHudColour = value))
				.option(colour("Half damage colour",
						"Something between the other two.",
						6,
						() -> AletheiaConfig.halfHudColour,
						value -> AletheiaConfig.halfHudColour = value))
				.option(colour("Vulnerable again colour",
						"Green by default: go.",
						4,
						() -> AletheiaConfig.vulnerableHudColour,
						value -> AletheiaConfig.vulnerableHudColour = value))
				.option(tick("Play sound when it changes",
						"Off by default -- in a long fight this fires often. Plays the alert sound; Titles \u2192 "
								+ "Sound picks which one.",
						false,
						() -> AletheiaConfig.invulnerableSound,
						value -> AletheiaConfig.invulnerableSound = value))
				.option(slider("Look this far for the boss",
						"How far away a mob can be and still be the one whose bar is watched.",
						64, 8, 128, 4, " blocks",
						() -> AletheiaConfig.invulnerableRange,
						value -> AletheiaConfig.invulnerableRange = value))
				.option(slider("Read the fill every",
						"How often the bar's colour is sampled. Lower reacts sooner and costs a little more; "
								+ "2 ticks is a tenth of a second.",
						2, 1, 10, 1, " ticks",
						() -> AletheiaConfig.invulnerableCheckTicks,
						value -> AletheiaConfig.invulnerableCheckTicks = value))
				.option(text("The bar's fill picture contains",
						"Telos draws boss bars as pictures in its resource pack. This names the part that is the "
								+ "coloured fill, so the mod samples that and not the frame around it.\n\n"
								+ "Leave alone unless the server changes its pack. /aletheia mobscan prints what is "
								+ "being drawn.",
						"xikage/default/inner",
						() -> AletheiaConfig.barFillTexture,
						value -> AletheiaConfig.barFillTexture = value))
				.option(text("A real boss bar's picture contains",
						"How a genuine boss bar is told apart from any other bar the server draws.\n\n"
								+ "Leave alone unless the server changes its pack.",
						"glyph/bossbar",
						() -> AletheiaConfig.bossBarTextureFilter,
						value -> AletheiaConfig.bossBarTextureFilter = value))
				.build());

		placement("the invulnerability readout", VulnerabilityHud.INSTANCE, 0, 4, 40).forEach(builder::group);

		return builder.build();
	}

	private static ConfigCategory bossBar() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Boss bar"))
				.tooltip(Component.literal("A boss bar you can move, shape and colour by what the fight is doing."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Bar"))
						.option(LabelOption.create(Component.literal(
								"The server's bar is fixed to the top of the screen at one size. This is the same "
										+ "reading, drawn by the mod, where you want it.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Draw the boss bar",
								"Draw the mod's own bar.",
								true,
								() -> AletheiaConfig.showBossBar,
								value -> AletheiaConfig.showBossBar = value))
						.option(tick("Hide the server's bar",
								"Hide the original, so you are not looking at two of the same thing.\n\n"
										+ "Turn this off if you would rather keep the server's bar and have the mod's "
										+ "alongside it.",
								true,
								() -> AletheiaConfig.hideVanillaBossBar,
								value -> AletheiaConfig.hideVanillaBossBar = value))
						.option(choice("What to draw",
								"\"Bar and words\" is the bar with whatever labels are switched on under Text.\n\n"
										+ "\"Words only\" drops the bar and keeps the labels -- the boss's name and its "
										+ "percentage, as a plain line you can put anywhere.\n\n"
										+ "\"Percentage only\" is the figure and nothing else, whatever the Text "
										+ "switches say.\n\n"
										+ "Neither of the last two draws a bar, so Width, Height, Ends, Outline and "
										+ "Backing stop applying and the readout is as wide as its words.",
								0, BAR_STYLES,
								() -> AletheiaConfig.bossBarStyle,
								value -> AletheiaConfig.bossBarStyle = value))
						.option(slider("Width",
								"How wide the bar is. 182 px is the vanilla width.",
								182, 40, 400, 1, " px",
								() -> AletheiaConfig.bossBarWidth,
								value -> AletheiaConfig.bossBarWidth = value))
						.option(slider("Height",
								"How thick the bar is.",
								10, 2, 40, 1, " px",
								() -> AletheiaConfig.bossBarHeight,
								value -> AletheiaConfig.bossBarHeight = value))
						.option(slider("Gap between bars",
								"The space between bars when a fight puts up more than one.",
								3, 0, 20, 1, " px",
								() -> AletheiaConfig.bossBarGap,
								value -> AletheiaConfig.bossBarGap = value))
						.option(tick("Count only the health that can be taken",
								"Some fights hold a boss's bar above zero -- a shield or a phase floor -- so the "
										+ "last stretch is health you cannot remove.\n\n"
										+ "On, the bar empties over the part you can actually take off, so it reaches "
										+ "zero when the boss does.",
								true,
								() -> AletheiaConfig.bossBarTrueHealth,
								value -> AletheiaConfig.bossBarTrueHealth = value))
						.option(choice("Ends",
								"The shape of the bar's ends.",
								1, EDGES,
								() -> AletheiaConfig.bossBarEdges,
								value -> AletheiaConfig.bossBarEdges = value))
						.option(slider("How much of the end is shaped",
								"How far in from each end the shaping reaches. Only used when Ends is not Square.",
								3, 1, 16, 1, " px",
								() -> AletheiaConfig.bossBarCorner,
								value -> AletheiaConfig.bossBarCorner = value))
						.option(tick("Outline it",
								"A thin border, which keeps the bar readable over a bright background.",
								true,
								() -> AletheiaConfig.bossBarBorder,
								value -> AletheiaConfig.bossBarBorder = value))
						.option(slider("Backing darkness",
								"How dark the empty part of the bar is. 0 leaves it clear.",
								70, 0, 100, 5, "%",
								() -> AletheiaConfig.bossBarBackingAlpha,
								value -> AletheiaConfig.bossBarBackingAlpha = value))
						.option(tick("Keep it centred",
								"On, the bar stays centred horizontally and Placement only sets how far down it "
										+ "sits -- so it stays centred when the window is resized.\n\n"
										+ "Off, it is placed by corner and offset like every other readout.",
								true,
								() -> AletheiaConfig.bossBarCentred,
								value -> AletheiaConfig.bossBarCentred = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Colours"))
						.option(tick("Colour it by the state",
								"Paint the bar by whether the boss can be hurt, using the colours below -- so the "
										+ "bar itself is the answer to \"can I attack yet\".\n\n"
										+ "Off, the bar keeps one colour throughout.",
								true,
								() -> AletheiaConfig.bossBarColourByState,
								value -> AletheiaConfig.bossBarColourByState = value))
						.option(text("Hittable colour",
								"Hex code for the bar while the boss can be hurt.",
								"#5AD022",
								() -> AletheiaConfig.bossBarVulnerableColour,
								value -> AletheiaConfig.bossBarVulnerableColour = value))
						.option(text("Half damage colour",
								"Hex code for the reduced-damage state.",
								"#B084E8",
								() -> AletheiaConfig.bossBarHalfColour,
								value -> AletheiaConfig.bossBarHalfColour = value))
						.option(text("Invulnerable colour",
								"Hex code for while the boss cannot be hurt.",
								"#1757A7",
								() -> AletheiaConfig.bossBarInvulnerableColour,
								value -> AletheiaConfig.bossBarInvulnerableColour = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Text"))
						.option(tick("Name the boss",
								"Put the boss's name with the bar.",
								true,
								() -> AletheiaConfig.bossBarShowName,
								value -> AletheiaConfig.bossBarShowName = value))
						.option(tick("Show the percentage",
								"How much health is left, in figures -- which a bar alone cannot tell you "
										+ "precisely.",
								true,
								() -> AletheiaConfig.bossBarShowPercent,
								value -> AletheiaConfig.bossBarShowPercent = value))
						.option(tick("Say the state in words",
								"Add INVULNERABLE or ATTACK to the bar's own label, for when the colour is not "
										+ "enough on its own.",
								true,
								() -> AletheiaConfig.bossBarShowState,
								value -> AletheiaConfig.bossBarShowState = value))
						.option(choice("Where the words go",
								"Above, on, or below the bar. On the bar is tightest; above and below stay "
										+ "readable at small bar heights.",
								0, TEXT_PLACES,
								() -> AletheiaConfig.bossBarTextPlace,
								value -> AletheiaConfig.bossBarTextPlace = value))
						.option(tick("Colour the words by the state too",
								"On, the label takes the same colour as the bar. Off, it keeps the fixed colour "
										+ "below.",
								false,
								() -> AletheiaConfig.bossBarTextByState,
								value -> AletheiaConfig.bossBarTextByState = value))
						.option(colour("Word colour",
								"The label's colour, when it is not following the state.",
								0,
								() -> AletheiaConfig.bossBarTextColour,
								value -> AletheiaConfig.bossBarTextColour = value))
						.build());

		placement("the boss bar", BossBarHud.INSTANCE, 0, 4, 12).forEach(builder::group);
		return builder.build();
	}

	private static ConfigCategory naturesGift() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Nature's Gift"))
				.tooltip(Component.literal("The boots' cooldown, as a number instead of a guess."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(LabelOption.create(Component.literal(
								"The server does not show this cooldown anywhere, so the mod times it itself.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show Nature's Gift cooldown",
								"A line counting down to the next proc.",
								true,
								() -> AletheiaConfig.showGiftHud,
								value -> AletheiaConfig.showGiftHud = value))
						.option(text("Label",
								"The word in front of the time. Blank for the bare number.",
								"ngift",
								() -> AletheiaConfig.giftLabel,
								value -> AletheiaConfig.giftLabel = value))
						.option(text("Ready text",
								"What it says when the cooldown is up.",
								"ready",
								() -> AletheiaConfig.giftReadyText,
								value -> AletheiaConfig.giftReadyText = value))
						.option(text("Only track boots named",
								"Only time boots whose name contains this. Blank tracks any boots that proc.\n\n"
										+ "For when you own more than one pair and only one of them matters.",
								"",
								() -> AletheiaConfig.giftNameFilter,
								value -> AletheiaConfig.giftNameFilter = value))
						.option(choice("Time format",
								"Minutes and seconds, or plain seconds.",
								0, TIME_FORMATS,
								() -> AletheiaConfig.giftTimeFormat,
								value -> AletheiaConfig.giftTimeFormat = value))
						.option(tick("Show even without the boots on",
								"Off, the line appears when the boots are equipped or the timer is still "
										+ "running.\n\n"
										+ "On, it is always there -- useful while you are placing it in the editor.",
								false,
								() -> AletheiaConfig.giftAlwaysShow,
								value -> AletheiaConfig.giftAlwaysShow = value))
						.build());

		placement("the cooldown", NaturesGiftHud.INSTANCE, 0, 4, 4).forEach(builder::group);

		builder.group(OptionGroup.createBuilder()
						.name(Component.literal("Cooldown source"))
						.option(slider("Fallback cooldown length",
								"How long to count when the server has not said. The timer normally learns the "
										+ "real length from the item itself; this is what it assumes until then.",
								360, 30, 900, 5, "s",
								() -> AletheiaConfig.giftFallbackCooldownSeconds,
								value -> AletheiaConfig.giftFallbackCooldownSeconds = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Proc title"))
						.option(tick("Show a title when it procs",
								"A title the moment the boots fire, so you know the timer has started.\n\n"
										+ TITLE_TIMING,
								true,
								() -> AletheiaConfig.showGiftProcTitle,
								value -> AletheiaConfig.showGiftProcTitle = value))
						.option(text("Proc title text",
								"The big line.",
								"NATURE'S GIFT",
								() -> AletheiaConfig.giftProcTitleText,
								value -> AletheiaConfig.giftProcTitleText = value))
						.option(text("Proc subtitle",
								"The smaller line. {time} is the cooldown that has just started.",
								"{time}",
								() -> AletheiaConfig.giftProcSubtitle,
								value -> AletheiaConfig.giftProcSubtitle = value))
						.option(colour("Proc colour",
								"What colour the title is drawn in.",
								4,
								() -> AletheiaConfig.giftProcColour,
								value -> AletheiaConfig.giftProcColour = value))
						.option(tick("Play sound on a proc",
								"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
								true,
								() -> AletheiaConfig.giftProcSound,
								value -> AletheiaConfig.giftProcSound = value))
						.build());

		return builder.build();
	}

	private static ConfigCategory afterburner() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Afterburner"))
				.tooltip(Component.literal("Counting down the cloak's delayed second shot."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Second shot"))
						.option(LabelOption.create(Component.literal(
								"The cloak's fire lands some seconds after you use it. Nothing on screen says when, "
										+ "so this counts it down.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Count down the fire shot",
								"A line counting down to the moment the fire lands.",
								true,
								() -> AletheiaConfig.showBurnHud,
								value -> AletheiaConfig.showBurnHud = value))
						.option(tenths("Fire lands after",
								"How long the delay is. Set this to what your cloak actually does -- if the "
										+ "countdown finishes early or late, this is the number to change.",
								70, 5, 300,
								() -> AletheiaConfig.burnDelayTenths,
								value -> AletheiaConfig.burnDelayTenths = value))
						.option(text("Ability item",
								"Which item starts the count, matched against its model. The default is the fire "
										+ "cloak's.\n\n"
										+ "Trimming it catches more -- \"ability/cloak\" takes every cloak.",
								"ability/cloak/ut-fire",
								() -> AletheiaConfig.burnItemFilter,
								value -> AletheiaConfig.burnItemFilter = value))
						.option(text("Label",
								"The word in front of the time. Blank for the bare number.",
								"burn",
								() -> AletheiaConfig.burnLabel,
								value -> AletheiaConfig.burnLabel = value))
						.option(text("Ready text",
								"What it says when nothing is in the air.",
								"ready",
								() -> AletheiaConfig.burnReadyText,
								value -> AletheiaConfig.burnReadyText = value))
						.option(tick("Show without the cloak in hand",
								"Off, the line appears while the cloak is held or a shot is counting down.\n\n"
										+ "On, it is always there -- useful while you are placing it in the editor.",
								false,
								() -> AletheiaConfig.burnAlwaysShow,
								value -> AletheiaConfig.burnAlwaysShow = value))
						.build());

		placement("the countdown", AfterburnerHud.INSTANCE, 0, 4, 28).forEach(builder::group);

		builder.group(OptionGroup.createBuilder()
				.name(Component.literal("Fire title"))
				.option(tick("Show a title when it fires",
						"A title at the moment the fire lands, for when you are not watching the countdown.\n\n"
								+ TITLE_TIMING,
						true,
						() -> AletheiaConfig.showBurnFireTitle,
						value -> AletheiaConfig.showBurnFireTitle = value))
				.option(text("Fire title text",
						"The big line.",
						"FIRE",
						() -> AletheiaConfig.burnFireTitleText,
						value -> AletheiaConfig.burnFireTitleText = value))
				.option(text("Fire subtitle",
						"The smaller line. Blank for none.",
						"",
						() -> AletheiaConfig.burnFireSubtitle,
						value -> AletheiaConfig.burnFireSubtitle = value))
				.option(colour("Fire colour",
						"What colour the title is drawn in.",
						2,
						() -> AletheiaConfig.burnFireColour,
						value -> AletheiaConfig.burnFireColour = value))
				.option(tick("Play sound when it fires",
						"Plays the alert sound. Titles \u2192 Sound picks which one, and how loud.",
						true,
						() -> AletheiaConfig.burnFireSound,
						value -> AletheiaConfig.burnFireSound = value))
				.build());

		return builder.build();
	}

	private static ConfigCategory playerStats() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Player stats"))
				.tooltip(Component.literal("Your own stats, off the server's HUD and onto yours."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(LabelOption.create(Component.literal(
								"Read straight out of the server's own HUD, so these are its numbers, not the mod's "
										+ "guess at them.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show your stats on screen",
								"Draw the stats you pick below where you have placed them.",
								true,
								() -> AletheiaConfig.showStatsHud,
								value -> AletheiaConfig.showStatsHud = value))
						// The names are the server's, not the mod's, so the command that lists them is
						// half of this setting -- said here because the settings screen is where you are
						// standing when you need to know.
						.option(text("Stats to show (/aletheia stats lists them)",
								"A comma-separated list of name=label pairs. The name is the server's, the label "
										+ "is what you want on screen: \"attack=ATK\" draws the attack stat as ATK.\n\n"
										+ "Run /aletheia stats to see exactly what the server is calling each one, and "
										+ "copy the names out of that. The order here is the order they are drawn in.",
								"attack=ATK, defense=DEF, speed=SPD, vitality=VIT",
								() -> AletheiaConfig.statsPicked,
								value -> AletheiaConfig.statsPicked = value))
						.option(tick("Keep the bracketed part",
								"Some stats arrive as \"142 (+30)\". Off keeps only the total; on keeps the whole "
										+ "thing, bonus and all.",
								false,
								() -> AletheiaConfig.statsFullValue,
								value -> AletheiaConfig.statsFullValue = value))
						.option(tick("All on one line",
								"Off stacks them in a column, which is the shape that fits down a side of the "
										+ "screen. On puts them in a row.",
								false,
								() -> AletheiaConfig.statsOneLine,
								value -> AletheiaConfig.statsOneLine = value))
						.option(text("Separator on one line",
								"What goes between the stats in a row. Only used when \"All on one line\" is on.",
								"  ",
								() -> AletheiaConfig.statsSeparator,
								value -> AletheiaConfig.statsSeparator = value))
						.option(colour("Colour",
								"What colour the stats are drawn in.",
								0,
								() -> AletheiaConfig.statsColour,
								value -> AletheiaConfig.statsColour = value))
						.build());

		placement("your stats", PlayerStatsHud.INSTANCE, 1, 4, 4).forEach(builder::group);
		return builder.build();
	}

	private static ConfigCategory hpPots() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("HP pots"))
				.tooltip(Component.literal("How many healing potions you have left, where you will see it."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(tick("Show the potion counter",
								"A line reading Pots: 1/5, in the colour below.",
								true,
								() -> AletheiaConfig.showPotsHud,
								value -> AletheiaConfig.showPotsHud = value))
						.option(text("Label",
								"The word in front of the count. Blank for the bare number.",
								"Pots",
								() -> AletheiaConfig.potsLabel,
								value -> AletheiaConfig.potsLabel = value))
						.option(slider("Out of",
								"How many you can hold. The server only tells the mod how many you have, so the "
										+ "total is set here.",
								5, 1, 20, 1, "",
								() -> AletheiaConfig.potsMax,
								value -> AletheiaConfig.potsMax = value))
						.option(colour("Colour",
								"The normal colour. The warning colours below override it when the count gets low.",
								0,
								() -> AletheiaConfig.potsColour,
								value -> AletheiaConfig.potsColour = value))
						.option(tick("Warn when they run low",
								"Turns the count amber at a third left and red at none, which is the whole point "
										+ "of having it on your own HUD -- noticing before it runs out.",
								true,
								() -> AletheiaConfig.potsWarnWhenLow,
								value -> AletheiaConfig.potsWarnWhenLow = value))
						.option(tick("Show even with no reading",
								"Off, the line appears once the server's HUD has a count in it.\n\n"
										+ "On, it is always there -- useful while you are placing it in the editor.",
								false,
								() -> AletheiaConfig.potsAlwaysShow,
								value -> AletheiaConfig.potsAlwaysShow = value))
						.build());

		placement("the potion counter", HpPotsHud.INSTANCE, 0, 4, 16).forEach(builder::group);

		builder.group(OptionGroup.createBuilder()
				.name(Component.literal("Source"))
				.option(text("Read the part whose font contains",
						"Which piece of the server's HUD the count is read from, matched against the name of the "
								+ "font it is drawn in.\n\n"
								+ "Leave alone unless the server changes its pack. /aletheia hudscan prints the fonts "
								+ "currently on screen.",
						"hppot",
						() -> AletheiaConfig.potsFontFilter,
						value -> AletheiaConfig.potsFontFilter = value))
				.option(slider("Read the count every",
						"How often the server's HUD is checked. Lower reacts sooner and costs a little more.",
						4, 1, 20, 1, " ticks",
						() -> AletheiaConfig.potsCheckTicks,
						value -> AletheiaConfig.potsCheckTicks = value))
				.build());

		return builder.build();
	}

	private static ConfigCategory dungeonTimer() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Dungeon timer"))
				.tooltip(Component.literal("A clock for the run you are in, and the time to beat."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(LabelOption.create(Component.literal(
								"Telos says how long a run took once it is over, in its own leaderboard. This is "
										+ "the half it does not give you: the clock while you are still running, and "
										+ "your best sitting next to it.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show the run clock",
								"A line counting up from the moment you enter a dungeon, in the colour below.",
								true,
								() -> AletheiaConfig.showTimerHud,
								value -> AletheiaConfig.showTimerHud = value))
						.option(text("Label",
								"The word in front of the time. Blank for the bare clock.",
								"run",
								() -> AletheiaConfig.timerLabel,
								value -> AletheiaConfig.timerLabel = value))
						.option(choice("Time format",
								"Minutes and seconds, or plain seconds. The same two Nature's Gift offers.",
								0, TIME_FORMATS,
								() -> AletheiaConfig.timerTimeFormat,
								value -> AletheiaConfig.timerTimeFormat = value))
						.option(colour("Colour",
								"The normal colour, while the best is still in reach.",
								0,
								() -> AletheiaConfig.timerColour,
								value -> AletheiaConfig.timerColour = value))
						.option(colour("Colour once the best is gone",
								"What the clock turns the moment it passes your best for this dungeon.\n\n"
										+ "This is the only comparison a timer without splits can honestly make. It "
										+ "cannot tell you whether you are ahead at this point in the run -- nothing "
										+ "here knows where in the run you are -- but it can tell you the moment the "
										+ "best is out of reach.",
								3,
								() -> AletheiaConfig.timerPastBestColour,
								value -> AletheiaConfig.timerPastBestColour = value))
						.option(tick("Show even outside a dungeon",
								"Off, the clock appears when a run starts and clears a little after it ends.\n\n"
										+ "On, it is always there -- useful while you are placing it in the editor.",
								false,
								() -> AletheiaConfig.timerAlwaysShow,
								value -> AletheiaConfig.timerAlwaysShow = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Personal best"))
						.option(LabelOption.create(Component.literal(
								"Bests are read off the server's own clear line, never off this clock -- a stopwatch "
										+ "started on a dimension change cannot agree with Telos to the second, and a "
										+ "best that disagrees with the leaderboard is worse than none.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show the time to beat",
								"Puts your best for this dungeon on the same line as the clock.",
								true,
								() -> AletheiaConfig.timerShowBest,
								value -> AletheiaConfig.timerShowBest = value))
						.option(text("Best label",
								"The word in front of it. Blank for the bare time.",
								"PB",
								() -> AletheiaConfig.timerBestLabel,
								value -> AletheiaConfig.timerBestLabel = value))
						.option(LabelOption.create(Component.literal(
								"/aletheia timer lists every best on file · /aletheia timer clear wipes them")
								.withStyle(ChatFormatting.DARK_GRAY)))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Dungeons"))
						.option(LabelOption.create(Component.literal(
								"Every dungeon is timed, with no list to keep: a Telos dungeon room is a numbered "
										+ "dimension (telos:dungeon/2, telos:neo_eden/1) and the open world is not. "
										+ "The run is named from the area the server's own ribbon shows, since the id "
										+ "is an instance slot -- the same dungeon is dungeon/2 one run and dungeon/11 "
										+ "the next.")
								.withStyle(ChatFormatting.GRAY)))
						.option(text("Extra dimensions that count as a dungeon",
								"A comma-separated list, matched against the dimension id you are standing in, for "
										+ "anywhere the rule above misses.\n\n"
										+ "Empty by default and normally left that way. Run /aletheia timer to print "
										+ "the dimension you are standing in, whether it counts, and what the server "
										+ "calls the area.",
								"",
								() -> AletheiaConfig.timerDimensionFilter,
								value -> AletheiaConfig.timerDimensionFilter = value))
						.build());

		placement("the run clock", DungeonTimerHud.INSTANCE, 0, 4, 52).forEach(builder::group);
		return builder.build();
	}

	/**
	 * The split readout: every boss of the dungeon, and what the leaderboard said each one took.
	 *
	 * <p>Its own category rather than a group under the run clock, because it is a second readout with
	 * its own place on screen, and a category can only carry one Placement group.
	 */
	private static ConfigCategory dungeonSplits() {
		ConfigCategory.Builder builder = ConfigCategory.createBuilder()
				.name(Component.literal("Dungeon splits"))
				.tooltip(Component.literal("Every boss of the dungeon you are in, and what each one took."))
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Readout"))
						.option(LabelOption.create(Component.literal(
								"A Telos dungeon is not one fight. Celestial's Province is Asmodeus, then Seraphim "
										+ "in the same room, then True Seraph through the portal that opens when "
										+ "Seraphim falls; Rustborn Kingdom has four stages ending in the Dawn of "
										+ "Creation. This lists them, fills each one in as it falls, and greys out "
										+ "what is still to come.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show the splits",
								"The list of bosses, in the colour below.",
								true,
								() -> AletheiaConfig.showSplitsHud,
								value -> AletheiaConfig.showSplitsHud = value))
						.option(tick("Show the dungeon's name at the top",
								"A heading above the list. Off for the bosses alone.",
								true,
								() -> AletheiaConfig.splitsShowTitle,
								value -> AletheiaConfig.splitsShowTitle = value))
						.option(text("Text for a stage not reached yet",
								"What stands in the time column until that boss falls.",
								"--",
								() -> AletheiaConfig.splitsPendingText,
								value -> AletheiaConfig.splitsPendingText = value))
						.option(colour("Colour",
								"Stages that are done, and the one you are on.",
								0,
								() -> AletheiaConfig.splitsColour,
								value -> AletheiaConfig.splitsColour = value))
						.option(tick("Show even outside a dungeon",
								"Off, the list appears when a run does. On, a sample stays up -- useful while you "
										+ "are placing it in the editor.",
								false,
								() -> AletheiaConfig.splitsAlwaysShow,
								value -> AletheiaConfig.splitsAlwaysShow = value))
						.build())
				.group(OptionGroup.createBuilder()
						.name(Component.literal("Against your best"))
						.option(LabelOption.create(Component.literal(
								"Each boss keeps its own best, taken from the server's clear line for that boss -- "
										+ "so a row compares like with like, and agrees with the leaderboard you just "
										+ "read. Note the server times a stage, not the run: in Celestial's Province "
										+ "Asmodeus and Seraphim share a clock, so Seraphim's time includes his.")
								.withStyle(ChatFormatting.GRAY)))
						.option(tick("Show the difference",
								"Puts +0:12 or -0:05 next to a boss you have beaten before. A stage that set a new "
										+ "best turns green instead.",
								true,
								() -> AletheiaConfig.splitsShowDelta,
								value -> AletheiaConfig.splitsShowDelta = value))
						.option(colour("Colour when slower than your best",
								"The colour for a stage that came in behind the time on file.",
								3,
								() -> AletheiaConfig.splitsBehindColour,
								value -> AletheiaConfig.splitsBehindColour = value))
						.option(LabelOption.create(Component.literal(
								"/aletheia timer lists the splits of the run you are in, and every best on file")
								.withStyle(ChatFormatting.DARK_GRAY)))
						.build());

		placement("the split list", DungeonSplitsHud.INSTANCE, 0, 4, 68).forEach(builder::group);
		return builder.build();
	}

	// ------------------------------------------------------------------ Placement

	/**
	 * The two placement groups every movable readout gets: the editor, and the numbers behind it.
	 *
	 * <p>Bound to the {@link AletheiaHud} rather than to the config fields, so the sliders and the
	 * drag editor are provably editing the same four values -- and so adding a readout cannot leave
	 * the two out of step.
	 *
	 * <p>The editor comes first and the numbers are collapsed behind it. Both work, but only one of
	 * them is a thing you can do without knowing the answer in advance, and putting four sliders in
	 * front of somebody is a good way to make them think that is the way it is done.
	 */
	private static List<OptionGroup> placement(String what, AletheiaHud hud,
			int anchorFallback, int offsetXFallback, int offsetYFallback) {
		List<OptionGroup> groups = new ArrayList<>(2);

		groups.add(OptionGroup.createBuilder()
				.name(Component.literal("Placement"))
				.description(describe("Where " + what + " sits on screen."))
				.option(LabelOption.create(Component.literal(
						"The editor draws every readout over the live game and lets you drag them into place.")
						.withStyle(ChatFormatting.GRAY)))
				.option(editorButton())
				.build());

		groups.add(OptionGroup.createBuilder()
				.name(Component.literal("Placement by hand"))
				.description(describe("The same four numbers the editor writes. Here for when you want an exact "
						+ "value, or the same layout on two machines."))
				.collapsed(true)
				.option(choice("Corner",
						"Which corner the distances below are measured from. Dragging in the editor sets this to "
								+ "whichever corner you drop the readout nearest, so it stays put when the window is "
								+ "resized.",
						anchorFallback, ANCHORS,
						hud::anchor, value -> hud.anchor(value)))
				.option(slider("Distance from the side",
						"How far in from the left or right edge, in GUI pixels.",
						offsetXFallback, 0, AletheiaHud.MAX_OFFSET, 1, " px",
						hud::offsetX, value -> hud.offsetX(value)))
				.option(slider("Distance from top/bottom",
						"How far in from the top or bottom edge, in GUI pixels.",
						offsetYFallback, 0, AletheiaHud.MAX_OFFSET, 1, " px",
						hud::offsetY, value -> hud.offsetY(value)))
				.option(slider("Size",
						"How big it is drawn, against your GUI Scale rather than instead of it.",
						100, AletheiaHud.MIN_SCALE, AletheiaHud.MAX_SCALE, 5, "%",
						hud::scalePercent, value -> hud.scalePercent(value)))
				.build());

		return groups;
	}

	// ------------------------------------------------------------------ Option shorthands

	/** One description, split on newlines so YACL lays out the paragraphs. */
	private static OptionDescription describe(String help) {
		List<Component> lines = new ArrayList<>();
		for (String line : help.split("\n")) {
			lines.add(Component.literal(line));
		}
		return OptionDescription.createBuilder().text(lines).build();
	}

	private static Option<Boolean> tick(String name, String help, boolean fallback,
			Supplier<Boolean> get, Consumer<Boolean> set) {
		return Option.<Boolean>createBuilder()
				.name(Component.literal(name))
				.description(describe(help))
				.binding(fallback, get, set)
				.controller(TickBoxControllerBuilder::create)
				.build();
	}

	private static Option<String> text(String name, String help, String fallback,
			Supplier<String> get, Consumer<String> set) {
		return Option.<String>createBuilder()
				.name(Component.literal(name))
				.description(describe(help))
				.binding(fallback, get, set)
				.controller(StringControllerBuilder::create)
				.build();
	}

	private static Option<Integer> slider(String name, String help, int fallback, int min, int max, int step,
			String unit, Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder()
				.name(Component.literal(name))
				.description(describe(help))
				.binding(fallback, get, set)
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range(min, max)
						.step(step)
						.formatValue(value -> Component.literal(value + unit)))
				.build();
	}

	/**
	 * A slider over tenths of a second, shown as {@code 7.0s}. Whole seconds are too coarse for the
	 * counts this is used for, and the settings library's sliders only hand back whole numbers.
	 */
	private static Option<Integer> tenths(String name, String help, int fallback, int min, int max,
			Supplier<Integer> get, Consumer<Integer> set) {
		return Option.<Integer>createBuilder()
				.name(Component.literal(name))
				.description(describe(help))
				.binding(fallback, get, set)
				.controller(option -> IntegerSliderControllerBuilder.create(option)
						.range(min, max)
						.step(1)
						.formatValue(value -> Component.literal(CooldownText.asTenths(value))))
				.build();
	}

	/** A list of labels stored as its index, which is how the settings file has always held these. */
	private static Option<Integer> choice(String name, String help, int fallback, String[] labels,
			Supplier<Integer> get, Consumer<Integer> set) {
		List<Integer> indices = IntStream.range(0, labels.length).boxed().toList();
		return Option.<Integer>createBuilder()
				.name(Component.literal(name))
				.description(describe(help))
				.binding(fallback, get, set)
				.controller(option -> CyclingListControllerBuilder.create(option)
						.values(indices)
						.formatValue(index -> Component.literal(label(labels, index))))
				.build();
	}

	private static Option<Integer> colour(String name, String help, int fallback,
			Supplier<Integer> get, Consumer<Integer> set) {
		return choice(name, help, fallback, COLOURS, get, set);
	}

	/** Nothing stops a hand-edited file naming an index that is not there. */
	private static String label(String[] labels, int index) {
		return index >= 0 && index < labels.length ? labels[index] : String.valueOf(index);
	}

	private static ButtonOption editorButton() {
		return ButtonOption.createBuilder()
				.name(Component.literal("Readout editor"))
				.description(describe("Draws every readout over the live game so you can drag them where you "
						+ "want them, resize them, and switch them on and off. The controls are listed on the "
						+ "screen itself.\n\n"
						+ "A readout with nothing to read stands in with its own name, so it can be placed "
						+ "without waiting for a fight."))
				.text(Component.literal("Open editor"))
				.action((screen, option) -> {
					// The editor takes the screen, so anything typed here is applied before it does.
					screen.finishOrSave();
					HudEditorScreen.openLater(Minecraft.getInstance());
				})
				.build();
	}
}
