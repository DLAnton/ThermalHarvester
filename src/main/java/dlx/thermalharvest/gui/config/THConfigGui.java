package dlx.thermalharvest.gui.config;

import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.network.ConfigSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно настроек мода: разделы config/cofh/thermalharvest.cfg.
 * После «Готово» файл сохраняется и значения применяются сразу (см. {@link #onConfigChanged}).
 */
public class THConfigGui extends GuiConfig {

    public THConfigGui(GuiScreen parent) {
        super(parent, elements(), ThermalHarvest.MOD_ID, false, false,
                I18n.format("config." + ThermalHarvest.MOD_ID + ".title"), "config/cofh/thermalharvest.cfg");
    }

    private static List<IConfigElement> elements() {
        List<IConfigElement> list = new ArrayList<>();
        for (String name : ThermalHarvest.CONFIG.getCategoryNames()) {
            list.add(new ConfigElement(ThermalHarvest.CONFIG.getCategory(name)));
        }
        return list;
    }

    /** Подписка на шине событий Forge (регистрируется в ProxyClient). */
    public static final class Events {

        @SubscribeEvent
        public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
            if (!ThermalHarvest.MOD_ID.equals(event.getModID())) {
                return;
            }
            ThermalHarvest.CONFIG.save();
            if (ConfigSync.remoteValues) {
                // Игра на чужом сервере: действуют его значения, файл применится после выхода. Сразу меняется только подсветка зоны.
                THConfig.loadClient(ThermalHarvest.CONFIG);
                return;
            }
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                // Главное меню: мир не загружен.
                THConfig.load(ThermalHarvest.CONFIG);
                THConfig.fireChanged();
                return;
            }
            // Одиночная игра или хост LAN: настройки читает поток сервера, поэтому применяем их там
            // (при паузе — сразу после неё), затем рассылаем подключённым игрокам и обновляем страницу HEI.
            server.addScheduledTask(() -> {
                THConfig.load(ThermalHarvest.CONFIG);
                ConfigSync.sendToAll();
                Minecraft.getMinecraft().addScheduledTask(THConfig::fireChanged);
            });
        }
    }
}
