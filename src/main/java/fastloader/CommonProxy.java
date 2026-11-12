package fastloader;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import fastloader.newgen.NewGen;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());
        FastLoader.LOG.info("FastLoader {} - New Generation world type", Tags.VERSION);
        NewGen.preInit();
    }

    public void init(FMLInitializationEvent event) {
        NewGen.init();
    }

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new fastloader.newgen.NGMapCommand());
    }
}
