package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.world.Domains;
import dev.landofif.aletheia.world.Props;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps chosen things out of the world: the props {@link Props} names -- the Arcanist monolith among
 * them -- and the fire circle {@link Domains} draws its own ring over.
 *
 * <p>This is the one gate every entity in the world passes through before it is drawn, so answering
 * no here is the whole of it: nothing is removed, nothing is moved, and the entity carries on
 * existing for everything that is not the renderer. Turning the setting off puts it back on the next
 * frame.
 *
 * <p>Injected at {@code HEAD} rather than at the return, so a hidden prop never costs the lookup and
 * frustum test that would only have been thrown away. Everything else pays two field reads for the
 * check and goes on unchanged.
 */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherHideMixin {

	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void aletheia$hideProps(Entity entity, Frustum frustum, double x, double y, double z,
			CallbackInfoReturnable<Boolean> cir) {
		if (Props.hidingAnything() && Props.hides(entity)) {
			cir.setReturnValue(false);
			return;
		}

		// Two questions rather than one filter: the fire circle is hidden because something is drawn in
		// its place, so it goes off with the ring's own setting and not with the prop filter.
		if (Domains.hidingCircles() && Domains.hides(entity)) {
			cir.setReturnValue(false);
		}
	}
}
