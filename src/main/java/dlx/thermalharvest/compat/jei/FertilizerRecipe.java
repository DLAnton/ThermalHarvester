package dlx.thermalharvest.compat.jei;

import cofh.core.util.helpers.StringHelper;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.util.THLang;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeWrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

/** Одно удобрение на странице HEI. Цифры берутся из текущих настроек (в том числе присланных сервером). */
public class FertilizerRecipe implements IRecipeWrapper {

    private static final int TEXT_X = 22;
    private static final int COLOR = 0x404040;
    private static final int COLOR_DIM = 0x808080;

    final String key;
    final ItemStack stack;

    FertilizerRecipe(String key, ItemStack stack) {
        this.key = key;
        this.stack = stack;
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInput(VanillaTypes.ITEM, stack);
    }

    @Override
    public void drawInfo(Minecraft mc, int width, int height, int mouseX, int mouseY) {
        FontRenderer font = mc.fontRenderer;
        THConfig.Fertilizer f = THConfig.FERTILIZERS.get(key);
        if (f == null) {
            font.drawString(StringHelper.localize("jei.thermalharvest.fertilizer.unused"), TEXT_X, 6, COLOR_DIM);
            return;
        }
        font.drawString(StringHelper.localizeFormat("jei.thermalharvest.fertilizer.growth", f.multiplier), TEXT_X, 0, COLOR);
        font.drawString(StringHelper.localizeFormat("gui.thermalharvest.perItem", THLang.cycles(f.units)), TEXT_X, 10, COLOR);
        if (THConfig.catalystBonus > 0) {
            font.drawString(StringHelper.localizeFormat("jei.thermalharvest.fertilizer.catalyst", f.multiplier + THConfig.catalystBonus),
                    TEXT_X, 20, COLOR_DIM);
        }
    }
}
