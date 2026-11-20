package dev.blockside.mod.server;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/** Chat panels with clickable buttons. Buttons run /resort commands so the server validates everything. */
public final class Ui {
    private Ui() {}

    public static MutableComponent text(String s) { return Component.literal(s); }
    public static MutableComponent text(String s, ChatFormatting f) { return Component.literal(s).withStyle(f); }
    public static MutableComponent title(String s) { return Component.literal("— " + s + " —").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD); }

    public static MutableComponent button(String label, String command, String hover) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }

    public static MutableComponent disabled(String label, String why) {
        return Component.literal("[" + label + "]").withStyle(st -> st.withColor(ChatFormatting.DARK_GRAY)
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(why))));
    }

    public static String bar(double f) {
        int n = (int) Math.round(Math.max(0, Math.min(1, f)) * 20);
        return "█".repeat(n) + "░".repeat(20 - n) + " " + Math.round(f * 100) + "%";
    }

    public static String money(long v) { return (v < 0 ? "-$" : "$") + String.format("%,d", Math.abs(v)); }
}
