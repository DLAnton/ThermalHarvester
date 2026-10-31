package dlx.thermalharvest.util;

import cofh.core.util.helpers.StringHelper;

/** Склонение «циклов» по числу: правило выбирается ключом gui.thermalharvest.plural в файле локализации. */
public final class THLang {

    private THLang() {}

    /** Например: cycles(64) -> «64 цикла», cycles(1000) -> «1000 циклов». */
    public static String cycles(int n) {
        return n + " " + StringHelper.localize("gui.thermalharvest.cycle." + pluralForm(n));
    }

    /** Множитель силы: x3 для целых, x2.7 для смеси. */
    public static String mult(double m) {
        long r = Math.round(m);
        if (Math.abs(m - r) < 0.05D) {
            return "x" + r;
        }
        return "x" + String.format(java.util.Locale.ROOT, "%.1f", m);
    }

    /** 1 — «цикл», 2 — «цикла», 5 — «циклов». Для английского: 1 или 5. */
    private static int pluralForm(int n) {
        n = Math.abs(n);
        if (!"slavic".equals(StringHelper.localize("gui.thermalharvest.plural"))) {
            return n == 1 ? 1 : 5;
        }
        int mod10 = n % 10;
        int mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return 1;
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return 2;
        }
        return 5;
    }
}
