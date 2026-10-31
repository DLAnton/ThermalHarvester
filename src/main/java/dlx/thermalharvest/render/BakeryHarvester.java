package dlx.thermalharvest.render;

import codechicken.lib.model.bakery.ModelErrorStateProperty;
import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.buffer.BakingVertexBuffer;
import codechicken.lib.texture.IWorldBlockTextureProvider;
import cofh.thermalexpansion.init.TEProps;
import cofh.thermalexpansion.init.TETextures;
import cofh.thermalexpansion.render.CubeBakeryBase;
import dlx.thermalharvest.block.BlockHarvester;
import dlx.thermalharvest.block.TileHarvester;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.property.IExtendedBlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * Модель блока — копия поведения BakeryMachine из Thermal Expansion: куб с текстурами машины,
 * оверлей конфигурации сторон (слой CUTOUT) и рамка уровня (Усиленный/Укреплённый/…).
 */
public class BakeryHarvester extends CubeBakeryBase {

    public static final BakeryHarvester INSTANCE = new BakeryHarvester();

    private static TextureAtlasSprite levelOverlay(boolean creative, int level) {
        if (creative) {
            return TETextures.MACHINE_OVERLAY_C;
        }
        return level <= 0 ? null : TETextures.MACHINE_OVERLAY[Math.min(level, 4)];
    }

    @Override
    public IExtendedBlockState handleState(IExtendedBlockState state, IBlockAccess world, BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof TileHarvester)) {
            return state.withProperty(ModelErrorStateProperty.ERROR_STATE,
                    ModelErrorStateProperty.ErrorState.of("Tile is not a TileHarvester at %s", pos));
        }
        state = state.withProperty(ModelErrorStateProperty.ERROR_STATE, ModelErrorStateProperty.ErrorState.OK);
        state = state.withProperty(BlockHarvester.TILE_HARVESTER, (TileHarvester) tile);
        state = state.withProperty(TEProps.BAKERY_WORLD, world);
        return state;
    }

    @Override
    public List<BakedQuad> bakeItemQuads(EnumFacing face, ItemStack stack) {
        List<BakedQuad> quads = new ArrayList<>();
        if (face != null && !stack.isEmpty()) {
            BakingVertexBuffer buffer = BakingVertexBuffer.create();
            buffer.begin(7, DefaultVertexFormats.ITEM);
            CCRenderState ccrs = CCRenderState.instance();
            ccrs.reset();
            ccrs.bind(buffer);

            boolean creative = BlockHarvester.itemBlock.isCreative(stack);
            int level = BlockHarvester.itemBlock.getLevel(stack);
            IWorldBlockTextureProvider provider = (IWorldBlockTextureProvider) Block.getBlockFromItem(stack.getItem());
            renderFace(ccrs, face, provider.getTexture(face, stack), -1);
            if (TEProps.renderMachineOverlay) {
                renderFaceOverlay(ccrs, face, levelOverlay(creative, level), -1);
            }
            buffer.finishDrawing();
            quads.addAll(buffer.bake());
        }
        return quads;
    }

    @Override
    public List<BakedQuad> bakeLayerFace(EnumFacing face, BlockRenderLayer layer, IExtendedBlockState state) {
        List<BakedQuad> quads = new ArrayList<>();
        if (face != null && state != null) {
            TileHarvester tile = state.getValue(BlockHarvester.TILE_HARVESTER);
            IBlockAccess world = state.getValue(TEProps.BAKERY_WORLD);
            if (tile == null || world == null) {
                return quads;
            }
            IWorldBlockTextureProvider provider = (IWorldBlockTextureProvider) state.getBlock();

            BakingVertexBuffer buffer = BakingVertexBuffer.create();
            buffer.begin(7, DefaultVertexFormats.ITEM);
            CCRenderState ccrs = CCRenderState.instance();
            ccrs.reset();
            ccrs.bind(buffer);

            renderFace(ccrs, face, provider.getTexture(face, state, layer, world, tile.getPos()), -1);
            if (layer == BlockRenderLayer.CUTOUT) {
                if (face.ordinal() == tile.getFacing()) {
                    // лицевая панель: лампы, окно и прорезь — независимые накладки поверх корпуса
                    for (TextureAtlasSprite sprite : tile.getFaceOverlays()) {
                        renderFace(ccrs, face, sprite, -1);
                    }
                }
                if (TEProps.renderMachineOverlay) {
                    renderFace(ccrs, face, levelOverlay(tile.isCreative, tile.getLevel()), -1);
                }
            }
            buffer.finishDrawing();
            quads.addAll(buffer.bake());
        }
        return quads;
    }
}
