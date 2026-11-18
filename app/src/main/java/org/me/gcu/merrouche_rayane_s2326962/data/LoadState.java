package org.me.gcu.merrouche_rayane_s2326962.data;

import java.time.ZonedDateTime;

/** Immutable description of the data loading status, observed by the UI (status banner) */
public class LoadState
{
    public enum Status
    {
        LOADING,        // download/parse in progress
        UP_TO_DATE,     // fresh data downloaded
        OFFLINE_CACHED, // no connection: showing the most recent saved data
        ERROR_CACHED,   // download failed (server/credentials): showing saved data
        NO_DATA         // nothing downloaded and nothing saved
    }

    public final Status status;
    public final String message;            // extra detail (error message) or ""
    public final ZonedDateTime dataTime;    // when the displayed data was downloaded (null if none)
    public final ZonedDateTime nextUpdate;  // when the next automatic update will run (null if unknown)
    public final int incidentCount;
    public final int roadworksCount;

    public LoadState(Status status, String message, ZonedDateTime dataTime, ZonedDateTime nextUpdate,
                     int incidentCount, int roadworksCount)
    {
        this.status = status;
        this.message = message == null ? "" : message;
        this.dataTime = dataTime;
        this.nextUpdate = nextUpdate;
        this.incidentCount = incidentCount;
        this.roadworksCount = roadworksCount;
    }

    public LoadState withStatus(Status newStatus, String newMessage)
    {
        return new LoadState(newStatus, newMessage, dataTime, nextUpdate, incidentCount, roadworksCount);
    }

    public LoadState withNextUpdate(ZonedDateTime next)
    {
        return new LoadState(status, message, dataTime, next, incidentCount, roadworksCount);
    }

    public static LoadState initial()
    {
        return new LoadState(Status.LOADING, "", null, null, 0, 0);
    }
}
