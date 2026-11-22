package dlx.thermalharvest.init;

import dlx.thermalharvest.ThermalHarvest;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Настройки мода (config/cofh/thermalharvest.cfg).
 * Ёмкость RF по уровням и число ячеек расширений берутся из конфигов Thermal Expansion,
 * чтобы сборщик вёл себя как остальные машины TE.
 */
public final class THConfig {

    private THConfig() {}

    // ------------------------------------------------------------------
    // Постоянные характеристики машины. Не настраиваются: на них опираются описания расширений,
    // вкладка машины и шкалы в окне (решение 22.11.2026).
    // ------------------------------------------------------------------

    /** Сколько колонок (3 блока по высоте) проверяется в секунду на уровнях 0..4. */
    private static final int[] COLUMNS_PER_SECOND = {1, 2, 4, 8, 16};
    /** Базовая мощность (уровень 0), задаёт ёмкость и приём RF как у машин TE (масштаб по уровням из конфига TE). */
    public static final int basePower = 20;
    /** Доп. затраты энергии (% к проверке и сбору) за каждый расширитель зоны. */
    public static final int rangeEnergyMod = 25;
    /** Радиус по горизонтали на уровне 0 (1 = 3x3). Каждый уровень и каждый расширитель добавляют +1. */
    public static final int baseRadius = 1;
    public static final int maxRadius = 16;
    /** Максимальный внутренний запас питательности. */
    public static final int fertilizerCapacity = 1000;
    /** Шанс (%) не потратить запас за каждый Рециркулятор удобрений и общий предел. */
    public static final int recyclerChancePerAugment = 20;
    public static final int recyclerChanceMax = 80;
    /** Насколько Катализатор роста повышает множитель скорости роста. */
    public static final int catalystBonus = 1;
    /** Сколько единиц запаса стоит 10 единиц питательных веществ IC2. */
    public static final int ic2NutrientCostPer10 = 1;
    /** Бак модуля орошения, mB. */
    public static final int tankCapacity = 10000;
    /** Интервал (тики), за который каждый орошаемый блок (пашня, жёрдочка IC2) расходует 1 mB. */
    public static final int waterTicksPerMb = 400;

    /** Пределы значений в строках списка удобрений. */
    public static final int FERTILIZER_MULTIPLIER_MIN = 2;
    public static final int FERTILIZER_MULTIPLIER_MAX = 8;
    public static final int FERTILIZER_UNITS_MIN = 1;
    public static final int FERTILIZER_UNITS_MAX = 1024;

    // ------------------------------------------------------------------
    // Настройки (config/cofh/thermalharvest.cfg).
    // ------------------------------------------------------------------

    // --- Энергия ---
    /** RF за проверку одной колонки. */
    public static int energyPerCheck = 5;
    /** RF за сбор одной культуры (и за прополку одного сорняка). */
    public static int energyPerHarvest = 200;

    // --- Сбор ---
    public static boolean breakParticles = true;
    /** Собирать адский нарост, посаженный на песок душ (вне жёрдочек). */
    public static boolean harvestNetherWartOnSoulSand = false;
    /** Выпалывать сорняки IC2 (на любой стадии). */
    public static boolean removeIC2Weeds = true;
    /** Пересобирается целиком и подменяется одной записью: настройки меняются из окна «Настройки», пока игра идёт. */
    public static volatile Set<String> BLACKLIST = new HashSet<>();

    // --- Удобрения ---
    /** Пополнять питательные вещества жёрдочек IC2 из запаса удобрения (как Crop-Matron). */
    public static boolean ic2Nutrients = true;
    /** Описание удобрений: предмет -> (множитель скорости, единиц запаса). */
    public static volatile Map<String, Fertilizer> FERTILIZERS = new HashMap<>();

    // --- Орошение ---
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
        removeObsolete(cfg);

        String c = "Energy";
        energyPerCheck = cfg.getInt("EnergyPerCheck", c, energyPerCheck, 1, 50, "RF consumed per checked column.");
        energyPerHarvest = cfg.getInt("EnergyPerHarvest", c, energyPerHarvest, 50, 1000,
                "RF consumed per harvested crop or removed weed.");

        c = "Harvesting";
        breakParticles = cfg.getBoolean("BreakParticles", c, breakParticles, "Show break particles/sound on harvest.");
        harvestNetherWartOnSoulSand = cfg.getBoolean("NetherWartOnSoulSand", c, harvestNetherWartOnSoulSand,
                "Also harvest and replant Nether Wart planted on Soul Sand (outside of crop sticks).");
        removeIC2Weeds = cfg.getBoolean("RemoveIC2Weeds", c, removeIC2Weeds, "Remove IC2 weeds from crop sticks.");
        String[] list = cfg.getStringList("Blacklist", c, new String[0],
                "Block registry names the Harvester must never touch, e.g. minecraft:wheat");
        Set<String> blacklist = new HashSet<>();
        for (String s : list) {
            blacklist.add(s.trim());
        }
        BLACKLIST = blacklist;

        c = "Fertilizer";
        ic2Nutrients = cfg.getBoolean("IC2Nutrients", c, ic2Nutrients, "Refill nutrients of IC2 crop sticks from the nutrient storage.");
        String[] ferts = cfg.getStringList("Fertilizers", c, DEFAULT_FERTILIZERS,
                "Accepted fertilizers: modid:item:meta=multiplier,units. Multiplier is the crop growth speed (2 = twice as fast, "
                        + FERTILIZER_MULTIPLIER_MIN + ".." + FERTILIZER_MULTIPLIER_MAX + "), units is how much nutrient storage one item provides ("
                        + FERTILIZER_UNITS_MIN + ".." + FERTILIZER_UNITS_MAX + "). One unit is spent per extra growth cycle.");
        Map<String, Fertilizer> fertilizers = new HashMap<>();
        for (String s : ferts) {
            parseFertilizer(s.trim(), fertilizers);
        }
        FERTILIZERS = fertilizers;

        loadClient(cfg);

        c = "Irrigation";
        ic2HydrationTarget = cfg.getInt("IC2HydrationTarget", c, ic2HydrationTarget, 0, 200,
                "Water storage level IC2 crop sticks are kept at while irrigation is active (0 disables).");

        setupGui(cfg);
    }

    /** Убирает из файла настройки, которые с 22.11.2026 стали постоянными. */
    private static void removeObsolete(Configuration cfg) {
        cfg.removeCategory(cfg.getCategory("Scanning"));
        cfg.removeCategory(cfg.getCategory("Area"));
        removeKeys(cfg, "Energy", "BasePower", "RangeAugmentEnergyMod");
        removeKeys(cfg, "Fertilizer", "Capacity", "RecyclerChancePerAugment", "RecyclerChanceMax", "CatalystBonus", "IC2NutrientCostPer10");
        removeKeys(cfg, "Irrigation", "TankCapacity", "TicksPerMbPerBlock");
    }

    private static void removeKeys(Configuration cfg, String category, String... keys) {
        if (!cfg.hasCategory(category)) {
            return;
        }
        ConfigCategory cat = cfg.getCategory(category);
        for (String key : keys) {
            cat.remove(key);
        }
    }

    /** Только клиентские настройки (подсветка зоны). Их сервер не присылает, поэтому они меняются и во время игры на сервере. */
    public static void loadClient(Configuration cfg) {
        String c = "Client";
        zoneAlpha = cfg.getFloat("ZoneHighlightOpacity", c, zoneAlpha, 0.05F, 0.6F,
                "Opacity of the harvest area highlight (button in the Harvester GUI).");
        zoneAnimated = cfg.getBoolean("ZoneHighlightAnimated", c, zoneAnimated, "Slowly move the stripes of the harvest area highlight.");
    }

    /** Ключи перевода для окна «Настройки». */
    private static void setupGui(Configuration cfg) {
        for (String name : cfg.getCategoryNames()) {
            ConfigCategory cat = cfg.getCategory(name);
            String key = "config." + ThermalHarvest.MOD_ID + "." + name.toLowerCase(Locale.ROOT);
            cat.setLanguageKey(key);
            for (Property p : cat.getOrderedValues()) {
                p.setLanguageKey(key + "." + p.getName());
            }
        }
    }

    private static void parseFertilizer(String entry, Map<String, Fertilizer> into) {
        try {
            String[] kv = entry.split("=");
            String[] id = kv[0].split(":");
            String[] val = kv[1].split(",");
            String key = id[0] + ":" + id[1] + ":" + (id.length > 2 ? Integer.parseInt(id[2]) : 0);
            int multiplier = clamp(Integer.parseInt(val[0].trim()), FERTILIZER_MULTIPLIER_MIN, FERTILIZER_MULTIPLIER_MAX, entry);
            int units = clamp(Integer.parseInt(val[1].trim()), FERTILIZER_UNITS_MIN, FERTILIZER_UNITS_MAX, entry);
            into.put(key, new Fertilizer(multiplier, units));
        } catch (Exception e) {
            ThermalHarvest.LOG.error("Invalid fertilizer entry '" + entry + "', expected modid:item:meta=multiplier,units");
        }
    }

    private static int clamp(int value, int min, int max, String entry) {
        if (value < min || value > max) {
            int clamped = Math.max(min, Math.min(max, value));
            ThermalHarvest.LOG.warn("Fertilizer entry '" + entry + "': " + value + " is outside " + min + ".." + max + ", using " + clamped);
            return clamped;
        }
        return value;
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
        return COLUMNS_PER_SECOND[Math.max(0, Math.min(level, COLUMNS_PER_SECOND.length - 1))];
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
