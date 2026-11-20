package dev.blockside.core.snow;

import java.util.SplittableRandom;

/**
 * Resort weather. Deterministic per (seed, day) so every client/server and a reload agree.
 * gameTime: 24000 ticks per day, 1000 ticks per hour, day starts at 06:00 (MC convention).
 */
public final class Weather {
    public long seed = 12345;
    /** Manual override for testing: NONE, STORM, WARM, COLD, CLEAR. */
    public String override = "NONE";
    public long overrideUntil;
    public long lastStormDay = -1;
    public int stormStartHour, stormHours;

    public static long day(long gameTime) { return Math.floorDiv(gameTime, 24000L); }
    public static double hourOfDay(long gameTime) { return ((Math.floorMod(gameTime, 24000L)) / 1000.0 + 6) % 24; }

    public Climate.Season season(Climate c, long gameTime) { return c.season(day(gameTime)); }

    /** Air temperature at height y. */
    public double temperature(Climate c, long gameTime, int y) {
        Climate.Season s = season(c, gameTime);
        double base = c.seasonTemp(s);
        double h = hourOfDay(gameTime);
        // Coldest around 05:00, warmest around 15:00.
        double diurnal = -Math.cos((h - 3) / 24.0 * 2 * Math.PI) * c.diurnalAmplitude;
        double t = base + diurnal - (y - c.referenceY) * c.lapsePerBlock;
        if (storming(c, gameTime)) t = Math.min(t, base - 1);
        switch (activeOverride(gameTime)) {
            case "WARM" -> t = 8 - (y - c.referenceY) * c.lapsePerBlock;
            case "COLD" -> t = -12 - (y - c.referenceY) * c.lapsePerBlock;
            default -> {}
        }
        return t;
    }

    public String activeOverride(long gameTime) { return gameTime < overrideUntil ? override : "NONE"; }

    public boolean storming(Climate c, long gameTime) {
        String o = activeOverride(gameTime);
        if (o.equals("STORM")) return true;
        if (!o.equals("NONE")) return false;
        long d = day(gameTime);
        SplittableRandom r = new SplittableRandom(seed * 31 + d);
        if (r.nextDouble() >= c.stormChance(c.season(d))) return false;
        int start = r.nextInt(24);
        int len = c.stormHoursMin + r.nextInt(Math.max(1, c.stormHoursMax - c.stormHoursMin + 1));
        double h = hourOfDay(gameTime);
        double rel = (h - start + 24) % 24;
        return rel < len;
    }

    /** m SWE per hour falling now. */
    public double snowfall(Climate c, long gameTime) { return storming(c, gameTime) ? c.stormSwePerHour : 0; }

    public String describe(Climate c, long gameTime, int y) {
        return String.format("%s day %d, %02d:00, %.1f°C%s", season(c, gameTime), day(gameTime) + 1,
                (int) hourOfDay(gameTime), temperature(c, gameTime, y), storming(c, gameTime) ? ", snowing" : "");
    }
}
