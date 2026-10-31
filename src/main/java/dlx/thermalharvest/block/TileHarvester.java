package dlx.thermalharvest.block;

import cofh.api.item.IAugmentItem;
import cofh.api.item.IUpgradeItem;
import cofh.core.block.TilePowered;
import cofh.core.fluid.FluidTankCore;
import cofh.core.network.PacketBase;
import cofh.core.util.CoreUtils;
import cofh.core.util.core.EnergyConfig;
import cofh.core.util.core.SideConfig;
import cofh.core.util.core.SlotConfig;
import cofh.core.util.helpers.AugmentHelper;
import cofh.core.util.helpers.EnergyHelper;
import cofh.core.util.helpers.FluidHelper;
import cofh.core.util.helpers.ServerHelper;
import cofh.redstoneflux.impl.EnergyStorage;
import cofh.thermalexpansion.block.machine.BlockMachine;
import cofh.thermalexpansion.block.machine.TileMachineBase;
import cofh.thermalexpansion.init.TEProps;
import cofh.thermalexpansion.init.TETextures;
import com.mojang.authlib.GameProfile;
import dlx.thermalharvest.ThermalHarvest;
import dlx.thermalharvest.compat.IC2CropCompat;
import dlx.thermalharvest.gui.ContainerHarvester;
import dlx.thermalharvest.gui.GuiHarvester;
import dlx.thermalharvest.init.THConfig;
import dlx.thermalharvest.init.THTextures;
import dlx.thermalharvest.item.ItemHarvesterAugment;
import dlx.thermalharvest.util.HarvestHelper;
import net.minecraft.block.BlockFarmland;
import net.minecraft.block.BlockStem;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.FarmlandWaterManager;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.ticket.AABBTicket;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.FluidTankProperties;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidTankProperties;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Сборщик урожая. Построен на TilePowered из CoFH Core — той же базе, что и машины Thermal Expansion,
 * поэтому получает стандартный функционал TE: RF, уровни через наборы улучшения, расширения, конфигурацию
 * сторон с авто-вводом/выводом, красный сигнал, защиту.
 * <p>
 * Работа: зона обходится по колонкам (каждая колонка — 3 слоя растений + пашня под ними), скорость обхода
 * зависит от уровня (по умолчанию 1/2/4/8/16 колонок в секунду). При проверке колонки машина собирает созревшие
 * культуры, выпалывает сорняки IC2, ускоряет рост за счёт запаса удобрения и, с модулем орошения, увлажняет пашню.
 * <p>
 * Орошение: пока в баке есть вода, сборщик держит «водную зону» Forge ({@link FarmlandWaterManager}) —
 * ванильная пашня считает её источником воды и не высыхает. Вода расходуется только на реальные орошаемые блоки.
 */
public class TileHarvester extends TilePowered implements ITickable {

    public static final int OUTPUT_SLOTS = 15;
    public static final int SLOT_CHARGE = 15;
    public static final int SLOT_FERTILIZER = 16;
    public static final int INVENTORY_SIZE = 17;

    /** Версия формата конфигурации сторон: 1 — 1.0/1.1, 2 — 1.2.0 (синий ввод), 3 — 1.2.1 (схема Пивоварни TE). */
    public static final int CONFIG_VERSION = 3;
    public static final String TAG_VERSION = "THVer";

    public static final String AUG_RANGE = ItemHarvesterAugment.ID_RANGE;
    public static final String AUG_IRRIGATION = ItemHarvesterAugment.ID_IRRIGATION;
    public static final String AUG_RECYCLER = ItemHarvesterAugment.ID_RECYCLER;
    public static final String AUG_CATALYST = ItemHarvesterAugment.ID_CATALYST;

    private static final GameProfile FAKE_PROFILE = new GameProfile(
            UUID.fromString("5d2b1c38-6a0e-4e2f-9b7a-3f1d2c4b8e71"), "[ThermalHarvester]");

    public static SideConfig SIDE_CONFIG;
    public static SlotConfig SLOT_CONFIG;
    public static EnergyConfig ENERGY_CONFIG;
    public static final HashSet<String> VALID_AUGMENTS = new HashSet<>();

    public static void initialize() {
        int[] outputs = new int[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputs[i] = i;
        }
        int[] all = new int[OUTPUT_SLOTS + 1];
        System.arraycopy(outputs, 0, all, 0, OUTPUT_SLOTS);
        all[OUTPUT_SLOTS] = SLOT_FERTILIZER;

        // Схема как у Пивоварни TE: синий — удобрение и вода, зелёный — только удобрение,
        // фиолетовый — только вода, оранжевый — урожай, «открыто» и «всё сразу».
        SIDE_CONFIG = new SideConfig();
        SIDE_CONFIG.numConfig = 7;
        SIDE_CONFIG.slotGroups = new int[][]{new int[0], {SLOT_FERTILIZER}, outputs, {SLOT_FERTILIZER}, new int[0], all, all};
        SIDE_CONFIG.sideTypes = new int[]{SideConfig.NONE, SideConfig.INPUT_ALL, SideConfig.OUTPUT_ALL,
                SideConfig.INPUT_PRIMARY, SideConfig.INPUT_SECONDARY, SideConfig.OPEN, SideConfig.OMNI};
        SIDE_CONFIG.defaultSides = new byte[]{1, 1, 2, 2, 2, 2};

        SLOT_CONFIG = new SlotConfig();
        SLOT_CONFIG.allowInsertionSlot = new boolean[INVENTORY_SIZE];
        SLOT_CONFIG.allowExtractionSlot = new boolean[INVENTORY_SIZE];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            SLOT_CONFIG.allowExtractionSlot[i] = true;
        }
        SLOT_CONFIG.allowInsertionSlot[SLOT_FERTILIZER] = true;

        ENERGY_CONFIG = new EnergyConfig();
        ENERGY_CONFIG.setDefaultParams(THConfig.basePower, TileMachineBase.smallStorage);

        VALID_AUGMENTS.add(AUG_RANGE);
        VALID_AUGMENTS.add(AUG_IRRIGATION);
        VALID_AUGMENTS.add(AUG_RECYCLER);
        VALID_AUGMENTS.add(AUG_CATALYST);

        GameRegistry.registerTileEntity(TileHarvester.class, new ResourceLocation(ThermalHarvest.MOD_ID, "harvester"));
    }

    /**
     * Переводит сохранённую конфигурацию сторон в текущую схему.
     * v1: {нет, вывод, открыто}; v2: {нет, ввод, вывод, открыто, всё}; v3: {нет, ввод, вывод, ввод осн., ввод доп., открыто, всё}.
     */
    public static byte[] migrateSides(byte[] sides, int fromVersion) {
        byte[] out = sides.clone();
        for (int i = 0; i < out.length; i++) {
            int v = out[i];
            if (fromVersion < 2) {
                out[i] = (byte) (v == 1 ? 2 : v == 2 ? 5 : 0);
            } else if (fromVersion == 2) {
                out[i] = (byte) (v == 3 ? 5 : v == 4 ? 6 : v);
            }
        }
        return out;
    }

    protected EnergyConfig energyConfig;
    protected int energyMod = 100;

    // --- расширения ---
    protected int rangeBonus;
    protected boolean augmentIrrigation;
    protected int recyclers;
    protected boolean augmentCatalyst;
    protected int radius = 1;

    // --- обход ---
    protected int cursor;
    /**
     * Ожидание: в зоне нет растений, жёрдочек и плодов, а пашню нечем поливать — машина не тратит энергию
     * и раз в 5 секунд бесплатно проверяет зону. Проверка также пересчитывает пашню для расчёта расхода воды.
     */
    protected boolean idle;
    /** Результат последней пробы: в зоне есть что растить удобрением (или убрать сорняк). */
    protected boolean probeGrow;
    protected boolean needProbe = true;
    protected int idleProbeTimer;
    protected static final int IDLE_PROBE_TICKS = 100;

    /**
     * Состояние лицевой панели, упаковано в байт: лампы (биты 0–1), окно (бит 2), прорезь (биты 3–4),
     * переполнение хранилища (бит 5).
     * Лампы: погашены — выключена, красные — обрабатывать нечего, зелёные — работа (обход или полив).
     * Окно — сбор урожая; оранжевая рамка окна — хранилище урожая переполнено.
     * Прорезь: тёмная — нет удобрения или энергии, ровный свет — запас есть, бегущие зубцы — удобрение расходуется.
     */
    public static final int LAMPS_OFF = 0;
    public static final int LAMPS_SLEEP = 1;
    public static final int LAMPS_SCAN = 2;
    public static final int SLOT_OFF = 0;
    public static final int SLOT_READY = 1;
    public static final int SLOT_RUN = 2;
    /** Инерция анимаций окна и прорези (тики): не гаснут сразу после последнего сбора или подкормки. */
    protected static final int ANIMATION_INERTIA = 50;
    public byte workState;
    protected int harvestTimer;
    protected int fertTimer;
    protected int poweredTimer;
    protected boolean wasEnabled;
    protected int scanAccumulator;
    protected byte[] wetColumns = new byte[0];
    protected int wetTotal;

    // --- энергия: учёт для отображения RF/t ---
    protected int energySpentWindow;
    protected int energySpentLastSecond;
    protected int activeTimer;

    // --- удобрение ---
    protected int nutrients;
    /** Сила запаса удобрения; дробная, если в запас подмешано удобрение другой силы. */
    protected double nutrientMultiplier = 1;
    /** Какое удобрение сейчас в запасе (для подсказки в интерфейсе). */
    protected ItemStack nutrientSource = ItemStack.EMPTY;

    // --- вода ---
    protected final FluidTankCore tank = new FluidTankCore(THConfig.tankCapacity);
    protected int waterAccumulator;
    protected boolean irrigating;
    protected AABBTicket waterTicket;
    protected int waterTicketRadius = -1;
    /** Счётчик пересоздания тикета орошения (секунды). */
    protected int waterTicketAge;
    protected static final int WATER_TICKET_REFRESH = 30;

    // --- прочее ---
    protected int inputTracker;
    protected int inputTrackerFluid;
    protected int outputTracker;
    protected final List<ItemStack> overflow = new ArrayList<>();

    public TileHarvester() {
        this.sideConfig = SIDE_CONFIG;
        this.slotConfig = SLOT_CONFIG;
        this.energyConfig = ENERGY_CONFIG.copy();
        this.energyStorage = new EnergyStorage(energyConfig.maxEnergy, energyConfig.maxPower * 4);

        this.inventory = new ItemStack[INVENTORY_SIZE];
        Arrays.fill(this.inventory, ItemStack.EMPTY);
        createAllSlots(this.inventory.length);
        Arrays.fill(this.augments, ItemStack.EMPTY);

        tank.setLock(FluidRegistry.WATER);
        setDefaultSides();
        this.enableAutoOutput = true;
        this.enableAutoInput = true;
        updateRadius();
    }

    /* ================================================================== */
    /* Идентификация                                                       */
    /* ================================================================== */

    @Override
    protected Object getMod() {
        return ThermalHarvest.instance;
    }

    @Override
    protected String getModVersion() {
        return ThermalHarvest.VERSION;
    }

    @Override
    protected String getTileName() {
        return "tile.thermalharvest.harvester.name";
    }

    /* ================================================================== */
    /* Уровни и улучшения                                                  */
    /* ================================================================== */

    @Override
    public boolean enableSecurity() {
        return true;
    }

    @Override
    public boolean smallStorage() {
        return TileMachineBase.smallStorage;
    }

    @Override
    protected int getLevelAutoInput() {
        return TEProps.levelAutoInput;
    }

    @Override
    protected int getLevelAutoOutput() {
        return TEProps.levelAutoOutput;
    }

    @Override
    protected int getLevelRSControl() {
        return TEProps.levelRedstoneControl;
    }

    @Override
    protected void setLevelFlags() {
        super.setLevelFlags();
        if (TileMachineBase.disableAutoInput) {
            this.hasAutoInput = false;
        }
        if (TileMachineBase.disableAutoOutput) {
            this.hasAutoOutput = false;
        }
    }

    @Override
    protected int getNumAugmentSlots(int level) {
        return TileMachineBase.NUM_AUGMENTS[clampLevel(level)];
    }

    @Override
    public boolean canUpgrade(ItemStack upgrade) {
        if (!AugmentHelper.isUpgradeItem(upgrade)) {
            return false;
        }
        IUpgradeItem item = (IUpgradeItem) upgrade.getItem();
        IUpgradeItem.UpgradeType type = item.getUpgradeType(upgrade);
        int uLevel = item.getUpgradeLevel(upgrade);
        switch (type) {
            case INCREMENTAL:
                return uLevel == this.level + 1 && !BlockMachine.enableClassicRecipes;
            case FULL:
                return uLevel > this.level && !BlockMachine.enableClassicRecipes;
            case CREATIVE:
                return !this.isCreative;
            default:
                return false;
        }
    }

    @Override
    protected boolean setLevel(int level) {
        if (super.setLevel(level)) {
            energyConfig.setDefaultParams(getBasePower(this.level), smallStorage());
            energyStorage.setCapacity(energyConfig.maxEnergy).setMaxTransfer(energyConfig.maxPower * 4);
            updateRadius();
            return true;
        }
        return false;
    }

    protected int getBasePower(int level) {
        return ENERGY_CONFIG.maxPower * TileMachineBase.POWER_SCALING[clampLevel(level)] / 100;
    }

    protected static int clampLevel(int level) {
        return Math.max(0, Math.min(level, 4));
    }

    /** Колонок в секунду на текущем уровне. */
    public int getScanRate() {
        return THConfig.columnsPerSecond(isCreative ? 4 : level);
    }

    /* ================================================================== */
    /* Расширения                                                          */
    /* ================================================================== */

    @Override
    protected void preAugmentInstall() {
        energyConfig.setDefaultParams(getBasePower(this.level), smallStorage());
        energyMod = 100;
        rangeBonus = 0;
        augmentIrrigation = false;
        recyclers = 0;
        augmentCatalyst = false;
    }

    @Override
    protected void postAugmentInstall() {
        energyStorage.setCapacity(energyConfig.maxEnergy).setMaxTransfer(energyConfig.maxPower * 4);
        energyMod = energyMod * TileMachineBase.ENERGY_SCALING[clampLevel(this.level)] / 100;
        if (!augmentIrrigation) {
            tank.setFluid(null);
            boolean changed = false;
            // без модуля орошения бака нет — стороны «только вода» (фиолетовые) сбрасываются
            for (int side = 0; side < sideCache.length; side++) {
                if (sideCache[side] == SIDE_WATER) {
                    sideCache[side] = 0;
                    changed = true;
                }
            }
            if (world != null && !world.isRemote) {
                setIrrigating(false);
                if (changed) {
                    markChunkDirty();
                    sendTilePacket(Side.CLIENT);
                }
            }
        }
        updateRadius();
    }

    /** Индекс типа «только вода» (фиолетовый) в sideTypes. */
    protected static final int SIDE_WATER = 4;

    /** Фиолетовая сторона доступна только при установленном модуле орошения (есть бак). */
    protected boolean isSideIndexAllowed(int index) {
        return index != SIDE_WATER || augmentIrrigation;
    }

    @Override
    public boolean incrSide(int side) {
        if (!super.incrSide(side)) {
            return false;
        }
        for (int guard = 0; guard < sideConfig.numConfig && !isSideIndexAllowed(sideCache[side]); guard++) {
            super.incrSide(side);
        }
        return true;
    }

    @Override
    public boolean decrSide(int side) {
        if (!super.decrSide(side)) {
            return false;
        }
        for (int guard = 0; guard < sideConfig.numConfig && !isSideIndexAllowed(sideCache[side]); guard++) {
            super.decrSide(side);
        }
        return true;
    }

    @Override
    protected boolean isValidAugment(IAugmentItem.AugmentType type, String id) {
        if (type == IAugmentItem.AugmentType.CREATIVE && !this.isCreative) {
            return false;
        }
        if (AUG_IRRIGATION.equals(id) && augmentIrrigation) {
            return false;
        }
        if (AUG_CATALYST.equals(id) && augmentCatalyst) {
            return false;
        }
        return VALID_AUGMENTS.contains(id) || super.isValidAugment(type, id);
    }

    @Override
    protected boolean installAugmentToSlot(int slot) {
        String id = AugmentHelper.getAugmentIdentifier(this.augments[slot]);
        if (AUG_RANGE.equals(id)) {
            rangeBonus++;
            energyMod += THConfig.rangeEnergyMod;
            return true;
        }
        if (!augmentIrrigation && AUG_IRRIGATION.equals(id)) {
            augmentIrrigation = true;
            return true;
        }
        if (AUG_RECYCLER.equals(id)) {
            recyclers++;
            return true;
        }
        if (!augmentCatalyst && AUG_CATALYST.equals(id)) {
            augmentCatalyst = true;
            return true;
        }
        return super.installAugmentToSlot(slot);
    }

    protected void updateRadius() {
        int r = THConfig.baseRadius + this.level + rangeBonus;
        int newRadius = Math.max(0, Math.min(r, THConfig.maxRadius));
        int columns = (newRadius * 2 + 1) * (newRadius * 2 + 1);
        if (newRadius != radius || wetColumns.length != columns) {
            radius = newRadius;
            wetColumns = new byte[columns];
            wetTotal = 0;
            cursor = 0;
        }
        wakeUp();
    }

    /** Смена зоны или расширений: состояние зоны проверяется заново на ближайшем тике. */
    protected void wakeUp() {
        idle = false;
        needProbe = true;
    }

    /**
     * Бесплатная проверка колонки: пересчитывает орошаемую пашню и сообщает, есть ли работа —
     * растение на пашне, стебель, плод, жёрдочка IC2 (или адский нарост на песке душ, если включено).
     */
    protected boolean probeColumn(int column) {
        int side = getAreaSide();
        BlockPos base = pos.add(column % side - radius, 0, column / side - radius);
        int wet = 0;
        boolean work = false;
        // пашня — работа только когда её есть чем поливать: стоит модуль орошения и в баке есть вода
        boolean canIrrigate = augmentIrrigation && tank.getFluidAmount() > 0;
        boolean canFertilize = nutrients > 0;
        for (int dy = 1; dy >= -2; dy--) {
            BlockPos p = base.up(dy);
            if (p.equals(pos) || !world.isBlockLoaded(p)) {
                continue;
            }
            IBlockState state = world.getBlockState(p);
            if (state.getBlock() instanceof BlockFarmland) {
                if (dy <= 0) {
                    wet++;
                    work |= canIrrigate;
                }
                continue;
            }
            if (dy < -1 || state.getBlock().isAir(state, world, p) || THConfig.isBlacklisted(state.getBlock())) {
                continue;
            }
            if (state.getBlock().hasTileEntity(state)) {
                if (ThermalHarvest.ic2Loaded) {
                    TileEntity tile = world.getTileEntity(p);
                    if (tile != null && IC2CropCompat.isCrop(tile)) {
                        // полив жёрдочек не зависит от сна: машина может спать из-за полного инвентаря
                        if (irrigating) {
                            IC2CropCompat.hydrate(tile);
                        }
                        work = true;
                        if (THConfig.removeIC2Weeds && IC2CropCompat.isWeed(tile)) {
                            probeGrow = true; // сорняк убирается без склада
                        } else if (canFertilize && !IC2CropCompat.canHarvest(tile)) {
                            probeGrow = true;
                        }
                    }
                }
            } else {
                if (!work) {
                    work = HarvestHelper.isProcessedBlock(world, p, state);
                }
                if (canFertilize && !probeGrow && isGrowable(p, state)) {
                    probeGrow = true;
                }
            }
        }
        setWet(column, wet);
        return work;
    }

    /** Незрелое растение на пашне или стебель без плода — то, что удобрение может ускорить. */
    protected boolean isGrowable(BlockPos p, IBlockState state) {
        if (HarvestHelper.isFarmPlant(world, p, state)) {
            return !HarvestHelper.isMature(state);
        }
        return HarvestHelper.isGrowableStem(world, p, state);
    }

    /**
     * Спать: работы нет вовсе, либо урожай некуда складывать и растить удобрением нечего.
     * Вызывать сразу после {@link #probeZone()}.
     */
    protected boolean shouldSleep(boolean work) {
        return !work || (isOutputBlocked() && !probeGrow);
    }

    /** Проверка всей зоны; true — есть что обрабатывать. */
    protected boolean probeZone() {
        probeGrow = false;
        boolean work = false;
        int columns = getColumnCount();
        for (int c = 0; c < columns; c++) {
            work |= probeColumn(c);
        }
        return work;
    }

    public int getRadius() {
        return radius;
    }

    public int getAreaSide() {
        return radius * 2 + 1;
    }

    public int getColumnCount() {
        int side = getAreaSide();
        return side * side;
    }

    public boolean hasIrrigation() {
        return augmentIrrigation;
    }

    public boolean isIrrigating() {
        return irrigating;
    }

    public int getRecyclerChance() {
        return Math.min(THConfig.recyclerChanceMax, recyclers * THConfig.recyclerChancePerAugment);
    }

    public boolean hasCatalyst() {
        return augmentCatalyst;
    }

    /** Текущий множитель скорости роста (1 — без ускорения). */
    public double getGrowthMultiplier() {
        if (nutrients <= 0) {
            return 1;
        }
        return nutrientMultiplier + (augmentCatalyst ? THConfig.catalystBonus : 0);
    }

    /** Множитель удобрения без учёта Катализатора. */
    public double getBaseMultiplier() {
        return nutrients > 0 ? nutrientMultiplier : 1;
    }

    /* ---- перенос запаса удобрения в предмет при разборе (как энергия у машин TE); вода не переносится ---- */
    public static final String TAG_NUTRIENTS = "THNutrients";
    public static final String TAG_NUTRIENT_MULT = "THNutrientMult";
    public static final String TAG_NUTRIENT_SOURCE = "THNutrientSource";

    public void writeNutrientsToStackTag(NBTTagCompound tag) {
        if (nutrients <= 0) {
            return;
        }
        tag.setInteger(TAG_NUTRIENTS, nutrients);
        tag.setFloat(TAG_NUTRIENT_MULT, (float) nutrientMultiplier);
        if (!nutrientSource.isEmpty()) {
            tag.setTag(TAG_NUTRIENT_SOURCE, nutrientSource.writeToNBT(new NBTTagCompound()));
        }
    }

    public void readNutrientsFromStackTag(NBTTagCompound tag) {
        int amount = Math.min(tag.getInteger(TAG_NUTRIENTS), THConfig.fertilizerCapacity);
        if (amount <= 0) {
            return;
        }
        nutrients = amount;
        nutrientMultiplier = Math.max(1, tag.getFloat(TAG_NUTRIENT_MULT)); // getFloat читает и старый int
        nutrientSource = tag.hasKey(TAG_NUTRIENT_SOURCE) ? new ItemStack(tag.getCompoundTag(TAG_NUTRIENT_SOURCE)) : ItemStack.EMPTY;
        markDirty();
    }

    public ItemStack getNutrientSource() {
        return nutrientSource;
    }

    public int getNutrients() {
        return nutrients;
    }

    public FluidTankCore getTank() {
        return tank;
    }

    public FluidStack getTankFluid() {
        return tank.getFluid();
    }

    protected int costCheck() {
        return THConfig.energyPerCheck * energyMod / 100;
    }

    protected int costHarvest() {
        return THConfig.energyPerHarvest * energyMod / 100;
    }

    /* ================================================================== */
    /* Работа                                                              */
    /* ================================================================== */

    @Override
    public void update() {
        if (ServerHelper.isClientWorld(world)) {
            return;
        }
        if (isCreative) {
            energyStorage.setEnergyStored(energyStorage.getMaxEnergyStored());
        }
        boolean enabled = redstoneControlOrDisable();

        if (timeCheck()) {
            transferOutput();
            transferInput();
            transferInputFluid();
            energySpentLastSecond = energySpentWindow;
            energySpentWindow = 0;
            if (augmentIrrigation) {
                payWater(enabled);
            }
        }
        if (irrigating && (!enabled || !hasWorkPower())) {
            setIrrigating(false); // орошение — работа машины: без сигнала или без энергии оно останавливается
        }
        loadFertilizer();

        if (enabled && !wasEnabled) {
            needProbe = true; // включили красным сигналом — сразу проверяем зону, не дожидаясь пробы раз в 5 секунд
        }
        wasEnabled = enabled;
        if (enabled && needProbe) {
            needProbe = false;
            idle = shouldSleep(probeZone());
            idleProbeTimer = IDLE_PROBE_TICKS;
            if (idle) {
                activeTimer = 0;
            }
        }
        if (enabled && idle) {
            // в зоне нечего обрабатывать: энергию не тратим, изредка бесплатно проверяем, не появилось ли что-то
            scanAccumulator = 0;
            if (--idleProbeTimer <= 0) {
                idleProbeTimer = IDLE_PROBE_TICKS;
                // проба всегда (она же пересчитывает полив), просыпаемся — только если есть куда складывать
                if (!shouldSleep(probeZone())) {
                    idle = false;
                }
            }
        } else if (enabled) {
            scanAccumulator += getScanRate();
            int guard = 0;
            while (scanAccumulator >= 20 && guard++ < 64) {
                if (!useEnergy(costCheck())) {
                    scanAccumulator = 0;
                    break;
                }
                scanAccumulator -= 20;
                scanColumn(cursor);
                cursor = (cursor + 1) % getColumnCount();
                activeTimer = 40;
                if (cursor == 0 && shouldSleep(probeZone())) {
                    // полный обход завершён: работы не осталось, либо урожай некуда складывать и растить
                    // нечего — засыпаем. Орошение при этом продолжается: оно оплачивается отдельно от обхода.
                    idle = true;
                    idleProbeTimer = IDLE_PROBE_TICKS;
                    activeTimer = 0;
                    break;
                }
            }
        } else {
            scanAccumulator = 0;
        }
        if (activeTimer > 0) {
            activeTimer--;
        }
        if (harvestTimer > 0) {
            harvestTimer--;
        }
        if (fertTimer > 0) {
            fertTimer--;
        }
        // «под питанием» с инерцией: при слабой подаче RF энергия колеблется у порога, а панель не должна мигать
        if (hasWorkPower()) {
            poweredTimer = 40;
        } else if (poweredTimer > 0) {
            poweredTimer--;
        }
        boolean powered = poweredTimer > 0;
        int lamps;
        if (harvestTimer > 0 || activeTimer > 0 || fertTimer > 0) {
            lamps = LAMPS_SCAN;
        } else if (enabled && idle && powered) {
            // спит, но поливает пашню — это тоже работа: зелёный; иначе — красный (обрабатывать нечего)
            lamps = irrigating && wetTotal > 0 ? LAMPS_SCAN : LAMPS_SLEEP;
        } else {
            lamps = LAMPS_OFF;
        }
        int slot;
        if (fertTimer > 0) {
            slot = SLOT_RUN;
        } else if (enabled && powered && nutrients > 0) {
            slot = SLOT_READY;
        } else {
            slot = SLOT_OFF;
        }
        // бит 5 — хранилище урожая переполнено (оранжевая рамка окна)
        byte state = (byte) (lamps | (harvestTimer > 0 ? 4 : 0) | (slot << 3) | (isOutputBlocked() ? 32 : 0));
        isActive = lamps == LAMPS_SCAN;
        if (state != workState) {
            // смена состояния редкая (у каждого есть инерция), поэтому пакет отправляется сразу
            workState = state;
            sendTilePacket(Side.CLIENT);
        }
        chargeEnergy();
    }

    /** Хватает ли энергии хотя бы на одну проверку колонки — признак того, что машина под питанием. */
    protected boolean hasWorkPower() {
        return isCreative || energyStorage.getEnergyStored() >= Math.max(1, costCheck());
    }

    protected boolean useEnergy(int amount) {
        if (amount <= 0 || isCreative) {
            return true;
        }
        if (energyStorage.getEnergyStored() < amount) {
            return false;
        }
        energyStorage.modifyEnergyStored(-amount);
        energySpentWindow += amount;
        return true;
    }

    protected FakePlayer getFakePlayer() {
        return FakePlayerFactory.get((WorldServer) world, FAKE_PROFILE);
    }

    /** Обработка одной колонки: растения на y-1..y+1, пашня на y-2..y. */
    protected void scanColumn(int column) {
        int side = getAreaSide();
        int dx = column % side - radius;
        int dz = column / side - radius;
        int wet = 0;
        boolean canStore = flushOverflow() && hasOutputSpace();

        for (int dy = 1; dy >= -2; dy--) {
            BlockPos p = pos.add(dx, dy, dz);
            if (p.equals(pos) || !world.isBlockLoaded(p)) {
                continue;
            }
            IBlockState state = world.getBlockState(p);

            if (state.getBlock() instanceof BlockFarmland) {
                if (dy <= 0) {
                    wet++;
                    if (irrigating && state.getValue(BlockFarmland.MOISTURE) < 7) {
                        world.setBlockState(p, state.withProperty(BlockFarmland.MOISTURE, 7), 2);
                    }
                }
                continue;
            }
            if (dy < -1 || state.getBlock().isAir(state, world, p) || THConfig.isBlacklisted(state.getBlock())) {
                continue;
            }

            // --- блоки с TileEntity: только жёрдочки IC2 (прочие «умные» растения не трогаем) ---
            // Вода за жёрдочку отдельно не списывается: она стоит на пашне, которая уже учтена.
            if (state.getBlock().hasTileEntity(state)) {
                if (ThermalHarvest.ic2Loaded) {
                    TileEntity tile = world.getTileEntity(p);
                    if (tile != null && IC2CropCompat.isCrop(tile)) {
                        processIC2(tile, canStore);
                        canStore = canStore && overflow.isEmpty();
                    }
                }
                continue;
            }

            // --- ванильные/модовые культуры на пашне ---
            if (HarvestHelper.isFarmPlant(world, p, state)) {
                if (HarvestHelper.isMature(state)) {
                    if (canStore && energyAvailable(costHarvest())) {
                        List<ItemStack> drops = HarvestHelper.harvestPlant(world, p, state, getFakePlayer());
                        if (drops != null) {
                            useEnergy(costHarvest());
                            storeDrops(drops);
                            canStore = overflow.isEmpty() && hasOutputSpace();
                        }
                    }
                } else {
                    boostVanilla(p, state, false);
                }
                continue;
            }

            // --- стебли тыквы/арбуза на пашне: ускоряем рост стебля и появление плода ---
            if (state.getBlock() instanceof BlockStem && world.getBlockState(p.down()).getBlock() instanceof BlockFarmland) {
                // плод уже вырос или ему негде появиться — удобрение не тратим
                if (HarvestHelper.isGrowableStem(world, p, state)) {
                    boostVanilla(p, state, true);
                }
                continue;
            }

            // --- тыквы и арбузы у стебля на пашне ---
            if (HarvestHelper.isFarmFruit(world, p, state) && canStore && energyAvailable(costHarvest())) {
                List<ItemStack> drops = HarvestHelper.harvestFruit(world, p, state, getFakePlayer());
                if (drops != null) {
                    useEnergy(costHarvest());
                    storeDrops(drops);
                    canStore = overflow.isEmpty() && hasOutputSpace();
                }
            }
        }
        setWet(column, wet);
    }

    protected boolean energyAvailable(int amount) {
        return isCreative || amount <= 0 || energyStorage.getEnergyStored() >= amount;
    }

    protected void processIC2(TileEntity tile, boolean canStore) {
        if (THConfig.removeIC2Weeds && IC2CropCompat.isWeed(tile)) {
            if (useEnergy(costHarvest())) {
                IC2CropCompat.removeWeed(tile);
            }
            return;
        }
        if (irrigating) {
            IC2CropCompat.hydrate(tile);
        }
        if (THConfig.ic2Nutrients && nutrients > 0) {
            int cost = IC2CropCompat.feedNutrients(tile, nutrients);
            if (cost > 0) {
                consumeNutrients(cost);
            }
        }
        if (IC2CropCompat.canHarvest(tile)) {
            if (canStore && energyAvailable(costHarvest())) {
                List<ItemStack> drops = IC2CropCompat.harvest(tile);
                if (drops != null) {
                    useEnergy(costHarvest());
                    storeDrops(drops);
                }
            }
            return;
        }
        int extra = rollExtraTicks(IC2CropCompat.ticksPerGrowthCycle());
        for (int i = 0; i < extra && nutrients > 0; i++) {
            if (!IC2CropCompat.safeGrowthTick(tile)) {
                break;
            }
            consumeNutrients(1);
        }
    }

    /**
     * @param fruitBearing стебель: и после созревания продолжает «работать» (выращивает плод), поэтому
     *                     проверка зрелости для него не применяется
     */
    protected void boostVanilla(BlockPos p, IBlockState state, boolean fruitBearing) {
        int randomTickSpeed = world.getGameRules().getInt("randomTickSpeed");
        if (randomTickSpeed <= 0) {
            return;
        }
        int extra = rollExtraTicks(4096.0D / randomTickSpeed);
        for (int i = 0; i < extra && nutrients > 0; i++) {
            IBlockState current = world.getBlockState(p);
            if (current.getBlock() != state.getBlock() || (!fruitBearing && HarvestHelper.isMature(current))) {
                break;
            }
            current.getBlock().updateTick(world, p, current, world.rand);
            consumeNutrients(1);
        }
    }

    /**
     * Сколько дополнительных циклов роста получает растение за один обход зоны.
     * Естественный темп — 1 цикл за ticksPerCycle тиков; удобрение с множителем M добавляет (M-1) таких же темпов.
     */
    protected int rollExtraTicks(double ticksPerCycle) {
        double multiplier = getGrowthMultiplier();
        if (multiplier <= 1 || ticksPerCycle <= 0) {
            return 0;
        }
        double sweepTicks = getColumnCount() * 20.0D / getScanRate();
        double expected = (multiplier - 1) * sweepTicks / ticksPerCycle;
        int n = (int) expected;
        if (world.rand.nextDouble() < expected - n) {
            n++;
        }
        return Math.min(n, 64);
    }

    protected void consumeNutrients(int amount) {
        if (amount > 0 && nutrients > 0) {
            fertTimer = ANIMATION_INERTIA; // удобрение пошло в дело (даже если Рециркулятор сберёг единицу)
        }
        int chance = getRecyclerChance();
        for (int i = 0; i < amount && nutrients > 0; i++) {
            if (chance <= 0 || world.rand.nextInt(100) >= chance) {
                nutrients--;
            }
        }
    }

    /** Запас — смесь удобрений разной силы (сила не совпадает с последним загруженным). */
    public boolean isNutrientMixed() {
        if (nutrients <= 0 || nutrientSource.isEmpty()) {
            return false;
        }
        THConfig.Fertilizer f = THConfig.getFertilizer(nutrientSource);
        return f == null || Math.abs(f.multiplier - nutrientMultiplier) > 0.005D;
    }

    /** Подгружает удобрение из слота во внутренний запас. */
    protected void loadFertilizer() {
        ItemStack stack = inventory[SLOT_FERTILIZER];
        if (stack.isEmpty()) {
            return;
        }
        THConfig.Fertilizer f = THConfig.getFertilizer(stack);
        if (f == null) {
            return;
        }
        if (nutrients + f.units > THConfig.fertilizerCapacity && nutrients > 0) {
            return; // не помещается — ждёт в ячейке
        }
        // Удобрения разной силы смешиваются: сила запаса — среднее по циклам роста
        // (100 циклов x2 + 100 циклов x4 = 200 циклов x3). Ничего не теряется и не «усиливается» даром.
        int added = Math.min(THConfig.fertilizerCapacity, nutrients + f.units) - nutrients;
        if (nutrients > 0) {
            nutrientMultiplier = (nutrients * nutrientMultiplier + added * (double) f.multiplier) / (nutrients + added);
            if (Math.abs(nutrientMultiplier - Math.round(nutrientMultiplier)) < 0.005D) {
                nutrientMultiplier = Math.round(nutrientMultiplier);
            }
        } else {
            nutrientMultiplier = f.multiplier;
        }
        nutrients += added;
        nutrientSource = ItemHandlerHelper.copyStackWithSize(stack, 1);
        stack.shrink(1);
        if (stack.isEmpty()) {
            inventory[SLOT_FERTILIZER] = ItemStack.EMPTY;
        }
        markDirty();
    }

    /* ------------------------------------------------------------------ */
    /* Вода                                                               */
    /* ------------------------------------------------------------------ */

    protected void setWet(int column, int count) {
        if (column < 0 || column >= wetColumns.length) {
            return;
        }
        int old = wetColumns[column];
        wetColumns[column] = (byte) count;
        wetTotal += count - old;
    }

    /** Раз в секунду: оплата воды за все орошаемые блоки зоны. Без воды орошение отключается. */
    protected void payWater(boolean enabled) {
        if (!enabled || !hasWorkPower()) {
            setIrrigating(false);
            return;
        }
        if (isCreative) {
            setIrrigating(true);
            return;
        }
        waterAccumulator += wetTotal * 20;
        int need = waterAccumulator / THConfig.waterTicksPerMb;
        if (need > 0) {
            if (tank.getFluidAmount() < need) {
                waterAccumulator = 0;
                setIrrigating(false);
                return;
            }
            tank.drain(need, true);
            waterAccumulator -= need * THConfig.waterTicksPerMb;
        }
        // орошаем, пока вода есть (или только что была оплачена последняя порция)
        setIrrigating(need > 0 || tank.getFluidAmount() > 0);
        refreshWaterTicket();
    }

    protected void setIrrigating(boolean value) {
        if (irrigating != value) {
            irrigating = value;
            markDirty();
        }
        updateWaterTicket();
    }

    protected void updateWaterTicket() {
        if (world == null || world.isRemote) {
            return;
        }
        if (!irrigating) {
            if (waterTicket != null && waterTicket.isValid()) {
                waterTicket.invalidate();
            }
            return;
        }
        if (waterTicket == null || waterTicketRadius != radius) {
            if (waterTicket != null) {
                waterTicket.invalidate();
            }
            AxisAlignedBB box = new AxisAlignedBB(
                    pos.getX() - radius, pos.getY() - 2, pos.getZ() - radius,
                    pos.getX() + radius + 1, pos.getY() + 1, pos.getZ() + radius + 1);
            waterTicket = FarmlandWaterManager.addAABBTicket(world, box);
            waterTicketRadius = radius;
        } else if (!waterTicket.isValid()) {
            waterTicket.validate();
        }
    }

    /**
     * Зона орошения может лежать в нескольких чанках. Если соседний чанк выгружался и загружался снова,
     * Forge не возвращает в него уже выданный тикет — его часть поля перестаёт считаться политой.
     * Поэтому раз в 30 секунд тикет выдаётся заново.
     */
    protected void refreshWaterTicket() {
        if (!irrigating || waterTicket == null) {
            waterTicketAge = 0;
            return;
        }
        if (++waterTicketAge >= WATER_TICKET_REFRESH) {
            waterTicketAge = 0;
            waterTicket.invalidate();
            waterTicket = null;
            updateWaterTicket();
        }
    }

    @Override
    public void onChunkUnload() {
        if (world != null && !world.isRemote) {
            releaseWaterTicket();
        }
        super.onChunkUnload();
    }

    protected void releaseWaterTicket() {
        if (waterTicket != null) {
            waterTicket.invalidate();
            waterTicket = null;
            waterTicketRadius = -1;
        }
        irrigating = false;
    }

    @Override
    public void invalidate() {
        if (world != null && !world.isRemote) {
            releaseWaterTicket();
        }
        super.invalidate();
    }

    /* ================================================================== */
    /* Инвентарь                                                           */
    /* ================================================================== */

    @Override
    public int getChargeSlot() {
        return SLOT_CHARGE;
    }

    protected void storeDrops(List<ItemStack> drops) {
        harvestTimer = ANIMATION_INERTIA;
        for (ItemStack stack : drops) {
            ItemStack rest = insertOutput(stack.copy());
            if (!rest.isEmpty()) {
                overflow.add(rest);
            }
        }
        markDirty();
    }

    protected ItemStack insertOutput(ItemStack stack) {
        for (int i = 0; i < OUTPUT_SLOTS && !stack.isEmpty(); i++) {
            ItemStack slot = inventory[i];
            if (!slot.isEmpty() && ItemHandlerHelper.canItemStacksStack(slot, stack)) {
                int move = Math.min(stack.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        for (int i = 0; i < OUTPUT_SLOTS && !stack.isEmpty(); i++) {
            if (inventory[i].isEmpty()) {
                int move = Math.min(stack.getCount(), stack.getMaxStackSize());
                inventory[i] = ItemHandlerHelper.copyStackWithSize(stack, move);
                stack.shrink(move);
            }
        }
        return stack;
    }

    protected boolean flushOverflow() {
        if (overflow.isEmpty()) {
            return true;
        }
        List<ItemStack> rest = new ArrayList<>();
        for (ItemStack stack : overflow) {
            ItemStack r = insertOutput(stack);
            if (!r.isEmpty()) {
                rest.add(r);
            }
        }
        overflow.clear();
        overflow.addAll(rest);
        return overflow.isEmpty();
    }

    /** Урожай некуда складывать: выходные слоты забиты или остался невыгруженный излишек. */
    protected boolean isOutputBlocked() {
        return !flushOverflow() || !hasOutputSpace();
    }

    protected boolean hasOutputSpace() {
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            if (inventory[i].isEmpty() || inventory[i].getCount() < inventory[i].getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    protected void transferInput() {
        if (!getTransferIn()) {
            return;
        }
        for (int i = inputTracker + 1; i <= inputTracker + 6; i++) {
            int side = i % 6;
            if (SideConfig.isPrimaryInput(sideConfig.sideTypes[sideCache[side]])
                    && extractItem(SLOT_FERTILIZER, ITEM_TRANSFER[clampLevel(level)], EnumFacing.VALUES[side])) {
                inputTracker = side;
                break;
            }
        }
    }

    /** Вода принимается на синих, фиолетовых, «открытых» и «всё сразу» сторонах (как в Пивоварне TE). */
    protected boolean acceptsFluidFrom(int side) {
        int type = sideConfig.sideTypes[sideCache[side]];
        return SideConfig.isSecondaryInput(type) || type == SideConfig.OPEN;
    }

    /** Авто-ввод воды из соседних баков/накопителей через синие и фиолетовые стороны. */
    protected void transferInputFluid() {
        if (!augmentIrrigation || !getTransferIn() || tank.getSpace() <= 0) {
            return;
        }
        for (int i = inputTrackerFluid + 1; i <= inputTrackerFluid + 6; i++) {
            int side = i % 6;
            if (!SideConfig.isSecondaryInput(sideConfig.sideTypes[sideCache[side]])) {
                continue;
            }
            EnumFacing face = EnumFacing.VALUES[side];
            FluidStack input = FluidHelper.extractFluidFromAdjacentFluidHandler(this, face,
                    Math.min(tank.getSpace(), FLUID_TRANSFER[clampLevel(level)]), false);
            if (input == null || input.getFluid() != FluidRegistry.WATER) {
                continue;
            }
            int filled = tank.fill(input, true);
            if (filled > 0) {
                FluidHelper.extractFluidFromAdjacentFluidHandler(this, face, filled, true);
                inputTrackerFluid = side;
                break;
            }
        }
    }

    protected void transferOutput() {
        if (!getTransferOut()) {
            return;
        }
        int budget = ITEM_TRANSFER[clampLevel(level)];
        for (int i = outputTracker + 1; i <= outputTracker + 6 && budget > 0; i++) {
            int side = i % 6;
            if (!SideConfig.isPrimaryOutput(sideConfig.sideTypes[sideCache[side]])) {
                continue;
            }
            for (int slot = 0; slot < OUTPUT_SLOTS && budget > 0; slot++) {
                if (inventory[slot].isEmpty()) {
                    continue;
                }
                int before = inventory[slot].getCount();
                if (transferItem(slot, budget, EnumFacing.VALUES[side])) {
                    budget -= before - inventory[slot].getCount();
                    outputTracker = side;
                }
            }
        }
    }

    public static boolean isFertilizer(ItemStack stack) {
        return THConfig.getFertilizer(stack) != null;
    }

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        if (slot == SLOT_CHARGE) {
            return EnergyHelper.isEnergyContainerItem(stack) || EnergyHelper.isEnergyHandler(stack);
        }
        if (slot == SLOT_FERTILIZER) {
            return isFertilizer(stack);
        }
        return false;
    }

    @Override
    public void blockBroken() {
        if (world != null && !world.isRemote) {
            for (ItemStack stack : overflow) {
                CoreUtils.dropItemStackIntoWorldWithVelocity(stack, world, pos);
            }
            overflow.clear();
        }
        super.blockBroken();
    }

    /* ================================================================== */
    /* Жидкость                                                            */
    /* ================================================================== */

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing from) {
        return (augmentIrrigation && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) || super.hasCapability(capability, from);
    }

    @Override
    public <T> T getCapability(Capability<T> capability, final EnumFacing from) {
        if (augmentIrrigation && capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new IFluidHandler() {
                @Override
                public IFluidTankProperties[] getTankProperties() {
                    FluidTankInfo info = tank.getInfo();
                    return new IFluidTankProperties[]{new FluidTankProperties(info.fluid, info.capacity, true, false)};
                }

                @Override
                public int fill(FluidStack resource, boolean doFill) {
                    if (resource == null || resource.getFluid() != FluidRegistry.WATER) {
                        return 0;
                    }
                    if (from != null && !acceptsFluidFrom(from.ordinal())) {
                        return 0;
                    }
                    return tank.fill(resource, doFill);
                }

                @Nullable
                @Override
                public FluidStack drain(FluidStack resource, boolean doDrain) {
                    return null;
                }

                @Nullable
                @Override
                public FluidStack drain(int maxDrain, boolean doDrain) {
                    return null;
                }
            });
        }
        return super.getCapability(capability, from);
    }

    /* ================================================================== */
    /* GUI                                                                 */
    /* ================================================================== */

    @Override
    public Object getGuiClient(InventoryPlayer inventory) {
        return new GuiHarvester(inventory, this);
    }

    @Override
    public Object getGuiServer(InventoryPlayer inventory) {
        return new ContainerHarvester(inventory, this);
    }

    @Override
    public int getScaledProgress(int scale) {
        int columns = getColumnCount();
        return columns <= 0 ? 0 : scale * cursor / columns;
    }

    @Override
    public int getScaledSpeed(int scale) {
        return isActive ? scale : 0;
    }

    public int getScaledNutrients(int scale) {
        return THConfig.fertilizerCapacity <= 0 ? 0 : Math.min(scale, scale * nutrients / THConfig.fertilizerCapacity);
    }

    @Override
    public int getInfoEnergyPerTick() {
        return (energySpentLastSecond + 19) / 20;
    }

    @Override
    public int getInfoMaxEnergyPerTick() {
        int perSecond = getScanRate() * costCheck();
        return Math.max(1, (perSecond + 19) / 20);
    }

    /* ================================================================== */
    /* NBT и сеть                                                          */
    /* ================================================================== */

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        if (nbt.getInteger(TAG_VERSION) < CONFIG_VERSION) {
            sideCache = migrateSides(sideCache, nbt.getInteger(TAG_VERSION));
            for (int i = 0; i < 6; i++) {
                if (sideCache[i] >= getNumConfig(i)) {
                    sideCache[i] = 0;
                }
            }
        }
        cursor = nbt.getInteger("Cursor");
        inputTracker = nbt.getInteger("TrackIn");
        outputTracker = nbt.getInteger("TrackOut");
        nutrients = nbt.getInteger("Nutrients");
        nutrientMultiplier = Math.max(1, nbt.getFloat("NutrientMult"));
        nutrientSource = nbt.hasKey("NutrientSource") ? new ItemStack(nbt.getCompoundTag("NutrientSource")) : ItemStack.EMPTY;
        irrigating = false; // восстановится при первой оплате воды
        tank.readFromNBT(nbt);
        overflow.clear();
        NBTTagList list = nbt.getTagList("Overflow", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ItemStack stack = new ItemStack(list.getCompoundTagAt(i));
            if (!stack.isEmpty()) {
                overflow.add(stack);
            }
        }
        updateRadius();
        if (cursor >= getColumnCount()) {
            cursor = 0;
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger(TAG_VERSION, CONFIG_VERSION);
        nbt.setInteger("Cursor", cursor);
        nbt.setInteger("TrackIn", inputTracker);
        nbt.setInteger("TrackOut", outputTracker);
        nbt.setInteger("Nutrients", nutrients);
        nbt.setFloat("NutrientMult", (float) nutrientMultiplier);
        if (!nutrientSource.isEmpty()) {
            nbt.setTag("NutrientSource", nutrientSource.writeToNBT(new NBTTagCompound()));
        }
        tank.writeToNBT(nbt);
        if (!overflow.isEmpty()) {
            NBTTagList list = new NBTTagList();
            for (ItemStack stack : overflow) {
                list.appendTag(stack.writeToNBT(new NBTTagCompound()));
            }
            nbt.setTag("Overflow", list);
        }
        return nbt;
    }

    @Override
    public PacketBase getGuiPacket() {
        PacketBase payload = super.getGuiPacket();
        payload.addInt(cursor);
        payload.addInt(rangeBonus);
        payload.addBool(augmentIrrigation);
        payload.addBool(irrigating);
        payload.addInt(recyclers);
        payload.addBool(augmentCatalyst);
        payload.addInt(nutrients);
        payload.addFloat((float) nutrientMultiplier);
        payload.addItemStack(nutrientSource);
        payload.addInt(energySpentLastSecond);
        payload.addFluidStack(tank.getFluid());
        return payload;
    }

    @Override
    protected void handleGuiPacket(PacketBase payload) {
        super.handleGuiPacket(payload);
        cursor = payload.getInt();
        rangeBonus = payload.getInt();
        augmentIrrigation = payload.getBool();
        irrigating = payload.getBool();
        recyclers = payload.getInt();
        augmentCatalyst = payload.getBool();
        nutrients = payload.getInt();
        nutrientMultiplier = payload.getFloat();
        nutrientSource = payload.getItemStack();
        energySpentLastSecond = payload.getInt();
        tank.setFluid(payload.getFluidStack());
        updateRadius();
    }

    /** Радиус нужен клиенту и вне GUI — для подсветки зоны. */
    @Override
    public PacketBase getTilePacket() {
        PacketBase payload = super.getTilePacket();
        payload.addInt(rangeBonus);
        payload.addByte(workState);
        return payload;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void handleTilePacket(PacketBase payload) {
        super.handleTilePacket(payload);
        rangeBonus = payload.getInt();
        workState = payload.getByte();
        updateRadius();
    }

    /* ================================================================== */
    /* Ориентация и текстуры                                               */
    /* ================================================================== */

    @Override
    public boolean setFacing(int side, boolean alternate) {
        if (side < 2 || side > 5) {
            return false;
        }
        sideCache[side] = 0;
        facing = (byte) side;
        markChunkDirty();
        sendTilePacket(Side.CLIENT);
        return true;
    }

    /** Накладки лицевой панели для текущего состояния (клиент). */
    @SideOnly(Side.CLIENT)
    public List<TextureAtlasSprite> getFaceOverlays() {
        List<TextureAtlasSprite> list = new ArrayList<>(4);
        int lamps = workState & 3;
        if (lamps == LAMPS_SLEEP) {
            list.add(THTextures.OV_LAMPS_SLEEP);
        } else if (lamps == LAMPS_SCAN) {
            list.add(THTextures.OV_LAMPS_SCAN);
        }
        if ((workState & 4) != 0) {
            list.add(THTextures.OV_WINDOW);
        }
        if ((workState & 32) != 0) {
            list.add(THTextures.OV_WINDOW_FULL);
        }
        int slot = (workState >> 3) & 3;
        if (slot == SLOT_READY) {
            list.add(THTextures.OV_SLOT_READY);
        } else if (slot == SLOT_RUN) {
            list.add(THTextures.OV_SLOT_RUN);
        }
        return list;
    }

    @Override
    public int getNumPasses() {
        return 2;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public TextureAtlasSprite getTexture(int side, int pass) {
        if (pass == 0) {
            if (side == 0) {
                return TETextures.MACHINE_BOTTOM;
            }
            if (side == 1) {
                return TETextures.MACHINE_TOP;
            }
            if (side != facing) {
                return TETextures.MACHINE_SIDE;
            }
            return THTextures.HARVESTER_FACE; // состояния рисуются накладками (BakeryHarvester)
        }
        if (side < 6) {
            return TETextures.CONFIG[sideConfig.sideTypes[sideCache[side]]];
        }
        return TETextures.MACHINE_SIDE;
    }
}
