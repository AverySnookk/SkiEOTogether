package dev.blockside.mod.server;

import dev.blockside.core.snow.SnowParams;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.core.snow.SurfaceKind;
import dev.blockside.mod.Registry;
import dev.blockside.mod.block.ResortSnowBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws the snowpack as snow layers on top of the ground (1/8-block resolution). These blocks are the
 * renderer for the shared surface: the client skier samples the same heights, and the groomer drives on them.
 */
public final class SnowRenderer {
    private SnowRenderer() {}

    public static void render(ServerLevel level, SnowTile t, SnowParams p) {
        BlockPos base = new BlockPos(t.x, t.ground, t.z);
        if (!level.isLoaded(base)) return;
        int layers = (int) Math.round(Math.min(t.depth(), p.maxVisibleDepth) * 8);
        SurfaceKind kind = t.kind();
        for (int i = 0; i < 3; i++) {
            BlockPos pos = base.above(i);
            BlockState cur = level.getBlockState(pos);
            boolean snow = WorldAdapter.isSnowCover(cur);
            if (!snow && !cur.isAir() && !cur.canBeReplaced()) break; // a building/tower stands here
            int want = Math.max(0, Math.min(8, layers - 8 * i));
            BlockState target;
            if (want == 0) {
                if (!snow) continue;
                target = Blocks.AIR.defaultBlockState();
            } else {
                target = switch (kind) {
                    case GROOMED -> Registry.GROOMED_SNOW.get().defaultBlockState().setValue(ResortSnowBlock.LAYERS, want);
                    case ICE -> Registry.ICY_SNOW.get().defaultBlockState().setValue(ResortSnowBlock.LAYERS, want);
                    default -> Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, want);
                };
            }
            if (!cur.equals(target)) level.setBlock(pos, target, 2);
        }
    }
}
