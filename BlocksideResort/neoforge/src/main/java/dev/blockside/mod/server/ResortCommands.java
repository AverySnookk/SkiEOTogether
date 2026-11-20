package dev.blockside.mod.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.blockside.core.ActionResult;
import dev.blockside.core.Facility;
import dev.blockside.core.HireResult;
import dev.blockside.core.LiftRecord;
import dev.blockside.core.Pos;
import dev.blockside.core.Resort;
import dev.blockside.core.Trail;
import dev.blockside.core.catalog.CatalogEntry;
import dev.blockside.core.catalog.EntryKind;
import dev.blockside.core.construction.Job;
import dev.blockside.core.economy.TxKind;
import dev.blockside.core.snow.SnowTile;
import dev.blockside.mod.Registry;
import dev.blockside.mod.item.ProjectKitItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** /resort … — every client action goes through here and is validated server-side. */
public final class ResortCommands {
    private ResortCommands() {}

    interface PlayerAction { void run(ServerPlayer p, Resort r, CommandContext<CommandSourceStack> c) throws CommandSyntaxException; }

    private static int run(CommandContext<CommandSourceStack> c, PlayerAction a) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Resort r = ResortServer.resort();
        if (r == null) return 0;
        a.run(p, r, c);
        ResortServer.broadcastNotices();
        ResortServer.sync();
        return 1;
    }

    private static int admin(CommandContext<CommandSourceStack> c, PlayerAction a) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!p.isCreative()) { p.sendSystemMessage(Ui.text("Admin/test commands need creative mode.", ChatFormatting.RED)); return 0; }
        return run(c, a);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lit(String s) { return Commands.literal(s); }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(lit("resort")
                .executes(c -> run(c, (p, r, x) -> Panels.dashboard(p)))
                .then(lit("claim").executes(c -> run(c, (p, r, x) -> {
                    var b = p.blockPosition();
                    ResortServer.reply(p, r.claim(ResortServer.who(p), new Pos(b.getX(), b.getY(), b.getZ())));
                    if (r.claimed()) giveStarterKit(p);
                })))
                .then(lit("kit").executes(c -> run(c, (p, r, x) -> giveStarterKit(p))))
                .then(lit("catalog").executes(c -> run(c, (p, r, x) -> Panels.catalog(p))))
                .then(lit("buy").then(Commands.argument("id", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> buy(p, r, StringArgumentType.getString(x, "id"))))))
                .then(lit("refund").executes(c -> run(c, (p, r, x) -> {
                    ItemStack held = p.getMainHandItem();
                    if (!held.is(Registry.PROJECT_KIT.get())) { p.sendSystemMessage(Ui.text("Hold the project kit to refund.", ChatFormatting.RED)); return; }
                    ActionResult res = r.refund(ProjectKitItem.purchaseId(held), ResortServer.who(p), ResortServer.now());
                    if (res.ok()) held.shrink(1);
                    ResortServer.reply(p, res);
                })))
                .then(lit("place").then(Commands.argument("action", StringArgumentType.word())
                        .executes(c -> run(c, (p, r, x) -> Placement.command(p, StringArgumentType.getString(x, "action"))))))
                .then(lit("hire").then(Commands.argument("job", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> {
                    String id = StringArgumentType.getString(x, "job");
                    Job j = r.jobs.get(id);
                    if (j != null && p.distanceToSqr(j.anchor.x(), j.anchor.y(), j.anchor.z()) > 32 * 32) {
                        p.sendSystemMessage(Ui.text("Go to the job site's center block to hire a crew.", ChatFormatting.RED)); return;
                    }
                    HireResult h = r.hire(id, ResortServer.who(p), ResortServer.now());
                    p.sendSystemMessage(Ui.text(h.message, h.ok() ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
                }))))
                .then(lit("jobs").executes(c -> run(c, (p, r, x) -> { for (Job j : r.jobs.values()) Panels.job(p, j); })))
                .then(lit("lifts").executes(c -> run(c, (p, r, x) -> {
                    if (r.lifts.isEmpty()) p.sendSystemMessage(Ui.text("No lifts yet.", ChatFormatting.GRAY));
                    for (LiftRecord l : r.lifts.values()) Panels.lift(p, l);
                })))
                .then(lit("lift")
                        .then(lit("open").then(Commands.argument("id", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> setLift(p, r, StringArgumentType.getString(x, "id"), true)))))
                        .then(lit("close").then(Commands.argument("id", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> setLift(p, r, StringArgumentType.getString(x, "id"), false))))))
                .then(lit("facility").then(lit("toggle").then(Commands.argument("id", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> {
                    Facility f = r.facilities.get(StringArgumentType.getString(x, "id"));
                    if (f == null || !r.isMember(ResortServer.who(p))) return;
                    f.open = !f.open;
                    Panels.facility(p, f);
                })))))
                .then(lit("groomer").executes(c -> run(c, (p, r, x) -> GroomerRuntime.dispatch(ResortServer.level(), p, r))))
                .then(lit("snow").executes(c -> run(c, (p, r, x) -> snowHere(p, r))))
                .then(lit("trail")
                        .then(lit("list").executes(c -> run(c, (p, r, x) -> TrailTools.list(p))))
                        .then(lit("preview").executes(c -> run(c, (p, r, x) -> TrailTools.preview(p, 9)))
                                .then(Commands.argument("width", DoubleArgumentType.doubleArg(3, 31)).executes(c -> run(c, (p, r, x) -> TrailTools.preview(p, DoubleArgumentType.getDouble(x, "width"))))))
                        .then(lit("confirm").then(Commands.argument("mode", StringArgumentType.word())
                                .then(Commands.argument("width", DoubleArgumentType.doubleArg(3, 31))
                                        .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> run(c, (p, r, x) ->
                                                TrailTools.confirm(p, StringArgumentType.getString(x, "mode"), DoubleArgumentType.getDouble(x, "width"), StringArgumentType.getString(x, "name"))))))))
                        .then(lit("undo").then(Commands.argument("job", StringArgumentType.word()).executes(c -> run(c, (p, r, x) -> TrailTools.undo(p, StringArgumentType.getString(x, "job"))))))
                        .then(lit("open").then(Commands.argument("id", StringArgumentType.greedyString()).executes(c -> run(c, (p, r, x) -> setTrail(p, r, StringArgumentType.getString(x, "id"), true)))))
                        .then(lit("close").then(Commands.argument("id", StringArgumentType.greedyString()).executes(c -> run(c, (p, r, x) -> setTrail(p, r, StringArgumentType.getString(x, "id"), false))))))
                .then(lit("testhill").executes(c -> admin(c, (p, r, x) -> {
                    p.sendSystemMessage(Ui.text("This reshapes a 61×105 area around you (uphill = north) and replaces what's there. Use a test world.", ChatFormatting.GOLD));
                    p.sendSystemMessage(Ui.button("Build the test hill here", "/resort testhill confirm", "Stand on flat ground at the bottom of where the hill should go"));
                })).then(lit("confirm").executes(c -> admin(c, (p, r, x) -> TestHill.build(p)))))
                .then(lit("selftest").requires(src -> src.getEntity() == null).then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("y", IntegerArgumentType.integer()).then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> {
                            SelfTest.start(new net.minecraft.core.BlockPos(IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"), IntegerArgumentType.getInteger(c, "z")));
                            c.getSource().sendSuccess(() -> Ui.text("selftest started"), false);
                            return 1;
                        })))))
                .then(lit("status").requires(src -> src.getEntity() == null).executes(c -> {
                    for (String line : SelfTest.status()) c.getSource().sendSuccess(() -> Ui.text(line), false);
                    return 1;
                }))
                .then(lit("console").requires(src -> src.getEntity() == null)
                        .then(lit("openlift").executes(c -> { ResortServer.resort().lifts.values().forEach(l -> l.open = true); return 1; }))
                        .then(lit("groom").executes(c -> { int n = SelfTest.groomBunnyHill(); c.getSource().sendSuccess(() -> Ui.text("groomed " + n + " tiles"), false); return 1; }))
                        .then(lit("skip").then(Commands.argument("hours", IntegerArgumentType.integer(1, 48)).executes(c -> {
                            ResortServer.setClock(ResortServer.now() + IntegerArgumentType.getInteger(c, "hours") * 1000L); return 1; })))
                        .then(lit("save").executes(c -> { ResortServer.save(); return 1; })))
                .then(lit("save").executes(c -> admin(c, (p, r, x) -> { ResortServer.save(); p.sendSystemMessage(Ui.text("Resort saved.", ChatFormatting.GREEN)); })))
                .then(lit("admin")
                        .then(lit("cash").then(Commands.argument("amount", IntegerArgumentType.integer()).executes(c -> admin(c, (p, r, x) -> {
                            int a = IntegerArgumentType.getInteger(x, "amount");
                            if (a >= 0) r.ledger.earn(a, TxKind.ADMIN, "admin", ResortServer.now()); else r.ledger.charge(-a, TxKind.ADMIN, "admin", ResortServer.now());
                        }))))
                        .then(lit("xp").then(Commands.argument("amount", IntegerArgumentType.integer(1)).executes(c -> admin(c, (p, r, x) ->
                                r.progression.grantOnce("admin:" + (++r.counter), IntegerArgumentType.getInteger(x, "amount"), "admin grant")))))
                        .then(lit("rep").then(Commands.argument("value", DoubleArgumentType.doubleArg(0, 100)).executes(c -> admin(c, (p, r, x) ->
                                r.reputation.set(DoubleArgumentType.getDouble(x, "value"))))))
                        .then(lit("weather").then(Commands.argument("kind", StringArgumentType.word()).executes(c -> admin(c, (p, r, x) -> weather(p, r, StringArgumentType.getString(x, "kind"), 6)))
                                .then(Commands.argument("hours", IntegerArgumentType.integer(1, 240)).executes(c -> admin(c, (p, r, x) ->
                                        weather(p, r, StringArgumentType.getString(x, "kind"), IntegerArgumentType.getInteger(x, "hours")))))))
                        .then(lit("skip").then(Commands.argument("hours", IntegerArgumentType.integer(1, 48)).executes(c -> admin(c, (p, r, x) -> {
                            var l = ResortServer.level();
                            ResortServer.setClock(l.getOverworldClockTime() + IntegerArgumentType.getInteger(x, "hours") * 1000L);
                            p.sendSystemMessage(Ui.text("Skipped ahead; the resort simulates the missed hours.", ChatFormatting.GREEN));
                        }))))
                        .then(lit("season").then(Commands.argument("season", StringArgumentType.word()).executes(c -> admin(c, (p, r, x) -> season(p, r, StringArgumentType.getString(x, "season"))))))));
    }

    private static void giveStarterKit(ServerPlayer p) {
        for (var item : new net.minecraft.world.item.Item[]{Registry.TABLET.get(), Registry.SKIS.get(), Registry.SURVEY_STAKE.get()})
            if (!p.getInventory().contains(new ItemStack(item))) give(p, new ItemStack(item));
        p.sendSystemMessage(Ui.text("Resort Tablet (dashboard), Skis (right-click to clip in) and Survey Stake (mark runs) are in your inventory.", ChatFormatting.AQUA));
    }

    private static void give(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(p.level(), p.getX(), p.getY(), p.getZ(), s));
    }

    private static void buy(ServerPlayer p, Resort r, String id) {
        ActionResult res = r.buy(id, ResortServer.who(p), ResortServer.now());
        ResortServer.reply(p, res);
        if (!res.ok()) return;
        CatalogEntry e = r.catalog.get(id);
        if (e.kind == EntryKind.EQUIPMENT) {
            Identifier key = Identifier.tryParse(e.item);
            net.minecraft.world.item.Item item = key == null ? null : BuiltInRegistries.ITEM.getValue(key);
            if (item != null) give(p, new ItemStack(item));
            p.sendSystemMessage(Ui.text("Delivered " + e.name + " to your inventory.", ChatFormatting.GREEN));
        } else {
            give(p, ProjectKitItem.create(res.ref(), id, e.name));
            p.sendSystemMessage(Ui.text(e.kind == EntryKind.LIFT
                    ? "Right-click the ground where the bottom station goes, then where the top station goes."
                    : "Right-click the ground to preview. Click the same spot again to rotate.", ChatFormatting.YELLOW));
        }
    }

    private static void setLift(ServerPlayer p, Resort r, String id, boolean open) {
        LiftRecord l = r.lifts.get(id);
        if (l == null) { p.sendSystemMessage(Ui.text("No such lift.", ChatFormatting.RED)); return; }
        if (!r.isMember(ResortServer.who(p))) return;
        l.open = open;
        r.notice(ResortServer.who(p) + (open ? " opened " : " closed ") + l.name + ".");
    }

    private static void setTrail(ServerPlayer p, Resort r, String idOrName, boolean open) {
        Trail t = r.trails.get(idOrName);
        if (t == null) for (Trail k : r.trails.values()) if (k.name.equalsIgnoreCase(idOrName)) t = k;
        if (t == null) { p.sendSystemMessage(Ui.text("No such run.", ChatFormatting.RED)); return; }
        if (!r.isMember(ResortServer.who(p))) return;
        t.open = open;
        r.notice(ResortServer.who(p) + (open ? " opened " : " closed ") + t.name + ".");
    }

    private static void snowHere(ServerPlayer p, Resort r) {
        SnowTile t = r.snowpack.get(p.getBlockX(), p.getBlockZ());
        String w = r.weather.describe(r.config.climate, ResortServer.now(), p.getBlockY());
        if (t == null) { p.sendSystemMessage(Ui.text("Not on managed terrain. " + w, ChatFormatting.GRAY)); return; }
        p.sendSystemMessage(Ui.text(String.format("Snow: %.2f m deep (needs %.2f), SWE %.3f m, density %.0f kg/m³, liquid %.3f, ice %.0f%%, groomed %.0f%% → %s. %s",
                t.depth(), t.neededDepth(r.config.snow), t.swe, t.density, t.liquid, t.ice * 100, t.groomed * 100, t.kind(), w), ChatFormatting.WHITE));
    }

    private static void weather(ServerPlayer p, Resort r, String kind, int hours) {
        r.weather.override = kind.toUpperCase();
        r.weather.overrideUntil = ResortServer.now() + hours * 1000L;
        p.sendSystemMessage(Ui.text("Weather: " + r.weather.override + " for " + hours + " h.", ChatFormatting.GREEN));
    }

    private static void season(ServerPlayer p, Resort r, String s) {
        var c = r.config.climate;
        int offset = switch (s.toLowerCase()) { case "autumn" -> 0; case "winter" -> c.autumnDays; case "spring" -> c.autumnDays + c.winterDays;
            case "summer" -> c.autumnDays + c.winterDays + c.springDays; default -> -1; };
        if (offset < 0) { p.sendSystemMessage(Ui.text("autumn | winter | spring | summer", ChatFormatting.RED)); return; }
        var l = ResortServer.level();
        long year = Math.floorDiv(l.getOverworldClockTime(), 24000L) / c.yearDays();
        ResortServer.setClock(((year + 1) * c.yearDays() + offset) * 24000L + 2000); // 08:00 on that day, next cycle
        p.sendSystemMessage(Ui.text("It's now " + s + ", 08:00.", ChatFormatting.GREEN));
    }
}
