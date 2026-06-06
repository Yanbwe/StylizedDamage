package com.stylizeddamage.forge.client;

import com.stylizeddamage.common.damage.ScreenPosition;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Maps a Minecraft world position to 2D screen coordinates using
 * the camera's view-projection matrix.
 */
public final class EntityScreenMapper {

    private static final double MIN_FORWARD_DEPTH = 0.05;

    /** Cached world-render projection matrix (set via RenderLevelStageEvent.AFTER_LEVEL).
     *  Used to extract dynamic FOV for zoom-aware screen projection. */
    public static volatile Matrix4f cachedProjectionMatrix;

    private EntityScreenMapper() {
        throw new AssertionError("Utility class");
    }

    public static ScreenPosition worldToScreen(
            final Camera camera,
            final Entity entity,
            final int screenWidth,
            final int screenHeight) {
        if (camera == null || entity == null || !camera.isInitialized()) {
            return null;
        }
        return project(camera, entity.getX(), entity.getEyeY(), entity.getZ(),
                screenWidth, screenHeight);
    }

    public static ScreenPosition worldToScreen(
            final Camera camera,
            final double wx, final double wy, final double wz,
            final int screenWidth,
            final int screenHeight) {
        if (camera == null || !camera.isInitialized()) {
            return null;
        }
        return project(camera, wx, wy, wz, screenWidth, screenHeight);
    }

    private static ScreenPosition project(
            final Camera camera,
            final double wx, final double wy, final double wz,
            final int screenWidth, final int screenHeight) {

        final Vec3 camPos = camera.getPosition();
        final double dx = wx - camPos.x;
        final double dy = wy - camPos.y;
        final double dz = wz - camPos.z;

        final Vector3f forward = camera.getLookVector();
        final Vector3f up = camera.getUpVector();
        final Vector3f left = camera.getLeftVector();

        final double sx = dx * left.x() + dy * left.y() + dz * left.z();
        final double sy = dx * up.x() + dy * up.y() + dz * up.z();
        final double sz = dx * forward.x() + dy * forward.y() + dz * forward.z();

        if (sz < MIN_FORWARD_DEPTH) {
            return null;
        }

        // Prefer dynamic FOV from cached projection matrix (accounts for spyglass etc.)
        final double tanHalfFovX, tanHalfFovY;
        final Matrix4f cachedProj = cachedProjectionMatrix;
        if (cachedProj != null) {
            // Extract half-FOV tangent from perspective matrix diagonal elements
            tanHalfFovX = 1.0 / Math.max(Math.abs(cachedProj.m00()), 0.001);
            tanHalfFovY = 1.0 / Math.max(Math.abs(cachedProj.m11()), 0.001);
        } else {
            // Fallback: derive from camera near-plane
            final Camera.NearPlane near = camera.getNearPlane();
            if (near != null) {
                final Vec3 center = near.getPointOnPlane(0, 0);
                final double nearDist = center.length();
                final Vec3 topCenter = near.getPointOnPlane(0, 1);
                final double halfHeightAtNear = topCenter.subtract(center).length();
                final Vec3 rightCenter = near.getPointOnPlane(-1, 0);
                final double halfWidthAtNear = rightCenter.subtract(center).length();
                tanHalfFovX = halfWidthAtNear / Math.max(nearDist, 0.01);
                tanHalfFovY = halfHeightAtNear / Math.max(nearDist, 0.01);
            } else {
                final double aspect = (double) screenWidth / screenHeight;
                final double tanHalfFovY_fallback = Math.tan(Math.toRadians(35.0));
                tanHalfFovY = tanHalfFovY_fallback;
                tanHalfFovX = tanHalfFovY_fallback * aspect;
            }
        }

        // Normalized device coordinates: (sx/sz)/tanHalfFovX in [-1, 1]
        final double ndcX = (sx / sz) / Math.max(tanHalfFovX, 0.01);
        final double ndcY = (sy / sz) / Math.max(tanHalfFovY, 0.01);

        final double hw = screenWidth / 2.0;
        final double hh = screenHeight / 2.0;

        // ndcX=-1 → left edge (0), ndcX=0 → center (hw), ndcX=+1 → right edge (screenWidth)
        return new ScreenPosition(hw * (1.0 - ndcX), hh * (1.0 - ndcY));
    }
}
