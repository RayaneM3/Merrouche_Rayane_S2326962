package org.me.gcu.merrouche_rayane_s2326962.model;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * One "situation" (unplanned event or roadworks) parsed from the DATEX II feed.
 * Holds the data of its <situationRecord>; if a <situation> contains several records,
 * each record becomes its own Situation object sharing the same situationId.
 */
public class Situation
{
    private final FeedType feedType;

    // <situation id=".." version="..">
    private String situationId = "";
    // <situationRecord xsi:type=".." id=".." version="..">
    private String recordId = "";
    private String recordVersion = "";
    private String recordType = "";      // xsi:type, i.e. "MaintenanceWorks"
    private String subType = "";         // i.e. roadClosed / queuingTraffic / roadworks

    private OffsetDateTime creationTime;
    private OffsetDateTime versionTime;
    private String probability = "";     // probabilityOfOccurrence
    private String sourceName = "";

    // <validity>
    private String validityStatus = "";
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;

    private final Impact impact = new Impact();
    private String comment = "";         // generalPublicComment
    private String complianceOption = "";
    private boolean urgentRoadworks = false;

    // <groupOfLocations>
    private String locationGroupType = ""; // i.e. "Linear"
    private String direction = "";        // tpegDirection
    private String linearLocationType = "";
    private final List<LocationPoint> locations = new ArrayList<>();

    // Any other value found inside the record (unknown or future fields): element name -> text
    private final Map<String, String> otherValues = new LinkedHashMap<>();

    public Situation(FeedType feedType)
    {
        this.feedType = feedType;
    }

    // ------------------------------------------------------------------ getters / setters

    public FeedType getFeedType() { return feedType; }

    public String getSituationId() { return situationId; }

    public void setSituationId(String situationId) { this.situationId = nn(situationId); }

    public String getRecordId() { return recordId; }

    public void setRecordId(String recordId) { this.recordId = nn(recordId); }

    public String getRecordVersion() { return recordVersion; }

    public void setRecordVersion(String recordVersion) { this.recordVersion = nn(recordVersion); }

    public String getRecordType() { return recordType; }

    public void setRecordType(String recordType) { this.recordType = nn(recordType); }

    public String getSubType() { return subType; }

    public void setSubType(String subType) { this.subType = nn(subType); }

    public OffsetDateTime getCreationTime() { return creationTime; }

    public void setCreationTime(OffsetDateTime creationTime) { this.creationTime = creationTime; }

    public OffsetDateTime getVersionTime() { return versionTime; }

    public void setVersionTime(OffsetDateTime versionTime) { this.versionTime = versionTime; }

    public String getProbability() { return probability; }

    public void setProbability(String probability) { this.probability = nn(probability); }

    public String getSourceName() { return sourceName; }

    public void setSourceName(String sourceName) { this.sourceName = nn(sourceName); }

    public String getValidityStatus() { return validityStatus; }

    public void setValidityStatus(String validityStatus) { this.validityStatus = nn(validityStatus); }

    public OffsetDateTime getStartTime() { return startTime; }

    public void setStartTime(OffsetDateTime startTime) { this.startTime = startTime; }

    public OffsetDateTime getEndTime() { return endTime; }

    public void setEndTime(OffsetDateTime endTime) { this.endTime = endTime; }

    public Impact getImpact() { return impact; }

    public String getComment() { return comment; }

    /** Appends, as some records may contain several comment values */
    public void addComment(String text)
    {
        if (text == null || text.trim().isEmpty())
            return;
        // the feed sometimes has double spaces inside comments
        String clean = text.trim().replaceAll("\\s{2,}", " ");
        comment = comment.isEmpty() ? clean : comment + "\n" + clean;
    }

    public String getComplianceOption() { return complianceOption; }

    public void setComplianceOption(String complianceOption) { this.complianceOption = nn(complianceOption); }

    public boolean isUrgentRoadworks() { return urgentRoadworks; }

    public void setUrgentRoadworks(boolean urgentRoadworks) { this.urgentRoadworks = urgentRoadworks; }

    public String getLocationGroupType() { return locationGroupType; }

    public void setLocationGroupType(String locationGroupType) { this.locationGroupType = nn(locationGroupType); }

    public String getDirection() { return direction; }

    public void setDirection(String direction) { this.direction = nn(direction); }

    public String getLinearLocationType() { return linearLocationType; }

    public void setLinearLocationType(String linearLocationType) { this.linearLocationType = nn(linearLocationType); }

    public List<LocationPoint> getLocations() { return locations; }

    public Map<String, String> getOtherValues() { return otherValues; }

    private static String nn(String s)
    {
        return s == null ? "" : s.trim();
    }

    // ------------------------------------------------------------------ derived information

    /** Unique key for this item (situation id + record id) */
    public String getKey()
    {
        return feedType.name() + ":" + situationId + "/" + recordId;
    }

    public SituationKind getKind()
    {
        return SituationKind.classify(recordType, subType, urgentRoadworks, impact.hasLaneInformation());
    }

    /** Type shown in the list, i.e. "Road closed", "Queuing traffic", "Roadworks" */
    public String getTypeLabel()
    {
        if (feedType == FeedType.ROADWORKS)
            return urgentRoadworks ? "Urgent roadworks" : "Roadworks";
        if (!subType.isEmpty())
            return TextHelper.humanise(subType);
        return TextHelper.humanise(recordType);
    }

    /** All road numbers involved: from the location descriptors and from the comment text */
    public Set<String> getRoads()
    {
        Set<String> roads = new LinkedHashSet<>();
        for (LocationPoint p : locations)
            roads.addAll(p.getRoads());
        roads.addAll(TextHelper.extractRoads(comment));
        return roads;
    }

    /** Main road, i.e. "M8" (empty if unknown) */
    public String getMainRoad()
    {
        Set<String> roads = getRoads();
        return roads.isEmpty() ? "" : roads.iterator().next();
    }

    public LocationPoint getPoint(String role)
    {
        for (LocationPoint p : locations)
            if (p.getRole().equalsIgnoreCase(role))
                return p;
        return null;
    }

    public List<LocationPoint> getPointsWithCoordinates()
    {
        List<LocationPoint> pts = new ArrayList<>();
        for (LocationPoint p : locations)
            if (p.hasCoordinates())
                pts.add(p);
        return pts;
    }

    public boolean hasCoordinates()
    {
        return !getPointsWithCoordinates().isEmpty();
    }

    /** Middle of the affected stretch (average of its points), or null without coordinates */
    public double[] getCentre()
    {
        List<LocationPoint> pts = getPointsWithCoordinates();
        if (pts.isEmpty())
            return null;
        double lat = 0, lon = 0;
        for (LocationPoint p : pts)
        {
            lat += p.getLatitude();
            lon += p.getLongitude();
        }
        return new double[] {lat / pts.size(), lon / pts.size()};
    }

    /** Distance in km from (lat, lon) to the nearest part of this situation; MAX_VALUE if unknown */
    public double distanceFromKm(double lat, double lon)
    {
        List<LocationPoint> pts = getPointsWithCoordinates();
        if (pts.isEmpty())
            return Double.MAX_VALUE;
        double best = Double.MAX_VALUE;
        if (pts.size() >= 2)
        {
            for (int i = 0; i < pts.size() - 1; i++)
            {
                LocationPoint a = pts.get(i), b = pts.get(i + 1);
                best = Math.min(best, GeoHelper.distanceToSegmentKm(lat, lon,
                        a.getLatitude(), a.getLongitude(), b.getLatitude(), b.getLongitude()));
            }
        }
        for (LocationPoint p : pts)
            best = Math.min(best, GeoHelper.distanceKm(lat, lon, p.getLatitude(), p.getLongitude()));
        return best;
    }

    /** Planned duration (start -> end); null if either date is missing */
    public Duration getDuration()
    {
        if (startTime == null || endTime == null)
            return null;
        return Duration.between(startTime, endTime);
    }

    public DurationBand getDurationBand()
    {
        Duration d = getDuration();
        return d == null ? DurationBand.UNKNOWN : DurationBand.fromHours(d.toHours());
    }

    /** "From A to B" description of the affected stretch, if names are available */
    public String getLocationDescription()
    {
        LocationPoint from = getPoint("from"), to = getPoint("to");
        String f = from == null ? "" : from.getDisplayName();
        String t = to == null ? "" : to.getDisplayName();
        if (!f.isEmpty() && !t.isEmpty() && !f.equalsIgnoreCase(t))
            return f + "  →  " + t;
        if (!f.isEmpty()) return f;
        if (!t.isEmpty()) return t;
        for (LocationPoint p : locations)
            if (!p.getDisplayName().isEmpty())
                return p.getDisplayName();
        return "";
    }

    /** true if the user's road text (i.e. "a74m", "M 8") refers to a road of this situation */
    public boolean matchesRoad(String query)
    {
        String q = TextHelper.normaliseRoad(query);
        if (q.isEmpty())
            return false;
        for (String road : getRoads())
            if (TextHelper.normaliseRoad(road).equals(q))
                return true;
        // Not a road number (i.e. "Kingston Bridge"): search place names and the description
        if (!q.matches("[MAB]\\d+M?"))
        {
            String lower = query.trim().toLowerCase(Locale.UK);
            if (comment.toLowerCase(Locale.UK).contains(lower))
                return true;
            for (LocationPoint p : locations)
                for (String d : p.getDescriptors().values())
                    if (d.toLowerCase(Locale.UK).contains(lower))
                        return true;
        }
        return false;
    }

    @Override
    public String toString()
    {
        return "Situation{" + feedType + " " + situationId + "/" + recordId + " type=" + recordType
                + " sub=" + subType + " roads=" + getRoads() + " start=" + startTime + " end=" + endTime
                + " impact=[" + impact.getSummary() + "] dir=" + direction + " locations=" + locations
                + " comment='" + comment + "'}";
    }
}
