package dev.blockside.mod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashMap;
import java.util.Map;

/** Block spec strings ("ns:id[prop=value,...]") <-> BlockState, used by templates and undo journals. */
public final class BlockIO {
    private static final Map<String, BlockState> CACHE = new HashMap<>();
    private BlockIO() {}

    public static BlockState parse(String spec) {
        return CACHE.computeIfAbsent(spec, BlockIO::parseUncached);
    }

    private static BlockState parseUncached(String spec) {
        String id = spec, props = null;
        int b = spec.indexOf('[');
        if (b >= 0) { id = spec.substring(0, b); props = spec.substring(b + 1, spec.length() - 1); }
        Identifier key = Identifier.tryParse(id);
        if (key == null) return Blocks.AIR.defaultBlockState();
        Block block = BuiltInRegistries.BLOCK.getValue(key);
        BlockState state = block.defaultBlockState();
        if (props != null && !props.isBlank()) {
            for (String kv : props.split(",")) {
                String[] p = kv.split("=", 2);
                if (p.length != 2) continue;
                Property<?> prop = block.getStateDefinition().getProperty(p[0].trim());
                if (prop != null) state = with(state, prop, p[1].trim());
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState s, Property<T> p, String v) {
        return p.getValue(v).map(val -> s.setValue(p, val)).orElse(s);
    }

    public static String spec(BlockState s) {
        StringBuilder sb = new StringBuilder(BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString());
        if (!s.getProperties().isEmpty()) {
            sb.append('[');
            boolean first = true;
            for (Property<?> p : s.getProperties()) {
                if (!first) sb.append(',');
                first = false;
                sb.append(p.getName()).append('=').append(valueName(s, p));
            }
            sb.append(']');
        }
        return sb.toString();
    }

    private static <T extends Comparable<T>> String valueName(BlockState s, Property<T> p) { return p.getName(s.getValue(p)); }

    public static BlockState rotate(BlockState s, int quarterTurns) {
        return switch (((quarterTurns % 4) + 4) % 4) {
            case 1 -> s.rotate(Rotation.CLOCKWISE_90);
            case 2 -> s.rotate(Rotation.CLOCKWISE_180);
            case 3 -> s.rotate(Rotation.COUNTERCLOCKWISE_90);
            default -> s;
        };
    }
}
