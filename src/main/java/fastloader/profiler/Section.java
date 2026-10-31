package fastloader.profiler;

/**
 * Every measured step of the chunk pipeline. The label is the base name shown in reports; dynamic sections
 * (generators, providers, listeners...) append the class / mod that does the work.
 */
public enum Section {

    // ---- loading / generation entry points (ChunkProviderServer) ----
    LOAD_CHUNK("ChunkProviderServer.loadChunk", Flags.LOAD),
    LOAD_ORIGINAL("ChunkProviderServer.originalLoadChunk", Flags.LOAD),
    DISK_READ("ChunkProviderServer.safeLoadChunk (disk read)", 0),
    TERRAIN("provideChunk (terrain)", 0),
    ON_LOAD("Chunk.onChunkLoad (tile entities + ChunkEvent.Load)", 0),
    POPULATE_CHECK("Chunk.populateChunk (neighbour check)", 0),
    POPULATE("ChunkProviderServer.populate", Flags.POPULATE_BOUNDARY),
    PROVIDER_POPULATE("populate", Flags.POPULATE_BOUNDARY | Flags.ATTRIBUTABLE),
    WORLDGEN_ALL("GameRegistry.generateWorld (all IWorldGenerators)", 0),
    WORLDGEN("IWorldGenerator", Flags.ATTRIBUTABLE),
    EVENT("Event", Flags.ATTRIBUTABLE),
    RECREATE_STRUCTURES("recreateStructures", Flags.ATTRIBUTABLE),

    // ---- async / disk IO ----
    ASYNC_READ("ChunkIO stage1: AnvilChunkLoader.loadChunk__Async (region read + NBT parse)", 0),
    ASYNC_FINISH("ChunkIO stage2: finish async load on main thread", Flags.LOAD),
    NBT_TO_CHUNK("AnvilChunkLoader.readChunkFromNBT", 0),
    LOAD_ENTITIES("AnvilChunkLoader.loadEntities (entities + tile entities)", 0),
    SAVE_CHUNK("ChunkProviderServer.safeSaveChunk", 0),
    SAVE_EXTRA("ChunkProviderServer.safeSaveExtraChunkData", 0),
    CHUNK_TO_NBT("AnvilChunkLoader.saveChunk (Chunk -> NBT)", 0),
    IO_WRITE("AnvilChunkLoader.writeNextIO (region file write)", 0),
    UNLOAD_QUEUE("ChunkProviderServer.unloadQueuedChunks", 0),
    ON_UNLOAD("Chunk.onChunkUnload", 0),
    SAVE_ALL("ChunkProviderServer.saveChunks", 0),

    // ---- lighting ----
    LIGHT_INIT("Chunk.func_150809_p (initial light population)", 0),
    SKYLIGHT("Chunk.generateSkylightMap", 0),
    LIGHT_CHECK("Chunk.func_150804_b (chunk light/gap checks)", 0),
    /** Counter only: every light update inside chunk work (timing each call would distort the result). */
    LIGHT_UPDATE("World.updateLightByType (count)", Flags.COUNTER),
    /** Every 32nd light update, timed; used to estimate the total light cost. */
    LIGHT_SAMPLE("World.updateLightByType (sampled 1/" + 32 + ")", 0),
    /** Counter only: World.setBlock calls inside chunk work. */
    SET_BLOCK("World.setBlock (count)", Flags.COUNTER),
    /** Every 64th World.setBlock inside chunk work, timed, with its internals below. */
    SET_BLOCK_SAMPLE("World.setBlock (sampled 1/" + 64 + ")", Flags.SAMPLE),
    SB_CHUNK("Chunk.func_150807_a (store block, heightmap, onBlockAdded)", Flags.SAMPLE),
    SB_RELIGHT("Chunk.relightBlock (sky light column after heightmap change)", Flags.SAMPLE),
    SB_LIGHT("World.func_147451_t (light check after setBlock)", Flags.SAMPLE),
    SB_NOTIFY("World.markAndNotifyBlock (client sync + neighbour updates)", Flags.SAMPLE),
    /** Counter only: block ticks scheduled by worldgen (falling gravel/sand, liquids...). */
    SCHEDULED_TICK("WorldServer.scheduleBlockUpdate (count)", Flags.COUNTER),

    // ---- terrain internals (vanilla generators + any mod generator built on them) ----
    TERRAIN_NOISE("ChunkProviderGenerate.func_147424_a (noise terrain)", 0),
    BIOME_BLOCKS("ChunkProviderGenerate.replaceBlocksForBiome", 0),
    BIOME_GEN("WorldChunkManager (GenLayer biome lookup)", 0),
    MAPGEN("MapGen", Flags.ATTRIBUTABLE),
    STRUCTURE("Structure", Flags.ATTRIBUTABLE),
    DECORATE("BiomeDecorator.decorateChunk", Flags.ATTRIBUTABLE),
    VANILLA_ORES("BiomeDecorator.generateOres", Flags.ATTRIBUTABLE),
    LAKES("WorldGenLakes", Flags.ATTRIBUTABLE),
    DUNGEONS("WorldGenDungeons", Flags.ATTRIBUTABLE),
    LIQUIDS("WorldGenLiquids (springs)", Flags.ATTRIBUTABLE),
    MINABLE("WorldGenMinable (ore blob)", Flags.ATTRIBUTABLE),
    WORLDGEN_SPAWN("SpawnerAnimals.performWorldGenSpawning", Flags.ATTRIBUTABLE),

    // ---- players / network ----
    PLAYER_INSTANCES("PlayerManager.updatePlayerInstances", 0),
    PLAYER_MOVE("PlayerManager.updatePlayerPertinentChunks", 0),
    PACKET_BUILD("S21PacketChunkData.func_149269_a (chunk -> packet data)", 0),

    // ---- GregTech (GT5-Unofficial) ----
    GT_CONTAINER("GT WorldGenContainer.run (stones + small ores + veins)", Flags.ATTRIBUTABLE),
    GT_VEIN_SEARCH("GT generateVein (vein seed / cache)", Flags.ATTRIBUTABLE),
    GT_VEIN("GT vein", Flags.ATTRIBUTABLE),
    GT_STONE("GT stone", Flags.ATTRIBUTABLE),
    GT_SMALL_ORE("GT small ore", Flags.ATTRIBUTABLE),

    // ---- Realistic World Gen ----
    RWG_TERRAIN("RWG generateTerrain (height noise)", 0),
    RWG_MAPGEN("RWG RealisticBiomeBase.generateMapGen (per-biome mapgen)", Flags.ATTRIBUTABLE),
    RWG_SURFACE("RWG replaceBlocksForBiome (surface)", 0),
    RWG_DECORATE("RWG RealisticBiomeBase.rDecorate (biome decorators)", Flags.ATTRIBUTABLE),

    // ---- FastLoader itself ----
    PREGEN("FastLoader pregenerator batch", 0),
    PREFETCH("FastLoader terrain prefetch (background thread)", 0);

    public final String label;
    final int flags;

    Section(String label, int flags) {
        this.label = label;
        this.flags = flags;
    }

    boolean isLoad() {
        return (flags & Flags.LOAD) != 0;
    }

    boolean isPopulateBoundary() {
        return (flags & Flags.POPULATE_BOUNDARY) != 0;
    }

    boolean isSample() {
        return (flags & Flags.SAMPLE) != 0;
    }

    boolean isCounter() {
        return (flags & Flags.COUNTER) != 0;
    }

    boolean isAttributable() {
        return (flags & Flags.ATTRIBUTABLE) != 0;
    }

    static final class Flags {

        static final int LOAD = 1;
        static final int POPULATE_BOUNDARY = 2;
        static final int ATTRIBUTABLE = 4;
        static final int COUNTER = 8;
        static final int SAMPLE = 16;
    }
}
