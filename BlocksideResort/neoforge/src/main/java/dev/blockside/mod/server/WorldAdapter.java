package dev.blockside.mod.server;

import dev.blockside.core.construction.Placement;
import dev.blockside.core.construction.SiteProbe;
import dev.blockside.core.snow.SurfaceKind;
import dev.blockside.mod.BlockIO;
import dev.blockside.mod.Registry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Translates between the core simulation and the Minecraft world. */
public final class WorldAdapter {
    private WorldAdapter() {}

    public static boolean isSnowCover(BlockState s) {
        return s.is(Blocks.SNOW) || s.is(Registry.GROOMED_SNOW.get()) || s.is(Registry.ICY_SNOW.get());
    }

    public static SiteProbe.Kind kind(BlockState s) {
        if (s.isAir()) return SiteProbe.Kind.AIR;
        if (s.getBlock() instanceof LiquidBlock) return SiteProbe.Kind.FLUID;
        if (isSnowCover(s) || s.is(BlockTags.LOGS) || s.is(BlockTags.LEAVES) || s.is(BlockTags.SAPLINGS)
                || s.is(BlockTags.FLOWERS) || s.canBeReplaced() || s.is(Blocks.SWEET_BERRY_BUSH) || s.is(Blocks.DEAD_BUSH))
            return SiteProbe.Kind.NATURAL;
        if (s.is(BlockTags.DIRT) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.SAND) || s.is(Blocks.GRAVEL)
                || s.is(Blocks.SNOW_BLOCK) || s.is(Blocks.POWDER_SNOW) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE)
                || s.is(Blocks.BLUE_ICE) || s.is(Blocks.CLAY) || s.is(Blocks.COBBLESTONE) || s.is(BlockTags.TERRACOTTA)
                || s.is(Blocks.MOSSY_COBBLESTONE) || s.is(BlockTags.COAL_ORES) || s.is(BlockTags.IRON_ORES)
                || s.is(BlockTags.COPPER_ORES))
            return SiteProbe.Kind.TERRAIN;
        return SiteProbe.Kind.BUILT;
    }

    public static SiteProbe probe(ServerLevel level) {
        return (x, y, z) -> kind(level.getBlockState(new BlockPos(x, y, z)));
    }

    /** y of the top face of the highest ground block (ignores trees, plants and snow cover). */
    public static int top(ServerLevel level, int x, int z) {
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        int min = level.getMinY();
        while (y > min) {
            SiteProbe.Kind k = kind(level.getBlockState(new BlockPos(x, y - 1, z)));
            if (k == SiteProbe.Kind.TERRAIN || k == SiteProbe.Kind.BUILT || k == SiteProbe.Kind.FLUID) break;
            y--;
        }
        return y;
    }

    /** Visible surface including snow cover (what a groomer drives on). */
    public static double surface(ServerLevel level, int x, int z) {
        int g = top(level, x, z);
        double h = g;
        for (int i = 0; i < 3; i++) {
            BlockState s = level.getBlockState(new BlockPos(x, g + i, z));
            int layers = snowLayers(s);
            if (layers <= 0) break;
            h = g + i + layers / 8.0;
            if (layers < 8) break;
        }
        return h;
    }

    public static int snowLayers(BlockState s) {
        if (s.is(Blocks.SNOW)) return s.getValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS);
        if (s.is(Registry.GROOMED_SNOW.get()) || s.is(Registry.ICY_SNOW.get()))
            return s.getValue(dev.blockside.mod.block.ResortSnowBlock.LAYERS);
        return 0;
    }

    public static SurfaceKind surfaceKind(BlockState s) {
        if (s.is(Registry.GROOMED_SNOW.get())) return SurfaceKind.GROOMED;
        if (s.is(Registry.ICY_SNOW.get()) || s.is(Blocks.ICE) || s.is(Blocks.PACKED_ICE) || s.is(Blocks.BLUE_ICE)) return SurfaceKind.ICE;
        if (s.is(Blocks.SNOW) || s.is(Blocks.POWDER_SNOW)) return SurfaceKind.POWDER;
        if (s.is(Blocks.SNOW_BLOCK)) return SurfaceKind.PACKED;
        return SurfaceKind.BARE;
    }

    /** Applies one construction placement. Returns null on success or why it couldn't. */
    public static String apply(ServerLevel level, Placement p) {
        BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
        if (!level.isLoaded(pos)) return "Waiting for the area to load";
        BlockState cur = level.getBlockState(pos);
        SiteProbe.Kind k = kind(cur);
        String spec = p.spec();
        if (spec.equals("@clear")) {
            if (k == SiteProbe.Kind.AIR) return null;
            if (k == SiteProbe.Kind.BUILT) return cur.is(Registry.JOB_BLOCK.get()) ? null : "Someone built inside the zone";
            if (k == SiteProbe.Kind.FLUID) return "Water flowed into the zone";
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            return null;
        }
        if (spec.startsWith("?")) {
            if (k == SiteProbe.Kind.AIR || k == SiteProbe.Kind.NATURAL) set(level, pos, BlockIO.parse(spec.substring(1)), p.rotation());
            return null;
        }
        BlockState target = BlockIO.rotate(BlockIO.parse(spec), p.rotation());
        if (cur.equals(target)) return null;
        if (k == SiteProbe.Kind.BUILT && !cur.is(Registry.JOB_BLOCK.get())) return "Blocked by a player-built block";
        if (cur.is(Registry.JOB_BLOCK.get())) return null; // never overwrite the job block
        set(level, pos, target, 0);
        return null;
    }

    private static void set(ServerLevel level, BlockPos pos, BlockState s, int rot) {
        BlockState r = BlockIO.rotate(s, rot);
        level.setBlock(pos, r, 3);
        level.playSound(null, pos, r.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.5F, 0.9F + level.getRandom().nextFloat() * 0.2F);
    }

    public static String spec(ServerLevel level, int x, int y, int z) { return BlockIO.spec(level.getBlockState(new BlockPos(x, y, z))); }

    public static void setSpec(ServerLevel level, int x, int y, int z, String spec) {
        level.setBlock(new BlockPos(x, y, z), BlockIO.parse(spec), 2);
    }
}
