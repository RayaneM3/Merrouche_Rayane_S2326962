package org.me.gcu.merrouche_rayane_s2326962.model;

/** Distance calculations for "search by distance" (pure Java, so it can be unit tested) */
public final class GeoHelper
{
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoHelper() { }

    /** Great-circle (haversine) distance in km between two lat/long points */
    public static double distanceKm(double lat1, double lon1, double lat2, double lon2)
    {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Distance in km from point P to the segment A-B (a "from"/"to" linear location).
     * Uses a local flat projection around P, accurate enough for road segments in Scotland.
     */
    public static double distanceToSegmentKm(double pLat, double pLon,
                                             double aLat, double aLon, double bLat, double bLon)
    {
        double kmPerDegLat = 111.32;
        double kmPerDegLon = 111.32 * Math.cos(Math.toRadians(pLat));
        double ax = (aLon - pLon) * kmPerDegLon, ay = (aLat - pLat) * kmPerDegLat;
        double bx = (bLon - pLon) * kmPerDegLon, by = (bLat - pLat) * kmPerDegLat;
        double dx = bx - ax, dy = by - ay;
        double len2 = dx * dx + dy * dy;
        double t = len2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2));
        double cx = ax + t * dx, cy = ay + t * dy; // closest point of the segment (P is the origin)
        return Math.sqrt(cx * cx + cy * cy);
    }
}
