package org.me.gcu.merrouche_rayane_s2326962.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Further processing of the text extracted by the PullParser: road numbers, dates,
 * DATEX II camelCase codes and the abbreviations used by Traffic Scotland.
 */
public final class TextHelper
{
    // Scottish road numbers: M8, A90, A74(M), B957, A720... (optional "(M)" motorway suffix).
    // Case-sensitive and without a space after the letter, so "a 2 lane..." is not a road.
    private static final Pattern ROAD = Pattern.compile("\\b([MAB])(\\d{1,4})(\\s?\\(M\\))?(?!\\d)");

    private static final DateTimeFormatter DISPLAY_DATE =
            DateTimeFormatter.ofPattern("EEE d MMM yyyy, HH:mm", Locale.UK);

    // Abbreviations found in roadworks descriptions
    private static final Map<String, String> ABBREVIATIONS = new LinkedHashMap<>();
    static
    {
        ABBREVIATIONS.put("TTL", "temporary traffic lights");
        ABBREVIATIONS.put("TTLs", "temporary traffic lights");
        ABBREVIATIONS.put("TTL's", "temporary traffic lights");
        ABBREVIATIONS.put("MVMS", "mobile variable message sign");
        ABBREVIATIONS.put("MLC", "mobile lane closure");
        ABBREVIATIONS.put("NB", "northbound");
        ABBREVIATIONS.put("SB", "southbound");
        ABBREVIATIONS.put("EB", "eastbound");
        ABBREVIATIONS.put("WB", "westbound");
        ABBREVIATIONS.put("JCT", "junction");
    }

    private TextHelper() { }

    /** "veryLongDelays" -> "Very long delays", "eastBound" -> "Eastbound", "TpegJunction" -> "Tpeg junction" */
    public static String humanise(String code)
    {
        if (code == null || code.isEmpty())
            return "";
        if (code.equalsIgnoreCase("eastBound") || code.equalsIgnoreCase("westBound")
                || code.equalsIgnoreCase("northBound") || code.equalsIgnoreCase("southBound"))
            return code.substring(0, 1).toUpperCase(Locale.UK) + code.substring(1).toLowerCase(Locale.UK);
        if (code.equalsIgnoreCase("bothWays"))
            return "Both directions";
        if (code.contains(" ")) // already normal text
            return code;
        String spaced = code.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.UK);
        return spaced.substring(0, 1).toUpperCase(Locale.UK) + spaced.substring(1);
    }

    /** Finds road numbers in any text, normalised: "the A74 (M) and m8" -> {"A74(M)", "M8"} */
    public static Set<String> extractRoads(String text)
    {
        Set<String> roads = new LinkedHashSet<>();
        if (text == null)
            return roads;
        Matcher m = ROAD.matcher(text);
        while (m.find())
        {
            String road = m.group(1) + m.group(2);
            if (m.group(3) != null)
                road = road + "(M)";
            roads.add(road);
        }
        return roads;
    }

    /** Canonical form used to compare road names typed by the user: "a 74 (m)" -> "A74M" */
    public static String normaliseRoad(String road)
    {
        if (road == null)
            return "";
        return road.toUpperCase(Locale.UK).replaceAll("[^A-Z0-9]", "");
    }

    /** Parses DATEX II dates like 2026-08-31T09:58:26+01:00; returns null if missing/invalid */
    public static OffsetDateTime parseDate(String text)
    {
        if (text == null || text.trim().isEmpty())
            return null;
        try
        {
            return OffsetDateTime.parse(text.trim());
        }
        catch (DateTimeParseException e)
        {
            return null;
        }
    }

    public static String formatDate(OffsetDateTime date)
    {
        return date == null ? "Not specified" : DISPLAY_DATE.format(date);
    }

    /** "3 days 4 hours", "2 years 3 months", "45 minutes" */
    public static String formatDuration(Duration d)
    {
        if (d == null || d.isNegative())
            return "Unknown";
        long days = d.toDays();
        if (days >= 365)
        {
            long months = Math.min(11, (days % 365) / 30); // 360-364 days is not "12 months"
            return plural(days / 365, "year") + (months > 0 ? " " + plural(months, "month") : "");
        }
        if (days >= 30)
            return plural(days / 30, "month") + (days % 30 > 0 ? " " + plural(days % 30, "day") : "");
        if (days >= 1)
            return plural(days, "day") + (d.toHours() % 24 > 0 ? " " + plural(d.toHours() % 24, "hour") : "");
        if (d.toHours() >= 1)
            return plural(d.toHours(), "hour") + (d.toMinutes() % 60 > 0 ? " " + plural(d.toMinutes() % 60, "minute") : "");
        return plural(Math.max(d.toMinutes(), 0), "minute");
    }

    private static String plural(long n, String unit)
    {
        return n + " " + unit + (n == 1 ? "" : "s");
    }

    /**
     * Splits a roadworks description such as "M8 WB JCT 15 On Slip Stirling Rd - Slip Closure"
     * into location ("M8 WB JCT 15 On Slip Stirling Rd") and works ("Slip Closure").
     * Returns {location, works}; works is "" when there is no " - " separator.
     */
    public static String[] splitWorksDescription(String comment)
    {
        if (comment == null)
            return new String[] {"", ""};
        int i = comment.lastIndexOf(" - ");
        if (i < 0)
            return new String[] {comment.trim(), ""};
        return new String[] {comment.substring(0, i).trim(), comment.substring(i + 3).trim()};
    }

    /** Expands known abbreviations (whole words only): "TTL" -> "TTL (temporary traffic lights)" */
    public static String explainAbbreviations(String text)
    {
        if (text == null)
            return "";
        StringBuilder explained = new StringBuilder();
        for (Map.Entry<String, String> e : ABBREVIATIONS.entrySet())
        {
            Pattern p = Pattern.compile("(?<![A-Za-z])" + Pattern.quote(e.getKey()) + "(?![A-Za-z'])");
            if (p.matcher(text).find() && explained.indexOf(e.getValue()) < 0)
            {
                if (explained.length() > 0) explained.append("; ");
                explained.append(e.getKey()).append(" = ").append(e.getValue());
            }
        }
        return explained.toString();
    }
}
