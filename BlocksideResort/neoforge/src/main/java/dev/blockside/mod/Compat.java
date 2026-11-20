package dev.blockside.mod;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * The few vanilla calls whose names/signatures move between Minecraft versions (or are private) go
 * through here by reflection, so a rename degrades one feature with a log line instead of breaking the
 * build. Minecraft 26.x ships unobfuscated, so these names are the real runtime names.
 */
public final class Compat {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Set<String> warned = new HashSet<>();
    private Compat() {}

    private static void warn(String what, Throwable t) {
        if (warned.add(what)) LOG.warn("[Blockside] compat: {} unavailable ({}); feature degraded", what, t.toString());
    }

    private static Method find(Class<?> c, String name, Class<?>... params) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }

    public static void setDisplayBlock(Display.BlockDisplay d, BlockState state) {
        try {
            Method m = find(Display.BlockDisplay.class, "setBlockState", BlockState.class);
            if (m != null) { m.invoke(d, state); return; }
            throw new NoSuchMethodException("setBlockState");
        } catch (Throwable t) { warn("BlockDisplay.setBlockState", t); }
    }

    /** Scale and offset of a block display (box drawn from the entity position + translation). */
    public static void setDisplayBox(Display d, float tx, float ty, float tz, float sx, float sy, float sz) {
        try {
            Class<?> tr = com.mojang.math.Transformation.class;
            Object t = new com.mojang.math.Transformation(new org.joml.Vector3f(tx, ty, tz), new org.joml.Quaternionf(),
                    new org.joml.Vector3f(sx, sy, sz), new org.joml.Quaternionf());
            Method m = find(Display.class, "setTransformation", tr);
            if (m == null) throw new NoSuchMethodException("setTransformation");
            m.invoke(d, t);
        } catch (Throwable t) { warn("Display.setTransformation", t); }
    }

    /** Smooth client-side interpolation between server position updates. */
    public static void setDisplayInterpolation(Display d, int ticks) {
        try {
            Method m = find(Display.class, "setPosRotInterpolationDuration", int.class);
            if (m == null) throw new NoSuchMethodException("setPosRotInterpolationDuration");
            m.invoke(d, ticks);
        } catch (Throwable t) { warn("Display.setPosRotInterpolationDuration", t); }
    }

    public static boolean startRiding(Entity rider, Entity vehicle) {
        try {
            for (Method m : Entity.class.getMethods()) {
                if (!m.getName().equals("startRiding")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length == 2 && p[1] == boolean.class) return (boolean) m.invoke(rider, vehicle, true);
                if (p.length == 3 && p[1] == boolean.class && p[2] == boolean.class) return (boolean) m.invoke(rider, vehicle, true, true);
            }
            throw new NoSuchMethodException("startRiding");
        } catch (Throwable t) { warn("Entity.startRiding", t); return false; }
    }

    /** Server-side copy of a rider's movement keys: forward, backward, left, right, jump, shift, sprint. */
    public static boolean[] lastInput(net.minecraft.server.level.ServerPlayer p) {
        try {
            Method m = find(p.getClass(), "getLastClientInput");
            if (m == null) throw new NoSuchMethodException("getLastClientInput");
            Object in = m.invoke(p);
            boolean[] out = new boolean[7];
            String[] names = {"forward", "backward", "left", "right", "jump", "shift", "sprint"};
            for (int i = 0; i < names.length; i++) out[i] = (boolean) in.getClass().getMethod(names[i]).invoke(in);
            return out;
        } catch (Throwable t) { warn("ServerPlayer.getLastClientInput", t); return null; }
    }

    /** Zeroes the walking input vector of the local player's input (client). */
    public static void zeroMoveVector(Object clientInput) {
        try {
            Field f = null;
            for (Class<?> k = clientInput.getClass(); k != null && f == null; k = k.getSuperclass()) {
                try { f = k.getDeclaredField("moveVector"); } catch (NoSuchFieldException ignored) { }
            }
            if (f == null) throw new NoSuchFieldException("moveVector");
            f.setAccessible(true);
            f.set(clientInput, net.minecraft.world.phys.Vec2.ZERO);
        } catch (Throwable t) { warn("ClientInput.moveVector", t); }
    }

    /** Reads a key of the client Input record held in ClientInput.keyPresses. */
    public static boolean[] clientKeys(Object clientInput) {
        try {
            Object keys = clientInput.getClass().getField("keyPresses").get(clientInput);
            boolean[] out = new boolean[7];
            String[] names = {"forward", "backward", "left", "right", "jump", "shift", "sprint"};
            for (int i = 0; i < names.length; i++) out[i] = (boolean) keys.getClass().getMethod(names[i]).invoke(keys);
            return out;
        } catch (Throwable t) { warn("ClientInput.keyPresses", t); return new boolean[7]; }
    }
}
