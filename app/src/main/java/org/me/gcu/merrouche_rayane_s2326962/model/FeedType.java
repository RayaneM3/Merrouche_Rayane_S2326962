package org.me.gcu.merrouche_rayane_s2326962.model;

/** The two Traffic Scotland DATEX II publications used by the app */
public enum FeedType
{
    INCIDENT("Unplanned event", "UnplannedEvents"),
    ROADWORKS("Roadworks", "Roadworks");

    private final String label;
    private final String publication;

    FeedType(String label, String publication)
    {
        this.label = label;
        this.publication = publication;
    }

    public String getLabel()
    {
        return label;
    }

    /** Endpoint for this publication (the URL format given in the coursework brief) */
    public String getUrl()
    {
        return "https://datex2.trafficscotland.org/rest/2.3/publications/" + publication + "/Content.xml";
    }
}
