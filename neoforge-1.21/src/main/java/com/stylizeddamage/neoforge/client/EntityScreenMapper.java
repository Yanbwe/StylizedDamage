package com.stylizeddamage.neoforge.client;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import com.stylizeddamage.common.damage.ScreenPosition;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Maps world position to screen coordinates using camera FOV projection.
 * Same as forge version.
 */
public final class EntityScreenMapper {

    private static final double MIN_FORWARD_DEPTH = 0.05;

    /** Cached world-render projection matrix (set via RenderLevelStageEvent.AFTER_LEVEL).
     *  Used to extract dynamic FOV for zoom-aware screen projection. */
    public static volatile Matrix4f cachedProjectionMatrix;

    private EntityScreenMapper() {}

    public static ScreenPosition worldToScreen(Camera camera, Entity entity, int sw, int sh) {
        if (camera == null || entity == null || !camera.isInitialized()) return null;
        return project(camera, entity.getX(), entity.getEyeY(), entity.getZ(), sw, sh);
    }

    public static ScreenPosition worldToScreen(Camera camera, double wx, double wy, double wz, int sw, int sh) {
        if (camera == null || !camera.isInitialized()) return null;
        return project(camera, wx, wy, wz, sw, sh);
    }

    private static ScreenPosition project(Camera camera, double wx, double wy, double wz, int sw, int sh) {
        Vec3 camPos = camera.getPosition();
        double dx = wx - camPos.x, dy = wy - camPos.y, dz = wz - camPos.z;

        Vector3f forward = camera.getLookVector();
        Vector3f up = camera.getUpVector();
        Vector3f left = camera.getLeftVector();

        double sx = dx * left.x() + dy * left.y() + dz * left.z();
        double sy = dx * up.x() + dy * up.y() + dz * up.z();
        double sz = dx * forward.x() + dy * forward.y() + dz * forward.z();
        if (sz < MIN_FORWARD_DEPTH) return null;

        // Prefer dynamic FOV from cached projection matrix (accounts for spyglass etc.)
        final double tanHalfFovX, tanHalfFovY;
        final Matrix4f cachedProj = cachedProjectionMatrix;
        if (cachedProj != null) {
            // Extract half-FOV tangent from perspective matrix diagonal elements
            tanHalfFovX = 1.0 / Math.max(Math.abs(cachedProj.m00()), 0.001);
            tanHalfFovY = 1.0 / Math.max(Math.abs(cachedProj.m11()), 0.001);
        } else {
            // Fallback: derive from camera near-plane
            Camera.NearPlane near = camera.getNearPlane();
            if (near != null) {
                Vec3 center = near.getPointOnPlane(0, 0);
                double nearDist = center.length();
                Vec3 topCenter = near.getPointOnPlane(0, 1);
                double halfH = topCenter.subtract(center).length();
                Vec3 rightCenter = near.getPointOnPlane(-1, 0);
                double halfW = rightCenter.subtract(center).length();
                tanHalfFovX = halfW / Math.max(nearDist, 0.01);
                tanHalfFovY = halfH / Math.max(nearDist, 0.01);
            } else {
                double aspect = (double) sw / sh;
                double tanHalfFovY_fb = Math.tan(Math.toRadians(35.0));
                tanHalfFovY = tanHalfFovY_fb;
                tanHalfFovX = tanHalfFovY_fb * aspect;
            }
        }

        double ndcX = (sx / sz) / Math.max(tanHalfFovX, 0.01);
        double ndcY = (sy / sz) / Math.max(tanHalfFovY, 0.01);
        double hw = sw / 2.0, hh = sh / 2.0;
        return new ScreenPosition(hw * (1.0 - ndcX), hh * (1.0 - ndcY));
    }
}
