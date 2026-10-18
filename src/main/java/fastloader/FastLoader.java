package fastloader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerAboutToStartEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import fastloader.profiler.LoadProfiler;

@Mod(modid = FastLoader.MODID, version = Tags.VERSION, name = "FastLoader", acceptedMinecraftVersions = "[1.7.10]")
public class FastLoader {

    public static final String MODID = "fastloader";
    public static final Logger LOG = LogManager.getLogger(MODID);

    @SidedProxy(clientSide = "fastloader.ClientProxy", serverSide = "fastloader.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        proxy.preInit(event);
    }

    @Mod.EventHandler
    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        proxy.serverStarting(event);
    }

    private long serverStartNanos;

    @Mod.EventHandler
    public void serverAboutToStart(FMLServerAboutToStartEvent event) {
        serverStartNanos = System.nanoTime();
        LoadProfiler.milestone("Server about to start (world loading)");
    }

    @Mod.EventHandler
    public void serverStarted(FMLServerStartedEvent event) {
        if (LoadProfiler.isActive()) {
            // dedicated server: this is the end of startup
            LoadProfiler.finish("Server started");
        } else {
            // client: startup was already reported at the main menu, just log how long the world took
            LoadProfiler.note(
                String.format(
                    "World loaded (integrated server started) in %d ms",
                    (System.nanoTime() - serverStartNanos) / 1_000_000L));
        }
    }
}
