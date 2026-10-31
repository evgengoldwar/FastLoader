package fastloader;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import fastloader.command.CommandFastLoader;
import fastloader.pregen.ChunkPregenerator;
import fastloader.profiler.ChunkProfiler;
import fastloader.profiler.ProfileReport;
import fastloader.profiler.StackSampler;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        FastLoader.LOG.info(
            "FastLoader {} - chunk profiler {}",
            Tags.VERSION,
            ChunkProfiler.enabled ? "enabled" : "disabled (/fastloader profile on)");
    }

    public void init(FMLInitializationEvent event) {
        FMLCommonHandler.instance()
            .bus()
            .register(ChunkPregenerator.INSTANCE);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        ProfileReport.newSession();
        ChunkProfiler.resetInterval();
        event.registerServerCommand(new CommandFastLoader());
        StackSampler.start();
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        ChunkPregenerator.INSTANCE.stop(null);
        StackSampler.stop();
        if (ChunkProfiler.enabled && ChunkProfiler.hasGeneration()) ProfileReport.manual();
    }
}
