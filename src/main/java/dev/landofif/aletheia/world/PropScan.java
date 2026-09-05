package dev.landofif.aletheia.world;

import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.mixin.BlockDisplayAccessor;
import dev.landofif.aletheia.mixin.ItemDisplayAccessor;
import dev.landofif.aletheia.mixin.TextDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Names whatever is standing in front of you, so a filter for {@link Props} can be read off rather
 * than guessed at.
 *
 * <p>{@link dev.landofif.aletheia.serverhud.MobScan} answers a different question -- it lists things
 * <i>wearing a name</i>, because it is looking for a boss's health bar. A prop generally has no name
 * at all, so it never appears there. This lists everything in range whether it is named or not, and
 * prints the strings a filter is actually matched against: the entity type, and the item model, item
 * or block a display is holding.
 *
 * <p>It centres on the <b>nearest mob</b> rather than on you, because that is where props are: they
 * are spawned on top of the thing they belong to, so measuring from the mob puts the things piled on
 * it at the top of the list and leaves the rest of the arena out of it.
 *
 * <p><b>One prop is many entities</b>, which is the thing that makes a plain listing useless. Telos
 * builds a prop out of one display per part and animates it with one display per frame: the Arcanist
 * monolith alone arrives as {@code arcanist_orb_n1a/orb}, {@code /body}, {@code /tophalf},
 * {@code /rune} and seven separate tentacle segments. Listed as they come, a single orb is ten rows
 * and everything else falls off the end of the report. So rows are folded to the model's
 * <b>folder</b> -- see {@link #stem} -- which is one row per prop and is also the word worth
 * filtering on.
 */
public final class PropScan {
	private PropScan() {
	}

	/** Close enough to be what is in your way, rather than everything in the arena. */
	public static final int DEFAULT_RANGE = 16;

	/**
	 * How far to look for the mob to scan around, and how far around it to then scan.
	 *
	 * <p>Props stack <b>on</b> whatever they belong to, so once the centre is the mob rather than you,
	 * a few blocks is the whole of it -- and a tight circle around the mob is a far shorter list than
	 * a wide one around you.
	 */
	private static final double TARGET_SEARCH = 24.0;
	public static final int TARGET_RANGE = 6;

	/** Chat keeps a hundred lines and these are the interesting ones, so the tail is dropped. */
	private static final int MAX_ROWS = 20;

	/** Long enough to recognise a name by, short enough to leave the row readable. */
	private static final int MAX_NAME = 20;

	/**
	 * One kind of thing in range.
	 *
	 * @param label   what it is, written as the strings a filter is matched against
	 * @param nearest how far off the closest one is
	 * @param count   how many of them there are
	 * @param parts   how many different models were folded into this row, {@code 1} for none
	 */
	public record Group(String label, double nearest, int count, int parts) {
	}

	/**
	 * @param rows   the kinds worth printing, nearest first
	 * @param kinds  how many kinds were found in all, so a truncated list can say so
	 * @param centre what the distances are measured from, or empty when they are measured from you
	 */
	public record Report(List<Group> rows, int kinds, String centre) {

		public int dropped() {
			return kinds - rows.size();
		}
	}

	/**
	 * The mob to scan around: the nearest living thing that is not you.
	 *
	 * <p>The crosshair is no good for this. {@code crosshairPickEntity} only picks inside your reach,
	 * about three blocks, and the mob you are hitting is generally further off than that -- the fire
	 * this was written against sat at four and a half.
	 *
	 * <p>Living is the whole test, and it is enough: the props are displays, interactions and effect
	 * clouds, none of which are alive, so the nearest living thing in an arena is the thing they are
	 * piled on.
	 *
	 * @return the mob, or {@code null} when nothing living is near enough to be worth centring on
	 */
	public static Entity nearestMob() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return null;
		}

		Entity nearest = null;
		double nearestDistance = TARGET_SEARCH;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == player || !(entity instanceof LivingEntity)) {
				continue;
			}
			double distance = entity.distanceTo(player);
			if (distance < nearestDistance) {
				nearest = entity;
				nearestDistance = distance;
			}
		}
		return nearest;
	}

	/**
	 * @param centre what to measure from -- a mob from {@link #nearestMob()}, or {@code null} for you
	 * @return what is within {@code range} blocks of it, nearest first, folded by kind
	 */
	public static Report scan(Entity centre, double range) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) {
			return new Report(List.of(), 0, "");
		}
		Entity from = centre == null ? player : centre;

		Map<String, Tally> found = new LinkedHashMap<>();
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == player) {
				continue;
			}
			double distance = entity.distanceTo(from);
			if (distance > range) {
				continue;
			}
			Kind kind = kindOf(entity);
			found.computeIfAbsent(kind.label(), key -> new Tally()).add(distance, kind.part());
		}

		List<Group> groups = new ArrayList<>(found.size());
		found.forEach((label, tally) -> groups.add(tally.toGroup(label)));
		groups.sort(Comparator.comparingDouble(Group::nearest));
		return new Report(
				groups.size() > MAX_ROWS ? List.copyOf(groups.subList(0, MAX_ROWS)) : groups,
				groups.size(),
				centre == null ? "" : kindOf(centre).label());
	}

	/**
	 * What one entity is.
	 *
	 * @param label the row it belongs on, folded
	 * @param part  the exact model behind it, so a folded row can say how many it stands for
	 */
	private record Kind(String label, String part) {
	}

	private static Kind kindOf(Entity entity) {
		String type = Props.typeName(entity.getType());

		if (entity instanceof Display.ItemDisplay display) {
			ItemStack stack = ((ItemDisplayAccessor) display).aletheia$itemStack();
			if (stack.isEmpty()) {
				return new Kind(type + " (holding nothing)", "");
			}
			String item = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
			Identifier model = stack.get(DataComponents.ITEM_MODEL);
			if (model == null) {
				return new Kind(type + "  item=" + item, "");
			}
			String path = model.getPath();
			return new Kind(type + "  model=" + stem(path) + "  item=" + item, path);
		}

		if (entity instanceof Display.BlockDisplay display) {
			BlockDisplayAccessor block = (BlockDisplayAccessor) display;
			return new Kind(type + "  block="
					+ BuiltInRegistries.BLOCK.getKey(block.aletheia$blockState().getBlock()).getPath(), "");
		}

		String name = readableName(entity);
		return new Kind(name.isEmpty() ? type : type + "  \"" + name + "\"", "");
	}

	/**
	 * A model path folded to the thing it belongs to: everything up to the last {@code /}, so all
	 * eleven parts of {@code arcanist_orb_n1a/tentacle_1_3_cube} and {@code .../orb} come out as
	 * {@code arcanist_orb_n1a/}.
	 *
	 * <p>The folder is the prop. A path without one is an animation instead, numbered per frame
	 * ({@code fire_0}, {@code fire_1}), and there the number comes off.
	 *
	 * <p>Only ever <i>shortens</i> the path, which matters: what is printed is still a substring of
	 * every model it stands for, so it can be pasted straight into the filter and will match them all.
	 */
	private static String stem(String path) {
		int folder = path.lastIndexOf('/');
		if (folder >= 0) {
			return path.substring(0, folder + 1);
		}

		int end = path.length();
		while (end > 0 && Character.isDigit(path.charAt(end - 1))) {
			end--;
		}
		// The number is all that comes off. Taking the trailing "_" with it would leave "fire" standing
		// in for "fire_0", which would quietly cover a "firebolt" alongside it.
		return end == path.length() || end == 0 ? path : path.substring(0, end);
	}

	/**
	 * An entity's name with the decoration taken off.
	 *
	 * <p>Folded to letters and digits because a Telos name is written in the pack's own font: printed
	 * as it stands it is a row of squares, and there is nothing to be gained from that in chat. This
	 * is for recognising the thing, not for filtering on -- {@link Props} deliberately does not match
	 * names.
	 */
	private static String readableName(Entity entity) {
		Component name = entity instanceof Display.TextDisplay display
				? ((TextDisplayAccessor) display).aletheia$text()
				: entity.getCustomName();
		if (name == null) {
			return "";
		}
		String folded = ChatText.lettersAndDigits(name.getString());
		return folded.length() > MAX_NAME ? folded.substring(0, MAX_NAME) + "..." : folded;
	}

	/** Running totals for one kind of thing while the scan walks the world. */
	private static final class Tally {
		private double nearest = Double.MAX_VALUE;
		private int count;
		private final Set<String> parts = new HashSet<>();

		private void add(double distance, String part) {
			nearest = Math.min(nearest, distance);
			count++;
			if (!part.isEmpty()) {
				parts.add(part);
			}
		}

		private Group toGroup(String label) {
			return new Group(label, nearest, count, Math.max(parts.size(), 1));
		}
	}
}
