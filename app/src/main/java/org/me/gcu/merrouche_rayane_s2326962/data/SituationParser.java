package org.me.gcu.merrouche_rayane_s2326962.data;

import android.util.Log;

import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.LocationPoint;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.model.TextHelper;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * Parses a Traffic Scotland DATEX II publication (already downloaded into a String) using
 * XmlPullParser, and builds the list of Situation objects.
 *
 * It follows the same approach as the Lab 3 CompletePullParser ("Thing" collection):
 *  - the same event loop: while (eventType != END_DOCUMENT) { START_TAG / END_TAG ... xpp.next() }
 *  - a new Situation object is created at the <situationRecord> START_TAG, filled in while the
 *    parser is inside it, and added to an ArrayList at its END_TAG
 *  - text is broken down further (dates, numbers, road names...) before being stored.
 *
 * The lab used one flag (insideAThing) to tell a Thing's <description> from the collection's one.
 * In DATEX II the same tag names appear in many places (i.e. <value> is a comment, a location
 * name or a source name depending on where it is), so the flag is generalised into a stack with
 * the path of open elements (i.e. situation > situationRecord > groupOfLocations >
 * tpegLinearLocation > to > ilc > descriptor > values > value) and inside("...") replaces the flag.
 * Text is collected in TEXT events and processed at the END_TAG (instead of nextText()), so
 * unknown/new elements never break the parsing: their values are just kept as "other information".
 */
public class SituationParser
{
    private static final String TAG = "SituationParser";
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final Deque<String> path = new ArrayDeque<>(); // open elements, top = current one
    private final StringBuilder text = new StringBuilder();

    private FeedType feed;              // publication being parsed
    private ArrayList<Situation> situations;
    private Situation current;         // situation record being parsed
    private LocationPoint point;        // location point being parsed
    private int pointOwnerDepth = -1;   // depth of the element that owns an auto-created point
    private String situationId = "";
    private String pendingDescriptor;   // descriptor text waiting for its "...DescriptorType"
    private OffsetDateTime publicationTime;

    /** Result of parsing one publication */
    public static class Result
    {
        public final FeedType feedType;
        public final OffsetDateTime publicationTime;
        public final ArrayList<Situation> situations;

        Result(FeedType feedType, OffsetDateTime publicationTime, ArrayList<Situation> situations)
        {
            this.feedType = feedType;
            this.publicationTime = publicationTime;
            this.situations = situations;
        }
    }

    /**
     * @param xml  the downloaded data
     * @param feed which publication it is (unplanned events or roadworks)
     */
    public Result parse(String xml, FeedType feed) throws XmlPullParserException, IOException
    {
        // reset state, so the same parser object can be reused
        this.feed = feed;
        situations = new ArrayList<>();
        path.clear();
        text.setLength(0);
        current = null;
        point = null;
        pointOwnerDepth = -1;
        situationId = "";
        pendingDescriptor = null;
        publicationTime = null;

        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(true);
        XmlPullParser xpp = factory.newPullParser();
        xpp.setInput(new StringReader(cleanUp(xml)));

        int eventType = xpp.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT)
        {
            if (eventType == XmlPullParser.START_TAG)
            {
                startTag(xpp);
            }
            else if (eventType == XmlPullParser.TEXT)
            {
                text.append(xpp.getText());
            }
            else if (eventType == XmlPullParser.END_TAG)
            {
                endTag(xpp.getName(), text.toString().trim());
                path.pop();
                text.setLength(0);
            }
            eventType = xpp.next(); // Get the next event before looping again
        }
        Log.d(TAG, feed + ": " + situations.size() + " situation records parsed");
        return new Result(feed, publicationTime, situations);
    }

    /** Removes any garbage before the XML declaration (as in the starter code) */
    static String cleanUp(String xml)
    {
        if (xml == null)
            return "";
        int i = xml.indexOf("<?");
        if (i < 0)
            i = xml.indexOf('<');
        return i > 0 ? xml.substring(i) : xml;
    }

    private void startTag(XmlPullParser xpp)
    {
        String name = xpp.getName();
        path.push(name);
        text.setLength(0);

        if (name.equals("situation"))
        {
            situationId = attribute(xpp, "id");
        }
        else if (name.equals("situationRecord"))
        {
            current = new Situation(feed);
            current.setSituationId(situationId);
            current.setRecordId(attribute(xpp, "id"));
            current.setRecordVersion(attribute(xpp, "version"));
            current.setRecordType(xsiType(xpp)); // i.e. "RoadOrCarriagewayOrLaneManagement"
        }
        else if (current != null && name.equals("groupOfLocations"))
        {
            current.setLocationGroupType(xsiType(xpp));
        }
        else if (current != null && inside("groupOfLocations") && (name.equals("from") || name.equals("to")))
        {
            point = new LocationPoint(name, xsiType(xpp));
            pointOwnerDepth = -1;
        }
        else if (current != null && inside("groupOfLocations") && point == null
                && (name.equals("pointCoordinates") || name.equals("pointByCoordinates")))
        {
            // Other location types (i.e. a single Point): create the point, owned by the parent element
            String owner = parentOf(name);
            point = new LocationPoint(owner, xsiType(xpp));
            pointOwnerDepth = path.size() - 1;
        }
    }

    private void endTag(String name, String value)
    {
        if (current == null)
        {
            if (name.equals("publicationTime"))
                publicationTime = TextHelper.parseDate(value);
            return; // outside any situationRecord (header info, etc.)
        }

        switch (name)
        {
            case "situationRecord":
                Log.d(TAG, "Parsed: " + current.getRecordId() + " " + current.getTypeLabel());
                situations.add(current);
                current = null;
                point = null;
                return;
            case "situationRecordCreationTime":
                current.setCreationTime(TextHelper.parseDate(value));
                return;
            case "situationRecordVersionTime":
                current.setVersionTime(TextHelper.parseDate(value));
                return;
            case "situationRecordCreationReference":
            case "situationRecordFirstSupplierVersionTime":
            case "sourceIdentification":
                return; // duplicates of information already stored
            case "probabilityOfOccurrence":
                current.setProbability(value);
                return;
            case "validityStatus":
                current.setValidityStatus(value);
                return;
            case "overallStartTime":
                current.setStartTime(TextHelper.parseDate(value));
                return;
            case "overallEndTime":
                current.setEndTime(TextHelper.parseDate(value));
                return;
            case "numberOfLanesRestricted":
                current.getImpact().setLanesRestricted(toInteger(value));
                return;
            case "numberOfOperationalLanes":
                current.getImpact().setOperationalLanes(toInteger(value));
                return;
            case "delaysType":
                current.getImpact().setDelaysType(value);
                return;
            case "complianceOption":
                current.setComplianceOption(value);
                return;
            case "urgentRoadworks":
                current.setUrgentRoadworks(value.equalsIgnoreCase("true"));
                return;
            case "tpegDirection":
                current.setDirection(value);
                return;
            case "tpegLinearLocationType":
                current.setLinearLocationType(value);
                return;
            case "latitude":
                if (point != null) point.setLatitude(toDouble(value));
                return;
            case "longitude":
                if (point != null) point.setLongitude(toDouble(value));
                return;
            case "value":
                handleValue(value);
                return;
            case "tpegIlcPointDescriptorType":
            case "tpegOtherPointDescriptorType":
            case "tpegJunctionPointDescriptorType":
                // the descriptor text came first; now that we know its type, store it
                if (point != null)
                    point.addDescriptor(value, pendingDescriptor);
                pendingDescriptor = null;
                return;
            case "from":
            case "to":
                if (point != null && pointOwnerDepth < 0 && inside("groupOfLocations"))
                {
                    current.getLocations().add(point);
                    point = null;
                }
                return;
            default:
                break;
        }

        // Auto-created point (non-linear location): add it when its owner element closes
        if (point != null && pointOwnerDepth >= 0 && path.size() == pointOwnerDepth)
        {
            current.getLocations().add(point);
            point = null;
            pointOwnerDepth = -1;
        }

        if (value.isEmpty())
            return; // container element

        String parent = parentOf(name);
        if (name.endsWith("Type") && "situationRecord".equals(parent) && current.getSubType().isEmpty())
        {
            // roadOrCarriagewayOrLaneManagementType, abnormalTrafficType, roadMaintenanceType,
            // accidentType, ... -> the specific kind of situation
            current.setSubType(value);
        }
        else if (inside("impact"))
        {
            current.getImpact().getOtherValues().put(name, value);
        }
        else if (!inside("groupOfLocations"))
        {
            current.getOtherValues().put(name, value); // unknown / future field: keep it
        }
    }

    /** <value> elements: their meaning depends on where they are */
    private void handleValue(String value)
    {
        if (inside("generalPublicComment"))
            current.addComment(value);
        else if (inside("sourceName"))
            current.setSourceName(value);
        else if (point != null && (inside("ilc") || inside("name") || inside("otherName")))
            pendingDescriptor = value;
        else if (!value.isEmpty() && !inside("groupOfLocations"))
        {
            // a multilingual value of some other element: store it under that element's name
            String owner = "value";
            for (String p : path)
                if (!p.equals("value") && !p.equals("values") && !p.equals("comment") && !p.equals("descriptor"))
                {
                    owner = p;
                    break;
                }
            current.getOtherValues().put(owner, value);
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean inside(String element)
    {
        return path.contains(element);
    }

    /** Name of the element enclosing the given (top of stack) element */
    private String parentOf(String element)
    {
        boolean found = false;
        for (String p : path) // iterates from the top of the stack
        {
            if (found)
                return p;
            if (p.equals(element))
                found = true;
        }
        return "";
    }

    private static String attribute(XmlPullParser xpp, String name)
    {
        String v = xpp.getAttributeValue(null, name);
        return v == null ? "" : v;
    }

    /** xsi:type attribute, i.e. <situationRecord xsi:type="MaintenanceWorks" ...> */
    private static String xsiType(XmlPullParser xpp)
    {
        String v = xpp.getAttributeValue(XSI_NS, "type");
        if (v == null) // fall back to any attribute called "type"
        {
            for (int i = 0; i < xpp.getAttributeCount(); i++)
                if (xpp.getAttributeName(i).endsWith("type"))
                    return xpp.getAttributeValue(i);
            return "";
        }
        return v;
    }

    private static Integer toInteger(String s)
    {
        try
        {
            return Integer.parseInt(s.trim());
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "Not an integer: " + s);
            return null;
        }
    }

    private static double toDouble(String s)
    {
        try
        {
            return Double.parseDouble(s.trim());
        }
        catch (NumberFormatException e)
        {
            Log.e(TAG, "Not a number: " + s);
            return Double.NaN;
        }
    }
}
