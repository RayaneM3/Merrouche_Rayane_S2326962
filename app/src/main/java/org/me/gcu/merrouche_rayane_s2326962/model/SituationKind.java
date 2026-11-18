package org.me.gcu.merrouche_rayane_s2326962.model;

import java.util.Locale;

/**
 * Category used to choose the icon (and label) for a situation.
 * Worked out from the situationRecord xsi:type and its "...Type" sub-type element.
 */
public enum SituationKind
{
    ROAD_CLOSED("Road closed"),
    LANE_RESTRICTION("Lane restriction"),
    QUEUING_TRAFFIC("Queuing traffic"),
    ACCIDENT("Accident"),
    OBSTRUCTION("Obstruction"),
    WEATHER("Weather / road conditions"),
    ROADWORKS("Roadworks"),
    URGENT_ROADWORKS("Urgent roadworks"),
    OTHER("Other event");

    private final String label;

    SituationKind(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    /**
     * @param recordType xsi:type of the situationRecord, i.e. "AbnormalTraffic"
     * @param subType    value of its sub-type element, i.e. "queuingTraffic" (may be empty)
     * @param urgent     urgentRoadworks flag
     * @param lanesAffected true if the impact says that some lanes are restricted
     */
    public static SituationKind classify(String recordType, String subType, boolean urgent, boolean lanesAffected)
    {
        String type = recordType == null ? "" : recordType.toLowerCase(Locale.UK);
        String sub = subType == null ? "" : subType.toLowerCase(Locale.UK);

        if (type.contains("maintenanceworks") || type.contains("constructionworks") || type.contains("roadworks"))
            return urgent ? URGENT_ROADWORKS : ROADWORKS;
        if (type.contains("accident"))
            return ACCIDENT;
        if (type.contains("abnormaltraffic"))
            return QUEUING_TRAFFIC;
        if (type.contains("poorenvironment") || type.contains("weather") || type.contains("roadconditions")
                || sub.contains("flood") || sub.contains("snow") || sub.contains("ice") || sub.contains("fog"))
            return WEATHER;
        if (type.contains("obstruction"))
            return OBSTRUCTION;
        if (type.contains("roadorcarriagewayorlanemanagement") || type.contains("networkmanagement"))
        {
            // roadClosed, carriagewayClosures... -> closed; laneClosures, contraflow, etc. -> restriction
            if (sub.contains("closed") || (sub.contains("closure") && !sub.contains("lane")))
                return ROAD_CLOSED;
            return LANE_RESTRICTION;
        }
        return OTHER;
    }
}
