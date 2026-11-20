package dev.blockside.mod.block;

import dev.blockside.mod.server.ResortServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The block in the middle of a construction zone. Right-click: project, crew quote, hire button,
 * progress. After completion it stays as the building's service desk / the lift's control panel.
 * Unbreakable so the zone can't be orphaned.
 */
public class JobBlock extends Block {
    public JobBlock(Properties p) { super(p); }

    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) ResortServer.jobBlockUsed(sl, pos, sp);
        return InteractionResult.SUCCESS;
    }
}
