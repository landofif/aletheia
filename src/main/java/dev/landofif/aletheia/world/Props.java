package dev.landofif.aletheia.world;

import dev.landofif.aletheia.ChatWatcher;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.mixin.BlockDisplayAccessor;
import dev.landofif.aletheia.mixin.ItemDisplayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which things standing in the world the client should decline to draw.
 *
 * <p>Written for the props a fight leaves in front of you -- the Arcanist monolith, the void
 * whatever-they-are that Maelstrom throws over a mob -- which have one thing in common: they are
 * scenery. Not a mob and not a block, but usually a <b>display entity</b>, whose only job is to hold
 * an item or a block in the air. There is nothing to target and nothing to walk through, only
 * something to see past.
 *
 * <p><b>What a filter is matched against</b> is the entity's type ({@code item_display},
 * {@code armor_stand}) and, for the two kinds of display, the item model, the item, or the block it
 * is showing. Those are all plain ASCII the server picked. <b>Names are deliberately not matched</b>
 * -- Telos writes them in its resource pack's own font, so a name is a run of picture glyphs rather
 * than the word it looks like, which is why {@link dev.landofif.aletheia.afterburner.Afterburner}
 * goes by model id too. {@link PropScan} prints all of it, so the word to filter on can be read off
 * rather than guessed at.
 *
 * <p><b>An empty filter hides nothing.</b> That is the opposite of the action bar and server HUD
 * filters, where empty means everything -- but everything here is every entity in the world, which
 * is not a state anyone would want to arrive in by clearing a text box.
 */
public final class Props {
	private Props() {
	}

	/** The filter the phrases below were split out of, so it is parsed on a change and not per frame. */
	private static String lastFilter = "";
	private static List<String> wanted = List.of();

	/**
	 * Type names, kept because reading one builds a fresh string and this is asked of every entity in
	 * view every frame. Entity types are singletons, so this holds one entry per kind in the world.
	 */
	private static final Map<EntityType<?>, String> TYPE_NAMES = new IdentityHashMap<>();

	/**
	 * Whether anything is being hidden at all.
	 *
	 * <p>Asked first and once per entity per frame, so it is two field reads and nothing else; the
	 * work of looking at the entity only happens after this has said yes.
	 */
	public static boolean hidingAnything() {
		return AletheiaConfig.enabled && AletheiaConfig.hideProps;
	}

	/** @return whether this entity is one of the things being hidden. */
	public static boolean hides(Entity entity) {
		if (!ChatWatcher.onEnabledServer() || entity == Minecraft.getInstance().player) {
			// Never yourself: "player" is a perfectly ordinary word to end up with in a filter, and
			// vanishing in third person is a puzzling way to find that out.
			return false;
		}

		List<String> filters = filters();
		return !filters.isEmpty() && matches(entity, filters);
	}

	/** The filter split into phrases, re-split only when the setting itself changes. */
	private static List<String> filters() {
		String filter = String.valueOf(AletheiaConfig.propHideFilter);
		if (!filter.equals(lastFilter)) {
			// Editing the filter has to take effect on the next frame, and splitting it is not work to
			// repeat for every entity in view.
			lastFilter = filter;
			wanted = NeoEdenParser.splitPhrases(filter);
		}
		return wanted;
	}

	private static boolean matches(Entity entity, List<String> filters) {
		if (any(filters, typeName(entity.getType()))) {
			return true;
		}

		if (entity instanceof Display.ItemDisplay display) {
			ItemStack stack = ((ItemDisplayAccessor) display).aletheia$itemStack();
			if (stack.isEmpty()) {
				return false;
			}
			// The model first: it is what the server actually chose for this prop, where the item under
			// it is usually the same placeholder for everything it hangs in the air.
			Identifier model = stack.get(DataComponents.ITEM_MODEL);
			return (model != null && any(filters, model.getPath()))
					|| any(filters, BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
		}

		if (entity instanceof Display.BlockDisplay display) {
			BlockDisplayAccessor block = (BlockDisplayAccessor) display;
			return any(filters, BuiltInRegistries.BLOCK.getKey(block.aletheia$blockState().getBlock()).getPath());
		}

		return false;
	}

	private static boolean any(List<String> filters, String value) {
		for (String phrase : filters) {
			if (ChatText.containsIgnoreCase(value, phrase)) {
				return true;
			}
		}
		return false;
	}

	/** e.g. {@code item_display}. Public because {@link PropScan} prints the same string it matches. */
	public static String typeName(EntityType<?> type) {
		return TYPE_NAMES.computeIfAbsent(type, EntityType::toShortString);
	}
}
