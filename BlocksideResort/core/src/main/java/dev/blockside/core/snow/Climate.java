package dev.blockside.core.snow;

/** Seasonal calendar and weather tuning. */
public final class Climate {
    public enum Season { AUTUMN, WINTER, SPRING, SUMMER }

    public int autumnDays = 3, winterDays = 14, springDays = 5, summerDays = 2;
    /** Mean temperature at the reference elevation per season (°C). */
    public double autumnTemp = 2, winterTemp = -7, springTemp = 3, summerTemp = 14;
    public double diurnalAmplitude = 4;
    public int referenceY = 64;
    /** °C per block of elevation. Exaggerated vs. reality so a 60-block mountain matters. */
    public double lapsePerBlock = 0.04;
    /** Chance per in-game day of a storm starting (per season). */
    public double autumnStorm = 0.25, winterStorm = 0.45, springStorm = 0.2;
    /** Storm snowfall in m SWE per hour. */
    public double stormSwePerHour = 0.004;
    public int stormHoursMin = 4, stormHoursMax = 12;
    /** Snow guns run at or below this temperature. */
    public double snowGunCutoff = -2;

    public int yearDays() { return autumnDays + winterDays + springDays + summerDays; }

    public Season season(long day) {
        long d = Math.floorMod(day, yearDays());
        if (d < autumnDays) return Season.AUTUMN;
        if (d < autumnDays + winterDays) return Season.WINTER;
        if (d < autumnDays + winterDays + springDays) return Season.SPRING;
        return Season.SUMMER;
    }

    public double seasonTemp(Season s) {
        return switch (s) { case AUTUMN -> autumnTemp; case WINTER -> winterTemp; case SPRING -> springTemp; case SUMMER -> summerTemp; };
    }

    public double stormChance(Season s) {
        return switch (s) { case AUTUMN -> autumnStorm; case WINTER -> winterStorm; case SPRING -> springStorm; case SUMMER -> 0; };
    }
}
