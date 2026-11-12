package fastloader.newgen;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;

import cpw.mods.fml.common.Loader;
import fastloader.Config;
import fastloader.FastLoader;
import fastloader.newgen.block.NGBlocks;
import fastloader.newgen.nether.NetherBiomes;
import fastloader.newgen.overworld.OverworldBiomes;
import fastloader.newgen.world.NewGenWorldType;

/** Entry point of the "New Generation" world type. */
public final class NewGen {

    public static WorldType WORLD_TYPE;

    private NewGen() {}

    public static void preInit() {
        NGBlocks.init();
        NetherBiomes.init();
        OverworldBiomes.init();
    }

    public static void init() {
        WORLD_TYPE = Loader.isModLoaded("cubicchunks") ? createCubicType() : new NewGenWorldType();
        FastLoader.LOG.info(
            "[NewGen] Registered world type '{}' (cubic: {})",
            WORLD_TYPE.getWorldTypeName(),
            Loader.isModLoaded("cubicchunks"));
    }

    private static WorldType createCubicType() {
        try {
            return (WorldType) Class.forName("fastloader.newgen.world.NewGenCubicWorldType")
                .getConstructor()
                .newInstance();
        } catch (Throwable t) {
            FastLoader.LOG.error("[NewGen] Could not create the Cubic Chunks world type, using the 256-high one", t);
            return new NewGenWorldType();
        }
    }

    public static boolean isNewGenType(WorldType type) {
        return type != null && type == WORLD_TYPE;
    }

    public static boolean isNewGenWorld(World world) {
        return world != null && world.getWorldInfo() != null
            && isNewGenType(
                world.getWorldInfo()
                    .getTerrainType());
    }

    /** Whether the modern Nether should be used for this world's Nether dimension. */
    public static boolean useNewNether(WorldType type) {
        return !Config.netherOnlyInNewGenWorlds || isNewGenType(type);
    }
}
