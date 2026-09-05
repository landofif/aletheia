package dev.landofif.aletheia.world;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws a flat ring on the ground, which is the whole of what {@link Domains} puts in the fire
 * circle's place.
 *
 * <p><b>Camera-relative, not world coordinates.</b> The event this is called from hands over a fresh
 * pose stack -- the matrix is the identity, and the view and projection are set on the pipeline
 * rather than on it -- so a vertex has to arrive already measured from the camera. That is what every
 * {@code - camera.x} below is doing, and it is also why the ring cannot simply be handed the centre
 * and left to it: at the distances Minecraft works in, a float holding a world coordinate has lost
 * enough precision to make a circle visibly wobble.
 *
 * <p><b>Why a band and not a line.</b> The obvious way to draw this is the line render type, and it
 * is the wrong one: a line is a fixed width in pixels, so the far side of a nine-block ring draws as
 * thick as the near side and the whole thing reads as flat. A ring built out of quads is a real thing
 * standing in the world -- it thins with distance and it is a boundary you can judge by eye, which is
 * the only reason it is on screen. The walls give it enough substance to still be visible when the
 * camera is nearly level with the floor, where a flat annulus disappears edge-on.
 *
 * <p>The render type is {@code debugQuads}: untextured, translucent, position and colour only, and
 * <b>depth tested without writing depth</b>. So the ring is hidden by a wall standing in front of it,
 * which is what makes it read as being on the floor rather than painted on the screen, and it never
 * occludes anything drawn after it.
 */
public final class DomainRing {
	private DomainRing() {
	}

	/** How wide the line itself is drawn, in blocks. */
	private static final float WIDTH = 0.08F;

	/**
	 * How far above the circle the ring floats, in blocks.
	 *
	 * <p>The server's circle sits all but flush with the floor, and two flat surfaces at the same
	 * height fight over every pixel. A finger's width of clearance is enough to settle that and still
	 * reads as being on the ground.
	 */
	private static final float LIFT = 0.2F;

	/**
	 * How many segments a ring is built from, worked out from its radius so that a segment is roughly a
	 * fixed length on the ground: a small circle is not paying for detail nobody can see, and a large
	 * one does not turn into a polygon.
	 */
	private static final int SEGMENTS_PER_BLOCK = 8;
	private static final int MIN_SEGMENTS = 24;
	private static final int MAX_SEGMENTS = 160;

	/**
	 * @param camera where the camera is this frame, which every vertex is measured from
	 * @param centre the middle of the ring, in world coordinates
	 * @param radius how far out to draw it, in blocks
	 * @param height how tall to stand the walls up, in blocks; {@code 0} draws a flat ring
	 * @param rgb    the colour, packed as {@code 0xRRGGBB}
	 * @param alpha  how solid to draw it, {@code 0} to {@code 1}
	 */
	public static void draw(PoseStack poseStack, MultiBufferSource.BufferSource buffers, Vec3 camera,
			Vec3 centre, double radius, float height, int rgb, float alpha) {
		if (radius <= 0.0 || alpha <= 0.0F) {
			return;
		}

		VertexConsumer buffer = buffers.getBuffer(RenderTypes.debugQuads());
		Matrix4f matrix = poseStack.last().pose();

		float red = ((rgb >> 16) & 0xFF) / 255.0F;
		float green = ((rgb >> 8) & 0xFF) / 255.0F;
		float blue = (rgb & 0xFF) / 255.0F;

		float outer = (float) (radius + WIDTH / 2.0F);
		float inner = (float) Math.max(0.0, radius - WIDTH / 2.0F);

		// Camera-relative, as above -- worked out once here rather than per vertex.
		float x = (float) (centre.x - camera.x);
		float z = (float) (centre.z - camera.z);
		float bottom = (float) (centre.y - camera.y) + LIFT;
		float top = bottom + Math.max(0.0F, height);

		int segments = Mth.clamp((int) (radius * SEGMENTS_PER_BLOCK), MIN_SEGMENTS, MAX_SEGMENTS);
		for (int segment = 0; segment < segments; segment++) {
			double from = (Math.PI * 2.0 * segment) / segments;
			double to = (Math.PI * 2.0 * (segment + 1)) / segments;

			float cosFrom = (float) Math.cos(from);
			float sinFrom = (float) Math.sin(from);
			float cosTo = (float) Math.cos(to);
			float sinTo = (float) Math.sin(to);

			float outerX1 = x + cosFrom * outer;
			float outerZ1 = z + sinFrom * outer;
			float outerX2 = x + cosTo * outer;
			float outerZ2 = z + sinTo * outer;

			float innerX1 = x + cosFrom * inner;
			float innerZ1 = z + sinFrom * inner;
			float innerX2 = x + cosTo * inner;
			float innerZ2 = z + sinTo * inner;

			// The lid. On a flat ring this is the only face there is.
			quad(buffer, matrix, red, green, blue, alpha,
					outerX1, top, outerZ1,
					outerX2, top, outerZ2,
					innerX2, top, innerZ2,
					innerX1, top, innerZ1);

			if (top <= bottom) {
				continue;
			}

			quad(buffer, matrix, red, green, blue, alpha,
					innerX1, bottom, innerZ1,
					innerX2, bottom, innerZ2,
					outerX2, bottom, outerZ2,
					outerX1, bottom, outerZ1);

			quad(buffer, matrix, red, green, blue, alpha,
					outerX1, bottom, outerZ1,
					outerX2, bottom, outerZ2,
					outerX2, top, outerZ2,
					outerX1, top, outerZ1);

			quad(buffer, matrix, red, green, blue, alpha,
					innerX1, top, innerZ1,
					innerX2, top, innerZ2,
					innerX2, bottom, innerZ2,
					innerX1, bottom, innerZ1);
		}
	}

	/**
	 * One face, wound the way a face pointing at you is wound.
	 *
	 * <p>Which way round hardly matters here -- {@code debugQuads} draws with culling off, so every
	 * face is drawn from both sides -- but a ring whose faces disagree is a ring that breaks the day it
	 * is drawn through anything that does cull.
	 */
	private static void quad(VertexConsumer buffer, Matrix4f matrix,
			float red, float green, float blue, float alpha,
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4) {
		buffer.addVertex(matrix, x1, y1, z1).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, x2, y2, z2).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, x3, y3, z3).setColor(red, green, blue, alpha);
		buffer.addVertex(matrix, x4, y4, z4).setColor(red, green, blue, alpha);
	}
}
