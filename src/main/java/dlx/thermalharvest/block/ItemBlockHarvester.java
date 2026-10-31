package dlx.thermalharvest.block;

import cofh.api.tileentity.IRedstoneControl;
import cofh.core.block.BlockCore;
import cofh.core.util.helpers.EnergyHelper;
import cofh.core.util.helpers.ReconfigurableHelper;
import cofh.core.util.helpers.RedstoneControlHelper;
import cofh.core.util.helpers.SecurityHelper;
import cofh.core.util.helpers.StringHelper;
import cofh.thermalexpansion.block.ItemBlockTEBase;
import cofh.thermalexpansion.block.machine.TileMachineBase;
import cofh.thermalexpansion.init.TEProps;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.util.THLang;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nullable;
import java.util.List;

public class ItemBlockHarvester extends ItemBlockTEBase {

    public ItemBlockHarvester(BlockCore block) {
        super(block);
    }

    @Override
    public ItemStack setDefaultTag(ItemStack stack, int level) {
        ReconfigurableHelper.setFacing(stack, 3);
        ReconfigurableHelper.setSideCache(stack, TileHarvester.SIDE_CONFIG.defaultSides);
        RedstoneControlHelper.setControl(stack, IRedstoneControl.ControlMode.DISABLED);
        EnergyHelper.setDefaultEnergyTag(stack, 0);
        stack.getTagCompound().setByte("Level", (byte) level);
        stack.getTagCompound().setInteger(TileHarvester.TAG_VERSION, TileHarvester.CONFIG_VERSION);
        return stack;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        SecurityHelper.addOwnerInformation(stack, tooltip);
        if (stack.getTagCompound() != null && stack.getTagCompound().getInteger(TileHarvester.TAG_NUTRIENTS) > 0) {
            // сохранённый при разборе запас удобрения — видно сразу, без Shift
            String line = StringHelper.localize("gui.thermalharvest.nutrients") + ": " + StringHelper.YELLOW
                    + THLang.cycles(stack.getTagCompound().getInteger(TileHarvester.TAG_NUTRIENTS)) + StringHelper.END;
            if (stack.getTagCompound().hasKey(TileHarvester.TAG_NUTRIENT_SOURCE)) {
                ItemStack source = new ItemStack(stack.getTagCompound().getCompoundTag(TileHarvester.TAG_NUTRIENT_SOURCE));
                if (!source.isEmpty()) {
                    float mult = stack.getTagCompound().getFloat(TileHarvester.TAG_NUTRIENT_MULT);
                    THConfig.Fertilizer f = THConfig.getFertilizer(source);
                    String name = f == null || Math.abs(f.multiplier - mult) > 0.005F
                            ? StringHelper.localize("gui.thermalharvest.fertilizerMix") : source.getDisplayName();
                    line += StringHelper.LIGHT_GRAY + " (" + name + ", " + THLang.mult(mult) + ")" + StringHelper.END;
                }
            }
            tooltip.add(line);
        }
        if (StringHelper.displayShiftForDetail && !StringHelper.isShiftKeyDown()) {
            tooltip.add(StringHelper.shiftForDetails());
        }
        if (!StringHelper.isShiftKeyDown()) {
            return;
        }
        SecurityHelper.addAccessInformation(stack, tooltip);
        tooltip.add(StringHelper.getInfoText("info.thermalharvest.harvester"));

        int level = isCreative(stack) ? 4 : getLevel(stack);
        int side = Math.min(THConfig.baseRadius + level, THConfig.maxRadius) * 2 + 1;
        int slots = TileMachineBase.NUM_AUGMENTS[Math.max(0, Math.min(level, 4))];
        tooltip.add(StringHelper.localize("info.thermalharvest.area") + ": " + StringHelper.YELLOW + side + "x" + side + "x3" + StringHelper.END);
        tooltip.add(StringHelper.localize("info.thermalharvest.augmentSlots") + ": " + StringHelper.YELLOW + slots + StringHelper.END);
        tooltip.add(StringHelper.localize("info.thermalharvest.scanSpeed") + ": " + StringHelper.YELLOW
                + THConfig.columnsPerSecond(level) + " " + StringHelper.localize("info.thermalharvest.columnsPerSecond") + StringHelper.END);

        if (getLevel(stack) >= TEProps.levelRedstoneControl) {
            RedstoneControlHelper.addRSControlInformation(stack, tooltip);
        }
    }
}
