package fastloader;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.WorldEvent;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import fastloader.command.CommandFastLoader;
import fastloader.optimize.HodgepodgeTuning;
import fastloader.optimize.TerrainPrefetcher;
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
        MinecraftForge.EVENT_BUS.register(this);
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        ProfileReport.newSession();
        ChunkProfiler.resetInterval();
        event.registerServerCommand(new CommandFastLoader());
        StackSampler.start();
        HodgepodgeTuning.apply();
    }

    public void serverStopping(FMLServerStoppingEvent event) {
        ChunkPregenerator.INSTANCE.stop(null);
        StackSampler.stop();
        TerrainPrefetcher.onServerStopping();
        if (ChunkProfiler.enabled && ChunkProfiler.hasGeneration()) ProfileReport.manual();
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        if (!event.world.isRemote) TerrainPrefetcher.onWorldUnload(event.world);
    }
}
