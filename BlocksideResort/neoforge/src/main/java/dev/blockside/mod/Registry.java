package dev.blockside.mod;

import dev.blockside.mod.block.JobBlock;
import dev.blockside.mod.block.ResortSnowBlock;
import dev.blockside.mod.block.SnowGunBlock;
import dev.blockside.mod.item.ProjectKitItem;
import dev.blockside.mod.item.ResortTabletItem;
import dev.blockside.mod.item.SkisItem;
import dev.blockside.mod.item.SurveyStakeItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;
import java.util.function.Supplier;

public final class Registry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, BlocksideMod.MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, BlocksideMod.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlocksideMod.MODID);

    public static final DeferredHolder<Block, JobBlock> JOB_BLOCK = block("job_block", JobBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(-1.0F, 3600000.0F).sound(SoundType.WOOD));
    public static final DeferredHolder<Block, SnowGunBlock> SNOW_GUN = block("snow_gun", SnowGunBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0F).sound(SoundType.METAL).noOcclusion());
    public static final DeferredHolder<Block, ResortSnowBlock> GROOMED_SNOW = block("groomed_snow", ResortSnowBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.2F).sound(SoundType.SNOW));
    public static final DeferredHolder<Block, ResortSnowBlock> ICY_SNOW = block("icy_snow", ResortSnowBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.3F).sound(SoundType.GLASS).friction(0.9F));
    public static final DeferredHolder<Block, Block> TOWER_SECTION = block("tower_section", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion());

    public static final DeferredHolder<Item, BlockItem> SNOW_GUN_ITEM = blockItem("snow_gun", SNOW_GUN);
    public static final DeferredHolder<Item, BlockItem> TOWER_SECTION_ITEM = blockItem("tower_section", TOWER_SECTION);
    public static final DeferredHolder<Item, ProjectKitItem> PROJECT_KIT = item("project_kit", ProjectKitItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<Item, SkisItem> SKIS = item("skis", SkisItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<Item, ResortTabletItem> TABLET = item("resort_tablet", ResortTabletItem::new, new Item.Properties().stacksTo(1));
    public static final DeferredHolder<Item, SurveyStakeItem> SURVEY_STAKE = item("survey_stake", SurveyStakeItem::new, new Item.Properties().stacksTo(1));

    public static final Supplier<CreativeModeTab> TAB = TABS.register("blockside", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.blockside"))
            .icon(() -> new ItemStack(SKIS.get()))
            .displayItems((params, out) -> {
                out.accept(TABLET.get());
                out.accept(SKIS.get());
                out.accept(SURVEY_STAKE.get());
                out.accept(SNOW_GUN_ITEM.get());
            })
            .build());

    private static <B extends Block> DeferredHolder<Block, B> block(String name, Function<BlockBehaviour.Properties, B> f, BlockBehaviour.Properties props) {
        return BLOCKS.register(name, id -> f.apply(props.setId(ResourceKey.create(Registries.BLOCK, id))));
    }

    private static <I extends Item> DeferredHolder<Item, I> item(String name, Function<Item.Properties, I> f, Item.Properties props) {
        return ITEMS.register(name, id -> f.apply(props.setId(ResourceKey.create(Registries.ITEM, id))));
    }

    private static DeferredHolder<Item, BlockItem> blockItem(String name, DeferredHolder<Block, ? extends Block> block) {
        return ITEMS.register(name, id -> new BlockItem(block.get(),
                new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
    }

    private Registry() {}
}
