package dlx.thermalharvest;

import cofh.core.gui.GuiHandler;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.network.ConfigSync;
import net.minecraftforge.common.MinecraftForge;
import dlx.thermalharvest.proxy.Proxy;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Thermal Harvester — аддон к Thermal Expansion.
 * Добавляет «Сборщик урожая» (аналог Crop Harvester из IC2), работающий по правилам машин Thermal Expansion:
 * RF, уровни через наборы улучшения, расширения (аугменты), авто-вывод, красный сигнал, защита владельцем.
 */
@Mod(modid = ThermalHarvest.MOD_ID, name = ThermalHarvest.MOD_NAME, version = ThermalHarvest.VERSION,
        dependencies = ThermalHarvest.DEPENDENCIES)
public class ThermalHarvest {

    public static final String MOD_ID = "thermalharvest";
    public static final String MOD_NAME = "Thermal Harvester";
    public static final String VERSION = "1.0.0";
    public static final String DEPENDENCIES = "required-after:codechickenlib;required-after:cofhcore;"
            + "required-after:thermalfoundation;required-after:thermalexpansion;after:ic2;after:waila;after:forestry;after:jei";

    public static final Logger LOG = LogManager.getLogger(MOD_ID);

    @Mod.Instance(MOD_ID)
    public static ThermalHarvest instance;

    @SidedProxy(clientSide = "dlx.thermalharvest.proxy.ProxyClient", serverSide = "dlx.thermalharvest.proxy.Proxy")
    public static Proxy proxy;

    public static Configuration CONFIG;

    /** IC2 (жердочки) присутствует в сборке. */
    public static boolean ic2Loaded;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ic2Loaded = Loader.isModLoaded("ic2");
        CONFIG = new Configuration(new File(event.getModConfigurationDirectory(), "cofh/thermalharvest.cfg"));
        CONFIG.load();
        THConfig.load(CONFIG);

        // Используем стандартный GUI-обработчик CoFH: TileCore#openGui -> player.openGui(getMod(), 0, ...)
        NetworkRegistry.INSTANCE.registerGuiHandler(instance, new GuiHandler());
        ConfigSync.init();
        MinecraftForge.EVENT_BUS.register(ConfigSync.INSTANCE);
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        if (CONFIG.hasChanged()) {
            CONFIG.save();
        }
    }
}
