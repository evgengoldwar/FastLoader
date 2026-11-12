package fastloader.newgen.overworld;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.MapGenCaves;
import net.minecraft.world.gen.MapGenRavine;
import net.minecraft.world.gen.feature.WorldGenDungeons;
import net.minecraft.world.gen.feature.WorldGenLakes;
import net.minecraft.world.gen.structure.MapGenMineshaft;
import net.minecraft.world.gen.structure.MapGenScatteredFeature;
import net.minecraft.world.gen.structure.MapGenStronghold;
import net.minecraft.world.gen.structure.MapGenVillage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.terraingen.InitMapGenEvent.EventType;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.eventhandler.Event;
import fastloader.Config;
import fastloader.newgen.noise.NoiseMath;
import fastloader.newgen.noise.NormalNoise;

/**
 * Modern-style overworld generator for 1.7.10 worlds (y 0-255, sea level 63). Terrain comes from the climate
 * splines + 3D noise ({@link OverworldNoise}), biomes from {@link OverworldBiomeSource}, surfaces from the biomes
 * themselves (so mesa bands, podzol, sand etc. behave like vanilla) and decoration from the biome decorators plus
 * whatever other mods add.
 */
public class OverworldChunkProviderNG implements IChunkProvider {

    private static final int H = 256;
    private static final int CW = 4, CHt = 8;
    private static final int NX = 16 / CW, NY = H / CHt;

    private final World world;
    private final long seed;
    private final boolean structures;
    private final boolean cubic;
    private final Random rand;
    private final OverworldBiomeSource biomes;
    private final TerrainShaper shaper = new TerrainShaper();
    private final OverworldNoise noise;
    private final Aquifer aquifer;
    private final NormalNoise surfaceNoise;

    private final MapGenBase caves = TerrainGen.getModdedMapGen(new MapGenCaves(), EventType.CAVE);
    private final MapGenBase ravines = TerrainGen.getModdedMapGen(new MapGenRavine(), EventType.RAVINE);
    private final MapGenStronghold stronghold = (MapGenStronghold) TerrainGen
        .getModdedMapGen(new MapGenStronghold(), EventType.STRONGHOLD);
    private final MapGenVillage village = (MapGenVillage) TerrainGen
        .getModdedMapGen(new MapGenVillage(), EventType.VILLAGE);
    private final MapGenMineshaft mineshaft = (MapGenMineshaft) TerrainGen
        .getModdedMapGen(new MapGenMineshaft(), EventType.MINESHAFT);
    private final MapGenScatteredFeature scattered = (MapGenScatteredFeature) TerrainGen
        .getModdedMapGen(new MapGenScatteredFeature(), EventType.SCATTERED_FEATURE);

    private final double[] density = new double[(NX + 1) * (NX + 1) * (NY + 1)];
    private final double[] surfaceAt = new double[256];
    private final OverworldNoise.Column[] columns = new OverworldNoise.Column[(NX + 1) * (NX + 1)];
    private final Climate.Sample sample = new Climate.Sample();
    private BiomeGenBase[] biomeBuf;

    public OverworldChunkProviderNG(World world, long seed, boolean structures) {
        this.world = world;
        this.seed = seed;
        this.structures = structures;
        this.cubic = Loader.isModLoaded("cubicchunks");
        this.rand = new Random(seed);
        this.biomes = world.getWorldChunkManager() instanceof OverworldBiomeSource obs ? obs
            : new OverworldBiomeSource(seed);
        this.noise = new OverworldNoise(seed);
        this.aquifer = new Aquifer(seed, !cubic);
        this.surfaceNoise = NormalNoise.create(seed, "surface", -6, 1, 1, 1);
        for (int i = 0; i < columns.length; i++) columns[i] = new OverworldNoise.Column();
    }

    // ------------------------------------------------------------------ terrain

    private void fillTerrain(int cx, int cz, Block[] blocks) {
        int bx = cx * 16, bz = cz * 16;
        int s = NX + 1;
        for (int ix = 0; ix <= NX; ix++) for (int iz = 0; iz <= NX; iz++) {
            int x = bx + ix * CW, z = bz + iz * CW;
            OverworldNoise.Column col = columns[ix * s + iz];
            biomes.climate.sample(x, z, sample);
            noise.column(shaper, sample, x, z, col);
            for (int iy = 0; iy <= NY; iy++) {
                density[(ix * s + iz) * (NY + 1) + iy] = noise.density(col, x, iy * CHt, z);
            }
        }
        // approximate surface per block column (bilinear offset)
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int ix = x / CW, iz = z / CW;
            double tx = (x % CW) / (double) CW, tz = (z % CW) / (double) CW;
            double o = NoiseMath.lerp2(
                tx,
                tz,
                columns[ix * s + iz].offset,
                columns[(ix + 1) * s + iz].offset,
                columns[ix * s + iz + 1].offset,
                columns[(ix + 1) * s + iz + 1].offset);
            surfaceAt[x * 16 + z] = TerrainShaper.surfaceHeight(o);
        }
        for (int ix = 0; ix < NX; ix++) for (int iz = 0; iz < NX; iz++) for (int iy = 0; iy < NY; iy++) {
            double d000 = density[(ix * s + iz) * (NY + 1) + iy];
            double d001 = density[(ix * s + iz + 1) * (NY + 1) + iy];
            double d100 = density[((ix + 1) * s + iz) * (NY + 1) + iy];
            double d101 = density[((ix + 1) * s + iz + 1) * (NY + 1) + iy];
            double d010 = density[(ix * s + iz) * (NY + 1) + iy + 1];
            double d011 = density[(ix * s + iz + 1) * (NY + 1) + iy + 1];
            double d110 = density[((ix + 1) * s + iz) * (NY + 1) + iy + 1];
            double d111 = density[((ix + 1) * s + iz + 1) * (NY + 1) + iy + 1];
            for (int py = 0; py < CHt; py++) {
                double ty = py / (double) CHt;
                int y = iy * CHt + py;
                for (int px = 0; px < CW; px++) {
                    double tx = px / (double) CW;
                    for (int pz = 0; pz < CW; pz++) {
                        double tz = pz / (double) CW;
                        double d = NoiseMath.lerp3(tx, ty, tz, d000, d100, d010, d110, d001, d101, d011, d111);
                        int x = ix * CW + px, z = iz * CW + pz;
                        Block b;
                        if (d > 0.0) b = Blocks.stone;
                        else b = aquifer.compute(bx + x, y, bz + z, surfaceAt[x * 16 + z], Config.overworldAquifers);
                        blocks[(x * 16 + z) * H + y] = b;
                    }
                }
            }
        }
    }

    private void surface(int cx, int cz, Block[] blocks, byte[] metas) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            BiomeGenBase biome = biomeBuf[z * 16 + x];
            double n = surfaceNoise.getValue(cx * 16 + x, 0, cz * 16 + z) * 6.0;
            // 1.7.10's surface builder indexes the block array transposed, so x and z are swapped here on purpose
            biome.genTerrainBlocks(world, rand, blocks, metas, cx * 16 + z, cz * 16 + x, n);
        }
        if (cubic) {
            // let the world continue below y=0: no bedrock floor
            for (int i = 0; i < 256; i++) for (int y = 0; y < 6; y++) {
                int idx = i * H + y;
                if (blocks[idx] == Blocks.bedrock) blocks[idx] = Blocks.stone;
            }
        }
    }

    // ------------------------------------------------------------------ IChunkProvider

    @Override
    public Chunk provideChunk(int cx, int cz) {
        rand.setSeed(cx * 341873128712L + cz * 132897987541L);
        Block[] blocks = new Block[16 * 16 * H];
        byte[] metas = new byte[blocks.length];
        biomeBuf = biomes.loadBlockGeneratorData(biomeBuf, cx * 16, cz * 16, 16, 16);
        fillTerrain(cx, cz, blocks);
        ChunkProviderEvent.ReplaceBiomeBlocks event = new ChunkProviderEvent.ReplaceBiomeBlocks(
            this,
            cx,
            cz,
            blocks,
            metas,
            biomeBuf,
            world);
        MinecraftForge.EVENT_BUS.post(event);
        if (event.getResult() != Event.Result.DENY) surface(cx, cz, blocks, metas);

        caves.func_151539_a(this, world, cx, cz, blocks);
        ravines.func_151539_a(this, world, cx, cz, blocks);
        if (structures) {
            mineshaft.func_151539_a(this, world, cx, cz, blocks);
            village.func_151539_a(this, world, cx, cz, blocks);
            stronghold.func_151539_a(this, world, cx, cz, blocks);
            scattered.func_151539_a(this, world, cx, cz, blocks);
        }

        Chunk chunk = new Chunk(world, blocks, metas, cx, cz);
        byte[] ids = chunk.getBiomeArray();
        for (int i = 0; i < ids.length; i++) ids[i] = (byte) biomeBuf[i].biomeID;
        chunk.generateSkylightMap();
        return chunk;
    }

    @Override
    public Chunk loadChunk(int cx, int cz) {
        return provideChunk(cx, cz);
    }

    @Override
    public boolean chunkExists(int cx, int cz) {
        return true;
    }

    @Override
    public void populate(IChunkProvider provider, int cx, int cz) {
        BlockFalling.fallInstantly = true;
        int x = cx * 16, z = cz * 16;
        BiomeGenBase biome = world.getBiomeGenForCoords(x + 16, z + 16);
        rand.setSeed(world.getSeed());
        long a = rand.nextLong() / 2L * 2L + 1L, b = rand.nextLong() / 2L * 2L + 1L;
        rand.setSeed(cx * a + cz * b ^ world.getSeed());
        boolean village = false;
        MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Pre(provider, world, rand, cx, cz, false));
        if (structures) {
            mineshaft.generateStructuresInChunk(world, rand, cx, cz);
            village = this.village.generateStructuresInChunk(world, rand, cx, cz);
            stronghold.generateStructuresInChunk(world, rand, cx, cz);
            scattered.generateStructuresInChunk(world, rand, cx, cz);
        }
        // modern versions dropped surface water lakes in favour of aquifers, but kept lava lakes
        if (!village && rand.nextInt(8) == 0
            && TerrainGen
                .populate(provider, world, rand, cx, cz, village, PopulateChunkEvent.Populate.EventType.LAVA)) {
            int ly = rand.nextInt(rand.nextInt(248) + 8);
            if (ly < 63 || rand.nextInt(10) == 0) {
                new WorldGenLakes(Blocks.lava)
                    .generate(world, rand, x + rand.nextInt(16) + 8, ly, z + rand.nextInt(16) + 8);
            }
        }
        if (TerrainGen
            .populate(provider, world, rand, cx, cz, village, PopulateChunkEvent.Populate.EventType.DUNGEON)) {
            for (int i = 0; i < 8; i++) {
                new WorldGenDungeons()
                    .generate(world, rand, x + rand.nextInt(16) + 8, rand.nextInt(256), z + rand.nextInt(16) + 8);
            }
        }
        biome.decorate(world, rand, x, z);
        if (TerrainGen
            .populate(provider, world, rand, cx, cz, village, PopulateChunkEvent.Populate.EventType.ANIMALS)) {
            SpawnerAnimals.performWorldGenSpawning(world, biome, x + 8, z + 8, 16, 16, rand);
        }
        x += 8;
        z += 8;
        if (TerrainGen.populate(provider, world, rand, cx, cz, village, PopulateChunkEvent.Populate.EventType.ICE)) {
            for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
                int top = world.getPrecipitationHeight(x + dx, z + dz);
                if (world.isBlockFreezable(dx + x, top - 1, dz + z))
                    world.setBlock(dx + x, top - 1, dz + z, Blocks.ice, 0, 2);
                if (world.func_147478_e(dx + x, top, dz + z, true))
                    world.setBlock(dx + x, top, dz + z, Blocks.snow_layer, 0, 2);
            }
        }
        MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Post(provider, world, rand, cx, cz, village));
        BlockFalling.fallInstantly = false;
    }

    @Override
    public boolean saveChunks(boolean all, IProgressUpdate progress) {
        return true;
    }

    @Override
    public boolean unloadQueuedChunks() {
        return false;
    }

    @Override
    public boolean canSave() {
        return true;
    }

    @Override
    public String makeString() {
        return "NewGenOverworldSource";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        if (type == EnumCreatureType.monster && scattered.func_143030_a(x, y, z))
            return scattered.getScatteredFeatureSpawnList();
        return biome.getSpawnableList(type);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String name, int x, int y, int z) {
        return "Stronghold".equals(name) && stronghold != null ? stronghold.func_151545_a(world, x, y, z) : null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int cx, int cz) {
        if (structures) {
            mineshaft.func_151539_a(this, world, cx, cz, null);
            village.func_151539_a(this, world, cx, cz, null);
            stronghold.func_151539_a(this, world, cx, cz, null);
            scattered.func_151539_a(this, world, cx, cz, null);
        }
    }

    @Override
    public void saveExtraData() {}
}
