package dev.landofif.aletheia.world;

import com.mojang.math.Transformation;
import dev.landofif.aletheia.ChatWatcher;
import dev.landofif.aletheia.config.AletheiaConfig;
import dev.landofif.aletheia.detect.ChatText;
import dev.landofif.aletheia.detect.NeoEdenParser;
import dev.landofif.aletheia.mixin.ItemDisplayAccessor;
import dev.landofif.aletheia.ui.Colours;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws Magnificat's domain as a ring you can actually judge your position against.
 *
 * <p>The ability puts a circle of fire on the floor and the only thing that matters about it is
 * whether you are inside it. What the server draws answers that badly: it is a picture on a flat
 * square hung a hair above the ground, so at a normal camera angle it is a thin ellipse of glare that
 * disappears entirely as the camera comes level, and in a fight there is fire, particles and half a
 * raid standing on top of it. This leaves the server's circle undrawn and puts a plain ring in the
 * same place, <b>coloured by which side of it you are standing on</b> -- which is the reading you
 * wanted from the circle in the first place, taken once per frame rather than guessed at.
 *
 * <p><b>Finding one.</b> The circle is an item display holding the model
 * {@code modelengine:magic_fire_circle2/out}, which is plain ASCII the server picked and is what the
 * filter matches -- for the same reason {@link Props} and
 * {@link dev.landofif.aletheia.afterburner.Afterburner} go by model id: names arrive in the resource
 * pack's own font and are a poor thing to match on. {@code /aletheia domain} prints what has been
 * found and the exact string being matched, so the filter can be read off rather than guessed at.
 *
 * <p><b>The radius is measured, not assumed.</b> The model is a three-block square with the ring
 * painted corner to corner, so the circle's radius on the ground is a block and a half times whatever
 * scale the display was spawned at -- nine blocks at the scale Magnificat uses. Taking it off the
 * entity means a ring that is the size the fire actually is, that grows with the circle while it is
 * being interpolated in, and that stays right if the ability ever comes in another size. The setting
 * is there to override it if a patch ever makes the measurement wrong.
 *
 * <p><b>Two switches, not one.</b> Hiding the server's circle and drawing the ring are separate
 * settings. They are usually both on, but the interesting states are the other two: the ring over the
 * top of the real circle is how you check the two line up, and hiding alone is for anyone who just
 * wants the glare gone.
 */
public final class Domains {
	private Domains() {
	}

	/**
	 * The ring's radius in blocks per unit of the display's scale.
	 *
	 * <p>The model is a flat square running from -16 to 32 in model space -- three blocks across,
	 * centred on the entity -- and the painted ring reaches its edges. So half of three is the radius
	 * one unit of scale buys.
	 */
	private static final double BLOCKS_PER_SCALE = 1.5;

	/** What to draw when the scale cannot be read, which is the size Magnificat's own circle is. */
	private static final double FALLBACK_RADIUS = 9.0;

	/** A scale this small is a circle that has not been interpolated in yet, not a tiny domain. */
	private static final double SMALLEST_RADIUS = 0.1;

	private static final int DEFAULT_INSIDE = 0x5AD022;
	private static final int DEFAULT_OUTSIDE = 0xE03C31;

	/** The circles in the world right now, refreshed on the tick and read on the frame. */
	private static final List<Display.ItemDisplay> circles = new ArrayList<>();

	/** The filter the phrases below were split out of, so it is parsed on a change and not per entity. */
	private static String lastFilter = "";
	private static List<String> wanted = List.of();

	public static void register() {
		// Found on the tick rather than on the frame: walking every entity in the world is not work to
		// do a hundred and twenty times a second when the answer changes twenty. The frame then only
		// asks the entities already found where they are, which is what has to be per-frame for the ring
		// to sit still while the camera moves.
		ClientTickEvents.END_CLIENT_TICK.register(Domains::tick);
		LevelRenderEvents.BEFORE_GIZMOS.register(Domains::draw);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> circles.clear());
	}

	// ------------------------------------------------------------------ finding the circles

	private static void tick(Minecraft client) {
		circles.clear();

		ClientLevel level = client.level;
		if (level == null || !watching() || filters().isEmpty() || !ChatWatcher.onEnabledServer()) {
			return;
		}

		for (Entity entity : level.entitiesForRendering()) {
			if (entity instanceof Display.ItemDisplay display && isCircle(display)) {
				circles.add(display);
			}
		}
	}

	/**
	 * Whether the list is worth keeping up to date.
	 *
	 * <p>Hiding does not read it -- that question is asked of the entity directly, per frame -- but
	 * {@code /aletheia domain} does, and someone running that has the feature on one way or the other.
	 */
	private static boolean watching() {
		return AletheiaConfig.enabled
				&& (AletheiaConfig.showDomainRing || AletheiaConfig.hideDomainCircle);
	}

	/**
	 * Whether this entity is one of the fire circles.
	 *
	 * <p>Also the whole of what the renderer asks, so it is written to answer no cheaply: the type
	 * check first, then the filter, and only then the item the display is carrying.
	 */
	public static boolean isCircle(Entity entity) {
		if (!(entity instanceof Display.ItemDisplay display)) {
			return false;
		}

		List<String> filters = filters();
		if (filters.isEmpty()) {
			return false;
		}

		ItemStack stack = ((ItemDisplayAccessor) display).aletheia$itemStack();
		if (stack.isEmpty()) {
			return false;
		}

		Identifier model = stack.get(DataComponents.ITEM_MODEL);
		if (model == null) {
			return false;
		}

		String path = model.getPath();
		for (String phrase : filters) {
			if (ChatText.containsIgnoreCase(path, phrase)) {
				return true;
			}
		}
		return false;
	}

	/** The filter split into phrases, re-split only when the setting itself changes. */
	private static List<String> filters() {
		String filter = String.valueOf(AletheiaConfig.domainFilter);
		if (!filter.equals(lastFilter)) {
			lastFilter = filter;
			wanted = NeoEdenParser.splitPhrases(filter);
		}
		return wanted;
	}

	// ------------------------------------------------------------------ hiding the server's circle

	/**
	 * Whether anything is being hidden at all, asked once per entity per frame by
	 * {@link dev.landofif.aletheia.mixin.EntityRenderDispatcherHideMixin} before it looks at the entity.
	 */
	public static boolean hidingCircles() {
		return AletheiaConfig.enabled && AletheiaConfig.hideDomainCircle;
	}

	/** @return whether this entity is a fire circle that should be left undrawn. */
	public static boolean hides(Entity entity) {
		return ChatWatcher.onEnabledServer() && isCircle(entity);
	}

	// ------------------------------------------------------------------ drawing the ring

	private static void draw(LevelRenderContext context) {
		if (circles.isEmpty() || !AletheiaConfig.enabled || !AletheiaConfig.showDomainRing) {
			return;
		}

		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return;
		}

		float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
		Vec3 camera = context.levelState().cameraRenderState.pos;
		Vec3 you = player.getPosition(partialTick);

		int inside = Colours.parse(AletheiaConfig.domainInsideColour, DEFAULT_INSIDE);
		int outside = Colours.parse(AletheiaConfig.domainOutsideColour, DEFAULT_OUTSIDE);
		float alpha = Mth.clamp(AletheiaConfig.domainRingAlpha, 0, 100) / 100.0F;
		float height = Math.max(0, AletheiaConfig.domainRingHeight) / 10.0F;

		for (Display.ItemDisplay circle : circles) {
			// The list is a tick old, and a circle can end within that tick.
			if (circle.isRemoved()) {
				continue;
			}

			Vec3 centre = circle.getPosition(partialTick);
			double radius = radiusOf(circle, partialTick);
			DomainRing.draw(
					context.poseStack(),
					context.bufferSource(),
					camera,
					centre,
					radius,
					height,
					within(you, centre, radius) ? inside : outside,
					alpha);
		}
	}

	/**
	 * Whether a position is inside a circle.
	 *
	 * <p>Measured flat, ignoring height: the domain is a column, and standing on a block at its edge
	 * does not put you outside it.
	 */
	public static boolean within(Vec3 position, Vec3 centre, double radius) {
		double x = position.x - centre.x;
		double z = position.z - centre.z;
		return x * x + z * z <= radius * radius;
	}

	/**
	 * How far out to draw a circle's ring, in blocks.
	 *
	 * <p>Taken off the display's own scale unless the setting overrides it -- see the class comment for
	 * where the block and a half comes from. The scale is read at the frame's point in the display's
	 * interpolation, so a circle still growing in is drawn at the size it is now rather than the size
	 * it will end up.
	 */
	public static double radiusOf(Display.ItemDisplay circle, float partialTick) {
		if (AletheiaConfig.domainRadius > 0) {
			return AletheiaConfig.domainRadius;
		}

		double measured = measuredRadius(circle, partialTick);
		return measured >= SMALLEST_RADIUS ? measured : FALLBACK_RADIUS;
	}

	/** @return the radius the display's scale works out to, or {@code 0} when it cannot be read */
	public static double measuredRadius(Display.ItemDisplay circle, float partialTick) {
		Display.RenderState state = circle.renderState();
		if (state == null) {
			// Filled in by the entity's own first tick, which a circle that has just arrived has not had.
			return 0.0;
		}

		Transformation transformation =
				state.transformation().get(circle.calculateInterpolationProgress(partialTick));
		Vector3fc scale = transformation.scale();

		// The circle lies flat, so the two axes across it are the model's own x and y; z is its
		// thickness, which is nothing. Whichever of the two is larger is the one to draw to.
		return Math.max(scale.x(), scale.y()) * BLOCKS_PER_SCALE;
	}

	// ------------------------------------------------------------------ /aletheia domain

	/**
	 * One circle standing in the world, for the diagnostic.
	 *
	 * @param model    the model id the filter was matched against
	 * @param distance how far you are from its middle, measured flat
	 * @param radius   the radius being drawn
	 * @param measured the radius its scale works out to, {@code 0} when it could not be read
	 * @param within   whether you are inside it
	 */
	public record Circle(String model, double distance, double radius, double measured, boolean within) {
	}

	/** What is on the floor right now and what is being drawn for it. */
	public static List<Circle> found() {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null) {
			return List.of();
		}

		Vec3 you = player.position();
		List<Circle> found = new ArrayList<>(circles.size());
		for (Display.ItemDisplay circle : circles) {
			if (circle.isRemoved()) {
				continue;
			}

			Vec3 centre = circle.position();
			double radius = radiusOf(circle, 1.0F);
			found.add(new Circle(
					modelOf(circle),
					Math.sqrt((you.x - centre.x) * (you.x - centre.x) + (you.z - centre.z) * (you.z - centre.z)),
					radius,
					measuredRadius(circle, 1.0F),
					within(you, centre, radius)));
		}
		found.sort((left, right) -> Double.compare(left.distance(), right.distance()));
		return found;
	}

	/** e.g. {@code modelengine:magic_fire_circle2/out}. */
	private static String modelOf(Display.ItemDisplay circle) {
		ItemStack stack = ((ItemDisplayAccessor) circle).aletheia$itemStack();
		Identifier model = stack.isEmpty() ? null : stack.get(DataComponents.ITEM_MODEL);
		return model == null ? "" : model.toString();
	}
}
