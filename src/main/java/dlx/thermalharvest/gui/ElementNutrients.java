package dlx.thermalharvest.gui;

import cofh.core.gui.GuiContainerCore;
import cofh.core.gui.element.ElementBase;
import cofh.core.util.helpers.RenderHelper;
import cofh.core.util.helpers.StringHelper;
import dlx.thermalharvest.block.TileHarvester;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.util.THLang;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.GlStateManager;

import java.util.List;

/** Узкая шкала запаса питательности под слотом удобрения. Рамка нарисована в текстуре GUI, здесь — заполнение. */
public class ElementNutrients extends ElementBase {

    private static final int FILL_U = 176;
    private static final int FILL_V = 100;
    private static final int FILL_W = 16;
    private static final int FILL_H = 2;

    private final TileHarvester tile;

    public ElementNutrients(GuiContainerCore gui, int posX, int posY, TileHarvester tile) {
        super(gui, posX, posY, 18, 4);
        this.tile = tile;
        setTexture(GuiHarvester.TEX_PATH, 256, 256);
    }

    @Override
    public void drawBackground(int mouseX, int mouseY, float gameTicks) {
        int width = tile.getScaledNutrients(FILL_W);
        if (width <= 0) {
            return;
        }
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        RenderHelper.bindTexture(texture);
        drawTexturedModalRect(posX + 1, posY + 1, FILL_U, FILL_V, width, FILL_H);
    }

    @Override
    public void drawForeground(int mouseX, int mouseY) {
    }

    /**
     * Подсказка:
     * <pre>
     * Запас удобрения: 960 / 1000 циклов роста
     * Костная мука: x2, 64 цикла за штуку
     * Скорость роста: x3 (x2 +1 Катализатор)
     * Экономия удобрения: 40% (Рециркулятор)
     * Следующее: Богатое удобрение (x3) — когда в запасе освободится место
     * </pre>
     */
    @Override
    public void addTooltip(List<String> list) {
        int nutrients = tile.getNutrients();
        list.add(StringHelper.localize("gui.thermalharvest.nutrients") + ": " + nutrients + " / "
                + THLang.cycles(THConfig.fertilizerCapacity) + " " + StringHelper.localize("gui.thermalharvest.ofGrowth"));
        if (nutrients <= 0) {
            list.add(GRAY + StringHelper.localize("gui.thermalharvest.nutrients.empty") + StringHelper.END);
            return;
        }
        ItemStack source = tile.getNutrientSource();
        double base = tile.getBaseMultiplier();
        THConfig.Fertilizer f = source == null || source.isEmpty() ? null : THConfig.getFertilizer(source);
        String line;
        if (tile.isNutrientMixed()) {
            // в запас подмешаны удобрения разной силы — сила средняя по циклам роста
            line = StringHelper.YELLOW + StringHelper.localize("gui.thermalharvest.fertilizerMix") + StringHelper.END + ": " + THLang.mult(base);
        } else {
            String name = source == null || source.isEmpty() ? StringHelper.localize("gui.thermalharvest.fertilizer") : source.getDisplayName();
            line = StringHelper.YELLOW + name + StringHelper.END + ": " + THLang.mult(base);
            if (f != null) {
                line += ", " + StringHelper.localizeFormat("gui.thermalharvest.perItem", THLang.cycles(f.units));
            }
        }
        list.add(line);

        double total = tile.getGrowthMultiplier();
        String speed = StringHelper.localize("gui.thermalharvest.growthSpeed") + ": " + StringHelper.WHITE + THLang.mult(total) + StringHelper.END;
        if (total > base + 0.001D) {
            speed += GRAY + " (" + THLang.mult(base) + " +" + Math.round(total - base) + " "
                    + StringHelper.localize("item.thermalharvest.augment.harvesterCatalyst.short") + ")" + StringHelper.END;
        }
        list.add(speed);

        int chance = tile.getRecyclerChance();
        if (chance > 0) {
            list.add(StringHelper.localize("gui.thermalharvest.recycle") + ": " + StringHelper.WHITE + chance + "%" + StringHelper.END
                    + GRAY + " (" + StringHelper.localize("item.thermalharvest.augment.harvesterRecycler.short") + ")" + StringHelper.END);
        }

        // Удобрение лежит в ячейке — значит, не помещается в запас и ждёт свободного места
        ItemStack next = tile.getStackInSlot(TileHarvester.SLOT_FERTILIZER);
        THConfig.Fertilizer nf = next.isEmpty() ? null : THConfig.getFertilizer(next);
        if (nf != null) {
            list.add(GRAY + StringHelper.localizeFormat("gui.thermalharvest.nextFertilizer", next.getDisplayName(), THLang.mult(nf.multiplier)) + StringHelper.END);
        }
    }

    private static final String GRAY = StringHelper.LIGHT_GRAY;
}
