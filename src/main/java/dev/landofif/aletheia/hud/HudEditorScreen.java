package dev.landofif.aletheia.hud;

import dev.landofif.aletheia.config.AletheiaConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag the readouts where you want them and scroll to resize, instead of guessing at slider values.
 *
 * <p>Everything is written straight back to the same settings the sliders edit, so the two stay in
 * step; the config is saved on the way out.
 */
public final class HudEditorScreen extends Screen {
	/** How close to an edge or a centre line counts as lined up with it. */
	private static final int SNAP_DISTANCE = 4;

	private static final int SCALE_STEP = 5;

	private static final int WASH = 0x88101010;
	private static final int BOX_IDLE = 0x33FFFFFF;
	private static final int BOX_ACTIVE = 0x445599FF;
	private static final int BOX_OFF = 0x22000000;
	private static final int BORDER_IDLE = 0x55FFFFFF;
	private static final int BORDER_ACTIVE = 0xFF7AA7FF;
	private static final int BORDER_OFF = 0x33FFFFFF;
	private static final int GUIDE = 0xAAFFDD55;

	/** How much of a switched-off readout still shows through, so it can be found and switched on. */
	private static final int OFF_ALPHA = 0x50;

	private static final int PANEL = 0xCC0A0A0A;
	private static final int PANEL_EDGE = 0x33FFFFFF;
	/** These three are handed to {@code withColor}, which wants plain RGB rather than ARGB. */
	private static final int HINT = 0xAAAAAA;
	private static final int HINT_KEY = 0xFFFFFF;
	private static final int OFF_LABEL = 0xFF8080;

	/** The readout being dragged, and where inside it the cursor took hold. */
	@Nullable
	private AletheiaHud dragging;
	private int grabX;
	private int grabY;

	/** Last touched, so the keyboard has something to act on once the mouse has moved away. */
	@Nullable
	private AletheiaHud selected;

	private double mouseX;
	private double mouseY;

	public HudEditorScreen() {
		super(Component.literal("Readout editor"));
	}

	/** Asked by the HUD layer, so a readout is not drawn twice while it is being arranged. */
	public static boolean isOpen() {
		return Minecraft.getInstance().screen instanceof HudEditorScreen;
	}

	/**
	 * Opens the editor on the next frame. A command runs while the chat screen is still up, and chat
	 * closes itself afterwards -- which would take this screen down with it if it opened right now.
	 *
	 * <p>{@code schedule} rather than {@code execute}: the latter runs a task on the spot when it is
	 * already on the client thread, which is exactly the case being avoided here.
	 */
	public static void openLater(Minecraft client) {
		client.schedule(() -> client.setScreen(new HudEditorScreen()));
	}

	@Override
	public boolean isPauseScreen() {
		// The world keeps running, so the readouts keep reading while they are being placed.
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// A flat wash rather than the usual blur: these are being lined up against the world behind them.
		graphics.fill(0, 0, width, height, WASH);
	}

	/** One readout as the editor sees it this frame. */
	private record Placed(AletheiaHud hud, String text, AletheiaHud.Box box, boolean active, boolean on) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		AletheiaHud hovered = dragging != null ? dragging : hudAt(mouseX, mouseY);
		List<Placed> placed = new ArrayList<>(AletheiaHuds.ALL.size());
		for (AletheiaHud hud : AletheiaHuds.ALL) {
			String text = textOf(hud);
			placed.add(new Placed(hud, text, hud.box(font, text, width, height), hud == hovered, hud.enabled()));
		}

		for (Placed entry : placed) {
			AletheiaHud.Box box = entry.box();
			int fill = entry.active() ? BOX_ACTIVE : (entry.on() ? BOX_IDLE : BOX_OFF);
			graphics.fill(box.x() - 2, box.y() - 2, box.x() + box.width() + 2, box.y() + box.height() + 2, fill);
			border(graphics, box, entry.active() ? BORDER_ACTIVE : (entry.on() ? BORDER_IDLE : BORDER_OFF));

			if (entry.hud() == dragging) {
				guides(graphics, box);
			}
		}

		// The readouts belong on top of their own backing boxes, which only a fresh stratum promises.
		graphics.nextStratum();

		for (Placed entry : placed) {
			// A switched-off readout is drawn faint rather than left out: it is still something you
			// came here to find, and it is switched back on with the right mouse button on the spot.
			int colour = entry.on() ? entry.hud().colour() : fade(entry.hud().colour());
			entry.hud().draw(graphics, font, entry.text(), colour, entry.box());
			if (entry.active()) {
				caption(graphics, entry.hud(), entry.box(), entry.on());
			}
		}

		hints(graphics);
	}

	/** The same colour, mostly transparent. */
	private static int fade(int colour) {
		return (colour & 0x00FFFFFF) | (OFF_ALPHA << 24);
	}

	/**
	 * The title and the controls, on a backing panel rather than as loose text over the world -- this
	 * is the one screen where everything behind it is deliberately still visible.
	 */
	private void hints(GuiGraphicsExtractor graphics) {
		List<Component> lines = List.of(
				Component.literal("Drag").withColor(HINT_KEY)
						.append(Component.literal(" to move  ·  ").withColor(HINT))
						.append(Component.literal("Scroll").withColor(HINT_KEY))
						.append(Component.literal(" to resize  ·  ").withColor(HINT))
						.append(Component.literal("Right-click").withColor(HINT_KEY))
						.append(Component.literal(" to switch a readout on or off").withColor(HINT)),
				Component.literal("Arrows").withColor(HINT_KEY)
						.append(Component.literal(" nudge (").withColor(HINT))
						.append(Component.literal("Ctrl").withColor(HINT_KEY))
						.append(Component.literal(" by ten)  ·  ").withColor(HINT))
						.append(Component.literal("R").withColor(HINT_KEY))
						.append(Component.literal(" puts one back  ·  ").withColor(HINT))
						.append(Component.literal("Esc").withColor(HINT_KEY))
						.append(Component.literal(" saves and closes").withColor(HINT)));

		int widest = 0;
		for (Component line : lines) {
			widest = Math.max(widest, font.width(line));
		}
		int lineHeight = font.lineHeight + 2;
		int panelHeight = 8 + lines.size() * lineHeight + 4;
		int top = height - panelHeight - 6;
		int left = (width - widest) / 2 - 10;
		int right = (width + widest) / 2 + 10;

		graphics.fill(left, top, right, top + panelHeight, PANEL);
		graphics.fill(left, top, right, top + 1, PANEL_EDGE);

		int y = top + 6;
		for (Component line : lines) {
			graphics.centeredText(font, line, width / 2, y, 0xFFFFFFFF);
			y += lineHeight;
		}

		graphics.centeredText(font, Component.literal("Aletheia — readout editor"), width / 2, 12,
				0xFFFFFFFF);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean left = event.button() == 0;
		boolean right = event.button() == 1;
		AletheiaHud hud = left || right ? hudAt(event.x(), event.y()) : null;
		if (hud == null) {
			return super.mouseClicked(event, doubleClick);
		}

		selected = hud;

		// The right button is the switch, so a readout can be turned on to be placed -- and off again
		// -- without leaving the one screen that shows you what it will look like.
		if (right) {
			hud.enabled(!hud.enabled());
			AletheiaConfig.save();
			return true;
		}

		AletheiaHud.Box box = hud.box(font, textOf(hud), width, height);
		dragging = hud;
		grabX = (int) Math.round(event.x()) - box.x();
		grabY = (int) Math.round(event.y()) - box.y();
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging == null) {
			return super.mouseDragged(event, dragX, dragY);
		}

		AletheiaHud.Box box = dragging.box(font, textOf(dragging), width, height);
		int x = (int) Math.round(event.x()) - grabX;
		int y = (int) Math.round(event.y()) - grabY;

		boolean snapping = !event.hasShiftDown();
		dragging.moveTo(
				snap(x, box.width(), width, snapping),
				snap(y, box.height(), height, snapping),
				box, width, height);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			AletheiaConfig.save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		AletheiaHud hud = hudAt(mouseX, mouseY);
		if (hud == null || scrollY == 0.0) {
			return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
		}

		selected = hud;
		hud.resize(scrollY > 0.0 ? SCALE_STEP : -SCALE_STEP);
		AletheiaConfig.save();
		return true;
	}

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
		super.mouseMoved(x, y);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Whatever is under the cursor, falling back to the last one dragged once the mouse has moved off.
		AletheiaHud hud = hudAt(mouseX, mouseY);
		if (hud == null) {
			hud = selected;
		}
		if (hud == null) {
			return super.keyPressed(event);
		}

		if (event.key() == GLFW.GLFW_KEY_R) {
			hud.resetPlacement();
			AletheiaConfig.save();
			return true;
		}

		int stepX = (event.isLeft() ? -1 : 0) + (event.isRight() ? 1 : 0);
		int stepY = (event.isUp() ? -1 : 0) + (event.isDown() ? 1 : 0);
		if (stepX == 0 && stepY == 0) {
			return super.keyPressed(event);
		}

		// Ctrl covers the distance in one press once the readout is roughly where it belongs.
		int step = event.hasControlDown() ? 10 : 1;
		AletheiaHud.Box box = hud.box(font, textOf(hud), width, height);
		hud.moveTo(box.x() + stepX * step, box.y() + stepY * step, box, width, height);
		AletheiaConfig.save();
		return true;
	}

	@Override
	public void onClose() {
		AletheiaConfig.save();
		super.onClose();
	}

	/** The live reading where there is one, so what is dragged is the real thing at its real width. */
	private String textOf(AletheiaHud hud) {
		String live = hud.text();
		return live == null || live.isBlank() ? hud.label() : live;
	}

	@Nullable
	private AletheiaHud hudAt(double mouseX, double mouseY) {
		// Backwards: the last drawn is the one on top, so it takes the click.
		for (int i = AletheiaHuds.ALL.size() - 1; i >= 0; i--) {
			AletheiaHud hud = AletheiaHuds.ALL.get(i);
			if (hud.box(font, textOf(hud), width, height).contains(mouseX, mouseY)) {
				return hud;
			}
		}
		return null;
	}

	/** Pulls a dragged edge onto the screen edge or the centre line when it comes close. */
	private static int snap(int position, int size, int screen, boolean enabled) {
		if (!enabled) {
			return position;
		}
		if (Math.abs(position) <= SNAP_DISTANCE) {
			return 0;
		}
		if (Math.abs(position + size - screen) <= SNAP_DISTANCE) {
			return screen - size;
		}
		int centre = (screen - size) / 2;
		if (Math.abs(position - centre) <= SNAP_DISTANCE) {
			return centre;
		}
		return position;
	}

	private void border(GuiGraphicsExtractor graphics, AletheiaHud.Box box, int colour) {
		int left = box.x() - 2;
		int top = box.y() - 2;
		int right = box.x() + box.width() + 2;
		int bottom = box.y() + box.height() + 2;

		graphics.fill(left, top, right, top + 1, colour);
		graphics.fill(left, bottom - 1, right, bottom, colour);
		graphics.fill(left, top, left + 1, bottom, colour);
		graphics.fill(right - 1, top, right, bottom, colour);
	}

	/** Names the readout and its size, above it where there is room and below it otherwise. */
	private void caption(GuiGraphicsExtractor graphics, AletheiaHud hud, AletheiaHud.Box box, boolean on) {
		Component caption = Component.literal(hud.label() + "  " + hud.scalePercent() + "%");
		if (!on) {
			caption = caption.copy().append(Component.literal("  OFF").withColor(OFF_LABEL));
		}
		int y = box.y() - font.lineHeight - 4;
		if (y < 0) {
			y = box.y() + box.height() + 4;
		}
		graphics.text(font, caption, box.x(), y, 0xFFFFFFFF);
	}

	/** Shows what a snap has lined the readout up with, so the pull is not a mystery. */
	private void guides(GuiGraphicsExtractor graphics, AletheiaHud.Box box) {
		if (box.x() == 0 || box.x() + box.width() == width || box.x() == (width - box.width()) / 2) {
			int centre = box.x() + box.width() / 2;
			graphics.fill(centre, 0, centre + 1, height, GUIDE);
		}
		if (box.y() == 0 || box.y() + box.height() == height || box.y() == (height - box.height()) / 2) {
			int centre = box.y() + box.height() / 2;
			graphics.fill(0, centre, width, centre + 1, GUIDE);
		}
	}
}
