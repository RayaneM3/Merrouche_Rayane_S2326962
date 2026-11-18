package org.me.gcu.merrouche_rayane_s2326962;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.me.gcu.merrouche_rayane_s2326962.data.SituationParser;
import org.me.gcu.merrouche_rayane_s2326962.model.DurationBand;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.LocationPoint;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.model.SituationKind;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser tests on the sample feeds supplied with the coursework (src/test/resources).
 * They check that what the app stores is consistent with the raw XML data.
 */
public class SituationParserTest
{
    private static String load(String name)
    {
        InputStream in = SituationParserTest.class.getClassLoader().getResourceAsStream(name);
        assertNotNull("missing test resource " + name, in);
        Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static int count(String xml, String regex)
    {
        Matcher m = Pattern.compile(regex).matcher(xml);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    private static List<Situation> parse(String file, FeedType feed) throws Exception
    {
        return new SituationParser().parse(load(file), feed).situations;
    }

    @Test
    public void everySituationRecordInTheXmlIsParsed() throws Exception
    {
        String[][] files = {
                {"unplannedContentExample.xml", "INCIDENT"},
                {"unplannedContentExample1.xml", "INCIDENT"},
                {"roadworksContentExample.xml", "ROADWORKS"},
                {"roadworksContentExample1.xml", "ROADWORKS"}};
        for (String[] f : files)
        {
            String xml = load(f[0]);
            List<Situation> list = new SituationParser().parse(xml, FeedType.valueOf(f[1])).situations;
            assertEquals(f[0], count(xml, "<situationRecord "), list.size());
            // every record has its comment, and both ends of its location with coordinates
            for (Situation s : list)
            {
                assertFalse(s.getRecordId() + " comment", s.getComment().isEmpty());
                assertEquals(s.getRecordId() + " points", 2, s.getPointsWithCoordinates().size());
                assertNotNull(s.getRecordId() + " start", s.getStartTime());
            }
        }
    }

    @Test
    public void incidentDetailsMatchTheXml() throws Exception
    {
        List<Situation> list = parse("unplannedContentExample1.xml", FeedType.INCIDENT);
        // first record: A6091 closed eastbound at Galafoot Bridge
        Situation s = list.get(0);
        assertEquals("RoadOrCarriagewayOrLaneManagement", s.getRecordType());
        assertEquals("roadClosed", s.getSubType());
        assertEquals(SituationKind.ROAD_CLOSED, s.getKind());
        assertEquals("Road closed", s.getTypeLabel());
        assertTrue(s.getComment().startsWith("The A6091 is closed Eastbound at Galafoot Bridge"));
        assertEquals("eastBound", s.getDirection());
        assertEquals(Integer.valueOf(0), s.getImpact().getOperationalLanes());
        assertEquals("mandatory", s.getComplianceOption());
        assertTrue(s.getRoads().contains("A6091"));

        LocationPoint to = s.getPoint("to");
        assertNotNull(to);
        assertEquals(55.598543, to.getLatitude(), 1e-9);
        assertEquals(-2.71775, to.getLongitude(), 1e-9);
        assertEquals("A6091 Melrose", to.getDisplayName());
        assertTrue(to.getRoads().contains("B6359")); // tpegIlcName2

        int queues = 0, closures = 0;
        for (Situation x : list)
        {
            if (x.getKind() == SituationKind.QUEUING_TRAFFIC) queues++;
            if (x.getKind() == SituationKind.ROAD_CLOSED) closures++;
        }
        assertEquals(5, queues);   // 5 AbnormalTraffic records in the file
        assertEquals(2, closures); // 2 roadClosed records
    }

    @Test
    public void roadworksDetailsMatchTheXml() throws Exception
    {
        String xml = load("roadworksContentExample.xml");
        List<Situation> list = new SituationParser().parse(xml, FeedType.ROADWORKS).situations;
        Situation s = list.get(0);
        assertEquals("D_159301_S", s.getSituationId());
        assertEquals("D_159301", s.getRecordId());
        assertEquals("MaintenanceWorks", s.getRecordType());
        assertEquals(SituationKind.ROADWORKS, s.getKind());
        assertEquals("longDelays", s.getImpact().getDelaysType());
        assertEquals(2, s.getImpact().getDelayLevel());
        assertEquals("M8 WB Woodside - A804 Phoenix Rd - Closure", s.getComment());
        assertTrue(s.getRoads().contains("M8"));
        assertEquals("M8 (A81 Underpass to Jct 18 Off Slip)", s.getPoint("from").getDisplayName());
        assertNotNull(s.getEndTime());
        assertEquals(DurationBand.LONG, s.getDurationBand()); // 2023-07-22 -> 2027-12-31

        // delay information present in as many records as <delaysType> elements in the XML
        int withDelays = 0;
        for (Situation x : list)
            if (!x.getImpact().getDelaysType().isEmpty()) withDelays++;
        assertEquals(count(xml, "<delaysType>"), withDelays);
    }

    @Test
    public void unknownElementsAndGarbageDoNotBreakParsing() throws Exception
    {
        String xml = "﻿garbage<?xml version=\"1.0\"?><d2LogicalModel><payloadPublication>"
                + "<situation id=\"X1\"><situationRecord id=\"R1\" version=\"1\">"
                + "<brandNewElement>something new</brandNewElement>"
                + "<generalPublicComment><comment><values><value lang=\"en\">Test  comment</value></values></comment></generalPublicComment>"
                + "<accidentType>collision</accidentType>"
                + "</situationRecord></situation></payloadPublication></d2LogicalModel>";
        List<Situation> list = new SituationParser().parse(xml, FeedType.INCIDENT).situations;
        assertEquals(1, list.size());
        assertEquals("Test comment", list.get(0).getComment());
        assertEquals("something new", list.get(0).getOtherValues().get("brandNewElement"));
        assertEquals("collision", list.get(0).getSubType());
        assertFalse(list.get(0).hasCoordinates());
    }

    @Test
    public void searchByRoadAndDistance() throws Exception
    {
        List<Situation> list = parse("unplannedContentExample1.xml", FeedType.INCIDENT);
        int m8 = 0;
        for (Situation s : list)
            if (s.matchesRoad(" m 8 ")) m8++;
        assertTrue(m8 > 0);
        // GCU campus (55.8668, -4.2502): the M8 Charing Cross/Kingston Bridge incidents are < 2 km away
        int near = 0;
        for (Situation s : list)
            if (s.distanceFromKm(55.8668, -4.2502) <= 2) near++;
        assertTrue(near > 0);
        for (Situation s : list)
            if (s.getRoads().contains("A6091")) // Melrose, in the Borders: far from Glasgow
                assertTrue(s.distanceFromKm(55.8668, -4.2502) > 50);
    }
}
