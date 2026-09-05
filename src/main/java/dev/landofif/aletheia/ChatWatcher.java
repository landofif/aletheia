package dev.landofif.aletheia;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.DungeonEvent;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.dreadwood.DreadwoodRun;
import dev.landofif.aletheia.timer.DungeonTimer;
import dev.landofif.aletheia.ui.Alerts;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Watches incoming chat and turns recognised Neo-Eden lines into on-screen titles. */
public final class ChatWatcher {
	private ChatWatcher() {
	}

	/**
	 * Some servers send the same line twice in quick succession (once as chat, once as an overlay).
	 * Suppress an identical repeat within this window so the title does not visibly restart.
	 */
	private static final long DEDUPE_WINDOW_MILLIS = 400L;

	private static String lastMessage = "";
	private static long lastMessageAt = 0L;

	public static void register() {
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			// Action bar text is handled by the Gui mixin instead -- most of it never reaches this event.
			if (overlay) {
				return;
			}
			handle(message);
		});

		ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) -> {
			if (AletheiaConfig.scanPlayerChat) {
				handle(message);
			}
		});
	}

	/** Entry point for text that did not arrive through the chat events, i.e. the action bar. */
	public static void inspect(Component message) {
		handle(message);
	}

	private static void handle(Component message) {
		if (!AletheiaConfig.enabled || !onEnabledServer()) {
			return;
		}

		String raw = message.getString();
		if (isRepeat(raw)) {
			return;
		}

		DungeonEvent event = NeoEdenParser.parse(
				raw,
				AletheiaConfig.strictProgressMatching,
				NeoEdenParser.splitPhrases(AletheiaConfig.extraIgnoredPhrases));
		if (event == null) {
			return;
		}

		if (AletheiaConfig.debugLogging) {
			Aletheia.LOGGER.info("matched {} in: {}", event, raw);
		}

		show(event);
	}

	/** Renders an event using the current settings. Also used by {@code /aletheia test}. */
	public static void show(DungeonEvent event) {
		switch (event) {
			case DungeonEvent.Progress progress -> {
				if (!AletheiaConfig.showProgressTitle) {
					return;
				}
				Alerts.showTitle(
						progress.format(AletheiaConfig.unknownTotalPlaceholder),
						AletheiaConfig.progressSubtitle,
						AletheiaConfig.colour(AletheiaConfig.progressColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.progressSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.PuzzleStarted puzzle -> {
				if (!AletheiaConfig.showPuzzleStartTitle) {
					return;
				}
				Alerts.showTitle(
						AletheiaConfig.puzzleStartTitleText,
						AletheiaConfig.puzzleStartShowPlayer ? puzzle.player() : null,
						AletheiaConfig.colour(AletheiaConfig.puzzleStartColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.puzzleStartSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.PuzzleSolved puzzle -> {
				if (!AletheiaConfig.showPuzzleTitle) {
					return;
				}
				Alerts.showTitle(
						AletheiaConfig.puzzleTitleText,
						AletheiaConfig.puzzleShowPlayer ? puzzle.player() : null,
						AletheiaConfig.colour(AletheiaConfig.puzzleColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.puzzleSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.BarracksStarted barracks -> {
				if (!AletheiaConfig.showBarracksStartTitle) {
					return;
				}
				Alerts.showTitle(
						AletheiaConfig.barracksStartTitleText,
						AletheiaConfig.barracksStartShowPlayer ? barracks.player() : null,
						AletheiaConfig.colour(AletheiaConfig.barracksStartColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.barracksStartSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.BarracksCleared barracks -> {
				if (!AletheiaConfig.showBarracksTitle) {
					return;
				}
				Alerts.showTitle(
						AletheiaConfig.barracksTitleText,
						AletheiaConfig.barracksShowPlayer ? barracks.player() : null,
						AletheiaConfig.colour(AletheiaConfig.barracksColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.barracksSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.DreadwoodRoomStarted room -> {
				// Told either way: a room being entered is what ends a run that already finished, and
				// it is what keeps the idle timeout from expiring part-way through a slow run.
				DreadwoodRun.started(room.room());
				if (!AletheiaConfig.showDreadwoodStartTitle) {
					return;
				}
				String[] fields = dreadwoodFields(room.room(), room.player(), DreadwoodRun.progress());
				Alerts.showTitle(
						Alerts.fill(AletheiaConfig.dreadwoodStartTitleText, fields),
						Alerts.fill(AletheiaConfig.dreadwoodStartSubtitle, fields),
						AletheiaConfig.colour(AletheiaConfig.dreadwoodStartColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.dreadwoodStartSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.DreadwoodRoomCleared room -> {
				// Counted before the switch below, so turning the title off does not lose track of the run.
				DreadwoodRun.Progress progress = DreadwoodRun.cleared(room.room());
				if (!AletheiaConfig.showDreadwoodClearedTitle) {
					return;
				}
				String[] fields = dreadwoodFields(room.room(), room.player(), progress);
				Alerts.showTitle(
						Alerts.fill(AletheiaConfig.dreadwoodClearedTitleText, fields),
						Alerts.fill(AletheiaConfig.dreadwoodClearedSubtitle, fields),
						AletheiaConfig.colour(AletheiaConfig.dreadwoodClearedColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.dreadwoodClearedSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.ShadowlandsSpawn spawn -> {
				if (!AletheiaConfig.showShadowlandsTitle || spokeRecently(spawn.mob())) {
					return;
				}
				String[] fields = {"mob", spawn.mob().toUpperCase(Locale.ROOT), "line", spawn.line()};
				Alerts.showTitle(
						Alerts.fill(AletheiaConfig.shadowlandsTitleText, fields),
						Alerts.fill(AletheiaConfig.shadowlandsSubtitle, fields),
						AletheiaConfig.colour(AletheiaConfig.shadowlandsColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.shadowlandsSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.CogStabilisers cog -> {
				if (!AletheiaConfig.showCogTitle) {
					return;
				}
				String[] fields = {
						"done", Integer.toString(cog.destroyed()),
						"total", Integer.toString(cog.total()),
				};
				// The last one is the fight opening up, not another number, so it gets its own wording --
				// unless that has been emptied, in which case 5/5 counts like the rest.
				boolean last = cog.allDestroyed() && !AletheiaConfig.cogFinalTitleText.isBlank();
				Alerts.showTitle(
						Alerts.fill(last ? AletheiaConfig.cogFinalTitleText : AletheiaConfig.cogTitleText, fields),
						Alerts.fill(AletheiaConfig.cogSubtitle, fields),
						AletheiaConfig.colour(last ? AletheiaConfig.cogFinalColour : AletheiaConfig.cogColour),
						AletheiaConfig.titleStayTicks);
				if (AletheiaConfig.cogSound) {
					Alerts.playSound();
				}
			}
			case DungeonEvent.DungeonCleared cleared -> {
				// The one event here that puts nothing on screen. Telos' own leaderboard is already up
				// saying the time and whether it beat your best, so all this does is stop the clock on
				// the server's number -- see DungeonTimer.
				DungeonTimer.cleared(cleared.boss(), cleared.seconds());
			}
			case DungeonEvent.CherubimShout shout -> {
				if (!AletheiaConfig.showCherubimTitle) {
					return;
				}
				Alerts.showTitle(
						shout.shout() == DungeonEvent.Shout.SILENCE
								? AletheiaConfig.silenceTitleText
								: AletheiaConfig.enoughTitleText,
						AletheiaConfig.cherubimSubtitle,
						AletheiaConfig.colour(AletheiaConfig.cherubimColour),
						AletheiaConfig.cherubimStayTicks);
				if (AletheiaConfig.cherubimSound) {
					Alerts.playSound();
				}
			}
		}
	}

	/**
	 * The last time each Shadowlands mob put a title up, so one that carries on talking does not keep
	 * announcing its own spawn. Keyed by mob, so two of them arriving together both show.
	 */
	private static final Map<String, Long> lastSpawnTitleAt = new HashMap<>();

	private static boolean spokeRecently(String mob) {
		int seconds = AletheiaConfig.shadowlandsRepeatSeconds;
		long now = System.currentTimeMillis();
		String key = mob.toLowerCase(Locale.ROOT);

		if (seconds > 0) {
			Long last = lastSpawnTitleAt.get(key);
			if (last != null && now - last < seconds * 1000L) {
				return true;
			}
		}
		lastSpawnTitleAt.put(key, now);
		return false;
	}

	/** Room names go in capitals, since every other title here is. */
	private static String[] dreadwoodFields(String room, String player, DreadwoodRun.Progress progress) {
		return new String[] {
				"room", room.toUpperCase(Locale.ROOT),
				"player", player,
				"done", Integer.toString(progress.done()),
				"total", Integer.toString(progress.total()),
		};
	}

	/** @return whether the address filter is satisfied (or not being enforced). */
	public static boolean onEnabledServer() {
		if (!AletheiaConfig.restrictToServer) {
			return true;
		}

		String filter = AletheiaConfig.serverAddressFilter;
		if (filter == null || filter.isBlank()) {
			return true;
		}

		ServerData server = Minecraft.getInstance().getCurrentServer();
		if (server == null || server.ip == null) {
			return false;
		}
		// Folded without building either side: this is asked once per boss bar per frame, since the HUD
		// filter checks it before it does anything else.
		return ChatText.containsIgnoreCase(server.ip, filter);
	}

	private static boolean isRepeat(String raw) {
		long now = System.currentTimeMillis();
		boolean repeat = raw.equals(lastMessage) && now - lastMessageAt < DEDUPE_WINDOW_MILLIS;
		lastMessage = raw;
		lastMessageAt = now;
		return repeat;
	}
}
