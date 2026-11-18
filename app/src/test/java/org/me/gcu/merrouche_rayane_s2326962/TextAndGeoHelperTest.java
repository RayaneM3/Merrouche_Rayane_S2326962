package org.me.gcu.merrouche_rayane_s2326962;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.me.gcu.merrouche_rayane_s2326962.model.DurationBand;
import org.me.gcu.merrouche_rayane_s2326962.model.GeoHelper;
import org.me.gcu.merrouche_rayane_s2326962.model.TextHelper;

import java.time.Duration;
import java.util.Set;

public class TextAndGeoHelperTest
{
    @Test
    public void roadNumbersAreExtractedFromText()
    {
        Set<String> roads = TextHelper.extractRoads("The A74(M) junction 19 and the M8 and B957; a 2 lane road");
        assertTrue(roads.contains("A74(M)"));
        assertTrue(roads.contains("M8"));
        assertTrue(roads.contains("B957"));
        assertEquals(3, roads.size()); // "a 2" is not a road
    }

    @Test
    public void userRoadInputIsNormalised()
    {
        assertEquals("A74M", TextHelper.normaliseRoad("a74 (m)"));
        assertEquals("M8", TextHelper.normaliseRoad(" m 8 "));
    }

    @Test
    public void codesAreHumanised()
    {
        assertEquals("Very long delays", TextHelper.humanise("veryLongDelays"));
        assertEquals("Eastbound", TextHelper.humanise("eastBound"));
        assertEquals("Both directions", TextHelper.humanise("bothWays"));
        assertEquals("Road closed", TextHelper.humanise("roadClosed"));
    }

    @Test
    public void worksDescriptionIsSplit()
    {
        assertArrayEquals(new String[] {"M8 WB JCT 15 On Slip Stirling Rd", "Slip Closure"},
                TextHelper.splitWorksDescription("M8 WB JCT 15 On Slip Stirling Rd - Slip Closure"));
        assertTrue(TextHelper.explainAbbreviations("A82 TTL's").contains("temporary traffic lights"));
    }

    @Test
    public void datesAndDurations()
    {
        assertNull(TextHelper.parseDate("not a date"));
        assertEquals(2026, TextHelper.parseDate("2026-08-31T09:58:26+01:00").getYear());
        assertEquals("3 days 4 hours", TextHelper.formatDuration(Duration.ofHours(76)));
        assertEquals("2 years 11 months", TextHelper.formatDuration(Duration.ofDays(365 * 2 + 362)));
        assertEquals("1 year", TextHelper.formatDuration(Duration.ofDays(370)));
        assertEquals(DurationBand.SHORT, DurationBand.fromHours(24 * 7));
        assertEquals(DurationBand.MEDIUM, DurationBand.fromHours(24 * 8));
        assertEquals(DurationBand.LONG, DurationBand.fromHours(24 * 91));
    }

    @Test
    public void distances()
    {
        // Glasgow (GCU) to Edinburgh (Waverley) is ~67 km in a straight line
        double d = GeoHelper.distanceKm(55.8668, -4.2502, 55.9520, -3.1890);
        assertEquals(67, d, 2);
        // a point on the segment has distance ~0
        assertEquals(0, GeoHelper.distanceToSegmentKm(55.5, -4.0, 55.0, -4.0, 56.0, -4.0), 0.01);
    }
}
