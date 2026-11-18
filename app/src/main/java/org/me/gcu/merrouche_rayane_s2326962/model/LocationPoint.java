package org.me.gcu.merrouche_rayane_s2326962.model;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * One point of a situation's <groupOfLocations>, i.e. the "from" or "to" end of a linear
 * location: coordinates plus the names/road numbers given by its descriptors.
 */
public class LocationPoint
{
    private final String role;      // "from", "to", or another element name for other location types
    private final String pointType; // xsi:type, i.e. "TpegJunction" / "TpegNonJunctionPoint"
    private double latitude = Double.NaN;
    private double longitude = Double.NaN;
    // descriptor type -> text, i.e. "junctionName" -> "J18", "intersectionName" -> "M8 J18 Charing Cross"
    private final Map<String, String> descriptors = new LinkedHashMap<>();
    // road numbers found in the descriptors (tpegIlcName1/2, linkName), i.e. "M8", "A74(M)"
    private final Set<String> roads = new LinkedHashSet<>();

    public LocationPoint(String role, String pointType)
    {
        this.role = role == null ? "" : role;
        this.pointType = pointType == null ? "" : pointType;
    }

    public String getRole() { return role; }

    public String getPointType() { return pointType; }

    public double getLatitude() { return latitude; }

    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }

    public void setLongitude(double longitude) { this.longitude = longitude; }

    public boolean hasCoordinates()
    {
        return !Double.isNaN(latitude) && !Double.isNaN(longitude);
    }

    public Map<String, String> getDescriptors() { return descriptors; }

    public Set<String> getRoads() { return roads; }

    /**
     * Stores a descriptor once both its text and its type are known
     * (in the XML the type element comes AFTER the text value).
     */
    public void addDescriptor(String descriptorType, String value)
    {
        if (value == null || value.trim().isEmpty())
            return;
        String type = descriptorType == null || descriptorType.isEmpty() ? "name" : descriptorType;
        String text = value.trim();
        // tpegIlcName1/2 and linkName are road numbers; keep them also as roads
        if (type.startsWith("tpegIlcName") || type.equals("linkName"))
            roads.addAll(TextHelper.extractRoads(text));
        String key = type;
        int n = 2;
        while (descriptors.containsKey(key)) // avoid overwriting repeated descriptor types
            key = type + n++;
        descriptors.put(key, text);
    }

    /** Best place name for display, i.e. "M8 J18 Charing Cross" or "A78 Skelmorlie" */
    public String getDisplayName()
    {
        String[] preferred = {"intersectionName", "nonLinkedPointName", "junctionName", "name"};
        for (String p : preferred)
            if (descriptors.containsKey(p))
            {
                String n = descriptors.get(p);
                // a bare junction number reads better with its road, i.e. "M8 J18"
                if (p.equals("junctionName") && !roads.isEmpty())
                    return roads.iterator().next() + " " + n;
                return n;
            }
        if (!roads.isEmpty())
            return String.join(" / ", roads);
        return "";
    }

    @Override
    public String toString()
    {
        return role + "(" + pointType + ") " + getDisplayName() + " [" + latitude + ", " + longitude + "] roads=" + roads;
    }
}
