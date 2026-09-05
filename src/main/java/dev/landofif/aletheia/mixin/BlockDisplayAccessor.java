package dev.landofif.aletheia.mixin;

import net.minecraft.world.entity.Display;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Reaches the block on a block display entity -- the other half of the pair with
 * {@link ItemDisplayAccessor}, and the other way a server hangs a prop in the air. Private to the
 * class for the same reason, and read the same way.
 */
@Mixin(Display.BlockDisplay.class)
public interface BlockDisplayAccessor {

	@Invoker("getBlockState")
	BlockState aletheia$blockState();
}
