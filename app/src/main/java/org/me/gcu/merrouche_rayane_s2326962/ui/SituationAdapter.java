package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import org.me.gcu.merrouche_rayane_s2326962.R;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RecyclerView adapter for the situations list and the search results.
 * Each row shows the icon of the situation type, its type and the generalPublicComment,
 * with the roadworks duration colour coding and the delay level.
 */
public class SituationAdapter extends RecyclerView.Adapter<SituationAdapter.ViewHolder>
{
    /** Callback to whoever owns the list (the fragment), as seen in the fragment comms lectures */
    public interface OnSituationClickListener
    {
        void onSituationClick(Situation situation);
    }

    private final OnSituationClickListener listener;
    private List<Situation> items = new ArrayList<>();
    private Map<String, Double> distances; // only for distance searches
    private String selectedKey;

    public SituationAdapter(OnSituationClickListener listener)
    {
        this.listener = listener;
    }

    public void setItems(List<Situation> newItems, Map<String, Double> distancesKm)
    {
        items = newItems == null ? new ArrayList<>() : newItems;
        distances = distancesKm;
        notifyDataSetChanged();
    }

    /** Highlights the row currently shown in the details pane (landscape master-detail) */
    public void setSelectedKey(String key)
    {
        selectedKey = key;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_situation, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position)
    {
        Situation s = items.get(position);
        h.icon.setImageResource(SituationStyle.iconFor(s.getKind()));
        h.strip.setBackgroundColor(ContextCompat.getColor(h.itemView.getContext(), SituationStyle.stripColour(s)));

        String road = s.getMainRoad();
        String type = s.getTypeLabel() + (road.isEmpty() ? "" : " · " + road);
        h.type.setText(type);
        h.comment.setText(s.getComment());

        if (distances != null && distances.containsKey(s.getKey()))
        {
            h.distance.setText(String.format(Locale.UK, "%.1f km", distances.get(s.getKey())));
            h.distance.setVisibility(View.VISIBLE);
        }
        else
            h.distance.setVisibility(View.GONE);

        SituationStyle.bindDurationBadge(h.durationBadge, s, false);
        SituationStyle.bindDelayBadge(h.delayBadge, s);

        boolean isSelected = s.getKey().equals(selectedKey);
        h.itemView.setBackgroundColor(isSelected
                ? ContextCompat.getColor(h.itemView.getContext(), R.color.selected_row) : 0);

        h.itemView.setContentDescription((s.getFeedType() == FeedType.INCIDENT ? "Unplanned event: " : "Roadworks: ")
                + type + ". " + s.getComment());
        h.itemView.setOnClickListener(v -> listener.onSituationClick(s));
    }

    @Override
    public int getItemCount()
    {
        return items.size();
    }

    /** Holds the widgets of one row, bound once and then reused (recycled) */
    static class ViewHolder extends RecyclerView.ViewHolder
    {
        final View strip;
        final ImageView icon;
        final TextView type, comment, distance, durationBadge, delayBadge;

        ViewHolder(View v)
        {
            super(v);
            strip = v.findViewById(R.id.colourStrip);
            icon = v.findViewById(R.id.kindIcon);
            type = v.findViewById(R.id.typeText);
            comment = v.findViewById(R.id.commentText);
            distance = v.findViewById(R.id.distanceText);
            durationBadge = v.findViewById(R.id.durationBadge);
            delayBadge = v.findViewById(R.id.delayBadge);
        }
    }
}
