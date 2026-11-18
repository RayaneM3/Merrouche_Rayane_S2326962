package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;

import org.me.gcu.merrouche_rayane_s2326962.model.LocationPoint;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.R;

import java.util.List;

/**
 * Tab 3: Google map with all situations, or the ones chosen by the user (search results, or one
 * situation from the details screen). Each situation gets a marker with its type icon at the
 * middle of the affected stretch, and a coloured line between its "from" and "to" points.
 */
public class SituationMapFragment extends Fragment implements OnMapReadyCallback
{
    // Central Scotland, used when there is nothing to show yet
    private static final LatLng SCOTLAND = new LatLng(56.4, -4.2);

    private TrafficViewModel viewModel;
    private GoogleMap map;
    private TextView title;
    private Button showAllButton;
    private boolean cameraPlaced = false;
    private TrafficViewModel.MapRequest lastRequest;

    public SituationMapFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(v, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);
        title = v.findViewById(R.id.mapTitle);
        showAllButton = v.findViewById(R.id.mapShowAllButton);
        showAllButton.setOnClickListener(b -> {
            cameraPlaced = false;
            viewModel.showAllOnMap();
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager().findFragmentById(R.id.googleMap);
        if (mapFragment != null)
            mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap)
    {
        if (getView() == null)
            return; // the fragment's view was destroyed before the map finished loading
        map = googleMap;
        map.getUiSettings().setZoomControlsEnabled(true);
        map.getUiSettings().setMapToolbarEnabled(false);
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(SCOTLAND, 6));
        // Tapping a marker's info window opens the details of that situation
        map.setOnInfoWindowClickListener(marker -> {
            Object key = marker.getTag();
            if (key instanceof String)
                viewModel.selectByKey((String) key);
        });
        // A new request (i.e. search results) re-frames the camera; a periodic data update doesn't
        viewModel.getMapRequest().observe(getViewLifecycleOwner(), r -> {
            if (r != lastRequest)
                cameraPlaced = false;
            lastRequest = r;
        });
        viewModel.getMapSituations().observe(getViewLifecycleOwner(), this::draw);
    }

    private void draw(List<Situation> situations)
    {
        if (map == null)
            return;
        map.clear();
        TrafficViewModel.MapRequest r = viewModel.getMapRequest().getValue();
        boolean all = r == null || r.keys == null;
        title.setText((r == null ? "All situations" : r.title) + " · " + situations.size() + " on map");
        showAllButton.setVisibility(all ? View.GONE : View.VISIBLE);

        LatLngBounds.Builder bounds = new LatLngBounds.Builder();
        int points = 0;
        Marker single = null;
        for (Situation s : situations)
        {
            double[] c = s.getCentre();
            if (c == null)
                continue; // no coordinates in the feed for this one
            int colour = ContextCompat.getColor(requireContext(), SituationStyle.stripColour(s));

            // line along the affected stretch (from -> to)
            List<LocationPoint> pts = s.getPointsWithCoordinates();
            if (pts.size() >= 2)
            {
                PolylineOptions line = new PolylineOptions().color(colour).width(10f);
                for (LocationPoint p : pts)
                {
                    LatLng ll = new LatLng(p.getLatitude(), p.getLongitude());
                    line.add(ll);
                    bounds.include(ll);
                    points++;
                }
                map.addPolyline(line);
            }

            LatLng centre = new LatLng(c[0], c[1]);
            bounds.include(centre);
            points++;
            String road = s.getMainRoad();
            Marker m = map.addMarker(new MarkerOptions()
                    .position(centre)
                    .title(s.getTypeLabel() + (road.isEmpty() ? "" : " · " + road))
                    .snippet(shorten(s.getComment()) + "  (tap for details)")
                    .icon(SituationStyle.markerFor(requireContext(), s.getKind()))
                    .anchor(0.5f, 0.5f));
            if (m != null)
            {
                m.setTag(s.getKey());
                single = m;
            }
        }

        if (cameraPlaced || points == 0)
            return;
        cameraPlaced = true;
        if (situations.size() == 1 && single != null)
        {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(single.getPosition(), 13));
            single.showInfoWindow();
        }
        else
        {
            int padding = (int) (48 * getResources().getDisplayMetrics().density);
            View mapView = getView();
            if (mapView != null && mapView.getWidth() > 0)
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),
                        mapView.getWidth(), mapView.getHeight(), padding));
            else
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(SCOTLAND, 6));
        }
    }

    private static String shorten(String text)
    {
        if (text == null)
            return "";
        String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 90 ? oneLine.substring(0, 87) + "…" : oneLine;
    }
}
