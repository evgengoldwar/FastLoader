package fastloader.newgen.nether;

import java.util.Random;

import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.entity.monster.EntityGhast;
import net.minecraft.entity.monster.EntityMagmaCube;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.BiomeDictionary;

/** A modern Nether biome. Terrain, surface and features are handled by {@link NetherChunkProviderNG}. */
public class NetherBiome extends BiomeGenBase {

    /** Name used for the modern sound events (music.nether.X, ambient.X.loop ...). */
    public final String soundName;
    /** Fog colour, 0xRRGGBB. */
    public final int fogColor;

    public NetherBiome(int id, String name, String soundName, int fogColor) {
        super(id, true);
        this.soundName = soundName;
        this.fogColor = fogColor;
        setBiomeName(name);
        setDisableRain();
        setTemperatureRainfall(2.0F, 0.0F);
        setColor(fogColor);
        topBlock = Blocks.netherrack;
        fillerBlock = Blocks.netherrack;
        field_150604_aj = 0;
        field_76754_C = 0;
        spawnableMonsterList.clear();
        spawnableCreatureList.clear();
        spawnableWaterCreatureList.clear();
        spawnableCaveCreatureList.clear();
        BiomeDictionary
            .registerBiomeType(this, BiomeDictionary.Type.NETHER, BiomeDictionary.Type.HOT, BiomeDictionary.Type.DRY);
    }

    public NetherBiome mob(Class<? extends net.minecraft.entity.EntityLiving> cls, int weight, int min, int max) {
        spawnableMonsterList.add(new SpawnListEntry(cls, weight, min, max));
        return this;
    }

    @Override
    public void decorate(World world, Random rand, int x, int z) {
        // handled by NetherChunkProviderNG
    }

    @Override
    public int getSkyColorByTemp(float temp) {
        return fogColor;
    }

    public static NetherBiome crimsonForest(int id) {
        return new NetherBiome(id, "Crimson Forest", "crimson_forest", 0x330303).mob(EntityPigZombie.class, 100, 2, 4)
            .mob(EntityMagmaCube.class, 2, 1, 2);
    }

    public static NetherBiome warpedForest(int id) {
        return new NetherBiome(id, "Warped Forest", "warped_forest", 0x1A051A).mob(EntityEnderman.class, 100, 1, 4);
    }

    public static NetherBiome soulSandValley(int id) {
        return new NetherBiome(id, "Soul Sand Valley", "soul_sand_valley", 0x1B4745).mob(EntitySkeleton.class, 20, 5, 5)
            .mob(EntityGhast.class, 50, 4, 4)
            .mob(EntityEnderman.class, 1, 4, 4);
    }

    public static NetherBiome basaltDeltas(int id) {
        return new NetherBiome(id, "Basalt Deltas", "basalt_deltas", 0x685F70).mob(EntityGhast.class, 40, 1, 1)
            .mob(EntityMagmaCube.class, 100, 2, 5);
    }

    public static NetherBiome netherWastes(int id) {
        return new NetherBiome(id, "Nether Wastes", "nether_wastes", 0x330808).mob(EntityGhast.class, 50, 4, 4)
            .mob(EntityPigZombie.class, 100, 4, 4)
            .mob(EntityMagmaCube.class, 2, 4, 4)
            .mob(EntityEnderman.class, 1, 4, 4);
    }

    /** Spawn list helper for the vanilla Hell biome when it is used as Nether Wastes. */
    public static void tweakVanillaHell() {
        BiomeGenBase.hell.topBlock = Blocks.netherrack;
        BiomeGenBase.hell.fillerBlock = Blocks.netherrack;
        if (BiomeGenBase.hell.getSpawnableList(EnumCreatureType.monster)
            .stream()
            .noneMatch(e -> e.entityClass == EntityEnderman.class)) {
            BiomeGenBase.hell.getSpawnableList(EnumCreatureType.monster)
                .add(new SpawnListEntry(EntityEnderman.class, 1, 4, 4));
        }
    }
}
