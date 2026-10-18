package fastloader.mixins;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;
import com.gtnewhorizon.gtnhmixins.builders.IMixins;
import cpw.mods.fml.relauncher.IFMLLoadingPlugin;
import fastloader.core.FastLoaderCore;
import net.minecraft.launchwrapper.Launch;

import java.util.List;
import java.util.Map;
import java.util.Set;

@IFMLLoadingPlugin.MCVersion("1.7.10")
public class EarlyMixinsLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    public EarlyMixinsLoader() {
        // These run inside class loading (transformer timing, prefetch threads), so they must never go through
        // the transformers themselves
        if (Launch.classLoader != null) {
            Launch.classLoader.addTransformerExclusion("fastloader.profiler.");
            Launch.classLoader.addTransformerExclusion("fastloader.core.");
        }
        FastLoaderCore.onCoremodInit();
    }

    @Override
    public String[] getASMTransformerClass() {
        return null;
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        fastloader.profiler.LoadProfiler.milestone("Coremod setup (FastLoader injectData)");
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }

    @Override
    public String getMixinConfig() {
        return "mixins.fastloader.early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        return IMixins.getEarlyMixins(Mixins.class, loadedCoreMods);
    }
}
