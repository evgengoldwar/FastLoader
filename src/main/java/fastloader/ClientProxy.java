package fastloader;

import net.minecraftforge.common.MinecraftForge;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import fastloader.newgen.client.NetherClientHandler;

public class ClientProxy extends CommonProxy {

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        MinecraftForge.EVENT_BUS.register(NetherClientHandler.INSTANCE);
        FMLCommonHandler.instance()
            .bus()
            .register(NetherClientHandler.INSTANCE);
    }
}
