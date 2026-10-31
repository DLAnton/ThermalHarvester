package dlx.thermalharvest.block;

import codechicken.lib.block.property.unlisted.UnlistedGenericTile;
import codechicken.lib.model.ModelRegistryHelper;
import codechicken.lib.model.bakery.CCBakeryModel;
import codechicken.lib.model.bakery.IBakeryProvider;
import codechicken.lib.model.bakery.ModelBakery;
import codechicken.lib.model.bakery.ModelErrorStateProperty;
import codechicken.lib.model.bakery.generation.IBakery;
import codechicken.lib.texture.IWorldBlockTextureProvider;
import codechicken.lib.texture.TextureUtils;
import cofh.core.render.IModelRegister;
import cofh.core.util.helpers.BlockHelper;
import cofh.core.util.helpers.FluidHelper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumHand;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import cofh.core.util.helpers.ReconfigurableHelper;
import cofh.thermalexpansion.block.BlockTEBase;
import cofh.thermalexpansion.init.TEProps;
import cofh.thermalexpansion.init.TETextures;
import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.init.THTextures;
import dlx.thermalharvest.render.BakeryHarvester;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.block.statemap.StateMapperBase;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Блок Сборщика урожая. Наследует BlockTEBase из Thermal Expansion: разборка/поворот ключом, сохранение
 * NBT при разрушении, твёрдость по уровню, частицы — всё как у машин TE. Рендер — через CCL-«пекарню»
 * (как BlockMachine), поэтому видны цветные метки конфигурации сторон и рамка уровня.
 */
public class BlockHarvester extends BlockTEBase implements IBakeryProvider, IWorldBlockTextureProvider, IModelRegister {

    public static final UnlistedGenericTile<TileHarvester> TILE_HARVESTER = new UnlistedGenericTile<>("tile_harvester", TileHarvester.class);

    public static ItemBlockHarvester itemBlock;
    public static ItemStack harvester = ItemStack.EMPTY;

    public BlockHarvester() {
        super(Material.IRON, ThermalHarvest.MOD_ID);
        setTranslationKey("harvester");
        setHardness(15.0F);
        setResistance(25.0F);
        setRegistryName(ThermalHarvest.MOD_ID, "harvester");
    }

    @Override
    protected BlockStateContainer createBlockState() {
        BlockStateContainer.Builder builder = new BlockStateContainer.Builder(this);
        builder.add(ModelErrorStateProperty.ERROR_STATE);
        builder.add(TILE_HARVESTER);
        builder.add(TEProps.BAKERY_WORLD);
        return builder.build();
    }

    @Override
    public void getSubBlocks(CreativeTabs tab, NonNullList<ItemStack> items) {
        if (TEProps.creativeTabShowAllBlockLevels) {
            for (int level = 0; level <= 4; level++) {
                items.add(itemBlock.setDefaultTag(new ItemStack(this), level));
            }
        } else {
            items.add(itemBlock.setDefaultTag(new ItemStack(this), TEProps.creativeTabLevel));
        }
        if (TEProps.creativeTabShowCreative) {
            items.add(itemBlock.setCreativeTag(new ItemStack(this)));
        }
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return "tile.thermalharvest.harvester.name";
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Override
    public TileEntity createTileEntity(World world, IBlockState state) {
        return new TileHarvester();
    }

    /** Как BlockMachine: восстанавливаем уровень, расширения, энергию и конфигурацию сторон из предмета. */
    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase living, ItemStack stack) {
        TileEntity te = world.getTileEntity(pos);
        if (stack.getTagCompound() != null && te instanceof TileHarvester) {
            TileHarvester tile = (TileHarvester) te;
            tile.isCreative = stack.getTagCompound().getBoolean("Creative");
            tile.setLevel(stack.getTagCompound().getByte("Level"));
            tile.readAugmentsFromNBT(stack.getTagCompound());
            tile.updateAugmentStatus();
            tile.setEnergyStored(stack.getTagCompound().getInteger("Energy"));
            tile.readNutrientsFromStackTag(stack.getTagCompound());

            int facing = BlockHelper.determineXZPlaceFacing(living);
            int storedFacing = ReconfigurableHelper.getFacing(stack);
            byte[] sideCache = ReconfigurableHelper.getSideCache(stack, tile.getDefaultSides());
            int version = stack.getTagCompound().getInteger(TileHarvester.TAG_VERSION);
            if (version < TileHarvester.CONFIG_VERSION && stack.getTagCompound().hasKey("SideCache")) {
                sideCache = TileHarvester.migrateSides(sideCache, version); // предмет из прошлой версии
            }
            for (int i = 0; i < sideCache.length; i++) {
                if (sideCache[i] >= tile.getNumConfig(i)) {
                    sideCache[i] = 0;
                }
            }
            tile.sideCache[0] = sideCache[0];
            tile.sideCache[1] = sideCache[1];
            tile.sideCache[facing] = 0;
            tile.sideCache[BlockHelper.getLeftSide(facing)] = sideCache[BlockHelper.getLeftSide(storedFacing)];
            tile.sideCache[BlockHelper.getRightSide(facing)] = sideCache[BlockHelper.getRightSide(storedFacing)];
            tile.sideCache[BlockHelper.getOppositeSide(facing)] = sideCache[BlockHelper.getOppositeSide(storedFacing)];
        }
        super.onBlockPlacedBy(world, pos, state, living, stack);
    }

    /** Версия формата конфигурации сторон сохраняется в предмете при разборке. */
    @Override
    public NBTTagCompound getItemStackTag(IBlockAccess world, BlockPos pos) {
        NBTTagCompound tag = super.getItemStackTag(world, pos);
        if (tag != null) {
            tag.setInteger(TileHarvester.TAG_VERSION, TileHarvester.CONFIG_VERSION);
            TileEntity te = world.getTileEntity(pos);
            if (te instanceof TileHarvester) {
                ((TileHarvester) te).writeNutrientsToStackTag(tag);
            }
        }
        return tag;
    }

    /** Ведро/ячейка с водой по блоку — заливка бака модуля орошения (как у машин TE). */
    @Override
    public boolean onBlockActivatedDelegate(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                            EnumFacing side, float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(pos);
        if (!(te instanceof TileHarvester) || !((TileHarvester) te).canPlayerAccess(player)) {
            return false;
        }
        if (te.hasCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null)) {
            ItemStack held = player.getHeldItem(hand);
            IFluidHandler handler = te.getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, null);
            if (FluidHelper.isFluidHandler(held)) {
                FluidHelper.interactWithHandler(held, handler, player, hand);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isNormalCube(IBlockState state, IBlockAccess world, BlockPos pos) {
        return false;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return true;
    }

    @Override
    public boolean isSideSolid(IBlockState baseState, IBlockAccess world, BlockPos pos, EnumFacing side) {
        return true;
    }

    /* ================================================================== */
    /* Рендер                                                              */
    /* ================================================================== */

    @Override
    @SideOnly(Side.CLIENT)
    public boolean canRenderInLayer(IBlockState state, BlockRenderLayer layer) {
        return layer == BlockRenderLayer.SOLID || layer == BlockRenderLayer.CUTOUT;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IBlockState getExtendedState(IBlockState state, IBlockAccess world, BlockPos pos) {
        return ModelBakery.handleExtendedState((IExtendedBlockState) super.getExtendedState(state, world, pos), world, pos);
    }

    @Override
    public IBakery getBakery() {
        return BakeryHarvester.INSTANCE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getTexture(EnumFacing side, ItemStack stack) {
        if (side == EnumFacing.DOWN) {
            return TETextures.MACHINE_BOTTOM;
        }
        if (side == EnumFacing.UP) {
            return TETextures.MACHINE_TOP;
        }
        return side != EnumFacing.NORTH ? TETextures.MACHINE_SIDE : THTextures.HARVESTER_FACE;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getTexture(EnumFacing side, IBlockState state, BlockRenderLayer layer, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileHarvester) {
            return ((TileHarvester) tile).getTexture(side.ordinal(), layer == BlockRenderLayer.SOLID ? 0 : 1);
        }
        return TextureUtils.getMissingSprite();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerModels() {
        final ModelResourceLocation location = new ModelResourceLocation(getRegistryName(), "normal");
        ModelLoader.setCustomStateMapper(this, new StateMapperBase() {
            @Override
            protected ModelResourceLocation getModelResourceLocation(IBlockState state) {
                return location;
            }
        });
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(this), 0, location);
        ModelRegistryHelper.register(location, new CCBakeryModel());

        ModelBakery.registerBlockKeyGenerator(this, state -> {
            TileHarvester tile = state.getValue(TILE_HARVESTER);
            StringBuilder builder = new StringBuilder(String.valueOf(state.getBlock().getRegistryName()));
            builder.append(",creative=").append(tile.isCreative);
            builder.append(",level=").append(tile.getLevel());
            builder.append(",facing=").append(tile.getFacing());
            // состояние лицевой панели (выкл / сон / анализ / сбор) — часть ключа кэша модели, иначе CCL отдаёт старую текстуру
            builder.append(",state=").append(tile.workState);
            builder.append(",side_config={");
            for (byte b : tile.sideCache) {
                builder.append(",").append(b);
            }
            builder.append("}");
            return builder.toString();
        });
        ModelBakery.registerItemKeyGenerator(itemBlock, stack -> ModelBakery.defaultItemKeyGenerator.generateKey(stack)
                + ",creative=" + itemBlock.isCreative(stack) + ",level=" + itemBlock.getLevel(stack));
    }
}
