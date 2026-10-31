package dlx.thermalharvest.compat.jei;

import cofh.core.util.helpers.StringHelper;
import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.init.THContent;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IDrawableStatic;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;

/** Категория HEI «Удобрения Сборщика урожая»: какие предметы принимаются и что они дают. */
public class FertilizerCategory implements IRecipeCategory<FertilizerRecipe> {

    public static final String UID = ThermalHarvest.MOD_ID + ".fertilizer";

    private final IDrawable background;
    private final IDrawable icon;
    private final IDrawableStatic slot;

    FertilizerCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(150, 28);
        icon = gui.createDrawableIngredient(new ItemStack(THContent.blockHarvester));
        slot = gui.getSlotDrawable();
    }

    @Override
    public String getUid() {
        return UID;
    }

    @Override
    public String getTitle() {
        return StringHelper.localize("jei.thermalharvest.fertilizer.title");
    }

    @Override
    public String getModName() {
        return ThermalHarvest.MOD_NAME;
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void drawExtras(Minecraft mc) {
        slot.draw(mc, 0, 5);
    }

    @Override
    public void setRecipe(IRecipeLayout layout, FertilizerRecipe recipe, IIngredients ingredients) {
        layout.getItemStacks().init(0, true, 0, 5);
        layout.getItemStacks().set(ingredients);
    }
}
