package dlx.thermalharvest.compat;

import cofh.core.util.helpers.StringHelper;
import dlx.thermalharvest.block.BlockHarvester;
import dlx.thermalharvest.block.TileHarvester;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.IWailaRegistrar;
import mcp.mobius.waila.api.SpecialChars;
import mcp.mobius.waila.api.WailaPlugin;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Hwyla: при наведении на Сборщик показывает содержимое внутреннего хранилища (иконки с количеством,
 * одинаковые предметы объединены), а также запас удобрения и воду в баке.
 * Класс загружается только самим Hwyla (через аннотацию @WailaPlugin).
 */
@WailaPlugin
public class WailaHarvester implements IWailaPlugin, IWailaDataProvider {

    private static final int PER_LINE = 5;
    private static final int MAX_TYPES = 15;

    @Override
    public void register(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(this, BlockHarvester.class);
        registrar.registerNBTProvider(this, BlockHarvester.class);
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity te, NBTTagCompound tag, World world, BlockPos pos) {
        if (!(te instanceof TileHarvester)) {
            return tag;
        }
        TileHarvester tile = (TileHarvester) te;
        List<ItemStack> merged = new ArrayList<>();
        for (int i = 0; i < TileHarvester.OUTPUT_SLOTS; i++) {
            ItemStack stack = tile.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            boolean added = false;
            for (ItemStack m : merged) {
                if (ItemHandlerHelper.canItemStacksStack(m, stack)) {
                    m.grow(stack.getCount());
                    added = true;
                    break;
                }
            }
            if (!added && merged.size() < MAX_TYPES) {
                merged.add(stack.copy());
            }
        }
        NBTTagList list = new NBTTagList();
        for (ItemStack m : merged) {
            NBTTagCompound t = new NBTTagCompound();
            t.setString("id", String.valueOf(m.getItem().getRegistryName()));
            t.setInteger("count", m.getCount());
            t.setInteger("meta", m.getMetadata());
            if (m.hasTagCompound()) {
                t.setString("nbt", m.getTagCompound().toString());
            }
            list.appendTag(t);
        }
        tag.setTag("THItems", list);
        tag.setInteger("THNutrients", tile.getNutrients());
        tag.setFloat("THMult", (float) tile.getGrowthMultiplier());
        if (tile.hasIrrigation()) {
            tag.setInteger("THWater", tile.getTank().getFluidAmount());
            tag.setInteger("THWaterCap", tile.getTank().getCapacity());
        }
        return tag;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> tip, IWailaDataAccessor accessor, IWailaConfigHandler config) {
        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null || !tag.hasKey("THItems")) {
            return tip;
        }
        NBTTagList list = tag.getTagList("THItems", 10);
        if (list.tagCount() == 0) {
            tip.add(StringHelper.localize("waila.thermalharvest.empty"));
        } else {
            StringBuilder line = new StringBuilder();
            int inLine = 0;
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound t = list.getCompoundTagAt(i);
                line.append(SpecialChars.getRenderString("waila.stack", "1", t.getString("id"),
                        String.valueOf(t.getInteger("count")), String.valueOf(t.getInteger("meta")), t.getString("nbt")));
                if (++inLine >= PER_LINE) {
                    tip.add(line.toString());
                    line = new StringBuilder();
                    inLine = 0;
                }
            }
            if (line.length() > 0) {
                tip.add(line.toString());
            }
        }
        int nutrients = tag.getInteger("THNutrients");
        if (nutrients > 0) {
            tip.add(StringHelper.localize("gui.thermalharvest.nutrients") + ": " + dlx.thermalharvest.util.THLang.cycles(nutrients)
                    + " (" + dlx.thermalharvest.util.THLang.mult(tag.getFloat("THMult")) + ")");
        }
        if (tag.hasKey("THWater")) {
            tip.add(StringHelper.localize("waila.thermalharvest.water") + ": " + tag.getInteger("THWater") + " / " + tag.getInteger("THWaterCap") + " mB");
        }
        return tip;
    }
}
