package dlx.thermalharvest.init;

import dlx.thermalharvest.ThermalHarvest;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Настройки мода (config/cofh/thermalharvest.cfg).
 * Ёмкость RF по уровням и число ячеек расширений берутся из конфигов Thermal Expansion,
 * чтобы сборщик вёл себя как остальные машины TE.
 */
public final class THConfig {

    private THConfig() {}

    // --- Обход зоны ---
    /** Сколько колонок (3 блока по высоте) проверяется в секунду на уровнях 0..4. */
    public static int[] columnsPerSecond = {1, 2, 4, 8, 16};

    // --- Энергия ---
    /** Базовая мощность (уровень 0), задаёт ёмкость и приём RF как у машин TE (масштаб по уровням из конфига TE). */
    public static int basePower = 20;
    /** RF за проверку одной колонки. */
    public static int energyPerCheck = 5;
    /** RF за сбор одной культуры (и за прополку одного сорняка). */
    public static int energyPerHarvest = 200;
    /** Доп. затраты энергии (% к проверке и сбору) за каждый расширитель зоны. */
    public static int rangeEnergyMod = 25;

    // --- Зона ---
    /** Радиус по горизонтали на уровне 0 (1 = 3x3). Каждый уровень и каждый расширитель добавляют +1. */
    public static int baseRadius = 1;
    public static int maxRadius = 16;

    // --- Сбор ---
    public static boolean breakParticles = true;
    /** Собирать адский нарост, посаженный на песок душ (вне жёрдочек). */
    public static boolean harvestNetherWartOnSoulSand = false;
    /** Выпалывать сорняки IC2 (на любой стадии). */
    public static boolean removeIC2Weeds = true;
    public static final Set<String> BLACKLIST = new HashSet<>();

    // --- Удобрения ---
    /** Максимальный внутренний запас питательности. */
    public static int fertilizerCapacity = 1000;
    /** Шанс (%) не потратить запас за каждый Рециркулятор удобрений и общий предел. */
    public static int recyclerChancePerAugment = 20;
    public static int recyclerChanceMax = 80;
    /** Насколько Катализатор роста повышает множитель скорости роста. */
    public static int catalystBonus = 1;
    /** Пополнять питательные вещества жёрдочек IC2 из запаса удобрения (как Crop-Matron). */
    public static boolean ic2Nutrients = true;
    /** Сколько единиц запаса стоит 10 единиц питательных веществ IC2. */
    public static int ic2NutrientCostPer10 = 1;
    /** Описание удобрений: предмет -> (множитель скорости, единиц запаса). */
    public static final Map<String, Fertilizer> FERTILIZERS = new HashMap<>();

    // --- Орошение ---
    public static int tankCapacity = 10000;
    /** Интервал (тики), за который каждый орошаемый блок (пашня, жёрдочка IC2) расходует 1 mB. */
    public static int waterTicksPerMb = 400;
    /** До какого значения поддерживать запас воды жёрдочек IC2 (максимум 200). */
    public static int ic2HydrationTarget = 200;

    // --- Клиент: подсветка зоны ---
    /** Непрозрачность маски подсветки зоны (0.05 .. 0.6). */
    public static float zoneAlpha = 0.16F;
    /** Анимация (медленный сдвиг полос) маски. */
    public static boolean zoneAnimated = true;

    private static final String[] DEFAULT_FERTILIZERS = {
            "minecraft:dye:15=2,64",
            "thermalfoundation:fertilizer:0=2,64",
            "thermalfoundation:fertilizer:1=3,128",
            "thermalfoundation:fertilizer:2=4,192",
            "ic2:crop_res:2=2,64",
            "forestry:fertilizer_compound:0=2,64",
            "forestry:fertilizer_bio:0=2,32"
    };

    public static void load(Configuration cfg) {
        String c = "Scanning";
        for (int i = 0; i < columnsPerSecond.length; i++) {
            columnsPerSecond[i] = cfg.getInt("ColumnsPerSecondLevel" + i, c, columnsPerSecond[i], 1, 200,
                    "Columns (3 blocks tall) checked per second at Level " + i + ".");
        }

        c = "Energy";
        basePower = cfg.getInt("BasePower", c, basePower, 10, 5000,
                "Base power of the Harvester (Level 0). Defines RF capacity and input rate; scales with level like Thermal Expansion machines.");
        energyPerCheck = cfg.getInt("EnergyPerCheck", c, energyPerCheck, 0, 100000, "RF consumed per checked column.");
        energyPerHarvest = cfg.getInt("EnergyPerHarvest", c, energyPerHarvest, 0, 1000000,
                "RF consumed per harvested crop or removed weed.");
        rangeEnergyMod = cfg.getInt("RangeAugmentEnergyMod", c, rangeEnergyMod, 0, 500,
                "Additional energy (percent) per installed Harvest Area Extender.");

        c = "Area";
        baseRadius = cfg.getInt("BaseRadius", c, baseRadius, 0, 16,
                "Horizontal radius at Level 0 (1 = 3x3). Every level adds 1, every Harvest Area Extender adds 1.");
        maxRadius = cfg.getInt("MaxRadius", c, maxRadius, 1, 32, "Hard cap for the horizontal radius.");

        c = "Harvesting";
        breakParticles = cfg.getBoolean("BreakParticles", c, breakParticles, "Show break particles/sound on harvest.");
        harvestNetherWartOnSoulSand = cfg.getBoolean("NetherWartOnSoulSand", c, harvestNetherWartOnSoulSand,
                "Also harvest and replant Nether Wart planted on Soul Sand (outside of crop sticks).");
        removeIC2Weeds = cfg.getBoolean("RemoveIC2Weeds", c, removeIC2Weeds, "Remove IC2 weeds from crop sticks.");
        String[] list = cfg.getStringList("Blacklist", c, new String[0],
                "Block registry names the Harvester must never touch, e.g. minecraft:wheat");
        BLACKLIST.clear();
        for (String s : list) {
            BLACKLIST.add(s.trim());
        }

        c = "Fertilizer";
        fertilizerCapacity = cfg.getInt("Capacity", c, fertilizerCapacity, 64, 100000, "Internal nutrient storage.");
        recyclerChancePerAugment = cfg.getInt("RecyclerChancePerAugment", c, recyclerChancePerAugment, 0, 100,
                "Chance (percent) not to consume nutrients, per installed Fertilizer Recycler.");
        recyclerChanceMax = cfg.getInt("RecyclerChanceMax", c, recyclerChanceMax, 0, 95, "Maximum total recycler chance.");
        catalystBonus = cfg.getInt("CatalystBonus", c, catalystBonus, 0, 10,
                "Added to the growth multiplier of the current fertilizer by the Growth Catalyst.");
        ic2Nutrients = cfg.getBoolean("IC2Nutrients", c, ic2Nutrients, "Refill nutrients of IC2 crop sticks from the nutrient storage.");
        ic2NutrientCostPer10 = cfg.getInt("IC2NutrientCostPer10", c, ic2NutrientCostPer10, 0, 100,
                "Nutrient storage units per 10 IC2 nutrient points.");
        String[] ferts = cfg.getStringList("Fertilizers", c, DEFAULT_FERTILIZERS,
                "Accepted fertilizers: modid:item:meta=multiplier,units. Multiplier is the crop growth speed (2 = twice as fast), "
                        + "units is how much nutrient storage one item provides. One unit is spent per extra growth cycle.");
        FERTILIZERS.clear();
        for (String s : ferts) {
            parseFertilizer(s.trim());
        }

        c = "Client";
        zoneAlpha = cfg.getFloat("ZoneHighlightOpacity", c, zoneAlpha, 0.05F, 0.6F,
                "Opacity of the harvest area highlight (button in the Harvester GUI).");
        zoneAnimated = cfg.getBoolean("ZoneHighlightAnimated", c, zoneAnimated, "Slowly move the stripes of the harvest area highlight.");

        c = "Irrigation";
        tankCapacity = cfg.getInt("TankCapacity", c, tankCapacity, 1000, 1000000, "Water tank capacity added by the Irrigation Module (mB).");
        waterTicksPerMb = cfg.getInt("TicksPerMbPerBlock", c, waterTicksPerMb, 1, 72000,
                "Every irrigated block (farmland or IC2 crop stick) consumes 1 mB per this many ticks (400 = 20 seconds).");
        ic2HydrationTarget = cfg.getInt("IC2HydrationTarget", c, ic2HydrationTarget, 0, 200,
                "Water storage level IC2 crop sticks are kept at while irrigation is active (0 disables).");
    }

    private static void parseFertilizer(String entry) {
        try {
            String[] kv = entry.split("=");
            String[] id = kv[0].split(":");
            String[] val = kv[1].split(",");
            String key = id[0] + ":" + id[1] + ":" + (id.length > 2 ? Integer.parseInt(id[2]) : 0);
            FERTILIZERS.put(key, new Fertilizer(Math.max(1, Integer.parseInt(val[0].trim())), Math.max(1, Integer.parseInt(val[1].trim()))));
        } catch (Exception e) {
            ThermalHarvest.LOG.error("Invalid fertilizer entry '" + entry + "', expected modid:item:meta=multiplier,units");
        }
    }

    /** Слушатели смены настроек (синхронизация с сервером, возврат к локальному конфигу) — например, страница HEI. */
    private static final java.util.List<Runnable> LISTENERS = new java.util.ArrayList<>();

    public static void addListener(Runnable listener) {
        LISTENERS.add(listener);
    }

    public static void fireChanged() {
        for (Runnable r : LISTENERS) {
            r.run();
        }
    }

    public static Fertilizer getFertilizer(ItemStack stack) {
        if (stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return null;
        }
        Fertilizer f = FERTILIZERS.get(stack.getItem().getRegistryName() + ":" + stack.getMetadata());
        return f;
    }

    /** Проверка, что предмет из записи вообще существует (для лога/подсказок). */
    public static boolean exists(String key) {
        String[] id = key.split(":");
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(id[0], id[1]));
        return item != null;
    }

    public static boolean isBlacklisted(Block block) {
        return !BLACKLIST.isEmpty() && block.getRegistryName() != null && BLACKLIST.contains(block.getRegistryName().toString());
    }

    public static int columnsPerSecond(int level) {
        return columnsPerSecond[Math.max(0, Math.min(level, columnsPerSecond.length - 1))];
    }

    public static final class Fertilizer {
        public final int multiplier;
        public final int units;

        public Fertilizer(int multiplier, int units) {
            this.multiplier = multiplier;
            this.units = units;
        }
    }
}
