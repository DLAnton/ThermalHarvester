package dlx.thermalharvest.init;

import dlx.thermalharvest.ThermalHarvest;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class THTextures {

    public static final THTextures INSTANCE = new THTextures();

    public static TextureAtlasSprite HARVESTER_FACE;
    /** Накладки лицевой панели (рисуются поверх корпуса независимо друг от друга). */
    public static TextureAtlasSprite OV_LAMPS_SLEEP;
    public static TextureAtlasSprite OV_LAMPS_SCAN;
    public static TextureAtlasSprite OV_WINDOW;
    /** Хранилище урожая переполнено: оранжевая рамка окна. */
    public static TextureAtlasSprite OV_WINDOW_FULL;
    public static TextureAtlasSprite OV_SLOT_READY;
    public static TextureAtlasSprite OV_SLOT_RUN;

    private THTextures() {}

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.getMap();
        HARVESTER_FACE = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/harvester_face"));
        OV_LAMPS_SLEEP = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/lamps_sleep"));
        OV_LAMPS_SCAN = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/lamps_scan"));
        OV_WINDOW = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/window"));
        OV_WINDOW_FULL = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/window_full"));
        OV_SLOT_READY = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/slot_ready"));
        OV_SLOT_RUN = map.registerSprite(new ResourceLocation(ThermalHarvest.MOD_ID, "blocks/overlay/slot_run"));
    }
}
