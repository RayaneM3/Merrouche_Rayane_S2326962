package org.me.gcu.merrouche_rayane_s2326962.model;

/**
 * Duration of a roadworks (overallStartTime -> overallEndTime), used for colour coding.
 * Thresholds chosen from the real feed: ~16% of roadworks last up to a week,
 * ~32% between a week and 3 months, and the rest longer.
 */
public enum DurationBand
{
    SHORT("Short (up to 7 days)", 7),
    MEDIUM("Medium (up to 3 months)", 90),
    LONG("Long (over 3 months)", Long.MAX_VALUE),
    UNKNOWN("Unknown duration", -1);

    private final String label;
    private final long maxDays;

    DurationBand(String label, long maxDays)
    {
        this.label = label;
        this.maxDays = maxDays;
    }

    public String getLabel()
    {
        return label;
    }

    public static DurationBand fromHours(long hours)
    {
        if (hours < 0)
            return UNKNOWN;
        double days = hours / 24.0;
        if (days <= SHORT.maxDays)
            return SHORT;
        if (days <= MEDIUM.maxDays)
            return MEDIUM;
        return LONG;
    }
}
