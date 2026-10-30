package fastloader.mixins;

import javax.annotation.Nonnull;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

public enum Mixins implements IMixins {

    // Chunk pipeline profiler: vanilla / Forge / FML side (server logic, so common mixins)
    PROFILER_CHUNK_PIPELINE(new MixinBuilder("Chunk pipeline profiler: load / generate / populate / save")
        .setPhase(Phase.EARLY)
        .addCommonMixins(
            "minecraft.MixinMinecraftServer",
            "minecraft.MixinWorldServer",
            "minecraft.MixinChunkProviderServer",
            "minecraft.MixinChunk",
            "minecraft.MixinAnvilChunkLoader",
            "forge.MixinChunkIOProvider",
            "forge.MixinGameRegistry",
            "forge.MixinASMEventHandler")),
    PROFILER_LIGHTING(new MixinBuilder("Chunk pipeline profiler: lighting")
        .setPhase(Phase.EARLY)
        .addCommonMixins("minecraft.MixinWorld")),
    PROFILER_TERRAIN(new MixinBuilder("Chunk pipeline profiler: terrain / decoration internals")
        .setPhase(Phase.EARLY)
        .addCommonMixins(
            "minecraft.MixinChunkProviderGenerate",
            "minecraft.MixinWorldChunkManager",
            "minecraft.MixinMapGenBase",
            "minecraft.MixinMapGenStructure",
            "minecraft.MixinBiomeDecorator",
            "minecraft.MixinSpawnerAnimals",
            "minecraft.MixinWorldGenLakes",
            "minecraft.MixinWorldGenDungeons",
            "minecraft.MixinWorldGenMinable",
            "minecraft.MixinWorldGenLiquids")),
    PROFILER_PLAYERS(new MixinBuilder("Chunk pipeline profiler: player chunk loading / sending")
        .setPhase(Phase.EARLY)
        .addCommonMixins("minecraft.MixinPlayerManager", "minecraft.MixinS21PacketChunkData")),
    PROFILER_GREGTECH(new MixinBuilder("Chunk pipeline profiler: GregTech oregen")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.GREGTECH)
        .addCommonMixins("gregtech.MixinGTWorldGenContainer", "gregtech.MixinGTWorldgenNamed")),
    PROFILER_RWG(new MixinBuilder("Chunk pipeline profiler: Realistic World Gen internals")
        .setPhase(Phase.LATE)
        .addRequiredMod(TargetMods.RWG)
        .addCommonMixins("rwg.MixinChunkGeneratorRealistic"));

    private final MixinBuilder builder;

    Mixins(MixinBuilder builder) {
        this.builder = builder;
    }

    @Nonnull
    @Override
    public MixinBuilder getBuilder() {
        return builder;
    }
}
