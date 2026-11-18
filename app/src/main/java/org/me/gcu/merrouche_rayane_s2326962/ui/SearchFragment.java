package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.me.gcu.merrouche_rayane_s2326962.R;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;

import java.util.List;

/**
 * Tab 2: search situations by road name, or by distance from the phone's (coarse) location or a
 * fixed location (GCU campus). Results can be opened one by one or shown all together on the map.
 */
public class SearchFragment extends Fragment implements SituationAdapter.OnSituationClickListener
{
    private static final String TAG = "SearchFragment";

    private TrafficViewModel viewModel;
    private AutoCompleteTextView roadInput;
    private RadioButton originMyLocation;
    private SeekBar radiusSeek;
    private TextView radiusText, resultsTitle;
    private View searchForm, resultsHeader, resultsEmpty;
    private SituationAdapter adapter;

    // Runtime permission request (coarse location is a "dangerous" permission)
    private final ActivityResultLauncher<String> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted)
                    searchFromDeviceLocation();
                else
                {
                    Toast.makeText(requireContext(), "Location not allowed: using GCU, Glasgow", Toast.LENGTH_LONG).show();
                    searchFromFixedLocation();
                }
            });

    public SearchFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_search, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(v, savedInstanceState);
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);

        searchForm = v.findViewById(R.id.searchForm);
        roadInput = v.findViewById(R.id.roadInput);
        originMyLocation = v.findViewById(R.id.originMyLocation);
        radiusSeek = v.findViewById(R.id.radiusSeek);
        radiusText = v.findViewById(R.id.radiusText);
        resultsHeader = v.findViewById(R.id.resultsHeader);
        resultsTitle = v.findViewById(R.id.resultsTitle);
        resultsEmpty = v.findViewById(R.id.resultsEmpty);

        RecyclerView list = v.findViewById(R.id.resultsList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new SituationAdapter(this);
        list.setAdapter(adapter);

        // --- road search
        v.findViewById(R.id.roadSearchButton).setOnClickListener(b -> searchRoad());
        roadInput.setOnEditorActionListener((tv, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER))
            {
                searchRoad();
                return true;
            }
            return false;
        });
        roadInput.setOnItemClickListener((parent, view, position, id) -> searchRoad());
        viewModel.getRoadNames().observe(getViewLifecycleOwner(), this::setRoadSuggestions);

        // --- distance search
        updateRadiusText();
        radiusSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { updateRadiusText(); }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        v.findViewById(R.id.nearbyButton).setOnClickListener(b -> searchNearby());

        // --- results
        v.findViewById(R.id.modifySearchButton).setOnClickListener(b -> setFormVisible(true));
        v.findViewById(R.id.resultsMapButton).setOnClickListener(b -> showResultsOnMap());
        viewModel.getSelected().observe(getViewLifecycleOwner(),
                s -> adapter.setSelectedKey(s == null ? null : s.getKey()));
        viewModel.getSearchResult().observe(getViewLifecycleOwner(), this::showResult);
        setFormVisible(viewModel.isSearchFormVisible());
    }

    private void setRoadSuggestions(List<String> roads)
    {
        ArrayAdapter<String> a = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_dropdown_item_1line, roads);
        roadInput.setAdapter(a);
    }

    private int radiusKm()
    {
        return radiusSeek.getProgress() + 1; // 1..100 km
    }

    private void updateRadiusText()
    {
        radiusText.setText("Within " + radiusKm() + " km");
    }

    private void setFormVisible(boolean visible)
    {
        viewModel.setSearchFormVisible(visible);
        TrafficViewModel.SearchResult r = viewModel.getSearchResult().getValue();
        // the form is always visible until there are results to show
        searchForm.setVisibility(visible || r == null ? View.VISIBLE : View.GONE);
        View modify = getView() == null ? null : getView().findViewById(R.id.modifySearchButton);
        if (modify != null)
            modify.setVisibility(searchForm.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
    }

    private void hideKeyboard()
    {
        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(roadInput.getWindowToken(), 0);
    }

    // ------------------------------------------------------------------ searches

    private void searchRoad()
    {
        String q = roadInput.getText().toString().trim();
        if (q.isEmpty())
        {
            roadInput.setError("Enter a road such as M8 or A90");
            return;
        }
        hideKeyboard();
        roadInput.dismissDropDown();
        viewModel.searchByRoad(q);
        setFormVisible(false);
    }

    private void searchNearby()
    {
        hideKeyboard();
        if (!originMyLocation.isChecked())
        {
            searchFromFixedLocation();
            return;
        }
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)
            searchFromDeviceLocation();
        else
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    private void searchFromFixedLocation()
    {
        viewModel.searchNearby(TrafficViewModel.FIXED_LAT, TrafficViewModel.FIXED_LON,
                TrafficViewModel.FIXED_LABEL, radiusKm());
        setFormVisible(false);
    }

    /** Coarse location from the phone (network/passive provider), using the Android LocationManager */
    private void searchFromDeviceLocation()
    {
        LocationManager lm = (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try
        {
            for (String provider : lm.getProviders(true))
            {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getTime() > best.getTime()))
                    best = l;
            }
            if (best != null)
            {
                useLocation(best);
                return;
            }
            // No recent location stored: ask for a fresh one (API 30+), otherwise use the fixed one
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
            {
                Toast.makeText(requireContext(), "Finding your location…", Toast.LENGTH_SHORT).show();
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null,
                        ContextCompat.getMainExecutor(requireContext()), location -> {
                            if (!isAdded()) return;
                            if (location != null) useLocation(location);
                            else fallbackToFixed();
                        });
                return;
            }
        }
        catch (SecurityException e)
        {
            Log.e(TAG, "Location permission missing", e);
        }
        fallbackToFixed();
    }

    private void useLocation(Location l)
    {
        viewModel.searchNearby(l.getLatitude(), l.getLongitude(), "your location", radiusKm());
        setFormVisible(false);
    }

    private void fallbackToFixed()
    {
        Toast.makeText(requireContext(), "Your location is not available: using GCU, Glasgow", Toast.LENGTH_LONG).show();
        searchFromFixedLocation();
    }

    // ------------------------------------------------------------------ results

    private void showResult(TrafficViewModel.SearchResult r)
    {
        if (r == null)
        {
            resultsHeader.setVisibility(View.GONE);
            resultsEmpty.setVisibility(View.GONE);
            adapter.setItems(null, null);
            return;
        }
        resultsHeader.setVisibility(View.VISIBLE);
        resultsTitle.setText(r.title + " · " + r.situations.size() + (r.situations.size() == 1 ? " result" : " results"));
        adapter.setItems(r.situations, r.distancesKm);
        resultsEmpty.setVisibility(r.situations.isEmpty() ? View.VISIBLE : View.GONE);
        setFormVisible(viewModel.isSearchFormVisible());
    }

    private void showResultsOnMap()
    {
        TrafficViewModel.SearchResult r = viewModel.getSearchResult().getValue();
        if (r == null || r.situations.isEmpty())
        {
            Toast.makeText(requireContext(), "No results to show on the map", Toast.LENGTH_SHORT).show();
            return;
        }
        viewModel.showOnMap(r.title, r.situations); // multiple user-selected situations
        viewModel.setCurrentTab(TrafficViewModel.TAB_MAP);
    }

    @Override
    public void onSituationClick(Situation situation)
    {
        viewModel.select(situation);
    }
}
