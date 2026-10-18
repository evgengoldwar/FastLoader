package fastloader.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {

    STARTUP_PROFILER_FML(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addCommonMixins("MixinLoadController")),
    STARTUP_PROFILER_CLIENT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins("MixinMinecraft_Profiler", "MixinTextureMap_Profiler", "MixinSimpleReloadableResourceManager"));

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
