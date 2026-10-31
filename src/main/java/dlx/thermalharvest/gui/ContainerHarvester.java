package dlx.thermalharvest.gui;

import cofh.core.gui.container.ContainerTileAugmentable;
import cofh.core.gui.slot.ISlotValidator;
import cofh.core.gui.slot.SlotEnergy;
import cofh.core.gui.slot.SlotRemoveOnly;
import cofh.core.gui.slot.SlotValidated;
import dlx.thermalharvest.block.TileHarvester;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class ContainerHarvester extends ContainerTileAugmentable implements ISlotValidator {

    public static final int GRID_X = 58;
    public static final int GRID_Y = 17;
    public static final int FERT_X = 34;
    public static final int FERT_Y = 17;

    protected final TileHarvester myTile;

    public ContainerHarvester(InventoryPlayer inventory, TileEntity tile) {
        super(inventory, tile);
        myTile = (TileHarvester) tile;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 5; col++) {
                addSlotToContainer(new SlotRemoveOnly(myTile, col + row * 5, GRID_X + col * 18, GRID_Y + row * 18));
            }
        }
        addSlotToContainer(new SlotEnergy(myTile, TileHarvester.SLOT_CHARGE, 8, 53));
        addSlotToContainer(new SlotValidated(this, myTile, TileHarvester.SLOT_FERTILIZER, FERT_X, FERT_Y));
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return TileHarvester.isFertilizer(stack);
    }
}
