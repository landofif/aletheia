package dev.landofif.aletheia.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the item on an item display entity -- a floating item with no dropped-item physics under
 * it, which is how a server hangs a prop in the air. The getter is private to the class and only its
 * own renderer calls it.
 *
 * <p>This reads the synced data straight, rather than going through the public
 * {@code itemRenderState()} record beside it: that record is filled in by the entity's own tick and
 * is null until the first one, which is exactly the moment a display comes into view.
 */
@Mixin(Display.ItemDisplay.class)
public interface ItemDisplayAccessor {

	@Invoker("getItemStack")
	ItemStack aletheia$itemStack();
}
