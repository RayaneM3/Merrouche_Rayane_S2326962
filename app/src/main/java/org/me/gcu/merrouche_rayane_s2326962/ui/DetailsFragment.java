package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import org.me.gcu.merrouche_rayane_s2326962.R;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Impact;
import org.me.gcu.merrouche_rayane_s2326962.model.LocationPoint;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.model.TextHelper;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;

/**
 * Details of the selected situation, exploiting all the information in the XML: impact, validity,
 * locations (names, road numbers, coordinates), status and record information, plus any other
 * field found in the feed. Shown on top of the list (portrait) or on the right (landscape).
 */
public class DetailsFragment extends Fragment
{
    private TrafficViewModel viewModel;
    private View placeholder, scroll;
    private ImageView icon;
    private TextView type, feed, comment, durationBadge, delayBadge;
    private LinearLayout sections;
    private Button closeButton;

    public DetailsFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_details, container, false);
        placeholder = v.findViewById(R.id.placeholder);
        scroll = v.findViewById(R.id.detailsScroll);
        icon = v.findViewById(R.id.detailIcon);
        type = v.findViewById(R.id.detailType);
        feed = v.findViewById(R.id.detailFeed);
        comment = v.findViewById(R.id.detailComment);
        durationBadge = v.findViewById(R.id.detailDurationBadge);
        delayBadge = v.findViewById(R.id.detailDelayBadge);
        sections = v.findViewById(R.id.detailSections);
        closeButton = v.findViewById(R.id.detailCloseButton);
        return v;
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(v, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);

        closeButton.setOnClickListener(b -> viewModel.clearSelection());
        v.findViewById(R.id.detailMapButton).setOnClickListener(b -> {
            Situation s = viewModel.getSelected().getValue();
            if (s != null)
                viewModel.showSelectedOnMap(s);
        });

        viewModel.getSelected().observe(getViewLifecycleOwner(), this::show);
    }

    private void show(Situation s)
    {
        if (s == null)
        {
            placeholder.setVisibility(View.VISIBLE);
            scroll.setVisibility(View.GONE);
            return;
        }
        placeholder.setVisibility(View.GONE);
        scroll.setVisibility(View.VISIBLE);
        scroll.scrollTo(0, 0);

        icon.setImageResource(SituationStyle.iconFor(s.getKind()));
        type.setText(s.getTypeLabel());
        String kind = s.getKind().getLabel();
        String feedLabel = s.getFeedType().getLabel();
        // avoid repeating the same word ("Roadworks · Roadworks")
        feed.setText(kind.equalsIgnoreCase(s.getTypeLabel()) || kind.equalsIgnoreCase(feedLabel)
                ? feedLabel + (s.getMainRoad().isEmpty() ? "" : " · " + s.getMainRoad())
                : feedLabel + " · " + kind);
        comment.setText(s.getComment());
        SituationStyle.bindDurationBadge(durationBadge, s, true);
        SituationStyle.bindDelayBadge(delayBadge, s);

        sections.removeAllViews();

        // ---- Where
        addSection("Where");
        addRow("Road(s)", String.join(", ", s.getRoads()));
        addRow("Direction", TextHelper.humanise(s.getDirection()));
        if (s.getFeedType() == FeedType.ROADWORKS)
        {
            String[] parts = TextHelper.splitWorksDescription(s.getComment());
            addRow("Location", parts[0]);
            addRow("Works", parts[1]);
        }
        for (String role : new String[] {"from", "to"})
        {
            LocationPoint p = s.getPoint(role);
            if (p != null)
                addRow(role.equals("from") ? "From" : "To", describePoint(p));
        }
        for (LocationPoint p : s.getLocations()) // any other kind of location point
            if (!p.getRole().equals("from") && !p.getRole().equals("to"))
                addRow(TextHelper.humanise(p.getRole()), describePoint(p));
        if (s.hasCoordinates())
        {
            double[] c = s.getCentre();
            double d = s.distanceFromKm(TrafficViewModel.FIXED_LAT, TrafficViewModel.FIXED_LON);
            addRow("From GCU", String.format(Locale.UK, "%.1f km (straight line)", d));
            addRow("Centre", String.format(Locale.UK, "%.5f, %.5f", c[0], c[1]));
        }
        addRow("Location type", join(TextHelper.humanise(s.getLocationGroupType()), TextHelper.humanise(s.getLinearLocationType())));

        // ---- When
        addSection("When");
        addRow("Start", TextHelper.formatDate(s.getStartTime()));
        if (s.getEndTime() != null)
            addRow("End", TextHelper.formatDate(s.getEndTime()));
        else if (s.getFeedType() == FeedType.INCIDENT)
            addRow("End", "Until further notice");
        if (s.getDuration() != null)
            addRow("Duration", TextHelper.formatDuration(s.getDuration()) + " · " + s.getDurationBand().getLabel());
        addRow("Validity", TextHelper.humanise(s.getValidityStatus()));

        // ---- Impact
        Impact impact = s.getImpact();
        addSection("Impact");
        if (impact.isEmpty())
            addRow("Impact", "No impact details given");
        else
        {
            if (impact.getOperationalLanes() != null)
                addRow("Lanes open", impact.getOperationalLanes() == 0 ? "None (road closed)" : String.valueOf(impact.getOperationalLanes()));
            if (impact.getLanesRestricted() != null)
                addRow("Lanes restricted", String.valueOf(impact.getLanesRestricted()));
            if (!impact.getDelaysType().isEmpty())
                addRow("Delays", TextHelper.humanise(impact.getDelaysType()));
            for (Map.Entry<String, String> e : impact.getOtherValues().entrySet())
                addRow(TextHelper.humanise(e.getKey()), TextHelper.humanise(e.getValue()));
        }

        // ---- Status
        addSection("Status");
        addRow("Situation", TextHelper.humanise(s.getRecordType()));
        addRow("Type", TextHelper.humanise(s.getSubType()));
        addRow("Probability", TextHelper.humanise(s.getProbability()));
        addRow("Compliance", TextHelper.humanise(s.getComplianceOption()));
        if (s.getFeedType() == FeedType.ROADWORKS)
            addRow("Urgent", s.isUrgentRoadworks() ? "Yes" : "No");
        addRow("Abbreviations", TextHelper.explainAbbreviations(s.getComment()));

        // ---- Other information (any field not handled above, i.e. new fields in the feed)
        if (!s.getOtherValues().isEmpty())
        {
            addSection("Other information");
            for (Map.Entry<String, String> e : s.getOtherValues().entrySet())
                addRow(TextHelper.humanise(e.getKey()), TextHelper.humanise(e.getValue()));
        }

        // ---- Record
        addSection("Record");
        addRow("Situation ID", s.getSituationId());
        addRow("Record ID", s.getRecordId() + (s.getRecordVersion().isEmpty() ? "" : " (version " + s.getRecordVersion() + ")"));
        addRow("Created", TextHelper.formatDate(s.getCreationTime()));
        addRow("Last updated", TextHelper.formatDate(s.getVersionTime()));
        addRow("Source", s.getSourceName());
    }

    private static String describePoint(LocationPoint p)
    {
        StringBuilder sb = new StringBuilder(p.getDisplayName());
        String junction = p.getDescriptors().get("junctionName");
        if (junction != null && !sb.toString().contains(junction))
            sb.append(" (").append(junction).append(")");
        if (!p.getRoads().isEmpty())
            sb.append("\nRoads: ").append(String.join(", ", p.getRoads()));
        if (p.hasCoordinates())
            sb.append(String.format(Locale.UK, "\n%.5f, %.5f", p.getLatitude(), p.getLongitude()));
        return sb.toString().trim();
    }

    private static String join(String a, String b)
    {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    private void addSection(String title)
    {
        TextView t = new TextView(requireContext(), null, 0, R.style.SectionTitle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (16 * getResources().getDisplayMetrics().density);
        t.setLayoutParams(lp);
        t.setText(title);
        sections.addView(t);
    }

    /** Adds a "label: value" row; empty values are skipped so only available data is shown */
    private void addRow(String label, String value)
    {
        if (value == null || value.trim().isEmpty())
            return;
        View row = getLayoutInflater().inflate(R.layout.item_detail_row, sections, false);
        ((TextView) row.findViewById(R.id.label)).setText(label);
        ((TextView) row.findViewById(R.id.value)).setText(value);
        sections.addView(row);
    }

    /** MainActivity hides the Close button in landscape, where the pane is always visible */
    public void setCloseButtonVisible(boolean visible)
    {
        if (closeButton != null)
            closeButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
