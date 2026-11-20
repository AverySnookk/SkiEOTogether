package dev.blockside.core.ski;

import dev.blockside.core.snow.SurfaceKind;

/** Deterministic ski dynamics on the shared surface. Pure function of state, input and surface. */
public final class SkiPhysics {
    private SkiPhysics() {}

    public static Vec3 headingVector(double yawDeg) {
        double r = Math.toRadians(yawDeg);
        return new Vec3(-Math.sin(r), 0, Math.cos(r));
    }

    /**
     * Advances the state by dt seconds while in contact with the surface.
     * @param surface sampled under the skier; null means no surface (airborne).
     */
    public static void step(SkiState st, SkiInput in, SurfacePoint surface, double dt, SkiParams p) {
        if (surface == null) {
            st.airborne = true;
            st.airTime += dt;
            Vec3 v = st.velocity;
            double drag = 1 - p.airDrag * v.horizontalLength() * dt;
            st.velocity = new Vec3(v.x() * drag, v.y() - p.gravity * dt, v.z() * drag);
            return;
        }
        Vec3 n = surface.normal();
        SurfaceKind kind = surface.kind();
        Vec3 v = st.velocity;
        if (st.airborne) {
            // Landing: lose the component into the slope; a hard flat landing scrubs speed.
            double into = -v.dot(n);
            v = v.projectOnPlane(n);
            st.crashed = into > 12;
            if (st.crashed) v = v.scale(0.4);
            st.airborne = false;
            st.airTime = 0;
        } else {
            v = v.projectOnPlane(n);
        }

        // Steering: skis rotate toward look yaw, with A/D adding to it.
        double target = in.yawDeg() + (in.left() ? -35 : 0) + (in.right() ? 35 : 0);
        double diff = wrap(target - st.headingDeg);
        double grip = kind.grip * (in.edge() ? p.edgeGripBonus : 1);
        double maxTurn = p.turnRate * dt * Math.min(1.5, 0.5 + grip / 6);
        st.headingDeg = wrap(st.headingDeg + Math.max(-maxTurn, Math.min(maxTurn, diff)));

        // Gravity along the slope.
        Vec3 g = new Vec3(0, -p.gravity, 0);
        v = v.add(g.projectOnPlane(n).scale(dt));

        // Split into along-ski and lateral components in the tangent plane.
        Vec3 ski = headingVector(st.headingDeg).projectOnPlane(n).normalize();
        if (ski.length() < 1e-6) ski = v.normalize();
        Vec3 lat = n.cross(ski).normalize();
        double vf = v.dot(ski), vl = v.dot(lat);

        // Lateral grip: edges bite, sideslip turns into forward speed (carving).
        double keep = Math.exp(-grip * dt);
        double lost = Math.abs(vl) * (1 - keep);
        vl *= keep;
        vf += Math.signum(vf == 0 ? 1 : vf) * lost * p.carveEfficiency;

        // Friction (normal force ~ g cos θ) and air drag.
        double cos = Math.max(0.2, n.y());
        double mu = kind.friction + (in.back() ? p.brakeFriction : 0);
        double fric = mu * p.gravity * cos * dt;
        double speed = Math.hypot(vf, vl);
        double drag = p.airDrag * (in.forward() ? p.tuckDragFactor : 1) * speed * speed * dt;
        double decel = fric + drag;
        if (speed > 1e-9) {
            double ns = Math.max(0, speed - decel) / speed;
            vf *= ns; vl *= ns;
        }
        // Skating push on the flats.
        if (in.forward() && Math.abs(vf) < p.skateBelow && kind.snow()) vf += p.skatePush * dt;

        v = ski.scale(vf).add(lat.scale(vl));
        double sp = v.length();
        if (sp > p.maxSpeed) v = v.scale(p.maxSpeed / sp);
        st.velocity = v;
    }

    static double wrap(double deg) {
        deg %= 360;
        if (deg > 180) deg -= 360;
        if (deg < -180) deg += 360;
        return deg;
    }
}
