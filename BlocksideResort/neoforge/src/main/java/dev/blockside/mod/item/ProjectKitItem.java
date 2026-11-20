package dev.blockside.mod.item;

import dev.blockside.mod.server.Placement;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

/**
 * A bought project. Carries the purchase id (the resort ledger knows what it is and whether it's been
 * used). Right-click the ground to preview the footprint; click again to rotate; confirm in chat.
 */
public class ProjectKitItem extends Item {
    public ProjectKitItem(Properties p) { super(p); }

    public static ItemStack create(String purchaseId, String catalogId, String name) {
        ItemStack s = new ItemStack(dev.blockside.mod.Registry.PROJECT_KIT.get());
        CompoundTag t = new CompoundTag();
        t.putString("purchase", purchaseId);
        t.putString("project", catalogId);
        s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        s.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("Project: " + name));
        return s;
    }

    public static String purchaseId(ItemStack s) {
        CustomData d = s.get(DataComponents.CUSTOM_DATA);
        return d == null ? "" : d.copyTag().getStringOr("purchase", "");
    }

    @Override public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel() instanceof ServerLevel sl && ctx.getPlayer() instanceof ServerPlayer sp)
            Placement.kitUsed(sl, sp, ctx.getItemInHand(), ctx.getClickedPos(), ctx.getClickedFace());
        return InteractionResult.SUCCESS;
    }
}
