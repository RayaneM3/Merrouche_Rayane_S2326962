package org.me.gcu.merrouche_rayane_s2326962.model;

import java.util.Locale;

import java.util.LinkedHashMap;
import java.util.Map;

/** Contents of the <impact> element of a situation record */
public class Impact
{
    private Integer lanesRestricted;   // <numberOfLanesRestricted>
    private Integer operationalLanes;  // <numberOfOperationalLanes>
    private String delaysType = "";    // <delays><delaysType>: delays / longDelays / veryLongDelays
    // Any other impact fields the feed may add in the future
    private final Map<String, String> otherValues = new LinkedHashMap<>();

    public Integer getLanesRestricted() { return lanesRestricted; }

    public void setLanesRestricted(Integer lanesRestricted) { this.lanesRestricted = lanesRestricted; }

    public Integer getOperationalLanes() { return operationalLanes; }

    public void setOperationalLanes(Integer operationalLanes) { this.operationalLanes = operationalLanes; }

    public String getDelaysType() { return delaysType; }

    public void setDelaysType(String delaysType) { this.delaysType = delaysType == null ? "" : delaysType.trim(); }

    public Map<String, String> getOtherValues() { return otherValues; }

    public boolean hasLaneInformation()
    {
        return lanesRestricted != null || operationalLanes != null;
    }

    /** 0 = no delay info, 1 = delays, 2 = long delays, 3 = very long delays */
    public int getDelayLevel()
    {
        String d = delaysType.toLowerCase(Locale.UK);
        if (d.contains("verylong")) return 3;
        if (d.contains("long")) return 2;
        if (d.contains("delay")) return 1;
        return 0;
    }

    public boolean isEmpty()
    {
        return !hasLaneInformation() && delaysType.isEmpty() && otherValues.isEmpty();
    }

    /** Human readable summary, i.e. "2 lanes restricted, long delays" */
    public String getSummary()
    {
        StringBuilder sb = new StringBuilder();
        if (operationalLanes != null)
            sb.append(operationalLanes == 0 ? "No lanes open" : operationalLanes + " lane(s) open");
        if (lanesRestricted != null)
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append(lanesRestricted).append(lanesRestricted == 1 ? " lane restricted" : " lanes restricted");
        }
        if (!delaysType.isEmpty())
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append(TextHelper.humanise(delaysType).toLowerCase(Locale.UK));
        }
        for (Map.Entry<String, String> e : otherValues.entrySet())
        {
            if (sb.length() > 0) sb.append(", ");
            sb.append(TextHelper.humanise(e.getKey())).append(": ").append(TextHelper.humanise(e.getValue()));
        }
        return sb.toString();
    }
}
