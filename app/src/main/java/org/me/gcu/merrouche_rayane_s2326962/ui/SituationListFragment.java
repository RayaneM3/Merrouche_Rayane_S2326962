package org.me.gcu.merrouche_rayane_s2326962.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;

import org.me.gcu.merrouche_rayane_s2326962.R;
import org.me.gcu.merrouche_rayane_s2326962.data.LoadState;
import org.me.gcu.merrouche_rayane_s2326962.model.FeedType;
import org.me.gcu.merrouche_rayane_s2326962.model.Situation;

import java.util.List;

/** Tab 1: summary list of all current situations (type + generalPublicComment) */
public class SituationListFragment extends Fragment implements SituationAdapter.OnSituationClickListener
{
    private TrafficViewModel viewModel;
    private SituationAdapter adapter;
    private TextView countText, emptyText;
    private View legend;

    public SituationListFragment()
    {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState)
    {
        super.onViewCreated(v, savedInstanceState);
        // Shared ViewModel: same instance as MainActivity and the other fragments
        viewModel = new ViewModelProvider(requireActivity()).get(TrafficViewModel.class);

        countText = v.findViewById(R.id.countText);
        emptyText = v.findViewById(R.id.emptyText);
        legend = v.findViewById(R.id.legend);

        RecyclerView list = v.findViewById(R.id.situationList);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.addItemDecoration(new DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL));
        adapter = new SituationAdapter(this);
        list.setAdapter(adapter);

        MaterialButtonToggleGroup filterGroup = v.findViewById(R.id.filterGroup);
        TrafficViewModel.ListFilter f = viewModel.getListFilter().getValue();
        filterGroup.check(f == TrafficViewModel.ListFilter.INCIDENTS ? R.id.filterIncidents
                : f == TrafficViewModel.ListFilter.ROADWORKS ? R.id.filterRoadworks : R.id.filterAll);
        filterGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.filterIncidents)
                viewModel.setListFilter(TrafficViewModel.ListFilter.INCIDENTS);
            else if (checkedId == R.id.filterRoadworks)
                viewModel.setListFilter(TrafficViewModel.ListFilter.ROADWORKS);
            else
                viewModel.setListFilter(TrafficViewModel.ListFilter.ALL);
        });

        // Views observe the ViewModel: they update by themselves when new data is downloaded
        viewModel.getFilteredSituations().observe(getViewLifecycleOwner(), this::showSituations);
        viewModel.getLoadState().observe(getViewLifecycleOwner(), s -> updateEmptyMessage());
        viewModel.getSelected().observe(getViewLifecycleOwner(),
                s -> adapter.setSelectedKey(s == null ? null : s.getKey()));
    }

    private void showSituations(List<Situation> situations)
    {
        adapter.setItems(situations, null);
        int incidents = 0, roadworks = 0;
        for (Situation s : situations)
        {
            if (s.getFeedType() == FeedType.INCIDENT) incidents++;
            else roadworks++;
        }
        countText.setText(situations.size() + " situations (" + incidents + " unplanned events, "
                + roadworks + " roadworks) · tap one for details");
        legend.setVisibility(viewModel.getListFilter().getValue() == TrafficViewModel.ListFilter.INCIDENTS
                ? View.GONE : View.VISIBLE);
        updateEmptyMessage();
    }

    /** With no data yet, says whether it is still being downloaded or there really is nothing */
    private void updateEmptyMessage()
    {
        List<Situation> shown = viewModel.getFilteredSituations().getValue();
        boolean empty = shown == null || shown.isEmpty();
        LoadState state = viewModel.getLoadState().getValue();
        boolean loading = state != null && state.status == LoadState.Status.LOADING;
        emptyText.setText(loading ? R.string.loading_data : R.string.no_results);
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        countText.setVisibility(empty && loading ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onSituationClick(Situation situation)
    {
        viewModel.select(situation); // MainActivity shows the details pane
    }
}
