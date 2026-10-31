package dlx.thermalharvest.proxy;

import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.init.THContent;
import dlx.thermalharvest.network.ConfigSync;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import dlx.thermalharvest.init.THTextures;
import dlx.thermalharvest.render.ZoneRenderer;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class ProxyClient extends Proxy {

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        MinecraftForge.EVENT_BUS.register(THTextures.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ZoneRenderer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(this);
    }

    /** Выход с сервера: возвращаемся к значениям локального конфига (сервер мог прислать свои). */
    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        if (!ConfigSync.remoteValues) {
            return;
        }
        Minecraft.getMinecraft().addScheduledTask(() -> {
            ConfigSync.remoteValues = false;
            THConfig.load(ThermalHarvest.CONFIG);
            THConfig.fireChanged();
        });
    }

    @SubscribeEvent
    public void registerModels(ModelRegistryEvent event) {
        THContent.blockHarvester.registerModels();
        THContent.itemAugment.registerModels();
    }
}
