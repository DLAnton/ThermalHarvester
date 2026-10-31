package dlx.thermalharvest.item;

import cofh.api.item.IAugmentItem;
import cofh.core.item.ItemMulti;
import cofh.core.util.helpers.ItemHelper;
import cofh.core.util.helpers.StringHelper;
import cofh.thermalexpansion.ThermalExpansion;
import dlx.thermalharvest.ThermalHarvest;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Расширения для Сборщика урожая. Реализуют IAugmentItem из CoFH API, поэтому вставляются во вкладку
 * расширений так же, как расширения Thermal Expansion.
 */
public class ItemHarvesterAugment extends ItemMulti implements IAugmentItem {

    public static final String ID_RANGE = "harvesterRange";
    public static final String ID_IRRIGATION = "harvesterIrrigation";
    public static final String ID_RECYCLER = "harvesterRecycler";
    public static final String ID_CATALYST = "harvesterCatalyst";

    public static ItemStack augmentRange = ItemStack.EMPTY;
    public static ItemStack augmentIrrigation = ItemStack.EMPTY;
    public static ItemStack augmentRecycler = ItemStack.EMPTY;
    public static ItemStack augmentCatalyst = ItemStack.EMPTY;

    private final Map<Integer, Entry> entries = new HashMap<>();

    public ItemHarvesterAugment() {
        super(ThermalHarvest.MOD_ID);
        setTranslationKey("augment");
        setCreativeTab(ThermalExpansion.tabUtils);
        setRegistryName(ThermalHarvest.MOD_ID, "augment");

        augmentRange = addAugment(0, ID_RANGE, AugmentType.BASIC, EnumRarity.UNCOMMON);
        augmentIrrigation = addAugment(1, ID_IRRIGATION, AugmentType.ADVANCED, EnumRarity.UNCOMMON);
        // meta 2 раньше был «Стимулятором роста» (1.1.0) — такие предметы становятся Рециркуляторами
        augmentRecycler = addAugment(2, ID_RECYCLER, AugmentType.BASIC, EnumRarity.UNCOMMON);
        augmentCatalyst = addAugment(3, ID_CATALYST, AugmentType.ADVANCED, EnumRarity.RARE);
    }

    private ItemStack addAugment(int meta, String id, AugmentType type, EnumRarity rarity) {
        entries.put(meta, new Entry(id, type));
        return addItem(meta, id, rarity);
    }

    @Override
    public String getItemStackDisplayName(ItemStack stack) {
        // "Расширение: Расширитель радиуса" — как у расширений Thermal Expansion
        return StringHelper.localize("info.thermalexpansion.augment.0") + ": " + super.getItemStackDisplayName(stack);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        String id = getAugmentIdentifier(stack);
        if (id.isEmpty()) {
            return;
        }
        addLines(tooltip, "info.thermalharvest.augment." + id + ".", "");
        if (StringHelper.displayShiftForDetail && !StringHelper.isShiftKeyDown()) {
            tooltip.add(StringHelper.shiftForDetails());
        }
        if (!StringHelper.isShiftKeyDown()) {
            return;
        }
        addLines(tooltip, "info.thermalharvest.augment." + id + ".a.", StringHelper.BRIGHT_GREEN);
        addLines(tooltip, "info.thermalharvest.augment." + id + ".b.", "§4");
        addLines(tooltip, "info.thermalharvest.augment." + id + ".c.", StringHelper.YELLOW);
    }

    private static void addLines(List<String> tooltip, String prefix, String color) {
        int i = 0;
        String key = prefix + i;
        while (StringHelper.canLocalize(key)) {
            tooltip.add(color + StringHelper.localize(key));
            key = prefix + (++i);
        }
    }

    /* IAugmentItem */

    @Override
    public AugmentType getAugmentType(ItemStack stack) {
        Entry e = entries.get(ItemHelper.getItemDamage(stack));
        return e == null ? AugmentType.CREATIVE : e.type;
    }

    @Override
    public String getAugmentIdentifier(ItemStack stack) {
        Entry e = entries.get(ItemHelper.getItemDamage(stack));
        return e == null ? "" : e.id;
    }

    private static final class Entry {
        final String id;
        final AugmentType type;

        Entry(String id, AugmentType type) {
            this.id = id;
            this.type = type;
        }
    }
}
