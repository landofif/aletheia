package dev.landofif.aletheia;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.config.ConfigScreen;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.CooldownText;
import dev.landofif.aletheia.detect.DungeonEvent;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.actionbar.ActionBar;
import dev.landofif.aletheia.afterburner.Afterburner;
import dev.landofif.aletheia.boss.BossBar;
import dev.landofif.aletheia.boss.BossPhaseTracker;
import dev.landofif.aletheia.boss.BossPhases;
import dev.landofif.aletheia.boss.BossReminders;
import dev.landofif.aletheia.boss.MobBars;
import dev.landofif.aletheia.boss.Vulnerability;
import dev.landofif.aletheia.dreadwood.DreadwoodRun;
import dev.landofif.aletheia.gift.NaturesGift;
import dev.landofif.aletheia.hud.HudEditorScreen;
import dev.landofif.aletheia.log.PassengerSpam;
import dev.landofif.aletheia.pots.HpPots;
import dev.landofif.aletheia.ui.Alerts;
import dev.landofif.aletheia.serverhud.MobScan;
import dev.landofif.aletheia.stats.PlayerStats;
import dev.landofif.aletheia.timer.DungeonBests;
import dev.landofif.aletheia.timer.DungeonSplits;
import dev.landofif.aletheia.timer.DungeonTimer;
import dev.landofif.aletheia.world.Domains;
import dev.landofif.aletheia.world.PropScan;
import dev.landofif.aletheia.serverhud.ServerHud;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * {@code /aletheia} -- a way to preview each title without being in the dungeon, and to check why
 * nothing is happening if the mod looks idle.
 */
public final class AletheiaCommands {
	private AletheiaCommands() {
	}

	/**
	 * Shorthands for {@code /aletheia}. {@code telos} is what the commands were called before the mod
	 * had a name of its own.
	 *
	 * <p>A client command wins over a server one of the same name -- it is dispatched locally and the
	 * server never sees it -- so a short alias is worth a moment's thought. Drop one from this list if
	 * Telos ever wants the name back.
	 */
	private static final String[] ALIASES = {"telos", "ale", "a"};

	public static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("aletheia");

			root.then(ClientCommands.literal("status").executes(context -> {
				status(context.getSource());
				return 1;
			}));

			LiteralArgumentBuilder<FabricClientCommandSource> test = ClientCommands.literal("test");
			test.then(preview("progress", () -> new DungeonEvent.Progress(259, 265)));
			test.then(preview("progress-undefined", () -> new DungeonEvent.Progress(175, DungeonEvent.Progress.UNKNOWN_TOTAL)));
			test.then(preview("puzzle-start", () -> new DungeonEvent.PuzzleStarted(playerName(), "light")));
			test.then(preview("puzzle", () -> new DungeonEvent.PuzzleSolved(playerName(), "light")));
			test.then(preview("barracks-start", () -> new DungeonEvent.BarracksStarted(playerName())));
			test.then(preview("barracks", () -> new DungeonEvent.BarracksCleared(playerName())));
			// Each preview takes the next room along, so running it repeatedly walks the run count up
			// the way a real dungeon does.
			test.then(preview("dreadwood-start", () -> new DungeonEvent.DreadwoodRoomStarted(playerName(), nextTestRoom())));
			test.then(preview("dreadwood-done", () -> new DungeonEvent.DreadwoodRoomCleared(playerName(), nextTestRoom())));
			test.then(preview("shadowlands", () -> new DungeonEvent.ShadowlandsSpawn(
					nextTestMob(), "The torch burns bright.")));
			// One stabiliser per run, so running it five times walks the fight from 1/5 to the last one.
			test.then(preview("cog", () -> new DungeonEvent.CogStabilisers(nextTestStabiliser(), TEST_STABILISERS)));
			test.then(preview("enough", () -> new DungeonEvent.CherubimShout(DungeonEvent.Shout.ENOUGH)));
			test.then(preview("silence", () -> new DungeonEvent.CherubimShout(DungeonEvent.Shout.SILENCE)));

			// The phase warnings do not come from chat, so they get their own preview: one phase of one
			// fight per run, so every wording can be seen without finding the boss.
			test.then(ClientCommands.literal("phase").executes(context -> {
				BossPhases.Phase phase = nextTestPhase();
				Alerts.showTitle(
						phase.name(),
						null,
						AletheiaConfig.colour(AletheiaConfig.phaseColour),
						AletheiaConfig.phaseStayTicks);
				if (AletheiaConfig.phaseSound) {
					Alerts.playSound();
				}
				return 1;
			}));

			// Nor do the party's own calls: they come off the same bar, in eleven fights, so this is the
			// only way to see the wording without standing in one. A call per run, so both can be seen.
			test.then(ClientCommands.literal("reminder").executes(context -> {
				BossReminders.preview();
				return 1;
			}));

			// Nor does the proc title -- it comes off the item cooldown, which cannot be faked from here.
			test.then(ClientCommands.literal("ngift").executes(context -> {
				int seconds = NaturesGift.lastTotalSeconds() > 0
						? NaturesGift.lastTotalSeconds()
						: AletheiaConfig.giftFallbackCooldownSeconds;
				NaturesGift.showProcTitle(seconds);
				return 1;
			}));

			// Nor does the fire title -- it lands at the end of a count that has to be started by a cast.
			test.then(ClientCommands.literal("burn").executes(context -> {
				Afterburner.showFireTitle();
				return 1;
			}));

			// The invulnerable readout is a state rather than a message, so its preview walks the state:
			// untouchable for a few seconds, then the window opening.
			test.then(ClientCommands.literal("invulnerable").executes(context -> {
				Vulnerability.preview();
				return 1;
			}));
			root.then(test);

			root.then(ClientCommands.literal("actionbar").executes(context -> {
				actionBar(context.getSource());
				return 1;
			}));

			root.then(ClientCommands.literal("hudscan").executes(context -> {
				hudScan(context.getSource());
				return 1;
			}));

			// The props in your way have no name, so they never show up in a mobscan. This lists what is
			// standing near you whether it is named or not, and prints the strings a filter matches.
			LiteralArgumentBuilder<FabricClientCommandSource> propscan = ClientCommands.literal("props");
			propscan.executes(context -> props(context.getSource(), 0));
			propscan.then(ClientCommands.argument("blocks", IntegerArgumentType.integer(1, 128))
					.executes(context -> props(context.getSource(), IntegerArgumentType.getInteger(context, "blocks"))));
			// Whatever is piled on the mob is the usual question, so that is the default. This is for the
			// other one: what is standing around *you*.
			propscan.then(ClientCommands.literal("me").executes(context ->
					props(context.getSource(), null, PropScan.DEFAULT_RANGE)));
			root.then(propscan);

			// The circle the ring is drawn over is one more display among the pile, so it is easy to lose
			// in a propscan. This asks the narrower question: what is being replaced, and at what size.
			root.then(ClientCommands.literal("domain").executes(context -> {
				domain(context.getSource());
				return 1;
			}));

			root.then(ClientCommands.literal("hud").executes(context -> {
				HudEditorScreen.openLater(Minecraft.getInstance());
				return 1;
			}));

			LiteralArgumentBuilder<FabricClientCommandSource> gift = ClientCommands.literal("ngift");
			gift.executes(context -> {
				gift(context.getSource());
				return 1;
			});
			// The countdown is the mod's own, so there has to be a way to end one that is wrong --
			// a proc missed while the boots were off, say.
			gift.then(ClientCommands.literal("reset").executes(context -> {
				NaturesGift.resetByHand();
				context.getSource().sendFeedback(Component.literal("Nature's Gift timer cleared.")
						.withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			root.then(gift);

			LiteralArgumentBuilder<FabricClientCommandSource> burn = ClientCommands.literal("burn");
			burn.executes(context -> {
				burn(context.getSource());
				return 1;
			});
			// The count normally starts itself off the cloak's cooldown. This is for when it cannot --
			// the ability somewhere the client cannot see it -- and is worth binding to a key, since
			// pressing it as you cast is as good as the reading.
			burn.then(ClientCommands.literal("start").executes(context -> {
				Afterburner.startByHand();
				context.getSource().sendFeedback(Component.literal("Counting to the fire: "
						+ Afterburner.statusLine()).withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			burn.then(ClientCommands.literal("reset").executes(context -> {
				Afterburner.resetByHand();
				context.getSource().sendFeedback(Component.literal("Afterburner count cleared.")
						.withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			root.then(burn);

			root.then(ClientCommands.literal("boss").executes(context -> {
				boss(context.getSource());
				return 1;
			}));

			// Takes a note so two runs can be told apart in the log: /aletheia mobscan blue, then
			// /aletheia mobscan normal, and the difference between them is what carries the state.
			LiteralArgumentBuilder<FabricClientCommandSource> mobscan = ClientCommands.literal("mobscan");
			mobscan.executes(context -> mobScan(context.getSource(), ""));
			mobscan.then(ClientCommands.argument("note", StringArgumentType.greedyString())
					.executes(context -> mobScan(context.getSource(), StringArgumentType.getString(context, "note"))));
			root.then(mobscan);

			root.then(ClientCommands.literal("stats").executes(context -> {
				stats(context.getSource());
				return 1;
			}));

			root.then(ClientCommands.literal("pots").executes(context -> {
				pots(context.getSource());
				return 1;
			}));

			LiteralArgumentBuilder<FabricClientCommandSource> dreadwood = ClientCommands.literal("dreadwood");
			dreadwood.executes(context -> {
				dreadwood(context.getSource());
				return 1;
			});
			dreadwood.then(ClientCommands.literal("reset").executes(context -> {
				DreadwoodRun.resetByHand();
				context.getSource().sendFeedback(Component.literal("Dreadwood run count cleared.")
						.withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			root.then(dreadwood);

			LiteralArgumentBuilder<FabricClientCommandSource> timer = ClientCommands.literal("timer");
			timer.executes(context -> {
				dungeonTimer(context.getSource());
				return 1;
			});
			timer.then(ClientCommands.literal("reset").executes(context -> {
				DungeonTimer.resetByHand();
				context.getSource().sendFeedback(Component.literal("Dungeon timer stopped.")
						.withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			timer.then(ClientCommands.literal("clear").executes(context -> {
				DungeonBests.forgetAll();
				context.getSource().sendFeedback(Component.literal("Personal bests wiped.")
						.withStyle(ChatFormatting.GREEN));
				return 1;
			}));
			root.then(timer);

			// The bare command opens the settings, which is what you want nine times out of ten.
			root.executes(context -> {
				openSettings(context.getSource());
				return 1;
			});

			// The shorthands, redirected at the real command rather than built again, so they cannot
			// drift out of step with it. Each needs its own executes(): a redirect only forwards the
			// arguments after the name, so without one a bare alias would be a syntax error where the
			// bare command opens the settings.
			CommandNode<FabricClientCommandSource> registered = dispatcher.register(root);
			for (String alias : ALIASES) {
				dispatcher.register(ClientCommands.literal(alias)
						.redirect(registered)
						.executes(context -> {
							openSettings(context.getSource());
							return 1;
						}));
			}
		});
	}

	/**
	 * Opens the settings.
	 *
	 * <p>Scheduled rather than opened on the spot for the same reason the HUD editor is: the command
	 * runs while the chat screen is still up, and chat closes itself afterwards, which would take this
	 * screen down with it.
	 */
	private static void openSettings(FabricClientCommandSource source) {
		// The guard belongs inside the scheduled task, not around scheduling it: the call happens on
		// the client thread a tick later, where a throw would go nowhere near this method.
		Minecraft client = Minecraft.getInstance();
		client.schedule(() -> {
			try {
				client.setScreen(ConfigScreen.create(null));
			} catch (Throwable failed) {
				Aletheia.LOGGER.warn("could not open the settings screen", failed);
				source.sendFeedback(Component.literal("Could not open the settings -- try the mod list "
						+ "instead.").withStyle(ChatFormatting.RED));
			}
		});
	}

	/**
	 * Prints the run clock, both things it is judging that by, and every personal best on file.
	 *
	 * <p>Two lines rather than one, because the timer asks two separate questions and either can be
	 * the one that went wrong. The <b>dimension</b> says whether you are in a dungeon at all -- a
	 * numbered room like {@code telos:dungeon/2} is one -- and the <b>area</b> the server's ribbon
	 * names is what the run gets filed under. A clock that will not start is the first line's fault;
	 * a run with no personal best beside it is the second's.
	 */
	private static void dungeonTimer(FabricClientCommandSource source) {
		if (DungeonTimer.running()) {
			String state = DungeonTimer.finished()
					? "finished in " + CooldownText.asClock(DungeonTimer.elapsedSeconds())
							+ (DungeonTimer.finishedWasBest() ? " -- a new best" : "")
					: "running, " + CooldownText.asClock(DungeonTimer.elapsedSeconds());
			String named = DungeonTimer.key().isEmpty() ? "Unnamed run" : DungeonTimer.key();
			source.sendFeedback(Component.literal(named + ": " + state)
					.withStyle(ChatFormatting.GREEN));
		} else {
			source.sendFeedback(Component.literal("No run being timed.").withStyle(ChatFormatting.GRAY));
		}

		source.sendFeedback(Component.literal("You are in " + DungeonTimer.currentDimension()
				+ (DungeonTimer.inDungeon() ? " -- a dungeon" : " -- not a dungeon"))
				.withStyle(ChatFormatting.GRAY));

		String area = DungeonTimer.currentArea();
		source.sendFeedback(Component.literal("The server calls this area "
				+ (area.isEmpty() ? "nothing it has said yet -- runs here cannot be filed" : area))
				.withStyle(ChatFormatting.GRAY));

		if (!DungeonTimer.filterTerms().isEmpty()) {
			source.sendFeedback(Component.literal("Also counting as a dungeon: "
					+ String.join(", ", DungeonTimer.filterTerms()))
					.withStyle(ChatFormatting.GRAY));
		}

		splits(source);

		Map<String, Integer> bests = DungeonBests.all();
		if (bests.isEmpty()) {
			source.sendFeedback(Component.literal("No personal bests yet -- they are taken from the "
					+ "server's own clear line.").withStyle(ChatFormatting.DARK_GRAY));
			return;
		}
		source.sendFeedback(Component.literal("Personal bests").withStyle(ChatFormatting.WHITE));
		bests.forEach((key, seconds) -> source.sendFeedback(
				Component.literal(" " + key + "  " + CooldownText.asClock(seconds))
						.withStyle(ChatFormatting.GRAY)));
	}

	/**
	 * The splits of the run in progress, in the readout's own order.
	 *
	 * <p>Printed here rather than left to the readout because the readout has no room to say <i>why</i>
	 * a dungeon shows no stages still to come: it has no table. That is the line worth having when
	 * the splits look thinner than expected.
	 */
	private static void splits(FabricClientCommandSource source) {
		List<DungeonSplits.Row> rows = DungeonSplits.rows();
		if (rows.isEmpty()) {
			if (DungeonTimer.running()) {
				source.sendFeedback(Component.literal("No splits yet.").withStyle(ChatFormatting.DARK_GRAY));
			}
			return;
		}

		source.sendFeedback(Component.literal("Splits" + (DungeonSplits.hasRoute()
				? "" : " -- no boss list for this dungeon, so only what has fallen is listed"))
				.withStyle(ChatFormatting.WHITE));
		for (DungeonSplits.Row row : rows) {
			String time = row.done() ? CooldownText.asClock(row.seconds()) : "--";
			String against = "";
			if (row.best()) {
				against = "  a new best";
			} else if (row.delta().isPresent() && row.delta().getAsInt() != 0) {
				int behind = row.delta().getAsInt();
				against = "  " + (behind > 0 ? "+" : "-") + CooldownText.asClock(Math.abs(behind));
			}
			source.sendFeedback(Component.literal(" " + row.label() + "  " + time + against)
					.withStyle(row.done() ? ChatFormatting.GRAY : ChatFormatting.DARK_GRAY));
		}
	}

	/**
	 * Prints what the server has been putting on the action bar, so a hide filter can be written
	 * without having to guess at the exact wording.
	 */
	private static void actionBar(FabricClientCommandSource source) {
		List<String> recent = ActionBar.recentMessages();

		source.sendFeedback(Component.literal("Recent action bar messages (newest first)")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		if (recent.isEmpty()) {
			source.sendFeedback(Component.literal(" nothing seen yet -- move around and try again")
					.withStyle(ChatFormatting.GRAY));
			return;
		}

		for (String message : recent) {
			source.sendFeedback(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(message).withStyle(ChatFormatting.WHITE)));
		}
		source.sendFeedback(Component.literal("Copy a distinctive word into \"Only hide lines containing\".")
				.withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Lists what is standing near you, so the thing in your way can be named and then filtered out.
	 *
	 * <p>Prints the strings {@link dev.landofif.aletheia.world.Props} matches a filter against, and
	 * nothing else: guessing at what a prop is called is exactly the problem this exists to solve.
	 */
	private static int props(FabricClientCommandSource source, int blocks) {
		// The props are on the mob, so the mob is what to measure from -- and a tight circle round it
		// beats a wide one round you. Only when there is no mob does the search fall back to yourself.
		Entity mob = PropScan.nearestMob();
		int range = blocks > 0
				? blocks
				: (mob == null ? PropScan.DEFAULT_RANGE : PropScan.TARGET_RANGE);
		return props(source, mob, range);
	}

	private static int props(FabricClientCommandSource source, Entity centre, int range) {
		PropScan.Report report = PropScan.scan(centre, range);

		source.sendFeedback(Component.literal(report.centre().isEmpty()
						? "Things within " + range + " blocks of you (nearest first)"
						: "Things within " + range + " blocks of " + report.centre() + " (nearest first)")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		if (report.rows().isEmpty()) {
			source.sendFeedback(Component.literal(" nothing in range -- stand next to it and try again, "
					+ "or widen the search with /aletheia props 24").withStyle(ChatFormatting.GRAY));
			return 1;
		}

		for (PropScan.Group group : report.rows()) {
			source.sendFeedback(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(String.format("%.1fm  ", group.nearest()))
							.withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(group.label()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(group.count() > 1 ? "  x" + group.count() : "")
							.withStyle(ChatFormatting.GRAY))
					// Say when a row stands for more than one model, so a folded prop does not look
					// like a single thing that happens to have been spawned eighteen times.
					.append(Component.literal(group.parts() > 1 ? "  (" + group.parts() + " parts)" : "")
							.withStyle(ChatFormatting.DARK_GRAY)));
		}

		// Silence about a truncated list is how a scan sends you looking for something it did not print.
		if (report.dropped() > 0) {
			source.sendFeedback(Component.literal(" ...and " + report.dropped() + " more kinds further "
					+ "out -- narrow the search with /aletheia props 3").withStyle(ChatFormatting.YELLOW));
		}

		source.sendFeedback(Component.literal("Copy a distinctive word into \"Types or models to hide\" "
				+ "under World, then tick \"Hide props standing in the world\".")
				.withStyle(ChatFormatting.GRAY));
		return 1;
	}

	/**
	 * Lists every piece of text the server currently has on screen and which channel it arrived
	 * through, so a readout that is not the action bar can be found rather than guessed at.
	 *
	 * <p>Lines drawn in a font from the server's resource pack are called out: that is what a
	 * shader-positioned HUD element looks like from here. See {@link ServerHud}.
	 */
	private static void hudScan(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Server HUD scan")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		List<ServerHud.Line> lines = ServerHud.snapshot();
		if (lines.isEmpty()) {
			source.sendFeedback(Component.literal(" Nothing on screen right now -- run this while the "
					+ "readout is visible.").withStyle(ChatFormatting.GRAY));
			return;
		}

		boolean anyCustom = false;
		for (ServerHud.Line line : lines) {
			boolean custom = line.custom();
			anyCustom |= custom;

			source.sendFeedback(Component.literal(" " + line.source() + ": ").withStyle(ChatFormatting.GRAY)
					.append(Component.literal(ChatText.normalize(line.text().getString()))
							.withStyle(custom ? ChatFormatting.YELLOW : ChatFormatting.WHITE)));

			if (!custom) {
				continue;
			}
			// Split by font: the server packs several unrelated fields into one component, and each
			// font is a separate readout landing in a different corner.
			for (ServerHud.Run run : line.runs()) {
				String value = ChatText.normalize(run.text());
				source.sendFeedback(Component.literal("   " + run.font() + " = ")
						.withStyle(ChatFormatting.DARK_AQUA)
						.append(Component.literal(value.isEmpty() ? "(pictures only)" : "\"" + value + "\"")
								.withStyle(value.isEmpty() ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE)));

				// A glyph that draws a picture -- a panel, a ribbon, a bar segment -- is worth naming,
				// since its texture is what you would put in the hide filter.
				for (Map.Entry<Integer, String> picture : run.pictures().entrySet()) {
					source.sendFeedback(Component.literal(String.format("     U+%04X ", picture.getKey()))
							.withStyle(ChatFormatting.DARK_GRAY)
							.append(Component.literal(picture.getValue()).withStyle(ChatFormatting.AQUA)));
				}
			}
		}

		source.sendFeedback(anyCustom
				? Component.literal("Yellow lines use a resource-pack font -- that is the server's HUD, and "
						+ "the channel named beside it is what carries it. Put a word from one of the font "
						+ "names into \"Parts to hide\" to blank that part out.").withStyle(ChatFormatting.GRAY)
				: Component.literal("No resource-pack fonts on screen. If the readout is visible, it is not "
						+ "coming from any of the channels above.").withStyle(ChatFormatting.GRAY));
	}

	/**
	 * Dumps whatever is in the boots slot, tracked or not. Deliberately reports on an unrecognised
	 * item too -- "not equipped" on its own gives you nothing to act on.
	 */
	private static void gift(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Nature's Gift").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		ItemStack boots = NaturesGift.bootsStack();
		if (boots.isEmpty()) {
			source.sendFeedback(Component.literal(" Your boots slot is empty.").withStyle(ChatFormatting.RED));
			timer(source);
			return;
		}

		boolean tracked = NaturesGift.isEquipped();
		line(source, "Tracked", tracked ? "yes" : "no", tracked);
		line(source, "Item name", NaturesGift.lastItemName(), true);

		// The name is drawn in the server's custom font, so show what the characters really are.
		line(source, "Name codepoints", describe(NaturesGift.lastItemName()), true);

		String filter = AletheiaConfig.giftNameFilter;
		line(source, "Name filter",
				filter == null || filter.isBlank() ? "(empty -- any ability boots)" : filter,
				true);

		int total = NaturesGift.lastTotalSeconds();
		line(source, "Cooldown stat in lore", total > 0 ? total + "s" : "NOT FOUND", total > 0);

		if (total <= 0) {
			source.sendFeedback(Component.literal(" No \"Cooldown\" line found in the lore below -- that is "
					+ "what identifies an ability piece.").withStyle(ChatFormatting.GRAY));
		} else if (!tracked) {
			source.sendFeedback(Component.literal(" The name filter above is excluding this item. Clear it "
					+ "in the settings.").withStyle(ChatFormatting.GRAY));
		}
		timer(source);

		List<String> lore = NaturesGift.lastLoreLines();
		if (lore.isEmpty()) {
			source.sendFeedback(Component.literal(" The item has no lore.").withStyle(ChatFormatting.GRAY));
			return;
		}

		source.sendFeedback(Component.literal(" Lore lines:").withStyle(ChatFormatting.GRAY));
		for (String loreLine : lore) {
			source.sendFeedback(Component.literal("  \u2022 ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(loreLine).withStyle(ChatFormatting.WHITE)));
		}
	}

	/**
	 * Reports on both hands, matched or not.
	 *
	 * <p>"Nothing is showing" has two very different causes here -- the cloak not being recognised, and
	 * its cooldown not reaching the client -- so both hands are printed with the model id the filter is
	 * matched against, which is the thing to copy into the settings if the default ever stops fitting.
	 */
	private static void burn(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Afterburner").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		String filter = AletheiaConfig.burnItemFilter;
		line(source, "Item filter",
				filter == null || filter.isBlank() ? "(empty -- any ability item)" : filter,
				true);
		line(source, "Fire lands after", CooldownText.asTenths(AletheiaConfig.burnDelayTenths), true);

		List<Afterburner.Held> hands = Afterburner.hands();
		if (hands.isEmpty()) {
			source.sendFeedback(Component.literal(" No player.").withStyle(ChatFormatting.RED));
			return;
		}

		for (Afterburner.Held hand : hands) {
			if (hand.name().isEmpty()) {
				line(source, hand.slot(), "empty", false);
				continue;
			}
			line(source, hand.slot(), hand.name() + (hand.matched() ? "  <- counting for this" : ""),
					hand.matched());
			line(source, "  model", hand.model().isEmpty() ? "(none)" : hand.model(), !hand.model().isEmpty());
			line(source, "  cooldown now", String.format("%.1f%% left", hand.percent() * 100f), true);
		}

		boolean matched = hands.stream().anyMatch(Afterburner.Held::matched);
		if (!matched) {
			source.sendFeedback(Component.literal(" Neither hand holds the ability. Hold the cloak, or "
					+ "copy the model id above into \"Ability item\" in the settings.")
					.withStyle(ChatFormatting.GRAY));
		}

		int total = Afterburner.lastTotalSeconds();
		line(source, "Its own cooldown", total > 0 ? total + "s" : "not stated in the lore", total > 0);
		line(source, "Reading", Afterburner.statusLine(), true);
		line(source, "Count", Afterburner.counting() ? "running" : "not running", true);

		String note = Afterburner.lastCount();
		if (!note.isEmpty()) {
			line(source, "Count last", note, true);
		}
		long sinceFire = Afterburner.secondsSinceFire();
		if (sinceFire >= 0L) {
			line(source, "Last fire", sinceFire + "s ago", true);
		}

		if (!matched) {
			source.sendFeedback(Component.literal(" /aletheia burn start counts by hand -- bind it to the "
					+ "key you cast with and the readout works either way.").withStyle(ChatFormatting.GRAY));
		}

		List<String> lore = Afterburner.lastLoreLines();
		if (!lore.isEmpty()) {
			source.sendFeedback(Component.literal(" Lore lines:").withStyle(ChatFormatting.GRAY));
			for (String loreLine : lore) {
				source.sendFeedback(Component.literal("  \u2022 ").withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(loreLine).withStyle(ChatFormatting.WHITE)));
			}
		}
	}

	/**
	 * Every reading the player list is carrying, by the name to put in the setting.
	 *
	 * <p>This is the other half of the stats setting: the names are the server's -- they come from the
	 * files behind the icons it draws -- so the only honest way to offer them is to print what was
	 * actually found and let you pick from it.
	 */
	private static void stats(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Player stats").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		// Read here and now: the ticker leaves the panel alone while the readout is switched off, and
		// this command is most useful precisely when it is -- it is how you find out what to switch on.
		PlayerStats.refresh();

		List<PlayerStats.Reading> readings = PlayerStats.all();
		if (readings.isEmpty()) {
			source.sendFeedback(Component.literal(" Nothing found in the player list. Open Tab to check "
					+ "there is a stat panel there at all -- if there is, /aletheia hudscan shows what it "
					+ "is made of.").withStyle(ChatFormatting.RED));
			return;
		}

		source.sendFeedback(Component.literal(" Found " + readings.size()
				+ " -- the name on the left is what goes in the setting:").withStyle(ChatFormatting.GRAY));
		for (PlayerStats.Reading reading : readings) {
			source.sendFeedback(Component.literal("  " + reading.name() + " ")
					.withStyle(ChatFormatting.GREEN)
					.append(Component.literal("= " + reading.value()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal("  (" + reading.source() + ")").withStyle(ChatFormatting.DARK_GRAY)));
		}

		String picked = AletheiaConfig.statsPicked;
		line(source, "Showing", picked == null || picked.isBlank() ? "(everything found)" : picked, true);

		String line = PlayerStats.statusLine();
		if (line.isBlank()) {
			source.sendFeedback(Component.literal(" Nothing on screen: none of the names picked above "
					+ "matched what was found.").withStyle(ChatFormatting.RED));
			return;
		}
		source.sendFeedback(Component.literal(" On screen:").withStyle(ChatFormatting.GRAY));
		for (String row : line.split("\n")) {
			source.sendFeedback(Component.literal("  " + row).withStyle(ChatFormatting.WHITE));
		}
	}

	/**
	 * Writes everything near you that could be carrying the boss's state to the game log, and says in
	 * chat how much it found.
	 *
	 * <p>The report is far too long for chat, and the point of it is comparing two runs -- one taken
	 * while the boss is untouchable, one while it is not -- which is much easier in a file.
	 */
	private static int mobScan(FabricClientCommandSource source, String note) {
		int found = MobScan.dump(note.isBlank() ? "(no note)" : note);
		source.sendFeedback(Component.literal("Wrote " + found + " named entities and every boss bar to "
						+ "the game log (logs/latest.log). Run it again with a different note while the boss "
						+ "is in the other state, then compare the two.")
				.withStyle(ChatFormatting.GREEN));
		return 1;
	}

	/**
	 * Every boss bar on screen and whether the phase tracker recognises it -- the bar's name is a
	 * picture, so this prints the texture it is matched on rather than an empty string.
	 *
	 * <p>It prints each bar's <b>colour</b> as well, which is the one thing on a boss bar you cannot
	 * see in game: the pack draws all seven the same. That is what the invulnerability readout hangs
	 * on, so it is what to check when the readout never appears.
	 */
	private static void boss(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Boss bars").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		List<BossPhaseTracker.Bar> bars = BossPhaseTracker.bars();
		if (bars.isEmpty()) {
			source.sendFeedback(Component.literal(" No boss bars on screen.").withStyle(ChatFormatting.GRAY));
			return;
		}

		// Only the first bar that counts as a boss is followed, so only that one is labelled as such.
		boolean followed = false;
		for (BossPhaseTracker.Bar bar : bars) {
			line(source, bar.known() ? bar.fight() : "not a fight with phases",
					BossPhases.asPercent(bar.progress()), bar.known());
			source.sendFeedback(Component.literal("   matched on: ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(bar.identity().isEmpty() ? "(nothing -- no text, no pictures)" : bar.identity())
							.withStyle(ChatFormatting.WHITE)));

			// The colour here is only ever information: Hierophant goes invulnerable behind a red bar
			// and the Cog Sentinel stays hittable behind a blue one, which is why the readout below
			// reads the bars over the mobs instead. What these bars do decide is whether this counts
			// as a boss fight at all.
			String watched = bar.boss()
					? " · a boss bar" + (followed ? "" : " · counts as a boss fight")
					: " · the server's HUD, not a fight";
			followed |= bar.boss();
			source.sendFeedback(Component.literal("   colour: ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(bar.colour()).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(watched).withStyle(ChatFormatting.DARK_GRAY)));
		}

		source.sendFeedback(Component.literal("A fight with phases shows its name in green. If one is red, "
				+ "copy the texture into BossPhases.").withStyle(ChatFormatting.GRAY));

		reminders(source);
		ourBar(source);
		mobBars(source);
	}

	/**
	 * Whether the ambush and deathmark calls apply to what is on screen.
	 *
	 * <p>Worth its own line because the answer is nearly always "this is not one of the eleven", and
	 * that is indistinguishable from a broken match: both are silence. This says which of the two it is
	 * while the fight is still up.
	 */
	private static void reminders(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Ambush and deathmark")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		if (!AletheiaConfig.showReminderTitles) {
			line(source, "Called here", "no -- switched off under Boss phases", false);
			return;
		}

		String fight = BossReminders.fightName();
		line(source, "Called here", fight == null
				? "no -- not one of the endgame dungeon bosses"
				: "yes -- " + fight, fight != null);
		if (fight != null) {
			source.sendFeedback(Component.literal("   at " + AletheiaConfig.ambushAt + "% and "
					+ AletheiaConfig.deathmarkAt + "%, once each per fight").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	/** The mod's own bar: whether it has a fight to draw, and what it has taken off the screen. */
	private static void ourBar(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Our own bar").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		boolean drawing = AletheiaConfig.enabled && AletheiaConfig.showBossBar;
		line(source, "Drawing it", drawing ? "yes" : "no -- switched off under Boss bar", drawing);
		List<BossBar.Fight> fights = BossBar.fights();
		line(source, "Following", BossBar.live()
						? (fights.size() == 1 ? "one bar" : fights.size() + " bars, drawn side by side")
						: "no fight on screen",
				BossBar.live());
		if (BossBar.live()) {
			for (BossBar.Fight fight : fights) {
				// A fight that ends above an empty bar is drawn rescaled, so both readings are printed:
				// this is the line to check when the bar disagrees with the server's own.
				String note = fight.endsEarly()
						? " · the server says " + BossPhases.asPercent(fight.sent())
								+ ", and it dies at " + BossPhases.asPercent(fight.endsAt())
						: "";
				source.sendFeedback(Component.literal("   \"" + fight.name() + "\" at ")
						.withStyle(ChatFormatting.DARK_GRAY)
						.append(Component.literal(BossPhases.asPercent(fight.progress()))
								.withStyle(ChatFormatting.WHITE))
						.append(Component.literal(note).withStyle(ChatFormatting.DARK_GRAY)));
			}
		}
		line(source, "Server's bar", AletheiaConfig.hideVanillaBossBar
						? (BossBar.hidingAnything() ? "hidden -- the fight's only, never the HUD's" : "nothing to hide")
						: "left up as well",
				true);
	}

	/**
	 * The bars floating over the mobs around you, which is where invulnerability actually lives. The
	 * boss bar at the top of the screen is not where the colour is read, but one has to be up for any
	 * of this to happen at all -- that is what keeps the readout off the wildlife.
	 */
	private static void mobBars(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Bars over mobs").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(source, "Invulnerable when the fill is", Vulnerability.wantedColourName()
				+ (AletheiaConfig.invulnerableLooseMatch ? ", or any clearly blue fill" : " exactly"), true);
		line(source, "Half damage when the fill is",
				(Vulnerability.halfColourName().isEmpty() ? "(nothing exact set)" : Vulnerability.halfColourName())
						+ (AletheiaConfig.invulnerableLooseMatch
								? ", or any purple -- a fill with red in it as well as blue"
								: " exactly -- the loose match is off, so nothing else counts"),
				AletheiaConfig.invulnerableLooseMatch || !Vulnerability.halfColourName().isEmpty());

		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		// Every gate, spelled out, since "nothing is showing" is nearly always one of them.
		boolean fight = Vulnerability.gatesPass();
		line(source, "Counts as a boss fight",
				fight ? "yes -- a boss bar is up" : "no -- nothing is read or shown without a boss bar", fight);
		line(source, "Only following mobs", switch (AletheiaConfig.invulnerableMinTier) {
			case 1 -> "miniboss or better";
			case 2 -> "boss only";
			default -> "of any grade";
		}, true);
		line(source, "Dimension", client.player.level().dimension().identifier().toString(), true);
		if (!AletheiaConfig.invulnerableDimensionFilter.isBlank()) {
			line(source, "Dimension filter", "\"" + AletheiaConfig.invulnerableDimensionFilter + "\"", true);
		}

		List<MobBars.Bar> bars = MobBars.nearby(client.player, client.level);
		if (bars.isEmpty()) {
			source.sendFeedback(Component.literal(" No mob bars in range. They are entities, so get closer, "
					+ "or raise \"Look this far for the boss\".").withStyle(ChatFormatting.GRAY));
			return;
		}

		for (MobBars.Bar bar : bars) {
			source.sendFeedback(Component.literal(" " + String.format("%.0f", bar.distance()) + "m ")
					.withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(String.format("#%06X", bar.colour())).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(" " + tierName(bar.tier()) + " ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(bar.name().isEmpty() ? "(no name)" : bar.name())
							.withStyle(ChatFormatting.GRAY)));
		}

		line(source, "Following", Vulnerability.watching()
						? Vulnerability.lastColour() + " on \"" + Vulnerability.followedName() + "\""
								+ " -- " + Vulnerability.stateName()
						: "nothing yet",
				Vulnerability.watching() && !Vulnerability.isInvulnerable());
	}

	/** How good a mob a bar belongs to, as {@link MobBars} ranks them. */
	private static String tierName(int tier) {
		return switch (tier) {
			case 2 -> "boss";
			case 1 -> "miniboss";
			case 0 -> "ordinary";
			default -> "unknown";
		};
	}

	/**
	 * What the potion counter is reading and where from -- the font it matched is the thing to check
	 * when the number stops appearing, since a pack update is all it takes to rename one.
	 */
	/**
	 * What Magnificat circles are on the floor and what is being drawn for them -- above all the two
	 * radii, since the measured one is the whole of why the ring does not need a number typed into it.
	 */
	private static void domain(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Magnificat domain")
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		String filter = AletheiaConfig.domainFilter;
		boolean filtering = filter != null && !filter.isBlank();
		line(source, "Circles to replace",
				filtering ? "\"" + filter + "\"" : "(empty -- nothing is replaced)", filtering);
		line(source, "Ring", AletheiaConfig.showDomainRing ? "drawn" : "off", AletheiaConfig.showDomainRing);
		line(source, "Server's circle",
				AletheiaConfig.hideDomainCircle ? "hidden" : "left drawn", AletheiaConfig.hideDomainCircle);
		line(source, "Radius setting",
				AletheiaConfig.domainRadius > 0
						? AletheiaConfig.domainRadius + " blocks"
						: "0 -- measured from the circle",
				true);

		List<Domains.Circle> circles = Domains.found();
		if (circles.isEmpty()) {
			source.sendFeedback(Component.literal(" Nothing found right now -- run this while a domain is "
					+ "on the floor. If one is and this is still empty, /aletheia props will name it.")
					.withStyle(ChatFormatting.GRAY));
			return;
		}

		source.sendFeedback(Component.literal("Found " + circles.size()
				+ (circles.size() == 1 ? " circle" : " circles") + " (nearest first)")
				.withStyle(ChatFormatting.WHITE));
		for (Domains.Circle circle : circles) {
			source.sendFeedback(Component.literal(" \u2022 ").withStyle(ChatFormatting.DARK_GRAY)
					.append(Component.literal(String.format("%.1fm out  ", circle.distance()))
							.withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(circle.within() ? "inside" : "outside")
							.withStyle(circle.within() ? ChatFormatting.GREEN : ChatFormatting.RED))
					.append(Component.literal(String.format("  r=%.1f", circle.radius()))
							.withStyle(ChatFormatting.WHITE))
					// Printed even when the setting is overriding it, since the point of the line is to
					// show whether the measurement agrees with the fire on the floor.
					.append(Component.literal(circle.measured() > 0.0
							? String.format("  (measured %.1f)", circle.measured())
							: "  (scale not read yet)")
							.withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal("  " + circle.model()).withStyle(ChatFormatting.GRAY)));
		}
	}

	private static void pots(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("HP pots").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		boolean reading = HpPots.has();
		line(source, "Reading", HpPots.statusLine(), reading);
		line(source, "Live", reading ? "yes" : "no -- the server is not sending it right now", reading);
		line(source, "Font filter", AletheiaConfig.potsFontFilter, true);
		line(source, "Matched font", HpPots.lastFont().isEmpty() ? "(never found)" : HpPots.lastFont(),
				!HpPots.lastFont().isEmpty());
		// The count is a picture glyph, so the texture is the reading -- hud_hp5.png is five left.
		line(source, "Counter picture",
				HpPots.lastTexture().isEmpty() ? "(none -- the field is not a picture)" : HpPots.lastTexture(),
				!HpPots.lastTexture().isEmpty());
		if (!HpPots.lastText().isEmpty()) {
			line(source, "That part also reads", "\"" + HpPots.lastText() + "\"", true);
		}

		if (!reading) {
			source.sendFeedback(Component.literal(" Run /aletheia hudscan while the counter is on screen and "
					+ "copy a word of its font name into \"Read the part whose font contains\".")
					.withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * Where the Dreadwood run count stands and what it is watching -- above all the dimension id,
	 * since that is what the reset hangs on and it cannot be guessed from outside the dungeon.
	 */
	private static void dreadwood(FabricClientCommandSource source) {
		source.sendFeedback(Component.literal("Dreadwood Thicket").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

		DreadwoodRun.Progress progress = DreadwoodRun.progress();
		line(source, "Rooms finished", progress.format(), true);

		List<String> rooms = DreadwoodRun.clearedRooms();
		line(source, "Which ones", rooms.isEmpty() ? "(none yet)" : String.join(", ", rooms), true);
		line(source, "Last room seen", DreadwoodRun.lastRoom().isEmpty() ? "(none)" : DreadwoodRun.lastRoom(), true);

		String dimension = DreadwoodRun.currentDimension();
		line(source, "Dimension", dimension.isEmpty() ? "(none)" : dimension, true);

		String filter = AletheiaConfig.dreadwoodDimensionFilter;
		boolean filtering = filter != null && !filter.isBlank();
		line(source, "Dungeon filter",
				filtering ? "\"" + filter + "\"" : "(empty -- the count only ends on a timeout or a relog)",
				filtering);
		if (filtering) {
			line(source, "Counts as in the dungeon", DreadwoodRun.insideDungeon() ? "yes" : "no",
					DreadwoodRun.insideDungeon());
		}

		String reset = DreadwoodRun.lastReset();
		if (!reset.isEmpty()) {
			line(source, "Count last cleared", reset, true);
		}

		if (filtering && !DreadwoodRun.insideDungeon() && !dimension.isEmpty()) {
			source.sendFeedback(Component.literal(" Run this again from inside the Thicket: if the dimension "
					+ "there does not contain the filter, copy a word of it into \"Dungeon dimension "
					+ "contains\".").withStyle(ChatFormatting.GRAY));
		}
	}

	/**
	 * The state of the mod's own countdown, next to what the server is saying -- the two disagreeing
	 * is normal (the server drops its cooldown when the boots come off) and is the whole reason the
	 * timer exists, so both are worth printing side by side.
	 */
	private static void timer(FabricClientCommandSource source) {
		int remaining = NaturesGift.remainingSeconds();
		line(source, "Reading", NaturesGift.statusLine(), true);
		line(source, "Own timer", remaining > 0 ? remaining + "s left" : "not running", true);
		line(source, "Server says",
				String.format("%.1f%% left", NaturesGift.lastCooldownPercent() * 100f),
				true);

		if (remaining > 0 && !NaturesGift.isEquipped()) {
			source.sendFeedback(Component.literal(" Still counting for boots you are not wearing -- the "
					+ "cooldown carries on whether or not they are on.").withStyle(ChatFormatting.GRAY));
		}

		int others = NaturesGift.otherCounts();
		if (others > 0) {
			line(source, "Other pieces counting", Integer.toString(others), true);
		}

		line(source, "Dimension",
				NaturesGift.currentDimension().isEmpty() ? "(none)" : NaturesGift.currentDimension(),
				true);

		String cleared = NaturesGift.lastClear();
		if (!cleared.isEmpty()) {
			line(source, "Timer last cleared", cleared, true);
		}
	}

	/** Renders a string with anything non-ASCII spelled out, so custom-font glyphs are visible. */
	private static String describe(String raw) {
		if (raw == null || raw.isEmpty()) {
			return "(empty)";
		}
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c >= 0x20 && c < 0x7F) {
				out.append(c);
			} else {
				out.append(String.format("\\u%04X", (int) c));
			}
		}
		return out.toString();
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> preview(String name, Supplier<DungeonEvent> event) {
		return ClientCommands.literal(name).executes(context -> {
			ChatWatcher.show(event.get());
			return 1;
		});
	}

	private static void status(FabricClientCommandSource source) {
		ServerData server = Minecraft.getInstance().getCurrentServer();
		String address = server == null || server.ip == null ? "(singleplayer)" : server.ip;

		source.sendFeedback(Component.literal(Aletheia.MOD_NAME).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(source, "Enabled", AletheiaConfig.enabled ? "yes" : "no", AletheiaConfig.enabled);
		line(source, "Server", address, true);

		boolean active = AletheiaConfig.enabled && ChatWatcher.onEnabledServer();
		line(source, "Watching chat", active ? "yes" : "no (see settings)", active);

		line(source, "Server filter",
				AletheiaConfig.restrictToServer ? "on -- \"" + AletheiaConfig.serverAddressFilter + "\"" : "off",
				true);
		line(source, "Strict score matching", AletheiaConfig.strictProgressMatching ? "on" : "off", true);

		// Shown even at zero while the setting is on, because that is the tell: the mixin behind it is
		// require = 0, so a vanilla rename makes it stop applying silently. Still 0 after a few minutes
		// in a dungeon, with the log still spamming, means it no longer injects.
		if (AletheiaConfig.quietPassengerWarnings) {
			long swallowed = PassengerSpam.total();
			line(source, "Passenger warnings quietened", Long.toString(swallowed), swallowed > 0);
		}

		// A live sanity check that the matcher still recognises the canonical line.
		DungeonEvent sample = NeoEdenParser.parse("Your persistence has been recorded. (259/265)", AletheiaConfig.strictProgressMatching);
		line(source, "Matcher self-test", sample == null ? "FAILED" : "ok", sample != null);
	}

	private static void line(FabricClientCommandSource source, String label, String value, boolean good) {
		source.sendFeedback(Component.literal(" " + label + ": ").withStyle(ChatFormatting.GRAY)
				.append(Component.literal(value).withStyle(good ? ChatFormatting.GREEN : ChatFormatting.RED)));
	}

	private static String playerName() {
		var player = Minecraft.getInstance().player;
		return player == null ? "Someone" : player.getName().getString();
	}

	/** The Thicket's rooms, for the previews -- a different one each time so the count moves. */
	private static final String[] TEST_ROOMS = {"Colour", "Wave", "Demon", "Bullet Hell"};
	private static int nextTestRoom;

	private static String nextTestRoom() {
		String room = TEST_ROOMS[nextTestRoom];
		nextTestRoom = (nextTestRoom + 1) % TEST_ROOMS.length;
		return room;
	}

	/** Every phase of every fight in turn, so the preview walks through the lot. */
	private static int nextTestPhase;

	private static BossPhases.Phase nextTestPhase() {
		List<BossPhases.Phase> phases = BossPhases.all().stream().flatMap(boss -> boss.phases().stream()).toList();
		BossPhases.Phase phase = phases.get(nextTestPhase);
		nextTestPhase = (nextTestPhase + 1) % phases.size();
		return phase;
	}

	/** Likewise the Cog Sentinel's stabilisers, counting up so the last one's wording is reachable. */
	private static final int TEST_STABILISERS = 5;
	private static int nextTestStabiliser;

	private static int nextTestStabiliser() {
		nextTestStabiliser = nextTestStabiliser % TEST_STABILISERS + 1;
		return nextTestStabiliser;
	}

	/** Likewise the Shadowlands announcers, one per run, so a repeat guard cannot swallow them. */
	private static final String[] TEST_MOBS = {"Defender", "Reaper", "Herald", "Warden"};
	private static int nextTestMob;

	private static String nextTestMob() {
		String mob = TEST_MOBS[nextTestMob];
		nextTestMob = (nextTestMob + 1) % TEST_MOBS.length;
		return mob;
	}
}
