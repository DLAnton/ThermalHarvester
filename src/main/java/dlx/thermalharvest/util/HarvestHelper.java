package dlx.thermalharvest.util;

import dlx.thermalharvest.init.THConfig;
import net.minecraft.block.Block;
import net.minecraft.block.BlockCrops;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.BlockNetherWart;
import net.minecraft.block.BlockStem;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyInteger;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Работа с «грядочными» культурами: только растения, стоящие на пашне (из любого мода),
 * плоды (тыква/арбуз) от стебля, растущего на пашне, и — опционально — адский нарост на песке душ.
 * Жёрдочки IC2 обрабатываются отдельно (IC2CropCompat).
 * <p>
 * Растение собирается только если его можно пересадить семенем из его же урожая —
 * сборщик никогда не оставляет пустую пашню вместо культуры.
 */
public final class HarvestHelper {

    private HarvestHelper() {}

    /* ------------------------------------------------------------------ */
    /* Классификация                                                       */
    /* ------------------------------------------------------------------ */

    /** Растение, растущее на пашне (стебли тыквы/арбуза не считаются — собираются их плоды). */
    public static boolean isFarmPlant(IBlockAccess world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockStem || !(block instanceof IPlantable || block instanceof BlockCrops)) {
            return false;
        }
        if (findAgeProperty(state) == null && !(block instanceof BlockCrops)) {
            return false;
        }
        Block soil = world.getBlockState(pos.down()).getBlock();
        if (soil instanceof BlockFarmland) {
            return true;
        }
        return block instanceof BlockNetherWart && soil == Blocks.SOUL_SAND && THConfig.harvestNetherWartOnSoulSand;
    }

    public static boolean isMature(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockCrops) {
            return ((BlockCrops) block).isMaxAge(state);
        }
        PropertyInteger age = findAgeProperty(state);
        if (age == null) {
            return false;
        }
        int max = Collections.max(age.getAllowedValues());
        return max > Collections.min(age.getAllowedValues()) && state.getValue(age) >= max;
    }

    /**
     * Стебель тыквы/арбуза без плода, которому удобрение действительно поможет: он ещё растёт,
     * либо уже вырос и рядом есть свободное место для плода (как в BlockStem.updateTick).
     * Окружённый со всех сторон стебель удобрение не тратит.
     */
    public static boolean isGrowableStem(World world, BlockPos pos, IBlockState state) {
        if (!(state.getBlock() instanceof BlockStem) || !(world.getBlockState(pos.down()).getBlock() instanceof BlockFarmland)) {
            return false;
        }
        if (state.getActualState(world, pos).getValue(BlockStem.FACING) != EnumFacing.UP) {
            return false; // плод уже вырос
        }
        if (state.getValue(BlockStem.AGE) < 7) {
            return true;
        }
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            BlockPos spot = pos.offset(face);
            if (!world.isAirBlock(spot)) {
                continue;
            }
            IBlockState soil = world.getBlockState(spot.down());
            Block b = soil.getBlock();
            if (b instanceof BlockFarmland || b == Blocks.DIRT || b == Blocks.GRASS
                    || b.canSustainPlant(soil, world, spot.down(), EnumFacing.UP, (BlockStem) state.getBlock())) {
                return true;
            }
        }
        return false;
    }

    /** Тыква/арбуз, к которому повёрнут стебель, растущий на пашне. */
    public static boolean isFarmFruit(IBlockAccess world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block != Blocks.PUMPKIN && block != Blocks.MELON_BLOCK) {
            return false;
        }
        for (EnumFacing face : EnumFacing.HORIZONTALS) {
            BlockPos stemPos = pos.offset(face);
            IBlockState stem = world.getBlockState(stemPos);
            if (!(stem.getBlock() instanceof BlockStem)) {
                continue;
            }
            if (!(world.getBlockState(stemPos.down()).getBlock() instanceof BlockFarmland)) {
                continue;
            }
            IBlockState actual = stem.getActualState(world, stemPos);
            if (actual.getValue(BlockStem.FACING) == face.getOpposite()) {
                return true;
            }
        }
        return false;
    }

    /* ------------------------------------------------------------------ */
    /* Сбор                                                                */
    /* ------------------------------------------------------------------ */

    /**
     * Собирает созревшую культуру и пересаживает её.
     *
     * @return урожай (без семени, ушедшего на пересадку) или null, если собирать нельзя.
     */
    public static List<ItemStack> harvestPlant(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!isMature(state)) {
            return null;
        }
        IBlockState replanted = replantState(state);
        if (replanted == null) {
            return null;
        }
        Block block = state.getBlock();
        ItemStack preferred = block.getItem(world, pos, state);
        // «Сухая» проверка без событий: даёт ли растение семя для пересадки
        NonNullList<ItemStack> probe = NonNullList.create();
        block.getDrops(probe, world, pos, state, 0);
        probe.removeIf(ItemStack::isEmpty);
        if (!removeSeed(world, pos, probe, preferred, block)) {
            return null; // нечем пересадить — не трогаем
        }
        if (!canBreak(world, pos, state, player)) {
            return null;
        }
        NonNullList<ItemStack> drops = collectDrops(world, pos, state, player);
        removeSeed(world, pos, drops, preferred, block); // если семени в итоговом урожае нет — пересадка за счёт машины
        if (THConfig.breakParticles) {
            world.playEvent(2001, pos, Block.getStateId(state));
        }
        world.setBlockState(pos, replanted, 3);
        return drops;
    }

    public static List<ItemStack> harvestFruit(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        if (!canBreak(world, pos, state, player)) {
            return null;
        }
        NonNullList<ItemStack> drops = collectDrops(world, pos, state, player);
        if (THConfig.breakParticles) {
            world.playEvent(2001, pos, Block.getStateId(state));
        }
        world.setBlockToAir(pos);
        return drops;
    }

    private static IBlockState replantState(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockCrops) {
            return ((BlockCrops) block).withAge(0);
        }
        PropertyInteger age = findAgeProperty(state);
        return age == null ? null : state.withProperty(age, Collections.min(age.getAllowedValues()));
    }

    /** Защита территорий и прочие моды могут запретить разрушение. */
    private static boolean canBreak(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(world, pos, state, player);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    private static NonNullList<ItemStack> collectDrops(World world, BlockPos pos, IBlockState state, EntityPlayer player) {
        NonNullList<ItemStack> drops = NonNullList.create();
        state.getBlock().getDrops(drops, world, pos, state, 0);
        float chance = ForgeEventFactory.fireBlockHarvesting(drops, world, pos, state, 0, 1.0F, false, player);
        if (chance < 1.0F) {
            Iterator<ItemStack> it = drops.iterator();
            while (it.hasNext()) {
                it.next();
                if (world.rand.nextFloat() > chance) {
                    it.remove();
                }
            }
        }
        drops.removeIf(ItemStack::isEmpty);
        return drops;
    }

    /** Убирает из урожая одно семя для пересадки. */
    private static boolean removeSeed(World world, BlockPos pos, NonNullList<ItemStack> drops, ItemStack preferred, Block block) {
        if (!preferred.isEmpty()) {
            for (int i = 0; i < drops.size(); i++) {
                ItemStack stack = drops.get(i);
                if (!stack.isEmpty() && ItemStack.areItemsEqual(stack, preferred)) {
                    shrink(drops, i);
                    return true;
                }
            }
        }
        for (int i = 0; i < drops.size(); i++) {
            ItemStack stack = drops.get(i);
            if (!stack.isEmpty() && stack.getItem() instanceof IPlantable) {
                IBlockState plant = ((IPlantable) stack.getItem()).getPlant(world, pos);
                if (plant != null && plant.getBlock() == block) {
                    shrink(drops, i);
                    return true;
                }
            }
        }
        return false;
    }

    private static void shrink(NonNullList<ItemStack> drops, int index) {
        ItemStack stack = drops.get(index);
        stack.shrink(1);
        if (stack.isEmpty()) {
            drops.remove(index);
        }
    }

    public static PropertyInteger findAgeProperty(IBlockState state) {
        for (IProperty<?> prop : state.getPropertyKeys()) {
            if (prop instanceof PropertyInteger && "age".equals(prop.getName())) {
                return (PropertyInteger) prop;
            }
        }
        return null;
    }

    /** Блок, который обрабатывает сборщик (для подсветки зоны): пашня, культура на пашне, стебель на пашне, плод. */
    public static boolean isProcessedBlock(IBlockAccess world, BlockPos pos, IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockFarmland) {
            return true;
        }
        if (isFarmPlant(world, pos, state)) {
            return true;
        }
        if (block instanceof BlockStem && world.getBlockState(pos.down()).getBlock() instanceof BlockFarmland) {
            return true;
        }
        return isFarmFruit(world, pos, state);
    }
}
