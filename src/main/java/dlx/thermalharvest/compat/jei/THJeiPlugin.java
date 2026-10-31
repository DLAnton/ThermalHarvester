package dlx.thermalharvest.compat.jei;

import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.init.THContent;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.IRecipeRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.recipe.IRecipeCategoryRegistration;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Интеграция с HEI/JEI: страница «Удобрения Сборщика урожая» (клавиши R/U на любом удобрении или на самом сборщике).
 * При входе на сервер с другим списком удобрений страница обновляется: новые добавляются, отсутствующие скрываются.
 */
@JEIPlugin
public class THJeiPlugin implements IModPlugin {

    private static final Map<String, FertilizerRecipe> RECIPES = new LinkedHashMap<>();
    private static final Set<String> HIDDEN = new HashSet<>();
    private static IRecipeRegistry registry;
    private static boolean listening;

    @Override
    public void registerCategories(IRecipeCategoryRegistration reg) {
        reg.addRecipeCategories(new FertilizerCategory(reg.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void register(IModRegistry reg) {
        RECIPES.clear();
        HIDDEN.clear();
        List<FertilizerRecipe> list = new ArrayList<>();
        for (String key : THConfig.FERTILIZERS.keySet()) {
            FertilizerRecipe r = create(key);
            if (r != null) {
                list.add(r);
            }
        }
        list.sort(Comparator.<FertilizerRecipe>comparingInt(r -> THConfig.FERTILIZERS.get(r.key).multiplier)
                .thenComparingInt(r -> THConfig.FERTILIZERS.get(r.key).units));
        for (FertilizerRecipe r : list) {
            RECIPES.put(r.key, r);
        }
        reg.addRecipes(list, FertilizerCategory.UID);
        reg.addRecipeCatalyst(new ItemStack(THContent.blockHarvester), FertilizerCategory.UID);
        // все расширения сборщика — колонка катализаторов служит списком совместимых модулей:
        // 0 — расширитель зоны, 1 — модуль орошения, 2 — Рециркулятор, 3 — Катализатор роста
        for (int meta = 0; meta < 4; meta++) {
            reg.addRecipeCatalyst(new ItemStack(THContent.itemAugment, 1, meta), FertilizerCategory.UID);
        }
        if (!listening) {
            listening = true;
            THConfig.addListener(THJeiPlugin::refresh);
        }
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        registry = runtime.getRecipeRegistry();
        refresh();
    }

    /** Сверяет страницу с текущим списком удобрений. */
    private static void refresh() {
        if (registry == null) {
            return;
        }
        for (String key : THConfig.FERTILIZERS.keySet()) {
            if (!RECIPES.containsKey(key)) {
                FertilizerRecipe r = create(key);
                if (r != null) {
                    RECIPES.put(key, r);
                    registry.addRecipe(r, FertilizerCategory.UID);
                }
            }
        }
        for (FertilizerRecipe r : RECIPES.values()) {
            boolean present = THConfig.FERTILIZERS.containsKey(r.key);
            if (!present && HIDDEN.add(r.key)) {
                registry.hideRecipe(r, FertilizerCategory.UID);
            } else if (present && HIDDEN.remove(r.key)) {
                registry.unhideRecipe(r, FertilizerCategory.UID);
            }
        }
    }

    private static FertilizerRecipe create(String key) {
        String[] id = key.split(":");
        if (id.length < 3) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id[0], id[1]));
        if (item == null) {
            return null; // мод с этим удобрением не установлен
        }
        int meta;
        try {
            meta = Integer.parseInt(id[2]);
        } catch (NumberFormatException e) {
            return null;
        }
        return new FertilizerRecipe(key, new ItemStack(item, 1, meta));
    }
}
