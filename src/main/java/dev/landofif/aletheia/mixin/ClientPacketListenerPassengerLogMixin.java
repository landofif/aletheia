package dev.landofif.aletheia.mixin;

import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.log.PassengerSpam;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Silences vanilla's "Received passengers for unknown entity" spam. See {@link PassengerSpam}.
 *
 * <p>Only the {@code LOGGER.warn} call is replaced, not the handling around it. The method reads:
 *
 * <pre>
 * Entity entity = this.level.getEntity(packet.getVehicle());
 * if (entity == null) {
 *     LOGGER.warn("Received passengers for unknown entity");
 *     return;
 * }
 * </pre>
 *
 * so redirecting the one call it makes cannot change what the client does with the packet -- the
 * {@code return} is still the next instruction. Injecting at HEAD and cancelling would have been the
 * obvious alternative and is worse: it would run ahead of
 * {@code PacketUtils.ensureRunningOnSameThread} and take on the job of deciding what an unknown
 * vehicle is, which is vanilla's to make.
 *
 * <p>{@code remap = false} on the target because {@code org.slf4j.Logger} is a library class and
 * keeps its name; the enclosing method is Minecraft's and is remapped as usual.
 *
 * <p><b>{@code require = 0} is deliberate.</b> This is a cosmetic fix to a log file, and
 * {@code aletheia.mixins.json} otherwise demands every injector succeed -- which would turn a
 * renamed vanilla method into a crash on startup instead of the warning simply coming back. There
 * are already crash reports in this instance from another mod failing exactly that way.
 */
@Mixin(ClientPacketListener.class)
public class ClientPacketListenerPassengerLogMixin {

	@Redirect(
			method = "handleSetEntityPassengersPacket",
			at = @At(
					value = "INVOKE",
					target = "Lorg/slf4j/Logger;warn(Ljava/lang/String;)V",
					remap = false),
			require = 0)
	private void aletheia$quietenPassengerWarning(Logger logger, String message) {
		if (!AletheiaConfig.quietPassengerWarnings) {
			logger.warn(message);
			return;
		}
		PassengerSpam.swallow();
	}
}
