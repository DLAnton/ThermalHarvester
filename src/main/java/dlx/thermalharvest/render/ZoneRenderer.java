package dlx.thermalharvest.render;

import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.block.TileHarvester;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.util.HarvestHelper;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Подсветка зоны сборщика: неброская чёрно-жёлтая разметка на поверхности обрабатываемой пашни.
 * <ul>
 *     <li>одна горизонтальная плитка на клетку (по верху пашни или плода) — без боковых граней, поэтому соседние
 *     плитки стыкуются ровно, без наложений и швов;</li>
 *     <li>растения и жёрдочки стоят поверх разметки — ничего не пересекается, мерцанию неоткуда взяться;</li>
 *     <li>яркость берётся из освещения мира: ночью разметка темнеет вместе с землёй и не светится;</li>
 *     <li>проверка глубины включена — сквозь блоки не видна.</li>
 * </ul>
 * Включается кнопкой в интерфейсе, видна только этому игроку.
 */
@SideOnly(Side.CLIENT)
public final class ZoneRenderer {

    public static final ZoneRenderer INSTANCE = new ZoneRenderer();

    private static final ResourceLocation MASK = new ResourceLocation(ThermalHarvest.MOD_ID, "textures/misc/zone_mask.png");
    /** Подъём плитки над поверхностью (защита от z-fighting). */
    private static final double LIFT = 0.004D;
    private static final double MAX_DISTANCE_SQ = 160 * 160;
    /** Тиков на полный сдвиг полосы (медленно). */
    private static final double ANIMATION_PERIOD = 200.0D;

    /** Режимы подсветки. */
    public static final int MODE_OFF = 0;
    public static final int MODE_FARMLAND = 1;
    public static final int MODE_FULL = 2;

    /** Включённые сборщики -> режим и плитки {x, y, z, яркость}. Плитки обновляются раз в 10 тиков. */
    private static final Map<Key, Entry> SHOWN = new HashMap<>();
    private static int refreshTimer;

    private ZoneRenderer() {}

    public static int getMode(TileHarvester tile) {
        Entry e = SHOWN.get(Key.of(tile));
        return e == null ? MODE_OFF : e.mode;
    }

    /** Переключение по кругу: выкл -> обрабатываемая пашня -> вся рабочая зона -> выкл. */
    public static void cycle(TileHarvester tile) {
        Key key = Key.of(tile);
        int next = (getMode(tile) + 1) % 3;
        if (next == MODE_OFF) {
            SHOWN.remove(key);
        } else {
            Entry e = new Entry(next);
            e.tiles = collect(tile, next);
            SHOWN.put(key, e);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || SHOWN.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null) {
            SHOWN.clear();
            return;
        }
        if (++refreshTimer < 10) {
            return;
        }
        refreshTimer = 0;
        int dim = mc.world.provider.getDimension();
        Iterator<Map.Entry<Key, Entry>> it = SHOWN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Entry> e = it.next();
            if (e.getKey().dim != dim) {
                continue;
            }
            TileEntity te = mc.world.getTileEntity(e.getKey().pos);
            if (!(te instanceof TileHarvester)) {
                it.remove();
                continue;
            }
            e.getValue().tiles = collect((TileHarvester) te, e.getValue().mode);
        }
    }

    private static List<double[]> collect(TileHarvester tile, int mode) {
        return mode == MODE_FULL ? collectFull(tile) : collectFarmland(tile);
    }

    /**
     * Вся рабочая зона: по одной плитке на каждую клетку — на поверхности первого твёрдого блока сверху вниз
     * в рабочих слоях (растения пропускаются). Клетки без опоры в рабочих слоях (вода, обрыв) не размечаются.
     */
    private static List<double[]> collectFull(TileHarvester tile) {
        List<double[]> tiles = new ArrayList<>();
        World world = tile.getWorld();
        BlockPos center = tile.getPos();
        int r = tile.getRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx == 0 && dz == 0) {
                    continue; // под самим сборщиком
                }
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (!world.isBlockLoaded(p)) {
                        break;
                    }
                    IBlockState state = world.getBlockState(p);
                    if (state.getMaterial().isLiquid()) {
                        break; // вода/лава — клетку не размечаем
                    }
                    if (state.getBlock() instanceof BlockFarmland || state.getMaterial().blocksMovement()) {
                        // верх блока; если над ним сплошной блок — поверхность закрыта, клетку не размечаем
                        if (dy < 1 && world.getBlockState(p.up()).isOpaqueCube()) {
                            break;
                        }
                        double top = state.getBoundingBox(world, p).maxY;
                        float light = Math.max(0.08F, world.getLightBrightness(p.up()));
                        tiles.add(new double[]{p.getX(), p.getY() + top + LIFT, p.getZ(), light});
                        break;
                    }
                }
            }
        }
        return tiles;
    }

    /** Плитки: верх каждой пашни в зоне (под культурами и жёрдочками) и верх плодов тыквы/арбуза. */
    private static List<double[]> collectFarmland(TileHarvester tile) {
        List<double[]> tiles = new ArrayList<>();
        World world = tile.getWorld();
        BlockPos center = tile.getPos();
        int r = tile.getRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 1; dy >= -2; dy--) {
                    BlockPos p = center.add(dx, dy, dz);
                    if (p.equals(center) || !world.isBlockLoaded(p)) {
                        continue;
                    }
                    IBlockState state = world.getBlockState(p);
                    double top;
                    if (state.getBlock() instanceof BlockFarmland) {
                        if (dy > 0) {
                            continue; // пашня над слоями растений не обрабатывается
                        }
                        top = state.getBoundingBox(world, p).maxY;
                    } else if (!state.getBlock().hasTileEntity(state) && HarvestHelper.isFarmFruit(world, p, state)) {
                        top = 1.0D;
                    } else {
                        continue;
                    }
                    BlockPos above = p.up();
                    float light = Math.max(0.08F, world.getLightBrightness(above));
                    tiles.add(new double[]{p.getX(), p.getY() + top + LIFT, p.getZ(), light});
                }
            }
        }
        return tiles;
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent event) {
        if (SHOWN.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        Entity view = mc.getRenderViewEntity();
        if (view == null || mc.world == null) {
            return;
        }
        float pt = event.getPartialTicks();
        double vx = view.lastTickPosX + (view.posX - view.lastTickPosX) * pt;
        double vy = view.lastTickPosY + (view.posY - view.lastTickPosY) * pt;
        double vz = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * pt;
        int dim = mc.world.provider.getDimension();
        double shift = THConfig.zoneAnimated ? (mc.world.getTotalWorldTime() % (long) ANIMATION_PERIOD + pt) / ANIMATION_PERIOD : 0.0D;
        float alpha = THConfig.zoneAlpha;

        GlStateManager.pushMatrix();
        GlStateManager.translate(-vx, -vy, -vz);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        GlStateManager.disableLighting();
        GlStateManager.disableCull();
        GlStateManager.enableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.enablePolygonOffset();
        GlStateManager.doPolygonOffset(-1.0F, -3.0F);
        GlStateManager.enableTexture2D();
        mc.getTextureManager().bindTexture(MASK);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(7 /* GL_QUADS */, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (Map.Entry<Key, Entry> e : SHOWN.entrySet()) {
            if (e.getKey().dim != dim || e.getKey().pos.distanceSqToCenter(vx, vy, vz) > MAX_DISTANCE_SQ) {
                continue;
            }
            for (double[] t : e.getValue().tiles) {
                double x = t[0];
                double y = t[1];
                double z = t[2];
                float l = (float) t[3];
                // UV по мировым координатам: полосы непрерывны через соседние плитки
                vertex(buf, x, y, z, shift, l, alpha);
                vertex(buf, x, y, z + 1, shift, l, alpha);
                vertex(buf, x + 1, y, z + 1, shift, l, alpha);
                vertex(buf, x + 1, y, z, shift, l, alpha);
            }
        }
        tess.draw();

        GlStateManager.doPolygonOffset(0.0F, 0.0F);
        GlStateManager.disablePolygonOffset();
        GlStateManager.depthMask(true);
        GlStateManager.enableCull();
        GlStateManager.disableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.popMatrix();
    }

    private static void vertex(BufferBuilder buf, double x, double y, double z, double shift, float light, float alpha) {
        buf.pos(x, y, z).tex((x + z) * 0.5D + shift, (z - x) * 0.5D).color(light, light, light, alpha).endVertex();
    }

    private static final class Entry {
        final int mode;
        List<double[]> tiles = new ArrayList<>();

        Entry(int mode) {
            this.mode = mode;
        }
    }

    private static final class Key {
        final int dim;
        final BlockPos pos;

        Key(int dim, BlockPos pos) {
            this.dim = dim;
            this.pos = pos;
        }

        static Key of(TileEntity tile) {
            return new Key(tile.getWorld().provider.getDimension(), tile.getPos().toImmutable());
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key && ((Key) o).dim == dim && ((Key) o).pos.equals(pos);
        }

        @Override
        public int hashCode() {
            return pos.hashCode() * 31 + dim;
        }
    }
}
