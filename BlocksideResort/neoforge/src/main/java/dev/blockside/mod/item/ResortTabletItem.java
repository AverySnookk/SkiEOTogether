package dev.blockside.mod.item;

import dev.blockside.mod.ClientRequests;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Opens the resort management screen. */
public class ResortTabletItem extends Item {
    public ResortTabletItem(Properties p) { super(p); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(level instanceof ServerLevel)) ClientRequests.openDashboard = true;
        return InteractionResult.SUCCESS;
    }
}
