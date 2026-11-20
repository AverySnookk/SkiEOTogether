package dev.blockside.mod.item;

import dev.blockside.mod.ClientRequests;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click to clip in / out. Skiing itself runs on the client for responsive control. */
public class SkisItem extends Item {
    public SkisItem(Properties p) { super(p); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel)) ClientRequests.toggleSkis = true;
        return InteractionResult.SUCCESS;
    }
}
