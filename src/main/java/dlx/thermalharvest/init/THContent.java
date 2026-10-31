package dlx.thermalharvest.init;

import cofh.core.util.helpers.RecipeHelper;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.item.ItemFrame;
import cofh.thermalexpansion.item.ItemReservoir;
import cofh.thermalfoundation.item.ItemFertilizer;
import cofh.thermalfoundation.item.ItemMaterial;
import cofh.thermalfoundation.item.ItemUpgrade;
import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.block.BlockHarvester;
import dlx.thermalharvest.block.ItemBlockHarvester;
import dlx.thermalharvest.block.TileHarvester;
import dlx.thermalharvest.item.ItemHarvesterAugment;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = ThermalHarvest.MOD_ID)
public final class THContent {

    public static BlockHarvester blockHarvester;
    public static ItemHarvesterAugment itemAugment;

    private THContent() {}

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        TileHarvester.initialize();
        blockHarvester = new BlockHarvester();
        event.getRegistry().register(blockHarvester);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        BlockHarvester.itemBlock = new ItemBlockHarvester(blockHarvester);
        BlockHarvester.itemBlock.setRegistryName(blockHarvester.getRegistryName());
        event.getRegistry().register(BlockHarvester.itemBlock);

        itemAugment = new ItemHarvesterAugment();
        event.getRegistry().register(itemAugment);
    }

    /** LOW — чтобы предметы TE/TF/TC (рамка, катушки, лейка) гарантированно были созданы. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        BlockHarvester.harvester = BlockHarvester.itemBlock.setDefaultTag(new ItemStack(blockHarvester), 0);

        // Сборщик: как рецепты машин TE (" X ", "YCY", "IPI")
        RecipeHelper.addShapedRecipe(BlockHarvester.harvester,
                " X ", "YCY", "IPI",
                'C', ItemFrame.frameMachine,
                'I', "gearCopper",
                'P', ItemMaterial.powerCoilGold,
                'X', Items.IRON_HOE,
                'Y', Blocks.HOPPER);

        // Расширитель радиуса
        RecipeHelper.addShapedRecipe(ItemHarvesterAugment.augmentRange,
                " G ", "RCR", " G ",
                'C', ItemMaterial.redstoneServo,
                'R', "dustRedstone",
                'G', "ingotBronze");

        // Модуль автоорошения: центр — базовый Резервуар Thermal Expansion (бак на 10 000 mB)
        RecipeHelper.addShapedRecipe(ItemHarvesterAugment.augmentIrrigation,
                " T ", "SCS", " T ",
                'C', ItemReservoir.reservoirBasic,
                'S', ItemMaterial.redstoneServo,
                'T', "ingotTin");

        // Рециркулятор удобрений — в духе «Восстановителя питательных веществ» Фитогенного облучателя
        RecipeHelper.addShapedRecipe(ItemHarvesterAugment.augmentRecycler,
                " G ", "ICI", " X ",
                'C', ItemMaterial.redstoneServo,
                'G', "gearSilver",
                'I', ItemFertilizer.fertilizerRich,
                'X', Blocks.HOPPER);

        // Катализатор роста — ставится только один
        RecipeHelper.addShapedRecipe(ItemHarvesterAugment.augmentCatalyst,
                " G ", "ICI", " X ",
                'C', ItemMaterial.powerCoilElectrum,
                'G', "gearLumium",
                'I', ItemFertilizer.fertilizerFlux,
                'X', "blockGlassHardened");

        // Улучшение в сетке крафта, если это включено в конфиге TE (Machine.UpgradeKitCrafting)
        if (BlockMachine.enableUpgradeKitCrafting) {
            ItemStack[] block = new ItemStack[5];
            for (int j = 0; j < 5; j++) {
                block[j] = BlockHarvester.itemBlock.setDefaultTag(new ItemStack(blockHarvester), j);
            }
            for (int j = 0; j < 4; j++) {
                RecipeHelper.addShapelessUpgradeKitRecipe(block[j + 1], block[j], ItemUpgrade.upgradeIncremental[j]);
            }
            for (int j = 1; j < 4; j++) {
                for (int k = 0; k <= j; k++) {
                    RecipeHelper.addShapelessUpgradeKitRecipe(block[j + 1], block[k], ItemUpgrade.upgradeFull[j]);
                }
            }
        }
    }
}
