package fastloader.newgen.nether;

import java.util.List;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFalling;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.MapGenCavesHell;
import net.minecraft.world.gen.structure.MapGenNetherBridge;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.terraingen.ChunkProviderEvent;
import net.minecraftforge.event.terraingen.PopulateChunkEvent;
import net.minecraftforge.event.terraingen.TerrainGen;

import cpw.mods.fml.common.eventhandler.Event;
import fastloader.Config;
import fastloader.newgen.block.BlockState;
import fastloader.newgen.block.NGBlocks;
import fastloader.newgen.noise.BlendedNoise;
import fastloader.newgen.noise.NoiseMath;
import fastloader.newgen.noise.NormalNoise;

/**
 * Modern-style Nether generator: 128 blocks tall, lava sea at y=31, blended 3D noise terrain with top/bottom slides,
 * multi-noise biomes and per-biome surface rules. Features are placed in {@link #populate}.
 */
public class NetherChunkProviderNG implements IChunkProvider {

    public static final int HEIGHT = 128;
    public static final int LAVA_LEVEL = 31;
    private static final int CELL_W = 4, CELL_H = 8;
    private static final int CX = 16 / CELL_W, CY = HEIGHT / CELL_H;

    private final World world;
    private final long seed;
    private final Random rand;
    private final NetherBiomeSource biomes;
    private final BlendedNoise base;
    private final NormalNoise surfaceNoise, stateSelector, netherrackPatch, wartPatch, soulSandLayer, gravelLayer,
        patchNoise;
    private final MapGenBase caves = TerrainGen.getModdedMapGen(
        new MapGenCavesHell(),
        net.minecraftforge.event.terraingen.InitMapGenEvent.EventType.NETHER_CAVE);
    public final MapGenNetherBridge fortress = (MapGenNetherBridge) TerrainGen.getModdedMapGen(
        new MapGenNetherBridge(),
        net.minecraftforge.event.terraingen.InitMapGenEvent.EventType.NETHER_BRIDGE);
    private final NetherDecorator decorator;
    private final double[] densityCache = new double[(CX + 1) * (CX + 1) * (CY + 1)];
    private BiomeGenBase[] biomeBuf;

    public NetherChunkProviderNG(World world, long seed) {
        this.world = world;
        this.seed = seed;
        this.rand = new Random(seed);
        this.biomes = world.getWorldChunkManager() instanceof NetherBiomeSource nbs ? nbs : new NetherBiomeSource(seed);
        Random r = new Random(seed ^ 0x6E657468L);
        // modern nether base noise: xz scale 1, y scale 3, xz factor 80, y factor 60, smear 8
        this.base = new BlendedNoise(r, 1.0, 3.0, 80.0, 60.0, 8.0);
        this.surfaceNoise = NormalNoise.create(seed, "nether_surface", -6, 1, 1, 1);
        this.stateSelector = NormalNoise.create(seed, "nether_state_selector", -4, 1);
        this.netherrackPatch = NormalNoise.create(seed, "netherrack", -3, 1, 0, 0, 0.35);
        this.wartPatch = NormalNoise.create(seed, "nether_wart", -3, 1, 0, 0, 0.9);
        this.soulSandLayer = NormalNoise.create(seed, "soul_sand_layer", -8, 1, 1, 1, 1, 0, 0, 0, 0, 0.013333);
        this.gravelLayer = NormalNoise.create(seed, "gravel_layer", -8, 1, 1, 1, 1, 0, 0, 0, 0, 0.013333);
        this.patchNoise = NormalNoise.create(seed, "patch", -5, 1, 0, 0, 0, 0, 0.013333);
        this.decorator = new NetherDecorator(this);
    }

    public World getWorld() {
        return world;
    }

    // ------------------------------------------------------------------ terrain

    /** Final density at a block position; > 0 is solid. */
    private double density(int x, int y, int z) {
        double d = base.compute(x, y, z);
        // top slide: fade towards +0.9375 (solid) in the top 24 blocks, giving the netherrack ceiling
        double top = NoiseMath.clampedMap(y, HEIGHT - 24, HEIGHT, 1.0, 0.0);
        d = NoiseMath.lerp(top, 0.9375, d);
        // bottom slide: fade towards +2.5 below y=24
        double bottom = NoiseMath.clampedMap(y, -8, 24, 0.0, 1.0);
        d = NoiseMath.lerp(bottom, 2.5, d);
        return NoiseMath.squeeze(d * 0.64);
    }

    private void fillTerrain(int cx, int cz, Block[] blocks) {
        int bx = cx * 16, bz = cz * 16;
        int s = CX + 1;
        for (int ix = 0; ix <= CX; ix++) for (int iz = 0; iz <= CX; iz++) for (int iy = 0; iy <= CY; iy++) {
            densityCache[(ix * s + iz) * (CY + 1) + iy] = density(bx + ix * CELL_W, iy * CELL_H, bz + iz * CELL_W);
        }
        for (int ix = 0; ix < CX; ix++) for (int iz = 0; iz < CX; iz++) for (int iy = 0; iy < CY; iy++) {
            double d000 = densityCache[(ix * s + iz) * (CY + 1) + iy];
            double d001 = densityCache[(ix * s + iz + 1) * (CY + 1) + iy];
            double d100 = densityCache[((ix + 1) * s + iz) * (CY + 1) + iy];
            double d101 = densityCache[((ix + 1) * s + iz + 1) * (CY + 1) + iy];
            double d010 = densityCache[(ix * s + iz) * (CY + 1) + iy + 1];
            double d011 = densityCache[(ix * s + iz + 1) * (CY + 1) + iy + 1];
            double d110 = densityCache[((ix + 1) * s + iz) * (CY + 1) + iy + 1];
            double d111 = densityCache[((ix + 1) * s + iz + 1) * (CY + 1) + iy + 1];
            for (int py = 0; py < CELL_H; py++) {
                double ty = py / (double) CELL_H;
                int y = iy * CELL_H + py;
                for (int px = 0; px < CELL_W; px++) {
                    double tx = px / (double) CELL_W;
                    for (int pz = 0; pz < CELL_W; pz++) {
                        double tz = pz / (double) CELL_W;
                        double d = NoiseMath.lerp3(tx, ty, tz, d000, d100, d010, d110, d001, d101, d011, d111);
                        int x = ix * CELL_W + px, z = iz * CELL_W + pz;
                        Block b;
                        if (d > 0.0) b = Blocks.netherrack;
                        else if (y <= LAVA_LEVEL) b = Blocks.lava;
                        else b = Blocks.air;
                        blocks[(x * 16 + z) * HEIGHT + y] = b;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ surface

    private static boolean solid(Block b) {
        return b != Blocks.air && b != Blocks.lava && b != null;
    }

    private void applySurface(int cx, int cz, Block[] blocks, byte[] metas, BiomeGenBase[] colBiomes) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int wx = cx * 16 + x, wz = cz * 16 + z;
            BiomeGenBase biome = colBiomes[z * 16 + x];
            int depthLimit = (int) (surfaceNoise.getValue(wx, 0, wz) * 2.75 + 3.0 + rand.nextDouble() * 0.25);
            int col = (x * 16 + z) * HEIGHT;
            int depth = -1; // solid blocks since last open space above
            for (int y = HEIGHT - 1; y >= 0; y--) {
                int i = col + y;
                Block b = blocks[i];
                if (!solid(b)) {
                    depth = -1;
                    continue;
                }
                depth++;
                boolean openAbove = depth == 0 && y < HEIGHT - 1 && blocks[i + 1] == Blocks.air;
                boolean ceiling = y > 0 && !solid(blocks[i - 1]) && blocks[i - 1] == Blocks.air;
                if (b != Blocks.netherrack) continue;
                BlockState put = surfaceFor(biome, wx, y, wz, depth, depthLimit, openAbove, ceiling);
                if (put != null) {
                    blocks[i] = put.block;
                    metas[i] = (byte) put.meta;
                }
            }
        }
    }

    private BlockState surfaceFor(BiomeGenBase biome, int x, int y, int z, int depth, int depthLimit, boolean openAbove,
        boolean ceiling) {
        if (biome == NetherBiomes.CRIMSON_FOREST || biome == NetherBiomes.WARPED_FOREST) {
            boolean crimson = biome == NetherBiomes.CRIMSON_FOREST;
            if (depth == 0 && openAbove && y >= LAVA_LEVEL) {
                if (netherrackPatch.getValue(x, 0, z) > 0.54) return null;
                return crimson ? NGBlocks.CRIMSON_NYLIUM : NGBlocks.WARPED_NYLIUM;
            }
            if (depth == 1 && y >= LAVA_LEVEL && wartPatch.getValue(x, 0, z) > 1.17) {
                return crimson ? NGBlocks.NETHER_WART_BLOCK : NGBlocks.WARPED_WART_BLOCK;
            }
            return null;
        }
        if (biome == NetherBiomes.SOUL_SAND_VALLEY) {
            if (ceiling || depth <= depthLimit && (depth > 0 || openAbove)) {
                return stateSelector.getValue(x, y, z) > 0.0 ? BlockState.of(Blocks.soul_sand) : NGBlocks.SOUL_SOIL;
            }
            return null;
        }
        if (biome == NetherBiomes.BASALT_DELTAS) {
            if (ceiling) return NGBlocks.BASALT;
            if (depth <= depthLimit && (depth > 0 || openAbove)) {
                return patchNoise.getValue(x, y, z) > 0.0 || stateSelector.getValue(x, y, z) > 0.2 ? NGBlocks.BASALT
                    : NGBlocks.BLACKSTONE;
            }
            return null;
        }
        // nether wastes: soul sand and gravel shores around the lava sea
        if (y >= LAVA_LEVEL - 1 && y <= LAVA_LEVEL + 4 && depth <= depthLimit) {
            if (soulSandLayer.getValue(x, y, z) > -0.012 && ((NoiseMath.hash(seed, x, y, z) & 3) != 0)) {
                return BlockState.of(Blocks.soul_sand);
            }
            if (gravelLayer.getValue(x, y, z) > 0.1) return BlockState.of(Blocks.gravel);
        }
        return null;
    }

    private void placeBedrock(int cx, int cz, Block[] blocks) {
        Random r = new Random(cx * 341873128712L + cz * 132897987541L ^ seed);
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            int col = (x * 16 + z) * HEIGHT;
            for (int y = 0; y < 5; y++) {
                // vertical gradient: always at 0, never at 5
                if (y == 0 || r.nextDouble() < 1.0 - y / 5.0) blocks[col + y] = Blocks.bedrock;
            }
            for (int y = HEIGHT - 1; y > HEIGHT - 6; y--) {
                int d = HEIGHT - 1 - y;
                if (d == 0 || r.nextDouble() < 1.0 - d / 5.0) blocks[col + y] = Blocks.bedrock;
            }
        }
    }

    // ------------------------------------------------------------------ IChunkProvider

    @Override
    public Chunk provideChunk(int cx, int cz) {
        rand.setSeed(cx * 341873128712L + cz * 132897987541L);
        Block[] blocks = new Block[16 * 16 * HEIGHT];
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
        if (event.getResult() != Event.Result.DENY) {
            applySurface(cx, cz, blocks, metas, biomeBuf);
            placeBedrock(cx, cz, blocks);
        }
        caves.func_151539_a(this, world, cx, cz, blocks);
        if (Config.netherFortresses) fortress.func_151539_a(this, world, cx, cz, blocks);

        Chunk chunk = new Chunk(world, blocks, metas, cx, cz);
        byte[] ids = chunk.getBiomeArray();
        for (int i = 0; i < ids.length; i++) ids[i] = (byte) biomeBuf[i].biomeID;
        chunk.resetRelightChecks();
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
        int bx = cx * 16, bz = cz * 16;
        MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Pre(provider, world, rand, cx, cz, false));
        Random r = new Random(seed);
        long a = r.nextLong() / 2L * 2L + 1L, b = r.nextLong() / 2L * 2L + 1L;
        r.setSeed(cx * a + cz * b ^ seed);
        if (Config.netherFortresses) fortress.generateStructuresInChunk(world, r, cx, cz);
        BiomeGenBase biome = world.getBiomeGenForCoords(bx + 16, bz + 16);
        decorator.decorate(world, r, bx, bz, biome);
        MinecraftForge.EVENT_BUS.post(new PopulateChunkEvent.Post(provider, world, rand, cx, cz, false));
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
        return "NewGenNetherSource";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getPossibleCreatures(EnumCreatureType type, int x, int y, int z) {
        if (type == EnumCreatureType.monster) {
            if (fortress.hasStructureAt(x, y, z)) return fortress.getSpawnList();
            if (fortress.func_142038_b(x, y, z) && world.getBlock(x, y - 1, z) == Blocks.nether_brick) {
                return fortress.getSpawnList();
            }
        }
        BiomeGenBase biome = world.getBiomeGenForCoords(x, z);
        return biome.getSpawnableList(type);
    }

    @Override
    public ChunkPosition func_147416_a(World world, String name, int x, int y, int z) {
        return "Fortress".equals(name) && fortress != null ? fortress.func_151545_a(world, x, y, z) : null;
    }

    @Override
    public int getLoadedChunkCount() {
        return 0;
    }

    @Override
    public void recreateStructures(int cx, int cz) {
        if (Config.netherFortresses) fortress.func_151539_a(this, world, cx, cz, null);
    }

    @Override
    public void saveExtraData() {}
}
