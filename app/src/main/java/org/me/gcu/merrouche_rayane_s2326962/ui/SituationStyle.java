package org.me.gcu.merrouche_rayane_s2326962.ui;

import java.util.Locale;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.me.gcu.merrouche_rayane_s2326962.R;
import org.me.gcu.merrouche_rayane_s2326962.model.DurationBand;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.model.SituationKind;
import org.me.gcu.merrouche_rayane_s2326962.model.TextHelper;

import java.util.EnumMap;
import java.util.Map;

/** Icons and colour coding shared by the list, the details screen and the map */
public final class SituationStyle
{
    private static final Map<SituationKind, BitmapDescriptor> markerCache = new EnumMap<>(SituationKind.class);

    private SituationStyle() { }

    @DrawableRes
    public static int iconFor(SituationKind kind)
    {
        switch (kind)
        {
            case ROAD_CLOSED: return R.drawable.ic_kind_road_closed;
            case LANE_RESTRICTION: return R.drawable.ic_kind_lane_restriction;
            case QUEUING_TRAFFIC: return R.drawable.ic_kind_queue;
            case ACCIDENT: return R.drawable.ic_kind_accident;
            case OBSTRUCTION: return R.drawable.ic_kind_obstruction;
            case WEATHER: return R.drawable.ic_kind_weather;
            case ROADWORKS: return R.drawable.ic_kind_roadworks;
            case URGENT_ROADWORKS: return R.drawable.ic_kind_urgent_roadworks;
            default: return R.drawable.ic_kind_other;
        }
    }

    /** Colour of the list strip / map line: roadworks by duration, unplanned events purple */
    @ColorRes
    public static int stripColour(Situation s)
    {
        if (s.getFeedType() == FeedType.INCIDENT)
            return R.color.incident_strip;
        return durationColour(s.getDurationBand());
    }

    @ColorRes
    public static int durationColour(DurationBand band)
    {
        switch (band)
        {
            case SHORT: return R.color.duration_short;
            case MEDIUM: return R.color.duration_medium;
            case LONG: return R.color.duration_long;
            default: return R.color.duration_unknown;
        }
    }

    @ColorRes
    public static int delayColour(int level)
    {
        if (level >= 3) return R.color.delay_3;
        if (level == 2) return R.color.delay_2;
        return R.color.delay_1;
    }

    /** Fills a duration badge ("Long: 4 years 5 months") or hides it (unplanned events) */
    public static void bindDurationBadge(TextView badge, Situation s, boolean longText)
    {
        if (s.getFeedType() != FeedType.ROADWORKS || s.getDurationBand() == DurationBand.UNKNOWN)
        {
            badge.setVisibility(android.view.View.GONE);
            return;
        }
        String band = s.getDurationBand().name().charAt(0) + s.getDurationBand().name().substring(1).toLowerCase(Locale.UK);
        String text = longText ? band + " works: " + TextHelper.formatDuration(s.getDuration())
                : band + " · " + TextHelper.formatDuration(s.getDuration());
        badge.setText(text);
        badge.setBackgroundColor(ContextCompat.getColor(badge.getContext(), durationColour(s.getDurationBand())));
        badge.setVisibility(android.view.View.VISIBLE);
    }

    /** Fills a delay badge ("Long delays") or hides it when the feed gives no delay level */
    public static void bindDelayBadge(TextView badge, Situation s)
    {
        int level = s.getImpact().getDelayLevel();
        if (level == 0)
        {
            badge.setVisibility(android.view.View.GONE);
            return;
        }
        badge.setText(TextHelper.humanise(s.getImpact().getDelaysType()));
        badge.setBackgroundColor(ContextCompat.getColor(badge.getContext(), delayColour(level)));
        badge.setVisibility(android.view.View.VISIBLE);
    }

    /**
     * Map marker: the situation icon on a white disc with a coloured ring, so it stands out on
     * any map type. Must be called after the map is ready (BitmapDescriptorFactory initialised).
     */
    public static BitmapDescriptor markerFor(Context context, SituationKind kind)
    {
        BitmapDescriptor cached = markerCache.get(kind);
        if (cached != null)
            return cached;
        float density = context.getResources().getDisplayMetrics().density;
        int size = (int) (36 * density);
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFFFFFF);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - density, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2 * density);
        paint.setColor(0xFF424242);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2 * density, paint);
        Drawable icon = ContextCompat.getDrawable(context, iconFor(kind));
        if (icon != null)
        {
            int pad = (int) (6 * density);
            icon.setBounds(pad, pad, size - pad, size - pad);
            icon.draw(canvas);
        }
        BitmapDescriptor d = BitmapDescriptorFactory.fromBitmap(bmp);
        markerCache.put(kind, d);
        return d;
    }
}
