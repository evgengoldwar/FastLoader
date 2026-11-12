package fastloader.newgen.overworld;

import java.util.Arrays;
import java.util.List;

import net.minecraft.world.biome.BiomeGenBase;

import fastloader.newgen.world.NoiseBiomeSource;

/** Modern multi-noise overworld biome source. */
public class OverworldBiomeSource extends NoiseBiomeSource {

    public final Climate climate;
    private final ThreadLocal<Climate.Sample> sample = ThreadLocal.withInitial(Climate.Sample::new);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public OverworldBiomeSource(long seed) {
        this.climate = new Climate(seed);
        List spawn = getBiomesToSpawnIn();
        spawn.clear();
        spawn.addAll(
            Arrays.asList(
                OverworldBiomes.PLAINS,
                OverworldBiomes.FOREST,
                OverworldBiomes.TAIGA,
                OverworldBiomes.SAVANNA,
                OverworldBiomes.BIRCH_FOREST,
                OverworldBiomes.MEADOW,
                OverworldBiomes.SUNFLOWER_PLAINS,
                OverworldBiomes.FLOWER_FOREST,
                OverworldBiomes.DARK_FOREST,
                OverworldBiomes.JUNGLE,
                OverworldBiomes.SNOWY_PLAINS,
                OverworldBiomes.SNOWY_TAIGA,
                OverworldBiomes.DESERT,
                OverworldBiomes.CHERRY_GROVE));
    }

    @Override
    protected BiomeGenBase computeBiome(int qx, int qz) {
        Climate.Sample s = climate.sample(qx * 4 + 2, qz * 4 + 2, sample.get());
        return OverworldBiomeBuilder.pick(s);
    }
}
