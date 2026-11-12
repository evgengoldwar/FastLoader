package fastloader.newgen.world;

import net.minecraft.world.World;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.WorldChunkManager;
import net.minecraft.world.chunk.IChunkProvider;

import fastloader.newgen.overworld.OverworldBiomeSource;
import fastloader.newgen.overworld.OverworldChunkProviderNG;

/**
 * "New Generation" world type: modern multi-noise overworld (continents, erosion, peaks & valleys, noise caves,
 * aquifers) and the modern Nether with its five biomes.
 */
public class NewGenWorldType extends WorldType {

    public static final String NAME = "newgeneration";

    public NewGenWorldType() {
        super(NAME);
    }

    @Override
    public WorldChunkManager getChunkManager(World world) {
        return new OverworldBiomeSource(world.getSeed());
    }

    @Override
    public IChunkProvider getChunkGenerator(World world, String options) {
        return new OverworldChunkProviderNG(
            world,
            world.getSeed(),
            world.getWorldInfo()
                .isMapFeaturesEnabled());
    }

    @Override
    public int getMinimumSpawnHeight(World world) {
        return 63;
    }

    @Override
    public double getHorizon(World world) {
        return 63.0;
    }

    @Override
    public float getCloudHeight() {
        return 192.0F;
    }
}
