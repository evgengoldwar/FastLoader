package fastloader.newgen.block;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;

import cpw.mods.fml.common.registry.GameRegistry;
import fastloader.FastLoader;

/**
 * Resolves every block the New Generation world type needs. Et Futurum Requiem blocks are preferred; when one is
 * missing (EFR not installed, or the feature disabled in its config - crimson/warped blocks are "experimental"
 * there) this mod registers its own replacement so the world always generates complete.
 */
public final class NGBlocks {

    private static final String EFR = "etfuturum";

    // ---- Nether ----
    public static BlockState CRIMSON_NYLIUM, WARPED_NYLIUM;
    public static BlockState CRIMSON_STEM, WARPED_STEM;
    public static BlockState NETHER_WART_BLOCK, WARPED_WART_BLOCK;
    public static BlockState SHROOMLIGHT;
    public static BlockState CRIMSON_FUNGUS, WARPED_FUNGUS;
    public static BlockState CRIMSON_ROOTS, WARPED_ROOTS;
    public static BlockState NETHER_SPROUTS;
    public static BlockState WEEPING_VINES, TWISTING_VINES;
    public static BlockState BASALT, BLACKSTONE, GILDED_BLACKSTONE;
    public static BlockState SOUL_SOIL, MAGMA, BONE_BLOCK;
    public static BlockState NETHER_GOLD_ORE, ANCIENT_DEBRIS;

    // ---- Overworld ----
    public static BlockState DEEPSLATE, TUFF, CALCITE, SMOOTH_BASALT, AMETHYST, BUDDING_AMETHYST;
    public static BlockState COPPER_ORE, DEEPSLATE_COPPER_ORE, DEEPSLATE_COAL_ORE, DEEPSLATE_IRON_ORE,
        DEEPSLATE_GOLD_ORE, DEEPSLATE_REDSTONE_ORE, DEEPSLATE_LAPIS_ORE, DEEPSLATE_DIAMOND_ORE, DEEPSLATE_EMERALD_ORE;
    public static BlockState COARSE_DIRT, PODZOL, BLUE_ICE, PACKED_ICE, CORNFLOWER, LILY_OF_THE_VALLEY, MUD;
    public static BlockState CHERRY_LOG, CHERRY_LEAVES, SWEET_BERRY_BUSH;

    private NGBlocks() {}

    private static Block efr(String name) {
        Block b = GameRegistry.findBlock(EFR, name);
        return b == Blocks.air ? null : b;
    }

    private static BlockState efr(String name, int meta) {
        Block b = efr(name);
        return b == null ? null : BlockState.of(b, meta);
    }

    private static <T extends Block> T register(T block, String name, boolean metaItem) {
        if (metaItem) GameRegistry.registerBlock(block, NGItemBlock.class, name);
        else GameRegistry.registerBlock(block, name);
        FastLoader.LOG.info("[NewGen] Registered fallback block {}", name);
        return block;
    }

    /** Called in preInit, after Et Futurum Requiem registered its blocks. */
    public static void init() {
        // nylium
        Block nylium = efr("nylium");
        if (nylium != null) {
            CRIMSON_NYLIUM = BlockState.of(nylium, 0);
            WARPED_NYLIUM = BlockState.of(nylium, 1);
        } else {
            Block b = register(
                new NGBlockSimple(Material.rock, true, "crimson_nylium", "warped_nylium").setDrop(Blocks.netherrack)
                    .setHardness(0.4F)
                    .setStepSound(Block.soundTypeStone),
                "nylium",
                true);
            CRIMSON_NYLIUM = BlockState.of(b, 0);
            WARPED_NYLIUM = BlockState.of(b, 1);
        }

        CRIMSON_STEM = orElse(efr("crimson_stem", 0), () -> pillar("crimson_stem", Material.wood, 2.0F));
        WARPED_STEM = orElse(efr("warped_stem", 0), () -> pillar("warped_stem", Material.wood, 2.0F));

        Block wart = efr("nether_wart");
        if (wart != null) {
            NETHER_WART_BLOCK = BlockState.of(wart, 0);
            WARPED_WART_BLOCK = BlockState.of(wart, 1);
        } else {
            Block b = register(
                new NGBlockSimple(Material.grass, false, "nether_wart_block", "warped_wart_block").setHardness(1.0F)
                    .setStepSound(Block.soundTypeGrass),
                "wart_block",
                true);
            NETHER_WART_BLOCK = BlockState.of(b, 0);
            WARPED_WART_BLOCK = BlockState.of(b, 1);
        }

        SHROOMLIGHT = orElse(
            efr("shroomlight", 0),
            () -> BlockState.of(
                register(
                    new NGBlockSimple(Material.gourd, false, "shroomlight").setHardness(1.0F)
                        .setLightLevel(1.0F)
                        .setStepSound(Block.soundTypeGrass),
                    "shroomlight",
                    false)));

        Block fungus = efr("nether_fungus");
        Block roots = efr("nether_roots");
        if (fungus != null && roots != null) {
            CRIMSON_FUNGUS = BlockState.of(fungus, 0);
            WARPED_FUNGUS = BlockState.of(fungus, 1);
            CRIMSON_ROOTS = BlockState.of(roots, 0);
            WARPED_ROOTS = BlockState.of(roots, 1);
        } else {
            Block f = register(new NGBlockPlant("crimson_fungus", "warped_fungus"), "fungus", true);
            Block r = register(new NGBlockPlant("crimson_roots", "warped_roots"), "roots", true);
            CRIMSON_FUNGUS = BlockState.of(f, 0);
            WARPED_FUNGUS = BlockState.of(f, 1);
            CRIMSON_ROOTS = BlockState.of(r, 0);
            WARPED_ROOTS = BlockState.of(r, 1);
        }
        NETHER_SPROUTS = orElse(
            efr("nether_sprouts", 0),
            () -> BlockState.of(register(new NGBlockPlant("nether_sprouts"), "nether_sprouts", false)));
        WEEPING_VINES = orElse(
            efr("weeping_vines", 0),
            () -> BlockState.of(register(new NGBlockVines("weeping_vines", false), "weeping_vines", false)));
        TWISTING_VINES = orElse(
            efr("twisting_vines", 0),
            () -> BlockState.of(register(new NGBlockVines("twisting_vines", true), "twisting_vines", false)));

        BASALT = orElse(efr("basalt", 0), () -> pillar("basalt", Material.rock, 1.25F));
        BLACKSTONE = orElse(efr("blackstone", 0), () -> simple("blackstone", Material.rock, 1.5F));
        GILDED_BLACKSTONE = orElse(efr("gilded_blackstone", 0), () -> BLACKSTONE);
        SOUL_SOIL = orElse(efr("soul_soil", 0), () -> {
            BlockState s = simple("soul_soil", Material.sand, 0.5F);
            s.block.setStepSound(Block.soundTypeSand);
            return s;
        });
        MAGMA = orElse(efr("magma", 0), () -> {
            BlockState s = simple("magma", Material.rock, 0.5F);
            s.block.setLightLevel(0.2F);
            return s;
        });
        BONE_BLOCK = orElse(efr("bone", 0), () -> pillar("bone_block", Material.rock, 2.0F));
        NETHER_GOLD_ORE = orElse(efr("nether_gold_ore", 0), () -> BlockState.of(Blocks.quartz_ore));
        ANCIENT_DEBRIS = efr("ancient_debris", 0);

        // overworld blocks - these only fall back to vanilla equivalents
        DEEPSLATE = orElse(efr("deepslate", 0), () -> BlockState.of(Blocks.stone));
        TUFF = efr("tuff", 0);
        CALCITE = efr("calcite", 0);
        SMOOTH_BASALT = efr("smooth_basalt", 0);
        AMETHYST = efr("amethyst_block", 0);
        BUDDING_AMETHYST = efr("budding_amethyst", 0);
        COPPER_ORE = efr("copper_ore", 0);
        DEEPSLATE_COPPER_ORE = orElse(efr("deepslate_copper_ore", 0), () -> COPPER_ORE);
        DEEPSLATE_COAL_ORE = orElse(efr("deepslate_coal_ore", 0), () -> BlockState.of(Blocks.coal_ore));
        DEEPSLATE_IRON_ORE = orElse(efr("deepslate_iron_ore", 0), () -> BlockState.of(Blocks.iron_ore));
        DEEPSLATE_GOLD_ORE = orElse(efr("deepslate_gold_ore", 0), () -> BlockState.of(Blocks.gold_ore));
        DEEPSLATE_REDSTONE_ORE = orElse(efr("deepslate_redstone_ore", 0), () -> BlockState.of(Blocks.redstone_ore));
        DEEPSLATE_LAPIS_ORE = orElse(efr("deepslate_lapis_ore", 0), () -> BlockState.of(Blocks.lapis_ore));
        DEEPSLATE_DIAMOND_ORE = orElse(efr("deepslate_diamond_ore", 0), () -> BlockState.of(Blocks.diamond_ore));
        DEEPSLATE_EMERALD_ORE = orElse(efr("deepslate_emerald_ore", 0), () -> BlockState.of(Blocks.emerald_ore));
        COARSE_DIRT = orElse(efr("coarse_dirt", 0), () -> BlockState.of(Blocks.dirt, 1));
        PODZOL = BlockState.of(Blocks.dirt, 2);
        BLUE_ICE = orElse(efr("blue_ice", 0), () -> BlockState.of(Blocks.packed_ice));
        PACKED_ICE = BlockState.of(Blocks.packed_ice);
        CORNFLOWER = efr("cornflower", 0);
        LILY_OF_THE_VALLEY = efr("lily_of_the_valley", 0);
        MUD = efr("mud", 0);
        CHERRY_LOG = efr("cherry_log", 0);
        CHERRY_LEAVES = efr("leaves", 1);
        SWEET_BERRY_BUSH = efr("sweet_berry_bush", 3);

        FastLoader.LOG.info(
            "[NewGen] Blocks: nylium={}, stems={}/{}, basalt={}, blackstone={}, soul_soil={}, deepslate={}",
            CRIMSON_NYLIUM,
            CRIMSON_STEM,
            WARPED_STEM,
            BASALT,
            BLACKSTONE,
            SOUL_SOIL,
            DEEPSLATE);
    }

    private static BlockState pillar(String name, Material m, float hardness) {
        NGBlockPillar b = new NGBlockPillar(m, name);
        b.setHardness(hardness);
        b.setStepSound(m == Material.wood ? Block.soundTypeWood : Block.soundTypeStone);
        return BlockState.of(register(b, name, false));
    }

    private static BlockState simple(String name, Material m, float hardness) {
        NGBlockSimple b = new NGBlockSimple(m, false, name);
        b.setHardness(hardness);
        b.setStepSound(Block.soundTypeStone);
        return BlockState.of(register(b, name, false));
    }

    private static BlockState orElse(BlockState s, java.util.function.Supplier<BlockState> fallback) {
        return s != null ? s : fallback.get();
    }

    /** True for any block that the nether generators treat as natural nether "stone". */
    public static boolean isNetherBase(Block b) {
        return b == Blocks.netherrack || b == BASALT.block
            || b == BLACKSTONE.block
            || b == Blocks.soul_sand
            || b == SOUL_SOIL.block
            || b == CRIMSON_NYLIUM.block
            || b == MAGMA.block;
    }
}
