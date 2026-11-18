package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import org.me.gcu.merrouche_rayane_s2326962.data.Credentials;
import org.me.gcu.merrouche_rayane_s2326962.data.LoadState;
import org.me.gcu.merrouche_rayane_s2326962.data.TrafficRepository;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;
import org.me.gcu.merrouche_rayane_s2326962.model.TextHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * ViewModel (MVVM): the single source of UI state, shared by MainActivity and all Fragments.
 * It survives rotation, so the data, the selected situation, the search results and the map
 * content are kept when the layout changes between portrait and landscape.
 */
public class TrafficViewModel extends AndroidViewModel
{
    public enum ListFilter { ALL, INCIDENTS, ROADWORKS }

    public static final int TAB_LIST = 0;
    public static final int TAB_SEARCH = 1;
    public static final int TAB_MAP = 2;

    // Fixed location used when the phone location is not available/allowed: GCU campus, Glasgow
    public static final double FIXED_LAT = 55.8668;
    public static final double FIXED_LON = -4.2502;
    public static final String FIXED_LABEL = "GCU, Glasgow";

    private static final String PREFS = "settings";
    private static final String PREF_INTERVAL = "updateIntervalMinutes";

    private final TrafficRepository repository;
    private final SharedPreferences prefs;

    private final MutableLiveData<ListFilter> listFilter = new MutableLiveData<>(ListFilter.ALL);
    private final MediatorLiveData<List<Situation>> filteredSituations = new MediatorLiveData<>();
    private final MutableLiveData<String> selectedKey = new MutableLiveData<>(null);
    private final MediatorLiveData<Situation> selected = new MediatorLiveData<>();
    private final MutableLiveData<SearchResult> searchResult = new MutableLiveData<>(null);
    private final MutableLiveData<MapRequest> mapRequest = new MutableLiveData<>(MapRequest.all());
    private final MediatorLiveData<List<Situation>> mapSituations = new MediatorLiveData<>();
    private final MediatorLiveData<List<String>> roadNames = new MediatorLiveData<>();
    private final MutableLiveData<Integer> currentTab = new MutableLiveData<>(TAB_LIST);
    private final MutableLiveData<Long> updateInterval = new MutableLiveData<>();
    private boolean searchFormVisible = true;

    public TrafficViewModel(@NonNull Application application)
    {
        super(application);
        prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        repository = new TrafficRepository(application, Credentials.fromManifest(application));

        LiveData<List<Situation>> all = repository.getSituations();
        filteredSituations.addSource(all, l -> filteredSituations.setValue(applyFilter()));
        filteredSituations.addSource(listFilter, f -> filteredSituations.setValue(applyFilter()));
        selected.addSource(all, l -> selected.setValue(findByKey(selectedKey.getValue())));
        selected.addSource(selectedKey, k -> selected.setValue(findByKey(k)));
        mapSituations.addSource(all, l -> mapSituations.setValue(resolveMap()));
        mapSituations.addSource(mapRequest, r -> mapSituations.setValue(resolveMap()));
        roadNames.addSource(all, l -> roadNames.setValue(collectRoads(l)));

        // Data is loaded at start-up and then automatically at the saved interval (default 1 hour)
        long minutes = prefs.getLong(PREF_INTERVAL, 60);
        updateInterval.setValue(minutes);
        repository.startAutoUpdate(minutes);
    }

    @Override
    protected void onCleared()
    {
        super.onCleared();
        repository.shutdown(); // stop the background threads when the app is closed
    }

    // ------------------------------------------------------------------ data & updates

    public LiveData<List<Situation>> getAllSituations() { return repository.getSituations(); }

    public LiveData<LoadState> getLoadState() { return repository.getState(); }

    public void refreshNow() { repository.refreshNow(); }

    public LiveData<Long> getUpdateInterval() { return updateInterval; }

    public void setUpdateInterval(long minutes)
    {
        prefs.edit().putLong(PREF_INTERVAL, minutes).apply();
        updateInterval.setValue(minutes);
        repository.startAutoUpdate(minutes);
    }

    // ------------------------------------------------------------------ list

    public LiveData<List<Situation>> getFilteredSituations() { return filteredSituations; }

    public LiveData<ListFilter> getListFilter() { return listFilter; }

    public void setListFilter(ListFilter f) { listFilter.setValue(f); }

    private List<Situation> applyFilter()
    {
        List<Situation> all = repository.getSituations().getValue();
        ListFilter f = listFilter.getValue();
        List<Situation> out = new ArrayList<>();
        if (all == null)
            return out;
        for (Situation s : all)
        {
            if (f == ListFilter.INCIDENTS && s.getFeedType() != FeedType.INCIDENT) continue;
            if (f == ListFilter.ROADWORKS && s.getFeedType() != FeedType.ROADWORKS) continue;
            out.add(s);
        }
        return out;
    }

    // ------------------------------------------------------------------ selection / details

    public LiveData<Situation> getSelected() { return selected; }

    public String getSelectedKey() { return selectedKey.getValue(); }

    public void select(Situation s) { selectedKey.setValue(s == null ? null : s.getKey()); }

    public void selectByKey(String key) { selectedKey.setValue(key); }

    public void clearSelection() { selectedKey.setValue(null); }

    private Situation findByKey(String key)
    {
        List<Situation> all = repository.getSituations().getValue();
        if (key == null || all == null)
            return null;
        for (Situation s : all)
            if (s.getKey().equals(key))
                return s;
        return null; // no longer in the feed after an update
    }

    // ------------------------------------------------------------------ search

    public LiveData<SearchResult> getSearchResult() { return searchResult; }

    public LiveData<List<String>> getRoadNames() { return roadNames; }

    public boolean isSearchFormVisible() { return searchFormVisible; }

    public void setSearchFormVisible(boolean visible) { searchFormVisible = visible; }

    /** Search by road name/number, i.e. "M8", "a74(m)" or a place such as "Kingston Bridge" */
    public void searchByRoad(String query)
    {
        List<Situation> all = repository.getSituations().getValue();
        List<Situation> found = new ArrayList<>();
        if (all != null)
            for (Situation s : all)
                if (s.matchesRoad(query))
                    found.add(s);
        searchResult.setValue(new SearchResult("Road: " + query.trim(), found, null));
    }

    /** Search situations within radiusKm of (lat, lon), nearest first */
    public void searchNearby(double lat, double lon, String placeLabel, int radiusKm)
    {
        List<Situation> all = repository.getSituations().getValue();
        final Map<String, Double> distances = new HashMap<>();
        List<Situation> found = new ArrayList<>();
        if (all != null)
            for (Situation s : all)
            {
                double d = s.distanceFromKm(lat, lon);
                if (d <= radiusKm)
                {
                    found.add(s);
                    distances.put(s.getKey(), d);
                }
            }
        Collections.sort(found, (a, b) -> Double.compare(distances.get(a.getKey()), distances.get(b.getKey())));
        String title = "Within " + radiusKm + " km of " + placeLabel;
        searchResult.setValue(new SearchResult(title, found, distances, lat, lon));
    }

    private static List<String> collectRoads(List<Situation> all)
    {
        TreeSet<String> roads = new TreeSet<>((a, b) -> {
            // natural order: M8 < M9 < M74 (letter, then number)
            String na = TextHelper.normaliseRoad(a), nb = TextHelper.normaliseRoad(b);
            int c = Character.compare(na.charAt(0), nb.charAt(0));
            if (c != 0) return c;
            return Integer.compare(number(na), number(nb)) != 0 ? Integer.compare(number(na), number(nb)) : a.compareTo(b);
        });
        if (all != null)
            for (Situation s : all)
                for (String r : s.getRoads())
                    if (!TextHelper.normaliseRoad(r).isEmpty())
                        roads.add(r);
        return new ArrayList<>(roads);
    }

    private static int number(String road)
    {
        String digits = road.replaceAll("\\D", "");
        try
        {
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    // ------------------------------------------------------------------ map

    public LiveData<List<Situation>> getMapSituations() { return mapSituations; }

    public LiveData<MapRequest> getMapRequest() { return mapRequest; }

    public void showAllOnMap() { mapRequest.setValue(MapRequest.all()); }

    public void showOnMap(String title, List<Situation> list)
    {
        List<String> keys = new ArrayList<>();
        for (Situation s : list)
            keys.add(s.getKey());
        mapRequest.setValue(new MapRequest(title, keys));
    }

    private List<Situation> resolveMap()
    {
        List<Situation> all = repository.getSituations().getValue();
        MapRequest r = mapRequest.getValue();
        if (all == null)
            return new ArrayList<>();
        if (r == null || r.keys == null)
            return all;
        List<Situation> out = new ArrayList<>();
        for (Situation s : all)
            if (r.keys.contains(s.getKey()))
                out.add(s);
        return out;
    }

    // ------------------------------------------------------------------ navigation

    public LiveData<Integer> getCurrentTab() { return currentTab; }

    public void setCurrentTab(int tab)
    {
        Integer cur = currentTab.getValue();
        if (cur == null || cur != tab)
            currentTab.setValue(tab);
    }

    // true while switching to the map from the details screen ("Show on map"): the selection
    // is kept so that, in landscape, the map and the details are shown side by side
    private boolean keepSelectionOnTabChange = false;

    public void showSelectedOnMap(Situation s)
    {
        showOnMap(s.getTypeLabel() + (s.getMainRoad().isEmpty() ? "" : " · " + s.getMainRoad()),
                java.util.Collections.singletonList(s));
        keepSelectionOnTabChange = true;
        setCurrentTab(TAB_MAP);
    }

    /** Read (and reset) by MainActivity when the tab changes */
    public boolean consumeKeepSelection()
    {
        boolean keep = keepSelectionOnTabChange;
        keepSelectionOnTabChange = false;
        return keep;
    }

    // ------------------------------------------------------------------ small state classes

    /** Result of a road or distance search */
    public static class SearchResult
    {
        public final String title;
        public final List<Situation> situations;
        public final Map<String, Double> distancesKm; // null for road searches
        public final double originLat, originLon;

        SearchResult(String title, List<Situation> situations, Map<String, Double> distancesKm)
        {
            this(title, situations, distancesKm, Double.NaN, Double.NaN);
        }

        SearchResult(String title, List<Situation> situations, Map<String, Double> distancesKm,
                     double originLat, double originLon)
        {
            this.title = title;
            this.situations = situations;
            this.distancesKm = distancesKm;
            this.originLat = originLat;
            this.originLon = originLon;
        }
    }

    /** What the map should display: everything (keys == null) or a chosen set of situations */
    public static class MapRequest
    {
        public final String title;
        public final List<String> keys;

        MapRequest(String title, List<String> keys)
        {
            this.title = title;
            this.keys = keys;
        }

        static MapRequest all()
        {
            return new MapRequest("All situations", null);
        }
    }
}
