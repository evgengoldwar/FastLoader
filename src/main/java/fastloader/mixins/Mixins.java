package fastloader.mixins;

import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import com.gtnewhorizon.gtnhmixins.builders.MixinBuilder;

import javax.annotation.Nonnull;

public enum Mixins implements IMixins {

    STARTUP_PROFILER_COMMON(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addCommonMixins("MixinProgressManager", "MixinProgressBar")),
    STARTUP_PROFILER_CLIENT(new MixinBuilder()
        .setPhase(Phase.EARLY)
        .addClientMixins("MixinMinecraft_Profiler", "MixinTextureMap_Profiler"));

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
