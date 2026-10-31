package dlx.thermalharvest.compat;

import dlx.thermalharvest.init.THConfig;
import ic2.api.crops.CropCard;
import ic2.api.crops.ICropTile;
import ic2.core.crop.TileEntityCrop;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Работа с жёрдочками IC2. Класс загружается JVM только если IC2 установлен
 * (все вызовы идут через проверку ThermalHarvest.ic2Loaded).
 * Сбор повторяет логику TileEntityCropHarvester из IC2, полив и подкормка — логику Crop-Matron.
 */
public final class IC2CropCompat {

    private IC2CropCompat() {}

    private static final String WEED_ID = "weed";

    public static boolean isCrop(TileEntity tile) {
        return tile instanceof ICropTile;
    }

    /* ------------------------------------------------------------------ */
    /* Сбор                                                                */
    /* ------------------------------------------------------------------ */

    public static boolean canHarvest(TileEntity tile) {
        if (!(tile instanceof ICropTile)) {
            return false;
        }
        ICropTile crop = (ICropTile) tile;
        CropCard card = crop.getCrop();
        if (card == null || isWeed(tile)) {
            return false;
        }
        int size = crop.getCurrentSize();
        boolean ready = size == card.getOptimalHarvestSize(crop) || size == card.getMaxSize();
        return ready && card.canBeHarvested(crop);
    }

    /** @return урожай (может быть пустым при неудачном «броске» IC2) или null, если растение не готово. */
    public static List<ItemStack> harvest(TileEntity tile) {
        if (!canHarvest(tile)) {
            return null;
        }
        List<ItemStack> drops = ((ICropTile) tile).performHarvest();
        List<ItemStack> ret = new ArrayList<>();
        if (drops != null) {
            for (ItemStack stack : drops) {
                if (stack != null && !stack.isEmpty()) {
                    ret.add(stack);
                }
            }
        }
        return ret;
    }

    /* ------------------------------------------------------------------ */
    /* Сорняки                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * Настоящий сорняк IC2 (на любой стадии). Культуры с ростом 24+, которые IC2 тоже считает «сорными»,
     * сюда не попадают — это выведенные игроком растения.
     */
    public static boolean isWeed(TileEntity tile) {
        if (!(tile instanceof ICropTile)) {
            return false;
        }
        CropCard card = ((ICropTile) tile).getCrop();
        return card != null && WEED_ID.equals(card.getId());
    }

    /** Выпалывает сорняк, оставляя пустую жёрдочку. */
    public static void removeWeed(TileEntity tile) {
        if (tile instanceof TileEntityCrop && isWeed(tile)) {
            TileEntityCrop crop = (TileEntityCrop) tile;
            crop.reset();
            crop.dirty = true;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Рост                                                                */
    /* ------------------------------------------------------------------ */

    /** Тиков между естественными циклами роста жёрдочки. */
    public static double ticksPerGrowthCycle() {
        return Math.max(1, TileEntityCrop.tickrate);
    }

    /**
     * Дополнительный цикл роста — только если условия (влажность, питательность, воздух) позволяют расти
     * без риска гибели растения. Повторяет шаг роста из TileEntityCrop#performTick.
     *
     * @return true, если цикл выполнен.
     */
    public static boolean safeGrowthTick(TileEntity tile) {
        if (!(tile instanceof TileEntityCrop)) {
            return false;
        }
        TileEntityCrop crop = (TileEntityCrop) tile;
        CropCard card = crop.getCrop();
        if (card == null || isWeed(tile) || !card.canGrow(crop) || crop.getCurrentSize() >= card.getMaxSize()) {
            return false;
        }
        int minimumQuality = (card.getProperties().getTier() - 1) * 4
                + crop.getStatGrowth() + crop.getStatGain() + crop.getStatResistance();
        minimumQuality = Math.max(0, minimumQuality);
        int providedQuality = card.getWeightInfluences(crop,
                crop.getTerrainHumidity(), crop.getTerrainNutrients(), crop.getTerrainAirQuality()) * 5;
        if (providedQuality < minimumQuality) {
            return false; // условия плохие: IC2 мог бы погубить растение — не рискуем
        }
        crop.performGrowthTick();
        card = crop.getCrop();
        if (card != null && crop.getGrowthPoints() >= card.getGrowthDuration(crop)) {
            crop.setGrowthPoints(0);
            crop.setCurrentSize(Math.min(crop.getCurrentSize() + 1, card.getMaxSize()));
            crop.dirty = true; // IC2 сам отправит новый размер клиентам и обновит блок
        }
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Полив и подкормка                                                   */
    /* ------------------------------------------------------------------ */

    /** Пополняет запас воды жёрдочки (как гидратирующая ячейка / Crop-Matron). */
    public static boolean hydrate(TileEntity tile) {
        if (!(tile instanceof ICropTile) || THConfig.ic2HydrationTarget <= 0) {
            return false;
        }
        ICropTile crop = (ICropTile) tile;
        if (crop.getStorageWater() < THConfig.ic2HydrationTarget) {
            crop.setStorageWater(THConfig.ic2HydrationTarget);
            return true;
        }
        return false;
    }

    /**
     * Пополняет питательные вещества жёрдочки до 100 (как удобрение IC2 / Crop-Matron).
     *
     * @param available доступный запас удобрения
     * @return сколько единиц запаса потрачено
     */
    public static int feedNutrients(TileEntity tile, int available) {
        if (!(tile instanceof ICropTile) || ((ICropTile) tile).getCrop() == null) {
            return 0;
        }
        ICropTile crop = (ICropTile) tile;
        int missing = 100 - crop.getStorageNutrients();
        if (missing < 10) {
            return 0;
        }
        int cost = (missing / 10) * THConfig.ic2NutrientCostPer10;
        if (cost > available) {
            return 0;
        }
        crop.setStorageNutrients(crop.getStorageNutrients() + (missing / 10) * 10);
        return cost;
    }
}
