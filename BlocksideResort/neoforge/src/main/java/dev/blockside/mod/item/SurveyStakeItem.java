package dev.blockside.mod.item;

import dev.blockside.mod.server.TrailTools;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Right-click ground to add a centerline point; sneak + right-click to clear the draft. */
public class SurveyStakeItem extends Item {
    public SurveyStakeItem(Properties p) { super(p); }

    @Override public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel sl && ctx.getPlayer() instanceof ServerPlayer sp)
            TrailTools.stakeUsed(sl, sp, ctx.getClickedPos());
        return InteractionResult.SUCCESS;
    }
}
